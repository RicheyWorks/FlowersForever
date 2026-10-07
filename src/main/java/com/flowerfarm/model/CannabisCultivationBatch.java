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

/** Local cultivation planning record, without compliance or inventory linkage. */
@Entity
@Table(name = "cannabis_cultivation_batches", indexes = {
        @Index(name = "idx_cannabis_batch_start", columnList = "start_date"),
        @Index(name = "idx_cannabis_batch_stage", columnList = "stage")
})
public class CannabisCultivationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_name", nullable = false, length = 120)
    private String batchName;

    @Column(nullable = false, length = 120)
    private String cultivar;

    @Column(nullable = false, length = 128)
    private String area;

    @Column(name = "plant_count", nullable = false)
    private int plantCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "planned_harvest_date", nullable = false)
    private LocalDate plannedHarvestDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CannabisCultivationStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CannabisCultivationBatch() {
    }

    public CannabisCultivationBatch(String batchName, String cultivar, String area,
                                   int plantCount, LocalDate startDate,
                                   LocalDate plannedHarvestDate,
                                   CannabisCultivationStage stage, String notes) {
        setBatchName(batchName);
        setCultivar(cultivar);
        setArea(area);
        setPlantCount(plantCount);
        setStartDate(startDate);
        setPlannedHarvestDate(plannedHarvestDate);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getBatchName() { return batchName; }
    public String getCultivar() { return cultivar; }
    public String getArea() { return area; }
    public int getPlantCount() { return plantCount; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getPlannedHarvestDate() { return plannedHarvestDate; }
    public CannabisCultivationStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBatchName(String batchName) {
        this.batchName = text(batchName, 120, "Batch name", true);
    }

    public void setCultivar(String cultivar) {
        this.cultivar = text(cultivar, 120, "Cultivar", false);
    }

    public void setArea(String area) {
        this.area = text(area, 128, "Cultivation area", true);
    }

    public void setPlantCount(int plantCount) {
        if (plantCount < 0 || plantCount > 1_000_000) {
            throw new IllegalArgumentException("Plant count must be between 0 and 1,000,000.");
        }
        this.plantCount = plantCount;
    }

    public void setStartDate(LocalDate startDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required.");
        }
        this.startDate = startDate;
    }

    public void setPlannedHarvestDate(LocalDate plannedHarvestDate) {
        if (plannedHarvestDate == null) {
            throw new IllegalArgumentException("Planned harvest date is required.");
        }
        this.plannedHarvestDate = plannedHarvestDate;
    }

    public void setStage(CannabisCultivationStage stage) {
        this.stage = stage == null ? CannabisCultivationStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (batchName == null || batchName.isBlank() || area == null || area.isBlank()
                || cultivar == null || notes == null || stage == null
                || startDate == null || plannedHarvestDate == null) {
            throw new IllegalArgumentException("Complete the required cultivation batch details.");
        }
        text(batchName, 120, "Batch name", true);
        text(cultivar, 120, "Cultivar", false);
        text(area, 128, "Cultivation area", true);
        text(notes, 2000, "Notes", false);
        if (plantCount < 0 || plantCount > 1_000_000) {
            throw new IllegalArgumentException("Plant count must be between 0 and 1,000,000.");
        }
        if (plannedHarvestDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "Planned harvest date cannot be before the start date.");
        }
    }

    public void copyBusinessFieldsFrom(CannabisCultivationBatch source) {
        if (source == null) {
            throw new IllegalArgumentException("Cultivation batch is required.");
        }
        source.validate();
        setBatchName(source.getBatchName());
        setCultivar(source.getCultivar());
        setArea(source.getArea());
        setPlantCount(source.getPlantCount());
        setStartDate(source.getStartDate());
        setPlannedHarvestDate(source.getPlannedHarvestDate());
        setStage(source.getStage());
        setNotes(source.getNotes());
    }

    /** Detached snapshot prevents callers from mutating a managed entity. */
    public CannabisCultivationBatch snapshot() {
        CannabisCultivationBatch copy = new CannabisCultivationBatch(
                batchName, cultivar, area, plantCount, startDate,
                plannedHarvestDate, stage, notes);
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
