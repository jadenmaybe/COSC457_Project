package com.example;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

/**
 * The screen shown after login: a header (who is signed in + Log Out) above a set of tabs.
 * The tabs differ by role. Tabs that aren't built yet are placeholders and get replaced one at a time.
 */
public class MainPanel extends JPanel {

    public MainPanel(Session session, CatalogRepository catalog, Runnable onLogout) {
        setLayout(new BorderLayout());
        add(buildHeader(session, onLogout), BorderLayout.NORTH);
        add(buildTabs(session, catalog), BorderLayout.CENTER);
    }

    private JPanel buildHeader(Session session, Runnable onLogout) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xC8C8C8)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        JLabel title = new JLabel("Library Management System");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

        JLabel who = new JLabel("Signed in as " + session.username() + " (" + session.role() + ")  ");
        JButton logout = new JButton("Log Out");
        logout.addActionListener(e -> onLogout.run());

        JPanel right = new JPanel();
        right.add(who);
        right.add(logout);

        header.add(title, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JTabbedPane buildTabs(Session session, CatalogRepository catalog) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Catalog", new CatalogPanel(session.role(), catalog));   // same screen for both roles
        if (session.role() == Role.ADMIN) {
            tabs.addTab("Check-Out Records", new PlaceholderPanel("Check-Out Records",
                    "Check-out date, due date, and which user has each item",
                    "Search and filter the records",
                    "Mark an item as Returned or Overdue"));
        } else {
            tabs.addTab("My Checkouts", new PlaceholderPanel("My Checked-Out Items",
                    "Items you currently have, with due dates",
                    "Status shown as Checked Out, Overdue or Returned"));
        }
        return tabs;
    }
}
