public class Recorder {
    private String type; //- "Sender" or "Receiver"
    private int dataIn = 0; //- Amount of Data received
    private int dataOut = 0; //- Amount of Data transferred
    private int packetIn = 0; //- Number of packets received
    private int packetOut = 0; //- Number of packets sent
    private int oos = 0; //- Number of out-of-sequence packets discarded
    private int ic = 0; //- Number of packets discarded due to incorrect checksum
    private int rt = 0; //- Number of retransmissions

    private int lastAck = -1;
    private int dup = 0; //- Number of duplicate acknowledgements

    public Recorder(String type) {
        this.type = type;
    }

    public void printStat() {
        if (type.equals("Sender")) {
            System.out.println("\nSender Statistics");
            System.out.println("Amount of Data transferred: " + dataOut + " bytes");
            System.out.println("Amount of Data received: " + dataIn + " bytes");
            System.out.println("Number of packets sent: " + packetOut);
            System.out.println("Number of packets received: " + packetIn);
            System.out.println("Number of packets discarded due to incorrect checksum: " + ic);
            System.out.println("Number of retransmissions: " + rt);
            System.out.println("Number of duplicate acknowledgements: " + dup);
        } else if (type.equals("Receiver")) {
            System.out.println("\nReceiver Statistics");
            System.out.println("Amount of Data transferred: " + dataOut + " bytes");
            System.out.println("Amount of Data received: " + dataIn + " bytes");
            System.out.println("Number of packets sent: " + packetOut);
            System.out.println("Number of packets received: " + packetIn);
            System.out.println("Number of out-of-sequence packets discarded: " + oos);
            System.out.println("Number of packets discarded due to incorrect checksum: " + ic);
            System.out.println("Number of retransmissions (Receiver may re-send for handshakes): " + rt);
            // System.out.println("Number of duplicate acknowledgements: " + dup);
        }
    }

    public synchronized void addDataIn(int data) {
        this.dataIn += data;
    }

    public synchronized void addDataOut(int data) {
        this.dataOut += data;
    }

    public synchronized void addPacketOut() {
        packetOut++;
    }

    public synchronized void addPacketIn() {
        packetIn++;
    }

    public synchronized void addOOS() {
        oos++;
    }

    public synchronized void addIC() {
        ic++;
    }

    public synchronized void addRT() {
        rt++;
    }

    public synchronized void addDup(int ack) {
        if (ack == lastAck) {
            dup++;
        }
        lastAck = ack;
    }
}
