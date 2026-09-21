package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskPlan;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.robotKey;
import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.simulated;

/** Process-local staged-plan store used by direct plugin construction and tests. */
final class TransientOptimusStagedPlanStore
        implements OptimusStagedPlanStore {

    private final Map<String, StoredPlan> plans = new HashMap<>();

    @Override
    public synchronized StoredPlan replace(
            RobotIdentity identity, String leaseId, TaskPlan plan) {
        StoredPlan stored = new StoredPlan(
                identity.robotId(), simulated(identity), leaseId,
                OptimusRobotAdapter.planFingerprint(plan.planToken()),
                plan.stagedAt(), plan.expiresAt());
        plans.put(robotKey(identity), stored);
        return stored;
    }

    @Override
    public synchronized Optional<StoredPlan> current(RobotIdentity identity) {
        StoredPlan stored = plans.get(robotKey(identity));
        requireIdentity(stored, identity);
        return Optional.ofNullable(stored);
    }

    @Override
    public synchronized StoredPlan claimExecution(
            RobotIdentity identity, String leaseId, String planToken,
            String requestId, Instant requestedAt) {
        String key = robotKey(identity);
        StoredPlan stored = plans.get(key);
        requireIdentity(stored, identity);
        if (stored == null) {
            throw new IllegalStateException("No reviewed Optimus task is staged");
        }
        if (!stored.leaseId().equals(leaseId) || !stored.matchesToken(planToken)) {
            throw new IllegalStateException(
                    "Staged Optimus task changed before execution was claimed");
        }
        if (stored.executionPending() && !stored.claimedBy(requestId)) {
            throw new IllegalStateException(
                    "Another Optimus execution request is already pending");
        }
        if (!stored.executionPending()) {
            stored = new StoredPlan(
                    stored.robotId(), stored.simulated(), stored.leaseId(),
                    stored.planTokenHash(), stored.stagedAt(), stored.expiresAt(),
                    requestId, requestedAt);
            plans.put(key, stored);
        }
        return stored;
    }

    @Override
    public synchronized void clear(RobotIdentity identity) {
        StoredPlan stored = plans.get(robotKey(identity));
        requireIdentity(stored, identity);
        plans.remove(robotKey(identity));
    }

    @Override
    public synchronized void clearExecution(
            RobotIdentity identity, String requestId, String planFingerprint) {
        String key = robotKey(identity);
        StoredPlan stored = plans.get(key);
        requireIdentity(stored, identity);
        if (stored != null && stored.claimedBy(requestId) && stored.matchesFingerprint(planFingerprint)) {
            plans.remove(key);
        }
    }

    private static void requireIdentity(
            StoredPlan stored, RobotIdentity identity) {
        if (stored != null && (!stored.robotId().equals(identity.robotId())
                || stored.simulated() != simulated(identity))) {
            throw new IllegalStateException(
                    "Stored staged plan belongs to a different robot identity");
        }
    }
}
