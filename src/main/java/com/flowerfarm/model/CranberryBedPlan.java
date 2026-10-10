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

/** A local cranberry bed plan, separate from actual harvests, stock, and sales. */
@Entity
@Table(name = "cranberry_bed_plans", indexes = {
        @Index(name = "idx_cranberry_harvest_start", columnList = "planned_harvest_start")
})
public class CranberryBedPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bed_name", nullable = false, length = 120)
    private String bedName;

    @Column(nullable = false, length = 120)
    private String variety;

    @Column(precision = 12, scale = 2)
    private BigDecimal acres;

    @Enumerated(EnumType.STRING)
    @Column(name = "harvest_method", nullable = false, length = 24)
    private CranberryHarvestMethod harvestMethod;

    @Column(name = "planned_harvest_start")
    private LocalDate plannedHarvestStart;

    @Column(name = "planned_harvest_end")
    private LocalDate plannedHarvestEnd;

    @Column(name = "intended_channel", nullable = false, length = 120)
    private String intendedChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CranberryBedStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CranberryBedPlan() {
    }

    public CranberryBedPlan(String bedName, String variety, BigDecimal acres,
                            CranberryHarvestMethod harvestMethod,
                            LocalDate plannedHarvestStart, LocalDate plannedHarvestEnd,
                            String intendedChannel, CranberryBedStage stage, String notes) {
        setBedName(bedName);
        setVariety(variety);
        setAcres(acres);
        setHarvestMethod(harvestMethod);
        setPlannedHarvestStart(plannedHarvestStart);
        setPlannedHarvestEnd(plannedHarvestEnd);
        setIntendedChannel(intendedChannel);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getBedName() { return bedName; }
    public String getVariety() { return variety; }
    public BigDecimal getAcres() { return acres; }
    public CranberryHarvestMethod getHarvestMethod() { return harvestMethod; }
    public LocalDate getPlannedHarvestStart() { return plannedHarvestStart; }
    public LocalDate getPlannedHarvestEnd() { return plannedHarvestEnd; }
    public String getIntendedChannel() { return intendedChannel; }
    public CranberryBedStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBedName(String bedName) {
        this.bedName = text(bedName, 120, "Bed name", true);
    }

    public void setVariety(String variety) {
        this.variety = text(variety, 120, "Variety", false);
    }

    public void setAcres(BigDecimal acres) {
        if (acres != null) {
            if (acres.signum() <= 0) {
                throw new IllegalArgumentException("Acres must be positive.");
            }
            try {
                BigDecimal scaled = acres.setScale(2, RoundingMode.UNNECESSARY);
                if (scaled.precision() > 12) {
                    throw new IllegalArgumentException(
                            "Acres cannot exceed 9,999,999,999.99.");
                }
                this.acres = scaled;
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "Acres can have at most two decimal places.", exception);
            }
        } else {
            this.acres = null;
        }
    }

    public void setHarvestMethod(CranberryHarvestMethod harvestMethod) {
        this.harvestMethod = harvestMethod == null
                ? CranberryHarvestMethod.UNDECIDED : harvestMethod;
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

    public void setStage(CranberryBedStage stage) {
        this.stage = stage == null ? CranberryBedStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (bedName == null || bedName.isBlank() || variety == null
                || harvestMethod == null || intendedChannel == null || stage == null
                || notes == null) {
            throw new IllegalArgumentException("Complete the required cranberry bed details.");
        }
        text(bedName, 120, "Bed name", true);
        text(variety, 120, "Variety", false);
        text(intendedChannel, 120, "Intended channel", false);
        text(notes, 2000, "Notes", false);
        if (acres != null && (acres.signum() <= 0 || acres.scale() > 2
                || acres.precision() > 12)) {
            throw new IllegalArgumentException(
                    "Acres must be positive, at most 9,999,999,999.99, with two decimal places.");
        }
        if (plannedHarvestEnd != null) {
            if (plannedHarvestStart == null) {
                throw new IllegalArgumentException(
                        "Planned harvest end requires a planned harvest start.");
            }
            if (plannedHarvestEnd.isBefore(plannedHarvestStart)) {
                throw new IllegalArgumentException(
                        "Planned harvest end cannot be before planned harvest start.");
            }
        }
    }

    public void copyBusinessFieldsFrom(CranberryBedPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Cranberry bed plan is required.");
        }
        source.validate();
        setBedName(source.bedName);
        setVariety(source.variety);
        setAcres(source.acres);
        setHarvestMethod(source.harvestMethod);
        setPlannedHarvestStart(source.plannedHarvestStart);
        setPlannedHarvestEnd(source.plannedHarvestEnd);
        setIntendedChannel(source.intendedChannel);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public CranberryBedPlan snapshot() {
        CranberryBedPlan copy = new CranberryBedPlan(bedName, variety, acres,
                harvestMethod, plannedHarvestStart, plannedHarvestEnd,
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
