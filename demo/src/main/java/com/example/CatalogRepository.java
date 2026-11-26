package com.example;

import java.util.List;

/**
 * Where the Catalog screen gets its items. The screen only knows this interface, so the in-memory sample
 * version can later be replaced by a MySQL version without touching any screen code.
 * (Database calls can be slow: when that version exists, the screen should call it from a SwingWorker.)
 */
public interface CatalogRepository {

    /** Items matching the query, sorted by title. */
    List<MediaItem> search(CatalogQuery query);

    /** Items matching the query, with reservation ownership resolved for one user. */
    default List<MediaItem> searchForUser(CatalogQuery query, String username) {
        return search(query);
    }

    /** One item by barcode, or null if there is none. */
    MediaItem findByBarcode(String barcode);

    /** Distinct genres, for the genre filter. */
    List<String> genres();

    /** Every shelf location that exists (Location table), for the Add Item form. */
    List<Location> locations();

    /**
     * Adds a new item. It starts out Available. Throws IllegalStateException with a user-readable message if it
     * can't be added (for example, the barcode is already taken).
     */
    void addItem(MediaItem item);

    /** Updates an existing item's editable details without changing its barcode or checkout status. */
    void updateItem(MediaItem item);

    /** A shelf location: Location.Floor_Num + Location.Collection_Area. */
    record Location(int floor, String area) {
        @Override
        public String toString() {
            return "Floor " + floor + ", " + area;
        }
    }
}
