package bgu.spl.net.srv;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class Database {
    private static class DatabaseHolder {
        private static final Database instance = new Database();
    }

    public static Database getInstance() {
        return DatabaseHolder.instance;
    }

    private final String sqlHost = "127.0.0.1";
    private final int sqlPort = 7778;
    
    private final ConcurrentHashMap<String, Integer> activeUsers = new ConcurrentHashMap<>();

    private Database() {
    }

    private synchronized String executeSQL(String query) {
        try (Socket socket = new Socket(sqlHost, sqlPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            
            out.print(query + "\u0000");
            out.flush();

            StringBuilder response = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\0') break;
                response.append((char) c);
            }
            return response.toString();
        } catch (Exception e) {
            return "ERROR";
        }
    }

    public boolean verifyPassword(String username, String password) {
        String query = "SELECT password FROM users WHERE username='" + username + "'";
        String result = executeSQL(query);
        
        if (result.startsWith("SUCCESS") && result.length() > 8) {
            return result.contains("'" + password + "'");
        }
        return false;
    }

    public boolean login(String username, int connectionId) {
        if (activeUsers.containsKey(username)) return false;

        String query = "INSERT INTO logins (username, login_time) VALUES ('" + username + "', datetime('now'))";
        executeSQL(query);

        activeUsers.put(username, connectionId);
        return true;
    }

    public void logout(String username) {
        if (username != null && activeUsers.containsKey(username)) {
            String query = "UPDATE logins SET logout_time=datetime('now') " +
                           "WHERE id = (SELECT id FROM logins WHERE username='" + username + "' " +
                           "AND logout_time IS NULL ORDER BY id DESC LIMIT 1)";
            executeSQL(query);

            activeUsers.remove(username);
        }
    }

    public void logFileUpload(String username, String filename, String gameChannel) {
        filename = filename.replace("'", ""); 
        String query = "INSERT INTO files (username, filename, upload_time, game_channel) " +
                       "VALUES ('" + username + "', '" + filename + "', datetime('now'), '" + gameChannel + "')";
        executeSQL(query);
    }

    public boolean isLoggedIn(String username) {
        return activeUsers.containsKey(username);
    }

    public void printReport() {
        String usersResult = executeSQL("SELECT username FROM users");
        String loginsResult = executeSQL("SELECT username, login_time, logout_time FROM logins");
        String filesResult = executeSQL("SELECT username, filename, upload_time, game_channel FROM files");

        System.out.println("Server Report:");
        System.out.println("Registered Users: " + usersResult);
        System.out.println("Login History: " + loginsResult);
        System.out.println("Uploaded Files: " + filesResult);
    }
}