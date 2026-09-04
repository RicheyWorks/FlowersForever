package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusRobotAdapter.AdapterMode;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.CommandReceipt;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotState;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Process-local journal used only by directly constructed plugins and tests. */
final class TransientOptimusOperationJournal implements OptimusOperationJournal {

    private final Map<String, OperationEvent> latest = new LinkedHashMap<>();
    private final List<JournalEvent> events = new ArrayList<>();
    private long nextEventId = 1;

    @Override
    public synchronized OperationEvent recordReceipt(
            String pluginId, String action, RobotIdentity identity,
            CommandReceipt receipt) {
        return recordReceipt(pluginId, action, "", "", identity, receipt);
    }

    @Override
    public synchronized OperationEvent recordExecutionReceipt(
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
    public synchronized OperationEvent recordState(
            String pluginId, RobotIdentity identity, RobotState state) {
        if (state.taskCommandId().isEmpty()) {
            throw new IllegalArgumentException(
                    "Robot state must identify an operation before it can be journaled");
        }
        OperationEvent previous = latest(pluginId, state.taskCommandId()).orElse(null);
        if (previous != null) {
            requireSameRobot(previous, identity);
        }
        OperationEvent event = OptimusOperationLifecycle.reconcileState(
                pluginId, identity, state, previous);
        return appendUnlessUnchanged(event);
    }

    @Override
    public synchronized OperationEvent recordUnknown(
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
    public synchronized Optional<OperationEvent> latest(
            String pluginId, String operationId) {
        return Optional.ofNullable(latest.get(key(pluginId, operationId)));
    }

    @Override
    public synchronized Optional<OperationEvent> latestByRequest(
            String pluginId, String requestId) {
        for (int index = events.size() - 1; index >= 0; index--) {
            OperationEvent event = events.get(index).event();
            if (event.pluginId().equals(pluginId)
                    && event.requestId().equals(requestId)) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    @Override
    public synchronized List<OperationEvent> recent(String pluginId, int limit) {
        List<OperationEvent> result = new ArrayList<>();
        Set<String> operationIds = new HashSet<>();
        for (int index = events.size() - 1;
                index >= 0 && result.size() < limit; index--) {
            OperationEvent event = events.get(index).event();
            if (event.pluginId().equals(pluginId)
                    && operationIds.add(event.operationId())) {
                result.add(event);
            }
        }
        return List.copyOf(result);
    }

    @Override
    public synchronized List<JournalEvent> after(
            String pluginId, long afterEventId, int limit) {
        return events.stream()
                .filter(entry -> entry.eventId() > afterEventId)
                .filter(entry -> entry.event().pluginId().equals(pluginId))
                .limit(limit)
                .toList();
    }

    private OperationEvent appendUnlessUnchanged(OperationEvent event) {
        String key = key(event.pluginId(), event.operationId());
        OperationEvent previous = latest.get(key);
        if (previous != null && sameLifecycle(previous, event)) {
            return previous;
        }
        latest.put(key, event);
        events.add(new JournalEvent(nextEventId++, event));
        return event;
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

    private static String key(String pluginId, String operationId) {
        return pluginId + '\0' + operationId;
    }
}
