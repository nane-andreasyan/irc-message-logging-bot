import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ircBot {

    public static String nickname;
    public static String username;
    public static String realName;
    public static PrintWriter out;
    public static Scanner in;


    public static void connect() throws IOException {
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter nickname:");
        nickname = sc.nextLine();

        System.out.print("Enter username:");
        username = sc.nextLine();

        System.out.print("Enter full name:");
        realName = sc.nextLine();

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket("irc.oftc.net", 6697);

        //Socket socket = new Socket("irc.oftc.net", 6667);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new Scanner(socket.getInputStream());

        write("NICK", nickname);
        write("USER", username+" 0 * :"+realName);

        while(in.hasNext()) {
            String message = in.nextLine();
            System.out.println("<<<" + message);

            if(message.startsWith("PING")) {
                System.out.println("got a ping");
                String content = message.substring(5);
                write("PONG", content);
            }

            if (message.contains(" 376 ") || message.contains(" 422 ")) {
                System.out.println("Which channel would you like to join?");
                String channel = sc.nextLine();
                if (!channel.startsWith("#")) {
                    channel = "#" + channel;
                }
                write("JOIN",channel);
            }
        }

        in.close();
        out.close();
        socket.close();
    }

    private static void write (String command, String message) {
        String serverMessage = command + " " + message;
        System.out.println(serverMessage);
        out.print(serverMessage + "\r\n");
        out.flush();
    }

    public static void main(String[] args) throws IOException {
        connect();

    }
}
