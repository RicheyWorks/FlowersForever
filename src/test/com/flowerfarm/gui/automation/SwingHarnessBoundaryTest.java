package com.flowerfarm.gui.automation;

import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingHarnessBoundaryTest {

    @Test
    void reportsIndividualValueClippingEvenWhenCollectionsAreSmall() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String longText = "x".repeat(501);
            JComboBox<String> combo = new JComboBox<>(new String[]{"Short", longText});
            combo.getAccessibleContext().setAccessibleName("Options");
            Map<String, Object> option = attach(combo).readControl("Options");
            assertThat(option).containsEntry("truncated", true).containsEntry("selected", "Short");
            assertThat(option.get("options")).isEqualTo(List.of("Short", "x".repeat(500)));

            JTable table = new JTable(new Object[][]{{longText}}, new Object[]{longText});
            table.getAccessibleContext().setAccessibleName("Evidence");
            Map<String, Object> cells = attach(table).readControl("Evidence");
            assertThat(cells).containsEntry("truncated", true);
            assertThat(cells.get("rows")).isEqualTo(List.of(List.of("x".repeat(500))));
            assertThat(cells.get("columns")).isEqualTo(List.of("x".repeat(500)));

            JList<String> list = new JList<>(new String[]{longText});
            list.getAccessibleContext().setAccessibleName("Items");
            Map<String, Object> items = attach(list).readControl("Items");
            assertThat(items).containsEntry("truncated", true);
            assertThat(items.get("items")).isEqualTo(List.of("x".repeat(500)));

            JButton button = new JButton(longText);
            button.getAccessibleContext().setAccessibleName("Button");
            assertThat(attach(button).readControl("Button"))
                    .containsEntry("label", "x".repeat(500)).containsEntry("truncated", true);

            JTabbedPane tabs = new JTabbedPane();
            tabs.getAccessibleContext().setAccessibleName("Tabs");
            tabs.addTab(longText, new JPanel());
            Map<String, Object> tabValues = attach(tabs).readControl("Tabs");
            assertThat(tabValues).containsEntry("selected", "x".repeat(500))
                    .containsEntry("truncated", true);
            assertThat(tabValues.get("tabs")).isEqualTo(List.of("x".repeat(500)));
        });
    }

    @Test
    void boundsSelectedRowsAndTabCollections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = new JTable(new DefaultTableModel(205, 1));
            table.getAccessibleContext().setAccessibleName("Table");
            table.setRowSelectionInterval(0, 204);
            Map<String, Object> tableValue = attach(table).readControl("Table");
            assertThat(tableValue).containsEntry("truncated", true);
            assertThat(tableValue.get("selectedRows")).asList().hasSize(200).endsWith(199);

            JList<Integer> list = new JList<>(java.util.stream.IntStream.range(0, 205)
                    .boxed().toArray(Integer[]::new));
            list.getAccessibleContext().setAccessibleName("List");
            list.setSelectionInterval(0, 204);
            Map<String, Object> listValue = attach(list).readControl("List");
            assertThat(listValue).containsEntry("truncated", true);
            assertThat(listValue.get("selectedRows")).asList().hasSize(200).endsWith(199);

            JTabbedPane tabs = new JTabbedPane();
            tabs.getAccessibleContext().setAccessibleName("Tabs");
            for (int index = 0; index < 205; index++) {
                tabs.addTab("Tab " + index, new JPanel());
            }
            Map<String, Object> tabValue = attach(tabs).readControl("Tabs");
            assertThat(tabValue).containsEntry("truncated", true).containsEntry("tabCount", 205);
            assertThat(tabValue.get("tabs")).asList().hasSize(200).endsWith("Tab 199");
        });
    }

    @Test
    void refusesDisabledOrHiddenMenuAncestorsBeforeExecuting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger activations = new AtomicInteger();
            JMenuItem command = new JMenuItem("Export");
            command.addActionListener(event -> activations.incrementAndGet());
            JMenu submenu = new JMenu("Reports");
            submenu.add(command);
            JMenu menu = new JMenu("Farm");
            menu.add(submenu);
            JMenuBar menuBar = new JMenuBar();
            menuBar.add(menu);
            JRootPane root = new JRootPane();
            root.setJMenuBar(menuBar);
            SwingHarnessBridge bridge = attach(root);
            List<String> path = List.of("Farm", "Reports", "Export");

            menu.setEnabled(false);
            assertThatThrownBy(() -> bridge.activateMenu(path))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Farm").hasMessageContaining("disabled");
            menu.setEnabled(true);
            submenu.setVisible(false);
            assertThatThrownBy(() -> bridge.activateMenu(path))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Reports").hasMessageContaining("hidden");
            submenu.setVisible(true);
            submenu.setEnabled(false);
            assertThatThrownBy(() -> bridge.activateMenu(path))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Reports").hasMessageContaining("disabled");
            submenu.setEnabled(true);
            menuBar.setVisible(false);
            assertThatThrownBy(() -> bridge.activateMenu(path))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Menu bar").hasMessageContaining("hidden");
            assertThat(activations).hasValue(0);
            menuBar.setVisible(true);
            bridge.activateMenu(path);
            assertThat(activations).hasValue(1);
        });
    }

    @Test
    void rejectsDisjointSelectionWithoutDiscardingCurrentRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = table();
            table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            table.setRowSelectionInterval(1, 1);
            SwingHarnessBridge bridge = attach(table);

            assertThatThrownBy(() -> bridge.selectRows("Harvest rows", List.of(0, 2)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("contiguous");
            assertThat(table.getSelectedRows()).containsExactly(1);

            List<List<Integer>> settledSelections = new ArrayList<>();
            table.getSelectionModel().addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    settledSelections.add(java.util.Arrays.stream(table.getSelectedRows())
                            .boxed().toList());
                }
            });
            bridge.selectRows("Harvest rows", List.of(2, 0, 1, 2));
            assertThat(table.getSelectedRows()).containsExactly(0, 1, 2);
            assertThat(settledSelections).containsExactly(List.of(0, 1, 2));
        });
    }

    @Test
    void deduplicatesRepeatedSingleRowSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = table();
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            attach(table).selectRows("Harvest rows", List.of(1, 1));
            assertThat(table.getSelectedRows()).containsExactly(1);
        });
    }

    @Test
    void respectsColumnsWithSortingDisabled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = table();
            TableRowSorter<DefaultTableModel> sorter =
                    new TableRowSorter<>((DefaultTableModel) table.getModel());
            sorter.setSortable(0, false);
            table.setRowSorter(sorter);
            assertThatThrownBy(() -> attach(table).sortTable(
                    "Harvest rows", "Crop", "ASCENDING"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not sortable");
            assertThat(sorter.getSortKeys()).isEmpty();
            assertThat(table.getValueAt(0, 0)).isEqualTo("Zinnia");
        });
    }

    @Test
    void navigatesRepeatedTitlesWithinTheirExactRoute() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane reports = new JTabbedPane();
            reports.addTab("Summary", new JPanel());
            reports.addTab("Reports", new JPanel());
            JTabbedPane primary = new JTabbedPane();
            primary.addTab("Dashboard", new JPanel());
            primary.addTab("Reports", reports);
            SwingHarnessBridge bridge = new SwingHarnessBridge();
            bridge.attach(primary, title -> {
                primary.setSelectedIndex(primary.indexOfTab(title));
                return true;
            });
            bridge.navigateRoute(List.of("Reports", "Reports"));
            assertThat(bridge.snapshot(SwingHarnessPlugin.ID).route())
                    .containsExactly("Reports", "Reports");

            primary.setSelectedIndex(0);
            reports.setEnabled(false);
            assertThatThrownBy(() -> bridge.navigateRoute(List.of("Reports", "Reports")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("disabled");
            assertThat(primary.getSelectedIndex()).isZero();
        });
    }

    private static JTable table() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Zinnia"}, {"Aster"}, {"Rose"}}, new Object[]{"Crop"}));
        table.getAccessibleContext().setAccessibleName("Harvest rows");
        return table;
    }

    private static SwingHarnessBridge attach(javax.swing.JComponent root) {
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(root, title -> false);
        return bridge;
    }
}
