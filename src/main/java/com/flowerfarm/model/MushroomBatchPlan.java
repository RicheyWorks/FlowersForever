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

import java.time.Instant;
import java.time.LocalDate;

/** A local mushroom batch plan, separate from actual harvests, stock, and sales. */
@Entity
@Table(name = "mushroom_batch_plans", indexes = {
        @Index(name = "idx_mushroom_batch_species", columnList = "mushroom_species"),
        @Index(name = "idx_mushroom_batch_fruiting_start",
                columnList = "planned_fruiting_start")
})
public class MushroomBatchPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_name", nullable = false, length = 120)
    private String batchName;

    @Column(name = "mushroom_species", nullable = false, length = 120)
    private String mushroomSpecies;

    @Column(name = "growing_method", nullable = false, length = 120)
    private String growingMethod;

    @Column(nullable = false, length = 120)
    private String substrate;

    @Column(name = "growing_site", nullable = false, length = 120)
    private String growingSite;

    @Column(name = "planned_inoculation_date")
    private LocalDate plannedInoculationDate;

    @Column(name = "planned_fruiting_start")
    private LocalDate plannedFruitingStart;

    @Column(name = "planned_fruiting_end")
    private LocalDate plannedFruitingEnd;

    @Column(name = "intended_channel", nullable = false, length = 120)
    private String intendedChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MushroomBatchStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MushroomBatchPlan() {
    }

    public MushroomBatchPlan(String batchName, String mushroomSpecies,
                             String growingMethod, String substrate, String growingSite,
                             LocalDate plannedInoculationDate,
                             LocalDate plannedFruitingStart, LocalDate plannedFruitingEnd,
                             String intendedChannel, MushroomBatchStage stage,
                             String notes) {
        setBatchName(batchName);
        setMushroomSpecies(mushroomSpecies);
        setGrowingMethod(growingMethod);
        setSubstrate(substrate);
        setGrowingSite(growingSite);
        setPlannedInoculationDate(plannedInoculationDate);
        setPlannedFruitingStart(plannedFruitingStart);
        setPlannedFruitingEnd(plannedFruitingEnd);
        setIntendedChannel(intendedChannel);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getBatchName() { return batchName; }
    public String getMushroomSpecies() { return mushroomSpecies; }
    public String getGrowingMethod() { return growingMethod; }
    public String getSubstrate() { return substrate; }
    public String getGrowingSite() { return growingSite; }
    public LocalDate getPlannedInoculationDate() { return plannedInoculationDate; }
    public LocalDate getPlannedFruitingStart() { return plannedFruitingStart; }
    public LocalDate getPlannedFruitingEnd() { return plannedFruitingEnd; }
    public String getIntendedChannel() { return intendedChannel; }
    public MushroomBatchStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBatchName(String batchName) {
        this.batchName = text(batchName, 120, "Batch name", true);
    }

    public void setMushroomSpecies(String mushroomSpecies) {
        this.mushroomSpecies = text(mushroomSpecies, 120, "Mushroom species", true);
    }

    public void setGrowingMethod(String growingMethod) {
        this.growingMethod = text(growingMethod, 120, "Growing method", false);
    }

    public void setSubstrate(String substrate) {
        this.substrate = text(substrate, 120, "Substrate", false);
    }

    public void setGrowingSite(String growingSite) {
        this.growingSite = text(growingSite, 120, "Growing site", false);
    }

    public void setPlannedInoculationDate(LocalDate plannedInoculationDate) {
        this.plannedInoculationDate = plannedInoculationDate;
    }

    public void setPlannedFruitingStart(LocalDate plannedFruitingStart) {
        this.plannedFruitingStart = plannedFruitingStart;
    }

    public void setPlannedFruitingEnd(LocalDate plannedFruitingEnd) {
        this.plannedFruitingEnd = plannedFruitingEnd;
    }

    public void setIntendedChannel(String intendedChannel) {
        this.intendedChannel = text(intendedChannel, 120, "Intended channel", false);
    }

    public void setStage(MushroomBatchStage stage) {
        this.stage = stage == null ? MushroomBatchStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (batchName == null || batchName.isBlank() || mushroomSpecies == null
                || mushroomSpecies.isBlank() || growingMethod == null || substrate == null
                || growingSite == null || intendedChannel == null || stage == null
                || notes == null) {
            throw new IllegalArgumentException("Complete the required mushroom batch details.");
        }
        text(batchName, 120, "Batch name", true);
        text(mushroomSpecies, 120, "Mushroom species", true);
        text(growingMethod, 120, "Growing method", false);
        text(substrate, 120, "Substrate", false);
        text(growingSite, 120, "Growing site", false);
        text(intendedChannel, 120, "Intended channel", false);
        text(notes, 2000, "Notes", false);
        if (plannedInoculationDate != null && plannedFruitingStart != null
                && plannedFruitingStart.isBefore(plannedInoculationDate)) {
            throw new IllegalArgumentException(
                    "Planned fruiting start cannot be before planned inoculation date.");
        }
        if (plannedFruitingEnd != null) {
            if (plannedFruitingStart == null) {
                throw new IllegalArgumentException(
                        "Planned fruiting end requires a planned fruiting start.");
            }
            if (plannedFruitingEnd.isBefore(plannedFruitingStart)) {
                throw new IllegalArgumentException(
                        "Planned fruiting end cannot be before planned fruiting start.");
            }
        }
    }

    public void copyBusinessFieldsFrom(MushroomBatchPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Mushroom batch plan is required.");
        }
        source.validate();
        setBatchName(source.batchName);
        setMushroomSpecies(source.mushroomSpecies);
        setGrowingMethod(source.growingMethod);
        setSubstrate(source.substrate);
        setGrowingSite(source.growingSite);
        setPlannedInoculationDate(source.plannedInoculationDate);
        setPlannedFruitingStart(source.plannedFruitingStart);
        setPlannedFruitingEnd(source.plannedFruitingEnd);
        setIntendedChannel(source.intendedChannel);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public MushroomBatchPlan snapshot() {
        MushroomBatchPlan copy = new MushroomBatchPlan(batchName, mushroomSpecies,
                growingMethod, substrate, growingSite, plannedInoculationDate,
                plannedFruitingStart, plannedFruitingEnd, intendedChannel, stage, notes);
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
