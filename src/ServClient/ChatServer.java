package ServClient;

import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 800;
    private static HashSet<ClientHandler> clients = new HashSet<>();
    
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur démarré sur le port " + PORT);
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
    
    // Méthode pour diffuser un message à tous les clients connectés
    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }
    
    // Méthode pour diffuser un fichier à tous les clients
    public static void broadcastFile(String fileName, byte[] fileData, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendFile(fileName, fileData);
            }
        }
    }
    
    // Méthode pour retirer un client de la liste des clients connectés
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client déconnecté. Nombre de clients actifs : " + clients.size());
    }
    
    // Classe interne pour gérer chaque client dans un thread séparé
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
                        
                        System.out.println("Fichier reçu de " + clientName + ": " + fileName);
                        broadcast("MSG:Serveur: " + clientName + " a partagé un fichier: " + fileName, null);
                        
                        // Diffuser le fichier à tous les autres clients
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
                broadcast("MSG:Serveur: " + clientName + " a quitté le chat.", this);
                removeClient(this);
            }
        }
        
        // Envoyer un message texte à ce client
        public void sendMessage(String message) {
            writer.println(message);
        }
        
        // Envoyer un fichier à ce client
        public void sendFile(String fileName, byte[] fileData) {
            try {
                // Envoyer d'abord les métadonnées du fichier
                writer.println("FILE:" + fileName + ":" + fileData.length);
                
                // Puis envoyer les données du fichier
                dataOutputStream.write(fileData);
                dataOutputStream.flush();
            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi du fichier à " + clientName + ": " + e.getMessage());
            }
        }
    }
}
