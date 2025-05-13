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
    private static String NICKNAME = "IRCLogBotAUA";
    public static String USERNAME = "IRCLogBotAUA";
    public static String REALNAME = "IRCLogBotAUA";
    private static final Map<String, List<String>> logs = new ConcurrentHashMap<>();
    private static final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();
    private static final ExecutorService logPool = Executors.newSingleThreadExecutor();

    private static final Map<String, String> pendingRequests = new ConcurrentHashMap<>();

    public static void connect() throws IOException {
        Scanner sc = new Scanner(System.in);

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket socket = (SSLSocket) factory.createSocket("irc.oftc.net", 6697);

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
                logPool.shutdown();
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

        while(true) {
            try {
                String message = messageQueue.take();
                System.out.println("<<< " + message);

                if (message.startsWith("PING")) {
                    write("PONG", message.substring(5));
                    continue;
                }

                if (message.contains("INVITE")) {
                    Matcher matcher = Pattern.compile("(#\\S+)").matcher(message);

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
            if(!logLine.contains(NICKNAME)) {
                fw.write(logLine + "\n");
            }
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

        if (msgText.contains("VERSION")) {
            write("NOTICE", sender + " :\u0001VERSION IRCLogBot 1.0 Java\u0001");
            return;
        }

        if (target.startsWith("#")) {
            String event = String.format("<%s>: %s", sender, msgText);
            logPool.submit(() -> logEvent(event, target));
        }

        if (!isPrivate && msgText.toLowerCase().contains(NICKNAME.toLowerCase())) {
            int fileLineCount = countLinesInLogFile(target);
            int totalIndex = fileLineCount + logs.getOrDefault(target, List.of()).size();
            pendingRequests.put(sender, target + "|" + totalIndex);
            write("PRIVMSG", sender + " :How many messages do you want?");
            return;
        }

        if (isPrivate && !pendingRequests.containsKey(sender)) {
            write("PRIVMSG", sender + " :Please mention me in a channel before sending your request");
        }

        if (isPrivate && pendingRequests.containsKey(sender)) {
            threadPool.submit(() -> {
                try {
                    int count = Integer.parseInt(msgText.trim());
                    String[] args = pendingRequests.remove(sender).split("\\|");
                    String channel = args[0];
                    int mentionIndex = Integer.parseInt(args[1]);
                    handleLastNCommand(sender, count, channel, mentionIndex);

                } catch (NumberFormatException e) {
                    write("PRIVMSG", sender + " :Invalid number format.");
                }
            });
        }
    }

    private static void handleLastNCommand(String sender, int n, String channel, int index) {
        String channelName = channel.replace("#", "");
        String logFilePath = LOG_DIR + "/log_" + channelName + ".txt";

        LinkedList<String> allLines = new LinkedList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                allLines.add(line);
            }
            int start = Math.max(0, index - n);
            int end = index;
            List<String> messagesToSend = allLines.subList(start, end);

            int available = end-start+1;
            if (n >= available) {
                write("PRIVMSG", sender + " :Warning: Requested " + n + " messages, but only " + available + " are available.");
            }
            write("PRIVMSG", sender + " :Here are your last " + messagesToSend.size() + " messages:");
            for (String logLine : messagesToSend) {
                write("PRIVMSG", sender + " :" + logLine);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (IOException e) {
            write("PRIVMSG", sender + " :Could not read logs for channel " + channel);
        }
    }

    private static int countLinesInLogFile(String channel) {
        String channelName = channel.replace("#", "");
        String logFilePath = LOG_DIR + "/log_" + channelName + ".txt";
        int count = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(logFilePath))) {
            while (br.readLine() != null) {
                count++;
            }
        } catch (IOException e) {
        }
        return count;
    }

    public static void main(String[] args) throws IOException {
        connect();
    }
}
