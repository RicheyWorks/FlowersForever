package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;

/** Durable mapping from one upstream AI/source record to its FlowersForever record. */
@Entity
@Table(name = "farm_data_import_receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_farm_data_source_record",
                columnNames = {"source_system", "record_type", "source_key"})
})
public class FarmDataImportReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system", nullable = false, length = 120)
    private String sourceSystem;

    @Column(name = "record_type", nullable = false, length = 32)
    private String recordType;

    @Column(name = "source_key", nullable = false, length = 240)
    private String sourceKey;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(name = "content_fingerprint", nullable = false, length = 64)
    private String contentFingerprint;

    @Column(name = "target_record_id", nullable = false)
    private Long targetRecordId;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FarmDataImportReceipt() {
    }

    public FarmDataImportReceipt(
            String sourceSystem, String recordType, String sourceKey, String action,
            String contentFingerprint, Long targetRecordId) {
        this.sourceSystem = normalizedSourceSystem(sourceSystem);
        this.recordType = slug(recordType, "Record type", 32);
        this.sourceKey = normalizedSourceKey(sourceKey);
        this.action = slug(action, "Action", 30);
        this.contentFingerprint = fingerprint(contentFingerprint);
        this.targetRecordId = positiveId(targetRecordId);
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (importedAt == null) {
            importedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public void updateTarget(String action, String contentFingerprint, Long targetRecordId) {
        this.action = slug(action, "Action", 30);
        this.contentFingerprint = fingerprint(contentFingerprint);
        this.targetRecordId = positiveId(targetRecordId);
    }

    public boolean matches(String expectedAction, String expectedFingerprint) {
        return action.equals(slug(expectedAction, "Action", 30))
                && contentFingerprint.equals(fingerprint(expectedFingerprint));
    }

    public Long getId() { return id; }
    public String getSourceSystem() { return sourceSystem; }
    public String getRecordType() { return recordType; }
    public String getSourceKey() { return sourceKey; }
    public String getAction() { return action; }
    public String getContentFingerprint() { return contentFingerprint; }
    public Long getTargetRecordId() { return targetRecordId; }
    public Instant getImportedAt() { return importedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }

    public static String normalizedSourceSystem(String value) {
        return text(value, "Source system", 120).toLowerCase(Locale.ROOT);
    }

    public static String normalizedSourceKey(String value) {
        return text(value, "Source key", 240);
    }

    private static String slug(String value, String field, int max) {
        String normalized = text(value, field, max).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException(
                    field + " must be a lowercase letter, number, or hyphen slug.");
        }
        return normalized;
    }

    private static String fingerprint(String value) {
        String normalized = text(value, "Content fingerprint", 64);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Content fingerprint must be a lowercase SHA-256 digest.");
        }
        return normalized;
    }

    private static Long positiveId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Target record id must be positive.");
        }
        return value;
    }

    private static String text(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(
                    field + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }
}
