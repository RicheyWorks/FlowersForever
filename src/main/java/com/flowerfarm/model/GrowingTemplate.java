package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Reusable, farm-owned crop assumptions for frost-aware production planning. */
@Entity
@Table(name = "growing_templates")
public class GrowingTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "crop_name", nullable = false, length = 120)
    private String cropName;

    @Column(nullable = false, length = 120)
    private String variety;

    @Column(name = "spacing_inches", nullable = false)
    private double spacingInches;

    @Column(name = "rows_per_bed", nullable = false)
    private int rowsPerBed;

    @Column(name = "sow_days_from_frost", nullable = false)
    private int sowDaysFromFrost;

    @Column(name = "transplant_days_from_frost")
    private Integer transplantDaysFromFrost;

    @Column(name = "days_to_harvest", nullable = false)
    private int daysToHarvest;

    @Column(name = "harvest_window_days", nullable = false)
    private int harvestWindowDays;

    @Column(name = "succession_days", nullable = false)
    private int successionDays;

    @Column(name = "stems_per_plant", nullable = false)
    private double stemsPerPlant;

    @Column(name = "germination_percent", nullable = false)
    private double germinationPercent;

    @Column(name = "soil_prep_lead_days", nullable = false)
    private int soilPrepLeadDays;

    @Column(name = "support_days_after_planting", nullable = false)
    private int supportDaysAfterPlanting;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GrowingTemplate() {
    }

    public GrowingTemplate(String name, String cropName, String variety,
                           double spacingInches, int rowsPerBed, int sowDaysFromFrost,
                           Integer transplantDaysFromFrost, int daysToHarvest,
                           int harvestWindowDays, int successionDays, double stemsPerPlant,
                           double germinationPercent, int soilPrepLeadDays,
                           int supportDaysAfterPlanting, boolean active, String notes) {
        setName(name);
        setCropName(cropName);
        setVariety(variety);
        setSpacingInches(spacingInches);
        setRowsPerBed(rowsPerBed);
        setSowDaysFromFrost(sowDaysFromFrost);
        setTransplantDaysFromFrost(transplantDaysFromFrost);
        setDaysToHarvest(daysToHarvest);
        setHarvestWindowDays(harvestWindowDays);
        setSuccessionDays(successionDays);
        setStemsPerPlant(stemsPerPlant);
        setGerminationPercent(germinationPercent);
        setSoilPrepLeadDays(soilPrepLeadDays);
        setSupportDaysAfterPlanting(supportDaysAfterPlanting);
        setActive(active);
        setNotes(notes);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCropName() { return cropName; }
    public String getVariety() { return variety; }
    public double getSpacingInches() { return spacingInches; }
    public int getRowsPerBed() { return rowsPerBed; }
    public int getSowDaysFromFrost() { return sowDaysFromFrost; }
    public Integer getTransplantDaysFromFrost() { return transplantDaysFromFrost; }
    public int getDaysToHarvest() { return daysToHarvest; }
    public int getHarvestWindowDays() { return harvestWindowDays; }
    public int getSuccessionDays() { return successionDays; }
    public double getStemsPerPlant() { return stemsPerPlant; }
    public double getGerminationPercent() { return germinationPercent; }
    public int getSoilPrepLeadDays() { return soilPrepLeadDays; }
    public int getSupportDaysAfterPlanting() { return supportDaysAfterPlanting; }
    public boolean isActive() { return active; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = text(name, 120, "Template name", true); }
    public void setCropName(String cropName) { this.cropName = text(cropName, 120, "Crop", true); }
    public void setVariety(String variety) { this.variety = text(variety, 120, "Variety", false); }

    public void setSpacingInches(double spacingInches) {
        this.spacingInches = decimal(spacingInches, 1, 72, "Spacing");
    }

    public void setRowsPerBed(int rowsPerBed) {
        this.rowsPerBed = integer(rowsPerBed, 1, 12, "Rows per bed");
    }

    public void setSowDaysFromFrost(int sowDaysFromFrost) {
        this.sowDaysFromFrost = integer(sowDaysFromFrost, -365, 365, "Sow frost offset");
    }

    public void setTransplantDaysFromFrost(Integer transplantDaysFromFrost) {
        if (transplantDaysFromFrost != null) {
            integer(transplantDaysFromFrost, -365, 365, "Transplant frost offset");
            if (transplantDaysFromFrost < sowDaysFromFrost) {
                throw new IllegalArgumentException("Transplant offset cannot be before sow offset.");
            }
        }
        this.transplantDaysFromFrost = transplantDaysFromFrost;
    }

    public void setDaysToHarvest(int daysToHarvest) {
        this.daysToHarvest = integer(daysToHarvest, 1, 730, "Days to harvest");
    }

    public void setHarvestWindowDays(int harvestWindowDays) {
        this.harvestWindowDays = integer(harvestWindowDays, 1, 365, "Harvest window");
    }

    public void setSuccessionDays(int successionDays) {
        this.successionDays = integer(successionDays, 0, 365, "Succession interval");
    }

    public void setStemsPerPlant(double stemsPerPlant) {
        this.stemsPerPlant = decimal(stemsPerPlant, 0.01, 1_000, "Stems per plant");
    }

    public void setGerminationPercent(double germinationPercent) {
        this.germinationPercent = decimal(germinationPercent, 1, 100, "Germination");
    }

    public void setSoilPrepLeadDays(int soilPrepLeadDays) {
        this.soilPrepLeadDays = integer(soilPrepLeadDays, 0, 180, "Soil-prep lead");
    }

    public void setSupportDaysAfterPlanting(int supportDaysAfterPlanting) {
        this.supportDaysAfterPlanting = integer(
                supportDaysAfterPlanting, 0, 365, "Support task offset");
    }

    public void setActive(boolean active) { this.active = active; }
    public void setNotes(String notes) { this.notes = text(notes, 1000, "Notes", false); }

    public void copyBusinessFieldsFrom(GrowingTemplate source) {
        if (source == null) {
            throw new IllegalArgumentException("Growing template is required.");
        }
        setName(source.getName());
        setCropName(source.getCropName());
        setVariety(source.getVariety());
        setSpacingInches(source.getSpacingInches());
        setRowsPerBed(source.getRowsPerBed());
        setSowDaysFromFrost(source.getSowDaysFromFrost());
        setTransplantDaysFromFrost(source.getTransplantDaysFromFrost());
        setDaysToHarvest(source.getDaysToHarvest());
        setHarvestWindowDays(source.getHarvestWindowDays());
        setSuccessionDays(source.getSuccessionDays());
        setStemsPerPlant(source.getStemsPerPlant());
        setGerminationPercent(source.getGerminationPercent());
        setSoilPrepLeadDays(source.getSoilPrepLeadDays());
        setSupportDaysAfterPlanting(source.getSupportDaysAfterPlanting());
        setActive(source.isActive());
        setNotes(source.getNotes());
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    private static int integer(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + ".");
        }
        return value;
    }

    private static double decimal(double value, double min, double max, String field) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max + ".");
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
