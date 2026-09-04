package com.flowerfarm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Durable current control lease for one stable robot identity. */
@Entity
@Table(name = "robot_control_leases")
public class RobotControlLease {

    @Id
    @Column(name = "robot_key", nullable = false, length = 160)
    private String robotKey;

    @Column(name = "robot_id", nullable = false, length = 120)
    private String robotId;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "lease_id", nullable = false, length = 80)
    private String leaseId;

    @Column(nullable = false, length = 80)
    private String holder;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RobotControlLease() {
    }

    public RobotControlLease(
            String robotKey, String robotId, boolean simulated,
            String leaseId, String holder, String tokenHash,
            Instant issuedAt, Instant expiresAt) {
        this.robotKey = text(robotKey, "Robot key", 160);
        this.robotId = text(robotId, "Robot id", 120);
        replace(leaseId, holder, tokenHash, issuedAt, expiresAt);
        this.simulated = simulated;
    }

    public void replace(
            String leaseId, String holder, String tokenHash,
            Instant issuedAt, Instant expiresAt) {
        this.leaseId = text(leaseId, "Lease id", 80);
        this.holder = text(holder, "Lease holder", 80);
        this.tokenHash = hash(tokenHash);
        this.issuedAt = instant(issuedAt, "Issued at");
        this.expiresAt = instant(expiresAt, "Expires at");
        if (!this.expiresAt.isAfter(this.issuedAt)) {
            throw new IllegalArgumentException("Lease expiry must follow issue time");
        }
        this.releasedAt = null;
        this.updatedAt = this.issuedAt;
    }

    public void release(Instant releasedAt) {
        this.releasedAt = instant(releasedAt, "Released at");
        this.updatedAt = this.releasedAt;
    }

    public boolean activeAt(Instant now) {
        return releasedAt == null && expiresAt.isAfter(now);
    }

    public boolean belongsTo(String robotId, boolean simulated) {
        return this.robotId.equals(robotId) && this.simulated == simulated;
    }

    public String getRobotKey() { return robotKey; }
    public String getRobotId() { return robotId; }
    public boolean isSimulated() { return simulated; }
    public String getLeaseId() { return leaseId; }
    public String getHolder() { return holder; }
    public String getTokenHash() { return tokenHash; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getReleasedAt() { return releasedAt; }
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
        String normalized = text(value, "Token hash", 64);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Token hash must be a lowercase SHA-256 digest");
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
