package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * A single harvest event for the flower farm — stems cut, bunches packed,
 * or bulk crop weight for Kitsap market days.
 */
@Entity
@Table(name = "harvest_log", indexes = {
        @Index(name = "idx_harvest_date", columnList = "harvestDate")
})
public class HarvestEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate harvestDate;

    @Column(nullable = false, length = 255)
    private String cropName;

    @Column(nullable = false)
    private double quantity;

    @Column(nullable = false, length = 64)
    private String unit;

    @Column(length = 128)
    private String bedOrField;

    @Column(length = 2000)
    private String notes;

    protected HarvestEntry() {
    }

    public HarvestEntry(LocalDate harvestDate, String cropName, double quantity,
                        String unit, String bedOrField, String notes) {
        this(null, harvestDate, cropName, quantity, unit, bedOrField, notes);
    }

    @JsonCreator
    public HarvestEntry(
            @JsonProperty("id") Long id,
            @JsonProperty("harvestDate") LocalDate harvestDate,
            @JsonProperty("cropName") String cropName,
            @JsonProperty("quantity") double quantity,
            @JsonProperty("unit") String unit,
            @JsonProperty("bedOrField") String bedOrField,
            @JsonProperty("notes") String notes) {
        this.id = id;
        setHarvestDate(harvestDate);
        setCropName(cropName);
        setQuantity(quantity);
        setUnit(unit);
        setBedOrField(bedOrField);
        setNotes(notes);
    }

    public Long getId() { return id; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public String getCropName() { return cropName; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }
    public String getBedOrField() { return bedOrField; }
    public String getNotes() { return notes; }

    public void setId(Long id) { this.id = id; }

    public void setHarvestDate(LocalDate harvestDate) {
        if (harvestDate == null) {
            throw new IllegalArgumentException("Harvest date is required.");
        }
        this.harvestDate = harvestDate;
    }

    public void setCropName(String cropName) {
        if (cropName == null || cropName.isBlank()) {
            throw new IllegalArgumentException("Crop name is required.");
        }
        this.cropName = requireMaxLength(cropName.trim(), 255, "Crop name");
    }

    public void setQuantity(double quantity) {
        if (!Double.isFinite(quantity) || quantity <= 0 || quantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Quantity must be a finite number greater than 0 and no more than "
                            + Integer.MAX_VALUE + ".");
        }
        this.quantity = quantity;
    }

    public void setUnit(String unit) {
        String normalized = (unit == null || unit.isBlank()) ? "stems" : unit.trim();
        this.unit = requireMaxLength(normalized, 64, "Unit");
    }

    public void setBedOrField(String bedOrField) {
        this.bedOrField = requireMaxLength(
                bedOrField == null ? "" : bedOrField.trim(), 128, "Bed or field");
    }

    public void setNotes(String notes) {
        this.notes = requireMaxLength(notes == null ? "" : notes.trim(), 2000, "Notes");
    }

    private static String requireMaxLength(String value, int max, String field) {
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return value;
    }
}
