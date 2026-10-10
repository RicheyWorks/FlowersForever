package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.HarnessActionEffect;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessManifest;
import com.flowerfarm.automation.HarnessPluginCapability;
import com.flowerfarm.automation.HarnessPluginKind;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.HarnessToolDefinition;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskDraft;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.AdapterMode;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskStatus;
import com.flowerfarm.automation.optimus.OptimusControlLeaseService.LeaseAuthorization;
import com.flowerfarm.automation.optimus.OptimusStagedPlanStore.StoredPlan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Optimus-ready robot harness adapter")
class OptimusHarnessPluginTest {

    private static final String TOKEN = "optimus-harness-token-123456789";

    @Test
    @DisplayName("publishes high-level robot tools without inventing a vendor transport")
    void publishesRobotContract() {
        HarnessGateway gateway = gateway(new SimulatedOptimusRobotAdapter(), properties());

        HarnessManifest manifest = gateway.manifest(TOKEN);
        assertThat(manifest.plugins()).singleElement().satisfies(plugin -> {
            assertThat(plugin.id()).isEqualTo("tesla-optimus");
            assertThat(plugin.kind()).isEqualTo(HarnessPluginKind.ROBOT);
            assertThat(plugin.capabilities()).contains(
                    HarnessPluginCapability.PHYSICAL_SENSING,
                    HarnessPluginCapability.PHYSICAL_ACTUATION,
                    HarnessPluginCapability.EMERGENCY_STOP);
        assertThat(plugin.version()).isEqualTo("0.23");
            assertThat(plugin.observations()).hasSize(27);
        });
        assertThat(manifest.tools())
                .extracting(HarnessToolDefinition::action)
                .containsExactly("get-control-lease", "acquire-control-lease",
                        "renew-control-lease", "release-control-lease",
                         "stage-task", "cancel-staged-task",
                         "execute-staged-task", "get-operation",
                         "list-operations", "list-operation-events",
                         "cancel-active-task",
                         "return-safe-pose", "emergency-stop");
        assertThat(tool(manifest, "get-control-lease").annotations().readOnlyHint())
                .isTrue();
        assertThat(tool(manifest, "acquire-control-lease").risk())
                .isEqualTo(HarnessRisk.MUTATE);
        HarnessToolDefinition stage = tool(manifest, "stage-task");
        assertThat(stage.annotations().physicalWorldHint()).isFalse();
        assertThat(stage.annotations().idempotentHint()).isFalse();
        assertThat(stage.allowed()).isFalse();
        assertThat(stage.inputSchema().toString())
                .contains("max-duration-seconds", "constraints", "items={type=string}",
                        "lease-token");
        HarnessToolDefinition execute = tool(manifest, "execute-staged-task");
        assertThat(execute.risk()).isEqualTo(HarnessRisk.PHYSICAL);
        assertThat(execute.annotations().physicalWorldHint()).isTrue();
        assertThat(execute.annotations().freshnessRequired()).isTrue();
        assertThat(execute.allowed()).isFalse();
        HarnessToolDefinition getOperation = tool(manifest, "get-operation");
        assertThat(getOperation.risk()).isEqualTo(HarnessRisk.READ);
        assertThat(getOperation.annotations().readOnlyHint()).isTrue();
        assertThat(getOperation.allowed()).isTrue();
        assertThat(tool(manifest, "list-operations").annotations().readOnlyHint())
                .isTrue();
        assertThat(tool(manifest, "list-operation-events")
                .annotations().readOnlyHint()).isTrue();
        HarnessToolDefinition cancel = tool(manifest, "cancel-active-task");
        assertThat(cancel.risk()).isEqualTo(HarnessRisk.PHYSICAL);
        assertThat(cancel.annotations().physicalWorldHint()).isTrue();
        assertThat(cancel.annotations().idempotentHint()).isTrue();
        assertThat(cancel.annotations().freshnessRequired()).isTrue();

        HarnessSnapshot observation = gateway.observe(TOKEN, OptimusHarnessPlugin.ID);
        assertThat(observation.metadata())
                .containsEntry("connection-state", "SIMULATED")
                .containsEntry("adapter-mode", "SIMULATOR")
                .containsEntry("simulated", true)
                .containsEntry("robot-model", "Optimus simulator")
                .containsEntry("coordinate-frame", "simulator-safe-home")
                .containsEntry("ready-for-command", true)
                .containsEntry("operational", true)
                .containsEntry("task-status", "IDLE")
                .containsEntry("task-progress-percent", 0.0)
                .containsEntry("staged-plan", false)
                .containsEntry("staged-plan-expired", false)
                .containsEntry("staged-plan-expires-at", "")
                .containsEntry("staged-plan-authorized", false)
                .containsEntry("staged-plan-adapter-confirmed", false)
                .containsEntry("execution-recovery-pending", false)
                .containsEntry("execution-adapter-confirmed", false)
                .containsEntry("control-lease-active", false)
                .containsEntry("control-lease-expires-at", "")
                .doesNotContainKeys("robot-id", "current-task", "task-command-id",
                        "task-status-message", "pose",
                        "control-lease-holder-fingerprint");
    }

    @Test
    @DisplayName("stages before motion, enforces policy and expiry, replays once, and stops")
    void executesReviewedPhysicalFlow() {
        SimulatedOptimusRobotAdapter adapter = new SimulatedOptimusRobotAdapter();
        HarnessProperties properties = properties();
        HarnessGateway gateway = gateway(adapter, properties);
        properties.setAllowDrafts(true);
        properties.setAllowMutations(true);
        String leaseToken = acquireLease(gateway, "reviewed-flow");

        HarnessResponse staged = gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("stage-1", "stage-task", Map.of(
                        "task", "Carry one empty harvest tote to the wash station",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 120,
                        "constraints", List.of("Keep clear of people",
                                "Do not handle tools"),
                        "lease-token", leaseToken)));
        String planToken = String.valueOf(staged.output().get("planToken"));
        assertThat(planToken).startsWith("sim-plan-");
        assertThat(staged.output()).containsKey("planExpiresAt");
        assertThat(staged.snapshot().metadata())
                .containsEntry("staged-plan", true)
                .containsEntry("staged-plan-expired", false)
                .containsEntry("staged-plan-adapter-confirmed", true)
                .doesNotContainKey("current-task");

        properties.setAllowSensitiveRead(true);
        assertThat(gateway.observe(TOKEN, OptimusHarnessPlugin.ID).metadata())
                .containsEntry("current-task",
                        "Carry one empty harvest tote to the wash station")
                .containsKey("pose");

        HarnessCommand physical = physicalCommand(
                "execute-1", "execute-staged-task", Map.of(
                        "plan-token", planToken, "lease-token", leaseToken));
        assertThatThrownBy(() -> gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID, physical))
                .isInstanceOf(HarnessPolicyException.class)
                .hasMessageContaining("PHYSICAL");

        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        properties.setAllowPhysicalActions(true);
        properties.validateConfiguration();
        assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("execute-no-expiry", "execute-staged-task",
                        Map.of("plan-token", planToken,
                                "lease-token", leaseToken))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires expiresAt");
        assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                physicalCommand("execute-wrong-plan", "execute-staged-task",
                        Map.of("plan-token", "different-plan",
                                "lease-token", leaseToken))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");

        HarnessResponse running = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID, physical);
        HarnessResponse replay = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID, physical);
        assertThat(running.output()).containsEntry("status", "RUNNING");
        assertThat(running.snapshot().metadata())
                .containsEntry("staged-plan", false)
                .containsEntry("staged-plan-adapter-confirmed", false)
                .containsEntry("motion-state", "RUNNING")
                .containsEntry("task-status", "RUNNING")
                .containsEntry("task-progress-percent", 0.0)
                .containsEntry("ready-for-command", false)
                .containsEntry("operational", false);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output().get("commandId"))
                .isEqualTo(running.output().get("commandId"));

        String commandId = String.valueOf(running.output().get("commandId"));
        HarnessResponse liveOperation = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("get-running-1", "get-operation",
                        Map.of("command-id", commandId)));
        assertThat(liveOperation.output())
                .containsEntry("operationId", commandId)
                .containsEntry("status", "RUNNING")
                .containsEntry("reconciliation", "LIVE")
                .containsEntry("terminal", false);
        adapter.advanceActiveTask(35, "Carrying tote through the marked lane");
        assertThat(gateway.observe(TOKEN, OptimusHarnessPlugin.ID).metadata())
                .containsEntry("task-command-id", commandId)
                .containsEntry("task-status", "RUNNING")
                .containsEntry("task-progress-percent", 35.0)
                .containsEntry("task-status-message",
                        "Carrying tote through the marked lane");
        assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                physicalCommand("cancel-wrong-command", "cancel-active-task",
                        Map.of("command-id", "different-command",
                                "lease-token", leaseToken))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");

        HarnessCommand cancel = physicalCommand(
                "cancel-1", "cancel-active-task", Map.of(
                        "command-id", commandId, "lease-token", leaseToken));
        HarnessResponse cancelled = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID, cancel);
        HarnessResponse cancelReplay = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID, cancel);
        HarnessResponse idempotentCancellation = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                physicalCommand("cancel-2", "cancel-active-task",
                        Map.of("command-id", commandId,
                                "lease-token", leaseToken)));
        assertThat(cancelled.output()).containsEntry("status", "CANCELLED");
        assertThat(cancelled.snapshot().metadata())
                .containsEntry("task-status", "CANCELLED")
                .containsEntry("motion-state", "IDLE")
                .containsEntry("ready-for-command", true);
        assertThat(cancelReplay.replayed()).isTrue();
        assertThat(cancelReplay.output().get("commandId"))
                .isEqualTo(cancelled.output().get("commandId"));
        assertThat(idempotentCancellation.replayed()).isFalse();
        assertThat(idempotentCancellation.output().get("commandId"))
                .isEqualTo(cancelled.output().get("commandId"));
        HarnessResponse terminalOperation = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("get-cancelled-1", "get-operation",
                        Map.of("command-id", commandId)));
        assertThat(terminalOperation.output())
                .containsEntry("operationId", commandId)
                .containsEntry("status", "CANCELLED")
                .containsEntry("progressPercent", 35.0)
                .containsEntry("terminal", true)
                .containsEntry("reconciliation", "LIVE");
        HarnessResponse recentOperations = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("list-operations-1", "list-operations",
                        Map.of("limit", 10)));
        assertThat(recentOperations.output())
                .containsEntry("count", 2)
                .containsEntry("limit", 10);
        assertThat(String.valueOf(recentOperations.output().get("operations")))
                .contains(commandId, "CANCELLED", "TERMINAL_HISTORY")
                .doesNotContain("robotId", "statusMessage");
        assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("list-too-many", "list-operations",
                        Map.of("limit", 26))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 25");
        HarnessResponse firstEventPage = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("list-events-1", "list-operation-events",
                        Map.of("after-cursor", "0", "limit", 2)));
        assertThat(firstEventPage.output())
                .containsEntry("count", 2)
                .containsEntry("limit", 2)
                .containsEntry("hasMore", true);
        String nextCursor = String.valueOf(
                firstEventPage.output().get("nextCursor"));
        assertThat(Long.parseLong(nextCursor)).isPositive();
        assertThat(String.valueOf(firstEventPage.output().get("events")))
                .contains(commandId, "RUNNING", "cursor")
                .doesNotContain("robotId", "statusMessage");
        HarnessResponse remainingEvents = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("list-events-2", "list-operation-events",
                        Map.of("after-cursor", nextCursor, "limit", 50)));
        assertThat(remainingEvents.output()).containsEntry("hasMore", false);
        assertThat(String.valueOf(remainingEvents.output().get("events")))
                .contains("CANCELLED", "cursor")
                .doesNotContain("robotId", "statusMessage");
        assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("bad-event-cursor", "list-operation-events",
                        Map.of("after-cursor", "not-a-cursor"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative decimal cursor");
        assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("event-limit-too-large", "list-operation-events",
                        Map.of("limit", 51))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 50");

        HarnessResponse stopped = gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                physicalCommand("stop-1", "emergency-stop", Map.of()));
        assertThat(stopped.output()).containsEntry("status", "STOPPED");
        assertThat(stopped.snapshot().metadata())
                .containsEntry("emergency-stop", true)
                .containsEntry("ready-for-command", false);
        assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("stage-after-stop", "stage-task", Map.of(
                        "task", "Move another tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reset it at the robot");
    }

    @Test
    @DisplayName("expires reviewed plans and blocks stale robot state")
    void rejectsExpiredPlansAndStaleState() {
        SimulatedOptimusRobotAdapter adapter = new SimulatedOptimusRobotAdapter();
        MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
        HarnessProperties properties = properties();
        properties.setAllowDrafts(true);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        properties.setAllowPhysicalActions(true);
        properties.validateConfiguration();
        HarnessGateway gateway = gateway(
                new OptimusHarnessPlugin(adapter, clock), properties);
        String leaseToken = acquireLease(gateway, "expiry-test");

        HarnessResponse staged = gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("expiring-stage", "stage-task", Map.of(
                        "task", "Move an empty tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)));
        String planToken = String.valueOf(staged.output().get("planToken"));
        assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("replace-stage", "stage-task", Map.of(
                        "task", "Move another tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already staged");

        clock.advance(Duration.ofSeconds(
                OptimusRobotAdapter.MAX_STAGED_PLAN_TTL_SECONDS - 10));
        HarnessResponse renewed = gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("renew-expiry-lease", "renew-control-lease", Map.of(
                        "lease-token", leaseToken,
                        "duration-seconds", 300)));
        String renewedLeaseToken = String.valueOf(
                renewed.output().get("leaseToken"));
        clock.advance(Duration.ofSeconds(11));
        assertThat(gateway.observe(TOKEN, OptimusHarnessPlugin.ID).metadata())
                .containsEntry("operational", false)
                .containsEntry("staged-plan", true)
                .containsEntry("staged-plan-expired", true);
        assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                physicalCommand("expired-execute", "execute-staged-task",
                        Map.of("plan-token", planToken,
                                "lease-token", renewedLeaseToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
        assertThat(gateway.observe(TOKEN, OptimusHarnessPlugin.ID).metadata())
                .containsEntry("staged-plan", false)
                .containsEntry("staged-plan-expired", false);

        Clock fixed = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
        OptimusHarnessPlugin stalePlugin = new OptimusHarnessPlugin(
                new StateOnlyAdapter(fixed.instant().minusSeconds(
                        OptimusHarnessPlugin.ROBOT_STATE_MAX_AGE_SECONDS + 1)), fixed);
        assertThat(stalePlugin.observe().metadata()).containsEntry("operational", false);
        String staleLeaseToken = String.valueOf(stalePlugin.execute(new HarnessCommand(
                "stale-acquire", "acquire-control-lease", Map.of(
                        "holder", "stale-test", "duration-seconds", 30)))
                .output().get("leaseToken"));
        assertThatThrownBy(() -> stalePlugin.execute(new HarnessCommand(
                "stale-stage", "stage-task", Map.of(
                "task", "Move a tote", "work-area", "Packing shed",
                "max-duration-seconds", 60,
                "lease-token", staleLeaseToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("state is stale");
    }

    @Test
    @DisplayName("reports successful terminal progress and verifies hardware identity")
    void reportsTerminalProgressAndVerifiesHardwareIdentity() {
        SimulatedOptimusRobotAdapter simulator = new SimulatedOptimusRobotAdapter();
        TaskDraft draft = new TaskDraft(
                "Carry an empty tote", "Packing shed", 60, List.of());
        String planToken = simulator.stage(draft).planToken();
        OptimusRobotAdapter.CommandReceipt running =
                simulator.executeStagedTask(planToken);
        simulator.advanceActiveTask(100, "Done");

        assertThat(running.status()).isEqualTo(OptimusRobotAdapter.TaskStatus.RUNNING);
        assertThat(simulator.observe().taskStatus())
                .isEqualTo(OptimusRobotAdapter.TaskStatus.SUCCEEDED);
        assertThat(simulator.observe().taskProgressPercent()).isEqualTo(100);
        assertThatThrownBy(() -> simulator.advanceActiveTask(90, "Going backwards"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currently running");

        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
        RobotIdentity hardware = new RobotIdentity(
                AdapterMode.HARDWARE, "optimus-owner-001", "Optimus",
                "official-sdk-version", "owner-declared-frame");
        StateOnlyAdapter adapter = new StateOnlyAdapter(now, hardware);
        assertThatThrownBy(() -> new OptimusHarnessPlugin(adapter, fixed, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires")
                .hasMessageContaining("expected-robot-id");
        assertThatThrownBy(() -> new OptimusHarnessPlugin(
                adapter, fixed, "different-robot"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
        assertThat(new OptimusHarnessPlugin(
                adapter, fixed, "optimus-owner-001").observe().metadata())
                .containsEntry("adapter-mode", "HARDWARE")
                .containsEntry("simulated", false)
                .containsEntry("robot-id", "optimus-owner-001");
    }

    @Test
    @DisplayName("rejects future-dated adapter command receipts")
    void rejectsFutureDatedCommandReceipt() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
        OptimusHarnessPlugin futureReceipt = new OptimusHarnessPlugin(
                new StateOnlyAdapter(now.plusSeconds(
                        OptimusHarnessPlugin.ROBOT_STATE_MAX_FUTURE_SKEW_SECONDS + 1)),
                fixed);
        assertThatThrownBy(() -> futureReceipt.execute(new HarnessCommand(
                "future-stop", "emergency-stop", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("future-dated command receipt");
    }

    @Test
    @DisplayName("marks an unreconciled running operation unknown after restart")
    void marksUnreconciledOperationUnknownAfterRestart() {
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        SimulatedOptimusRobotAdapter firstAdapter =
                new SimulatedOptimusRobotAdapter();
        OptimusHarnessPlugin first = new OptimusHarnessPlugin(
                firstAdapter, Clock.systemUTC(), "", journal);
        String leaseToken = String.valueOf(first.execute(new HarnessCommand(
                "restart-acquire", "acquire-control-lease", Map.of(
                        "holder", "restart-test", "duration-seconds", 60)))
                .output().get("leaseToken"));

        HarnessExecution staged = first.execute(new HarnessCommand(
                "restart-stage", "stage-task", Map.of(
                "task", "Carry an empty tote", "work-area", "Packing shed",
                "max-duration-seconds", 60,
                "lease-token", leaseToken)));
        String planToken = String.valueOf(staged.output().get("planToken"));
        HarnessExecution running = first.execute(new HarnessCommand(
                "restart-execute", "execute-staged-task",
                Map.of("plan-token", planToken, "lease-token", leaseToken)));
        String operationId = String.valueOf(running.output().get("commandId"));
        first.observe();

        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(
                new SimulatedOptimusRobotAdapter(), Clock.systemUTC(), "", journal);
        HarnessExecution recovered = restarted.execute(new HarnessCommand(
                "restart-query", "get-operation",
                Map.of("command-id", operationId)));

        assertThat(recovered.output())
                .containsEntry("operationId", operationId)
                .containsEntry("status", "UNKNOWN")
                .containsEntry("terminal", false)
                .containsEntry("reconciliation", "UNCONFIRMED");
    }

    @Test
    @DisplayName("leases are exclusive, expiring, rotating, and emergency-stop independent")
    void enforcesRobotScopedControlLeases() {
        MutableClock clock = new MutableClock(
                Instant.now(), ZoneOffset.UTC);
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        HarnessProperties properties = properties();
        properties.setAllowDrafts(true);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        properties.setAllowPhysicalActions(true);
        properties.validateConfiguration();
        OptimusHarnessPlugin firstPlugin = new OptimusHarnessPlugin(
                new SimulatedOptimusRobotAdapter(), clock, "", journal, leases);
        HarnessGateway first = new HarnessGateway(
                new HarnessRegistry(List.of(firstPlugin)), properties, clock);

        HarnessResponse acquired = first.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-acquire-1", "acquire-control-lease", Map.of(
                        "holder", "packing-agent-a", "duration-seconds", 30)));
        String leaseId = String.valueOf(acquired.output().get("leaseId"));
        String firstToken = String.valueOf(acquired.output().get("leaseToken"));
        assertThat(firstToken).hasSize(43);
        assertThat(acquired.snapshot().metadata())
                .containsEntry("control-lease-active", true)
                .doesNotContainValue("packing-agent-a")
                .doesNotContainValue(firstToken);

        OptimusHarnessPlugin restartedPlugin = new OptimusHarnessPlugin(
                new SimulatedOptimusRobotAdapter(), clock, "", journal, leases);
        HarnessGateway restarted = new HarnessGateway(
                new HarnessRegistry(List.of(restartedPlugin)), properties, clock);
        assertThatThrownBy(() -> restarted.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-acquire-race", "acquire-control-lease", Map.of(
                        "holder", "packing-agent-b", "duration-seconds", 30))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already leased");
        HarnessResponse status = restarted.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-status-1", "get-control-lease", Map.of()));
        assertThat(status.output())
                .containsEntry("active", true)
                .doesNotContainKeys("leaseId", "leaseToken", "holder",
                        "holderFingerprint");
        assertThatThrownBy(() -> first.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-stage-wrong", "stage-task", Map.of(
                        "task", "Move an empty tote", "work-area", "Packing shed",
                        "max-duration-seconds", 30,
                        "lease-token", "wrong-lease-token"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid active control lease");

        HarnessResponse staged = first.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-stage-1", "stage-task", Map.of(
                        "task", "Move an empty tote", "work-area", "Packing shed",
                        "max-duration-seconds", 30,
                        "lease-token", firstToken)));
        assertThat(staged.snapshot().metadata())
                .containsEntry("staged-plan-authorized", true);

        clock.advance(Duration.ofSeconds(5));
        HarnessResponse renewed = first.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-renew-1", "renew-control-lease", Map.of(
                        "lease-token", firstToken, "duration-seconds", 30)));
        String renewedToken = String.valueOf(renewed.output().get("leaseToken"));
        assertThat(renewed.output()).containsEntry("leaseId", leaseId);
        assertThat(renewedToken).isNotEqualTo(firstToken);
        assertThatThrownBy(() -> first.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-cancel-old-token", "cancel-staged-task", Map.of(
                        "lease-token", firstToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid active control lease");
        first.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-cancel-new-token", "cancel-staged-task", Map.of(
                        "lease-token", renewedToken)));

        restarted.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-release-1", "release-control-lease", Map.of(
                        "lease-token", renewedToken)));
        assertThat(restarted.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-status-2", "get-control-lease", Map.of()))
                .output()).containsEntry("active", false)
                .containsEntry("expiresAt", "")
                .doesNotContainKey("holderFingerprint");

        assertThatThrownBy(() -> restarted.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-too-short", "acquire-control-lease", Map.of(
                        "holder", "packing-agent-b", "duration-seconds", 14))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 15 and 300");
        String expiringToken = String.valueOf(restarted.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-acquire-2", "acquire-control-lease", Map.of(
                        "holder", "packing-agent-b", "duration-seconds", 15)))
                .output().get("leaseToken"));
        restarted.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-stage-before-expiry", "stage-task", Map.of(
                        "task", "Move a tote", "work-area", "Packing shed",
                        "max-duration-seconds", 30,
                        "lease-token", expiringToken)));
        clock.advance(Duration.ofSeconds(16));
        assertThatThrownBy(() -> restarted.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-expired-stage", "stage-task", Map.of(
                        "task", "Move a tote", "work-area", "Packing shed",
                        "max-duration-seconds", 30,
                        "lease-token", expiringToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid active control lease");
        String replacementToken = String.valueOf(restarted.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-acquire-3", "acquire-control-lease", Map.of(
                        "holder", "packing-agent-c", "duration-seconds", 30)))
                .output().get("leaseToken"));
        HarnessResponse orphanCancelled = restarted.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-cancel-orphan", "cancel-staged-task", Map.of(
                        "lease-token", replacementToken)));
        assertThat(orphanCancelled.snapshot().metadata())
                .containsEntry("staged-plan", false)
                .containsEntry("staged-plan-authorized", false);

        HarnessResponse emergency = restarted.execute(
                TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("lease-free-stop", "emergency-stop", Map.of(),
                        clock.instant().plusSeconds(10)));
        assertThat(emergency.output()).containsEntry("status", "STOPPED");
    }

    @Test
    @DisplayName("resumes a staged task after plugin restart")
    void resumesStagedTaskAfterRestart() {
        Clock clock = Clock.systemUTC();
        SimulatedOptimusRobotAdapter adapter = new SimulatedOptimusRobotAdapter();
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin first = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(first.execute(new HarnessCommand(
                "staged-restart-acquire", "acquire-control-lease", Map.of(
                        "holder", "restart-agent", "duration-seconds", 60)))
                .output().get("leaseToken"));
        HarnessExecution staged = first.execute(new HarnessCommand(
                "staged-restart-stage", "stage-task", Map.of(
                        "task", "Move an empty tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)));
        String planToken = String.valueOf(staged.output().get("planToken"));

        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        assertThat(restarted.observe().metadata())
                .containsEntry("staged-plan", true)
                .containsEntry("staged-plan-authorized", true)
                .containsEntry("staged-plan-adapter-confirmed", true);
        HarnessExecution running = restarted.execute(new HarnessCommand(
                "staged-restart-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken, "lease-token", leaseToken)));

        assertThat(running.output()).containsEntry("status", "RUNNING");
        assertThat(restarted.observe().metadata())
                .containsEntry("staged-plan", false)
                .containsEntry("staged-plan-adapter-confirmed", false)
                .containsEntry("task-status", "RUNNING");

        String commandId = String.valueOf(running.output().get("commandId"));
        restarted.execute(new HarnessCommand(
                "staged-restart-cancel-running", "cancel-active-task", Map.of(
                        "command-id", commandId, "lease-token", leaseToken)));
        HarnessExecution stagedBeforeAdapterRestart = restarted.execute(
                new HarnessCommand("stage-before-adapter-restart", "stage-task", Map.of(
                        "task", "Move another empty tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)));
        String stalePlanToken = String.valueOf(
                stagedBeforeAdapterRestart.output().get("planToken"));

        OptimusHarnessPlugin adapterRestarted = new OptimusHarnessPlugin(
                new SimulatedOptimusRobotAdapter(), clock, "",
                journal, leases, stagedPlans);
        assertThat(adapterRestarted.observe().metadata())
                .containsEntry("staged-plan", true)
                .containsEntry("staged-plan-authorized", true)
                .containsEntry("staged-plan-adapter-confirmed", false)
                .containsEntry("task-status", "IDLE");
        assertThatThrownBy(() -> adapterRestarted.execute(new HarnessCommand(
                "execute-after-adapter-restart", "execute-staged-task", Map.of(
                        "plan-token", stalePlanToken,
                        "lease-token", leaseToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no longer reports this exact task as staged");
        assertThat(adapterRestarted.observe().metadata())
                .containsEntry("staged-plan", false)
                .containsEntry("staged-plan-adapter-confirmed", false);
        adapterRestarted.execute(new HarnessCommand(
                "restage-after-adapter-restart", "stage-task", Map.of(
                        "task", "Move another empty tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)));
        assertThat(adapterRestarted.observe().metadata())
                .containsEntry("staged-plan", true)
                .containsEntry("staged-plan-adapter-confirmed", true);

        OptimusRobotAdapter strictRestartedAdapter = mock(
                OptimusRobotAdapter.class,
                delegatesTo(new SimulatedOptimusRobotAdapter()));
        OptimusHarnessPlugin directlyRestaged = new OptimusHarnessPlugin(
                strictRestartedAdapter, clock, "", journal, leases, stagedPlans);
        directlyRestaged.execute(new HarnessCommand(
                "direct-restage-after-adapter-restart", "stage-task", Map.of(
                        "task", "Move another empty tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)));
        verify(strictRestartedAdapter, never()).cancelStagedTask();
        assertThat(directlyRestaged.observe().metadata())
                .containsEntry("staged-plan", true)
                .containsEntry("staged-plan-adapter-confirmed", true);
    }

    @Test
    @DisplayName("confirms the exact staged plan instead of any staged task")
    void confirmsExactStagedPlanFingerprint() {
        Clock clock = Clock.systemUTC();
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        SimulatedOptimusRobotAdapter sourceAdapter =
                new SimulatedOptimusRobotAdapter();
        OptimusHarnessPlugin source = new OptimusHarnessPlugin(
                sourceAdapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(source.execute(new HarnessCommand(
                "fingerprint-acquire", "acquire-control-lease", Map.of(
                        "holder", "fingerprint-agent", "duration-seconds", 300)))
                .output().get("leaseToken"));
        String originalPlanToken = String.valueOf(source.execute(new HarnessCommand(
                "fingerprint-stage", "stage-task", Map.of(
                        "task", "Move reviewed tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60, "lease-token", leaseToken)))
                .output().get("planToken"));
        assertThat(source.observe().metadata())
                .containsEntry("staged-plan-adapter-confirmed", true);

        SimulatedOptimusRobotAdapter mismatchedAdapter =
                new SimulatedOptimusRobotAdapter();
        mismatchedAdapter.stage(new TaskDraft(
                "Move unrelated tote", "Loading bay", 60, List.of()));
        OptimusHarnessPlugin mismatched = new OptimusHarnessPlugin(
                mismatchedAdapter, clock, "", journal, leases, stagedPlans);
        assertThat(mismatched.observe().metadata())
                .containsEntry("staged-plan", true)
                .containsEntry("task-status", "STAGED")
                .containsEntry("staged-plan-adapter-confirmed", false);
        assertThatThrownBy(() -> mismatched.execute(new HarnessCommand(
                "fingerprint-mismatch-execute", "execute-staged-task", Map.of(
                        "plan-token", originalPlanToken,
                        "lease-token", leaseToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact task");
        assertThat(mismatched.observe().metadata())
                .containsEntry("staged-plan", false)
                .containsEntry("task-status", "STAGED");

        String replacementPlanToken = String.valueOf(mismatched.execute(
                new HarnessCommand("fingerprint-restage", "stage-task", Map.of(
                        "task", "Move reviewed tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)))
                .output().get("planToken"));
        assertThat(mismatched.observe().metadata())
                .containsEntry("staged-plan-adapter-confirmed", true);

        OptimusRobotAdapter.RobotState exactState = mismatchedAdapter.observe();
        OptimusRobotAdapter missingFingerprint = mock(
                OptimusRobotAdapter.class, delegatesTo(mismatchedAdapter));
        when(missingFingerprint.observe()).thenReturn(
                withoutStagedPlanFingerprint(exactState));
        OptimusHarnessPlugin legacyRestart = new OptimusHarnessPlugin(
                missingFingerprint, clock, "", journal, leases, stagedPlans);
        assertThat(legacyRestart.observe().metadata())
                .containsEntry("task-status", "STAGED")
                .containsEntry("staged-plan-adapter-confirmed", false);
        assertThatThrownBy(() -> legacyRestart.execute(new HarnessCommand(
                "fingerprint-missing-execute", "execute-staged-task", Map.of(
                        "plan-token", replacementPlanToken,
                        "lease-token", leaseToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact task");
        assertThat(legacyRestart.observe().metadata())
                .containsEntry("staged-plan", false);
    }

    @Test
    @DisplayName("returns a plan token only after exact fresh adapter acknowledgement")
    void requiresExactFreshStageAcknowledgement() {
        for (StageAcknowledgement acknowledgement : StageAcknowledgement.values()) {
            Clock clock = Clock.systemUTC();
            OptimusRobotAdapter adapter = acknowledgementAdapter(
                    acknowledgement, clock);
            OptimusHarnessPlugin plugin = new OptimusHarnessPlugin(
                    adapter, clock, "", new TransientOptimusOperationJournal(),
                    new TransientOptimusControlLeaseService(clock),
                    new TransientOptimusStagedPlanStore());
            String leaseToken = String.valueOf(plugin.execute(new HarnessCommand(
                    "ack-acquire-" + acknowledgement, "acquire-control-lease", Map.of(
                            "holder", "ack-agent", "duration-seconds", 300)))
                    .output().get("leaseToken"));

            assertThatThrownBy(() -> plugin.execute(new HarnessCommand(
                    "ack-stage-" + acknowledgement, "stage-task", Map.of(
                            "task", "Move acknowledgement tote",
                            "work-area", "Packing shed",
                            "max-duration-seconds", 60,
                            "lease-token", leaseToken))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(acknowledgement == StageAcknowledgement.STALE
                            ? "stale" : "confirm the exact staged task");
            verify(adapter).cancelStagedTask();
            assertThat(plugin.observe().metadata())
                    .containsEntry("staged-plan", false)
                    .containsEntry("task-status", "IDLE");
        }
    }

    @Test
    @DisplayName("rolls back ambiguous staging and lease revalidation failures")
    void rollsBackEveryFailureAfterAdapterStageStarts() {
        Clock clock = Clock.systemUTC();
        SimulatedOptimusRobotAdapter ambiguousDelegate =
                new SimulatedOptimusRobotAdapter();
        OptimusRobotAdapter ambiguousAdapter = mock(
                OptimusRobotAdapter.class, delegatesTo(ambiguousDelegate));
        IllegalStateException stageFailure = new IllegalStateException(
                "Adapter timed out after accepting the draft");
        doAnswer(invocation -> {
            ambiguousDelegate.stage(invocation.getArgument(0));
            throw stageFailure;
        }).when(ambiguousAdapter).stage(any(TaskDraft.class));
        OptimusHarnessPlugin ambiguousPlugin = new OptimusHarnessPlugin(
                ambiguousAdapter, clock);
        String ambiguousLease = String.valueOf(ambiguousPlugin.execute(
                new HarnessCommand("rollback-acquire", "acquire-control-lease",
                        Map.of("holder", "rollback-agent",
                                "duration-seconds", 300)))
                .output().get("leaseToken"));

        assertThat(catchThrowable(() -> ambiguousPlugin.execute(new HarnessCommand(
                "rollback-ambiguous-stage", "stage-task", Map.of(
                        "task", "Carry an empty tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", ambiguousLease)))))
                .isSameAs(stageFailure);
        verify(ambiguousAdapter).cancelStagedTask();
        assertThat(ambiguousDelegate.observe().taskStatus()).isEqualTo(TaskStatus.IDLE);

        SimulatedOptimusRobotAdapter leaseDelegate =
                new SimulatedOptimusRobotAdapter();
        OptimusRobotAdapter leaseAdapter = mock(
                OptimusRobotAdapter.class, delegatesTo(leaseDelegate));
        OptimusControlLeaseService leases = mock(OptimusControlLeaseService.class);
        LeaseAuthorization initial = new LeaseAuthorization(
                "lease-before-stage", clock.instant().plusSeconds(300));
        IllegalStateException leaseFailure = new IllegalStateException(
                "Control lease expired while the task was being staged");
        when(leases.requireValid(any(RobotIdentity.class), anyString()))
                .thenReturn(initial)
                .thenThrow(leaseFailure);
        OptimusHarnessPlugin leasePlugin = new OptimusHarnessPlugin(
                leaseAdapter, clock, "", new TransientOptimusOperationJournal(),
                leases, new TransientOptimusStagedPlanStore());

        assertThat(catchThrowable(() -> leasePlugin.execute(new HarnessCommand(
                "rollback-expired-lease", "stage-task", Map.of(
                        "task", "Carry a harvest tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", "expiring-lease-token")))))
                .isSameAs(leaseFailure);
        verify(leaseAdapter).cancelStagedTask();
        assertThat(leaseDelegate.observe().taskStatus()).isEqualTo(TaskStatus.IDLE);
    }

    @Test
    @DisplayName("preserves the primary staging failure when rollback also fails")
    void preservesPrimaryStageFailureWhenRollbackFails() {
        Clock clock = Clock.systemUTC();
        SimulatedOptimusRobotAdapter delegate = new SimulatedOptimusRobotAdapter();
        OptimusRobotAdapter adapter = mock(
                OptimusRobotAdapter.class, delegatesTo(delegate));
        OptimusStagedPlanStore stagedPlans = mock(OptimusStagedPlanStore.class);
        IllegalStateException storageFailure = new IllegalStateException(
                "Staged-plan database is unavailable");
        IllegalStateException cleanupFailure = new IllegalStateException(
                "Robot connection was lost during rollback");
        when(stagedPlans.replace(
                any(RobotIdentity.class), anyString(), any()))
                .thenThrow(storageFailure);
        doThrow(cleanupFailure).when(adapter).cancelStagedTask();
        OptimusHarnessPlugin plugin = new OptimusHarnessPlugin(
                adapter, clock, "", new TransientOptimusOperationJournal(),
                new TransientOptimusControlLeaseService(clock), stagedPlans);
        String leaseToken = String.valueOf(plugin.execute(new HarnessCommand(
                "rollback-store-acquire", "acquire-control-lease", Map.of(
                        "holder", "rollback-agent", "duration-seconds", 300)))
                .output().get("leaseToken"));

        Throwable thrown = catchThrowable(() -> plugin.execute(new HarnessCommand(
                "rollback-store-stage", "stage-task", Map.of(
                        "task", "Carry a propagation tray",
                        "work-area", "Greenhouse aisle",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken))));

        assertThat(thrown).isSameAs(storageFailure);
        assertThat(thrown.getSuppressed()).containsExactly(cleanupFailure);
        verify(adapter).cancelStagedTask();
        verify(stagedPlans, never()).clear(any(RobotIdentity.class));
        assertThat(delegate.observe().taskStatus()).isEqualTo(TaskStatus.STAGED);
    }

    @Test
    @DisplayName("returns a token only after exact staged-plan commit verification")
    void verifiesExactDurablePlanCommitBeforeReturningToken() {
        for (CommitVerification verification : CommitVerification.values()) {
            Clock clock = Clock.systemUTC();
            SimulatedOptimusRobotAdapter delegate =
                    new SimulatedOptimusRobotAdapter();
            OptimusRobotAdapter adapter = mock(
                    OptimusRobotAdapter.class, delegatesTo(delegate));
            OptimusStagedPlanStore stagedPlans = mock(OptimusStagedPlanStore.class);
            AtomicReference<StoredPlan> committed = new AtomicReference<>();
            IllegalStateException readFailure = new IllegalStateException(
                    "Staged-plan read failed after commit");
            when(stagedPlans.replace(
                    any(RobotIdentity.class), anyString(), any()))
                    .thenAnswer(invocation -> {
                        RobotIdentity identity = invocation.getArgument(0);
                        String leaseId = invocation.getArgument(1);
                        OptimusRobotAdapter.TaskPlan plan = invocation.getArgument(2);
                        StoredPlan exact = new StoredPlan(
                                identity.robotId(),
                                identity.adapterMode() == AdapterMode.SIMULATOR,
                                leaseId,
                                OptimusRobotAdapter.planFingerprint(plan.planToken()),
                                plan.stagedAt(), plan.expiresAt());
                        committed.set(exact);
                        return verification == CommitVerification.RETURN_MISMATCH
                                ? mismatchedStoredPlan(exact) : exact;
                    });
            when(stagedPlans.current(any(RobotIdentity.class)))
                    .thenAnswer(ignored -> {
                        StoredPlan exact = committed.get();
                        if (exact == null) {
                            return Optional.empty();
                        }
                        return switch (verification) {
                            case RETURN_MISMATCH, NORMALIZED_READ -> Optional.of(
                                    verification == CommitVerification.NORMALIZED_READ
                                            ? normalizedStoredPlan(exact) : exact);
                            case MISSING_READ -> Optional.empty();
                            case MISMATCHED_READ -> Optional.of(
                                    mismatchedStoredPlan(exact));
                            case READ_FAILURE -> throw readFailure;
                        };
                    });
            OptimusHarnessPlugin plugin = new OptimusHarnessPlugin(
                    adapter, clock, "", new TransientOptimusOperationJournal(),
                    new TransientOptimusControlLeaseService(clock), stagedPlans);
            String leaseToken = String.valueOf(plugin.execute(new HarnessCommand(
                    "commit-acquire-" + verification, "acquire-control-lease",
                    Map.of("holder", "commit-agent", "duration-seconds", 300)))
                    .output().get("leaseToken"));

            HarnessCommand stageCommand = new HarnessCommand(
                    "commit-stage-" + verification, "stage-task", Map.of(
                            "task", "Carry a verified tote",
                            "work-area", "Packing shed",
                            "max-duration-seconds", 60,
                            "lease-token", leaseToken));

            if (verification == CommitVerification.NORMALIZED_READ) {
                HarnessExecution staged = plugin.execute(stageCommand);
                assertThat(staged.output().get("planToken")).isNotNull();
                verify(adapter, never()).cancelStagedTask();
                verify(stagedPlans, never()).clear(any(RobotIdentity.class));
                assertThat(delegate.observe().taskStatus()).isEqualTo(TaskStatus.STAGED);
                continue;
            }
            Throwable thrown = catchThrowable(() -> plugin.execute(stageCommand));

            if (verification == CommitVerification.READ_FAILURE) {
                assertThat(thrown).isSameAs(readFailure);
            } else {
                assertThat(thrown)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("did not retain the exact staged task");
            }
            verify(adapter).cancelStagedTask();
            verify(stagedPlans).clear(any(RobotIdentity.class));
            assertThat(delegate.observe().taskStatus()).isEqualTo(TaskStatus.IDLE);
        }
    }

    @Test
    @DisplayName("recovers an accepted execution after its adapter response is lost")
    void recoversAcceptedExecutionAfterRestart() {
        Clock clock = Clock.systemUTC();
        SimulatedOptimusRobotAdapter delegate = new SimulatedOptimusRobotAdapter();
        OptimusRobotAdapter adapter = mock(
                OptimusRobotAdapter.class, delegatesTo(delegate));
        IllegalStateException lostResponse = new IllegalStateException(
                "Adapter response was lost after execution started");
        doAnswer(invocation -> {
            delegate.executeStagedTask(invocation.getArgument(0));
            throw lostResponse;
        }).when(adapter).executeStagedTask(anyString());
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin plugin = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(plugin.execute(new HarnessCommand(
                "intent-acquire", "acquire-control-lease", Map.of(
                        "holder", "recovery-agent", "duration-seconds", 300)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(plugin.execute(new HarnessCommand(
                "intent-stage", "stage-task", Map.of(
                        "task", "Carry an empty recovery tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)))
                .output().get("planToken"));
        HarnessCommand originalExecution = new HarnessCommand(
                "intent-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken, "lease-token", leaseToken));

        assertThat(catchThrowable(() -> plugin.execute(originalExecution)))
                .isSameAs(lostResponse);
        assertThat(stagedPlans.current(adapter.identity()))
                .hasValueSatisfying(intent -> {
                    assertThat(intent.executionPending()).isTrue();
                    assertThat(intent.claimedBy(originalExecution.requestId())).isTrue();
                });
        assertThat(plugin.observe().metadata())
                .containsEntry("staged-plan", false)
                .containsEntry("execution-recovery-pending", true)
                .containsEntry("execution-adapter-confirmed", true)
                .containsEntry("task-status", "RUNNING");

        HarnessCommand conflictingExecution = new HarnessCommand(
                "intent-execute-conflict", "execute-staged-task", Map.of(
                        "plan-token", planToken, "lease-token", leaseToken));
        assertThatThrownBy(() -> plugin.execute(conflictingExecution))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already pending");

        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        HarnessExecution recovered = restarted.execute(originalExecution);

        assertThat(recovered.output())
                .containsEntry("status", "RUNNING")
                .containsEntry("recovered", true);
        assertThat(stagedPlans.current(adapter.identity())).isEmpty();
        assertThat(journal.latest(
                OptimusHarnessPlugin.ID,
                String.valueOf(recovered.output().get("commandId"))))
                .hasValueSatisfying(event -> {
                    assertThat(event.action()).isEqualTo("execute-staged-task");
                    assertThat(event.status()).isEqualTo(TaskStatus.RUNNING);
                });
        verify(adapter, times(1)).executeStagedTask(planToken);
    }

    @Test
    @DisplayName("retries the exact claimed plan when execution was not accepted")
    void retriesUnacceptedExecutionAfterRestart() {
        Clock clock = Clock.systemUTC();
        SimulatedOptimusRobotAdapter delegate = new SimulatedOptimusRobotAdapter();
        OptimusRobotAdapter adapter = mock(
                OptimusRobotAdapter.class, delegatesTo(delegate));
        IllegalStateException unavailable = new IllegalStateException(
                "Adapter was unavailable before accepting execution");
        AtomicReference<Boolean> firstAttempt = new AtomicReference<>(true);
        doAnswer(invocation -> {
            if (firstAttempt.getAndSet(false)) {
                throw unavailable;
            }
            return delegate.executeStagedTask(invocation.getArgument(0));
        }).when(adapter).executeStagedTask(anyString());
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin plugin = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(plugin.execute(new HarnessCommand(
                "unaccepted-acquire", "acquire-control-lease", Map.of(
                        "holder", "retry-agent", "duration-seconds", 300)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(plugin.execute(new HarnessCommand(
                "unaccepted-stage", "stage-task", Map.of(
                        "task", "Carry an empty retry tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)))
                .output().get("planToken"));
        HarnessCommand execution = new HarnessCommand(
                "unaccepted-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken, "lease-token", leaseToken));

        assertThat(catchThrowable(() -> plugin.execute(execution))).isSameAs(unavailable);
        assertThat(plugin.observe().metadata())
                .containsEntry("execution-recovery-pending", true)
                .containsEntry("execution-adapter-confirmed", false)
                .containsEntry("task-status", "STAGED");

        HarnessExecution recovered = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans)
                .execute(execution);

        assertThat(recovered.output())
                .containsEntry("status", "RUNNING")
                .doesNotContainKey("recovered");
        assertThat(stagedPlans.current(adapter.identity())).isEmpty();
        verify(adapter, times(2)).executeStagedTask(planToken);
    }

    @Test
    @DisplayName("durably replays a completed execution after gateway restart")
    void durablyReplaysCompletedExecutionAfterGatewayRestart() {
        Clock clock = Clock.systemUTC();
        OptimusRobotAdapter adapter = recordingSimulator();
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin first = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(first.execute(new HarnessCommand(
                "durable-replay-acquire", "acquire-control-lease", Map.of(
                        "holder", "durable-replay-agent",
                        "duration-seconds", 300)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(first.execute(new HarnessCommand(
                "durable-replay-stage", "stage-task", Map.of(
                        "task", "Carry a replay tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)))
                .output().get("planToken"));
        HarnessCommand original = new HarnessCommand(
                "durable-replay-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken,
                        "lease-token", leaseToken), clock.instant().plusSeconds(20));
        HarnessExecution accepted = first.execute(original);

        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        HarnessGateway restartedGateway = new HarnessGateway(
                new HarnessRegistry(List.of(restarted)), properties(),
                Clock.fixed(original.expiresAt().plusSeconds(1), ZoneOffset.UTC));
        HarnessResponse replay = restartedGateway.execute(
                TOKEN, OptimusHarnessPlugin.ID, original);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output())
                .containsEntry("commandId", accepted.output().get("commandId"))
                .containsEntry("status", "RUNNING")
                .containsEntry("recovered", true)
                .containsEntry("reconciliationPersisted", true);
        verify(adapter, times(1)).executeStagedTask(planToken);

        HarnessCommand collision = new HarnessCommand(
                original.requestId(), original.action(), Map.of(
                        "plan-token", "different-plan",
                        "lease-token", leaseToken), original.expiresAt());
        assertThatThrownBy(() -> new HarnessGateway(
                new HarnessRegistry(List.of(restarted)), properties())
                .execute(TOKEN, OptimusHarnessPlugin.ID, collision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already used for a different command");
        verify(adapter, times(1)).executeStagedTask(planToken);
    }

    @Test
    @DisplayName("replays a durable execution while the owner API is unavailable")
    void durablyReplaysExecutionDuringAdapterOutage() {
        Clock clock = Clock.systemUTC();
        OptimusRobotAdapter adapter = recordingSimulator();
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin first = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(first.execute(new HarnessCommand(
                "outage-replay-acquire", "acquire-control-lease", Map.of(
                        "holder", "outage-replay-agent",
                        "duration-seconds", 300)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(first.execute(new HarnessCommand(
                "outage-replay-stage", "stage-task", Map.of(
                        "task", "Carry an outage replay tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)))
                .output().get("planToken"));
        HarnessCommand original = new HarnessCommand(
                "outage-replay-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken,
                        "lease-token", leaseToken), clock.instant().plusSeconds(20));
        HarnessExecution accepted = first.execute(original);
        doThrow(new IllegalStateException("Owner API unavailable"))
                .when(adapter).observe();

        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        assertThat(restarted.observe().metadata())
                .containsEntry("connection-state", "UNAVAILABLE")
                .containsEntry("task-status", "UNKNOWN");
        HarnessResponse replay = gateway(restarted, properties()).execute(
                TOKEN, OptimusHarnessPlugin.ID, original);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output())
                .containsEntry("commandId", accepted.output().get("commandId"))
                .containsEntry("status", "UNKNOWN")
                .containsEntry("recovered", true)
                .containsEntry("adapterConfirmed", false)
                .containsEntry("reconciliationPersisted", true)
                .containsEntry("reconciliation", "UNCONFIRMED");
        assertThat(replay.snapshot().metadata())
                .containsEntry("connection-state", "UNAVAILABLE")
                .containsEntry("operational", false)
                .containsEntry("task-status", "UNKNOWN")
                .containsEntry("execution-adapter-confirmed", false)
                .doesNotContainKeys("battery-percent", "emergency-stop", "pose");
        assertThat(journal.latest(
                OptimusHarnessPlugin.ID,
                String.valueOf(accepted.output().get("commandId"))))
                .hasValueSatisfying(event -> {
                    assertThat(event.status()).isEqualTo(TaskStatus.UNKNOWN);
                    assertThat(event.statusMessage())
                            .contains("unavailable during durable response replay");
                });
        assertThatThrownBy(() -> restarted.execute(physicalCommand(
                "outage-new-physical-command", "return-safe-pose", Map.of(
                        "lease-token", leaseToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adapter state is unavailable");
        verify(adapter, times(1)).executeStagedTask(planToken);
        verify(adapter, never()).returnToSafePose();

        RobotIdentity startupIdentity = adapter.identity();
        when(adapter.identity()).thenReturn(new RobotIdentity(
                startupIdentity.adapterMode(), "different-robot",
                startupIdentity.model(), startupIdentity.softwareVersion(),
                startupIdentity.coordinateFrame()));
        assertThatThrownBy(() -> gateway(restarted, properties()).execute(
                TOKEN, OptimusHarnessPlugin.ID, original))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity changed");
        verify(adapter, times(1)).executeStagedTask(planToken);
    }

    @Test
    @DisplayName("replays when the unconfirmed reconciliation cannot be appended")
    void replaysWhenReconciliationJournalWriteFails() {
        Clock clock = Clock.systemUTC();
        OptimusRobotAdapter adapter = recordingSimulator();
        TransientOptimusOperationJournal durableJournal =
                new TransientOptimusOperationJournal();
        OptimusOperationJournal journal = mock(
                OptimusOperationJournal.class, delegatesTo(durableJournal));
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin first = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(first.execute(new HarnessCommand(
                "journal-outage-acquire", "acquire-control-lease", Map.of(
                        "holder", "journal-outage-agent",
                        "duration-seconds", 300)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(first.execute(new HarnessCommand(
                "journal-outage-stage", "stage-task", Map.of(
                        "task", "Carry a journal outage tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)))
                .output().get("planToken"));
        HarnessCommand original = new HarnessCommand(
                "journal-outage-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken,
                        "lease-token", leaseToken), clock.instant().plusSeconds(20));
        HarnessExecution accepted = first.execute(original);
        doThrow(new IllegalStateException("Owner API unavailable"))
                .when(adapter).observe();
        doThrow(new IllegalStateException("Journal is temporarily read-only"))
                .when(journal).recordUnknown(
                        anyString(), any(RobotIdentity.class),
                        any(OptimusOperationJournal.OperationEvent.class),
                        any(Instant.class), anyString());

        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        HarnessResponse replay = gateway(restarted, properties()).execute(
                TOKEN, OptimusHarnessPlugin.ID, original);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output())
                .containsEntry("commandId", accepted.output().get("commandId"))
                .containsEntry("status", "UNKNOWN")
                .containsEntry("recovered", true)
                .containsEntry("adapterConfirmed", false)
                .containsEntry("reconciliationPersisted", false)
                .containsEntry("reconciliation", "UNCONFIRMED");
        assertThat(replay.snapshot().metadata())
                .containsEntry("connection-state", "UNAVAILABLE")
                .containsEntry("task-status", "UNKNOWN")
                .containsEntry("operational", false);
        assertThat(durableJournal.latest(
                OptimusHarnessPlugin.ID,
                String.valueOf(accepted.output().get("commandId"))))
                .hasValueSatisfying(event ->
                        assertThat(event.status()).isEqualTo(TaskStatus.RUNNING));
        HarnessResponse retried = gateway(restarted, properties()).execute(
                TOKEN, OptimusHarnessPlugin.ID, original);
        assertThat(retried.output())
                .containsEntry("status", "UNKNOWN")
                .containsEntry("reconciliationPersisted", false);
        verify(adapter, times(1)).executeStagedTask(planToken);
    }

    @Test
    @DisplayName("replays live state when its lifecycle append is unavailable")
    void replaysLiveStateWhenLifecycleJournalWriteFails() {
        Clock clock = Clock.systemUTC();
        OptimusRobotAdapter adapter = recordingSimulator();
        TransientOptimusOperationJournal durableJournal =
                new TransientOptimusOperationJournal();
        OptimusOperationJournal journal = mock(
                OptimusOperationJournal.class, delegatesTo(durableJournal));
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin first = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(first.execute(new HarnessCommand(
                "live-journal-acquire", "acquire-control-lease", Map.of(
                        "holder", "live-journal-agent",
                        "duration-seconds", 300)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(first.execute(new HarnessCommand(
                "live-journal-stage", "stage-task", Map.of(
                        "task", "Carry a live journal tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)))
                .output().get("planToken"));
        HarnessCommand original = new HarnessCommand(
                "live-journal-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken,
                        "lease-token", leaseToken), clock.instant().plusSeconds(20));
        HarnessExecution accepted = first.execute(original);
        doThrow(new IllegalStateException("Lifecycle journal is read-only"))
                .when(journal).recordState(
                        anyString(), any(RobotIdentity.class),
                        any(OptimusRobotAdapter.RobotState.class));

        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        HarnessResponse replay = gateway(restarted, properties()).execute(
                TOKEN, OptimusHarnessPlugin.ID, original);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output())
                .containsEntry("commandId", accepted.output().get("commandId"))
                .containsEntry("status", "RUNNING")
                .containsEntry("recovered", true)
                .containsEntry("adapterConfirmed", true)
                .containsEntry("reconciliationPersisted", false)
                .containsEntry("reconciliation", "LIVE");
        assertThat(replay.snapshot().metadata())
                .containsEntry("connection-state", "SIMULATED")
                .containsEntry("operational", false)
                .containsEntry("task-status", "RUNNING");
        assertThat(durableJournal.latest(
                OptimusHarnessPlugin.ID,
                String.valueOf(accepted.output().get("commandId"))))
                .hasValueSatisfying(event ->
                        assertThat(event.status()).isEqualTo(TaskStatus.RUNNING));
        verify(journal, times(2)).recordState(
                anyString(), any(RobotIdentity.class),
                any(OptimusRobotAdapter.RobotState.class));
        verify(adapter, times(1)).executeStagedTask(planToken);
    }

    @Test
    @DisplayName("keeps operation reads available when lifecycle appends fail")
    void keepsOperationReadsAvailableWhenLifecycleAppendsFail() {
        Clock clock = Clock.systemUTC();
        OptimusRobotAdapter adapter = recordingSimulator();
        TransientOptimusOperationJournal durableJournal =
                new TransientOptimusOperationJournal();
        OptimusOperationJournal journal = mock(
                OptimusOperationJournal.class, delegatesTo(durableJournal));
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin plugin = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(plugin.execute(new HarnessCommand(
                "read-outage-acquire", "acquire-control-lease", Map.of(
                        "holder", "read-outage-agent",
                        "duration-seconds", 300)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(plugin.execute(new HarnessCommand(
                "read-outage-stage", "stage-task", Map.of(
                        "task", "Carry a read outage tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)))
                .output().get("planToken"));
        HarnessExecution accepted = plugin.execute(new HarnessCommand(
                "read-outage-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken,
                        "lease-token", leaseToken), clock.instant().plusSeconds(20)));
        String commandId = String.valueOf(accepted.output().get("commandId"));
        doThrow(new IllegalStateException("Lifecycle journal is read-only"))
                .when(journal).recordState(
                        anyString(), any(RobotIdentity.class),
                        any(OptimusRobotAdapter.RobotState.class));

        HarnessExecution live = plugin.execute(new HarnessCommand(
                "read-outage-live", "get-operation",
                Map.of("command-id", commandId)));
        HarnessExecution recent = plugin.execute(new HarnessCommand(
                "read-outage-recent", "list-operations", Map.of()));
        HarnessExecution events = plugin.execute(new HarnessCommand(
                "read-outage-events", "list-operation-events", Map.of()));

        assertThat(live.output())
                .containsEntry("operationId", commandId)
                .containsEntry("status", "RUNNING")
                .containsEntry("reconciliation", "LIVE")
                .containsEntry("reconciliationPersisted", false);
        assertThat(String.valueOf(recent.output().get("operations")))
                .contains(commandId, "RUNNING", "LIVE",
                        "reconciliationPersisted=false");
        assertThat(events.output())
                .containsEntry("reconciliationPersisted", false);
        assertThat(String.valueOf(events.output().get("events")))
                .contains(commandId, "RUNNING");

        doThrow(new IllegalStateException("Lifecycle journal is read-only"))
                .when(journal).recordUnknown(
                        anyString(), any(RobotIdentity.class),
                        any(OptimusOperationJournal.OperationEvent.class),
                        any(Instant.class), anyString());
        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(
                new SimulatedOptimusRobotAdapter(), clock, "",
                journal, leases, stagedPlans);
        HarnessExecution unconfirmed = restarted.execute(new HarnessCommand(
                "read-outage-unconfirmed", "get-operation",
                Map.of("command-id", commandId)));
        HarnessExecution unconfirmedList = restarted.execute(new HarnessCommand(
                "read-outage-unconfirmed-list", "list-operations", Map.of()));

        assertThat(unconfirmed.output())
                .containsEntry("operationId", commandId)
                .containsEntry("status", "UNKNOWN")
                .containsEntry("reconciliation", "UNCONFIRMED")
                .containsEntry("reconciliationPersisted", false);
        assertThat(String.valueOf(unconfirmedList.output().get("operations")))
                .contains(commandId, "UNKNOWN", "UNCONFIRMED",
                        "reconciliationPersisted=false");
        assertThat(durableJournal.latest(OptimusHarnessPlugin.ID, commandId))
                .hasValueSatisfying(event ->
                        assertThat(event.status()).isEqualTo(TaskStatus.RUNNING));
        verify(adapter, times(1)).executeStagedTask(planToken);
    }

    @Test
    @DisplayName("preserves progress and terminal history when live journal writes fail")
    void preservesLifecycleDuringJournalWriteOutage() {
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        OptimusRobotAdapter adapter = recordingSimulator();
        TransientOptimusOperationJournal durableJournal = new TransientOptimusOperationJournal();
        OptimusOperationJournal journal = mock(
                OptimusOperationJournal.class, delegatesTo(durableJournal));
        OptimusHarnessPlugin plugin = new OptimusHarnessPlugin(
                adapter, clock, "", journal, new TransientOptimusControlLeaseService(clock),
                new TransientOptimusStagedPlanStore());
        String leaseToken = String.valueOf(plugin.execute(new HarnessCommand(
                "monotonic-outage-lease", "acquire-control-lease", Map.of(
                        "holder", "outage-review", "duration-seconds", 300)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(plugin.execute(new HarnessCommand(
                "monotonic-outage-stage", "stage-task", Map.of(
                        "task", "Carry one tote", "work-area", "Shed",
                        "max-duration-seconds", 60, "lease-token", leaseToken)))
                .output().get("planToken"));
        HarnessCommand original = new HarnessCommand(
                "monotonic-outage-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken, "lease-token", leaseToken));
        String commandId = String.valueOf(plugin.execute(original).output().get("commandId"));
        OptimusRobotAdapter.RobotState current = adapter.observe();
        durableJournal.recordState(OptimusHarnessPlugin.ID, adapter.identity(),
                operationState(commandId, TaskStatus.RUNNING, 65, "Progress confirmed",
                        clock.instant().plusSeconds(1)));
        when(adapter.observe()).thenReturn(new OptimusRobotAdapter.RobotState(
                current.connectionState(), current.motionState(), current.batteryPercent(),
                current.emergencyStopped(), current.readyForCommand(), current.currentTask(),
                current.pose(), commandId, TaskStatus.RUNNING, 20, "Delayed progress",
                current.stagedPlanFingerprint(), clock.instant().plusSeconds(2)));
        doThrow(new IllegalStateException("Journal is read-only"))
                .when(journal).recordState(anyString(), any(RobotIdentity.class),
                        any(OptimusRobotAdapter.RobotState.class));

        HarnessExecution progressing = plugin.execute(new HarnessCommand(
                "monotonic-outage-progress", "get-operation", Map.of("command-id", commandId)));
        assertThat(progressing.output()).containsEntry("progressPercent", 65d)
                .containsEntry("reconciliationPersisted", false);

        durableJournal.recordState(OptimusHarnessPlugin.ID, adapter.identity(),
                operationState(commandId, TaskStatus.SUCCEEDED, 100, "Finished",
                        clock.instant().plusSeconds(2)));
        HarnessExecution finished = plugin.execute(new HarnessCommand(
                "monotonic-outage-finished", "get-operation", Map.of("command-id", commandId)));
        HarnessExecution recent = plugin.execute(new HarnessCommand(
                "monotonic-outage-recent", "list-operations", Map.of()));
        HarnessExecution replayed = plugin.replay(original).orElseThrow();

        assertThat(finished.output()).containsEntry("status", "SUCCEEDED")
                .containsEntry("progressPercent", 100d).containsEntry("terminal", true);
        assertThat(String.valueOf(recent.output().get("operations")))
                .contains("status=SUCCEEDED", "progressPercent=100.0", "terminal=true");
        assertThat(replayed.output()).containsEntry("status", "SUCCEEDED")
                .containsEntry("reconciliationPersisted", false);
        verify(adapter, times(1)).executeStagedTask(planToken);
    }

    @Test
    @DisplayName("blocks physical dispatch when a lease expires during a state read")
    void blocksDispatchAfterLeaseExpiresDuringRead() {
        for (String action : List.of(
                "execute-staged-task", "cancel-active-task", "return-safe-pose")) {
            DispatchFixture fixture = dispatchFixture(action, 15);
            OptimusRobotAdapter.RobotState state = fixture.adapter().observe();
            doAnswer(ignored -> {
                fixture.clock().advance(Duration.ofSeconds(16));
                return copyStagedState(state, state.stagedPlanFingerprint(),
                        fixture.clock().instant());
            }).when(fixture.adapter()).observe();

            assertThatThrownBy(() -> fixture.plugin().execute(fixture.command(null)))
                    .as(action).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("valid active control lease");
            fixture.verifyNoDispatch();
        }
    }

    @Test
    @DisplayName("blocks physical dispatch when a lease rotates during a state read")
    void blocksDispatchAfterLeaseRotatesDuringRead() {
        DispatchFixture fixture = dispatchFixture("execute-staged-task", 300);
        OptimusRobotAdapter.RobotState state = fixture.adapter().observe();
        doAnswer(ignored -> {
            fixture.plugin().execute(new HarnessCommand(
                    "rotate-during-observe", "renew-control-lease", Map.of(
                            "lease-token", fixture.leaseToken(), "duration-seconds", 300)));
            return state;
        }).when(fixture.adapter()).observe();

        assertThatThrownBy(() -> fixture.plugin().execute(fixture.command(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid active control lease");
        fixture.verifyNoDispatch();
    }

    @Test
    @DisplayName("rechecks an initially fresh physical deadline after an adapter state read")
    void blocksDispatchAfterDeadlineExpiresDuringRead() {
        for (String action : List.of(
                "execute-staged-task", "cancel-active-task", "return-safe-pose")) {
            DispatchFixture fixture = dispatchFixture(action, 300);
            HarnessCommand command = fixture.command(fixture.clock().instant().plusSeconds(10));
            OptimusRobotAdapter.RobotState state = fixture.adapter().observe();
            doAnswer(ignored -> {
                fixture.clock().advance(Duration.ofSeconds(11));
                return copyStagedState(state, state.stagedPlanFingerprint(),
                        fixture.clock().instant());
            }).when(fixture.adapter()).observe();
            HarnessProperties policy = properties();
            policy.setAllowMutations(true);
            policy.setAllowDestructive(true);
            policy.setAllowPhysicalActions(true);
            HarnessGateway gateway = new HarnessGateway(
                    new HarnessRegistry(List.of(fixture.plugin())), policy, fixture.clock());

            assertThatThrownBy(() -> gateway.execute(TOKEN, OptimusHarnessPlugin.ID, command))
                    .as(action).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expired before dispatch");
            fixture.verifyNoDispatch();
        }
    }

    @Test
    @DisplayName("keeps durable operation reads available during owner API outage")
    void keepsOperationReadsAvailableDuringOwnerApiOutage() {
        Clock clock = Clock.systemUTC();
        OptimusRobotAdapter adapter = recordingSimulator();
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin first = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        String leaseToken = String.valueOf(first.execute(new HarnessCommand(
                "offline-read-acquire", "acquire-control-lease", Map.of(
                        "holder", "offline-read-agent",
                        "duration-seconds", 300)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(first.execute(new HarnessCommand(
                "offline-read-stage", "stage-task", Map.of(
                        "task", "Carry an offline-read tote",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 60,
                        "lease-token", leaseToken)))
                .output().get("planToken"));
        HarnessExecution accepted = first.execute(new HarnessCommand(
                "offline-read-execute", "execute-staged-task", Map.of(
                        "plan-token", planToken,
                        "lease-token", leaseToken), clock.instant().plusSeconds(20)));
        String commandId = String.valueOf(accepted.output().get("commandId"));
        doThrow(new IllegalStateException("Owner API unavailable"))
                .when(adapter).observe();

        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(
                adapter, clock, "", journal, leases, stagedPlans);
        HarnessGateway gateway = gateway(restarted, properties());
        HarnessResponse operation = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID, new HarnessCommand(
                        "offline-read-operation", "get-operation",
                        Map.of("command-id", commandId)));
        HarnessResponse recent = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID, new HarnessCommand(
                        "offline-read-recent", "list-operations", Map.of()));
        HarnessResponse events = gateway.execute(
                TOKEN, OptimusHarnessPlugin.ID, new HarnessCommand(
                        "offline-read-events", "list-operation-events", Map.of()));

        assertThat(operation.output())
                .containsEntry("operationId", commandId)
                .containsEntry("status", "UNKNOWN")
                .containsEntry("reconciliation", "UNCONFIRMED")
                .containsEntry("reconciliationPersisted", true)
                .containsEntry("adapterAvailable", false);
        assertThat(operation.snapshot().metadata())
                .containsEntry("connection-state", "UNAVAILABLE")
                .containsEntry("task-status", "UNKNOWN")
                .containsEntry("operational", false);
        assertThat(String.valueOf(recent.output().get("operations")))
                .contains(commandId, "UNKNOWN", "UNCONFIRMED",
                        "reconciliationPersisted=true",
                        "adapterAvailable=false");
        assertThat(events.output())
                .containsEntry("adapterAvailable", false)
                .containsEntry("reconciliationPersisted", true);
        assertThat(String.valueOf(events.output().get("events")))
                .contains(commandId, "RUNNING", "UNKNOWN");
        verify(adapter, times(1)).executeStagedTask(planToken);
    }

    @Test
    @DisplayName("keeps durable robot lifecycle and progress monotonic")
    void keepsDurableRobotLifecycleAndProgressMonotonic() {
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        RobotIdentity identity = new SimulatedOptimusRobotAdapter().identity();
        String commandId = "monotonic-operation";
        Instant acceptedAt = Instant.parse("2026-09-02T12:00:00Z");
        journal.recordExecutionReceipt(
                OptimusHarnessPlugin.ID, "monotonic-request", "a".repeat(64),
                identity, new OptimusRobotAdapter.CommandReceipt(
                        commandId, TaskStatus.RUNNING, "Accepted", acceptedAt));
        OptimusOperationJournal.OperationEvent running = journal.recordState(
                OptimusHarnessPlugin.ID, identity, operationState(
                        commandId, TaskStatus.RUNNING, 60,
                        "Sixty percent", acceptedAt.plusSeconds(1)));

        OptimusOperationJournal.OperationEvent older = journal.recordState(
                OptimusHarnessPlugin.ID, identity, operationState(
                        commandId, TaskStatus.RUNNING, 80,
                        "Late older sample", acceptedAt));
        OptimusOperationJournal.OperationEvent lowerProgress = journal.recordState(
                OptimusHarnessPlugin.ID, identity, operationState(
                        commandId, TaskStatus.RUNNING, 30,
                        "Regressed progress", acceptedAt.plusSeconds(2)));
        OptimusOperationJournal.OperationEvent regressedPhase = journal.recordState(
                OptimusHarnessPlugin.ID, identity, operationState(
                        commandId, TaskStatus.STAGED, 25,
                        "Regressed phase", acceptedAt.plusSeconds(3)));
        OptimusOperationJournal.OperationEvent recovered = journal.recordState(
                OptimusHarnessPlugin.ID, identity, operationState(
                        commandId, TaskStatus.RUNNING, 70,
                        "Recovered", acceptedAt.plusSeconds(4)));
        OptimusOperationJournal.OperationEvent succeeded = journal.recordState(
                OptimusHarnessPlugin.ID, identity, operationState(
                        commandId, TaskStatus.SUCCEEDED, 100,
                        "Complete", acceptedAt.plusSeconds(5)));
        int terminalEventCount = journal.after(
                OptimusHarnessPlugin.ID, 0, 20).size();
        OptimusOperationJournal.OperationEvent resurrected = journal.recordState(
                OptimusHarnessPlugin.ID, identity, operationState(
                        commandId, TaskStatus.RUNNING, 90,
                        "Stale reconnect", acceptedAt.plusSeconds(6)));

        assertThat(older).isEqualTo(running);
        assertThat(lowerProgress.status()).isEqualTo(TaskStatus.RUNNING);
        assertThat(lowerProgress.progressPercent()).isEqualTo(60);
        assertThat(regressedPhase.status()).isEqualTo(TaskStatus.UNKNOWN);
        assertThat(regressedPhase.progressPercent()).isEqualTo(60);
        assertThat(recovered.status()).isEqualTo(TaskStatus.RUNNING);
        assertThat(recovered.progressPercent()).isEqualTo(70);
        assertThat(succeeded.status()).isEqualTo(TaskStatus.SUCCEEDED);
        assertThat(succeeded.progressPercent()).isEqualTo(100);
        assertThat(resurrected).isEqualTo(succeeded);
        assertThat(journal.after(OptimusHarnessPlugin.ID, 0, 20))
                .hasSize(terminalEventCount);
    }

    @Test
    @DisplayName("reconciles stale staged records across every cleanup action")
    void reconcilesStaleStagedRecordsAcrossCleanupActions() {
        MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
        TransientOptimusOperationJournal journal =
                new TransientOptimusOperationJournal();
        TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(clock);
        TransientOptimusStagedPlanStore stagedPlans =
                new TransientOptimusStagedPlanStore();
        OptimusHarnessPlugin original = new OptimusHarnessPlugin(
                new SimulatedOptimusRobotAdapter(), clock, "",
                journal, leases, stagedPlans);
        String leaseToken = String.valueOf(original.execute(new HarnessCommand(
                "cleanup-acquire", "acquire-control-lease", Map.of(
                        "holder", "cleanup-agent", "duration-seconds", 300)))
                .output().get("leaseToken"));
        original.execute(new HarnessCommand(
                "cleanup-stage-cancel", "stage-task", Map.of(
                        "task", "Move cancel tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60, "lease-token", leaseToken)));

        OptimusRobotAdapter cancelAdapter = recordingSimulator();
        OptimusHarnessPlugin afterCancelRestart = new OptimusHarnessPlugin(
                cancelAdapter, clock, "", journal, leases, stagedPlans);
        afterCancelRestart.execute(new HarnessCommand(
                "cleanup-cancel", "cancel-staged-task",
                Map.of("lease-token", leaseToken)));
        verify(cancelAdapter, never()).cancelStagedTask();
        assertThat(afterCancelRestart.observe().metadata())
                .containsEntry("staged-plan", false);

        afterCancelRestart.execute(new HarnessCommand(
                "cleanup-stage-release", "stage-task", Map.of(
                        "task", "Move release tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60, "lease-token", leaseToken)));
        OptimusRobotAdapter releaseAdapter = recordingSimulator();
        OptimusHarnessPlugin afterReleaseRestart = new OptimusHarnessPlugin(
                releaseAdapter, clock, "", journal, leases, stagedPlans);
        afterReleaseRestart.execute(new HarnessCommand(
                "cleanup-release", "release-control-lease",
                Map.of("lease-token", leaseToken)));
        verify(releaseAdapter, never()).cancelStagedTask();
        assertThat(afterReleaseRestart.observe().metadata())
                .containsEntry("staged-plan", false)
                .containsEntry("control-lease-active", false);

        leaseToken = String.valueOf(afterReleaseRestart.execute(new HarnessCommand(
                "cleanup-reacquire", "acquire-control-lease", Map.of(
                        "holder", "cleanup-agent", "duration-seconds", 300)))
                .output().get("leaseToken"));
        afterReleaseRestart.execute(new HarnessCommand(
                "cleanup-stage-pose", "stage-task", Map.of(
                        "task", "Move pose tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60, "lease-token", leaseToken)));
        OptimusRobotAdapter poseAdapter = recordingSimulator();
        OptimusHarnessPlugin afterPoseRestart = new OptimusHarnessPlugin(
                poseAdapter, clock, "", journal, leases, stagedPlans);
        HarnessExecution safePose = afterPoseRestart.execute(new HarnessCommand(
                "cleanup-pose", "return-safe-pose",
                Map.of("lease-token", leaseToken)));
        verify(poseAdapter, never()).cancelStagedTask();
        assertThat(safePose.output()).containsEntry("status", "RUNNING");
        assertThat(afterPoseRestart.observe().metadata())
                .containsEntry("staged-plan", false);

        OptimusRobotAdapter expirySourceAdapter = recordingSimulator();
        OptimusHarnessPlugin expirySource = new OptimusHarnessPlugin(
                expirySourceAdapter, clock, "", journal, leases, stagedPlans);
        HarnessExecution expiring = expirySource.execute(new HarnessCommand(
                "cleanup-stage-expiry", "stage-task", Map.of(
                        "task", "Move expiry tote", "work-area", "Packing shed",
                        "max-duration-seconds", 60, "lease-token", leaseToken)));
        String planToken = String.valueOf(expiring.output().get("planToken"));
        clock.advance(Duration.ofSeconds(290));
        leaseToken = String.valueOf(expirySource.execute(new HarnessCommand(
                "cleanup-renew", "renew-control-lease", Map.of(
                        "lease-token", leaseToken, "duration-seconds", 300)))
                .output().get("leaseToken"));
        clock.advance(Duration.ofSeconds(11));

        OptimusRobotAdapter expiryAdapter = recordingSimulator();
        OptimusHarnessPlugin afterExpiryRestart = new OptimusHarnessPlugin(
                expiryAdapter, clock, "", journal, leases, stagedPlans);
        String renewedLeaseToken = leaseToken;
        assertThatThrownBy(() -> afterExpiryRestart.execute(new HarnessCommand(
                "cleanup-expired", "execute-staged-task", Map.of(
                        "plan-token", planToken,
                        "lease-token", renewedLeaseToken))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
        verify(expiryAdapter, never()).cancelStagedTask();
        assertThat(afterExpiryRestart.observe().metadata())
                .containsEntry("staged-plan", false);
    }

    @Test
    @DisplayName("is absent by default and requires explicit simulator wiring")
    void isConditionallyWired() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(OptimusTestConfiguration.class);
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(OptimusHarnessPlugin.class)
                .doesNotHaveBean(OptimusRobotAdapter.class));
        runner.withPropertyValues("flowerfarm.harness.optimus.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
                });
        runner.withPropertyValues(
                        "flowerfarm.harness.optimus.enabled=true",
                        "flowerfarm.harness.optimus.mode=simulator")
                .run(context -> assertThat(context)
                        .hasSingleBean(OptimusHarnessPlugin.class)
                        .hasSingleBean(OptimusRobotAdapter.class));
    }

    @Test
    @DisplayName("rejects unbounded task requests before they reach an adapter")
    void validatesTaskBounds() {
        assertThatThrownBy(() -> new TaskDraft("Move tote", "Shed", 901, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 900");
        assertThatThrownBy(() -> new TaskDraft("Move tote", "Shed", 60,
                java.util.Collections.nCopies(13, "constraint")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At most 12");
        Instant stagedAt = Instant.parse("2026-09-01T12:00:00Z");
        assertThatThrownBy(() -> new OptimusRobotAdapter.TaskPlan(
                "plan", new TaskDraft("Move tote", "Shed", 60, List.of()), stagedAt,
                stagedAt.plusSeconds(OptimusRobotAdapter.MAX_STAGED_PLAN_TTL_SECONDS + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must expire within");
        assertThatThrownBy(() -> new OptimusRobotAdapter.RobotState(
                "CONNECTED", "IDLE", 80, false, true, "", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("observedAt is required");
        assertThatThrownBy(() -> new OptimusRobotAdapter.TaskPlan(
                "plan", new TaskDraft("Move tote", "Shed", 60, List.of()), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stagedAt is required");
        assertThatThrownBy(() -> new OptimusRobotAdapter.CommandReceipt(
                "command", TaskStatus.RUNNING, "Accepted", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acceptedAt is required");
    }

    private static HarnessToolDefinition tool(
            HarnessManifest manifest, String action) {
        return manifest.tools().stream()
                .filter(candidate -> candidate.action().equals(action))
                .findFirst().orElseThrow();
    }

    private static DispatchFixture dispatchFixture(String action, int leaseSeconds) {
        MutableClock clock = new MutableClock(Instant.now(), ZoneOffset.UTC);
        OptimusRobotAdapter adapter = recordingSimulator();
        OptimusHarnessPlugin plugin = new OptimusHarnessPlugin(
                adapter, clock, "", new TransientOptimusOperationJournal(),
                new TransientOptimusControlLeaseService(clock),
                new TransientOptimusStagedPlanStore());
        String leaseToken = String.valueOf(plugin.execute(new HarnessCommand(
                "dispatch-lease", "acquire-control-lease", Map.of(
                        "holder", "dispatch-review", "duration-seconds", leaseSeconds)))
                .output().get("leaseToken"));
        String planToken = String.valueOf(plugin.execute(new HarnessCommand(
                "dispatch-stage", "stage-task", Map.of(
                        "task", "Carry one tote", "work-area", "Shed",
                        "max-duration-seconds", 60, "lease-token", leaseToken)))
                .output().get("planToken"));
        String commandId = "";
        if (action.equals("cancel-active-task")) {
            commandId = String.valueOf(plugin.execute(new HarnessCommand(
                    "dispatch-start", "execute-staged-task", Map.of(
                            "lease-token", leaseToken, "plan-token", planToken)))
                    .output().get("commandId"));
        }
        return new DispatchFixture(
                action, clock, adapter, plugin, leaseToken, planToken, commandId);
    }

    private record DispatchFixture(
            String action, MutableClock clock, OptimusRobotAdapter adapter,
            OptimusHarnessPlugin plugin, String leaseToken, String planToken, String commandId) {

        private HarnessCommand command(Instant expiresAt) {
            Map<String, Object> arguments = switch (action) {
                case "execute-staged-task" -> Map.of(
                        "lease-token", leaseToken, "plan-token", planToken);
                case "cancel-active-task" -> Map.of(
                        "lease-token", leaseToken, "command-id", commandId);
                default -> Map.of("lease-token", leaseToken);
            };
            return new HarnessCommand("dispatch-under-review", action, arguments, expiresAt);
        }

        private void verifyNoDispatch() {
            verify(adapter, times(action.equals("cancel-active-task") ? 1 : 0))
                    .executeStagedTask(planToken);
            verify(adapter, never()).cancelActiveTask(anyString());
            verify(adapter, never()).returnToSafePose();
            verify(adapter, never()).cancelStagedTask();
        }
    }

    private static HarnessCommand physicalCommand(
            String requestId, String action, Map<String, Object> arguments) {
        return new HarnessCommand(requestId, action, arguments,
                Instant.now().plusSeconds(10));
    }

    private static OptimusRobotAdapter.RobotState operationState(
            String commandId, TaskStatus status, double progress,
            String message, Instant observedAt) {
        boolean busy = status == TaskStatus.RUNNING || status == TaskStatus.STAGED;
        return new OptimusRobotAdapter.RobotState(
                "SIMULATED", status.name(), 80, false, !busy,
                "Monotonic lifecycle task", Map.of(), commandId,
                status, progress, message, observedAt);
    }

    private static String acquireLease(HarnessGateway gateway, String holder) {
        HarnessResponse acquired = gateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("acquire-" + holder, "acquire-control-lease", Map.of(
                        "holder", holder, "duration-seconds", 300)));
        return String.valueOf(acquired.output().get("leaseToken"));
    }

    private static HarnessGateway gateway(
            OptimusRobotAdapter adapter, HarnessProperties properties) {
        return gateway(new OptimusHarnessPlugin(adapter), properties);
    }

    private static OptimusRobotAdapter recordingSimulator() {
        return mock(OptimusRobotAdapter.class,
                delegatesTo(new SimulatedOptimusRobotAdapter()));
    }

    private static OptimusRobotAdapter acknowledgementAdapter(
            StageAcknowledgement acknowledgement, Clock clock) {
        SimulatedOptimusRobotAdapter delegate = new SimulatedOptimusRobotAdapter();
        OptimusRobotAdapter adapter = mock(
                OptimusRobotAdapter.class, delegatesTo(delegate));
        when(adapter.observe()).thenAnswer(ignored -> {
            OptimusRobotAdapter.RobotState state = delegate.observe();
            if (state.taskStatus() != TaskStatus.STAGED) {
                return state;
            }
            return switch (acknowledgement) {
                case IDLE -> new OptimusRobotAdapter.RobotState(
                        state.connectionState(), "IDLE", state.batteryPercent(),
                        state.emergencyStopped(), true, "", state.pose(),
                        clock.instant());
                case MISSING_FINGERPRINT -> withoutStagedPlanFingerprint(state);
                case MISMATCHED_FINGERPRINT -> copyStagedState(
                        state, OptimusRobotAdapter.planFingerprint("different-plan"),
                        state.observedAt());
                case STALE -> copyStagedState(
                        state, state.stagedPlanFingerprint(),
                        clock.instant().minusSeconds(
                                OptimusHarnessPlugin.ROBOT_STATE_MAX_AGE_SECONDS + 1));
            };
        });
        return adapter;
    }

    private static StoredPlan mismatchedStoredPlan(StoredPlan exact) {
        return new StoredPlan(
                exact.robotId(), exact.simulated(), exact.leaseId(),
                OptimusRobotAdapter.planFingerprint("different-plan"),
                exact.stagedAt(), exact.expiresAt());
    }

    private static StoredPlan normalizedStoredPlan(StoredPlan exact) {
        return new StoredPlan(
                exact.robotId(), exact.simulated(), exact.leaseId(),
                exact.planTokenHash(),
                exact.stagedAt().plusNanos(500_000),
                exact.expiresAt().plusNanos(500_000));
    }

    private static OptimusRobotAdapter.RobotState copyStagedState(
            OptimusRobotAdapter.RobotState state, String fingerprint,
            Instant observedAt) {
        return new OptimusRobotAdapter.RobotState(
                state.connectionState(), state.motionState(), state.batteryPercent(),
                state.emergencyStopped(), state.readyForCommand(), state.currentTask(),
                state.pose(), state.taskCommandId(), state.taskStatus(),
                state.taskProgressPercent(), state.taskStatusMessage(), fingerprint,
                observedAt);
    }

    private static OptimusRobotAdapter.RobotState withoutStagedPlanFingerprint(
            OptimusRobotAdapter.RobotState state) {
        return new OptimusRobotAdapter.RobotState(
                state.connectionState(), state.motionState(), state.batteryPercent(),
                state.emergencyStopped(), state.readyForCommand(), state.currentTask(),
                state.pose(), state.taskCommandId(), state.taskStatus(),
                state.taskProgressPercent(), state.taskStatusMessage(),
                state.observedAt());
    }

    private enum StageAcknowledgement {
        IDLE,
        MISSING_FINGERPRINT,
        MISMATCHED_FINGERPRINT,
        STALE
    }

    private enum CommitVerification {
        NORMALIZED_READ,
        RETURN_MISMATCH,
        MISSING_READ,
        MISMATCHED_READ,
        READ_FAILURE
    }

    private static HarnessGateway gateway(
            OptimusHarnessPlugin plugin, HarnessProperties properties) {
        return new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties);
    }

    private static HarnessProperties properties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.validateConfiguration();
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    @Import({OptimusHarnessPlugin.class, SimulatedOptimusRobotAdapter.class})
    static class OptimusTestConfiguration {

        @Bean
        OptimusOperationJournal operationJournal() {
            return new TransientOptimusOperationJournal();
        }

        @Bean
        OptimusControlLeaseService controlLeaseService() {
            return new TransientOptimusControlLeaseService(Clock.systemUTC());
        }

        @Bean
        OptimusStagedPlanStore stagedPlanStore() {
            return new TransientOptimusStagedPlanStore();
        }
    }

    private static final class StateOnlyAdapter implements OptimusRobotAdapter {
        private final Instant observedAt;
        private final RobotIdentity identity;

        private StateOnlyAdapter(Instant observedAt) {
            this(observedAt, new RobotIdentity(AdapterMode.SIMULATOR,
                    "state-only-simulator", "State-only simulator", "test",
                    "test-frame"));
        }

        private StateOnlyAdapter(Instant observedAt, RobotIdentity identity) {
            this.observedAt = observedAt;
            this.identity = identity;
        }

        @Override
        public RobotIdentity identity() {
            return identity;
        }

        @Override
        public RobotState observe() {
            return new RobotState("CONNECTED", "IDLE", 80, false,
                    true, "", Map.of(), observedAt);
        }

        @Override
        public TaskPlan stage(TaskDraft draft) {
            throw new AssertionError("Stale state must block staging");
        }

        @Override
        public void cancelStagedTask() {
            throw new AssertionError("Nothing was staged");
        }

        @Override
        public CommandReceipt executeStagedTask(String planToken) {
            throw new AssertionError("Nothing was staged");
        }

        @Override
        public CommandReceipt cancelActiveTask(String commandId) {
            throw new AssertionError("Nothing is running");
        }

        @Override
        public CommandReceipt returnToSafePose() {
            throw new AssertionError("State is stale");
        }

        @Override
        public CommandReceipt emergencyStop() {
            return new CommandReceipt("state-only-stop", TaskStatus.STOPPED,
                    "State-only adapter stop is latched", observedAt);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(instant, requestedZone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
