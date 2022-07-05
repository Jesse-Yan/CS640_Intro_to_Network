package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.*;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import java.util.*;
import java.lang.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device implements Runnable {
    static final boolean DEBUG = false;
    private final ConcurrentHashMap<String, Record> switchingTable;

    public void run() {
        try {
            while (true) {
                if (DEBUG) DEBUG_PRINT_ST();
                if (DEBUG) System.out.println("Cleaning...");
                if (switchingTable != null) {
                    for (Map.Entry<String, Record> kv : switchingTable.entrySet()) {
                        if (System.currentTimeMillis() > kv.getValue().expireTime) {
                            switchingTable.remove(kv.getKey());
                        }
                    }
                }
                if (DEBUG) System.out.println("Finished Cleaning");
                if (DEBUG) DEBUG_PRINT_ST();
                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace(System.out);
        }

    }

    /**
     * Creates a router for a specific host.
     *
     * @param host hostname for the router
     */
    public Switch(String host, DumpFile logfile) {
        super(host, logfile);
        switchingTable = new ConcurrentHashMap<>();
        Thread t = new Thread(this);
        t.start();
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     *
     * @param etherPacket the Ethernet packet that was received
     * @param inIface     the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface) {
//        if (!((etherPacket.getPayload() instanceof ARP)
//                || (etherPacket.getPayload() instanceof ICMP)
//                || (etherPacket.getPayload() instanceof IPv4))) {
//            if (DEBUG) System.out.println("Wrong Type Packet");
//            return;
//        }


        System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));


        if (DEBUG) DEBUG_PRINT_ST();

        String srcMac = etherPacket.getSourceMAC().toString();
        if (DEBUG) System.out.println("srcMac = " + srcMac);

        if (DEBUG) {
            if (switchingTable.get(srcMac) == null) System.out.println("No record for srcMac");
            else System.out.println("table[srcMac],time_passed_in_ms,iface = "
                    + (switchingTable.get(srcMac).expireTime - System.currentTimeMillis()) + ','
                    + switchingTable.get(srcMac).iface.getName());
        }

        switchingTable.put(srcMac, new Record(inIface)); // learn src

        if (DEBUG) System.out.println("srcMac Renewing...");
        if (DEBUG) System.out.println("table[srcMac],time_passed_in_ms,iface = "
                + (switchingTable.get(srcMac).expireTime - System.currentTimeMillis()) + ','
                + switchingTable.get(srcMac).iface.getName());

        String destMac = etherPacket.getDestinationMAC().toString();
        if (DEBUG) {
            System.out.println("destMac = " + destMac);
            if (switchingTable.get(destMac) == null) System.out.println("No record for destMac");
            else System.out.println("table[destMac],time_passed_in_ms,iface = "
                    + (switchingTable.get(destMac).expireTime - System.currentTimeMillis()) + ','
                    + switchingTable.get(destMac).iface.getName());
        }


        Record record = switchingTable.get(destMac);
        if (record == null) {
            if (DEBUG) {
                System.out.println("We need to flood");
            }
            floodPacket(etherPacket, inIface);
        } else {
            if (DEBUG) {
                System.out.print("We can send directly");
                System.out.println("Send to " + record.iface.getName());
            }
            sendPacket(etherPacket, record.iface);
        }

        if (DEBUG) DEBUG_PRINT_ST();
        if (DEBUG) System.out.println("Stop handle packet");
    }


    private void floodPacket(Ethernet etherPacket, Iface inIface) {
        if (DEBUG) System.out.println("Flooding...");

        for (Iface iface : getInterfaces().values()) {
//            if (iface.getName().equals(inIface.getName())) continue;
            if (iface.equals(inIface)) continue;
            if (DEBUG) {
                System.out.println("in iface " + inIface.getName() +
                        " must not equal to out iface " + iface.getName());
                System.out.println("Send to " + iface.getName());
            }
            sendPacket(etherPacket, iface);
        }

    }


    private void DEBUG_PRINT_ST() {
        System.out.println("------Print Switching Table");

        for (String mac : switchingTable.keySet()) {
            System.out.println("mac,time_passed_in_ms,iface = "
                    + mac + ","
                    + (switchingTable.get(mac).expireTime - System.currentTimeMillis()) + ','
                    + switchingTable.get(mac).iface.getName());
        }

        System.out.println("------------------------");
    }
}

class Record {
    final int TTL = 15; // 15 sec
    long expireTime;
    Iface iface;

    Record(Iface iface) {
        expireTime = System.currentTimeMillis() + TTL * 1000L;
        this.iface = iface;
    }
}
