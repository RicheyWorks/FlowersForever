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

/** Local live-plant growing plan, separate from stock and orders. */
@Entity
@Table(name = "nursery_batches", indexes = {
        @Index(name = "idx_nursery_plant_name", columnList = "plant_name"),
        @Index(name = "idx_nursery_planned_ready", columnList = "planned_ready_date")
})
public class NurseryBatch {

    private static final int MAX_STARTING_COUNT = 1_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_name", nullable = false, length = 120)
    private String batchName;

    @Column(name = "plant_name", nullable = false, length = 120)
    private String plantName;

    @Column(nullable = false, length = 120)
    private String cultivar;

    @Enumerated(EnumType.STRING)
    @Column(name = "plant_form", nullable = false, length = 24)
    private NurseryPlantForm form;

    @Column(name = "growing_area", nullable = false, length = 128)
    private String growingArea;

    @Column(name = "started_date")
    private LocalDate startedDate;

    @Column(name = "planned_ready_date")
    private LocalDate plannedReadyDate;

    @Column(name = "starting_count")
    private Integer startingCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_channel", nullable = false, length = 16)
    private NurserySalesChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private NurseryBatchStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NurseryBatch() {
    }

    public NurseryBatch(String batchName, String plantName, String cultivar,
                        NurseryPlantForm form, String growingArea,
                        LocalDate startedDate, LocalDate plannedReadyDate,
                        Integer startingCount, NurserySalesChannel channel,
                        NurseryBatchStage stage, String notes) {
        setBatchName(batchName);
        setPlantName(plantName);
        setCultivar(cultivar);
        setForm(form);
        setGrowingArea(growingArea);
        setStartedDate(startedDate);
        setPlannedReadyDate(plannedReadyDate);
        setStartingCount(startingCount);
        setChannel(channel);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getBatchName() { return batchName; }
    public String getPlantName() { return plantName; }
    public String getCultivar() { return cultivar; }
    public NurseryPlantForm getForm() { return form; }
    public String getGrowingArea() { return growingArea; }
    public LocalDate getStartedDate() { return startedDate; }
    public LocalDate getPlannedReadyDate() { return plannedReadyDate; }
    public Integer getStartingCount() { return startingCount; }
    public NurserySalesChannel getChannel() { return channel; }
    public NurseryBatchStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBatchName(String batchName) {
        this.batchName = text(batchName, 120, "Batch name", true);
    }

    public void setPlantName(String plantName) {
        this.plantName = text(plantName, 120, "Plant name", true);
    }

    public void setCultivar(String cultivar) {
        this.cultivar = text(cultivar, 120, "Cultivar", false);
    }

    public void setForm(NurseryPlantForm form) {
        this.form = form == null ? NurseryPlantForm.UNSPECIFIED : form;
    }

    public void setGrowingArea(String growingArea) {
        this.growingArea = text(growingArea, 128, "Growing area", false);
    }

    public void setStartedDate(LocalDate startedDate) {
        this.startedDate = startedDate;
    }

    public void setPlannedReadyDate(LocalDate plannedReadyDate) {
        this.plannedReadyDate = plannedReadyDate;
    }

    public void setStartingCount(Integer startingCount) {
        if (startingCount != null
                && (startingCount <= 0 || startingCount > MAX_STARTING_COUNT)) {
            throw new IllegalArgumentException(
                    "Starting plant count must be between 1 and 1,000,000.");
        }
        this.startingCount = startingCount;
    }

    public void setChannel(NurserySalesChannel channel) {
        this.channel = channel == null ? NurserySalesChannel.UNDECIDED : channel;
    }

    public void setStage(NurseryBatchStage stage) {
        this.stage = stage == null ? NurseryBatchStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (batchName == null || batchName.isBlank()
                || plantName == null || plantName.isBlank()
                || cultivar == null || form == null || growingArea == null
                || channel == null || stage == null || notes == null) {
            throw new IllegalArgumentException("Complete the required nursery batch details.");
        }
        text(batchName, 120, "Batch name", true);
        text(plantName, 120, "Plant name", true);
        text(cultivar, 120, "Cultivar", false);
        text(growingArea, 128, "Growing area", false);
        text(notes, 2000, "Notes", false);
        setStartingCount(startingCount);
        if (startedDate != null && plannedReadyDate != null
                && plannedReadyDate.isBefore(startedDate)) {
            throw new IllegalArgumentException(
                    "Planned ready date cannot be before started date.");
        }
    }

    public void copyBusinessFieldsFrom(NurseryBatch source) {
        if (source == null) {
            throw new IllegalArgumentException("Nursery batch is required.");
        }
        source.validate();
        setBatchName(source.batchName);
        setPlantName(source.plantName);
        setCultivar(source.cultivar);
        setForm(source.form);
        setGrowingArea(source.growingArea);
        setStartedDate(source.startedDate);
        setPlannedReadyDate(source.plannedReadyDate);
        setStartingCount(source.startingCount);
        setChannel(source.channel);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Detached copy prevents callers from mutating a managed entity. */
    public NurseryBatch snapshot() {
        NurseryBatch copy = new NurseryBatch(batchName, plantName, cultivar, form,
                growingArea, startedDate, plannedReadyDate, startingCount,
                channel, stage, notes);
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
