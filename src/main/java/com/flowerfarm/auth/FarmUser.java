package com.flowerfarm.auth;

/**
 * In-memory barn user account.
 */
public record FarmUser(String username, String password, FarmRole role) {

    public FarmUser {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username required");
        }
        username = username.trim();
        if (username.length() > 64) {
            throw new IllegalArgumentException("username cannot exceed 64 characters");
        }
        if (password == null) {
            password = "";
        }
        if (password.length() > 256) {
            throw new IllegalArgumentException("password cannot exceed 256 characters");
        }
        if (role == null) {
            role = FarmRole.HAND;
        }
    }

    @Override
    public String toString() {
        return username + " (" + role + ")";
    }
}
