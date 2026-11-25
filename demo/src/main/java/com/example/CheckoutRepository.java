package com.example;

import java.util.List;

/**
 * Where checkout records come from. The screens only know this interface, so the in-memory sample can later be
 * replaced by a MySQL version. More methods will be added as screens need them
 * (all records for the admin tab, checking an item out, marking it returned).
 */
public interface CheckoutRepository {

    /** Every checkout (open and returned) for one borrower, soonest due date first. */
    List<CheckoutRecord> forUser(String username);

    /** Every checkout (open and returned), soonest due date first. */
    List<CheckoutRecord> allRecords();

    /** How long a borrowed item may be kept before it is overdue. */
    int LOAN_DAYS = 14;

    /**
     * Checks an available item out to a borrower (due LOAN_DAYS from today) and marks the item Checked Out.
     * Throws IllegalStateException with a user-readable message if it can't be done (item no longer available,
     * borrow limit reached, unknown account, ...).
     */
    CheckoutRecord checkOut(String username, MediaItem item);

    /** Marks an open checkout as returned. */
    void markReturned(CheckoutRecord record);
}
