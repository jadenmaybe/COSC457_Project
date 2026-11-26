package com.example;

import java.awt.Color;

/**
 * Where a single checkout stands. Database: Check_Out.Status ('Checked Out', 'Overdue', 'Returned'); Reserved comes from Reserve.Status.
 * (Not the same as ItemStatus, which describes the item itself in the catalog.)
 */
public enum CheckoutStatus {
    CHECKED_OUT("Checked Out", new Color(0x9A6700)),
    OVERDUE("Overdue", new Color(0xB00020)),
    RETURNED("Returned", new Color(0x1B7F3B)),
    RESERVED("Reserved", new Color(0x6639BA));

    private final String label;
    private final Color color;

    CheckoutStatus(String label, Color color) {
        this.label = label;
        this.color = color;
    }

    public Color color() {
        return color;
    }

    @Override
    public String toString() {
        return label;
    }
}
