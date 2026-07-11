package com.flowerfarm.auth;

/**
 * In-memory barn user account.
 */
public record FarmUser(String username, String password, FarmRole role) {

    public FarmUser {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username required");
        }
        if (password == null) {
            password = "";
        }
        if (role == null) {
            role = FarmRole.HAND;
        }
        username = username.trim();
    }

    @Override
    public String toString() {
        return username + " (" + role + ")";
    }
}
