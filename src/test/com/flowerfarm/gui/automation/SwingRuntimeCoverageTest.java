package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessControl;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.gui.tabs.EventServicesTab;
import com.flowerfarm.gui.tabs.TabHost;
import org.junit.jupiter.api.Test;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SwingRuntimeCoverageTest {
    private static final String TOKEN = "runtime-control-coverage-token-123456";

    @Test
    void readsProductionEventStatusAndLayoutThenClearsTheActualServiceSelection() throws Exception {
        JComponent root = onEdt(() -> {
            JComponent content = new EventServicesTab(mock(TabHost.class)).getUIComponent();
            content.setSize(1400, 1000);
            layout(content);
            return content;
        });
        Fixture fixture = fixture(root);
        assertThat(fixture.readableSelectors()).contains("eventServices.catalogCount", "eventServices.feedback");
        assertThat(fixture.read("catalog", "read-control", Map.of("selector", "eventServices.catalogCount")))
                .containsEntry("kind", "label");

        fixture.action("occasion", "choose-option", Map.of("selector", "Event occasion filter",
                "value", "Memorial / funeral"));
        fixture.action("category", "choose-option", Map.of("selector", "Event service category filter",
                "value", "Photography & media"));
        fixture.action("services", "select-rows", Map.of("table", "eventServices.table", "rows", List.of(0, 1)));
        fixture.action("brief", "activate-button", Map.of("selector", "Build selected-service brief"));

        assertThat(fixture.read("feedback", "read-control", Map.of("selector", "eventServices.feedback")))
                .containsEntry("value", "2 services in brief");
        Map<String, Object> scroll = fixture.read("production-scroll", "read-layout",
                Map.of("selector", "eventServices.table", "kind", "scroll"));
        assertThat(scroll).containsEntry("kind", "scroll").containsKeys("horizontal", "vertical", "contextToken");
        Map<String, Object> split = fixture.read("production-split", "read-layout",
                Map.of("selector", "eventServices.table", "kind", "split"));
        int minimum = number(split, "minimum");
        int maximum = number(split, "maximum");
        assertThat(maximum).isGreaterThan(minimum);
        int location = minimum + (maximum - minimum) / 2;
        fixture.action("resize-production", "set-divider",
                Map.of("selector", "eventServices.table", "value", location));
        assertThat(fixture.read("production-resized", "read-layout",
                Map.of("selector", "eventServices.table", "kind", "split")))
                .containsEntry("value", location);

        fixture.action("clear-services", "clear-selection", Map.of("selector", "eventServices.table"));
        assertThat(fixture.read("cleared-services", "read-control", Map.of("selector", "eventServices.table")))
                .containsEntry("selectedRows", List.of());
    }

    @Test
    void scrollsAnUnnamedAncestorAndSetsToggleStateWithoutRepeatedListenerEffects() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        JPanel view = onEdt(() -> {
            JPanel content = new JPanel();
            content.setName("packing.view");
            content.setPreferredSize(new Dimension(600, 500));
            JCheckBox packed = new JCheckBox("Packed");
            packed.addActionListener(event -> changes.incrementAndGet());
            content.add(packed);
            return content;
        });
        JScrollPane root = onEdt(() -> {
            JScrollPane pane = new JScrollPane(view);
            pane.setSize(200, 150);
            layout(pane);
            return pane;
        });
        Fixture fixture = fixture(root);
        Map<String, Object> ranges = fixture.read("packing-ranges", "read-layout",
                Map.of("selector", "packing.view", "kind", "scroll"));
        int maximum = number((Map<?, ?>) ranges.get("vertical"), "maximumValue");
        assertThat(maximum).isPositive();

        fixture.action("scroll-packing", "scroll",
                Map.of("selector", "packing.view", "axis", "VERTICAL", "value", maximum));
        Map<String, Object> after = fixture.read("packing-scrolled", "read-layout",
                Map.of("selector", "packing.view", "kind", "scroll"));
        assertThat(number((Map<?, ?>) after.get("vertical"), "value")).isEqualTo(maximum);

        fixture.action("already-unpacked", "set-toggle", Map.of("selector", "Packed", "selected", false));
        assertThat(changes).hasValue(0);
        HarnessCommand check = fixture.bound("mark-packed", "set-toggle", Map.of("selector", "Packed", "selected", true));
        HarnessResponse original = fixture.action(check);
        fixture.action("already-packed", "set-toggle", Map.of("selector", "Packed", "selected", true));
        HarnessResponse replay = fixture.execute(check);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output()).isEqualTo(original.output());
        assertThat(changes).hasValue(1);
        assertThat(fixture.read("packed-state", "read-control", Map.of("selector", "Packed")))
                .containsEntry("selected", true);
        assertThatThrownBy(() -> fixture.execute(new HarnessCommand("bad-boolean", "set-toggle",
                Map.of("selector", "Packed", "selected", "true"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(changes).hasValue(1);
    }

    @Test
    void selectsAnExactObservedComboIndexWhenRenderedLabelsRepeat() throws Exception {
        JComboBox<Choice> combo = onEdt(() -> {
            JComboBox<Choice> control = new JComboBox<>(new Choice[]{new Choice(1), new Choice(2)});
            control.getAccessibleContext().setAccessibleName("Service package");
            control.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean selected, boolean focused) {
                    return super.getListCellRendererComponent(list, "Same package label", index, selected, focused);
                }
            });
            return control;
        });
        Fixture fixture = fixture(combo);
        Map<String, Object> observed = fixture.read("second-package", "read-control",
                Map.of("selector", "Service package", "offset", 1, "limit", 1));
        assertThat(observed).containsEntry("options", List.of("Same package label"))
                .containsEntry("optionIndexes", List.of(1));
        HarnessResponse ambiguous = fixture.execute(new HarnessCommand("ambiguous-package", "choose-option",
                Map.of("selector", "Service package", "value", "Same package label")));
        drain();
        assertThat(fixture.bridge().actionStatus(ambiguous.requestId()).output()).containsEntry("status", "FAILED");

        fixture.action("exact-package", "choose-option-index", Map.of("selector", "Service package", "index", 1));

        onEdt(() -> {
            assertThat(combo.getSelectedItem()).isEqualTo(new Choice(2));
            return null;
        });
        assertThat(fixture.read("chosen-package", "read-control", Map.of("selector", "Service package")))
                .containsEntry("selectedIndex", 1);
    }

    @Test
    void clearsBothTableAndListSelectionThroughTheirProductionModels() throws Exception {
        JTable table = new JTable(new Object[][]{{"Dahlia"}, {"Zinnia"}}, new Object[]{"Crop"});
        JList<String> list = new JList<>(new String[]{"Seed", "Plant", "Harvest"});
        JPanel root = onEdt(() -> {
            JPanel panel = new JPanel();
            table.setName("selected.table");
            list.setName("selected.list");
            table.setRowSelectionInterval(0, 1);
            list.setSelectionInterval(0, 2);
            panel.add(table);
            panel.add(list);
            return panel;
        });
        Fixture fixture = fixture(root);

        fixture.action("clear-table", "clear-selection", Map.of("selector", "selected.table"));
        fixture.action("clear-list", "clear-selection", Map.of("selector", "selected.list"));

        assertThat(fixture.read("empty-table", "read-control", Map.of("selector", "selected.table")))
                .containsEntry("selectedRows", List.of());
        assertThat(fixture.read("empty-list", "read-control", Map.of("selector", "selected.list")))
                .containsEntry("selectedRows", List.of());
    }

    @Test
    void reportsFailureWhenListenersRestoreTableRowsOrColumnsThenAllowsRecovery() throws Exception {
        AtomicBoolean restoreRows = new AtomicBoolean(true);
        AtomicBoolean restoreColumns = new AtomicBoolean(false);
        JTable table = onEdt(() -> {
            JTable control = new JTable(new Object[][]{{"Dahlia"}}, new Object[]{"Crop"});
            control.setName("persistent.table");
            control.setCellSelectionEnabled(true);
            control.setRowSelectionInterval(0, 0);
            control.setColumnSelectionInterval(0, 0);
            control.getSelectionModel().addListSelectionListener(event -> {
                if (restoreRows.get() && control.getSelectionModel().isSelectionEmpty()) {
                    control.setRowSelectionInterval(0, 0);
                }
            });
            control.getColumnModel().getSelectionModel().addListSelectionListener(event -> {
                if (restoreColumns.get() && control.getColumnModel().getSelectionModel().isSelectionEmpty()) {
                    control.setColumnSelectionInterval(0, 0);
                }
            });
            return control;
        });
        Fixture fixture = fixture(table);
        fixture.execute(fixture.bound("restore-row", "clear-selection", Map.of("selector", "persistent.table")));
        drain();
        assertThat(fixture.bridge().actionStatus("restore-row").output()).containsEntry("status", "FAILED")
                .containsEntry("failureType", "IllegalStateException");
        onEdt(() -> {
            assertThat(table.getSelectedRow()).isZero();
            restoreRows.set(false);
            restoreColumns.set(true);
            table.setColumnSelectionInterval(0, 0);
            return null;
        });

        fixture.execute(fixture.bound("restore-column", "clear-selection", Map.of("selector", "persistent.table")));
        drain();
        assertThat(fixture.bridge().actionStatus("restore-column").output()).containsEntry("status", "FAILED")
                .containsEntry("failureType", "IllegalStateException");
        onEdt(() -> {
            assertThat(table.getSelectedRow()).isEqualTo(-1);
            assertThat(table.getSelectedColumn()).isZero();
            restoreColumns.set(false);
            return null;
        });

        fixture.action("clear-recovered-table", "clear-selection", Map.of("selector", "persistent.table"));
        onEdt(() -> {
            assertThat(table.getSelectedRow()).isEqualTo(-1);
            assertThat(table.getSelectedColumn()).isEqualTo(-1);
            return null;
        });
    }

    @Test
    void reportsFailureWhenAListenerRestoresListSelectionThenAllowsRecovery() throws Exception {
        AtomicBoolean restoreSelection = new AtomicBoolean(true);
        JList<String> list = onEdt(() -> {
            JList<String> control = new JList<>(new String[]{"Seed", "Plant"});
            control.setName("persistent.list");
            control.setSelectedIndex(0);
            control.addListSelectionListener(event -> {
                if (restoreSelection.get() && control.isSelectionEmpty()) {
                    control.setSelectedIndex(0);
                }
            });
            return control;
        });
        Fixture fixture = fixture(list);
        fixture.execute(fixture.bound("restore-list", "clear-selection", Map.of("selector", "persistent.list")));
        drain();
        assertThat(fixture.bridge().actionStatus("restore-list").output()).containsEntry("status", "FAILED")
                .containsEntry("failureType", "IllegalStateException");
        onEdt(() -> {
            assertThat(list.getSelectedIndex()).isZero();
            restoreSelection.set(false);
            return null;
        });

        fixture.action("clear-recovered-list", "clear-selection", Map.of("selector", "persistent.list"));
        onEdt(() -> {
            assertThat(list.isSelectionEmpty()).isTrue();
            return null;
        });
    }

    @Test
    void readsTableRowsAndColumnsBeyondTheOldPrefixAndRejectsAChangedPageContext() throws Exception {
        JTable table = onEdt(() -> {
            Object[][] values = new Object[205][55];
            Object[] columns = new Object[55];
            for (int column = 0; column < columns.length; column++) {
                columns[column] = "Column " + column;
                for (int row = 0; row < values.length; row++) {
                    values[row][column] = row + ":" + column;
                }
            }
            JTable control = new JTable(new DefaultTableModel(values, columns));
            control.setName("large.table");
            return control;
        });
        Fixture fixture = fixture(table);
        String context = fixture.context();
        Map<String, Object> pageArgs = Map.of("selector", "large.table", "offset", 200, "limit", 5,
                "column-offset", 50, "column-limit", 5, "expected-context", context);
        Map<String, Object> page = fixture.read("tail-page", "read-control", pageArgs);
        assertThat(page).containsEntry("rowIndexes", List.of(200, 201, 202, 203, 204))
                .containsEntry("columnIndexes", List.of(50, 51, 52, 53, 54))
                .containsEntry("hasMore", false).containsEntry("hasMoreColumns", false)
                .containsEntry("contextToken", context);
        assertThat((List<?>) page.get("rows")).first()
                .isEqualTo(List.of("200:50", "200:51", "200:52", "200:53", "200:54"));
        onEdt(() -> {
            table.getModel().setValueAt("updated crop", 200, 50);
            return null;
        });

        assertThatThrownBy(() -> fixture.read("stale-tail-page", "read-control", pageArgs))
                .isInstanceOf(DesktopContextChangedException.class);

        Map<String, Object> fresh = new LinkedHashMap<>(pageArgs);
        fresh.put("expected-context", fixture.context());
        Map<String, Object> refreshed = fixture.read("fresh-tail-page", "read-control", fresh);
        assertThat((List<?>) refreshed.get("rows")).first()
                .isEqualTo(List.of("updated crop", "200:51", "200:52", "200:53", "200:54"));
    }

    @Test
    void rejectsContinuationWhenTheEffectiveTableRendererChangesBetweenPages() throws Exception {
        JTable table = onEdt(() -> {
            JTable control = new JTable(new Object[][]{{"Rose"}}, new Object[]{"Crop"});
            control.setName("rendered.table");
            control.setDefaultRenderer(Object.class, prefixedRenderer("Original: "));
            return control;
        });
        Fixture fixture = fixture(table);
        Map<String, Object> first = fixture.read("original-prefix", "read-item",
                Map.of("selector", "rendered.table", "index", 0, "column", 0, "limit", 8));
        assertThat(first).containsEntry("value", "Original");
        onEdt(() -> {
            table.setDefaultRenderer(Object.class, prefixedRenderer("Replacement: "));
            return null;
        });

        assertThatThrownBy(() -> fixture.read("stale-renderer-page", "read-item",
                Map.of("selector", "rendered.table", "index", 0, "column", 0, "offset", 8,
                        "expected-context", first.get("contextToken"))))
                .isInstanceOf(DesktopContextChangedException.class);
        assertThat(fixture.read("fresh-rendered-cell", "read-item",
                Map.of("selector", "rendered.table", "index", 0, "column", 0)))
                .containsEntry("value", "Replacement: Rose");
    }

    private static DefaultTableCellRenderer prefixedRenderer(String prefix) {
        return new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                super.setValue(prefix + value);
            }
        };
    }

    @Test
    void discoversReadonlyAndLayoutSelectorsWithoutTheirValuesAndPagesLongText() throws Exception {
        String confidentialLabel = "private customer status 7042";
        String tail = "private record tail 6394";
        JSplitPane root = onEdt(() -> {
            JLabel status = new JLabel(confidentialLabel);
            status.setName("private.status");
            JTextArea notes = new JTextArea("a".repeat(8000) + tail);
            notes.setEditable(false);
            notes.getAccessibleContext().setAccessibleName("Historical notes");
            JScrollPane scroll = new JScrollPane(notes);
            scroll.setName("notes.scroll");
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, status, scroll);
            split.setName("notes.split");
            split.setSize(700, 400);
            layout(split);
            return split;
        });
        Fixture fixture = fixture(root);
        HarnessSnapshot observation = fixture.observe();
        assertThat(fixture.readableSelectors()).contains("private.status", "Historical notes");
        assertThat(selectors(observation, "layoutControls")).contains("notes.scroll", "notes.split");
        assertThat(observation.controls()).extracting(HarnessControl::selector)
                .doesNotContain("private.status", "Historical notes", "notes.scroll", "notes.split");
        assertThat(observation.toString()).doesNotContain(confidentialLabel, tail);

        Map<String, Object> first = fixture.read("notes-prefix", "read-control", Map.of("selector", "Historical notes"));
        assertThat(first).containsEntry("returnedCount", 8000).containsEntry("nextOffset", 8000);
        Map<String, Object> last = fixture.read("notes-tail", "read-control",
                Map.of("selector", "Historical notes", "offset", 8000, "limit", 100,
                        "expected-context", first.get("contextToken")));
        assertThat(last).containsEntry("value", tail).containsEntry("hasMore", false);
        assertThat(fixture.read("status-value", "read-control", Map.of("selector", "private.status")))
                .containsEntry("value", confidentialLabel);
    }

    @Test
    void pagesScreenTextWithOneContextAndExcludesInactiveTabsAndPasswords() throws Exception {
        JLabel status = new JLabel("Visible status 476");
        JTabbedPane root = onEdt(() -> {
            status.setName("visible.status");
            JPanel current = new JPanel();
            current.add(status);
            JTextArea notes = new JTextArea("Notes " + "farm ".repeat(45));
            notes.setEditable(false);
            current.add(notes);
            JPasswordField password = new JPasswordField("unreadable-password-982");
            password.setName("password");
            current.add(password);
            JPanel hidden = new JPanel();
            hidden.add(new JLabel("Hidden outcome 889"));
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Current", current);
            tabs.addTab("Other", hidden);
            return tabs;
        });
        Fixture fixture = fixture(root);
        String context = fixture.context();
        StringBuilder combined = new StringBuilder();
        int offset = 0;
        int pages = 0;
        while (true) {
            assertThat(++pages).isLessThan(30);
            Map<String, Object> page = fixture.read("screen-page-" + pages, "read-screen",
                    Map.of("offset", offset, "limit", 30, "expected-context", context));
            assertThat(page).containsEntry("contextToken", context);
            combined.append(page.get("text"));
            if (!Boolean.TRUE.equals(page.get("hasMore"))) {
                break;
            }
            int next = number(page, "nextOffset");
            assertThat(next).isGreaterThan(offset);
            offset = next;
        }
        assertThat(combined.toString()).contains("Visible status 476", "Notes farm")
                .doesNotContain("unreadable-password-982", "Hidden outcome 889");
        onEdt(() -> {
            status.setText("Changed status 477");
            return null;
        });
        assertThatThrownBy(() -> fixture.read("stale-screen", "read-screen",
                Map.of("expected-context", context)))
                .isInstanceOf(DesktopContextChangedException.class);
        assertThat(fixture.read("fresh-screen", "read-screen",
                Map.of("expected-context", fixture.context())).get("text"))
                .asString().contains("Changed status 477");
    }

    @Test
    void enforcesReadPoliciesAndRequiredArgumentsWhileGeometryRemainsReadable() throws Exception {
        JScrollPane root = onEdt(() -> {
            JTextArea notes = new JTextArea("private notes");
            notes.setName("notes");
            JScrollPane pane = new JScrollPane(notes);
            pane.setName("scroll");
            pane.setSize(300, 200);
            layout(pane);
            return pane;
        });
        Fixture fixture = fixture(root);
        fixture.properties().setAllowSensitiveRead(false);
        assertThatThrownBy(() -> fixture.read("denied-screen", "read-screen", Map.of()))
                .isInstanceOf(HarnessPolicyException.class);
        assertThatThrownBy(() -> fixture.read("denied-control", "read-control", Map.of("selector", "notes")))
                .isInstanceOf(HarnessPolicyException.class);
        assertThat(fixture.read("allowed-geometry", "read-layout", Map.of("selector", "notes", "kind", "scroll")))
                .containsEntry("kind", "scroll").doesNotContainKey("value");

        fixture.properties().setAllowSensitiveRead(true);
        assertThatThrownBy(() -> fixture.read("missing-selector", "read-control", Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("selector");
        assertThatThrownBy(() -> fixture.read("missing-kind", "read-layout", Map.of("selector", "notes")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kind");
        assertThatThrownBy(() -> fixture.read("absent-control", "read-control", Map.of("selector", "unknown")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 0");
    }

    @Test
    void layoutAncestorResolutionCannotEscapeTheActiveModalRoot() throws Exception {
        JPanel active = onEdt(() -> {
            JPanel panel = new JPanel();
            panel.setName("modal.view");
            panel.setPreferredSize(new Dimension(600, 500));
            panel.add(new JLabel("Dialog text"));
            return panel;
        });
        JScrollPane owner = onEdt(() -> {
            JScrollPane pane = new JScrollPane(active);
            pane.setName("owner.scroll");
            pane.setSize(200, 150);
            layout(pane);
            return pane;
        });
        SwingHarnessBridge bridge = new SwingHarnessBridge(ignored -> active);
        onEdt(() -> {
            bridge.attach(owner, title -> false);
            return null;
        });
        Fixture fixture = fixture(bridge);

        assertThatThrownBy(() -> fixture.read("outside-modal", "read-layout",
                Map.of("selector", "modal.view", "kind", "scroll")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No active JScrollPane");
        assertThat(selectors(fixture.observe(), "layoutControls")).doesNotContain("owner.scroll");
        onEdt(() -> {
            assertThat(owner.getVerticalScrollBar().getValue()).isZero();
            return null;
        });
    }

    private static int number(Map<?, ?> value, String key) {
        return ((Number) value.get(key)).intValue();
    }

    private static List<String> selectors(HarnessSnapshot snapshot, String key) {
        List<?> controls = (List<?>) snapshot.metadata().get(key);
        assertThat(controls).isNotNull();
        return controls.stream().map(value -> ((HarnessControl) value).selector()).toList();
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
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowDrafts(true);
        properties.setAllowSensitiveRead(true);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties);
        return new Fixture(bridge, gateway, properties);
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        FutureTask<T> pending = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(pending);
        return pending.get();
    }

    private static void drain() throws Exception {
        onEdt(() -> null);
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layout(child);
            }
        }
    }

    private record Choice(int id) { }

    private record Fixture(SwingHarnessBridge bridge, HarnessGateway gateway, HarnessProperties properties) {
        HarnessSnapshot observe() {
            return gateway.observe(TOKEN, SwingHarnessPlugin.ID);
        }

        String context() {
            Object context = observe().metadata().get("contextToken");
            assertThat(context).isInstanceOf(String.class);
            return (String) context;
        }

        List<String> readableSelectors() {
            return selectors(observe(), "readableControls");
        }

        HarnessCommand bound(String id, String action, Map<String, Object> arguments) {
            Map<String, Object> bound = new LinkedHashMap<>(arguments);
            bound.put("expected-context", context());
            return new HarnessCommand(id, action, bound);
        }

        HarnessResponse execute(HarnessCommand command) {
            return gateway.execute(TOKEN, SwingHarnessPlugin.ID, command);
        }

        HarnessResponse action(String id, String action, Map<String, Object> arguments) throws Exception {
            return action(bound(id, action, arguments));
        }

        HarnessResponse action(HarnessCommand command) throws Exception {
            HarnessResponse response = execute(command);
            drain();
            assertThat(bridge.actionStatus(command.requestId()).output())
                    .as(command.action() + " " + command.requestId()).containsEntry("status", "COMPLETED");
            return response;
        }

        Map<String, Object> read(String id, String action, Map<String, Object> arguments) {
            HarnessResponse response = execute(new HarnessCommand(id, action, arguments));
            assertThat(response.success()).isTrue();
            assertThat(response.output()).containsKey("contextToken");
            return response.output();
        }
    }
}
