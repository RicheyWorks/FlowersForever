package com.flowerfarm.util;

import java.util.Locale;

/** Shared comparison rules for worker-facing order and inventory unit labels. */
public final class InventoryUnits {

    private InventoryUnits() {
    }

    public static boolean equivalent(String left, String right) {
        return canonical(left).equals(canonical(right));
    }

    /** Fractions are meaningful for these measures; packaged items remain whole counts. */
    public static boolean measured(String unit) {
        return switch (canonical(unit)) {
            case "gram", "kilogram", "ounce", "pound", "hundredweight", "ton", "bushel",
                    "gallon", "liter", "milliliter", "milligram", "foot", "inch", "hour" -> true;
            default -> false;
        };
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
            case "clone", "clones" -> "clone";
            case "head", "heads" -> "head";
            case "egg", "eggs" -> "egg";
            case "bird", "birds" -> "bird";
            case "tree", "trees" -> "tree";
            case "jar", "jars" -> "jar";
            case "colony", "colonies" -> "colony";
            case "bottle", "bottles" -> "bottle";
            case "bulb", "bulbs" -> "bulb";
            case "pot", "pots" -> "pot";
            case "packet", "packets" -> "packet";
            case "bucket", "buckets" -> "bucket";
            case "crate", "crates" -> "crate";
            case "each", "ea", "item", "items", "unit", "units" -> "each";
            case "dozen", "dozens" -> "dozen";
            case "weight (lb)", "pound", "pounds", "lb", "lbs" -> "pound";
            case "ounce", "ounces", "oz" -> "ounce";
            case "gram", "grams", "g" -> "gram";
            case "milligram", "milligrams", "mg" -> "milligram";
            case "milliliter", "milliliters", "millilitre", "millilitres", "ml" -> "milliliter";
            case "hour", "hours", "hr" -> "hour";
            case "kilogram", "kilograms", "kg" -> "kilogram";
            case "hundredweight", "hundredweights", "cwt" -> "hundredweight";
            case "ton", "tons" -> "ton";
            case "bushel", "bushels" -> "bushel";
            case "bin", "bins" -> "bin";
            case "bag", "bags" -> "bag";
            case "case", "cases" -> "case";
            case "box", "boxes" -> "box";
            case "carton", "cartons" -> "carton";
            case "sack", "sacks" -> "sack";
            case "tote", "totes" -> "tote";
            case "flat", "flats" -> "flat";
            case "bale", "bales" -> "bale";
            case "pack", "packs" -> "pack";
            case "gallon", "gallons" -> "gallon";
            case "liter", "liters", "litre", "litres" -> "liter";
            case "foot", "feet", "ft" -> "foot";
            case "inch", "inches", "in" -> "inch";
            default -> unit;
        };
    }
}
