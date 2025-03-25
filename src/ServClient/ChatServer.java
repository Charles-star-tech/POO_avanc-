package ServClient;

import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Set;

public class ChatServer {
    private static final int PORT = 12041; // Port d'écoute
    private static Set<ClientHandler> clientHandlers = new HashSet<>(); // Set des handlers clients

    public static void main(String[] args) {
        System.out.println("Serveur démarré...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                new ClientHandler(serverSocket.accept()).start(); 
                // Accepte la connexion d'un client et crée un handler pour chaque client
            }
        } catch (IOException e) {
            System.err.println("Erreur du serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientStatus = "En ligne"; // Par défaut
        private OutputStream outputStream; // Flux pour envoyer des fichiers

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                outputStream = socket.getOutputStream(); // Flux de sortie pour envoyer des fichiers

                synchronized (clientHandlers) {
                    clientHandlers.add(this); // Ajoute le client à la liste des handlers
                }

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("MESSAGE:")) {
                        // Envoi d'un message texte
                        handleMessage(message.substring(8)); // Enlève le préfixe "MESSAGE:"
                    } else if (message.startsWith("FICHIER:")) {
                        // Envoi d'un fichier
                        handleFile(message.substring(8)); // Enlève le préfixe "FICHIER:"
                    } else if (message.startsWith("STATUT:")) {
                        // Mise à jour du statut de l'utilisateur
                        handleStatus(message.substring(7)); // Enlève le préfixe "STATUT:"
                    } else if (message.equalsIgnoreCase("exit")) {
                        break; // Terminer la connexion si "exit"
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur de communication avec le client: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Fermeture des ressources et suppression du client de la liste
                try {
                    if (socket != null) {
                        socket.close();
                    }
                } catch (IOException e) {
                    System.err.println("Erreur de fermeture du socket: " + e.getMessage());
                }

                synchronized (clientHandlers) {
                    clientHandlers.remove(this); // Retire le client de la liste
                }

                System.out.println("Client déconnecté");
            }
        }

        // Gère l'envoi d'un message texte à tous les clients
        private void handleMessage(String message) {
            synchronized (clientHandlers) {
                for (ClientHandler handler : clientHandlers) {
                    if (handler != this) { // Ne pas envoyer à soi-même
                        handler.out.println("MESSAGE: " + message); // Envoi à tous les autres clients
                    }
                }
            }
        }

        private void handleFile(String fileName) {
            synchronized (clientHandlers) {
                for (ClientHandler handler : clientHandlers) {
                    if (handler != this) {
                        handler.out.println("FICHIER:" + fileName); // Préfixe pour indiquer qu'un fichier va suivre
                    }
                }
            }

            // Réception et enregistrement du fichier
            byte[] buffer = new byte[4096];
            int bytesRead;
            File file = new File(fileName); // Suppose que le fichier est dans le répertoire courant
            try (FileOutputStream fileOutputStream = new FileOutputStream(file);
                 InputStream inputStream = socket.getInputStream()) {

                // Lecture des données binaires et écriture dans le fichier
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                fileOutputStream.flush();
                System.out.println("Fichier reçu: " + fileName);
            } catch (IOException e) {
                System.err.println("Erreur lors de la réception du fichier: " + e.getMessage());
            }
        }

        private void sendFileToClients(String fileName) {
            synchronized (clientHandlers) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                File file = new File(fileName);
                if (file.exists()) {
                    try (FileInputStream fileInputStream = new FileInputStream(file)) {
                        for (ClientHandler handler : clientHandlers) {
                            if (handler != this) {
                                handler.out.println("FICHIER:" + fileName); // Indique qu'un fichier va être envoyé
                            }
                        }

                        // Envoi du fichier à tous les clients
                        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                            for (ClientHandler handler : clientHandlers) {
                                if (handler != this) {
                                    handler.outputStream.write(buffer, 0, bytesRead); // Envoi des données binaires
                                }
                            }
                        }
                        System.out.println("Fichier envoyé: " + fileName);
                    } catch (IOException e) {
                        System.err.println("Erreur lors de l'envoi du fichier: " + e.getMessage());
                    }
                } else {
                    System.out.println("Le fichier " + fileName + " n'existe pas.");
                }
            }
        }

        // Gère la mise à jour du statut de l'utilisateur
        private void handleStatus(String status) {
            this.clientStatus = status;
            synchronized (clientHandlers) {
                for (ClientHandler handler : clientHandlers) {
                    if (handler != this) {
                        handler.out.println("STATUT: " + clientStatus); // Envoie le statut aux autres
                    }
                }
            }
        }
    }
}
