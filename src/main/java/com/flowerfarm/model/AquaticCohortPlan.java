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

/** A local aquaculture cohort plan, separate from actual harvests, stock, and sales. */
@Entity
@Table(name = "aquatic_cohort_plans", indexes = {
        @Index(name = "idx_aquatic_cohort_species", columnList = "species"),
        @Index(name = "idx_aquatic_cohort_harvest_start", columnList = "planned_harvest_start")
})
public class AquaticCohortPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_name", nullable = false, length = 120)
    private String cohortName;

    @Column(nullable = false, length = 120)
    private String species;

    @Column(name = "growing_site", nullable = false, length = 120)
    private String growingSite;

    @Column(name = "culture_method", nullable = false, length = 120)
    private String cultureMethod;

    @Column(name = "planned_stocking_date")
    private LocalDate plannedStockingDate;

    @Column(name = "planned_harvest_start", nullable = false)
    private LocalDate plannedHarvestStart;

    @Column(name = "planned_harvest_end")
    private LocalDate plannedHarvestEnd;

    @Column(name = "intended_destination", nullable = false, length = 120)
    private String intendedDestination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AquaticCohortStage stage;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AquaticCohortPlan() {
    }

    public AquaticCohortPlan(String cohortName, String species, String growingSite,
                             String cultureMethod, LocalDate plannedStockingDate,
                             LocalDate plannedHarvestStart, LocalDate plannedHarvestEnd,
                             String intendedDestination, AquaticCohortStage stage, String notes) {
        setCohortName(cohortName);
        setSpecies(species);
        setGrowingSite(growingSite);
        setCultureMethod(cultureMethod);
        setPlannedStockingDate(plannedStockingDate);
        setPlannedHarvestStart(plannedHarvestStart);
        setPlannedHarvestEnd(plannedHarvestEnd);
        setIntendedDestination(intendedDestination);
        setStage(stage);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getCohortName() { return cohortName; }
    public String getSpecies() { return species; }
    public String getGrowingSite() { return growingSite; }
    public String getCultureMethod() { return cultureMethod; }
    public LocalDate getPlannedStockingDate() { return plannedStockingDate; }
    public LocalDate getPlannedHarvestStart() { return plannedHarvestStart; }
    public LocalDate getPlannedHarvestEnd() { return plannedHarvestEnd; }
    public String getIntendedDestination() { return intendedDestination; }
    public AquaticCohortStage getStage() { return stage; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCohortName(String cohortName) {
        this.cohortName = text(cohortName, 120, "Cohort name", true);
    }

    public void setSpecies(String species) {
        this.species = text(species, 120, "Species", true);
    }

    public void setGrowingSite(String growingSite) {
        this.growingSite = text(growingSite, 120, "Growing site", true);
    }

    public void setCultureMethod(String cultureMethod) {
        this.cultureMethod = text(cultureMethod, 120, "Culture method", false);
    }

    public void setPlannedStockingDate(LocalDate plannedStockingDate) {
        this.plannedStockingDate = plannedStockingDate;
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

    public void setStage(AquaticCohortStage stage) {
        this.stage = stage == null ? AquaticCohortStage.PLANNED : stage;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (cohortName == null || cohortName.isBlank() || species == null || species.isBlank()
                || growingSite == null || growingSite.isBlank() || plannedHarvestStart == null
                || cultureMethod == null || intendedDestination == null || stage == null
                || notes == null) {
            throw new IllegalArgumentException("Complete the required aquaculture cohort details.");
        }
        text(cohortName, 120, "Cohort name", true);
        text(species, 120, "Species", true);
        text(growingSite, 120, "Growing site", true);
        text(cultureMethod, 120, "Culture method", false);
        text(intendedDestination, 120, "Intended destination", false);
        text(notes, 2000, "Notes", false);
        if (plannedStockingDate != null && plannedStockingDate.isAfter(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned stocking date cannot be after planned harvest start.");
        }
        if (plannedHarvestEnd != null && plannedHarvestEnd.isBefore(plannedHarvestStart)) {
            throw new IllegalArgumentException(
                    "Planned harvest end cannot be before planned harvest start.");
        }
    }

    public void copyBusinessFieldsFrom(AquaticCohortPlan source) {
        if (source == null) {
            throw new IllegalArgumentException("Aquaculture cohort plan is required.");
        }
        source.validate();
        setCohortName(source.cohortName);
        setSpecies(source.species);
        setGrowingSite(source.growingSite);
        setCultureMethod(source.cultureMethod);
        setPlannedStockingDate(source.plannedStockingDate);
        setPlannedHarvestStart(source.plannedHarvestStart);
        setPlannedHarvestEnd(source.plannedHarvestEnd);
        setIntendedDestination(source.intendedDestination);
        setStage(source.stage);
        setNotes(source.notes);
    }

    /** Return a detached snapshot so callers cannot mutate a managed entity. */
    public AquaticCohortPlan snapshot() {
        AquaticCohortPlan copy = new AquaticCohortPlan(cohortName, species, growingSite,
                cultureMethod, plannedStockingDate, plannedHarvestStart, plannedHarvestEnd,
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
