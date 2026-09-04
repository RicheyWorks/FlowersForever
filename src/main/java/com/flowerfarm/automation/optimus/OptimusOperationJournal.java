package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusRobotAdapter.CommandReceipt;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotState;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Append-only lifecycle journal behind the provider-neutral Optimus tools. */
public interface OptimusOperationJournal {

    OperationEvent recordReceipt(
            String pluginId, String action, RobotIdentity identity,
            CommandReceipt receipt);

    OperationEvent recordExecutionReceipt(
            String pluginId, String requestId, String requestFingerprint,
            RobotIdentity identity, CommandReceipt receipt);

    OperationEvent recordState(
            String pluginId, RobotIdentity identity, RobotState state);

    OperationEvent recordUnknown(
            String pluginId, RobotIdentity identity, OperationEvent previous,
            Instant observedAt, String reason);

    Optional<OperationEvent> latest(String pluginId, String operationId);

    Optional<OperationEvent> latestByRequest(String pluginId, String requestId);

    List<OperationEvent> recent(String pluginId, int limit);

    List<JournalEvent> after(String pluginId, long afterEventId, int limit);

    record JournalEvent(long eventId, OperationEvent event) {

        public JournalEvent {
            if (eventId < 1) {
                throw new IllegalArgumentException("Event id must be positive");
            }
            if (event == null) {
                throw new IllegalArgumentException("Operation event is required");
            }
        }
    }

    record OperationEvent(
            String pluginId, String operationId, String action,
            TaskStatus status, double progressPercent, String statusMessage,
            String robotId, boolean simulated, Instant acceptedAt,
            Instant observedAt, String requestId, String requestFingerprint) {

        public OperationEvent(
                String pluginId, String operationId, String action,
                TaskStatus status, double progressPercent, String statusMessage,
                String robotId, boolean simulated, Instant acceptedAt,
                Instant observedAt) {
            this(pluginId, operationId, action, status, progressPercent,
                    statusMessage, robotId, simulated, acceptedAt, observedAt,
                    "", "");
        }

        public OperationEvent {
            requestId = requestId == null ? "" : requestId.trim();
            requestFingerprint = requestFingerprint == null
                    ? "" : requestFingerprint.trim();
            if (requestId.isEmpty() != requestFingerprint.isEmpty()) {
                throw new IllegalArgumentException(
                        "Request id and fingerprint must be stored together");
            }
        }

        public boolean terminal() {
            return status == TaskStatus.SUCCEEDED
                    || status == TaskStatus.FAILED
                    || status == TaskStatus.CANCELLED
                    || status == TaskStatus.STOPPED;
        }

        public boolean matchesRequestFingerprint(String fingerprint) {
            String supplied = fingerprint == null ? "" : fingerprint.trim();
            return !requestId.isEmpty() && MessageDigest.isEqual(
                    requestFingerprint.getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII));
        }
    }
}
