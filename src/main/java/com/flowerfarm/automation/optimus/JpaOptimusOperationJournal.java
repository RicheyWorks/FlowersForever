package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusRobotAdapter.AdapterMode;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.CommandReceipt;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotState;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskStatus;
import com.flowerfarm.model.RobotOperationEvent;
import com.flowerfarm.repository.RobotOperationEventRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Database-backed append-only Optimus operation journal. */
@Component
public class JpaOptimusOperationJournal implements OptimusOperationJournal {

    private final RobotOperationEventRepository repository;

    public JpaOptimusOperationJournal(RobotOperationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public OperationEvent recordReceipt(
            String pluginId, String action, RobotIdentity identity,
            CommandReceipt receipt) {
        return recordReceipt(
                pluginId, action, "", "", identity, receipt);
    }

    @Override
    @Transactional
    public OperationEvent recordExecutionReceipt(
            String pluginId, String requestId, String requestFingerprint,
            RobotIdentity identity, CommandReceipt receipt) {
        return recordReceipt(pluginId, "execute-staged-task", requestId,
                requestFingerprint, identity, receipt);
    }

    private OperationEvent recordReceipt(
            String pluginId, String action, String requestId,
            String requestFingerprint, RobotIdentity identity,
            CommandReceipt receipt) {
        OperationEvent previous = latest(
                pluginId, receipt.commandId()).orElse(null);
        if (previous != null) {
            requireSameRobot(previous, identity);
        }
        double progress = OptimusOperationLifecycle.receiptProgress(
                receipt.status(), previous);
        OperationEvent event = new OperationEvent(
                pluginId, receipt.commandId(), action, receipt.status(), progress,
                receipt.message(), identity.robotId(), simulated(identity),
                receipt.acceptedAt(), receipt.acceptedAt(), requestId,
                requestFingerprint);
        return appendUnlessUnchanged(event);
    }

    @Override
    @Transactional
    public OperationEvent recordState(
            String pluginId, RobotIdentity identity, RobotState state) {
        if (state.taskCommandId().isEmpty()) {
            throw new IllegalArgumentException(
                    "Robot state must identify an operation before it can be journaled");
        }
        Optional<OperationEvent> previous = latest(pluginId, state.taskCommandId());
        previous.ifPresent(event -> requireSameRobot(event, identity));
        OperationEvent event = OptimusOperationLifecycle.reconcileState(
                pluginId, identity, state, previous.orElse(null));
        return appendUnlessUnchanged(event);
    }

    @Override
    @Transactional
    public OperationEvent recordUnknown(
            String pluginId, RobotIdentity identity, OperationEvent previous,
            Instant observedAt, String reason) {
        requireSameRobot(previous, identity);
        OperationEvent event = new OperationEvent(
                pluginId, previous.operationId(), previous.action(),
                TaskStatus.UNKNOWN, previous.progressPercent(), reason,
                identity.robotId(), simulated(identity), previous.acceptedAt(),
                observedAt);
        return appendUnlessUnchanged(event);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OperationEvent> latest(String pluginId, String operationId) {
        return repository.findLatest(pluginId, operationId).map(
                JpaOptimusOperationJournal::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OperationEvent> latestByRequest(
            String pluginId, String requestId) {
        return repository.findByRequest(pluginId, requestId).map(
                JpaOptimusOperationJournal::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OperationEvent> recent(String pluginId, int limit) {
        return repository.findRecentOperations(pluginId, limit).stream()
                .map(JpaOptimusOperationJournal::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalEvent> after(
            String pluginId, long afterEventId, int limit) {
        return repository.findAfter(pluginId, afterEventId, limit).stream()
                .map(JpaOptimusOperationJournal::toJournalEvent)
                .toList();
    }

    private OperationEvent appendUnlessUnchanged(OperationEvent event) {
        Optional<OperationEvent> previous = latest(
                event.pluginId(), event.operationId());
        if (previous.isPresent() && sameLifecycle(previous.orElseThrow(), event)) {
            return previous.orElseThrow();
        }
        return fromEntity(repository.append(toEntity(event)));
    }

    private static boolean sameLifecycle(
            OperationEvent left, OperationEvent right) {
        return left.action().equals(right.action())
                && left.status() == right.status()
                && Double.compare(left.progressPercent(), right.progressPercent()) == 0
                && left.statusMessage().equals(right.statusMessage())
                && left.robotId().equals(right.robotId())
                && left.simulated() == right.simulated()
                && left.requestId().equals(right.requestId())
                && left.requestFingerprint().equals(right.requestFingerprint());
    }

    private static void requireSameRobot(
            OperationEvent event, RobotIdentity identity) {
        if (!event.robotId().equals(identity.robotId())
                || event.simulated() != simulated(identity)) {
            throw new IllegalStateException(
                    "Stored operation belongs to a different robot identity");
        }
    }

    private static boolean simulated(RobotIdentity identity) {
        return identity.adapterMode() == AdapterMode.SIMULATOR;
    }

    private static RobotOperationEvent toEntity(OperationEvent event) {
        return new RobotOperationEvent(
                event.pluginId(), event.operationId(), event.action(),
                event.status().name(), event.progressPercent(), event.statusMessage(),
                event.robotId(), event.simulated(), event.acceptedAt(),
                event.observedAt(), event.requestId(), event.requestFingerprint());
    }

    private static OperationEvent fromEntity(RobotOperationEvent event) {
        return new OperationEvent(
                event.getPluginId(), event.getOperationId(), event.getAction(),
                TaskStatus.valueOf(event.getStatus()), event.getProgressPercent(),
                event.getStatusMessage(), event.getRobotId(), event.isSimulated(),
                event.getAcceptedAt(), event.getObservedAt(),
                event.getRequestId(), event.getRequestFingerprint());
    }

    private static JournalEvent toJournalEvent(RobotOperationEvent event) {
        return new JournalEvent(event.getId(), fromEntity(event));
    }
}
