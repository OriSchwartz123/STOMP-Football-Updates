package bgu.spl.net.api;

import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.Database;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;
    private String currentUser = null;

    private static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> topicSubscribers = new ConcurrentHashMap<>();
    private static final AtomicInteger messageIdCounter = new AtomicInteger(0);
    private final Map<String, String> clientSubscriptions = new ConcurrentHashMap<>();

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(String message) {
        List<String> parts = parseMessage(message);
        if (parts.isEmpty()) return;

        String command = parts.remove(0);
        String body = parts.remove(parts.size() - 1);

        Map<String, String> headers = new ConcurrentHashMap<>();
        try {
            for (String line : parts) {
                int splitAt = line.indexOf(':');
                if (splitAt != -1) {
                    headers.put(line.substring(0, splitAt), line.substring(splitAt + 1));
                }
            }

            switch (command) {
                case "CONNECT":
                    handleConnect(headers);
                    break;
                case "SUBSCRIBE":
                    handleSubscribe(headers);
                    break;
                case "UNSUBSCRIBE":
                    handleUnsubscribe(headers);
                    break;
                case "SEND":
                    handleSend(headers, body);
                    break;
                case "DISCONNECT":
                    handleDisconnect(headers);
                    break;
                default:
                    sendError(headers, "Unknown Command", "Command not implemented.");
            }
        } catch (Exception e) {
            sendError(headers, "Processing Error", e.getMessage());
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private void handleConnect(Map<String, String> headers) {
        String login = headers.get("login");
        String passcode = headers.get("passcode");

        if (login == null || passcode == null) {
            sendError(headers, "Malformed Frame", "Missing login or passcode.");
            return;
        }

        Database db = Database.getInstance();

        if (!db.verifyPassword(login, passcode)) {
            sendError(headers, "Wrong password", "Bad credentials or user does not exist.");
            return;
        }

        if (db.isLoggedIn(login)) {
            sendError(headers, "User already logged in", "User " + login + " is already active.");
            return;
        }

        if (db.login(login, connectionId)) {
            currentUser = login;
            String response = "CONNECTED\n" +
                              "version:1.2\n" +
                              "\n" +
                              "\u0000";
            connections.send(connectionId, response);
        } else {
            sendError(headers, "Login Failed", "Could not log in user.");
        }
    }

    private void handleSubscribe(Map<String, String> headers) {
        if (currentUser == null) {
            sendError(headers, "Not Logged In", "You must log in first.");
            return;
        }

        String destination = headers.get("destination");
        String id = headers.get("id");

        if (destination == null || id == null) {
            sendError(headers, "Malformed Frame", "Missing destination or id.");
            return;
        }

        clientSubscriptions.put(id, destination);
        topicSubscribers.computeIfAbsent(destination, k -> new ConcurrentHashMap<>())
                        .put(connectionId, id);

        handleReceipt(headers);
    }

    private void handleUnsubscribe(Map<String, String> headers) {
        if (currentUser == null) {
            sendError(headers, "Not Logged In", "You must log in first.");
            return;
        }

        String id = headers.get("id");
        if (id == null) {
            sendError(headers, "Malformed Frame", "Missing id header.");
            return;
        }

        String topic = clientSubscriptions.remove(id);
        if (topic != null) {
            if (topicSubscribers.containsKey(topic)) {
                topicSubscribers.get(topic).remove(connectionId);
            }
        }
        handleReceipt(headers);
    }

    private void handleSend(Map<String, String> headers, String body) {
        if (currentUser == null) {
            sendError(headers, "Not Logged In", "You must log in first.");
            return;
        }

        String destination = headers.get("destination");
        if (destination == null) {
            sendError(headers, "Malformed Frame", "Missing destination header.");
            return;
        }

        if (!clientSubscriptions.containsValue(destination)) {
            sendError(headers, "Access Denied", "You must subscribe to the topic first.");
            return;
        }

        String fileDesc = body.length() > 50 ? body.substring(0, 50) + "..." : body;
        fileDesc = fileDesc.replace("\n", " ");
        Database.getInstance().logFileUpload(currentUser, fileDesc, destination);

        if (topicSubscribers.containsKey(destination)) {
            Map<Integer, String> subscribers = topicSubscribers.get(destination);
            for (Map.Entry<Integer, String> entry : subscribers.entrySet()) {
                int targetId = entry.getKey();
                String subId = entry.getValue();

                String msgFrame = "MESSAGE\n" +
                                  "subscription:" + subId + "\n" +
                                  "message-id:" + messageIdCounter.getAndIncrement() + "\n" +
                                  "destination:" + destination + "\n" +
                                  "\n" +
                                  body +
                                  "\u0000";

                connections.send(targetId, msgFrame);
            }
        }
        handleReceipt(headers);
    }

    private void handleDisconnect(Map<String, String> headers) {
        String receipt = headers.get("receipt");
        if (receipt != null) {
            String response = "RECEIPT\n" +
                              "receipt-id:" + receipt + "\n" +
                              "\n" +
                              "\u0000";
            connections.send(connectionId, response);
        }
        performLogout();
        shouldTerminate = true;
    }

    private void handleReceipt(Map<String, String> headers) {
        String receiptId = headers.get("receipt");
        if (receiptId != null) {
            String response = "RECEIPT\n" +
                              "receipt-id:" + receiptId + "\n" +
                              "\n" +
                              "\u0000";
            connections.send(connectionId, response);
        }
    }

    private void sendError(Map<String, String> clientHeaders, String message, String description) {
        String receiptId = clientHeaders != null ? clientHeaders.get("receipt") : null;
        StringBuilder sb = new StringBuilder();
        sb.append("ERROR\n");
        if (receiptId != null) {
            sb.append("receipt-id:").append(receiptId).append("\n");
        }
        sb.append("message:").append(message).append("\n");
        sb.append("\n");
        sb.append(description).append("\n");
        sb.append("\u0000");

        connections.send(connectionId, sb.toString());
        performLogout();
        shouldTerminate = true;
    }

    private void performLogout() {
        if (currentUser != null) {
            Database.getInstance().logout(currentUser);
            
            for (Map.Entry<String, String> entry : clientSubscriptions.entrySet()) {
                String topic = entry.getValue();
                if (topicSubscribers.containsKey(topic)) {
                    topicSubscribers.get(topic).remove(connectionId);
                }
            }
            clientSubscriptions.clear();
            currentUser = null;
        }
    }

    private List<String> parseMessage(String message) {
        List<String> answer = new ArrayList<>();
        String[] lines = message.split("\n", -1);
        int i = 0;
        
        while (i < lines.length && lines[i].trim().isEmpty()) {
            i++;
        }
        
        while (i < lines.length) {
            String line = lines[i];
            if (line.length() > 0 && line.charAt(line.length() - 1) == '\r') {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) break;
            answer.add(line);
            i++;
        }
        
        StringBuilder body = new StringBuilder();
        if (i < lines.length) {
            i++; 
            for (int j = i; j < lines.length; j++) {
                body.append(lines[j]);
                if (j < lines.length - 1) body.append("\n");
            }
        }
        answer.add(body.toString().replace("\u0000", ""));
        return answer;
    }
}