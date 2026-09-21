package com.flowerfarm.automation.optimus;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.AdapterMode;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.RobotIdentity;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskDraft;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter.TaskPlan;
import com.flowerfarm.automation.optimus.OptimusStagedPlanStore.StoredPlan;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OptimusReplayCleanupTest {
    private static final String TOKEN = "optimus-replay-cleanup-token-123456789";
    private static final TaskDraft DRAFT = new TaskDraft("Carry a tote", "packing", 60, List.of());

    @Test
    void clearsAnAcceptedExecutionClaimWhenCleanupRecoversAfterRestart() {
        Fixture fixture = new Fixture();
        fixture.acceptWhileCleanupFails();
        fixture.cleanupAvailable.set(true);

        HarnessResponse replay = fixture.replay();

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output()).containsEntry("commandId", fixture.commandId())
                .containsEntry("stagedPlanCleanupPersisted", true);
        assertThat(fixture.durablePlans.current(fixture.adapter.identity())).isEmpty();
        fixture.simulator.advanceActiveTask(100, "Complete");
        assertThat(fixture.plugin().execute(fixture.stageCommand("next-stage")).output())
                .containsKey("planToken");
        verify(fixture.adapter, times(1)).executeStagedTask(fixture.planToken);
    }

    @Test
    void returnsTheAcceptedReceiptWhileCleanupRemainsUnavailableThenRetriesCleanup() {
        Fixture fixture = new Fixture();
        fixture.acceptWhileCleanupFails();

        for (int attempt = 0; attempt < 2; attempt++) {
            HarnessResponse replay = fixture.replay();
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.output()).containsEntry("commandId", fixture.commandId())
                    .containsEntry("stagedPlanCleanupPersisted", false);
            assertThat(fixture.durablePlans.current(fixture.adapter.identity()).orElseThrow()
                    .claimedBy(fixture.execution.requestId())).isTrue();
        }

        fixture.cleanupAvailable.set(true);
        assertThat(fixture.replay().output()).containsEntry("stagedPlanCleanupPersisted", true);
        assertThat(fixture.durablePlans.current(fixture.adapter.identity())).isEmpty();
        assertThat(fixture.cleanupAttempts).hasValue(4);
        verify(fixture.adapter, times(1)).executeStagedTask(fixture.planToken);
    }

    @Test
    void retriesCleanupThroughNewStagingAfterAnUnavailableReplayWasCached() {
        Fixture fixture = new Fixture();
        fixture.acceptWhileCleanupFails();
        HarnessGateway sameGateway = gateway(fixture.plugin());
        assertThat(sameGateway.execute(TOKEN, OptimusHarnessPlugin.ID, fixture.execution).output())
                .containsEntry("stagedPlanCleanupPersisted", false);
        fixture.simulator.advanceActiveTask(100, "Complete");
        HarnessCommand nextStage = fixture.stageCommand("next-stage");

        assertThatThrownBy(() -> sameGateway.execute(TOKEN, OptimusHarnessPlugin.ID, nextStage))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cleanup is unavailable");
        fixture.cleanupAvailable.set(true);
        assertThat(sameGateway.execute(TOKEN, OptimusHarnessPlugin.ID, fixture.execution).output())
                .containsEntry("stagedPlanCleanupPersisted", false);

        HarnessResponse next = sameGateway.execute(TOKEN, OptimusHarnessPlugin.ID, nextStage);
        String nextToken = (String) next.output().get("planToken");
        assertThat(fixture.durablePlans.current(fixture.adapter.identity()).orElseThrow()
                .matchesToken(nextToken)).isTrue();
        assertThat(sameGateway.execute(TOKEN, OptimusHarnessPlugin.ID,
                new HarnessCommand("read-completed", "get-operation",
                        Map.of("command-id", fixture.commandId()))).output())
                .containsEntry("status", "SUCCEEDED");
        assertThat(fixture.cleanupAttempts).hasValue(4);
        verify(fixture.adapter, times(1)).executeStagedTask(fixture.planToken);
    }

    @Test
    void retainsCompletedAdapterStateWhenItsJournalAppendFailsBeforeNewStaging() {
        Fixture fixture = new Fixture();
        fixture.plugin().execute(fixture.execution);
        fixture.simulator.advanceActiveTask(100, "Complete");
        doThrow(new IllegalStateException("Operation journal is unavailable")).when(fixture.journal)
                .recordState(anyString(), any(RobotIdentity.class), any(OptimusRobotAdapter.RobotState.class));

        assertThatThrownBy(() -> fixture.plugin().execute(fixture.stageCommand("next-stage")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("journal is unavailable");

        assertThat(fixture.simulator.observe().taskCommandId()).isEqualTo(fixture.commandId());
        assertThat(fixture.simulator.observe().taskStatus()).isEqualTo(OptimusRobotAdapter.TaskStatus.SUCCEEDED);
        verify(fixture.adapter, times(1)).stage(any(TaskDraft.class));
        doAnswer(delegatesTo(fixture.durableJournal)).when(fixture.journal)
                .recordState(anyString(), any(RobotIdentity.class), any(OptimusRobotAdapter.RobotState.class));
        fixture.plugin().execute(fixture.stageCommand("next-stage"));
        assertThat(fixture.journal.latest(OptimusHarnessPlugin.ID, fixture.commandId()).orElseThrow().status())
                .isEqualTo(OptimusRobotAdapter.TaskStatus.SUCCEEDED);
        verify(fixture.adapter, times(2)).stage(any(TaskDraft.class));
        verify(fixture.adapter, times(1)).executeStagedTask(fixture.planToken);
    }

    @Test
    void preservesCompletionBeforeSafePoseAndDefersDispatchDuringJournalFailure() {
        Fixture fixture = new Fixture();
        fixture.plugin().execute(fixture.execution);
        fixture.simulator.advanceActiveTask(100, "Complete");
        HarnessCommand safePose = new HarnessCommand("safe-pose", "return-safe-pose",
                Map.of("lease-token", fixture.leaseToken), Instant.now().plusSeconds(30));
        doThrow(new IllegalStateException("Operation journal is unavailable")).when(fixture.journal)
                .recordState(anyString(), any(RobotIdentity.class), any(OptimusRobotAdapter.RobotState.class));

        assertThatThrownBy(() -> fixture.plugin().execute(safePose))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("journal is unavailable");
        assertThat(fixture.simulator.observe().taskCommandId()).isEqualTo(fixture.commandId());
        verify(fixture.adapter, never()).returnToSafePose();

        doAnswer(delegatesTo(fixture.durableJournal)).when(fixture.journal)
                .recordState(anyString(), any(RobotIdentity.class), any(OptimusRobotAdapter.RobotState.class));
        fixture.plugin().execute(safePose);
        assertThat(fixture.journal.latest(OptimusHarnessPlugin.ID, fixture.commandId()).orElseThrow().status())
                .isEqualTo(OptimusRobotAdapter.TaskStatus.SUCCEEDED);
        verify(fixture.adapter, times(1)).returnToSafePose();
    }

    @Test
    void recoversAcceptedClaimThroughTheOtherLifecycleCleanupActions() {
        for (String action : List.of("release-control-lease", "cancel-staged-task", "return-safe-pose")) {
            Fixture fixture = new Fixture();
            fixture.acceptWhileCleanupFails();
            fixture.cleanupAvailable.set(true);
            fixture.simulator.advanceActiveTask(100, "Complete");

            fixture.plugin().execute(new HarnessCommand("cleanup-" + action, action,
                    Map.of("lease-token", fixture.leaseToken), Instant.now().plusSeconds(30)));

            assertThat(fixture.durablePlans.current(fixture.adapter.identity())).isEmpty();
            if (action.equals("release-control-lease")) {
                assertThat(fixture.leases.status(fixture.adapter.identity()).active()).isFalse();
            } else if (action.equals("return-safe-pose")) {
                assertThat(fixture.journal.latest(OptimusHarnessPlugin.ID, fixture.commandId()).orElseThrow().status())
                        .isEqualTo(OptimusRobotAdapter.TaskStatus.SUCCEEDED);
            }
            verify(fixture.adapter, never()).cancelStagedTask();
            verify(fixture.adapter, times(1)).executeStagedTask(fixture.planToken);
        }
    }

    @Test
    void doesNotReportCleanupAsPersistedWhenTheStoreKeepsTheMatchingClaim() {
        Fixture fixture = new Fixture();
        fixture.acceptWhileCleanupFails();
        fixture.cleanupAvailable.set(true);
        doNothing().when(fixture.plans).clearExecution(any(RobotIdentity.class), anyString(), anyString());

        HarnessResponse replay = fixture.replay();

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output()).containsEntry("stagedPlanCleanupPersisted", false);
        assertThat(fixture.durablePlans.current(fixture.adapter.identity()).orElseThrow()
                .claimedBy(fixture.execution.requestId())).isTrue();
        verify(fixture.adapter, times(1)).executeStagedTask(fixture.planToken);
    }

    @Test
    void preservesANewerReviewedPlanWhenReplayingAnEarlierExecution() {
        Fixture fixture = new Fixture();
        fixture.plugin().execute(fixture.execution);
        fixture.simulator.advanceActiveTask(100, "Complete");
        String nextToken = (String) fixture.plugin().execute(fixture.stageCommand("next-stage"))
                .output().get("planToken");
        StoredPlan newer = fixture.durablePlans.current(fixture.adapter.identity()).orElseThrow();

        assertThat(fixture.replay().replayed()).isTrue();

        assertThat(fixture.durablePlans.current(fixture.adapter.identity())).contains(newer);
        assertThat(newer.matchesToken(nextToken)).isTrue();
        verify(fixture.adapter, never()).cancelStagedTask();
        verify(fixture.adapter, times(1)).executeStagedTask(fixture.planToken);
    }

    @Test
    void preservesClaimsWithADifferentRequestOrPlanToken() {
        for (boolean replaceToken : List.of(false, true)) {
            Fixture fixture = new Fixture();
            fixture.acceptWhileCleanupFails();
            fixture.cleanupAvailable.set(true);
            StoredPlan original = fixture.durablePlans.current(fixture.adapter.identity()).orElseThrow();
            String replacementToken = replaceToken ? "a-new-reviewed-token" : fixture.planToken;
            String replacementRequest = replaceToken ? fixture.execution.requestId() : "another-execution";
            fixture.durablePlans.replace(fixture.adapter.identity(), original.leaseId(), new TaskPlan(
                    replacementToken, DRAFT, original.stagedAt(), original.expiresAt()));
            StoredPlan replacement = fixture.durablePlans.claimExecution(fixture.adapter.identity(),
                    original.leaseId(), replacementToken, replacementRequest, Instant.now());

            assertThat(fixture.replay().replayed()).isTrue();

            assertThat(fixture.durablePlans.current(fixture.adapter.identity())).contains(replacement);
            fixture.simulator.advanceActiveTask(100, "Complete");
            assertThatThrownBy(() -> fixture.plugin().execute(fixture.stageCommand("next-stage")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("pending recovery");
            assertThat(fixture.durablePlans.current(fixture.adapter.identity())).contains(replacement);
            verify(fixture.adapter, times(1)).executeStagedTask(fixture.planToken);
        }
    }

    @Test
    void rejectsAnAcceptedReceiptForAnotherRobotWithoutClearingItsClaim() {
        Fixture fixture = new Fixture();
        fixture.acceptWhileCleanupFails();
        fixture.cleanupAvailable.set(true);
        StoredPlan pending = fixture.durablePlans.current(fixture.adapter.identity()).orElseThrow();
        OptimusRobotAdapter other = mock(OptimusRobotAdapter.class);
        when(other.identity()).thenReturn(new RobotIdentity(
                AdapterMode.SIMULATOR, "another-robot", "Other simulator", "test", "safe-home"));
        OptimusHarnessPlugin restarted = new OptimusHarnessPlugin(other, Clock.systemUTC(), "",
                fixture.journal, fixture.leases, fixture.plans);

        assertThatThrownBy(() -> gateway(restarted).execute(TOKEN, OptimusHarnessPlugin.ID, fixture.execution))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("different robot identity");

        assertThat(fixture.durablePlans.current(fixture.adapter.identity())).contains(pending);
        assertThat(fixture.cleanupAttempts).hasValue(1);
        verify(other, never()).executeStagedTask(anyString());
    }

    private static HarnessGateway gateway(OptimusHarnessPlugin plugin) {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowDrafts(true);
        properties.setAllowMutations(true);
        properties.validateConfiguration();
        return new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties);
    }

    private static final class Fixture {
        private final SimulatedOptimusRobotAdapter simulator = new SimulatedOptimusRobotAdapter();
        private final OptimusRobotAdapter adapter = mock(OptimusRobotAdapter.class, delegatesTo(simulator));
        private final TransientOptimusOperationJournal durableJournal = new TransientOptimusOperationJournal();
        private final OptimusOperationJournal journal = mock(OptimusOperationJournal.class, delegatesTo(durableJournal));
        private final TransientOptimusControlLeaseService leases =
                new TransientOptimusControlLeaseService(Clock.systemUTC());
        private final TransientOptimusStagedPlanStore durablePlans = new TransientOptimusStagedPlanStore();
        private final AtomicBoolean cleanupAvailable = new AtomicBoolean(true);
        private final AtomicInteger cleanupAttempts = new AtomicInteger();
        private final OptimusStagedPlanStore plans = mock(OptimusStagedPlanStore.class, invocation -> {
            if (invocation.getMethod().getName().startsWith("clear")) {
                cleanupAttempts.incrementAndGet();
                if (!cleanupAvailable.get()) {
                    throw new IllegalStateException("Staged-plan cleanup is unavailable");
                }
            }
            return delegatesTo(durablePlans).answer(invocation);
        });
        private final String leaseToken;
        private final String planToken;
        private final HarnessCommand execution;

        private Fixture() {
            OptimusHarnessPlugin plugin = plugin();
            leaseToken = (String) plugin.execute(new HarnessCommand("acquire", "acquire-control-lease",
                    Map.of("holder", "cleanup-test", "duration-seconds", 300))).output().get("leaseToken");
            planToken = (String) plugin.execute(stageCommand("stage")).output().get("planToken");
            execution = new HarnessCommand("execute", "execute-staged-task",
                    Map.of("lease-token", leaseToken, "plan-token", planToken), Instant.now().plusSeconds(30));
        }

        private void acceptWhileCleanupFails() {
            cleanupAvailable.set(false);
            assertThatThrownBy(() -> plugin().execute(execution)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("cleanup is unavailable");
            assertThat(journal.latestByRequest(OptimusHarnessPlugin.ID, execution.requestId())).isPresent();
            assertThat(durablePlans.current(adapter.identity()).orElseThrow().executionPending()).isTrue();
        }

        private OptimusHarnessPlugin plugin() {
            return new OptimusHarnessPlugin(adapter, Clock.systemUTC(), "", journal, leases, plans);
        }

        private HarnessResponse replay() {
            return gateway(plugin()).execute(TOKEN, OptimusHarnessPlugin.ID, execution);
        }

        private String commandId() {
            return journal.latestByRequest(OptimusHarnessPlugin.ID, execution.requestId()).orElseThrow().operationId();
        }

        private HarnessCommand stageCommand(String requestId) {
            return new HarnessCommand(requestId, "stage-task", Map.of("task", DRAFT.task(),
                    "work-area", DRAFT.workArea(), "max-duration-seconds", DRAFT.maxDurationSeconds(),
                    "lease-token", leaseToken));
        }
    }
}
