package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessResponse;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingReadConsistencyTest {
    private static final String TOKEN = "desktop-read-consistency-token-123456";

    @Test
    void firstUnboundReadOfAFreshTableSucceedsWithoutAnObservationWarmup() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture(new SwingHarnessBridge());
            try {
                HarnessCommand command = fixture.command("fresh-table", "read-control",
                        Map.of("selector", "records"), false);
                HarnessResponse response = fixture.execute(command);
                assertFreshRead(response);
                assertThat(response.output()).containsEntry("rows", List.of(List.of("101"), List.of("202")))
                        .containsEntry("rowCount", 2).containsEntry("hasMore", false);
                fixture.assertNoAction(command);
            } finally {
                fixture.bridge().detach();
            }
        });
    }

    @Test
    void rejectsBoundMixedPagesWithoutCachingAPartialSuccess() throws Exception {
        SwingUtilities.invokeAndWait(() -> verifyRefreshedPage(true));
    }

    @Test
    void rejectsUnboundMixedPagesAndLetsTheSameCommandRetry() throws Exception {
        SwingUtilities.invokeAndWait(() -> verifyRefreshedPage(false));
    }

    @Test
    void rejectsAnItemRenderedFromTheOldModelBeforePublishingANewContext() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture(new SwingHarnessBridge());
            try {
                HarnessCommand command = fixture.command("changed-item", "read-item",
                        Map.of("selector", "records", "index", 0, "column", 0,
                                "offset", 0, "limit", 2), true);
                fixture.armRefresh();

                assertThatThrownBy(() -> fixture.execute(command))
                        .isInstanceOf(DesktopContextChangedException.class);
                fixture.assertNoAction(command);

                HarnessResponse recovered = fixture.execute(fixture.command(command.requestId(), "read-item",
                        Map.of("selector", "records", "index", 0, "column", 0), true));
                assertFreshRead(recovered);
                assertThat(recovered.output()).containsEntry("value", "999")
                        .containsEntry("textLength", 3).containsEntry("hasMore", false);
            } finally {
                fixture.bridge().detach();
            }
        });
    }

    @Test
    void rejectsAReadWhoseActiveDialogChangesDuringRendering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<JComponent> active = new AtomicReference<>();
            Fixture fixture = fixture(new SwingHarnessBridge(ignored -> active.get()));
            active.set(fixture.table());
            JPanel dialog = new JPanel();
            dialog.add(new JLabel("Replacement dialog"));
            try {
                HarnessCommand command = fixture.command("changed-dialog", "read-control",
                        Map.of("selector", "records"), true);
                fixture.arm(() -> active.set(dialog));

                assertThatThrownBy(() -> fixture.execute(command))
                        .isInstanceOf(DesktopContextChangedException.class);
                fixture.assertNoAction(command);

                HarnessResponse recovered = fixture.execute(fixture.command(command.requestId(),
                        "read-screen", Map.of(), true));
                assertFreshRead(recovered);
                assertThat(recovered.output()).containsEntry("text", "Replacement dialog");
            } finally {
                fixture.bridge().detach();
            }
        });
    }

    @Test
    void rejectsAttachmentReplacementDuringRenderingAndRecoversOnTheNewAttachment() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture(new SwingHarnessBridge());
            JPanel replacement = new JPanel();
            replacement.add(new JLabel("New attachment"));
            try {
                HarnessCommand command = fixture.command("changed-attachment", "read-control",
                        Map.of("selector", "records"), true);
                fixture.arm(() -> fixture.bridge().attach(replacement, route -> false));

                assertThatThrownBy(() -> fixture.execute(command))
                        .isInstanceOf(DesktopContextChangedException.class);
                fixture.assertNoAction(command);

                HarnessResponse recovered = fixture.execute(fixture.command(command.requestId(),
                        "read-screen", Map.of(), true));
                assertFreshRead(recovered);
                assertThat(recovered.output()).containsEntry("text", "New attachment");
            } finally {
                fixture.bridge().detach();
            }
        });
    }

    private static void verifyRefreshedPage(boolean bound) {
        Fixture fixture = fixture(new SwingHarnessBridge());
        try {
            HarnessCommand command = fixture.command("changed-page", "read-control",
                    Map.of("selector", "records"), bound);
            fixture.armRefresh();

            assertThatThrownBy(() -> fixture.execute(command))
                    .isInstanceOf(DesktopContextChangedException.class);
            fixture.assertNoAction(command);
            assertThat(fixture.table().getValueAt(0, 0)).isEqualTo(999);
            assertThat(fixture.table().getValueAt(1, 0)).isEqualTo(888);

            HarnessCommand retry = command;
            if (bound) {
                // A rejected read creates no cached receipt: its request ID can carry a fresh context.
                assertThatThrownBy(() -> fixture.execute(command))
                        .isInstanceOf(DesktopContextChangedException.class);
                retry = fixture.command(command.requestId(), command.action(),
                        Map.of("selector", "records"), true);
            }
            HarnessResponse recovered = fixture.execute(retry);
            assertFreshRead(recovered);
            assertThat(recovered.output()).containsEntry("rows", List.of(List.of("999"), List.of("888")))
                    .containsEntry("rowCount", 2).containsEntry("hasMore", false);
            fixture.assertNoAction(retry);
            HarnessResponse replay = fixture.execute(retry);
            assertThat(replay.success()).isTrue();
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.output()).isEqualTo(recovered.output());
        } finally {
            fixture.bridge().detach();
        }
    }

    private static void assertFreshRead(HarnessResponse response) {
        assertThat(response.success()).isTrue();
        assertThat(response.replayed()).isFalse();
        assertThat(response.output().get("contextToken")).isInstanceOf(String.class)
                .isEqualTo(response.snapshot().metadata().get("contextToken"));
    }

    private static Fixture fixture(SwingHarnessBridge bridge) {
        DefaultTableModel model = new DefaultTableModel(new Object[][]{{101}, {202}}, new Object[]{"ID"});
        JTable table = new JTable(model);
        table.setName("records");
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        AtomicBoolean armed = new AtomicBoolean();
        AtomicReference<Runnable> callback = new AtomicReference<>(() -> { });
        renderer.addPropertyChangeListener("text", event -> {
            if ("101".equals(event.getNewValue()) && armed.compareAndSet(true, false)) {
                callback.get().run();
            }
        });
        table.setDefaultRenderer(Object.class, renderer);
        bridge.attach(table, route -> false);
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowSensitiveRead(true);
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties);
        return new Fixture(table, model, bridge, plugin, gateway, armed, callback);
    }

    private record Fixture(JTable table, DefaultTableModel model, SwingHarnessBridge bridge,
                           SwingHarnessPlugin plugin, HarnessGateway gateway,
                           AtomicBoolean armed, AtomicReference<Runnable> callback) {
        HarnessCommand command(String id, String action, Map<String, Object> arguments, boolean bound) {
            Map<String, Object> supplied = new LinkedHashMap<>(arguments);
            if (bound) {
                gateway.observe(TOKEN, SwingHarnessPlugin.ID);
                supplied.put("expected-context", gateway.observe(TOKEN, SwingHarnessPlugin.ID)
                        .metadata().get("contextToken"));
            }
            return new HarnessCommand(id, action, supplied);
        }

        HarnessResponse execute(HarnessCommand command) {
            return gateway.execute(TOKEN, SwingHarnessPlugin.ID, command);
        }

        void arm(Runnable action) {
            callback.set(action);
            armed.set(true);
        }

        void armRefresh() {
            arm(() -> model.setDataVector(new Object[][]{{999}, {888}}, new Object[]{"ID"}));
        }

        void assertNoAction(HarnessCommand command) {
            assertThat(plugin.replay(command)).isEmpty();
            assertThatThrownBy(() -> bridge.actionStatus(command.requestId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No retained desktop action");
        }
    }
}
