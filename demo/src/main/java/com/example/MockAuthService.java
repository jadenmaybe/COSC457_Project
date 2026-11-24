package com.example;

/** Placeholder login: accepts any non-blank username and password. TODO: replace with a database lookup. */
public class MockAuthService implements AuthService {

    @Override
    public Session login(Role role, String username, char[] password) throws AuthException {
        if (username == null || username.isBlank()) {
            throw new AuthException("Please enter your username.");
        }
        if (password == null || password.length == 0) {
            throw new AuthException("Please enter your password.");
        }
        return new Session(role, username.trim());
    }
}
