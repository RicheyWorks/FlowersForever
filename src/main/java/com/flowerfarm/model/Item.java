package com.flowerfarm.model;

import com.flowerfarm.util.CsvSupport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.util.Locale;

/**
 * Represents a single inventory item in the flower farm.
 *
 * <p>Persisted via JPA (H2 file by default). Fields are validated on
 * construction and setters. Supports JSON via Jackson and CSV via {@link #toCsv()}.
 */
@Entity
@Table(name = "inventory_items")
public class Item {

    private static final double MAX_MONEY_VALUE = 1_000_000_000.0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 128)
    private String category;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false, length = 64)
    private String unit;

    @Column(nullable = false)
    private double cost;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 2000)
    private String notes;

    /** JPA only. */
    protected Item() {
    }

    /**
     * Convenience constructor used throughout the app and tests (no id yet).
     */
    public Item(String name, String category, double price, String unit,
                double cost, int quantity, String notes) {
        this(null, name, category, price, unit, cost, quantity, notes);
    }

    /**
     * Full constructor with optional id. Used by Jackson and JPA hydration paths.
     */
    @JsonCreator
    public Item(
            @JsonProperty("id")       Long id,
            @JsonProperty("name")     String name,
            @JsonProperty("category") String category,
            @JsonProperty("price")    double price,
            @JsonProperty("unit")     String unit,
            @JsonProperty("cost")     double cost,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("notes")    String notes) {

        this.id       = id;
        this.name     = requireName(name);
        this.category = normalizeCategory(category);
        this.price    = requireNonNegative(price, "Price");
        this.unit     = normalizeUnit(unit);
        this.cost     = requireNonNegative(cost, "Cost");
        this.quantity = requireNonNegativeQuantity(quantity);
        this.notes    = normalizeNotes(notes);
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public Long   getId()       { return id; }
    public String getName()     { return name; }
    public String getCategory() { return category; }
    public double getPrice()    { return price; }
    public String getUnit()     { return unit; }
    public double getCost()     { return cost; }
    public int    getQuantity() { return quantity; }
    public String getNotes()    { return notes; }

    // ── Setters ────────────────────────────────────────────────────────────────

    public void setId(Long id)                   { this.id = id; }
    public void setName(String name)             { this.name = requireName(name); }
    public void setCategory(String category)     { this.category = normalizeCategory(category); }
    public void setPrice(double price)           { this.price = requireNonNegative(price, "Price"); }
    public void setUnit(String unit)             { this.unit = normalizeUnit(unit); }
    public void setCost(double cost)             { this.cost = requireNonNegative(cost, "Cost"); }
    public void setQuantity(int quantity)        { this.quantity = requireNonNegativeQuantity(quantity); }
    public void setNotes(String notes)           { this.notes = normalizeNotes(notes); }

    /**
     * Copies business fields from {@code source} onto this entity, keeping this id.
     */
    public void copyBusinessFieldsFrom(Item source) {
        if (source == null) {
            throw new IllegalArgumentException("Source item must not be null.");
        }
        setName(source.getName());
        setCategory(source.getCategory());
        setPrice(source.getPrice());
        setUnit(source.getUnit());
        setCost(source.getCost());
        setQuantity(source.getQuantity());
        setNotes(source.getNotes());
    }

    // ── Validation helpers ─────────────────────────────────────────────────────

    private static String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name cannot be null or empty.");
        }
        return requireMaxLength(name.trim(), 255, "Item name");
    }

    private static String normalizeCategory(String category) {
        String normalized = (category != null && !category.trim().isEmpty())
                ? category.trim() : "Other";
        return requireMaxLength(normalized, 128, "Category");
    }

    private static String normalizeUnit(String unit) {
        String normalized = (unit != null && !unit.trim().isEmpty())
                ? unit.trim() : "Per Unit";
        return requireMaxLength(normalized, 64, "Unit");
    }

    private static String normalizeNotes(String notes) {
        return requireMaxLength(notes != null ? notes.trim() : "", 2000, "Notes");
    }

    private static double requireNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0 || value > MAX_MONEY_VALUE) {
            throw new IllegalArgumentException(field + " must be a finite number between 0 and "
                    + (long) MAX_MONEY_VALUE + ".");
        }
        return value;
    }

    private static String requireMaxLength(String value, int max, String field) {
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return value;
    }

    private static int requireNonNegativeQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        return quantity;
    }

    // ── CSV ────────────────────────────────────────────────────────────────────

    public String toCsv() {
        return CsvSupport.row(',',
                name,
                category,
                String.format(Locale.ROOT, "%.2f", price),
                unit,
                String.format(Locale.ROOT, "%.2f", cost),
                quantity,
                notes);
    }

    @Override
    public String toString() {
        return String.format(
                "Item{id=%s, name='%s', category='%s', price=%.2f, unit='%s', cost=%.2f, quantity=%d, notes='%s'}",
                id, name, category, price, unit, cost, quantity, notes);
    }
}
