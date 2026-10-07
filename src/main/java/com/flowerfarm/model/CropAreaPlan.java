package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/** Local field and orchard planning record, without inventory or harvest linkage. */
@Entity
@Table(name = "crop_area_plans", indexes = {
        @Index(name = "idx_crop_area_workspace", columnList = "workspace_code"),
        @Index(name = "idx_crop_area_target_harvest", columnList = "target_harvest_date")
})
public class CropAreaPlan {

    private static final BigDecimal MAX_ACRES = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "workspace_code", nullable = false, length = 32)
    private FarmWorkspace workspace;

    @Column(name = "area_name", nullable = false, length = 120)
    private String areaName;

    @Column(nullable = false, length = 120)
    private String variety;

    @Column(precision = 9, scale = 2)
    private BigDecimal acres;

    @Column(name = "planting_date")
    private LocalDate plantingDate;

    @Column(name = "target_harvest_date")
    private LocalDate targetHarvestDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CropAreaStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CropAreaPlan() {
    }

    public CropAreaPlan(FarmWorkspace workspace, String areaName, String variety,
                        BigDecimal acres, LocalDate plantingDate,
                        LocalDate targetHarvestDate, CropAreaStage stage, String notes) {
        setWorkspace(workspace);
        setAreaName(areaName);
        setVariety(variety);
        setAcres(acres);
        setPlantingDate(plantingDate);
        setTargetHarvestDate(targetHarvestDate);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public FarmWorkspace getWorkspace() { return workspace; }
    public String getAreaName() { return areaName; }
    public String getVariety() { return variety; }
    public BigDecimal getAcres() { return acres; }
    public LocalDate getPlantingDate() { return plantingDate; }
    public LocalDate getTargetHarvestDate() { return targetHarvestDate; }
    public CropAreaStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setWorkspace(FarmWorkspace workspace) {
        requireSupportedWorkspace(workspace);
        this.workspace = workspace;
    }

    public void setAreaName(String areaName) {
        this.areaName = text(areaName, 120, "Area name", true);
    }

    public void setVariety(String variety) {
        this.variety = text(variety, 120, "Variety", false);
    }

    public void setAcres(BigDecimal acres) {
        if (acres == null) {
            this.acres = null;
            return;
        }
        if (acres.signum() <= 0 || acres.compareTo(MAX_ACRES) > 0) {
            throw new IllegalArgumentException("Acres must be greater than zero and at most 1,000,000.");
        }
        try {
            this.acres = acres.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Acres cannot exceed two decimal places.", exception);
        }
    }

    public void setPlantingDate(LocalDate plantingDate) {
        this.plantingDate = plantingDate;
    }

    public void setTargetHarvestDate(LocalDate targetHarvestDate) {
        this.targetHarvestDate = targetHarvestDate;
    }

    public void setStage(CropAreaStage stage) {
        this.stage = stage == null ? CropAreaStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        requireSupportedWorkspace(workspace);
        if (areaName == null || areaName.isBlank() || variety == null
                || stage == null || notes == null) {
            throw new IllegalArgumentException("Complete the required crop area plan details.");
        }
        text(areaName, 120, "Area name", true);
        text(variety, 120, "Variety", false);
        text(notes, 2000, "Notes", false);
        if (acres != null) {
            setAcres(acres);
        }
        if (plantingDate != null && targetHarvestDate != null
                && targetHarvestDate.isBefore(plantingDate)) {
            throw new IllegalArgumentException(
                    "Target harvest date cannot be before the planting date.");
        }
    }

    public void copyBusinessFieldsFrom(CropAreaPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Crop area plan is required.");
        }
        source.validate();
        if (workspace != source.workspace) {
            throw new IllegalArgumentException(
                    "A crop area plan cannot move between farm workspaces.");
        }
        setAreaName(source.areaName);
        setVariety(source.variety);
        setAcres(source.acres);
        setPlantingDate(source.plantingDate);
        setTargetHarvestDate(source.targetHarvestDate);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Detached snapshot prevents callers from mutating a managed entity. */
    public CropAreaPlan snapshot() {
        CropAreaPlan copy = new CropAreaPlan(workspace, areaName, variety, acres,
                plantingDate, targetHarvestDate, stage, notes);
        copy.id = id;
        copy.createdAt = createdAt;
        copy.updatedAt = updatedAt;
        return copy;
    }

    @PrePersist
    void beforeInsert() {
        validate();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        validate();
        updatedAt = Instant.now();
    }

    public static void requireSupportedWorkspace(FarmWorkspace workspace) {
        if (workspace != FarmWorkspace.POTATO_FARM
                && workspace != FarmWorkspace.APPLE_ORCHARD) {
            throw new IllegalArgumentException(
                    "Crop area plans require a potato farm or apple orchard workspace.");
        }
    }

    private static String text(String value, int max, String field, boolean required) {
        String clean = value == null ? "" : value.trim();
        if (required && clean.isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (clean.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return clean;
    }
}
