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

/** A local cutting plan and status, without bale, feed, stock, or order linkage. */
@Entity
@Table(name = "hay_cuttings", indexes = {
        @Index(name = "idx_hay_cutting_field", columnList = "field_name"),
        @Index(name = "idx_hay_cutting_cut_date", columnList = "planned_cut_date")
})
public class HayCutting {

    private static final BigDecimal MAX_ACRES = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "field_name", nullable = false, length = 120)
    private String fieldName;

    @Column(name = "forage_crop", nullable = false, length = 120)
    private String forageCrop;

    @Column(name = "cutting_number", nullable = false)
    private int cuttingNumber;

    @Column(precision = 9, scale = 2)
    private BigDecimal acres;

    @Column(name = "planned_cut_date", nullable = false)
    private LocalDate plannedCutDate;

    @Column(name = "planned_bale_date")
    private LocalDate plannedBaleDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private HayCuttingStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HayCutting() {
    }

    public HayCutting(String fieldName, String forageCrop, int cuttingNumber,
                      BigDecimal acres, LocalDate plannedCutDate,
                      LocalDate plannedBaleDate, HayCuttingStage stage, String notes) {
        setFieldName(fieldName);
        setForageCrop(forageCrop);
        setCuttingNumber(cuttingNumber);
        setAcres(acres);
        setPlannedCutDate(plannedCutDate);
        setPlannedBaleDate(plannedBaleDate);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getFieldName() { return fieldName; }
    public String getForageCrop() { return forageCrop; }
    public int getCuttingNumber() { return cuttingNumber; }
    public BigDecimal getAcres() { return acres; }
    public LocalDate getPlannedCutDate() { return plannedCutDate; }
    public LocalDate getPlannedBaleDate() { return plannedBaleDate; }
    public HayCuttingStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFieldName(String fieldName) {
        this.fieldName = text(fieldName, 120, "Field name", true);
    }

    public void setForageCrop(String forageCrop) {
        this.forageCrop = text(forageCrop, 120, "Forage crop", true);
    }

    public void setCuttingNumber(int cuttingNumber) {
        if (cuttingNumber < 1 || cuttingNumber > 99) {
            throw new IllegalArgumentException("Cutting number must be between 1 and 99.");
        }
        this.cuttingNumber = cuttingNumber;
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

    public void setPlannedCutDate(LocalDate plannedCutDate) {
        this.plannedCutDate = plannedCutDate;
    }

    public void setPlannedBaleDate(LocalDate plannedBaleDate) {
        this.plannedBaleDate = plannedBaleDate;
    }

    public void setStage(HayCuttingStage stage) {
        this.stage = stage == null ? HayCuttingStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (fieldName == null || fieldName.isBlank() || forageCrop == null
                || forageCrop.isBlank() || plannedCutDate == null || stage == null
                || notes == null) {
            throw new IllegalArgumentException("Complete the required hay cutting details.");
        }
        text(fieldName, 120, "Field name", true);
        text(forageCrop, 120, "Forage crop", true);
        setCuttingNumber(cuttingNumber);
        text(notes, 2000, "Notes", false);
        if (acres != null) {
            setAcres(acres);
        }
        if (plannedBaleDate != null && plannedBaleDate.isBefore(plannedCutDate)) {
            throw new IllegalArgumentException("Planned bale date cannot be before planned cut date.");
        }
    }

    public void copyBusinessFieldsFrom(HayCutting source) {
        if (source == null) {
            throw new IllegalArgumentException("Hay cutting is required.");
        }
        source.validate();
        setFieldName(source.fieldName);
        setForageCrop(source.forageCrop);
        setCuttingNumber(source.cuttingNumber);
        setAcres(source.acres);
        setPlannedCutDate(source.plannedCutDate);
        setPlannedBaleDate(source.plannedBaleDate);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public HayCutting snapshot() {
        HayCutting copy = new HayCutting(fieldName, forageCrop, cuttingNumber,
                acres, plannedCutDate, plannedBaleDate, stage, notes);
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
