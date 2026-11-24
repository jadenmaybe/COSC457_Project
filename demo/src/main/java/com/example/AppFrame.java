package com.example;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Dimension;

/**
 * The one window of the app. It swaps between the login screen and the main screen
 * using a CardLayout, so there is a single window the whole time.
 */
public class AppFrame extends JFrame {
    private static final String LOGIN = "login";
    private static final String MAIN = "main";

    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final AuthService auth = new MockAuthService();   // TODO: real database-backed AuthService
    private final CatalogRepository catalog = new SampleCatalogRepository();   // TODO: MySQL-backed repository
    private final LoginPanel loginPanel;
    private MainPanel mainPanel;

    public AppFrame() {
        super("Library Management System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(760, 520));
        setSize(960, 640);
        setLocationRelativeTo(null);

        loginPanel = new LoginPanel(auth, this::showMain);
        root.add(loginPanel, LOGIN);
        setContentPane(root);
        showLogin();
    }

    private void showLogin() {
        if (mainPanel != null) {
            root.remove(mainPanel);
            mainPanel = null;
        }
        loginPanel.reset();
        cards.show(root, LOGIN);
        getRootPane().setDefaultButton(loginPanel.defaultButton());
    }

    private void showMain(Session session) {
        mainPanel = new MainPanel(session, catalog, this::showLogin);
        root.add(mainPanel, MAIN);
        cards.show(root, MAIN);
        getRootPane().setDefaultButton(null);
        root.revalidate();
    }
}
