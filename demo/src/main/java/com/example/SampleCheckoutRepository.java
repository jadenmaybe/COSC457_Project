package com.example;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hard-coded sample checkouts for the demo users "alice" and "bob". Dates are relative to today, so the overdue
 * item stays overdue whenever you run the app. They line up with the sample catalog's checked-out/overdue items.
 * TODO: replace with a MySQL-backed version.
 */
public class SampleCheckoutRepository implements CheckoutRepository {
    private final List<CheckoutRecord> records = new ArrayList<>();

    public SampleCheckoutRepository(CatalogRepository catalog) {
        // borrower, barcode, checked out N days ago, due in N days (negative = past due), returned N days ago (or null)
        add(catalog, "alice", "BC100", 9, 5, null);      // 1984: checked out, due soon
        add(catalog, "alice", "BC111", 12, 2, null);     // Laptop: checked out
        add(catalog, "alice", "BC112", 40, -26, 28);     // The Hobbit: returned
        add(catalog, "alice", "BC107", 70, -56, 60);     // Pride and Prejudice: returned
        add(catalog, "bob", "BC101", 28, -14, null);     // A Brief History of Time: OVERDUE
        add(catalog, "bob", "BC108", 5, 9, null);        // Sapiens: checked out
        add(catalog, "bob", "BC109", 35, -21, 24);       // Inception: returned
    }

    private void add(CatalogRepository catalog, String borrower, String barcode, int outDaysAgo, int dueInDays,
                     Integer returnedDaysAgo) {
        MediaItem item = catalog.findByBarcode(barcode);
        if (item == null) {
            throw new IllegalStateException("Sample checkout refers to unknown barcode " + barcode);
        }
        LocalDate today = LocalDate.now();
        records.add(new CheckoutRecord(borrower, item, today.minusDays(outDaysAgo), today.plusDays(dueInDays),
                returnedDaysAgo == null ? null : today.minusDays(returnedDaysAgo)));
    }

    @Override
    public List<CheckoutRecord> forUser(String username) {
        return records.stream()
                .filter(r -> r.borrower.equalsIgnoreCase(username))
                .sorted(Comparator.comparing((CheckoutRecord r) -> r.dueOn))
                .collect(Collectors.toList());
    }
}
