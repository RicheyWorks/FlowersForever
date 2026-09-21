package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessSnapshot;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SwingSnapshotConsistencyTest {
    @Test
    void returnedControlOrderAndContextKeepTheSameTargetsWhenCaptureChangesTheirAliases() throws Exception {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        AtomicReference<Runnable> duringCapture = new AtomicReference<>();
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        try {
            onEdt(() -> {
                JPanel root = captureCallbackPanel(duringCapture);
                JButton first = button("Approve", "approve-action", firstCalls);
                JButton second = button("Reject", "reject-action", secondCalls);
                root.add(first);
                root.add(second);
                bridge.attach(root, title -> false);
                duringCapture.set(() -> {
                    first.getAccessibleContext().setAccessibleName("reject-action");
                    second.getAccessibleContext().setAccessibleName("approve-action");
                });
                return null;
            });
            HarnessSnapshot observed = plugin.observe();
            if (!Boolean.TRUE.equals(observed.metadata().get("ready"))) {
                assertThat(observed.metadata()).doesNotContainKey("contextToken");
                observed = plugin.observe();
            }
            assertThat(observed.metadata()).containsEntry("ready", true);
            assertThat(observed.controls()).extracting(control -> control.selector())
                    .containsExactly("reject-action", "approve-action");
            assertThat(observed.controls()).allMatch(control -> control.commandable());

            plugin.execute(new HarnessCommand("first-observed-button", "activate-button", Map.of(
                    "selector", observed.controls().get(0).selector(),
                    "expected-context", observed.metadata().get("contextToken"))));
            onEdt(() -> null);
            assertThat(bridge.actionStatus("first-observed-button").output())
                    .containsEntry("status", "COMPLETED");
            assertThat(firstCalls).hasValue(1);
            assertThat(secondCalls).hasValue(0);
        } finally {
            bridge.detach();
        }
    }

    @Test
    void doesNotPublishAReadySnapshotForAnAttachmentReplacedDuringContextCapture() throws Exception {
        AtomicReference<Runnable> duringCapture = new AtomicReference<>();
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        try {
            onEdt(() -> {
                JPanel original = captureCallbackPanel(duringCapture);
                original.add(button("Original", "original-action", new AtomicInteger()));
                JPanel replacement = new JPanel();
                replacement.add(button("Replacement", "replacement-action", new AtomicInteger()));
                bridge.attach(original, title -> false);
                duringCapture.set(() -> bridge.attach(replacement, title -> false));
                return null;
            });
            HarnessSnapshot changed = plugin.observe();
            assertThat(changed.metadata()).containsEntry("ready", false).doesNotContainKey("contextToken");
            assertThat(changed.controls()).isEmpty();
            HarnessSnapshot recovered = plugin.observe();
            assertThat(recovered.metadata()).containsEntry("ready", true).containsKey("contextToken");
            assertThat(recovered.controls()).extracting(control -> control.selector())
                    .containsExactly("replacement-action");
        } finally {
            bridge.detach();
        }
    }

    private static JPanel captureCallbackPanel(AtomicReference<Runnable> callback) {
        // This intentionally models a custom getter with a synchronous callback;
        // it is an invariant probe, not a claim about a production panel getter.
        return new JPanel() {
            @Override public TransferHandler getTransferHandler() {
                Runnable action = callback.getAndSet(null);
                if (action != null) {
                    action.run();
                }
                return super.getTransferHandler();
            }
        };
    }

    private static JButton button(String title, String selector, AtomicInteger calls) {
        JButton button = new JButton(title);
        button.getAccessibleContext().setAccessibleName(selector);
        button.addActionListener(event -> calls.incrementAndGet());
        return button;
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        FutureTask<T> result = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(result);
        return result.get();
    }
}
