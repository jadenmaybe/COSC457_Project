package com.example;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The signed-in user's own checkouts: what they have now, when it is due, and what they have returned.
 * Unlike the catalog, a user sees their OWN items' real status here, including Overdue.
 */
public class MyCheckoutsPanel extends JPanel {
    private static final String CURRENT = "Items I have now";
    private static final String RETURNED = "Returned items";
    private static final String ALL = "All items";

    private final Session session;
    private final CheckoutRepository repo;

    private final JComboBox<String> filterBox = new JComboBox<>(new String[]{CURRENT, RETURNED, ALL});
    private final JLabel summaryLabel = new JLabel(" ");
    private final JLabel overdueLabel = new JLabel(" ");
    private final JLabel countLabel = new JLabel(" ");
    private final JButton detailsButton = new JButton("View Details");
    private final JButton refreshButton = new JButton("Refresh");

    private final MyCheckoutsTableModel model = new MyCheckoutsTableModel();
    private final JTable table = new JTable(model);

    public MyCheckoutsPanel(Session session, CheckoutRepository repo) {
        this.session = session;
        this.repo = repo;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        add(buildTop(), BorderLayout.NORTH);
        add(buildTable(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        filterBox.addActionListener(e -> refresh());
        refreshButton.addActionListener(e -> refresh());
        detailsButton.addActionListener(e -> openSelected());
        detailsButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(
                e -> detailsButton.setEnabled(table.getSelectedRow() >= 0));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });
        refresh();
    }

    // ------------------------------------------------------------------ layout

    private JPanel buildTop() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JLabel heading = new JLabel("My Checked-Out Items");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel who = new JLabel("Account: " + session.username());
        who.setForeground(Color.GRAY);
        who.setAlignmentX(Component.LEFT_ALIGNMENT);

        overdueLabel.setForeground(CheckoutStatus.OVERDUE.color());
        overdueLabel.setFont(overdueLabel.getFont().deriveFont(Font.BOLD));

        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        summary.add(summaryLabel);
        summary.add(overdueLabel);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        filter.add(new JLabel("Show:"));
        filter.add(filterBox);
        filter.setAlignmentX(Component.LEFT_ALIGNMENT);

        top.add(heading);
        top.add(who);
        top.add(summary);
        top.add(filter);
        return top;
    }

    private JScrollPane buildTable() {
        TableRowSorter<MyCheckoutsTableModel> sorter = new TableRowSorter<>(model);
        sorter.setSortable(MyCheckoutsTableModel.DUE_IN, false);   // text like "5 days" doesn't sort meaningfully
        table.setRowSorter(sorter);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(LocalDate.class, new DateRenderer());
        table.setDefaultRenderer(CheckoutStatus.class, new StatusRenderer());

        int[] widths = {220, 140, 95, 100, 100, 100, 100, 100};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return new JScrollPane(table);
    }

    private JPanel buildBottom() {
        JPanel bar = new JPanel(new BorderLayout());
        countLabel.setForeground(Color.GRAY);
        bar.add(countLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(detailsButton);
        buttons.add(refreshButton);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------ behavior

    /** Reloads from the repository. Called when the tab is shown and whenever the filter changes. */
    public void refresh() {
        // TODO: when this talks to MySQL, load inside a SwingWorker so the window never freezes.
        List<CheckoutRecord> mine = repo.forUser(session.username());

        long open = mine.stream().filter(CheckoutRecord::isOpen).count();
        long overdue = mine.stream().filter(r -> r.status() == CheckoutStatus.OVERDUE).count();
        summaryLabel.setText(open == 1 ? "1 item checked out" : open + " items checked out");
        overdueLabel.setText(overdue == 0 ? " " : overdue + " overdue: please return as soon as you can");

        String choice = (String) filterBox.getSelectedItem();
        List<CheckoutRecord> shown = new ArrayList<>();
        for (CheckoutRecord r : mine) {
            boolean keep = CURRENT.equals(choice) ? r.isOpen() : !RETURNED.equals(choice) || !r.isOpen();
            if (keep) {
                shown.add(r);
            }
        }
        model.setRecords(shown);
        countLabel.setText(shown.isEmpty() ? emptyMessage(choice)
                : shown.size() + (shown.size() == 1 ? " item" : " items"));
    }

    private static String emptyMessage(String choice) {
        if (CURRENT.equals(choice)) {
            return "You have no items checked out right now.";
        }
        if (RETURNED.equals(choice)) {
            return "You have not returned any items yet.";
        }
        return "You have no checkout history yet.";
    }

    private void openSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        CheckoutRecord r = model.recordAt(table.convertRowIndexToModel(viewRow));
        new ItemDetailsDialog(SwingUtilities.getWindowAncestor(this), r.item, session.role()).setVisible(true);
    }

    // ------------------------------------------------------------------ table model + renderers

    private static class MyCheckoutsTableModel extends AbstractTableModel {
        static final int DUE_IN = 5;
        private static final String[] COLUMNS = {
                "Title", "Author", "Media Type", "Checked Out", "Due Date", "Due In", "Returned", "Status"};
        private List<CheckoutRecord> records = new ArrayList<>();

        void setRecords(List<CheckoutRecord> r) {
            records = new ArrayList<>(r);
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
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return switch (c) {
                case 3, 4, 6 -> LocalDate.class;
                case 7 -> CheckoutStatus.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int row, int col) {
            CheckoutRecord r = records.get(row);
            return switch (col) {
                case 0 -> r.item.displayTitle();
                case 1 -> r.item.author;
                case 2 -> r.item.mediaType.toString();
                case 3 -> r.checkedOutOn;
                case 4 -> r.dueOn;
                case 5 -> r.dueInText();
                case 6 -> r.returnedOn;
                case 7 -> r.status();
                default -> null;
            };
        }
    }

    private static class DateRenderer extends DefaultTableCellRenderer {
        private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

        @Override
        protected void setValue(Object value) {
            setText(value instanceof LocalDate d ? d.format(FORMAT) : "");
        }
    }

    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, col);
            setFont(getFont().deriveFont(Font.BOLD));
            if (!selected && value instanceof CheckoutStatus s) {
                setForeground(s.color());
            }
            return this;
        }
    }
}
