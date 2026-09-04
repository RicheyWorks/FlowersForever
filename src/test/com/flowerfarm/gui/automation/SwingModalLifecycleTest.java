package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessResponse;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.SecondaryLoop;
import java.awt.Dialog;
import java.awt.Toolkit;
import java.awt.Window;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingModalLifecycleTest {
    private static final String TOKEN = "modal-lifecycle-test-token-123456";

    @Test
    void resolvesOnlyVisibleOwnedModalsAndPrefersTheirNestedDialog() {
        Window owner = mock(Window.class);
        Dialog hidden = mock(Dialog.class);
        Dialog modal = mock(Dialog.class);
        Dialog nested = mock(Dialog.class);
        Window utility = mock(Window.class);
        when(owner.getOwnedWindows()).thenReturn(new Window[]{hidden, utility, modal});
        when(utility.isShowing()).thenReturn(true);
        when(utility.getOwnedWindows()).thenReturn(new Window[0]);
        when(modal.isShowing()).thenReturn(true);
        when(modal.isModal()).thenReturn(true);
        when(modal.getOwnedWindows()).thenReturn(new Window[]{nested});
        when(nested.isShowing()).thenReturn(true);
        when(nested.isModal()).thenReturn(true);
        when(nested.getOwnedWindows()).thenReturn(new Window[0]);
        assertThat(SwingHarnessBridge.activeOwnedModal(owner)).isSameAs(nested);
        when(nested.isShowing()).thenReturn(false);
        assertThat(SwingHarnessBridge.activeOwnedModal(owner)).isSameAs(modal);
        when(modal.isShowing()).thenReturn(false);
        assertThat(SwingHarnessBridge.activeOwnedModal(owner)).isNull();
    }

    @Test
    void returnsPendingAndLetsAnotherCommandResolveTheModalWithoutReplayingTheAction() {
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> {
            AtomicReference<JComponent> activeModal = new AtomicReference<>();
            SwingHarnessBridge bridge = new SwingHarnessBridge(
                    root -> activeModal.get() == null ? root : activeModal.get());
            SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
            HarnessGateway gateway = gateway(plugin);
            JPanel root = new JPanel();
            JPanel modal = new JPanel();
            AtomicInteger opens = new AtomicInteger();
            SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
            CountDownLatch completed = new CountDownLatch(1);
            SwingUtilities.invokeAndWait(() -> {
                JButton open = new JButton("Open confirmation");
                open.addActionListener(event -> {
                    opens.incrementAndGet();
                    activeModal.set(modal);
                    try {
                        loop.enter();
                    } finally {
                        activeModal.set(null);
                        completed.countDown();
                    }
                });
                JButton close = new JButton("Cancel confirmation");
                close.addActionListener(event -> loop.exit());
                root.add(open);
                modal.add(close);
                bridge.attach(root, title -> true);
            });
            HarnessCommand open = command("open", "activate-button", "selector", "Open confirmation");
            try {
                HarnessResponse accepted = gateway.execute(TOKEN, SwingHarnessPlugin.ID, open);
                assertThat(accepted.success()).isTrue();
                assertThat(accepted.output()).containsEntry("accepted", true)
                        .containsEntry("completed", false).containsEntry("status", "PENDING");
                assertThat(accepted.snapshot().metadata()).containsEntry("modalActive", true);
                assertThat(accepted.snapshot().controls()).extracting(control -> control.selector())
                        .containsExactly("Cancel confirmation");
                assertThatThrownBy(() -> bridge.navigate("Reports"))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dialog");
                assertThatThrownBy(() -> bridge.activateButton("Open confirmation"))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 0");

                HarnessResponse replay = gateway.execute(TOKEN, SwingHarnessPlugin.ID, open);
                assertThat(replay.replayed()).isTrue();
                assertThat(replay.output()).containsEntry("status", "PENDING");
                HarnessResponse close = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                        command("close", "activate-button", "selector", "Cancel confirmation"));
                assertThat(close.output()).containsEntry("status", "COMPLETED");
                assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
                SwingUtilities.invokeAndWait(() -> { });
                HarnessResponse status = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                        command("status", "action-status", "request-id", "open"));
                assertThat(status.output()).containsEntry("status", "COMPLETED")
                        .containsEntry("completed", true);
                assertThat(status.snapshot().metadata()).containsEntry("modalActive", false);
                assertThat(opens).hasValue(1);
                assertThat(plugin.replay(open)).get().extracting(result -> result.output())
                        .isEqualTo(accepted.output());
            } finally {
                loop.exit();
            }
        });
    }

    @Test
    void reportsPostDispatchFailuresWithoutExposingPrivateExceptionMessages() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            JPanel root = new JPanel();
            JButton fail = new JButton("Fail action");
            SwingUtilities.invokeAndWait(() -> {
                root.add(fail);
                fail.addActionListener(event -> {
                    throw new IllegalStateException("Private customer note must not leak");
                });
            });
            SwingHarnessBridge bridge = new SwingHarnessBridge();
            bridge.attach(root, title -> false);
            HarnessGateway gateway = gateway(new SwingHarnessPlugin(bridge));
            gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                    command("failure", "activate-button", "selector", "Fail action"));
            SwingUtilities.invokeAndWait(() -> { });
            HarnessResponse status = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                    command("failure-status", "action-status", "request-id", "failure"));
            assertThat(status.success()).isFalse();
            assertThat(status.output()).containsEntry("status", "FAILED")
                    .containsEntry("failureType", "IllegalStateException");
            assertThat(status.toString()).doesNotContain("Private customer note");
        });
    }

    @Test
    void actionStatusDoesNotExposeOriginalSelectorsValuesOrMessages() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            JComboBox<String> choices = new JComboBox<>(new String[]{"None", "Private customer option"});
            SwingUtilities.invokeAndWait(() -> choices.getAccessibleContext()
                    .setAccessibleName("Private customer selector"));
            SwingHarnessBridge bridge = new SwingHarnessBridge();
            bridge.attach(choices, title -> false);
            SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
            HarnessCommand choose = new HarnessCommand("private-option", "choose-option",
                    Map.of("selector", "Private customer selector", "value", "Private customer option"));
            plugin.execute(choose);
            SwingUtilities.invokeAndWait(() -> { });
            var status = plugin.execute(command("private-status", "action-status", "request-id", "private-option"));
            assertThat(status.output()).containsEntry("status", "COMPLETED")
                    .containsOnlyKeys("actionRequestId", "status", "accepted", "completed");
            assertThat(status.toString()).doesNotContain("Private customer");
            assertThat(plugin.replay(choose)).isPresent();
        });
    }

    @Test
    void keepsTheGatewayResponsiveWhenTheEventThreadIsBlocked() {
        assertTimeoutPreemptively(Duration.ofSeconds(7), () -> {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch started = new CountDownLatch(1);
            JPanel root = new JPanel();
            JButton block = new JButton("Slow action");
            SwingUtilities.invokeAndWait(() -> {
                root.add(block);
                block.addActionListener(event -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            });
            SwingHarnessBridge bridge = new SwingHarnessBridge();
            bridge.attach(root, title -> false);
            HarnessGateway gateway = gateway(new SwingHarnessPlugin(bridge));
            try {
                HarnessResponse accepted = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                        command("blocked", "activate-button", "selector", "Slow action"));
                assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(accepted.output()).containsEntry("status", "PENDING");
                assertThat(accepted.snapshot().metadata()).containsEntry("ready", false)
                        .containsEntry("reason", "Desktop event loop is busy");
                HarnessResponse status = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                        command("blocked-status", "action-status", "request-id", "blocked"));
                assertThat(status.output()).containsEntry("status", "PENDING");
                HarnessResponse navigation = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                        command("blocked-navigation", "navigate", "tab", "Reports"));
                assertThat(navigation.output()).containsEntry("status", "PENDING");
            } finally {
                release.countDown();
                SwingUtilities.invokeAndWait(() -> { });
            }
        });
    }

    private static HarnessCommand command(String id, String action, String key, String value) {
        return new HarnessCommand(id, action, Map.of(key, value));
    }

    private static HarnessGateway gateway(SwingHarnessPlugin plugin) {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        return new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties);
    }
}
