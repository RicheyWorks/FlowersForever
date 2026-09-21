package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SwingActionJournalTest {

    @Test
    void concurrentDuplicateFreezesTheFirstPublishedReceiptWhileStatusStaysCurrent() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // Longer than the test deadline, so the initial dispatch cannot time out
            // before the duplicate publishes its receipt, regardless of scheduling.
            SwingActionJournal journal = new SwingActionJournal(10_000);
            HarnessCommand command = new HarnessCommand("shared-action", "activate-button",
                    Map.of("selector", "Save"));
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger activations = new AtomicInteger();
            var dispatcher = Executors.newSingleThreadExecutor();
            try {
                var original = dispatcher.submit(() -> journal.dispatch(command, () -> {
                    activations.incrementAndGet();
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return HarnessExecution.success("Saved");
                }));
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

                HarnessExecution duplicate = journal.dispatch(command, () -> {
                    activations.incrementAndGet();
                    return HarnessExecution.success("Unexpected duplicate save");
                });
                assertThat(duplicate.output()).containsEntry("status", "PENDING");
                assertThat(journal.replay(command)).contains(duplicate);

                release.countDown();
                assertThat(original.get(2, TimeUnit.SECONDS)).isEqualTo(duplicate);
                assertThat(journal.replay(command)).contains(duplicate);
                assertThat(journal.status(command.requestId()).output())
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("completed", true);
                assertThat(activations).hasValue(1);
            } finally {
                release.countDown();
                dispatcher.shutdownNow();
                SwingUtilities.invokeAndWait(() -> { });
            }
        });
    }
}
