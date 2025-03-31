package ServClient;

import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 800;
    private static HashSet<ClientHandler> clients = new HashSet<>();
    
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur d�marr� sur le port " + PORT);
            System.out.println("En attente de connexions...");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nouvelle connexion de : " + clientSocket.getInetAddress().getHostAddress());
                
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur du serveur: " + e.getMessage());
        }
    }
    
    // M�thode pour diffuser un message � tous les clients connect�s
    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }
    
    // M�thode pour diffuser un fichier � tous les clients
    public static void broadcastFile(String fileName, byte[] fileData, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendFile(fileName, fileData);
            }
        }
    }
    
    // M�thode pour retirer un client de la liste des clients connect�s
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client d�connect�. Nombre de clients actifs : " + clients.size());
    }
    
    // Classe interne pour g�rer chaque client dans un thread s�par�
    static class ClientHandler implements Runnable {
        private Socket socket;
        private BufferedReader reader;
        private PrintWriter writer;
        private DataInputStream dataInputStream;
        private DataOutputStream dataOutputStream;
        private String clientName;
        
        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        }
        
        @Override
        public void run() {
            try {
                // Demander le nom du client
                writer.println("Entrez votre nom :");
                clientName = reader.readLine();
                broadcast("MSG:Serveur: " + clientName + " a rejoint le chat.", this);
                
                String message;
                while ((message = reader.readLine()) != null) {
                    if (message.equals("exit")) {
                        break;
                    } else if (message.startsWith("FILE:")) {
                        // Format: FILE:nom_fichier:taille
                        String[] parts = message.split(":", 3);
                        String fileName = parts[1];
                        int fileSize = Integer.parseInt(parts[2]);
                        
                        // Recevoir le fichier
                        byte[] fileData = new byte[fileSize];
                        dataInputStream.readFully(fileData);
                        
                        System.out.println("Fichier re�u de " + clientName + ": " + fileName);
                        broadcast("MSG:Serveur: " + clientName + " a partag� un fichier: " + fileName, null);
                        
                        // Diffuser le fichier � tous les autres clients
                        broadcastFile(fileName, fileData, this);
                    } else if (message.startsWith("MSG:")) {
                        System.out.println(clientName + ": " + message.substring(4));
                        broadcast("MSG:" + clientName + ": " + message.substring(4), this);
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur avec le client " + clientName + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                broadcast("MSG:Serveur: " + clientName + " a quitt� le chat.", this);
                removeClient(this);
            }
        }
        
        // Envoyer un message texte � ce client
        public void sendMessage(String message) {
            writer.println(message);
        }
        
        // Envoyer un fichier � ce client
        public void sendFile(String fileName, byte[] fileData) {
            try {
                // Envoyer d'abord les m�tadonn�es du fichier
                writer.println("FILE:" + fileName + ":" + fileData.length);
                
                // Puis envoyer les donn�es du fichier
                dataOutputStream.write(fileData);
                dataOutputStream.flush();
            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi du fichier � " + clientName + ": " + e.getMessage());
            }
        }
    }
}
