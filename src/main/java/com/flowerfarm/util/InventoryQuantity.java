package com.flowerfarm.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Exact decimal stock arithmetic, with whole counts and fractional measured units. */
public final class InventoryQuantity {
    public static final int SCALE = 6;
    public static final double MAX_VALUE = Integer.MAX_VALUE;

    private InventoryQuantity() { }

    public static BigDecimal decimal(double value) {
        if (!Double.isFinite(value) || value < 0 || value > MAX_VALUE) {
            throw new IllegalArgumentException("Quantity must be a finite number between 0 and "
                    + Integer.MAX_VALUE + ".");
        }
        try {
            return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException tooPrecise) {
            throw new IllegalArgumentException("Quantity supports up to six decimal places; no weight was rounded.",
                    tooPrecise);
        }
    }

    public static double requireForUnit(double value, String unit) {
        BigDecimal amount = decimal(value);
        if (!InventoryUnits.measured(unit) && amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Quantity for " + unit + " must be a whole number. "
                    + "Use a weight or volume unit for measured products.");
        }
        return amount.doubleValue();
    }

    public static double add(double left, double right) {
        return decimal(decimal(left).add(decimal(right)).doubleValue()).doubleValue();
    }

    public static int whole(double value) {
        return decimal(requireForUnit(value, "each")).intValueExact();
    }

    public static double parse(Object raw) {
        if (raw == null || raw.toString().isBlank()) return 0;
        try {
            return decimal(Double.parseDouble(raw.toString().trim())).doubleValue();
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Quantity must be numeric; no stock value was substituted.", invalid);
        }
    }

    /** Preserve legacy whole-number receipt fingerprints while supporting measured quantities. */
    public static Number wireNumber(double value) {
        BigDecimal amount = decimal(value).stripTrailingZeros();
        return amount.scale() <= 0 ? Integer.valueOf(amount.intValueExact()) : amount;
    }

    public static boolean validPositive(double value, String unit) {
        try { return requireForUnit(value, unit) > 0; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    public static double subtract(double left, double right) {
        return decimal(decimal(left).subtract(decimal(right)).doubleValue()).doubleValue();
    }

    public static double shortage(double needed, double available) {
        return available >= needed ? 0 : subtract(needed, available);
    }

    public static <T> String summary(java.util.List<T> rows,
            java.util.function.ToDoubleFunction<T> quantity,
            java.util.function.Function<T, String> unit) {
        java.util.Map<String, BigDecimal> totals = new java.util.TreeMap<>();
        for (T row : rows) {
            totals.merge(InventoryUnits.canonical(unit.apply(row)),
                    BigDecimal.valueOf(quantity.applyAsDouble(row)), BigDecimal::add);
        }
        return totals.entrySet().stream()
                .map(entry -> entry.getValue().stripTrailingZeros().toPlainString() + " " + entry.getKey())
                .collect(java.util.stream.Collectors.joining(" · "));
    }

    public static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** Keep the established harvest CSV display while retaining every measured decimal. */
    public static String csv(double value) {
        BigDecimal amount = BigDecimal.valueOf(value).stripTrailingZeros();
        return amount.setScale(Math.max(2, amount.scale())).toPlainString();
    }
}
