package com.flowerfarm.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Reusable bouquet, event, or wholesale design recipe with economic assumptions. */
@Entity
@Table(name = "floral_recipes")
public class FloralRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "design_type", nullable = false, length = 64)
    private String designType;

    @Column(name = "sale_price", nullable = false)
    private double salePrice;

    @Column(name = "labor_minutes", nullable = false)
    private int laborMinutes;

    @Column(name = "labor_rate_hour", nullable = false)
    private double laborRatePerHour;

    @Column(name = "overhead_percent", nullable = false)
    private double overheadPercent;

    @Column(name = "waste_percent", nullable = false)
    private double wastePercent;

    @Column(length = 1200)
    private String notes;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.EAGER)
    private List<FloralRecipeIngredient> ingredients = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FloralRecipe() {
    }

    public FloralRecipe(String name, String designType, double salePrice,
                        int laborMinutes, double laborRatePerHour,
                        double overheadPercent, double wastePercent, String notes) {
        setName(name);
        setDesignType(designType);
        setSalePrice(salePrice);
        setLaborMinutes(laborMinutes);
        setLaborRatePerHour(laborRatePerHour);
        setOverheadPercent(overheadPercent);
        setWastePercent(wastePercent);
        setNotes(notes);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDesignType() { return designType; }
    public double getSalePrice() { return salePrice; }
    public int getLaborMinutes() { return laborMinutes; }
    public double getLaborRatePerHour() { return laborRatePerHour; }
    public double getOverheadPercent() { return overheadPercent; }
    public double getWastePercent() { return wastePercent; }
    public String getNotes() { return notes; }
    public List<FloralRecipeIngredient> getIngredients() { return ingredients; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = text(name, 160, "Recipe name", true); }
    public void setDesignType(String designType) {
        this.designType = text(designType, 64, "Design type", true);
    }
    public void setSalePrice(double salePrice) { this.salePrice = money(salePrice, "Sale price"); }

    public void setLaborMinutes(int laborMinutes) {
        if (laborMinutes < 0 || laborMinutes > 10_000) {
            throw new IllegalArgumentException("Labor minutes must be between 0 and 10,000.");
        }
        this.laborMinutes = laborMinutes;
    }

    public void setLaborRatePerHour(double laborRatePerHour) {
        this.laborRatePerHour = money(laborRatePerHour, "Labor rate");
    }

    public void setOverheadPercent(double overheadPercent) {
        this.overheadPercent = percent(overheadPercent, "Overhead");
    }

    public void setWastePercent(double wastePercent) {
        this.wastePercent = percent(wastePercent, "Waste");
    }

    public void setNotes(String notes) { this.notes = text(notes, 1200, "Notes", false); }

    public void setIngredients(List<FloralRecipeIngredient> ingredients) {
        this.ingredients.clear();
        if (ingredients != null) {
            ingredients.forEach(this::addIngredient);
        }
    }

    public void addIngredient(FloralRecipeIngredient ingredient) {
        if (ingredient == null) {
            return;
        }
        ingredient.setRecipe(this);
        ingredients.add(ingredient);
    }

    public void copyBusinessFieldsFrom(FloralRecipe source) {
        if (source == null) {
            throw new IllegalArgumentException("Floral recipe is required.");
        }
        setName(source.getName());
        setDesignType(source.getDesignType());
        setSalePrice(source.getSalePrice());
        setLaborMinutes(source.getLaborMinutes());
        setLaborRatePerHour(source.getLaborRatePerHour());
        setOverheadPercent(source.getOverheadPercent());
        setWastePercent(source.getWastePercent());
        setNotes(source.getNotes());
        setIngredients(source.getIngredients().stream()
                .map(FloralRecipeIngredient::detachedCopy).toList());
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    private static double money(double value, String field) {
        if (!Double.isFinite(value) || value < 0 || value > 1_000_000_000) {
            throw new IllegalArgumentException(field + " must be a non-negative finite amount.");
        }
        return value;
    }

    private static double percent(double value, String field) {
        if (!Double.isFinite(value) || value < 0 || value > 500) {
            throw new IllegalArgumentException(field + " percent must be between 0 and 500.");
        }
        return value;
    }

    private static String text(String value, int max, String field, boolean required) {
        String normalized = value == null ? "" : value.trim();
        if (required && normalized.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }
}
