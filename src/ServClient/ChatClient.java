package ServClient;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.awt.Desktop;
import java.text.SimpleDateFormat;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.util.Date;

public class ChatClient extends JFrame {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 800;
    private static final String DOWNLOADS_FOLDER = "downloads";
    
    // Couleurs simplifiées pour une meilleure lisibilité
    private static final Color PRIMARY_COLOR = new Color(70, 130, 180); // Bleu acier
    private static final Color SECONDARY_COLOR = new Color(240, 248, 255); // Alice bleu
    private static final Color BUTTON_COLOR = new Color(50, 50, 150); // Bleu foncé pour les boutons
    private static final Color DISCONNECT_BUTTON_COLOR = new Color(180, 50, 50); // Rouge pour déconnexion
    private static final Color TEXT_COLOR = new Color(47, 79, 79); // Gris ardoise foncé
    private static final Font MAIN_FONT = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font HEADER_FONT = new Font("Segoe UI", Font.BOLD, 16);
    
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private String clientName;
    private boolean isConnected = false;
    
    // Composants de l'interface graphique
    private JTextPane chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton sendFileButton;
    private JButton disconnectButton;
    private JPanel inputPanel;
    private JScrollPane scrollPane;
    private JLabel statusLabel;
    private JPanel headerPanel;
    
    // Liste des fichiers reçus pour permettre leur ouverture
    private Map<String, String> receivedFiles = new HashMap<>();
    private JPopupMenu filePopupMenu;
    
    // ExecutorService pour gérer les tâches asynchrones
    private ExecutorService executorService;
    
    public ChatClient() {
        super("MyChatApp");
        
        // Initialiser les composants de l'interface graphique
        initUI();
        
        // Créer le dossier de téléchargements s'il n'existe pas
        createDownloadsFolder();
        
        // Demander le nom d'utilisateur
        clientName = JOptionPane.showInputDialog(this, "Entrez votre nom :", "Connexion", JOptionPane.QUESTION_MESSAGE);
        if (clientName == null || clientName.trim().isEmpty()) {
            clientName = "Utilisateur" + System.currentTimeMillis() % 1000;
        }
        
        // Mettre à jour le titre de la fenêtre avec le nom d'utilisateur
        setTitle("MyChatApp - " + clientName);
        
        // Initialiser l'ExecutorService
        executorService = Executors.newFixedThreadPool(2);
        
        // Se connecter au serveur
        connectToServer();
    }
    
    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        
        // Définir les styles globaux
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Panneau principal avec BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        mainPanel.setBackground(SECONDARY_COLOR);
        
        // Panneau d'entête avec informations de statut
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(PRIMARY_COLOR);
        headerPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        
        JLabel titleLabel = new JLabel("Chat en réseau");
        titleLabel.setFont(HEADER_FONT);
        titleLabel.setForeground(Color.WHITE);
        
        statusLabel = new JLabel("Statut: Déconnecté");
        statusLabel.setFont(MAIN_FONT);
        statusLabel.setForeground(Color.WHITE);
        
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusLabel, BorderLayout.EAST);
        
        // Zone d'affichage des messages avec style amélioré
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setFont(MAIN_FONT);
        chatArea.setBackground(Color.WHITE);
        chatArea.setForeground(TEXT_COLOR);
        
        // Activer les hyperliens dans la zone de chat
        chatArea.setEditorKit(JEditorPane.createEditorKitForContentType("text/html"));
        chatArea.setContentType("text/html");
        chatArea.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String filePath = e.getDescription();
                openFile(filePath);
            }
        });
        
        scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createLineBorder(PRIMARY_COLOR, 1));
        
        // Configuration du menu contextuel pour les fichiers
        filePopupMenu = new JPopupMenu();
        
        // Ajouter un écouteur de clic droit sur la zone de chat
        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    // On utilisera la position du clic pour déterminer si on est sur une mention de fichier
                    showFileOptionsIfAvailable(e.getX(), e.getY());
                }
            }
        });
        
        // Zone de saisie des messages avec style amélioré
        messageField = new JTextField();
        messageField.setFont(MAIN_FONT);
        messageField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(PRIMARY_COLOR, 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        messageField.addActionListener(e -> sendMessage());
        
     // Boutons avec couleurs personnalisées
     // Couleur uniforme pour tous les boutons
        Color buttonColor = new Color(0, 128, 0); // Exemple de couleur verte (vous pouvez ajuster selon vos besoins)

        sendButton = createStyledButton("Envoyer", buttonColor); // Utilisation de la couleur verte pour "Envoyer"
        sendButton.addActionListener(e -> sendMessage());

        sendFileButton = createStyledButton("Fichier", buttonColor); // Utilisation de la même couleur pour "Fichier"
        sendFileButton.setIcon(createFileIcon());
        sendFileButton.addActionListener(e -> selectAndSendFile());

        disconnectButton = createStyledButton("Déconnecter", buttonColor); // Utilisation de la même couleur pour "Déconnecter"
        disconnectButton.addActionListener(e -> {
            if (isConnected) {
                disconnect();
            } else {
                dispose();
                System.exit(0);
            }
        });


        
        // Panel pour les contrôles de saisie
        inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBackground(SECONDARY_COLOR);
        inputPanel.setBorder(new EmptyBorder(10, 0, 0, 0));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setBackground(SECONDARY_COLOR);
        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);
        buttonPanel.add(disconnectButton);
        
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        
        // Ajouter les composants au panneau principal
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);
        
        // Ajouter le panneau principal au cadre
        setContentPane(mainPanel);
        
        // Ajouter gestionnaire pour fermeture de fenêtre
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (isConnected) {
                    disconnect();
                }
                executorService.shutdown();
            }
        });
    }
    
    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(MAIN_FONT);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bgColor.darker(), 1),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)
        ));
        
        // Effet de survol plus subtil
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.darker());
                button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
                button.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });
        
        return button;
    }
    
    private Icon createFileIcon() {
        // Création d'une icône simple pour le bouton fichier
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                
                // Dessiner un document simple
                g2.fillRect(x, y+1, 10, 12);
                g2.fillPolygon(
                    new int[]{x+10, x+15, x+15}, 
                    new int[]{y+1, y+6, y+1}, 
                    3
                );
                
                g2.dispose();
            }
            
            @Override
            public int getIconWidth() {
                return 16;
            }
            
            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }
    
    private void createDownloadsFolder() {
        File downloadsDir = new File(DOWNLOADS_FOLDER);
        if (!downloadsDir.exists()) {
            downloadsDir.mkdir();
        }
    }
    
    private void connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            dataInputStream = new DataInputStream(socket.getInputStream());
            dataOutputStream = new DataOutputStream(socket.getOutputStream());
            
            isConnected = true;
            
            // Modifier le libellé d'état
            statusLabel.setText("Statut: Connecté");
            disconnectButton.setText("Déconnecter");
            
            // Envoyer le nom du client au serveur avant de lire le message de bienvenue
            writer.println(clientName);
            
            // Lire le message de bienvenue du serveur
            String welcomeMessage = reader.readLine();
            if (welcomeMessage != null) {
                appendToChatArea(welcomeMessage, Color.BLUE);
            }
            
            // Lancer un thread pour lire les messages du serveur
            executorService.submit(this::readMessages);
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur de connexion: " + e.getMessage(), 
                                         "Erreur", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private void readMessages() {
        try {
            String message;
            while (isConnected && (message = reader.readLine()) != null) {
                if (message.startsWith("MSG:")) {
                    // C'est un message texte
                    final String textMessage = message.substring(4);
                    
                    // Déterminer la couleur en fonction de l'expéditeur
                    final Color messageColor;
                    if (textMessage.startsWith("Serveur:")) {
                        messageColor = new Color(128, 0, 128); // Violet pour les messages du serveur
                    } else if (textMessage.contains(clientName + ":")) {
                        messageColor = new Color(0, 100, 0); // Vert foncé pour mes messages
                    } else {
                        messageColor = new Color(0, 0, 139); // Bleu foncé pour les autres
                    }
                    
                    SwingUtilities.invokeLater(() -> appendToChatArea(textMessage, messageColor));
                    
                } else if (message.startsWith("FILE:")) {
                    // C'est un fichier
                    // Format: FILE:nom_fichier:taille
                    String[] parts = message.split(":", 3);
                    if (parts.length == 3) {
                        String fileName = parts[1];
                        try {
                            int fileSize = Integer.parseInt(parts[2]);
                            
                            // Recevoir le fichier
                            byte[] fileData = new byte[fileSize];
                            dataInputStream.readFully(fileData);
                            
                            // Enregistrer le fichier dans le dossier downloads
                            final String savedPath = saveFile(fileName, fileData);
                            
                            SwingUtilities.invokeLater(() -> {
                                // Utiliser un lien hypertexte pour le fichier
                                if (savedPath != null) {
                                    appendFileLink(fileName, fileSize, savedPath);
                                } else {
                                    appendToChatArea("Erreur lors de la réception du fichier: " + fileName, Color.RED);
                                }
                            });
                        } catch (NumberFormatException e) {
                            SwingUtilities.invokeLater(() -> 
                                appendToChatArea("Format de taille de fichier invalide: " + parts[2], Color.RED));
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (isConnected) {
                SwingUtilities.invokeLater(() -> {
                    appendToChatArea("Erreur lors de la lecture des messages: " + e.getMessage(), Color.RED);
                });
            }
        }
    }
    
    // Méthode pour ajouter un lien de fichier cliquable
    private void appendFileLink(String fileName, int fileSize, String filePath) {
        StyledDocument doc = chatArea.getStyledDocument();
        
        // Obtenir l'heure actuelle
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        String timestamp = "[" + timeFormat.format(new Date()) + "] ";
        
        // Créer un style pour l'horodatage
        Style timestampStyle = chatArea.addStyle("Timestamp", null);
        StyleConstants.setForeground(timestampStyle, Color.GRAY);
        StyleConstants.setFontSize(timestampStyle, 12);
        
        // Créer un style pour les liens
        Style linkStyle = chatArea.addStyle("Link", null);
        StyleConstants.setForeground(linkStyle, Color.BLUE);
        StyleConstants.setUnderline(linkStyle, true);
        StyleConstants.setFontSize(linkStyle, 14);
        StyleConstants.setBold(linkStyle, true);

        // Texte du fichier
        String fileText = "📎 Fichier reçu: " + fileName + " (" + formatFileSize(fileSize) + ")";
        
        try {
            // Ajouter l'horodatage
            doc.insertString(doc.getLength(), timestamp, timestampStyle);
            
            // Ajouter le lien du fichier
            insertFileLink(doc, fileText, filePath);
            
            // Ajouter une note explicative
            doc.insertString(doc.getLength(), " [Cliquez pour ouvrir]\n", null);
            
            // Stocker la référence au fichier
            receivedFiles.put(fileText, filePath);
            
            // Défiler automatiquement vers le bas
            chatArea.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Méthode pour insérer un lien cliquable dans le document
    private void insertFileLink(StyledDocument doc, String text, String filePath) throws BadLocationException {
        // Créer l'action pour ouvrir le fichier
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, Color.BLUE);
        StyleConstants.setUnderline(attributes, true);
        StyleConstants.setBold(attributes, true);
        
        // Utiliser un MouseListener pour gérer les clics sur le lien
        final int startPosition = doc.getLength();
        doc.insertString(startPosition, text, attributes);
        final int endPosition = doc.getLength();
        
        // Mémoriser la position du lien pour le traitement des clics
        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    int pos = chatArea.viewToModel2D(e.getPoint());
                    if (pos >= startPosition && pos <= endPosition) {
                        openFile(filePath);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                try {
                    int pos = chatArea.viewToModel2D(e.getPoint());
                    if (pos >= startPosition && pos <= endPosition) {
                        chatArea.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                chatArea.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });
        
        chatArea.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                try {
                    int pos = chatArea.viewToModel2D(e.getPoint());
                    if (pos >= startPosition && pos <= endPosition) {
                        chatArea.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    } else {
                        chatArea.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " octets";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f Ko", size / 1024.0);
        } else {
            return String.format("%.1f Mo", size / (1024.0 * 1024.0));
        }
    }
    
    private void showFileOptionsIfAvailable(int x, int y) {
        try {
            int position = chatArea.viewToModel2D(new Point(x, y));
            if (position >= 0) {
                StyledDocument doc = chatArea.getStyledDocument();
                
                // Rechercher la ligne actuelle
                String text = doc.getText(0, doc.getLength());
                int lineStart = 0;
                for (int i = position; i >= 0; i--) {
                    if (i < text.length() && text.charAt(i) == '\n') {
                        lineStart = i + 1;
                        break;
                    }
                }
                
                int lineEnd = text.indexOf('\n', position);
                if (lineEnd == -1) lineEnd = text.length();
                
                String currentLine = text.substring(lineStart, lineEnd);
                
                // Vérifier si cette ligne contient une notification de fichier
                for (String key : receivedFiles.keySet()) {
                    if (currentLine.contains(key)) {
                        showFileOptions(x, y, key);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showFileOptions(int x, int y, String fileKey) {
        filePopupMenu.removeAll();
        
        String filePath = receivedFiles.get(fileKey);
        if (filePath != null) {
            JMenuItem openItem = new JMenuItem("Ouvrir le fichier");
            openItem.setFont(MAIN_FONT);
            openItem.addActionListener(e -> openFile(filePath));
            
            JMenuItem openFolderItem = new JMenuItem("Ouvrir le dossier contenant");
            openFolderItem.setFont(MAIN_FONT);
            openFolderItem.addActionListener(e -> openFolder(filePath));
            
            filePopupMenu.add(openItem);
            filePopupMenu.add(openFolderItem);
            filePopupMenu.show(chatArea, x, y);
        }
    }
    
    private void openFile(String filePath) {
        try {
            File file = new File(filePath);
            if (Desktop.isDesktopSupported() && file.exists()) {
                Desktop.getDesktop().open(file);
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Impossible d'ouvrir le fichier automatiquement.\nChemin: " + filePath,
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Erreur lors de l'ouverture du fichier: " + e.getMessage(),
                "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void openFolder(String filePath) {
        try {
            File file = new File(filePath);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.getParentFile());
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Impossible d'ouvrir le dossier automatiquement.\nChemin: " + file.getParent(),
                    "Information", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, 
                "Erreur lors de l'ouverture du dossier: " + e.getMessage(),
                "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void sendMessage() {
        String messageText = messageField.getText().trim();
        if (!messageText.isEmpty() && isConnected) {
            if (messageText.equals("exit")) {
                disconnect();
            } else {
                writer.println("MSG:" + messageText);
                messageField.setText("");
            }
        }
    }
    
    private void selectAndSendFile() {
        if (!isConnected) {
            JOptionPane.showMessageDialog(this, "Vous n'êtes pas connecté au serveur.",
                "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Sélectionner un fichier à partager");
        int result = fileChooser.showOpenDialog(this);
        
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            sendFile(selectedFile);
        }
    }
    
    private void sendFile(File file) {
        if (!isConnected) return;
        
        try {
            long fileSize = file.length();
            
            // Vérifier si le fichier n'est pas trop gros (limite à 100 Mo)
            if (fileSize > 100 * 1024 * 1024) {
                JOptionPane.showMessageDialog(this, 
                    "Le fichier est trop volumineux. La taille maximale est de 100 Mo.",
                    "Fichier trop volumineux", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            byte[] fileData = Files.readAllBytes(file.toPath());
            
            // Envoyer d'abord les métadonnées du fichier
            writer.println("FILE:" + file.getName() + ":" + fileData.length);
            
            // Puis envoyer les données du fichier
            dataOutputStream.write(fileData);
            dataOutputStream.flush();
            
            appendToChatArea("📤 Vous avez envoyé: " + file.getName() + " (" + formatFileSize(fileSize) + ")", 
                           new Color(0, 100, 0)); // Vert foncé
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur lors de l'envoi du fichier: " + e.getMessage(), 
                                         "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private String saveFile(String fileName, byte[] fileData) {
        try {
            String filePath = DOWNLOADS_FOLDER + File.separator + fileName;
            
            // Si un fichier avec le même nom existe déjà, ajouter un suffixe numérique
            File file = new File(filePath);
            int counter = 1;
            while (file.exists()) {
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    String name = fileName.substring(0, dotIndex);
                    String extension = fileName.substring(dotIndex);
                    filePath = DOWNLOADS_FOLDER + File.separator + name + "_" + counter + extension;
                } else {
                    filePath = DOWNLOADS_FOLDER + File.separator + fileName + "_" + counter;
                }
                file = new File(filePath);
                counter++;
            }
            
            Files.write(Paths.get(filePath), fileData);
            return filePath;
        } catch (IOException e) {
            appendToChatArea("Erreur lors de l'enregistrement du fichier: " + e.getMessage(), Color.RED);
            return null;
        }
    }
    
    private void disconnect() {
        try {
            if (isConnected) {
                isConnected = false;
                
                // Envoyer le message de déconnexion
                if (writer != null) {
                    writer.println("exit");
                }
                
                statusLabel.setText("Statut: Déconnecté");
                appendToChatArea("Vous êtes déconnecté du serveur.", Color.BLUE);
                
                // Changer le libellé du bouton
                disconnectButton.setText("Quitter");
                
                // Désactiver les éléments d'interface
                messageField.setEnabled(false);
                sendButton.setEnabled(false);
                sendFileButton.setEnabled(false);
                
                // Fermer les ressources
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (dataInputStream != null) dataInputStream.close();
                if (dataOutputStream != null) dataOutputStream.close();
            }
        } catch (IOException e) {
            appendToChatArea("Erreur lors de la déconnexion: " + e.getMessage(), Color.RED);
        }
    }
    
    private void appendToChatArea(String message, Color color) {
        StyledDocument doc = chatArea.getStyledDocument();
        
        // Obtenir l'heure actuelle
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        String timestamp = "[" + timeFormat.format(new Date()) + "] ";
        
        // Créer un style pour l'horodatage
        Style timestampStyle = chatArea.addStyle("Timestamp", null);
        StyleConstants.setForeground(timestampStyle, Color.GRAY);
        StyleConstants.setFontSize(timestampStyle, 12);
        
        // Créer un style pour le message
        Style messageStyle = chatArea.addStyle("Message", null);
        StyleConstants.setForeground(messageStyle, color);
        StyleConstants.setFontSize(messageStyle, 14);
        
        try {
            // Ajouter l'horodatage
            doc.insertString(doc.getLength(), timestamp, timestampStyle);
            
            // Ajouter le message
            doc.insertString(doc.getLength(), message + "\n", messageStyle);
            
            // Défiler automatiquement vers le bas
            chatArea.setCaretPosition(doc.getLength());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient();
            client.setVisible(true);
        });
    }
}
