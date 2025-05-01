import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.time.LocalDateTime;
import java.net.Socket;
import java.util.Scanner;

public class ircBot {

    public static String nickname;
    public static String username;
    public static String realName;
    public static PrintWriter out;
    public static Scanner in;
    private static final String LOG_DIR = "logs";


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

        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            boolean created = logDir.mkdir();
            if (!created) {
                System.err.println("Failed to create logs directory");
            }
        }

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
                write("JOIN", channel);
                String finalChannel = channel;
                new Thread(() -> {
                    Scanner userInput = new Scanner(System.in);
                    while (true) {
                        String line = userInput.nextLine();
                        if (line.startsWith("/join ")) {
                            String newChannel = line.split(" ", 2)[1];
                            if (!newChannel.startsWith("#")) {
                                newChannel = "#" + newChannel;
                            }
                            write("JOIN", newChannel);
                            System.out.println("Joined channel " + newChannel);
                        }
                        else if (line.equalsIgnoreCase("/part")) {
                            write("PART", finalChannel);
                            System.out.println("Left the channel " + finalChannel);
                        }
                        else if (line.equalsIgnoreCase("/quit")) {
                            write("QUIT", ":Goodbye");
                            System.exit(0);
                        }
                        else if (line.startsWith("/msg ")) {
                            String[] tokens = line.split(" ", 3);
                            String recipient = tokens[1];
                            String privateMessage = tokens[2];
                            write("PRIVMSG", recipient + " :" + privateMessage);
                            System.out.println("Sent private message to " + recipient + ": " + privateMessage);
                        }
                        else {
                            write("PRIVMSG " + finalChannel, ":" + line);
                        }
                    }
                }).start();
            }

            if (message.contains("PRIVMSG")) {
                String[] parts = message.split(" ", 4);
                if (parts.length >= 4) {
                    String sender = parts[0].substring(1, parts[0].indexOf("!"));
                    String target = parts[2];
                    String msgText = message.split(":", 3)[2];

                    if (target.startsWith("#")) {
                        String timestamp = java.time.LocalDateTime.now().toString();
                        String logLine = String.format("[%s] <%s> %s", timestamp, sender, msgText);
                        String fileName = LOG_DIR + "/log_" + target.substring(1) + ".txt";
                        try (FileWriter fw = new FileWriter(fileName, true)) {
                            fw.write(logLine + "\n");
                        } catch (IOException e) {
                            System.err.println("Error writing log: " + e.getMessage());
                        }
                    }
                    else if (target.equalsIgnoreCase(nickname)) {
                        System.out.println("Private message from " + sender + ": " + msgText);
                    }

                }
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

        if (command.equals("PRIVMSG")) {
            String[] msgParts = message.split(" ", 2);
            if (msgParts.length == 2) {
                String target = msgParts[0];  // e.g., #testchannel
                String text = msgParts[1].startsWith(":") ? msgParts[1].substring(1) : msgParts[1];
                if (target.startsWith("#")) {
                    String timestamp = java.time.LocalDateTime.now().toString();
                    String logLine = String.format("[%s] <%s> %s", timestamp, nickname, text);
                    String fileName = LOG_DIR + "/log_" + target.substring(1) + ".txt";
                    try (FileWriter fw = new FileWriter(fileName, true)) {
                        fw.write(logLine + "\n");
                    } catch (IOException e) {
                        System.err.println("Error logging sent message: " + e.getMessage());
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        connect();

    }
}
