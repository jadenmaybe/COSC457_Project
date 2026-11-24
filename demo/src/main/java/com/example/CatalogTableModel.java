package com.example;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Feeds the catalog table. To show different columns, edit COLUMNS and valueAt() together. */
public class CatalogTableModel extends AbstractTableModel {
    // TODO: change which columns the catalog shows once you've settled on the information types.
    public static final String[] COLUMNS = {
            "Barcode", "Title", "Author", "Media Type", "Genre", "Year", "Location", "Status"};
    public static final int STATUS_COLUMN = 7;

    private final Role viewer;   // decides what status the viewer may see (only admins see Overdue)
    private List<MediaItem> items = new ArrayList<>();

    public CatalogTableModel(Role viewer) {
        this.viewer = viewer;
    }

    public void setItems(List<MediaItem> newItems) {
        items = new ArrayList<>(newItems);
        fireTableDataChanged();
    }

    public MediaItem itemAt(int modelRow) {
        return items.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return items.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int col) {
        return COLUMNS[col];
    }

    @Override
    public Class<?> getColumnClass(int col) {
        return switch (col) {
            case 5 -> Integer.class;
            case STATUS_COLUMN -> ItemStatus.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int col) {
        MediaItem i = items.get(row);
        return switch (col) {
            case 0 -> i.barcode;
            case 1 -> i.displayTitle();
            case 2 -> i.author;
            case 3 -> i.mediaType.toString();
            case 4 -> i.genre;
            case 5 -> i.pubYear;
            case 6 -> i.locationText();
            case 7 -> i.statusFor(viewer);
            default -> null;
        };
    }
}
