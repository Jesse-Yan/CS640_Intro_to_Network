import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Util {
    public static void printSend(Packet packet) {
        System.out.println("snd " + print(packet));

    }

    public static void printReceive(Packet packet) {
        System.out.println("rcv " + print(packet));
    }

    private static String print(Packet packet) {
        return packet.getTimeStamp() + " " +
                (packet.getFlags().getS() == 1 ? "S " : "- ") +
                (packet.getFlags().getA() == 1 ? "A " : "- ") +
                (packet.getFlags().getF() == 1 ? "F " : "- ") +
                (packet.getData().length > 0 ? "D " : "- ") +
                packet.getSeq() + " " +
                packet.getData().length + " " +
                packet.getAck();
    }

    public static int checksum(byte[] data) { //
        int checksum = 0;
        for (int i = 0; i < data.length; i += 16) {
            int segment = 0;
            for (int j = 0; j < 16; j++) {
                if (i + j < data.length) {
                    segment += (data[i + j] << (16 - j - 1));
                }
            }
            checksum += segment;
            if (checksum >> 16 != 0) {
                checksum = (checksum & 0b1111111111111111) + 1;
            }
        }

        return ~checksum & 0b1111111111111111;
    }

    public static Packet resetChecksum(Packet packet) {
        packet.setChecksum(0);
        packet.setChecksum(checksum(packet.toByteArray()));
        return packet;
    }

    public static boolean validateChecksum(Packet packet) {
        Packet copy = new Packet(packet.toByteArray());
        copy.setChecksum(0);
        return packet.getChecksum() == checksum(copy.toByteArray());
    }

    public static byte[] readFile(String path) throws IOException {
        byte[] data = Files.readAllBytes(Paths.get(path));
        return data;
    }
}
