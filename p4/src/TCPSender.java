import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;

public class TCPSender {
    private Recorder recorder = new Recorder("Sender");
    private int port;
    private InetAddress remoteIP;
    private int remotePort;
    private int sws;

    private int MSS; // MTU - 20 - 8
    private int MDS;

    // Sent but not acked
    private ConcurrentSkipListMap<Integer, RetransmissionPacket> pending = new ConcurrentSkipListMap<>();

    private long timeout = 5000000000L; // initial timeout in ns, will be changed based on RTT using updateTimeout()

    private int ack = 0;
    private int receiverAck = 0;
    private int seq = 0;

    private int fileIdx = 0;

    private int ackCount = 0;

    private int mnr = 16;

    private boolean exitTimeWait = false;

    private ScheduledExecutorService pool;

    private DatagramSocket socket;

    Thread receiveAck = null;

    private byte[] file; // file content

    public TCPSender(int port, String remoteIP, int remotePort, String fileName, int mtu, int sws) throws IOException {
        this.port = port;
        this.remoteIP = InetAddress.getByName(remoteIP);
        this.remotePort = remotePort;
        this.file = Util.readFile(fileName);
        this.sws = sws;
        this.MSS = mtu - 20 - 8;
        this.MDS = MSS - 24;
        pool = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = defaultThreadFactory.newThread(r);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        });
    }

    public void start() throws IOException, InterruptedException {
        socket = new DatagramSocket(port);
        socket.setReceiveBufferSize(6553600);
        connect();
        socket.setSoTimeout(10);
        startSending();
        pool.shutdownNow();
        socket = new DatagramSocket(port);
        socket.setReceiveBufferSize(6553600);
        socket.setSoTimeout(1);
        pool = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = defaultThreadFactory.newThread(r);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        });
        terminate();
        recorder.printStat();
        pool.shutdown();
    }

    // Used for Debugging
    public void debug() {
        pool.schedule(() -> {
            System.out.println("Pending Size:" + pending.size());
            System.out.println("Seq:" + seq);
            System.out.println("Ack:" + receiverAck);
            debug();
        }, 2, TimeUnit.SECONDS);
    }

    public void connect() throws IOException {
        Packet packet = new Packet(seq, 0, System.nanoTime(), "S", new byte[0]);
        sendPacket(packet);

        DatagramPacket inDatagramPacket = new DatagramPacket(new byte[MSS], MSS);
        Packet receivedPkg;
        while (true) {
            socket.receive(inDatagramPacket);
            recorder.addPacketIn();
            receivedPkg = handleReceive(inDatagramPacket);
            if (receivedPkg != null)
                break;
        }

        seq += 1;
        ack = receivedPkg.getSeq() + 1;
        receiverAck = receivedPkg.getAck();
        packet = new Packet(seq, ack, System.nanoTime(), "A", new byte[0]);
        byte[] packetByte = packet.toByteArray();
        Util.printSend(packet);
        socket.send(new DatagramPacket(packetByte, packetByte.length, remoteIP, remotePort));
        recorder.addPacketOut();
    }

    private void startSending() throws IOException, InterruptedException {
        receiveAck = new Thread(() -> {
            while (true) {
                DatagramPacket ackPacket = new DatagramPacket(new byte[MSS], MSS);
                try {
                    socket.receive(ackPacket);
                    recorder.addPacketIn();
                    handleReceive(ackPacket);
                } catch (SocketTimeoutException e1) {
                    try {
                        Thread.sleep(5);
                    } catch (Exception e) {
                    }
                } catch (IOException e2) {
                    if (pending.size() != 0) {
                        System.err.println(e2);
                        recorder.printStat();
                        System.exit(1);
                    } else {
                        return;
                    }
                }
            }
        });
        receiveAck.setPriority(Thread.MAX_PRIORITY);
        receiveAck.start();
        while (fileIdx < file.length) {
            int sendingSize = Math.min((sws - pending.size()) * MDS, MDS);
            if (sendingSize == 0) {
                continue;
            }
            if (file.length - fileIdx < sendingSize) {
                sendingSize = file.length - fileIdx;
            }
            byte[] data = Arrays.copyOfRange(file, fileIdx, fileIdx + sendingSize);
            fileIdx += sendingSize;
            Packet packet = new Packet(seq, ack, System.nanoTime(), "A", data);
            sendPacket(packet);
            seq += sendingSize;
            recorder.addDataOut(sendingSize);
        }
        while (pending.size() > 0) 
            cleanPending();
        socket.close();
        receiveAck.join();
        receiveAck = null;
    }

    private void terminate() throws IOException {
        timeout = timeout * 16;
        
        Packet finPkg = new Packet(seq, ack, System.nanoTime(), "FA", new byte[0]);
        sendPacket(finPkg);
        seq += 1;
        
        boolean timeWaitState = false;

        try {
            while (!exitTimeWait) {
                DatagramPacket finAckGram = new DatagramPacket(new byte[MSS], MSS);
                try {
                    socket.receive(finAckGram);
                } catch (SocketTimeoutException e) {
                    continue;
                }
                recorder.addPacketIn();
                handleReceive(finAckGram);
                Packet finAck = new Packet(finAckGram.getData());
                if (finAck.getFlags().getF() == 1) {
                    Packet lastPack = new Packet(seq, ack + 1, System.nanoTime(), "A", new byte[0]);
                    Util.printSend(lastPack);
                    socket.send(new DatagramPacket(lastPack.toByteArray(), lastPack.toByteArray().length, remoteIP,
                            remotePort));
                    recorder.addPacketOut();
                    if (timeWaitState) {
                        recorder.addRT();
                    } else {
                        timeWaitState = true;
                        pool.schedule(() -> {
                            exitTimeWait = true;
                            socket.close();
                        }, 16 * timeout, TimeUnit.NANOSECONDS);
                        System.out.println("Entering TIME_WAIT for " + 16 * timeout / 1000000 + "ms");
                    }
                }
            }
        } catch (RejectedExecutionException e1) {

        } catch (IOException e2) {
            if (!exitTimeWait) {
                throw new IOException(e2);
            }
        }
    }

    private synchronized Packet handleReceive(DatagramPacket inDatagramPacket) throws IOException {
        Packet inPacket = new Packet(inDatagramPacket.getData());
        if (!Util.validateChecksum(inPacket)) {
            recorder.addIC();
            return null;
        }

        updateTimeout(inPacket);
        Util.printReceive(inPacket);

        int pkgAck = inPacket.getAck();
        recorder.addDup(pkgAck);
        if (pkgAck < receiverAck) {
            for(Map.Entry<Integer, RetransmissionPacket> pair : pending.entrySet()) {
                pair.getValue().setMnr(0);
            }
            return null;
        } else if (pkgAck == receiverAck) {
            ackCount++;
            if (ackCount >= 3) {
                ackCount = 0;
                RetransmissionPacket rp = pending.get(pkgAck);
                if (rp != null && rp.getFuture() != null) {
                    rp.getFuture().cancel(true);
                    rp.setFuture(fastRetransmittingPacket(rp.getPacket()));
                }
                cleanPending();
            }
            for(Map.Entry<Integer, RetransmissionPacket> pair : pending.entrySet()) {
                pair.getValue().setMnr(0);
            }
            return null;
        } else {
            receiverAck = pkgAck;
            ackCount = 1;
            cleanPending();
            for(Map.Entry<Integer, RetransmissionPacket> pair : pending.entrySet()) {
                pair.getValue().setMnr(0);
            }
        }
        return inPacket;
    }

    private synchronized void cleanPending() {
        while (pending.size() > 0 && pending.firstKey() < receiverAck) {
            RetransmissionPacket rp = pending.pollFirstEntry().getValue();
            if (rp.getFuture() != null) {
                rp.getFuture().cancel(true);
            }
        }
    }

    public synchronized void sendPacket(Packet pkt) throws IOException {
        pkt.setTimeStamp(System.nanoTime());
        pkt = Util.resetChecksum(pkt);
        byte[] packetByte = pkt.toByteArray();
        Util.printSend(pkt);
        socket.send(new DatagramPacket(packetByte, packetByte.length, remoteIP, remotePort));
        recorder.addPacketOut();
        pending.put(pkt.getSeq(), new RetransmissionPacket(pkt, retransmittingPacket(pkt)));
    }

    private synchronized ScheduledFuture<?> fastRetransmittingPacket(Packet pkt) throws IOException {
        updateRetransmittingPacket(pkt);
        return retransmittingPacket(pkt);
    }

    private synchronized ScheduledFuture<?> retransmittingPacket(Packet pkt) {
        try {
            return pool.schedule(() -> {
                synchronized (this) {
                    if (pkt.getSeq() >= receiverAck && pending.containsKey(pkt.getSeq())) {
                        try {
                            updateRetransmittingPacket(pkt);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }, timeout, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException e) {

        }
        return null;
    }

    private synchronized void updateRetransmittingPacket(Packet pkt) throws IOException {
        RetransmissionPacket rp = pending.get(pkt.getSeq());
        if (rp.getMnr() >= mnr) {
            System.err.println("Maximum Number of Retransmissions Exceed!");
            recorder.printStat();
            System.exit(1);
        }
        Packet rpkt = rp.getPacket();
        rpkt.setTimeStamp(System.nanoTime());
        Util.resetChecksum(rpkt);
        socket.send(new DatagramPacket(rpkt.toByteArray(), rpkt.toByteArray().length, remoteIP, remotePort));
        recorder.addRT();
        recorder.addPacketOut();
        Util.printSend(pkt);
        rp.setMnr(rp.getMnr() + 1);
        rp.setFuture(retransmittingPacket(pkt));
        if (rp.getMnr() >= mnr - 4 && receiveAck != null) {
            DatagramPacket ackPacket = new DatagramPacket(new byte[MSS], MSS);
            try {
                socket.receive(ackPacket);
                recorder.addPacketIn();
                handleReceive(ackPacket);
            } catch (IOException e) {
                return;
            }
        }
    }

    private long estimatedRTT;
    private long estimatedDevRTT;
    private long sampleRTT;
    private long sampleDevRTT;

    private synchronized void updateTimeout(Packet inPacket) {
        long timestamp = inPacket.getTimeStamp();
        int seq = inPacket.getSeq();
        if (seq == 0) {
            estimatedRTT = System.nanoTime() - timestamp;
            estimatedDevRTT = 0;
            timeout = estimatedRTT * 2;
        } else {
            sampleRTT = System.nanoTime() - timestamp;
            sampleDevRTT = Math.abs(sampleRTT - estimatedRTT);
            estimatedRTT = (long) (0.875 * estimatedRTT + 0.125 * sampleRTT);
            estimatedDevRTT = (long) (0.75 * estimatedDevRTT + 0.25 * sampleDevRTT);
            timeout = estimatedRTT + 4 * estimatedDevRTT;
        }
    }
}