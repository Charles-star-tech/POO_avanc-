package ServClient;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 4000;  // Utilisation du même port
    private static ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Chat Server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                threadPool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    // Inner class to handle individual client connections
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader input;
        private PrintWriter output;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // Setup input and output streams
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                output = new PrintWriter(socket.getOutputStream(), true);

                // Prompt for username
                output.println("Enter your username:");
                username = input.readLine();
                clients.put(username, this);
                broadcastMessage(username + " has joined the chat.");

                // Handle client messages
                String message;
                while ((message = input.readLine()) != null) {
                    if (message.equalsIgnoreCase("exit")) {
                        break;  // If message is "exit", break the loop to close the connection
                    }

                    // Check for file transfer command
                    if (message.startsWith("FILE:")) {
                        handleFileTransfer(message);
                    } else {
                        broadcastMessage(username + ": " + message);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error handling client " + username + ": " + e.getMessage());
            } finally {
                // Cleanup when client exits or when error occurs
                if (username != null) {
                    clients.remove(username);
                    broadcastMessage(username + " has left the chat.");
                }
                try {
                    socket.close();  // Close the socket to disconnect the client
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Broadcast message to all connected clients
        private void broadcastMessage(String message) {
            for (ClientHandler client : clients.values()) {
                client.output.println(message);
            }
        }

        // Handle file transfer between clients
        private void handleFileTransfer(String fileCommand) throws IOException {
            // Parse file transfer details
            String[] parts = fileCommand.split(":");
            if (parts.length < 3) return;

            String fileName = parts[1];
            int fileSize = Integer.parseInt(parts[2]);

            // Create file receive buffer
            byte[] buffer = new byte[1024];
            InputStream fileInputStream = socket.getInputStream();
            
            // Broadcast file transfer to other clients
            broadcastMessage(username + " is sending file: " + fileName);

            // Broadcast file to all other clients
            for (ClientHandler client : clients.values()) {
                if (client != this) {
                    // Send file transfer details
                    client.output.println("FILE:" + fileName + ":" + fileSize);
                    
                    // Send actual file content
                    try (FileOutputStream fos = new FileOutputStream(fileName)) {
                        int bytesRead;
                        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        }
    }
}
