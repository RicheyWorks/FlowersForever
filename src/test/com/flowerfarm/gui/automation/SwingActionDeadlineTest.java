package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.SwingUtilities;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingActionDeadlineTest {
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    void refusesWorkThatExpiresInTheEventQueueAndRetainsItsReceipt(long nanosAfterExpiry)
            throws Exception {
        Clock clock = mock(Clock.class);
        AtomicReference<Instant> time = new AtomicReference<>(NOW);
        when(clock.instant()).thenAnswer(ignored -> time.get());
        SwingActionJournal journal = new SwingActionJournal(0, clock);
        AtomicInteger saves = new AtomicInteger();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HarnessCommand command = command("queued-save", NOW.plusSeconds(1));
        SwingUtilities.invokeLater(() -> {
            blocked.countDown();
            awaitRelease(release);
        });
        try {
            assertThat(blocked.await(2, TimeUnit.SECONDS)).isTrue();
            HarnessExecution accepted = journal.dispatch(command, () -> save(saves));
            assertThat(accepted.output()).containsEntry("status", "PENDING");
            assertThat(journal.pendingIds()).containsExactly(command.requestId());
            time.set(command.expiresAt().plusNanos(nanosAfterExpiry));
            release.countDown();
            drainEdt();

            assertThat(saves).hasValue(0);
            assertThat(journal.status(command.requestId()).success()).isFalse();
            assertThat(journal.status(command.requestId()).output())
                    .containsEntry("status", "FAILED").containsEntry("completed", true)
                    .containsEntry("failureType", "DesktopCommandExpiredException");
            assertThat(journal.status(command.requestId()).toString()).doesNotContain("Save private order");
            assertThat(journal.pendingIds()).isEmpty();
            assertThat(journal.replay(command)).contains(accepted);
            assertThat(journal.dispatch(command, () -> save(saves))).isEqualTo(accepted);
            assertThatThrownBy(() -> journal.dispatch(
                    command(command.requestId(), NOW.plusSeconds(60)), () -> save(saves)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different desktop command");

            HarnessCommand replacement = command("reviewed-new-save", time.get().plusSeconds(1));
            journal.dispatch(replacement, () -> save(saves));
            drainEdt();
            assertThat(journal.status(replacement.requestId()).output()).containsEntry("status", "COMPLETED");
            assertThat(saves).hasValue(1);
        } finally {
            release.countDown();
            drainEdt();
        }
    }

    @Test
    void startsBeforeDeadlineAndReplaysItsCompletedWorkAfterExpiry() throws Exception {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        SwingActionJournal journal = new SwingActionJournal(0, clock);
        AtomicInteger saves = new AtomicInteger();
        HarnessCommand command = command("just-in-time", NOW.plusNanos(1));
        HarnessExecution accepted = journal.dispatch(command, () -> {
            // Once the callback has started, expiry must not turn a committed write into a retry.
            when(clock.instant()).thenReturn(NOW.plusSeconds(2));
            return save(saves);
        });
        drainEdt();

        assertThat(journal.status(command.requestId()).output()).containsEntry("status", "COMPLETED");
        assertThat(journal.replay(command)).contains(accepted);
        assertThat(journal.dispatch(command, () -> save(saves))).isEqualTo(accepted);
        assertThat(saves).hasValue(1);
    }

    @Test
    void commandsWithoutDeadlineStillExecute() throws Exception {
        SwingActionJournal journal = new SwingActionJournal(0, Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        AtomicInteger saves = new AtomicInteger();
        HarnessCommand command = command("optional-deadline", null);
        journal.dispatch(command, () -> save(saves));
        drainEdt();

        assertThat(journal.status(command.requestId()).output()).containsEntry("status", "COMPLETED");
        assertThat(saves).hasValue(1);
    }

    private static HarnessCommand command(String id, Instant expiresAt) {
        return new HarnessCommand(id, "activate-button", Map.of("selector", "Save private order"), expiresAt);
    }

    private static HarnessExecution save(AtomicInteger saves) {
        saves.incrementAndGet();
        return HarnessExecution.success("Saved");
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release the event queue");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void drainEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
