package ServClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class ChatClient extends JFrame {
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    
    // GUI Components
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton fileButton;
    
    private String username;

    public ChatClient() {
        // Setup GUI
        setTitle("Network Chat Client");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Initialize components
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        
        messageField = new JTextField();
        sendButton = new JButton("Send");
        fileButton = new JButton("Send File");
        
        // Layout
        setLayout(new BorderLayout());
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(messageField, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(sendButton);
        buttonPanel.add(fileButton);
        
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        
        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
        
        // Event Listeners
        sendButton.addActionListener(e -> sendMessage());
        fileButton.addActionListener(e -> sendFile());
        messageField.addActionListener(e -> sendMessage());
        
        // Prompt for username
        username = JOptionPane.showInputDialog("Entrez votre nom d'utilisateur :");
        
        // Connect to server
        connectToServer();
    }
    
    private void connectToServer() {
        try {
            socket = new Socket("localhost", 4000);  // Port 4000 (same as server)
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
            
            // Send username
            output.println(username);
            
            // Start message listener thread
            new Thread(this::listenForMessages).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Impossible de se connecter au serveur");
            e.printStackTrace();
        }
    }
    
    private void sendMessage() {
        String message = messageField.getText();
        if (message.equalsIgnoreCase("exit")) {
            output.println("exit");  // Send exit message to the server
            System.exit(0);  // Close the application
        } else if (!message.isEmpty()) {
            output.println(username + ": " + message);  // Send regular message
            messageField.setText("");  // Clear message field
        } else {
            JOptionPane.showMessageDialog(this, "Le message ne peut pas être vide.");
        }
    }

    
    private void sendFile() {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // Send file transfer command
                output.println("FILE:" + selectedFile.getName() + ":" + selectedFile.length());
                
                // Send file content
                try (FileInputStream fis = new FileInputStream(selectedFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        socket.getOutputStream().write(buffer, 0, bytesRead);
                    }
                }
                
                chatArea.append("Fichier envoyé : " + selectedFile.getName() + "\n");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Erreur lors de l'envoi du fichier");
                e.printStackTrace();
            }
        }
    }
    
    private void listenForMessages() {
        try {
            String message;
            while ((message = input.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    if (finalMessage.startsWith("FILE:")) {
                        String[] parts = finalMessage.split(":");
                        if (parts.length >= 3) {
                            String fileName = parts[1];
                            int fileSize = Integer.parseInt(parts[2]);
                            receiveFile(fileName, fileSize);
                        }
                    } else {
                        chatArea.append(finalMessage + "\n");
                    }
                });
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Connexion perdue");
            e.printStackTrace();
        }
    }
    
    private void receiveFile(String fileName, int fileSize) {
        try {
            File downloadDir = new File("downloads");
            if (!downloadDir.exists()) downloadDir.mkdir();
            
            File receivedFile = new File(downloadDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(receivedFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                int totalBytesRead = 0;
                
                while (totalBytesRead < fileSize && 
                       (bytesRead = socket.getInputStream().read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
            }
            
            chatArea.append("Fichier reçu : " + fileName + "\n");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur lors de la réception du fichier");
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatClient().setVisible(true);
        });
    }
}
