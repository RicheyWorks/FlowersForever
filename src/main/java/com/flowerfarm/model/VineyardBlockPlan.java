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

/** Local vineyard block plan, without inventory, order, or winery production linkage. */
@Entity
@Table(name = "vineyard_block_plans", indexes = {
        @Index(name = "idx_vineyard_block_variety", columnList = "grape_variety"),
        @Index(name = "idx_vineyard_block_harvest_start", columnList = "planned_harvest_start")
})
public class VineyardBlockPlan {

    private static final BigDecimal MAX_ACRES = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_name", nullable = false, length = 120)
    private String blockName;

    @Column(nullable = false, length = 120)
    private String location;

    @Column(name = "grape_variety", nullable = false, length = 120)
    private String grapeVariety;

    @Column(precision = 9, scale = 2)
    private BigDecimal acres;

    @Column(name = "planned_harvest_start")
    private LocalDate plannedHarvestStart;

    @Column(name = "planned_harvest_end")
    private LocalDate plannedHarvestEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VineyardDestination destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VineyardBlockStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VineyardBlockPlan() {
    }

    public VineyardBlockPlan(String blockName, String location, String grapeVariety,
                             BigDecimal acres, LocalDate plannedHarvestStart,
                             LocalDate plannedHarvestEnd, VineyardDestination destination,
                             VineyardBlockStage stage, String notes) {
        setBlockName(blockName);
        setLocation(location);
        setGrapeVariety(grapeVariety);
        setAcres(acres);
        setPlannedHarvestStart(plannedHarvestStart);
        setPlannedHarvestEnd(plannedHarvestEnd);
        setDestination(destination);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getBlockName() { return blockName; }
    public String getLocation() { return location; }
    public String getGrapeVariety() { return grapeVariety; }
    public BigDecimal getAcres() { return acres; }
    public LocalDate getPlannedHarvestStart() { return plannedHarvestStart; }
    public LocalDate getPlannedHarvestEnd() { return plannedHarvestEnd; }
    public VineyardDestination getDestination() { return destination; }
    public VineyardBlockStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBlockName(String blockName) {
        this.blockName = text(blockName, 120, "Block name", true);
    }

    public void setLocation(String location) {
        this.location = text(location, 120, "Location", false);
    }

    public void setGrapeVariety(String grapeVariety) {
        this.grapeVariety = text(grapeVariety, 120, "Grape variety", true);
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

    public void setPlannedHarvestStart(LocalDate plannedHarvestStart) {
        this.plannedHarvestStart = plannedHarvestStart;
    }

    public void setPlannedHarvestEnd(LocalDate plannedHarvestEnd) {
        this.plannedHarvestEnd = plannedHarvestEnd;
    }

    public void setDestination(VineyardDestination destination) {
        this.destination = destination == null ? VineyardDestination.UNDECIDED : destination;
    }

    public void setStage(VineyardBlockStage stage) {
        this.stage = stage == null ? VineyardBlockStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (blockName == null || blockName.isBlank() || location == null
                || grapeVariety == null || grapeVariety.isBlank()
                || destination == null || stage == null || notes == null) {
            throw new IllegalArgumentException("Complete the required vineyard block details.");
        }
        text(blockName, 120, "Block name", true);
        text(location, 120, "Location", false);
        text(grapeVariety, 120, "Grape variety", true);
        text(notes, 2000, "Notes", false);
        if (acres != null) {
            setAcres(acres);
        }
        if (plannedHarvestStart != null && plannedHarvestEnd != null
                && plannedHarvestEnd.isBefore(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned harvest end cannot be before planned harvest start.");
        }
    }

    public void copyBusinessFieldsFrom(VineyardBlockPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Vineyard block plan is required.");
        }
        source.validate();
        setBlockName(source.blockName);
        setLocation(source.location);
        setGrapeVariety(source.grapeVariety);
        setAcres(source.acres);
        setPlannedHarvestStart(source.plannedHarvestStart);
        setPlannedHarvestEnd(source.plannedHarvestEnd);
        setDestination(source.destination);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public VineyardBlockPlan snapshot() {
        VineyardBlockPlan copy = new VineyardBlockPlan(blockName, location, grapeVariety,
                acres, plannedHarvestStart, plannedHarvestEnd, destination, stage, notes);
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
