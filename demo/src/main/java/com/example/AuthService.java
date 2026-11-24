package com.example;

/**
 * How the login screen checks credentials. The UI only talks to this interface,
 * so swapping the mock for a real database check later changes no UI code.
 */
public interface AuthService {

    /** Returns a Session if the credentials are valid; throws AuthException (with a user-readable message) if not. */
    Session login(Role role, String username, char[] password) throws AuthException;

    class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }
}
