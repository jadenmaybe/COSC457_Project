package com.example;

/** The two kinds of people who can sign in. */
public enum Role {
    USER("Library User"),
    ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
