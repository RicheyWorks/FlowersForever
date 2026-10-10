package com.flowerfarm.automation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Locale;
import java.util.Objects;

/** Typed signal a device or application publishes in snapshot metadata. */
public record HarnessObservationSpec(String name, HarnessValueType type,
                                     String description,
                                     @JsonInclude(JsonInclude.Include.NON_NULL)
                                     String unit,
                                     boolean sensitive) {

    public HarnessObservationSpec {
        name = slug(name);
        type = Objects.requireNonNull(type, "type");
        description = required(description, "observation description");
        unit = unit == null || unit.isBlank() ? null : unit.trim();
    }

    private static String slug(String value) {
        String normalized = required(value, "observation name")
                .toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9-]{0,39}")) {
            throw new IllegalArgumentException(
                    "observation name must be a 1-40 character lowercase slug");
        }
        return normalized;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
