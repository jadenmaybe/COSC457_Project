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
 * The screen shown after login: a header (who is signed in + Log Out) above role-specific tabs.
 */
public class MainPanel extends JPanel {

    public MainPanel(Session session, CatalogRepository catalog, CheckoutRepository checkouts, Runnable onLogout) {
        setLayout(new BorderLayout());
        add(buildHeader(session, onLogout), BorderLayout.NORTH);
        add(buildTabs(session, catalog, checkouts), BorderLayout.CENTER);
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

        JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        left.add(new LogoPlaceholder(48, 48)); // swap in src/main/resources/logo.png
        left.add(title);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JTabbedPane buildTabs(Session session, CatalogRepository catalog, CheckoutRepository checkouts) {
        JTabbedPane tabs = new JTabbedPane();
        CatalogPanel catalogPanel = new CatalogPanel(session.role(), catalog,
                session.role() == Role.ADMIN ? null : session.username());   // same screen for both roles
        if (session.role() != Role.ADMIN) {
            catalogPanel.enableCheckout(checkouts, session.username());
        }
        tabs.addTab("Catalog", catalogPanel);
        // Reload whenever the user switches back to the catalog, so item statuses (e.g. after a return) are current.
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == catalogPanel) {
                catalogPanel.refresh();
            }
        });
        if (session.role() == Role.ADMIN) {
            AdminCheckoutsPanel all = new AdminCheckoutsPanel(checkouts);
            tabs.addTab("Check-Out Records", all);
            tabs.addChangeListener(e -> {
                if (tabs.getSelectedComponent() == all) {
                    all.refresh();
                }
            });
        } else {
            MyCheckoutsPanel mine = new MyCheckoutsPanel(session, checkouts);
            tabs.addTab("My Checkouts", mine);
            // Reload whenever the user switches to this tab, so it is never out of date.
            tabs.addChangeListener(e -> {
                if (tabs.getSelectedComponent() == mine) {
                    mine.refresh();
                }
            });
        }
        return tabs;
    }
}
