package com.example;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One time one person borrowed one item. Database: a row of Check_Out
 * (UserNum, ItemBarcode, Checkout_Date, Due_Date, Return_Date; the item details come from joining Item/Record_Info).
 */
public class CheckoutRecord {
    public final String borrower;        // who has/had it (the user's full name)
    public final Integer borrowerId;     // User.ID_Num / Check_Out.UserNum; null when unknown (sample data)
    public final MediaItem item;
    public final LocalDate checkedOutOn;
    public final LocalDate dueOn;
    public LocalDate returnedOn;         // null until it is returned

    public CheckoutRecord(String borrower, MediaItem item, LocalDate checkedOutOn, LocalDate dueOn,
                          LocalDate returnedOn) {
        this(borrower, null, item, checkedOutOn, dueOn, returnedOn);
    }

    public CheckoutRecord(String borrower, Integer borrowerId, MediaItem item, LocalDate checkedOutOn,
                          LocalDate dueOn, LocalDate returnedOn) {
        this.borrower = borrower;
        this.borrowerId = borrowerId;
        this.item = item;
        this.checkedOutOn = checkedOutOn;
        this.dueOn = dueOn;
        this.returnedOn = returnedOn;
    }

    /** Worked out from the dates, so it can never disagree with them. */
    public CheckoutStatus status() {
        if (returnedOn != null) {
            return CheckoutStatus.RETURNED;
        }
        return dueOn.isBefore(LocalDate.now()) ? CheckoutStatus.OVERDUE : CheckoutStatus.CHECKED_OUT;
    }

    public boolean isOpen() {
        return returnedOn == null;
    }

    /** Whole days from today until the due date (negative when past due). */
    public long daysUntilDue() {
        return ChronoUnit.DAYS.between(LocalDate.now(), dueOn);
    }

    /** "5 days", "Today", "3 days overdue", or blank once returned. */
    public String dueInText() {
        if (!isOpen()) {
            return "";
        }
        long d = daysUntilDue();
        if (d == 0) {
            return "Today";
        }
        long n = Math.abs(d);
        String unit = n == 1 ? " day" : " days";
        return d > 0 ? n + unit : n + unit + " overdue";
    }
}
