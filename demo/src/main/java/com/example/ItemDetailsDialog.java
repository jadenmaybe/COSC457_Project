package com.example;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pop-up showing everything known about one catalog item.
 * What it shows comes from MediaItem.details(role), so new information types added there
 * appear here automatically, and a regular user never sees Overdue.
 */
public class ItemDetailsDialog extends JDialog {
    /** Already shown in the header/banner, so not repeated in the field list. */
    private static final Set<String> SHOWN_IN_HEADER = Set.of("Title", "Media type", "Status");

    public ItemDetailsDialog(Window owner, MediaItem item, Role viewer) {
        this(owner, item, viewer, null);
    }

    /** @param onCheckout what the Check Out button does (null = the button stays disabled) */
    public ItemDetailsDialog(Window owner, MediaItem item, Role viewer, Consumer<MediaItem> onCheckout) {
        super(owner, item.displayTitle(), ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout());
        content.add(buildHeader(item, viewer), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(buildFields(item, viewer));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buildButtons(item, viewer, onCheckout), BorderLayout.SOUTH);
        setContentPane(content);

        setSize(540, 620);
        setMinimumSize(new Dimension(420, 360));
        setLocationRelativeTo(owner);

        // Esc closes the window.
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // ------------------------------------------------------------------ header

    private JPanel buildHeader(MediaItem item, Role viewer) {
        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));

        JLabel title = new JLabel("<html>" + escape(item.displayTitle()) + "</html>");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        String sub = item.mediaType.toString();
        if (item.author != null && !item.author.isBlank()) {
            sub += "  •  " + item.author;
        }
        JLabel subtitle = new JLabel(sub);
        subtitle.setForeground(Color.GRAY);

        JPanel titles = new JPanel(new BorderLayout(0, 2));
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.CENTER);

        header.add(titles, BorderLayout.NORTH);
        header.add(buildBanner(item, viewer), BorderLayout.CENTER);
        return header;
    }

    /** The colored availability strip: the first thing a borrower wants to know. */
    private JLabel buildBanner(MediaItem item, Role viewer) {
        ItemStatus s = item.statusFor(viewer);
        String text;
        Color bg;
        Color fg;
        switch (s) {
            case AVAILABLE -> {
                text = "Available" + (item.locationText().isEmpty() ? "" : " — find it at " + item.locationText());
                bg = new Color(0xE6F4EA);
                fg = new Color(0x1B7F3B);
            }
            case CHECKED_OUT -> {
                text = "Currently checked out";
                bg = new Color(0xFFF4DB);
                fg = new Color(0x9A6700);
            }
            case ON_HOLD -> {
                text = "On hold for another user";
                bg = new Color(0xEFE7FB);
                fg = new Color(0x6639BA);
            }
            default -> {   // OVERDUE: only reachable for admins, see MediaItem.statusFor
                text = "Overdue — checked out and past its due date";
                bg = new Color(0xFDE7EA);
                fg = new Color(0xB00020);
            }
        }
        JLabel banner = new JLabel(text, SwingConstants.LEFT);
        banner.setOpaque(true);
        banner.setBackground(bg);
        banner.setForeground(fg);
        banner.setFont(banner.getFont().deriveFont(Font.BOLD, 13f));
        banner.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return banner;
    }

    // ------------------------------------------------------------------ fields

    private JPanel buildFields(MediaItem item, Role viewer) {
        JPanel panel = new WidthTrackingPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 16, 12, 16));

        int y = 0;
        for (Map.Entry<String, String> e : item.details(viewer).entrySet()) {
            if (SHOWN_IN_HEADER.contains(e.getKey())) {
                continue;
            }
            GridBagConstraints g = new GridBagConstraints();
            g.gridy = y++;
            g.insets = new Insets(4, 0, 4, 12);
            g.anchor = GridBagConstraints.NORTHWEST;

            JLabel label = new JLabel(e.getKey());
            label.setForeground(Color.GRAY);
            g.gridx = 0;
            g.weightx = 0;
            panel.add(label, g);

            g.gridx = 1;
            g.weightx = 1;
            g.insets = new Insets(4, 0, 4, 0);
            g.fill = GridBagConstraints.HORIZONTAL;
            panel.add(valueArea(e.getValue()), g);
        }
        // Pushes the rows to the top when there are few of them.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = y;
        filler.weighty = 1;
        panel.add(new JPanel(), filler);
        return panel;
    }

    /** Read-only, wrapping, selectable text (so a long description wraps and an ISBN can be copied). */
    private static JTextArea valueArea(String value) {
        JTextArea a = new JTextArea(value);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setBorder(null);
        a.setFont(new JLabel().getFont());
        a.setColumns(24);
        return a;
    }

    // ------------------------------------------------------------------ buttons

    private JPanel buildButtons(MediaItem item, Role viewer, Consumer<MediaItem> onCheckout) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xC8C8C8)));

        if (viewer != Role.ADMIN) {
            JButton checkout = new JButton("Check Out");
            if (onCheckout != null && item.isAvailable()) {
                checkout.addActionListener(e -> {
                    dispose();
                    onCheckout.accept(item);
                });
            } else {
                checkout.setEnabled(false);
                checkout.setToolTipText(onCheckout == null ? "Open this item from the Catalog tab to check it out"
                        : "Only available items can be checked out");
            }
            bar.add(checkout);
        }
        JButton close = new JButton(new AbstractAction("Close") {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        bar.add(close);
        getRootPane().setDefaultButton(close);
        return bar;
    }

    // ------------------------------------------------------------------ helpers

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** A panel that is always as wide as the scroll pane, so wrapped text re-wraps when the window resizes. */
    private static class WidthTrackingPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
