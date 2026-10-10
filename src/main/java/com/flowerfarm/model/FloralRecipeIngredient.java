package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One stem/material requirement in a reusable floral recipe. */
@Entity
@Table(name = "floral_recipe_ingredients")
public class FloralRecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    @JsonIgnore
    private FloralRecipe recipe;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "stems_per_design", nullable = false)
    private double stemsPerDesign;

    @Column(name = "pack_size", nullable = false)
    private int packSize;

    @Column(name = "pack_cost", nullable = false)
    private double packCost;

    @Column(length = 500)
    private String notes;

    protected FloralRecipeIngredient() {
    }

    public FloralRecipeIngredient(String itemName, double stemsPerDesign,
                                  int packSize, double packCost, String notes) {
        setItemName(itemName);
        setStemsPerDesign(stemsPerDesign);
        setPackSize(packSize);
        setPackCost(packCost);
        setNotes(notes);
    }

    public Long getId() { return id; }
    public FloralRecipe getRecipe() { return recipe; }
    public String getItemName() { return itemName; }
    public double getStemsPerDesign() { return stemsPerDesign; }
    public int getPackSize() { return packSize; }
    public double getPackCost() { return packCost; }
    public String getNotes() { return notes; }

    public void setId(Long id) { this.id = id; }
    public void setRecipe(FloralRecipe recipe) { this.recipe = recipe; }
    public void setItemName(String itemName) { this.itemName = text(itemName, 255, "Item", true); }

    public void setStemsPerDesign(double stemsPerDesign) {
        if (!Double.isFinite(stemsPerDesign) || stemsPerDesign <= 0 || stemsPerDesign > 1_000_000) {
            throw new IllegalArgumentException("Stems per design must be greater than zero.");
        }
        this.stemsPerDesign = stemsPerDesign;
    }

    public void setPackSize(int packSize) {
        if (packSize < 1 || packSize > 1_000_000) {
            throw new IllegalArgumentException("Pack size must be between 1 and 1,000,000.");
        }
        this.packSize = packSize;
    }

    public void setPackCost(double packCost) {
        if (!Double.isFinite(packCost) || packCost < 0 || packCost > 1_000_000_000) {
            throw new IllegalArgumentException("Pack cost must be a non-negative finite amount.");
        }
        this.packCost = packCost;
    }

    public void setNotes(String notes) { this.notes = text(notes, 500, "Notes", false); }

    public FloralRecipeIngredient detachedCopy() {
        return new FloralRecipeIngredient(itemName, stemsPerDesign, packSize, packCost, notes);
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
