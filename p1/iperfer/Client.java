import java.io.OutputStream;
import java.net.Socket;

public class Client {
    private final String host;
    private final int port;
    private final int time;

    public Client(String host, int port, int time) {
        this.host = host;
        this.port = port;
        this.time = time;
    }

    public void start() {
        try (Socket socket = new Socket(host, port); OutputStream os = socket.getOutputStream()) {
            byte[] buffer = new byte[1000];
            double sent = 0;
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 1000L * time) {
                os.write(buffer);
                sent += 1;
            }
            double rate = 8 * sent / (1000 * time);
            System.out.println("sent=" + (int) sent + " KB rate=" + String.format("%.3f", rate) + " Mbps");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // quick test (server needs to run first)
    public static void main(String[] args) {
        Client client = new Client("127.0.0.1", 8866, 5);
        client.start();
    }
}
