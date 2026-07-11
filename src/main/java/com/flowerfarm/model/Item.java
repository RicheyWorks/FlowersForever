package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/**
 * Represents a single inventory item in the flower farm.
 *
 * <p>Persisted via JPA (H2 file by default). Fields are validated on
 * construction and setters. Supports JSON via Jackson and CSV via {@link #toCsv()}.
 */
@Entity
@Table(name = "inventory_items")
public class Item {

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
        return name.trim();
    }

    private static String normalizeCategory(String category) {
        return (category != null && !category.trim().isEmpty()) ? category.trim() : "Other";
    }

    private static String normalizeUnit(String unit) {
        return (unit != null && !unit.trim().isEmpty()) ? unit.trim() : "Per Unit";
    }

    private static String normalizeNotes(String notes) {
        return (notes != null) ? notes.trim() : "";
    }

    private static double requireNonNegative(double value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " cannot be negative.");
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
        return String.format("%s,%s,%.2f,%s,%.2f,%d,\"%s\"",
                name,
                category,
                price,
                unit,
                cost,
                quantity,
                notes.replace("\"", "\"\""));
    }

    @Override
    public String toString() {
        return String.format(
                "Item{id=%s, name='%s', category='%s', price=%.2f, unit='%s', cost=%.2f, quantity=%d, notes='%s'}",
                id, name, category, price, unit, cost, quantity, notes);
    }
}
