import java.util.concurrent.ScheduledFuture;

public class RetransmissionPacket {
    private Packet packet;
    private ScheduledFuture<?> future;
    private int mnr = 0;

    public RetransmissionPacket(Packet packet, ScheduledFuture<?> future) {
        this.packet = packet;
        this.future = future;
    }

    public ScheduledFuture<?> getFuture() {
        return future;
    }

    public void setFuture(ScheduledFuture<?> future) {
        this.future = future;
    }

    public Packet getPacket() {
        return packet;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

    public int getMnr() {
        return mnr;
    }

    public void setMnr(int mnr) {
        this.mnr = mnr;
    }
}
