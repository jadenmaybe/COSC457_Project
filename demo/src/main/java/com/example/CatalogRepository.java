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

    /** One item by barcode, or null if there is none. */
    MediaItem findByBarcode(String barcode);

    /** Distinct genres, for the genre filter. */
    List<String> genres();
}
