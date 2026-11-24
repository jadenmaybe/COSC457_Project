package com.example;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.function.Consumer;

/** The sign-in screen: pick User or Admin, enter credentials, press Login. */
public class LoginPanel extends JPanel {
    private final AuthService auth;
    private final Consumer<Session> onSuccess;

    private final JRadioButton userOption = new JRadioButton(Role.USER.toString(), true);
    private final JRadioButton adminOption = new JRadioButton(Role.ADMIN.toString());
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JButton loginButton = new JButton("Log In");
    private final JLabel messageLabel = new JLabel(" ", SwingConstants.CENTER);

    public LoginPanel(AuthService auth, Consumer<Session> onSuccess) {
        this.auth = auth;
        this.onSuccess = onSuccess;
        setLayout(new GridBagLayout()); // centers the card in the window
        add(buildCard());

        loginButton.addActionListener(e -> attemptLogin());
        // Pressing Enter in either field logs in.
        usernameField.addActionListener(e -> attemptLogin());
        passwordField.addActionListener(e -> attemptLogin());
    }

    private JPanel buildCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC8C8C8)),
                BorderFactory.createEmptyBorder(24, 32, 24, 32)));

        JLabel title = new JLabel("Library Management System");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Please sign in to continue");
        subtitle.setForeground(Color.GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        ButtonGroup group = new ButtonGroup();
        group.add(userOption);
        group.add(adminOption);
        JPanel roleRow = new JPanel();
        roleRow.add(userOption);
        roleRow.add(adminOption);

        JPanel form = new JPanel(new GridBagLayout());
        addRow(form, 0, "Username", usernameField);
        addRow(form, 1, "Password", passwordField);

        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setPreferredSize(new Dimension(120, 32));

        messageLabel.setForeground(new Color(0xB00020));
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel demoNote = new JLabel("<html><center>Any username and password works.<br>"
                + "Sample checkouts exist for the users \"alice\" and \"bob\".</center></html>");
        demoNote.setFont(demoNote.getFont().deriveFont(Font.ITALIC, 11f));
        demoNote.setForeground(Color.GRAY);
        demoNote.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(title);
        card.add(Box.createVerticalStrut(4));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(16));
        card.add(roleRow);
        card.add(Box.createVerticalStrut(8));
        card.add(form);
        card.add(Box.createVerticalStrut(8));
        card.add(messageLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(loginButton);
        card.add(Box.createVerticalStrut(12));
        card.add(demoNote);
        return card;
    }

    private static void addRow(JPanel form, int y, String label, java.awt.Component field) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = y;
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0;
        form.add(new JLabel(label), g);
        g.gridx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        form.add(field, g);
    }

    private void attemptLogin() {
        Role role = adminOption.isSelected() ? Role.ADMIN : Role.USER;
        char[] password = passwordField.getPassword();
        try {
            Session session = auth.login(role, usernameField.getText(), password);
            messageLabel.setText(" ");
            onSuccess.accept(session);
        } catch (AuthService.AuthException ex) {
            messageLabel.setText(ex.getMessage());
        } finally {
            Arrays.fill(password, '\0'); // don't leave the password sitting in memory
        }
    }

    /** Called when returning to this screen after logout. */
    public void reset() {
        usernameField.setText("");
        passwordField.setText("");
        messageLabel.setText(" ");
        userOption.setSelected(true);
        usernameField.requestFocusInWindow();
    }

    public JButton defaultButton() {
        return loginButton;
    }
}
