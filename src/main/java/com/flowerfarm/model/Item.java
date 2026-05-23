package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single inventory item in the flower farm.
 * Immutable fields are validated at construction time.
 * Supports JSON serialization via Jackson and CSV export via toCsv().
 */
public class Item {

    private String name;
    private String category;
    private double price;
    private String unit;
    private double cost;
    private int quantity;
    private String notes;

    /**
     * Full constructor with validation. Used by Jackson for JSON deserialization
     * and throughout the application for manual construction.
     */
    @JsonCreator
    public Item(
            @JsonProperty("name")     String name,
            @JsonProperty("category") String category,
            @JsonProperty("price")    double price,
            @JsonProperty("unit")     String unit,
            @JsonProperty("cost")     double cost,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("notes")    String notes) {

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Item name cannot be null or empty.");
        }
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        if (cost < 0) {
            throw new IllegalArgumentException("Cost cannot be negative.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }

        this.name     = name.trim();
        this.category = (category != null && !category.trim().isEmpty()) ? category.trim() : "Other";
        this.price    = price;
        this.unit     = (unit != null && !unit.trim().isEmpty()) ? unit.trim() : "Per Unit";
        this.cost     = cost;
        this.quantity = quantity;
        this.notes    = (notes != null) ? notes.trim() : "";
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getName()     { return name; }
    public String getCategory() { return category; }
    public double getPrice()    { return price; }
    public String getUnit()     { return unit; }
    public double getCost()     { return cost; }
    public int    getQuantity() { return quantity; }
    public String getNotes()    { return notes; }

    // ── Setters (needed for Jackson deserialization in some contexts) ───────────

    public void setName(String name)         { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setPrice(double price)       { this.price = price; }
    public void setUnit(String unit)         { this.unit = unit; }
    public void setCost(double cost)         { this.cost = cost; }
    public void setQuantity(int quantity)    { this.quantity = quantity; }
    public void setNotes(String notes)       { this.notes = notes; }

    // ── CSV ────────────────────────────────────────────────────────────────────

    /**
     * Serializes this item to a single CSV line.
     * Notes are quoted to handle embedded commas.
     */
    public String toCsv() {
        return String.format("%s,%s,%.2f,%s,%.2f,%d,\"%s\"",
                name,
                category,
                price,
                unit,
                cost,
                quantity,
                notes.replace("\"", "\"\""));   // escape internal quotes
    }

    @Override
    public String toString() {
        return String.format(
                "Item{name='%s', category='%s', price=%.2f, unit='%s', cost=%.2f, quantity=%d, notes='%s'}",
                name, category, price, unit, cost, quantity, notes);
    }
}
