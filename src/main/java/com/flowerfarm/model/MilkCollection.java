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
import java.time.Instant;
import java.time.LocalDate;

/** A dated milking session note, separate from inventory and sales records. */
@Entity
@Table(name = "milk_collections", indexes = {
        @Index(name = "idx_milk_collection_date", columnList = "collection_date"),
        @Index(name = "idx_milk_collection_group", columnList = "group_name")
})
public class MilkCollection {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_date", nullable = false)
    private LocalDate collectionDate;

    @Column(name = "group_name", nullable = false, length = 120)
    private String groupName;

    @Column(name = "session_name", nullable = false, length = 120)
    private String sessionName;

    @Column(name = "tank_location", nullable = false, length = 128)
    private String tankLocation;

    @Column(nullable = false, precision = 9, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_code", nullable = false, length = 16)
    private MilkCollectionUnit unit;

    @Column(nullable = false, length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MilkCollection() {
    }

    public MilkCollection(LocalDate collectionDate, String groupName,
                          String sessionName, String tankLocation, BigDecimal amount,
                          MilkCollectionUnit unit, String notes) {
        setCollectionDate(collectionDate);
        setGroupName(groupName);
        setSessionName(sessionName);
        setTankLocation(tankLocation);
        setAmount(amount);
        setUnit(unit);
        setNotes(notes);
        validate();
    }

    public Long getId() { return id; }
    public LocalDate getCollectionDate() { return collectionDate; }
    public String getGroupName() { return groupName; }
    public String getSessionName() { return sessionName; }
    public String getTankLocation() { return tankLocation; }
    public BigDecimal getAmount() { return amount; }
    public MilkCollectionUnit getUnit() { return unit; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCollectionDate(LocalDate collectionDate) {
        if (collectionDate == null) {
            throw new IllegalArgumentException("Collection date is required.");
        }
        this.collectionDate = collectionDate;
    }

    public void setGroupName(String groupName) {
        this.groupName = text(groupName, 120, "Group name", true);
    }

    public void setSessionName(String sessionName) {
        this.sessionName = text(sessionName, 120, "Session name", true);
    }

    public void setTankLocation(String tankLocation) {
        this.tankLocation = text(tankLocation, 128, "Tank or location", false);
    }

    public void setAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero and at most 1,000,000.00.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount cannot exceed two decimal places.");
        }
        this.amount = amount.setScale(2);
    }

    public void setUnit(MilkCollectionUnit unit) {
        if (unit == null) {
            throw new IllegalArgumentException("Unit is required.");
        }
        this.unit = unit;
    }

    public void setNotes(String notes) {
        this.notes = text(notes, 2000, "Notes", false);
    }

    public void validate() {
        if (collectionDate == null) {
            throw new IllegalArgumentException("Collection date is required.");
        }
        text(groupName, 120, "Group name", true);
        text(sessionName, 120, "Session name", true);
        text(tankLocation, 128, "Tank or location", false);
        setAmount(amount);
        setUnit(unit);
        text(notes, 2000, "Notes", false);
    }

    public void copyBusinessFieldsFrom(MilkCollection source) {
        if (source == null) {
            throw new IllegalArgumentException("Milk collection is required.");
        }
        source.validate();
        setCollectionDate(source.collectionDate);
        setGroupName(source.groupName);
        setSessionName(source.sessionName);
        setTankLocation(source.tankLocation);
        setAmount(source.amount);
        setUnit(source.unit);
        setNotes(source.notes);
    }

    /** Detached copy prevents callers from mutating a managed entity. */
    public MilkCollection snapshot() {
        MilkCollection copy = new MilkCollection(collectionDate, groupName,
                sessionName, tankLocation, amount, unit, notes);
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
