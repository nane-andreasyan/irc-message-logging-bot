import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.net.Socket;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class ircBot {

    public static PrintWriter out;
    public static Scanner in;
    private static final String LOG_DIR = "logs";
    private static String NICKNAME = "IRCLogBot";
    public static String USERNAME = "IRCLogBot";
    public static String REALNAME = "IRCLogBot";
    private static final Map<String, List<String>> logs = new ConcurrentHashMap<>();
    private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();
    private static final Map<String, String> pendingRequests = new ConcurrentHashMap<>();

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

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            try {
                write("QUIT", ":Bot shutting down");
                if (out != null) out.close();
                if (in != null) in.close();
                threadPool.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        new Thread(() -> {
            while (in.hasNext()) {
                String message = in.nextLine();
                messageQueue.add(message);
            }
        }).start();

        while (true) {
            try {
                String message = messageQueue.take();
                System.out.println("<<< " + message);

                if (message.startsWith("PING")) {
                    write("PONG", message.substring(5));
                    continue;
                }

                if (message.contains("invite")) {
                    Matcher matcher = Pattern.compile("#([^\\s]+)").matcher(message);
                    if (matcher.find()) {
                        write("JOIN", matcher.group(1));
                    }
                }

                if (message.contains("PRIVMSG")) {
                    handlePrivMsg(message);
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
    private static void handlePrivMsg(String message) {
        String[] parts = message.split(" ", 4);
        if (parts.length < 4) return;

        String sender = parts[0].substring(1, parts[0].indexOf("!"));
        String target = parts[2];
        String msgText = message.split(":", 3)[2].trim();

        boolean isPrivate = target.equalsIgnoreCase(NICKNAME);

        if (target.startsWith("#")) {
            logs.computeIfAbsent(target, k -> new ArrayList<>())
                    .add(String.format("[%s] <%s>: %s", LocalDateTime.now(), sender, msgText));
        }

        if (!isPrivate && msgText.toLowerCase().contains(NICKNAME.toLowerCase())) {
            write("PRIVMSG", sender + " :How many messages do you want?");
            pendingRequests.put(sender, target);
            return;
        }

        if (isPrivate && !pendingRequests.containsKey(sender)) {
            write("PRIVMSG", sender + " :Please mention me in a channel before sending your request");
        }

        if (isPrivate && pendingRequests.containsKey(sender)) {
            threadPool.submit(() -> {
                try {
                    int count = Integer.parseInt(msgText.trim());
                    String channel = pendingRequests.remove(sender);
                    List<String> channelLogs = logs.getOrDefault(channel, new ArrayList<>());
                    int start = Math.max(0, channelLogs.size() - count);
                    for (int i = start; i < channelLogs.size(); i++) {
                        write("PRIVMSG", sender + " :" + channelLogs.get(i));
                    }
                } catch (NumberFormatException e) {
                    write("PRIVMSG", sender + " :Invalid number format.");
                }
            });
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
