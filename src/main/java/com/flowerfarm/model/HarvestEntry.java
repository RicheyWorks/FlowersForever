package com.flowerfarm.model;

import com.flowerfarm.util.InventoryQuantity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * A single harvest event for the flower farm — stems cut, bunches packed,
 * or bulk crop weight for Kitsap market days.
 */
@Entity
@Table(name = "harvest_log", indexes = {
        @Index(name = "idx_harvest_date", columnList = "harvestDate"),
        @Index(name = "uq_harvest_lot_code", columnList = "lot_code", unique = true)
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

    @Column(name = "inventory_contribution", nullable = false, precision = 16, scale = 6)
    private java.math.BigDecimal inventoryContribution;

    @Column(nullable = false, length = 64)
    private String unit;

    @Column(length = 128)
    private String bedOrField;

    @Column(length = 2000)
    private String notes;

    @Column(name = "lot_code", nullable = false, length = 64, unique = true)
    private String lotCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "harvest_grade", nullable = false, length = 32)
    private HarvestGrade grade = HarvestGrade.UNGRADED;

    @Column(name = "loss_quantity", nullable = false)
    private double lossQuantity;

    @Column(name = "storage_location", nullable = false, length = 128)
    private String storageLocation = "";

    protected HarvestEntry() {
    }

    public HarvestEntry(LocalDate harvestDate, String cropName, double quantity,
                        String unit, String bedOrField, String notes) {
        this(null, harvestDate, cropName, quantity, unit, bedOrField, notes,
                null, HarvestGrade.UNGRADED, 0, "");
    }

    public HarvestEntry(Long id, LocalDate harvestDate, String cropName, double quantity,
                        String unit, String bedOrField, String notes) {
        this(id, harvestDate, cropName, quantity, unit, bedOrField, notes,
                null, HarvestGrade.UNGRADED, 0, "");
    }

    public HarvestEntry(LocalDate harvestDate, String cropName, double quantity,
                        String unit, String bedOrField, String notes,
                        String lotCode, HarvestGrade grade, double lossQuantity,
                        String storageLocation) {
        this(null, harvestDate, cropName, quantity, unit, bedOrField, notes,
                lotCode, grade, lossQuantity, storageLocation);
    }

    @JsonCreator
    public HarvestEntry(
            @JsonProperty("id") Long id,
            @JsonProperty("harvestDate") LocalDate harvestDate,
            @JsonProperty("cropName") String cropName,
            @JsonProperty("quantity") double quantity,
            @JsonProperty("unit") String unit,
            @JsonProperty("bedOrField") String bedOrField,
            @JsonProperty("notes") String notes,
            @JsonProperty("lotCode") String lotCode,
            @JsonProperty("grade") HarvestGrade grade,
            @JsonProperty("lossQuantity") double lossQuantity,
            @JsonProperty("storageLocation") String storageLocation) {
        this.id = id;
        setHarvestDate(harvestDate);
        setCropName(cropName);
        setQuantity(quantity);
        setInventoryContribution(quantity);
        setUnit(unit);
        setBedOrField(bedOrField);
        setNotes(notes);
        setLotCode(lotCode);
        setGrade(grade);
        setLossQuantity(lossQuantity);
        setStorageLocation(storageLocation);
    }

    public Long getId() { return id; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public String getCropName() { return cropName; }
    public double getQuantity() { return quantity; }
    @JsonIgnore
    public double getInventoryContribution() { return inventoryContribution.doubleValue(); }
    public void setInventoryContribution(double value) {
        inventoryContribution = InventoryQuantity.decimal(value);
    }
    public String getUnit() { return unit; }
    public String getBedOrField() { return bedOrField; }
    public String getNotes() { return notes; }
    public String getLotCode() { return lotCode; }
    public HarvestGrade getGrade() { return grade; }
    public double getLossQuantity() { return lossQuantity; }
    public String getStorageLocation() { return storageLocation; }
    public double getGrossQuantity() {
        return java.math.BigDecimal.valueOf(quantity)
                .add(java.math.BigDecimal.valueOf(lossQuantity)).doubleValue();
    }

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

    public void setLotCode(String lotCode) {
        String normalized = lotCode == null ? "" : lotCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.isEmpty() && !normalized.matches("[A-Z0-9][A-Z0-9._/-]{0,63}")) {
            throw new IllegalArgumentException(
                    "Lot code must contain 1 to 64 letters, numbers, '.', '_', '/', or '-' characters.");
        }
        this.lotCode = normalized;
    }

    public void setGrade(HarvestGrade grade) {
        this.grade = grade == null ? HarvestGrade.UNGRADED : grade;
    }

    public void setLossQuantity(double lossQuantity) {
        if (!Double.isFinite(lossQuantity) || lossQuantity < 0
                || lossQuantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Loss quantity must be a finite number from 0 through "
                            + Integer.MAX_VALUE + ".");
        }
        this.lossQuantity = lossQuantity;
    }

    public void setStorageLocation(String storageLocation) {
        this.storageLocation = requireMaxLength(
                storageLocation == null ? "" : storageLocation.trim(),
                128, "Storage location");
    }

    private static String requireMaxLength(String value, int max, String field) {
        if (value.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return value;
    }
}
