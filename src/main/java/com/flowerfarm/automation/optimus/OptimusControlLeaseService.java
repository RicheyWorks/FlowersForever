package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusRobotAdapter.AdapterMode;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;

import java.time.Instant;
import java.util.Objects;

/** Exclusive, expiring control authority bound to one stable robot identity. */
public interface OptimusControlLeaseService {

    int MIN_TTL_SECONDS = 15;
    int MAX_TTL_SECONDS = 300;

    LeaseGrant acquire(RobotIdentity identity, String holder, int ttlSeconds);

    LeaseGrant renew(RobotIdentity identity, String leaseToken, int ttlSeconds);

    void release(RobotIdentity identity, String leaseToken);

    LeaseAuthorization requireValid(RobotIdentity identity, String leaseToken);

    LeaseStatus status(RobotIdentity identity);

    static String robotKey(RobotIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return identity.adapterMode().name().toLowerCase()
                + ":" + identity.robotId();
    }

    static boolean simulated(RobotIdentity identity) {
        return identity.adapterMode() == AdapterMode.SIMULATOR;
    }

    static String holder(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new IllegalArgumentException(
                    "Lease holder must contain between 1 and 80 characters");
        }
        return normalized;
    }

    static int ttl(int value) {
        if (value < MIN_TTL_SECONDS || value > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("Control lease duration must be between "
                    + MIN_TTL_SECONDS + " and " + MAX_TTL_SECONDS + " seconds");
        }
        return value;
    }

    record LeaseGrant(String leaseId, String leaseToken,
                      Instant issuedAt, Instant expiresAt) {
        public LeaseGrant {
            Objects.requireNonNull(leaseId, "leaseId");
            Objects.requireNonNull(leaseToken, "leaseToken");
            Objects.requireNonNull(issuedAt, "issuedAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    record LeaseAuthorization(String leaseId, Instant expiresAt) {
        public LeaseAuthorization {
            Objects.requireNonNull(leaseId, "leaseId");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    record LeaseStatus(boolean active, String leaseId, String expiresAt) {
        public LeaseStatus {
            leaseId = leaseId == null ? "" : leaseId;
            expiresAt = expiresAt == null ? "" : expiresAt;
            if (!active && (!leaseId.isEmpty() || !expiresAt.isEmpty())) {
                throw new IllegalArgumentException(
                        "Inactive lease status must not expose prior lease metadata");
            }
        }

        static LeaseStatus inactive() {
            return new LeaseStatus(false, "", "");
        }
    }
}
