package com.flowerfarm.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Metadata for one immutable photo captured against an operational farm record. */
@Entity
@Table(name = "field_attachments")
public class FieldAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private FieldAttachmentTargetType targetType;

    @Column(name = "target_record_id", nullable = false)
    private Long targetRecordId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 64)
    private String mediaType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 80)
    private String storageKey;

    @Column(length = 500)
    private String caption;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FieldAttachment() {
    }

    public FieldAttachment(
            FieldAttachmentTargetType targetType,
            Long targetRecordId,
            String originalFilename,
            String mediaType,
            long byteSize,
            String sha256,
            String storageKey,
            String caption,
            Instant capturedAt) {
        this.targetType = java.util.Objects.requireNonNull(targetType, "Attachment target type");
        if (targetRecordId == null || targetRecordId <= 0) {
            throw new IllegalArgumentException("Attachment target id must be positive.");
        }
        if (byteSize < 1) {
            throw new IllegalArgumentException("Attachment size must be positive.");
        }
        this.targetRecordId = targetRecordId;
        this.originalFilename = required(originalFilename, 255, "Attachment filename");
        this.mediaType = required(mediaType, 64, "Attachment media type");
        this.byteSize = byteSize;
        this.sha256 = digest(sha256, "Attachment hash");
        this.storageKey = required(storageKey, 80, "Attachment storage key");
        this.caption = optional(caption, 500, "Attachment caption");
        this.capturedAt = capturedAt == null ? Instant.now() : capturedAt;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public Long getId() { return id; }
    public FieldAttachmentTargetType getTargetType() { return targetType; }
    public Long getTargetRecordId() { return targetRecordId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMediaType() { return mediaType; }
    public long getByteSize() { return byteSize; }
    public String getSha256() { return sha256; }
    @JsonIgnore
    public String getStorageKey() { return storageKey; }
    public String getCaption() { return caption; }
    public Instant getCapturedAt() { return capturedAt; }
    public Instant getCreatedAt() { return createdAt; }

    private static String digest(String value, String label) {
        String normalized = required(value, 64, label);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be a SHA-256 digest.");
        }
        return normalized;
    }

    private static String required(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(label + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }

    private static String optional(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(label + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }
}
