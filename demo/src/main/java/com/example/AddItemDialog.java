package com.example;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.LocalDate;
import java.util.List;

/**
 * The administrator's "Add Media Item" form. The fields shown depend on the media type: a Book asks for an ISBN,
 * a Video/Audio item for a runtime, Equipment for just an equipment type (it has no title/author record).
 * It builds a MediaItem and hands it to CatalogRepository.addItem(); the repository does the database work.
 */
public class AddItemDialog extends JDialog {
    private static final String NEW_LOCATION = "New location...";
    private static final String[] CONDITIONS = { "Excellent", "Good", "Fair", "Poor" };
    private static final String[] PERIODICAL_TYPES = { "Journal", "Magazine", "Newspaper" };
    private static final String[] FORMATS = { "CD", "DVD", "Blu-ray", "Vinyl", "Cassette", "Digital" };

    private final CatalogRepository repo;
    private final Runnable onAdded;

    // ---- item
    private final JComboBox<MediaType> typeBox = new JComboBox<>(MediaType.values());
    private final JTextField barcodeField = new JTextField(20);
    private final JTextField callField = new JTextField(20);
    private final JComboBox<String> conditionBox = new JComboBox<>(CONDITIONS);
    private final JTextField onlineField = new JTextField(20);

    // ---- location
    private final JComboBox<Object> locationBox = new JComboBox<>();
    private final JSpinner floorSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 99, 1));
    private final JTextField areaField = new JTextField(16);
    private final JPanel newLocationRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));

    // ---- record (title, author, ...). Equipment has none.
    private final JPanel recordPanel = new JPanel(new GridBagLayout());
    private final JTextField titleField = new JTextField(20);
    private final JTextField authorField = new JTextField(20);
    private final JTextField publisherField = new JTextField(20);
    private final JTextField yearField = new JTextField(6);
    private final JComboBox<String> genreBox = new JComboBox<>();
    private final JTextArea descriptionArea = new JTextArea(3, 20);

    // ---- media-type specific
    private final JPanel typeCards = new JPanel(new CardLayout());
    private final JTextField isbnField = new JTextField(20);
    private final JTextField bookPagesField = new JTextField(6);
    private final JTextField doiField = new JTextField(20);
    private final JTextField articlePagesField = new JTextField(6);
    private final JTextField issnField = new JTextField(20);
    private final JComboBox<String> periodicalTypeBox = new JComboBox<>(PERIODICAL_TYPES);
    private final JTextField periodicalPagesField = new JTextField(6);
    private final JTextField runtimeField = new JTextField(6);
    private final JComboBox<String> formatBox = new JComboBox<>(FORMATS);
    private final JTextField equipmentTypeField = new JTextField(20);

    private final JLabel errorLabel = new JLabel(" ");
    private final JButton saveButton = new JButton("Add Item");
    private final JButton cancelButton = new JButton("Cancel");

    public AddItemDialog(Window owner, CatalogRepository repo, Runnable onAdded) {
        super(owner, "Add Media Item", ModalityType.APPLICATION_MODAL);
        this.repo = repo;
        this.onAdded = onAdded;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        nameComponents();
        configureFields();

        JPanel content = new JPanel(new BorderLayout());
        content.add(buildHeader(), BorderLayout.NORTH);
        JPanel formTop = new JPanel(new BorderLayout()); // keeps every section at its natural height
        formTop.add(buildForm(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(formTop);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        setSize(560, 640);
        setMinimumSize(new Dimension(460, 400));
        setLocationRelativeTo(owner);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);

        typeBox.addActionListener(e -> typeChanged());
        locationBox.addActionListener(e -> locationChanged());
        saveButton.addActionListener(e -> save());
        cancelButton.addActionListener(e -> dispose());
        typeChanged();
        loadOptions();
    }

    // ------------------------------------------------------------------ layout

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        JLabel title = new JLabel("Add Media Item");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel hint = new JLabel("Fields marked * are required.");
        hint.setForeground(Color.GRAY);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(4, 16, 8, 16));

        JPanel item = section("Item");
        addRow(item, "Media type *", typeBox);
        addRow(item, "Barcode *", barcodeField);
        addRow(item, "Call number", callField);
        addRow(item, "Condition", conditionBox);
        addRow(item, "Online link", onlineField);
        addRow(item, "Location *", locationBox);
        newLocationRow.add(new JLabel("Floor"));
        newLocationRow.add(floorSpinner);
        newLocationRow.add(new JLabel("Area"));
        newLocationRow.add(areaField);
        newLocationRow.setVisible(false);
        addRow(item, "", newLocationRow);
        form.add(item);

        recordPanel.setBorder(BorderFactory.createTitledBorder("Description"));
        addRow(recordPanel, "Title *", titleField);
        addRow(recordPanel, "Author", authorField);
        addRow(recordPanel, "Publisher", publisherField);
        addRow(recordPanel, "Year published", yearField);
        addRow(recordPanel, "Genre", genreBox);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBorder(titleField.getBorder());
        addRow(recordPanel, "Description", descriptionArea);
        form.add(recordPanel);

        typeCards.setBorder(BorderFactory.createTitledBorder("Details"));
        typeCards.add(card(row("ISBN", isbnField), row("Pages", bookPagesField)), MediaType.BOOK.name());
        typeCards.add(card(row("DOI", doiField), row("Pages", articlePagesField)), MediaType.ARTICLE.name());
        typeCards.add(card(row("ISSN", issnField), row("Periodical type", periodicalTypeBox),
                row("Pages", periodicalPagesField)), MediaType.PERIODICAL.name());
        typeCards.add(card(row("Runtime (minutes)", runtimeField), row("Format", formatBox)),
                MediaType.VIDEO_AUDIO.name());
        typeCards.add(card(row("Equipment type *", equipmentTypeField)), MediaType.EQUIPMENT.name());
        form.add(typeCards);
        return form;
    }

    private JPanel buildButtons() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xC8C8C8)));
        errorLabel.setForeground(new Color(0xB00020));
        errorLabel.setBorder(BorderFactory.createEmptyBorder(6, 16, 0, 16));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(cancelButton);
        buttons.add(saveButton);
        bottom.add(errorLabel, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.CENTER);
        getRootPane().setDefaultButton(saveButton);
        return bottom;
    }

    private static JPanel section(String title) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        return p;
    }

    /** Adds "label  [component]" as the next row of a GridBagLayout panel. */
    private static void addRow(JPanel p, String label, JComponent field) {
        int row = p.getComponentCount() / 2;
        GridBagConstraints l = new GridBagConstraints();
        l.gridx = 0;
        l.gridy = row;
        l.anchor = GridBagConstraints.NORTHWEST;
        l.insets = new Insets(3, 6, 3, 8);
        JLabel name = new JLabel(label);
        name.setPreferredSize(new Dimension(120, name.getPreferredSize().height)); // same column in every section
        p.add(name, l);
        GridBagConstraints f = new GridBagConstraints();
        f.gridx = 1;
        f.gridy = row;
        f.weightx = 1;
        f.fill = GridBagConstraints.HORIZONTAL;
        f.insets = new Insets(3, 0, 3, 6);
        p.add(field, f);
    }

    private static JPanel row(String label, JComponent field) {
        JPanel p = new JPanel(new GridBagLayout());
        addRow(p, label, field);
        return p;
    }

    private static JPanel card(JPanel... rows) {
        JPanel p = new JPanel(new GridBagLayout());
        for (int i = 0; i < rows.length; i++) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = i;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add(rows[i], c);
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(p, BorderLayout.NORTH);
        return wrap;
    }

    private void configureFields() {
        genreBox.setEditable(true);
        conditionBox.setSelectedItem("Good");
        locationBox.addItem("Loading...");
        genreBox.addItem("");
        saveButton.setEnabled(false); // until the locations have loaded
    }

    private void nameComponents() { // names let tests find the fields
        typeBox.setName("type");
        barcodeField.setName("barcode");
        callField.setName("call");
        conditionBox.setName("condition");
        onlineField.setName("online");
        locationBox.setName("location");
        floorSpinner.setName("newFloor");
        areaField.setName("newArea");
        titleField.setName("title");
        authorField.setName("author");
        publisherField.setName("publisher");
        yearField.setName("year");
        genreBox.setName("genre");
        descriptionArea.setName("description");
        isbnField.setName("isbn");
        bookPagesField.setName("bookPages");
        doiField.setName("doi");
        articlePagesField.setName("articlePages");
        issnField.setName("issn");
        periodicalTypeBox.setName("periodicalType");
        periodicalPagesField.setName("periodicalPages");
        runtimeField.setName("runtime");
        formatBox.setName("format");
        equipmentTypeField.setName("equipmentType");
        errorLabel.setName("error");
        saveButton.setName("save");
        cancelButton.setName("cancel");
    }

    // ------------------------------------------------------------------ behavior

    private void typeChanged() {
        MediaType type = (MediaType) typeBox.getSelectedItem();
        ((CardLayout) typeCards.getLayout()).show(typeCards, type.name());
        recordPanel.setVisible(type != MediaType.EQUIPMENT);
        errorLabel.setText(" ");
    }

    private void locationChanged() {
        newLocationRow.setVisible(NEW_LOCATION.equals(locationBox.getSelectedItem()));
        revalidate();
        repaint();
    }

    /** Loads the existing locations and genres in the background (they come from the database). */
    private void loadOptions() {
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() {
                return new Object[] { repo.locations(), repo.genres() };
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                locationBox.removeAllItems();
                try {
                    Object[] loaded = get();
                    for (CatalogRepository.Location l : (List<CatalogRepository.Location>) loaded[0]) {
                        locationBox.addItem(l);
                    }
                    for (String g : (List<String>) loaded[1]) {
                        genreBox.addItem(g);
                    }
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    errorLabel.setText("Could not load locations and genres: " + t.getMessage());
                }
                locationBox.addItem(NEW_LOCATION);
                locationBox.setSelectedIndex(0);
                saveButton.setEnabled(true);
            }
        }.execute();
    }

    private void save() {
        MediaItem item;
        try {
            item = readForm();
        } catch (IllegalArgumentException e) {
            errorLabel.setText(e.getMessage());
            return;
        }
        errorLabel.setText(" ");
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                repo.addItem(item); // runs off the UI thread
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    errorLabel.setText(t.getMessage());
                    saveButton.setEnabled(true);
                    cancelButton.setEnabled(true);
                    return;
                }
                dispose();
                JOptionPane.showMessageDialog(getOwner(),
                        "Added \"" + item.displayTitle() + "\" (barcode " + item.barcode + ").",
                        "Item Added", JOptionPane.INFORMATION_MESSAGE);
                if (onAdded != null) {
                    onAdded.run();
                }
            }
        }.execute();
    }

    /** Checks the form and turns it into a MediaItem. Throws IllegalArgumentException with a readable message. */
    private MediaItem readForm() {
        MediaType type = (MediaType) typeBox.getSelectedItem();
        MediaItem m = new MediaItem(required(barcodeField, "Barcode", 50), type);
        m.callNumber = optional(callField, "Call number", 100);
        m.condition = (String) conditionBox.getSelectedItem();
        m.onlineRef = optional(onlineField, "Online link", 255);

        Object loc = locationBox.getSelectedItem();
        if (loc instanceof CatalogRepository.Location l) {
            m.floorNum = l.floor();
            m.collectionArea = l.area();
        } else if (NEW_LOCATION.equals(loc)) {
            m.floorNum = (Integer) floorSpinner.getValue();
            m.collectionArea = required(areaField, "Location area", 100);
        } else {
            throw new IllegalArgumentException("Choose a location.");
        }

        if (type != MediaType.EQUIPMENT) {
            m.title = required(titleField, "Title", 255);
            m.author = optional(authorField, "Author", 255);
            m.publisher = optional(publisherField, "Publisher", 255);
            m.pubYear = number(yearField, "Year published", 1, LocalDate.now().getYear() + 1);
            m.genre = blankToNull(comboText(genreBox), "Genre", 100);
            m.description = blankToNull(descriptionArea.getText(), "Description", 5000);
        }

        switch (type) {
            case BOOK -> {
                m.isbn = optional(isbnField, "ISBN", 50);
                m.pageCount = number(bookPagesField, "Pages", 1, 100000);
            }
            case ARTICLE -> {
                m.doi = optional(doiField, "DOI", 100);
                m.pageCount = number(articlePagesField, "Pages", 1, 100000);
            }
            case PERIODICAL -> {
                m.issn = optional(issnField, "ISSN", 50);
                m.periodicalType = (String) periodicalTypeBox.getSelectedItem();
                m.pageCount = number(periodicalPagesField, "Pages", 1, 100000);
            }
            case VIDEO_AUDIO -> {
                m.runtimeMinutes = number(runtimeField, "Runtime", 1, 100000);
                m.formatType = (String) formatBox.getSelectedItem();
            }
            case EQUIPMENT -> m.equipmentType = required(equipmentTypeField, "Equipment type", 50);
        }
        return m;
    }

    // ------------------------------------------------------------------ validation helpers

    private static String required(JTextComponent f, String label, int max) {
        String v = f.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return blankToNull(v, label, max);
    }

    private static String optional(JTextComponent f, String label, int max) {
        return blankToNull(f.getText(), label, max);
    }

    private static String blankToNull(String text, String label, int max) {
        String v = text == null ? "" : text.trim();
        if (v.length() > max) {
            throw new IllegalArgumentException(label + " is too long (at most " + max + " characters).");
        }
        return v.isEmpty() ? null : v;
    }

    private static Integer number(JTextComponent f, String label, int min, int max) {
        String v = f.getText().trim();
        if (v.isEmpty()) {
            return null;
        }
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) {
                throw new NumberFormatException();
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a whole number from " + min + " to " + max + ".");
        }
    }

    private static String comboText(JComboBox<String> box) {
        Object v = box.getEditor().getItem();
        return v == null ? "" : v.toString();
    }
}
