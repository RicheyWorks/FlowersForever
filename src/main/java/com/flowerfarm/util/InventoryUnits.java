package com.flowerfarm.util;

import java.util.Locale;

/** Shared comparison rules for worker-facing order and inventory unit labels. */
public final class InventoryUnits {

    private InventoryUnits() {
    }

    public static boolean equivalent(String left, String right) {
        return canonical(left).equals(canonical(right));
    }

    public static String canonical(String value) {
        String unit = value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("\\s+", " ");
        if (unit.startsWith("per ")) {
            unit = unit.substring(4).trim();
        }
        return switch (unit) {
            case "stem", "stems" -> "stem";
            case "bunch", "bunches" -> "bunch";
            case "bouquet", "bouquets" -> "bouquet";
            case "tray", "trays" -> "tray";
            case "plant", "plants" -> "plant";
            case "bulb", "bulbs" -> "bulb";
            case "pot", "pots" -> "pot";
            case "packet", "packets" -> "packet";
            case "bucket", "buckets" -> "bucket";
            case "crate", "crates" -> "crate";
            case "each", "ea", "item", "items", "unit", "units" -> "each";
            case "dozen", "dozens" -> "dozen";
            case "pound", "pounds", "lb", "lbs" -> "pound";
            case "ounce", "ounces", "oz" -> "ounce";
            case "gram", "grams", "g" -> "gram";
            case "kilogram", "kilograms", "kg" -> "kilogram";
            case "foot", "feet", "ft" -> "foot";
            case "inch", "inches", "in" -> "inch";
            default -> unit;
        };
    }
}
