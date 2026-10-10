package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.optimus.OptimusOperationJournal.OperationEvent;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.AdapterMode;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotState;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskStatus;

/** Normalizes adapter lifecycle updates before either journal persists them. */
final class OptimusOperationLifecycle {

    private static final String EXTERNAL_ACTION = "external-operation";

    private OptimusOperationLifecycle() {
    }

    static OperationEvent reconcileState(
            String pluginId, RobotIdentity identity, RobotState state,
            OperationEvent previous) {
        if (previous != null && (previous.terminal()
                || state.observedAt().isBefore(previous.observedAt()))) {
            return previous;
        }

        TaskStatus status = state.taskStatus();
        double progress = state.taskProgressPercent();
        String message = state.taskStatusMessage();
        if (previous != null) {
            progress = Math.max(previous.progressPercent(), progress);
            if (regresses(previous.status(), status)) {
                status = TaskStatus.UNKNOWN;
                message = "Adapter reported a regressed task lifecycle phase";
            }
        }

        return new OperationEvent(
                pluginId, state.taskCommandId(), previous == null
                        ? EXTERNAL_ACTION : previous.action(),
                status, progress, message, identity.robotId(),
                identity.adapterMode() == AdapterMode.SIMULATOR,
                previous == null ? state.observedAt() : previous.acceptedAt(),
                state.observedAt());
    }

    static double receiptProgress(
            TaskStatus status, OperationEvent previous) {
        if (status == TaskStatus.SUCCEEDED) {
            return 100;
        }
        return previous == null ? 0 : previous.progressPercent();
    }

    private static boolean regresses(
            TaskStatus previous, TaskStatus current) {
        if (previous == TaskStatus.UNKNOWN || current == TaskStatus.UNKNOWN
                || terminal(current)) {
            return false;
        }
        return phase(current) < phase(previous);
    }

    private static boolean terminal(TaskStatus status) {
        return status == TaskStatus.SUCCEEDED
                || status == TaskStatus.FAILED
                || status == TaskStatus.CANCELLED
                || status == TaskStatus.STOPPED;
    }

    private static int phase(TaskStatus status) {
        return switch (status) {
            case IDLE -> 0;
            case STAGED -> 1;
            case RUNNING -> 2;
            default -> 3;
        };
    }
}
