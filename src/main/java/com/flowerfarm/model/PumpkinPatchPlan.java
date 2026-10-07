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

/** A local pumpkin patch plan, separate from actual harvests, admissions, and sales. */
@Entity
@Table(name = "pumpkin_patch_plans", indexes = {
        @Index(name = "idx_pumpkin_picking_start", columnList = "planned_picking_start")
})
public class PumpkinPatchPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patch_name", nullable = false, length = 120)
    private String patchName;

    @Column(nullable = false, length = 120)
    private String variety;

    @Column(precision = 12, scale = 2)
    private BigDecimal acres;

    @Column(name = "planned_planting_date")
    private LocalDate plannedPlantingDate;

    @Column(name = "planned_picking_start")
    private LocalDate plannedPickingStart;

    @Column(name = "planned_picking_end")
    private LocalDate plannedPickingEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_mode", nullable = false, length = 24)
    private PumpkinSalesMode salesMode;

    @Column(name = "visitor_area_notes", nullable = false, length = 2000)
    private String visitorAreaNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PumpkinPatchStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PumpkinPatchPlan() {
    }

    public PumpkinPatchPlan(String patchName, String variety, BigDecimal acres,
                            LocalDate plannedPlantingDate, LocalDate plannedPickingStart,
                            LocalDate plannedPickingEnd, PumpkinSalesMode salesMode,
                            String visitorAreaNotes, PumpkinPatchStage stage,
                            String notes) {
        setPatchName(patchName);
        setVariety(variety);
        setAcres(acres);
        setPlannedPlantingDate(plannedPlantingDate);
        setPlannedPickingStart(plannedPickingStart);
        setPlannedPickingEnd(plannedPickingEnd);
        setSalesMode(salesMode);
        setVisitorAreaNotes(visitorAreaNotes);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getPatchName() { return patchName; }
    public String getVariety() { return variety; }
    public BigDecimal getAcres() { return acres; }
    public LocalDate getPlannedPlantingDate() { return plannedPlantingDate; }
    public LocalDate getPlannedPickingStart() { return plannedPickingStart; }
    public LocalDate getPlannedPickingEnd() { return plannedPickingEnd; }
    public PumpkinSalesMode getSalesMode() { return salesMode; }
    public String getVisitorAreaNotes() { return visitorAreaNotes; }
    public PumpkinPatchStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPatchName(String patchName) {
        this.patchName = text(patchName, 120, "Patch name", true);
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

    public void setPlannedPlantingDate(LocalDate plannedPlantingDate) {
        this.plannedPlantingDate = plannedPlantingDate;
    }

    public void setPlannedPickingStart(LocalDate plannedPickingStart) {
        this.plannedPickingStart = plannedPickingStart;
    }

    public void setPlannedPickingEnd(LocalDate plannedPickingEnd) {
        this.plannedPickingEnd = plannedPickingEnd;
    }

    public void setSalesMode(PumpkinSalesMode salesMode) {
        this.salesMode = salesMode == null ? PumpkinSalesMode.UNDECIDED : salesMode;
    }

    public void setVisitorAreaNotes(String visitorAreaNotes) {
        this.visitorAreaNotes = text(visitorAreaNotes, 2000,
                "Visitor area notes", false);
    }

    public void setStage(PumpkinPatchStage stage) {
        this.stage = stage == null ? PumpkinPatchStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (patchName == null || patchName.isBlank() || variety == null
                || salesMode == null || visitorAreaNotes == null || stage == null
                || notes == null) {
            throw new IllegalArgumentException("Complete the required pumpkin patch details.");
        }
        text(patchName, 120, "Patch name", true);
        text(variety, 120, "Variety", false);
        text(visitorAreaNotes, 2000, "Visitor area notes", false);
        text(notes, 2000, "Notes", false);
        if (acres != null && (acres.signum() <= 0 || acres.scale() > 2
                || acres.precision() > 12)) {
            throw new IllegalArgumentException(
                    "Acres must be positive, at most 9,999,999,999.99, with two decimal places.");
        }
        if (plannedPlantingDate != null && plannedPickingStart != null
                && plannedPickingStart.isBefore(plannedPlantingDate)) {
            throw new IllegalArgumentException(
                    "Planned picking start cannot be before planned planting date.");
        }
        if (plannedPickingEnd != null) {
            if (plannedPickingStart == null) {
                throw new IllegalArgumentException(
                        "Planned picking end requires a planned picking start.");
            }
            if (plannedPickingEnd.isBefore(plannedPickingStart)) {
                throw new IllegalArgumentException(
                        "Planned picking end cannot be before planned picking start.");
            }
        }
    }

    public void copyBusinessFieldsFrom(PumpkinPatchPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Pumpkin patch plan is required.");
        }
        source.validate();
        setPatchName(source.patchName);
        setVariety(source.variety);
        setAcres(source.acres);
        setPlannedPlantingDate(source.plannedPlantingDate);
        setPlannedPickingStart(source.plannedPickingStart);
        setPlannedPickingEnd(source.plannedPickingEnd);
        setSalesMode(source.salesMode);
        setVisitorAreaNotes(source.visitorAreaNotes);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public PumpkinPatchPlan snapshot() {
        PumpkinPatchPlan copy = new PumpkinPatchPlan(patchName, variety, acres,
                plannedPlantingDate, plannedPickingStart, plannedPickingEnd,
                salesMode, visitorAreaNotes, stage, notes);
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
