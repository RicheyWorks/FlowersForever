package com.flowerfarm.service;

import java.util.Objects;

/** One plain-language farm reminder a grower can open from Today’s list. */
public record GrowerReminder(Kind kind, String title, String detail, String targetTab) {

    public enum Kind {
        FIELD_TASK,
        HARVEST,
        PACK,
        STOCK,
        KNOWLEDGE,
        CHECKLIST
    }

    public GrowerReminder {
        kind = Objects.requireNonNull(kind, "kind");
        title = requireText(title, "title");
        detail = detail == null ? "" : detail.trim();
        targetTab = requireText(targetTab, "targetTab");
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
