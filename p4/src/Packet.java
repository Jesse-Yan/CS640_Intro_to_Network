import java.nio.ByteBuffer;
import java.util.Arrays;

// Packet contains data and other information about the packet
public class Packet {
    private int seq;
    private int ack;
    private long timeStamp;
    private Flags flags;
    private byte[] data;
    private int checksum = 0;

    public Packet(int seq, int ack, long timeStamp, String flags, byte[] data) {
        this.seq = seq;
        this.ack = ack;
        this.timeStamp = timeStamp;
        this.flags = new Flags(flags);
        this.data = data;
        this.checksum = Util.checksum(toByteArray());
    }


    // Translate byte array to Packet
    public Packet(byte[] packet) {
        ByteBuffer buffer = ByteBuffer.wrap(packet);
        seq = buffer.getInt();
        ack = buffer.getInt();
        timeStamp = buffer.getLong();
        int dataLengthAndFlags = buffer.getInt();
        int S = dataLengthAndFlags >> 2 & 1;
        int F = dataLengthAndFlags >> 1 & 1;
        int A = dataLengthAndFlags & 1;
        int length = dataLengthAndFlags >> 3;
        data = Arrays.copyOfRange(packet, 24, 24 + length);
        flags = new Flags(S, F, A);
        checksum = buffer.getInt();
    }

    // Translate Packet to byte array
    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 24);
        buffer.putInt(seq);
        buffer.putInt(ack);
        buffer.putLong(timeStamp);
        buffer.putInt((data.length << 3) | (flags.getS() << 2) | (flags.getF() << 1) | flags.getA());
        buffer.putInt(checksum);
        buffer.put(data);
        return buffer.array();
    }

    public int getSeq() {
        return seq;
    }

    public int getAck() {
        return ack;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public Flags getFlags() {
        return flags;
    }

    public byte[] getData() {
        return data;
    }

    public int getChecksum() {
        return checksum;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public void setAck(int ack) {
        this.ack = ack;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public void setFlags(Flags flags) {
        this.flags = flags;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setChecksum(int checksum) {
        this.checksum = checksum;
    }

    public void resetChecksum() {
        Packet copy = new Packet(toByteArray());
        copy.setChecksum(0);
        checksum = Util.checksum(copy.toByteArray());
    }
}