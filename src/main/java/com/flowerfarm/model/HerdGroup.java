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

/** A current livestock group planning snapshot, separate from animal and sales records. */
@Entity
@Table(name = "herd_groups", indexes = {
        @Index(name = "idx_herd_group_species", columnList = "species"),
        @Index(name = "idx_herd_group_next_check", columnList = "next_check_date")
})
public class HerdGroup {

    private static final int MAX_HEAD_COUNT = 1_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_name", nullable = false, length = 120)
    private String groupName;

    @Column(nullable = false, length = 120)
    private String species;

    @Column(name = "head_count", nullable = false)
    private Integer headCount;

    @Column(nullable = false, length = 128)
    private String location;

    @Column(nullable = false, length = 120)
    private String purpose;

    @Column(name = "next_check_date")
    private LocalDate nextCheckDate;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HerdGroup() {
    }

    public HerdGroup(String groupName, String species, Integer headCount,
                     String location, String purpose, LocalDate nextCheckDate,
                     String notes) {
        setGroupName(groupName);
        setSpecies(species);
        setHeadCount(headCount);
        setLocation(location);
        setPurpose(purpose);
        setNextCheckDate(nextCheckDate);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public String getGroupName() { return groupName; }
    public String getSpecies() { return species; }
    public Integer getHeadCount() { return headCount; }
    public String getLocation() { return location; }
    public String getPurpose() { return purpose; }
    public LocalDate getNextCheckDate() { return nextCheckDate; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setGroupName(String groupName) {
        this.groupName = text(groupName, 120, "Group name", true);
    }

    public void setSpecies(String species) {
        this.species = text(species, 120, "Species", true);
    }

    public void setHeadCount(Integer headCount) {
        if (headCount == null || headCount < 0 || headCount > MAX_HEAD_COUNT) {
            throw new IllegalArgumentException("Head count must be between 0 and 1,000,000.");
        }
        this.headCount = headCount;
    }

    public void setLocation(String location) {
        this.location = text(location, 128, "Location", false);
    }

    public void setPurpose(String purpose) {
        this.purpose = text(purpose, 120, "Purpose", false);
    }

    public void setNextCheckDate(LocalDate nextCheckDate) {
        this.nextCheckDate = nextCheckDate;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (groupName == null || species == null || headCount == null
                || location == null || purpose == null || notes == null) {
            throw new IllegalArgumentException("Complete the required herd group details.");
        }
        text(groupName, 120, "Group name", true);
        text(species, 120, "Species", true);
        setHeadCount(headCount);
        text(location, 128, "Location", false);
        text(purpose, 120, "Purpose", false);
        text(notes, 2000, "Notes", false);
    }

    public void copyBusinessFieldsFrom(HerdGroup source) {
        if (source == null) {
            throw new IllegalArgumentException("Herd group is required.");
        }
        source.validate();
        setGroupName(source.groupName);
        setSpecies(source.species);
        setHeadCount(source.headCount);
        setLocation(source.location);
        setPurpose(source.purpose);
        setNextCheckDate(source.nextCheckDate);
        setNotes(source.notes);
    }

    /** Detached copy prevents callers from mutating a managed entity. */
    public HerdGroup snapshot() {
        HerdGroup copy = new HerdGroup(groupName, species, headCount, location,
                purpose, nextCheckDate, notes);
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
