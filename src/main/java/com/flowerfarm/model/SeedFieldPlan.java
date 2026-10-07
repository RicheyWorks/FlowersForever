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

/** A local seed crop calendar and progress stage, without yield or certification linkage. */
@Entity
@Table(name = "seed_field_plans", indexes = {
        @Index(name = "idx_seed_field_crop", columnList = "seed_crop"),
        @Index(name = "idx_seed_field_harvest_start", columnList = "planned_harvest_start")
})
public class SeedFieldPlan {

    private static final BigDecimal MAX_ACRES = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_name", nullable = false, length = 120)
    private String fieldName;

    @Column(name = "seed_crop", nullable = false, length = 120)
    private String seedCrop;

    @Column(nullable = false, length = 120)
    private String variety;

    @Column(precision = 9, scale = 2)
    private BigDecimal acres;

    @Column(name = "previous_crop", nullable = false, length = 120)
    private String previousCrop;

    @Column(name = "planned_sowing_date")
    private LocalDate plannedSowingDate;

    @Column(name = "planned_flowering_start")
    private LocalDate plannedFloweringStart;

    @Column(name = "planned_flowering_end")
    private LocalDate plannedFloweringEnd;

    @Column(name = "planned_harvest_start", nullable = false)
    private LocalDate plannedHarvestStart;

    @Column(name = "planned_harvest_end")
    private LocalDate plannedHarvestEnd;

    @Column(name = "intended_use", nullable = false, length = 120)
    private String intendedUse;

    @Column(name = "isolation_notes", nullable = false, length = 1000)
    private String isolationNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SeedFieldStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SeedFieldPlan() {
    }

    public SeedFieldPlan(String fieldName, String seedCrop, String variety,
                         BigDecimal acres, String previousCrop,
                         LocalDate plannedSowingDate, LocalDate plannedFloweringStart,
                         LocalDate plannedFloweringEnd, LocalDate plannedHarvestStart,
                         LocalDate plannedHarvestEnd, String intendedUse,
                         String isolationNotes, SeedFieldStage stage, String notes) {
        setFieldName(fieldName);
        setSeedCrop(seedCrop);
        setVariety(variety);
        setAcres(acres);
        setPreviousCrop(previousCrop);
        setPlannedSowingDate(plannedSowingDate);
        setPlannedFloweringStart(plannedFloweringStart);
        setPlannedFloweringEnd(plannedFloweringEnd);
        setPlannedHarvestStart(plannedHarvestStart);
        setPlannedHarvestEnd(plannedHarvestEnd);
        setIntendedUse(intendedUse);
        setIsolationNotes(isolationNotes);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getFieldName() { return fieldName; }
    public String getSeedCrop() { return seedCrop; }
    public String getVariety() { return variety; }
    public BigDecimal getAcres() { return acres; }
    public String getPreviousCrop() { return previousCrop; }
    public LocalDate getPlannedSowingDate() { return plannedSowingDate; }
    public LocalDate getPlannedFloweringStart() { return plannedFloweringStart; }
    public LocalDate getPlannedFloweringEnd() { return plannedFloweringEnd; }
    public LocalDate getPlannedHarvestStart() { return plannedHarvestStart; }
    public LocalDate getPlannedHarvestEnd() { return plannedHarvestEnd; }
    public String getIntendedUse() { return intendedUse; }
    public String getIsolationNotes() { return isolationNotes; }
    public SeedFieldStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFieldName(String fieldName) {
        this.fieldName = text(fieldName, 120, "Field name", true);
    }

    public void setSeedCrop(String seedCrop) {
        this.seedCrop = text(seedCrop, 120, "Seed crop", true);
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

    public void setPlannedSowingDate(LocalDate plannedSowingDate) {
        this.plannedSowingDate = plannedSowingDate;
    }

    public void setPlannedFloweringStart(LocalDate plannedFloweringStart) {
        this.plannedFloweringStart = plannedFloweringStart;
    }

    public void setPlannedFloweringEnd(LocalDate plannedFloweringEnd) {
        this.plannedFloweringEnd = plannedFloweringEnd;
    }

    public void setPlannedHarvestStart(LocalDate plannedHarvestStart) {
        this.plannedHarvestStart = plannedHarvestStart;
    }

    public void setPlannedHarvestEnd(LocalDate plannedHarvestEnd) {
        this.plannedHarvestEnd = plannedHarvestEnd;
    }

    public void setIntendedUse(String intendedUse) {
        this.intendedUse = text(intendedUse, 120, "Intended use", false);
    }

    public void setIsolationNotes(String isolationNotes) {
        this.isolationNotes = text(isolationNotes, 1000, "Isolation notes", false);
    }

    public void setStage(SeedFieldStage stage) {
        this.stage = stage == null ? SeedFieldStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (fieldName == null || fieldName.isBlank() || seedCrop == null
                || seedCrop.isBlank() || plannedHarvestStart == null || stage == null
                || variety == null || previousCrop == null || intendedUse == null
                || isolationNotes == null || notes == null) {
            throw new IllegalArgumentException("Complete the required seed field details.");
        }
        text(fieldName, 120, "Field name", true);
        text(seedCrop, 120, "Seed crop", true);
        text(variety, 120, "Variety", false);
        text(previousCrop, 120, "Previous crop", false);
        text(intendedUse, 120, "Intended use", false);
        text(isolationNotes, 1000, "Isolation notes", false);
        text(notes, 2000, "Notes", false);
        if (acres != null) {
            setAcres(acres);
        }
        if (plannedSowingDate != null && plannedSowingDate.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned sowing date cannot be after planned harvest start.");
        }
        if (plannedFloweringStart != null && plannedFloweringStart.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned flowering start cannot be after planned harvest start.");
        }
        if (plannedFloweringEnd != null && plannedFloweringEnd.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned flowering end cannot be after planned harvest start.");
        }
        if (plannedFloweringStart != null && plannedFloweringEnd != null
                && plannedFloweringEnd.isBefore(plannedFloweringStart)) {
            throw new IllegalArgumentException(
                    "Planned flowering end cannot be before planned flowering start.");
        }
        if (plannedSowingDate != null && plannedFloweringStart != null
                && plannedFloweringStart.isBefore(plannedSowingDate)) {
            throw new IllegalArgumentException(
                    "Planned flowering start cannot be before planned sowing date.");
        }
        if (plannedSowingDate != null && plannedFloweringEnd != null
                && plannedFloweringEnd.isBefore(plannedSowingDate)) {
            throw new IllegalArgumentException(
                    "Planned flowering end cannot be before planned sowing date.");
        }
        if (plannedHarvestEnd != null && plannedHarvestEnd.isBefore(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned harvest end cannot be before planned harvest start.");
        }
    }

    public void copyBusinessFieldsFrom(SeedFieldPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Seed field plan is required.");
        }
        source.validate();
        setFieldName(source.fieldName);
        setSeedCrop(source.seedCrop);
        setVariety(source.variety);
        setAcres(source.acres);
        setPreviousCrop(source.previousCrop);
        setPlannedSowingDate(source.plannedSowingDate);
        setPlannedFloweringStart(source.plannedFloweringStart);
        setPlannedFloweringEnd(source.plannedFloweringEnd);
        setPlannedHarvestStart(source.plannedHarvestStart);
        setPlannedHarvestEnd(source.plannedHarvestEnd);
        setIntendedUse(source.intendedUse);
        setIsolationNotes(source.isolationNotes);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public SeedFieldPlan snapshot() {
        SeedFieldPlan copy = new SeedFieldPlan(fieldName, seedCrop, variety, acres,
                previousCrop, plannedSowingDate, plannedFloweringStart,
                plannedFloweringEnd, plannedHarvestStart, plannedHarvestEnd,
                intendedUse, isolationNotes, stage, notes);
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
