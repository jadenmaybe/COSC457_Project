package com.example;

/** Who is signed in. Later screens read this to know which user's data to show. */
public final class Session {
    private final Role role;
    private final String username;

    public Session(Role role, String username) {
        this.role = role;
        this.username = username;
    }

    public Role role() {
        return role;
    }

    public String username() {
        return username;
    }
}
