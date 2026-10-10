package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.HarnessValueType;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingContextBindingTest {
    private static final String TOKEN = "desktop-context-binding-token-123456";
    private static final Set<String> CONTEXT_ACTIONS = Set.of(
            "activate-button", "submit-field", "double-click-row", "activate-menu",
            "invoke-shortcut", "navigate", "navigate-route", "replace-text",
            "choose-option", "select-tab", "select-row", "select-rows", "sort-table",
            "select-list-row", "set-spinner", "set-slider", "choose-file", "cancel-file-dialog",
            "scroll", "set-divider", "set-toggle", "choose-option-index", "clear-selection",
            "read-control", "read-screen", "read-layout", "read-item", "edit-text");

    @Test
    void publishesAnOptionalStringContextForQueuedActionsAndPagedReads() throws Exception {
        JPanel root = onEdt(JPanel::new);
        Fixture fixture = fixture(root);
        assertThat(fixture.plugin().descriptor().actions()).hasSize(30);
        fixture.plugin().descriptor().actions().forEach(action -> {
            var context = action.arguments().stream()
                    .filter(argument -> argument.name().equals("expected-context")).toList();
            if (CONTEXT_ACTIONS.contains(action.name())) {
                assertThat(context).as(action.name()).singleElement()
                        .matches(argument -> argument.type() == HarnessValueType.STRING
                                && !argument.required());
            } else {
                assertThat(context).as(action.name()).isEmpty();
            }
        });
        fixture.gateway().manifest(TOKEN).tools().forEach(tool -> {
            Map<?, ?> properties = (Map<?, ?>) tool.inputSchema().get("properties");
            assertThat(properties.containsKey("expected-context"))
                    .as(tool.action()).isEqualTo(CONTEXT_ACTIONS.contains(tool.action()));
            assertThat((List<?>) tool.inputSchema().get("required"))
                    .noneMatch("expected-context"::equals);
        });
        assertThatThrownBy(() -> fixture.execute(new HarnessCommand(
                "invalid-context", "activate-button",
                Map.of("selector", "Save", "expected-context", 7))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected-context");
    }

    @Test
    void unchangedObservationsAllowABoundActionWithoutExposingFieldValues() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        JPanel root = onEdt(() -> {
            JPanel panel = panelWithSave(saves);
            JTextField draft = new JTextField("private customer draft 91f46a");
            draft.getAccessibleContext().setAccessibleName("Customer draft");
            panel.add(draft);
            return panel;
        });
        Fixture fixture = fixture(root);
        String context = fixture.context();
        assertThat(fixture.context()).isEqualTo(context);

        fixture.executeAndDrain(boundSave("unchanged", context));

        assertThat(saves).hasValue(1);
        assertCompleted(fixture, "unchanged");
        assertThat(fixture.observe().metadata()).containsEntry("fieldValuesIncluded", false);
        assertThat(fixture.observe().toString()).doesNotContain("private customer draft 91f46a");
    }

    @Test
    void rejectsAReplacementWithTheSameSelectorAndRecoversWithANewObservation() throws Exception {
        AtomicInteger originalSaves = new AtomicInteger();
        AtomicInteger replacementSaves = new AtomicInteger();
        JPanel root = onEdt(() -> panelWithSave(originalSaves));
        Fixture fixture = fixture(root);
        String oldContext = fixture.context();
        onEdt(() -> {
            root.removeAll();
            root.add(saveButton(replacementSaves));
            return null;
        });
        HarnessCommand stale = boundSave("replaced-control", oldContext);

        HarnessResponse rejected = fixture.executeAndDrain(stale);
        assertContextRejected(fixture, stale.requestId());
        assertThat(originalSaves).hasValue(0);
        assertThat(replacementSaves).hasValue(0);

        String freshContext = fixture.context();
        assertThat(freshContext).isNotEqualTo(oldContext);
        HarnessResponse replay = newGateway(fixture.plugin()).execute(TOKEN, SwingHarnessPlugin.ID, stale);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output()).isEqualTo(rejected.output());
        assertContextRejected(fixture, stale.requestId());
        assertThat(replacementSaves).hasValue(0);

        fixture.executeAndDrain(boundSave("fresh-control", freshContext));
        assertCompleted(fixture, "fresh-control");
        assertThat(replacementSaves).hasValue(1);
    }

    @Test
    void checksTheContextAfterQueuingImmediatelyBeforeTheCallbackRuns() throws Exception {
        AtomicInteger originalSaves = new AtomicInteger();
        AtomicInteger replacementSaves = new AtomicInteger();
        JPanel root = onEdt(() -> panelWithSave(originalSaves));
        Fixture fixture = fixture(root);
        HarnessCommand command = boundSave("queued-replacement", fixture.context());
        HarnessExecution accepted;
        try (BlockedEdt ignored = new BlockedEdt(() -> {
            root.removeAll();
            root.add(saveButton(replacementSaves));
        })) {
            accepted = fixture.plugin().execute(command);
            assertThat(accepted.output()).containsEntry("status", "PENDING");
        }

        assertContextRejected(fixture, command.requestId());
        assertThat(originalSaves).hasValue(0);
        assertThat(replacementSaves).hasValue(0);
        assertThat(fixture.plugin().replay(command)).contains(accepted);
    }

    @Test
    void detectsRowReorderingAndContentChangesBeforeSelectingByViewIndex() throws Exception {
        JTable table = onEdt(() -> {
            JTable result = new JTable(new DefaultTableModel(
                    new Object[][]{{"Zinnia"}, {"Dahlia"}}, new Object[]{"Crop"}));
            result.getAccessibleContext().setAccessibleName("Harvest rows");
            result.setAutoCreateRowSorter(true);
            return result;
        });
        Fixture fixture = fixture(table);
        String originalContext = fixture.context();
        onEdt(() -> {
            table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
            return null;
        });
        fixture.executeAndDrain(boundRow("reordered-rows", originalContext, 0));
        assertContextRejected(fixture, "reordered-rows");
        onEdt(() -> {
            assertThat(table.getSelectedRows()).isEmpty();
            return null;
        });

        fixture.executeAndDrain(boundRow("fresh-row", fixture.context(), 0));
        assertCompleted(fixture, "fresh-row");
        onEdt(() -> {
            assertThat(table.getValueAt(table.getSelectedRow(), 0)).isEqualTo("Dahlia");
            return null;
        });

        String beforeContentChange = fixture.context();
        onEdt(() -> {
            table.getModel().setValueAt("Tulip", 0, 0);
            return null;
        });
        fixture.executeAndDrain(boundRow("changed-content", beforeContentChange, 1));
        assertContextRejected(fixture, "changed-content");
        onEdt(() -> {
            assertThat(table.getSelectedRows()).containsExactly(0);
            return null;
        });
    }

    @Test
    void detectsAReplacedTableModelEvenWhenTheVisibleRowsAreIdentical() throws Exception {
        JTable table = onEdt(() -> {
            JTable result = new JTable(new DefaultTableModel(
                    new Object[][]{{"Dahlia"}}, new Object[]{"Crop"}));
            result.getAccessibleContext().setAccessibleName("Harvest rows");
            return result;
        });
        Fixture fixture = fixture(table);
        String originalContext = fixture.context();
        onEdt(() -> {
            table.setModel(new DefaultTableModel(new Object[][]{{"Dahlia"}}, new Object[]{"Crop"}));
            return null;
        });

        fixture.executeAndDrain(boundRow("replaced-model", originalContext, 0));

        assertContextRejected(fixture, "replaced-model");
        onEdt(() -> {
            assertThat(table.getSelectedRows()).isEmpty();
            return null;
        });
    }

    @Test
    void rejectsAnObservationFromAReplacedActiveModal() throws Exception {
        AtomicInteger originalSaves = new AtomicInteger();
        AtomicInteger replacementSaves = new AtomicInteger();
        JPanel root = onEdt(JPanel::new);
        JPanel originalModal = onEdt(() -> panelWithSave(originalSaves));
        JPanel replacementModal = onEdt(() -> panelWithSave(replacementSaves));
        AtomicReference<JComponent> active = new AtomicReference<>(originalModal);
        SwingHarnessBridge bridge = new SwingHarnessBridge(ignored -> active.get());
        onEdt(() -> {
            bridge.attach(root, title -> false);
            return null;
        });
        Fixture fixture = fixture(bridge);
        String originalContext = fixture.context();
        assertThat(fixture.observe().metadata()).containsEntry("modalActive", true);
        onEdt(() -> {
            active.set(replacementModal);
            return null;
        });

        fixture.executeAndDrain(boundSave("changed-modal", originalContext));

        assertContextRejected(fixture, "changed-modal");
        assertThat(originalSaves).hasValue(0);
        assertThat(replacementSaves).hasValue(0);
        fixture.executeAndDrain(boundSave("fresh-modal", fixture.context()));
        assertCompleted(fixture, "fresh-modal");
        assertThat(replacementSaves).hasValue(1);
    }

    @Test
    void reattachingTheSameRootInvalidatesAPreviousObservation() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        JPanel root = onEdt(() -> panelWithSave(saves));
        Fixture fixture = fixture(root);
        String originalContext = fixture.context();
        onEdt(() -> {
            fixture.bridge().attach(root, title -> false);
            return null;
        });

        fixture.executeAndDrain(boundSave("reattached-root", originalContext));

        assertContextRejected(fixture, "reattached-root");
        assertThat(saves).hasValue(0);
        fixture.executeAndDrain(boundSave("fresh-attachment", fixture.context()));
        assertThat(saves).hasValue(1);
    }

    @Test
    void attachmentReplacementAfterQueuingCannotRetargetTheAction() throws Exception {
        AtomicInteger originalSaves = new AtomicInteger();
        AtomicInteger replacementSaves = new AtomicInteger();
        Fixture fixture = fixture(onEdt(() -> panelWithSave(originalSaves)));
        JPanel replacement = onEdt(() -> panelWithSave(replacementSaves));
        HarnessCommand command = boundSave("queued-attachment", fixture.context());
        try (BlockedEdt ignored = new BlockedEdt(() -> { })) {
            assertThat(fixture.plugin().execute(command).output()).containsEntry("status", "PENDING");
            // attach has always been callable off the EDT; its identity change must
            // protect an already queued action even before EDT cleanup runs.
            fixture.bridge().attach(replacement, title -> false);
        }

        assertContextRejected(fixture, command.requestId());
        assertThat(originalSaves).hasValue(0);
        assertThat(replacementSaves).hasValue(0);
    }

    @Test
    void attachmentReplacementDuringContextResolutionCannotRetargetTheAction() throws Exception {
        AtomicInteger originalSaves = new AtomicInteger();
        AtomicInteger replacementSaves = new AtomicInteger();
        JPanel original = onEdt(() -> panelWithSave(originalSaves));
        JPanel replacement = onEdt(() -> panelWithSave(replacementSaves));
        AtomicBoolean replaceOnResolve = new AtomicBoolean();
        AtomicInteger replacements = new AtomicInteger();
        AtomicReference<SwingHarnessBridge> target = new AtomicReference<>();
        var reattacher = Executors.newSingleThreadExecutor();
        try {
            SwingHarnessBridge bridge = new SwingHarnessBridge(root -> {
                if (replaceOnResolve.compareAndSet(true, false)) {
                    try {
                        // Keep the callback on the EDT while the public attach API
                        // replaces its target off-thread. Listener cleanup cannot
                        // run until this resolver and its queued action return.
                        reattacher.submit(() -> {
                            target.get().attach(replacement, title -> false);
                            replacements.incrementAndGet();
                        }).get(2, TimeUnit.SECONDS);
                    } catch (Exception exception) {
                        throw new IllegalStateException("Concurrent test attachment failed", exception);
                    }
                }
                return root;
            });
            target.set(bridge);
            onEdt(() -> {
                bridge.attach(original, title -> false);
                return null;
            });
            Fixture fixture = fixture(bridge);
            HarnessCommand command = boundSave("attachment-during-resolution", fixture.context());
            replaceOnResolve.set(true);

            fixture.executeAndDrain(command);

            assertThat(replacements).hasValue(1);
            assertContextRejected(fixture, command.requestId());
            assertThat(originalSaves).hasValue(0);
            assertThat(replacementSaves).hasValue(0);
            fixture.executeAndDrain(boundSave("fresh-after-resolution", fixture.context()));
            assertThat(replacementSaves).hasValue(1);
        } finally {
            reattacher.shutdownNow();
        }
    }

    @Test
    void completedReceiptsRemainReplayableAfterTheContextChanges() throws Exception {
        AtomicInteger saves = new AtomicInteger();
        JPanel root = onEdt(() -> panelWithSave(saves));
        Fixture fixture = fixture(root);
        HarnessCommand command = boundSave("saved-once", fixture.context());
        HarnessResponse original = fixture.executeAndDrain(command);
        assertCompleted(fixture, command.requestId());
        onEdt(() -> {
            root.removeAll();
            return null;
        });
        fixture.observe();

        HarnessResponse replay = newGateway(fixture.plugin()).execute(TOKEN, SwingHarnessPlugin.ID, command);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output()).isEqualTo(original.output());
        assertThat(saves).hasValue(1);
        assertCompleted(fixture, command.requestId());
    }

    @Test
    void omittedContextPreservesExistingSelectorBasedBehavior() throws Exception {
        AtomicInteger originalSaves = new AtomicInteger();
        AtomicInteger replacementSaves = new AtomicInteger();
        JPanel root = onEdt(() -> panelWithSave(originalSaves));
        Fixture fixture = fixture(root);
        fixture.observe();
        onEdt(() -> {
            root.removeAll();
            root.add(saveButton(replacementSaves));
            return null;
        });

        fixture.executeAndDrain(new HarnessCommand("legacy-command", "activate-button", Map.of("selector", "Save")));

        assertCompleted(fixture, "legacy-command");
        assertThat(originalSaves).hasValue(0);
        assertThat(replacementSaves).hasValue(1);
    }

    @Test
    void unavailableAndBusyObservationsNeverPublishAnActionContext() throws Exception {
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        Fixture fixture = fixture(bridge);
        assertThat(fixture.observe().metadata()).containsEntry("ready", false).doesNotContainKey("contextToken");
        onEdt(() -> {
            bridge.attach(new JPanel(), title -> false);
            return null;
        });
        fixture.context();
        try (BlockedEdt ignored = new BlockedEdt(() -> { })) {
            HarnessSnapshot busy = fixture.observe();
            assertThat(busy.metadata()).containsEntry("ready", false).doesNotContainKey("contextToken");
        }
        fixture.context();
        bridge.detach();
        assertThat(fixture.observe().metadata()).containsEntry("ready", false).doesNotContainKey("contextToken");
    }

    private static void assertContextRejected(Fixture fixture, String requestId) {
        HarnessExecution status = fixture.bridge().actionStatus(requestId);
        assertThat(status.success()).isFalse();
        assertThat(status.output()).containsEntry("status", "FAILED")
                .containsEntry("completed", true)
                .containsEntry("failureType", "DesktopContextChangedException");
    }

    private static void assertCompleted(Fixture fixture, String requestId) {
        assertThat(fixture.bridge().actionStatus(requestId).output())
                .containsEntry("status", "COMPLETED").containsEntry("completed", true);
    }

    private static HarnessCommand boundSave(String requestId, String context) {
        return new HarnessCommand(requestId, "activate-button",
                Map.of("selector", "Save", "expected-context", context));
    }

    private static HarnessCommand boundRow(String requestId, String context, int row) {
        return new HarnessCommand(requestId, "select-row",
                Map.of("table", "Harvest rows", "row", row, "expected-context", context));
    }

    private static JPanel panelWithSave(AtomicInteger saves) {
        JPanel root = new JPanel();
        root.add(saveButton(saves));
        return root;
    }

    private static JButton saveButton(AtomicInteger saves) {
        JButton button = new JButton("Save");
        button.addActionListener(event -> saves.incrementAndGet());
        return button;
    }

    private static Fixture fixture(JComponent root) throws Exception {
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        onEdt(() -> {
            bridge.attach(root, title -> false);
            return null;
        });
        return fixture(bridge);
    }

    private static Fixture fixture(SwingHarnessBridge bridge) {
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        return new Fixture(bridge, plugin, newGateway(plugin));
    }

    private static HarnessGateway newGateway(SwingHarnessPlugin plugin) {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowDrafts(true);
        properties.setAllowSensitiveRead(true);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        return new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties);
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        FutureTask<T> pending = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(pending);
        return pending.get();
    }

    private record Fixture(SwingHarnessBridge bridge, SwingHarnessPlugin plugin, HarnessGateway gateway) {
        HarnessSnapshot observe() {
            return gateway.observe(TOKEN, SwingHarnessPlugin.ID);
        }

        String context() {
            HarnessSnapshot snapshot = observe();
            assertThat(snapshot.metadata()).containsEntry("ready", true);
            Object context = snapshot.metadata().get("contextToken");
            assertThat(context).isInstanceOf(String.class);
            assertThat((String) context).isNotBlank();
            return (String) context;
        }

        HarnessResponse execute(HarnessCommand command) {
            return gateway.execute(TOKEN, SwingHarnessPlugin.ID, command);
        }

        HarnessResponse executeAndDrain(HarnessCommand command) throws Exception {
            HarnessResponse response = execute(command);
            onEdt(() -> null);
            return response;
        }
    }

    /** Holds the EDT until dispatch has queued the action, then changes its target before execution. */
    private static final class BlockedEdt implements AutoCloseable {
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        BlockedEdt(Runnable beforeUnblock) throws Exception {
            CountDownLatch entered = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Test did not release the blocked EDT");
                    }
                    beforeUnblock.run();
                } catch (Throwable exception) {
                    failure.set(exception);
                }
            });
            if (!entered.await(2, TimeUnit.SECONDS)) {
                release.countDown();
                throw new AssertionError("EDT did not reach the test barrier");
            }
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            onEdt(() -> null);
            if (failure.get() != null) {
                throw new AssertionError("EDT barrier failed", failure.get());
            }
        }
    }
}
