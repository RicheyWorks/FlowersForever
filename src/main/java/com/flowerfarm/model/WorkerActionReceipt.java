package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Immutable result of one retry-safe phone or tablet field operation. */
@Entity
@Table(name = "worker_action_receipts")
public class WorkerActionReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_record_id", nullable = false)
    private Long targetRecordId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "operation_key_hash", nullable = false, unique = true, length = 64)
    private String operationKeyHash;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "response_sha256", nullable = false, length = 64)
    private String responseSha256;

    @Lob
    @Column(name = "response_json", nullable = false)
    private String responseJson;

    protected WorkerActionReceipt() {
    }

    public WorkerActionReceipt(
            Long targetRecordId,
            String action,
            String operationKeyHash,
            String requestFingerprint,
            String responseSha256,
            String responseJson) {
        if (targetRecordId == null || targetRecordId <= 0) {
            throw new IllegalArgumentException("Worker action target id must be positive.");
        }
        this.targetRecordId = targetRecordId;
        this.action = required(action, 32, "Worker action");
        this.operationKeyHash = digest(operationKeyHash, "Operation key hash");
        this.requestFingerprint = digest(requestFingerprint, "Request fingerprint");
        this.responseSha256 = digest(responseSha256, "Response hash");
        this.responseJson = required(responseJson, 1_000_000, "Worker action response");
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public Long getId() { return id; }
    public Long getTargetRecordId() { return targetRecordId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getAction() { return action; }
    public String getOperationKeyHash() { return operationKeyHash; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public String getResponseSha256() { return responseSha256; }
    public String getResponseJson() { return responseJson; }

    private static String digest(String value, String field) {
        String normalized = required(value, 64, field);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest.");
        }
        return normalized;
    }

    private static String required(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }
}
