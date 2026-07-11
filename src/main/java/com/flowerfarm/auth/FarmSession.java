package com.flowerfarm.auth;

/**
 * Holds the currently logged-in barn user for the desktop session.
 * When auth is disabled, role defaults to OWNER (full local access).
 */
public final class FarmSession {

    private static volatile FarmUser current;
    private static volatile boolean authEnabled;

    private FarmSession() {}

    public static void setAuthEnabled(boolean enabled) {
        authEnabled = enabled;
    }

    public static boolean isAuthEnabled() {
        return authEnabled;
    }

    public static void set(FarmUser user) {
        current = user;
    }

    public static void clear() {
        current = null;
    }

    public static FarmUser current() {
        return current;
    }

    public static boolean isAuthenticated() {
        return current != null;
    }

    public static FarmRole role() {
        if (!authEnabled) {
            return FarmRole.OWNER;
        }
        return current == null ? FarmRole.VIEWER : current.role();
    }

    public static boolean isOwner() {
        return role() == FarmRole.OWNER;
    }

    public static boolean isAtLeastHand() {
        return role().canWrite();
    }

    public static boolean canMutateData() {
        return role().canWrite();
    }

    public static boolean canClearHistory() {
        return role().canClearHistory();
    }

    public static String displayName() {
        if (!authEnabled) {
            return "local (auth off)";
        }
        if (current == null) {
            return "not signed in";
        }
        return current.username() + " · " + current.role().label();
    }

    public static String roleHint() {
        return role().shortDescription();
    }
}
