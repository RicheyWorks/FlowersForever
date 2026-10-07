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

/** A local grain field calendar and progress stage, without yield or storage linkage. */
@Entity
@Table(name = "grain_field_plans", indexes = {
        @Index(name = "idx_grain_field_crop", columnList = "grain_crop"),
        @Index(name = "idx_grain_field_harvest_start", columnList = "planned_harvest_start")
})
public class GrainFieldPlan {

    private static final BigDecimal MAX_ACRES = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_name", nullable = false, length = 120)
    private String fieldName;

    @Column(name = "grain_crop", nullable = false, length = 120)
    private String grainCrop;

    @Column(nullable = false, length = 120)
    private String variety;

    @Column(precision = 9, scale = 2)
    private BigDecimal acres;

    @Column(name = "previous_crop", nullable = false, length = 120)
    private String previousCrop;

    @Column(name = "planned_seeding_date")
    private LocalDate plannedSeedingDate;

    @Column(name = "planned_harvest_start", nullable = false)
    private LocalDate plannedHarvestStart;

    @Column(name = "planned_harvest_end")
    private LocalDate plannedHarvestEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GrainFieldStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GrainFieldPlan() {
    }

    public GrainFieldPlan(String fieldName, String grainCrop, String variety,
                          BigDecimal acres, String previousCrop,
                          LocalDate plannedSeedingDate, LocalDate plannedHarvestStart,
                          LocalDate plannedHarvestEnd, GrainFieldStage stage, String notes) {
        setFieldName(fieldName);
        setGrainCrop(grainCrop);
        setVariety(variety);
        setAcres(acres);
        setPreviousCrop(previousCrop);
        setPlannedSeedingDate(plannedSeedingDate);
        setPlannedHarvestStart(plannedHarvestStart);
        setPlannedHarvestEnd(plannedHarvestEnd);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getFieldName() { return fieldName; }
    public String getGrainCrop() { return grainCrop; }
    public String getVariety() { return variety; }
    public BigDecimal getAcres() { return acres; }
    public String getPreviousCrop() { return previousCrop; }
    public LocalDate getPlannedSeedingDate() { return plannedSeedingDate; }
    public LocalDate getPlannedHarvestStart() { return plannedHarvestStart; }
    public LocalDate getPlannedHarvestEnd() { return plannedHarvestEnd; }
    public GrainFieldStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFieldName(String fieldName) {
        this.fieldName = text(fieldName, 120, "Field name", true);
    }

    public void setGrainCrop(String grainCrop) {
        this.grainCrop = text(grainCrop, 120, "Grain crop", true);
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

    public void setPreviousCrop(String previousCrop) {
        this.previousCrop = text(previousCrop, 120, "Previous crop", false);
    }

    public void setPlannedSeedingDate(LocalDate plannedSeedingDate) {
        this.plannedSeedingDate = plannedSeedingDate;
    }

    public void setPlannedHarvestStart(LocalDate plannedHarvestStart) {
        this.plannedHarvestStart = plannedHarvestStart;
    }

    public void setPlannedHarvestEnd(LocalDate plannedHarvestEnd) {
        this.plannedHarvestEnd = plannedHarvestEnd;
    }

    public void setStage(GrainFieldStage stage) {
        this.stage = stage == null ? GrainFieldStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (fieldName == null || fieldName.isBlank() || grainCrop == null
                || grainCrop.isBlank() || plannedHarvestStart == null
                || stage == null || notes == null || variety == null || previousCrop == null) {
            throw new IllegalArgumentException("Complete the required grain field details.");
        }
        text(fieldName, 120, "Field name", true);
        text(grainCrop, 120, "Grain crop", true);
        text(variety, 120, "Variety", false);
        text(previousCrop, 120, "Previous crop", false);
        text(notes, 2000, "Notes", false);
        if (acres != null) {
            setAcres(acres);
        }
        if (plannedSeedingDate != null && plannedSeedingDate.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned seeding date cannot be after planned harvest start.");
        }
        if (plannedHarvestEnd != null && plannedHarvestEnd.isBefore(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned harvest end cannot be before planned harvest start.");
        }
    }

    public void copyBusinessFieldsFrom(GrainFieldPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Grain field plan is required.");
        }
        source.validate();
        setFieldName(source.fieldName);
        setGrainCrop(source.grainCrop);
        setVariety(source.variety);
        setAcres(source.acres);
        setPreviousCrop(source.previousCrop);
        setPlannedSeedingDate(source.plannedSeedingDate);
        setPlannedHarvestStart(source.plannedHarvestStart);
        setPlannedHarvestEnd(source.plannedHarvestEnd);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public GrainFieldPlan snapshot() {
        GrainFieldPlan copy = new GrainFieldPlan(fieldName, grainCrop, variety, acres,
                previousCrop, plannedSeedingDate, plannedHarvestStart, plannedHarvestEnd,
                stage, notes);
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
