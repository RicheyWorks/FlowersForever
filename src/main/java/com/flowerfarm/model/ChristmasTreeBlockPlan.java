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

/** A local Christmas tree block plan, separate from actual cutting, stock, and sales. */
@Entity
@Table(name = "christmas_tree_block_plans", indexes = {
        @Index(name = "idx_christmas_tree_species", columnList = "tree_species"),
        @Index(name = "idx_christmas_tree_harvest_year", columnList = "target_harvest_year")
})
public class ChristmasTreeBlockPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_name", nullable = false, length = 120)
    private String blockName;

    @Column(name = "tree_species", nullable = false, length = 120)
    private String treeSpecies;

    @Column(name = "planting_year")
    private Integer plantingYear;

    @Column(precision = 12, scale = 2)
    private BigDecimal acres;

    @Column(name = "planted_tree_count")
    private Integer plantedTreeCount;

    @Column(name = "target_height", nullable = false, length = 80)
    private String targetHeight;

    @Column(name = "next_shearing_date")
    private LocalDate nextShearingDate;

    @Column(name = "target_harvest_year", nullable = false)
    private Integer targetHarvestYear;

    @Column(name = "planned_cut_start")
    private LocalDate plannedCutStart;

    @Column(name = "planned_cut_end")
    private LocalDate plannedCutEnd;

    @Column(name = "intended_channel", nullable = false, length = 120)
    private String intendedChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ChristmasTreeBlockStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChristmasTreeBlockPlan() {
    }

    public ChristmasTreeBlockPlan(String blockName, String treeSpecies,
                                  Integer plantingYear, BigDecimal acres,
                                  Integer plantedTreeCount, String targetHeight,
                                  LocalDate nextShearingDate, Integer targetHarvestYear,
                                  LocalDate plannedCutStart, LocalDate plannedCutEnd,
                                  String intendedChannel, ChristmasTreeBlockStage stage,
                                  String notes) {
        setBlockName(blockName);
        setTreeSpecies(treeSpecies);
        setPlantingYear(plantingYear);
        setAcres(acres);
        setPlantedTreeCount(plantedTreeCount);
        setTargetHeight(targetHeight);
        setNextShearingDate(nextShearingDate);
        setTargetHarvestYear(targetHarvestYear);
        setPlannedCutStart(plannedCutStart);
        setPlannedCutEnd(plannedCutEnd);
        setIntendedChannel(intendedChannel);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getBlockName() { return blockName; }
    public String getTreeSpecies() { return treeSpecies; }
    public Integer getPlantingYear() { return plantingYear; }
    public BigDecimal getAcres() { return acres; }
    public Integer getPlantedTreeCount() { return plantedTreeCount; }
    public String getTargetHeight() { return targetHeight; }
    public LocalDate getNextShearingDate() { return nextShearingDate; }
    public Integer getTargetHarvestYear() { return targetHarvestYear; }
    public LocalDate getPlannedCutStart() { return plannedCutStart; }
    public LocalDate getPlannedCutEnd() { return plannedCutEnd; }
    public String getIntendedChannel() { return intendedChannel; }
    public ChristmasTreeBlockStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBlockName(String blockName) {
        this.blockName = text(blockName, 120, "Block name", true);
    }

    public void setTreeSpecies(String treeSpecies) {
        this.treeSpecies = text(treeSpecies, 120, "Tree species", true);
    }

    public void setPlantingYear(Integer plantingYear) {
        this.plantingYear = plantingYear;
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
                throw new IllegalArgumentException("Acres can have at most two decimal places.", exception);
            }
        } else {
            this.acres = null;
        }
    }

    public void setPlantedTreeCount(Integer plantedTreeCount) {
        this.plantedTreeCount = plantedTreeCount;
    }

    public void setTargetHeight(String targetHeight) {
        this.targetHeight = text(targetHeight, 80, "Target height", false);
    }

    public void setNextShearingDate(LocalDate nextShearingDate) {
        this.nextShearingDate = nextShearingDate;
    }

    public void setTargetHarvestYear(Integer targetHarvestYear) {
        this.targetHarvestYear = targetHarvestYear;
    }

    public void setPlannedCutStart(LocalDate plannedCutStart) {
        this.plannedCutStart = plannedCutStart;
    }

    public void setPlannedCutEnd(LocalDate plannedCutEnd) {
        this.plannedCutEnd = plannedCutEnd;
    }

    public void setIntendedChannel(String intendedChannel) {
        this.intendedChannel = text(intendedChannel, 120, "Intended channel", false);
    }

    public void setStage(ChristmasTreeBlockStage stage) {
        this.stage = stage == null ? ChristmasTreeBlockStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (blockName == null || blockName.isBlank() || treeSpecies == null
                || treeSpecies.isBlank() || targetHarvestYear == null
                || targetHeight == null || intendedChannel == null || stage == null
                || notes == null) {
            throw new IllegalArgumentException("Complete the required Christmas tree block details.");
        }
        text(blockName, 120, "Block name", true);
        text(treeSpecies, 120, "Tree species", true);
        text(targetHeight, 80, "Target height", false);
        text(intendedChannel, 120, "Intended channel", false);
        text(notes, 2000, "Notes", false);
        if (targetHarvestYear < 1900 || targetHarvestYear > 2200) {
            throw new IllegalArgumentException("Target harvest year must be between 1900 and 2200.");
        }
        if (plantingYear != null && (plantingYear < 1900 || plantingYear > 2200
                || plantingYear > targetHarvestYear)) {
            throw new IllegalArgumentException(
                    "Planting year must be between 1900 and the target harvest year.");
        }
        if (acres != null && (acres.signum() <= 0 || acres.scale() > 2
                || acres.precision() > 12)) {
            throw new IllegalArgumentException(
                    "Acres must be positive, at most 9,999,999,999.99, with two decimal places.");
        }
        if (plantedTreeCount != null && plantedTreeCount <= 0) {
            throw new IllegalArgumentException("Planted tree count must be positive.");
        }
        if (plannedCutStart != null && plannedCutStart.getYear() != targetHarvestYear) {
            throw new IllegalArgumentException("Planned cut start must fall in the target harvest year.");
        }
        if (plannedCutEnd != null) {
            if (plannedCutStart == null) {
                throw new IllegalArgumentException("Planned cut end requires a planned cut start.");
            }
            if (plannedCutEnd.getYear() != targetHarvestYear
                    || plannedCutEnd.isBefore(plannedCutStart)) {
                throw new IllegalArgumentException(
                        "Planned cut end must follow the start in the target harvest year.");
            }
        }
        if (nextShearingDate != null
                && nextShearingDate.getYear() > targetHarvestYear) {
            throw new IllegalArgumentException(
                    "Next shearing date cannot be after the target harvest year.");
        }
        if (nextShearingDate != null && plannedCutStart != null
                && nextShearingDate.isAfter(plannedCutStart)) {
            throw new IllegalArgumentException(
                    "Next shearing date cannot be after planned cut start.");
        }
    }

    public void copyBusinessFieldsFrom(ChristmasTreeBlockPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Christmas tree block plan is required.");
        }
        source.validate();
        setBlockName(source.blockName);
        setTreeSpecies(source.treeSpecies);
        setPlantingYear(source.plantingYear);
        setAcres(source.acres);
        setPlantedTreeCount(source.plantedTreeCount);
        setTargetHeight(source.targetHeight);
        setNextShearingDate(source.nextShearingDate);
        setTargetHarvestYear(source.targetHarvestYear);
        setPlannedCutStart(source.plannedCutStart);
        setPlannedCutEnd(source.plannedCutEnd);
        setIntendedChannel(source.intendedChannel);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public ChristmasTreeBlockPlan snapshot() {
        ChristmasTreeBlockPlan copy = new ChristmasTreeBlockPlan(blockName, treeSpecies,
                plantingYear, acres, plantedTreeCount, targetHeight, nextShearingDate,
                targetHarvestYear, plannedCutStart, plannedCutEnd, intendedChannel,
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
