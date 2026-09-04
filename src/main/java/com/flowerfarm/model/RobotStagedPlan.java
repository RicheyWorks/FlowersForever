package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Durable resume metadata for the one staged plan associated with a robot. */
@Entity
@Table(name = "robot_staged_plans")
public class RobotStagedPlan {

    @Id
    @Column(name = "robot_key", nullable = false, length = 160)
    private String robotKey;

    @Column(name = "robot_id", nullable = false, length = 120)
    private String robotId;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "lease_id", nullable = false, length = 80)
    private String leaseId;

    @Column(name = "plan_token_hash", nullable = false, length = 64)
    private String planTokenHash;

    @Column(name = "staged_at", nullable = false)
    private Instant stagedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "execution_request_id", length = 160)
    private String executionRequestId;

    @Column(name = "execution_requested_at")
    private Instant executionRequestedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RobotStagedPlan() {
    }

    public RobotStagedPlan(
            String robotKey, String robotId, boolean simulated,
            String leaseId, String planTokenHash,
            Instant stagedAt, Instant expiresAt) {
        this.robotKey = text(robotKey, "Robot key", 160);
        this.robotId = text(robotId, "Robot id", 120);
        this.simulated = simulated;
        replace(leaseId, planTokenHash, stagedAt, expiresAt);
    }

    public void replace(
            String leaseId, String planTokenHash,
            Instant stagedAt, Instant expiresAt) {
        this.leaseId = text(leaseId, "Lease id", 80);
        this.planTokenHash = hash(planTokenHash);
        this.stagedAt = instant(stagedAt, "Staged at");
        this.expiresAt = instant(expiresAt, "Expires at");
        if (!this.expiresAt.isAfter(this.stagedAt)) {
            throw new IllegalArgumentException(
                    "Staged-plan expiry must follow staging time");
        }
        this.updatedAt = this.stagedAt;
        this.executionRequestId = null;
        this.executionRequestedAt = null;
    }

    public void claimExecution(String requestId, Instant requestedAt) {
        String normalizedRequestId = text(
                requestId, "Execution request id", 160);
        Instant normalizedRequestedAt = instant(
                requestedAt, "Execution requested at");
        if (executionRequestId != null
                && !executionRequestId.equals(normalizedRequestId)) {
            throw new IllegalStateException(
                    "Another Optimus execution request is already pending");
        }
        if (executionRequestId == null) {
            executionRequestId = normalizedRequestId;
            executionRequestedAt = normalizedRequestedAt;
            updatedAt = normalizedRequestedAt;
        }
    }

    public boolean belongsTo(String robotId, boolean simulated) {
        return this.robotId.equals(robotId) && this.simulated == simulated;
    }

    public String getRobotKey() { return robotKey; }
    public String getRobotId() { return robotId; }
    public boolean isSimulated() { return simulated; }
    public String getLeaseId() { return leaseId; }
    public String getPlanTokenHash() { return planTokenHash; }
    public Instant getStagedAt() { return stagedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getExecutionRequestId() { return executionRequestId; }
    public Instant getExecutionRequestedAt() { return executionRequestedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    private static String text(String value, String field, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field
                    + " must contain between 1 and " + max + " characters");
        }
        return normalized;
    }

    private static String hash(String value) {
        String normalized = text(value, "Plan token hash", 64);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Plan token hash must be a lowercase SHA-256 digest");
        }
        return normalized;
    }

    private static Instant instant(Instant value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
