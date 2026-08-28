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

/** One crop allocation from sowing through its expected harvest window. */
@Entity
@Table(name = "season_plan_entries", indexes = {
        @Index(name = "idx_season_plan_year", columnList = "season_year"),
        @Index(name = "idx_season_plan_bed", columnList = "bed_or_field"),
        @Index(name = "idx_season_plan_harvest", columnList = "harvest_start,harvest_end")
})
public class SeasonPlanEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "season_year", nullable = false)
    private int seasonYear;

    @Column(name = "crop_name", nullable = false, length = 120)
    private String cropName;

    @Column(length = 120)
    private String variety;

    @Column(name = "bed_or_field", nullable = false, length = 128)
    private String bedOrField;

    @Column(name = "sow_date", nullable = false)
    private LocalDate sowDate;

    @Column(name = "transplant_date")
    private LocalDate transplantDate;

    @Column(name = "harvest_start", nullable = false)
    private LocalDate harvestStart;

    @Column(name = "harvest_end", nullable = false)
    private LocalDate harvestEnd;

    @Column(name = "bed_feet", nullable = false)
    private double bedFeet;

    @Column(name = "expected_stems", nullable = false)
    private int expectedStems;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SeasonPlanStatus status;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SeasonPlanEntry() {
    }

    public SeasonPlanEntry(int seasonYear, String cropName, String variety,
                           String bedOrField, LocalDate sowDate, LocalDate transplantDate,
                           LocalDate harvestStart, LocalDate harvestEnd, double bedFeet,
                           int expectedStems, SeasonPlanStatus status, String notes) {
        setSeasonYear(seasonYear);
        setCropName(cropName);
        setVariety(variety);
        setBedOrField(bedOrField);
        setSowDate(sowDate);
        setTransplantDate(transplantDate);
        setHarvestStart(harvestStart);
        setHarvestEnd(harvestEnd);
        setBedFeet(bedFeet);
        setExpectedStems(expectedStems);
        setStatus(status);
        setNotes(notes);
        validateSchedule();
    }

    public Long getId() { return id; }
    public int getSeasonYear() { return seasonYear; }
    public String getCropName() { return cropName; }
    public String getVariety() { return variety; }
    public String getBedOrField() { return bedOrField; }
    public LocalDate getSowDate() { return sowDate; }
    public LocalDate getTransplantDate() { return transplantDate; }
    public LocalDate getHarvestStart() { return harvestStart; }
    public LocalDate getHarvestEnd() { return harvestEnd; }
    public double getBedFeet() { return bedFeet; }
    public int getExpectedStems() { return expectedStems; }
    public SeasonPlanStatus getStatus() { return status; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }

    public void setSeasonYear(int seasonYear) {
        if (seasonYear < 2000 || seasonYear > 2200) {
            throw new IllegalArgumentException("Season year must be between 2000 and 2200.");
        }
        this.seasonYear = seasonYear;
    }

    public void setCropName(String cropName) {
        this.cropName = requireText(cropName, 120, "Crop");
    }

    public void setVariety(String variety) {
        this.variety = optionalText(variety, 120, "Variety");
    }

    public void setBedOrField(String bedOrField) {
        this.bedOrField = requireText(bedOrField, 128, "Bed or field");
    }

    public void setSowDate(LocalDate sowDate) {
        this.sowDate = requireDate(sowDate, "Sow date");
    }

    public void setTransplantDate(LocalDate transplantDate) {
        this.transplantDate = transplantDate;
    }

    public void setHarvestStart(LocalDate harvestStart) {
        this.harvestStart = requireDate(harvestStart, "Harvest start");
    }

    public void setHarvestEnd(LocalDate harvestEnd) {
        this.harvestEnd = requireDate(harvestEnd, "Harvest end");
    }

    public void setBedFeet(double bedFeet) {
        if (!Double.isFinite(bedFeet) || bedFeet <= 0 || bedFeet > 100_000) {
            throw new IllegalArgumentException("Bed length must be between 0 and 100,000 feet.");
        }
        this.bedFeet = bedFeet;
    }

    public void setExpectedStems(int expectedStems) {
        if (expectedStems < 0 || expectedStems > 100_000_000) {
            throw new IllegalArgumentException(
                    "Expected stems must be between 0 and 100,000,000.");
        }
        this.expectedStems = expectedStems;
    }

    public void setStatus(SeasonPlanStatus status) {
        this.status = status == null ? SeasonPlanStatus.PLANNED : status;
    }

    public void setNotes(String notes) {
        this.notes = optionalText(notes, 1000, "Notes");
    }

    public void validateSchedule() {
        if (transplantDate != null && transplantDate.isBefore(sowDate)) {
            throw new IllegalArgumentException("Transplant date cannot be before sow date.");
        }
        LocalDate established = transplantDate == null ? sowDate : transplantDate;
        if (harvestStart.isBefore(established)) {
            throw new IllegalArgumentException(
                    "Harvest start cannot be before sowing or transplanting.");
        }
        if (harvestEnd.isBefore(harvestStart)) {
            throw new IllegalArgumentException("Harvest end cannot be before harvest start.");
        }
        int earliestYear = Math.min(sowDate.getYear(), harvestStart.getYear());
        int latestYear = Math.max(harvestEnd.getYear(), harvestStart.getYear());
        if (seasonYear < earliestYear || seasonYear > latestYear) {
            throw new IllegalArgumentException("Season year must match the planting schedule.");
        }
    }

    public void copyBusinessFieldsFrom(SeasonPlanEntry source) {
        if (source == null) {
            throw new IllegalArgumentException("Season plan entry is required.");
        }
        setSeasonYear(source.getSeasonYear());
        setCropName(source.getCropName());
        setVariety(source.getVariety());
        setBedOrField(source.getBedOrField());
        setSowDate(source.getSowDate());
        setTransplantDate(source.getTransplantDate());
        setHarvestStart(source.getHarvestStart());
        setHarvestEnd(source.getHarvestEnd());
        setBedFeet(source.getBedFeet());
        setExpectedStems(source.getExpectedStems());
        setStatus(source.getStatus());
        setNotes(source.getNotes());
        validateSchedule();
    }

    public void initializeTimestamps(Instant now) {
        Instant timestamp = now == null ? Instant.now() : now;
        createdAt = timestamp;
        updatedAt = timestamp;
    }

    public void touch(Instant now) {
        updatedAt = now == null ? Instant.now() : now;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        validateSchedule();
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
        validateSchedule();
    }

    private static LocalDate requireDate(LocalDate value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private static String requireText(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return optionalText(value, max, field);
    }

    private static String optionalText(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }
}
