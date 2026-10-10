package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/** Immutable lifecycle event for one adapter-owned robot operation. */
@Entity
@Table(name = "robot_operation_events")
public class RobotOperationEvent {

    private static final Set<String> STATUSES = Set.of(
            "IDLE", "STAGED", "RUNNING", "SUCCEEDED", "FAILED",
            "CANCELLED", "STOPPED", "UNKNOWN");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plugin_id", nullable = false, length = 30)
    private String pluginId;

    @Column(name = "operation_id", nullable = false, length = 160)
    private String operationId;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "progress_percent", nullable = false)
    private double progressPercent;

    @Column(name = "status_message", nullable = false, length = 500)
    private String statusMessage;

    @Column(name = "robot_id", nullable = false, length = 120)
    private String robotId;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "request_id", length = 160)
    private String requestId;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected RobotOperationEvent() {
    }

    public RobotOperationEvent(
            String pluginId, String operationId, String action, String status,
            double progressPercent, String statusMessage, String robotId,
            boolean simulated, Instant acceptedAt, Instant observedAt) {
        this(pluginId, operationId, action, status, progressPercent,
                statusMessage, robotId, simulated, acceptedAt, observedAt,
                "", "");
    }

    public RobotOperationEvent(
            String pluginId, String operationId, String action, String status,
            double progressPercent, String statusMessage, String robotId,
            boolean simulated, Instant acceptedAt, Instant observedAt,
            String requestId, String requestFingerprint) {
        this.pluginId = slug(pluginId, "Plugin id", 30);
        this.operationId = text(operationId, "Operation id", 160, true);
        this.action = slug(action, "Action", 30);
        this.status = supportedStatus(status);
        if (!Double.isFinite(progressPercent)
                || progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException(
                    "Progress percent must be between 0 and 100.");
        }
        this.progressPercent = progressPercent;
        this.statusMessage = text(statusMessage, "Status message", 500, false);
        this.robotId = text(robotId, "Robot id", 120, true);
        this.simulated = simulated;
        this.acceptedAt = requiredInstant(acceptedAt, "Accepted at");
        this.observedAt = requiredInstant(observedAt, "Observed at");
        String normalizedRequestId = text(requestId, "Request id", 160, false);
        String normalizedRequestFingerprint = hash(requestFingerprint);
        if (normalizedRequestId.isEmpty()
                != normalizedRequestFingerprint.isEmpty()) {
            throw new IllegalArgumentException(
                    "Request id and fingerprint must be stored together.");
        }
        this.requestId = normalizedRequestId.isEmpty()
                ? null : normalizedRequestId;
        this.requestFingerprint = normalizedRequestFingerprint.isEmpty()
                ? null : normalizedRequestFingerprint;
    }

    @PrePersist
    void beforeInsert() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getPluginId() { return pluginId; }
    public String getOperationId() { return operationId; }
    public String getAction() { return action; }
    public String getStatus() { return status; }
    public double getProgressPercent() { return progressPercent; }
    public String getStatusMessage() { return statusMessage; }
    public String getRobotId() { return robotId; }
    public boolean isSimulated() { return simulated; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getObservedAt() { return observedAt; }
    public String getRequestId() { return requestId; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Instant getRecordedAt() { return recordedAt; }

    public void setId(Long id) { this.id = id; }

    private static String supportedStatus(String value) {
        String normalized = value == null ? ""
                : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported robot operation status '" + normalized + "'.");
        }
        return normalized;
    }

    private static String slug(String value, String field, int max) {
        String normalized = text(value, field, max, true)
                .toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException(
                    field + " must be a lowercase letter, number, or hyphen slug.");
        }
        return normalized;
    }

    private static String text(
            String value, String field, int max, boolean required) {
        String normalized = value == null ? "" : value.trim();
        if (required && normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        if (normalized.length() > max) {
            throw new IllegalArgumentException(
                    field + " cannot exceed " + max + " characters.");
        }
        return normalized;
    }

    private static Instant requiredInstant(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value;
    }

    private static String hash(String value) {
        String normalized = text(value, "Request fingerprint", 64, false);
        if (!normalized.isEmpty() && !normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Request fingerprint must be a lowercase SHA-256 digest.");
        }
        return normalized;
    }
}
