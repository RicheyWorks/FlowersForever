package com.flowerfarm.gui.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.mcp.McpProtocolHandler;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingMenuRuntimeTest {
    private static final String TOKEN = "swing-menu-wire-test-token-123456";

    @Test
    void readsAndSetsClosedMenuTogglesThroughMcpAndReplaysWithoutClickingTwice() throws Exception {
        try (Fixture fixture = fixture()) {
            JsonNode read = fixture.call("read-mode", "read-control", Map.of("path", List.of("View", "Dark Mode")));
            JsonNode value = read.path("result").path("structuredContent").path("output");
            assertThat(value.path("selected").asBoolean()).isFalse();
            assertThat(value.path("enabled").asBoolean()).isTrue();
            String token = value.path("contextToken").asText();
            assertThat(token).isNotBlank();
            Map<String, Object> arguments = Map.of("path", List.of("View", "Dark Mode"),
                    "selected", true, "expected-context", token);
            JsonNode changed = fixture.call("set-mode", "set-toggle", arguments);
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(changed.path("result").path("isError").asBoolean()).isFalse();
            assertThat(fixture.clicks()).hasValue(1);
            assertThat(fixture.call("set-mode", "set-toggle", arguments).path("result")
                    .path("structuredContent").path("replayed").asBoolean()).isTrue();
            assertThat(fixture.clicks()).hasValue(1);
            JsonNode current = fixture.call("read-mode-now", "read-control", Map.of("path", List.of("View", "Dark Mode")));
            assertThat(current.path("result").path("structuredContent").path("output").path("selected").asBoolean())
                    .isTrue();
            fixture.call("retain-mode", "set-toggle", Map.of("path", List.of("View", "Dark Mode"), "selected", true));
            SwingUtilities.invokeAndWait(() -> assertThat(fixture.menu().isPopupMenuVisible()).isFalse());
            assertThat(fixture.clicks()).hasValue(1);
        }
    }

    @Test
    void invokesAClosedMenuAcceleratorThroughMcpWithContextAndReceiptReplay() throws Exception {
        try (Fixture fixture = fixture()) {
            JsonNode read = fixture.call("shortcut-observation", "read-control",
                    Map.of("path", List.of("View", "Dark Mode")));
            String context = read.path("result").path("structuredContent").path("output")
                    .path("contextToken").asText();
            Map<String, Object> arguments = Map.of("shortcut", "ctrl shift D", "expected-context", context);
            JsonNode changed = fixture.call("toggle-shortcut", "invoke-shortcut", arguments);
            String actionId = changed.path("result").path("structuredContent").path("output")
                    .path("actionRequestId").asText();
            assertThat(actionId).isNotBlank();
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(fixture.bridge().actionStatus(actionId).output())
                    .containsEntry("status", "COMPLETED");
            assertThat(fixture.clicks()).hasValue(1);
            JsonNode replay = fixture.call("toggle-shortcut", "invoke-shortcut", arguments);
            assertThat(replay.path("result").path("structuredContent").path("replayed").asBoolean()).isTrue();
            assertThat(fixture.clicks()).hasValue(1);
            SwingUtilities.invokeAndWait(() -> {
                assertThat(fixture.menu().isPopupMenuVisible()).isFalse();
                assertThat(fixture.menu().getItem(0).isSelected()).isTrue();
            });

            JsonNode rejected = fixture.call("stale-shortcut", "invoke-shortcut", arguments);
            String rejectedId = rejected.path("result").path("structuredContent").path("output")
                    .path("actionRequestId").asText();
            assertThat(rejectedId).isNotBlank();
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(fixture.bridge().actionStatus(rejectedId).output())
                    .containsEntry("status", "FAILED")
                    .containsEntry("failureType", "DesktopContextChangedException");
            assertThat(fixture.clicks()).hasValue(1);
        }
    }

    @Test
    void menuAcceleratorsKeepTheExistingDestructivePolicyGate() throws Exception {
        try (Fixture fixture = fixture()) {
            HarnessProperties properties = new HarnessProperties();
            properties.setEnabled(true);
            properties.setToken(TOKEN);
            HarnessGateway readOnly = new HarnessGateway(new HarnessRegistry(List.of(fixture.plugin())), properties);

            assertThatThrownBy(() -> readOnly.execute(TOKEN, SwingHarnessPlugin.ID,
                    new HarnessCommand("denied-shortcut", "invoke-shortcut", Map.of("shortcut", "ctrl shift D"))))
                    .isInstanceOf(com.flowerfarm.automation.HarnessPolicyException.class);
            assertThat(fixture.clicks()).hasValue(0);
        }
    }

    @Test
    void reportsDisabledMenuAncestorsAndRejectsChangingThem() throws Exception {
        try (Fixture fixture = fixture()) {
            SwingUtilities.invokeAndWait(() -> fixture.menu().setEnabled(false));
            var read = fixture.plugin().execute(new HarnessCommand("disabled-read", "read-control",
                    Map.of("path", List.of("View", "Dark Mode"))));
            assertThat(read.output()).containsEntry("enabled", false).containsEntry("selected", false);
            fixture.plugin().execute(new HarnessCommand("disabled-toggle", "set-toggle",
                    Map.of("path", List.of("View", "Dark Mode"), "selected", true)));
            SwingUtilities.invokeAndWait(() -> { });
            assertThat(fixture.bridge().actionStatus("disabled-toggle").output()).containsEntry("status", "FAILED");
            assertThat(fixture.clicks()).hasValue(0);
        }
    }

    @Test
    void rejectsMissingOrConflictingTargetForms() throws Exception {
        try (Fixture fixture = fixture()) {
            for (Map<String, Object> arguments : List.of(Map.<String, Object>of(),
                    Map.<String, Object>of("selector", "Dark Mode", "path", List.of("View", "Dark Mode")))) {
                assertThatThrownBy(() -> fixture.plugin().execute(new HarnessCommand("invalid-target", "read-control", arguments)))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly one");
            }
            assertThat(fixture.clicks()).hasValue(0);
        }
    }

    private static Fixture fixture() throws Exception {
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        AtomicInteger clicks = new AtomicInteger();
        JMenu menu = new JMenu("View");
        SwingUtilities.invokeAndWait(() -> {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem("Dark Mode");
            item.setAccelerator(KeyStroke.getKeyStroke("ctrl shift D"));
            item.addActionListener(event -> clicks.incrementAndGet());
            menu.add(item);
            JMenuBar bar = new JMenuBar();
            bar.add(menu);
            JRootPane root = new JRootPane();
            root.setContentPane(new JPanel());
            root.setJMenuBar(bar);
            bridge.attach(root, title -> false);
        });
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowSensitiveRead(true);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties);
        return new Fixture(bridge, plugin, new McpProtocolHandler(gateway, properties, mapper), mapper, menu, clicks);
    }

    private record Fixture(SwingHarnessBridge bridge, SwingHarnessPlugin plugin, McpProtocolHandler handler,
                           ObjectMapper mapper, JMenu menu, AtomicInteger clicks) implements AutoCloseable {
        JsonNode call(String id, String action, Map<String, Object> arguments) {
            return handler.handle(mapper.valueToTree(Map.of("jsonrpc", "2.0", "id", id, "method", "tools/call",
                    "params", Map.of("_meta", Map.of(
                            "io.modelcontextprotocol/protocolVersion", McpProtocolHandler.CURRENT_PROTOCOL_VERSION,
                            "io.modelcontextprotocol/clientInfo", Map.of("name", "swing-runtime-test", "version", "1"),
                            "io.modelcontextprotocol/clientCapabilities", Map.of()),
                            "name", "swing-desktop__" + action, "arguments", arguments))));
        }

        @Override public void close() {
            bridge.detach();
        }
    }
}
