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
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * The library catalog: a search bar with filters above a table of results.
 * Shared by both roles. The role decides which buttons appear in the bottom
 * bar.
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

    private final JButton detailsButton = new JButton("View Details");
    private final JButton editItemButton = new JButton("Edit Selected...");
    private final JButton checkoutButton = new JButton("Check Out Selected");
    private CheckoutRepository checkoutRepo; // null until enableCheckout() is called
    private String checkoutUser;
    private Consumer<MediaItem> onItemOpened = this::showDetails; // what a double-click does
    private boolean loadingFilters; // true while we fill the combo boxes, so they don't trigger searches

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
        table.setAutoCreateRowSorter(true); // click a column header to sort
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(ItemStatus.class, new StatusRenderer());

        int[] widths = { 70, 230, 150, 100, 110, 55, 150, 100 };
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
        detailsButton.setEnabled(false); // enabled once a row is selected
        buttons.add(detailsButton);
        if (role == Role.ADMIN) {
            JButton add = new JButton("Add Media Item...");
            add.setName("addItem");
            add.addActionListener(e -> new AddItemDialog(SwingUtilities.getWindowAncestor(this), repo,
                    this::afterItemSaved).setVisible(true));
            buttons.add(add);
            editItemButton.setName("editItem");
            editItemButton.setEnabled(false);
            editItemButton.addActionListener(e -> {
                MediaItem item = selectedItem();
                if (item != null) {
                    new AddItemDialog(SwingUtilities.getWindowAncestor(this), repo, item,
                            this::afterItemSaved).setVisible(true);
                }
            });
            buttons.add(editItemButton);
        } else {
            checkoutButton.setEnabled(false); // enabled when an available item is selected
            buttons.add(checkoutButton);
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
        searchField.addActionListener(e -> runSearch()); // Enter key
        typeBox.addActionListener(e -> filterChanged());
        genreBox.addActionListener(e -> filterChanged());
        fieldBox.addActionListener(e -> filterChanged());
        availableOnly.addActionListener(e -> filterChanged());
        clearButton.addActionListener(e -> clearFilters());

        detailsButton.addActionListener(e -> {
            MediaItem item = selectedItem();
            if (item != null) {
                showDetails(item);
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            detailsButton.setEnabled(table.getSelectedRow() >= 0);
            editItemButton.setEnabled(role == Role.ADMIN && table.getSelectedRow() >= 0);
            updateCheckoutButton();
        });
        checkoutButton.addActionListener(e -> {
            MediaItem item = selectedItem();
            if (item != null) {
                checkOutItem(item);
            }
        });

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
        CatalogQuery query = currentQuery();
        countLabel.setText("Loading...");
        new SwingWorker<List<MediaItem>, Void>() {
            @Override
            protected List<MediaItem> doInBackground() {
                return repo.search(query); // runs off the UI thread
            }

            @Override
            protected void done() {
                try {
                    List<MediaItem> results = get();
                    model.setItems(results);
                    countLabel.setText(results.size() + (results.size() == 1 ? " item found" : " items found"));
                } catch (Exception e) {
                    countLabel.setText("Could not load the catalog.");
                    JOptionPane.showMessageDialog(CatalogPanel.this, rootMessage(e), "Database error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static String rootMessage(Exception e) {
        Throwable t = e.getCause() != null ? e.getCause() : e;
        return t.getMessage();
    }

    private void showDetails(MediaItem item) {
        new ItemDetailsDialog(SwingUtilities.getWindowAncestor(this), item, role,
                checkoutRepo == null ? null : this::checkOutItem).setVisible(true);
    }

    /** After an admin saves an item, refresh the genre filter and catalog. */
    private void afterItemSaved() {
        Object selected = genreBox.getSelectedItem();
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return repo.genres();
            }

            @Override
            protected void done() {
                try {
                    List<String> genres = get();
                    loadingFilters = true;
                    genreBox.removeAllItems();
                    genreBox.addItem(ALL_GENRES);
                    for (String g : genres) {
                        genreBox.addItem(g);
                    }
                    genreBox.setSelectedItem(selected); // keeps the filter if it still exists
                    if (genreBox.getSelectedIndex() < 0) {
                        genreBox.setSelectedIndex(0);
                    }
                } catch (Exception ignored) {
                    // keep the old genre list; the item list below still reloads
                } finally {
                    loadingFilters = false;
                }
                runSearch();
            }
        }.execute();
    }

    // ------------------------------------------------------------------ checking out

    /** Lets this screen check items out for the signed-in user. Called by MainPanel for regular users. */
    public void enableCheckout(CheckoutRepository checkouts, String username) {
        this.checkoutRepo = checkouts;
        this.checkoutUser = username;
        updateCheckoutButton();
    }

    private void updateCheckoutButton() {
        MediaItem item = selectedItem();
        boolean canCheckOut = checkoutRepo != null && item != null && item.isAvailable();
        checkoutButton.setEnabled(canCheckOut);
        checkoutButton.setToolTipText(item != null && !item.isAvailable()
                ? "Only available items can be checked out" : null);
    }

    /** Asks for confirmation, then checks the item out in the background and reloads the list. */
    private void checkOutItem(MediaItem item) {
        if (checkoutRepo == null) {
            return;
        }
        LocalDate due = LocalDate.now().plusDays(CheckoutRepository.LOAN_DAYS);
        int answer = JOptionPane.showConfirmDialog(this,
                "Check out \"" + item.displayTitle() + "\"?\nIt will be due on " + due + ".",
                "Confirm Check Out", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        checkoutButton.setEnabled(false);
        new SwingWorker<CheckoutRecord, Void>() {
            @Override
            protected CheckoutRecord doInBackground() {
                return checkoutRepo.checkOut(checkoutUser, item); // runs off the UI thread
            }

            @Override
            protected void done() {
                try {
                    CheckoutRecord record = get();
                    JOptionPane.showMessageDialog(CatalogPanel.this,
                            "\"" + item.displayTitle() + "\" is checked out. Due " + record.dueOn
                                    + ".\nYou can find it under My Checkouts.",
                            "Checked Out", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(CatalogPanel.this, rootMessage(e), "Could not check out",
                            JOptionPane.ERROR_MESSAGE);
                }
                runSearch(); // always reload, so the list shows what the database really says
            }
        }.execute();
    }

    // ------------------------------------------------------------------ for other
    // screens

    /** The item in the selected row, or null if nothing is selected. */
    public MediaItem selectedItem() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? null : model.itemAt(table.convertRowIndexToModel(viewRow));
    }

    /** What to do when the user double-clicks an item. */
    public void setOnItemOpened(Consumer<MediaItem> handler) {
        this.onItemOpened = handler;
    }

    /**
     * Reload from the repository (call after an item is added or its status
     * changes).
     */
    public void refresh() {
        runSearch();
    }

    // ------------------------------------------------------------------ status
    // colors

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
