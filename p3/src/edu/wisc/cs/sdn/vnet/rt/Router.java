package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.*;

import java.util.*;
import java.nio.ByteBuffer;


/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
    private final int ICMP_TTL = 0;
    private final int ICMP_NETWORK_UNREACHABLE = 1;
    private final int ICMP_HOST_UNREACHABLE = 2;
    private final int ICMP_PORT_UNREACHABLE = 3;
    private final int ICMP_ECHO = 4;
    private final int RIP_REQUEST = 5;
    private final int RIP_RESPONSE = 6;
    private final int RIP_UNSOLICATED = 7;

    /**
     * Routing table for the router
     */
    private RouteTable routeTable;

    /**
     * ARP cache for the router
     */
    private ArpCache arpCache;

    /**
     * Creates a router for a specific host.
     *
     * @param host hostname for the router
     */
    public Router(String host, DumpFile logfile) {
        super(host, logfile);
        this.routeTable = new RouteTable();
        this.arpCache = new ArpCache();
    }

    /**
     * @return routing table for the router
     */
    public RouteTable getRouteTable() {
        return this.routeTable;
    }

    /**
     * Load a new routing table from a file.
     *
     * @param routeTableFile the name of the file containing the routing table
     */
    public void loadRouteTable(String routeTableFile) {
        if (!routeTable.load(routeTableFile, this)) {
            System.err.println("Error setting up routing table from file " + routeTableFile);
            System.exit(1);
        }

        System.out.println("Loaded static route table");
        System.out.println("-------------------------------------------------");
        System.out.print(this.routeTable.toString());
        System.out.println("-------------------------------------------------");
    }

    /**
     * Load a new ARP cache from a file.
     *
     * @param arpCacheFile the name of the file containing the ARP cache
     */
    public void loadArpCache(String arpCacheFile) {
        if (!arpCache.load(arpCacheFile)) {
            System.err.println("Error setting up ARP cache from file " + arpCacheFile);
            System.exit(1);
        }

        System.out.println("Loaded static ARP cache");
        System.out.println("----------------------------------");
        System.out.print(this.arpCache.toString());
        System.out.println("----------------------------------");
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     *
     * @param etherPacket the Ethernet packet that was received
     * @param inIface     the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface) {
        System.out.println("*** -> Received packet: " + etherPacket.toString().replace("\n", "\n\t"));

        /********************************************************************/
        /* TODO: Handle packets											 */

        switch (etherPacket.getEtherType()) {
            case Ethernet.TYPE_IPv4:
                IPv4 ipPacket = (IPv4) etherPacket.getPayload();
                if (ipPacket.getDestinationAddress() == IPv4.toIPv4Address("224.0.0.9") && ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
                    UDP udpPacket = (UDP) ipPacket.getPayload();
                    if (udpPacket.getDestinationPort() == UDP.RIP_PORT) {
                        this.handleRIPPacket(etherPacket, inIface);
                        break;
                    }
                }

                this.handleIpPacket(etherPacket, inIface);
                break;
            // Ignore all other packet types, for now
        }

        /********************************************************************/
    }

    private void handleICMP(IPv4 ipPacket, Iface inIface, int mode) {
        // Handle Ethernet
        Ethernet ether = new Ethernet();
        ether.setEtherType(Ethernet.TYPE_IPv4);
        ether.setSourceMACAddress(inIface.getMacAddress().toBytes());
        int sourAddr = ipPacket.getSourceAddress();
        RouteEntry sourceMatch = this.routeTable.lookup(sourAddr);
        int next = sourceMatch.getGatewayAddress();
        if (0 == next) {
            next = sourAddr;
        }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(next);
        if (null != arpEntry) {
            ether.setDestinationMACAddress(arpEntry.getMac().toBytes());

            // Handle IP
            IPv4 ip = new IPv4();
            ip.setTtl((byte) 64);
            ip.setProtocol(IPv4.PROTOCOL_ICMP);
            ip.setSourceAddress(mode == ICMP_ECHO ? ipPacket.getDestinationAddress() : inIface.getIpAddress());
            ip.setDestinationAddress(sourAddr);

            // Handle ICMP
            ICMP icmp = new ICMP();
            byte type;
            byte code;
            switch (mode) {
                case ICMP_TTL:
                    type = (byte) 11;
                    code = (byte) 0;
                    break;
                case ICMP_NETWORK_UNREACHABLE:
                    type = (byte) 3;
                    code = (byte) 0;
                    break;
                case ICMP_HOST_UNREACHABLE:
                    type = (byte) 3;
                    code = (byte) 1;
                    break;
                case ICMP_PORT_UNREACHABLE:
                    type = (byte) 3;
                    code = (byte) 3;
                    break;
                default:
                    type = (byte) 0;
                    code = (byte) 0;
                    break;
            }
            icmp.setIcmpType(type);
            icmp.setIcmpCode(code);

            // Handle Data
            if (mode != ICMP_ECHO) {
                Data data = new Data();
                int dataLength = ipPacket.getHeaderLength() * 4 + 8;
                ByteBuffer bb = ByteBuffer.allocate(dataLength + 4);
                bb.put(new byte[]{0, 0, 0, 0});
                byte[] serialized = ipPacket.serialize();
                for (int i = 0; i < dataLength; i++) {
                    bb.put(serialized[i]);
                }
                data.setData(bb.array());
                icmp.setPayload(data);
            } else {
                ICMP ipPacketICMP = (ICMP) ipPacket.getPayload();
                icmp.setPayload(ipPacketICMP.getPayload());
            }

            // Set Payload
            ether.setPayload(ip);
            ip.setPayload(icmp);


            // Compute Checksum and Send
            ip.resetChecksum();
            byte[] serialized = ip.serialize();
            ip.deserialize(serialized, 0, serialized.length);
            this.sendPacket(ether, inIface);
        }
    }

    public void initRIP() {
        // System.out.println("Initializing RIP");

        // broadcast RIP packet
        // System.out.println("Broadcasting RIP packet");
        for (Iface iface : this.interfaces.values()) {
            int mask = iface.getSubnetMask();
            int network = iface.getIpAddress() & iface.getSubnetMask();
            DVEntry dvEntry = new DVEntry();
            dvEntry.address = network;
            dvEntry.mask = mask;
            routeTable.updateDV(network, dvEntry);
            routeTable.insert(network, 0, mask, iface);
            sendRIP(RIP_REQUEST, null, iface);
        }

        // start timers
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("SEND UNSOLLICITED RIP EVERY 10 SECONDS");
                for (Iface iface : interfaces.values()) {
                    sendRIP(RIP_UNSOLICATED, null, iface);
                }
            }
        }, 0, 10000);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("CLEAN OVERDUE EVERY SECONDS");
                routeTable.cleanDV();
            }
        }, 0, 1000);
    }

    private void handleRIPPacket(Ethernet etherPacket, Iface inIface) {
        // System.out.println("Handling RIP packet");
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        UDP udpPacket = (UDP) ipPacket.getPayload();
        RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();
        int mode = ripPacket.getCommand();
        if (mode == RIPv2.COMMAND_REQUEST) {
            handleRipRequest(etherPacket, inIface);
        } else if (mode == RIPv2.COMMAND_RESPONSE) {
            handleRipResponse(etherPacket, inIface);
        }
    }

    private void handleRipRequest(Ethernet etherPacket, Iface inIface) {
        sendRIP(RIP_RESPONSE, etherPacket, inIface);
    }

    private void handleRipResponse(Ethernet etherPacket, Iface inIface) {
        // System.out.println("Handling RIP response");
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        UDP udpPacket = (UDP) ipPacket.getPayload();
        RIPv2 ripPacket = (RIPv2) udpPacket.getPayload();

        List<RIPv2Entry> entries = ripPacket.getEntries();
        for (RIPv2Entry entry : entries) {
            int ip = entry.getAddress();
            int mask = entry.getSubnetMask();
            int nextHop = ipPacket.getSourceAddress();
            int metric = Math.min(16, entry.getMetric() + 1);
            int network = ip & mask;
            routeTable.mergeDV(network, mask, nextHop, metric, ip, inIface);
        }
    }

    private void sendRIP(int mode, Ethernet etherPacket, Iface iface) {
        // System.out.println("Sending RIP");
        // Create Ethernet
        Ethernet etherPacketResponse = new Ethernet();
        etherPacketResponse.setEtherType(Ethernet.TYPE_IPv4);
        etherPacketResponse.setSourceMACAddress(iface.getMacAddress().toBytes());
        // Create IP
        IPv4 ipPacketResponse = new IPv4();
        ipPacketResponse.setProtocol(IPv4.PROTOCOL_UDP);
        ipPacketResponse.setSourceAddress(iface.getIpAddress());
        ipPacketResponse.setTtl((byte) 64);
        // Create UDP
        UDP udpPacketResponse = new UDP();
        udpPacketResponse.setSourcePort(UDP.RIP_PORT);
        udpPacketResponse.setDestinationPort(UDP.RIP_PORT);
        RIPv2 ripPacketResponse = new RIPv2();

        // Create RIP Packet
        if (mode == RIP_REQUEST) {
            etherPacketResponse.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
            ipPacketResponse.setDestinationAddress(IPv4.toIPv4Address("224.0.0.9"));
            ripPacketResponse.setCommand(RIPv2.COMMAND_REQUEST);
        } else if (mode == RIP_UNSOLICATED) {
            etherPacketResponse.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
            ipPacketResponse.setDestinationAddress(IPv4.toIPv4Address("224.0.0.9"));
            ripPacketResponse.setCommand(RIPv2.COMMAND_RESPONSE);
        } else if (mode == RIP_RESPONSE) {
            etherPacketResponse.setDestinationMACAddress(etherPacketResponse.getSourceMACAddress());
            ipPacketResponse.setDestinationAddress(((IPv4) etherPacket.getPayload()).getSourceAddress());
            ripPacketResponse.setCommand(RIPv2.COMMAND_RESPONSE);
        }
        etherPacketResponse.setPayload(ipPacketResponse);
        ipPacketResponse.setPayload(udpPacketResponse);
        udpPacketResponse.setPayload(ripPacketResponse);
        List<RIPv2Entry> entries = routeTable.loadDV();
        ripPacketResponse.setEntries(entries);
        sendPacket(etherPacketResponse, iface);
    }


    private void handleIpPacket(Ethernet etherPacket, Iface inIface) {
        // Make sure it's an IP packet
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
            return;
        }

        for (Iface iface : this.interfaces.values()) {
            arpCache.insert(iface.getMacAddress(), iface.getIpAddress());
        }

        // Get IP header
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum) {
            return;
        }

        // Check TTL
        ipPacket.setTtl((byte) (ipPacket.getTtl() - 1));
        if (0 == ipPacket.getTtl()) {
            handleICMP(ipPacket, inIface, ICMP_TTL);
            return;
        }

        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();

        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values()) {
            if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
                byte pkgProtocol = ipPacket.getProtocol();
                if (pkgProtocol == IPv4.PROTOCOL_UDP || pkgProtocol == IPv4.PROTOCOL_TCP) {
                    handleICMP(ipPacket, inIface, ICMP_PORT_UNREACHABLE);
                } else if (pkgProtocol == IPv4.PROTOCOL_ICMP) {
                    ICMP icmp = (ICMP) ipPacket.getPayload();
                    if (icmp.getIcmpType() == (byte) 8) {
                        // Echo
                        handleICMP(ipPacket, inIface, ICMP_ECHO);
                    }
                }
                return;
            }
        }

        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
    }

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface) {
        // Make sure it's an IP packet
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
            return;
        }
        System.out.println("Forward IP packet");

        // Get IP header
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch) {
            handleICMP(ipPacket, inIface, ICMP_NETWORK_UNREACHABLE);
            return;
        }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface) {
            return;
        }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop) {
            nextHop = dstAddr;
        }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry) {
            handleICMP(ipPacket, inIface, ICMP_HOST_UNREACHABLE);
            return;
        }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

        this.sendPacket(etherPacket, outIface);
    }
}
