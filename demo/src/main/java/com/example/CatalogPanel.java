package com.example;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * The library catalog: a search bar with filters above a table of results.
 * Shared by both roles. The role decides which buttons appear in the bottom bar.
 */
public class CatalogPanel extends JPanel {
    private static final String ALL_TYPES = "All media types";
    private static final String ALL_GENRES = "All genres";

    private final Role role;
    private final CatalogRepository repo;

    private final JTextField searchField = new JTextField(22);
    private final JComboBox<CatalogQuery.Field> fieldBox = new JComboBox<>(CatalogQuery.Field.values());
    private final JComboBox<Object> typeBox = new JComboBox<>();
    private final JComboBox<String> genreBox = new JComboBox<>();
    private final JCheckBox availableOnly = new JCheckBox("Available now only");
    private final JButton searchButton = new JButton("Search");
    private final JButton clearButton = new JButton("Clear");

    private final CatalogTableModel model;
    private final JTable table;
    private final JLabel countLabel = new JLabel(" ");

    private Consumer<MediaItem> onItemOpened;   // set by the details window in the next step
    private boolean loadingFilters;             // true while we fill the combo boxes, so they don't trigger searches

    public CatalogPanel(Role role, CatalogRepository repo) {
        this.role = role;
        this.repo = repo;
        this.model = new CatalogTableModel(role);
        this.table = new JTable(model);
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        add(buildSearchBar(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        fillFilters();
        wireEvents();
        runSearch();
    }

    // ------------------------------------------------------------------ layout

    private JPanel buildSearchBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row1.add(new JLabel("Search:"));
        row1.add(searchField);
        row1.add(new JLabel("in"));
        row1.add(fieldBox);
        row1.add(searchButton);
        row1.add(clearButton);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row2.add(new JLabel("Media type:"));
        row2.add(typeBox);
        row2.add(new JLabel("Genre:"));
        row2.add(genreBox);
        row2.add(availableOnly);

        bar.add(row1);
        bar.add(row2);
        return bar;
    }

    private JScrollPane buildTable() {
        table.setAutoCreateRowSorter(true);          // click a column header to sort
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(ItemStatus.class, new StatusRenderer());

        int[] widths = {70, 230, 150, 100, 110, 55, 150, 100};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return new JScrollPane(table);
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        countLabel.setForeground(Color.GRAY);
        bar.add(countLabel, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        // Role-specific buttons. They are placeholders for now; later steps give them behavior.
        if (role == Role.ADMIN) {
            JButton add = new JButton("Add Media Item...");
            add.setEnabled(false);
            add.setToolTipText("Coming in a later step");
            buttons.add(add);
        } else {
            JButton checkout = new JButton("Check Out Selected");
            checkout.setEnabled(false);
            checkout.setToolTipText("Coming in a later step");
            buttons.add(checkout);
        }
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------ behavior

    private void fillFilters() {
        loadingFilters = true;
        typeBox.addItem(ALL_TYPES);
        for (MediaType t : MediaType.values()) {
            typeBox.addItem(t);
        }
        genreBox.addItem(ALL_GENRES);
        for (String g : repo.genres()) {
            genreBox.addItem(g);
        }
        loadingFilters = false;
    }

    private void wireEvents() {
        searchButton.addActionListener(e -> runSearch());
        searchField.addActionListener(e -> runSearch());       // Enter key
        typeBox.addActionListener(e -> filterChanged());
        genreBox.addActionListener(e -> filterChanged());
        fieldBox.addActionListener(e -> filterChanged());
        availableOnly.addActionListener(e -> filterChanged());
        clearButton.addActionListener(e -> clearFilters());

        // Double-click a row to open that item (the details window plugs in here next).
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && onItemOpened != null) {
                    MediaItem item = selectedItem();
                    if (item != null) {
                        onItemOpened.accept(item);
                    }
                }
            }
        });
    }

    private void filterChanged() {
        if (!loadingFilters) {
            runSearch();
        }
    }

    private void clearFilters() {
        loadingFilters = true;
        searchField.setText("");
        fieldBox.setSelectedIndex(0);
        typeBox.setSelectedIndex(0);
        genreBox.setSelectedIndex(0);
        availableOnly.setSelected(false);
        loadingFilters = false;
        runSearch();
    }

    private CatalogQuery currentQuery() {
        MediaType type = typeBox.getSelectedItem() instanceof MediaType t ? t : null;
        String genre = ALL_GENRES.equals(genreBox.getSelectedItem()) ? null : (String) genreBox.getSelectedItem();
        return new CatalogQuery(searchField.getText(), (CatalogQuery.Field) fieldBox.getSelectedItem(),
                type, genre, availableOnly.isSelected());
    }

    private void runSearch() {
        // TODO: when this talks to MySQL, run it inside a SwingWorker so the window never freezes.
        List<MediaItem> results = repo.search(currentQuery());
        model.setItems(results);
        countLabel.setText(results.size() + (results.size() == 1 ? " item found" : " items found"));
    }

    // ------------------------------------------------------------------ for other screens

    /** The item in the selected row, or null if nothing is selected. */
    public MediaItem selectedItem() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? null : model.itemAt(table.convertRowIndexToModel(viewRow));
    }

    /** What to do when the user double-clicks an item. */
    public void setOnItemOpened(Consumer<MediaItem> handler) {
        this.onItemOpened = handler;
    }

    /** Reload from the repository (call after an item is added or its status changes). */
    public void refresh() {
        runSearch();
    }

    // ------------------------------------------------------------------ status colors

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, col);
            setFont(getFont().deriveFont(Font.BOLD));
            if (!selected && value instanceof ItemStatus s) {
                setForeground(switch (s) {
                    case AVAILABLE -> new Color(0x1B7F3B);
                    case CHECKED_OUT -> new Color(0x9A6700);
                    case ON_HOLD -> new Color(0x6639BA);
                    case OVERDUE -> new Color(0xB00020);
                });
            }
            return this;
        }
    }
}
