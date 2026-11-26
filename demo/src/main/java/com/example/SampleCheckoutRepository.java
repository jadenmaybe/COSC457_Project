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
    private final List<ReservationRecord> reservations = new ArrayList<>();

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

    @Override
    public List<CheckoutRecord> allRecords() {
        return records.stream()
                .sorted(Comparator.comparing((CheckoutRecord r) -> r.dueOn))
                .collect(Collectors.toList());
    }

    @Override
    public List<ReservationRecord> reservationsFor(String username) {
        return reservations.stream()
                .filter(r -> r.borrower.equalsIgnoreCase(username))
                .sorted(Comparator.comparing((ReservationRecord r) -> r.reservedOn))
                .collect(Collectors.toList());
    }

    @Override
    public CheckoutRecord checkOut(String username, MediaItem item) {
        if (!item.isAvailable()) {
            throw new IllegalStateException("This item is not available to check out.");
        }
        // Checking out a book you had reserved fulfils the reservation.
        reservations.removeIf(r -> r.borrower.equalsIgnoreCase(username) && r.item.barcode.equals(item.barcode));
        LocalDate today = LocalDate.now();
        CheckoutRecord record = new CheckoutRecord(username, item, today, today.plusDays(LOAN_DAYS), null);
        records.add(record);
        item.status = ItemStatus.CHECKED_OUT;
        return record;
    }

    @Override
    public void markReturned(CheckoutRecord record) {
        if (!records.contains(record)) {
            throw new IllegalArgumentException("Checkout record does not belong to this repository");
        }
        if (!record.isOpen()) {
            throw new IllegalStateException("Checkout record has already been returned");
        }

        record.returnedOn = LocalDate.now();
        record.item.status = ItemStatus.AVAILABLE;
    }

    @Override
    public LocalDate reserve(String username, MediaItem item) {
        if (item.mediaType != MediaType.BOOK) {
            throw new IllegalStateException("Only books can be reserved.");
        }
        List<CheckoutRecord> open = records.stream()
                .filter(r -> r.item.barcode.equals(item.barcode) && r.isOpen())
                .sorted(Comparator.comparing((CheckoutRecord r) -> r.dueOn))
                .collect(Collectors.toList());
        if (open.isEmpty()) {
            throw new IllegalStateException("Nobody has this book checked out, so it can't be reserved. Check it out instead.");
        }
        if (open.stream().anyMatch(r -> r.borrower.equalsIgnoreCase(username))) {
            throw new IllegalStateException("You already have this book checked out, so you can't reserve it.");
        }
        if (reservations.stream().anyMatch(
                r -> r.borrower.equalsIgnoreCase(username) && r.item.barcode.equals(item.barcode))) {
            throw new IllegalStateException("You have already reserved this book.");
        }
        reservations.add(new ReservationRecord(username, item, LocalDate.now()));
        return open.get(0).dueOn;
    }
}
