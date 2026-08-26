package com.flowerfarm.gui;

import com.flowerfarm.cli.CliCommandRouter;
import com.flowerfarm.gui.tabs.FlowerFarmTab;
import com.flowerfarm.gui.tabs.InventoryWorkspaceTab;
import com.flowerfarm.gui.tabs.ReferenceTab;
import com.flowerfarm.gui.view.CrmCustomerSearchView;
import com.flowerfarm.gui.view.HarvestBatchView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Extracted desktop and CLI routing")
class InteractionRoutingTest {

    @Test
    @DisplayName("tab navigator owns population, refresh, named selection, and shortcuts")
    void navigatesTabs() {
        TestTab dashboard = new TestTab("Dashboard");
        TestTab harvest = new TestTab("Harvest Log");
        JTabbedPane pane = new JTabbedPane();
        FarmTabNavigator navigator = new FarmTabNavigator(pane, List.of(dashboard, harvest));

        navigator.populate();
        assertThat(pane.getTabCount()).isEqualTo(2);
        assertThat(pane.getIconAt(0)).isNotNull();
        assertThat(pane.getClientProperty("JTabbedPane.tabType")).isEqualTo("card");
        assertThat(pane.getClientProperty("JTabbedPane.minimumTabWidth")).isEqualTo(190);
        assertThat(pane.getTabComponentAt(0).getName()).isEqualTo("farm.navigationItem");
        assertThat(pane.getTabComponentAt(0).getAccessibleContext().getAccessibleName())
                .isEqualTo("Dashboard navigation item, shortcut Alt+1");
        assertThat(dashboard.initialized).isEqualTo(1);
        assertThat(navigator.select("Harvest Log")).isTrue();
        assertThat(pane.getSelectedIndex()).isEqualTo(1);
        navigator.refreshAll();
        assertThat(dashboard.refreshed).isEqualTo(1);
        assertThat(harvest.refreshed).isGreaterThanOrEqualTo(1);

        JRootPane root = new JRootPane();
        AtomicInteger refreshes = new AtomicInteger();
        navigator.installShortcuts(root, refreshes::incrementAndGet);
        Action refresh = root.getActionMap().get("refreshAll");
        refresh.actionPerformed(null);
        assertThat(refreshes).hasValue(1);
        assertThat(root.getActionMap().get("selectTab0")).isNotNull();
        assertThat(pane.getMnemonicAt(0)).isEqualTo('D');
    }

    @Test
    @DisplayName("reference workspace preserves old deep links without sidebar clutter")
    void routesReferenceDeepLinks() {
        TestTab dashboard = new TestTab("Dashboard");
        ReferenceTab reference = new ReferenceTab(
                new TestTab("Rose Varieties"),
                new TestTab("Pricing Info"),
                new TestTab("Rose Visualizer"));
        JTabbedPane pane = new JTabbedPane();
        FarmTabNavigator navigator = new FarmTabNavigator(pane, List.of(dashboard, reference));

        navigator.populate();

        assertThat(pane.getTabCount()).isEqualTo(2);
        assertThat(navigator.select("Rose Visualizer")).isTrue();
        assertThat(pane.getSelectedIndex()).isEqualTo(1);
        JTabbedPane referenceTabs = findTabbedPane(reference.getUIComponent());
        assertThat(referenceTabs.getSelectedIndex()).isEqualTo(2);
        assertThat(referenceTabs.getTitleAt(2)).isEqualTo("Growth visualizer");
        assertThat(referenceTabs.getMnemonicAt(0)).isEqualTo('R');
        assertThat(referenceTabs.getMnemonicAt(2)).isEqualTo('G');
    }

    @Test
    @DisplayName("inventory workspace preserves stock and add-item deep links")
    void routesInventoryDeepLinks() {
        InventoryWorkspaceTab inventory = new InventoryWorkspaceTab(
                new TestTab("Inventory"), new TestTab("Add Item"));
        JTabbedPane pane = new JTabbedPane();
        FarmTabNavigator navigator = new FarmTabNavigator(pane, List.of(inventory));

        navigator.populate();
        assertThat(navigator.select("Add Item")).isTrue();
        JTabbedPane inventoryTabs = findTabbedPane(inventory.getUIComponent());
        assertThat(inventoryTabs.getSelectedIndex()).isEqualTo(1);
        assertThat(inventoryTabs.getTitleAt(1)).isEqualTo("Add item");
        assertThat(inventoryTabs.getMnemonicAt(0)).isEqualTo('S');
        assertThat(inventoryTabs.getMnemonicAt(1)).isEqualTo('A');

        assertThat(navigator.select("Inventory")).isTrue();
        assertThat(inventoryTabs.getSelectedIndex()).isZero();
    }

    @Test
    @DisplayName("CLI router keeps ordered commands and exit behavior outside the runner")
    void routesCliCommands() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        AtomicInteger calls = new AtomicInteger();
        CliCommandRouter router = new CliCommandRouter(output)
                .register("1", "Run", scanner -> calls.incrementAndGet())
                .exit("2", "Exit", scanner -> calls.addAndGet(10));

        router.printMenu();
        assertThat(router.dispatch("1", new Scanner(""))).isTrue();
        assertThat(router.dispatch("2", new Scanner(""))).isFalse();
        assertThat(router.dispatch("missing", new Scanner(""))).isTrue();

        assertThat(router.size()).isEqualTo(2);
        assertThat(calls).hasValue(11);
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("1) Run", "2) Exit", "Unknown option");
        assertThatThrownBy(() -> router.register("1", "Again", scanner -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    @DisplayName("extracted view components retain actions and accessible input contracts")
    void composesInteractionViews() throws Exception {
        AtomicInteger searches = new AtomicInteger();
        AtomicInteger batches = new AtomicInteger();
        CrmCustomerSearchView[] searchView = new CrmCustomerSearchView[1];
        HarvestBatchView[] batchView = new HarvestBatchView[1];

        SwingUtilities.invokeAndWait(() -> {
            searchView[0] = new CrmCustomerSearchView(searches::incrementAndGet);
            batchView[0] = new HarvestBatchView(batches::incrementAndGet);
            searchView[0].searchField().postActionEvent();
            batchView[0].logButton().doClick();
            batchView[0].sampleButton().doClick();
        });

        assertThat(searches).hasValue(1);
        assertThat(batches).hasValue(1);
        assertThat(batchView[0].batchArea().getAccessibleContext().getAccessibleName())
                .isEqualTo("Batch harvest lines");
        assertThat(batchView[0].batchArea().getText()).contains("Crop,Quantity", "Dahlia mix");
        assertThat(batchView[0].statusLabel().getAccessibleContext().getAccessibleName())
                .isEqualTo("Batch harvest readiness");
    }

    private static final class TestTab implements FlowerFarmTab {
        private final String title;
        private int initialized;
        private int refreshed;

        private TestTab(String title) {
            this.title = title;
        }

        @Override public String getTabTitle() {
            return title;
        }

        @Override public JComponent getUIComponent() {
            return new JPanel();
        }

        @Override public void initialize() {
            initialized++;
        }

        @Override public void refreshData() {
            refreshed++;
        }
    }

    private static JTabbedPane findTabbedPane(JComponent root) {
        if (root instanceof JTabbedPane tabs) {
            return tabs;
        }
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JComponent component) {
                JTabbedPane found = findTabbedPane(component);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
