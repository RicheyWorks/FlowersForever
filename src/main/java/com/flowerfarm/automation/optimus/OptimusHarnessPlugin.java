package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.HarnessActionEffect;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessArgumentSpec;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessObservationSpec;
import com.flowerfarm.automation.HarnessPlugin;
import com.flowerfarm.automation.HarnessPluginCapability;
import com.flowerfarm.automation.HarnessPluginDescriptor;
import com.flowerfarm.automation.HarnessPluginKind;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.HarnessValueType;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.AdapterMode;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.CommandReceipt;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotState;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskDraft;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskPlan;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskStatus;
import com.flowerfarm.automation.optimus.OptimusOperationJournal.JournalEvent;
import com.flowerfarm.automation.optimus.OptimusOperationJournal.OperationEvent;
import com.flowerfarm.automation.optimus.OptimusControlLeaseService.LeaseAuthorization;
import com.flowerfarm.automation.optimus.OptimusControlLeaseService.LeaseGrant;
import com.flowerfarm.automation.optimus.OptimusControlLeaseService.LeaseStatus;
import com.flowerfarm.automation.optimus.OptimusStagedPlanStore.StoredPlan;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** High-level, fail-closed harness adapter for a future customer Optimus API. */
@Component
@ConditionalOnProperty(name = "flowerfarm.harness.optimus.enabled", havingValue = "true")
public final class OptimusHarnessPlugin implements HarnessPlugin {

    public static final String ID = "tesla-optimus";
    public static final long ROBOT_STATE_MAX_AGE_SECONDS = 5;
    public static final long ROBOT_STATE_MAX_FUTURE_SKEW_SECONDS = 2;

    private static final HarnessPluginDescriptor DESCRIPTOR =
            new HarnessPluginDescriptor(
                    ID, "Tesla Optimus integration boundary",
                    "High-level supervised tasks through a replaceable robot adapter.",
                    "0.22", HarnessPluginKind.ROBOT,
                    Set.of(HarnessPluginCapability.OBSERVATION,
                            HarnessPluginCapability.COMMANDS,
                            HarnessPluginCapability.PHYSICAL_SENSING,
                            HarnessPluginCapability.PHYSICAL_ACTUATION,
                            HarnessPluginCapability.EMERGENCY_STOP),
                    List.of(
                            signal("connection-state", HarnessValueType.STRING,
                                    "Adapter connection state.", null, false),
                            signal("adapter-mode", HarnessValueType.STRING,
                                    "Whether the adapter is a simulator or hardware.", null,
                                    false),
                            signal("simulated", HarnessValueType.BOOLEAN,
                                    "Whether this is explicitly a simulated robot.", null,
                                    false),
                            signal("robot-id", HarnessValueType.STRING,
                                    "Stable adapter-reported robot identity.", null, true),
                            signal("robot-model", HarnessValueType.STRING,
                                    "Adapter-reported robot model.", null, false),
                            signal("robot-software-version", HarnessValueType.STRING,
                                    "Adapter-reported software version.", null, false),
                            signal("coordinate-frame", HarnessValueType.STRING,
                                    "Adapter-owned coordinate frame for reported pose.", null,
                                    false),
                            signal("motion-state", HarnessValueType.STRING,
                                    "Current high-level motion state.", null, false),
                            signal("battery-percent", HarnessValueType.NUMBER,
                                    "Reported battery charge.", "%", false),
                            signal("emergency-stop", HarnessValueType.BOOLEAN,
                                    "Whether the stop is latched.", null, false),
                            signal("ready-for-command", HarnessValueType.BOOLEAN,
                                    "Whether the adapter accepts a new task.", null, false),
                            signal("operational", HarnessValueType.BOOLEAN,
                                    "Whether state is fresh and the robot is ready.", null, false),
                            signal("staged-plan", HarnessValueType.BOOLEAN,
                                    "Whether a reviewed plan token is staged.", null, false),
                            signal("staged-plan-expired", HarnessValueType.BOOLEAN,
                                    "Whether the staged plan has passed its deadline.", null, false),
                            signal("staged-plan-expires-at", HarnessValueType.STRING,
                                    "Expiry of the staged plan, or an empty string.", null, false),
                            signal("staged-plan-authorized", HarnessValueType.BOOLEAN,
                                    "Whether the current lease still owns the staged plan.",
                                    null, false),
                            signal("staged-plan-adapter-confirmed",
                                    HarnessValueType.BOOLEAN,
                                    "Whether current adapter state still reports the plan staged.",
                                    null, false),
                            signal("execution-recovery-pending",
                                    HarnessValueType.BOOLEAN,
                                    "Whether an interrupted execution handoff needs recovery.",
                                    null, false),
                            signal("execution-adapter-confirmed",
                                    HarnessValueType.BOOLEAN,
                                    "Whether adapter state confirms the exact pending plan started.",
                                    null, false),
                            signal("control-lease-active", HarnessValueType.BOOLEAN,
                                    "Whether this robot has an active exclusive controller.",
                                    null, false),
                            signal("control-lease-expires-at", HarnessValueType.STRING,
                                    "Active control lease expiry, or an empty string.",
                                    null, false),
                            signal("current-task", HarnessValueType.STRING,
                                    "Current or most recent high-level task.", null, true),
                            signal("task-command-id", HarnessValueType.STRING,
                                    "Command id of the active or most recent task.", null, true),
                            signal("task-status", HarnessValueType.STRING,
                                    "Typed lifecycle state for the active or recent task.",
                                    null, false),
                            signal("task-progress-percent", HarnessValueType.NUMBER,
                                    "Monotonic adapter-reported task progress.", "%", false),
                            signal("task-status-message", HarnessValueType.STRING,
                                    "Adapter-reported task progress detail.", null, true),
                            signal("pose", HarnessValueType.OBJECT,
                                    "Pose reported in the adapter-declared frame.", null, true)),
                    List.of(
                            new HarnessActionSpec("get-control-lease",
                                    "Read redacted control-lease availability for this robot.",
                                    HarnessRisk.READ, List.of()),
                            new HarnessActionSpec("acquire-control-lease",
                                    "Acquire exclusive robot control for 15 to 300 seconds.",
                                    HarnessRisk.MUTATE, List.of(
                                    argument("holder", HarnessValueType.STRING,
                                            "Operator or agent label, at most 80 characters.",
                                            true),
                                    argument("duration-seconds", HarnessValueType.INTEGER,
                                            "Lease duration from 15 to 300 seconds.", true))),
                            new HarnessActionSpec("renew-control-lease",
                                    "Rotate the token and extend the active control lease.",
                                    HarnessRisk.MUTATE, List.of(
                                    leaseTokenArgument(),
                                    argument("duration-seconds", HarnessValueType.INTEGER,
                                            "Lease duration from 15 to 300 seconds.", true))),
                            new HarnessActionSpec("release-control-lease",
                                    "Release exclusive control and discard its staged plan.",
                                    HarnessRisk.MUTATE, List.of(leaseTokenArgument())),
                            new HarnessActionSpec("stage-task",
                                    "Stage a bounded high-level task for review; the "
                                            + "plan expires within five minutes.",
                                    HarnessRisk.DRAFT, HarnessActionEffect.DRAFT_STATE,
                                    false, false, List.of(
                                    argument("task", HarnessValueType.STRING,
                                            "High-level task, at most 240 characters.", true),
                                    argument("work-area", HarnessValueType.STRING,
                                            "Named supervised work area.", true),
                                    argument("max-duration-seconds", HarnessValueType.INTEGER,
                                            "Maximum requested duration from 1 to 900.", true),
                                    arrayArgument("constraints", HarnessValueType.STRING,
                                            "Optional safety or handling constraints.", false),
                                    leaseTokenArgument())),
                            new HarnessActionSpec("cancel-staged-task",
                                    "Cancel the staged task without moving the robot.",
                                    HarnessRisk.DRAFT, List.of(leaseTokenArgument())),
                            new HarnessActionSpec("execute-staged-task",
                                    "Start only the exact active, unexpired staged task and "
                                            + "return its operation id.",
                                    HarnessRisk.PHYSICAL, List.of(
                                    argument("plan-token", HarnessValueType.STRING,
                                            "Exact token returned by stage-task.", true),
                                    leaseTokenArgument())),
                            new HarnessActionSpec("get-operation",
                                    "Read and reconcile one durable operation by command id.",
                                    HarnessRisk.READ, List.of(
                                    argument("command-id", HarnessValueType.STRING,
                                            "Command id returned by a robot action.", true))),
                            new HarnessActionSpec("list-operations",
                                    "List and reconcile recent durable robot operations.",
                                    HarnessRisk.READ, List.of(
                                    argument("limit", HarnessValueType.INTEGER,
                                            "Optional result limit from 1 to 25.", false))),
                            new HarnessActionSpec("list-operation-events",
                                    "Read append-only operation lifecycle events after a cursor.",
                                    HarnessRisk.READ, List.of(
                                    argument("after-cursor", HarnessValueType.STRING,
                                            "Optional cursor from a prior response; defaults to 0.",
                                            false),
                                    argument("limit", HarnessValueType.INTEGER,
                                            "Optional result limit from 1 to 50.", false))),
                            new HarnessActionSpec("cancel-active-task",
                                    "Gracefully cancel the exact running task by command id.",
                                    HarnessRisk.PHYSICAL, HarnessActionEffect.PHYSICAL_WORLD,
                                    true, true, List.of(
                                    argument("command-id", HarnessValueType.STRING,
                                            "Exact id returned by execute-staged-task.", true),
                                    leaseTokenArgument())),
                            new HarnessActionSpec("return-safe-pose",
                                    "Return the robot through its adapter-owned safe-pose routine.",
                                    HarnessRisk.PHYSICAL, HarnessActionEffect.PHYSICAL_WORLD,
                                    true, true, List.of(leaseTokenArgument())),
                            new HarnessActionSpec("emergency-stop",
                                    "Request the adapter's independently implemented stop path.",
                                    HarnessRisk.PHYSICAL, HarnessActionEffect.PHYSICAL_WORLD,
                                    true, true, List.of())));

    private final OptimusRobotAdapter adapter;
    private final Clock clock;
    private final String expectedRobotId;
    private final RobotIdentity identity;
    private final OptimusOperationJournal operationJournal;
    private final OptimusControlLeaseService controlLeases;
    private final OptimusStagedPlanStore stagedPlans;

    @Autowired
    public OptimusHarnessPlugin(
            OptimusRobotAdapter adapter,
            OptimusOperationJournal operationJournal,
            OptimusControlLeaseService controlLeases,
            OptimusStagedPlanStore stagedPlans,
            @Value("${flowerfarm.harness.optimus.expected-robot-id:}")
            String expectedRobotId) {
        this(adapter, Clock.systemUTC(), expectedRobotId,
                operationJournal, controlLeases, stagedPlans);
    }

    public OptimusHarnessPlugin(OptimusRobotAdapter adapter) {
        this(adapter, Clock.systemUTC(), "");
    }

    OptimusHarnessPlugin(OptimusRobotAdapter adapter, Clock clock) {
        this(adapter, clock, "");
    }

    OptimusHarnessPlugin(
            OptimusRobotAdapter adapter, Clock clock, String expectedRobotId) {
        this(adapter, clock, expectedRobotId,
                new TransientOptimusOperationJournal(),
                new TransientOptimusControlLeaseService(clock),
                new TransientOptimusStagedPlanStore());
    }

    OptimusHarnessPlugin(
            OptimusRobotAdapter adapter, Clock clock, String expectedRobotId,
            OptimusOperationJournal operationJournal) {
        this(adapter, clock, expectedRobotId, operationJournal,
                new TransientOptimusControlLeaseService(clock),
                new TransientOptimusStagedPlanStore());
    }

    OptimusHarnessPlugin(
            OptimusRobotAdapter adapter, Clock clock, String expectedRobotId,
            OptimusOperationJournal operationJournal,
            OptimusControlLeaseService controlLeases) {
        this(adapter, clock, expectedRobotId, operationJournal, controlLeases,
                new TransientOptimusStagedPlanStore());
    }

    OptimusHarnessPlugin(
            OptimusRobotAdapter adapter, Clock clock, String expectedRobotId,
            OptimusOperationJournal operationJournal,
            OptimusControlLeaseService controlLeases,
            OptimusStagedPlanStore stagedPlans) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.expectedRobotId = expectedRobotId == null ? "" : expectedRobotId.trim();
        this.identity = requireExpectedIdentity(adapter.identity());
        this.operationJournal = Objects.requireNonNull(
                operationJournal, "operationJournal");
        this.controlLeases = Objects.requireNonNull(controlLeases, "controlLeases");
        this.stagedPlans = Objects.requireNonNull(stagedPlans, "stagedPlans");
    }

    @Override
    public HarnessPluginDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public synchronized HarnessSnapshot observe() {
        try {
            return availableSnapshot(state());
        } catch (AdapterUnavailableException unavailable) {
            return unavailableSnapshot();
        }
    }

    private HarnessSnapshot availableSnapshot(RobotState state) {
        Instant now = clock.instant();
        if (!state.taskCommandId().isEmpty() && isFresh(state, now)) {
            try {
                operationJournal.recordState(ID, identity, state);
            } catch (RuntimeException unavailableJournalWrite) {
                // Live state remains useful after the acceptance receipt exists.
            }
        }
        StoredPlan stagedPlan = stagedPlans.current(identity).orElse(null);
        boolean executionPending = stagedPlan != null
                && stagedPlan.executionPending();
        boolean planExpired = stagedPlan != null
                && !stagedPlan.expiresAt().isAfter(now);
        LeaseStatus lease = controlLeases.status(identity);
        boolean stagedPlanAuthorized = stagedPlan != null && lease.active()
                && lease.leaseId().equals(stagedPlan.leaseId());
        boolean stagedPlanAdapterConfirmed = adapterConfirmsStaged(
                state, stagedPlan);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("connection-state", state.connectionState());
        metadata.put("adapter-mode", identity.adapterMode().name());
        metadata.put("simulated", identity.adapterMode() == AdapterMode.SIMULATOR);
        metadata.put("robot-id", identity.robotId());
        metadata.put("robot-model", identity.model());
        metadata.put("robot-software-version", identity.softwareVersion());
        metadata.put("coordinate-frame", identity.coordinateFrame());
        metadata.put("motion-state", state.motionState());
        metadata.put("battery-percent", state.batteryPercent());
        metadata.put("emergency-stop", state.emergencyStopped());
        metadata.put("ready-for-command", state.readyForCommand());
        metadata.put("operational", isOperational(state, now));
        metadata.put("staged-plan", stagedPlan != null && !executionPending);
        metadata.put("staged-plan-expired", planExpired);
        metadata.put("staged-plan-expires-at",
                stagedPlan == null ? "" : stagedPlan.expiresAt().toString());
        metadata.put("staged-plan-authorized", stagedPlanAuthorized);
        metadata.put("staged-plan-adapter-confirmed",
                stagedPlanAdapterConfirmed);
        metadata.put("execution-recovery-pending", executionPending);
        metadata.put("execution-adapter-confirmed",
                adapterConfirmsExecution(state, stagedPlan));
        metadata.put("control-lease-active", lease.active());
        metadata.put("control-lease-expires-at", lease.expiresAt());
        metadata.put("current-task", state.currentTask());
        metadata.put("task-command-id", state.taskCommandId());
        metadata.put("task-status", state.taskStatus().name());
        metadata.put("task-progress-percent", state.taskProgressPercent());
        metadata.put("task-status-message", state.taskStatusMessage());
        metadata.put("pose", state.pose());
        return new HarnessSnapshot(ID, state.observedAt(), List.of("Optimus"),
                List.of(), metadata);
    }

    private HarnessSnapshot unavailableSnapshot() {
        Instant now = clock.instant();
        StoredPlan stagedPlan = stagedPlans.current(identity).orElse(null);
        boolean executionPending = stagedPlan != null
                && stagedPlan.executionPending();
        boolean planExpired = stagedPlan != null
                && !stagedPlan.expiresAt().isAfter(now);
        LeaseStatus lease = controlLeases.status(identity);
        boolean stagedPlanAuthorized = stagedPlan != null && lease.active()
                && lease.leaseId().equals(stagedPlan.leaseId());
        OperationEvent event = operationJournal.recent(ID, 1).stream()
                .findFirst().orElse(null);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("connection-state", "UNAVAILABLE");
        metadata.put("adapter-mode", identity.adapterMode().name());
        metadata.put("simulated", identity.adapterMode() == AdapterMode.SIMULATOR);
        metadata.put("robot-id", identity.robotId());
        metadata.put("robot-model", identity.model());
        metadata.put("robot-software-version", identity.softwareVersion());
        metadata.put("coordinate-frame", identity.coordinateFrame());
        metadata.put("motion-state", "UNKNOWN");
        metadata.put("ready-for-command", false);
        metadata.put("operational", false);
        metadata.put("staged-plan", stagedPlan != null && !executionPending);
        metadata.put("staged-plan-expired", planExpired);
        metadata.put("staged-plan-expires-at",
                stagedPlan == null ? "" : stagedPlan.expiresAt().toString());
        metadata.put("staged-plan-authorized", stagedPlanAuthorized);
        metadata.put("staged-plan-adapter-confirmed", false);
        metadata.put("execution-recovery-pending", executionPending);
        metadata.put("execution-adapter-confirmed", false);
        metadata.put("control-lease-active", lease.active());
        metadata.put("control-lease-expires-at", lease.expiresAt());
        TaskStatus displayedStatus = event == null || !event.terminal()
                ? TaskStatus.UNKNOWN : event.status();
        metadata.put("task-status", displayedStatus.name());
        metadata.put("task-progress-percent",
                event == null ? 0 : event.progressPercent());
        if (event != null) {
            metadata.put("task-command-id", event.operationId());
            metadata.put("task-status-message", event.terminal()
                    ? event.statusMessage()
                    : "Adapter unavailable; durable operation state is unconfirmed");
        }
        return new HarnessSnapshot(ID, now, List.of("Optimus"),
                List.of(), metadata);
    }

    @Override
    public synchronized Optional<HarnessExecution> replay(HarnessCommand command) {
        OperationEvent receipt = operationJournal.latestByRequest(
                ID, command.requestId()).orElse(null);
        if (receipt == null) {
            return Optional.empty();
        }
        if (!"execute-staged-task".equals(command.action())
                || !receipt.matchesRequestFingerprint(
                executionRequestFingerprint(command))) {
            throw new IllegalArgumentException("requestId '" + command.requestId()
                    + "' was already used for a different command");
        }
        requireCurrentRobot(receipt);
        OperationEvent event = operationJournal.latest(
                ID, receipt.operationId()).orElse(receipt);
        requireCurrentRobot(event);
        Instant now = clock.instant();
        RobotState current = null;
        try {
            current = state();
        } catch (AdapterUnavailableException unavailable) {
            // The durable response remains useful while live reconciliation is offline.
        }
        boolean exactLive = current != null
                && receipt.operationId().equals(current.taskCommandId())
                && isFresh(current, now)
                && OptimusRobotAdapter.planFingerprint(
                        command.text("plan-token"))
                        .equals(current.stagedPlanFingerprint());
        boolean reconciliationPersisted = true;
        if (exactLive) {
            try {
                event = operationJournal.recordState(ID, identity, current);
            } catch (RuntimeException unavailableJournalWrite) {
                event = liveStateEvent(event, current);
                reconciliationPersisted = false;
            }
        } else if (!event.terminal()) {
            String reason = current == null
                    ? "Adapter unavailable during durable response replay"
                    : "Adapter does not confirm the durably replayed execution";
            try {
                event = operationJournal.recordUnknown(
                        ID, identity, event, now, reason);
            } catch (RuntimeException unavailableJournalWrite) {
                event = unconfirmedReplayEvent(event, now, reason);
                reconciliationPersisted = false;
            }
        }
        String reconciliation = exactLive ? "LIVE"
                : event.terminal() ? "TERMINAL_HISTORY" : "UNCONFIRMED";
        return Optional.of(HarnessExecution.success(
                "Recovered durable Optimus execution response", Map.of(
                        "commandId", event.operationId(),
                        "status", event.status().name(),
                        "acceptedAt", event.acceptedAt().toString(),
                        "recovered", true,
                        "adapterConfirmed", exactLive,
                        "reconciliationPersisted", reconciliationPersisted,
                        "reconciliation", reconciliation)));
    }

    private OperationEvent unconfirmedReplayEvent(
            OperationEvent event, Instant observedAt, String reason) {
        return new OperationEvent(
                ID, event.operationId(), event.action(), TaskStatus.UNKNOWN,
                event.progressPercent(), reason, identity.robotId(),
                identity.adapterMode() == AdapterMode.SIMULATOR,
                event.acceptedAt(), observedAt);
    }

    private OperationEvent liveStateEvent(
            OperationEvent previous, RobotState current) {
        return OptimusOperationLifecycle.reconcileState(ID, identity, current, previous);
    }

    @Override
    public synchronized HarnessExecution execute(HarnessCommand command) {
        return switch (command.action()) {
            case "get-control-lease" -> getControlLease();
            case "acquire-control-lease" -> acquireControlLease(command);
            case "renew-control-lease" -> renewControlLease(command);
            case "release-control-lease" -> releaseControlLease(command);
            case "stage-task" -> stage(command);
            case "cancel-staged-task" -> {
                requireLease(command);
                discardStagedPlan(state());
                yield HarnessExecution.success("Cancelled staged Optimus task");
            }
            case "execute-staged-task" -> executeStagedTask(command);
            case "get-operation" -> getOperation(command);
            case "list-operations" -> listOperations(command);
            case "list-operation-events" -> listOperationEvents(command);
            case "cancel-active-task" -> cancelActiveTask(command);
            case "return-safe-pose" -> {
                requireLease(command);
                RobotState current = requireOperationalState("return to a safe pose");
                requireDispatchAuthorization(command, current);
                if (stagedPlans.current(identity).isPresent()
                        || adapterReportsStaged(current)) {
                    discardStagedPlan(current);
                }
                requireDispatchAuthorization(command, current);
                yield receipt("return-safe-pose", adapter.returnToSafePose(),
                        TaskStatus.RUNNING);
            }
            case "emergency-stop" -> {
                requireDispatchDeadline(command);
                HarnessExecution stopped = receipt(
                        "emergency-stop", adapter.emergencyStop(), TaskStatus.STOPPED);
                clearStagedPlan();
                yield stopped;
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported Optimus action '" + command.action() + "'");
        };
    }

    private HarnessExecution getControlLease() {
        LeaseStatus status = controlLeases.status(identity);
        return HarnessExecution.success("Read Optimus control lease",
                leaseStatusOutput(status));
    }

    private HarnessExecution acquireControlLease(HarnessCommand command) {
        LeaseGrant grant = controlLeases.acquire(
                identity, command.text("holder"),
                command.integer("duration-seconds"));
        return HarnessExecution.success("Acquired exclusive Optimus control lease",
                leaseGrantOutput(grant));
    }

    private HarnessExecution renewControlLease(HarnessCommand command) {
        requireLease(command);
        LeaseGrant grant = controlLeases.renew(
                identity, command.text("lease-token"),
                command.integer("duration-seconds"));
        return HarnessExecution.success("Renewed and rotated Optimus control lease",
                leaseGrantOutput(grant));
    }

    private HarnessExecution releaseControlLease(HarnessCommand command) {
        requireLease(command);
        if (stagedPlans.current(identity).isPresent()) {
            discardStagedPlan(state());
        }
        controlLeases.release(identity, command.text("lease-token"));
        return HarnessExecution.success("Released Optimus control lease",
                Map.of("released", true));
    }

    private HarnessExecution stage(HarnessCommand command) {
        LeaseAuthorization authorization = requireLease(command);
        RobotState current = requireOperationalState("stage a task");
        StoredPlan existing = stagedPlans.current(identity).orElse(null);
        if (existing != null) {
            if (existing.executionPending()) {
                throw new IllegalStateException(
                        "An Optimus execution is pending recovery; retry its original request");
            }
            boolean adapterHasStagedPlan = adapterConfirmsStaged(current, existing);
            if (!existing.expiresAt().isAfter(clock.instant())
                    || !authorization.leaseId().equals(existing.leaseId())
                    || !adapterHasStagedPlan) {
                discardStagedPlan(current);
            } else {
                throw new IllegalStateException(
                        "An Optimus task is already staged; cancel it before replacing it");
            }
        } else if (adapterReportsStaged(current)) {
            discardStagedPlan(current);
        }
        List<String> constraints = command.arguments().containsKey("constraints")
                ? command.texts("constraints") : List.of();
        TaskDraft draft = new TaskDraft(
                command.text("task"), command.text("work-area"),
                command.integer("max-duration-seconds"), constraints);
        TaskPlan plan;
        try {
            plan = adapter.stage(draft);
        } catch (RuntimeException exception) {
            throw cancelAdapterPlanAfter(exception);
        }
        if (plan == null) {
            throw cancelAdapterPlanAfter(new IllegalStateException(
                    "Optimus adapter returned no staged plan"));
        }
        if (!draft.equals(plan.draft())) {
            throw cancelAdapterPlanAfter(new IllegalStateException(
                    "Optimus adapter changed the reviewed task while staging it"));
        }
        Instant now = clock.instant();
        if (plan.stagedAt().isAfter(
                now.plusSeconds(ROBOT_STATE_MAX_FUTURE_SKEW_SECONDS))) {
            throw cancelAdapterPlanAfter(new IllegalStateException(
                    "Optimus adapter returned a future-dated staged plan"));
        }
        if (!plan.expiresAt().isAfter(now)) {
            throw cancelAdapterPlanAfter(new IllegalStateException(
                    "Optimus adapter returned an already expired staged plan"));
        }
        RobotState acknowledgement;
        try {
            acknowledgement = state();
        } catch (RuntimeException exception) {
            throw cancelAdapterPlanAfter(exception);
        }
        if (!isFresh(acknowledgement, clock.instant())) {
            throw cancelAdapterPlanAfter(new IllegalStateException(
                    "Optimus adapter returned stale or future-dated staged state"));
        }
        if (!adapterConfirmsStaged(acknowledgement, plan)) {
            throw cancelAdapterPlanAfter(new IllegalStateException(
                    "Optimus adapter did not confirm the exact staged task"));
        }
        LeaseAuthorization confirmed;
        try {
            confirmed = requireLease(command);
        } catch (RuntimeException exception) {
            throw cancelAdapterPlanAfter(exception);
        }
        if (!confirmed.leaseId().equals(authorization.leaseId())) {
            throw cancelAdapterPlanAfter(new IllegalStateException(
                    "Control lease changed while the task was being staged"));
        }
        StoredPlan written;
        StoredPlan committed;
        try {
            written = stagedPlans.replace(identity, confirmed.leaseId(), plan);
            committed = stagedPlans.current(identity).orElse(null);
        } catch (RuntimeException exception) {
            throw rollbackStoredPlanAfter(exception);
        }
        if (!storedPlanMatches(written, confirmed, plan)
                || !storedPlanMatches(committed, confirmed, plan)) {
            throw rollbackStoredPlanAfter(new IllegalStateException(
                    "Optimus staged-plan store did not retain the exact staged task"));
        }
        return HarnessExecution.success("Staged Optimus task for physical review", Map.of(
                "planToken", plan.planToken(),
                "stagedAt", plan.stagedAt().toString(),
                "planExpiresAt", plan.expiresAt().toString(),
                "maxDurationSeconds", plan.draft().maxDurationSeconds()));
    }

    private HarnessExecution executeStagedTask(HarnessCommand command) {
        LeaseAuthorization authorization = requireLease(command);
        StoredPlan stagedPlan = stagedPlans.current(identity).orElseThrow(() ->
                new IllegalStateException("No reviewed Optimus task is staged"));
        String planToken = command.text("plan-token");
        if (!stagedPlan.matchesToken(planToken)) {
            throw new IllegalArgumentException(
                    "Plan token does not match the currently staged Optimus task");
        }
        requireStagedPlanOwner(stagedPlan, authorization);
        if (stagedPlan.executionPending()
                && !stagedPlan.claimedBy(command.requestId())) {
            throw new IllegalStateException(
                    "Another Optimus execution request is already pending");
        }
        if (!stagedPlan.executionPending()
                && !stagedPlan.expiresAt().isAfter(clock.instant())) {
            discardStagedPlan(state());
            throw new IllegalStateException(
                    "The staged Optimus task expired; stage it again for review");
        }
        RobotState current = requireFreshState("execute a staged task");
        if (stagedPlan.executionPending()) {
            HarnessExecution recovered = recoverPendingExecution(
                    command, stagedPlan, current);
            if (recovered != null) {
                return recovered;
            }
        }
        if (!stagedPlan.expiresAt().isAfter(clock.instant())) {
            discardStagedPlan(current);
            throw new IllegalStateException(
                    "The staged Optimus task expired; stage it again for review");
        }
        requireOperational(current, "execute a staged task");
        if (!adapterConfirmsStaged(current, stagedPlan)) {
            if (!stagedPlan.executionPending()) {
                clearStagedPlan();
            }
            throw new IllegalStateException(
                    "Optimus adapter no longer reports this exact task as staged; "
                            + "stage it again");
        }

        requireStagedPlanOwner(stagedPlan, requireDispatchAuthorization(command, current));
        Instant executionRequestedAt = stagedPlan.executionPending()
                ? stagedPlan.executionRequestedAt() : clock.instant();
        StoredPlan claimed = stagedPlans.claimExecution(
                identity, authorization.leaseId(), planToken,
                command.requestId(), executionRequestedAt);
        StoredPlan committed = stagedPlans.current(identity).orElse(null);
        if (!executionClaimMatches(
                claimed, stagedPlan, command.requestId(), executionRequestedAt)
                || !executionClaimMatches(
                        committed, stagedPlan, command.requestId(),
                        executionRequestedAt)) {
            throw new IllegalStateException(
                    "Optimus execution claim was not durably retained");
        }

        requireStagedPlanOwner(stagedPlan, requireDispatchAuthorization(command, current));
        if (!stagedPlan.expiresAt().isAfter(clock.instant())) {
            throw new IllegalStateException(
                    "The staged Optimus task expired before dispatch; stage it again for review");
        }
        HarnessExecution execution = executionReceipt(
                command, adapter.executeStagedTask(planToken), TaskStatus.RUNNING);
        clearStagedPlan();
        return execution;
    }

    private HarnessExecution recoverPendingExecution(
            HarnessCommand command, StoredPlan stagedPlan, RobotState current) {
        if (!stagedPlan.claimedBy(command.requestId())) {
            throw new IllegalStateException(
                    "Another Optimus execution request is already pending");
        }
        if (!adapterConfirmsExecution(current, stagedPlan)) {
            if (adapterConfirmsStaged(current, stagedPlan)) {
                return null;
            }
            throw new IllegalStateException(
                    "Pending Optimus execution cannot yet be matched to adapter state");
        }
        CommandReceipt recovered = new CommandReceipt(
                current.taskCommandId(), current.taskStatus(),
                "Recovered exact Optimus execution after an interrupted response",
                current.observedAt());
        executionReceipt(command, recovered, current.taskStatus());
        operationJournal.recordState(ID, identity, current);
        clearStagedPlan();
        return HarnessExecution.success(recovered.message(), Map.of(
                "commandId", recovered.commandId(),
                "status", recovered.status().name(),
                "acceptedAt", recovered.acceptedAt().toString(),
                "recovered", true));
    }

    private HarnessExecution getOperation(HarnessCommand command) {
        String operationId = command.text("command-id");
        RobotState current = availableState();
        boolean adapterAvailable = current != null;
        Instant now = clock.instant();
        boolean currentMatch = adapterAvailable
                && operationId.equals(current.taskCommandId());
        boolean live = currentMatch && isFresh(current, now);
        boolean reconciliationPersisted = true;

        OperationEvent event;
        if (live) {
            try {
                event = operationJournal.recordState(ID, identity, current);
            } catch (RuntimeException unavailableJournalWrite) {
                OperationEvent previous = operationJournal.latest(
                        ID, operationId).orElse(null);
                if (previous != null) {
                    requireCurrentRobot(previous);
                }
                event = liveStateEvent(previous, current);
                reconciliationPersisted = false;
            }
        } else {
            event = operationJournal.latest(ID, operationId).orElseThrow(() ->
                    new IllegalArgumentException(
                            "No recorded Optimus operation '" + operationId + "'"));
            requireCurrentRobot(event);
            if (!event.terminal()) {
                String reason = !adapterAvailable
                        ? "Adapter unavailable during operation query"
                        : currentMatch
                        ? "Adapter state is stale; operation requires reconciliation"
                        : "Adapter does not currently report this operation";
                try {
                    event = operationJournal.recordUnknown(
                            ID, identity, event, now, reason);
                } catch (RuntimeException unavailableJournalWrite) {
                    event = unconfirmedReplayEvent(event, now, reason);
                    reconciliationPersisted = false;
                }
            }
        }

        String reconciliation = live ? "LIVE"
                : event.terminal() ? "TERMINAL_HISTORY" : "UNCONFIRMED";
        return HarnessExecution.success("Read durable Optimus operation",
                operationOutput(event, reconciliation,
                        reconciliationPersisted, adapterAvailable));
    }

    private HarnessExecution listOperations(HarnessCommand command) {
        int limit = command.arguments().containsKey("limit")
                ? command.integer("limit") : 10;
        if (limit < 1 || limit > 25) {
            throw new IllegalArgumentException(
                    "list-operations limit must be between 1 and 25");
        }
        RobotState current = availableState();
        boolean adapterAvailable = current != null;
        Instant now = clock.instant();
        boolean currentFresh = adapterAvailable && isFresh(current, now);
        boolean liveStatePersisted = true;
        if (currentFresh && !current.taskCommandId().isEmpty()) {
            try {
                operationJournal.recordState(ID, identity, current);
            } catch (RuntimeException unavailableJournalWrite) {
                liveStatePersisted = false;
            }
        }
        boolean currentPersistence = liveStatePersisted;

        List<Map<String, Object>> operations = operationJournal.recent(ID, limit)
                .stream()
                .map(event -> reconcileListedOperation(
                        event, current, currentFresh, now,
                        currentPersistence, adapterAvailable))
                .toList();
        return HarnessExecution.success("Listed durable Optimus operations", Map.of(
                "operations", operations,
                "count", operations.size(),
                "limit", limit));
    }

    private Map<String, Object> reconcileListedOperation(
            OperationEvent recorded, RobotState current,
            boolean currentFresh, Instant now,
            boolean liveStatePersisted, boolean adapterAvailable) {
        requireCurrentRobot(recorded);
        boolean live = currentFresh
                && recorded.operationId().equals(current.taskCommandId());
        OperationEvent event = recorded;
        boolean reconciliationPersisted = true;
        if (live) {
            if (!liveStatePersisted) {
                event = liveStateEvent(recorded, current);
                reconciliationPersisted = false;
            }
        } else if (!event.terminal()) {
            String reason = adapterAvailable
                    ? "Adapter does not currently report this operation"
                    : "Adapter unavailable during operation query";
            try {
                event = operationJournal.recordUnknown(
                        ID, identity, event, now, reason);
            } catch (RuntimeException unavailableJournalWrite) {
                event = unconfirmedReplayEvent(event, now, reason);
                reconciliationPersisted = false;
            }
        }
        String reconciliation = live ? "LIVE"
                : event.terminal() ? "TERMINAL_HISTORY" : "UNCONFIRMED";
        return operationOutput(event, reconciliation,
                reconciliationPersisted, adapterAvailable);
    }

    private HarnessExecution listOperationEvents(HarnessCommand command) {
        int limit = command.arguments().containsKey("limit")
                ? command.integer("limit") : 25;
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException(
                    "list-operation-events limit must be between 1 and 50");
        }
        long afterCursor = eventCursor(command);
        RobotState current = availableState();
        boolean adapterAvailable = current != null;
        Instant now = clock.instant();
        boolean reconciliationPersisted = true;
        if (adapterAvailable && isFresh(current, now)
                && !current.taskCommandId().isEmpty()) {
            try {
                operationJournal.recordState(ID, identity, current);
            } catch (RuntimeException unavailableJournalWrite) {
                reconciliationPersisted = false;
            }
        }

        List<JournalEvent> page = operationJournal.after(
                ID, afterCursor, limit + 1);
        boolean hasMore = page.size() > limit;
        List<JournalEvent> returned = hasMore ? page.subList(0, limit) : page;
        long nextCursor = returned.isEmpty()
                ? afterCursor : returned.get(returned.size() - 1).eventId();
        List<Map<String, Object>> events = returned.stream()
                .map(OptimusHarnessPlugin::journalEventOutput)
                .toList();
        return HarnessExecution.success("Listed Optimus operation events", Map.of(
                "events", events,
                "count", events.size(),
                "limit", limit,
                "nextCursor", Long.toString(nextCursor),
                "hasMore", hasMore,
                "reconciliationPersisted", reconciliationPersisted,
                "adapterAvailable", adapterAvailable));
    }

    private static long eventCursor(HarnessCommand command) {
        if (!command.arguments().containsKey("after-cursor")) {
            return 0;
        }
        String cursor = command.text("after-cursor");
        if (!cursor.matches("0|[1-9][0-9]{0,18}")) {
            throw invalidEventCursor();
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException exception) {
            throw invalidEventCursor();
        }
    }

    private static IllegalArgumentException invalidEventCursor() {
        return new IllegalArgumentException(
                "after-cursor must be a non-negative decimal cursor");
    }

    private static Map<String, Object> journalEventOutput(JournalEvent entry) {
        OperationEvent event = entry.event();
        return Map.of(
                "cursor", Long.toString(entry.eventId()),
                "operationId", event.operationId(),
                "action", event.action(),
                "status", event.status().name(),
                "progressPercent", event.progressPercent(),
                "terminal", event.terminal(),
                "simulated", event.simulated(),
                "acceptedAt", event.acceptedAt().toString(),
                "observedAt", event.observedAt().toString());
    }

    private static Map<String, Object> operationOutput(
            OperationEvent event, String reconciliation,
            boolean reconciliationPersisted, boolean adapterAvailable) {
        return Map.ofEntries(
                Map.entry("operationId", event.operationId()),
                Map.entry("action", event.action()),
                Map.entry("status", event.status().name()),
                Map.entry("progressPercent", event.progressPercent()),
                Map.entry("terminal", event.terminal()),
                Map.entry("simulated", event.simulated()),
                Map.entry("acceptedAt", event.acceptedAt().toString()),
                Map.entry("lastObservedAt", event.observedAt().toString()),
                Map.entry("reconciliation", reconciliation),
                Map.entry("reconciliationPersisted",
                        reconciliationPersisted),
                Map.entry("adapterAvailable", adapterAvailable));
    }

    private HarnessExecution cancelActiveTask(HarnessCommand command) {
        requireLease(command);
        RobotState state = requireFreshState("cancel an active task");
        String commandId = command.text("command-id");
        if (!state.taskCommandId().equals(commandId)) {
            throw new IllegalArgumentException(
                    "Command id does not match the active or most recent Optimus task");
        }
        if (state.taskStatus() != TaskStatus.RUNNING
                && state.taskStatus() != TaskStatus.CANCELLED) {
            throw new IllegalStateException(
                    "The identified Optimus task is not running");
        }
        requireDispatchAuthorization(command, state);
        return receipt("cancel-active-task", adapter.cancelActiveTask(commandId),
                TaskStatus.CANCELLED);
    }

    private RobotState requireOperationalState(String action) {
        RobotState state = requireFreshState(action);
        requireOperational(state, action);
        return state;
    }

    private static void requireOperational(RobotState state, String action) {
        if (state.emergencyStopped()) {
            throw new IllegalStateException("Cannot " + action
                    + " because the Optimus emergency stop is latched; reset it at the robot");
        }
        if (!state.readyForCommand()) {
            throw new IllegalStateException("Cannot " + action
                    + " because Optimus is not ready for a command");
        }
    }

    private RobotState requireFreshState(String action) {
        RobotState state = state();
        if (!isFresh(state, clock.instant())) {
            throw new IllegalStateException("Cannot " + action
                    + " because Optimus state is stale or future-dated");
        }
        return state;
    }

    private RobotState state() {
        RobotIdentity reportedIdentity;
        try {
            reportedIdentity = adapter.identity();
        } catch (RuntimeException failure) {
            throw new AdapterUnavailableException(
                    "Optimus adapter identity is unavailable", failure);
        }
        if (reportedIdentity == null) {
            throw new AdapterUnavailableException(
                    "Optimus adapter returned no robot identity");
        }
        RobotIdentity currentIdentity = requireExpectedIdentity(reportedIdentity);
        if (!identity.equals(currentIdentity)) {
            throw new IllegalStateException(
                    "Optimus adapter identity changed after plugin startup");
        }
        RobotState state;
        try {
            state = adapter.observe();
        } catch (RuntimeException failure) {
            throw new AdapterUnavailableException(
                    "Optimus adapter state is unavailable", failure);
        }
        if (state == null) {
            throw new AdapterUnavailableException(
                    "Optimus adapter returned no robot state");
        }
        return state;
    }

    private RobotState availableState() {
        try {
            return state();
        } catch (AdapterUnavailableException unavailable) {
            return null;
        }
    }

    private static final class AdapterUnavailableException
            extends IllegalStateException {

        private AdapterUnavailableException(String message) {
            super(message);
        }

        private AdapterUnavailableException(
                String message, RuntimeException cause) {
            super(message, cause);
        }
    }

    private RobotIdentity requireExpectedIdentity(RobotIdentity reportedIdentity) {
        if (reportedIdentity == null) {
            throw new IllegalStateException("Optimus adapter returned no robot identity");
        }
        if (reportedIdentity.adapterMode() == AdapterMode.HARDWARE
                && expectedRobotId.isEmpty()) {
            throw new IllegalStateException("A hardware Optimus adapter requires "
                    + "flowerfarm.harness.optimus.expected-robot-id");
        }
        if (!expectedRobotId.isEmpty()
                && !expectedRobotId.equals(reportedIdentity.robotId())) {
            throw new IllegalStateException("Optimus adapter robot id does not match "
                    + "flowerfarm.harness.optimus.expected-robot-id");
        }
        return reportedIdentity;
    }

    private void requireCurrentRobot(OperationEvent event) {
        boolean simulated = identity.adapterMode() == AdapterMode.SIMULATOR;
        if (!identity.robotId().equals(event.robotId())
                || simulated != event.simulated()) {
            throw new IllegalStateException(
                    "Stored operation belongs to a different robot identity");
        }
    }

    private static boolean isOperational(RobotState state, Instant now) {
        return isFresh(state, now) && !state.emergencyStopped()
                && state.readyForCommand();
    }

    private static boolean adapterConfirmsStaged(
            RobotState state, StoredPlan stagedPlan) {
        return stagedPlan != null && adapterReportsStaged(state)
                && stagedPlan.matchesFingerprint(state.stagedPlanFingerprint());
    }

    private static boolean adapterConfirmsStaged(
            RobotState state, TaskPlan stagedPlan) {
        return adapterReportsStaged(state)
                && OptimusRobotAdapter.planFingerprint(stagedPlan.planToken())
                        .equals(state.stagedPlanFingerprint());
    }

    private static boolean adapterConfirmsExecution(
            RobotState state, StoredPlan stagedPlan) {
        return stagedPlan != null && stagedPlan.executionPending()
                && !state.taskCommandId().isEmpty()
                && state.taskStatus() != TaskStatus.IDLE
                && state.taskStatus() != TaskStatus.STAGED
                && state.taskStatus() != TaskStatus.UNKNOWN
                && stagedPlan.matchesFingerprint(state.stagedPlanFingerprint());
    }

    private static boolean adapterReportsStaged(RobotState state) {
        return state.taskStatus() == TaskStatus.STAGED;
    }

    private static boolean isFresh(RobotState state, Instant now) {
        return !state.observedAt().isBefore(
                now.minusSeconds(ROBOT_STATE_MAX_AGE_SECONDS))
                && !state.observedAt().isAfter(
                now.plusSeconds(ROBOT_STATE_MAX_FUTURE_SKEW_SECONDS));
    }

    private LeaseAuthorization requireLease(HarnessCommand command) {
        return controlLeases.requireValid(identity, command.text("lease-token"));
    }

    private LeaseAuthorization requireDispatchAuthorization(
            HarnessCommand command, RobotState state) {
        LeaseAuthorization authorization = requireLease(command);
        if (!authorization.expiresAt().isAfter(clock.instant())) {
            throw new IllegalStateException(
                    "A valid active control lease is required at command dispatch");
        }
        requireDispatchDeadline(command);
        if (!isFresh(state, clock.instant())) {
            throw new IllegalStateException(
                    "Optimus state became stale before command dispatch");
        }
        return authorization;
    }

    private void requireDispatchDeadline(HarnessCommand command) {
        // The gateway requires physical deadlines; direct embedded callers may omit one.
        if (command.expiresAt() != null && !command.expiresAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException(
                    "Action '" + command.action() + "' command has expired before dispatch");
        }
    }

    private static void requireStagedPlanOwner(
            StoredPlan stagedPlan, LeaseAuthorization authorization) {
        if (!authorization.leaseId().equals(stagedPlan.leaseId())) {
            throw new IllegalStateException(
                    "The staged Optimus plan belongs to a different control lease");
        }
    }

    private static Map<String, Object> leaseGrantOutput(LeaseGrant grant) {
        return Map.of(
                "leaseId", grant.leaseId(),
                "leaseToken", grant.leaseToken(),
                "issuedAt", grant.issuedAt().toString(),
                "expiresAt", grant.expiresAt().toString());
    }

    private static Map<String, Object> leaseStatusOutput(LeaseStatus status) {
        return Map.of(
                "active", status.active(),
                "expiresAt", status.expiresAt());
    }

    private void clearStagedPlan() {
        stagedPlans.clear(identity);
    }

    private void discardStagedPlan(RobotState state) {
        StoredPlan stored = stagedPlans.current(identity).orElse(null);
        if (stored != null && stored.executionPending()
                && !adapterConfirmsStaged(state, stored)) {
            throw new IllegalStateException(
                    "Pending Optimus execution must be recovered before its record is discarded");
        }
        if (adapterReportsStaged(state)) {
            adapter.cancelStagedTask();
        }
        clearStagedPlan();
    }

    private void discardAdapterPlan() {
        adapter.cancelStagedTask();
    }

    private RuntimeException cancelAdapterPlanAfter(RuntimeException failure) {
        try {
            discardAdapterPlan();
        } catch (RuntimeException cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
        }
        return failure;
    }

    private RuntimeException rollbackStoredPlanAfter(RuntimeException failure) {
        try {
            discardAdapterPlan();
        } catch (RuntimeException cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
            return failure;
        }
        try {
            clearStagedPlan();
        } catch (RuntimeException cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
        }
        return failure;
    }

    private boolean storedPlanMatches(
            StoredPlan stored, LeaseAuthorization authorization, TaskPlan plan) {
        try {
            return stored != null
                    && !stored.executionPending()
                    && identity.robotId().equals(stored.robotId())
                    && (identity.adapterMode() == AdapterMode.SIMULATOR)
                            == stored.simulated()
                    && authorization.leaseId().equals(stored.leaseId())
                    && stored.matchesToken(plan.planToken())
                    && samePersistedInstant(plan.stagedAt(), stored.stagedAt())
                    && samePersistedInstant(plan.expiresAt(), stored.expiresAt());
        } catch (RuntimeException invalidStoredPlan) {
            return false;
        }
    }

    private boolean executionClaimMatches(
            StoredPlan claimed, StoredPlan stagedPlan, String requestId,
            Instant requestedAt) {
        try {
            return claimed != null && stagedPlan != null
                    && identity.robotId().equals(claimed.robotId())
                    && (identity.adapterMode() == AdapterMode.SIMULATOR)
                            == claimed.simulated()
                    && claimed.leaseId().equals(stagedPlan.leaseId())
                    && claimed.matchesFingerprint(stagedPlan.planTokenHash())
                    && samePersistedInstant(
                            stagedPlan.stagedAt(), claimed.stagedAt())
                    && samePersistedInstant(
                            stagedPlan.expiresAt(), claimed.expiresAt())
                    && claimed.claimedBy(requestId)
                    && samePersistedInstant(
                            requestedAt, claimed.executionRequestedAt());
        } catch (RuntimeException invalidClaim) {
            return false;
        }
    }

    private static boolean samePersistedInstant(Instant expected, Instant stored) {
        return stored != null && Duration.between(expected, stored).abs()
                .compareTo(Duration.ofMillis(1)) < 0;
    }

    private static void suppressCleanupFailure(
            RuntimeException failure, RuntimeException cleanupFailure) {
        if (failure != cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private HarnessExecution receipt(
            String action, CommandReceipt receipt, TaskStatus expectedStatus) {
        requireReceipt(receipt, expectedStatus);
        operationJournal.recordReceipt(ID, action, identity, receipt);
        return receiptOutput(receipt);
    }

    private HarnessExecution executionReceipt(
            HarnessCommand command, CommandReceipt receipt,
            TaskStatus expectedStatus) {
        requireReceipt(receipt, expectedStatus);
        operationJournal.recordExecutionReceipt(
                ID, command.requestId(), executionRequestFingerprint(command),
                identity, receipt);
        return receiptOutput(receipt);
    }

    private void requireReceipt(
            CommandReceipt receipt, TaskStatus expectedStatus) {
        if (receipt == null) {
            throw new IllegalStateException("Optimus adapter returned no command receipt");
        }
        if (receipt.status() != expectedStatus) {
            throw new IllegalStateException("Optimus adapter returned "
                    + receipt.status() + " where " + expectedStatus + " was required");
        }
        if (receipt.acceptedAt().isAfter(clock.instant()
                .plusSeconds(ROBOT_STATE_MAX_FUTURE_SKEW_SECONDS))) {
            throw new IllegalStateException(
                    "Optimus adapter returned a future-dated command receipt");
        }
    }

    private static HarnessExecution receiptOutput(CommandReceipt receipt) {
        return HarnessExecution.success(receipt.message(), Map.of(
                "commandId", receipt.commandId(),
                "status", receipt.status().name(),
                "acceptedAt", receipt.acceptedAt().toString()));
    }

    private static String executionRequestFingerprint(HarnessCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, command.requestId());
            updateDigest(digest, command.action());
            updateDigest(digest, command.text("plan-token"));
            updateDigest(digest, command.text("lease-token"));
            updateDigest(digest, command.expiresAt() == null
                    ? "" : command.expiresAt().toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static HarnessArgumentSpec argument(
            String name, HarnessValueType type, String description, boolean required) {
        return new HarnessArgumentSpec(name, type, description, required);
    }

    private static HarnessArgumentSpec leaseTokenArgument() {
        return argument("lease-token", HarnessValueType.STRING,
                "Active exclusive control capability for this robot.", true);
    }

    private static HarnessArgumentSpec arrayArgument(
            String name, HarnessValueType itemType,
            String description, boolean required) {
        return new HarnessArgumentSpec(name, HarnessValueType.ARRAY,
                description, required, itemType);
    }

    private static HarnessObservationSpec signal(
            String name, HarnessValueType type, String description,
            String unit, boolean sensitive) {
        return new HarnessObservationSpec(name, type, description, unit, sensitive);
    }
}
