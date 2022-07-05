public class Iperfer {
    static boolean isClient;
    static String host;
    static int port;
    static int time;

    static void badArgs() {
        System.err.println("Error: missing or additional arguments");
        System.exit(-1);
    }

    static void badPort() {
        System.err.println("Error: port number must be in the range 1024 to 65535");
        System.exit(-1);
    }

    public static void main(String[] args) {
        // check bad args
        if (args.length != 7 && args.length != 3) badArgs();
        if (args.length == 7) {
            if (!"-c".equals(args[0])) badArgs();
            if (!"-h".equals(args[1])) badArgs();
            if (!"-p".equals(args[3])) badArgs();
            if (!"-t".equals(args[5])) badArgs();
        }
        if (args.length == 3) {
            if (!"-s".equals(args[0])) badArgs();
            if (!"-p".equals(args[1])) badArgs();
        }

        // parse args
        if (args.length == 7) {
            isClient = true;
            host = args[2];
            port = Integer.parseInt(args[4]);
            time = Integer.parseInt(args[6]);
        }
        if (args.length == 3) {
            isClient = false;
            port = Integer.parseInt(args[2]);
        }

        // check port range
        if (port < 1024 || port > 65535) badPort();

        if (isClient) {
            Client client = new Client(host, port, time);
            client.start();
        } else {
            Server server = new Server(port);
            server.start();
        }
    }
}

