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

/** Local tree nut orchard calendar and progress stage, without yield or inventory linkage. */
@Entity
@Table(name = "nut_block_plans", indexes = {
        @Index(name = "idx_nut_block_crop", columnList = "nut_crop"),
        @Index(name = "idx_nut_block_harvest_start", columnList = "planned_harvest_start")
})
public class NutBlockPlan {

    private static final BigDecimal MAX_ACRES = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_name", nullable = false, length = 120)
    private String blockName;

    @Column(name = "nut_crop", nullable = false, length = 120)
    private String nutCrop;

    @Column(nullable = false, length = 120)
    private String variety;

    @Column(name = "pollinizer_variety", nullable = false, length = 120)
    private String pollinizerVariety;

    @Column(precision = 9, scale = 2)
    private BigDecimal acres;

    @Column(name = "tree_count")
    private Integer treeCount;

    @Column(name = "planned_bloom_start")
    private LocalDate plannedBloomStart;

    @Column(name = "planned_floor_prep_date")
    private LocalDate plannedFloorPrepDate;

    @Column(name = "planned_harvest_start", nullable = false)
    private LocalDate plannedHarvestStart;

    @Column(name = "planned_harvest_end")
    private LocalDate plannedHarvestEnd;

    @Column(name = "intended_destination", nullable = false, length = 120)
    private String intendedDestination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private NutBlockStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NutBlockPlan() {
    }

    public NutBlockPlan(String blockName, String nutCrop, String variety,
                        String pollinizerVariety, BigDecimal acres, Integer treeCount,
                        LocalDate plannedBloomStart, LocalDate plannedFloorPrepDate,
                        LocalDate plannedHarvestStart, LocalDate plannedHarvestEnd,
                        String intendedDestination, NutBlockStage stage, String notes) {
        setBlockName(blockName);
        setNutCrop(nutCrop);
        setVariety(variety);
        setPollinizerVariety(pollinizerVariety);
        setAcres(acres);
        setTreeCount(treeCount);
        setPlannedBloomStart(plannedBloomStart);
        setPlannedFloorPrepDate(plannedFloorPrepDate);
        setPlannedHarvestStart(plannedHarvestStart);
        setPlannedHarvestEnd(plannedHarvestEnd);
        setIntendedDestination(intendedDestination);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getBlockName() { return blockName; }
    public String getNutCrop() { return nutCrop; }
    public String getVariety() { return variety; }
    public String getPollinizerVariety() { return pollinizerVariety; }
    public BigDecimal getAcres() { return acres; }
    public Integer getTreeCount() { return treeCount; }
    public LocalDate getPlannedBloomStart() { return plannedBloomStart; }
    public LocalDate getPlannedFloorPrepDate() { return plannedFloorPrepDate; }
    public LocalDate getPlannedHarvestStart() { return plannedHarvestStart; }
    public LocalDate getPlannedHarvestEnd() { return plannedHarvestEnd; }
    public String getIntendedDestination() { return intendedDestination; }
    public NutBlockStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBlockName(String blockName) {
        this.blockName = text(blockName, 120, "Block name", true);
    }

    public void setNutCrop(String nutCrop) {
        this.nutCrop = text(nutCrop, 120, "Nut crop", true);
    }

    public void setVariety(String variety) {
        this.variety = text(variety, 120, "Variety", false);
    }

    public void setPollinizerVariety(String pollinizerVariety) {
        this.pollinizerVariety = text(pollinizerVariety, 120, "Pollinizer variety", false);
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

    public void setTreeCount(Integer treeCount) {
        if (treeCount != null && treeCount <= 0) {
            throw new IllegalArgumentException("Tree count must be greater than zero.");
        }
        this.treeCount = treeCount;
    }

    public void setPlannedBloomStart(LocalDate plannedBloomStart) {
        this.plannedBloomStart = plannedBloomStart;
    }

    public void setPlannedFloorPrepDate(LocalDate plannedFloorPrepDate) {
        this.plannedFloorPrepDate = plannedFloorPrepDate;
    }

    public void setPlannedHarvestStart(LocalDate plannedHarvestStart) {
        this.plannedHarvestStart = plannedHarvestStart;
    }

    public void setPlannedHarvestEnd(LocalDate plannedHarvestEnd) {
        this.plannedHarvestEnd = plannedHarvestEnd;
    }

    public void setIntendedDestination(String intendedDestination) {
        this.intendedDestination = text(intendedDestination, 120, "Intended destination", false);
    }

    public void setStage(NutBlockStage stage) {
        this.stage = stage == null ? NutBlockStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (blockName == null || blockName.isBlank() || nutCrop == null
                || nutCrop.isBlank() || plannedHarvestStart == null || stage == null
                || variety == null || pollinizerVariety == null
                || intendedDestination == null || notes == null) {
            throw new IllegalArgumentException("Complete the required tree nut block details.");
        }
        text(blockName, 120, "Block name", true);
        text(nutCrop, 120, "Nut crop", true);
        text(variety, 120, "Variety", false);
        text(pollinizerVariety, 120, "Pollinizer variety", false);
        text(intendedDestination, 120, "Intended destination", false);
        text(notes, 2000, "Notes", false);
        if (acres != null) {
            setAcres(acres);
        }
        if (treeCount != null) {
            setTreeCount(treeCount);
        }
        if (plannedBloomStart != null && plannedBloomStart.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned bloom start cannot be after planned harvest start.");
        }
        if (plannedFloorPrepDate != null && plannedFloorPrepDate.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned floor prep date cannot be after planned harvest start.");
        }
        if (plannedHarvestEnd != null && plannedHarvestEnd.isBefore(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned harvest end cannot be before planned harvest start.");
        }
    }

    public void copyBusinessFieldsFrom(NutBlockPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Tree nut block plan is required.");
        }
        source.validate();
        setBlockName(source.blockName);
        setNutCrop(source.nutCrop);
        setVariety(source.variety);
        setPollinizerVariety(source.pollinizerVariety);
        setAcres(source.acres);
        setTreeCount(source.treeCount);
        setPlannedBloomStart(source.plannedBloomStart);
        setPlannedFloorPrepDate(source.plannedFloorPrepDate);
        setPlannedHarvestStart(source.plannedHarvestStart);
        setPlannedHarvestEnd(source.plannedHarvestEnd);
        setIntendedDestination(source.intendedDestination);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public NutBlockPlan snapshot() {
        NutBlockPlan copy = new NutBlockPlan(blockName, nutCrop, variety,
                pollinizerVariety, acres, treeCount, plannedBloomStart,
                plannedFloorPrepDate, plannedHarvestStart, plannedHarvestEnd,
                intendedDestination, stage, notes);
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
