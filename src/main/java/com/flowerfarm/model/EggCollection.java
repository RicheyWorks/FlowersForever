package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** A dated egg collection note, separate from inventory and sales records. */
@Entity
@Table(name = "egg_collections", indexes = {
        @Index(name = "idx_egg_collection_date", columnList = "collection_date"),
        @Index(name = "idx_egg_collection_flock", columnList = "flock_name")
})
public class EggCollection {

    private static final int MAX_COUNT = 1_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_date", nullable = false)
    private LocalDate collectionDate;

    @Column(name = "flock_name", nullable = false, length = 120)
    private String flockName;

    @Column(nullable = false, length = 128)
    private String location;

    @Column(name = "collected_count", nullable = false)
    private Integer collectedCount;

    @Column(name = "unusable_count", nullable = false)
    private Integer unusableCount;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EggCollection() {
    }

    public EggCollection(LocalDate collectionDate, String flockName, String location,
                         Integer collectedCount, Integer unusableCount, String notes) {
        setCollectionDate(collectionDate);
        setFlockName(flockName);
        setLocation(location);
        setCollectedCount(collectedCount);
        setUnusableCount(unusableCount);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public LocalDate getCollectionDate() { return collectionDate; }
    public String getFlockName() { return flockName; }
    public String getLocation() { return location; }
    public Integer getCollectedCount() { return collectedCount; }
    public Integer getUnusableCount() { return unusableCount; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** A display calculation; it does not create sellable inventory. */
    public int getUsableCount() {
        validateCounts();
        return collectedCount - unusableCount;
    }

    public void setCollectionDate(LocalDate collectionDate) {
        if (collectionDate == null) {
            throw new IllegalArgumentException("Collection date is required.");
        }
        this.collectionDate = collectionDate;
    }

    public void setFlockName(String flockName) {
        this.flockName = text(flockName, 120, "Flock name", true);
    }

    public void setLocation(String location) {
        this.location = text(location, 128, "Location", false);
    }

    public void setCollectedCount(Integer collectedCount) {
        this.collectedCount = count(collectedCount, "Collected count");
    }

    public void setUnusableCount(Integer unusableCount) {
        this.unusableCount = count(unusableCount, "Unusable count");
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (collectionDate == null) {
            throw new IllegalArgumentException("Collection date is required.");
        }
        text(flockName, 120, "Flock name", true);
        text(location, 128, "Location", false);
        text(notes, 2000, "Notes", false);
        validateCounts();
    }

    public void copyBusinessFieldsFrom(EggCollection source) {
        if (source == null) {
            throw new IllegalArgumentException("Egg collection is required.");
        }
        source.validate();
        setCollectionDate(source.collectionDate);
        setFlockName(source.flockName);
        setLocation(source.location);
        setCollectedCount(source.collectedCount);
        setUnusableCount(source.unusableCount);
        setNotes(source.notes);
    }

    /** Detached copy prevents callers from mutating a managed entity. */
    public EggCollection snapshot() {
        EggCollection copy = new EggCollection(collectionDate, flockName, location,
                collectedCount, unusableCount, notes);
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

    private void validateCounts() {
        count(collectedCount, "Collected count");
        count(unusableCount, "Unusable count");
        if (unusableCount > collectedCount) {
            throw new IllegalArgumentException(
                    "Unusable count cannot exceed collected count.");
        }
    }

    private static Integer count(Integer value, String field) {
        if (value == null || value < 0 || value > MAX_COUNT) {
            throw new IllegalArgumentException(field + " must be between 0 and 1,000,000.");
        }
        return value;
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
