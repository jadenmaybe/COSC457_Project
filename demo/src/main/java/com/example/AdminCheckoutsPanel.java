package com.example;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Displays checkout history across all borrowers for the admin tab. */
public class AdminCheckoutsPanel extends JPanel {
    private static final String ALL = "All records";
    private static final String CURRENT = "Currently checked out";
    private static final String OVERDUE = "Overdue";
    private static final String RETURNED = "Returned";

    private final CheckoutRepository repo;
    private final JComboBox<String> filterBox =
            new JComboBox<>(new String[]{ALL, CURRENT, OVERDUE, RETURNED});
    private final JTextField searchField = new JTextField(22);
    private final JLabel summaryLabel = new JLabel(" ");
    private final JLabel countLabel = new JLabel(" ");
    private final JButton returnButton = new JButton("Mark Returned");
    private final CheckoutsTableModel model = new CheckoutsTableModel();
    private final JTable table = new JTable(model);
    private List<CheckoutRecord> records = new ArrayList<>();

    public AdminCheckoutsPanel(CheckoutRepository repo) {
        this.repo = repo;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        add(buildTop(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        filterBox.addActionListener(e -> applyFilters());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilters();
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateReturnButton());
        returnButton.addActionListener(e -> markSelectedReturned());
        refresh();
    }

    private JPanel buildTop() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JLabel heading = new JLabel("Check-Out Records");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        summaryLabel.setForeground(Color.GRAY);
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        filters.add(new JLabel("Show:"));
        filters.add(filterBox);
        filters.add(new JLabel("Search borrower, user ID or item:"));
        filters.add(searchField);
        filters.setAlignmentX(Component.LEFT_ALIGNMENT);

        top.add(heading);
        top.add(summaryLabel);
        top.add(filters);
        return top;
    }

    private JScrollPane buildTable() {
        TableRowSorter<CheckoutsTableModel> sorter = new TableRowSorter<>(model);
        sorter.setSortable(CheckoutsTableModel.DUE_IN, false);
        table.setRowSorter(sorter);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(LocalDate.class, new DateRenderer());
        table.setDefaultRenderer(CheckoutStatus.class, new StatusRenderer());
        table.setDefaultRenderer(Integer.class, new IdRenderer());

        int[] widths = {100, 70, 220, 140, 110, 110, 110, 110, 110, 100};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return new JScrollPane(table);
    }

    private JPanel buildBottom() {
        JPanel bar = new JPanel(new BorderLayout());
        countLabel.setForeground(Color.GRAY);
        bar.add(countLabel, BorderLayout.WEST);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        returnButton.setEnabled(false);
        buttons.add(returnButton);
        buttons.add(refreshButton);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    /** Reloads the repository and reapplies the current search and status filters. */
    public void refresh() {
        new SwingWorker<List<CheckoutRecord>, Void>() {
            @Override
            protected List<CheckoutRecord> doInBackground() {
                return repo.allRecords(); // runs off the UI thread
            }

            @Override
            protected void done() {
                try {
                    records = new ArrayList<>(get());
                    applyFilters();
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(AdminCheckoutsPanel.this, t.getMessage(), "Database error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void updateReturnButton() {
        int selectedRow = table.getSelectedRow();
        boolean canReturn = false;
        if (selectedRow >= 0) {
            CheckoutRecord selected = model.recordAt(table.convertRowIndexToModel(selectedRow));
            canReturn = selected.isOpen();
        }
        returnButton.setEnabled(canReturn);
    }

    private void markSelectedReturned() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        CheckoutRecord selected = model.recordAt(table.convertRowIndexToModel(selectedRow));
        if (!selected.isOpen()) {
            updateReturnButton();
            return;
        }

        int answer = JOptionPane.showConfirmDialog(this,
                "Mark \"" + selected.item.displayTitle() + "\" borrowed by " + selected.borrower + " as returned?",
                "Confirm Return", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        returnButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                repo.markReturned(selected); // runs off the UI thread
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(AdminCheckoutsPanel.this, t.getMessage(),
                            "Unable to Mark Returned", JOptionPane.ERROR_MESSAGE);
                }
                refresh();
            }
        }.execute();
    }

    private void applyFilters() {
        String filter = (String) filterBox.getSelectedItem();
        String search = searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<CheckoutRecord> shown = new ArrayList<>();
        for (CheckoutRecord record : records) {
            boolean matchesStatus = ALL.equals(filter)
                    || CURRENT.equals(filter) && record.isOpen()
                    || OVERDUE.equals(filter) && record.status() == CheckoutStatus.OVERDUE
                    || RETURNED.equals(filter) && !record.isOpen();
            boolean matchesSearch = search.isEmpty()
                    || contains(record.borrower, search)
                    || record.borrowerId != null && String.valueOf(record.borrowerId).equals(search)
                    || contains(record.item.displayTitle(), search)
                    || contains(record.item.author, search)
                    || contains(record.item.barcode, search);
            if (matchesStatus && matchesSearch) {
                shown.add(record);
            }
        }

        model.setRecords(shown);
        returnButton.setEnabled(false);
        long open = records.stream().filter(CheckoutRecord::isOpen).count();
        long overdue = records.stream().filter(r -> r.status() == CheckoutStatus.OVERDUE).count();
        summaryLabel.setText(records.size() + " total records · " + open + " currently checked out · "
                + overdue + " overdue");
        countLabel.setText(shown.size() + (shown.size() == 1 ? " record shown" : " records shown"));
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private static class CheckoutsTableModel extends AbstractTableModel {
        static final int DUE_IN = 7;
        private static final String[] COLUMNS = {
                "Borrower", "User ID", "Title", "Author", "Media Type", "Checked Out", "Due Date", "Due In",
                "Returned", "Status"
        };
        private List<CheckoutRecord> records = new ArrayList<>();

        void setRecords(List<CheckoutRecord> records) {
            this.records = new ArrayList<>(records);
            fireTableDataChanged();
        }

        CheckoutRecord recordAt(int row) {
            return records.get(row);
        }

        @Override
        public int getRowCount() {
            return records.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 1 -> Integer.class;
                case 5, 6, 8 -> LocalDate.class;
                case 9 -> CheckoutStatus.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            CheckoutRecord record = records.get(row);
            return switch (column) {
                case 0 -> record.borrower;
                case 1 -> record.borrowerId;
                case 2 -> record.item.displayTitle();
                case 3 -> record.item.author;
                case 4 -> record.item.mediaType.toString();
                case 5 -> record.checkedOutOn;
                case 6 -> record.dueOn;
                case 7 -> record.dueInText();
                case 8 -> record.returnedOn;
                case 9 -> record.status();
                default -> null;
            };
        }
    }

    private static class DateRenderer extends DefaultTableCellRenderer {
        private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

        @Override
        protected void setValue(Object value) {
            setText(value instanceof LocalDate date ? date.format(FORMAT) : "");
        }
    }

    /** Left-aligned, plain digits (no thousands separators) so it reads as an ID, not a quantity. */
    private static class IdRenderer extends DefaultTableCellRenderer {
        IdRenderer() {
            setHorizontalAlignment(LEFT);
        }

        @Override
        protected void setValue(Object value) {
            setText(value == null ? "" : value.toString());
        }
    }

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            setFont(getFont().deriveFont(Font.BOLD));
            if (!selected && value instanceof CheckoutStatus status) {
                setForeground(status.color());
            }
            return this;
        }
    }
}
