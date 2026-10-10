package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SwingActionJournalBoundaryTest {
    @Test
    void saturationRejectsOnlyNewWorkAndTheRejectedIdCanRunAfterCapacityReturns() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            SwingActionJournal journal = new SwingActionJournal(0);
            CountDownLatch blocked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            List<HarnessCommand> commands = new ArrayList<>();
            List<HarnessExecution> receipts = new ArrayList<>();
            SwingUtilities.invokeLater(() -> {
                blocked.countDown();
                awaitRelease(release);
            });
            try {
                assertThat(blocked.await(2, TimeUnit.SECONDS)).isTrue();
                for (int index = 0; index < 16; index++) {
                    HarnessCommand command = command("pending-" + index);
                    commands.add(command);
                    receipts.add(journal.dispatch(command, () -> completed(calls)));
                }
                assertThat(journal.pendingIds()).containsExactlyElementsOf(
                        commands.stream().map(HarnessCommand::requestId).toList());
                assertThat(receipts).allSatisfy(receipt -> assertThat(receipt.output())
                        .containsEntry("accepted", true).containsEntry("status", "PENDING"));

                HarnessCommand overflow = command("overflow");
                assertThatThrownBy(() -> journal.dispatch(overflow, () -> completed(calls)))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("Too many pending");
                assertThat(journal.replay(overflow)).isEmpty();
                assertThatThrownBy(() -> journal.status(overflow.requestId()))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No retained");
                assertThat(journal.dispatch(commands.get(0), () -> completed(calls)))
                        .isEqualTo(receipts.get(0));
                assertThatThrownBy(() -> journal.dispatch(new HarnessCommand(commands.get(0).requestId(),
                        "activate-button", Map.of("selector", "Different button")), () -> completed(calls)))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different desktop command");
                assertThat(calls).hasValue(0);

                release.countDown();
                drainEdt();
                assertThat(calls).hasValue(16);
                assertThat(journal.pendingIds()).isEmpty();
                for (int index = 0; index < commands.size(); index++) {
                    HarnessCommand command = commands.get(index);
                    assertThat(journal.status(command.requestId()).output()).containsEntry("status", "COMPLETED");
                    assertThat(journal.replay(command)).contains(receipts.get(index));
                }
                journal.dispatch(overflow, () -> completed(calls));
                drainEdt();
                assertThat(journal.status(overflow.requestId()).output()).containsEntry("status", "COMPLETED");
                assertThat(calls).hasValue(17);
            } finally {
                release.countDown();
                drainEdt();
            }
        });
    }

    @Test
    void receiptEvictionPreservesAnOlderPendingModalWhileRemovingCompletedReceipts() {
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
            SwingActionJournal journal = new SwingActionJournal(0);
            SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(1);
            AtomicInteger modalCalls = new AtomicInteger();
            AtomicInteger completedCalls = new AtomicInteger();
            HarnessCommand pending = command("oldest-pending-modal");
            try {
                HarnessExecution receipt = journal.dispatch(pending, () -> {
                    modalCalls.incrementAndGet();
                    // This event runs only once the secondary loop is accepting EDT work.
                    SwingUtilities.invokeLater(entered::countDown);
                    try {
                        loop.enter();
                        return HarnessExecution.success("Modal closed");
                    } finally {
                        finished.countDown();
                    }
                });
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(receipt.output()).containsEntry("status", "PENDING");
                for (int index = 0; index < 256; index++) {
                    // Keep at most 15 additional callbacks queued beside the open modal.
                    if (index > 0 && index % 15 == 0) {
                        drainEdt();
                    }
                    journal.dispatch(command("completed-" + index), () -> completed(completedCalls));
                }
                drainEdt();
                assertThat(completedCalls).hasValue(256);
                assertThat(journal.pendingIds()).containsExactly(pending.requestId());
                assertThat(journal.status(pending.requestId()).output()).containsEntry("status", "PENDING");
                assertThat(journal.replay(pending)).contains(receipt);
                assertThat(journal.dispatch(pending, () -> completed(modalCalls))).isEqualTo(receipt);
                assertThat(modalCalls).hasValue(1);
                assertThat(journal.replay(command("completed-0"))).isEmpty();
                assertThatThrownBy(() -> journal.status("completed-0"))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No retained");
                for (int index = 1; index < 256; index++) {
                    assertThat(journal.status("completed-" + index).output()).containsEntry("status", "COMPLETED");
                }

                SwingUtilities.invokeLater(loop::exit);
                assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
                drainEdt();
                assertThat(journal.pendingIds()).isEmpty();
                assertThat(journal.status(pending.requestId()).output()).containsEntry("status", "COMPLETED");
                assertThat(journal.replay(pending)).contains(receipt);
                journal.dispatch(command("after-modal"), () -> completed(completedCalls));
                drainEdt();
                assertThat(journal.replay(pending)).isEmpty();
                assertThat(journal.status("after-modal").output()).containsEntry("status", "COMPLETED");
                assertThat(completedCalls).hasValue(257);
                assertThat(modalCalls).hasValue(1);
            } finally {
                SwingUtilities.invokeLater(loop::exit);
                assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
                drainEdt();
            }
        });
    }

    @Test
    void interruptedDispatchReturnsItsAcceptedReceiptAndEveryRetryKeepsOneCallback() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            SwingActionJournal journal = new SwingActionJournal(10_000);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            AtomicReference<Thread> caller = new AtomicReference<>();
            AtomicBoolean interruptedOnReturn = new AtomicBoolean();
            HarnessCommand command = command("interrupted-dispatch");
            var dispatcher = Executors.newSingleThreadExecutor();
            try {
                var original = dispatcher.submit(() -> {
                    caller.set(Thread.currentThread());
                    HarnessExecution receipt = journal.dispatch(command, () -> {
                        calls.incrementAndGet();
                        started.countDown();
                        awaitRelease(release);
                        return HarnessExecution.success("Saved once");
                    });
                    interruptedOnReturn.set(Thread.currentThread().isInterrupted());
                    return receipt;
                });
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                caller.get().interrupt();
                HarnessExecution receipt = original.get(2, TimeUnit.SECONDS);
                assertThat(interruptedOnReturn).isTrue();
                assertThat(receipt.success()).isTrue();
                assertThat(receipt.output()).containsEntry("accepted", true)
                        .containsEntry("status", "PENDING").containsEntry("completed", false);
                assertThat(journal.pendingIds()).containsExactly(command.requestId());
                assertThat(journal.dispatch(command, () -> completed(calls))).isEqualTo(receipt);
                assertThat(journal.replay(command)).contains(receipt);
                assertThat(calls).hasValue(1);

                release.countDown();
                drainEdt();
                assertThat(journal.status(command.requestId()).output())
                        .containsEntry("status", "COMPLETED").containsEntry("completed", true);
                assertThat(journal.dispatch(command, () -> completed(calls))).isEqualTo(receipt);
                assertThat(journal.replay(command)).contains(receipt);
                assertThat(calls).hasValue(1);
            } finally {
                release.countDown();
                dispatcher.shutdownNow();
                drainEdt();
            }
        });
    }

    private static HarnessCommand command(String id) {
        return new HarnessCommand(id, "activate-button", Map.of("selector", "Save"));
    }

    private static HarnessExecution completed(AtomicInteger calls) {
        calls.incrementAndGet();
        return HarnessExecution.success("Completed");
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test did not release its EDT barrier");
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
