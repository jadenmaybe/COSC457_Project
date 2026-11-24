package com.example;

/**
 * Whether an item can be borrowed right now.
 * Database: Item.Status ('Available', 'Checked Out', 'On Hold'). Overdue is really a Check_Out
 * state (open checkout with a past due date), shown here because users and admins both see it.
 */
public enum ItemStatus {
    AVAILABLE("Available"),
    CHECKED_OUT("Checked Out"),
    ON_HOLD("On Hold"),
    OVERDUE("Overdue");

    private final String label;

    ItemStatus(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
