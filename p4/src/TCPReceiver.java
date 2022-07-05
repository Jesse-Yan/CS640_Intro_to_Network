import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

public class TCPReceiver {

    Recorder recorder = new Recorder("Receiver");
    private int port;
    private int MSS; // MTU - 20 - 8
    private int sws;

    private boolean receiveSyn = false;

    private int seq = 0;
    private int ack = 1;

    private DatagramSocket socket;
    private InetAddress remoteIP;
    private int remotePort;

    private boolean exitTimeWait = false;

    private FileOutputStream fos;

    private TreeMap<Integer, Packet> buffer = new TreeMap<>();

    public TCPReceiver(int port, int mtu, int sws, String fileName) throws FileNotFoundException {
        this.port = port;
        this.MSS = mtu - 20 - 8;
        this.sws = sws;
        this.fos = new FileOutputStream(fileName);
    }

    public void start() throws IOException {
        this.socket = new DatagramSocket(port);
        this.socket.setReceiveBufferSize(6553600);
        connect();
        startReceive();
        terminate();
        recorder.printStat();
    }

    private void connect() throws IOException {
        while (true) {
            DatagramPacket synGram = new DatagramPacket(new byte[MSS], MSS);
            socket.receive(synGram);
            recorder.addPacketIn();
            if (handleReceiveOnStart(synGram))
                break;
        }
    }

    private void startReceive() throws IOException {
        while (true) {
            DatagramPacket dataGram = new DatagramPacket(new byte[MSS], MSS);
            socket.receive(dataGram);
            recorder.addPacketIn();
            if (handleReceiveOnData(dataGram))
                break;
        }
    }

    private void terminate() throws IOException {
        fos.close();
        sendLastFAKPkt();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                exitTimeWait = true;
                socket.close();
            }
        }, 5000);

        System.out.println("Waiting for Last Ack for 5000 ms");

        try {
            while (!exitTimeWait) {
                DatagramPacket finGram = new DatagramPacket(new byte[MSS], MSS);
                socket.receive(finGram);
                recorder.addPacketIn();
                Packet lastPacket = new Packet(finGram.getData());
                if (!Util.validateChecksum(lastPacket)) {
                    recorder.addIC();
                    continue;
                }
                recorder.addDup(lastPacket.getAck());
                Util.printReceive(lastPacket);
                synchronized (this) {
                    if (lastPacket.getFlags().equals("A")) {
                        timer.cancel();
                        socket.close();
                        break;
                    } else if (lastPacket.getFlags().equals("AF")) {
                        sendLastFAKPkt();
                    }
                }
            }
        } catch (IOException e) {
            if (!exitTimeWait) {
                throw new IOException(e);
            }
            timer.cancel();
        }
    }

    private void sendLastFAKPkt() throws IOException {
        Packet finAck = new Packet(seq, ack, System.nanoTime(), "AF", new byte[0]);
        byte[] finAckByte = finAck.toByteArray();
        socket.send(new DatagramPacket(finAckByte, finAckByte.length, remoteIP, remotePort));
        Util.printSend(finAck);
        recorder.addPacketOut();
    }

    private boolean handleReceiveOnStart(DatagramPacket gram) throws IOException {
        Packet pkt = new Packet(gram.getData());
        if (!Util.validateChecksum(pkt)) {
            recorder.addIC();
            return false;
        }
        Util.printReceive(pkt);
        recorder.addDup(pkt.getAck());
        if (pkt.getFlags().getS() == 1 && pkt.getSeq() == 0) {
            this.remoteIP = gram.getAddress();
            this.remotePort = gram.getPort();
            ack = pkt.getSeq() + 1;
            Packet synAckPkt = new Packet(seq, ack, pkt.getTimeStamp(), "SA", new byte[0]);
            byte[] synAckByte = synAckPkt.toByteArray();

            socket.send(new DatagramPacket(synAckByte, synAckByte.length, remoteIP, remotePort));

            Util.printSend(synAckPkt);
            recorder.addPacketOut();
            receiveSyn = true;
            return false;
        } else if (pkt.getFlags().getS() == 0 && pkt.getAck() == seq + 1 && receiveSyn) {
            seq += 1;
            return true;
        }
        return false;
    }

    private boolean handleReceiveOnData(DatagramPacket dataGram) throws IOException {
        Packet inPacket = new Packet(dataGram.getData());
        if (!Util.validateChecksum(inPacket)) {
            recorder.addIC();
            return false;
        }
        Util.printReceive(inPacket);
        recorder.addDup(inPacket.getAck());
        int dataLength = inPacket.getData().length;
        if (dataLength != 0) {
            int receiveSeq = inPacket.getSeq();
            if (buffer.size() >= sws) recorder.addOOS();
            if (receiveSeq > ack) {
                if (buffer.size() < sws) {
                    buffer.put(receiveSeq, inPacket);
                }
            } else if (receiveSeq == ack) {
                fos.write(inPacket.getData());
                ack += dataLength;
                recorder.addDataIn(dataLength);
                while (buffer.size() > 0 && ack == buffer.firstKey()) {
                    Packet bufPacket = buffer.pollFirstEntry().getValue();
                    fos.write(bufPacket.getData());
                    ack += bufPacket.getData().length;
                    recorder.addDataIn(bufPacket.getData().length);
                }
            }
            Packet ackPkg = new Packet(seq, ack, inPacket.getTimeStamp(), "A", new byte[0]);
            byte[] ackPkgByte = ackPkg.toByteArray();
            socket.send(new DatagramPacket(ackPkgByte, ackPkgByte.length, remoteIP, remotePort));
            Util.printSend(ackPkg);
            recorder.addPacketOut();
        } else if (inPacket.getFlags().getF() == 1) {
            ack = inPacket.getSeq() + 1;
            return true;
        }
        return false;
    }
}
