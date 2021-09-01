import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private final static HashMap<String, String> allAccounts;
    private final static HashMap<String, ArrayList<String>> messages;
    private final static HashMap<String, HashMap<String, String>> hosts;
    private final static HashMap<String, HashMap<String, Integer>> ports;
    private static ServerSocket serverSocket;
    private static String loggedIn;
    private static int mainPort;
    private static String mainHost;
    private static volatile boolean pause;

    static {
        allAccounts = new HashMap<>();
        messages = new HashMap<>();
        hosts = new HashMap<>();
        ports = new HashMap<>();
        mainPort = -37;
    }

    public static void main(String[] args) {
        start();
    }

    private static void start() {
        String input = scanner.nextLine();
        while (!input.equals("exit")) {
            handleInput(input);
            input = scanner.nextLine();
        }
    }

    private static void handleInput(String input) {
        if (input.startsWith("userconfig"))
            handleUserConfig(input);
        else if (input.startsWith("portconfig"))
            handlePortConfig(input);
        else if (input.startsWith("contactconfig"))
            handleContactConfig(input);
        else if (input.startsWith("show"))
            handleShow(input);
        else if (input.startsWith("focus"))
            handleFocus(input);
        else if (input.startsWith("send"))
            handleSend(input);
        else System.out.println("invalid command!");
    }

    private static void handleUserConfig(String input) {
        if (input.contains("--create")) {
            if (input.contains("--password") && input.contains("--username"))
                System.out.println(createUser(input));
            else
                System.out.println("invalid command!");
        } else if (input.contains("--login")) {
            if (input.contains("--password") && input.contains("--username"))
                System.out.println(loginUser(input));
            else
                System.out.println("invalid command!");
        } else if (input.contains("--logout"))
            System.out.println(logout());
    }

    private static String logout() {
        if (loggedIn == null) return "you must login before logging out";
        loggedIn = null;
        mainHost = null;
        mainPort = -37;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        return "success";
    }

    private static String loginUser(String input) {
        String[] parts = input.split(" --");
        String username = "";
        String password = "";
        for (String part : parts) {
            if (part.startsWith("username")) {
                String[] usernames = part.split(" ");
                username = usernames[1];
            }
            else if (part.startsWith("password")) {
                String[] passwords = part.split(" ");
                password = passwords[1];
            }
        }
        if (!allAccounts.containsKey(username))
            return "user not found";
        if (!allAccounts.get(username).equals(password))
            return "incorrect password";
        loggedIn = username;
        return "success";
    }

    private static String createUser(String input) {
        String[] parts = input.split(" --");
        String username = "";
        String password = "";
        for (String part : parts) {
            if (part.startsWith("password")) {
                String[] passwords = part.split(" ");
                password = passwords[1];
            }
            else if (part.startsWith("username")) {
                String[] usernames = part.split(" ");
                username = usernames[1];
            }
        }
        if (allAccounts.containsKey(username) || !username.matches("[A-Za-z0-9_\\-]+"))
            return "this username is unavailable";
        allAccounts.put(username, password);
        ports.put(username, new HashMap<>());
        hosts.put(username, new HashMap<>());
        messages.put(username, new ArrayList<>());
        return "success";
    }

    private static void handlePortConfig(String input) {
        if (input.contains("--listen") && !input.contains("--rebind") && !input.contains("--close"))
            System.out.println(handleListen(input));
        else if (input.contains("--rebind"))
            System.out.println(handleRebind(input));
        else if (input.contains("--close"))
            System.out.println(handleClose(input));
    }

    private static String handleClose(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String[] parts = input.split(" --");
        String number = "";
        for (String part : parts)
            if (part.startsWith("port") && !part.startsWith("portconfig"))
                number = part.split(" ")[1];
        if (!number.matches("[0-9]+"))
            return "invalid command!";
        try {
            if (serverSocket.getLocalPort() != Integer.parseInt(number) || serverSocket == null)
                return "the port you specified was not open";
            serverSocket.close();
            serverSocket = null;
            return "success";
        } catch (IOException e) {
            return "an error occurred";
        }
    }

    private static String handleRebind(String input) {
        String[] parts = input.split(" --");
        String number = "";
        if (loggedIn == null)
            return "you must login to access this feature";
        for (String part : parts)
            if (part.startsWith("port") && !part.startsWith("portconfig"))
                number = part.split(" ")[1];
        if (!number.matches("[0-9]+"))
            return "invalid command!";
        try {
            if (serverSocket != null)
                serverSocket.close();
            serverSocket = new ServerSocket(Integer.parseInt(number), 1, InetAddress.getByName("localhost"));
            createThread();
            return "success";
        } catch (IOException e) {
            return "an error occurred";
        }
    }

    private static String handleListen(String input) {
        String[] parts = input.split(" --");
        if (loggedIn == null)
            return "you must login to access this feature";
        String number = "";
        for (String part : parts)
            if (part.startsWith("port") && !part.startsWith("portconfig")) {
                number = part.split(" ")[1];
            }
        if (!number.matches("[0-9]+"))
            return "invalid command!";
        if (serverSocket != null)
            return "the port is already set";
        try {
            serverSocket = new ServerSocket(Integer.parseInt(number), 1, InetAddress.getByName("localhost"));
            createThread();
            return "success";
        } catch (IOException e) {
            return "an error occurred";
        }
    }

    private static void createThread() {
        new Thread(() -> {
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    pause = true;
                    DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                    handleMessage(dataInputStream.readUTF(), socket.getInetAddress().getHostAddress());
                    dataInputStream.close();
                    socket.close();
                } catch (Exception e) {
                    break;
                }
            }
        }).start();
    }

    private static void handleMessage(String message, String host) {
        String number = message.split(":")[0];
        message = message.replaceFirst(number + ":", "");
        messages.get(loggedIn).add(message);
        String username = message.split(" -> ")[0];
        hosts.get(loggedIn).put(username, host);
        ports.get(loggedIn).put(username, Integer.parseInt(number));
        pause = false;
    }

    private static void handleContactConfig(String input) {
        if (input.contains("--link")) {
            if (input.contains("--host") && input.contains("--port") && input.contains("--username"))
                System.out.println(handleLink(input));
            else System.out.println("invalid command!");
        } else System.out.println("invalid command!");
    }


    private static String sendMessage(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String[] parts = input.split(" --");
        String message = "";
        String host = "";
        String port = "";
        for (String part : parts) {
            if (part.startsWith("message")) {
                part = part.replaceAll("\"", "");
                message = part.substring(8);
                message = message.trim();
            }
            else if (part.startsWith("port"))
                port = part.split(" ")[1];
            else if (part.startsWith("host"))
                host = part.split(" ")[1];
        }
        return finalMessage(message, host, port);
    }

    private static String finalMessage(String message, String host, String port) {
        if (!port.matches("\\d+"))
            return "could not send message";
        if (host == null)
            return "could not send message";
        try {
            Socket socket = new Socket(host, Integer.parseInt(port));
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
            dataOutputStream.writeUTF(serverSocket.getLocalPort() + ":" + loggedIn + " -> " + message);
            dataOutputStream.flush();
            dataOutputStream.close();
            socket.close();
            return "success";
        } catch (Exception e) {
            return "could not send message";
        }
    }

    private static String handleLink(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String host = "";
        String username = "";
        String[] parts = input.split(" --");
        String number = "";
        for (String part : parts)
            if (part.startsWith("port"))
                number = part.split(" ")[1];
            else if (part.startsWith("host"))
                host = part.split(" ")[1];
            else if (part.startsWith("username"))
                username = part.split(" ")[1];
        if (!number.matches("[0-9]+"))
            return "invalid command!";
        hosts.get(loggedIn).put(username, host);
        ports.get(loggedIn).put(username, Integer.parseInt(number));
        return "success";
    }

    private static void handleShow(String input) {
        if (input.contains("--contacts"))
            System.out.println(handleContacts());
        else if (input.contains("--contact"))
            System.out.println(ShowOneContact(input));
        else if (input.contains("--senders") && !input.contains("--count"))
            System.out.println(showSenders());
        else if (input.contains("--count") && input.contains("--senders"))
            System.out.println(countSenders());
        else if (input.contains("--count") && input.contains("--messages"))
            System.out.println(countMessages());
        else if (input.contains("--count") && input.contains("--messages") && input.contains("--from"))
            System.out.println(countForUsername(input));
        else if (input.contains("--messages") && input.contains("--from"))
            System.out.println(messagesFromUsername(input));
        else if (input.contains("--messages"))
            System.out.println(showMessages());
        else System.out.println("invalid command!");
    }

    public static String showMessages() {
        while (pause) Thread.onSpinWait();
        if (loggedIn == null)
            return "you must login to access this feature";
        ArrayList<String> myMessages = messages.get(loggedIn);
        if (myMessages.isEmpty())
            return "no item is available";
        StringBuilder stringBuilder = new StringBuilder();
        for (String message : myMessages)
            stringBuilder.append(message).append("\n");
        return stringBuilder.substring(0, stringBuilder.length() - 1);
    }

    public static String messagesFromUsername(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String[] parts = input.split(" --");
        String username = "";
        while (pause) Thread.onSpinWait();
        for (String part : parts)
            if (part.startsWith("from"))
                username = part.split(" ")[1];
        ArrayList<String> allMessages = new ArrayList<>();
        for (String message : messages.get(loggedIn))
            if (message.startsWith(username)) allMessages.add(message.substring(username.length() + 4));
        if (allMessages.isEmpty())
            return "no item is available";
        StringBuilder stringBuilder = new StringBuilder();
        for (String message : allMessages)
            stringBuilder.append(message).append("\n");
        return stringBuilder.substring(0, stringBuilder.length() - 1);
    }

    private static String countForUsername(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String username = "";
        String[] parts = input.split(" --");
        for (String part : parts)
            if (part.startsWith("from"))
                username = part.split(" ")[1];
        while (pause) Thread.onSpinWait();
        ArrayList<String> allMessages = new ArrayList<>();
        for (String message : messages.get(loggedIn))
            if (message.startsWith(username)) allMessages.add(message.substring(username.length() + 4));
        return String.valueOf(allMessages.size());
    }

    private static String countMessages() {
        if (loggedIn == null)
            return "you must login to access this feature";
        while (pause) Thread.onSpinWait();
        ArrayList<String> myMessages = messages.get(loggedIn);
        return String.valueOf(myMessages.size());
    }

    private static String countSenders() {
        if (loggedIn == null)
            return "you must login to access this feature";
        while (pause) Thread.onSpinWait();
        ArrayList<String> myMessages = messages.get(loggedIn);
        HashSet<String> senders = new HashSet<>();
        for (String message : myMessages)
            senders.add(message.split(" -> ")[0]);
        return String.valueOf(senders.size());
    }

    private static String showSenders() {
        while (pause) Thread.onSpinWait();
        if (loggedIn == null)
            return "you must login to access this feature";
        ArrayList<String> myMessages = messages.get(loggedIn);
        if (myMessages.isEmpty())
            return "no item is available";
        HashSet<String> senders = new HashSet<>();
        for (String message : myMessages)
            senders.add(message.split(" -> ")[0]);
        StringBuilder stringBuilder = new StringBuilder();
        for (String sender : senders)
            stringBuilder.append(sender).append("\n");
        return stringBuilder.substring(0, stringBuilder.length() - 1);
    }

    private static String ShowOneContact(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String username = "";
        String[] parts = input.split(" --");
        for (String part : parts)
            if (part.startsWith("contact"))
                username = part.split(" ")[1];
        while (pause) Thread.onSpinWait();
        if (!hosts.get(loggedIn).containsKey(username))
            return "no contact with such username was found";
        return hosts.get(loggedIn).get(username) + ":" + ports.get(loggedIn).get(username);
    }

    private static String handleContacts() {
        if (loggedIn == null)
            return "you must login to access this feature";
        while (pause) Thread.onSpinWait();
        HashMap<String, String> thisHosts = hosts.get(loggedIn);
        HashMap<String, Integer> thisPorts = ports.get(loggedIn);
        if (thisHosts.isEmpty())
            return "no item is available";
        StringBuilder stringBuilder = new StringBuilder();
        for (String username : hosts.keySet())
            stringBuilder.append(username).append(" -> ").append(thisHosts.get(username)).append(":").append(thisPorts.get(username)).append("\n");
        return stringBuilder.substring(0, stringBuilder.length() - 1);
    }

    private static void handleFocus(String input) {
        if (input.contains("--start") && input.contains("--host") && input.contains("--port"))
            System.out.println(focusHostPort(input));
        else if (input.contains("--start") && input.contains("--host"))
            System.out.println(focusHost(input));
        else if (input.contains("--port"))
            System.out.println(focusPort(input));
        else if (input.contains("--username"))
            System.out.println(focusUsername(input));
        else if (input.contains("--stop"))
            System.out.println(stopFocus());
        else System.out.println("invalid command!");
    }

    private static String stopFocus() {
        if (loggedIn == null)
            return "you must login to access this feature";
        mainHost = null;
        mainPort = -37;
        return "success";
    }

    private static String focusUsername(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String username = "";
        String[] parts = input.split(" --");
        for (String part : parts)
            if (part.startsWith("username"))
                username = part.split(" ")[1];
        if (!hosts.get(loggedIn).containsKey(username))
            return "no contact with such username was found";
        mainPort = ports.get(loggedIn).get(username);
        mainHost = hosts.get(loggedIn).get(username);
        return "success";
    }

    private static String focusHostPort(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String port = "";
        String host = "";
        String[] parts = input.split(" --");
        for (String part : parts)
            if (part.startsWith("port"))
                port = part.split(" ")[1];
            else if (part.startsWith("host"))
                host = part.split(" ")[1];
        if (!port.matches("\\d+"))
            return "invalid command!";
        mainHost = host;
        mainPort = Integer.parseInt(port);
        return "success";
    }

    private static String focusPort(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String port = "";
        String[] parts = input.split(" --");
        for (String part : parts)
            if (part.startsWith("port"))
                port = part.split(" ")[1];
        if (!port.matches("\\d+"))
            return "invalid command!";
        if (mainHost == null)
            return "you must focus on a host before using this command";
        mainPort = Integer.parseInt(port);
        return "success";
    }

    private static String focusHost(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String host = "";
        String[] parts = input.split(" --");
        for (String part : parts)
            if (part.startsWith("host"))
                host = part.split(" ")[1];
        mainHost = host;
        mainPort = -37;
        return "success";
    }

    private static void handleSend(String input) {
        if (input.contains("--message") && input.contains("--host")) {
            if (input.contains("--port"))
                System.out.println(sendMessage(input));
            else System.out.println("invalid command!");
        } else if (input.contains("--message") && input.contains("--username"))
            System.out.println(sendPMToUsers(input));
        else if (input.contains("--port") && input.contains("--message"))
            System.out.println(sendWithPort(input));
        else if (input.contains("--message"))
            System.out.println(sendWithFocus(input));
        else System.out.println("invalid command!");
    }

    private static String sendWithFocus(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String[] parts = input.split(" --");
        String message = "";
        for (String part : parts) {
            if (part.startsWith("message")) {
                part = part.replaceAll("\"", "");
                message = part.substring(8);
                message = message.trim();
            }
        }
        return finalMessage(message, mainHost, String.valueOf(mainPort));
    }

    private static String sendWithPort(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String[] parts = input.split(" --");
        String message = "";
        String port = "";
        for (String part : parts) {
            if (part.startsWith("message")) {
                part = part.replaceAll("\"", "");
                message = part.substring(8);
                message = message.trim();
            }
            else if (part.startsWith("port"))
                port = part.split(" ")[1];
        }
        return finalMessage(message, mainHost, port);
    }

    private static String sendPMToUsers(String input) {
        if (loggedIn == null)
            return "you must login to access this feature";
        String[] parts = input.split(" --");
        String message = "";
        String username = "";
        for (String part : parts) {
            if (part.startsWith("message")) {
                part = part.replaceAll("\"", "");
                message = part.substring(8);
                message = message.trim();
            }
            else if (part.startsWith("username"))
                username = part.split(" ")[1];
        }
        if (!hosts.get(loggedIn).containsKey(username))
            return "no contact with such username was found";
        return finalMessage(message, hosts.get(loggedIn).get(username), String.valueOf(ports.get(loggedIn).get(username)));
    }
}
