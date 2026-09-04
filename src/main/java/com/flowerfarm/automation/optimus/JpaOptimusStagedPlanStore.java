package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskPlan;
import com.flowerfarm.model.RobotStagedPlan;
import com.flowerfarm.repository.RobotStagedPlanJpaRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.robotKey;
import static com.flowerfarm.automation.optimus.OptimusControlLeaseService.simulated;

/** JPA-backed staged-plan resume store. */
@Component
public class JpaOptimusStagedPlanStore implements OptimusStagedPlanStore {

    private final RobotStagedPlanJpaRepository repository;

    public JpaOptimusStagedPlanStore(
            RobotStagedPlanJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public StoredPlan replace(
            RobotIdentity identity, String leaseId, TaskPlan plan) {
        String key = robotKey(identity);
        RobotStagedPlan stored = repository.findForUpdate(key).orElse(null);
        requireIdentity(stored, identity);
        String tokenHash = OptimusRobotAdapter.planFingerprint(plan.planToken());
        if (stored == null) {
            stored = new RobotStagedPlan(
                    key, identity.robotId(), simulated(identity), leaseId,
                    tokenHash, plan.stagedAt(), plan.expiresAt());
        } else {
            stored.replace(leaseId, tokenHash, plan.stagedAt(), plan.expiresAt());
        }
        return fromEntity(repository.saveAndFlush(stored));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredPlan> current(RobotIdentity identity) {
        return repository.findById(robotKey(identity)).map(stored -> {
            requireIdentity(stored, identity);
            return fromEntity(stored);
        });
    }

    @Override
    @Transactional
    public StoredPlan claimExecution(
            RobotIdentity identity, String leaseId, String planToken,
            String requestId, Instant requestedAt) {
        RobotStagedPlan stored = repository.findForUpdate(
                robotKey(identity)).orElseThrow(() ->
                new IllegalStateException("No reviewed Optimus task is staged"));
        requireIdentity(stored, identity);
        StoredPlan current = fromEntity(stored);
        if (!current.leaseId().equals(leaseId)
                || !current.matchesToken(planToken)) {
            throw new IllegalStateException(
                    "Staged Optimus task changed before execution was claimed");
        }
        stored.claimExecution(requestId, requestedAt);
        return fromEntity(repository.saveAndFlush(stored));
    }

    @Override
    @Transactional
    public void clear(RobotIdentity identity) {
        RobotStagedPlan stored = repository.findForUpdate(
                robotKey(identity)).orElse(null);
        requireIdentity(stored, identity);
        if (stored != null) {
            repository.delete(stored);
        }
    }

    private static StoredPlan fromEntity(RobotStagedPlan stored) {
        return new StoredPlan(
                stored.getRobotId(), stored.isSimulated(), stored.getLeaseId(),
                stored.getPlanTokenHash(), stored.getStagedAt(),
                stored.getExpiresAt(), stored.getExecutionRequestId(),
                stored.getExecutionRequestedAt());
    }

    private static void requireIdentity(
            RobotStagedPlan stored, RobotIdentity identity) {
        if (stored != null && !stored.belongsTo(
                identity.robotId(), simulated(identity))) {
            throw new IllegalStateException(
                    "Stored staged plan belongs to a different robot identity");
        }
    }
}
