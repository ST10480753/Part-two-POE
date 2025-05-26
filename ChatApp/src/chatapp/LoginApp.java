package chatapp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;
import org.json.simple.*;
import org.json.simple.parser.*;

public class LoginApp {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LoginForm::new);
    }

    // Database connection
    public static class DBConnection {
        public static Connection getConnection() {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                return DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/mydb", "root", "Masilela23");
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    // Login Form
    public static class LoginForm extends JFrame {
        JTextField userField;
        JPasswordField passField;

        public LoginForm() {
            setTitle("Login");
            setLayout(new GridLayout(4, 2, 5, 5));

            add(new JLabel("Username:"));
            userField = new JTextField();
            add(userField);

            add(new JLabel("Password:"));
            passField = new JPasswordField();
            add(passField);

            JButton loginBtn = new JButton("Login");
            loginBtn.addActionListener(e -> login());
            add(loginBtn);

            JButton signUpBtn = new JButton("Sign Up");
            signUpBtn.addActionListener(e -> {
                dispose();
                new SignUpForm();
            });
            add(signUpBtn);

            setSize(300, 200);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setVisible(true);
        }

        private void login() {
            String username = userField.getText();
            String password = String.valueOf(passField.getPassword());

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT first_name FROM users WHERE username = ? AND password = ?")) {
                ps.setString(1, username);
                ps.setString(2, password);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String firstName = rs.getString("first_name");
                    dispose();
                    new Dashboard(firstName);
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid credentials.");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Login failed. Please try again.");
            }
        }
    }

    // Sign Up Form
    public static class SignUpForm extends JFrame {
        JTextField nameField, usernameField, cellField;
        JPasswordField passwordField;

        public SignUpForm() {
            setTitle("Sign Up");
            setLayout(new GridLayout(5, 2, 5, 5));

            add(new JLabel("First Name:"));
            nameField = new JTextField();
            add(nameField);

            add(new JLabel("Username:"));
            usernameField = new JTextField();
            add(usernameField);

            add(new JLabel("Password:"));
            passwordField = new JPasswordField();
            add(passwordField);

            add(new JLabel("Cell Number (+code):"));
            cellField = new JTextField();
            add(cellField);

            JButton registerBtn = new JButton("Register");
            registerBtn.addActionListener(e -> registerUser());
            add(registerBtn);

            JButton backBtn = new JButton("Back to Login");
            backBtn.addActionListener(e -> {
                dispose();
                new LoginForm();
            });
            add(backBtn);

            setSize(400, 220);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setVisible(true);
        }

        private void registerUser() {
            String name = nameField.getText();
            String user = usernameField.getText();
            String pass = String.valueOf(passwordField.getPassword());
            String cell = cellField.getText();

            if (name.isEmpty() || user.isEmpty() || pass.isEmpty() || cell.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All fields are required!");
                return;
            }

            if (!user.matches("(?=.*[A-Z])(?=.*_).{5,}")) {
                JOptionPane.showMessageDialog(this, "Username must have 1 capital letter, 1 underscore, and be at least 5 characters.");
                return;
            }

            if (!pass.matches("(?=.*[A-Z])(?=.*\\d).{8,}")) {
                JOptionPane.showMessageDialog(this, "Password must have 1 capital letter, 1 number, and be at least 8 characters.");
                return;
            }

            if (!cell.matches("^\\+\\d{1,4}\\d{1,10}$")) {
                JOptionPane.showMessageDialog(this, "Cell number must start with + and include country code and number (max 10 digits).");
                return;
            }

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "INSERT INTO users (first_name, username, password, cell_number) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, name);
                ps.setString(2, user);
                ps.setString(3, pass);
                ps.setString(4, cell);
                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Registration successful!");
                dispose();
                new LoginForm();
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1062) { // Duplicate entry error code
                    JOptionPane.showMessageDialog(this, "Username already exists!");
                } else {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Registration failed. Please try again.");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Registration failed. Please try again.");
            }
        }
    }

    // Dashboard
    public static class Dashboard extends JFrame {
        public Dashboard(String firstName) {
            setTitle("Dashboard");
            setLayout(new BorderLayout());

            JLabel welcome = new JLabel("Welcome, " + firstName + "!", JLabel.CENTER);
            welcome.setFont(new Font("Arial", Font.BOLD, 16));
            add(welcome, BorderLayout.NORTH);

            JPanel panel = new JPanel(new GridLayout(1, 3));
            JButton sendBtn = new JButton("Send Message");
            JButton viewBtn = new JButton("View Messages");
            JButton quitBtn = new JButton("Quit");

            sendBtn.addActionListener(e -> MessageStore.sendMessage(this));
            viewBtn.addActionListener(e -> MessageStore.showMessages(this));
            quitBtn.addActionListener(e -> {
                dispose();
                new LoginForm();
            });

            panel.add(sendBtn);
            panel.add(viewBtn);
            panel.add(quitBtn);

            add(panel, BorderLayout.CENTER);

            setSize(450, 150);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            setVisible(true);
        }
    }

    // Message handling
    public static class MessageStore {
        private static final String FILE_PATH = "messages.json";

        public static void sendMessage(Component parent) {
            String msg = JOptionPane.showInputDialog(parent, "Enter message:");
            if (msg == null || msg.isEmpty()) return;

            JSONArray messages = loadMessages();
            JSONObject msgObj = new JSONObject();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timestamp = LocalDateTime.now().format(formatter);
            msgObj.put("message", msg);
            msgObj.put("timestamp", timestamp);

            messages.add(msgObj);

            try (FileWriter file = new FileWriter(FILE_PATH)) {
                file.write(messages.toJSONString());
                file.flush();
                JOptionPane.showMessageDialog(parent, "Message saved.");
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(parent, "Failed to save message.");
            }
        }

        public static void showMessages(Component parent) {
            JSONArray messages = loadMessages();
            if (messages.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "No messages found.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (Object o : messages) {
                JSONObject m = (JSONObject) o;
                sb.append(m.get("timestamp")).append(" - ").append(m.get("message")).append("\n");
            }

            JTextArea area = new JTextArea(sb.toString());
            area.setEditable(false);
            JScrollPane pane = new JScrollPane(area);
            pane.setPreferredSize(new Dimension(400, 300));
            JOptionPane.showMessageDialog(parent, pane, "Messages", JOptionPane.INFORMATION_MESSAGE);
        }

        private static JSONArray loadMessages() {
            File file = new File(FILE_PATH);
            if (!file.exists()) return new JSONArray();

            try (FileReader reader = new FileReader(file)) {
                return (JSONArray) new JSONParser().parse(reader);
            } catch (Exception e) {
                e.printStackTrace();
                return new JSONArray();
            }
        }
    }
}
