package ServClient;

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ChatClient {
    private static final String SERVER_ADDRESS = "localhost";  // Adresse du serveur
    private static final int SERVER_PORT = 12041; // Port du serveur
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private JFrame frame;
    private JTextArea textArea;
    private JTextField textField;
    private JButton sendButton;
    private JButton sendFileButton;
    private JButton disconnectButton;
    private JFileChooser fileChooser;

    // Constructeur pour initialiser l'interface graphique et se connecter au serveur
    public ChatClient() {
        try {
            // Connexion au serveur
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.out.println("Erreur de connexion au serveur : " + e.getMessage());
            return;
        }

        // Initialisation de l'interface graphique
        frame = new JFrame("Client de Chat");
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textField = new JTextField();
        sendButton = new JButton("Envoyer");
        sendFileButton = new JButton("Envoyer un fichier");
        disconnectButton = new JButton("D�connexion");
        fileChooser = new JFileChooser();

        // Mise en place du layout de la fen�tre
        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);
        frame.add(textField, BorderLayout.SOUTH);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());
        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);
        buttonPanel.add(disconnectButton);
        frame.add(buttonPanel, BorderLayout.NORTH);

        // Gestion des �v�nements
        sendButton.addActionListener(e -> sendMessage());
        sendFileButton.addActionListener(e -> sendFile());
        disconnectButton.addActionListener(e -> disconnect());

        // Fermer proprement le client lorsque la fen�tre est ferm�e
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(500, 400);
        frame.setVisible(true);

        // D�marrer un thread pour �couter les messages entrants
        new Thread(new IncomingMessageListener()).start();
    }

    // Fonction pour envoyer un message texte
    private void sendMessage() {
        String message = textField.getText();
        if (!message.isEmpty()) {
            if (message.equalsIgnoreCase("EXIT")) {
                out.println("EXIT");
                disconnect();
                return;
            }
            out.println("MESSAGE:" + message); // Pr�fixer pour indiquer qu'il s'agit d'un message texte
            textArea.append("Moi: " + message + "\n");
            textField.setText("");
        }
    }

    // Fonction pour envoyer un fichier
    private void sendFile() {
        int returnValue = fileChooser.showOpenDialog(frame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                // Envoyer le nom du fichier au serveur pour indiquer qu'un fichier va suivre
                out.println("FICHIER:" + file.getName()); // Pr�fixer pour indiquer qu'il s'agit d'un fichier

                // Envoi du fichier au serveur
                sendFileToServer(file);
                textArea.append("Fichier envoy�: " + file.getName() + "\n");
            } catch (IOException e) {
                textArea.append("Erreur lors de l'envoi du fichier: " + e.getMessage() + "\n");
            }
        }
    }

    // Envoie un fichier au serveur via les flux de donn�es
    private void sendFileToServer(File file) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file);
             OutputStream outputStream = socket.getOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }
    }

    // Classe pour �couter les messages entrants
    private class IncomingMessageListener implements Runnable {
        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("MESSAGE:")) {
                        textArea.append("Serveur: " + message.substring(8) + "\n");
                    } else if (message.startsWith("FICHIER:")) {
                        String fileName = message.substring(8);
                        receiveFile(fileName);
                    } else if (message.equalsIgnoreCase("EXIT")) {
                        disconnect();
                    }
                }
            } catch (IOException e) {
                textArea.append("Erreur de communication avec le serveur\n");
            }
        }
    }

    // Fonction pour recevoir un fichier et l'enregistrer localement
    private void receiveFile(String fileName) {
        JFileChooser saveFileChooser = new JFileChooser();
        saveFileChooser.setSelectedFile(new File(fileName));
        int returnValue = saveFileChooser.showSaveDialog(frame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File file = saveFileChooser.getSelectedFile();
            try (InputStream inputStream = socket.getInputStream();
                 FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }
                textArea.append("Fichier re�u et enregistr�: " + file.getName() + "\n");
            } catch (IOException e) {
                textArea.append("Erreur lors de la r�ception du fichier: " + e.getMessage() + "\n");
            }
        }
    }

    // Fonction pour d�connecter le client
    private void disconnect() {
        try {
            socket.close();
            textArea.append("D�connexion du serveur...\n");
            System.exit(0); // Ferme le programme
        } catch (IOException e) {
            textArea.append("Erreur lors de la d�connexion: " + e.getMessage() + "\n");
        }
    }

    // M�thode principale pour d�marrer le client
    public static void main(String[] args) {
        new ChatClient();
    }
}
