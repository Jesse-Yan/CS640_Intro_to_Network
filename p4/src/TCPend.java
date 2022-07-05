import java.io.IOException;

public class TCPend {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 12) {
            int port = Integer.parseInt(args[1]);
            String remoteIP = args[3];
            int remotePort = Integer.parseInt(args[5]);
            String fileName = args[7];
            int mtu = Integer.parseInt(args[9]);
            int sws = Integer.parseInt(args[11]);
            TCPSender sender = new TCPSender(port, remoteIP, remotePort, fileName, mtu, sws);
            sender.start();
        } else if (args.length == 8) {
            int port = Integer.parseInt(args[1]);
            int mtu = Integer.parseInt(args[3]);
            int sws = Integer.parseInt(args[5]);
            String fileName = args[7];
            TCPReceiver receiver = new TCPReceiver(port, mtu, sws, fileName);
            receiver.start();
        } else {
            System.out.println("To Send:");
            System.out.println("java TCPend -p <port> -s <remote IP> -a <remote port> –f <file name> -m <mtu> -c <sws>");
            System.out.println("To Receive:");
            System.out.println("java TCPend -p <port> -m <mtu> -c <sws> -f <file name>");
        }
    }
}
