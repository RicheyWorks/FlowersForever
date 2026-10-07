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

/** Local berry block planning record, without harvest, stock, or sales linkage. */
@Entity
@Table(name = "berry_block_plans", indexes = {
        @Index(name = "idx_berry_block_crop", columnList = "crop"),
        @Index(name = "idx_berry_block_first_pick", columnList = "first_pick_date")
})
public class BerryBlockPlan {

    private static final BigDecimal MAX_ACRES = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_name", nullable = false, length = 120)
    private String blockName;

    @Column(nullable = false, length = 120)
    private String crop;

    @Column(nullable = false, length = 120)
    private String cultivar;

    @Column(precision = 9, scale = 2)
    private BigDecimal acres;

    @Column(name = "planting_date")
    private LocalDate plantingDate;

    @Column(name = "first_pick_date")
    private LocalDate firstPickDate;

    @Column(name = "last_pick_date")
    private LocalDate lastPickDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_channel", nullable = false, length = 16)
    private BerrySalesChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BerryBlockStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BerryBlockPlan() {
    }

    public BerryBlockPlan(String blockName, String crop, String cultivar,
                          BigDecimal acres, LocalDate plantingDate,
                          LocalDate firstPickDate, LocalDate lastPickDate,
                          BerrySalesChannel channel, BerryBlockStage stage, String notes) {
        setBlockName(blockName);
        setCrop(crop);
        setCultivar(cultivar);
        setAcres(acres);
        setPlantingDate(plantingDate);
        setFirstPickDate(firstPickDate);
        setLastPickDate(lastPickDate);
        setChannel(channel);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getBlockName() { return blockName; }
    public String getCrop() { return crop; }
    public String getCultivar() { return cultivar; }
    public BigDecimal getAcres() { return acres; }
    public LocalDate getPlantingDate() { return plantingDate; }
    public LocalDate getFirstPickDate() { return firstPickDate; }
    public LocalDate getLastPickDate() { return lastPickDate; }
    public BerrySalesChannel getChannel() { return channel; }
    public BerryBlockStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBlockName(String blockName) {
        this.blockName = text(blockName, 120, "Block name", true);
    }

    public void setCrop(String crop) {
        this.crop = text(crop, 120, "Berry crop", true);
    }

    public void setCultivar(String cultivar) {
        this.cultivar = text(cultivar, 120, "Cultivar", false);
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

    public void setPlantingDate(LocalDate plantingDate) {
        this.plantingDate = plantingDate;
    }

    public void setFirstPickDate(LocalDate firstPickDate) {
        this.firstPickDate = firstPickDate;
    }

    public void setLastPickDate(LocalDate lastPickDate) {
        this.lastPickDate = lastPickDate;
    }

    public void setChannel(BerrySalesChannel channel) {
        this.channel = channel == null ? BerrySalesChannel.UNDECIDED : channel;
    }

    public void setStage(BerryBlockStage stage) {
        this.stage = stage == null ? BerryBlockStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (blockName == null || blockName.isBlank() || crop == null || crop.isBlank()
                || cultivar == null || channel == null || stage == null || notes == null) {
            throw new IllegalArgumentException("Complete the required berry block details.");
        }
        text(blockName, 120, "Block name", true);
        text(crop, 120, "Berry crop", true);
        text(cultivar, 120, "Cultivar", false);
        text(notes, 2000, "Notes", false);
        if (acres != null) {
            setAcres(acres);
        }
        if (plantingDate != null && firstPickDate != null
                && firstPickDate.isBefore(plantingDate)) {
            throw new IllegalArgumentException("First pick date cannot be before planting date.");
        }
        if (firstPickDate != null && lastPickDate != null
                && lastPickDate.isBefore(firstPickDate)) {
            throw new IllegalArgumentException("Last pick date cannot be before first pick date.");
        }
        if (plantingDate != null && lastPickDate != null
                && lastPickDate.isBefore(plantingDate)) {
            throw new IllegalArgumentException("Last pick date cannot be before planting date.");
        }
    }

    public void copyBusinessFieldsFrom(BerryBlockPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Berry block plan is required.");
        }
        source.validate();
        setBlockName(source.blockName);
        setCrop(source.crop);
        setCultivar(source.cultivar);
        setAcres(source.acres);
        setPlantingDate(source.plantingDate);
        setFirstPickDate(source.firstPickDate);
        setLastPickDate(source.lastPickDate);
        setChannel(source.channel);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Detached snapshot prevents callers from mutating a managed entity. */
    public BerryBlockPlan snapshot() {
        BerryBlockPlan copy = new BerryBlockPlan(blockName, crop, cultivar,
                acres, plantingDate, firstPickDate, lastPickDate, channel, stage, notes);
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
