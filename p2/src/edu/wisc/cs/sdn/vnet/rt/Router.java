package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import java.nio.ByteBuffer;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	static final boolean DEBUG = false;

	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
				etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		// Handle packets                                            
		
		if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
			if (DEBUG) System.out.println("Return on TYPE");
			return;
		}
		
		IPv4 pkt = (IPv4) etherPacket.getPayload();
		short ck = pkt.getChecksum();
		if (DEBUG) System.out.println("CheckSum in Pkt: " + ck);
		pkt.setChecksum((short) 0);
		ByteBuffer bb = ByteBuffer.wrap(pkt.serialize());
		if (ck != bb.getShort(10)) {
			if (DEBUG) System.out.println("Return on Checksum");
			return;
		}

		if (DEBUG) System.out.println("TTL in Pkt: " + pkt.getTtl());
		if (pkt.getTtl() <= (byte) 1) {
			if (DEBUG) System.out.println("Return on TTL");
			return;
		}

		pkt.setTtl((byte) (pkt.getTtl() - 1));

		pkt.setChecksum((short) 0);
		byte[] pkts = pkt.serialize();
		pkt.deserialize(pkts, 0, pkts.length);

		int dest = pkt.getDestinationAddress();
		if (DEBUG) System.out.println("PKT Dest IP: " + dest);

		for (Iface i : interfaces.values()) {
			if (i.getIpAddress() == dest) {
				if (DEBUG) System.out.println("Return on Check Interface");
				return;
			}
		}

		RouteEntry re = routeTable.lookup(dest);
		if (re == null) {
			if (DEBUG) System.out.println("Return on No RouteEntry");
			return;
		}

		etherPacket.setPayload(pkt);
		
		ArpEntry ae = arpCache.lookup(dest);
		MACAddress ma = ae.getMac();
		if (DEBUG) System.out.println("NPE: " + re.getInterface().getMacAddress());
		etherPacket.setSourceMACAddress(re.getInterface().getMacAddress().toBytes());
		etherPacket.setDestinationMACAddress(ma.toBytes());

		if (DEBUG) System.out.println("MAC Source: " + etherPacket.getSourceMAC());
		if (DEBUG) System.out.println("MAC Dest: " + etherPacket.getDestinationMAC());

		boolean res = sendPacket(etherPacket, re.getInterface());
		if (DEBUG) System.out.println("SendPacket: " + res);
		/********************************************************************/
	}
}
