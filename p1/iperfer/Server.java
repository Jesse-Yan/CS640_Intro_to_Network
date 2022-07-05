import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private final int port;

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port);
             Socket clientSocket = serverSocket.accept();
             InputStream is = clientSocket.getInputStream();
        ) {
            byte[] buffer = new byte[1000];
            double received = 0;
            long startTime = System.currentTimeMillis();

            int len;
            while ((len = is.read(buffer)) != -1) {
                received += len;
            }

            long endTime = System.currentTimeMillis();
            double rate = (8 * received) / 1000 / (endTime - startTime);
            System.out.println("received=" + (int) received / 1000 + " KB rate=" + String.format("%.3f", rate) + " Mbps");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // quick test (server needs to run first)
    public static void main(String[] args) {
        Server server = new Server(8866);
        server.start();
    }
}
