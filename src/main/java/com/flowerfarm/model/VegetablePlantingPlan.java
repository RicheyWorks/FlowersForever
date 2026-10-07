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

/** A vegetable succession calendar, separate from actual harvests, stock, and sales. */
@Entity
@Table(name = "vegetable_planting_plans", indexes = {
        @Index(name = "idx_vegetable_planting_crop", columnList = "vegetable_crop"),
        @Index(name = "idx_vegetable_planting_harvest_start", columnList = "planned_harvest_start")
})
public class VegetablePlantingPlan {

    private static final BigDecimal MAX_ACRES = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_or_bed", nullable = false, length = 120)
    private String fieldOrBed;

    @Column(name = "vegetable_crop", nullable = false, length = 120)
    private String vegetableCrop;

    @Column(nullable = false, length = 120)
    private String variety;

    @Column(name = "previous_crop", nullable = false, length = 120)
    private String previousCrop;

    @Column(precision = 9, scale = 2)
    private BigDecimal acres;

    @Column(name = "succession_number", nullable = false)
    private int successionNumber;

    @Column(name = "planned_sowing_date")
    private LocalDate plannedSowingDate;

    @Column(name = "planned_transplant_date")
    private LocalDate plannedTransplantDate;

    @Column(name = "planned_harvest_start", nullable = false)
    private LocalDate plannedHarvestStart;

    @Column(name = "planned_harvest_end")
    private LocalDate plannedHarvestEnd;

    @Column(name = "intended_channel", nullable = false, length = 120)
    private String intendedChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VegetablePlantingStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VegetablePlantingPlan() {
    }

    public VegetablePlantingPlan(String fieldOrBed, String vegetableCrop, String variety,
                                 String previousCrop, BigDecimal acres, int successionNumber,
                                 LocalDate plannedSowingDate, LocalDate plannedTransplantDate,
                                 LocalDate plannedHarvestStart, LocalDate plannedHarvestEnd,
                                 String intendedChannel, VegetablePlantingStage stage, String notes) {
        setFieldOrBed(fieldOrBed);
        setVegetableCrop(vegetableCrop);
        setVariety(variety);
        setPreviousCrop(previousCrop);
        setAcres(acres);
        setSuccessionNumber(successionNumber);
        setPlannedSowingDate(plannedSowingDate);
        setPlannedTransplantDate(plannedTransplantDate);
        setPlannedHarvestStart(plannedHarvestStart);
        setPlannedHarvestEnd(plannedHarvestEnd);
        setIntendedChannel(intendedChannel);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getFieldOrBed() { return fieldOrBed; }
    public String getVegetableCrop() { return vegetableCrop; }
    public String getVariety() { return variety; }
    public String getPreviousCrop() { return previousCrop; }
    public BigDecimal getAcres() { return acres; }
    public int getSuccessionNumber() { return successionNumber; }
    public LocalDate getPlannedSowingDate() { return plannedSowingDate; }
    public LocalDate getPlannedTransplantDate() { return plannedTransplantDate; }
    public LocalDate getPlannedHarvestStart() { return plannedHarvestStart; }
    public LocalDate getPlannedHarvestEnd() { return plannedHarvestEnd; }
    public String getIntendedChannel() { return intendedChannel; }
    public VegetablePlantingStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFieldOrBed(String fieldOrBed) {
        this.fieldOrBed = text(fieldOrBed, 120, "Field or bed", true);
    }

    public void setVegetableCrop(String vegetableCrop) {
        this.vegetableCrop = text(vegetableCrop, 120, "Vegetable crop", true);
    }

    public void setVariety(String variety) {
        this.variety = text(variety, 120, "Variety", false);
    }

    public void setPreviousCrop(String previousCrop) {
        this.previousCrop = text(previousCrop, 120, "Previous crop", false);
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

    public void setSuccessionNumber(int successionNumber) {
        if (successionNumber <= 0) {
            throw new IllegalArgumentException("Succession number must be greater than zero.");
        }
        this.successionNumber = successionNumber;
    }

    public void setPlannedSowingDate(LocalDate plannedSowingDate) {
        this.plannedSowingDate = plannedSowingDate;
    }

    public void setPlannedTransplantDate(LocalDate plannedTransplantDate) {
        this.plannedTransplantDate = plannedTransplantDate;
    }

    public void setPlannedHarvestStart(LocalDate plannedHarvestStart) {
        this.plannedHarvestStart = plannedHarvestStart;
    }

    public void setPlannedHarvestEnd(LocalDate plannedHarvestEnd) {
        this.plannedHarvestEnd = plannedHarvestEnd;
    }

    public void setIntendedChannel(String intendedChannel) {
        this.intendedChannel = text(intendedChannel, 120, "Intended channel", false);
    }

    public void setStage(VegetablePlantingStage stage) {
        this.stage = stage == null ? VegetablePlantingStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (fieldOrBed == null || fieldOrBed.isBlank() || vegetableCrop == null
                || vegetableCrop.isBlank() || plannedHarvestStart == null || stage == null
                || variety == null || previousCrop == null || intendedChannel == null
                || notes == null) {
            throw new IllegalArgumentException("Complete the required vegetable planting details.");
        }
        text(fieldOrBed, 120, "Field or bed", true);
        text(vegetableCrop, 120, "Vegetable crop", true);
        text(variety, 120, "Variety", false);
        text(previousCrop, 120, "Previous crop", false);
        text(intendedChannel, 120, "Intended channel", false);
        text(notes, 2000, "Notes", false);
        setSuccessionNumber(successionNumber);
        if (acres != null) {
            setAcres(acres);
        }
        if (plannedSowingDate != null && plannedSowingDate.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned sowing date cannot be after planned harvest start.");
        }
        if (plannedTransplantDate != null && plannedTransplantDate.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned transplant date cannot be after planned harvest start.");
        }
        if (plannedSowingDate != null && plannedTransplantDate != null
                && plannedSowingDate.isAfter(plannedTransplantDate)) {
            throw new IllegalArgumentException(
                    "Planned sowing date cannot be after planned transplant date.");
        }
        if (plannedHarvestEnd != null && plannedHarvestEnd.isBefore(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned harvest end cannot be before planned harvest start.");
        }
    }

    public void copyBusinessFieldsFrom(VegetablePlantingPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Vegetable planting plan is required.");
        }
        source.validate();
        setFieldOrBed(source.fieldOrBed);
        setVegetableCrop(source.vegetableCrop);
        setVariety(source.variety);
        setPreviousCrop(source.previousCrop);
        setAcres(source.acres);
        setSuccessionNumber(source.successionNumber);
        setPlannedSowingDate(source.plannedSowingDate);
        setPlannedTransplantDate(source.plannedTransplantDate);
        setPlannedHarvestStart(source.plannedHarvestStart);
        setPlannedHarvestEnd(source.plannedHarvestEnd);
        setIntendedChannel(source.intendedChannel);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public VegetablePlantingPlan snapshot() {
        VegetablePlantingPlan copy = new VegetablePlantingPlan(fieldOrBed, vegetableCrop,
                variety, previousCrop, acres, successionNumber, plannedSowingDate,
                plannedTransplantDate, plannedHarvestStart, plannedHarvestEnd,
                intendedChannel, stage, notes);
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
