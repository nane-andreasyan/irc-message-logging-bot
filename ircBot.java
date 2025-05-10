import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class ircBot {


    public static PrintWriter out;
    public static Scanner in;
    private static final String LOG_DIR = "logs";
    private static String NICKNAME = "IRCLogBot";
    public static String USERNAME = "IRCLogBot";
    public static String REALNAME = "IRCLogBot";
    private static final String LOG_FILE = LOG_DIR + "/log_main.txt";

    public static void connect() throws IOException {
        Scanner sc = new Scanner(System.in);

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket("irc.oftc.net", 6697);

        //Socket socket = new Socket("irc.oftc.net", 6667);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new Scanner(socket.getInputStream());

        write("NICK", NICKNAME);
        write("USER", USERNAME+" 0 * :"+ REALNAME);

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
                        } else if (line.equalsIgnoreCase("/part")) {
                            write("PART", finalChannel);
                            System.out.println("Left the channel " + finalChannel);
                        } else if (line.equalsIgnoreCase("/quit")) {
                            write("QUIT", ":Goodbye");
                            System.exit(0);
                        } else if (line.startsWith("/msg ")) {
                            String[] tokens = line.split(" ", 3);
                            if (tokens.length >= 3) {
                                String recipient = tokens[1];
                                String privateMessage = tokens[2];
                                write("PRIVMSG", recipient + " :" + privateMessage);
                                System.out.println("Sent private message to " + recipient + ": " + privateMessage);
                            }
                        } else {
                            write("PRIVMSG", finalChannel + " :" + line);
                        }
                    }
                }).start();
            }

            if (message.contains(" JOIN ")) {
                String joiner = message.substring(1, message.indexOf("!"));
                String channel = message.split("JOIN ")[1].substring(1);
                if (!joiner.equalsIgnoreCase(nickname)) {
                    logEvent(String.format("[%s] *** %s joined #%s", LocalDateTime.now(), joiner, channel));
                }
            }

            if (message.contains(" PART ")) {
                String leaver = message.substring(1, message.indexOf("!"));
                String channel = message.split("PART ")[1].split(" ")[0];
                if (!leaver.equalsIgnoreCase(nickname)) {
                    logEvent(String.format("[%s] *** %s left #%s", LocalDateTime.now(), leaver, channel));
                }
            }

            if (message.contains("PRIVMSG")) {
                String[] parts = message.split(" ", 4);
                if (parts.length >= 4) {
                    String sender = parts[0].substring(1, parts[0].indexOf("!"));
                    String target = parts[2];
                    String msgText = message.split(":", 3)[2];

                    if (target.startsWith("#")) {
                        logEvent(String.format("[%s] <%s> (channel %s): %s", LocalDateTime.now(), sender, target, msgText));
                    } else if (target.equalsIgnoreCase(nickname)) {
                        System.out.println("Private message from " + sender + ": " + msgText);
                        if (msgText.toLowerCase().startsWith("last ")) {
                            handleLastNCommand(sender, msgText);
                        }
                    }
                }
            }

            if (message.contains(" QUIT ")) {
                String quitter = message.substring(1, message.indexOf("!"));
                String quitMessage = "";

                int quitIndex = message.indexOf("QUIT :");
                if (quitIndex != -1) {
                    quitMessage = message.substring(quitIndex + 6).trim();
                }

                if (!quitter.equalsIgnoreCase(nickname)) {
                    logEvent(String.format("[%s] *** %s quit (%s)", LocalDateTime.now(), quitter, quitMessage));
                }
            }
        }

        in.close();
        out.close();
        socket.close();
    }

    private static void write(String command, String message) {
        String serverMessage = command + " " + message;
        System.out.println(serverMessage);
        out.print(serverMessage + "\r\n");
        out.flush();
    }

    private static void logEvent(String event, String channel) {
        String channelName = channel.replace("#", "");
        String logFilePath = LOG_DIR + "/log_" + channelName + ".txt";

        String timestamp = "[" + LocalDateTime.now() + "]";
        String logLine = String.format("%s %s", timestamp, event);

        try (FileWriter fw = new FileWriter(logFilePath, true)) {
            fw.write(logLine + "\n");
        } catch (IOException e) {
            System.err.println("Error writing the log for channel " + channelName + ": " + e.getMessage());
        }
    }

    private static void handleLastNCommand(String sender, int n, String channel) {
        String sanitizedChannel = channel.replace("#", "");
        String logFilePath = LOG_DIR + "/log_" + sanitizedChannel + ".txt";

        LinkedList<String> allLines = new LinkedList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                allLines.add(line);
            }

            int available = allLines.size();
            List<String> messagesToSend;
            if (n >= available) {
                messagesToSend = allLines;
                write("PRIVMSG", sender + " :Warning: Requested " + n + " messages, but only " + available + " are available.");
            } else {
                messagesToSend = allLines.subList(available - n, available);
            }

            StringBuilder combined = new StringBuilder("\n");
            for (String logLine : messagesToSend) {
                combined.append(logLine).append("\n");
            }

            write("PRIVMSG", sender + " :" + combined.toString().trim());

        } catch (IOException e) {
            write("PRIVMSG", sender + " :Could not read logs for channel " + channel);
        }
    }


    public static void main(String[] args) throws IOException {
        connect();
    }
}
