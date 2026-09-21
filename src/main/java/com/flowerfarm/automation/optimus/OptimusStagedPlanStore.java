package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskPlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

/** Restart-safe storage boundary for the one currently staged robot task. */
public interface OptimusStagedPlanStore {

    StoredPlan replace(
            RobotIdentity identity, String leaseId, TaskPlan plan);

    StoredPlan claimExecution(
            RobotIdentity identity, String leaseId, String planToken,
            String requestId, Instant requestedAt);

    Optional<StoredPlan> current(RobotIdentity identity);

    void clear(RobotIdentity identity);

    /** Atomically clears only the pending execution with this exact request and plan fingerprint. */
    void clearExecution(RobotIdentity identity, String requestId, String planFingerprint);

    record StoredPlan(
            String robotId, boolean simulated, String leaseId,
            String planTokenHash, Instant stagedAt, Instant expiresAt,
            String executionRequestId, Instant executionRequestedAt) {

        public StoredPlan(
                String robotId, boolean simulated, String leaseId,
                String planTokenHash, Instant stagedAt, Instant expiresAt) {
            this(robotId, simulated, leaseId, planTokenHash, stagedAt,
                    expiresAt, "", null);
        }

        public StoredPlan {
            executionRequestId = executionRequestId == null
                    ? "" : executionRequestId.trim();
            if (executionRequestId.isEmpty() != (executionRequestedAt == null)) {
                throw new IllegalArgumentException(
                        "Execution request id and timestamp must be stored together");
            }
        }

        public boolean matchesToken(String planToken) {
            return matchesFingerprint(
                    OptimusRobotAdapter.planFingerprint(planToken));
        }

        public boolean matchesFingerprint(String fingerprint) {
            String supplied = fingerprint == null ? "" : fingerprint.trim();
            return MessageDigest.isEqual(
                    planTokenHash.getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII));
        }

        public boolean executionPending() {
            return !executionRequestId.isEmpty();
        }

        public boolean claimedBy(String requestId) {
            String supplied = requestId == null ? "" : requestId.trim();
            return executionPending() && MessageDigest.isEqual(
                    executionRequestId.getBytes(StandardCharsets.UTF_8),
                    supplied.getBytes(StandardCharsets.UTF_8));
        }
    }
}
