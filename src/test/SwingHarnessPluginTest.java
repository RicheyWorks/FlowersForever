package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AI-pluggable Swing desktop adapter")
class SwingHarnessPluginTest {

    @Test
    @DisplayName("observes without field values and executes every declared interaction type")
    void observesAndExecutesAccessibleCommands() {
        Fixture fixture = fixture();
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(fixture.bridge);

        HarnessSnapshot before = plugin.observe();
        assertThat(before.route()).containsExactly("Reference");
        assertThat(before.controls())
                .extracting(control -> control.selector())
                .contains("Crop draft", "Crop choice", "plant.table", "Task list",
                        "Stem count", "Stem angle", "Apply draft");
        assertThat(before.controls()).filteredOn(control -> control.selector().equals("plant.table"))
                .singleElement().satisfies(control -> assertThat(control.label()).isEqualTo("Plant table"));
        assertThat(before.toString()).doesNotContain("private crop note");
        assertThat(before.metadata()).containsEntry("fieldValuesIncluded", false);
        assertThat(before.metadata()).containsEntry("routeCount", 2);
        assertThat(before.metadata().get("routes"))
                .isEqualTo(List.of(List.of("Reference"), List.of("Details")));
        assertThat(before.metadata().get("menuPaths"))
                .isEqualTo(List.of(List.of("View", "Refresh farm")));
        assertThat(before.controls()).filteredOn(control ->
                control.selector().equals("Desktop password"))
                .allMatch(control -> !control.commandable());

        plugin.execute(command("nav", "navigate", Map.of("tab", "Inventory")));
        plugin.execute(command("text", "replace-text",
                Map.of("selector", "Crop draft", "value", "Nootka Rose")));
        plugin.execute(command("option", "choose-option",
                Map.of("selector", "Crop choice", "value", "Native rose")));
        plugin.execute(command("row", "select-row",
                Map.of("table", "plant.table", "row", 1)));
        plugin.execute(command("sort", "sort-table",
                Map.of("table", "plant.table", "column", "Plant",
                        "direction", "DESCENDING")));
        plugin.execute(command("rows", "select-rows",
                Map.of("table", "plant.table", "rows", java.util.List.of(0, 1))));
        assertThat(fixture.table.getSelectedRows()).containsExactly(0, 1);
        plugin.execute(command("list", "select-list-row",
                Map.of("selector", "Task list", "row", 1)));
        plugin.execute(command("spinner", "set-spinner",
                Map.of("selector", "Stem count", "value", "4")));
        plugin.execute(command("slider", "set-slider",
                Map.of("selector", "Stem angle", "value", 7)));
        plugin.execute(command("button", "activate-button",
                Map.of("selector", "Apply draft")));
        plugin.execute(command("submit", "submit-field",
                Map.of("selector", "Crop draft")));
        plugin.execute(command("double", "double-click-row",
                Map.of("table", "plant.table", "row", 1)));
        plugin.execute(command("menu", "activate-menu",
                Map.of("path", List.of("View", "Refresh farm"))));
        plugin.execute(command("shortcut", "invoke-shortcut",
                Map.of("shortcut", "F5")));
        Map<String, Object> fieldValue = plugin.execute(command("read", "read-control",
                Map.of("selector", "Crop draft"))).output();
        Map<String, Object> tableValue = plugin.execute(command("read-table", "read-control",
                Map.of("selector", "plant.table"))).output();
        Map<String, Object> screen = plugin.execute(command(
                "screen", "capture-screen", Map.of())).output();
        plugin.execute(command("tab", "select-tab",
                Map.of("selector", "Workspaces", "tab", "Details")));

        assertThat(fixture.selectedTab).hasValue("Inventory");
        assertThat(fixture.field.getText()).isEqualTo("Nootka Rose");
        assertThat(fixture.combo.getSelectedIndex()).isEqualTo(1);
        assertThat(fixture.table.getValueAt(0, 0)).isEqualTo("Nootka Rose");
        assertThat(fixture.table.getSelectedRows()).containsExactly(1);
        assertThat(fixture.list.getSelectedIndex()).isEqualTo(1);
        assertThat(fixture.spinner.getValue()).isEqualTo(4);
        assertThat(fixture.slider.getValue()).isEqualTo(7);
        assertThat(fixture.tabs.getSelectedIndex()).isEqualTo(1);
        assertThat(fixture.clicks).hasValue(1);
        assertThat(fixture.submits).hasValue(1);
        assertThat(fixture.doubleClicks).hasValue(1);
        assertThat(fixture.menuActivations).hasValue(1);
        assertThat(fixture.shortcutActivations).hasValue(1);
        assertThat(fieldValue)
                .containsEntry("kind", "text")
                .containsEntry("value", "Nootka Rose")
                .containsEntry("truncated", false);
        assertThat(tableValue)
                .containsEntry("kind", "table")
                .containsEntry("rowCount", 2)
                .containsEntry("selectedRows", List.of(1));
        assertThat(screen)
                .containsEntry("mediaType", "image/png")
                .containsEntry("encoding", "base64")
                .containsEntry("width", 640)
                .containsEntry("height", 480);
        byte[] png = Base64.getDecoder().decode(String.valueOf(screen.get("data")));
        assertThat(png).startsWith(new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        assertThat(String.valueOf(screen.get("sha256"))).hasSize(64);
    }

    @Test
    @DisplayName("never writes password fields and reports detached state safely")
    void protectsPasswordsAndDetachedDesktop() {
        Fixture fixture = fixture();
        assertThatThrownBy(() -> fixture.bridge.replaceText("Desktop password", "changed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password fields");
        assertThat(fixture.password.getPassword()).containsExactly(
                'n', 'e', 'v', 'e', 'r', '-', 'e', 'x', 'p', 'o', 's', 'e');
        assertThatThrownBy(() -> fixture.bridge.submitField("Desktop password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password fields");
        assertThatThrownBy(() -> fixture.bridge.readControl("Desktop password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never readable");

        fixture.combo.setEnabled(false);
        assertThatThrownBy(() -> fixture.bridge.chooseOption("Crop choice", "Native rose"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");

        fixture.table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        assertThatThrownBy(() -> fixture.bridge.selectRows("plant.table",
                java.util.List.of(0, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only one selected row");
        assertThat(fixture.table.getSelectedRows()).isEmpty();

        assertThatThrownBy(() -> fixture.bridge.doubleClickRow("plant.table", 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside table");
        assertThatThrownBy(() -> fixture.bridge.activateMenu(
                List.of("View", "Missing command")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("found 0");
        assertThatThrownBy(() -> fixture.bridge.invokeShortcut("ctrl shift alt nonsense"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid Swing keystroke");

        fixture.bridge.detach();
        assertThat(fixture.bridge.snapshot(SwingHarnessPlugin.ID).metadata())
                .containsEntry("ready", false);
    }

    @Test
    @DisplayName("publishes and opens exact nested routes")
    void publishesAndNavigatesNestedRoutes() {
        JTabbedPane reportTabs = new JTabbedPane();
        reportTabs.getAccessibleContext().setAccessibleName("Report evidence tabs");
        reportTabs.addTab("Summary", new JPanel());
        reportTabs.addTab("Products", new JPanel());
        JTabbedPane primary = new JTabbedPane();
        primary.getAccessibleContext().setAccessibleName("Farm workflow navigation");
        primary.addTab("Dashboard", new JPanel());
        primary.addTab("Reports", reportTabs);
        JRootPane root = new JRootPane();
        root.setContentPane(primary);

        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(root, title -> {
            for (int index = 0; index < primary.getTabCount(); index++) {
                if (title.equals(primary.getTitleAt(index))) {
                    primary.setSelectedIndex(index);
                    return true;
                }
            }
            return false;
        });
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);

        assertThat(plugin.observe().metadata().get("routes")).isEqualTo(List.of(
                List.of("Dashboard"),
                List.of("Reports", "Summary"),
                List.of("Reports", "Products")));
        plugin.execute(command("route", "navigate-route",
                Map.of("path", List.of("Reports", "Products"))));

        assertThat(plugin.observe().route()).containsExactly("Reports", "Products");
        assertThat(plugin.execute(command("missing", "navigate-route",
                Map.of("path", List.of("Reports", "Missing")))).output())
                .containsEntry("status", "FAILED")
                .containsEntry("failureType", "IllegalArgumentException");

        reportTabs.setSelectedIndex(0);
        reportTabs.setEnabledAt(1, false);
        assertThat(plugin.execute(command("disabled", "navigate-route",
                Map.of("path", List.of("Reports", "Products")))).output())
                .containsEntry("status", "FAILED")
                .containsEntry("failureType", "IllegalArgumentException");
        assertThat(plugin.observe().route()).containsExactly("Reports", "Summary");
    }

    private static HarnessCommand command(
            String requestId, String action, Map<String, Object> arguments) {
        return new HarnessCommand(requestId, action, arguments);
    }

    private static Fixture fixture() {
        JTextField field = new JTextField("private crop note");
        field.getAccessibleContext().setAccessibleName("Crop draft");
        AtomicInteger submits = new AtomicInteger();
        field.addActionListener(event -> submits.incrementAndGet());
        JPasswordField password = new JPasswordField("never-expose");
        password.getAccessibleContext().setAccessibleName("Desktop password");
        JComboBox<Choice> combo = new JComboBox<>(new Choice[]{
                new Choice("all", "All plants"), new Choice("native", "Native rose")});
        combo.getAccessibleContext().setAccessibleName("Crop choice");
        combo.setRenderer((list, value, index, selected, focus) ->
                new JLabel(value == null ? "" : value.label()));
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Camas"}, {"Nootka Rose"}}, new Object[]{"Plant"}));
        table.setName("plant.table");
        table.getAccessibleContext().setAccessibleName("Plant table");
        table.setAutoCreateRowSorter(true);
        AtomicInteger doubleClicks = new AtomicInteger();
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    doubleClicks.incrementAndGet();
                }
            }
        });
        JList<String> list = new JList<>(new String[]{"Harvest", "Pack"});
        list.getAccessibleContext().setAccessibleName("Task list");
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        spinner.getAccessibleContext().setAccessibleName("Stem count");
        JSlider slider = new JSlider(0, 10, 2);
        slider.getAccessibleContext().setAccessibleName("Stem angle");
        AtomicInteger clicks = new AtomicInteger();
        JButton apply = new JButton("Apply draft");
        apply.addActionListener(event -> clicks.incrementAndGet());

        JPanel content = new JPanel();
        content.add(field);
        content.add(password);
        content.add(combo);
        content.add(new JScrollPane(table));
        content.add(new JScrollPane(list));
        content.add(spinner);
        content.add(slider);
        content.add(apply);
        JTabbedPane tabs = new JTabbedPane();
        tabs.getAccessibleContext().setAccessibleName("Workspaces");
        tabs.addTab("Reference", content);
        tabs.addTab("Details", new JPanel());
        JPanel contentRoot = new JPanel(new BorderLayout());
        contentRoot.add(tabs);
        AtomicInteger menuActivations = new AtomicInteger();
        JMenuItem refresh = new JMenuItem("Refresh farm");
        refresh.addActionListener(event -> menuActivations.incrementAndGet());
        JMenu view = new JMenu("View");
        view.add(refresh);
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(view);
        JRootPane root = new JRootPane();
        root.setContentPane(contentRoot);
        root.setJMenuBar(menuBar);
        root.setSize(640, 480);
        root.doLayout();
        AtomicInteger shortcutActivations = new AtomicInteger();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F5"), "refresh-farm");
        root.getActionMap().put("refresh-farm", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                shortcutActivations.incrementAndGet();
            }
        });
        AtomicReference<String> selectedTab = new AtomicReference<>();
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(root, title -> {
            selectedTab.set(title);
            return true;
        });
        return new Fixture(bridge, field, password, combo, table, list, spinner, slider,
                tabs, clicks, submits, doubleClicks, menuActivations,
                shortcutActivations, selectedTab);
    }

    private record Choice(String id, String label) {
        @Override public String toString() {
            return id;
        }
    }

    private record Fixture(SwingHarnessBridge bridge, JTextField field,
                           JPasswordField password, JComboBox<Choice> combo,
                           JTable table, JList<String> list, JSpinner spinner,
                           JSlider slider, JTabbedPane tabs, AtomicInteger clicks,
                           AtomicInteger submits, AtomicInteger doubleClicks,
                           AtomicInteger menuActivations, AtomicInteger shortcutActivations,
                           AtomicReference<String> selectedTab) {
    }
}
