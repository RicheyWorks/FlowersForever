package com.flowerfarm.automation.optimus;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Deterministic in-process stand-in for development before a customer SDK exists. */
@Component
@ConditionalOnProperty(name = "flowerfarm.harness.optimus.mode", havingValue = "simulator")
public final class SimulatedOptimusRobotAdapter implements OptimusRobotAdapter {

    private static final RobotIdentity IDENTITY = new RobotIdentity(
            AdapterMode.SIMULATOR, "optimus-simulator", "Optimus simulator",
            "0.2", "simulator-safe-home");

    private TaskPlan stagedPlan;
    private String motionState = "IDLE";
    private String currentTask = "";
    private String taskCommandId = "";
    private TaskStatus taskStatus = TaskStatus.IDLE;
    private double taskProgressPercent;
    private String taskStatusMessage = "Simulator is idle";
    private boolean emergencyStopped;
    private String taskPlanFingerprint = "";
    private String lastExecutedToken = "";
    private CommandReceipt lastExecution;
    private CommandReceipt lastCancellation;
    private CommandReceipt stopReceipt;

    @Override
    public RobotIdentity identity() {
        return IDENTITY;
    }

    @Override
    public synchronized RobotState observe() {
        return new RobotState("SIMULATED", motionState, 100.0,
                emergencyStopped, !emergencyStopped && taskStatus != TaskStatus.RUNNING,
                currentTask,
                Map.of("frame", IDENTITY.coordinateFrame(), "location", "safe-home"),
                taskCommandId, taskStatus, taskProgressPercent,
                taskStatusMessage,
                stagedPlan == null ? taskPlanFingerprint
                        : OptimusRobotAdapter.planFingerprint(stagedPlan.planToken()),
                Instant.now());
    }

    @Override
    public synchronized TaskPlan stage(TaskDraft draft) {
        ensureNotStopped();
        ensureNoActiveTask();
        if (stagedPlan != null && stagedPlan.expiresAt().isAfter(Instant.now())) {
            throw new IllegalStateException(
                    "An Optimus task is already staged; cancel it before replacing it");
        }
        stagedPlan = new TaskPlan("sim-plan-" + UUID.randomUUID(), draft, Instant.now());
        taskPlanFingerprint = OptimusRobotAdapter.planFingerprint(
                stagedPlan.planToken());
        motionState = "STAGED";
        currentTask = draft.task();
        taskCommandId = "";
        taskStatus = TaskStatus.STAGED;
        taskProgressPercent = 0;
        taskStatusMessage = "Task is staged for review";
        return stagedPlan;
    }

    @Override
    public synchronized void cancelStagedTask() {
        if (stagedPlan != null) {
            stagedPlan = null;
            taskPlanFingerprint = "";
            currentTask = "";
            taskCommandId = "";
            taskStatus = TaskStatus.IDLE;
            taskProgressPercent = 0;
            taskStatusMessage = "Staged task was cancelled";
            if (!emergencyStopped) {
                motionState = "IDLE";
            }
        }
    }

    @Override
    public synchronized CommandReceipt executeStagedTask(String planToken) {
        ensureNotStopped();
        if (planToken != null && planToken.equals(lastExecutedToken)
                && lastExecution != null) {
            return lastExecution;
        }
        if (stagedPlan == null) {
            throw new IllegalStateException("No Optimus task is staged");
        }
        if (!stagedPlan.expiresAt().isAfter(Instant.now())) {
            stagedPlan = null;
            taskPlanFingerprint = "";
            currentTask = "";
            motionState = "IDLE";
            taskCommandId = "";
            taskStatus = TaskStatus.IDLE;
            taskProgressPercent = 0;
            taskStatusMessage = "Staged task expired";
            throw new IllegalStateException(
                    "The staged Optimus task expired; stage it again for review");
        }
        if (!stagedPlan.planToken().equals(planToken)) {
            throw new IllegalArgumentException(
                    "Plan token does not match the currently staged Optimus task");
        }
        currentTask = stagedPlan.draft().task();
        motionState = "RUNNING";
        lastExecutedToken = stagedPlan.planToken();
        taskPlanFingerprint = OptimusRobotAdapter.planFingerprint(lastExecutedToken);
        lastExecution = receipt(TaskStatus.RUNNING,
                "Simulator accepted the staged high-level task");
        taskCommandId = lastExecution.commandId();
        taskStatus = TaskStatus.RUNNING;
        taskProgressPercent = 0;
        taskStatusMessage = "Task accepted; awaiting simulator progress";
        stagedPlan = null;
        return lastExecution;
    }

    @Override
    public synchronized CommandReceipt cancelActiveTask(String commandId) {
        String requestedId = requireCommandId(commandId);
        if (lastCancellation != null && requestedId.equals(taskCommandId)
                && taskStatus == TaskStatus.CANCELLED) {
            return lastCancellation;
        }
        if (taskStatus != TaskStatus.RUNNING) {
            throw new IllegalStateException("No Optimus task is currently running");
        }
        if (!taskCommandId.equals(requestedId)) {
            throw new IllegalArgumentException(
                    "Command id does not match the currently running Optimus task");
        }
        motionState = "IDLE";
        taskStatus = TaskStatus.CANCELLED;
        taskStatusMessage = "Active simulator task was cancelled";
        lastCancellation = receipt(TaskStatus.CANCELLED, taskStatusMessage);
        return lastCancellation;
    }

    /** Advances the simulator without exposing a progress mutation through the harness. */
    public synchronized void advanceActiveTask(
            double progressPercent, String statusMessage) {
        ensureNotStopped();
        if (taskStatus != TaskStatus.RUNNING) {
            throw new IllegalStateException("No Optimus task is currently running");
        }
        if (!Double.isFinite(progressPercent)
                || progressPercent < taskProgressPercent || progressPercent > 100) {
            throw new IllegalArgumentException(
                    "Progress must be monotonic and between the current value and 100");
        }
        taskProgressPercent = progressPercent;
        taskStatusMessage = statusMessage == null || statusMessage.isBlank()
                ? "Simulator task is in progress" : statusMessage.trim();
        if (progressPercent == 100) {
            taskStatus = TaskStatus.SUCCEEDED;
            motionState = "IDLE";
            taskStatusMessage = "Simulator completed the high-level task";
        }
    }

    @Override
    public synchronized CommandReceipt returnToSafePose() {
        ensureNotStopped();
        ensureNoActiveTask();
        stagedPlan = null;
        taskPlanFingerprint = "";
        currentTask = "Return to safe pose";
        motionState = "RUNNING";
        CommandReceipt receipt = receipt(TaskStatus.RUNNING,
                "Simulator accepted the safe-pose task");
        taskCommandId = receipt.commandId();
        taskStatus = TaskStatus.RUNNING;
        taskProgressPercent = 0;
        taskStatusMessage = receipt.message();
        return receipt;
    }

    @Override
    public synchronized CommandReceipt emergencyStop() {
        if (stopReceipt != null) {
            return stopReceipt;
        }
        boolean stoppedReviewedPlan = taskStatus == TaskStatus.STAGED;
        emergencyStopped = true;
        stagedPlan = null;
        if (stoppedReviewedPlan) {
            taskPlanFingerprint = "";
        }
        motionState = "EMERGENCY_STOPPED";
        if (currentTask.isEmpty()) {
            currentTask = "Emergency stop";
        }
        taskStatus = TaskStatus.STOPPED;
        taskStatusMessage = "Simulator emergency stop is latched";
        stopReceipt = receipt(TaskStatus.STOPPED, taskStatusMessage);
        if (taskCommandId.isEmpty()) {
            taskCommandId = stopReceipt.commandId();
        }
        return stopReceipt;
    }

    private void ensureNotStopped() {
        if (emergencyStopped) {
            throw new IllegalStateException(
                    "Optimus emergency stop is latched; reset it at the robot");
        }
    }

    private void ensureNoActiveTask() {
        if (taskStatus == TaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "An Optimus task is already running; cancel it before staging another");
        }
    }

    private static String requireCommandId(String commandId) {
        if (commandId == null || commandId.isBlank() || commandId.length() > 160) {
            throw new IllegalArgumentException(
                    "commandId is required and must contain at most 160 characters");
        }
        return commandId.trim();
    }

    private static CommandReceipt receipt(TaskStatus status, String message) {
        return new CommandReceipt("sim-command-" + UUID.randomUUID(),
                status, message, Instant.now());
    }
}
