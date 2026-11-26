package com.example;

import java.time.LocalDate;

/**
 * One user's active reservation of one book. Database: a row of Reserve with Status 'Reserved'
 * (UserNum, ItemBarcode, Reserve_Date; the item details come from joining Item/Record_Info).
 */
public class ReservationRecord {
    public final String borrower;        // who reserved it (the user's full name, like CheckoutRecord.borrower)
    public final MediaItem item;
    public final LocalDate reservedOn;

    public ReservationRecord(String borrower, MediaItem item, LocalDate reservedOn) {
        this.borrower = borrower;
        this.item = item;
        this.reservedOn = reservedOn;
    }

    public CheckoutStatus status() {
        return CheckoutStatus.RESERVED;
    }
}
