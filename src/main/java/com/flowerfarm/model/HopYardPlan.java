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

/** A local hop yard calendar and progress stage, without yield or processing linkage. */
@Entity
@Table(name = "hop_yard_plans", indexes = {
        @Index(name = "idx_hop_yard_variety", columnList = "hop_variety"),
        @Index(name = "idx_hop_yard_harvest_start", columnList = "planned_harvest_start")
})
public class HopYardPlan {

    private static final BigDecimal MAX_ACRES = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "yard_name", nullable = false, length = 120)
    private String yardName;

    @Column(name = "hop_variety", nullable = false, length = 120)
    private String hopVariety;

    @Column(precision = 9, scale = 2)
    private BigDecimal acres;

    @Column(name = "planned_stringing_date")
    private LocalDate plannedStringingDate;

    @Column(name = "planned_harvest_start", nullable = false)
    private LocalDate plannedHarvestStart;

    @Column(name = "planned_harvest_end")
    private LocalDate plannedHarvestEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private HopYardStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HopYardPlan() {
    }

    public HopYardPlan(String yardName, String hopVariety, BigDecimal acres,
                       LocalDate plannedStringingDate, LocalDate plannedHarvestStart,
                       LocalDate plannedHarvestEnd, HopYardStage stage, String notes) {
        setYardName(yardName);
        setHopVariety(hopVariety);
        setAcres(acres);
        setPlannedStringingDate(plannedStringingDate);
        setPlannedHarvestStart(plannedHarvestStart);
        setPlannedHarvestEnd(plannedHarvestEnd);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getYardName() { return yardName; }
    public String getHopVariety() { return hopVariety; }
    public BigDecimal getAcres() { return acres; }
    public LocalDate getPlannedStringingDate() { return plannedStringingDate; }
    public LocalDate getPlannedHarvestStart() { return plannedHarvestStart; }
    public LocalDate getPlannedHarvestEnd() { return plannedHarvestEnd; }
    public HopYardStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setYardName(String yardName) {
        this.yardName = text(yardName, 120, "Yard name", true);
    }

    public void setHopVariety(String hopVariety) {
        this.hopVariety = text(hopVariety, 120, "Hop variety", true);
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

    public void setPlannedStringingDate(LocalDate plannedStringingDate) {
        this.plannedStringingDate = plannedStringingDate;
    }

    public void setPlannedHarvestStart(LocalDate plannedHarvestStart) {
        this.plannedHarvestStart = plannedHarvestStart;
    }

    public void setPlannedHarvestEnd(LocalDate plannedHarvestEnd) {
        this.plannedHarvestEnd = plannedHarvestEnd;
    }

    public void setStage(HopYardStage stage) {
        this.stage = stage == null ? HopYardStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (yardName == null || yardName.isBlank() || hopVariety == null
                || hopVariety.isBlank() || plannedHarvestStart == null
                || stage == null || notes == null) {
            throw new IllegalArgumentException("Complete the required hop yard details.");
        }
        text(yardName, 120, "Yard name", true);
        text(hopVariety, 120, "Hop variety", true);
        text(notes, 2000, "Notes", false);
        if (acres != null) {
            setAcres(acres);
        }
        if (plannedStringingDate != null
                && plannedStringingDate.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned stringing date cannot be after planned harvest start.");
        }
        if (plannedHarvestEnd != null && plannedHarvestEnd.isBefore(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned harvest end cannot be before planned harvest start.");
        }
    }

    public void copyBusinessFieldsFrom(HopYardPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Hop yard plan is required.");
        }
        source.validate();
        setYardName(source.yardName);
        setHopVariety(source.hopVariety);
        setAcres(source.acres);
        setPlannedStringingDate(source.plannedStringingDate);
        setPlannedHarvestStart(source.plannedHarvestStart);
        setPlannedHarvestEnd(source.plannedHarvestEnd);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public HopYardPlan snapshot() {
        HopYardPlan copy = new HopYardPlan(yardName, hopVariety, acres,
                plannedStringingDate, plannedHarvestStart, plannedHarvestEnd, stage, notes);
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
