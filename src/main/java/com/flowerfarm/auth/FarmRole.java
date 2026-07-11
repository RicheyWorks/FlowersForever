package com.flowerfarm.auth;

/**
 * Roles for shared barn / multi-user installs.
 */
public enum FarmRole {
    /** Full access: inventory, harvest, CRM, connectors, reports, admin ops. */
    OWNER,
    /** Day-to-day ops: inventory, harvest, CRM, visualizer; no audit clear. */
    HAND,
    /** Read-only REST + view-oriented GUI (no writes / no clear history). */
    VIEWER;

    public String springRole() {
        return name();
    }

    /** Human label for UI badges. */
    public String label() {
        return switch (this) {
            case OWNER -> "Owner";
            case HAND -> "Hand";
            case VIEWER -> "Viewer";
        };
    }

    public String shortDescription() {
        return switch (this) {
            case OWNER -> "Full access (incl. clear audit history)";
            case HAND -> "Read + write farm ops";
            case VIEWER -> "Read-only — no saves or connector writes";
        };
    }

    public boolean canWrite() {
        return this == OWNER || this == HAND;
    }

    public boolean canClearHistory() {
        return this == OWNER;
    }

    public static FarmRole fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return HAND;
        }
        try {
            return FarmRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HAND;
        }
    }
}
