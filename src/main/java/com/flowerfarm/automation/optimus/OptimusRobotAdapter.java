package com.flowerfarm.automation.optimus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Vendor-boundary port for an Optimus robot or simulator. No Tesla transport
 * assumptions belong in the harness plugin.
 */
public interface OptimusRobotAdapter {

    /** Upper bound for human review before a staged task must be regenerated. */
    long MAX_STAGED_PLAN_TTL_SECONDS = 300;

    /** Stable identity used to prevent commands from reaching the wrong robot. */
    RobotIdentity identity();

    RobotState observe();

    TaskPlan stage(TaskDraft draft);

    void cancelStagedTask();

    CommandReceipt executeStagedTask(String planToken);

    CommandReceipt cancelActiveTask(String commandId);

    CommandReceipt returnToSafePose();

    CommandReceipt emergencyStop();

    /** Stable, non-plaintext identity for matching adapter and stored plans. */
    static String planFingerprint(String planToken) {
        String normalized = bounded(planToken, "planToken", 160);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    enum AdapterMode {
        SIMULATOR,
        HARDWARE
    }

    enum TaskStatus {
        IDLE,
        STAGED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED,
        STOPPED,
        UNKNOWN
    }

    record RobotIdentity(AdapterMode adapterMode, String robotId, String model,
                         String softwareVersion, String coordinateFrame) {
        public RobotIdentity {
            if (adapterMode == null) {
                throw new IllegalArgumentException("adapterMode is required");
            }
            robotId = bounded(robotId, "robotId", 120);
            model = bounded(model, "model", 120);
            softwareVersion = bounded(softwareVersion, "softwareVersion", 80);
            coordinateFrame = bounded(coordinateFrame, "coordinateFrame", 120);
        }
    }

    /**
     * Current adapter state. The plan fingerprint stays populated on an
     * originating running or recent task so interrupted execution can be
     * matched without exposing the opaque plan token.
     */
    record RobotState(String connectionState, String motionState,
                      double batteryPercent, boolean emergencyStopped,
                      boolean readyForCommand, String currentTask,
                      Map<String, Object> pose, String taskCommandId,
                      TaskStatus taskStatus, double taskProgressPercent,
                      String taskStatusMessage, String stagedPlanFingerprint,
                      Instant observedAt) {

        /** Compatibility constructor for adapters without plan fingerprinting. */
        public RobotState(String connectionState, String motionState,
                          double batteryPercent, boolean emergencyStopped,
                          boolean readyForCommand, String currentTask,
                          Map<String, Object> pose, String taskCommandId,
                          TaskStatus taskStatus, double taskProgressPercent,
                          String taskStatusMessage, Instant observedAt) {
            this(connectionState, motionState, batteryPercent, emergencyStopped,
                    readyForCommand, currentTask, pose, taskCommandId, taskStatus,
                    taskProgressPercent, taskStatusMessage, "", observedAt);
        }

        /** Compatibility constructor for adapters that have no active task yet. */
        public RobotState(String connectionState, String motionState,
                          double batteryPercent, boolean emergencyStopped,
                          boolean readyForCommand, String currentTask,
                          Map<String, Object> pose, Instant observedAt) {
            this(connectionState, motionState, batteryPercent, emergencyStopped,
                    readyForCommand, currentTask, pose, "",
                    legacyStatus(motionState, emergencyStopped), 0, "", observedAt);
        }

        public RobotState {
            connectionState = required(connectionState, "connectionState");
            motionState = required(motionState, "motionState");
            if (!Double.isFinite(batteryPercent)
                    || batteryPercent < 0 || batteryPercent > 100) {
                throw new IllegalArgumentException(
                        "batteryPercent must be between 0 and 100");
            }
            currentTask = currentTask == null ? "" : currentTask.trim();
            pose = pose == null ? Map.of() : Map.copyOf(pose);
            taskCommandId = optionalBounded(taskCommandId, "taskCommandId", 160);
            taskStatus = taskStatus == null ? TaskStatus.UNKNOWN : taskStatus;
            if (!Double.isFinite(taskProgressPercent)
                    || taskProgressPercent < 0 || taskProgressPercent > 100) {
                throw new IllegalArgumentException(
                        "taskProgressPercent must be between 0 and 100");
            }
            taskStatusMessage = optionalBounded(
                    taskStatusMessage, "taskStatusMessage", 500);
            stagedPlanFingerprint = optionalBounded(
                    stagedPlanFingerprint, "stagedPlanFingerprint", 64);
            if (!stagedPlanFingerprint.isEmpty()
                    && !stagedPlanFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "stagedPlanFingerprint must be a lowercase SHA-256 digest");
            }
            if (!stagedPlanFingerprint.isEmpty()
                    && taskStatus == TaskStatus.IDLE) {
                throw new IllegalArgumentException(
                        "stagedPlanFingerprint requires a staged or recent task");
            }
            if (taskStatus == TaskStatus.RUNNING && taskCommandId.isEmpty()) {
                throw new IllegalArgumentException(
                        "A running task must report its command id");
            }
            observedAt = requiredInstant(observedAt, "observedAt");
        }

        private static TaskStatus legacyStatus(
                String motionState, boolean emergencyStopped) {
            if (emergencyStopped) {
                return TaskStatus.STOPPED;
            }
            return "STAGED".equalsIgnoreCase(motionState)
                    ? TaskStatus.STAGED : TaskStatus.IDLE;
        }
    }

    record TaskDraft(String task, String workArea, int maxDurationSeconds,
                     List<String> constraints) {
        public TaskDraft {
            task = bounded(task, "task", 240);
            workArea = bounded(workArea, "workArea", 120);
            if (maxDurationSeconds < 1 || maxDurationSeconds > 900) {
                throw new IllegalArgumentException(
                        "maxDurationSeconds must be between 1 and 900");
            }
            constraints = constraints == null ? List.of() : List.copyOf(constraints);
            if (constraints.size() > 12) {
                throw new IllegalArgumentException("At most 12 constraints are supported");
            }
            constraints = constraints.stream()
                    .map(value -> bounded(value, "constraint", 200))
                    .toList();
        }
    }

    record TaskPlan(String planToken, TaskDraft draft, Instant stagedAt,
                    Instant expiresAt) {

        public TaskPlan(String planToken, TaskDraft draft, Instant stagedAt) {
            this(planToken, draft, stagedAt, stagedAt == null ? null
                    : stagedAt.plusSeconds(MAX_STAGED_PLAN_TTL_SECONDS));
        }

        public TaskPlan {
            planToken = bounded(planToken, "planToken", 160);
            if (draft == null) {
                throw new IllegalArgumentException("draft is required");
            }
            stagedAt = requiredInstant(stagedAt, "stagedAt");
            expiresAt = requiredInstant(expiresAt, "expiresAt");
            if (!expiresAt.isAfter(stagedAt)) {
                throw new IllegalArgumentException("expiresAt must be after stagedAt");
            }
            if (expiresAt.isAfter(
                    stagedAt.plusSeconds(MAX_STAGED_PLAN_TTL_SECONDS))) {
                throw new IllegalArgumentException("Optimus task plans must expire within "
                        + MAX_STAGED_PLAN_TTL_SECONDS + " seconds");
            }
        }
    }

    record CommandReceipt(String commandId, TaskStatus status, String message,
                          Instant acceptedAt) {
        public CommandReceipt {
            commandId = bounded(commandId, "commandId", 160);
            if (status == null) {
                throw new IllegalArgumentException("status is required");
            }
            message = optionalBounded(message, "message", 500);
            acceptedAt = requiredInstant(acceptedAt, "acceptedAt");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String bounded(String value, String label, int maxLength) {
        String normalized = required(value, label);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + " must contain at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optionalBounded(
            String value, String label, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + " must contain at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static Instant requiredInstant(Instant value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }
}
