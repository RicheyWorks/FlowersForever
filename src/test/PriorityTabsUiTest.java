package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingFileHarness;
import com.flowerfarm.gui.presenter.CrmPresenter;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Priority workflow modern UI")
class PriorityTabsUiTest {

    @Test
    @DisplayName("dashboard uses the shared page hierarchy and semantic actions")
    void dashboardUsesSharedHierarchy() throws Exception {
        DashboardTab tab = new DashboardTab(
                null, null, null, null, null, null, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> root[0] = tab.getUIComponent());

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "farm.pageHeader")).hasSize(1);
        assertThat(findNamed(root[0], "farm.section")).hasSize(2);
        assertThat(button(root[0], "Refresh overview").getAccessibleContext().getAccessibleName())
                .isEqualTo("Refresh overview");
        assertThat(button(root[0], "Morning briefing").isEnabled()).isTrue();
        assertThat(button(root[0], "Open priority").isEnabled()).isFalse();
        assertThat(button(root[0], "Open tool").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "dashboard.moreTools"))
                .singleElement()
                .isInstanceOf(JComboBox.class);
        assertThat(findNamed(root[0], "dashboard.priorityQueue"))
                .singleElement()
                .isInstanceOf(JComboBox.class)
                .satisfies(component -> assertThat(component.isEnabled()).isFalse());
        assertThat(findNamed(root[0], "dashboard.priorityDetail"))
                .singleElement()
                .satisfies(component -> assertThat(((JLabel) component).getText())
                        .contains("Refresh the overview", "ranked work queue"));
        assertThat(findNamed(root[0], "dashboard.dailySheetStatus"))
                .singleElement()
                .satisfies(component -> assertThat(((JLabel) component).getText())
                        .isEqualTo("Daily sheets ready"));
        assertThat(findNamed(root[0], "dashboard.workspace"))
                .singleElement()
                .isInstanceOf(JTabbedPane.class)
                .satisfies(component -> assertThat(((JTabbedPane) component).getTabCount())
                        .isEqualTo(2));
    }

    @Test
    @DisplayName("dashboard quick actions use retained harvest and market workflows")
    void dashboardQuickActionsUseRetainedWorkflows() throws Exception {
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                null, null, null, null, null, null, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> root[0] = tab.getUIComponent());
        List<JButton> harvestButtons = find(root[0], JButton.class).stream()
                .filter(candidate -> "Log harvest".equals(candidate.getText()))
                .toList();
        assertThat(harvestButtons).hasSize(2)
                .allSatisfy(candidate -> assertThat(candidate.getToolTipText()).isNotBlank());
        assertThat(button(root[0], "Market packing").getToolTipText())
                .contains("packing list", "pick sheet");

        SwingUtilities.invokeAndWait(() -> harvestButtons.get(0).doClick());
        assertThat(host.selectedTab).isEqualTo("Harvest Log");
        assertThat(host.harvestEntryReturnTab).isEqualTo("Dashboard");
        SwingUtilities.invokeAndWait(() -> harvestButtons.get(1).doClick());
        assertThat(host.selectedTab).isEqualTo("Harvest Log");
        assertThat(host.harvestEntryReturnTab).isEqualTo("Dashboard");
        SwingUtilities.invokeAndWait(() -> button(root[0], "Market packing").doClick());
        assertThat(host.selectedTab).isEqualTo("Market Day");
        assertThat(host.marketDayReturnTab).isEqualTo("Dashboard");
    }

    @Test
    @DisplayName("dashboard ranks the most urgent condition and routes to its workflow")
    void dashboardRoutesTopPriority() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        Item low = new Item("Nootka Rose", "Flowers/Plants",
                4.5, "stems", 1.0, 2, "");
        when(inventory.getAllItems()).thenReturn(List.of(low));
        when(inventory.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(1, 9, 2, 1, 2, 10));
        when(harvest.totalQuantityLast7Days()).thenReturn(0.0);
        when(harvest.totalQuantityPrior7Days()).thenReturn(8.0);
        when(harvest.dailyQuantitiesLast7Days()).thenReturn(new double[7]);
        when(orders.weekRevenueSummary()).thenReturn(new OrderService.WeekRevenueSummary(
                LocalDate.now().minusDays(6), LocalDate.now(), 0, 0, 50, 0, 0, 1));
        when(orders.dailyRevenueLast7Days()).thenReturn(new double[7]);
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                inventory, harvest, orders, null, null, null, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitButtonEnabled(root[0], "Open priority");
        SwingUtilities.invokeAndWait(() -> button(root[0], "Open priority").doClick());

        assertThat(findNamed(root[0], "dashboard.priorityQueue"))
                .singleElement()
                .isInstanceOf(JComboBox.class)
                .satisfies(component -> {
                    JComboBox<?> queue = (JComboBox<?>) component;
                    assertThat(queue.getItemCount()).isEqualTo(3);
                    assertThat(queue.getItemAt(0).toString())
                            .contains("Restock Nootka Rose", "2 left");
                    assertThat(queue.getItemAt(1).toString())
                            .contains("No harvest logged");
                    assertThat(queue.getItemAt(2).toString())
                            .contains("Confirm 1 draft order");
                });
        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.inventoryProducts).containsExactly("Nootka Rose");
        assertThat(host.inventoryReturnTab).isEqualTo("Dashboard");
        assertThat(host.inventoryReturnLabel).isEqualTo("Dashboard");

        SwingUtilities.invokeAndWait(() -> {
                    JComboBox<?> queue = (JComboBox<?>) findNamed(
                    root[0], "dashboard.priorityQueue").get(0);
            queue.setSelectedIndex(1);
            button(root[0], "Open priority").doClick();
        });

        assertThat(findNamed(root[0], "dashboard.priorityStatus"))
                .singleElement()
                .satisfies(component -> assertThat(((javax.swing.JLabel) component).getText())
                        .contains("Priority 2 of 3", "Follow-up"));
        assertThat(findNamed(root[0], "dashboard.priorityDetail"))
                .singleElement()
                .satisfies(component -> {
                    JLabel detail = (JLabel) component;
                    assertThat(detail.getText())
                            .contains("Do next", "No harvest logged", "Harvest Log");
                    assertThat(detail.getAccessibleContext().getAccessibleDescription())
                            .contains("follow-up priority", "Opens Harvest Log");
                });
        assertThat(host.selectedTab).isEqualTo("Harvest Log");
        assertThat(host.harvestEntryReturnTab).isEqualTo("Dashboard");
        assertThat(host.harvestEntryReturnLabel).isEqualTo("Dashboard");

        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> queue = (JComboBox<?>) findNamed(
                    root[0], "dashboard.priorityQueue").get(0);
            queue.setSelectedIndex(2);
            button(root[0], "Open priority").doClick();
        });
        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderStatus).isEqualTo("DRAFT");
        assertThat(host.orderStatusReturnTab).isEqualTo("Dashboard");
        assertThat(host.orderStatusReturnLabel).isEqualTo("Dashboard");
    }

    @Test
    @DisplayName("dashboard urgent watering priority opens the real irrigation workspace")
    void dashboardRoutesUrgentWateringPriority() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        IrrigationAdvisorService irrigation = mock(IrrigationAdvisorService.class);
        when(inventory.getAllItems()).thenReturn(List.of());
        when(inventory.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(0, 0, 0, 0, 0, 0));
        when(harvest.totalQuantityLast7Days()).thenReturn(20.0);
        when(harvest.totalQuantityPrior7Days()).thenReturn(20.0);
        when(harvest.dailyQuantitiesLast7Days()).thenReturn(new double[7]);
        when(orders.weekRevenueSummary()).thenReturn(new OrderService.WeekRevenueSummary(
                LocalDate.now().minusDays(6), LocalDate.now(), 0, 0, 0, 0, 0, 0));
        when(orders.dailyRevenueLast7Days()).thenReturn(new double[7]);
        when(irrigation.adviseClimatology()).thenReturn(
                new IrrigationAdvisorService.IrrigationAdvice(
                        "Port Orchard, WA", "CLIMATOLOGY", LocalDate.now().toString(),
                        IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                        IrrigationAdvisorService.Priority.HIGH,
                        "Deep-water active beds before the afternoon heat.",
                        List.of("Run drip irrigation before 10 AM."), List.of("Field A"),
                        null, null, null, null, null, "Dry maritime summer pattern."));
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                inventory, harvest, orders, irrigation, null, null, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitButtonEnabled(root[0], "Open priority");
        SwingUtilities.invokeAndWait(() -> button(root[0], "Open priority").doClick());

        assertThat(host.selectedTab).isEqualTo("Irrigation & Care");
        assertThat(host.irrigationReturnTab).isEqualTo("Dashboard");
        assertThat(host.irrigationReturnLabel).isEqualTo("Dashboard");
        assertThat(((JLabel) findNamed(root[0], "dashboard.priorityDetail").get(0)).getText())
                .contains("Watering high", "Irrigation & Care");
    }

    @Test
    @DisplayName("dashboard refresh runs off the UI thread and queues one fresh snapshot")
    void dashboardRefreshIsResponsiveAndCoalescesRequests() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        Item item = new Item("Nootka Rose", "Flowers/Plants",
                4.5, "stems", 1.0, 12, "");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        AtomicInteger loads = new AtomicInteger();
        when(inventory.getAllItems()).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            if (loads.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            }
            return List.of(item);
        });
        when(inventory.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(1, 54, 12, 0, 12, 12));
        when(harvest.totalQuantityLast7Days()).thenReturn(24.0);
        when(harvest.totalQuantityPrior7Days()).thenReturn(20.0);
        when(harvest.dailyQuantitiesLast7Days()).thenReturn(new double[7]);
        when(harvest.productionByBedLast7Days()).thenReturn(
                new HarvestService.BedProductionReport(
                        LocalDate.now().minusDays(6).toString(), LocalDate.now().toString(),
                        0, 0, 0, List.of(), "No bed production"));
        when(orders.weekRevenueSummary()).thenReturn(new OrderService.WeekRevenueSummary(
                LocalDate.now().minusDays(6), LocalDate.now(), 80, 0, 0, 80, 1, 0));
        when(orders.realizedRevenuePrior7Days()).thenReturn(50.0);
        when(orders.dailyRevenueLast7Days()).thenReturn(new double[7]);
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                inventory, harvest, orders, null, null, null, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startOverviewRefresh();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Refresh overview").isEnabled()).isFalse();
            assertThat(button(root[0], "Open priority").isEnabled()).isFalse();
            assertThat(findNamed(root[0], "dashboard.priorityQueue").get(0).isEnabled())
                    .isFalse();
            assertThat(((JLabel) findNamed(
                    root[0], "dashboard.priorityDetail").get(0)).getText())
                    .contains("snapshot is refreshing", "temporarily locked");
            tab.startOverviewRefresh();
            assertThat(((JLabel) findNamed(
                    root[0], "dashboard.overviewStatus").get(0)).getText())
                    .contains("Refresh queued");
        });
        release.countDown();
        awaitButtonEnabled(root[0], "Refresh overview");

        assertThat(calledOnUiThread).isFalse();
        assertThat(loads).hasValue(2);
        verify(inventory, times(2)).getAllItems();
        JLabel result = (JLabel) findNamed(root[0], "dashboard.overviewStatus").get(0);
        assertThat(result.getText()).startsWith("Updated ").endsWith("· 7-day view");
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("updated successfully", "completed orders only");
        assertThat(host.status).isEqualTo("Dashboard overview refreshed.");
    }

    @Test
    @DisplayName("dashboard refresh failure keeps prior values and exposes an inline retry")
    void dashboardRefreshFailureIsRecoverable() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(inventory.getAllItems()).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            throw new IllegalStateException("inventory database unavailable");
        });
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                inventory, harvest, orders, null, null, null, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startOverviewRefresh();
        });
        awaitLabel(root[0], "Refresh failed · retry");

        assertThat(calledOnUiThread).isFalse();
        assertThat(button(root[0], "Refresh overview").isEnabled()).isTrue();
        JLabel result = (JLabel) findNamed(root[0], "dashboard.overviewStatus").get(0);
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("inventory database unavailable", "Existing dashboard values were kept");
        assertThat(((JLabel) findNamed(root[0], "dashboard.priorityStatus").get(0)).getText())
                .isEqualTo("Priorities unavailable · retry");
        assertThat(button(root[0], "Open priority").isEnabled()).isFalse();
        assertThat(host.status).contains("failed", "Existing values were kept", "retry");
    }

    @Test
    @DisplayName("dashboard restores a clearly stale last-good priority after refresh failure")
    void dashboardRestoresLastGoodPriorityAfterRefreshFailure() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        Item low = new Item("Nootka Rose", "Flowers/Plants",
                4.5, "stems", 1.0, 2, "");
        AtomicInteger loads = new AtomicInteger();
        when(inventory.getAllItems()).thenAnswer(invocation -> {
            if (loads.incrementAndGet() == 1) {
                return List.of(low);
            }
            throw new IllegalStateException("inventory database unavailable");
        });
        when(inventory.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(1, 9, 2, 1, 2, 10));
        when(harvest.totalQuantityLast7Days()).thenReturn(8.0);
        when(harvest.totalQuantityPrior7Days()).thenReturn(8.0);
        when(harvest.dailyQuantitiesLast7Days()).thenReturn(new double[7]);
        when(harvest.productionByBedLast7Days()).thenReturn(
                new HarvestService.BedProductionReport(
                        LocalDate.now().minusDays(6).toString(), LocalDate.now().toString(),
                        0, 0, 0, List.of(), "No bed production"));
        when(orders.weekRevenueSummary()).thenReturn(new OrderService.WeekRevenueSummary(
                LocalDate.now().minusDays(6), LocalDate.now(), 0, 0, 0, 0, 0, 0));
        when(orders.dailyRevenueLast7Days()).thenReturn(new double[7]);
        DashboardTab tab = new DashboardTab(
                inventory, harvest, orders, null, null, null, null, new TrackingHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startOverviewRefresh();
        });
        awaitButtonEnabled(root[0], "Open priority");

        SwingUtilities.invokeAndWait(tab::startOverviewRefresh);
        awaitLabel(root[0], "Refresh failed · retry");

        assertThat(loads).hasValue(2);
        assertThat(button(root[0], "Open priority").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "dashboard.priorityQueue").get(0).isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "dashboard.priorityStatus").get(0)).getText())
                .contains("Priority 1 of 1", "last good snapshot");
        JLabel detail = (JLabel) findNamed(root[0], "dashboard.priorityDetail").get(0);
        assertThat(detail.getText())
                .contains("Restock Nootka Rose", "verify conditions in the workspace");
        assertThat(detail.getAccessibleContext().getAccessibleDescription())
                .contains("snapshot refresh failed", "verify current conditions");
    }

    @Test
    @DisplayName("day closeout separates operational follow-ups in resolution order")
    void dayCloseoutRanksResolutionHandoffs() {
        DayCloseoutService.DayCloseout blocked = new DayCloseoutService.DayCloseout(
                LocalDate.now(), "17:00", "Kitsap", 1, 80, List.of(),
                1, 2, 60, 0, 0, 40, 80, 60, 500,
                10, List.of(new DayCloseoutService.LowStockLine(
                        "Nootka Rose", 2, "stems")), List.of(), "Closeout");
        DayCloseoutService.DayCloseout ready = new DayCloseoutService.DayCloseout(
                LocalDate.now(), "17:00", "Kitsap", 1, 80, List.of(),
                0, 0, 0, 24, 1, 40, 80, 0, 500,
                10, List.of(), List.of(), "Closeout");

        assertThat(DashboardTab.closeoutActions(blocked))
                .extracting(DashboardTab.CloseoutAction::targetTab)
                .containsExactly("Market Day", "Harvest Log", "Inventory",
                        "Customers & Orders");
        assertThat(DashboardTab.closeoutActions(blocked).get(2).navigationMessage())
                .contains("closeout low-stock queue");
        assertThat(DashboardTab.closeoutActions(blocked).get(2).inventoryProducts())
                .containsExactly("Nootka Rose");
        assertThat(DashboardTab.closeoutActions(blocked).get(3).orderStatus())
                .isEqualTo("CANCELLED");
        assertThat(DashboardTab.closeoutActions(ready)).isEmpty();

        DashboardTab.CloseoutActionChooser chooser =
                DashboardTab.buildCloseoutActionChooser(
                        DashboardTab.closeoutActions(blocked));
        assertThat(chooser.queue().getItemCount()).isEqualTo(4);
        assertThat(chooser.queue().getItemAt(0).toString())
                .contains("Finish 2 confirmed order", "Market Day");
        chooser.queue().setSelectedIndex(2);
        assertThat(chooser.selectedAction().targetTab()).isEqualTo("Inventory");
        assertThat(findNamed(chooser.component(), "dashboard.closeoutFollowUpDetail"))
                .singleElement()
                .satisfies(component -> {
                    JLabel detail = (JLabel) component;
                    assertThat(detail.getText())
                            .contains("Opens Inventory", "closeout low-stock queue");
                    assertThat(detail.getAccessibleContext().getAccessibleDescription())
                            .contains("Count or restock", "Opened Inventory");
                });
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                null, null, null, null, null, null, null, host);
        tab.startCloseoutAction(chooser.selectedAction());
        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.status)
                .contains("closeout low-stock queue", "Nootka Rose");

        chooser.queue().setSelectedIndex(3);
        tab.startCloseoutAction(chooser.selectedAction());
        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderStatus).isEqualTo("CANCELLED");
        assertThat(host.orderStatusReturnTab).isEqualTo("Dashboard");
        assertThat(host.orderStatusReturnLabel).isEqualTo("Dashboard");
    }

    @Test
    @DisplayName("morning briefing ranks startup work from its operational snapshot")
    void morningBriefingRanksStartupHandoffs() {
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan market =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 2, 120,
                        List.of(), List.of(), 1, "Packing");
        IrrigationAdvisorService.IrrigationAdvice irrigation =
                new IrrigationAdvisorService.IrrigationAdvice(
                        "Port Orchard", "CLIMATOLOGY", day.toString(),
                        IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                        IrrigationAdvisorService.Priority.HIGH,
                        "Water before heat", List.of(), List.of(),
                        0.0, 1.0, 1.0, 80.0, 55.0, "Dry");
        MorningBriefingService.MorningBriefing busy =
                new MorningBriefingService.MorningBriefing(
                        day, "06:00", "Kitsap", 0, 0, 120,
                        market, null, irrigation,
                        List.of(new MorningBriefingService.LowStockLine(
                                "Nootka Rose", 2, "stems")),
                        10, List.of(), "Briefing");
        MorningBriefingService.MorningBriefing ready =
                new MorningBriefingService.MorningBriefing(
                        day, "06:00", "Kitsap", 40, 80, 0,
                        new MarketDayPackingService.MarketDayPlan(
                                day, day, day, "CONFIRMED", 0, 0,
                                List.of(), List.of(), 0, "Packing"),
                        null, null, List.of(), 10, List.of(), "Briefing");

        assertThat(DashboardTab.morningActions(busy))
                .extracting(DashboardTab.MorningAction::targetTab)
                .containsExactly("Market Day", "Irrigation & Care", "Inventory",
                        "Harvest Log");
        assertThat(DashboardTab.morningActions(busy).get(2).navigationMessage())
                .contains("morning low-stock queue");
        assertThat(DashboardTab.morningActions(busy).get(2).inventoryProducts())
                .containsExactly("Nootka Rose");
        assertThat(DashboardTab.morningActions(ready)).isEmpty();

        DashboardTab.MorningActionChooser chooser =
                DashboardTab.buildMorningActionChooser(
                        DashboardTab.morningActions(busy));
        assertThat(chooser.queue().getItemCount()).isEqualTo(4);
        assertThat(chooser.queue().getItemAt(0).toString())
                .contains("Resolve 1 packing shortfall", "Market Day");
        assertThat(chooser.selectedAction().targetTab()).isEqualTo("Market Day");
        chooser.queue().setSelectedIndex(2);
        assertThat(chooser.selectedAction().targetTab()).isEqualTo("Inventory");
        assertThat(findNamed(chooser.component(), "dashboard.morningTaskDetail"))
                .singleElement()
                .satisfies(component -> {
                    JLabel detail = (JLabel) component;
                    assertThat(detail.getText())
                            .contains("Next up", "Opens Inventory", "morning low-stock queue");
                    assertThat(detail.getAccessibleContext().getAccessibleDescription())
                            .contains("next up morning task", "Count or restock",
                                    "Opens Inventory");
                });
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                null, null, null, null, null, null, null, host);
        tab.startMorningAction(chooser.selectedAction());
        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.status)
                .contains("morning low-stock queue", "Nootka Rose");
        chooser.queue().setSelectedIndex(0);
        tab.startMorningAction(chooser.selectedAction());
        assertThat(host.selectedTab).isEqualTo("Market Day");
        assertThat(host.marketDayReturnTab).isEqualTo("Dashboard");
        assertThat(host.marketDayReturnLabel).isEqualTo("Dashboard");
        chooser.queue().setSelectedIndex(3);
        tab.startMorningAction(chooser.selectedAction());
        assertThat(host.selectedTab).isEqualTo("Harvest Log");
        assertThat(host.harvestEntryReturnTab).isEqualTo("Dashboard");
        assertThat(host.harvestEntryReturnLabel).isEqualTo("Dashboard");
    }

    @Test
    @DisplayName("daily sheets prepare off the UI thread and block overlapping requests")
    void dashboardPreparesDailySheetInBackgroundOnce() throws Exception {
        MorningBriefingService morning = mock(MorningBriefingService.class);
        DayCloseoutService closeout = mock(DayCloseoutService.class);
        LocalDate day = LocalDate.now();
        MorningBriefingService.MorningBriefing briefing =
                new MorningBriefingService.MorningBriefing(
                        day, "06:00", "Kitsap", 40, 80, 0,
                        new MarketDayPackingService.MarketDayPlan(
                                day, day, day, "CONFIRMED", 0, 0,
                                List.of(), List.of(), 0, "Packing"),
                        null, null,
                        List.of(new MorningBriefingService.LowStockLine(
                                "Nootka Rose", 2, "stems")),
                        10, List.of(), "Briefing");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(morning.buildOffline()).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return briefing;
        });
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                null, null, null, null, null, morning, closeout, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startMorningBriefing();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Morning briefing").isEnabled()).isFalse();
            assertThat(button(root[0], "Day closeout").isEnabled()).isFalse();
            tab.startMorningBriefing();
            tab.startDayCloseout();
        });
        release.countDown();
        awaitLabel(root[0], "Morning briefing ready · 1 priority");

        assertThat(calledOnUiThread).isFalse();
        verify(morning, times(1)).buildOffline();
        verify(closeout, never()).build();
        assertThat(button(root[0], "Morning briefing").isEnabled()).isTrue();
        assertThat(button(root[0], "Day closeout").isEnabled()).isTrue();
        JLabel result = (JLabel) findNamed(root[0], "dashboard.dailySheetStatus").get(0);
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("prepared", "Count or restock 1 low-stock item");
        assertThat(host.status).contains("1 operational priority");
    }

    @Test
    @DisplayName("daily-sheet failures recover inline without losing the retry actions")
    void dashboardDailySheetFailureIsRetryable() throws Exception {
        MorningBriefingService morning = mock(MorningBriefingService.class);
        DayCloseoutService closeout = mock(DayCloseoutService.class);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(closeout.build()).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            throw new IllegalStateException("database unavailable");
        });
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                null, null, null, null, null, morning, closeout, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startDayCloseout();
        });
        awaitLabel(root[0], "Day closeout failed · retry available");

        assertThat(calledOnUiThread).isFalse();
        assertThat(button(root[0], "Morning briefing").isEnabled()).isTrue();
        assertThat(button(root[0], "Day closeout").isEnabled()).isTrue();
        JLabel result = (JLabel) findNamed(root[0], "dashboard.dailySheetStatus").get(0);
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("database unavailable", "No daily sheet was opened", "retry");
        assertThat(host.status).contains("could not be prepared", "retry");
    }

    @Test
    @DisplayName("daily-sheet PDF export runs in the background and blocks duplicate work")
    void dashboardExportsDailySheetInBackgroundOnce() throws Exception {
        MorningBriefingService morning = mock(MorningBriefingService.class);
        DayCloseoutService closeoutService = mock(DayCloseoutService.class);
        LocalDate day = LocalDate.now();
        MorningBriefingService.MorningBriefing briefing =
                new MorningBriefingService.MorningBriefing(
                        day, "06:00", "Kitsap", 40, 80, 0,
                        new MarketDayPackingService.MarketDayPlan(
                                day, day, day, "CONFIRMED", 0, 0,
                                List.of(), List.of(), 0, "Packing"),
                        null, null, List.of(), 10, List.of(), "Briefing");
        DayCloseoutService.DayCloseout closeout = new DayCloseoutService.DayCloseout(
                day, "17:00", "Kitsap", 1, 80, List.of(),
                0, 0, 0, 24, 1, 40, 80, 0, 500,
                10, List.of(), List.of(), "Closeout");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        byte[] pdf = "%PDF-1.4\nbriefing".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(morning.generatePdf(briefing)).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return pdf;
        });
        Path directory = Files.createTempDirectory("dashboard-sheet-export");
        Path destination = directory.resolve("morning-briefing.pdf");
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                null, null, null, null, null, morning, closeoutService, host);
        JComponent[] root = new JComponent[1];

        try {
            SwingUtilities.invokeAndWait(() -> {
                root[0] = tab.getUIComponent();
                tab.startMorningBriefingExport(briefing, destination);
            });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            SwingUtilities.invokeAndWait(() -> {
                assertThat(button(root[0], "Morning briefing").isEnabled()).isFalse();
                assertThat(button(root[0], "Day closeout").isEnabled()).isFalse();
                tab.startMorningBriefingExport(briefing, destination);
                tab.startDayCloseoutExport(closeout, directory.resolve("closeout.pdf"));
            });
            release.countDown();
            awaitLabel(root[0], "Morning briefing saved · morning-briefing.pdf");

            assertThat(calledOnUiThread).isFalse();
            verify(morning, times(1)).generatePdf(briefing);
            verify(closeoutService, never()).generatePdf(closeout);
            assertThat(Files.readAllBytes(destination)).isEqualTo(pdf);
            assertThat(button(root[0], "Morning briefing").isEnabled()).isTrue();
            JLabel result = (JLabel) findNamed(root[0], "dashboard.dailySheetStatus").get(0);
            assertThat(result.getAccessibleContext().getAccessibleDescription())
                    .contains("saved successfully", destination.toAbsolutePath().toString());
            assertThat(host.status).isEqualTo(
                    "Morning briefing saved as “morning-briefing.pdf”.");
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(directory.resolve("closeout.pdf"));
            Files.deleteIfExists(directory);
        }
    }

    @Test
    @DisplayName("failed daily-sheet export preserves the existing destination and retry state")
    void dashboardExportFailurePreservesExistingFile() throws Exception {
        MorningBriefingService morning = mock(MorningBriefingService.class);
        DayCloseoutService closeoutService = mock(DayCloseoutService.class);
        LocalDate day = LocalDate.now();
        DayCloseoutService.DayCloseout closeout = new DayCloseoutService.DayCloseout(
                day, "17:00", "Kitsap", 1, 80, List.of(),
                0, 0, 0, 24, 1, 40, 80, 0, 500,
                10, List.of(), List.of(), "Closeout");
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(closeoutService.generatePdf(closeout)).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            throw new IllegalStateException("PDF renderer unavailable");
        });
        Path directory = Files.createTempDirectory("dashboard-sheet-failure");
        Path destination = directory.resolve("closeout.pdf");
        byte[] original = "existing closeout".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(destination, original);
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                null, null, null, null, null, morning, closeoutService, host);
        JComponent[] root = new JComponent[1];

        try {
            SwingUtilities.invokeAndWait(() -> {
                root[0] = tab.getUIComponent();
                tab.startDayCloseoutExport(closeout, destination);
            });
            awaitLabel(root[0], "Day closeout export failed · retry available");

            assertThat(calledOnUiThread).isFalse();
            assertThat(Files.readAllBytes(destination)).isEqualTo(original);
            assertThat(button(root[0], "Day closeout").isEnabled()).isTrue();
            JLabel result = (JLabel) findNamed(root[0], "dashboard.dailySheetStatus").get(0);
            assertThat(result.getAccessibleContext().getAccessibleDescription())
                    .contains("PDF renderer unavailable", "selected destination", "retry");
            assertThat(host.status).contains("was not saved", "retry");
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    @DisplayName("dashboard daily sheets cancel safely and protect an existing PDF")
    void dashboardDailySheetSaveChoicesAreSafe() throws Exception {
        MorningBriefingService morning = mock(MorningBriefingService.class);
        DayCloseoutService closeoutService = mock(DayCloseoutService.class);
        LocalDate day = LocalDate.now();
        MorningBriefingService.MorningBriefing briefing =
                new MorningBriefingService.MorningBriefing(
                        day, "06:00", "Kitsap", 40, 80, 0,
                        new MarketDayPackingService.MarketDayPlan(
                                day, day, day, "CONFIRMED", 0, 0,
                                List.of(), List.of(), 0, "Packing"),
                        null, null, List.of(), 10, List.of(), "Briefing");
        DayCloseoutService.DayCloseout closeout = new DayCloseoutService.DayCloseout(
                day, "17:00", "Kitsap", 1, 80, List.of(),
                0, 0, 0, 24, 1, 40, 80, 0, 500,
                10, List.of(), List.of(), "Closeout");
        Path directory = Files.createTempDirectory("dashboard-sheet-choice");
        Path existing = directory.resolve("reviewed-closeout.pdf");
        byte[] original = "keep reviewed closeout".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        Files.write(existing, original);
        SwingFileHarness files = new SwingFileHarness()
                .cancelSave("Save morning briefing PDF",
                        "morning-briefing-" + day + ".pdf")
                .saveAs("Save day closeout PDF",
                        "day-closeout-" + day + ".pdf", existing.toFile());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Replace existing day closeout?", false, form -> form
                        .expectText("reviewed-closeout.pdf")
                        .expectText("Replace it?"));
        TrackingHost host = new TrackingHost();
        DashboardTab tab = new DashboardTab(
                null, null, null, null, null, morning, closeoutService, host,
                dialogs, files);
        JComponent[] root = new JComponent[1];

        try {
            SwingUtilities.invokeAndWait(() -> {
                root[0] = tab.getUIComponent();
                tab.chooseMorningBriefingExport(briefing);
            });
            awaitLabel(root[0], "Morning briefing ready · save cancelled");
            assertThat(host.status).isEqualTo(
                    "Morning briefing save cancelled. No file was changed.");

            SwingUtilities.invokeAndWait(() -> tab.chooseDayCloseoutExport(closeout));
            awaitLabel(root[0], "Day closeout not saved · existing file kept");
            assertThat(Files.readAllBytes(existing)).isEqualTo(original);
            assertThat(host.status).isEqualTo(
                    "Day closeout was not saved. The existing file was kept.");
            verify(morning, never()).generatePdf(any());
            verify(closeoutService, never()).generatePdf(any());
            files.assertComplete();
            dialogs.assertComplete();
        } finally {
            Files.deleteIfExists(existing);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    @DisplayName("add-item form uses safe defaults, guidance, and explicit viewer access")
    void addItemFormIsGuidedAndRoleAware() throws Exception {
        AddItemTab tab = new AddItemTab(mock(InventoryService.class), new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.applyRolePermissions(false);
        });

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "farm.formHint"))
                .extracting(component -> ((JLabel) component).getText())
                .anyMatch(text -> text.contains("prices stay intact"))
                .anyMatch(text -> text.contains("shared setup"));
        assertThat(findNamed(root[0], "farm.statusBadge")).hasSize(2);
        assertThat(button(root[0], "Add item to inventory").isEnabled()).isFalse();
        assertThat(button(root[0], "Use starter").isEnabled()).isFalse();
        assertThat(findNamed(root[0], "inventory.keepSetup"))
                .singleElement()
                .satisfies(component -> assertThat(component.isEnabled()).isFalse());
        assertThat(find(root[0], javax.swing.JTextField.class))
                .extracting(javax.swing.JTextField::getText)
                .contains("0.00", "0");
    }

    @Test
    @DisplayName("add-item form previews margin and opening inventory value as workers type")
    void addItemPreviewsUnitEconomics() throws Exception {
        AddItemTab tab = new AddItemTab(mock(InventoryService.class), new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Selling price").setText("4.75");
            textField(root[0], "Cost per unit").setText("1.40");
            textField(root[0], "Inventory quantity").setText("28");
        });

        assertThat(((JLabel) findNamed(root[0], "inventory.unitProfit").get(0)).getText())
                .isEqualTo("$3.35");
        assertThat(((JLabel) findNamed(root[0], "inventory.marginRate").get(0)).getText())
                .isEqualTo("71%");
        assertThat(((JLabel) findNamed(root[0], "inventory.openingValue").get(0)).getText())
                .isEqualTo("$133.00");

        SwingUtilities.invokeAndWait(() ->
                textField(root[0], "Cost per unit").setText("6.00"));

        assertThat(((JLabel) findNamed(root[0], "inventory.unitProfit").get(0)).getText())
                .isEqualTo("−$1.25");
        assertThat(((JLabel) findNamed(root[0], "inventory.marginRate").get(0)).getText())
                .isEqualTo("-26%");
    }

    @Test
    @DisplayName("missing inventory handoff prefills only a blank item draft and returns safely")
    void addItemMissingProductHandoffPreservesExistingDraft() throws Exception {
        TrackingHost host = new TrackingHost();
        AddItemTab tab = new AddItemTab(mock(InventoryService.class), host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.prepareMissingItem("Anemone");
        });

        assertThat(textField(root[0], "Item name").getText()).isEqualTo("Anemone");
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .contains("Missing product prepared", "Anemone"));
        JButton returnButton = button(root[0], "Return to stock queue");
        assertThat(returnButton.isVisible()).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Item name").setText("Wedding Bucket");
            textField(root[0], "Inventory quantity").setText("6");
            tab.prepareMissingItem("Ranunculus");
        });

        assertThat(textField(root[0], "Item name").getText()).isEqualTo("Wedding Bucket");
        assertThat(textField(root[0], "Inventory quantity").getText()).isEqualTo("6");
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("Existing item draft kept"));

        SwingUtilities.invokeAndWait(returnButton::doClick);
        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.status).contains(
                "focused stock queue", "Ranunculus", "Refresh Inventory");
    }

    @Test
    @DisplayName("add-item domain bounds stay inline and identify the exact field")
    void addItemBoundFailureHighlightsPriceWithoutSaving() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        TrackingHost host = new TrackingHost();
        AddItemTab tab = new AddItemTab(inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Item name").setText("Market Bouquet");
            textField(root[0], "Selling price").setText("1000000001");
            textField(root[0], "Cost per unit").setText("12.00");
            textField(root[0], "Inventory quantity").setText("4");
            button(root[0], "Add item to inventory").doClick();
        });

        awaitLabel(root[0], "Check the highlighted field");
        JTextField price = textField(root[0], "Selling price");
        assertThat(price.getText()).isEqualTo("1000000001");
        assertThat(price.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(price.getAccessibleContext().getAccessibleDescription())
                .contains("1000000000");
        assertThat(textField(root[0], "Cost per unit").getText()).isEqualTo("12.00");
        assertThat(textField(root[0], "Inventory quantity").getText()).isEqualTo("4");
        assertThat(host.status).contains("Item not saved", "highlighted value",
                "every other detail was kept");
        verify(inventory, never()).addItem(any());

        SwingUtilities.invokeAndWait(() -> price.setText("10.00"));

        assertThat(price.getClientProperty("JComponent.outline")).isNull();
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("Changed · ready to retry"));
    }

    @Test
    @DisplayName("add-item repeat mode retains only safe shared setup after save")
    void addItemRepeatModeKeepsSafeSetup() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        AddItemTab tab = new AddItemTab(inventory, new TestHost());
        JComponent[] root = new JComponent[1];
        String[] values = new String[6];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Item name").setText("Sunrise Dahlia");
            textField(root[0], "Selling price").setText("4.75");
            textField(root[0], "Cost per unit").setText("1.40");
            textField(root[0], "Inventory quantity").setText("28");
            textField(root[0], "Item notes").setText("Cooler bin 3");
            JComboBox<?> variety = find(root[0], JComboBox.class).stream()
                    .filter(combo -> "Rose variety".equals(
                            combo.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            variety.setSelectedItem("Damask");
            ((JCheckBox) findNamed(root[0], "inventory.keepSetup").get(0)).setSelected(true);
            button(root[0], "Add item to inventory").doClick();
        });
        awaitLabel(root[0], "Added “Sunrise Dahlia” · setup kept");
        SwingUtilities.invokeAndWait(() -> {
            values[0] = textField(root[0], "Item name").getText();
            values[1] = textField(root[0], "Selling price").getText();
            values[2] = textField(root[0], "Cost per unit").getText();
            values[3] = textField(root[0], "Inventory quantity").getText();
            values[4] = textField(root[0], "Item notes").getText();
            values[5] = ((JLabel) findNamed(root[0], "farm.statusBadge").stream()
                    .filter(component -> ((JLabel) component).getText().contains("Added"))
                    .findFirst().orElseThrow()).getText();
        });

        verify(inventory).addItem(any(Item.class));
        assertThat(values).containsExactly("", "4.75", "1.40", "0", "",
                "Added “Sunrise Dahlia” · setup kept");
    }

    @Test
    @DisplayName("add-item save runs off the UI thread and cannot submit twice")
    void addItemSaveRunsInBackgroundOnce() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(inventory.addItem(any(Item.class))).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.getArgument(0);
        });
        AddItemTab tab = new AddItemTab(inventory, new TestHost());
        JComponent[] root = new JComponent[1];
        JButton[] save = new JButton[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Item name").setText("Market Mix Bouquet");
            textField(root[0], "Selling price").setText("24.00");
            textField(root[0], "Cost per unit").setText("8.50");
            textField(root[0], "Inventory quantity").setText("14");
            textField(root[0], "Item notes").setText("Saturday market cooler");
            save[0] = button(root[0], "Add item to inventory");
            save[0].doClick();
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            tab.applyRolePermissions(true);
            assertThat(save[0].getText()).isEqualTo("Saving item…");
            assertThat(save[0].isEnabled()).isFalse();
            assertThat(textField(root[0], "Item name").isEnabled()).isFalse();
            assertThat(((JCheckBox) findNamed(
                    root[0], "inventory.keepSetup").get(0)).isEnabled()).isFalse();
            save[0].doClick();
        });

        assertThat(calledOnEdt).isFalse();
        release.countDown();
        awaitLabel(root[0], "Added “Market Mix Bouquet”");

        assertThat(save[0].getText()).isEqualTo("Add item to inventory");
        assertThat(save[0].isEnabled()).isTrue();
        assertThat(textField(root[0], "Item name").getText()).isEmpty();
        verify(inventory).addItem(any(Item.class));
    }

    @Test
    @DisplayName("failed add-item save preserves every entered field and repeat choice")
    void addItemFailureKeepsDraftForRetry() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        when(inventory.addItem(any(Item.class)))
                .thenThrow(new IllegalStateException("Inventory database unavailable"));
        TrackingHost host = new TrackingHost();
        AddItemTab tab = new AddItemTab(inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Item name").setText("Wedding Dahlia Bucket");
            textField(root[0], "Selling price").setText("85.00");
            textField(root[0], "Cost per unit").setText("31.25");
            textField(root[0], "Inventory quantity").setText("6");
            textField(root[0], "Item notes").setText("Friday pickup");
            ((JCheckBox) findNamed(root[0], "inventory.keepSetup").get(0)).setSelected(true);
            button(root[0], "Add item to inventory").doClick();
        });
        awaitLabel(root[0], "Item was not saved · retry");

        JLabel status = find(root[0], JLabel.class).stream()
                .filter(label -> "Item was not saved · retry".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(textField(root[0], "Item name").getText())
                .isEqualTo("Wedding Dahlia Bucket");
        assertThat(textField(root[0], "Selling price").getText()).isEqualTo("85.00");
        assertThat(textField(root[0], "Cost per unit").getText()).isEqualTo("31.25");
        assertThat(textField(root[0], "Inventory quantity").getText()).isEqualTo("6");
        assertThat(textField(root[0], "Item notes").getText()).isEqualTo("Friday pickup");
        assertThat(((JCheckBox) findNamed(
                root[0], "inventory.keepSetup").get(0)).isSelected()).isTrue();
        assertThat(button(root[0], "Add item to inventory").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("Inventory database unavailable", "Every entered field",
                        "repeat-entry choice", "kept");
        assertThat(host.status)
                .contains("Inventory database unavailable", "details were kept", "retry");
    }

    @Test
    @DisplayName("duplicate add-item attempt preserves the draft and opens the existing SKU")
    void addItemDuplicateRoutesToExistingInventoryItem() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        when(inventory.addItem(any(Item.class))).thenThrow(
                new InventoryService.DuplicateItemNameException(14L, "Dahlia Mix"));
        TrackingHost host = new TrackingHost();
        AddItemTab tab = new AddItemTab(inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Item name").setText("dahlia mix");
            textField(root[0], "Selling price").setText("4.25");
            textField(root[0], "Cost per unit").setText("1.75");
            textField(root[0], "Inventory quantity").setText("16");
            textField(root[0], "Item notes").setText("Second cooler count");
            ((JCheckBox) findNamed(root[0], "inventory.keepSetup").get(0)).setSelected(true);
            button(root[0], "Add item to inventory").doClick();
        });
        awaitLabel(root[0], "“Dahlia Mix” already exists · no duplicate added");

        JLabel status = find(root[0], JLabel.class).stream()
                .filter(label -> label.getText().contains("already exists · no duplicate"))
                .findFirst().orElseThrow();
        assertThat(textField(root[0], "Item name").getText()).isEqualTo("dahlia mix");
        assertThat(textField(root[0], "Selling price").getText()).isEqualTo("4.25");
        assertThat(textField(root[0], "Cost per unit").getText()).isEqualTo("1.75");
        assertThat(textField(root[0], "Inventory quantity").getText()).isEqualTo("16");
        assertThat(textField(root[0], "Item notes").getText())
                .isEqualTo("Second cooler count");
        assertThat(((JCheckBox) findNamed(
                root[0], "inventory.keepSetup").get(0)).isSelected()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("already contains", "Every entered field", "Open existing item");
        assertThat(button(root[0], "Open existing item").isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(() -> button(root[0], "Open existing item").doClick());

        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.status).contains("existing Inventory item", "Dahlia Mix",
                "instead of creating a duplicate");
        verify(inventory).addItem(any(Item.class));
    }

    @Test
    @DisplayName("harvest log provides filter, empty, entry, batch, and totals workspaces")
    void harvestLogUsesStructuredWorkspace() throws Exception {
        HarvestService service = emptyHarvestService();
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(find(root[0], JSplitPane.class)).hasSize(1);
        assertThat(find(root[0], JTabbedPane.class))
                .singleElement()
                .satisfies(tabs -> assertThat(tabs.getTabCount()).isEqualTo(3));
        assertThat(findNamed(root[0], "farm.emptyState"))
                .singleElement()
                .satisfies(component -> assertThat(component.isVisible()).isTrue());
        JTable table = find(root[0], JTable.class).get(0);
        assertThat(table.getRowHeight()).isEqualTo(FarmUi.TABLE_ROW_HEIGHT);
        assertThat(table.getAccessibleContext().getAccessibleName())
                .isEqualTo("Harvest history table");
    }

    @Test
    @DisplayName("harvest refresh runs off the UI thread and coalesces a newer snapshot")
    void harvestRefreshIsResponsiveAndDiscardsStaleResult() throws Exception {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry oldEntry = new HarvestEntry(1L, LocalDate.now(), "Old Cut",
                10, "stems", "Field A", "");
        HarvestEntry currentEntry = new HarvestEntry(2L, LocalDate.now(), "Current Cut",
                14, "stems", "Field B", "");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class))).thenAnswer(invocation -> {
                    calledOnEdt.set(SwingUtilities.isEventDispatchThread());
                    if (calls.incrementAndGet() == 1) {
                        entered.countDown();
                        assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                        return List.of(oldEntry);
                    }
                    return List.of(currentEntry);
                });
        when(service.totalsByCrop()).thenReturn(Map.of("Old Cut", 10.0),
                Map.of("Current Cut", 14.0));
        when(service.totalsByCrop(anyList())).thenAnswer(invocation -> {
            List<HarvestEntry> entries = invocation.getArgument(0);
            return entries.isEmpty() ? Map.of()
                    : Map.of(entries.get(0).getCropName(), entries.get(0).getQuantity());
        });
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Refresh").isEnabled()).isFalse();
            assertThat(button(root[0], "Add harvest").isEnabled()).isFalse();
            tab.startHistoryRefresh();
            assertThat(((JLabel) findNamed(root[0], "harvest.refreshStatus").get(0)).getText())
                    .startsWith("Refresh queued");
        });

        release.countDown();
        awaitHarvestRefresh(root[0]);

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Harvest history table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(calledOnEdt).isFalse();
        assertThat(calls).hasValue(2);
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getValueAt(0, 2)).isEqualTo("Current Cut");
        assertThat(button(root[0], "Refresh").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("harvest refresh failure preserves rows, filters, selection, and entry state")
    void harvestRefreshFailurePreservesWorkingSnapshot() throws Exception {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry entry = new HarvestEntry(9L, LocalDate.now(), "Nootka Rose",
                24, "stems", "Field A", "Morning cut");
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class)))
                .thenReturn(List.of(entry))
                .thenThrow(new IllegalStateException("harvest database unavailable"));
        when(service.totalsByCrop()).thenReturn(Map.of("Nootka Rose", 24.0));
        when(service.totalsByCrop(anyList())).thenReturn(Map.of("Nootka Rose", 24.0));
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Harvest history table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Filter by crop").setText("Nootka");
            tab.startHistoryRefresh();
        });
        awaitHarvestRefresh(root[0]);

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Harvest history table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JLabel status = (JLabel) findNamed(root[0], "harvest.refreshStatus").get(0);
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getSelectedRow()).isZero();
        assertThat(textField(root[0], "Filter by crop").getText()).isEqualTo("Nootka");
        assertThat(button(root[0], "Load selected").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("harvest database unavailable", "Existing rows", "retry");
        assertThat(host.status).contains("failed", "Existing work was kept", "retry");
    }

    @Test
    @DisplayName("harvest filters target only the invalid date and keep the last good history")
    void harvestFilterValidationIsPreciseAndRecoverable() throws Exception {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry entry = new HarvestEntry(9L, LocalDate.now(), "Nootka Rose",
                24, "stems", "Field A", "Morning cut");
        AtomicInteger filterCalls = new AtomicInteger();
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class))).thenAnswer(invocation -> {
                    filterCalls.incrementAndGet();
                    return List.of(entry);
                });
        when(service.totalsByCrop()).thenReturn(Map.of("Nootka Rose", 24.0));
        when(service.totalsByCrop(anyList())).thenReturn(Map.of("Nootka Rose", 24.0));
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);
        JTextField from = (JTextField) findNamed(root[0], "harvest.filterFrom").get(0);
        JTextField to = (JTextField) findNamed(root[0], "harvest.filterTo").get(0);
        SwingUtilities.invokeAndWait(() -> {
            from.setText("yesterday");
            button(root[0], "Apply filters").doClick();
        });
        awaitLabel(root[0], "Check From date");

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Harvest history table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(from.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(to.getClientProperty("JComponent.outline")).isNull();
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getValueAt(0, 2)).isEqualTo("Nootka Rose");
        assertThat(filterCalls).hasValue(1);
        assertThat(host.status).contains("Harvest filters not applied", "Existing harvest rows");

        LocalDate today = LocalDate.now();
        SwingUtilities.invokeAndWait(() -> {
            from.setText(today.toString());
            to.setText(today.minusDays(1).toString());
            button(root[0], "Apply filters").doClick();
        });
        awaitLabel(root[0], "Check To date");

        assertThat(from.getClientProperty("JComponent.outline")).isNull();
        assertThat(to.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(to.getToolTipText()).contains("on or after the From date");
        assertThat(filterCalls).hasValue(1);

        SwingUtilities.invokeAndWait(() -> to.setText(today.toString()));
        assertThat(to.getClientProperty("JComponent.outline")).isNull();
        assertThat(((JLabel) findNamed(root[0], "harvest.refreshStatus").get(0)).getText())
                .isEqualTo("Changed · ready to apply filters");
        SwingUtilities.invokeAndWait(() -> button(root[0], "Apply filters").doClick());
        awaitLabel(root[0], "1 entry · 24 total");
        assertThat(filterCalls).hasValue(2);
    }

    @Test
    @DisplayName("exact harvest handoff clears hiding filters and selects the requested entry")
    void harvestExactRecordHandoffSelectsRequestedEntry() throws Exception {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry entry = new HarvestEntry(44L, LocalDate.now(), "Anemone",
                36, "stems", "Bed C", "Morning cut");
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(List.of(entry));
        when(service.totalsByCrop()).thenReturn(Map.of("Anemone", 36.0));
        when(service.totalsByCrop(anyList())).thenReturn(Map.of("Anemone", 36.0));
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Filter by crop").setText("Other crop");
            textField(root[0], "Filter by bed").setText("Other bed");
            ((JTextField) findNamed(root[0], "harvest.filterFrom").get(0))
                    .setText(LocalDate.now().minusDays(2).toString());
            tab.showHarvest(44L);
        });
        awaitHarvestRefresh(root[0]);

        JTable history = (JTable) findNamed(root[0], "harvest.history.table").get(0);
        assertThat(history.getSelectedRow()).isZero();
        assertThat(history.getValueAt(history.getSelectedRow(), 0)).isEqualTo(44L);
        assertThat(textField(root[0], "Filter by crop").getText()).isBlank();
        assertThat(textField(root[0], "Filter by bed").getText()).isBlank();
        assertThat(((JTextField) findNamed(root[0], "harvest.filterFrom").get(0)).getText())
                .isBlank();
        assertThat(((JTextField) findNamed(root[0], "harvest.filterTo").get(0)).getText())
                .isBlank();
        assertThat(find(root[0], JCheckBox.class).stream()
                .filter(box -> "Totals = filtered view".equals(box.getText()))
                .findFirst().orElseThrow().isSelected()).isFalse();
        assertThat(host.status).contains(
                "Opened harvest #44", "Activity History", "filters were cleared");
        assertThat(findNamed(root[0], "harvest.handoffBanner"))
                .singleElement().satisfies(component -> assertThat(component.isVisible()).isFalse());

        SwingUtilities.invokeAndWait(() -> tab.showHarvest(
                44L, "Activity History", "Activity History"));
        awaitHarvestRefresh(root[0]);
        assertThat(findNamed(root[0], "harvest.handoffBanner"))
                .singleElement().satisfies(component -> assertThat(component.isVisible()).isTrue());
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("Reviewing harvest #44 from Activity History"));
        JButton returnButton = button(root[0], "Return to Activity History");
        assertThat(returnButton.getAccessibleContext().getAccessibleName())
                .contains("Activity History", "harvest 44");
        SwingUtilities.invokeAndWait(returnButton::doClick);
        assertThat(host.selectedTab).isEqualTo("Activity History");
        assertThat(host.status).contains("Returned to Activity History", "harvest #44");
        assertThat(findNamed(root[0], "harvest.handoffBanner"))
                .singleElement().satisfies(component -> assertThat(component.isVisible()).isFalse());
        JTabbedPane workspace = (JTabbedPane) findNamed(root[0], "harvest.workspace").get(0);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Harvest crop or variety").setText("Unfinished Dahlia");
            workspace.setSelectedIndex(2);
            tab.showEntryFrom("Dashboard", "Dashboard");
        });
        assertThat(workspace.getSelectedIndex()).isZero();
        assertThat(textField(root[0], "Harvest crop or variety").getText())
                .isEqualTo("Unfinished Dahlia");
        JButton entryReturn = button(root[0], "Return to Dashboard");
        assertThat(entryReturn.getAccessibleContext().getAccessibleName())
                .contains("Dashboard", "harvest entry");
        SwingUtilities.invokeAndWait(entryReturn::doClick);
        assertThat(host.selectedTab).isEqualTo("Dashboard");
        assertThat(host.status).isEqualTo("Returned to Dashboard.");
        verify(service, times(2)).filter(eq(""), eq(""), eq(""), isNull(), isNull());
    }

    @Test
    @DisplayName("harvest bed-production report prepares off the UI thread and blocks duplicate work")
    void harvestBedProductionReportRunsInBackgroundOnce() throws Exception {
        HarvestService service = mock(HarvestService.class);
        LocalDate from = LocalDate.now().minusDays(6);
        LocalDate to = LocalDate.now();
        HarvestService.BedProductionReport report = new HarvestService.BedProductionReport(
                from.toString(), to.toString(), 1, 2, 36,
                List.of(new HarvestService.BedProduction(
                        "Field A", 36, 2, Map.of("Nootka Rose", 36.0),
                        from.toString(), to.toString())),
                "Field A · 36 stems");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.productionByBed(from, to)).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return report;
        });
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startBedProductionReport(from, to);
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Create report").isEnabled()).isFalse();
            assertThat(findNamed(root[0], "harvest.reportTools").get(0).isEnabled()).isFalse();
            tab.startBedProductionReport(from, to);
        });

        release.countDown();
        awaitLabel(root[0], "1 bed · 2 harvest entries");

        assertThat(calledOnEdt).isFalse();
        assertThat(button(root[0], "Create report").isEnabled()).isTrue();
        verify(service).productionByBed(from, to);
    }

    @Test
    @DisplayName("harvest CSV export uses a captured view off the UI thread")
    void harvestCsvExportRunsInBackgroundOnce() throws Exception {
        HarvestService service = mock(HarvestService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        doAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(service).exportToCsv(anyString(), anyList());
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        File destination = new File("target/harvest-responsive-test.csv");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startCsvExport(destination, true);
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Create report").isEnabled()).isFalse();
            tab.startCsvExport(destination, true);
        });

        release.countDown();
        awaitLabel(root[0], "Harvest CSV saved · 0 visible rows");

        assertThat(calledOnEdt).isFalse();
        assertThat(button(root[0], "Create report").isEnabled()).isTrue();
        verify(service).exportToCsv(anyString(), anyList());
    }

    @Test
    @DisplayName("harvest report failure keeps filters and restores retry controls")
    void harvestReportFailureKeepsWorkingState() throws Exception {
        HarvestService service = mock(HarvestService.class);
        LocalDate from = LocalDate.now().minusDays(6);
        LocalDate to = LocalDate.now();
        when(service.buildHarvestLogReport(from, to))
                .thenThrow(new IllegalStateException("report database unavailable"));
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Harvest filter start date").setText(from.toString());
            textField(root[0], "Harvest filter end date").setText(to.toString());
            tab.startHarvestLogReport(from, to);
        });
        awaitLabel(root[0], "Report or export failed · retry");

        JLabel status = (JLabel) findNamed(root[0], "harvest.reportStatus").get(0);
        assertThat(textField(root[0], "Harvest filter start date").getText())
                .isEqualTo(from.toString());
        assertThat(textField(root[0], "Harvest filter end date").getText())
                .isEqualTo(to.toString());
        assertThat(button(root[0], "Create report").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("report database unavailable", "Current harvest rows",
                        "filters", "kept");
        assertThat(host.status).contains("failed", "Current harvest work was kept", "retry");
    }

    @Test
    @DisplayName("new harvest batches start empty and load sample stock only on request")
    void harvestBatchStartsEmptyUntilFarmerAddsLines() throws Exception {
        HarvestService service = emptyHarvestService();
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());

        SwingUtilities.invokeAndWait(() -> {
            JComponent root = tab.getUIComponent();
            JTextArea lines = textArea(root, "Batch harvest lines");
            JButton submit = button(root, "Log batch harvest");
            assertThat(lines.getText()).isEmpty();
            assertThat(submit.isEnabled()).isFalse();
            submit.doClick();

            button(root, "Use sample lines").doClick();
            assertThat(lines.getText()).contains("Nootka Rose,40", "Dahlia mix,15");
            assertThat(submit.isEnabled()).isTrue();
        });

        verify(service, never()).addBatch(anyList());
    }

    @Test
    @DisplayName("batch harvest previews exact decimal inventory impact before logging")
    void harvestBatchPreviewsRoundedInventoryImpact() throws Exception {
        HarvestLogTab tab = new HarvestLogTab(emptyHarvestService(), new TestHost());
        JComponent[] root = new JComponent[1];
        Object[] state = new Object[7];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            JTextArea batch = find(root[0], JTextArea.class).stream()
                    .filter(area -> "Batch harvest lines".equals(
                            area.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            JLabel status = (JLabel) findNamed(root[0], "harvest.batchStatus")
                    .stream().findFirst().orElseThrow();
            JButton log = button(root[0], "Log batch harvest");

            batch.setText("Lavender,1.5,lbs");
            state[0] = log.isEnabled();
            state[1] = status.getText();
            state[2] = log.getToolTipText();

            batch.setText("Lavender,0.1,lbs");
            state[3] = log.isEnabled();
            state[4] = status.getText();

            batch.setText("Lavender,2,lbs");
            textField(root[0], "Batch harvest date")
                    .setText(LocalDate.now().plusDays(1).toString());
            state[5] = log.isEnabled();
            state[6] = status.getText();
        });

        assertThat(state[0]).isEqualTo(true);
        assertThat(state[1].toString()).contains(
                "1 row ready", "1.5 lbs", "inventory +2 lbs", "rounded by row");
        assertThat(state[2]).isEqualTo("Creates 1 harvest entry and updates inventory.");
        assertThat(state[3]).isEqualTo(false);
        assertThat(state[4].toString()).contains("Line 1", "0.5 or more");
        assertThat(state[5]).isEqualTo(false);
        assertThat(state[6].toString()).contains("Harvest date", "cannot be after today");
        assertThat(textField(root[0], "Harvest quantity")
                .getAccessibleContext().getAccessibleDescription())
                .contains("inventory stores whole units");
    }

    @Test
    @DisplayName("batch harvest owns visible defaults and applies them without changing single entry")
    void harvestBatchUsesItsOwnVisibleContext() throws Exception {
        HarvestService service = emptyHarvestService();
        AtomicReference<List<HarvestEntry>> captured = new AtomicReference<>();
        when(service.addBatch(anyList())).thenAnswer(invocation -> {
            List<HarvestEntry> entries = invocation.getArgument(0);
            captured.set(List.copyOf(entries));
            return entries;
        });
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        LocalDate batchDate = LocalDate.now().minusDays(1);

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Harvest date").setText(LocalDate.now().minusDays(5).toString());
            textField(root[0], "Harvest unit").setText("crates");
            textField(root[0], "Harvest bed or field").setText("Single Bed");
            textField(root[0], "Harvest notes").setText("Single entry draft");
            textField(root[0], "Batch harvest date").setText(batchDate.toString());
            textField(root[0], "Batch default unit").setText("bunches");
            textField(root[0], "Batch default bed or field").setText("Tunnel 3");
            textField(root[0], "Batch harvest notes").setText("Wedding morning cut");
            textArea(root[0], "Batch harvest lines").setText(
                    "Cafe Dahlia,8\nNootka Rose,12,stems,Bed A");
            button(root[0], "Log batch harvest").doClick();
        });
        awaitLabel(root[0], "2 harvest entries logged · inventory up to date");

        assertThat(captured.get()).hasSize(2);
        assertThat(captured.get().get(0))
                .extracting(HarvestEntry::getHarvestDate, HarvestEntry::getUnit,
                        HarvestEntry::getBedOrField, HarvestEntry::getNotes)
                .containsExactly(batchDate, "bunches", "Tunnel 3", "Wedding morning cut");
        assertThat(captured.get().get(1))
                .extracting(HarvestEntry::getHarvestDate, HarvestEntry::getUnit,
                        HarvestEntry::getBedOrField, HarvestEntry::getNotes)
                .containsExactly(batchDate, "stems", "Bed A", "Wedding morning cut");
        assertThat(textField(root[0], "Harvest unit").getText()).isEqualTo("crates");
        assertThat(textField(root[0], "Harvest bed or field").getText()).isEqualTo("Single Bed");
        assertThat(textField(root[0], "Harvest notes").getText()).isEqualTo("Single entry draft");
        assertThat(textField(root[0], "Batch default unit").getText()).isEqualTo("bunches");
        assertThat(textArea(root[0], "Batch harvest lines").getText()).isEmpty();
    }

    @Test
    @DisplayName("batch harvest saves off the UI thread and blocks duplicate submission")
    void harvestBatchRunsInBackgroundOnce() throws Exception {
        HarvestService service = emptyHarvestService();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.addBatch(anyList())).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to finish the test batch");
            }
            return List.copyOf(invocation.getArgument(0));
        });
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];
        Object[] busy = new Object[6];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textArea(root[0], "Batch harvest lines").setText("Nootka Rose,12,stems");
            button(root[0], "Log batch harvest").doClick();
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            tab.applyRolePermissions(true);
            JButton submit = (JButton) findNamed(root[0], "harvest.batchSubmit").get(0);
            busy[0] = submit.getText();
            busy[1] = submit.isEnabled();
            busy[2] = textArea(root[0], "Batch harvest lines").isEnabled();
            busy[3] = button(root[0], "Use sample lines").isEnabled();
            busy[4] = ((JLabel) findNamed(root[0], "harvest.batchStatus").get(0)).getText();
            submit.doClick();
            busy[5] = host.status;
        });

        assertThat(busy).containsExactly(
                "Logging batch…", false, false, false,
                "Logging 1 row · inventory updating",
                "⏳ Logging batch harvest (1 row)…");
        assertThat(calledOnEdt).isFalse();

        release.countDown();
        awaitLabel(root[0], "1 harvest entry logged · inventory up to date");

        assertThat(textArea(root[0], "Batch harvest lines").getText()).isEmpty();
        assertThat(((JButton) findNamed(root[0], "harvest.batchSubmit").get(0)).getText())
                .isEqualTo("Log batch harvest");
        assertThat(((JButton) findNamed(root[0], "harvest.batchSubmit").get(0)).isEnabled())
                .isFalse();
        assertThat(host.status).isEqualTo(
                "1 harvest entry logged. Inventory is up to date.");
        verify(service).addBatch(anyList());
    }

    @Test
    @DisplayName("batch harvest aggregates every new SKU into one inventory review handoff")
    void harvestBatchSurfacesCreatedInventorySkus() throws Exception {
        HarvestService service = emptyHarvestService();
        when(service.missingInventoryProducts(anyList()))
                .thenReturn(List.of("Cafe Dahlia", "Anemone"));
        when(service.addBatch(anyList())).thenAnswer(
                invocation -> List.copyOf(invocation.getArgument(0)));
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textArea(root[0], "Batch harvest lines").setText(
                    "Cafe Dahlia,8,bunches,Bed B\nAnemone,24,stems,Bed C");
            button(root[0], "Log batch harvest").doClick();
        });
        awaitLabel(root[0], "2 harvest entries logged · 2 new Inventory SKUs need setup");

        JLabel setup = (JLabel) findNamed(root[0], "harvest.newSkuStatus").get(0);
        JButton review = (JButton) findNamed(root[0], "harvest.reviewNewSku").get(0);
        assertThat(setup.getText()).isEqualTo("2 new Inventory SKUs need setup");
        assertThat(setup.getAccessibleContext().getAccessibleDescription())
                .contains("Cafe Dahlia", "Anemone", "zero price and cost");
        assertThat(review.getText()).isEqualTo("Review new Inventory SKUs");
        assertThat(host.status).contains(
                "2 harvest entries logged", "Cafe Dahlia", "Anemone");

        SwingUtilities.invokeAndWait(review::doClick);
        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.status).contains("Cafe Dahlia", "Anemone", "Harvest Log");
    }

    @Test
    @DisplayName("failed batch harvest keeps every line and returns a clear retry state")
    void harvestBatchFailureKeepsInput() throws Exception {
        HarvestService service = emptyHarvestService();
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.addBatch(anyList())).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            throw new IllegalStateException("Harvest database is busy");
        });
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textArea(root[0], "Batch harvest lines").setText("Dahlia Mix,8,bunches,Bed B");
            button(root[0], "Log batch harvest").doClick();
        });
        awaitLabel(root[0],
                "Batch not logged · Harvest database is busy · try again");

        JLabel status = (JLabel) findNamed(root[0], "harvest.batchStatus").get(0);
        assertThat(calledOnEdt).isFalse();
        assertThat(textArea(root[0], "Batch harvest lines").getText())
                .isEqualTo("Dahlia Mix,8,bunches,Bed B");
        assertThat(button(root[0], "Log batch harvest").isEnabled()).isTrue();
        assertThat(button(root[0], "Use sample lines").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("batch lines were kept", "Harvest database is busy", "try again");
        assertThat(host.status).contains(
                "Logging the harvest batch could not be completed",
                "Harvest database is busy", "batch lines were kept");

        SwingUtilities.invokeAndWait(() ->
                textArea(root[0], "Batch harvest lines").append("\nSweet Pea,4,bunches"));
        assertThat(status.getText()).contains("2 rows ready");
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .startsWith("Batch harvest readiness:");
    }

    @Test
    @DisplayName("harvest viewer keeps filters and exports but disables write controls")
    void harvestViewerPermissions() throws Exception {
        HarvestLogTab tab = new HarvestLogTab(emptyHarvestService(), new TestHost());
        JComponent[] root = new JComponent[1];
        boolean[] saveInitiallyEnabled = new boolean[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            saveInitiallyEnabled[0] = button(root[0], "Save edit").isEnabled();
            tab.applyRolePermissions(false);
        });

        assertThat(saveInitiallyEnabled[0]).isFalse();
        assertThat(button(root[0], "Add harvest").isEnabled()).isFalse();
        assertThat(findNamed(root[0], "harvest.keepContext"))
                .singleElement()
                .satisfies(component -> assertThat(component.isEnabled()).isFalse());
        assertThat(button(root[0], "Save edit").isEnabled()).isFalse();
        assertThat(button(root[0], "Run entry action").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "harvest.entryTools"))
                .singleElement()
                .isInstanceOf(JComboBox.class)
                .satisfies(component -> assertThat(comboItems((JComboBox<?>) component))
                        .containsExactly("Refresh history", "Export selected lot label"));
        assertThat(button(root[0], "Log batch harvest").isEnabled()).isFalse();
        assertThat(button(root[0], "Apply filters").isEnabled()).isTrue();
        assertThat(button(root[0], "Create report").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "harvest.reportTools"))
                .singleElement()
                .isInstanceOf(JComboBox.class);

        SwingUtilities.invokeAndWait(() -> tab.applyRolePermissions(true));
        JComboBox<?> entryTools = (JComboBox<?>) findNamed(root[0], "harvest.entryTools").get(0);
        assertThat(comboItems(entryTools)).containsExactly(
                "Clear form", "Duplicate selected as today",
                "Export selected lot label", "Refresh history", "Delete selected harvest");
    }

    @Test
    @DisplayName("single harvest keeps stable field context while clearing one-off values")
    void harvestKeepsContextForNextCut() throws Exception {
        HarvestService service = emptyHarvestService();
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Harvest crop or variety").setText("Nootka Rose");
            textField(root[0], "Harvest quantity").setText("24");
            textField(root[0], "Harvest unit").setText("stems");
            textField(root[0], "Harvest bed or field").setText("Field A");
            textField(root[0], "Harvest notes").setText("Morning cut");
            button(root[0], "Add harvest").doClick();
        });
        awaitLabel(root[0], "Saved · context kept for next cut");

        verify(service).add(any(HarvestEntry.class));
        assertThat(textField(root[0], "Harvest crop or variety").getText())
                .isEqualTo("Nootka Rose");
        assertThat(textField(root[0], "Harvest quantity").getText()).isEqualTo("1");
        assertThat(textField(root[0], "Harvest unit").getText()).isEqualTo("stems");
        assertThat(textField(root[0], "Harvest bed or field").getText()).isEqualTo("Field A");
        assertThat(textField(root[0], "Harvest notes").getText()).isEmpty();
        assertThat(findNamed(root[0], "farm.statusBadge"))
                .extracting(component -> ((javax.swing.JLabel) component).getText())
                .contains("Saved · context kept for next cut");
        assertThat(host.status).contains("Context kept for the next cut");
    }

    @Test
    @DisplayName("new harvest SKU is disclosed and routed to focused inventory setup")
    void harvestCreatedInventorySkuRequiresVisibleSetupReview() throws Exception {
        HarvestService service = mock(HarvestService.class);
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(List.of());
        when(service.totalsByCrop()).thenReturn(Map.of());
        when(service.totalsByCrop(anyList())).thenReturn(Map.of());
        when(service.missingInventoryProducts(anyList())).thenReturn(List.of("Anemone"));
        when(service.add(any(HarvestEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Harvest crop or variety").setText("Anemone");
            textField(root[0], "Harvest quantity").setText("36");
            textField(root[0], "Harvest unit").setText("stems");
            button(root[0], "Add harvest").doClick();
        });
        awaitLabel(root[0], "Harvest logged · new Inventory SKU needs setup");

        JLabel setup = (JLabel) findNamed(root[0], "harvest.newSkuStatus").get(0);
        JButton review = (JButton) findNamed(root[0], "harvest.reviewNewSku").get(0);
        assertThat(setup.isVisible()).isTrue();
        assertThat(setup.getText()).isEqualTo("1 new Inventory SKU needs setup");
        assertThat(setup.getAccessibleContext().getAccessibleDescription())
                .contains("Anemone", "zero price and cost", "catalog details");
        assertThat(review.isVisible()).isTrue();
        assertThat(host.status).contains(
                "new Inventory SKU", "zero price and cost", "review its setup");

        SwingUtilities.invokeAndWait(review::doClick);
        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.status).contains(
                "harvest-created SKU setup", "Anemone", "price", "cost", "unit");
        assertThat(review.isVisible()).isFalse();
        verify(service).missingInventoryProducts(argThat(entries ->
                entries.size() == 1 && "Anemone".equals(entries.get(0).getCropName())));
    }

    @Test
    @DisplayName("single harvest validation stays inline and preserves the worker entry")
    void harvestValidationIsInlineAndRecoverable() throws Exception {
        HarvestService service = emptyHarvestService();
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Harvest crop or variety").setText("Nootka Rose");
            textField(root[0], "Harvest quantity").setText("several");
            textField(root[0], "Harvest bed or field").setText("Field A");
            button(root[0], "Add harvest").doClick();
        });

        JTextField quantity = textField(root[0], "Harvest quantity");
        JLabel status = find(root[0], JLabel.class).stream()
                .filter(label -> "Check quantity".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(quantity.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(quantity.getToolTipText()).contains("numeric quantity");
        assertThat(textField(root[0], "Harvest crop or variety").getText())
                .isEqualTo("Nootka Rose");
        assertThat(textField(root[0], "Harvest bed or field").getText())
                .isEqualTo("Field A");
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("Your entry is still in the form", "Add harvest");
        assertThat(host.status)
                .contains("Harvest not saved", "numeric quantity", "entry is still in the form");
        verify(service, never()).add(any(HarvestEntry.class));

        SwingUtilities.invokeAndWait(() -> quantity.setText("12"));
        assertThat(quantity.getClientProperty("JComponent.outline")).isNull();
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("Changed · ready to retry"));

        SwingUtilities.invokeAndWait(() -> button(root[0], "Add harvest").doClick());
        awaitLabel(root[0], "Saved · context kept for next cut");

        assertThat(quantity.getClientProperty("JComponent.outline")).isNull();
        verify(service).add(any(HarvestEntry.class));
    }

    @Test
    @DisplayName("single harvest saves off the UI thread and cannot submit twice")
    void singleHarvestSaveRunsInBackgroundOnce() throws Exception {
        HarvestService service = mock(HarvestService.class);
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(List.of());
        when(service.totalsByCrop()).thenReturn(Map.of());
        when(service.totalsByCrop(anyList())).thenReturn(Map.of());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.add(any(HarvestEntry.class))).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.getArgument(0);
        });
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        JButton[] submit = new JButton[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Harvest crop or variety").setText("Sweet Pea");
            textField(root[0], "Harvest quantity").setText("18");
            textField(root[0], "Harvest unit").setText("bunches");
            textField(root[0], "Harvest bed or field").setText("Tunnel 2");
            textField(root[0], "Harvest notes").setText("First morning cut");
            submit[0] = button(root[0], "Add harvest");
            submit[0].doClick();
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            tab.applyRolePermissions(true);
            assertThat(submit[0].isEnabled()).isFalse();
            assertThat(textField(root[0], "Harvest crop or variety").isEnabled()).isFalse();
            assertThat(button(root[0], "Log batch harvest").isEnabled()).isFalse();
            submit[0].doClick();
        });

        assertThat(calledOnEdt).isFalse();
        release.countDown();
        awaitLabel(root[0], "Saved · context kept for next cut");
        awaitHarvestRefresh(root[0]);

        assertThat(submit[0].isEnabled()).isTrue();
        assertThat(textField(root[0], "Harvest crop or variety").getText())
                .isEqualTo("Sweet Pea");
        assertThat(textField(root[0], "Harvest quantity").getText()).isEqualTo("1");
        verify(service).add(any(HarvestEntry.class));
    }

    @Test
    @DisplayName("failed single harvest keeps every field and retry choice")
    void singleHarvestFailureKeepsEntryForRetry() throws Exception {
        HarvestService service = mock(HarvestService.class);
        when(service.add(any(HarvestEntry.class)))
                .thenThrow(new IllegalStateException("Harvest database unavailable"));
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Harvest date").setText("2026-08-24");
            textField(root[0], "Harvest crop or variety").setText("Cafe au Lait Dahlia");
            textField(root[0], "Harvest quantity").setText("7.5");
            textField(root[0], "Harvest unit").setText("buckets");
            textField(root[0], "Harvest bed or field").setText("Bed D4");
            textField(root[0], "Harvest notes").setText("Wedding order");
            ((JCheckBox) findNamed(root[0], "harvest.keepContext").get(0)).setSelected(false);
            button(root[0], "Add harvest").doClick();
        });
        awaitLabel(root[0], "Harvest not logged · retry");

        JLabel status = find(root[0], JLabel.class).stream()
                .filter(label -> "Harvest not logged · retry".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(textField(root[0], "Harvest date").getText()).isEqualTo("2026-08-24");
        assertThat(textField(root[0], "Harvest crop or variety").getText())
                .isEqualTo("Cafe au Lait Dahlia");
        assertThat(textField(root[0], "Harvest quantity").getText()).isEqualTo("7.5");
        assertThat(textField(root[0], "Harvest unit").getText()).isEqualTo("buckets");
        assertThat(textField(root[0], "Harvest bed or field").getText()).isEqualTo("Bed D4");
        assertThat(textField(root[0], "Harvest notes").getText()).isEqualTo("Wedding order");
        assertThat(((JCheckBox) findNamed(
                root[0], "harvest.keepContext").get(0)).isSelected()).isFalse();
        assertThat(button(root[0], "Add harvest").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("Harvest database unavailable", "Date", "crop", "quantity",
                        "unit", "bed", "notes", "keep-context choice", "kept");
        assertThat(host.status)
                .contains("Harvest database unavailable", "form and target were kept", "retry");
    }

    @Test
    @DisplayName("harvest edit saves the captured target off the UI thread")
    void harvestEditRunsInBackground() throws Exception {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry original = new HarvestEntry(9L, LocalDate.of(2026, 8, 23),
                "Nootka Rose", 12, "stems", "Field A", "Morning cut");
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class)))
                .thenReturn(List.of(original));
        when(service.totalsByCrop()).thenReturn(Map.of("Nootka Rose", 12.0));
        when(service.totalsByCrop(anyList())).thenReturn(Map.of("Nootka Rose", 12.0));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.update(eq(9L), any(HarvestEntry.class))).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.getArgument(1);
        });
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable history = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Harvest history table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            history.setRowSelectionInterval(0, 0);
            button(root[0], "Load selected").doClick();
            textField(root[0], "Harvest quantity").setText("20");
            textField(root[0], "Harvest notes").setText("Corrected bucket count");
            button(root[0], "Save edit").doClick();
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(calledOnEdt).isFalse();
        assertThat(button(root[0], "Save edit").isEnabled()).isFalse();

        release.countDown();
        awaitLabel(root[0], "Harvest updated · inventory corrected");
        awaitHarvestRefresh(root[0]);

        assertThat(button(root[0], "Add harvest").isEnabled()).isTrue();
        assertThat(textField(root[0], "Harvest crop or variety").getText()).isEmpty();
        verify(service).update(eq(9L), argThat(entry ->
                entry.getQuantity() == 20
                        && "Corrected bucket count".equals(entry.getNotes())));
    }

    @Test
    @DisplayName("harvest deletion reverses inventory off the UI thread exactly once")
    void harvestDeleteRunsInBackgroundOnce() throws Exception {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry entry = new HarvestEntry(9L, LocalDate.of(2026, 8, 23),
                "Nootka Rose", 12, "stems", "Field A", "Morning cut");
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class)))
                .thenReturn(List.of(entry), List.of());
        when(service.totalsByCrop()).thenReturn(Map.of("Nootka Rose", 12.0), Map.of());
        when(service.totalsByCrop(anyList()))
                .thenReturn(Map.of("Nootka Rose", 12.0), Map.of());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        doAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            started.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(service).delete(9L);
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable history = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Harvest history table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            history.setRowSelectionInterval(0, 0);
            tab.startHarvestDelete(9L, "Nootka Rose", "2026-08-23");
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Deleting harvest…").isEnabled()).isFalse();
            JTable history = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Harvest history table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertThat(history.isEnabled()).isFalse();
            tab.applyRolePermissions(true);
            assertThat(button(root[0], "Deleting harvest…").isEnabled()).isFalse();
            tab.startHarvestDelete(9L, "Nootka Rose", "2026-08-23");
        });
        release.countDown();
        awaitLabel(root[0], "Harvest #9 deleted · inventory reversed");
        awaitHarvestRefresh(root[0]);

        assertThat(calledOnEdt).isFalse();
        verify(service, times(1)).delete(9L);
        assertThat(button(root[0], "Run entry action").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("failed harvest deletion retains the row and reports inventory uncertainty")
    void harvestDeleteFailurePreservesTarget() throws Exception {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry entry = new HarvestEntry(9L, LocalDate.of(2026, 8, 23),
                "Nootka Rose", 12, "stems", "Field A", "Morning cut");
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class)))
                .thenReturn(List.of(entry));
        when(service.totalsByCrop()).thenReturn(Map.of("Nootka Rose", 12.0));
        when(service.totalsByCrop(anyList())).thenReturn(Map.of("Nootka Rose", 12.0));
        doThrow(new IllegalStateException("inventory reversal unavailable"))
                .when(service).delete(9L);
        TrackingHost host = new TrackingHost();
        HarvestLogTab tab = new HarvestLogTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable history = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Harvest history table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            history.setRowSelectionInterval(0, 0);
            tab.startHarvestDelete(9L, "Nootka Rose", "2026-08-23");
        });
        awaitLabel(root[0], "Harvest #9 not deleted · retry");

        JTable history = find(root[0], JTable.class).stream()
                .filter(candidate -> "Harvest history table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JLabel result = find(root[0], JLabel.class).stream()
                .filter(label -> "Harvest #9 not deleted · retry".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(history.getSelectedRow()).isZero();
        assertThat(history.isEnabled()).isTrue();
        assertThat(button(root[0], "Run entry action").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("inventory reversal unavailable", "remains selected",
                        "no inventory reversal result", "retry");
        assertThat(host.status).contains("not deleted", "remains selected", "retry");
    }

    @Test
    @DisplayName("harvest row actions follow selection and viewer transitions")
    void harvestSelectionAndRoleTransition() throws Exception {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry entry = new HarvestEntry(9L, LocalDate.now(), "Nootka Rose",
                24, "stems", "Field A", "Morning cut");
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class)))
                .thenReturn(List.of(entry));
        when(service.totalsByCrop()).thenReturn(Map.of("Nootka Rose", 24.0));
        when(service.totalsByCrop(anyList())).thenReturn(Map.of("Nootka Rose", 24.0));
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        boolean[] states = new boolean[3];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).get(0);
            states[0] = button(root[0], "Load selected").isEnabled();
            table.setRowSelectionInterval(0, 0);
            states[1] = button(root[0], "Load selected").isEnabled();
            tab.applyRolePermissions(false);
            states[2] = button(root[0], "Load selected").isEnabled();
        });

        assertThat(states).containsExactly(false, true, false);
    }

    @Test
    @DisplayName("sorted harvest actions load the exact visible row by model identity")
    void harvestSortedSelectionTargetsExactEntry() throws Exception {
        HarvestService service = mock(HarvestService.class);
        HarvestEntry large = new HarvestEntry(1L, LocalDate.now(), "Dahlia Mix",
                100, "stems", "Field A", "Large cut");
        HarvestEntry small = new HarvestEntry(2L, LocalDate.now(), "Nootka Rose",
                20, "stems", "Field B", "Small cut");
        when(service.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class)))
                .thenReturn(List.of(large, small));
        when(service.totalsByCrop()).thenReturn(Map.of(
                "Dahlia Mix", 100.0, "Nootka Rose", 20.0));
        when(service.totalsByCrop(anyList())).thenReturn(Map.of(
                "Dahlia Mix", 100.0, "Nootka Rose", 20.0));
        HarvestLogTab tab = new HarvestLogTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        Object[] state = new Object[5];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitHarvestRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Harvest history table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.getRowSorter().toggleSortOrder(3);
            table.setRowSelectionInterval(0, 0);
            state[0] = table.getValueAt(0, 0);
            button(root[0], "Load selected").doClick();
            state[1] = textField(root[0], "Harvest crop or variety").getText();
            state[2] = textField(root[0], "Harvest quantity").getText();
            state[3] = textField(root[0], "Harvest bed or field").getText();
            state[4] = findNamed(root[0], "farm.statusBadge").stream()
                    .map(component -> ((JLabel) component).getText())
                    .filter(text -> text.startsWith("Editing #"))
                    .findFirst().orElseThrow();
        });

        assertThat(state).containsExactly(2L, "Nootka Rose", "20.0", "Field B",
                "Editing #2 · Nootka Rose");
    }

    @Test
    @DisplayName("empty filtered orders offer one-step recovery without clearing entry")
    void crmEmptyFilteredOrdersRecoverWithoutClearingEntry() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer existingCustomer = new Customer(
                401L, "Existing Florist", "Morgan", "orders@existing.example",
                "360-555-0140", "FLORIST", "Established account");
        when(customers.search(anyString())).thenReturn(List.of(existingCustomer));
        when(customers.getAll()).thenReturn(List.of(existingCustomer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab crm = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = crm.getUIComponent();
            crm.initialize();
        });
        awaitCrmRefresh(root[0]);

        JButton showAll = (JButton) findNamed(root[0], "crm.orderEmptyShowAll").get(0);
        assertThat(showAll.isVisible()).isTrue();
        JButton toolbarShowAll = (JButton) findNamed(root[0], "crm.showAllOrders").get(0);
        assertThat(toolbarShowAll.getMnemonic()).isEqualTo(java.awt.event.KeyEvent.VK_L);
        assertThat(showAll.getMnemonic()).isZero();
        assertThat(showAll.getAccessibleContext().getAccessibleDescription())
                .contains("Alt+L", "unfinished new-order or product entry");
        JComponent orderPanel = (JComponent) findNamed(root[0], "crm.orderPanel").get(0);
        assertThat(find(orderPanel, JButton.class).stream()
                .filter(button -> button.getMnemonic() == java.awt.event.KeyEvent.VK_L))
                .extracting(JButton::getText)
                .containsExactly("Show all");
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("No orders match these filters"));

        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order product name").setText("Unfinished Cafe au Lait");
            textField(root[0], "Order notes").setText("Friday delivery draft");
            toolbarShowAll.doClick();
        });
        awaitCrmRefresh(root[0]);

        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.orderStatusFilter").get(0)).getSelectedItem())
                .isEqualTo("All statuses");
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.orderHandoffFilter").get(0)).getSelectedItem())
                .isEqualTo("All handoffs");
        assertThat(((JTextField) findNamed(
                root[0], "crm.orderFilterFrom").get(0)).getText()).isBlank();
        assertThat(((JTextField) findNamed(
                root[0], "crm.orderFilterTo").get(0)).getText()).isBlank();
        assertThat(textField(root[0], "Order product name").getText())
                .isEqualTo("Unfinished Cafe au Lait");
        assertThat(textField(root[0], "Order notes").getText())
                .isEqualTo("Friday delivery draft");
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText()).isEqualTo("No orders yet"));
        assertThat(showAll.isVisible()).isFalse();
        assertThat(host.status).contains(
                "Showing all orders", "unfinished new-order entry was kept");
    }

    @Test
    @DisplayName("order filters target only the invalid date and preserve current results")
    void crmOrderFilterValidationIsPreciseAndRecoverable() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        AtomicInteger filterCalls = new AtomicInteger();
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenAnswer(invocation -> {
                    filterCalls.incrementAndGet();
                    return List.of();
                });
        TrackingHost host = new TrackingHost();
        CrmTab crm = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = crm.getUIComponent();
            crm.initialize();
        });
        awaitCrmRefresh(root[0]);
        JTextField from = (JTextField) findNamed(root[0], "crm.orderFilterFrom").get(0);
        JTextField to = (JTextField) findNamed(root[0], "crm.orderFilterTo").get(0);
        SwingUtilities.invokeAndWait(() -> {
            from.setText("last Tuesday");
            button(root[0], "Apply filters").doClick();
        });
        awaitLabel(root[0], "Check From date");

        assertThat(from.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(to.getClientProperty("JComponent.outline")).isNull();
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText()).isEqualTo("Check From date"));
        assertThat(filterCalls).hasValue(1);
        assertThat(host.status).contains("Order filters not applied", "Existing order results were kept");

        LocalDate today = LocalDate.now();
        SwingUtilities.invokeAndWait(() -> {
            from.setText(today.toString());
            to.setText(today.minusDays(1).toString());
            button(root[0], "Apply filters").doClick();
        });
        awaitLabel(root[0], "Check To date");

        assertThat(from.getClientProperty("JComponent.outline")).isNull();
        assertThat(to.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(to.getToolTipText()).contains("on or after the From date");
        assertThat(filterCalls).hasValue(1);

        SwingUtilities.invokeAndWait(() -> to.setText(today.toString()));
        assertThat(to.getClientProperty("JComponent.outline")).isNull();
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("Changed · ready to apply filters"));
        SwingUtilities.invokeAndWait(() -> button(root[0], "Apply filters").doClick());
        awaitCrmRefresh(root[0]);
        assertThat(filterCalls).hasValue(2);
    }

    @Test
    @DisplayName("valid filters clear stale date errors without losing the entered range")
    void validFilterRecoveryClearsErrors() throws Exception {
        HarvestLogTab harvest = new HarvestLogTab(emptyHarvestService(), new TestHost());
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab crm = new CrmTab(customers, orders, null, host);
        JTextField[] fields = new JTextField[4];
        JComponent[] harvestRoot = new JComponent[1];
        JComponent[] crmRoot = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            harvestRoot[0] = harvest.getUIComponent();
            harvest.initialize();
            crmRoot[0] = crm.getUIComponent();
            crm.initialize();
        });
        awaitHarvestRefresh(harvestRoot[0]);
        SwingUtilities.invokeAndWait(() -> {
            fields[0] = (JTextField) findNamed(harvestRoot[0], "harvest.filterFrom").get(0);
            fields[1] = (JTextField) findNamed(harvestRoot[0], "harvest.filterTo").get(0);
            FarmUi.markFieldError(fields[0], "Old date error");
            FarmUi.markFieldError(fields[1], "Old date error");
            button(harvestRoot[0], "Apply filters").doClick();
        });
        awaitHarvestRefresh(harvestRoot[0]);
        awaitCrmRefresh(crmRoot[0]);
        SwingUtilities.invokeAndWait(() -> {
            fields[2] = (JTextField) findNamed(crmRoot[0], "crm.orderFilterFrom").get(0);
            fields[3] = (JTextField) findNamed(crmRoot[0], "crm.orderFilterTo").get(0);
            FarmUi.markFieldError(fields[2], "Old date error");
            FarmUi.markFieldError(fields[3], "Old date error");
            button(crmRoot[0], "Apply filters").doClick();
        });
        awaitCrmRefresh(crmRoot[0]);

        assertThat(fields).allSatisfy(field -> {
            assertThat(field.getClientProperty("JComponent.outline")).isNull();
            assertThat(field.getText()).isNotBlank();
        });
    }

    @Test
    @DisplayName("CRM status handoff clears limits and shows the complete cancelled queue")
    void crmStatusHandoffClearsLimits() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab crm = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = crm.getUIComponent();
            crm.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> crm.showOrdersByStatus("CANCELLED"));
        awaitCrmRefresh(root[0]);

        JComboBox<?> statusFilter = (JComboBox<?>) findNamed(
                root[0], "crm.orderStatusFilter").get(0);
        assertThat(statusFilter.getSelectedItem()).isEqualTo("Cancelled");
        assertThat(((JTextField) findNamed(root[0], "crm.orderFilterFrom").get(0)).getText())
                .isBlank();
        assertThat(((JTextField) findNamed(root[0], "crm.orderFilterTo").get(0)).getText())
                .isBlank();
        assertThat(findNamed(root[0], "crm.orderHandoffBanner"))
                .singleElement().satisfies(component -> assertThat(component.isVisible()).isFalse());

        SwingUtilities.invokeAndWait(() -> crm.showOrdersByStatus(
                "CANCELLED", "Activity History", "Activity History"));
        awaitCrmRefresh(root[0]);
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("Reviewing Cancelled orders from Activity History"));
        JButton returnButton = button(root[0], "Return to Activity History");
        assertThat(returnButton.getAccessibleContext().getAccessibleName())
                .contains("Activity History", "Cancelled orders");
        SwingUtilities.invokeAndWait(returnButton::doClick);
        assertThat(host.selectedTab).isEqualTo("Activity History");
        assertThat(host.status).isEqualTo("Returned to Activity History.");
        verify(orders, times(2)).filter(eq("CANCELLED"), eq(""), isNull(), isNull());
    }

    @Test
    @DisplayName("CRM isolates every open order needing handoff review")
    void crmNeedsHandoffReviewQueueClearsBroadFilters() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder unresolved = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Legacy import");
        unresolved.setId(41L);
        unresolved.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder pickup = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "CONFIRMED", "", "PICKUP");
        pickup.setId(42L);
        pickup.addLine(new OrderLine("Nootka Rose", 1, "stems", 4));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(unresolved, pickup));
        TrackingHost host = new TrackingHost();
        CrmTab crm = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = crm.getUIComponent();
            crm.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JComboBox<?>) findNamed(root[0], "crm.orderStatusFilter").get(0))
                    .setSelectedItem("Cancelled");
            textField(root[0], "Order customer filter").setText("Hidden customer");
            button(root[0], "Needs handoff review").doClick();
        });
        awaitCrmRefresh(root[0]);

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo(41L);
        assertThat(table.getValueAt(0, 4)).isEqualTo("Needs review");
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.orderStatusFilter").get(0)).getSelectedItem()).isEqualTo("Open");
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.orderHandoffFilter").get(0)).getSelectedItem())
                .isEqualTo("Needs review");
        assertThat(textField(root[0], "Order customer filter").getText()).isBlank();
        assertThat(((JTextField) findNamed(root[0], "crm.orderFilterFrom").get(0)).getText())
                .isBlank();
        assertThat(((JTextField) findNamed(root[0], "crm.orderFilterTo").get(0)).getText())
                .isBlank();
        assertThat(button(root[0], "Needs handoff review").getMnemonic())
                .isEqualTo(java.awt.event.KeyEvent.VK_G);
        assertThat(host.status).contains("Needs handoff review", "filters were cleared");
    }

    @Test
    @DisplayName("CRM completion queue includes only orders that can complete from current stock")
    void crmCompletionReadinessQueueMatchesCompletionGates() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");

        CustomerOrder ready = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Saturday pickup", "PICKUP");
        ready.setId(61L);
        ready.addLine(new OrderLine("Dahlia Mix", 4, "stems", 3.5));
        CustomerOrder missingHandoff = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Legacy import");
        missingHandoff.setId(62L);
        missingHandoff.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder empty = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "", "DELIVERY");
        empty.setId(63L);
        CustomerOrder shortStock = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "", "DELIVERY");
        shortStock.setId(64L);
        shortStock.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder wrongUnit = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "", "MARKET");
        wrongUnit.setId(65L);
        wrongUnit.addLine(new OrderLine("Dahlia Mix", 2, "bunches", 3.5));
        CustomerOrder invalidQuantity = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "", "PICKUP");
        invalidQuantity.setId(66L);
        invalidQuantity.addLine(new OrderLine("Dahlia Mix", 0.4, "stems", 3.5));

        List<CustomerOrder> confirmed = List.of(
                ready, missingHandoff, empty, shortStock, wrongUnit, invalidQuantity);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(new Item(
                "Dahlia Mix", "Flowers/Plants", 3.5, "stems", 1.0, 10, "")));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(confirmed);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> button(root[0], "Ready to complete").doClick());
        awaitCrmRefresh(root[0]);

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JComboBox<?> readiness = (JComboBox<?>) findNamed(
                root[0], "crm.orderStatusFilter").get(0);
        assertThat(table.getRowCount()).isEqualTo(1);
        assertThat(table.getValueAt(0, 0)).isEqualTo(61L);
        assertThat(readiness.getSelectedItem()).isEqualTo("Ready to complete");
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("1 ready to complete · 5 need attention"));
        assertThat(button(root[0], "Ready to complete").getMnemonic())
                .isEqualTo(java.awt.event.KeyEvent.VK_Y);

        SwingUtilities.invokeAndWait(() -> {
            readiness.setSelectedItem("Needs attention");
            button(root[0], "Apply filters").doClick();
        });
        awaitCrmRefresh(root[0]);
        assertThat(table.getRowCount()).isEqualTo(5);
        assertThat(java.util.stream.IntStream.range(0, table.getRowCount())
                .mapToObj(row -> table.getValueAt(row, 0)))
                .containsExactly(62L, 63L, 64L, 65L, 66L);
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("5 need attention · 1 ready"));
        assertThat(((JLabel) find(root[0], JLabel.class).stream()
                .filter(label -> "5 need attention · 1 ready".equals(label.getText()))
                .findFirst().orElseThrow()).getAccessibleContext().getAccessibleDescription())
                .contains("handoff", "saved products", "quantities", "units", "stock");

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        assertThat(readiness.getSelectedItem()).isEqualTo("Needs attention");
        assertThat(table.getRowCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("CRM readiness reserves shared stock by earliest date and stable order id")
    void crmCompletionReadinessReservesStockInWorkingOrder() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        LocalDate firstDate = LocalDate.now();

        CustomerOrder later = new CustomerOrder(
                customer, firstDate.plusDays(1), "CONFIRMED", "", "DELIVERY");
        later.setId(1L);
        later.addLine(new OrderLine("Dahlia Mix", 6, "stems", 3.5));
        CustomerOrder sameDateSecond = new CustomerOrder(
                customer, firstDate, "CONFIRMED", "", "PICKUP");
        sameDateSecond.setId(72L);
        sameDateSecond.addLine(new OrderLine("Dahlia Mix", 6, "stems", 3.5));
        CustomerOrder sameDateFirst = new CustomerOrder(
                customer, firstDate, "CONFIRMED", "", "MARKET");
        sameDateFirst.setId(71L);
        sameDateFirst.addLine(new OrderLine("Dahlia Mix", 6, "stems", 3.5));

        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(new Item(
                "Dahlia Mix", "Flowers/Plants", 3.5, "stems", 1.0, 10, "")));
        // Repository order is newest first; the readiness queue must impose work priority.
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(
                        List.of(later, sameDateSecond, sameDateFirst));
        CrmTab tab = new CrmTab(customers, orders, null, inventory, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> button(root[0], "Ready to complete").doClick());
        awaitCrmRefresh(root[0]);

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JComboBox<?> statusFilter = (JComboBox<?>) findNamed(
                root[0], "crm.orderStatusFilter").get(0);
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getValueAt(0, 0)).isEqualTo(71L);
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("1 ready to complete · 2 need attention"));

        SwingUtilities.invokeAndWait(() -> {
            statusFilter.setSelectedItem("Needs attention");
            button(root[0], "Apply filters").doClick();
        });
        awaitCrmRefresh(root[0]);
        assertThat(table.getRowCount()).isEqualTo(2);
        assertThat(table.getValueAt(0, 0)).isEqualTo(72L);
        assertThat(table.getValueAt(1, 0)).isEqualTo(1L);

        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        JLabel stock = (JLabel) findNamed(root[0], "crm.selectedOrderStock").get(0);
        assertThat(stock.getText()).contains(
                "1 queue stock gap", "Dahlia Mix short 2 after earlier orders");
        assertThat(stock.getAccessibleContext().getAccessibleDescription())
                .contains("Earlier dated ready orders reserve", "Complete those orders first");
    }

    @Test
    @DisplayName("context-free delivery is locked while instructed delivery and walk-up market remain valid")
    void crmDeliveryRequiresContactOrSavedDestinationContext() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer walkUp = new Customer(8L, "Farm Stand", "", "", "", "MARKET", "");
        CustomerOrder unsafeDelivery = new CustomerOrder(
                walkUp, LocalDate.now(), "DRAFT", "", "DELIVERY");
        unsafeDelivery.setId(41L);
        unsafeDelivery.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder instructedDelivery = new CustomerOrder(
                walkUp, LocalDate.now(), "DRAFT", "Leave at the north barn", "DELIVERY");
        instructedDelivery.setId(42L);
        instructedDelivery.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder market = new CustomerOrder(
                walkUp, LocalDate.now(), "DRAFT", "", "MARKET");
        market.setId(43L);
        market.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(walkUp));
        when(customers.getAll()).thenReturn(List.of(walkUp));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(
                        List.of(unsafeDelivery, instructedDelivery, market));
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);

        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        JButton confirm = button(root[0], "Confirm order");
        assertThat(confirm.isEnabled()).isFalse();
        assertThat(confirm.getAccessibleContext().getAccessibleDescription()).contains(
                "missing delivery contact or destination context",
                "open the customer account", "account delivery note",
                "explicit order delivery instructions");
        assertThat(button(root[0], "Open customer").isEnabled()).isTrue();
        verify(orders, never()).confirmReviewed(anyLong(), any());

        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(1, 1));
        assertThat(button(root[0], "Confirm order").isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(2, 2));
        assertThat(button(root[0], "Confirm order").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("selected order exposes customer contact and opens the account without losing entry")
    void crmSelectedOrderKeepsCustomerContextAtHand() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST",
                "Use the side gate after 3 PM");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane workspace = (JTabbedPane) findNamed(root[0], "crm.workspace").get(0);
            workspace.setSelectedIndex(1);
            JTable ordersTable = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            ordersTable.setRowSelectionInterval(0, 0);

            JTextArea context = (JTextArea) findNamed(
                    root[0], "crm.selectedOrderCustomer").get(0);
            assertThat(context.isEditable()).isFalse();
            assertThat(context.getLineWrap()).isTrue();
            assertThat(context.getText()).contains(
                    "Kitsap Blooms", "Florist", "Sam Rivera",
                    "360-555-0142", "sam@kitsap.test", "Use the side gate");
            assertThat(context.getAccessibleContext().getAccessibleDescription())
                    .contains("Selected order customer Kitsap Blooms", "Account note");

            textField(root[0], "Order product name").setText("Cosmos");
            textField(root[0], "Order unit price").setText("2.50");
            JButton open = button(root[0], "Open customer");
            assertThat(open.isEnabled()).isTrue();
            open.doClick();

            assertThat(workspace.getSelectedIndex()).isZero();
            JTable customerTable = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer accounts table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertThat(customerTable.getSelectedRow()).isNotNegative();
            assertThat(customerTable.getValueAt(customerTable.getSelectedRow(), 0))
                    .isEqualTo(7L);
            assertThat(textField(root[0], "Order product name").getText())
                    .isEqualTo("Cosmos");
            assertThat(textField(root[0], "Order unit price").getText())
                    .isEqualTo("2.50");
        });

        assertThat(host.status).contains(
                "Opened customer", "Kitsap Blooms", "order #42", "unfinished entry work were kept");
        awaitCrmRefresh(root[0]);
        JLabel opened = find(root[0], JLabel.class).stream()
                .filter(label -> "Opened linked customer · “Kitsap Blooms”".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(opened.getAccessibleContext().getAccessibleDescription())
                .contains("exact account is selected", "keyboard focus", "customer table",
                        "unfinished entry work were kept");
        JButton returnToOrder = (JButton) findNamed(
                root[0], "crm.returnFromCustomer").get(0);
        assertThat(returnToOrder.getText()).isEqualTo("Return to order #42");
        SwingUtilities.invokeAndWait(returnToOrder::doClick);
        JTabbedPane workspace = (JTabbedPane) findNamed(root[0], "crm.workspace").get(0);
        JTable returnedOrders = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        assertThat(workspace.getSelectedIndex()).isOne();
        assertThat(returnedOrders.getSelectedRow()).isNotNegative();
        assertThat(returnedOrders.getValueAt(returnedOrders.getSelectedRow(), 0)).isEqualTo(42L);
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(host.status).contains("Returned to order #42", "Order entry work was kept");
        Customer noContact = new Customer(8L, "Farm Stand", "", "", "",
                "MARKET", "");
        CustomerOrder noContactOrder = new CustomerOrder(
                noContact, LocalDate.of(2026, 9, 13), "DRAFT", "");
        assertThat(CrmTab.orderCustomerContext(noContactOrder)).satisfies(context -> {
            assertThat(context.missingContact()).isTrue();
            assertThat(context.displayText()).contains(
                    "Farm Stand", "No contact person, phone, or email saved");
            assertThat(context.accessibleText()).contains(
                    "No contact person, phone, or email is saved");
        });
        verify(orders, never()).confirm(any());
        verify(orders, never()).fulfill(any());
    }

    @Test
    @DisplayName("new-order customer context verifies delivery contact and survives workflow changes")
    void crmNewOrderCustomerContextIsActionableAndPreserved() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer florist = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST",
                "Use the side gate after 3 PM");
        Customer market = new Customer(8L, "Farm Stand", "", "", "", "MARKET", "");
        CustomerOrder saved = new CustomerOrder(
                florist, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        saved.setId(42L);
        saved.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(florist, market));
        when(customers.getAll()).thenReturn(List.of(florist, market));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(saved));
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JTextArea context = (JTextArea) findNamed(
                    root[0], "crm.newOrderCustomerContext").get(0);
            JComboBox<?> customerChoice = find(root[0], JComboBox.class).stream()
                    .filter(combo -> "New order customer".equals(
                            combo.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertThat(context.isEditable()).isFalse();
            assertThat(context.getText()).contains(
                    "Kitsap Blooms", "Florist", "Sam Rivera", "360-555-0142",
                    "sam@kitsap.test", "Use the side gate");
            assertThat(customerChoice.getAccessibleContext().getAccessibleDescription())
                    .contains("New order customer Kitsap Blooms", "Account note");

            customerChoice.setSelectedIndex(1);
            assertThat(context.getText()).contains(
                    "Farm Stand", "Market", "No contact person, phone, or email saved",
                    "verify delivery details");
            assertThat(context.getAccessibleContext().getAccessibleDescription())
                    .contains("No contact person, phone, or email is saved",
                            "before confirmation");

            JComboBox<?> handoff = (JComboBox<?>) findNamed(
                    root[0], "crm.orderHandoffType").get(0);
            JTextField instructions = textField(root[0], "Order notes");
            handoff.setSelectedItem("Delivery");
            assertThat(context.getText()).contains(
                    "Delivery readiness · Missing", "account delivery note",
                    "order instruction before confirmation");
            assertThat(context.getAccessibleContext().getAccessibleDescription())
                    .contains("Delivery context is missing", "before confirmation");

            instructions.setText("Leave at the blue cooler");
            assertThat(context.getText()).contains(
                    "Delivery readiness · Ready from order instruction");
            assertThat(context.getAccessibleContext().getAccessibleDescription())
                    .contains("Delivery context is ready from order instruction");

            instructions.setText("");
            customerChoice.setSelectedIndex(0);
            assertThat(context.getText()).contains(
                    "Delivery readiness · Ready from customer contact, account delivery note");
            customerChoice.setSelectedIndex(1);
            textField(root[0], "Order product name").setText("Cosmos");

            JTable orderTable = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            orderTable.setRowSelectionInterval(0, 0);
            assertThat(context.getText()).startsWith("Retained new-order customer (paused)")
                    .contains("Farm Stand");
            assertThat(context.getAccessibleContext().getAccessibleDescription())
                    .contains("draft is paused", "Farm Stand");
            button(root[0], "Start another order").doClick();
            assertThat(context.getText()).doesNotContain("paused").contains("Farm Stand");
            assertThat(((CrmPresenter.CustomerChoice) customerChoice.getSelectedItem()).id())
                    .isEqualTo(8L);
            assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
            tab.refreshData();
        });
        awaitCrmRefresh(root[0]);

        JTextArea context = (JTextArea) findNamed(
                root[0], "crm.newOrderCustomerContext").get(0);
        JComboBox<?> customerChoice = find(root[0], JComboBox.class).stream()
                .filter(combo -> "New order customer".equals(
                        combo.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(((CrmPresenter.CustomerChoice) customerChoice.getSelectedItem()).id())
                .isEqualTo(8L);
        assertThat(context.getText()).contains("Farm Stand", "verify delivery details");
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        verify(orders, never()).createReviewed(
                any(), any(), any(), any(), any(), any(), anyMap(), anyBoolean());
    }

    @Test
    @DisplayName("new-order delivery context opens the exact customer without losing draft entry")
    void crmNewOrderCustomerHandoffPreservesCompleteDraft() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer florist = new Customer(7L, "Kitsap Blooms", "", "", "",
                "FLORIST", "Loading dock entrance");
        when(customers.search(anyString())).thenReturn(List.of(florist));
        when(customers.getAll()).thenReturn(List.of(florist));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> handoff = (JComboBox<?>) findNamed(
                    root[0], "crm.orderHandoffType").get(0);
            handoff.setSelectedItem("Delivery");
            textField(root[0], "Order notes").setText("Call from loading dock");
            textField(root[0], "Order product name").setText("Cafe au Lait Dahlia");
            textField(root[0], "Order quantity").setText("12");
            textField(root[0], "Order unit").setText("stems");
            textField(root[0], "Order unit price").setText("4.50");

            JButton open = (JButton) findNamed(
                    root[0], "crm.openNewOrderCustomer").get(0);
            assertThat(open.isEnabled()).isTrue();
            assertThat(open.getMnemonic()).isEqualTo(java.awt.event.KeyEvent.VK_V);
            open.doClick();

            JTabbedPane workspace = (JTabbedPane) findNamed(
                    root[0], "crm.workspace").get(0);
            assertThat(workspace.getSelectedIndex()).isZero();
            JTable customerTable = find(root[0], JTable.class).stream()
                    .filter(table -> "Customer accounts table".equals(
                            table.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertThat(customerTable.getSelectedRow()).isNotNegative();
            assertThat(customerTable.getValueAt(customerTable.getSelectedRow(), 0))
                    .isEqualTo(7L);
        });
        awaitCrmRefresh(root[0]);

        assertThat(textField(root[0], "Order notes").getText())
                .isEqualTo("Call from loading dock");
        assertThat(textField(root[0], "Order product name").getText())
                .isEqualTo("Cafe au Lait Dahlia");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("12");
        assertThat(textField(root[0], "Order unit").getText()).isEqualTo("stems");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("4.50");
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.orderHandoffType").get(0)).getSelectedItem())
                .isEqualTo("Delivery");
        assertThat(host.status).contains(
                "Opened customer", "Kitsap Blooms", "new-order draft",
                "unfinished entry work were kept");
        JButton returnToEntry = (JButton) findNamed(
                root[0], "crm.returnFromCustomer").get(0);
        assertThat(findNamed(root[0], "crm.customerHandoffBanner").get(0).isVisible())
                .isTrue();
        assertThat(returnToEntry.getText()).isEqualTo("Return to unfinished order entry");
        assertThat(returnToEntry.getMnemonic()).isEqualTo(java.awt.event.KeyEvent.VK_R);
        SwingUtilities.invokeAndWait(returnToEntry::doClick);

        assertThat(((JTabbedPane) findNamed(root[0], "crm.workspace").get(0))
                .getSelectedIndex()).isOne();
        assertThat(findNamed(root[0], "crm.customerHandoffBanner").get(0).isVisible())
                .isFalse();
        assertThat(textField(root[0], "Order product name").getText())
                .isEqualTo("Cafe au Lait Dahlia");
        assertThat(host.status).contains(
                "Returned to unfinished order entry", "Every draft field was kept");
        verify(orders, never()).createReviewed(
                any(), any(), any(), any(), any(), any(), anyMap(), anyBoolean());
    }

    @Test
    @DisplayName("exact-order handoff clears hiding filters and selects the requested order")
    void crmExactOrderHandoffSelectsRequestedOrder() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Keep upright", "PICKUP");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        TrackingHost host = new TrackingHost();
        CrmTab crm = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = crm.getUIComponent();
            crm.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JComboBox<?>) findNamed(root[0], "crm.orderStatusFilter").get(0))
                    .setSelectedItem("Draft");
            ((JComboBox<?>) findNamed(root[0], "crm.orderHandoffFilter").get(0))
                    .setSelectedItem("Needs review");
            textField(root[0], "Order customer filter").setText("Other farm");
            ((JTextField) findNamed(root[0], "crm.orderFilterFrom").get(0))
                    .setText(LocalDate.now().minusDays(2).toString());
            ((JTextField) findNamed(root[0], "crm.orderFilterTo").get(0))
                    .setText(LocalDate.now().minusDays(1).toString());
            crm.showOrder(42L);
        });
        awaitHostStatus(host, "Opened order #42; filters were cleared and the saved order is selected.");

        JTable orderTable = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        assertThat(orderTable.getSelectedRow()).isZero();
        assertThat(orderTable.getValueAt(orderTable.getSelectedRow(), 0)).isEqualTo(42L);
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.orderStatusFilter").get(0)).getSelectedItem())
                .isEqualTo("All statuses");
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.orderHandoffFilter").get(0)).getSelectedItem())
                .isEqualTo("All handoffs");
        assertThat(textField(root[0], "Order customer filter").getText()).isBlank();
        assertThat(((JTextField) findNamed(root[0], "crm.orderFilterFrom").get(0)).getText())
                .isBlank();
        assertThat(((JTextField) findNamed(root[0], "crm.orderFilterTo").get(0)).getText())
                .isBlank();
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                .contains("Selected #42", "Kitsap Blooms", "Confirmed");
        assertThat(host.status).contains("order #42", "filters were cleared", "selected");
        assertThat(findNamed(root[0], "crm.orderHandoffBanner"))
                .singleElement().satisfies(component -> assertThat(component.isVisible()).isFalse());

        SwingUtilities.invokeAndWait(() -> crm.showOrder(
                42L, "Market Day", "Market Day"));
        assertThat(findNamed(root[0], "crm.orderHandoffBanner"))
                .singleElement().satisfies(component -> assertThat(component.isVisible()).isTrue());
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("Reviewing order #42 from Market Day"));
        JButton returnButton = button(root[0], "Return to Market Day");
        assertThat(returnButton.getAccessibleContext().getAccessibleName())
                .contains("Market Day", "order 42");
        SwingUtilities.invokeAndWait(() -> crm.showOrderLine(
                42L, null, "Dahlia Mix", "Market Day", "Market Day"));
        awaitHostStatus(host, "Opened order #42; filters were cleared and saved product “"
                + "Dahlia Mix” is selected for correction.");
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0)).getSelectedItem().toString())
                .isEqualTo("Dahlia Mix · 12 stems · $3.50");
        assertThat(find(root[0], JLabel.class)).anySatisfy(label ->
                assertThat(label.getText()).isEqualTo(
                        "Correcting Dahlia Mix on order #42 from Market Day"));
        SwingUtilities.invokeAndWait(returnButton::doClick);
        assertThat(host.selectedTab).isEqualTo("Market Day");
        assertThat(host.status).contains("Returned to Market Day", "order #42");
        assertThat(findNamed(root[0], "crm.orderHandoffBanner"))
                .singleElement().satisfies(component -> assertThat(component.isVisible()).isFalse());
        verify(orders, times(3)).filter(eq("ALL"), eq(""), isNull(), isNull());
    }

    @Test
    @DisplayName("exact-line handoff selects the saved duplicate product that needs correction")
    void crmExactOrderLineHandoffSelectsRequestedSavedProduct() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Keep upright", "PICKUP");
        order.setId(42L);
        OrderLine covered = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        covered.setId(420L);
        order.addLine(covered);
        OrderLine invalid = new OrderLine("Dahlia Mix", 0.4, "stems", 3.5);
        invalid.setId(421L);
        order.addLine(invalid);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 3.5, "stems", 1, 50, "")));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        TrackingHost host = new TrackingHost();
        CrmTab crm = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = crm.getUIComponent();
            crm.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> crm.showOrderLineCorrection(
                42L, 421L, "Dahlia Mix", "Market Day", "Market Day"));
        awaitHostStatus(host, "Editing “Dahlia Mix” on order #42. Correct the fields, "
                + "then save product changes.");

        JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0);
        assertThat(savedProducts.getSelectedItem().toString())
                .isEqualTo("Dahlia Mix · 0.4 stems · $3.50");
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Dahlia Mix");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("0.4");
        assertThat(button(root[0], "Save product changes").isEnabled()).isFalse();
        assertThat(find(root[0], JLabel.class)).anySatisfy(label ->
                assertThat(label.getText()).isEqualTo(
                        "Correcting Dahlia Mix on order #42 from Market Day"));
        JButton returnButton = button(root[0], "Return to Market Day");
        assertThat(returnButton.getAccessibleContext().getAccessibleName())
                .contains("Dahlia Mix", "order 42");

        SwingUtilities.invokeAndWait(returnButton::doClick);

        assertThat(host.selectedTab).isEqualTo("Market Day");
        assertThat(host.status).contains(
                "Returned to Market Day", "Dahlia Mix on order #42");
        assertThat(host.status).doesNotContain("unsaved product correction");
        assertThat(textField(root[0], "Order product name").getText()).isBlank();
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("1");

        SwingUtilities.invokeAndWait(() -> crm.showOrderLineCorrection(
                42L, 421L, "Dahlia Mix", "Market Day", "Market Day"));
        awaitHostStatus(host, "Editing “Dahlia Mix” on order #42. Correct the fields, "
                + "then save product changes.");
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order quantity").setText("2");
            returnButton.doClick();
        });

        assertThat(host.status).contains(
                "Returned to Market Day", "unsaved product correction remains");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("2");
    }

    @Test
    @DisplayName("exact-order handoff reports when the saved order no longer exists")
    void crmExactOrderHandoffReportsMissingOrder() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab crm = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = crm.getUIComponent();
            crm.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> crm.showOrder(404L));
        awaitHostStatus(host, "Order #404 is no longer available in Customers & Orders.");

        JTable orderTable = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        assertThat(orderTable.getSelectedRow()).isNegative();
        assertThat(host.status).contains("Order #404", "no longer available");
        verify(orders).filter(eq("ALL"), eq(""), isNull(), isNull());
    }

    @Test
    @DisplayName("customers and orders separate editing work while preserving viewer exports")
    void crmStructuredWorkspaceAndPermissions() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(
                anyString(), anyString(), nullable(LocalDate.class), nullable(LocalDate.class)))
                .thenReturn(List.of());
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];
        boolean[] guidedInitialState = new boolean[3];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
            tab.applyRolePermissions(false);
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            guidedInitialState[0] = button(root[0], "Save edit").isEnabled();
            guidedInitialState[1] = button(root[0], "Confirm order").isEnabled();
            guidedInitialState[2] = button(root[0], "Complete order").isEnabled();
        });

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(tab.getTabTitle()).isEqualTo("Customers & Orders");
        assertThat(findNamed(root[0], "crm.workspace"))
                .singleElement()
                .isInstanceOf(JTabbedPane.class)
                .satisfies(component -> assertThat(((JTabbedPane) component).getTabCount())
                        .isEqualTo(2));
        assertThat(findNamed(root[0], "crm.orderWorkspace"))
                .singleElement()
                .isInstanceOf(JSplitPane.class)
                .satisfies(component -> assertThat(
                        ((JSplitPane) component).getOrientation())
                        .isEqualTo(JSplitPane.VERTICAL_SPLIT));
        assertThat(findNamed(root[0], "crm.orderEntryScroll"))
                .singleElement()
                .isInstanceOf(javax.swing.JScrollPane.class);
        assertThat(find(root[0], JTable.class)).hasSize(2)
                .allSatisfy(table -> assertThat(table.getRowHeight())
                        .isEqualTo(FarmUi.TABLE_ROW_HEIGHT));
        assertThat(findNamed(root[0], "farm.emptyState")).hasSize(2)
                .allSatisfy(component -> assertThat(component.isVisible()).isTrue());
        assertThat(guidedInitialState).containsOnly(false);
        assertThat(button(root[0], "Add customer").isEnabled()).isFalse();
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
        assertThat(button(root[0], "Start another order").isEnabled()).isFalse();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(button(root[0], "Statement PDF…").isEnabled()).isFalse();
        assertThat(button(root[0], "Run action").isEnabled()).isFalse();
        assertThat(findNamed(root[0], "crm.orderTools"))
                .singleElement()
                .isInstanceOf(JComboBox.class)
                .satisfies(component -> assertThat(comboItems((JComboBox<?>) component))
                        .containsExactly("Save invoice", "Export filtered orders",
                                "Export all orders"));

        SwingUtilities.invokeAndWait(() -> tab.applyRolePermissions(true));
        JComboBox<?> orderTools = (JComboBox<?>) findNamed(root[0], "crm.orderTools").get(0);
        assertThat(comboItems(orderTools)).containsExactly(
                "Save invoice", "Export filtered orders",
                "Export all orders", "Cancel selected order", "Delete selected order");
    }

    @Test
    @DisplayName("CRM refresh runs off the UI thread and coalesces a newer snapshot")
    void crmRefreshIsResponsiveAndDiscardsStaleResult() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer oldCustomer = new Customer(7L, "Old Account", "", "", "",
                "WHOLESALE", "");
        Customer currentCustomer = new Customer(8L, "Current Account", "", "", "",
                "WHOLESALE", "");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(customers.search(anyString())).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                return List.of(oldCustomer);
            }
            return List.of(currentCustomer);
        });
        when(customers.getAll()).thenReturn(List.of(oldCustomer), List.of(currentCustomer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Refresh CRM").isEnabled()).isFalse();
            assertThat(button(root[0], "Add customer").isEnabled()).isFalse();
            tab.startCrmRefresh();
            assertThat(((JLabel) findNamed(root[0], "crm.refreshStatus").get(0)).getText())
                    .startsWith("Refresh queued");
        });

        release.countDown();
        awaitCrmRefresh(root[0]);

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer accounts table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(calledOnUiThread).isFalse();
        assertThat(calls).hasValue(2);
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getValueAt(0, 1)).isEqualTo("Current Account");
        assertThat(button(root[0], "Refresh CRM").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("applying CRM order filters never queries the database on the UI thread")
    void crmOrderFiltersUseBackgroundRefresh() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger filterCalls = new AtomicInteger();
        AtomicBoolean filterCalledOnUiThread = new AtomicBoolean(true);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenAnswer(invocation -> {
            if (filterCalls.incrementAndGet() == 1) {
                return List.of();
            }
            filterCalledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return List.of();
        });
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JComboBox<?>) findNamed(root[0], "crm.orderStatusFilter").get(0))
                    .setSelectedItem("Confirmed");
            button(root[0], "Apply filters").doClick();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean uiStillResponsive = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            uiStillResponsive.set(true);
            assertThat(button(root[0], "Refresh CRM").isEnabled()).isFalse();
            assertThat(((JLabel) findNamed(root[0], "crm.refreshStatus").get(0)).getText())
                    .startsWith("Refreshing customers and orders");
        });
        release.countDown();
        awaitCrmRefresh(root[0]);

        assertThat(uiStillResponsive).isTrue();
        assertThat(filterCalledOnUiThread).isFalse();
        assertThat(filterCalls).hasValue(2);
        verify(orders).filter(eq("CONFIRMED"), eq(""), any(LocalDate.class),
                any(LocalDate.class));
    }

    @Test
    @DisplayName("customer search stays responsive and discards an older in-flight result")
    void crmCustomerSearchRunsOffUiThreadAndKeepsLatestQuery() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer initial = new Customer(1L, "Initial Farm", "", "", "",
                "WHOLESALE", "");
        Customer stale = new Customer(2L, "Kitsap Blooms", "", "", "",
                "FLORIST", "");
        Customer latest = new Customer(3L, "Farm Stand", "", "", "",
                "MARKET", "");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean searchCalledOnUiThread = new AtomicBoolean(true);
        AtomicInteger searchCalls = new AtomicInteger();
        when(customers.search(anyString())).thenAnswer(invocation -> {
            searchCalls.incrementAndGet();
            String query = invocation.getArgument(0);
            if ("Kitsap".equals(query)) {
                searchCalledOnUiThread.set(SwingUtilities.isEventDispatchThread());
                entered.countDown();
                assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                return List.of(stale);
            }
            if ("Farm".equals(query)) {
                return List.of(latest);
            }
            return List.of(initial);
        });
        when(customers.getAll()).thenReturn(List.of(initial, stale, latest));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Customer search").setText("Kitsap");
            button(root[0], "Search").doClick();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean uiStillResponsive = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            uiStillResponsive.set(true);
            assertThat(button(root[0], "Refresh CRM").isEnabled()).isFalse();
            textField(root[0], "Customer search").setText("Farm");
            button(root[0], "Search").doClick();
            assertThat(((JLabel) findNamed(root[0], "crm.refreshStatus").get(0)).getText())
                    .startsWith("Refresh queued");
        });
        release.countDown();
        awaitCrmRefresh(root[0]);

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer accounts table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(uiStillResponsive).isTrue();
        assertThat(searchCalledOnUiThread).isFalse();
        assertThat(searchCalls).hasValue(3);
        assertThat(textField(root[0], "Customer search").getText()).isEqualTo("Farm");
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getValueAt(0, 0)).isEqualTo(3L);
        assertThat(table.getValueAt(0, 1)).isEqualTo("Farm Stand");
        assertThat(host.status).contains("Refreshing customers and orders");
    }

    @Test
    @DisplayName("CRM refresh failure preserves the working customer and order snapshot")
    void crmRefreshFailurePreservesWorkingSnapshot() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        when(customers.search(anyString())).thenReturn(List.of(customer))
                .thenThrow(new IllegalStateException("customer database unavailable"));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer accounts table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Customer search").setText("Kitsap");
            tab.startCrmRefresh();
        });
        awaitCrmRefresh(root[0]);

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer accounts table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JLabel status = (JLabel) findNamed(root[0], "crm.refreshStatus").get(0);
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getSelectedRow()).isZero();
        assertThat(textField(root[0], "Customer search").getText()).isEqualTo("Kitsap");
        assertThat(button(root[0], "Load selected").isEnabled()).isTrue();
        assertThat(button(root[0], "Add customer").isEnabled()).isFalse();
        assertThat(button(root[0], "Delete selected").isEnabled()).isFalse();
        assertThat(status.getText()).contains("previous snapshot shown");
        assertThat(status.getText()).contains("Customers unavailable");
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("Customers could not be loaded", "partial refresh response was discarded",
                        "customer database unavailable", "Existing rows", "retry");
        assertThat(host.status).contains("failed", "Existing work was kept", "retry");
    }

    @Test
    @DisplayName("customer statement prepares off the UI thread and blocks duplicate document work")
    void crmCustomerStatementRunsInBackgroundOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        CustomerStatementService statements = mock(CustomerStatementService.class);
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to = LocalDate.now();
        CustomerStatementService.CustomerStatement statement =
                mock(CustomerStatementService.CustomerStatement.class);
        when(statement.orderCount()).thenReturn(2);
        when(statement.grandTotal()).thenReturn(185.0);
        when(statement.from()).thenReturn(from);
        when(statement.to()).thenReturn(to);
        when(statement.customerName()).thenReturn("Kitsap Blooms");
        when(statement.plainText()).thenReturn("Kitsap Blooms statement");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(statements.build(7L, from, to)).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return statement;
        });
        CrmTab tab = new CrmTab(customers, orders, statements, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startCustomerStatement(7L, "Kitsap Blooms", from, to);
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(findNamed(root[0], "crm.orderTools").get(0).isEnabled()).isFalse();
            tab.startCustomerStatement(7L, "Kitsap Blooms", from, to);
        });

        release.countDown();
        awaitLabel(root[0], "Statement ready · 2 orders");

        assertThat(calledOnEdt).isFalse();
        assertThat(findNamed(root[0], "crm.orderTools").get(0).isEnabled()).isTrue();
        verify(statements).build(7L, from, to);
    }

    @Test
    @DisplayName("invoice rendering and file writing run off the UI thread")
    void crmInvoiceExportRunsInBackgroundOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(orders.generateInvoicePdf(42L)).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return new byte[]{1, 2, 3};
        });
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];
        Path destination = Files.createTempFile("crm-invoice-responsive", ".pdf");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.startInvoiceExport(42L, destination.toFile());
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(findNamed(root[0], "crm.orderTools").get(0).isEnabled()).isFalse();
            tab.startInvoiceExport(42L, destination.toFile());
        });

        release.countDown();
        awaitLabel(root[0], "Invoice saved · order #42");

        assertThat(calledOnEdt).isFalse();
        assertThat(Files.readAllBytes(destination)).containsExactly(1, 2, 3);
        verify(orders).generateInvoicePdf(42L);
        Files.deleteIfExists(destination);
    }

    @Test
    @DisplayName("order export failure keeps CRM filters and restores document controls")
    void crmOrderExportFailureKeepsWorkingState() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        doThrow(new IllegalStateException("order export database unavailable"))
                .when(orders).exportToCsv(anyString(), anyList());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];
        File destination = new File("target/orders-responsive-test.csv");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Order customer filter").setText("Kitsap");
            tab.startOrdersExport(destination, true);
        });
        awaitLabel(root[0], "Document or export failed · retry");

        JLabel status = (JLabel) findNamed(root[0], "crm.documentStatus").get(0);
        assertThat(textField(root[0], "Order customer filter").getText()).isEqualTo("Kitsap");
        assertThat(findNamed(root[0], "crm.orderTools").get(0).isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("order export database unavailable", "Customer rows",
                        "filters", "entry work were kept");
        assertThat(host.status).contains("failed", "Current CRM work was kept", "retry");
    }

    @Test
    @DisplayName("inventory choice fills and previews a fulfillable order line")
    void crmInventoryChoicePreviewsOrderLine() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        Item item = new Item("Dahlia Mix", "Flowers/Plants",
                3.5, "stems", 1.0, 8, "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(item));
        when(inventory.findByNameIgnoreCase("Dahlia Mix"))
                .thenReturn(java.util.Optional.of(item));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        CrmTab tab = new CrmTab(customers, orders, null, inventory, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> products = (JComboBox<?>) findNamed(
                    root[0], "crm.inventoryProductChoice").get(0);
            products.setSelectedIndex(1);
        });

        assertThat(find(root[0], JTextField.class).stream()
                .filter(field -> "Order product name".equals(
                        field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().getText()).isEqualTo("Dahlia Mix");
        assertThat(find(root[0], JTextField.class).stream()
                .filter(field -> "Order unit price".equals(
                        field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().getText()).isEqualTo("3.50");
        assertThat(findNamed(root[0], "crm.orderLinePreview"))
                .singleElement()
                .satisfies(component -> assertThat(((javax.swing.JLabel) component).getText())
                        .contains("$3.50", "8 on hand"));
        assertThat(button(root[0], "Create & select order").isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(() -> textField(root[0], "Order unit").setText("bunches"));
        assertThat(findNamed(root[0], "crm.orderLinePreview"))
                .singleElement()
                .satisfies(component -> assertThat(((javax.swing.JLabel) component).getText())
                        .contains("Unit must match Inventory", "stems"));
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order unit").setText("stems");
            textField(root[0], "Order quantity").setText("0.4");
        });
        assertThat(findNamed(root[0], "crm.orderLinePreview"))
                .singleElement()
                .satisfies(component -> assertThat(((javax.swing.JLabel) component).getText())
                        .contains("round to at least 1 inventory unit"));
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(() -> textField(root[0], "Order quantity").setText("1"));
        SwingUtilities.invokeAndWait(() -> textField(root[0], "Order unit price").setText("0"));
        assertThat(findNamed(root[0], "crm.orderLinePreview"))
                .singleElement()
                .satisfies(component -> assertThat(((javax.swing.JLabel) component).getText())
                        .contains("$0.00", "8 on hand"));
        assertThat(button(root[0], "Create & select order").isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(() ->
                textField(root[0], "Order unit price").setText("1000000001"));
        assertThat(findNamed(root[0], "crm.orderLinePreview"))
                .singleElement()
                .satisfies(component -> assertThat(((javax.swing.JLabel) component).getText())
                        .contains("Unit price", "1000000000"));
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        verify(inventory, never()).findByNameIgnoreCase(anyString());
    }

    @Test
    @DisplayName("product preview includes earlier reservations hidden by CRM filters")
    void crmProductPreviewUsesQueueReservationsBeforeSave() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        LocalDate day = LocalDate.now();
        CustomerOrder earlier = new CustomerOrder(
                customer, day, "CONFIRMED", "Market load", "MARKET");
        earlier.setId(71L);
        earlier.addLine(new OrderLine("Dahlia Mix", 6, "stems", 3.5));
        CustomerOrder selected = new CustomerOrder(
                customer, day.plusDays(1), "CONFIRMED", "Delivery", "DELIVERY");
        selected.setId(72L);
        OrderLine saved = new OrderLine("Dahlia Mix", 2, "stems", 3.5);
        saved.setId(720L);
        selected.addLine(saved);
        Item item = new Item("Dahlia Mix", "Flowers/Plants",
                3.5, "stems", 1.0, 10, "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(item));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(selected));
        when(orders.confirmedCompletionQueue()).thenReturn(List.of(earlier, selected));
        CrmTab tab = new CrmTab(customers, orders, null, inventory, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            assertThat(table.getRowCount()).isOne();
            int selectedRow = java.util.stream.IntStream.range(0, table.getRowCount())
                    .filter(row -> Long.valueOf(72L).equals(table.getValueAt(row, 0)))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(selectedRow, selectedRow);
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order quantity").setText("3");
            textField(root[0], "Order unit").setText("stems");
            textField(root[0], "Order unit price").setText("3.50");
        });

        JLabel preview = (JLabel) findNamed(root[0], "crm.orderLinePreview").get(0);
        assertThat(preview.getText()).contains(
                "Already saved on this order in 1 line",
                "Short by 1 at completion",
                "2 already saved + 3 entered = 5 required",
                "6 inventory units reserved for earlier confirmed orders");
        assertThat(preview.getAccessibleContext().getAccessibleDescription())
                .contains("Short by 1", "reserved for earlier confirmed orders");
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrderStock").get(0)).getText())
                .isEqualTo("Stock covers selected order · 1 product");
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();
        verify(orders, never()).addLineReviewed(anyLong(), any(), any());
    }

    @Test
    @DisplayName("additional product preview warns about duplicates and excludes an edited line")
    void crmAdditionalProductPreviewUsesPostSaveOrderDemand() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Saturday pickup");
        order.setId(52L);
        OrderLine saved = new OrderLine("Dahlia Mix", 6, "stems", 3.5);
        saved.setId(520L);
        order.addLine(saved);
        Item item = new Item("Dahlia Mix", "Flowers/Plants",
                3.5, "stems", 1.0, 8, "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(item));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        CrmTab tab = new CrmTab(customers, orders, null, inventory, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order quantity").setText("4");
            textField(root[0], "Order unit").setText("stems");
            textField(root[0], "Order unit price").setText("3.50");
        });

        JLabel preview = (JLabel) findNamed(root[0], "crm.orderLinePreview").get(0);
        assertThat(preview.getText()).contains(
                "Already saved on this order in 1 line", "$14.00", "Short by 2",
                "6 already saved + 4 entered = 10 required");
        assertThat(preview.getAccessibleContext().getAccessibleDescription())
                .contains("Already saved on this order in 1 line", "Short by 2", "10 required");
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(() ->
                textField(root[0], "Order quantity").setText("1"));

        assertThat(preview.getText()).contains(
                        "Already saved on this order in 1 line", "8 on hand",
                        "7 required after save", "6 already saved")
                .doesNotContain("Short by");
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(() -> button(root[0], "Edit selected product").doClick());

        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("6");
        assertThat(preview.getText()).contains("8 on hand")
                .doesNotContain("Already saved on this order", "Short by");
        assertThat(button(root[0], "Save product changes").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("duplicate saved product requires a stable second approval before adding")
    void crmDuplicateSavedProductRequiresExplicitSeparateLineApproval() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Saturday pickup");
        order.setId(52L);
        OrderLine saved = new OrderLine("Dahlia Mix", 6, "stems", 3.5);
        saved.setId(520L);
        order.addLine(saved);
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Saturday pickup");
        updated.setId(52L);
        updated.addLine(saved);
        OrderLine separate = new OrderLine("Dahlia Mix", 2, "stems", 4.0);
        separate.setId(521L);
        updated.addLine(separate);
        CountDownLatch added = new CountDownLatch(1);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order), List.of(updated));
        when(orders.addLineReviewed(eq(52L), any(), any())).thenAnswer(invocation -> {
            added.countDown();
            return updated;
        });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("stems");
            textField(root[0], "Order unit price").setText("4.00");
            button(root[0], "Add product to selected order").doClick();
        });

        JButton approve = button(root[0], "Add separate product line");
        assertThat(approve.isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "crm.orderLinePreview").get(0)).getText())
                .isEqualTo("Duplicate product paused · review saved line");
        assertThat(approve.getAccessibleContext().getAccessibleDescription())
                .contains("already saved on order #52", "Choose Add separate product line again");
        assertThat(host.status).contains("Product not added", "distinct price, grade, or allocation");
        verify(orders, never()).addLineReviewed(anyLong(), any(), any());

        SwingUtilities.invokeAndWait(() ->
                textField(root[0], "Order unit price").setText("4.25"));
        JButton resetAdd = button(root[0], "Add product to selected order");
        assertThat(resetAdd.isEnabled()).isTrue();
        assertThat(resetAdd.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("Add the entered product to order #52.");
        verify(orders, never()).addLineReviewed(anyLong(), any(), any());

        SwingUtilities.invokeAndWait(() -> {
            button(root[0], "Add product to selected order").doClick();
            button(root[0], "Add separate product line").doClick();
        });
        assertThat(added.await(2, TimeUnit.SECONDS)).isTrue();
        verify(orders, times(1)).addLineReviewed(eq(52L), any(), argThat(line ->
                "Dahlia Mix".equals(line.getProductName())
                        && Double.compare(line.getQuantity(), 2) == 0
                        && Double.compare(line.getUnitPrice(), 4.25) == 0));
    }

    @Test
    @DisplayName("selected order aggregates stock and blocks unsafe completion")
    void crmSelectedOrderStockGateUsesCumulativeQuantities() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Saturday pickup");
        confirmed.setId(52L);
        confirmed.addLine(new OrderLine("Dahlia Mix", 6, "stems", 3.5));
        confirmed.addLine(new OrderLine("dahlia mix", 6, "stems", 3.5));
        Item item = new Item("Dahlia Mix", "Flowers/Plants",
                3.5, "stems", 1.0, 10, "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(item));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
        });

        JLabel stock = (JLabel) findNamed(root[0], "crm.selectedOrderStock").get(0);
        assertThat(stock.getText())
                .contains("1 order stock gap", "Dahlia Mix short 2");
        assertThat(stock.getAccessibleContext().getAccessibleDescription())
                .contains("blocks completion for order 52", "Dahlia Mix",
                        "refresh Customers and Orders");
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        JButton resolveStock = button(root[0], "Resolve order stock");
        assertThat(resolveStock.isEnabled()).isTrue();
        assertThat(resolveStock.getMnemonic()).isEqualTo(java.awt.event.KeyEvent.VK_B);
        JComponent orderPanel = (JComponent) findNamed(root[0], "crm.orderPanel").get(0);
        assertThat(find(orderPanel, JButton.class).stream()
                .filter(button -> button.getMnemonic() == java.awt.event.KeyEvent.VK_B))
                .extracting(JButton::getText)
                .containsExactly("Resolve order stock");

        SwingUtilities.invokeAndWait(() -> {
            resolveStock.doClick();
            tab.startOrderFulfillment(52L, "the Kitsap Blooms order");
        });

        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.status).contains(
                "Order #52 has 1 stock gap", "Resolve stock", "refresh");
        assertThat(((JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0)).getText())
                .isEqualTo("Completion locked · resolve order stock");
        verify(orders, never()).fulfill(52L);
        verify(orders, never()).fulfillReviewed(
                eq(52L), any(OrderService.OrderReviewSnapshot.class));
    }

    @Test
    @DisplayName("selected-order stock assessment blocks an inventory unit mismatch")
    void crmSelectedOrderStockGateDetectsUnitMismatch() {
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "");
        order.setId(53L);
        order.addLine(new OrderLine("Dahlia Mix", 4, "bunches", 3.5));

        CrmTab.OrderStockAssessment assessment = CrmTab.assessOrderStock(
                order, List.of(new CrmPresenter.InventoryChoice(
                        "Dahlia Mix", "stems", 3.5, 20)));

        assertThat(assessment.gaps()).singleElement().satisfies(gap -> {
            assertThat(gap.unitMismatch()).isTrue();
            assertThat(gap.missing()).isZero();
            assertThat(gap.orderUnit()).isEqualTo("bunches");
            assertThat(gap.inventoryUnit()).isEqualTo("stems");
        });
    }

    @Test
    @DisplayName("selected-order unit mismatch opens the exact saved line with inventory unit")
    void crmSelectedOrderUnitMismatchRoutesToExactLineCorrection() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera", "", "",
                "FLORIST", "Friday delivery");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Keep upright");
        order.setId(53L);
        OrderLine mismatched = new OrderLine("Dahlia Mix", 4, "bunches", 3.5);
        mismatched.setId(530L);
        order.addLine(mismatched);
        Item item = new Item("Dahlia Mix", "Flowers/Plants",
                3.5, "stems", 1.0, 20, "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(item));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
        });

        JLabel stock = (JLabel) findNamed(root[0], "crm.selectedOrderStock").get(0);
        assertThat(stock.getText()).contains(
                "1 order packing issue", "unit mismatch: order bunches, inventory stems");
        assertThat(stock.getAccessibleContext().getAccessibleDescription())
                .contains("Correct order unit", "Inventory's required unit");
        JButton correct = button(root[0], "Correct order unit");
        assertThat(correct.isEnabled()).isTrue();
        assertThat(correct.getMnemonic()).isEqualTo(java.awt.event.KeyEvent.VK_B);

        SwingUtilities.invokeAndWait(correct::doClick);

        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Dahlia Mix");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("4");
        assertThat(textField(root[0], "Order unit").getText()).isEqualTo("stems");
        assertThat(button(root[0], "Save product changes").isEnabled()).isTrue();
        assertThat(host.status).contains(
                "Correct", "Dahlia Mix", "order #53", "Inventory requires stems");
        assertThat(host.selectedTab).isNotEqualTo("Inventory");
        verify(orders, never()).updateLine(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("Open customer loads a filtered-out account without blocking or losing order entry")
    void crmOpenCustomerUsesBackgroundExactAccountHandoff() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST", "Side gate");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger searchCalls = new AtomicInteger();
        AtomicBoolean searchCalledOnUiThread = new AtomicBoolean(true);
        when(customers.search(anyString())).thenAnswer(invocation -> {
            if (searchCalls.incrementAndGet() == 1) {
                return List.of();
            }
            searchCalledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return List.of(customer);
        });
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane workspace = (JTabbedPane) findNamed(root[0], "crm.workspace").get(0);
            workspace.setSelectedIndex(1);
            JTable ordersTable = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            ordersTable.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Cosmos");
            textField(root[0], "Order quantity").setText("12");
            button(root[0], "Open customer").doClick();
            assertThat(workspace.getSelectedIndex()).isZero();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        AtomicBoolean uiStillResponsive = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            uiStillResponsive.set(true);
            assertThat(button(root[0], "Refresh CRM").isEnabled()).isFalse();
            assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
            assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("12");
            assertThat(host.status).contains("Opening customer", "background", "unfinished entry work");
        });
        release.countDown();
        awaitCrmRefresh(root[0]);

        JTable customerTable = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer accounts table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(uiStillResponsive).isTrue();
        assertThat(searchCalledOnUiThread).isFalse();
        assertThat(searchCalls).hasValue(2);
        assertThat(customerTable.getSelectedRow()).isNotNegative();
        assertThat(customerTable.getValueAt(customerTable.getSelectedRow(), 0)).isEqualTo(7L);
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("12");
        assertThat(host.status).contains(
                "Opened customer", "Kitsap Blooms", "order #42", "unfinished entry work were kept");
    }

    @Test
    @DisplayName("Open customer retains its exact-account handoff after reload failure")
    void crmOpenCustomerReloadFailureRetriesExactAccount() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST", "Side gate");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString()))
                .thenReturn(List.of())
                .thenThrow(new IllegalStateException("customer list unavailable"))
                .thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane workspace = (JTabbedPane) findNamed(root[0], "crm.workspace").get(0);
            workspace.setSelectedIndex(1);
            JTable ordersTable = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            ordersTable.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Cosmos");
            button(root[0], "Open customer").doClick();
        });
        awaitCrmRefresh(root[0]);

        assertThat(host.status).contains(
                "Could not reload customer", "Kitsap Blooms", "order #42",
                "unfinished entry work were kept", "Retry Refresh CRM");
        JLabel failed = find(root[0], JLabel.class).stream()
                .filter(label -> "Linked customer not loaded · retry refresh".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(failed.getAccessibleContext().getAccessibleDescription())
                .contains("exact account target", "order", "unfinished entry work", "Refresh CRM");
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(button(root[0], "Refresh CRM").isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(() -> button(root[0], "Refresh CRM").doClick());
        awaitCrmRefresh(root[0]);

        JTable customerTable = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer accounts table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(customerTable.getSelectedRow()).isNotNegative();
        assertThat(customerTable.getValueAt(customerTable.getSelectedRow(), 0)).isEqualTo(7L);
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(host.status).contains("Opened customer", "Kitsap Blooms", "order #42");
        JLabel recovered = find(root[0], JLabel.class).stream()
                .filter(label -> "Opened linked customer · “Kitsap Blooms”".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(recovered.getAccessibleContext().getAccessibleDescription())
                .contains("keyboard focus moved", "customer table");
        verify(customers, times(3)).search(anyString());
    }

    @Test
    @DisplayName("Open customer explains a linked account outside the refreshed search")
    void crmOpenCustomerOutsideSearchHasKeyboardRecovery() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST", "Side gate");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JTabbedPane) findNamed(root[0], "crm.workspace").get(0)).setSelectedIndex(1);
            JTable ordersTable = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            ordersTable.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Cosmos");
            button(root[0], "Open customer").doClick();
        });
        awaitCrmRefresh(root[0]);

        JLabel result = find(root[0], JLabel.class).stream()
                .filter(label -> "Linked customer outside current search".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("Kitsap Blooms", "order #42", "Keyboard focus",
                        "Customer search", "Show all", "unfinished entry work were kept");
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(host.status).contains("outside the current customer search", "Show all");
        verify(customers, times(2)).search(anyString());
    }

    @Test
    @DisplayName("selected-order readiness routes a legacy sub-unit line into correction")
    void crmSelectedOrderStockGateCorrectsLegacySubUnitQuantity() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Saturday pickup");
        order.setId(54L);
        OrderLine invalid = new OrderLine("Dahlia Mix", 0.4, "stems", 3.5);
        invalid.setId(540L);
        order.addLine(invalid);
        Item item = new Item("Dahlia Mix", "Flowers/Plants",
                3.5, "stems", 1.0, 10, "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(item));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
        });

        JLabel stock = (JLabel) findNamed(root[0], "crm.selectedOrderStock").get(0);
        assertThat(stock.getText()).contains(
                "1 order packing issue", "quantity must round to at least 1 inventory unit");
        assertThat(stock.getAccessibleContext().getAccessibleDescription())
                .contains("blocks completion", "Correct saved quantity", "at least one");
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        JButton correct = button(root[0], "Correct saved quantity");
        assertThat(correct.isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(correct::doClick);

        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Dahlia Mix");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("0.4");
        assertThat(button(root[0], "Save product changes").isEnabled()).isFalse();
        assertThat(((JLabel) findNamed(root[0], "crm.orderLinePreview").get(0)).getText())
                .contains("round to at least 1 inventory unit");
        assertThat(host.status).contains(
                "Correct", "Dahlia Mix", "order #54", "at least one inventory unit");
        verify(orders, never()).fulfill(any());
    }

    @Test
    @DisplayName("customer creation runs off the UI thread and blocks duplicate entry")
    void crmCreatesCustomerInBackgroundOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(customers.add(any())).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return invocation.getArgument(0);
        });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Customer or business name").setText("Kitsap Blooms");
            textField(root[0], "Customer contact person").setText("Sam Rivera");
            button(root[0], "Add customer").doClick();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Adding customer…").isEnabled()).isFalse();
            assertThat(textField(root[0], "Customer or business name").isEnabled()).isFalse();
            button(root[0], "Adding customer…").doClick();
        });
        release.countDown();
        awaitHostStatus(host, "Added customer “Kitsap Blooms”.");
        awaitCrmRefresh(root[0]);

        assertThat(calledOnUiThread).isFalse();
        verify(customers, times(1)).add(any());
        assertThat(textField(root[0], "Customer or business name").getText()).isEmpty();
        assertThat(button(root[0], "Add customer").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("committed customer creation requires refresh only when CRM reload fails")
    void crmSavedCustomerReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer saved = new Customer(41L, "Kitsap Blooms", "Sam Rivera",
                "orders@kitsap.example", "360-555-0100", "WHOLESALE", "Thursday pickup");
        when(customers.search(anyString())).thenReturn(List.of(), List.of(saved));
        when(customers.getAll()).thenReturn(List.of(), List.of(saved));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of())
                .thenThrow(new IllegalStateException("customer reload unavailable"))
                .thenReturn(List.of());
        when(customers.add(any())).thenReturn(saved);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Customer or business name").setText("Kitsap Blooms");
            textField(root[0], "Customer contact person").setText("Sam Rivera");
            button(root[0], "Add customer").doClick();
        });
        awaitLabel(root[0], "Customer saved once · reload required");
        awaitCrmRefresh(root[0]);

        JLabel receipt = find(root[0], JLabel.class).stream()
                .filter(label -> "Customer saved once · reload required".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(receipt.getAccessibleContext().getAccessibleDescription())
                .contains("Kitsap Blooms", "saved once", "Refresh CRM only", "do not repeat");
        assertThat(textField(root[0], "Customer or business name").isEnabled()).isFalse();
        assertThat(button(root[0], "Add customer").isEnabled()).isFalse();
        assertThat(button(root[0], "Save edit").isEnabled()).isFalse();
        assertThat(button(root[0], "Delete selected").isEnabled()).isFalse();
        assertThat(button(root[0], "Refresh CRM").isEnabled()).isTrue();
        assertThat(host.status).contains("saved once", "Refresh CRM only");
        verify(customers, times(1)).add(any());

        SwingUtilities.invokeAndWait(() -> button(root[0], "Refresh CRM").doClick());
        awaitLabel(root[0], "Customer saved · “Kitsap Blooms”");

        assertThat(textField(root[0], "Customer or business name").isEnabled()).isTrue();
        assertThat(button(root[0], "Add customer").isEnabled()).isTrue();
        verify(customers, times(1)).add(any());
    }

    @Test
    @DisplayName("committed customer edit requires refresh only when CRM reload fails")
    void crmSavedCustomerEditReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer original = new Customer(41L, "Kitsap Blooms", "Sam Rivera",
                "orders@kitsap.example", "360-555-0100", "WHOLESALE", "Thursday pickup");
        Customer saved = new Customer(41L, "Kitsap Blooms", "Alex Rivera",
                "orders@kitsap.example", "360-555-0100", "WHOLESALE", "Friday pickup");
        when(customers.search(anyString())).thenReturn(List.of(original), List.of(saved));
        when(customers.getAll()).thenReturn(List.of(original), List.of(saved));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of())
                .thenThrow(new IllegalStateException("customer reload unavailable"))
                .thenReturn(List.of());
        when(customers.update(eq(41L), any())).thenReturn(saved);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer accounts table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            button(root[0], "Load selected").doClick();
            textField(root[0], "Customer contact person").setText("Alex Rivera");
            button(root[0], "Save edit").doClick();
        });
        awaitLabel(root[0], "Customer saved once · reload required");
        awaitCrmRefresh(root[0]);

        assertThat(textField(root[0], "Customer or business name").isEnabled()).isFalse();
        assertThat(button(root[0], "Add customer").isEnabled()).isFalse();
        assertThat(button(root[0], "Save edit").isEnabled()).isFalse();
        assertThat(button(root[0], "Delete selected").isEnabled()).isFalse();
        verify(customers, times(1)).update(eq(41L), any());

        SwingUtilities.invokeAndWait(() -> button(root[0], "Refresh CRM").doClick());
        awaitLabel(root[0], "Customer saved · “Kitsap Blooms”");

        assertThat(button(root[0], "Add customer").isEnabled()).isTrue();
        verify(customers, times(1)).update(eq(41L), any());
    }

    @Test
    @DisplayName("failed customer creation keeps the complete form ready to retry")
    void crmCustomerFailureKeepsDraft() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        when(customers.add(any())).thenThrow(new IllegalStateException("customer database busy"));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Customer or business name").setText("Kitsap Blooms");
            textField(root[0], "Customer contact person").setText("Sam Rivera");
            textField(root[0], "Customer notes").setText("Thursday pickup");
            button(root[0], "Add customer").doClick();
        });
        awaitLabel(root[0], "Customer not saved · retry available");

        assertThat(textField(root[0], "Customer or business name").getText())
                .isEqualTo("Kitsap Blooms");
        assertThat(textField(root[0], "Customer contact person").getText())
                .isEqualTo("Sam Rivera");
        assertThat(textField(root[0], "Customer notes").getText())
                .isEqualTo("Thursday pickup");
        assertThat(button(root[0], "Add customer").isEnabled()).isTrue();
        JLabel result = (JLabel) find(root[0], JLabel.class).stream()
                .filter(label -> "Customer not saved · retry available".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("customer database busy", "details were kept", "retry");
        assertThat(host.status).contains("not saved", "kept", "retry");
    }

    @Test
    @DisplayName("customer validation stays inline and clears when the owner corrects it")
    void crmCustomerValidationIsInlineAndRecoverable() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Customer or business name").setText("Kitsap Blooms");
            textField(root[0], "Customer email").setText("not-an-email");
            textField(root[0], "Customer notes").setText("Saturday pickup");
            button(root[0], "Add customer").doClick();
        });
        awaitLabel(root[0], "Customer not saved · correct highlighted field");

        JTextField email = textField(root[0], "Customer email");
        assertThat(email.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(email.getAccessibleContext().getAccessibleDescription())
                .contains("name@example.com");
        assertThat(textField(root[0], "Customer notes").getText())
                .isEqualTo("Saturday pickup");
        assertThat(host.status).contains("Customer not saved", "highlighted field");
        verify(customers, never()).add(any());

        SwingUtilities.invokeAndWait(() -> email.setText("orders@kitsap.example"));

        assertThat(email.getClientProperty("JComponent.outline")).isNull();
        assertThat(find(root[0], JLabel.class))
                .anySatisfy(label -> assertThat(label.getText())
                        .isEqualTo("Changed · ready to retry customer"));
    }

    @Test
    @DisplayName("duplicate customer creation retains the draft and focuses the existing account")
    void crmDuplicateCustomerRoutesToExistingAccount() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer existing = new Customer(14L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.example", "360-555-0100", "WHOLESALE", "Thursday pickup");
        when(customers.search(anyString())).thenReturn(List.of(), List.of(existing));
        when(customers.getAll()).thenReturn(List.of(existing));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        when(customers.add(any())).thenThrow(
                new CustomerService.DuplicateCustomerNameException(14L, "Kitsap Blooms"));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Customer or business name").setText("kitsap blooms");
            textField(root[0], "Customer contact person").setText("New contact");
            textField(root[0], "Customer notes").setText("Draft details retained");
            button(root[0], "Add customer").doClick();
        });
        awaitLabel(root[0], "“Kitsap Blooms” already exists · no duplicate added");

        assertThat(textField(root[0], "Customer contact person").getText())
                .isEqualTo("New contact");
        assertThat(textField(root[0], "Customer notes").getText())
                .isEqualTo("Draft details retained");
        assertThat(button(root[0], "Review existing customer").isEnabled()).isTrue();
        assertThat(host.status).contains("already exists", "entered details were kept");

        SwingUtilities.invokeAndWait(() -> button(root[0], "Review existing customer").doClick());
        awaitCrmRefresh(root[0]);
        JTable customerTable = find(root[0], JTable.class).stream()
                .filter(table -> "Customer accounts table".equals(
                        table.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(customerTable.getSelectedRow()).isEqualTo(0);
        assertThat(customerTable.getValueAt(0, 0)).isEqualTo(14L);
        assertThat(textField(root[0], "Customer contact person").getText())
                .isEqualTo("New contact");
        assertThat(textField(root[0], "Customer notes").getText())
                .isEqualTo("Draft details retained");
        assertThat(host.status).contains("Finding existing customer", "remain in the form");
        verify(customers).add(any());
    }

    @Test
    @DisplayName("customer deletion runs off the UI thread once with a captured account")
    void crmDeletesCustomerInBackgroundOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@example.test", "360-555-0184", "WHOLESALE", "Thursday pickup");
        when(customers.search(anyString())).thenReturn(List.of(customer), List.of());
        when(customers.getAll()).thenReturn(List.of(customer), List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        doAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(customers).delete(7L);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer accounts table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            button(root[0], "Load selected").doClick();
            tab.startCustomerDelete(7L, "Kitsap Blooms");
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Deleting customer…").isEnabled()).isFalse();
            assertThat(textField(root[0], "Customer or business name").isEnabled()).isFalse();
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer accounts table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertThat(table.isEnabled()).isFalse();
            tab.applyRolePermissions(true);
            assertThat(button(root[0], "Deleting customer…").isEnabled()).isFalse();
            tab.startCustomerDelete(7L, "Kitsap Blooms");
        });
        release.countDown();
        awaitHostStatus(host, "Deleted customer “Kitsap Blooms”.");
        awaitCrmRefresh(root[0]);

        assertThat(calledOnUiThread).isFalse();
        verify(customers, times(1)).delete(7L);
        assertThat(textField(root[0], "Customer or business name").getText()).isEmpty();
        assertThat(button(root[0], "Delete selected").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("committed customer deletion requires refresh only when CRM reload fails")
    void crmDeletedCustomerReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@example.test", "360-555-0184", "WHOLESALE", "Thursday pickup");
        when(customers.search(anyString())).thenReturn(List.of(customer), List.of());
        when(customers.getAll()).thenReturn(List.of(customer), List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of())
                .thenThrow(new IllegalStateException("customer reload unavailable"))
                .thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> tab.startCustomerDelete(7L, "Kitsap Blooms"));
        awaitLabel(root[0], "Customer deleted once · reload required");
        awaitCrmRefresh(root[0]);

        JLabel receipt = find(root[0], JLabel.class).stream()
                .filter(label -> "Customer deleted once · reload required".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(receipt.getAccessibleContext().getAccessibleDescription())
                .contains("deleted once", "Refresh CRM only", "do not repeat");
        assertThat(textField(root[0], "Customer or business name").isEnabled()).isFalse();
        assertThat(button(root[0], "Add customer").isEnabled()).isFalse();
        assertThat(button(root[0], "Delete selected").isEnabled()).isFalse();
        verify(customers, times(1)).delete(7L);

        SwingUtilities.invokeAndWait(() -> button(root[0], "Refresh CRM").doClick());
        awaitLabel(root[0], "Customer deleted · “Kitsap Blooms”");

        assertThat(textField(root[0], "Customer or business name").isEnabled()).isTrue();
        assertThat(button(root[0], "Add customer").isEnabled()).isTrue();
        verify(customers, times(1)).delete(7L);
    }

    @Test
    @DisplayName("failed customer deletion retains selection and the loaded customer form")
    void crmCustomerDeleteFailurePreservesAccount() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@example.test", "360-555-0184", "WHOLESALE", "Thursday pickup");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        doThrow(new IllegalStateException("customer has linked orders"))
                .when(customers).delete(7L);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer accounts table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            button(root[0], "Load selected").doClick();
            tab.startCustomerDelete(7L, "Kitsap Blooms");
        });
        awaitLabel(root[0], "Customer not deleted · retry available");

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer accounts table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JLabel result = find(root[0], JLabel.class).stream()
                .filter(label -> "Customer not deleted · retry available".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.isEnabled()).isTrue();
        assertThat(textField(root[0], "Customer or business name").getText())
                .isEqualTo("Kitsap Blooms");
        assertThat(textField(root[0], "Customer contact person").getText())
                .isEqualTo("Sam Rivera");
        assertThat(button(root[0], "Delete selected").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("customer has linked orders", "account", "loaded customer form",
                        "Delete linked orders first", "retry");
        assertThat(host.status).contains("not deleted", "remains selected",
                "linked orders", "retry");
    }

    @Test
    @DisplayName("initial order creation runs off the UI thread and blocks duplicates")
    void crmCreatesOrderInBackgroundOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder created = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        created.setHandoffType("DELIVERY");
        created.setId(42L);
        created.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(), List.of(created));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(orders.createReviewed(any(), any(LocalDate.class), anyString(), anyString(),
                anyString(), anyList(), anyMap(), anyBoolean()))
                .thenAnswer(invocation -> {
                    calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
                    entered.countDown();
                    assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                    return created;
                });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order date").setText("2026-09-12");
            find(root[0], JComboBox.class).stream()
                    .filter(combo -> "New order handoff type".equals(
                            combo.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow().setSelectedItem("Delivery");
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order unit price").setText("3.50");
            JButton create = button(root[0], "Create & select order");
            assertThat(create.getMnemonic()).isEqualTo(java.awt.event.KeyEvent.VK_N);
            assertThat(button(root[0], "Add product to selected order").getMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_A);
            assertThat(button(root[0], "Confirm order").getMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_M);
            assertThat(button(root[0], "Complete order").getMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_P);
            assertThat(button(root[0], "Ready to complete").getMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_Y);
            assertThat(button(root[0], "Open draft customer").getMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_V);
            assertThat(button(root[0], "Save order date").getMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_T);
            assertThat(button(root[0], "Save handoff type").getMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_F);
            JComponent lifecycle = (JComponent) findNamed(
                    root[0], "crm.orderLifecycleActions").get(0);
            assertThat(lifecycle.getLayout()).isInstanceOf(FlowLayout.class);
            FlowLayout lifecycleLayout = (FlowLayout) lifecycle.getLayout();
            assertThat(lifecycleLayout.getAlignment()).isEqualTo(FlowLayout.LEFT);
            assertThat(find(lifecycle, JButton.class)).extracting(JButton::getText)
                    .containsExactly("Create & select order", "Add product to selected order",
                            "Confirm order", "Complete order");
            assertThat(findNamed(root[0], "crm.newOrderSetup").get(0).isVisible()).isTrue();
            assertThat(findNamed(root[0], "crm.selectedOrderReview").get(0).isVisible())
                    .isFalse();
            assertThat(create.isVisible()).isTrue();
            assertThat(button(root[0], "Add product to selected order").isVisible()).isFalse();
            assertThat(button(root[0], "New order open").isEnabled()).isFalse();
            JComponent orderPanel = (JComponent) findNamed(root[0], "crm.orderPanel").get(0);
            assertThat(find(orderPanel, JButton.class).stream()
                    .filter(button -> button.getMnemonic() == java.awt.event.KeyEvent.VK_V))
                    .extracting(JButton::getText)
                    .containsExactly("Open draft customer");
            assertThat(find(root[0], JLabel.class).stream()
                    .filter(label -> "Product*:".equals(label.getText()))
                    .findFirst().orElseThrow().getDisplayedMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_R);
            assertThat(find(root[0], JLabel.class).stream()
                    .filter(label -> "Handoff*:".equals(label.getText()))
                    .findFirst().orElseThrow().getDisplayedMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_H);
            assertThat(label(root[0], "Selected order date").getLabelFor())
                    .isSameAs(findNamed(root[0], "crm.selectedOrderDate").get(0));
            assertThat(label(root[0], "Selected handoff type").getLabelFor())
                    .isSameAs(findNamed(root[0], "crm.selectedOrderHandoffType").get(0));
            assertThat(label(root[0], "Pickup / packing instructions").getLabelFor())
                    .isSameAs(findNamed(root[0], "crm.selectedOrderNotes").get(0));
            assertThat(label(root[0], "Saved products").getLabelFor())
                    .isSameAs(findNamed(root[0], "crm.selectedOrderLine").get(0));
            assertThat(label(root[0], "More order actions").getLabelFor())
                    .isSameAs(findNamed(root[0], "crm.orderTools").get(0));
            Object submitKey = orderPanel.getInputMap(
                            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .get(javax.swing.KeyStroke.getKeyStroke(
                            java.awt.event.KeyEvent.VK_ENTER,
                            java.awt.event.InputEvent.CTRL_DOWN_MASK));
            assertThat(submitKey).isEqualTo("crm.submitOrderProduct");
            assertThat(orderPanel.getInputMap(
                            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .get(javax.swing.KeyStroke.getKeyStroke(
                            java.awt.event.KeyEvent.VK_F6, 0)))
                    .isEqualTo("crm.cycleOrderFocus");
            orderPanel.getActionMap().get(submitKey).actionPerformed(
                    new java.awt.event.ActionEvent(orderPanel,
                            java.awt.event.ActionEvent.ACTION_PERFORMED, "Ctrl+Enter"));
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Creating order…").isEnabled()).isFalse();
            assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
            JComponent orderPanel = (JComponent) findNamed(root[0], "crm.orderPanel").get(0);
            orderPanel.getActionMap().get("crm.submitOrderProduct").actionPerformed(
                    new java.awt.event.ActionEvent(orderPanel,
                            java.awt.event.ActionEvent.ACTION_PERFORMED, "Ctrl+Enter"));
        });
        release.countDown();
        awaitLabel(root[0], "Selected #42 · Kitsap Blooms · Draft · Delivery · 1 product · $3.50");
        SwingUtilities.invokeAndWait(() -> {
            assertThat(findNamed(root[0], "crm.newOrderSetup").get(0).isVisible()).isFalse();
            assertThat(findNamed(root[0], "crm.selectedOrderReview").get(0).isVisible())
                    .isTrue();
            assertThat(button(root[0], "Add product to selected order").isVisible()).isTrue();
            assertThat(button(root[0], "New order").isEnabled()).isTrue();
        });

        assertThat(calledOnUiThread).isFalse();
        verify(orders, times(1)).createReviewed(any(), eq(LocalDate.of(2026, 9, 12)), eq("DRAFT"),
                anyString(), eq("DELIVERY"), anyList(), eq(Map.of()), eq(false));
        assertThat(textField(root[0], "Order date").getText()).isEqualTo("2026-09-12");
        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
    }

    @Test
    @DisplayName("same-customer same-day active order requires an intentional split-order review")
    void crmPreflightsPossibleDuplicateOrderWithoutLosingDraft() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Jordan", "",
                "360-555-0112", "WHOLESALE", "");
        LocalDate future = LocalDate.now().plusDays(10);
        CustomerOrder existing = new CustomerOrder(
                customer, future, "CONFIRMED", "Use the side entrance");
        existing.setId(42L);
        existing.setHandoffType("DELIVERY");
        existing.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        existing.addLine(new OrderLine("Rose Bunch", 2, "bunches", 18));
        CustomerOrder completed = new CustomerOrder(
                customer, future, "FULFILLED", "Earlier delivery");
        completed.setId(41L);
        completed.setHandoffType("DELIVERY");
        completed.addLine(new OrderLine("Tulip Mix", 1, "bunches", 15));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(), List.of(existing));
        AtomicBoolean lookupOnUiThread = new AtomicBoolean(true);
        when(orders.findByCustomer(7L)).thenAnswer(invocation -> {
            lookupOnUiThread.set(SwingUtilities.isEventDispatchThread());
            return List.of(completed, existing);
        });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order date").setText(future.toString());
            textField(root[0], "Order notes").setText("Gate code 1842");
            textField(root[0], "Order product name").setText("Cosmos");
            textField(root[0], "Order quantity").setText("3");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("16.50");
            button(root[0], "Create & select order").doClick();
        });
        awaitLabel(root[0], "1 active order already on " + future + " · draft kept");

        JLabel selected = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(lookupOnUiThread).isFalse();
        assertThat(selected.getAccessibleContext().getAccessibleDescription()).contains(
                "Kitsap Blooms", "#42 Confirmed", "Delivery", "2 products", "$64.00",
                "Open the intended order", "Create separate order", "preserves every entered field");
        assertThat(host.status).contains(
                "Order not created", "active order already exists", "complete draft was kept");
        assertThat(textField(root[0], "Order date").getText()).isEqualTo(future.toString());
        assertThat(textField(root[0], "Order notes").getText()).isEqualTo("Gate code 1842");
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("3");
        assertThat(textField(root[0], "Order unit").getText()).isEqualTo("bunches");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("16.50");
        assertThat(button(root[0], "Create & select order").isEnabled()).isTrue();
        verify(orders, times(1)).findByCustomer(7L);
        verify(orders, never()).createReviewed(any(), any(LocalDate.class), anyString(),
                anyString(), anyString(), anyList(), anyMap(), anyBoolean());

        CrmPresenter.ActiveOrderMatch match = new CrmPresenter.ActiveOrderMatch(
                CrmPresenter.orderView(List.of(existing)).rows().get(0),
                OrderService.reviewSnapshot(existing));
        SwingUtilities.invokeAndWait(() -> tab.openExistingOrderForProduct(
                match, customer.getName(), future.toString(),
                new CrmPresenter.LineDraft("Cosmos", "3", "bunches", "16.50")));
        awaitCrmRefresh(root[0]);
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                .isEqualTo("Order #42 opened · “Cosmos” preserved for review");

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.getValueAt(0, 0)).isEqualTo(42L);
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("3");
        assertThat(textField(root[0], "Order unit").getText()).isEqualTo("bunches");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("16.50");
        assertThat(textField(root[0], "Order notes").getText()).isEqualTo("Gate code 1842");
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();
        assertThat(selected.getAccessibleContext().getAccessibleDescription()).contains(
                "unsaved", "Cosmos", "bound to this exact order", "Nothing has been added yet");
        verify(orders, never()).createReviewed(any(), any(LocalDate.class), anyString(),
                anyString(), anyString(), anyList(), anyMap(), anyBoolean());
    }

    @Test
    @DisplayName("F6 reveals and announces the order list and compact product entry destinations")
    void crmF6RevealsAndAnnouncesOrderWorkflowFocus() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        JTable orderTable = (JTable) findNamed(root[0], "crm.orders.table").get(0);

        SwingUtilities.invokeAndWait(() -> tab.cycleOrderWorkflowFocus(null));
        assertThat(host.status).contains(
                "Keyboard focus moved to the order list", "arrow keys", "F6", "Product entry");

        SwingUtilities.invokeAndWait(() -> tab.cycleOrderWorkflowFocus(orderTable));
        assertThat(host.status).contains(
                "Keyboard focus moved to Product entry", "Ctrl+Enter", "submits");

        JTextField product = textField(root[0], "Order product name");
        SwingUtilities.invokeAndWait(() -> {
            product.setEnabled(false);
            orderTable.setEnabled(true);
            tab.cycleOrderWorkflowFocus(orderTable);
        });
        assertThat(host.status).contains(
                "Product entry is temporarily locked", "focus remains on the order list", "F6 again");

        SwingUtilities.invokeAndWait(() -> {
            orderTable.setEnabled(false);
            tab.cycleOrderWorkflowFocus(null);
        });
        assertThat(host.status).contains(
                "F6 focus did not move", "order list and Product entry are locked", "Wait", "F6 again");

        SwingUtilities.invokeAndWait(() -> {
            product.setEnabled(true);
            tab.cycleOrderWorkflowFocus(null);
        });
        assertThat(host.status).contains(
                "order list is temporarily locked", "focus moved to Product entry",
                "wait for the result");
    }

    @Test
    @DisplayName("F6 routes an empty order list to visible new-order product entry")
    void crmF6EmptyOrderListRevealsProductEntry() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> tab.cycleOrderWorkflowFocus(null));

        assertThat(host.status).contains(
                "order list is empty", "Product entry", "new-order fields", "Ctrl+Enter");
    }

    @Test
    @DisplayName("future order creation focuses exact Draft customer and date before selection")
    void crmFutureOrderCannotDisappearBehindPreviousFilters() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        LocalDate future = LocalDate.now().plusDays(21);
        CustomerOrder created = new CustomerOrder(customer, future, "DRAFT", "Loading dock");
        created.setId(64L);
        created.addLine(new OrderLine("Dahlia Mix", 4, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenAnswer(invocation -> {
                    String status = invocation.getArgument(0);
                    String customerQuery = invocation.getArgument(1);
                    LocalDate from = invocation.getArgument(2);
                    LocalDate to = invocation.getArgument(3);
                    return "DRAFT".equals(status)
                            && "Kitsap Blooms".equals(customerQuery)
                            && future.equals(from) && future.equals(to)
                            ? List.of(created) : List.of();
                });
        when(orders.createReviewed(any(), any(LocalDate.class), anyString(), anyString(),
                anyString(), anyList(), anyMap(), anyBoolean()))
                .thenReturn(created);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        assertThat(((JTextField) findNamed(root[0], "crm.orderFilterTo").get(0)).getText())
                .isEqualTo(LocalDate.now().toString());

        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order date").setText(future.toString());
            textField(root[0], "Order notes").setText("Loading dock");
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order quantity").setText("4");
            textField(root[0], "Order unit price").setText("3.50");
            button(root[0], "Create & select order").doClick();
        });
        awaitLabel(root[0], "Selected #64 · Kitsap Blooms · Draft · 1 product · $14.00");

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.getValueAt(0, 0)).isEqualTo(64L);
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.orderStatusFilter").get(0)).getSelectedItem())
                .isEqualTo("Draft");
        assertThat(textField(root[0], "Order customer filter").getText())
                .isEqualTo("Kitsap Blooms");
        assertThat(((JTextField) findNamed(root[0], "crm.orderFilterFrom").get(0)).getText())
                .isEqualTo(future.toString());
        assertThat(((JTextField) findNamed(root[0], "crm.orderFilterTo").get(0)).getText())
                .isEqualTo(future.toString());
        assertThat(host.status).contains(
                "Draft order #64", "saved", "selected it", "next product");
        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
        assertThat(textField(root[0], "Order notes").getText()).isEmpty();
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        verify(orders, times(1)).createReviewed(eq(7L), eq(future), eq("DRAFT"),
                eq("Loading dock"), eq("PICKUP"), anyList(), eq(Map.of()), eq(false));
        verify(orders).filter(eq("DRAFT"), eq("Kitsap Blooms"), eq(future), eq(future));
    }

    @Test
    @DisplayName("post-save reload failure names the saved order and prevents duplicate retry")
    void crmSavedOrderReloadFailureRetainsExactSelectionRetry() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        LocalDate future = LocalDate.now().plusDays(14);
        CustomerOrder created = new CustomerOrder(customer, future, "DRAFT", "Call first");
        created.setId(65L);
        created.addLine(new OrderLine("Rose Bunch", 2, "bunches", 18));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of())
                .thenThrow(new IllegalStateException("Order database temporarily unavailable"))
                .thenReturn(List.of(created));
        when(orders.createReviewed(any(), any(LocalDate.class), anyString(), anyString(),
                anyString(), anyList(), anyMap(), anyBoolean()))
                .thenReturn(created);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order date").setText(future.toString());
            textField(root[0], "Order notes").setText("Call first");
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("18.00");
            button(root[0], "Create & select order").doClick();
        });
        awaitLabel(root[0],
                "CRM refresh failed · Orders unavailable · previous snapshot shown · retry");
        awaitLabel(root[0], "Order #65 saved once · exact-order reload failed");

        assertThat(((JLabel) findNamed(root[0], "crm.refreshStatus").get(0)).getText())
                .startsWith("CRM refresh failed");
        assertThat(host.status).contains(
                "Order #65", "was saved", "could not reload", "Retry Refresh CRM",
                "do not create");
        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
        assertThat(textField(root[0], "Order notes").getText()).isEmpty();
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.orderStatusFilter").get(0)).getSelectedItem())
                .isEqualTo("Draft");
        assertThat(textField(root[0], "Order customer filter").getText())
                .isEqualTo("Kitsap Blooms");
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        assertThat(textField(root[0], "Order date").isEnabled()).isFalse();
        assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0))
                .getAccessibleContext().getAccessibleDescription()).contains(
                "Draft order #65", "saved once", "fields were cleared and locked",
                "Retry Refresh CRM only", "do not create this order again");
        assertThat(button(root[0], "Create & select order").getToolTipText()).contains(
                "saved once", "locked to prevent a duplicate", "do not create");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "#65", "only safe retry action", "do not create");

        SwingUtilities.invokeAndWait(() -> submitOrderProductShortcut(root[0]));
        assertThat(host.status).contains(
                "order #65", "saved once", "Refresh CRM only", "do not create this order again");
        verify(orders, times(1)).createReviewed(eq(7L), eq(future), eq("DRAFT"),
                eq("Call first"), eq("PICKUP"), anyList(), eq(Map.of()), eq(false));

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitLabel(root[0], "Selected #65 · Kitsap Blooms · Draft · 1 product · $36.00");

        assertThat(host.status).contains(
                "Draft order #65", "saved", "selected it", "next product");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).isEqualTo(
                "Reload customers, filtered orders, and inventory choices.");
        verify(orders, times(1)).createReviewed(eq(7L), eq(future), eq("DRAFT"),
                eq("Call first"), eq("PICKUP"), anyList(), eq(Map.of()), eq(false));
    }

    @Test
    @DisplayName("failed initial order creation preserves every field for retry")
    void crmOrderCreationFailureKeepsDraft() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        when(orders.createReviewed(any(), any(LocalDate.class), anyString(), anyString(),
                anyString(), anyList(), anyMap(), anyBoolean()))
                .thenThrow(new IllegalStateException("orders database busy"));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order date").setText("2026-09-13");
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order quantity").setText("12");
            textField(root[0], "Order unit price").setText("3.50");
            textField(root[0], "Order notes").setText("Saturday market");
            button(root[0], "Create & select order").doClick();
        });
        awaitLabel(root[0], "Order not created · retry available");

        assertThat(textField(root[0], "Order date").getText()).isEqualTo("2026-09-13");
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Dahlia Mix");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("12");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("3.50");
        assertThat(textField(root[0], "Order notes").getText()).isEqualTo("Saturday market");
        assertThat(button(root[0], "Create & select order").isEnabled()).isTrue();
        JLabel result = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("orders database busy", "details were kept", "retry");
        assertThat(host.status).contains("not created", "kept", "retry");
    }

    @Test
    @DisplayName("concurrent same-day order creation refreshes review and preserves full draft")
    void crmConcurrentDuplicateCreationKeepsDraftForReview() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        LocalDate day = LocalDate.now().plusDays(12);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        when(orders.createReviewed(any(), any(LocalDate.class), anyString(), anyString(),
                anyString(), anyList(), anyMap(), anyBoolean())).thenThrow(
                new OrderService.DuplicateOrderReviewChangedException(
                        7L, day, Set.of(), Set.of(88L)));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order date").setText(day.toString());
            textField(root[0], "Order product name").setText("Cosmos");
            textField(root[0], "Order quantity").setText("3");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("16.50");
            textField(root[0], "Order notes").setText("Gate code 1842");
            button(root[0], "Create & select order").doClick();
        });
        awaitLabel(root[0], "Active orders changed · draft kept · review again");

        JLabel result = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "No order was created", "complete new-order draft was kept", "review");
        assertThat(textField(root[0], "Order date").getText()).isEqualTo(day.toString());
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("3");
        assertThat(textField(root[0], "Order unit").getText()).isEqualTo("bunches");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("16.50");
        assertThat(textField(root[0], "Order notes").getText()).isEqualTo("Gate code 1842");
        assertThat(host.status).contains("not created", "draft was kept", "duplicate review");
        verify(orders, times(1)).createReviewed(any(), eq(day), eq("DRAFT"),
                eq("Gate code 1842"), eq("PICKUP"), anyList(), eq(Map.of()), eq(false));
    }

    @Test
    @DisplayName("invalid order date disables creation and keyboard submit reveals it")
    void crmInvalidOrderDateKeepsDraftAndSkipsService() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order date").setText("Saturday");
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order quantity").setText("12");
            textField(root[0], "Order unit price").setText("3.50");
            assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
            submitOrderProductShortcut(root[0]);
        });
        awaitLabel(root[0], "Order not created · check order date");

        JTextField date = textField(root[0], "Order date");
        assertThat(date.getText()).isEqualTo("Saturday");
        assertThat(date.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(date.getAccessibleContext().getAccessibleDescription())
                .contains("YYYY-MM-DD");
        assertThat(date.getSelectedText()).isEqualTo("Saturday");
        assertThat(textField(root[0], "Order product name").getText())
                .isEqualTo("Dahlia Mix");
        assertThat(host.status).contains("Order not created", "highlighted order date");
        verify(orders, never()).createReviewed(any(), any(LocalDate.class), anyString(),
                anyString(), anyString(), anyList(), anyMap(), anyBoolean());

        SwingUtilities.invokeAndWait(() -> date.setText(LocalDate.now().plusDays(1).toString()));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(date.getClientProperty("JComponent.outline")).isNull();
        assertThat(button(root[0], "Create & select order").isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                .isEqualTo("Changed · ready to retry order");
    }

    @Test
    @DisplayName("overlong order instructions disable creation and remain intact for correction")
    void crmOverlongOrderInstructionsStayLocalAndRecoverInline() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        String overlongInstructions = "x".repeat(2001);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order date").setText(LocalDate.now().plusDays(1).toString());
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order unit price").setText("3.50");
            textField(root[0], "Order notes").setText(overlongInstructions);
            assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
            submitOrderProductShortcut(root[0]);
        });
        awaitLabel(root[0], "Order not created · check order instructions");

        JTextField notes = textField(root[0], "Order notes");
        assertThat(notes.getText()).isEqualTo(overlongInstructions);
        assertThat(notes.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(notes.getAccessibleContext().getAccessibleDescription())
                .contains("cannot exceed 2000 characters");
        assertThat(textField(root[0], "Order product name").getText())
                .isEqualTo("Dahlia Mix");
        assertThat(host.status).contains("Order not created", "highlighted order instructions");
        verify(orders, never()).createReviewed(any(), any(LocalDate.class), anyString(),
                anyString(), anyString(), anyList(), anyMap(), anyBoolean());

        SwingUtilities.invokeAndWait(() -> notes.setText("Leave buckets at the side gate"));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(notes.getClientProperty("JComponent.outline")).isNull();
        assertThat(button(root[0], "Create & select order").isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                .isEqualTo("Changed · ready to retry order");
    }

    @Test
    @DisplayName("order keyboard submit reveals each invalid product value in entry order")
    void crmKeyboardSubmitTargetsFirstInvalidProductValue() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order quantity").setText("twelve");
            textField(root[0], "Order unit").setText("");
            textField(root[0], "Order unit price").setText("free");
            submitOrderProductShortcut(root[0]);
        });

        JTextField quantity = textField(root[0], "Order quantity");
        assertThat(quantity.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(quantity.getSelectedText()).isEqualTo("twelve");
        assertThat(textField(root[0], "Order product name")
                .getClientProperty("JComponent.outline")).isNull();
        assertThat(host.status).contains("Quantity", "first value", "Ctrl+Enter");

        SwingUtilities.invokeAndWait(() -> {
            quantity.setText("12");
            submitOrderProductShortcut(root[0]);
        });
        JTextField unit = textField(root[0], "Order unit");
        assertThat(unit.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(quantity.getClientProperty("JComponent.outline")).isNull();

        SwingUtilities.invokeAndWait(() -> {
            unit.setText("stems");
            submitOrderProductShortcut(root[0]);
        });
        JTextField price = textField(root[0], "Order unit price");
        assertThat(price.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(price.getSelectedText()).isEqualTo("free");
        verify(orders, never()).createReviewed(any(), any(LocalDate.class), anyString(),
                anyString(), anyString(), anyList(), anyMap(), anyBoolean());
    }

    @Test
    @DisplayName("order product limits mark the exact field while workers type")
    void crmOrderProductLimitsValidateInlineWithoutSubmission() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        CrmTab tab = new CrmTab(customers, orders, null, new TrackingHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JTextField product = textField(root[0], "Order product name");
            JTextField quantity = textField(root[0], "Order quantity");
            JTextField unit = textField(root[0], "Order unit");
            JTextField price = textField(root[0], "Order unit price");
            product.setText("x".repeat(256));
            price.setText("2.00");
            assertThat(product.getClientProperty("JComponent.outline")).isEqualTo("error");
            assertThat(product.getAccessibleContext().getAccessibleDescription())
                    .contains("Product name", "255 characters");
            assertThat(quantity.getClientProperty("JComponent.outline")).isNull();
            assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();

            product.setText("Dahlia Mix");
            quantity.setText("2147483648");
            assertThat(product.getClientProperty("JComponent.outline")).isNull();
            assertThat(quantity.getClientProperty("JComponent.outline")).isEqualTo("error");
            assertThat(quantity.getToolTipText()).contains("no more than 2147483647");

            quantity.setText("12");
            unit.setText("u".repeat(65));
            assertThat(quantity.getClientProperty("JComponent.outline")).isNull();
            assertThat(unit.getClientProperty("JComponent.outline")).isEqualTo("error");
            assertThat(unit.getAccessibleContext().getAccessibleDescription())
                    .contains("Unit", "64 characters");

            unit.setText("stems");
            price.setText("1000000001");
            assertThat(unit.getClientProperty("JComponent.outline")).isNull();
            assertThat(price.getClientProperty("JComponent.outline")).isEqualTo("error");
            assertThat(price.getToolTipText()).contains("between 0 and 1000000000");

            price.setText("3.50");
            assertThat(List.of(product, quantity, unit, price))
                    .allSatisfy(field -> assertThat(
                            field.getClientProperty("JComponent.outline")).isNull());
            assertThat(button(root[0], "Create & select order").isEnabled()).isTrue();
            assertThat(((JLabel) findNamed(root[0], "crm.orderLinePreview").get(0)).getText())
                    .contains("$42.00 line total");
        });

        verify(orders, never()).createReviewed(any(), any(LocalDate.class), anyString(),
                anyString(), anyString(), anyList(), anyMap(), anyBoolean());
    }

    @Test
    @DisplayName("adding an order product runs off the UI thread and blocks duplicates")
    void crmAddsOrderLineInBackgroundOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder existing = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        existing.setId(42L);
        existing.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder updated = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        updated.setId(42L);
        updated.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        updated.addLine(new OrderLine("Rose Bunch", 2, "bunches", 18));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(existing), List.of(updated));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        AtomicReference<OrderService.OrderReviewSnapshot> capturedReview =
                new AtomicReference<>();
        when(orders.addLineReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class), any()))
                .thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            capturedReview.set(invocation.getArgument(1));
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return updated;
        });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("18.00");
            button(root[0], "Add product to selected order").doClick();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Adding product…").isEnabled()).isFalse();
            assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertThat(table.isEnabled()).isFalse();
            button(root[0], "Adding product…").doClick();
        });
        release.countDown();
        awaitHostStatus(host, "Added “Rose Bunch” to order #42.");
        awaitCrmRefresh(root[0]);

        assertThat(calledOnUiThread).isFalse();
        verify(orders, times(1)).addLineReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class), any());
        verify(orders, never()).addLine(eq(42L), any());
        assertThat(capturedReview.get()).isEqualTo(OrderService.reviewSnapshot(existing));
        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
        assertThat(textField(root[0], "Order product name")
                .getAccessibleContext().getAccessibleDescription()).contains(
                "Next product entry is ready", "selected exact order", "Ctrl+Enter");
        assertThat(host.status).contains(
                "Exact order selected", "next product entry is ready", "Ctrl+Enter");
    }

    @Test
    @DisplayName("saved product outside the refreshed view cannot be submitted twice")
    void crmReportsSavedProductWhenOrderLeavesCurrentView() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder existing = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        existing.setId(42L);
        existing.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        updated.setId(42L);
        updated.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        updated.addLine(new OrderLine("Rose Bunch", 2, "bunches", 18));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(existing), List.of());
        OrderService.OrderReviewSnapshot review = OrderService.reviewSnapshot(existing);
        when(orders.addLineReviewed(eq(42L), eq(review), any())).thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("18.00");
            button(root[0], "Add product to selected order").doClick();
        });
        awaitLabel(root[0], "Product saved · order #42 outside current view");
        awaitCrmRefresh(root[0]);

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel status = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(table.getRowCount()).isZero();
        assertThat(table.getSelectedRow()).isEqualTo(-1);
        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("1");
        assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
        assertThat(status.getAccessibleContext().getAccessibleDescription()).contains(
                "Rose Bunch", "saved once", "order #42", "current filtered view",
                "cleared to prevent a duplicate", "Show all", "do not enter");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "Rose Bunch", "saved once", "only safe retry action", "do not enter");
        assertThat(host.status).contains(
                "saved once", "cleared to prevent a duplicate", "do not enter");
        verify(orders, times(1)).addLineReviewed(eq(42L), eq(review), any());
        verify(orders, never()).addLine(eq(42L), any());
    }

    @Test
    @DisplayName("saved product locks stale order actions when its automatic reload fails")
    void crmSavedProductReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder existing = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        existing.setId(42L);
        existing.setHandoffType("PICKUP");
        existing.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        updated.setId(42L);
        updated.setHandoffType("PICKUP");
        updated.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        updated.addLine(new OrderLine("Rose Bunch", 2, "bunches", 18));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(existing))
                .thenThrow(new IllegalStateException("order database temporarily unavailable"))
                .thenReturn(List.of(updated));
        OrderService.OrderReviewSnapshot review = OrderService.reviewSnapshot(existing);
        when(orders.addLineReviewed(eq(42L), eq(review), any())).thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("18.00");
            button(root[0], "Add product to selected order").doClick();
        });
        awaitLabel(root[0], "Product saved · exact order reload failed");

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel status = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.getValueAt(0, 5)).isEqualTo(1);
        assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(status.getAccessibleContext().getAccessibleDescription()).contains(
                "Rose Bunch", "saved once", "could not reload the exact order",
                "cleared to prevent a duplicate", "Retry Refresh CRM", "do not enter");
        assertThat(button(root[0], "Confirm order").getToolTipText()).contains(
                "saved once", "do not enter the product again");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "saved once", "only safe retry action", "do not enter");
        verify(orders, times(1)).addLineReviewed(eq(42L), eq(review), any());

        SwingUtilities.invokeAndWait(() -> submitOrderProductShortcut(root[0]));
        assertThat(host.status).contains(
                "Rose Bunch", "saved once", "order #42", "Refresh CRM", "do not enter");
        verify(orders, times(1)).addLineReviewed(eq(42L), eq(review), any());

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitHostStatus(host, "Added “Rose Bunch” to order #42. Exact order selected; "
                + "the next product entry is ready. Use Ctrl+Enter to add it after review.");
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.getValueAt(0, 5)).isEqualTo(2);
        assertThat(textField(root[0], "Order product name").isEnabled()).isTrue();
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).isEqualTo(
                "Reload customers, filtered orders, and inventory choices.");
        verify(orders, times(1)).addLineReviewed(eq(42L), eq(review), any());
        verify(orders, never()).addLine(eq(42L), any());
    }

    @Test
    @DisplayName("stale product add keeps worker entry and refreshes a changed order")
    void crmRejectsProductAddWhenDisplayedOrderChanged() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam",
                "sam@example.test", "360-555-0100", "WHOLESALE", "");
        CustomerOrder reviewed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        reviewed.setId(42L);
        reviewed.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder current = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "CONFIRMED", "Front counter");
        current.setId(42L);
        current.addLine(new OrderLine("Dahlia Mix", 2, "stems", 3.75));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(reviewed), List.of(current));
        OrderService.OrderReviewSnapshot firstReview = OrderService.reviewSnapshot(reviewed);
        when(orders.addLineReviewed(eq(42L), eq(firstReview), any()))
                .thenThrow(new OrderService.StaleOrderReviewException(
                        42L, "product entry",
                        "The product was not added and inventory was unchanged",
                        "review the order and add the product again"));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("18.00");
            button(root[0], "Add product to selected order").doClick();
        });
        awaitLabel(root[0], "Order #42 changed · product not added");
        awaitCrmRefresh(root[0]);

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer orders table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JLabel result = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.getValueAt(0, 1)).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(table.getValueAt(0, 3)).isEqualTo("Confirmed");
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Rose Bunch");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("2");
        assertThat(textField(root[0], "Order unit").getText()).isEqualTo("bunches");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("18.00");
        assertThat(button(root[0], "Review changed order before adding").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "product was not added", "inventory was unchanged",
                "entered product was kept", "Compare it with the refreshed customer");
        assertThat(host.status).contains(
                "changed before the product saved", "entered product was kept");
        verify(orders).addLineReviewed(eq(42L), eq(firstReview), any());
        verify(orders, never()).addLine(eq(42L), any());
    }

    @Test
    @DisplayName("CRM refresh requires changed-order review before adding a preserved product")
    void crmReviewsChangedOrderContextBeforeAddingPreservedProduct() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam",
                "sam@example.test", "360-555-0100", "WHOLESALE", "");
        CustomerOrder reviewed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        reviewed.setHandoffType("PICKUP");
        reviewed.setId(42L);
        reviewed.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder current = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "DRAFT", "Front counter");
        current.setHandoffType("DELIVERY");
        current.setId(42L);
        current.addLine(new OrderLine("Dahlia Mix", 2, "stems", 3.75));
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "DRAFT", "Front counter");
        updated.setHandoffType("DELIVERY");
        updated.setId(42L);
        updated.addLine(new OrderLine("Dahlia Mix", 2, "stems", 3.75));
        updated.addLine(new OrderLine("Rose Bunch", 2, "bunches", 18));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(reviewed), List.of(current), List.of(updated));
        OrderService.OrderReviewSnapshot currentReview = OrderService.reviewSnapshot(current);
        when(orders.addLineReviewed(eq(42L), eq(currentReview), any())).thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("18.00");
            button(root[0], "Refresh CRM").doClick();
        });
        awaitCrmRefresh(root[0]);

        JButton review = button(root[0], "Review changed order before adding");
        assertThat(review.isEnabled()).isTrue();
        assertThat(review.getToolTipText()).contains(
                "2026-09-12", "Pickup", "Back dock", "1 stems Dahlia Mix",
                "2026-09-13", "Delivery", "Front counter", "2 stems Dahlia Mix",
                "Rose Bunch", "does not add the product");
        assertThat(textField(root[0], "Order product name").getText())
                .isEqualTo("Rose Bunch");

        SwingUtilities.invokeAndWait(review::doClick);
        verify(orders, never()).addLineReviewed(anyLong(), any(), any());
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();
        assertThat(host.status).contains(
                "acknowledged", "Nothing was added", "inventory was unchanged");

        SwingUtilities.invokeAndWait(
                () -> button(root[0], "Add product to selected order").doClick());
        awaitHostStatus(host, "Added “Rose Bunch” to order #42.");
        awaitCrmRefresh(root[0]);

        verify(orders).addLineReviewed(eq(42L), eq(currentReview), any());
        verify(orders, never()).addLine(eq(42L), any());
        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
    }

    @Test
    @DisplayName("deleted add target preserves product and requires explicit new-order recovery")
    void crmRecoversProductDraftAfterTargetOrderDisappears() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam",
                "sam@example.test", "360-555-0100", "WHOLESALE", "");
        CustomerOrder reviewed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        reviewed.setId(42L);
        reviewed.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(reviewed), List.of());
        OrderService.OrderReviewSnapshot firstReview = OrderService.reviewSnapshot(reviewed);
        when(orders.addLineReviewed(eq(42L), eq(firstReview), any()))
                .thenThrow(new OrderService.StaleOrderReviewException(
                        42L, "product entry",
                        "The product was not added and inventory was unchanged",
                        "review the order and add the product again", true));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            // Preserve a separate new-order product before entering work for order #42.
            textField(root[0], "Order product name").setText("Tulip Bunch");
            textField(root[0], "Order quantity").setText("1");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("15.00");
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("18.00");
            button(root[0], "Add product to selected order").doClick();
        });
        awaitLabel(root[0], "Order #42 missing · product preserved");
        awaitCrmRefresh(root[0]);

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer orders table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JLabel result = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(table.getRowCount()).isZero();
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Rose Bunch");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("2");
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
        JButton recover = button(root[0], "Move product to new order");
        assertThat(recover.isEnabled()).isTrue();
        assertThat(recover.getMnemonic()).isEqualTo(java.awt.event.KeyEvent.VK_W);
        assertThat(recover.getAccessibleContext().getAccessibleDescription())
                .contains("preserved product", "missing order", "new-order draft");
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "target order no longer exists", "Move product to new order", "Clear product entry");

        SwingUtilities.invokeAndWait(() -> submitOrderProductShortcut(root[0]));
        assertThat(host.status).contains("Product not submitted", "Order #42", "missing");
        verify(orders, never()).createReviewed(
                any(), any(), anyString(), anyString(), anyString(), anyList(),
                anyMap(), anyBoolean());

        SwingUtilities.invokeAndWait(() -> button(root[0], "Move product to new order").doClick());
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Rose Bunch");
        assertThat(button(root[0], "Create & select order").isEnabled()).isTrue();
        assertThat(host.status).contains(
                "Moved the preserved product", "missing order #42", "earlier new-order", "paused");

        SwingUtilities.invokeAndWait(() -> {
            button(root[0], "Clear product entry").doClick();
            button(root[0], "Discard product entry").doClick();
        });
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Tulip Bunch");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("1");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("15.00");
        assertThat(host.status).contains("earlier new-order product draft was restored");
    }

    @Test
    @DisplayName("saved order product edits in place and Enter submits the captured line")
    void crmEditsSelectedOrderLineInBackground() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = new CustomerOrder(customer, LocalDate.now(), "CONFIRMED", "");
        original.setId(42L);
        OrderLine originalLine = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        originalLine.setId(101L);
        original.addLine(originalLine);
        CustomerOrder updated = new CustomerOrder(customer, LocalDate.now(), "CONFIRMED", "");
        updated.setId(42L);
        OrderLine updatedLine = new OrderLine("Dahlia Mix", 18, "stems", 4.25);
        updatedLine.setId(101L);
        updated.addLine(updatedLine);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 4.25, "stems", 1, 50, "")));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(original), List.of(updated));
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        OrderService.OrderReviewLine reviewedLine = new OrderService.OrderReviewLine(
                101L, "Dahlia Mix", 12, "stems", 3.5);
        when(orders.updateLineReviewed(
                eq(42L), eq(101L), eq(OrderService.reviewSnapshot(original)),
                eq(reviewedLine), any())).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            return updated;
        });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            button(root[0], "Edit selected product").doClick();
            assertThat(textField(root[0], "Order product name").getText())
                    .isEqualTo("Dahlia Mix");
            assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("12");
            assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("3.50");
            assertThat(button(root[0], "Save product changes").isEnabled()).isTrue();
            assertThat(button(root[0], "Clear product entry").isEnabled()).isTrue();
            textField(root[0], "Order quantity").setText("18");
            JTextField price = textField(root[0], "Order unit price");
            price.setText("4.25");
            price.postActionEvent();
        });
        awaitHostStatus(host, "Updated “Dahlia Mix” on order #42. Order totals, Market Day, "
                + "and stock guidance are refreshing; inventory was not changed.");
        awaitCrmRefresh(root[0]);
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);

        JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0);
        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer orders table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(calledOnUiThread).isFalse();
        verify(orders, times(1)).updateLineReviewed(
                eq(42L), eq(101L), eq(OrderService.reviewSnapshot(original)),
                eq(reviewedLine), any());
        verify(orders, never()).updateLine(eq(42L), eq(101L), any());
        verify(orders, never()).addLine(eq(42L), any());
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.getValueAt(0, 6)).isEqualTo("76.50");
        assertThat(savedProducts.getSelectedItem().toString())
                .contains("Dahlia Mix", "18 stems", "$4.25");
        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
        assertThat(host.status).contains("inventory was not changed");
    }

    @Test
    @DisplayName("saved product correction locks stale order actions when reload fails")
    void crmSavedProductCorrectionReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        CustomerOrder updated = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 18, "stems", 4.25);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(original))
                .thenThrow(new IllegalStateException("order database temporarily unavailable"))
                .thenReturn(List.of(updated));
        OrderService.OrderReviewLine reviewedLine = new OrderService.OrderReviewLine(
                101L, "Dahlia Mix", 12, "stems", 3.5);
        when(orders.updateLineReviewed(eq(42L), eq(101L),
                eq(OrderService.reviewSnapshot(original)), eq(reviewedLine), any()))
                .thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            button(root[0], "Edit selected product").doClick();
            textField(root[0], "Order quantity").setText("18");
            textField(root[0], "Order unit price").setText("4.25");
            button(root[0], "Save product changes").doClick();
        });
        awaitLabel(root[0], "Product updated once · exact order reload failed");

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(table.getValueAt(0, 6)).isEqualTo("42.00");
        assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
        assertThat(button(root[0], "Edit selected product").isEnabled()).isFalse();
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "Dahlia Mix", "saved once", "order #42", "could not reload",
                "Retry Refresh CRM only", "do not save or re-enter");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "saved once", "only safe retry action", "do not save");
        verify(orders, times(1)).updateLineReviewed(
                eq(42L), eq(101L), eq(OrderService.reviewSnapshot(original)),
                eq(reviewedLine), any());

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Product updated once · order #42 refreshed");
        assertThat(table.getValueAt(0, 6)).isEqualTo("76.50");
        assertThat(textField(root[0], "Order product name").isEnabled()).isTrue();
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).isEqualTo(
                "Reload customers, filtered orders, and inventory choices.");
        verify(orders, times(1)).updateLineReviewed(
                eq(42L), eq(101L), eq(OrderService.reviewSnapshot(original)),
                eq(reviewedLine), any());
        verify(orders, never()).updateLine(eq(42L), eq(101L), any());
    }

    @Test
    @DisplayName("stale product edit keeps the correction and retries against the newer line")
    void crmRebasesStaleProductCorrectionBeforeRetry() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder reviewed = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        CustomerOrder current = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 15, "stems", 3.75);
        CustomerOrder corrected = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 18, "stems", 4.25);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 4.25, "stems", 1, 50, "")));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(reviewed), List.of(current), List.of(corrected));
        OrderService.OrderReviewLine firstReview = new OrderService.OrderReviewLine(
                101L, "Dahlia Mix", 12, "stems", 3.5);
        OrderService.OrderReviewLine currentReview = new OrderService.OrderReviewLine(
                101L, "Dahlia Mix", 15, "stems", 3.75);
        when(orders.updateLineReviewed(
                eq(42L), eq(101L), eq(OrderService.reviewSnapshot(reviewed)),
                eq(firstReview), any()))
                .thenThrow(new OrderService.StaleOrderLineException(
                        42L, "editing", currentReview));
        when(orders.updateLineReviewed(
                eq(42L), eq(101L), eq(OrderService.reviewSnapshot(current)),
                eq(currentReview), any()))
                .thenReturn(corrected);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            button(root[0], "Edit selected product").doClick();
            textField(root[0], "Order quantity").setText("18");
            textField(root[0], "Order unit price").setText("4.25");
            button(root[0], "Save product changes").doClick();
        });
        awaitLabel(root[0], "Saved product changed · refresh and review");
        awaitCrmRefresh(root[0]);

        JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(savedProducts.getSelectedItem().toString())
                .contains("Dahlia Mix", "15 stems", "$3.75");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("18");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("4.25");
        assertThat(button(root[0], "Save product changes").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "saved line is now", "15.0 stems", "$3.75",
                "No order or inventory value was changed",
                "entered product correction was kept");

        SwingUtilities.invokeAndWait(() ->
                button(root[0], "Save product changes").doClick());
        awaitHostStatus(host, "Updated “Dahlia Mix” on order #42. Order totals, Market Day, "
                + "and stock guidance are refreshing; inventory was not changed.");
        awaitCrmRefresh(root[0]);
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);

        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0)).getSelectedItem().toString())
                .contains("Dahlia Mix", "18 stems", "$4.25");
        verify(orders).updateLineReviewed(eq(42L), eq(101L),
                eq(OrderService.reviewSnapshot(reviewed)), eq(firstReview), any());
        verify(orders).updateLineReviewed(eq(42L), eq(101L),
                eq(OrderService.reviewSnapshot(current)), eq(currentReview), any());
        verify(orders, never()).updateLine(eq(42L), eq(101L), any());
    }

    @Test
    @DisplayName("product edit preserves correction and re-reviews changed order context")
    void crmRebasesProductCorrectionAfterOrderContextChanges() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam",
                "sam@example.test", "360-555-0100", "WHOLESALE", "Call on arrival");
        CustomerOrder reviewed = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        reviewed.setHandoffType("PICKUP");
        reviewed.setNotes("Back dock");
        CustomerOrder current = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        current.setHandoffType("DELIVERY");
        current.setNotes("Front counter");
        CustomerOrder corrected = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 18, "stems", 4.25);
        corrected.setHandoffType("DELIVERY");
        corrected.setNotes("Front counter");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(reviewed), List.of(current), List.of(corrected));
        OrderService.OrderReviewSnapshot reviewedSnapshot = OrderService.reviewSnapshot(reviewed);
        OrderService.OrderReviewSnapshot currentSnapshot = OrderService.reviewSnapshot(current);
        OrderService.OrderReviewLine reviewedLine = OrderService.reviewLine(
                reviewed.getLines().get(0));
        when(orders.updateLineReviewed(eq(42L), eq(101L), eq(reviewedSnapshot),
                eq(reviewedLine), any()))
                .thenThrow(new OrderService.StaleOrderReviewException(
                        42L, "product correction",
                        "The product correction was not saved and inventory was unchanged",
                        "review the order and correct the product again"));
        when(orders.updateLineReviewed(eq(42L), eq(101L), eq(currentSnapshot),
                eq(reviewedLine), any())).thenReturn(corrected);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            button(root[0], "Edit selected product").doClick();
            textField(root[0], "Order quantity").setText("18");
            textField(root[0], "Order unit price").setText("4.25");
            button(root[0], "Save product changes").doClick();
        });
        awaitLabel(root[0], "Order #42 changed · product correction kept");
        awaitCrmRefresh(root[0]);

        JLabel result = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("18");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("4.25");
        assertThat(button(root[0], "Save product changes").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "customer, date, handoff, status, instructions, and saved products",
                "correction was not saved", "inventory was unchanged");

        SwingUtilities.invokeAndWait(() ->
                button(root[0], "Save product changes").doClick());
        awaitHostStatus(host, "Updated “Dahlia Mix” on order #42. Order totals, Market Day, "
                + "and stock guidance are refreshed; inventory was not changed.");
        awaitCrmRefresh(root[0]);

        verify(orders).updateLineReviewed(eq(42L), eq(101L), eq(reviewedSnapshot),
                eq(reviewedLine), any());
        verify(orders).updateLineReviewed(eq(42L), eq(101L), eq(currentSnapshot),
                eq(reviewedLine), any());
        verify(orders, never()).updateLine(eq(42L), eq(101L), any());
    }

    @Test
    @DisplayName("failed order product save keeps the selected target and complete line")
    void crmOrderLineFailureKeepsDraftAndTarget() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder existing = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        existing.setId(42L);
        existing.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(existing));
        when(orders.addLineReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class), any()))
                .thenThrow(new IllegalStateException("orders database busy"));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("18.00");
            button(root[0], "Add product to selected order").doClick();
        });
        awaitLabel(root[0], "Product not added · retry available");

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer orders table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.isEnabled()).isTrue();
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Rose Bunch");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("2");
        assertThat(textField(root[0], "Order unit").getText()).isEqualTo("bunches");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("18.00");
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();
        JLabel result = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("orders database busy", "details were kept", "retry");
        assertThat(host.status).contains("Product not added", "kept", "retry");
    }

    @Test
    @DisplayName("selected saved order product is removed in the background without inventory change")
    void crmRemovesSelectedOrderLineWithoutInventoryChange() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        original.setId(42L);
        OrderLine dahlias = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        dahlias.setId(101L);
        OrderLine roses = new OrderLine("Rose Bunch", 2, "bunches", 18);
        roses.setId(102L);
        original.addLine(dahlias);
        original.addLine(roses);
        CustomerOrder updated = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        updated.setId(42L);
        OrderLine remaining = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        remaining.setId(101L);
        updated.addLine(remaining);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 3.5, "stems", 1, 50, ""),
                new Item("Rose Bunch", "Flowers/Plants", 18, "bunches", 1, 10, "")));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(original), List.of(updated));
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        OrderService.OrderReviewLine reviewedLine = new OrderService.OrderReviewLine(
                102L, "Rose Bunch", 2, "bunches", 18);
        when(orders.removeLineReviewed(42L, 102L, OrderService.reviewSnapshot(original),
                reviewedLine)).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            return updated;
        });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderLine").get(0);
            assertThat(savedProducts.getItemCount()).isEqualTo(2);
            savedProducts.setSelectedIndex(1);
            assertThat(savedProducts.getSelectedItem().toString()).contains("Rose Bunch");
            JButton remove = button(root[0], "Remove selected product");
            assertThat(remove.getMnemonic()).isEqualTo(java.awt.event.KeyEvent.VK_O);
            assertThat(remove.getAccessibleContext().getAccessibleDescription()).contains(
                    "exact customer, delivery, and product review", "Alt+O",
                    "Inventory does not change");
            assertThat(find(root[0], JButton.class).stream()
                    .filter(candidate -> candidate.getMnemonic()
                            == java.awt.event.KeyEvent.VK_O))
                    .singleElement().isSameAs(remove);
            remove.doClick();
        });
        awaitLabel(root[0], "Order #42 product removed");
        awaitCrmRefresh(root[0]);

        JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0);
        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer orders table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(calledOnUiThread).isFalse();
        verify(orders, times(1)).removeLineReviewed(
                42L, 102L, OrderService.reviewSnapshot(original), reviewedLine);
        verify(orders, never()).removeLine(42L, 102L);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.getValueAt(0, 0)).isEqualTo(42L);
        assertThat(savedProducts.getItemCount()).isOne();
        assertThat(savedProducts.getItemAt(0).toString()).contains("Dahlia Mix");
        assertThat(host.status).contains("Rose Bunch", "Inventory was not changed");
    }

    @Test
    @DisplayName("removed product locks stale order actions when reload fails")
    void crmRemovedProductReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = orderWithTwoLines(customer, 2, 18);
        CustomerOrder updated = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        updated.setStatus("DRAFT");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(original))
                .thenThrow(new IllegalStateException("order database temporarily unavailable"))
                .thenReturn(List.of(updated));
        OrderService.OrderReviewLine reviewedLine = new OrderService.OrderReviewLine(
                102L, "Rose Bunch", 2, "bunches", 18);
        when(orders.removeLineReviewed(42L, 102L, OrderService.reviewSnapshot(original),
                reviewedLine)).thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderLineRemoval(42L, 102L, "Rose Bunch",
                    "the Draft order for Kitsap Blooms", reviewedLine);
        });
        awaitLabel(root[0], "Product removed once · exact order reload failed");

        JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(savedProducts.getItemCount()).isEqualTo(2);
        assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
        assertThat(button(root[0], "Remove selected product").isEnabled()).isFalse();
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "Rose Bunch", "removed once", "order #42", "could not reload",
                "Retry Refresh CRM only", "do not remove this product again");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "removed once", "only safe retry action", "do not remove");
        verify(orders, times(1)).removeLineReviewed(
                42L, 102L, OrderService.reviewSnapshot(original), reviewedLine);

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #42 product removed");
        assertThat(savedProducts.getItemCount()).isOne();
        assertThat(savedProducts.getItemAt(0).toString()).contains("Dahlia Mix");
        assertThat(textField(root[0], "Order product name").isEnabled()).isTrue();
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).isEqualTo(
                "Reload customers, filtered orders, and inventory choices.");
        verify(orders, times(1)).removeLineReviewed(
                42L, 102L, OrderService.reviewSnapshot(original), reviewedLine);
        verify(orders, never()).removeLine(42L, 102L);
    }

    @Test
    @DisplayName("stale product removal refreshes the changed line and removes nothing")
    void crmRejectsStaleProductRemoval() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder reviewed = orderWithTwoLines(
                customer, 2, 18);
        CustomerOrder current = orderWithTwoLines(
                customer, 3, 20);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(reviewed), List.of(current));
        OrderService.OrderReviewLine firstReview = new OrderService.OrderReviewLine(
                102L, "Rose Bunch", 2, "bunches", 18);
        OrderService.OrderReviewLine currentReview = new OrderService.OrderReviewLine(
                102L, "Rose Bunch", 3, "bunches", 20);
        when(orders.removeLineReviewed(42L, 102L, OrderService.reviewSnapshot(reviewed),
                firstReview))
                .thenThrow(new OrderService.StaleOrderLineException(
                        42L, "removing it", currentReview));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderLine").get(0);
            savedProducts.setSelectedIndex(1);
            button(root[0], "Remove selected product").doClick();
        });
        awaitLabel(root[0], "Order #42 changed · refresh and review");
        awaitComboSelection(root[0], "crm.selectedOrderLine", "Rose Bunch · 3 bunches · $20.00");

        JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(savedProducts.getItemCount()).isEqualTo(2);
        assertThat(savedProducts.getSelectedItem().toString())
                .contains("Rose Bunch", "3 bunches", "$20.00");
        assertThat(button(root[0], "Remove selected product").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "saved line is now", "3.0 bunches", "$20.00",
                "No order or inventory value was changed", "nothing was removed");
        assertThat(host.status).contains(
                "changed before removal", "No order or inventory value changed");
        verify(orders).removeLineReviewed(
                42L, 102L, OrderService.reviewSnapshot(reviewed), firstReview);
        verify(orders, never()).removeLine(42L, 102L);
    }

    @Test
    @DisplayName("product removal rejects changed delivery context and refreshes the exact order")
    void crmRejectsProductRemovalAfterOrderContextChanges() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam",
                "sam@example.test", "360-555-0100", "WHOLESALE", "Call on arrival");
        CustomerOrder reviewed = orderWithTwoLines(customer, 2, 18);
        reviewed.setHandoffType("PICKUP");
        reviewed.setNotes("Back dock");
        CustomerOrder current = orderWithTwoLines(customer, 2, 18);
        current.setHandoffType("DELIVERY");
        current.setNotes("Front counter");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(reviewed), List.of(current));
        OrderService.OrderReviewSnapshot reviewedSnapshot = OrderService.reviewSnapshot(reviewed);
        OrderService.OrderReviewLine reviewedLine = new OrderService.OrderReviewLine(
                102L, "Rose Bunch", 2, "bunches", 18);
        when(orders.removeLineReviewed(42L, 102L, reviewedSnapshot, reviewedLine))
                .thenThrow(new OrderService.StaleOrderReviewException(
                        42L, "product removal", "Nothing was removed and inventory was unchanged",
                        "review the order and remove the product again"));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderLine").get(0);
            savedProducts.setSelectedIndex(1);
            button(root[0], "Remove selected product").doClick();
        });
        awaitLabel(root[0], "Order #42 changed · refresh and review");
        awaitCrmRefresh(root[0]);

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        JComboBox<?> savedProducts = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0);
        assertThat(savedProducts.getItemCount()).isEqualTo(2);
        assertThat(savedProducts.getSelectedItem().toString()).contains("Rose Bunch", "2 bunches");
        assertThat(button(root[0], "Remove selected product").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "current customer, date, handoff, status, instructions, and every saved product",
                "Nothing was removed", "inventory was unchanged");
        assertThat(host.status).contains(
                "changed before removal", "No order or inventory value changed");
        verify(orders).removeLineReviewed(42L, 102L, reviewedSnapshot, reviewedLine);
        verify(orders, never()).removeLine(42L, 102L);
    }

    @Test
    @DisplayName("application exit warning tracks meaningful unfinished order work")
    void crmApplicationExitWarningTracksDraftsAndCorrections() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam", "", "",
                "FLORIST", "West loading dock");
        Customer alternate = new Customer(8L, "Soundview Market", "Lee", "", "",
                "RETAIL", "Saturday booth");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Keep upright", "DELIVERY");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 4, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer, alternate));
        when(customers.getAll()).thenReturn(List.of(customer, alternate));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        assertThat(tab.applicationExitWarning()).isNull();

        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order date").setText(LocalDate.now().plusDays(2).toString());
            @SuppressWarnings("unchecked")
            JComboBox<String> handoff = (JComboBox<String>) findNamed(
                    root[0], "crm.orderHandoffType").get(0);
            handoff.setSelectedItem("Market");
            textField(root[0], "Order notes").setText("Bring tent weights");
            @SuppressWarnings("unchecked")
            JComboBox<CrmPresenter.CustomerChoice> customerChoice =
                    (JComboBox<CrmPresenter.CustomerChoice>) find(root[0], JComboBox.class).stream()
                            .filter(combo -> "New order customer".equals(
                                    combo.getAccessibleContext().getAccessibleName()))
                            .findFirst().orElseThrow();
            customerChoice.setSelectedIndex(1);
        });
        assertThat(tab.applicationExitWarning()).contains(
                "unfinished new-order customer", "unfinished new-order date",
                "unfinished new-order handoff",
                "unfinished new-order instructions");
        JButton resetDetails = (JButton) findNamed(
                root[0], "crm.resetNewOrderDetails").get(0);
        assertThat(resetDetails.isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order product name").setText("Cosmos");
            textField(root[0], "Order unit price").setText("2.50");
            resetDetails.doClick();
        });
        assertThat(resetDetails.getText()).isEqualTo("Discard new-order changes");
        assertThat(textField(root[0], "Order notes").getText())
                .isEqualTo("Bring tent weights");
        assertThat(tab.applicationExitWarning()).contains("unfinished new-order date");

        SwingUtilities.invokeAndWait(resetDetails::doClick);
        assertThat(textField(root[0], "Order date").getText())
                .isEqualTo(LocalDate.now().toString());
        assertThat(textField(root[0], "Order notes").getText()).isEmpty();
        assertThat(((CrmPresenter.CustomerChoice) find(root[0], JComboBox.class).stream()
                .filter(combo -> "New order customer".equals(
                        combo.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().getSelectedItem()).name())
                .isEqualTo("Kitsap Blooms");
        assertThat(((JComboBox<?>) findNamed(root[0], "crm.orderHandoffType").get(0))
                .getSelectedItem()).isEqualTo("Pickup");
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("2.50");
        assertThat(resetDetails.isEnabled()).isFalse();
        assertThat(tab.applicationExitWarning()).contains(
                "1 unfinished product entry", "Closing now", "Keep working")
                .doesNotContain("unfinished new-order customer", "unfinished new-order date",
                        "unfinished new-order handoff",
                        "unfinished new-order instructions");

        SwingUtilities.invokeAndWait(() -> {
            button(root[0], "Clear product entry").doClick();
            button(root[0], "Discard product entry").doClick();
        });
        assertThat(tab.applicationExitWarning()).isNull();

        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            ((JTextField) findNamed(root[0], "crm.selectedOrderNotes").get(0))
                    .setText("Call before unloading");
        });
        assertThat(tab.applicationExitWarning()).contains(
                "unsaved corrections on 1 saved order", "explicitly clear each draft");
    }

    @Test
    @DisplayName("unsaved selected-order work locks confirmation and completion until resolved")
    void crmLifecycleActionsWaitForPendingOrderWork() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        draft.setHandoffType("PICKUP");
        draft.setId(42L);
        draft.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "CONFIRMED", "Loading dock");
        confirmed.setHandoffType("DELIVERY");
        confirmed.setId(43L);
        confirmed.addLine(new OrderLine("Nootka Rose", 2, "stems", 4.0));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(draft, confirmed));
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            int draftRow = -1;
            int confirmedRow = -1;
            for (int row = 0; row < table.getRowCount(); row++) {
                if (Long.valueOf(42L).equals(table.getValueAt(row, 0))) {
                    draftRow = row;
                } else if (Long.valueOf(43L).equals(table.getValueAt(row, 0))) {
                    confirmedRow = row;
                }
            }
            assertThat(draftRow).isNotNegative();
            assertThat(confirmedRow).isNotNegative();

            table.setRowSelectionInterval(draftRow, draftRow);
            JButton confirm = button(root[0], "Confirm order");
            JTextField selectedDate = (JTextField) findNamed(
                    root[0], "crm.selectedOrderDate").get(0);
            JTextField selectedNotes = (JTextField) findNamed(
                    root[0], "crm.selectedOrderNotes").get(0);
            JComboBox<?> selectedHandoff = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderHandoffType").get(0);
            assertThat(confirm.isEnabled()).isTrue();

            selectedDate.setText("2026-09-14");
            assertThat(confirm.isEnabled()).isFalse();
            assertThat(confirm.getToolTipText()).contains("unsaved work", "save or restore the order date");
            selectedDate.setText("2026-09-12");
            assertThat(confirm.isEnabled()).isTrue();

            selectedNotes.setText("Call before arrival");
            assertThat(confirm.isEnabled()).isFalse();
            assertThat(confirm.getToolTipText()).contains("save or restore the order instructions");
            selectedNotes.setText("Keep upright");
            assertThat(confirm.isEnabled()).isTrue();

            assertThat(selectedHandoff.getSelectedItem()).isEqualTo("Pickup");
            selectedHandoff.setSelectedItem("Market");
            assertThat(confirm.isEnabled()).isFalse();
            assertThat(confirm.getToolTipText()).contains("save or restore the handoff type");
            selectedHandoff.setSelectedItem("Pickup");
            assertThat(confirm.isEnabled()).isTrue();

            textField(root[0], "Order product name").setText("Cosmos");
            textField(root[0], "Order unit price").setText("2.50");
            assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();
            assertThat(confirm.isEnabled()).isFalse();
            assertThat(confirm.getToolTipText()).contains("add or clear the product entry");
            JButton clear = button(root[0], "Clear product entry");
            assertThat(clear.isEnabled()).isTrue();
            clear.doClick();
            assertThat(confirm.isEnabled()).isFalse();
            assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
            button(root[0], "Discard product entry").doClick();
            assertThat(confirm.isEnabled()).isTrue();
            assertThat(textField(root[0], "Order product name").getText()).isEmpty();

            table.setRowSelectionInterval(confirmedRow, confirmedRow);
            JButton complete = button(root[0], "Complete order");
            assertThat(complete.isEnabled()).isTrue();
            textField(root[0], "Order product name").setText("Sunflower");
            textField(root[0], "Order unit price").setText("3.00");
            assertThat(complete.isEnabled()).isFalse();
            assertThat(complete.getAccessibleContext().getAccessibleDescription())
                    .contains("unsaved work", "add or clear the product entry");
            button(root[0], "Clear product entry").doClick();
            assertThat(complete.isEnabled()).isFalse();
            assertThat(textField(root[0], "Order product name").getText())
                    .isEqualTo("Sunflower");
            JButton discard = button(root[0], "Discard product entry");
            assertThat(discard.getAccessibleContext().getAccessibleDescription())
                    .contains("still unsaved", "Choose Discard product entry again");
            textField(root[0], "Order unit price").setText("3.25");
            assertThat(button(root[0], "Clear product entry").isEnabled()).isTrue();
            assertThat(textField(root[0], "Order product name").getText())
                    .isEqualTo("Sunflower");
            button(root[0], "Clear product entry").doClick();
            button(root[0], "Discard product entry").doClick();
            assertThat(complete.isEnabled()).isTrue();
        });

        verify(orders, never()).confirm(any());
        verify(orders, never()).fulfill(any());
    }

    @Test
    @DisplayName("destructive order reviews wait until every visible draft is resolved")
    void crmDestructiveActionsWaitForPendingOrderWork() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        draft.setHandoffType("PICKUP");
        draft.setId(42L);
        draft.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(draft));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            JTextField selectedDate = (JTextField) findNamed(
                    root[0], "crm.selectedOrderDate").get(0);
            JTextField selectedNotes = (JTextField) findNamed(
                    root[0], "crm.selectedOrderNotes").get(0);
            JComboBox<?> selectedHandoff = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderHandoffType").get(0);
            JComboBox<?> tools = (JComboBox<?>) findNamed(
                    root[0], "crm.orderTools").get(0);

            selectedDate.setText("2026-09-14");
            selectedNotes.setText("Call before arrival");
            selectedHandoff.setSelectedItem("Delivery");
            textField(root[0], "Order product name").setText("Cosmos");
            textField(root[0], "Order unit price").setText("2.50");
            tools.setSelectedItem("Delete selected order");

            JButton run = button(root[0], "Run action");
            assertThat(run.isEnabled()).isFalse();
            assertThat(run.getToolTipText()).contains(
                    "Order #42 has unsaved", "product entry", "order date",
                    "handoff type", "order instructions", "saved order data only",
                    "no cancellation or deletion has started");
            assertThat(run.getAccessibleContext().getAccessibleDescription())
                    .contains("restore or clear", "Delete selected order");
            run.doClick();

            selectedDate.setText("2026-09-12");
            selectedNotes.setText("Keep upright");
            selectedHandoff.setSelectedItem("Pickup");
            button(root[0], "Clear product entry").doClick();
            button(root[0], "Discard product entry").doClick();
            assertThat(run.isEnabled()).isTrue();

            selectedNotes.setText("Use west gate");
            tools.setSelectedItem("Cancel selected order");
            assertThat(run.isEnabled()).isFalse();
            assertThat(run.getAccessibleContext().getAccessibleDescription()).contains(
                    "unsaved order instructions", "Cancel selected order",
                    "saved order data only");
            assertThat(host.status).doesNotContain("cancelled", "deleted");
        });

        verify(orders, never()).deleteReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).cancelReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class));
    }

    @Test
    @DisplayName("destructive order tools explain lifecycle states that cannot be changed")
    void crmDestructiveActionsRespectCompletedAndCancelledLifecycle() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder completed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 10), "FULFILLED", "Delivered");
        completed.setHandoffType("DELIVERY");
        completed.setId(41L);
        completed.addLine(new OrderLine("Dahlia Mix", 4, "stems", 3.5));
        CustomerOrder cancelled = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 11), "CANCELLED", "Customer cancelled");
        cancelled.setHandoffType("PICKUP");
        cancelled.setId(42L);
        cancelled.addLine(new OrderLine("Cosmos", 2, "bunches", 8.0));
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        draft.setHandoffType("PICKUP");
        draft.setId(43L);
        draft.addLine(new OrderLine("Sunflower", 3, "stems", 4.0));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(completed, cancelled, draft));
        CrmTab tab = new CrmTab(customers, orders, null, new TrackingHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            JComboBox<?> tools = (JComboBox<?>) findNamed(
                    root[0], "crm.orderTools").get(0);
            JButton run = button(root[0], "Run action");
            int completedRow = -1;
            int cancelledRow = -1;
            int draftRow = -1;
            for (int row = 0; row < table.getRowCount(); row++) {
                if (Long.valueOf(41L).equals(table.getValueAt(row, 0))) {
                    completedRow = row;
                } else if (Long.valueOf(42L).equals(table.getValueAt(row, 0))) {
                    cancelledRow = row;
                } else if (Long.valueOf(43L).equals(table.getValueAt(row, 0))) {
                    draftRow = row;
                }
            }

            table.setRowSelectionInterval(completedRow, completedRow);
            tools.setSelectedItem("Cancel selected order");
            assertThat(run.isEnabled()).isFalse();
            assertThat(run.getToolTipText()).contains(
                    "already completed", "Inventory was deducted", "cannot be cancelled",
                    "invoice or activity record");
            assertThat(run.getAccessibleContext().getAccessibleDescription())
                    .contains("Keep the completed order as history");

            tools.setSelectedItem("Delete selected order");
            assertThat(run.isEnabled()).isFalse();
            assertThat(run.getToolTipText()).contains(
                    "already completed", "cannot be deleted", "permanent sales",
                    "inventory history record");

            table.setRowSelectionInterval(cancelledRow, cancelledRow);
            tools.setSelectedItem("Cancel selected order");
            assertThat(run.isEnabled()).isFalse();
            assertThat(run.getToolTipText()).contains(
                    "already cancelled", "no longer in active packing", "No second cancellation");
            tools.setSelectedItem("Delete selected order");
            assertThat(run.isEnabled()).isTrue();

            table.setRowSelectionInterval(draftRow, draftRow);
            tools.setSelectedItem("Cancel selected order");
            assertThat(run.isEnabled()).isTrue();
        });

        verify(orders, never()).deleteReviewed(
                anyLong(), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).cancelReviewed(
                anyLong(), any(OrderService.OrderReviewSnapshot.class));
    }

    @Test
    @DisplayName("immutable orders preserve product work and move it into a new order")
    void crmImmutableOrdersAreReadOnlyWithoutLosingProductDraft() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Loading dock");
        confirmed.setHandoffType("DELIVERY");
        confirmed.setId(41L);
        confirmed.addLine(new OrderLine("Dahlia Mix", 4, "stems", 3.5));
        CustomerOrder completed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "FULFILLED", "Loading dock");
        completed.setHandoffType("DELIVERY");
        completed.setId(41L);
        completed.addLine(new OrderLine("Dahlia Mix", 4, "stems", 3.5));
        CustomerOrder cancelled = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "CANCELLED", "Customer cancelled");
        cancelled.setHandoffType("PICKUP");
        cancelled.setId(42L);
        cancelled.addLine(new OrderLine("Cosmos", 2, "bunches", 8.0));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(confirmed, cancelled), List.of(completed, cancelled));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Sunflower");
            textField(root[0], "Order quantity").setText("6");
            textField(root[0], "Order unit").setText("stems");
            textField(root[0], "Order unit price").setText("4.25");
            button(root[0], "Refresh CRM").doClick();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            assertThat(table.getValueAt(table.getSelectedRow(), 3)).isEqualTo("Completed");
            JTextField product = textField(root[0], "Order product name");
            JTextField quantity = textField(root[0], "Order quantity");
            JTextField unit = textField(root[0], "Order unit");
            JTextField price = textField(root[0], "Order unit price");
            JComboBox<?> inventoryChoice = (JComboBox<?>) findNamed(
                    root[0], "crm.inventoryProductChoice").get(0);
            assertThat(List.of(product, quantity, unit, price, inventoryChoice))
                    .allSatisfy(component -> assertThat(component.isEnabled()).isFalse());
            assertThat(product.getText()).isEqualTo("Sunflower");
            assertThat(quantity.getText()).isEqualTo("6");
            assertThat(price.getText()).isEqualTo("4.25");
            assertThat(product.getAccessibleContext().getAccessibleDescription()).contains(
                    "Order #41", "Completed", "read-only", "move this preserved product");
            assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
            assertThat(button(root[0], "Clear product entry").isEnabled()).isTrue();
            assertThat(button(root[0], "Edit selected product").isEnabled()).isFalse();
            assertThat(button(root[0], "Remove selected product").isEnabled()).isFalse();
            JButton move = button(root[0], "Move product to new order");
            assertThat(move.isEnabled()).isTrue();
            assertThat(move.getAccessibleContext().getAccessibleDescription()).contains(
                    "preserved product", "read-only Completed order", "new-order draft");
            move.doClick();

            assertThat(table.getSelectedRow()).isEqualTo(-1);
            assertThat(product.isEnabled()).isTrue();
            assertThat(product.getText()).isEqualTo("Sunflower");
            assertThat(quantity.getText()).isEqualTo("6");
            assertThat(price.getText()).isEqualTo("4.25");
            assertThat(button(root[0], "Create & select order").isEnabled()).isTrue();
            assertThat(host.status).contains(
                    "Moved the preserved product", "read-only Completed order #41",
                    "Review customer and delivery details");

            int cancelledRow = -1;
            for (int row = 0; row < table.getRowCount(); row++) {
                if (Long.valueOf(42L).equals(table.getValueAt(row, 0))) {
                    cancelledRow = row;
                }
            }
            table.setRowSelectionInterval(cancelledRow, cancelledRow);
            assertThat(product.isEnabled()).isFalse();
            assertThat(product.getText()).isEmpty();
            assertThat(product.getAccessibleContext().getAccessibleDescription()).contains(
                    "Order #42", "Cancelled", "read-only", "Start another order");
            assertThat(button(root[0], "Start another order").isEnabled()).isTrue();
        });

        verify(orders, never()).addLine(anyLong(), any());
        verify(orders, never()).addLineReviewed(
                anyLong(), any(OrderService.OrderReviewSnapshot.class), any());
    }

    @Test
    @DisplayName("CRM exposes unspecified handoffs and blocks both lifecycle actions")
    void crmLifecycleRequiresSpecifiedHandoff() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        draft.setId(44L);
        draft.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "CONFIRMED", "Loading dock");
        confirmed.setId(45L);
        confirmed.addLine(new OrderLine("Nootka Rose", 2, "stems", 4));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(draft, confirmed));
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertThat(table.getColumnName(4)).isEqualTo("Handoff");
            int draftRow = -1;
            int confirmedRow = -1;
            for (int row = 0; row < table.getRowCount(); row++) {
                assertThat(table.getValueAt(row, 4)).isEqualTo("Needs review");
                if (Long.valueOf(44L).equals(table.getValueAt(row, 0))) {
                    draftRow = row;
                } else if (Long.valueOf(45L).equals(table.getValueAt(row, 0))) {
                    confirmedRow = row;
                }
            }

            table.setRowSelectionInterval(draftRow, draftRow);
            JButton confirm = button(root[0], "Confirm order");
            JButton saveHandoff = button(root[0], "Save handoff type");
            JComboBox<?> handoff = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderHandoffType").get(0);
            assertThat(handoff.getSelectedItem()).isEqualTo("Unspecified");
            assertThat(confirm.isEnabled()).isFalse();
            assertThat(saveHandoff.isEnabled()).isFalse();
            assertThat(confirm.getToolTipText()).contains(
                    "choose Pickup, Delivery, or Market", "save the handoff type");

            handoff.setSelectedItem("Delivery");
            assertThat(saveHandoff.isEnabled()).isTrue();
            assertThat(confirm.isEnabled()).isFalse();
            assertThat(confirm.getToolTipText()).contains("save or restore the handoff type");

            table.setRowSelectionInterval(confirmedRow, confirmedRow);
            JButton complete = button(root[0], "Complete order");
            assertThat(complete.isEnabled()).isFalse();
            assertThat(complete.getAccessibleContext().getAccessibleDescription()).contains(
                    "choose Pickup, Delivery, or Market", "save the handoff type");
        });

        verify(orders, never()).confirm(any());
        verify(orders, never()).fulfill(any());
    }

    @Test
    @DisplayName("empty saved orders stay drafts and direct workers to add a product")
    void crmEmptyOrderBlocksLifecycleUntilProductIsSaved() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder emptyDraft = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 14), "DRAFT", "Delivery at noon");
        emptyDraft.setId(44L);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(emptyDraft));
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            JButton confirm = button(root[0], "Confirm order");

            assertThat(confirm.isEnabled()).isFalse();
            assertThat(confirm.getToolTipText())
                    .contains("Order #44", "add at least one saved product");
            assertThat(confirm.getAccessibleContext().getAccessibleDescription())
                    .contains("add at least one saved product");
            assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                    .contains("Selected #44", "0 products");

            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order unit price").setText("3.50");
            assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();
            assertThat(confirm.isEnabled()).isFalse();
            confirm.doClick();
        });

        verify(orders, never()).confirm(any());
        verify(orders, never()).fulfill(any());
    }

    @Test
    @DisplayName("confirmation review lists every saved product in a compact scrollable panel")
    void crmConfirmationReviewShowsExactMultiProductLoad() {
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST", "Use side gate");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        order.setId(52L);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        order.addLine(new OrderLine("Rose Bunch", 2, "bunches", 18));
        order.addLine(new OrderLine("Complimentary Cosmos", 1, "stem", 0));

        CrmTab.OrderConfirmationReview review = CrmTab.orderConfirmationReview(
                order, new CrmTab.OrderStockAssessment(true, 3, List.of()));
        assertThat(review.heading()).contains(
                "Confirm order #52", "Kitsap Blooms", "2026-09-12");
        assertThat(review.details()).contains(
                "Customer contact: Sam Rivera · 360-555-0142 · sam@kitsap.test",
                "Account delivery note: Use side gate",
                "Products: 3",
                "1. 12 stems Dahlia Mix · $3.50 each · $42.00",
                "2. 2 bunches Rose Bunch · $18.00 each · $36.00",
                "3. 1 stem Complimentary Cosmos · $0.00 each · $0.00",
                "Order total: $78.00", "Keep upright", "all 3 products currently covered",
                "eligible for packing and Market Day", "Inventory is not deducted");

        JComponent panel = CrmTab.orderConfirmationPanel(review);
        assertThat(findNamed(panel, "crm.orderConfirmationReview")).hasSize(1);
        assertThat(findNamed(panel, "crm.orderConfirmationScroll"))
                .singleElement().isInstanceOf(javax.swing.JScrollPane.class)
                .satisfies(component -> {
                    javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) component;
                    assertThat(scroll.getPreferredSize().height).isEqualTo(250);
                    assertThat(scroll.getMinimumSize().width).isEqualTo(340);
                });
        assertThat(findNamed(panel, "crm.orderConfirmationDetails"))
                .singleElement().isInstanceOf(JTextArea.class)
                .satisfies(component -> {
                    JTextArea details = (JTextArea) component;
                    assertThat(details.isEditable()).isFalse();
                    assertThat(details.getLineWrap()).isTrue();
                    assertThat(details.getText()).isEqualTo(review.details());
                    assertThat(details.getAccessibleContext().getAccessibleName())
                            .contains("Saved order products", "confirmation consequences");
                });
    }

    @Test
    @DisplayName("confirmation and completion show note-only delivery context")
    void crmConsequenceReviewsShowAccountDeliveryNoteWithoutContact() {
        Customer customer = new Customer(8L, "Harbor Florist", "", "", "",
                "FLORIST", "Deliver to the west loading dock");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "", "DELIVERY");
        order.setId(54L);
        order.addLine(new OrderLine("Dahlia Mix", 6, "stems", 3.5));
        CrmTab.OrderStockAssessment stock =
                new CrmTab.OrderStockAssessment(true, 1, List.of());

        CrmTab.OrderConfirmationReview confirmation =
                CrmTab.orderConfirmationReview(order, stock);
        CrmTab.OrderCompletionReview completion =
                CrmTab.orderCompletionReview(order, stock);

        assertThat(confirmation.details()).contains(
                "Customer contact: none saved",
                "Account delivery note: Deliver to the west loading dock",
                "Handoff type: Delivery");
        assertThat(completion.details()).contains(
                "Customer contact: none saved",
                "Account delivery note: Deliver to the west loading dock",
                "Handoff type: Delivery");
    }

    @Test
    @DisplayName("confirmation review calls out a past handoff date")
    void crmConfirmationReviewWarnsAboutPastDate() {
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST", "Use side gate");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now().minusDays(1), "DRAFT", "Keep upright");
        order.setId(53L);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));

        CrmTab.OrderConfirmationReview review = CrmTab.orderConfirmationReview(
                order, new CrmTab.OrderStockAssessment(true, 1, List.of()));

        assertThat(review.details()).contains(
                "Date warning", "handoff date is in the past",
                "Verify it before confirmation");
    }

    @Test
    @DisplayName("cancellation and deletion review every exact order consequence in a scroll")
    void crmDestructiveOrderReviewIsExactAndCompact() {
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST", "Use side gate");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Keep upright");
        order.setId(52L);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        order.addLine(new OrderLine("Rose Bunch", 2, "bunches", 18));

        CrmTab.OrderDestructiveReview cancellation =
                CrmTab.orderDestructiveReview(order, false);
        CrmTab.OrderDestructiveReview deletion =
                CrmTab.orderDestructiveReview(order, true);
        assertThat(cancellation.heading()).contains(
                "Cancel order #52", "Kitsap Blooms", "$78.00");
        assertThat(cancellation.details()).contains(
                "Current status: CONFIRMED", "Pickup / delivery date: 2026-09-12",
                "Customer contact: Sam Rivera · 360-555-0142 · sam@kitsap.test",
                "Account delivery note: Use side gate",
                "Instructions: Keep upright", "Products: 2",
                "1. 12 stems Dahlia Mix · $3.50 each · $42.00",
                "2. 2 bunches Rose Bunch · $18.00 each · $36.00",
                "Order total: $78.00", "changes this order to Cancelled",
                "removes it from active packing and Market Day work",
                "does not deduct or restore inventory");
        assertThat(deletion.heading()).contains(
                "Permanently delete order #52", "Kitsap Blooms", "$78.00");
        assertThat(deletion.details()).contains(
                "permanently removes this order and every saved product line",
                "cannot be undone", "Inventory is unchanged");

        JComponent panel = CrmTab.orderDestructivePanel(cancellation);
        assertThat(findNamed(panel, "crm.orderDestructiveReview")).hasSize(1);
        assertThat(findNamed(panel, "crm.orderDestructiveScroll"))
                .singleElement().isInstanceOf(javax.swing.JScrollPane.class)
                .satisfies(component -> {
                    javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) component;
                    assertThat(scroll.getPreferredSize().height).isEqualTo(280);
                    assertThat(scroll.getMinimumSize().width).isEqualTo(340);
                });
        assertThat(findNamed(panel, "crm.orderDestructiveDetails"))
                .singleElement().isInstanceOf(JTextArea.class)
                .satisfies(component -> {
                    JTextArea details = (JTextArea) component;
                    assertThat(details.isEditable()).isFalse();
                    assertThat(details.getLineWrap()).isTrue();
                    assertThat(details.getText()).isEqualTo(cancellation.details());
                    assertThat(details.getAccessibleContext().getAccessibleName())
                            .contains("Exact order", "consequences");
                });
    }

    @Test
    @DisplayName("direct completion reviews exact customer load and rounded inventory deduction")
    void crmCompletionReviewShowsExactMultiProductInventoryImpact() {
        LocalDate scheduledDate = LocalDate.now().plusDays(7);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.test", "360-555-0142", "FLORIST", "Use side gate");
        CustomerOrder order = new CustomerOrder(
                customer, scheduledDate, "CONFIRMED", "Keep upright");
        order.setId(52L);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        order.addLine(new OrderLine("Rose Bunch", 1.6, "bunches", 18));
        order.addLine(new OrderLine("Complimentary Cosmos", 1, "stem", 0));

        CrmTab.OrderCompletionReview review = CrmTab.orderCompletionReview(
                order, new CrmTab.OrderStockAssessment(true, 3, List.of()));
        assertThat(review.heading()).contains(
                "Complete order #52", "Kitsap Blooms", "$70.80");
        assertThat(review.details()).contains(
                "Pickup / delivery date: " + scheduledDate,
                "EARLY COMPLETION WARNING", "scheduled for " + scheduledDate,
                "physical handoff already happened",
                "Customer contact: Sam Rivera · 360-555-0142 · sam@kitsap.test",
                "Account delivery note: Use side gate",
                "Instructions: Keep upright", "Products: 3",
                "1. 12 stems Dahlia Mix · $3.50 each · $42.00 · deduct 12 inventory units",
                "2. 1.6 bunches Rose Bunch · $18.00 each · $28.80 · deduct 2 inventory units",
                "3. 1 stem Complimentary Cosmos · $0.00 each · $0.00 · deduct 1 inventory unit",
                "Order total: $70.80", "all 3 products covered",
                "Live stock is checked again", "marks this order completed",
                "physical pickup, delivery, or load-out is complete",
                "Cancel if any customer, date, product, quantity, unit, price, or instruction is not exact");

        JComponent panel = CrmTab.orderCompletionPanel(review);
        assertThat(findNamed(panel, "crm.orderCompletionReview")).hasSize(1);
        assertThat(findNamed(panel, "crm.orderCompletionScroll"))
                .singleElement().isInstanceOf(javax.swing.JScrollPane.class)
                .satisfies(component -> {
                    javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) component;
                    assertThat(scroll.getPreferredSize().height).isEqualTo(300);
                    assertThat(scroll.getMinimumSize().width).isEqualTo(340);
                });
        assertThat(findNamed(panel, "crm.orderCompletionDetails"))
                .singleElement().isInstanceOf(JTextArea.class)
                .satisfies(component -> {
                    JTextArea details = (JTextArea) component;
                    assertThat(details.isEditable()).isFalse();
                    assertThat(details.getLineWrap()).isTrue();
                    assertThat(details.getText()).isEqualTo(review.details());
                    assertThat(details.getAccessibleContext().getAccessibleName())
                            .contains("Exact customer load", "inventory deduction");
                });
    }

    @Test
    @DisplayName("order confirmation runs off the UI thread once with a captured target")
    void crmConfirmsOrderInBackgroundOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Call before delivery");
        draft.setId(42L);
        draft.setHandoffType("DELIVERY");
        draft.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        CustomerOrder confirmed = new CustomerOrder(customer, LocalDate.now(), "CONFIRMED", "");
        confirmed.setId(42L);
        confirmed.setHandoffType("DELIVERY");
        confirmed.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(draft), List.of(confirmed));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        AtomicReference<OrderService.OrderReviewSnapshot> capturedReview =
                new AtomicReference<>();
        doAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            capturedReview.set(invocation.getArgument(1));
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(orders).confirmReviewed(eq(42L), any(OrderService.OrderReviewSnapshot.class));
        CrmTab.OrderConfirmationReview review = CrmTab.orderConfirmationReview(
                draft, new CrmTab.OrderStockAssessment(true, 1, List.of(
                        new CrmTab.OrderStockGap(
                                "Dahlia Mix", 1, 0, 1, false, false,
                                "stems", "stems"))));
        assertThat(review.heading()).contains(
                "Confirm order #42", "Kitsap Blooms", LocalDate.now().toString());
        assertThat(review.details()).contains(
                "Products: 1", "Order total: $3.50", "Call before delivery",
                "1. 1 stems Dahlia Mix · $3.50 each · $3.50",
                "1 packing issue", "completion stays locked",
                "eligible for packing and Market Day", "Inventory is not deducted");
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            button(root[0], "Confirm order").doClick();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Confirming…").isEnabled()).isFalse();
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            assertThat(table.isEnabled()).isFalse();
            tab.startOrderConfirmation(42L, "the Kitsap Blooms order");
        });
        release.countDown();
        awaitLabel(root[0], "Order #42 confirmed · ready to pack");
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #42 confirmed once · ready to pack");

        assertThat(calledOnUiThread).isFalse();
        assertThat(capturedReview.get()).satisfies(snapshot -> {
            assertThat(snapshot.orderId()).isEqualTo(42L);
            assertThat(snapshot.customerName()).isEqualTo("Kitsap Blooms");
            assertThat(snapshot.status()).isEqualTo("DRAFT");
            assertThat(snapshot.notes()).isEqualTo("Call before delivery");
            assertThat(snapshot.lines()).singleElement().satisfies(line -> {
                assertThat(line.productName()).isEqualTo("Dahlia Mix");
                assertThat(line.quantity()).isEqualTo(1);
                assertThat(line.unitPrice()).isEqualTo(3.5);
            });
        });
        verify(orders, times(1)).confirmReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).confirm(42L);
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(((JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0))
                .getAccessibleContext().getAccessibleDescription()).contains(
                "confirmed once", "Inventory was not changed",
                "refreshed Confirmed order remains selected", "Do not confirm it again");
    }

    @Test
    @DisplayName("confirmed order outside the refreshed draft queue retains a status receipt")
    void crmConfirmationOutsideCurrentViewCannotLookRetryable() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Saturday delivery");
        draft.setId(42L);
        draft.setHandoffType("DELIVERY");
        draft.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(draft), List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderConfirmation(42L, "the Kitsap Blooms Saturday delivery");
        });
        awaitLabel(root[0], "Order #42 confirmed once · outside current view");
        awaitCrmRefresh(root[0]);

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(table.getRowCount()).isZero();
        assertThat(table.getSelectedRow()).isEqualTo(-1);
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "confirmed once", "Inventory was not changed",
                "outside the current filtered view", "Draft-only", "Show all",
                "Confirmed filter", "Do not confirm this order again");
        assertThat(button(root[0], "Confirm order").getToolTipText()).contains(
                "Inventory was not changed", "Do not confirm");
        assertThat(host.status).contains(
                "confirmed once", "inventory was not changed", "outside the current view",
                "do not confirm");
        verify(orders, times(1)).confirmReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).confirm(42L);
    }

    @Test
    @DisplayName("successful confirmation stays non-repeatable when its automatic reload fails")
    void crmConfirmationReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Saturday delivery");
        draft.setId(42L);
        draft.setHandoffType("DELIVERY");
        draft.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Saturday delivery");
        confirmed.setId(42L);
        confirmed.setHandoffType("DELIVERY");
        confirmed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(draft))
                .thenThrow(new IllegalStateException("order database temporarily unavailable"))
                .thenReturn(List.of(confirmed));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderConfirmation(42L, "the Kitsap Blooms Saturday delivery");
        });
        awaitLabel(root[0], "Order #42 confirmed once · exact-order reload failed");

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "confirmed once", "inventory was not changed", "could not reload",
                "Retry Refresh CRM only", "do not run confirmation again");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "confirmed once", "only safe retry action", "do not run confirmation again");
        assertThat(host.status).contains(
                "confirmed once", "inventory was not changed", "CRM reload failed",
                "Retry Refresh CRM", "do not confirm");
        verify(orders, times(1)).confirmReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class));

        SwingUtilities.invokeAndWait(() -> submitOrderProductShortcut(root[0]));
        assertThat(host.status).contains(
                "confirmed once", "Inventory was not changed", "Refresh CRM only",
                "do not add or re-enter a product");
        verify(orders, times(1)).confirmReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class));

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #42 confirmed once · ready to pack");
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        verify(orders, times(1)).confirmReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).confirm(42L);
    }

    @Test
    @DisplayName("stale confirmation keeps the draft selected and requires a fresh review")
    void crmRejectsConfirmationWhenReviewedOrderChanged() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Call before delivery");
        draft.setId(42L);
        draft.setHandoffType("DELIVERY");
        OrderLine line = new OrderLine("Dahlia Mix", 1, "stems", 3.5);
        line.setId(101L);
        draft.addLine(line);
        CustomerOrder current = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Use loading dock");
        current.setId(42L);
        current.setHandoffType("DELIVERY");
        OrderLine currentLine = new OrderLine("Dahlia Mix", 2, "stems", 3.5);
        currentLine.setId(101L);
        current.addLine(currentLine);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(draft), List.of(current));
        doThrow(new IllegalArgumentException(
                "Order #42 changed after confirmation review. No status was changed. Refresh "
                        + "Customers and Orders, review the current customer load, then confirm it again."))
                .when(orders).confirmReviewed(
                        eq(42L), any(OrderService.OrderReviewSnapshot.class));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderConfirmation(42L, "the Draft order for Kitsap Blooms");
        });
        awaitLabel(root[0], "Order #42 changed · review refreshed draft");

        JLabel completion = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(completion.getAccessibleContext().getAccessibleDescription()).contains(
                "changed after confirmation review", "No status was changed",
                "Refresh Customers and Orders", "exact order remains selected",
                "now refreshed", "Open a new confirmation review");
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                .contains("Selected #42", "Kitsap Blooms", "Draft");
        assertThat(((JTextField) findNamed(root[0], "crm.selectedOrderNotes").get(0)).getText())
                .isEqualTo("Use loading dock");
        assertThat(String.valueOf(((JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderLine").get(0)).getSelectedItem()))
                .contains("Dahlia Mix", "2 stems");
        assertThat(button(root[0], "Confirm order").isEnabled()).isTrue();
        assertThat(host.status).contains(
                "was not confirmed", "current draft", "stock guidance", "refreshed");
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);
        verify(orders).confirmReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).confirm(42L);
    }

    @Test
    @DisplayName("stale confirmation stays locked when its automatic refresh fails")
    void crmConfirmationRefreshFailureKeepsStaleReviewLocked() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Call before delivery");
        draft.setId(42L);
        draft.setHandoffType("DELIVERY");
        draft.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(draft))
                .thenThrow(new IllegalStateException("draft reload offline"));
        doThrow(new IllegalArgumentException(
                "Order #42 changed after confirmation review. No status was changed."))
                .when(orders).confirmReviewed(
                        eq(42L), any(OrderService.OrderReviewSnapshot.class));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderConfirmation(42L, "the Draft order for Kitsap Blooms");
        });
        awaitLabel(root[0], "Order #42 not confirmed · automatic refresh failed");

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(button(root[0], "Confirm order").getToolTipText()).contains(
                "No status was changed", "could not reload", "Confirmation stays locked",
                "Refresh CRM");
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "exact draft remains selected", "could not reload", "Refresh CRM");
        assertThat(((JLabel) findNamed(root[0], "crm.refreshStatus").get(0)).getText())
                .contains("CRM refresh failed", "retry");
        assertThat(host.status).contains(
                "not confirmed", "automatic CRM refresh failed", "Existing work was kept");
        verify(orders).confirmReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class));
    }

    @Test
    @DisplayName("reviewed cancellation runs once in the background without inventory")
    void crmCancelsOnlyTheReviewedOrderSnapshot() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "WHOLESALE", "");
        CustomerOrder confirmed = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        confirmed.setNotes("Side gate");
        CustomerOrder cancelled = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        cancelled.setNotes("Side gate");
        cancelled.setStatus("CANCELLED");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed), List.of(cancelled));
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        AtomicReference<OrderService.OrderReviewSnapshot> captured = new AtomicReference<>();
        when(orders.cancelReviewed(
                eq(42L), any(OrderService.OrderReviewSnapshot.class))).thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            captured.set(invocation.getArgument(1));
            return cancelled;
        });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(confirmed);
        SwingUtilities.invokeAndWait(() -> tab.startOrderCancellation(
                42L, "the Confirmed order for Kitsap Blooms", reviewed));
        awaitLabel(root[0], "Order #42 cancelled");

        assertThat(calledOnUiThread).isFalse();
        assertThat(captured.get()).isEqualTo(reviewed);
        verify(orders).cancelReviewed(42L, reviewed);
        verify(orders, never()).cancel(42L);
        assertThat(host.status).contains("was cancelled");
    }

    @Test
    @DisplayName("committed cancellation locks stale order actions until exact reload succeeds")
    void crmCancelledOrderReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "WHOLESALE", "");
        CustomerOrder confirmed = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        CustomerOrder cancelled = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        cancelled.setStatus("CANCELLED");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(confirmed))
                .thenThrow(new IllegalStateException("order reload offline"))
                .thenReturn(List.of(cancelled));
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(confirmed);
        when(orders.cancelReviewed(42L, reviewed)).thenReturn(cancelled);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderCancellation(
                    42L, "the Confirmed order for Kitsap Blooms", reviewed);
        });
        awaitLabel(root[0], "Order #42 cancelled once · CRM reload failed");

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(table.getValueAt(0, 3)).isEqualTo("Confirmed");
        SwingUtilities.invokeAndWait(() -> ((JComboBox<?>) findNamed(
                root[0], "crm.orderTools").get(0)).setSelectedItem("Cancel selected order"));
        assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
        assertThat(button(root[0], "Run action").isEnabled()).isFalse();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "cancelled once", "Inventory was not changed", "could not reload",
                "Retry Refresh CRM only", "retained pre-cancellation data");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "cancelled once", "only safe retry action", "Do not cancel");
        verify(orders, times(1)).cancelReviewed(42L, reviewed);

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #42 cancelled");
        assertThat(table.getValueAt(0, 3)).isEqualTo("Cancelled");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).isEqualTo(
                "Reload customers, filtered orders, and inventory choices.");
        verify(orders, times(1)).cancelReviewed(42L, reviewed);
        verify(orders, never()).cancel(42L);
    }

    @Test
    @DisplayName("committed cancellation remains explicit when current filters hide it")
    void crmCancelledOrderOutsideCurrentViewReportsOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder confirmed = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        CustomerOrder cancelled = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        cancelled.setStatus("CANCELLED");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed), List.of());
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(confirmed);
        when(orders.cancelReviewed(42L, reviewed)).thenReturn(cancelled);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderCancellation(
                    42L, "the Confirmed order for Kitsap Blooms", reviewed);
        });
        awaitLabel(root[0], "Order #42 cancelled once · outside current view");
        awaitCrmRefresh(root[0]);

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(table.getRowCount()).isZero();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "cancelled once", "outside the current filtered view", "Show all",
                "Cancelled", "retained pre-cancellation data");
        assertThat(host.status).contains("cancelled once", "outside", "Show all");
        verify(orders, times(1)).cancelReviewed(42L, reviewed);
        verify(orders, never()).cancel(42L);
    }

    @Test
    @DisplayName("committed deletion locks a retained stale row until CRM confirms absence")
    void crmDeletedOrderReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Side gate");
        draft.setId(42L);
        OrderLine line = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        line.setId(101L);
        draft.addLine(line);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(draft))
                .thenThrow(new IllegalStateException("order reload offline"))
                .thenReturn(List.of());
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(draft);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderDeletion(42L, "the Draft order for Kitsap Blooms", reviewed);
        });
        awaitLabel(root[0], "Order #42 deleted once · CRM reload failed");

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getSelectedRow()).isEqualTo(-1);
        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(0, 0);
            ((JComboBox<?>) findNamed(root[0], "crm.orderTools").get(0))
                    .setSelectedItem("Delete selected order");
        });
        assertThat(button(root[0], "Run action").isEnabled()).isFalse();
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "permanently deleted once", "Inventory was not changed", "could not reload",
                "retained row is stale", "Do not select it or delete it again");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "permanently deleted once", "only safe retry action", "Do not select");
        verify(orders, times(1)).deleteReviewed(42L, reviewed);

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #42 deleted");
        assertThat(table.getRowCount()).isZero();
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).isEqualTo(
                "Reload customers, filtered orders, and inventory choices.");
        verify(orders, times(1)).deleteReviewed(42L, reviewed);
        verify(orders, never()).delete(42L);
    }

    @Test
    @DisplayName("stale deletion refreshes the changed order and deletes nothing")
    void crmRejectsDeletionWhenReviewedOrderChanged() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "WHOLESALE", "");
        CustomerOrder reviewedOrder = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Side gate");
        reviewedOrder.setId(42L);
        OrderLine reviewedLine = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        reviewedLine.setId(101L);
        reviewedOrder.addLine(reviewedLine);
        CustomerOrder current = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 14), "DRAFT", "Front desk");
        current.setId(42L);
        OrderLine currentLine = new OrderLine("Dahlia Mix", 15, "stems", 3.75);
        currentLine.setId(101L);
        current.addLine(currentLine);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(reviewedOrder), List.of(current));
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(reviewedOrder);
        doThrow(new IllegalArgumentException(
                "Order #42 changed after deletion review. The order was not deleted and "
                        + "inventory was unchanged. Refresh Customers and Orders, review the "
                        + "current customer load, then review the deletion again."))
                .when(orders).deleteReviewed(42L, reviewed);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> tab.startOrderDeletion(
                42L, "the Draft order for Kitsap Blooms", reviewed));
        awaitLabel(root[0], "Order #42 changed · review refreshed order");
        awaitOrderTableValue(root[0], 1, "2026-09-14");

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "changed after deletion review", "not deleted", "inventory was unchanged",
                "exact order remains selected", "now refreshed", "new consequence review",
                "Delete selected order");
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                .contains("Selected #42", "Kitsap Blooms", "Draft");
        SwingUtilities.invokeAndWait(() -> ((JComboBox<?>) findNamed(
                root[0], "crm.orderTools").get(0)).setSelectedItem("Delete selected order"));
        assertThat(button(root[0], "Run action").isEnabled()).isTrue();
        assertThat(host.status).contains(
                "was not changed", "current saved order", "refreshed",
                "deletion again");
        verify(orders).deleteReviewed(42L, reviewed);
        verify(orders, never()).delete(42L);
    }

    @Test
    @DisplayName("stale cancellation stays locked when its automatic refresh fails")
    void crmCancellationRefreshFailureKeepsDestructiveReviewLocked() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder confirmed = orderWithLine(
                customer, 42L, 101L, "Dahlia Mix", 12, "stems", 3.5);
        confirmed.setNotes("Side gate");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(confirmed))
                .thenThrow(new IllegalStateException("order reload offline"));
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(confirmed);
        doThrow(new IllegalArgumentException(
                "Order #42 changed after cancellation review. It was not cancelled and inventory "
                        + "was unchanged."))
                .when(orders).cancelReviewed(42L, reviewed);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> tab.startOrderCancellation(
                42L, "the Confirmed order for Kitsap Blooms", reviewed));
        awaitLabel(root[0],
                "Order #42 cancellation stopped · automatic refresh failed");

        SwingUtilities.invokeAndWait(() -> ((JComboBox<?>) findNamed(
                root[0], "crm.orderTools").get(0)).setSelectedItem("Cancel selected order"));
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(button(root[0], "Run action").isEnabled()).isFalse();
        assertThat(button(root[0], "Run action").getToolTipText()).contains(
                "not cancelled", "could not reload", "Cancel selected order stays locked",
                "Refresh CRM");
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "exact order remains selected", "could not reload", "Refresh CRM");
        assertThat(((JLabel) findNamed(root[0], "crm.refreshStatus").get(0)).getText())
                .contains("CRM refresh failed", "retry");
        assertThat(host.status).contains(
                "was not changed", "automatic CRM refresh failed", "Existing work was kept");
        verify(orders).cancelReviewed(42L, reviewed);
        verify(orders, never()).cancel(42L);
    }

    @Test
    @DisplayName("saved handoff correction refreshes packing without changing inventory")
    void crmUpdatesSelectedOrderHandoffInBackground() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "loading dock");
        original.setHandoffType("PICKUP");
        original.setId(42L);
        original.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "loading dock");
        updated.setHandoffType("DELIVERY");
        updated.setId(42L);
        updated.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(original), List.of(updated));
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(orders.updateHandoffTypeReviewed(42L, "PICKUP", "DELIVERY"))
                .thenAnswer(invocation -> {
                    calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
                    return updated;
                });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            JComboBox<?> handoff = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderHandoffType").get(0);
            assertThat(handoff.getSelectedItem()).isEqualTo("Pickup");
            assertThat(handoff.getAccessibleContext().getAccessibleName())
                    .isEqualTo("Selected order handoff type");
            handoff.setSelectedItem("Delivery");
            assertThat(button(root[0], "Save handoff type").isEnabled()).isTrue();
            assertThat(button(root[0], "Save handoff type").getMnemonic())
                    .isEqualTo(java.awt.event.KeyEvent.VK_F);
            button(root[0], "Save handoff type").doClick();
        });
        awaitLabel(root[0], "Order #42 handoff saved · packing refreshed");
        awaitCrmRefresh(root[0]);

        JComboBox<?> handoff = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderHandoffType").get(0);
        assertThat(calledOnUiThread).isFalse();
        verify(orders, times(1)).updateHandoffTypeReviewed(42L, "PICKUP", "DELIVERY");
        verify(orders, never()).updateHandoffType(any(), anyString());
        assertThat(handoff.getSelectedItem()).isEqualTo("Delivery");
        assertThat(button(root[0], "Save handoff type").isEnabled()).isFalse();
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                .contains("Delivery");
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);
        assertThat(host.status).contains("handoff type", "inventory was not changed");
    }

    @Test
    @DisplayName("saved handoff locks stale order actions when CRM reload fails")
    void crmSavedHandoffReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = orderWithHandoff(customer, 42L, "PICKUP");
        CustomerOrder updated = orderWithHandoff(customer, 42L, "DELIVERY");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(original))
                .thenThrow(new IllegalStateException("order reload offline"))
                .thenReturn(List.of(updated));
        when(orders.updateHandoffTypeReviewed(42L, "PICKUP", "DELIVERY"))
                .thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            JComboBox<?> handoff = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderHandoffType").get(0);
            handoff.setSelectedItem("Delivery");
            button(root[0], "Save handoff type").doClick();
        });
        awaitLabel(root[0], "Order #42 handoff saved once · CRM reload failed");

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderHandoffType").get(0)).getSelectedItem())
                .isEqualTo("Delivery");
        assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
        assertThat(button(root[0], "Save handoff type").isEnabled()).isFalse();
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "handoff", "Delivery", "saved once", "could not reload",
                "Retry Refresh CRM only", "retained pre-save order data");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "handoff", "saved once", "only safe retry action", "do not save");
        verify(orders, times(1)).updateHandoffTypeReviewed(42L, "PICKUP", "DELIVERY");

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #42 handoff saved · packing refreshed");
        assertThat(((JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderHandoffType").get(0)).getSelectedItem())
                .isEqualTo("Delivery");
        assertThat(textField(root[0], "Order product name").isEnabled()).isTrue();
        verify(orders, times(1)).updateHandoffTypeReviewed(42L, "PICKUP", "DELIVERY");
        verify(orders, never()).updateHandoffType(any(), anyString());
    }

    @Test
    @DisplayName("stale handoff correction keeps the draft and can retry against the newer value")
    void crmRetriesStaleOrderHandoffWithoutLosingCorrection() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder reviewed = orderWithHandoff(customer, 42L, "PICKUP");
        CustomerOrder current = orderWithHandoff(customer, 42L, "MARKET");
        CustomerOrder updated = orderWithHandoff(customer, 42L, "DELIVERY");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(reviewed), List.of(current), List.of(updated));
        when(orders.updateHandoffTypeReviewed(42L, "PICKUP", "DELIVERY"))
                .thenThrow(new IllegalArgumentException(
                        "Order #42 handoff type changed after you started editing. "
                                + "The saved handoff is now MARKET. No order or inventory value "
                                + "was changed. Refresh Customers and Orders, review the current "
                                + "handoff, then save again."));
        when(orders.updateHandoffTypeReviewed(42L, "MARKET", "DELIVERY"))
                .thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            JComboBox<?> handoff = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderHandoffType").get(0);
            handoff.setSelectedItem("Delivery");
            button(root[0], "Save handoff type").doClick();
        });
        awaitLabel(root[0], "Order #42 changed · refresh and review");
        awaitCrmRefresh(root[0]);

        JComboBox<?> handoff = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderHandoffType").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(handoff.getSelectedItem()).isEqualTo("Delivery");
        assertThat(button(root[0], "Review newer saved handoff").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "saved handoff is now MARKET", "entered correction was kept",
                "Compare the current saved value");

        SwingUtilities.invokeAndWait(() -> {
            button(root[0], "Review newer saved handoff").doClick();
            assertThat(button(root[0], "Save handoff type").isEnabled()).isTrue();
            button(root[0], "Save handoff type").doClick();
        });
        awaitLabel(root[0], "Order #42 handoff saved · packing refreshed");
        awaitCrmRefresh(root[0]);

        verify(orders).updateHandoffTypeReviewed(42L, "PICKUP", "DELIVERY");
        verify(orders).updateHandoffTypeReviewed(42L, "MARKET", "DELIVERY");
        assertThat(handoff.getSelectedItem()).isEqualTo("Delivery");
        assertThat(host.status).contains("handoff type", "inventory was not changed");
    }

    private static CustomerOrder orderWithHandoff(
            Customer customer, long id, String handoffType) {
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "loading dock");
        order.setId(id);
        order.setHandoffType(handoffType);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        return order;
    }

    @Test
    @DisplayName("saved order date correction refreshes Market Day without changing inventory")
    void crmReschedulesSelectedOrderInBackground() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "pickup");
        original.setId(42L);
        original.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "CONFIRMED", "pickup");
        updated.setId(42L);
        updated.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(original), List.of(updated));
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(orders.updateOrderDateReviewed(42L, LocalDate.of(2026, 9, 12),
                LocalDate.of(2026, 9, 13)))
                .thenAnswer(invocation -> {
                    calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
                    return updated;
                });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            JTextField selectedDate = (JTextField) findNamed(
                    root[0], "crm.selectedOrderDate").get(0);
            assertThat(selectedDate.getText()).isEqualTo("2026-09-12");
            selectedDate.setText("2026-09-13");
            assertThat(button(root[0], "Save order date").isEnabled()).isTrue();
            button(root[0], "Save order date").doClick();
        });
        awaitLabel(root[0], "Order #42 date saved · Market Day refreshed");
        awaitCrmRefresh(root[0]);

        JTextField selectedDate = (JTextField) findNamed(
                root[0], "crm.selectedOrderDate").get(0);
        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer orders table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(calledOnUiThread).isFalse();
        verify(orders, times(1)).updateOrderDateReviewed(
                42L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13));
        verify(orders, never()).updateOrderDate(any(), any());
        assertThat(String.valueOf(table.getValueAt(0, 1))).isEqualTo("2026-09-13");
        assertThat(selectedDate.getText()).isEqualTo("2026-09-13");
        assertThat(button(root[0], "Save order date").isEnabled()).isFalse();
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);
        assertThat(host.status).contains("Market Day", "inventory was not changed");
    }

    @Test
    @DisplayName("saved order date locks stale order actions when CRM reload fails")
    void crmSavedOrderDateReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "pickup");
        original.setId(42L);
        original.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "CONFIRMED", "pickup");
        updated.setId(42L);
        updated.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(original))
                .thenThrow(new IllegalStateException("order reload offline"))
                .thenReturn(List.of(updated));
        when(orders.updateOrderDateReviewed(
                42L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13)))
                .thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            JTextField date = (JTextField) findNamed(
                    root[0], "crm.selectedOrderDate").get(0);
            date.setText("2026-09-13");
            button(root[0], "Save order date").doClick();
        });
        awaitLabel(root[0], "Order #42 date saved once · CRM reload failed");

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(String.valueOf(table.getValueAt(0, 1))).isEqualTo("2026-09-12");
        assertThat(((JTextField) findNamed(
                root[0], "crm.selectedOrderDate").get(0)).getText())
                .isEqualTo("2026-09-13");
        assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
        assertThat(button(root[0], "Save order date").isEnabled()).isFalse();
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "date", "2026-09-13", "saved once", "could not reload",
                "Retry Refresh CRM only", "retained pre-save order data");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "date", "saved once", "only safe retry action", "do not save");
        verify(orders, times(1)).updateOrderDateReviewed(
                42L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13));

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #42 date saved · Market Day refreshed");
        assertThat(String.valueOf(table.getValueAt(0, 1))).isEqualTo("2026-09-13");
        assertThat(textField(root[0], "Order product name").isEnabled()).isTrue();
        verify(orders, times(1)).updateOrderDateReviewed(
                42L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13));
        verify(orders, never()).updateOrderDate(any(), any());
    }

    @Test
    @DisplayName("saved order date remains explicit when rescheduling moves it outside filters")
    void crmSavedOrderDateOutsideCurrentViewReportsOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "pickup");
        original.setId(42L);
        original.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 13), "CONFIRMED", "pickup");
        updated.setId(42L);
        updated.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(original), List.of());
        when(orders.updateOrderDateReviewed(
                42L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13)))
                .thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            ((JTextField) findNamed(root[0], "crm.selectedOrderDate").get(0))
                    .setText("2026-09-13");
            button(root[0], "Save order date").doClick();
        });
        awaitLabel(root[0], "Order #42 date saved once · outside current view");
        awaitCrmRefresh(root[0]);

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(table.getRowCount()).isZero();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "date", "2026-09-13", "saved once", "outside the current filtered view",
                "Show all", "do not save this correction again");
        assertThat(host.status).contains(
                "date", "saved once", "outside", "Show all");
        verify(orders, times(1)).updateOrderDateReviewed(
                42L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13));
        verify(orders, never()).updateOrderDate(any(), any());
    }

    @Test
    @DisplayName("stale delivery correction keeps the draft and shows the newer saved date")
    void crmRejectsStaleOrderDateWithoutLosingCorrection() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder reviewed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "pickup");
        reviewed.setId(42L);
        reviewed.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder current = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 14), "CONFIRMED", "pickup");
        current.setId(42L);
        current.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(reviewed), List.of(current));
        when(orders.updateOrderDateReviewed(
                42L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13)))
                .thenThrow(new IllegalArgumentException(
                        "Order #42 pickup or delivery date changed after you started editing. "
                                + "The saved date is now 2026-09-14. No order or inventory value "
                                + "was changed. Refresh Customers and Orders, review the current "
                                + "date, then save again."));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            JTextField selectedDate = (JTextField) findNamed(
                    root[0], "crm.selectedOrderDate").get(0);
            selectedDate.setText("2026-09-13");
            button(root[0], "Save order date").doClick();
        });
        awaitLabel(root[0], "Order #42 changed · refresh and review");
        awaitCrmRefresh(root[0]);

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer orders table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JTextField selectedDate = (JTextField) findNamed(
                root[0], "crm.selectedOrderDate").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(String.valueOf(table.getValueAt(0, 1))).isEqualTo("2026-09-14");
        assertThat(selectedDate.getText()).isEqualTo("2026-09-13");
        assertThat(button(root[0], "Review newer saved date").isEnabled()).isTrue();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "saved date is now 2026-09-14", "No order or inventory value was changed",
                "entered correction was kept", "Compare the current saved value");
        assertThat(host.status).contains(
                "changed before the correction saved", "No order or inventory value changed",
                "entered correction was kept");
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);
        verify(orders).updateOrderDateReviewed(
                42L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13));
        verify(orders, never()).updateOrderDate(any(), any());
    }

    @Test
    @DisplayName("unsaved order corrections follow each order while workers inspect another")
    void crmPreservesPerOrderDateHandoffAndInstructionDraftsAcrossSelectionChanges() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder first = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        first.setHandoffType("PICKUP");
        first.setId(42L);
        first.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder second = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 15), "CONFIRMED", "Loading dock");
        second.setHandoffType("DELIVERY");
        second.setId(43L);
        second.addLine(new OrderLine("Nootka Rose", 8, "stems", 4.0));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(first, second));
        CrmTab tab = new CrmTab(customers, orders, null, new TrackingHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            JTextField selectedDate = (JTextField) findNamed(
                    root[0], "crm.selectedOrderDate").get(0);
            JTextField selectedNotes = (JTextField) findNamed(
                    root[0], "crm.selectedOrderNotes").get(0);
            JComboBox<?> selectedHandoff = (JComboBox<?>) findNamed(
                    root[0], "crm.selectedOrderHandoffType").get(0);

            table.setRowSelectionInterval(0, 0);
            selectedDate.setText("2026-09-13");
            selectedHandoff.setSelectedItem("Market");
            selectedNotes.setText("Call before arrival");
            table.setRowSelectionInterval(1, 1);
            assertThat(selectedDate.getText()).isEqualTo("2026-09-15");
            assertThat(selectedHandoff.getSelectedItem()).isEqualTo("Delivery");
            assertThat(selectedNotes.getText()).isEqualTo("Loading dock");

            table.setRowSelectionInterval(0, 0);
            assertThat(selectedDate.getText()).isEqualTo("2026-09-13");
            assertThat(selectedHandoff.getSelectedItem()).isEqualTo("Market");
            assertThat(selectedNotes.getText()).isEqualTo("Call before arrival");
            assertThat(button(root[0], "Save order date").isEnabled()).isTrue();
            assertThat(button(root[0], "Save handoff type").isEnabled()).isTrue();
            assertThat(button(root[0], "Save order instructions").isEnabled()).isTrue();
        });
        verify(orders, never()).updateOrderDate(any(), any());
        verify(orders, never()).updateHandoffType(any(), anyString());
        verify(orders, never()).updateNotes(any(), anyString());
    }

    @Test
    @DisplayName("CRM refresh requires review before an unsaved correction can replace newer work")
    void crmMakesWorkersReviewNewerSavedCorrectionsBeforeRetry() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder reviewed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Keep upright");
        reviewed.setHandoffType("PICKUP");
        reviewed.setId(42L);
        reviewed.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder newer = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 14), "DRAFT", "Use west gate");
        newer.setHandoffType("DELIVERY");
        newer.setId(42L);
        newer.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder other = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 15), "DRAFT", "Loading dock");
        other.setHandoffType("MARKET");
        other.setId(43L);
        other.addLine(new OrderLine("Nootka Rose", 8, "stems", 4.0));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(reviewed, other), List.of(newer, other));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(0, 0);
            ((JTextField) findNamed(root[0], "crm.selectedOrderDate").get(0))
                    .setText("2026-09-13");
            ((JComboBox<?>) findNamed(root[0], "crm.selectedOrderHandoffType").get(0))
                    .setSelectedItem("Market");
            ((JTextField) findNamed(root[0], "crm.selectedOrderNotes").get(0))
                    .setText("Call before arrival");
            table.setRowSelectionInterval(1, 1);
            button(root[0], "Refresh CRM").doClick();
        });
        awaitCrmRefresh(root[0]);

        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(0, 0));
        JTextField date = (JTextField) findNamed(root[0], "crm.selectedOrderDate").get(0);
        JComboBox<?> handoff = (JComboBox<?>) findNamed(
                root[0], "crm.selectedOrderHandoffType").get(0);
        JTextField notes = (JTextField) findNamed(
                root[0], "crm.selectedOrderNotes").get(0);
        JLabel selected = (JLabel) findNamed(root[0], "crm.selectedOrder").get(0);
        assertThat(date.getText()).isEqualTo("2026-09-13");
        assertThat(handoff.getSelectedItem()).isEqualTo("Market");
        assertThat(notes.getText()).isEqualTo("Call before arrival");
        assertThat(selected.getText()).contains("changed · review newer saved");
        assertThat(button(root[0], "Review newer saved date").getToolTipText())
                .contains("2026-09-14", "2026-09-13", "does not save");
        assertThat(button(root[0], "Review newer saved handoff").getToolTipText())
                .contains("Delivery", "Market", "does not save");
        assertThat(button(root[0], "Review newer saved instructions").getToolTipText())
                .contains("Use west gate", "Call before arrival", "does not save");

        SwingUtilities.invokeAndWait(() -> {
            button(root[0], "Review newer saved date").doClick();
            button(root[0], "Review newer saved handoff").doClick();
            button(root[0], "Review newer saved instructions").doClick();
        });

        assertThat(button(root[0], "Save order date").isEnabled()).isTrue();
        assertThat(button(root[0], "Save handoff type").isEnabled()).isTrue();
        assertThat(button(root[0], "Save order instructions").isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0)).getText())
                .contains("reviewed · draft still unsaved");
        assertThat(host.status).contains("nothing was saved", "Choose Save only");
        verify(orders, never()).updateOrderDateReviewed(anyLong(), any(), any());
        verify(orders, never()).updateHandoffTypeReviewed(anyLong(), anyString(), anyString());
        verify(orders, never()).updateNotesReviewed(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("saved order instructions are explicit and preserve the unfinished new-order draft")
    void crmUpdatesSelectedOrderInstructionsWithoutOverwritingNewOrderDraft() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Keep upright");
        original.setId(42L);
        original.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Call on arrival");
        updated.setId(42L);
        updated.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(original), List.of(updated));
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        when(orders.updateNotesReviewed(42L, "Keep upright", "Call on arrival"))
                .thenAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            return updated;
        });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTextField newOrderNotes = textField(root[0], "Order notes");
            newOrderNotes.setText("Unfinished new-order draft");
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            JTextField selectedNotes = (JTextField) findNamed(
                    root[0], "crm.selectedOrderNotes").get(0);
            assertThat(selectedNotes.getText()).isEqualTo("Keep upright");
            assertThat(newOrderNotes.getText()).isEqualTo("Unfinished new-order draft");
            selectedNotes.setText("Call on arrival");
            assertThat(button(root[0], "Save order instructions").isEnabled()).isTrue();
            button(root[0], "Save order instructions").doClick();
        });
        awaitLabel(root[0], "Order #42 instructions saved");
        awaitCrmRefresh(root[0]);

        assertThat(calledOnUiThread).isFalse();
        verify(orders, times(1)).updateNotesReviewed(
                42L, "Keep upright", "Call on arrival");
        verify(orders, never()).updateNotes(any(), anyString());
        assertThat(((JTextField) findNamed(root[0], "crm.selectedOrderNotes").get(0)).getText())
                .isEqualTo("Call on arrival");
        assertThat(textField(root[0], "Order notes").getText())
                .isEqualTo("Unfinished new-order draft");
        assertThat(button(root[0], "Save order instructions").isEnabled()).isFalse();
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);
        assertThat(host.status).contains(
                "Instructions were saved", "Kitsap Blooms", "inventory was not changed");
    }

    @Test
    @DisplayName("saved order instructions lock stale order actions when CRM reload fails")
    void crmSavedOrderInstructionsReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder original = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Keep upright");
        original.setId(42L);
        original.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder updated = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Call on arrival");
        updated.setId(42L);
        updated.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(original))
                .thenThrow(new IllegalStateException("order reload offline"))
                .thenReturn(List.of(updated));
        when(orders.updateNotesReviewed(42L, "Keep upright", "Call on arrival"))
                .thenReturn(updated);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            JTextField notes = (JTextField) findNamed(
                    root[0], "crm.selectedOrderNotes").get(0);
            notes.setText("Call on arrival");
            button(root[0], "Save order instructions").doClick();
        });
        awaitLabel(root[0], "Order #42 instructions saved once · CRM reload failed");

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(((JTextField) findNamed(
                root[0], "crm.selectedOrderNotes").get(0)).getText())
                .isEqualTo("Call on arrival");
        assertThat(textField(root[0], "Order product name").isEnabled()).isFalse();
        assertThat(button(root[0], "Save order instructions").isEnabled()).isFalse();
        assertThat(button(root[0], "Confirm order").isEnabled()).isFalse();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "instructions", "Call on arrival", "saved once", "could not reload",
                "Retry Refresh CRM only", "retained pre-save order data");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "instructions", "saved once", "only safe retry action", "do not save");
        verify(orders, times(1)).updateNotesReviewed(
                42L, "Keep upright", "Call on arrival");

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #42 instructions saved");
        assertThat(((JTextField) findNamed(
                root[0], "crm.selectedOrderNotes").get(0)).getText())
                .isEqualTo("Call on arrival");
        assertThat(textField(root[0], "Order product name").isEnabled()).isTrue();
        verify(orders, times(1)).updateNotesReviewed(
                42L, "Keep upright", "Call on arrival");
        verify(orders, never()).updateNotes(any(), anyString());
    }

    @Test
    @DisplayName("failed order notes save preserves the target and notes for retry")
    void crmOrderNotesFailureKeepsTargetAndDraft() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Keep upright");
        draft.setId(42L);
        draft.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(draft));
        doThrow(new IllegalStateException("orders database busy"))
                .when(orders).updateNotesReviewed(
                        42L, "Keep upright", "Saturday market");
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order notes").setText("Unfinished new-order draft");
            JTextField selectedNotes = (JTextField) findNamed(
                    root[0], "crm.selectedOrderNotes").get(0);
            assertThat(selectedNotes.getText()).isEqualTo("Keep upright");
            selectedNotes.setText("Saturday market");
            button(root[0], "Save order instructions").doClick();
        });
        awaitLabel(root[0], "Order #42 not updated · retry available");

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer orders table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.isEnabled()).isTrue();
        assertThat(((JTextField) findNamed(root[0], "crm.selectedOrderNotes").get(0)).getText())
                .isEqualTo("Saturday market");
        assertThat(textField(root[0], "Order notes").getText())
                .isEqualTo("Unfinished new-order draft");
        assertThat(button(root[0], "Save order instructions").isEnabled()).isTrue();
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("orders database busy", "same order remains selected",
                        "details were kept", "retry");
        assertThat(host.status).contains("not updated", "selected order was kept", "retry");
    }

    @Test
    @DisplayName("unfinished product entries remain bound to their original order")
    void crmProductDraftsCannotFollowTheWrongOrderSelection() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder first = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        first.setId(42L);
        OrderLine firstLine = new OrderLine("Dahlia Mix", 1, "stems", 3.5);
        firstLine.setId(101L);
        first.addLine(firstLine);
        CustomerOrder second = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        second.setId(43L);
        OrderLine secondLine = new OrderLine("Tulip Bunch", 1, "bunches", 12);
        secondLine.setId(102L);
        second.addLine(secondLine);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(first, second));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);

        SwingUtilities.invokeAndWait(() -> {
            table.setRowSelectionInterval(0, 0);
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order quantity").setText("2");
            textField(root[0], "Order unit").setText("bunches");
            textField(root[0], "Order unit price").setText("18.00");
            table.setRowSelectionInterval(1, 1);
        });

        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("1");
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
        assertThat(host.status).contains("Paused", "order #42", "own product entry");

        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order product name").setText("Cosmos");
            textField(root[0], "Order quantity").setText("4");
            textField(root[0], "Order unit").setText("stems");
            textField(root[0], "Order unit price").setText("2.50");
            table.setRowSelectionInterval(0, 0);
        });

        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Rose Bunch");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("2");
        assertThat(textField(root[0], "Order unit").getText()).isEqualTo("bunches");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("18.00");
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();
        assertThat(host.status).contains("Restored", "order #42", "bound to this order");

        SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(1, 1));
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Cosmos");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("4");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("2.50");

        SwingUtilities.invokeAndWait(() -> {
            button(root[0], "Clear product entry").doClick();
            button(root[0], "Discard product entry").doClick();
            button(root[0], "Edit selected product").doClick();
            textField(root[0], "Order quantity").setText("7");
            table.setRowSelectionInterval(0, 0);
            table.setRowSelectionInterval(1, 1);
        });
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Tulip Bunch");
        assertThat(textField(root[0], "Order quantity").getText()).isEqualTo("7");
        assertThat(button(root[0], "Save product changes").isEnabled()).isTrue();
        assertThat(host.status).contains("Restored", "order #43", "bound to this order");
        verify(orders, never()).addLine(anyLong(), any());
        verify(orders, never()).updateLine(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("new order remains selected for fast multi-product entry")
    void crmSelectsNewOrderForNextProduct() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder created = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        created.setId(42L);
        created.addLine(new OrderLine("Dahlia Mix", 1, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(), List.of(created));
        when(orders.createReviewed(any(), any(LocalDate.class), anyString(), anyString(),
                anyString(), anyList(), anyMap(), anyBoolean()))
                .thenReturn(created);
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order product name").setText("Dahlia Mix");
            textField(root[0], "Order unit price").setText("3.50");
            button(root[0], "Create & select order").doClick();
        });
        awaitLabel(root[0], "Selected #42 · Kitsap Blooms · Draft · 1 product · $3.50");
        assertThat(tab.applicationExitWarning()).isNull();

        JTable ordersTable = find(root[0], JTable.class).stream()
                .filter(table -> "Customer orders table".equals(
                        table.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(ordersTable.getSelectedRow()).isZero();
        assertThat(ordersTable.getValueAt(ordersTable.getSelectedRow(), 0)).isEqualTo(42L);
        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
        assertThat(button(root[0], "Start another order").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "crm.newOrderDetails")).hasSize(1);
        assertThat(findNamed(root[0], "crm.productEntry")).hasSize(1);
        assertThat(textField(root[0], "Order date").isEnabled()).isFalse();
        assertThat(textField(root[0], "Order notes").isEnabled()).isFalse();
        assertThat(find(root[0], JComboBox.class).stream()
                .filter(combo -> "New order customer".equals(
                        combo.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().isEnabled()).isFalse();
        assertThat(find(root[0], JComboBox.class).stream()
                .filter(combo -> "New order status".equals(
                        combo.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().isEnabled()).isFalse();
        assertThat(find(root[0], JComboBox.class).stream()
                .filter(combo -> "New order handoff type".equals(
                        combo.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow())
                .satisfies(combo -> {
                    assertThat(combo.getSelectedItem()).isEqualTo("Pickup");
                    assertThat(combo.isEnabled()).isFalse();
                    assertThat(combo.getToolTipText()).contains("pickup", "delivery", "market");
                });
        assertThat(textField(root[0], "Order notes").getToolTipText())
                .contains("draft paused", "Start another order");
        assertThat(findNamed(root[0], "crm.selectedOrder"))
                .singleElement()
                .satisfies(component -> assertThat(((JLabel) component).getText())
                        .contains("Selected #42", "Kitsap Blooms", "Draft",
                                "1 product", "$3.50"));

        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Order product name").setText("Rose Bunch");
            textField(root[0], "Order unit price").setText("18.00");
        });

        assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();
        assertThat(host.status).contains("selected it", "next product");

        SwingUtilities.invokeAndWait(() -> button(root[0], "Start another order").doClick());

        assertThat(ordersTable.getSelectedRow()).isEqualTo(-1);
        assertThat(textField(root[0], "Order product name").getText()).isEmpty();
        assertThat(button(root[0], "Create & select order").isEnabled()).isFalse();
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isFalse();
        assertThat(button(root[0], "Start another order").isEnabled()).isFalse();
        assertThat(textField(root[0], "Order date").isEnabled()).isTrue();
        assertThat(textField(root[0], "Order notes").isEnabled()).isTrue();
        assertThat(find(root[0], JComboBox.class).stream()
                .filter(combo -> "New order handoff type".equals(
                        combo.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().isEnabled()).isTrue();
        assertThat(find(root[0], JComboBox.class).stream()
                .filter(combo -> "New order customer".equals(
                        combo.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow().isEnabled()).isTrue();
        assertThat(find(root[0], JComboBox.class).stream()
                .filter(combo -> "New order status".equals(
                        combo.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow())
                .satisfies(combo -> {
                    assertThat(combo.isEnabled()).isFalse();
                    assertThat(comboItems(combo)).containsExactly("Draft");
                    assertThat(combo.getToolTipText())
                            .contains("always start as Draft", "review", "Confirm order");
                    assertThat(combo.getAccessibleContext().getAccessibleDescription())
                            .contains("saved details", "Confirm order");
                });
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                .contains("New order mode", "separate order");
        assertThat(host.status).contains("order #42", "paused safely", "reselect");

        SwingUtilities.invokeAndWait(() -> ordersTable.setRowSelectionInterval(0, 0));
        assertThat(textField(root[0], "Order product name").getText()).isEqualTo("Rose Bunch");
        assertThat(textField(root[0], "Order unit price").getText()).isEqualTo("18.00");
        assertThat(button(root[0], "Add product to selected order").isEnabled()).isTrue();
        assertThat(host.status).contains("Restored", "order #42", "bound to this order");
    }

    @Test
    @DisplayName("overdue completion requires stable late-handoff approval")
    void crmOverdueCompletionRequiresExplicitStableReview() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera", "", "",
                "FLORIST", "Loading dock");
        LocalDate overdue = LocalDate.now().minusDays(5);
        CustomerOrder confirmed = new CustomerOrder(
                customer, overdue, "CONFIRMED", "Call on arrival", "DELIVERY");
        confirmed.setId(40L);
        confirmed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        CustomerOrder completed = new CustomerOrder(
                customer, overdue, "FULFILLED", "Call on arrival", "DELIVERY");
        completed.setId(40L);
        completed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        CountDownLatch fulfilled = new CountDownLatch(1);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed), List.of(completed));
        when(orders.fulfillReviewed(eq(40L), any(OrderService.OrderReviewSnapshot.class)))
                .thenAnswer(invocation -> {
                    fulfilled.countDown();
                    return completed;
                });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
        });

        JButton complete = button(root[0], "Complete order");
        assertThat(complete.isEnabled()).isTrue();
        assertThat(complete.getAccessibleContext().getAccessibleDescription())
                .contains("scheduled for " + overdue, "old order", "today's handoff");
        SwingUtilities.invokeAndWait(complete::doClick);

        JButton reviewOverdue = button(root[0], "Review overdue completion");
        assertThat(reviewOverdue.isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0)).getText())
                .isEqualTo("Overdue handoff · completion paused");
        assertThat(host.status).contains(
                "scheduled for " + overdue, "inventory was not changed", "happened late");
        verify(orders, never()).fulfillReviewed(anyLong(), any());

        JTextField notes = (JTextField) findNamed(root[0], "crm.selectedOrderNotes").get(0);
        SwingUtilities.invokeAndWait(() -> notes.setText("Different loading dock"));
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(() -> notes.setText("Call on arrival"));
        assertThat(button(root[0], "Complete order").isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            button(root[0], "Complete order").doClick();
            button(root[0], "Review overdue completion").doClick();
        });
        assertThat(fulfilled.await(2, TimeUnit.SECONDS)).isTrue();
        verify(orders, times(1)).fulfillReviewed(
                eq(40L), any(OrderService.OrderReviewSnapshot.class));
    }

    @Test
    @DisplayName("future-dated completion requires stable early-handoff approval")
    void crmFutureCompletionRequiresExplicitStableReview() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera", "", "",
                "FLORIST", "Loading dock");
        LocalDate future = LocalDate.now().plusDays(14);
        CustomerOrder confirmed = new CustomerOrder(
                customer, future, "CONFIRMED", "Call on arrival", "DELIVERY");
        confirmed.setId(41L);
        confirmed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        CustomerOrder completed = new CustomerOrder(
                customer, future, "FULFILLED", "Call on arrival", "DELIVERY");
        completed.setId(41L);
        completed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        CountDownLatch fulfilled = new CountDownLatch(1);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed), List.of(completed));
        when(orders.fulfillReviewed(eq(41L), any(OrderService.OrderReviewSnapshot.class)))
                .thenAnswer(invocation -> {
                    fulfilled.countDown();
                    return completed;
                });
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
        });

        JButton complete = button(root[0], "Complete order");
        assertThat(complete.isEnabled()).isTrue();
        assertThat(complete.getAccessibleContext().getAccessibleDescription())
                .contains("scheduled for " + future, "early physical handoff must be explicit");
        SwingUtilities.invokeAndWait(complete::doClick);

        JButton reviewEarly = button(root[0], "Review early completion");
        assertThat(reviewEarly.isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0)).getText())
                .isEqualTo("Future handoff · completion paused");
        assertThat(host.status).contains(
                "scheduled for " + future, "inventory was not changed", "actually happened early");
        verify(orders, never()).fulfillReviewed(anyLong(), any());

        JTextField selectedDate = (JTextField) findNamed(
                root[0], "crm.selectedOrderDate").get(0);
        SwingUtilities.invokeAndWait(() -> selectedDate.setText(future.plusDays(1).toString()));
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(() -> selectedDate.setText(future.toString()));
        assertThat(button(root[0], "Complete order").isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            button(root[0], "Complete order").doClick();
            button(root[0], "Review early completion").doClick();
        });
        assertThat(fulfilled.await(2, TimeUnit.SECONDS)).isTrue();
        verify(orders, times(1)).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));
    }

    @Test
    @DisplayName("individual order completion runs off the UI thread and blocks duplicates")
    void crmCompletesOrderInBackgroundOnce() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Saturday pickup");
        confirmed.setId(41L);
        confirmed.setHandoffType("PICKUP");
        confirmed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        CustomerOrder completed = new CustomerOrder(
                customer, LocalDate.now(), "FULFILLED", "Saturday pickup");
        completed.setId(41L);
        completed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed), List.of(completed));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnUiThread = new AtomicBoolean(true);
        AtomicReference<OrderService.OrderReviewSnapshot> capturedReview =
                new AtomicReference<>();
        doAnswer(invocation -> {
            calledOnUiThread.set(SwingUtilities.isEventDispatchThread());
            capturedReview.set(invocation.getArgument(1));
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(orders).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            tab.startOrderFulfillment(41L, "the Kitsap Blooms order");
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Completing order…").isEnabled()).isFalse();
            assertThat(button(root[0], "Apply filters").isEnabled()).isFalse();
            assertThat(find(root[0], JTable.class).stream()
                    .filter(table -> "Customer orders table".equals(
                            table.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow().isEnabled()).isFalse();
            tab.applyRolePermissions(true);
            assertThat(button(root[0], "Completing order…").isEnabled()).isFalse();
            tab.startOrderFulfillment(41L, "the Kitsap Blooms order");
        });

        release.countDown();
        awaitLabel(root[0], "Order #41 completed · inventory updated");
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #41 completed once · inventory deducted");

        assertThat(calledOnUiThread).isFalse();
        assertThat(capturedReview.get()).satisfies(snapshot -> {
            assertThat(snapshot.orderId()).isEqualTo(41L);
            assertThat(snapshot.customerName()).isEqualTo("Kitsap Blooms");
            assertThat(snapshot.status()).isEqualTo("CONFIRMED");
            assertThat(snapshot.notes()).isEqualTo("Saturday pickup");
            assertThat(snapshot.lines()).singleElement().satisfies(line -> {
                assertThat(line.productName()).isEqualTo("Dahlia Mix");
                assertThat(line.quantity()).isEqualTo(8);
                assertThat(line.unit()).isEqualTo("stems");
            });
        });
        verify(orders, times(1)).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).fulfill(41L);
        assertThat(button(root[0], "Apply filters").isEnabled()).isTrue();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(((JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0))
                .getAccessibleContext().getAccessibleDescription())
                .contains("completed successfully", "deducted once", "Do not run completion again");
        assertThat(host.status).contains(
                "complete", "deducted once", "refreshed Completed order is selected");
    }

    @Test
    @DisplayName("completed order outside the refreshed queue retains an inventory receipt")
    void crmCompletionOutsideCurrentViewCannotLookRetryable() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Saturday pickup");
        confirmed.setId(41L);
        confirmed.setHandoffType("PICKUP");
        confirmed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed), List.of());
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderFulfillment(41L, "the Kitsap Blooms Saturday pickup");
        });
        awaitLabel(root[0], "Order #41 completed once · outside current view");
        awaitCrmRefresh(root[0]);

        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(table.getRowCount()).isZero();
        assertThat(table.getSelectedRow()).isEqualTo(-1);
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "completed successfully", "inventory was deducted once",
                "outside the current filtered view", "Confirmed-only", "Show all",
                "Completed filter", "Do not complete or deduct this order again");
        assertThat(button(root[0], "Complete order").getToolTipText()).contains(
                "deducted once", "Do not complete");
        assertThat(host.status).contains(
                "complete", "deducted once", "outside the current view", "do not complete");
        verify(orders, times(1)).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).fulfill(41L);
    }

    @Test
    @DisplayName("successful completion stays non-repeatable when its automatic reload fails")
    void crmCompletionReloadFailureRequiresRefreshOnly() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Saturday pickup");
        confirmed.setId(41L);
        confirmed.setHandoffType("PICKUP");
        confirmed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        CustomerOrder completed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "FULFILLED", "Saturday pickup");
        completed.setId(41L);
        completed.setHandoffType("PICKUP");
        completed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(confirmed))
                .thenThrow(new IllegalStateException("order database temporarily unavailable"))
                .thenReturn(List.of(completed));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderFulfillment(41L, "the Kitsap Blooms Saturday pickup");
        });
        awaitLabel(root[0], "Order #41 completed once · exact-order reload failed");

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "completed successfully", "inventory was deducted once", "could not reload",
                "Retry Refresh CRM only", "do not run completion");
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).contains(
                "inventory was deducted once", "only safe retry action",
                "do not run completion");
        assertThat(host.status).contains(
                "complete", "inventory was deducted once", "CRM reload failed",
                "Retry Refresh CRM", "do not complete");
        verify(orders, times(1)).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));

        SwingUtilities.invokeAndWait(() -> submitOrderProductShortcut(root[0]));
        assertThat(host.status).contains(
                "completed successfully", "deducted once", "Refresh CRM only",
                "do not add a product", "deduct inventory again");
        verify(orders, times(1)).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));

        SwingUtilities.invokeAndWait(tab::refreshData);
        awaitCrmRefresh(root[0]);
        awaitLabel(root[0], "Order #41 completed once · inventory deducted");
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(button(root[0], "Refresh CRM").getAccessibleContext()
                .getAccessibleDescription()).isEqualTo(
                "Reload customers, filtered orders, and inventory choices.");
        verify(orders, times(1)).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).fulfill(41L);
    }

    @Test
    @DisplayName("failed individual order completion preserves selection and a clear retry")
    void crmCompletionFailureKeepsOrderActionable() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Saturday pickup");
        confirmed.setId(41L);
        confirmed.setHandoffType("PICKUP");
        confirmed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        Item available = new Item("Dahlia Mix", "Flowers/Plants",
                3.5, "stems", 1.0, 8, "");
        Item reduced = new Item("Dahlia Mix", "Flowers/Plants",
                3.5, "stems", 1.0, 2, "");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(inventory.getAllItems()).thenReturn(List.of(available), List.of(reduced));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed));
        doThrow(new IllegalArgumentException("only 2 stems available"))
                .when(orders).fulfillReviewed(
                        eq(41L), any(OrderService.OrderReviewSnapshot.class));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(root[0], JTable.class).stream()
                    .filter(candidate -> "Customer orders table".equals(
                            candidate.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            table.setRowSelectionInterval(0, 0);
            tab.startOrderFulfillment(41L, "the Kitsap Blooms order");
        });
        awaitLabel(root[0], "Order #41 not completed · current stock refreshed");

        JTable table = find(root[0], JTable.class).stream()
                .filter(candidate -> "Customer orders table".equals(
                        candidate.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        JLabel stock = (JLabel) findNamed(root[0], "crm.selectedOrderStock").get(0);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(table.isEnabled()).isTrue();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(stock.getText()).contains("short 6");
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("only 2 stems available", "remains selected", "stock guidance",
                        "refreshed", "before opening another completion review");
        assertThat(host.status).contains("not completed", "stock guidance", "refreshed",
                "before retrying");
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);
        verify(inventory, times(2)).getAllItems();
        verify(orders).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).fulfill(41L);
    }

    @Test
    @DisplayName("failed completion keeps retry locked when its automatic refresh also fails")
    void crmCompletionRefreshFailureKeepsStaleRetryLocked() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Saturday pickup");
        confirmed.setId(41L);
        confirmed.setHandoffType("PICKUP");
        confirmed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(confirmed))
                .thenThrow(new IllegalStateException("order reload offline"));
        doThrow(new IllegalArgumentException("only 2 stems available"))
                .when(orders).fulfillReviewed(
                        eq(41L), any(OrderService.OrderReviewSnapshot.class));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderFulfillment(41L, "the Kitsap Blooms order");
        });
        awaitLabel(root[0], "Order #41 not completed · automatic refresh failed");

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        assertThat(table.getSelectedRow()).isZero();
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(button(root[0], "Complete order").getToolTipText())
                .contains("only 2 stems available", "could not reload", "Refresh CRM");
        assertThat(result.getAccessibleContext().getAccessibleDescription())
                .contains("exact order remains selected", "could not reload", "Refresh CRM");
        assertThat(((JLabel) findNamed(root[0], "crm.refreshStatus").get(0)).getText())
                .contains("CRM refresh failed", "retry");
        assertThat(host.status).contains(
                "not completed", "automatic CRM refresh failed", "Existing work was kept");
        verify(orders).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));
    }

    @Test
    @DisplayName("stale direct completion preserves the confirmed order without inventory changes")
    void crmRejectsCompletionWhenReviewedOrderChanged() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Saturday pickup");
        confirmed.setId(41L);
        confirmed.setHandoffType("PICKUP");
        OrderLine line = new OrderLine("Dahlia Mix", 8, "stems", 3.5);
        line.setId(101L);
        confirmed.addLine(line);
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed));
        doThrow(new IllegalArgumentException(
                "Order #41 changed after completion review. No status or inventory was changed. "
                        + "Refresh Customers and Orders, review the current customer load, then "
                        + "complete it again."))
                .when(orders).fulfillReviewed(
                        eq(41L), any(OrderService.OrderReviewSnapshot.class));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderFulfillment(41L, "the Kitsap Blooms order");
        });
        awaitLabel(root[0], "Order #41 changed · review refreshed order");

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "changed after completion review", "No status or inventory was changed",
                "Refresh Customers and Orders", "remains selected", "now refreshed");
        assertThat(((JLabel) findNamed(root[0], "crm.selectedOrder").get(0)).getText())
                .contains("Selected #41", "Kitsap Blooms", "Confirmed");
        assertThat(button(root[0], "Complete order").isEnabled()).isTrue();
        assertThat(host.status).contains(
                "not completed", "current order", "stock guidance", "refreshed");
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);
        verify(orders).fulfillReviewed(
                eq(41L), any(OrderService.OrderReviewSnapshot.class));
        verify(orders, never()).fulfill(41L);
    }

    @Test
    @DisplayName("failed completion reports when the refreshed order leaves the current view")
    void crmCompletionRefreshReportsMissingTargetWithoutStaleRetry() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "", "", "",
                "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Saturday pickup");
        confirmed.setId(41L);
        confirmed.setHandoffType("PICKUP");
        confirmed.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed), List.of());
        doThrow(new IllegalArgumentException(
                "Order #41 changed after completion review. No status or inventory was changed."))
                .when(orders).fulfillReviewed(
                        eq(41L), any(OrderService.OrderReviewSnapshot.class));
        TrackingHost host = new TrackingHost();
        CrmTab tab = new CrmTab(customers, orders, null, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
            table.setRowSelectionInterval(0, 0);
            tab.startOrderFulfillment(41L, "the Kitsap Blooms order");
        });
        awaitLabel(root[0], "Order #41 not completed · no longer in current view");

        JLabel result = (JLabel) findNamed(root[0], "crm.fulfillmentStatus").get(0);
        JTable table = (JTable) findNamed(root[0], "crm.orders.table").get(0);
        assertThat(table.getRowCount()).isZero();
        assertThat(table.getSelectedRow()).isEqualTo(-1);
        assertThat(button(root[0], "Complete order").isEnabled()).isFalse();
        assertThat(result.getAccessibleContext().getAccessibleDescription()).contains(
                "refreshed successfully", "no longer in the current filtered view",
                "completed, cancelled, deleted", "Clear the filters", "do not repeat");
        assertThat(host.status).contains(
                "Order #41", "no longer in the refreshed view", "current state");
        assertThat(host.refreshCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("customer selection enables only actions appropriate to the active role")
    void customerSelectionAndRoleTransition() throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@example.test", "360-555-0184", "FLORIST", "Thursday pickup");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        CrmTab tab = new CrmTab(customers, orders, null, new TestHost());
        JComponent[] root = new JComponent[1];
        boolean[] states = new boolean[6];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitCrmRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JTable customerTable = find(root[0], JTable.class).stream()
                    .filter(table -> "Customer accounts table".equals(
                            table.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            customerTable.setRowSelectionInterval(0, 0);
            states[0] = button(root[0], "Load selected").isEnabled();
            states[1] = button(root[0], "Delete selected").isEnabled();
            states[2] = button(root[0], "Statement PDF…").isEnabled();
            tab.applyRolePermissions(false);
            states[3] = button(root[0], "Load selected").isEnabled();
            states[4] = button(root[0], "Delete selected").isEnabled();
            states[5] = button(root[0], "Statement PDF…").isEnabled();
        });

        assertThat(states).containsExactly(true, true, true, false, false, true);
    }

    @Test
    @DisplayName("empty market day is neutral and gives exact recovery instead of stock OK")
    void emptyMarketDayPlanIsActionableAndNotGreenStockSuccess() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 0, 0,
                        List.of(), List.of(), 0, ""));
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);

        JLabel status = (JLabel) findNamed(root[0], "marketDay.planStatus").get(0);
        assertThat(status.getText())
                .contains("Orders: 0", "no work in scope")
                .doesNotContain("stock OK");
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("No orders are ready", "Review draft orders",
                        "include unconfirmed drafts");
        assertThat(textArea(root[0], "Market day packing list").getText())
                .contains("No orders are ready", "Review draft orders",
                        "include unconfirmed drafts");
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();
        assertThat(button(root[0], "Next unpacked").isEnabled()).isFalse();
        assertThat(button(root[0], "Resolve stock shortfalls").isEnabled()).isFalse();

        SwingUtilities.invokeAndWait(() -> button(root[0], "Review draft orders").doClick());
        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderStatus).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("market day emphasizes scope and keeps viewer-safe packing exports")
    void marketDayHierarchyAndPermissions() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 125,
                        List.of(new MarketDayPackingService.CustomerPack(
                                1L, "Kitsap Blooms", "FLORIST",
                                "Sam Rivera", "360-555-0142", "sam@kitsap.example", "CONFIRMED",
                                day, "Keep upright", List.of(
                                        new MarketDayPackingService.PackLine(
                                                "Dahlia Mix", "stems", 12, 3.5, 42)), 125,
                                "DELIVERY")),
                        List.of(new MarketDayPackingService.ProductNeed(
                                "Dahlia Mix", "stems", 12, 4, true, 8)),
                        1, "Packing list"));
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];
        boolean[] enabledForWorker = new boolean[2];
        String[] packingProgress = new String[2];
        String[] refreshStatus = new String[1];
        boolean[] nextUnpacked = new boolean[2];
        boolean[] retainedAfterRefresh = new boolean[1];
        String[] completionGuidance = new String[2];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            enabledForWorker[0] = button(
                    root[0], "Complete confirmed orders…").isEnabled();
            packingProgress[0] = ((javax.swing.JLabel) findNamed(
                    root[0], "marketDay.packingProgress").get(0)).getText();
            nextUnpacked[0] = button(root[0], "Next unpacked").isEnabled();
            JCheckBox packed = (JCheckBox) findNamed(
                    root[0], "marketDay.packOrder.1").get(0);
            packed.doClick();
            enabledForWorker[1] = button(
                    root[0], "Complete confirmed orders…").isEnabled();
            JLabel completion = (JLabel) findNamed(
                    root[0], "marketDay.fulfillmentStatus").get(0);
            completionGuidance[0] = completion.getText();
            completionGuidance[1] = completion.getAccessibleContext()
                    .getAccessibleDescription();
            packingProgress[1] = ((javax.swing.JLabel) findNamed(
                    root[0], "marketDay.packingProgress").get(0)).getText();
            nextUnpacked[1] = button(root[0], "Next unpacked").isEnabled();
            tab.refreshData();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            retainedAfterRefresh[0] = ((JCheckBox) findNamed(
                    root[0], "marketDay.packOrder.1").get(0)).isSelected();
            refreshStatus[0] = ((JLabel) findNamed(
                    root[0], "marketDay.checklistRefresh").get(0)).getText();
            button(root[0], "Resolve stock shortfalls").doClick();
            tab.applyRolePermissions(false);
        });

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "farm.section")).hasSize(2);
        assertThat(enabledForWorker).containsExactly(false, false);
        assertThat(completionGuidance[0])
                .contains("0 of 1 checked", "1 stock gap", "completion locked");
        assertThat(completionGuidance[1])
                .contains("Resolve every stock gap in Inventory", "rebuild this packing list");
        assertThat(packingProgress[0]).contains("0 packed", "0 ready remaining", "1 blocked");
        assertThat(packingProgress[1]).contains("0 packed", "0 ready remaining", "1 blocked");
        assertThat(nextUnpacked).containsExactly(false, false);
        assertThat(retainedAfterRefresh[0]).isFalse();
        assertThat(refreshStatus[0]).contains("kept when order details are unchanged");
        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.status).contains(
                "Opened Inventory for market shortfalls", "Dahlia Mix");
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();
        assertThat(button(root[0], "Build packing list").isEnabled()).isTrue();
        assertThat(button(root[0], "Export file…").isEnabled()).isTrue();
        assertThat(button(root[0], "Next Saturday").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "marketDay.exportType"))
                .singleElement()
                .isInstanceOf(JComboBox.class);
        assertThat(findNamed(root[0], "marketDay.packingChecklist")).hasSize(1);
        assertThat(findNamed(root[0], "marketDay.checklistHeader"))
                .singleElement().isInstanceOf(JPanel.class)
                .satisfies(component -> assertThat(((JPanel) component).getLayout())
                        .isInstanceOf(BoxLayout.class));
        assertThat(findNamed(root[0], "marketDay.checklistActions"))
                .singleElement().isInstanceOf(JPanel.class)
                .satisfies(component -> {
                    JPanel actions = (JPanel) component;
                    assertThat(actions.getLayout()).isInstanceOf(GridLayout.class);
                    assertThat(find(actions, JButton.class)).extracting(JButton::getText)
                            .containsExactly("Next unpacked", "Clear checks");
                    assertThat(actions.getAccessibleContext().getAccessibleName())
                            .contains("navigation", "recovery");
                });
        assertThat(findNamed(root[0], "marketDay.packDetails.1"))
                 .singleElement()
                 .satisfies(component -> assertThat(((javax.swing.JLabel) component).getText())
                         .contains("12 stems Dahlia Mix", "Sam Rivera", "360-555-0142",
                                 "sam@kitsap.example", "Keep upright"));
        assertThat(((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).getText())
                .contains("Delivery", day.toString());
        assertThat(((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0))
                .getAccessibleContext().getAccessibleName())
                .contains("Kitsap Blooms", "Delivery", day.toString());
        assertThat(((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).isEnabled())
                .isFalse();
        assertThat(((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0))
                .getAccessibleContext().getAccessibleDescription())
                .contains("cannot be marked packed", "Dahlia Mix", "short 8 stems");
        assertThat(findNamed(root[0], "marketDay.packReadiness.1"))
                .singleElement().satisfies(component -> {
                    JLabel label = (JLabel) component;
                    assertThat(label.getText()).contains("Blocked", "1 product issue");
                    assertThat(label.getAccessibleContext().getAccessibleDescription())
                            .contains("Dahlia Mix", "Resolve the order or Inventory issue");
                });
        assertThat(findNamed(root[0], "marketDay.handoffGroup.DELIVERY"))
                .singleElement()
                .satisfies(component -> assertThat(((JLabel) component).getText())
                        .contains("Delivery", "1 load"));
        assertThat(findNamed(root[0], "marketDay.packingProgress"))
                .singleElement()
                .satisfies(component -> assertThat(((javax.swing.JLabel) component).getText())
                        .contains("0 packed", "0 ready remaining", "1 blocked"));
        assertThat(findNamed(root[0], "marketDay.shortfallSummary"))
                .singleElement()
                .satisfies(component -> {
                    JLabel label = (JLabel) component;
                    assertThat(label.getText())
                            .contains("1 stock gap", "Dahlia Mix short 8 stems");
                    assertThat(label.getAccessibleContext().getAccessibleDescription())
                            .contains("block order completion", "Dahlia Mix",
                                    "rebuild this packing list");
                });
        assertThat(button(root[0], "Resolve stock shortfalls").isEnabled()).isTrue();
        assertThat(button(root[0], "Next unpacked").isEnabled()).isFalse();

        SwingUtilities.invokeAndWait(() -> button(root[0], "Open order #1").doClick());
        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderId).isEqualTo(1L);
        assertThat(host.orderReturnTab).isEqualTo("Market Day");
        assertThat(host.orderReturnLabel).isEqualTo("Market Day");

        SwingUtilities.invokeAndWait(() -> button(root[0], "Review draft orders").doClick());
        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderStatus).isEqualTo("DRAFT");
        assertThat(host.orderStatusReturnTab).isEqualTo("Market Day");
        assertThat(host.orderStatusReturnLabel).isEqualTo("Market Day");

        SwingUtilities.invokeAndWait(() -> tab.showFrom("Dashboard", "Dashboard"));
        assertThat(findNamed(root[0], "marketDay.handoffBanner"))
                .singleElement().satisfies(component -> assertThat(component.isVisible()).isTrue());
        assertThat(((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).isSelected())
                .isFalse();
        JButton returnButton = button(root[0], "Return to Dashboard");
        assertThat(returnButton.getAccessibleContext().getAccessibleName())
                .contains("Dashboard", "Market Day packing");
        SwingUtilities.invokeAndWait(returnButton::doClick);
        assertThat(host.selectedTab).isEqualTo("Dashboard");
        assertThat(host.status).isEqualTo("Returned to Dashboard from Market Day.");
    }

    @Test
    @DisplayName("market day resets only affected packed loads when stock readiness changes")
    void marketDayTargetsPackingBlocksAndPreservesUnaffectedLoads() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack dahliaOrder =
                new MarketDayPackingService.CustomerPack(
                        1L, "Kitsap Blooms", "FLORIST", "Sam", "360-555-0142", "",
                        "CONFIRMED", day, "Keep upright", List.of(
                        new MarketDayPackingService.PackLine(
                                "Dahlia Mix", "stems", 8, 3.5, 28)), 28, "DELIVERY");
        MarketDayPackingService.CustomerPack tulipOrder =
                new MarketDayPackingService.CustomerPack(
                        2L, "Harbor Market", "WHOLESALE", "Lee", "360-555-0150", "",
                        "CONFIRMED", day, "Front table", List.of(
                        new MarketDayPackingService.PackLine(
                                "Tulip", "stems", 5, 2, 10)), 10, "MARKET");
        MarketDayPackingService.ProductNeed dahliaCovered =
                new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "stems", 8, 10, false, 0);
        MarketDayPackingService.ProductNeed tulipCovered =
                new MarketDayPackingService.ProductNeed(
                        "Tulip", "stems", 5, 12, false, 0);
        MarketDayPackingService.ProductNeed dahliaGap =
                new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "stems", 8, 3, true, 5);
        MarketDayPackingService.MarketDayPlan covered =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 2, 38,
                        List.of(dahliaOrder, tulipOrder),
                        List.of(dahliaCovered, tulipCovered), 0, "Covered packing list");
        MarketDayPackingService.MarketDayPlan changed =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 2, 38,
                        List.of(dahliaOrder, tulipOrder),
                        List.of(dahliaGap, tulipCovered), 1, "Changed packing list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(covered, changed);
        MarketDayTab tab = new MarketDayTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).doClick();
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.2").get(0)).doClick();
            assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isTrue();
            tab.refreshData();
        });
        awaitMarketPlan(root[0]);

        JCheckBox affected = (JCheckBox) findNamed(
                root[0], "marketDay.packOrder.1").get(0);
        JCheckBox unaffected = (JCheckBox) findNamed(
                root[0], "marketDay.packOrder.2").get(0);
        assertThat(affected.isSelected()).isFalse();
        assertThat(affected.isEnabled()).isFalse();
        assertThat(affected.getAccessibleContext().getAccessibleDescription())
                .contains("cannot be marked packed", "Dahlia Mix", "short 5 stems");
        assertThat(unaffected.isSelected()).isTrue();
        assertThat(unaffected.isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(
                root[0], "marketDay.packReadiness.1").get(0)).getText())
                .contains("Blocked", "1 product issue");
        assertThat(((JLabel) findNamed(
                root[0], "marketDay.packReadiness.2").get(0)).getText())
                .contains("Ready to pack", "checks clear");
        assertThat(((JLabel) findNamed(
                root[0], "marketDay.packingProgress").get(0)).getText())
                .contains("1 packed", "0 ready remaining", "1 blocked");
        assertThat(((JLabel) findNamed(
                root[0], "marketDay.checklistRefresh").get(0)).getText())
                .contains("1 kept", "1 changed", "reset");
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("market day scope changes lock the previous plan until it is rebuilt")
    void marketDayChangedScopeCannotPackExportOrCompletePreviousPlan() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenAnswer(invocation -> {
                    LocalDate requested = invocation.getArgument(0);
                    MarketDayPackingService.CustomerPack customer =
                            new MarketDayPackingService.CustomerPack(
                                    1L, "Kitsap Blooms", "FLORIST", "Sam Rivera",
                                    "360-555-0142", "sam@kitsap.example", "CONFIRMED",
                                    requested, "Keep upright", List.of(
                                            new MarketDayPackingService.PackLine(
                                                    "Dahlia Mix", "stems", 12, 3.5, 42)),
                                    42, "DELIVERY");
                    return new MarketDayPackingService.MarketDayPlan(
                            requested, requested, requested, "CONFIRMED", 1, 42,
                            List.of(customer), List.of(), 0, List.of(), 0,
                            "Packing list");
                });
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).doClick();
            assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isTrue();
            assertThat(button(root[0], "Export file…").isEnabled()).isTrue();
            textField(root[0], "Market or delivery date")
                    .setText(LocalDate.now().plusDays(1).toString());
        });

        JCheckBox staleCheck = (JCheckBox) findNamed(
                root[0], "marketDay.packOrder.1").get(0);
        assertThat(staleCheck.isSelected()).isTrue();
        assertThat(staleCheck.isEnabled()).isFalse();
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();
        assertThat(button(root[0], "Export file…").isEnabled()).isFalse();
        assertThat(button(root[0], "Next unpacked").isEnabled()).isFalse();
        assertThat(((JLabel) findNamed(root[0], "marketDay.planStatus").get(0)).getText())
                .contains("Packing scope changed", "rebuild required");
        assertThat(((JLabel) findNamed(root[0], "marketDay.fulfillmentStatus").get(0))
                .getAccessibleContext().getAccessibleDescription())
                .contains("Scope changed", "Build the packing list again");

        JTextField date = textField(root[0], "Market or delivery date");
        SwingUtilities.invokeAndWait(() -> {
            date.select(0, 0);
            date.setText("Saturday");
            date.postActionEvent();
        });
        assertThat(date.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(button(root[0], "Build packing list").isEnabled()).isFalse();
        assertThat(((JLabel) findNamed(root[0], "marketDay.planStatus").get(0)).getText())
                .contains("Market date invalid", "correct and rebuild");
        verify(service, times(1)).buildPlan(
                any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean());

        LocalDate corrected = LocalDate.now().plusDays(2);
        SwingUtilities.invokeAndWait(() -> {
            date.select(0, 0);
            date.setText(corrected.toString());
            assertThat(button(root[0], "Build packing list").isEnabled()).isTrue();
            button(root[0], "Build packing list").doClick();
        });
        awaitMarketPlan(root[0]);

        assertThat(date.getClientProperty("JComponent.outline")).isNull();
        assertThat(((JCheckBox) findNamed(
                root[0], "marketDay.packOrder.1").get(0)).isEnabled()).isTrue();
        assertThat(((JCheckBox) findNamed(
                root[0], "marketDay.packOrder.1").get(0)).isSelected()).isFalse();
        assertThat(button(root[0], "Export file…").isEnabled()).isTrue();
        verify(service, times(2)).buildPlan(
                any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("market day exposes a collision-free keyboard path through packing")
    void marketDayPackingActionsHaveUniqueMnemonics() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 0, 0,
                        List.of(), List.of(), 0, "No orders"));
        MarketDayTab tab = new MarketDayTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);

        Map<String, Integer> mnemonics = Map.ofEntries(
                Map.entry("Build packing list", button(root[0], "Build packing list").getMnemonic()),
                Map.entry("Today", button(root[0], "Today").getMnemonic()),
                Map.entry("Next Saturday", button(root[0], "Next Saturday").getMnemonic()),
                Map.entry("Export file", button(root[0], "Export file…").getMnemonic()),
                Map.entry("Review drafts", button(root[0], "Review draft orders").getMnemonic()),
                Map.entry("Resolve stock", button(root[0], "Resolve stock shortfalls").getMnemonic()),
                Map.entry("Complete orders", button(root[0], "Complete confirmed orders…").getMnemonic()),
                Map.entry("Next unpacked", button(root[0], "Next unpacked").getMnemonic()),
                Map.entry("Return", button(root[0], "Return to previous workflow").getMnemonic()),
                Map.entry("Draft scope", ((JCheckBox) find(root[0], JCheckBox.class).stream()
                        .filter(box -> "Include unconfirmed drafts".equals(box.getText()))
                        .findFirst().orElseThrow()).getMnemonic()),
                Map.entry("Completed scope", ((JCheckBox) find(root[0], JCheckBox.class).stream()
                        .filter(box -> "Include completed orders".equals(box.getText()))
                        .findFirst().orElseThrow()).getMnemonic()));

        assertThat(mnemonics).containsEntry("Build packing list", java.awt.event.KeyEvent.VK_B)
                .containsEntry("Next unpacked", java.awt.event.KeyEvent.VK_N)
                .containsEntry("Complete orders", java.awt.event.KeyEvent.VK_C)
                .containsEntry("Draft scope", java.awt.event.KeyEvent.VK_U)
                .containsEntry("Completed scope", java.awt.event.KeyEvent.VK_O);
        assertThat(mnemonics.values()).doesNotHaveDuplicates().doesNotContain(0);
        assertThat(find(root[0], JLabel.class).stream()
                .filter(label -> "Market date:".equals(label.getText()))
                .findFirst().orElseThrow().getDisplayedMnemonic())
                .isEqualTo(java.awt.event.KeyEvent.VK_D);
        assertThat(find(root[0], JLabel.class).stream()
                .filter(label -> "Window days:".equals(label.getText()))
                .findFirst().orElseThrow().getDisplayedMnemonic())
                .isEqualTo(java.awt.event.KeyEvent.VK_W);
    }

    @Test
    @DisplayName("market day keeps every multi-product load detail visible in its scrollable queue")
    void marketDayMultiProductCardGrowsInsteadOfClippingDetails() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        List<MarketDayPackingService.PackLine> products = List.of(
                new MarketDayPackingService.PackLine("Dahlia Mix", "stems", 12, 3.5, 42),
                new MarketDayPackingService.PackLine("Cafe au Lait", "stems", 8, 4, 32),
                new MarketDayPackingService.PackLine("Nootka Rose", "stems", 10, 2.5, 25),
                new MarketDayPackingService.PackLine("Cosmos", "bunches", 3, 12, 36),
                new MarketDayPackingService.PackLine("Snapdragon", "stems", 15, 2, 30),
                new MarketDayPackingService.PackLine("Sunflower", "stems", 6, 3, 18));
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        88L, "Peninsula Floral Studio", "FLORIST", "Sam Rivera",
                        "360-555-0142", "sam@peninsula.example", "CONFIRMED", day,
                        "Keep every bucket upright and call the loading-dock contact on arrival.",
                        products, 183, "DELIVERY");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 183,
                        List.of(customer), List.of(), 0, "Packing list"));
        MarketDayTab tab = new MarketDayTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);

        JComponent card = (JComponent) findNamed(
                root[0], "marketDay.packCard.88").get(0);
        JLabel details = (JLabel) findNamed(
                root[0], "marketDay.packDetails.88").get(0);
        assertThat(card.getPreferredSize().height).isGreaterThan(118);
        assertThat(card.getMaximumSize().height)
                .isGreaterThanOrEqualTo(card.getPreferredSize().height);
        assertThat(card.getMaximumSize().width).isEqualTo(Integer.MAX_VALUE);
        assertThat(details.getText()).contains(
                "Products:", "12 stems Dahlia Mix", "6 stems Sunflower", "<br>",
                "Sam Rivera", "Keep every bucket upright");
        assertThat(details.getAccessibleContext().getAccessibleDescription()).contains(
                "Order 88 products", "Dahlia Mix", "Sunflower", "Sam Rivera",
                "loading-dock contact");
        java.awt.BorderLayout layout = (java.awt.BorderLayout) card.getLayout();
        assertThat(SwingUtilities.isDescendingFrom(
                button(root[0], "Open order #88"),
                layout.getLayoutComponent(java.awt.BorderLayout.SOUTH))).isTrue();
    }

    @Test
    @DisplayName("market day blocks and routes a context-free confirmed Delivery")
    void marketDayRoutesMissingDeliveryContextToExactOrder() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack delivery =
                new MarketDayPackingService.CustomerPack(
                        8L, "Legacy Florist", "FLORIST", "", "", "", "CONFIRMED",
                        day, "", List.of(new MarketDayPackingService.PackLine(
                                "Dahlia Mix", "stems", 4, 3.5, 14)), 14, "DELIVERY");
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 14,
                        List.of(delivery), List.of(), 0, "Packing list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);

        JCheckBox packed = (JCheckBox) findNamed(
                root[0], "marketDay.packOrder.8").get(0);
        assertThat(packed.isEnabled()).isFalse();
        assertThat(packed.getAccessibleContext().getAccessibleDescription()).contains(
                "Delivery with no contact", "account delivery note", "order instruction");
        assertThat(((JLabel) findNamed(
                root[0], "marketDay.packReadiness.8").get(0)).getText())
                .contains("Blocked", "delivery context missing");
        assertThat(((JLabel) findNamed(
                root[0], "marketDay.shortfallSummary").get(0)).getText())
                .contains("1 Delivery context issue", "completion locked");
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();

        SwingUtilities.invokeAndWait(() -> button(root[0], "Add delivery context #8").doClick());
        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderId).isEqualTo(8L);
        assertThat(host.orderReturnTab).isEqualTo("Market Day");
    }

    @Test
    @DisplayName("market day makes unspecified handoffs actionable and blocks completion")
    void marketDayRequiresExactHandoffBeforeLoadCompletion() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack unresolved =
                new MarketDayPackingService.CustomerPack(
                        9L, "Legacy Buyer", "WHOLESALE", "Sam", "360-555-0199", "",
                        "CONFIRMED", day, "Call first", List.of(
                        new MarketDayPackingService.PackLine(
                                "Dahlia Mix", "stems", 4, 3.5, 14)), 14, "UNSPECIFIED");
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 14,
                        List.of(unresolved), List.of(), 0, "Packing list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            JCheckBox packed = (JCheckBox) findNamed(
                    root[0], "marketDay.packOrder.9").get(0);
            packed.doClick();
            button(root[0], "Review handoff #9").doClick();
        });

        assertThat(findNamed(root[0], "marketDay.handoffGroup.UNSPECIFIED"))
                .singleElement()
                .satisfies(component -> {
                    JLabel label = (JLabel) component;
                    assertThat(label.getText()).contains("Needs handoff review", "1 load");
                    assertThat(label.getAccessibleContext().getAccessibleName())
                            .contains("Needs handoff review", "1 customer load");
                });
        assertThat(((JCheckBox) findNamed(root[0], "marketDay.packOrder.9").get(0)).getText())
                .contains("Unspecified handoff", day.toString());
        assertThat(((JCheckBox) findNamed(root[0], "marketDay.packOrder.9").get(0)).isEnabled())
                .isFalse();
        assertThat(((JLabel) findNamed(
                root[0], "marketDay.packReadiness.9").get(0)).getText())
                .contains("Blocked", "handoff needs review");
        assertThat(((JLabel) findNamed(root[0], "marketDay.shortfallSummary").get(0)).getText())
                .contains("1 unspecified handoff", "completion locked");
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();
        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderId).isEqualTo(9L);
        assertThat(host.orderReturnTab).isEqualTo("Market Day");
    }

    @Test
    @DisplayName("market-day completion reviews only exact checked confirmed loads and value")
    void marketDayCompletionReviewExcludesReferenceOrders() {
        LocalDate day = LocalDate.of(2026, 9, 12);
        MarketDayPackingService.CustomerPack confirmed =
                new MarketDayPackingService.CustomerPack(
                        52L, "Kitsap Blooms", "FLORIST",
                        "Sam Rivera", "360-555-0142", "sam@kitsap.test",
                        "Deliver to the west loading dock", "CONFIRMED",
                        day, "Keep upright", List.of(
                                new MarketDayPackingService.PackLine(
                                        "Dahlia Mix", "stems", 12, 3.5, 42),
                                new MarketDayPackingService.PackLine(
                                        "Rose Bunch", "bunches", 2, 18, 36)), 78, "DELIVERY");
        MarketDayPackingService.CustomerPack draft =
                new MarketDayPackingService.CustomerPack(
                        53L, "Preview Farm", "WHOLESALE", "DRAFT", day, "Preview only",
                        List.of(new MarketDayPackingService.PackLine(
                                "Future Crop", "stems", 100, 9.99, 999)), 999);
        MarketDayPackingService.CustomerPack fulfilled =
                new MarketDayPackingService.CustomerPack(
                        54L, "Past Customer", "MARKET", "FULFILLED", day, "Already left",
                        List.of(new MarketDayPackingService.PackLine(
                                "Past Crop", "stems", 20, 10, 200)), 200);
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day.minusDays(6), day, "CONFIRMED + DRAFT + FULFILLED",
                        3, 1_277, List.of(confirmed, draft, fulfilled), List.of(), 0,
                        "Packing list");

        MarketDayTab.MarketDayCompletionReview review =
                MarketDayTab.marketDayCompletionReview(plan);
        assertThat(review.confirmedCount()).isEqualTo(1);
        assertThat(review.confirmedValue()).isEqualTo(78);
        assertThat(review.heading()).contains("1 checked confirmed order", "$78.00");
        assertThat(review.details()).contains(
                "Market window: 2026-09-06 → 2026-09-12",
                "Only the confirmed orders below will be completed",
                "Draft and already-completed reference orders are excluded",
                "1. Order #52 · Kitsap Blooms · Delivery · 2026-09-12",
                "Contact: Sam Rivera · 360-555-0142 · sam@kitsap.test",
                "Account delivery note: Deliver to the west loading dock",
                "1) 12 stems Dahlia Mix · $3.50 each · $42.00",
                "2) 2 bunches Rose Bunch · $18.00 each · $36.00",
                "Instructions: Keep upright",
                "Total: 1 confirmed order · 2 products · $78.00",
                "Completing deducts matching inventory");
        assertThat(review.details()).doesNotContain(
                "Order #53", "Order #54", "$999.00", "$200.00", "$1,277.00");

        JComponent panel = MarketDayTab.marketDayCompletionPanel(review);
        assertThat(findNamed(panel, "marketDay.completionReview")).hasSize(1);
        assertThat(findNamed(panel, "marketDay.completionScroll"))
                .singleElement().isInstanceOf(javax.swing.JScrollPane.class)
                .satisfies(component -> {
                    javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) component;
                    assertThat(scroll.getPreferredSize().height).isEqualTo(300);
                    assertThat(scroll.getMinimumSize().width).isEqualTo(340);
                });
        assertThat(findNamed(panel, "marketDay.completionDetails"))
                .singleElement().isInstanceOf(JTextArea.class)
                .satisfies(component -> {
                    JTextArea details = (JTextArea) component;
                    assertThat(details.isEditable()).isFalse();
                    assertThat(details.getLineWrap()).isTrue();
                    assertThat(details.getText()).isEqualTo(review.details());
                    assertThat(details.getAccessibleContext().getAccessibleName())
                            .contains("Checked customer loads", "completion consequences");
                    assertThat(details.getAccessibleContext().getAccessibleDescription())
                            .contains("Account delivery note: Deliver to the west loading dock");
                });
    }

    @Test
    @DisplayName("draft preview stock gaps stay visible without locking confirmed completion")
    void marketDayDraftPreviewGapDoesNotBlockCoveredConfirmedLoad() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack confirmed =
                new MarketDayPackingService.CustomerPack(
                        71L, "Kitsap Blooms", "FLORIST", "", "", "", "CONFIRMED", day, "Load first",
                        List.of(new MarketDayPackingService.PackLine(
                                "Tulip", "stems", 5, 2, 10)), 10, "DELIVERY");
        MarketDayPackingService.CustomerPack draft =
                new MarketDayPackingService.CustomerPack(
                        72L, "Preview Farm", "WHOLESALE", "DRAFT", day, "Preview only",
                        List.of(new MarketDayPackingService.PackLine(
                                "Tulip", "stems", 10, 2, 20)), 20);
        MarketDayPackingService.ProductNeed planningGap =
                new MarketDayPackingService.ProductNeed(
                        "Tulip", "stems", 15, 6, true, 9);
        MarketDayPackingService.ProductNeed confirmedCovered =
                new MarketDayPackingService.ProductNeed(
                        "Tulip", "stems", 5, 6, false, 0);
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED+DRAFT", 2, 30,
                        List.of(confirmed, draft), List.of(planningGap), 1,
                        List.of(confirmedCovered), 0, "Preview packing list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> ((JCheckBox) findNamed(
                root[0], "marketDay.packOrder.71").get(0)).doClick());

        JLabel stock = (JLabel) findNamed(root[0], "marketDay.shortfallSummary").get(0);
        JLabel completion = (JLabel) findNamed(root[0], "marketDay.fulfillmentStatus").get(0);
        JLabel summary = (JLabel) findNamed(root[0], "marketDay.planStatus").get(0);
        assertThat(stock.getText()).contains(
                "Draft preview", "1 stock gap", "Tulip short 9 stems",
                "completion not blocked");
        assertThat(stock.getAccessibleContext().getAccessibleDescription()).contains(
                "preview-only issues do not block confirmed completion", "Tulip");
        assertThat(completion.getText()).contains("1 checked", "ready to complete");
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isTrue();
        assertThat(summary.getText()).contains(
                "2", "$30.00", "Draft preview issue(s)", "completion available");
        assertThat(host.status).contains(
                "2 visible order(s)", "$30.00 active pipeline",
                "1 confirmed", "$10.00 ready to complete");
    }

    @Test
    @DisplayName("market day routes an empty confirmed order back for product correction")
    void marketDayEmptyConfirmedOrderBlocksPackingAndOpensExactOrder() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 0,
                        List.of(new MarketDayPackingService.CustomerPack(
                                77L, "Kitsap Blooms", "FLORIST", "", "", "", "CONFIRMED",
                                day, "Delivery at noon", List.of(), 0, "DELIVERY")),
                        List.of(), 0, "Packing list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);

        SwingUtilities.invokeAndWait(() -> {
            JCheckBox packed = (JCheckBox) findNamed(
                    root[0], "marketDay.packOrder.77").get(0);
            assertThat(packed.isEnabled()).isFalse();
            assertThat(packed.getToolTipText())
                    .contains("no saved products", "Open the order", "rebuild");
            assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();
            JLabel completion = (JLabel) findNamed(
                    root[0], "marketDay.fulfillmentStatus").get(0);
            assertThat(completion.getText())
                    .contains("1 packing issue", "completion locked");
            JLabel issue = (JLabel) findNamed(
                    root[0], "marketDay.shortfallSummary").get(0);
            assertThat(issue.getText())
                    .contains("1 empty confirmed order", "completion locked");
            assertThat(issue.getAccessibleContext().getAccessibleDescription())
                    .contains("order #77", "add at least one product", "rebuild");
            button(root[0], "Open empty order #77").doClick();
            tab.startFulfillment(plan);
        });

        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderId).isEqualTo(77L);
        assertThat(host.orderReturnTab).isEqualTo("Market Day");
        assertThat(host.orderReturnLabel).isEqualTo("Market Day");
        verify(service, never()).fulfillConfirmedOrders(any());
    }

    @Test
    @DisplayName("market-day completion rejects a shortfall snapshot even when invoked directly")
    void marketDayCompletionCannotBypassStockGate() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 42,
                        List.of(new MarketDayPackingService.CustomerPack(
                                8L, "Harbor Florist", "FLORIST", "", "", "", "CONFIRMED",
                                day, "", List.of(new MarketDayPackingService.PackLine(
                                "Tulips", "stems", 12, 3.5, 42)), 42, "PICKUP")),
                        List.of(new MarketDayPackingService.ProductNeed(
                                "Tulips", "stems", 12, 4, true, 8)),
                        1, "Short packing list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> tab.startFulfillment(plan));

        JLabel status = (JLabel) findNamed(
                root[0], "marketDay.fulfillmentStatus").get(0);
        assertThat(status.getText()).isEqualTo("1 stock gap · completion locked");
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("Resolve the listed products in Inventory",
                        "rebuild the packing list", "before completing orders");
        assertThat(host.status)
                .contains("completion blocked by 1 stock gap", "Resolve stock", "rebuild");
        verify(service, never()).fulfillConfirmedOrders(any());
    }

    @Test
    @DisplayName("market day names and blocks an order-to-inventory unit mismatch")
    void marketDayUnitMismatchIsActionable() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        1L, "Kitsap Blooms", "FLORIST", "", "", "", "CONFIRMED", day, "",
                        List.of(new MarketDayPackingService.PackLine(
                                "Dahlia Mix", "bunches", 4, 12, 48, 101L)), 48, "MARKET");
        MarketDayPackingService.ProductNeed mismatch =
                new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "bunches", 4, 20, false, 0,
                        true, "stems", false);
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 48,
                        List.of(customer), List.of(mismatch), 1, "Unit mismatch list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);

        JLabel issue = (JLabel) findNamed(root[0], "marketDay.shortfallSummary").get(0);
        assertThat(issue.getText())
                .contains("1 packing issue", "Dahlia Mix unit mismatch",
                        "order bunches", "inventory stems");
        assertThat(issue.getAccessibleContext().getAccessibleDescription())
                .contains("Dahlia Mix on order #1", "Open that exact order",
                        "Correct the order if its unit is wrong",
                        "if the catalog unit is wrong", "fresh Market Day snapshot",
                        "status to say it is current");
        JButton reviewUnit = button(root[0], "Review unit #1");
        assertThat(reviewUnit.isEnabled()).isTrue();
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();

        SwingUtilities.invokeAndWait(reviewUnit::doClick);

        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderId).isEqualTo(1L);
        assertThat(host.orderLineId).isEqualTo(101L);
        assertThat(host.orderLineProduct).isEqualTo("Dahlia Mix");
        assertThat(host.orderLineCorrection).isTrue();
        assertThat(host.orderReturnTab).isEqualTo("Market Day");
        assertThat(host.orderReturnLabel).isEqualTo("Market Day");
        assertThat(host.inventoryProducts).isEmpty();
    }

    @Test
    @DisplayName("market day opens the exact confirmed order with an invalid rounded quantity")
    void marketDayInvalidQuantityOpensExactConfirmedOrder() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        42L, "Harbor Florist", "FLORIST", "", "", "", "CONFIRMED", day, "",
                        List.of(new MarketDayPackingService.PackLine(
                                "Cosmos", "stems", 0.4, 3, 1.2, 420L)), 1.2, "PICKUP");
        MarketDayPackingService.ProductNeed invalidQuantity =
                new MarketDayPackingService.ProductNeed(
                        "Cosmos", "stems", 0, 20, false, 0,
                        false, "stems", true);
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 1.2,
                        List.of(customer), List.of(invalidQuantity), 0,
                        "Invalid quantity list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);

        JLabel issue = (JLabel) findNamed(root[0], "marketDay.shortfallSummary").get(0);
        assertThat(issue.getText()).contains(
                "1 packing issue", "Cosmos quantity must round to at least 1 inventory unit");
        assertThat(issue.getAccessibleContext().getAccessibleDescription())
                .contains("Cosmos on order #42", "below one rounded inventory unit",
                        "saved product selected", "correct its quantity",
                        "fresh Market Day snapshot", "status to say it is current");
        JButton correctQuantity = button(root[0], "Correct quantity #42");
        assertThat(correctQuantity.isEnabled()).isTrue();
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();

        SwingUtilities.invokeAndWait(correctQuantity::doClick);

        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderId).isEqualTo(42L);
        assertThat(host.orderLineId).isEqualTo(420L);
        assertThat(host.orderLineProduct).isEqualTo("Cosmos");
        assertThat(host.orderLineCorrection).isTrue();
        assertThat(host.orderReturnTab).isEqualTo("Market Day");
        assertThat(host.orderReturnLabel).isEqualTo("Market Day");
        assertThat(host.inventoryProducts).isEmpty();
    }

    @Test
    @DisplayName("market day routes a preview-only invalid quantity to its exact draft")
    void marketDayPreviewInvalidQuantityOpensExactDraftWithoutBlockingCompletion()
            throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack confirmed =
                new MarketDayPackingService.CustomerPack(
                        51L, "Kitsap Blooms", "FLORIST", "", "", "", "CONFIRMED", day,
                        "Deliver to the side entrance",
                        List.of(new MarketDayPackingService.PackLine(
                                "Tulip", "stems", 5, 2, 10)), 10, "DELIVERY");
        MarketDayPackingService.CustomerPack draft =
                new MarketDayPackingService.CustomerPack(
                        52L, "Preview Farm", "WHOLESALE", "DRAFT", day, "",
                        List.of(new MarketDayPackingService.PackLine(
                                "Cosmos", "stems", 0.4, 3, 1.2, 520L)), 1.2);
        MarketDayPackingService.ProductNeed draftIssue =
                new MarketDayPackingService.ProductNeed(
                        "Cosmos", "stems", 0, 20, false, 0,
                        false, "stems", true);
        MarketDayPackingService.ProductNeed confirmedCovered =
                new MarketDayPackingService.ProductNeed(
                        "Tulip", "stems", 5, 12, false, 0);
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED+DRAFT", 2, 11.2,
                        List.of(confirmed, draft), List.of(draftIssue), 0,
                        List.of(confirmedCovered), 0, "Preview invalid quantity list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> ((JCheckBox) findNamed(
                root[0], "marketDay.packOrder.51").get(0)).doClick());

        JLabel issue = (JLabel) findNamed(root[0], "marketDay.shortfallSummary").get(0);
        assertThat(issue.getText()).contains(
                "Draft preview", "1 packing issue", "completion not blocked");
        assertThat(issue.getAccessibleContext().getAccessibleDescription())
                .contains("Cosmos on order #52", "Draft preview issue",
                        "does not block confirmed completion", "fresh Market Day snapshot",
                        "status to say it is current");
        JButton correctQuantity = button(root[0], "Correct quantity #52");
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isTrue();

        SwingUtilities.invokeAndWait(correctQuantity::doClick);

        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderId).isEqualTo(52L);
        assertThat(host.orderLineId).isEqualTo(520L);
        assertThat(host.orderLineProduct).isEqualTo("Cosmos");
        assertThat(host.orderLineCorrection).isTrue();
        assertThat(host.orderReturnTab).isEqualTo("Market Day");
        assertThat(host.orderReturnLabel).isEqualTo("Market Day");
        assertThat(host.inventoryProducts).isEmpty();
    }

    @Test
    @DisplayName("market-day plan refresh runs off the UI thread and coalesces a newer snapshot")
    void marketDayRefreshIsResponsiveAndDiscardsStaleResult() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan oldPlan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 25,
                        List.of(), List.of(), 0, "Old packing list");
        MarketDayPackingService.MarketDayPlan currentPlan =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 25,
                        List.of(), List.of(), 0, "Current packing list");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenAnswer(invocation -> {
                    calledOnEdt.set(SwingUtilities.isEventDispatchThread());
                    if (calls.incrementAndGet() == 1) {
                        entered.countDown();
                        assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                        return oldPlan;
                    }
                    return currentPlan;
                });
        MarketDayTab tab = new MarketDayTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Build packing list").isEnabled()).isFalse();
            assertThat(button(root[0], "Export file…").isEnabled()).isFalse();
            tab.startPlanRefresh();
            assertThat(((JLabel) findNamed(root[0], "marketDay.planStatus").get(0)).getText())
                    .startsWith("Refresh queued");
        });

        release.countDown();
        awaitMarketPlan(root[0]);

        assertThat(calledOnEdt).isFalse();
        assertThat(calls).hasValue(2);
        assertThat(textArea(root[0], "Market day packing list").getText())
                .isEqualTo("Current packing list");
        assertThat(button(root[0], "Build packing list").isEnabled()).isTrue();
        assertThat(button(root[0], "Export file…").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("market-day refresh failure preserves the reviewed packing checklist")
    void marketDayRefreshFailurePreservesWorkingSnapshot() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan plan = confirmedMarketPlan(day);
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan)
                .thenThrow(new IllegalStateException("packing database unavailable"));
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).doClick();
            tab.startPlanRefresh();
        });
        awaitMarketPlan(root[0]);

        JCheckBox packed = (JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0);
        JLabel status = (JLabel) findNamed(root[0], "marketDay.planStatus").get(0);
        assertThat(packed.isSelected()).isTrue();
        assertThat(packed.isEnabled()).isTrue();
        assertThat(textArea(root[0], "Market day packing list").getText())
                .isEqualTo(plan.plainText());
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();
        assertThat(button(root[0], "Export file…").isEnabled()).isTrue();
        assertThat(status.getText()).contains("previous plan shown");
        assertThat(((JLabel) findNamed(root[0], "marketDay.fulfillmentStatus").get(0)).getText())
                .isEqualTo("Refresh failed · previous plan shown · completion locked");
        assertThat(button(root[0], "Complete confirmed orders…").getToolTipText())
                .contains("Rebuild it successfully", "deducting inventory");
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("packing database unavailable", "Existing plan", "retry");
        assertThat(host.status).contains("failed", "Existing packing work was kept", "retry");
    }

    @Test
    @DisplayName("market-day CSV export uses one captured plan off the UI thread")
    void marketDayExportRunsInBackgroundOnce() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan plan = confirmedMarketPlan(
                day, 1L, "Deliver to west loading dock");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.exportCsv(plan)).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return "section,orderId,customer\nORDER,1,Kitsap Blooms\n";
        });
        MarketDayTab tab = new MarketDayTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        Path directory = Files.createTempDirectory("market-day-export");
        Path destination = directory.resolve("packing.csv");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() ->
                tab.startMarketDayExport("Spreadsheet CSV", destination.toFile(), plan));
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Export file…").isEnabled()).isFalse();
            assertThat(((JComboBox<?>) findNamed(
                    root[0], "marketDay.exportType").get(0)).isEnabled()).isFalse();
            assertThat(((JCheckBox) findNamed(
                    root[0], "marketDay.packOrder.1").get(0)).isEnabled()).isTrue();
            assertThat(tab.applicationExitWarning()).contains(
                    "a packing export still writing", "interrupt", "file write");
            tab.startMarketDayExport("Spreadsheet CSV", destination.toFile(), plan);
        });

        assertThat(calledOnEdt).isFalse();
        release.countDown();
        awaitLabel(root[0], "CSV saved · packing.csv");

        assertThat(Files.readString(destination))
                .isEqualTo("section,orderId,customer\nORDER,1,Kitsap Blooms\n");
        assertThat(button(root[0], "Export file…").isEnabled()).isTrue();
        assertThat(tab.applicationExitWarning()).isNull();
        verify(service).exportCsv(argThat(snapshot -> snapshot != plan
                && snapshot.customers().size() == 1
                && "Deliver to west loading dock"
                        .equals(snapshot.customers().get(0).accountNotes())
                && "PICKUP".equals(snapshot.customers().get(0).handoffType())));
        Files.deleteIfExists(destination);
        Files.deleteIfExists(directory);
    }

    @Test
    @DisplayName("failed market-day export preserves its destination and packing checks")
    void marketDayExportFailureKeepsWorkingState() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan plan = confirmedMarketPlan(day);
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        when(service.generatePackingPdf(plan))
                .thenThrow(new IllegalStateException("PDF renderer unavailable"));
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];
        Path destination = Files.createTempFile("existing-market-day", ".pdf");
        Files.writeString(destination, "existing packing handoff");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).doClick();
            tab.startMarketDayExport("Packing PDF", destination.toFile(), plan);
        });
        awaitLabel(root[0], "Export failed · retry");

        JLabel status = (JLabel) findNamed(root[0], "marketDay.exportStatus").get(0);
        JCheckBox packed = (JCheckBox) findNamed(
                root[0], "marketDay.packOrder.1").get(0);
        assertThat(Files.readString(destination)).isEqualTo("existing packing handoff");
        assertThat(packed.isSelected()).isTrue();
        assertThat(packed.isEnabled()).isTrue();
        assertThat(button(root[0], "Export file…").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("PDF renderer unavailable", "packing list", "packed checks", "kept");
        assertThat(host.status)
                .contains("PDF renderer unavailable", "Packing work was kept", "retry");
        Files.deleteIfExists(destination);
    }

    @Test
    @DisplayName("market-day completion refresh verifies exact fulfilled orders, not counts")
    void marketDayCompletionRefreshRequiresExactOrdersToLeaveConfirmedQueue() {
        LocalDate day = LocalDate.now();

        assertThat(MarketDayTab.completedOrdersLeftConfirmedQueue(
                Set.of(1L), confirmedMarketPlan(day, 1L))).isFalse();
        assertThat(MarketDayTab.completedOrdersLeftConfirmedQueue(
                Set.of(1L), confirmedMarketPlan(day, 2L))).isTrue();
        assertThat(MarketDayTab.completedOrdersLeftConfirmedQueue(
                Set.of(), confirmedMarketPlan(day, 2L))).isFalse();
    }

    @Test
    @DisplayName("market-day completion runs off the UI thread and cannot be submitted twice")
    void marketDayCompletionRunsInBackgroundOnce() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan plan = confirmedMarketPlan(day);
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.fulfillConfirmedOrders(plan)).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for market completion");
            }
            return new MarketDayPackingService.FulfillBatchResult(
                    1, 1, 0, 0, List.of("#1 → FULFILLED"));
        });
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];
        Object[] busy = new Object[7];
        String[] busyExitWarning = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        assertThat(tab.applicationExitWarning()).isNull();
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).doClick();
            assertThat(tab.applicationExitWarning()).contains(
                    "1 checked packed load", "Keep working", "packing progress");
            tab.startFulfillment(plan);
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        SwingUtilities.invokeAndWait(() -> {
            tab.applyRolePermissions(true);
            JButton submit = (JButton) findNamed(
                    root[0], "marketDay.fulfillConfirmed").get(0);
            busy[0] = submit.getText();
            busy[1] = submit.isEnabled();
            busy[2] = textField(root[0], "Market or delivery date").isEnabled();
            busy[3] = button(root[0], "Build packing list").isEnabled();
            busy[4] = ((JCheckBox) findNamed(
                    root[0], "marketDay.packOrder.1").get(0)).isEnabled();
            busy[5] = ((JLabel) findNamed(
                    root[0], "marketDay.fulfillmentStatus").get(0)).getText();
            busy[6] = host.status;
            tab.startFulfillment(plan);
            busyExitWarning[0] = tab.applicationExitWarning();
        });

        assertThat(busy).containsExactly(
                "Completing orders…", false, false, false, false,
                "Completing 1 order · inventory updating",
                "⏳ Completing 1 market-day order…");
        assertThat(busyExitWarning[0]).contains(
                "1 checked packed load", "completion still running");
        assertThat(calledOnEdt).isFalse();

        release.countDown();
        awaitLabel(root[0], "1 completed once · packing refresh required");
        awaitMarketPlan(root[0]);

        assertThat(textField(root[0], "Market or delivery date").isEnabled()).isTrue();
        assertThat(button(root[0], "Build packing list").isEnabled()).isTrue();
        assertThat(((JCheckBox) findNamed(
                root[0], "marketDay.packOrder.1").get(0)).isSelected()).isTrue();
        JButton complete = button(root[0], "Complete confirmed orders…");
        assertThat(complete.isEnabled()).isFalse();
        assertThat(complete.getToolTipText()).contains(
                "completed once", "inventory was deducted once",
                "Build packing list only", "do not complete this checklist again");
        assertThat(host.status).contains(
                "saved once", "Build the packing list", "do not complete");
        assertThat(tab.applicationExitWarning()).isNull();
        SwingUtilities.invokeAndWait(() -> tab.startFulfillment(plan));
        verify(service).fulfillConfirmedOrders(plan);
    }

    @Test
    @DisplayName("failed market-day completion preserves the reviewed packing checklist")
    void marketDayCompletionFailureKeepsChecklist() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan plan = confirmedMarketPlan(day);
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.fulfillConfirmedOrders(plan)).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            throw new IllegalStateException("Order database is busy");
        });
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).doClick();
            tab.startFulfillment(plan);
        });
        awaitLabel(root[0],
                "Completion failed · Order database is busy · try again");

        JLabel status = (JLabel) findNamed(
                root[0], "marketDay.fulfillmentStatus").get(0);
        JCheckBox packed = (JCheckBox) findNamed(
                root[0], "marketDay.packOrder.1").get(0);
        assertThat(calledOnEdt).isFalse();
        assertThat(packed.isSelected()).isTrue();
        assertThat(packed.isEnabled()).isTrue();
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isTrue();
        assertThat(button(root[0], "Build packing list").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("Packing checks were kept", "Order database is busy",
                        "no completion result was received");
        assertThat(host.status).contains(
                "Completing market-day orders could not be completed",
                "Order database is busy", "Packing checks were kept");
        assertThat(tab.applicationExitWarning()).contains(
                "1 checked packed load", "explicitly discard this packing progress");
        JButton clearChecks = (JButton) findNamed(
                root[0], "marketDay.clearPackedChecks").get(0);
        assertThat(clearChecks.getText()).isEqualTo("Clear checks");
        assertThat(clearChecks.isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(clearChecks::doClick);
        assertThat(clearChecks.getText()).isEqualTo("Discard checks");
        assertThat(packed.isSelected()).isTrue();
        assertThat(tab.applicationExitWarning()).contains("1 checked packed load");
        SwingUtilities.invokeAndWait(clearChecks::doClick);
        assertThat(packed.isSelected()).isFalse();
        assertThat(tab.applicationExitWarning()).isNull();
        assertThat(clearChecks.isEnabled()).isFalse();
        assertThat(host.status).contains(
                "1 unfinished packed-load check cleared", "No order or inventory value changed");
    }

    @Test
    @DisplayName("stale market-day completion directs workers to rebuild before retrying")
    void staleMarketDayCompletionRequiresRebuild() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.MarketDayPlan plan = confirmedMarketPlan(day);
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);
        when(service.fulfillConfirmedOrders(plan)).thenThrow(new IllegalArgumentException(
                "Packing details changed for #1 Kitsap Blooms. Rebuild the packing list and "
                        + "check the updated load before completing orders."));
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).doClick();
            tab.startFulfillment(plan);
        });
        awaitLabel(root[0], "Packing review changed · rebuild required");

        JLabel status = (JLabel) findNamed(
                root[0], "marketDay.fulfillmentStatus").get(0);
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("Packing details changed", "No order was completed",
                        "Build packing list");
        assertThat(button(root[0], "Build packing list").getToolTipText())
                .contains("changed customer loads", "current stock");
        assertThat(((JCheckBox) findNamed(
                root[0], "marketDay.packOrder.1").get(0)).isSelected()).isTrue();
        assertThat(host.status).contains(
                "stopped before any order changed", "Rebuild the packing list");
        verify(service).fulfillConfirmedOrders(plan);
    }

    @Test
    @DisplayName("market-day recovery handoff retains every failed order and exact reason")
    void marketDayCompletionHandoffDoesNotTruncateFailures() {
        MarketDayPackingService.FulfillBatchResult result =
                new MarketDayPackingService.FulfillBatchResult(
                        4, 1, 0, 3, List.of(
                        "#1 Kitsap Blooms → FULFILLED ($80.00)",
                        "#2 Harbor Florist FAILED: only 2 stems available",
                        "#3 Island Market FAILED: inventory unit is bunches, order uses stems",
                        "#4 Bay Farm FAILED: order quantity must round to at least 1 inventory unit"));

        assertThat(MarketDayTab.fulfillmentHandoffText(result)).contains(
                "Completion result · 1 completed · 3 failed · 0 skipped",
                "#1 Kitsap Blooms → FULFILLED ($80.00)",
                "#2 Harbor Florist FAILED: only 2 stems available",
                "#3 Island Market FAILED: inventory unit is bunches, order uses stems",
                "#4 Bay Farm FAILED: order quantity must round to at least 1 inventory unit",
                "completed orders are already saved",
                "Failed confirmed orders remain checked below",
                "complete only the remaining confirmed orders");
    }

    @Test
    @DisplayName("partial market-day completion identifies and retains the retryable order")
    void marketDayPartialCompletionKeepsFailedOrderActionable() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack completed =
                new MarketDayPackingService.CustomerPack(
                        1L, "Kitsap Blooms", "FLORIST", "", "", "", "CONFIRMED", day, "",
                        List.of(new MarketDayPackingService.PackLine(
                                "Dahlia Mix", "bunches", 4, 20, 80)), 80, "MARKET");
        MarketDayPackingService.CustomerPack failed =
                new MarketDayPackingService.CustomerPack(
                        2L, "Harbor Florist", "FLORIST", "", "", "", "CONFIRMED", day,
                        "Deliver to the loading dock",
                        List.of(new MarketDayPackingService.PackLine(
                                "Tulips", "stems", 15, 3, 45)), 45, "DELIVERY");
        MarketDayPackingService.MarketDayPlan initial =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 2, 125,
                        List.of(completed, failed), List.of(), 0, "Initial list");
        MarketDayPackingService.MarketDayPlan remaining =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 45,
                        List.of(failed), List.of(), 0, "Remaining list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(initial, remaining);
        when(service.fulfillConfirmedOrders(initial)).thenReturn(
                new MarketDayPackingService.FulfillBatchResult(
                        2, 1, 0, 1, List.of(
                        "#1 Kitsap Blooms → FULFILLED ($80.00)",
                        "#2 Harbor Florist FAILED: only 2 stems available")));
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.1").get(0)).doClick();
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.2").get(0)).doClick();
            tab.startFulfillment(initial);
        });
        awaitLabel(root[0], "1 completed · 1 failed · retry remaining");
        awaitMarketPlan(root[0]);

        JLabel status = (JLabel) findNamed(
                root[0], "marketDay.fulfillmentStatus").get(0);
        JCheckBox retry = (JCheckBox) findNamed(
                root[0], "marketDay.packOrder.2").get(0);
        assertThat(findNamed(root[0], "marketDay.packOrder.1")).isEmpty();
        assertThat(retry.isSelected()).isTrue();
        assertThat(retry.isEnabled()).isTrue();
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isTrue();
        assertThat(status.getToolTipText()).contains(
                "#2 Harbor Florist FAILED", "only 2 stems available",
                "Retry only orders still confirmed");
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("#2 Harbor Florist FAILED", "only 2 stems available");
        javax.swing.JScrollPane handoff = (javax.swing.JScrollPane) findNamed(
                root[0], "marketDay.fulfillmentResultHandoff").get(0);
        JTextArea handoffDetails = (JTextArea) findNamed(
                root[0], "marketDay.fulfillmentResultDetails").get(0);
        assertThat(handoff.isVisible()).isTrue();
        assertThat(handoff.getPreferredSize().height).isEqualTo(112);
        assertThat(handoffDetails.isEditable()).isFalse();
        assertThat(handoffDetails.getText()).contains(
                "Completion result · 1 completed · 1 failed · 0 skipped",
                "#1 Kitsap Blooms → FULFILLED ($80.00)",
                "#2 Harbor Florist FAILED: only 2 stems available",
                "completed orders are already saved",
                "Failed confirmed orders remain checked below",
                "complete only the remaining confirmed orders");
        assertThat(handoffDetails.getAccessibleContext().getAccessibleDescription())
                .isEqualTo(handoffDetails.getText());
    }

    @Test
    @DisplayName("late Market Day order change stays unfulfilled and requires a fresh packing check")
    void marketDayLateOrderChangeRequiresFreshReview() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack reviewed =
                new MarketDayPackingService.CustomerPack(
                        2L, "Harbor Florist", "FLORIST", "", "", "", "CONFIRMED", day, "Side door",
                        List.of(new MarketDayPackingService.PackLine(
                                "Tulips", "stems", 15, 3, 45)), 45, "DELIVERY");
        MarketDayPackingService.CustomerPack changed =
                new MarketDayPackingService.CustomerPack(
                        2L, "Harbor Florist", "FLORIST", "", "", "", "CONFIRMED", day, "Front desk",
                        List.of(new MarketDayPackingService.PackLine(
                                "Tulips", "stems", 18, 3, 54)), 54, "DELIVERY");
        MarketDayPackingService.MarketDayPlan initial =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 45,
                        List.of(reviewed), List.of(), 0, "Reviewed list");
        MarketDayPackingService.MarketDayPlan current =
                new MarketDayPackingService.MarketDayPlan(
                        day, day, day, "CONFIRMED", 1, 54,
                        List.of(changed), List.of(), 0, "Changed list");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(initial, current);
        when(service.fulfillConfirmedOrders(initial)).thenReturn(
                new MarketDayPackingService.FulfillBatchResult(
                        1, 0, 0, 1, List.of(
                        "#2 Harbor Florist FAILED: Order #2 changed after completion review. "
                                + "No status or inventory was changed. Refresh Customers and Orders, "
                                + "review the current details, then complete it again.")));
        TrackingHost host = new TrackingHost();
        MarketDayTab tab = new MarketDayTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.2").get(0)).doClick();
            tab.startFulfillment(initial);
        });
        awaitLabel(root[0], "0 completed · 1 failed · retry remaining");
        awaitMarketPlan(root[0]);

        JLabel status = (JLabel) findNamed(
                root[0], "marketDay.fulfillmentStatus").get(0);
        JCheckBox changedLoad = (JCheckBox) findNamed(
                root[0], "marketDay.packOrder.2").get(0);
        JTextArea handoffDetails = (JTextArea) findNamed(
                root[0], "marketDay.fulfillmentResultDetails").get(0);
        assertThat(changedLoad.isSelected()).isFalse();
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();
        assertThat(status.getToolTipText()).contains(
                "#2 Harbor Florist FAILED", "No status or inventory was changed",
                "Retry only orders still confirmed");
        assertThat(status.getAccessibleContext().getAccessibleDescription()).contains(
                "changed after completion review", "No status or inventory was changed");
        assertThat(handoffDetails.getText()).contains(
                "0 completed · 1 failed", "#2 Harbor Florist FAILED",
                "No status or inventory was changed");
        assertThat(host.status).isEqualTo("Market-day completion: 0 done, 1 failed.");
        verify(service).fulfillConfirmedOrders(initial);
    }

    @Test
    @DisplayName("market day resets a packed check when the handoff type changes")
    void marketDayChangedHandoffRequiresFreshPackingCheck() throws Exception {
        MarketDayPackingService service = mock(MarketDayPackingService.class);
        LocalDate day = LocalDate.now();
        MarketDayPackingService.CustomerPack original =
                new MarketDayPackingService.CustomerPack(
                        7L, "Harbor Florist", "FLORIST", "", "", "", "CONFIRMED", day, "",
                        List.of(new MarketDayPackingService.PackLine(
                                "Tulips", "stems", 10, 2.5, 25)), 25, "PICKUP");
        MarketDayPackingService.CustomerPack changed =
                new MarketDayPackingService.CustomerPack(
                        7L, "Harbor Florist", "FLORIST", "", "", "", "CONFIRMED", day, "",
                        List.of(new MarketDayPackingService.PackLine(
                                "Tulips", "stems", 10, 2.5, 25)), 25, "DELIVERY");
        when(service.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(
                        new MarketDayPackingService.MarketDayPlan(
                                day, day, day, "CONFIRMED", 1, 25,
                                List.of(original), List.of(), 0, "Original packing list"),
                        new MarketDayPackingService.MarketDayPlan(
                                day, day, day, "CONFIRMED", 1, 25,
                                List.of(changed), List.of(), 0, "Changed packing list"));
        MarketDayTab tab = new MarketDayTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        boolean[] selected = new boolean[1];
        String[] refreshStatus = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "marketDay.packOrder.7").get(0)).doClick();
            tab.refreshData();
        });
        awaitMarketPlan(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            selected[0] = ((JCheckBox) findNamed(
                    root[0], "marketDay.packOrder.7").get(0)).isSelected();
            refreshStatus[0] = ((JLabel) findNamed(
                    root[0], "marketDay.checklistRefresh").get(0)).getText();
        });

        assertThat(selected[0]).isFalse();
        assertThat(refreshStatus[0]).contains("1 changed or out-of-scope check reset");
        assertThat(findNamed(root[0], "marketDay.handoffGroup.PICKUP")).isEmpty();
        assertThat(findNamed(root[0], "marketDay.handoffGroup.DELIVERY")).hasSize(1);
        assertThat(button(root[0], "Complete confirmed orders…").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("activity history exposes an empty timeline and owner-only clear action")
    void syncHistoryHierarchyAndPermissions() throws Exception {
        SyncHistoryService service = mock(SyncHistoryService.class);
        when(service.filter(anyString(), anyString(),
                nullable(Boolean.class), anyString(), anyInt())).thenReturn(List.of());
        when(service.stats(anyList())).thenReturn(new SyncHistoryService.HistoryStats(0, 0, 0));
        when(service.distinctConnectors(anyInt())).thenReturn(List.of());
        when(service.distinctOperations(anyInt())).thenReturn(List.of());
        SyncHistoryTab tab = new SyncHistoryTab(service, new ReadOnlyHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
            tab.applyRolePermissions(false);
        });
        awaitActivityRefresh(root[0]);

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(tab.getTabTitle()).isEqualTo("Activity History");
        assertThat(findNamed(root[0], "farm.emptyState"))
                .singleElement()
                .satisfies(component -> assertThat(component.isVisible()).isTrue());
        assertThat(find(root[0], JTable.class))
                .singleElement()
                .satisfies(table -> assertThat(table.getAccessibleContext().getAccessibleName())
                        .isEqualTo("Farm activity history table"));
        assertThat(button(root[0], "Clear history…").isEnabled()).isFalse();
        assertThat(button(root[0], "Export CSV…").isEnabled()).isTrue();
        assertThat(button(root[0], "Export PDF…").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "activity.selectedDetail"))
                .singleElement()
                .satisfies(component -> assertThat(((JTextArea) component).getText())
                        .contains("No matching activity"));
        assertThat(button(root[0], "Open related workflow").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("activity refresh runs off the UI thread and coalesces a newer snapshot")
    void activityRefreshIsResponsiveAndDiscardsStaleResult() throws Exception {
        SyncHistoryService service = mock(SyncHistoryService.class);
        com.flowerfarm.model.SyncHistoryEntry oldEntry =
                new com.flowerfarm.model.SyncHistoryEntry(
                        "legacy", "IMPORT", true, "Old activity", "", 1);
        com.flowerfarm.model.SyncHistoryEntry currentEntry =
                new com.flowerfarm.model.SyncHistoryEntry(
                        "square", "IMPORT", true, "Current activity", "", 2);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.filter(anyString(), anyString(), nullable(Boolean.class),
                anyString(), anyInt())).thenAnswer(invocation -> {
                    calledOnEdt.set(SwingUtilities.isEventDispatchThread());
                    if (calls.incrementAndGet() == 1) {
                        entered.countDown();
                        assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                        return List.of(oldEntry);
                    }
                    return List.of(currentEntry);
                });
        when(service.stats(anyList())).thenAnswer(invocation -> {
            List<?> rows = invocation.getArgument(0);
            return new SyncHistoryService.HistoryStats(rows.size(), rows.size(), 0);
        });
        when(service.distinctConnectors(anyInt())).thenReturn(
                List.of("legacy"), List.of("square"));
        when(service.distinctOperations(anyInt())).thenReturn(List.of("IMPORT"));
        SyncHistoryTab tab = new SyncHistoryTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Refresh").isEnabled()).isFalse();
            assertThat(button(root[0], "Export CSV…").isEnabled()).isFalse();
            assertThat(button(root[0], "Clear history…").isEnabled()).isFalse();
            tab.startActivityRefresh();
            assertThat(((JLabel) findNamed(root[0], "activity.refreshStatus").get(0)).getText())
                    .startsWith("Refresh queued");
        });

        release.countDown();
        awaitActivityRefresh(root[0]);

        JTable table = find(root[0], JTable.class).get(0);
        assertThat(calledOnEdt).isFalse();
        assertThat(calls).hasValue(2);
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getValueAt(0, 6)).isEqualTo("Current activity");
        assertThat(button(root[0], "Refresh").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("activity refresh failure preserves timeline filters and diagnostic selection")
    void activityRefreshFailurePreservesWorkingSnapshot() throws Exception {
        SyncHistoryService service = mock(SyncHistoryService.class);
        com.flowerfarm.model.SyncHistoryEntry entry =
                new com.flowerfarm.model.SyncHistoryEntry(
                        "harvest", "HARVEST_LOG", false,
                        "Harvest save failed", "Inventory row was locked", 1);
        when(service.filter(anyString(), anyString(), nullable(Boolean.class),
                anyString(), anyInt())).thenReturn(List.of(entry))
                .thenThrow(new IllegalStateException("activity database unavailable"));
        when(service.stats(anyList())).thenReturn(new SyncHistoryService.HistoryStats(1, 0, 1));
        when(service.distinctConnectors(anyInt())).thenReturn(List.of("harvest"));
        when(service.distinctOperations(anyInt())).thenReturn(List.of("HARVEST_LOG"));
        TrackingHost host = new TrackingHost();
        SyncHistoryTab tab = new SyncHistoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitActivityRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Activity message search").setText("Harvest");
            tab.startActivityRefresh();
        });
        awaitActivityRefresh(root[0]);

        JTable table = find(root[0], JTable.class).get(0);
        JLabel status = (JLabel) findNamed(root[0], "activity.refreshStatus").get(0);
        assertThat(table.getRowCount()).isOne();
        assertThat(table.getSelectedRow()).isZero();
        assertThat(textField(root[0], "Activity message search").getText())
                .isEqualTo("Harvest");
        assertThat(((JTextArea) findNamed(root[0], "activity.selectedDetail").get(0)).getText())
                .contains("Harvest save failed", "Inventory row was locked");
        assertThat(button(root[0], "Export CSV…").isEnabled()).isTrue();
        assertThat(button(root[0], "Clear history…").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("activity database unavailable", "Existing rows", "retry");
        assertThat(host.status).contains("failed", "Existing diagnostics were kept", "retry");
    }

    @Test
    @DisplayName("activity CSV export uses one captured view off the UI thread")
    void activityExportRunsInBackgroundOnce() throws Exception {
        SyncHistoryService service = mock(SyncHistoryService.class);
        com.flowerfarm.model.SyncHistoryEntry entry =
                new com.flowerfarm.model.SyncHistoryEntry(
                        "harvest", "HARVEST_LOG", true,
                        "Harvest saved", "Bed 2", 1);
        when(service.filter(anyString(), anyString(), nullable(Boolean.class),
                anyString(), anyInt())).thenReturn(List.of(entry));
        when(service.stats(anyList())).thenReturn(new SyncHistoryService.HistoryStats(1, 1, 0));
        when(service.distinctConnectors(anyInt())).thenReturn(List.of("harvest"));
        when(service.distinctOperations(anyInt())).thenReturn(List.of("HARVEST_LOG"));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        doAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            Files.writeString(Path.of((String) invocation.getArgument(0)),
                    "Id,When,Connector\n1,2026-08-24,harvest\n");
            return null;
        }).when(service).exportToCsv(anyString(), anyList());
        SyncHistoryTab tab = new SyncHistoryTab(service, new TestHost());
        JComponent[] root = new JComponent[1];
        Path directory = Files.createTempDirectory("activity-export");
        Path destination = directory.resolve("activity.csv");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitActivityRefresh(root[0]);
        SwingUtilities.invokeAndWait(() ->
                tab.startActivityCsvExport(destination.toFile(), List.of(entry)));
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(root[0], "Export CSV…").isEnabled()).isFalse();
            assertThat(button(root[0], "Export PDF…").isEnabled()).isFalse();
            assertThat(button(root[0], "Refresh").isEnabled()).isTrue();
            tab.startActivityCsvExport(destination.toFile(), List.of(entry));
        });

        assertThat(calledOnEdt).isFalse();
        release.countDown();
        awaitLabel(root[0], "CSV saved · 1 rows");
        JLabel exportStatus = (JLabel) findNamed(
                root[0], "activity.exportStatus").get(0);
        assertThat(exportStatus.getText())
                .withFailMessage(exportStatus.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("CSV saved · 1 rows");

        assertThat(Files.readString(destination)).contains("harvest");
        assertThat(button(root[0], "Export CSV…").isEnabled()).isTrue();
        verify(service).exportToCsv(anyString(), anyList());
        Files.deleteIfExists(destination);
        Files.deleteIfExists(directory);
    }

    @Test
    @DisplayName("failed activity PDF preserves its destination, filters, and diagnostics")
    void activityPdfFailureKeepsWorkingState() throws Exception {
        SyncHistoryService service = mock(SyncHistoryService.class);
        com.flowerfarm.model.SyncHistoryEntry entry =
                new com.flowerfarm.model.SyncHistoryEntry(
                        "harvest", "HARVEST_LOG", false,
                        "Harvest save failed", "Inventory row locked", 1);
        when(service.filter(anyString(), anyString(), nullable(Boolean.class),
                anyString(), anyInt())).thenReturn(List.of(entry));
        when(service.stats(anyList())).thenReturn(new SyncHistoryService.HistoryStats(1, 0, 1));
        when(service.distinctConnectors(anyInt())).thenReturn(List.of("harvest"));
        when(service.distinctOperations(anyInt())).thenReturn(List.of("HARVEST_LOG"));
        SyncHistoryService.AuditReport report = new SyncHistoryService.AuditReport(
                LocalDate.now(), "Harvest failures", 1, 0, 1,
                List.of(entry), "Harvest failures");
        when(service.buildAuditReport(anyList(), anyString())).thenReturn(report);
        when(service.generateAuditPdf(report))
                .thenThrow(new IllegalStateException("PDF renderer unavailable"));
        TrackingHost host = new TrackingHost();
        SyncHistoryTab tab = new SyncHistoryTab(service, host);
        JComponent[] root = new JComponent[1];
        Path destination = Files.createTempFile("existing-activity", ".pdf");
        Files.writeString(destination, "existing audit handoff");

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitActivityRefresh(root[0]);
        SwingUtilities.invokeAndWait(() -> {
            textField(root[0], "Activity message search").setText("Harvest");
            tab.startActivityPdfExport(
                    destination.toFile(), List.of(entry), "Harvest failures");
        });
        awaitLabel(root[0], "Export failed · retry");

        JLabel status = (JLabel) findNamed(root[0], "activity.exportStatus").get(0);
        assertThat(Files.readString(destination)).isEqualTo("existing audit handoff");
        assertThat(textField(root[0], "Activity message search").getText())
                .isEqualTo("Harvest");
        assertThat(((JTextArea) findNamed(root[0], "activity.selectedDetail").get(0)).getText())
                .contains("Harvest save failed", "Inventory row locked");
        assertThat(button(root[0], "Export PDF…").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("PDF renderer unavailable", "rows", "filters", "destination file", "kept");
        assertThat(host.status)
                .contains("PDF renderer unavailable", "diagnostics were kept", "retry");
        Files.deleteIfExists(destination);
    }

    @Test
    @DisplayName("activity-history management distinguishes owner from field hand")
    void activityHistoryOwnerPermission() throws Exception {
        SyncHistoryService service = mock(SyncHistoryService.class);
        when(service.filter(anyString(), anyString(),
                nullable(Boolean.class), anyString(), anyInt())).thenReturn(List.of());
        when(service.stats(anyList())).thenReturn(new SyncHistoryService.HistoryStats(0, 0, 0));
        when(service.distinctConnectors(anyInt())).thenReturn(List.of());
        when(service.distinctOperations(anyInt())).thenReturn(List.of());
        SyncHistoryTab owner = new SyncHistoryTab(service, new TestHost());
        SyncHistoryTab hand = new SyncHistoryTab(service, new HandHost());
        boolean[] states = new boolean[2];

        SwingUtilities.invokeAndWait(() -> {
            JComponent ownerRoot = owner.getUIComponent();
            owner.applyRolePermissions(true);
            states[0] = button(ownerRoot, "Clear history…").isEnabled();
            JComponent handRoot = hand.getUIComponent();
            hand.applyRolePermissions(true);
            states[1] = button(handRoot, "Clear history…").isEnabled();
        });

        assertThat(states).containsExactly(true, false);
    }

    @Test
    @DisplayName("activity failures expose readable recovery guidance and contextual routing")
    void activityHistoryRecoveryInspector() throws Exception {
        SyncHistoryService service = mock(SyncHistoryService.class);
        List<com.flowerfarm.model.SyncHistoryEntry> entries = List.of(
                new com.flowerfarm.model.SyncHistoryEntry(
                        "harvest", "HARVEST_LOG", false,
                        "Harvest save failed", "Inventory row was locked", 1),
                new com.flowerfarm.model.SyncHistoryEntry(
                        "airtable", "SYNC", false,
                        "Customer sync failed", "Authentication expired", 0),
                new com.flowerfarm.model.SyncHistoryEntry(
                        "crm", "ORDER_STATUS", true,
                        "Order #12 status CONFIRMED → CANCELLED", "notes=weather", 1));
        when(service.filter(anyString(), anyString(),
                nullable(Boolean.class), anyString(), anyInt())).thenReturn(entries);
        when(service.stats(anyList())).thenReturn(new SyncHistoryService.HistoryStats(3, 1, 2));
        when(service.distinctConnectors(anyInt())).thenReturn(List.of("harvest", "airtable"));
        when(service.distinctOperations(anyInt()))
                .thenReturn(List.of("HARVEST_LOG", "SYNC", "ORDER_STATUS"));
        TrackingHost host = new TrackingHost();
        SyncHistoryTab tab = new SyncHistoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitActivityRefresh(root[0]);

        assertThat(((JTextArea) findNamed(root[0], "activity.selectedDetail").get(0)).getText())
                .contains("Harvest save failed", "Inventory row was locked", "Harvest log");
        assertThat(((JLabel) findNamed(root[0], "activity.recovery").get(0)).getText())
                .contains("Review Harvest Log", "retry");
        assertThat(button(root[0], "Open related workflow").isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(() -> button(root[0], "Open related workflow").doClick());
        assertThat(host.selectedTab).isEqualTo("Harvest Log");

        JTable activityTable = find(root[0], JTable.class).get(0);
        SwingUtilities.invokeAndWait(() -> activityTable.setRowSelectionInterval(1, 1));
        assertThat(((JTextArea) findNamed(root[0], "activity.selectedDetail").get(0)).getText())
                .contains("Customer sync failed", "Authentication expired");
        assertThat(((JLabel) findNamed(root[0], "activity.recovery").get(0)).getText())
                .contains("credentials", "network access", "field mapping");
        assertThat(button(root[0], "Open related workflow").isEnabled()).isFalse();

        SwingUtilities.invokeAndWait(() -> activityTable.setRowSelectionInterval(2, 2));
        assertThat(button(root[0], "Open related workflow").getToolTipText())
                .contains("Cancelled orders selected");
        SwingUtilities.invokeAndWait(() -> button(root[0], "Open related workflow").doClick());
        assertThat(host.selectedTab).isEqualTo("Customers & Orders");
        assertThat(host.orderStatus).isEqualTo("CANCELLED");
        assertThat(host.orderStatusReturnTab).isEqualTo("Activity History");
        assertThat(host.orderStatusReturnLabel).isEqualTo("Activity History");
    }

    @Test
    @DisplayName("activity history routes harvest and order IDs to exact saved records")
    void activityHistoryRoutesExactRecordIds() throws Exception {
        SyncHistoryService service = mock(SyncHistoryService.class);
        List<com.flowerfarm.model.SyncHistoryEntry> entries = List.of(
                new com.flowerfarm.model.SyncHistoryEntry(
                        "harvest", "HARVEST_LOG", true,
                        "Harvest logged: Anemone × 36 stems",
                        "harvestId=44, inventoryId=9", 36),
                new com.flowerfarm.model.SyncHistoryEntry(
                        "crm", "ORDER_STATUS", true,
                        "Order #15 status DRAFT → CONFIRMED", "notes=Saturday", 1));
        when(service.filter(anyString(), anyString(),
                nullable(Boolean.class), anyString(), anyInt())).thenReturn(entries);
        when(service.stats(anyList())).thenReturn(new SyncHistoryService.HistoryStats(2, 2, 0));
        when(service.distinctConnectors(anyInt())).thenReturn(List.of("harvest", "crm"));
        when(service.distinctOperations(anyInt()))
                .thenReturn(List.of("HARVEST_LOG", "ORDER_STATUS"));
        TrackingHost host = new TrackingHost();
        SyncHistoryTab tab = new SyncHistoryTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitActivityRefresh(root[0]);

        assertThat(button(root[0], "Open related workflow").getToolTipText())
                .contains("harvest #44", "selected");
        SwingUtilities.invokeAndWait(() -> button(root[0], "Open related workflow").doClick());
        assertThat(host.harvestId).isEqualTo(44L);
        assertThat(host.harvestReturnTab).isEqualTo("Activity History");
        assertThat(host.harvestReturnLabel).isEqualTo("Activity History");

        JTable activityTable = find(root[0], JTable.class).get(0);
        SwingUtilities.invokeAndWait(() -> activityTable.setRowSelectionInterval(1, 1));
        assertThat(button(root[0], "Open related workflow").getToolTipText())
                .contains("order #15", "selected");
        SwingUtilities.invokeAndWait(() -> button(root[0], "Open related workflow").doClick());
        assertThat(host.orderId).isEqualTo(15L);
        assertThat(host.orderReturnTab).isEqualTo("Activity History");
        assertThat(host.orderReturnLabel).isEqualTo("Activity History");
    }

    @Test
    @DisplayName("reports presents one clear primary action and an accessible range workspace")
    void reportsHierarchy() throws Exception {
        ReportService service = mock(ReportService.class);
        when(service.preview(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate from = invocation.getArgument(0);
            LocalDate to = invocation.getArgument(1);
            return new ReportService.ReportPreview(
                    from, to, 12, 5, 2, 2, 875.50, 510.00,
                    List.of(new ReportService.ProductPerformance(
                            "Dahlia", "stems", 80, 3, 420, 260, 5.25)),
                    List.of(new ReportService.CustomerPerformance(
                            "Stem & Vine", 3, 420, 260, 140)));
        });
        ReportsTab tab = new ReportsTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> root[0] = tab.getUIComponent());

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "farm.pageHeader")).hasSize(1);
        assertThat(findNamed(root[0], "farm.section")).hasSize(2);
        assertThat(button(root[0], "Create 7-day report…")
                .getAccessibleContext().getAccessibleName())
                .isEqualTo("Create 7-day report…");
        assertThat(button(root[0], "Today").isEnabled()).isTrue();
        assertThat(button(root[0], "Last 7 days").isEnabled()).isTrue();
        assertThat(button(root[0], "This month").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "reports.summary")).hasSize(1);
        assertThat(findNamed(root[0], "farm.metricCard")).hasSize(4);
        assertThat(((JLabel) findNamed(root[0], "reports.rangePreview").get(0)).getText())
                .contains("7 days selected", "harvest", "orders", "revenue");

        SwingUtilities.invokeAndWait(() -> textField(root[0], "Report start date").setText("bad-date"));
        assertThat(button(root[0], "Create selected report…").isEnabled()).isFalse();
        assertThat(((JLabel) findNamed(root[0], "reports.rangePreview").get(0)).getText())
                .contains("Start date must use YYYY-MM-DD");

        SwingUtilities.invokeAndWait(() -> button(root[0], "Last 7 days").doClick());
        assertThat(button(root[0], "Create selected report…").isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "reports.rangePreview").get(0)).getText())
                .startsWith("7 days selected");

        SwingUtilities.invokeAndWait(() -> button(root[0], "Preview range").doClick());
        awaitLabel(root[0], "Range preview ready");
        assertThat(((JLabel) findNamed(root[0], "reports.preview.harvest").get(0)).getText())
                .isEqualTo("12 logs");
        assertThat(((JLabel) findNamed(root[0], "reports.preview.orders").get(0)).getText())
                .isEqualTo("5 orders");
        assertThat(((JLabel) findNamed(
                root[0], "reports.preview.committedRevenue").get(0)).getText())
                .isEqualTo("$875.50");
        assertThat(((JLabel) findNamed(
                root[0], "reports.preview.fulfilledRevenue").get(0)).getText())
                .isEqualTo("$510.00");
        JTable products = (JTable) findNamed(
                root[0], "reports.productPerformance").get(0);
        JTable customers = (JTable) findNamed(
                root[0], "reports.customerPerformance").get(0);
        assertThat(products.getRowCount()).isEqualTo(1);
        assertThat(products.getValueAt(0, 0)).isEqualTo("Dahlia");
        assertThat(products.getValueAt(0, 4)).isEqualTo("$5.25");
        assertThat(customers.getRowCount()).isEqualTo(1);
        assertThat(customers.getValueAt(0, 0)).isEqualTo("Stem & Vine");
        assertThat(customers.getValueAt(0, 2)).isEqualTo("$140.00");
    }

    @Test
    @DisplayName("report generation stays off the UI thread and failures recover inline")
    void reportsBackgroundFailureState() throws Exception {
        ReportService service = mock(ReportService.class);
        boolean[] calledOnEdt = new boolean[1];
        when(service.generateReportPdf(any(LocalDate.class), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    calledOnEdt[0] = SwingUtilities.isEventDispatchThread();
                    throw new IllegalStateException("Report database is busy");
                });
        ReportsTab tab = new ReportsTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            button(root[0], "Create selected report…").doClick();
        });
        awaitLabel(root[0], "Report needs attention");

        assertThat(calledOnEdt[0]).isFalse();
        assertThat(button(root[0], "Create selected report…").isEnabled()).isTrue();
        assertThat(button(root[0], "Create 7-day report…").isEnabled()).isTrue();
        assertThat(((JTextArea) findNamed(root[0], "reports.summary").get(0)).getText())
                .contains("Report database is busy", "try again");
    }

    @Test
    @DisplayName("irrigation uses status badges, structured guidance, and explicit data modes")
    void irrigationHierarchy() throws Exception {
        IrrigationAdvisorService service = mock(IrrigationAdvisorService.class);
        when(service.advise(true)).thenReturn(new IrrigationAdvisorService.IrrigationAdvice(
                "Port Orchard, WA", "LIVE", LocalDate.now().toString(),
                IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                IrrigationAdvisorService.Priority.HIGH,
                "Deep-water active beds before the afternoon heat.",
                List.of("Run drip irrigation before 10 AM.",
                        "Check soil moisture in Field A after watering."), List.of("Field A"),
                0.1, 1.2, 1.1, 86.0, 58.0, "Dry maritime summer pattern."));
        TrackingHost host = new TrackingHost();
        IrrigationInfoTab tab = new IrrigationInfoTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitLabel(root[0], "Mode: LIVE (Open-Meteo)");

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "farm.statusBadge")).hasSize(3);
        assertThat(findNamed(root[0], "farm.section")).hasSize(2);
        assertThat(button(root[0], "Refresh live weather").isEnabled()).isTrue();
        assertThat(button(root[0], "Use climatology only").isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "irrigation.actionProgress").get(0)).getText())
                .isEqualTo("0 of 2 done · 2 remaining");
        JCheckBox firstAction = (JCheckBox) findNamed(root[0], "irrigation.action.0").get(0);
        SwingUtilities.invokeAndWait(firstAction::doClick);
        assertThat(((JLabel) findNamed(root[0], "irrigation.actionProgress").get(0)).getText())
                .isEqualTo("1 of 2 done · 1 remaining");
        assertThat(button(root[0], "Clear checks").isEnabled()).isTrue();
        SwingUtilities.invokeAndWait(() -> button(root[0], "Refresh live weather").doClick());
        awaitButtonEnabled(root[0], "Refresh live weather");
        JCheckBox refreshedAction = (JCheckBox) findNamed(root[0], "irrigation.action.0").get(0);
        assertThat(refreshedAction.isSelected()).isTrue();
        SwingUtilities.invokeAndWait(() -> tab.showFrom("Dashboard", "Dashboard"));
        assertThat(findNamed(root[0], "irrigation.handoffBanner"))
                .singleElement().satisfies(component -> assertThat(component.isVisible()).isTrue());
        assertThat(refreshedAction.isSelected()).isTrue();
        JButton returnButton = button(root[0], "Return to Dashboard");
        assertThat(returnButton.getAccessibleContext().getAccessibleName())
                .contains("Dashboard", "Irrigation and Care");
        SwingUtilities.invokeAndWait(returnButton::doClick);
        assertThat(host.selectedTab).isEqualTo("Dashboard");
        assertThat(host.status).isEqualTo("Returned to Dashboard from Irrigation & Care.");
        SwingUtilities.invokeAndWait(() -> button(root[0], "Clear checks").doClick());
        assertThat(refreshedAction.isSelected()).isFalse();
    }

    @Test
    @DisplayName("irrigation refresh failure keeps the reviewed field plan and checks")
    void irrigationRefreshFailurePreservesFieldWork() throws Exception {
        IrrigationAdvisorService service = mock(IrrigationAdvisorService.class);
        IrrigationAdvisorService.IrrigationAdvice advice =
                new IrrigationAdvisorService.IrrigationAdvice(
                        "Port Orchard, WA", "LIVE", LocalDate.now().toString(),
                        IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                        IrrigationAdvisorService.Priority.HIGH,
                        "Deep-water active beds before the afternoon heat.",
                        List.of("Run drip irrigation before 10 AM.",
                                "Check soil moisture in Field A after watering."),
                        List.of("Field A"), 0.1, 1.2, 1.1, 86.0, 58.0,
                        "Dry maritime summer pattern.");
        when(service.advise(true)).thenReturn(advice)
                .thenThrow(new IllegalStateException("Forecast timed out"));
        TrackingHost host = new TrackingHost();
        IrrigationInfoTab tab = new IrrigationInfoTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitLabel(root[0], "Mode: LIVE (Open-Meteo)");
        SwingUtilities.invokeAndWait(() -> {
            ((JCheckBox) findNamed(root[0], "irrigation.action.0").get(0)).doClick();
            tab.refreshAdvice(true);
        });
        awaitLabel(root[0], "Weather refresh failed · last plan kept");

        JCheckBox retained = (JCheckBox) findNamed(
                root[0], "irrigation.action.0").get(0);
        JLabel progress = (JLabel) findNamed(
                root[0], "irrigation.actionProgress").get(0);
        JLabel refresh = find(root[0], JLabel.class).stream()
                .filter(label -> "Weather refresh failed · last plan kept".equals(label.getText()))
                .findFirst().orElseThrow();
        assertThat(retained.isSelected()).isTrue();
        assertThat(retained.isEnabled()).isTrue();
        assertThat(progress.getText()).isEqualTo("1 of 2 done · 1 remaining");
        assertThat(textArea(root[0], "Today's irrigation plan").getText())
                .contains("Dry maritime summer pattern", "Active beds: Field A");
        assertThat(refresh.getAccessibleContext().getAccessibleDescription())
                .contains("Forecast timed out", "plan and completed checks", "verify conditions");
        assertThat(button(root[0], "Refresh live weather").isEnabled()).isTrue();
        assertThat(host.status).contains("Last field plan and checks were kept");
    }

    @Test
    @DisplayName("irrigation refresh coalesces to the newest requested data mode")
    void irrigationRefreshCoalescesNewestMode() throws Exception {
        IrrigationAdvisorService service = mock(IrrigationAdvisorService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        IrrigationAdvisorService.IrrigationAdvice oldAdvice =
                new IrrigationAdvisorService.IrrigationAdvice(
                        "Port Orchard, WA", "LIVE", LocalDate.now().toString(),
                        IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                        IrrigationAdvisorService.Priority.HIGH, "Old live plan",
                        List.of("Old action"), List.of(), 0.1, 1.2, 1.1, 86.0, 58.0, "Old");
        IrrigationAdvisorService.IrrigationAdvice currentAdvice =
                new IrrigationAdvisorService.IrrigationAdvice(
                        "Port Orchard, WA", "CLIMATOLOGY", LocalDate.now().toString(),
                        IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                        IrrigationAdvisorService.Priority.MEDIUM, "Current offline plan",
                        List.of("Current action"), List.of(), null, null, null, null, null,
                        "Current");
        when(service.advise(true)).thenAnswer(invocation -> {
            calls.incrementAndGet();
            entered.countDown();
            assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
            return oldAdvice;
        });
        when(service.advise(false)).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return currentAdvice;
        });
        IrrigationInfoTab tab = new IrrigationInfoTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            tab.refreshAdvice(false);
            assertThat(find(root[0], JLabel.class))
                    .extracting(JLabel::getText)
                    .contains("Refresh queued · current weather check finishing");
        });
        release.countDown();
        awaitLabel(root[0], "Mode: CLIMATOLOGY (offline climatology)");

        assertThat(calls).hasValue(2);
        assertThat(textArea(root[0], "Today's irrigation plan").getText())
                .contains("Current").doesNotContain("Old");
        assertThat(findNamed(root[0], "irrigation.action.0"))
                .singleElement()
                .satisfies(component -> assertThat(
                        component.getAccessibleContext().getAccessibleName())
                        .contains("Current action"));
    }

    @Test
    @DisplayName("irrigation failures become an actionable state and keep offline notes")
    void irrigationErrorState() throws Exception {
        IrrigationAdvisorService service = mock(IrrigationAdvisorService.class);
        when(service.advise(true)).thenThrow(new IllegalStateException("Forecast timed out"));
        IrrigationInfoTab tab = new IrrigationInfoTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitLabel(root[0], "Weather unavailable");

        assertThat(button(root[0], "Refresh live weather").isEnabled()).isTrue();
        assertThat(((JLabel) findNamed(root[0], "irrigation.actionProgress").get(0)).getText())
                .isEqualTo("Checklist unavailable");
        assertThat(find(root[0], javax.swing.JTextArea.class))
                .extracting(javax.swing.JTextArea::getText)
                .anySatisfy(text -> assertThat(text)
                        .contains("Forecast timed out")
                        .contains("Standing maritime guidance remains available"));
    }

    @Test
    @DisplayName("rose guide uses the shared hierarchy and keeps viewer-safe exploration")
    void roseGuideHierarchyAndPermissions() throws Exception {
        RoseVarietiesTab tab = new RoseVarietiesTab(
                mock(InventoryService.class), new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.applyRolePermissions(false);
        });

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "farm.section")).hasSize(2);
        assertThat(button(root[0], "Add sample Nootka Rose").isEnabled()).isFalse();
        assertThat(button(root[0], "Open Rose Visualizer").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "rose.varieties.guide")).hasSize(1);
    }

    @Test
    @DisplayName("rose sample saves off the UI thread once and hands off to Inventory")
    void roseSampleSaveRunsInBackgroundOnce() throws Exception {
        InventoryService service = mock(InventoryService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(service.getAllItems()).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            started.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of();
        });
        when(service.addItem(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TrackingHost host = new TrackingHost();
        RoseVarietiesTab tab = new RoseVarietiesTab(service, host);
        JComponent[] root = new JComponent[1];
        JButton[] add = new JButton[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            add[0] = button(root[0], "Add sample Nootka Rose");
            tab.startAddSampleRose();
        });
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            tab.applyRolePermissions(true);
            assertThat(add[0].isEnabled()).isFalse();
            tab.startAddSampleRose();
        });

        assertThat(calledOnEdt).isFalse();
        release.countDown();
        awaitLabel(root[0], "Nootka Rose added · open Inventory to edit");

        assertThat(add[0].getText()).isEqualTo("Nootka Rose added");
        assertThat(add[0].isEnabled()).isFalse();
        verify(service).getAllItems();
        verify(service).addItem(any(Item.class));
        SwingUtilities.invokeAndWait(() -> button(root[0], "Open Inventory").doClick());
        assertThat(host.selectedTab).isEqualTo("Inventory");
        assertThat(host.status).isEqualTo("Opened Inventory from the rose variety guide.");
    }

    @Test
    @DisplayName("rose sample detects an existing product without adding a duplicate")
    void roseSampleDoesNotDuplicateInventory() throws Exception {
        InventoryService service = mock(InventoryService.class);
        when(service.getAllItems()).thenReturn(List.of(new Item(
                "Nootka Rose", "Flowers/Plants", 4.25, "Per Stem", 2.10, 12,
                "Existing production SKU")));
        TrackingHost host = new TrackingHost();
        RoseVarietiesTab tab = new RoseVarietiesTab(service, host);
        JComponent[] root = new JComponent[1];
        JButton[] add = new JButton[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            add[0] = button(root[0], "Add sample Nootka Rose");
            tab.startAddSampleRose();
        });
        awaitLabel(root[0], "Nootka Rose already exists · open Inventory to review");

        assertThat(add[0].getText()).isEqualTo("Already in Inventory");
        assertThat(add[0].isEnabled()).isFalse();
        verify(service, never()).addItem(any(Item.class));
        assertThat(host.status).contains("already in Inventory", "no duplicate was added");
    }

    @Test
    @DisplayName("failed rose sample save keeps guide filters and restores retry")
    void roseSampleFailureKeepsWorkingState() throws Exception {
        InventoryService service = mock(InventoryService.class);
        when(service.getAllItems()).thenReturn(List.of());
        when(service.addItem(any(Item.class)))
                .thenThrow(new IllegalStateException("Inventory database unavailable"));
        TrackingHost host = new TrackingHost();
        RoseVarietiesTab tab = new RoseVarietiesTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            textField(root[0], "Rose variety search").setText("native");
            tab.startAddSampleRose();
        });
        awaitLabel(root[0], "Sample not added · retry");

        JLabel status = (JLabel) findNamed(root[0], "rose.sampleStatus").get(0);
        assertThat(textField(root[0], "Rose variety search").getText()).isEqualTo("native");
        assertThat(button(root[0], "Add sample Nootka Rose").isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("Inventory database unavailable", "variety guide", "filters", "kept");
        assertThat(host.status)
                .contains("Inventory database unavailable", "ready to retry");
    }

    @Test
    @DisplayName("trend analysis turns real harvest history into an actionable planning signal")
    void trendHierarchy() throws Exception {
        TrendService service = mock(TrendService.class);
        when(service.analyzeQuantityTrend()).thenReturn(new TrendService.TrendResult(
                120, "Real weekly details", null,
                "Nootka Rose", "stems", 5, 90,
                LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 31),
                List.of(
                        new TrendService.TrendPoint(LocalDate.of(2026, 6, 29), 50, 52),
                        new TrendService.TrendPoint(LocalDate.of(2026, 7, 6), 60, 61),
                        new TrendService.TrendPoint(LocalDate.of(2026, 7, 13), 70, 70),
                        new TrendService.TrendPoint(LocalDate.of(2026, 7, 20), 80, 79),
                        new TrendService.TrendPoint(LocalDate.of(2026, 7, 27), 90, 88)),
                9, 43, 0.96));
        TrackingHost host = new TrackingHost();
        TrendAnalysisTab tab = new TrendAnalysisTab(service, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.runAnalysis();
        });
        awaitLabel(root[0], "Forecast ready");

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "farm.section")).hasSize(1);
        assertThat(button(root[0], "Analyze trends").isEnabled()).isTrue();
        assertThat(findNamed(root[0], "trend.analysis.output")).hasSize(1);
        assertThat(findNamed(root[0], "farm.metricCard")).hasSize(5);
        assertThat(findNamed(root[0], "trend.chart")).hasSize(1);
        assertThat(((JTable) findNamed(root[0], "trend.weeklyTable").get(0)).getRowCount())
                .isEqualTo(6);
        assertThat(((JLabel) findNamed(root[0], "trend.forecast").get(0)).getText())
                .isEqualTo("120 stems");
        assertThat(((JLabel) findNamed(root[0], "trend.change").get(0)).getText())
                .isEqualTo("+33%");
        assertThat(((JLabel) findNamed(root[0], "trend.reliability").get(0)).getText())
                .isEqualTo("Strong");
        assertThat(((JLabel) findNamed(root[0], "trend.freshness").get(0)).getText())
                .isEqualTo("4 weeks old");
        JLabel context = (JLabel) findNamed(root[0], "trend.context").get(0);
        assertThat(context.getText())
                .contains("Nootka Rose", "5 weeks", "latest Jul 27: 90 stems",
                        "Forecast +33%");
        assertThat(context.getAccessibleContext().getAccessibleName())
                .contains("5 recorded weeks", "latest 90 stems",
                        "latest week 2026-07-27");
        assertThat(((JLabel) findNamed(root[0], "trend.guidance").get(0)).getText())
                .contains("History gap of 5 weeks", "update the Harvest Log");
        SwingUtilities.invokeAndWait(() -> button(root[0], "Open Harvest Log").doClick());
        assertThat(host.selectedTab).isEqualTo("Harvest Log");
    }

    @Test
    @DisplayName("trend analysis explains insufficient real history without a fabricated forecast")
    void trendInsufficientHistoryState() throws Exception {
        TrendService service = mock(TrendService.class);
        when(service.analyzeQuantityTrend()).thenReturn(new TrendService.TrendResult(
                0, null,
                "Record at least 3 separate weeks for the same crop and unit before forecasting. "
                        + "Best available series has 2 weeks.",
                "Nootka Rose", "stems", 2, Double.NaN));
        TrendAnalysisTab tab = new TrendAnalysisTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.runAnalysis();
        });
        awaitLabel(root[0], "Analysis needs attention");

        assertThat(((JLabel) findNamed(root[0], "trend.forecast").get(0)).getText())
                .isEqualTo("Needs history");
        assertThat(((JLabel) findNamed(root[0], "trend.context").get(0)).getText())
                .contains("Nootka Rose", "2 recorded weeks");
        assertThat(((JLabel) findNamed(root[0], "trend.guidance").get(0)).getText())
                .contains("at least three separate weeks");
        assertThat(((JTextArea) findNamed(root[0], "trend.analysis.output").get(0)).getText())
                .contains("Best available series has 2 weeks")
                .doesNotContain("sample");
    }

    @Test
    @DisplayName("trend analysis lets growers choose a crop series and recent history window")
    void trendSeriesAndWindowSelection() throws Exception {
        TrendService service = mock(TrendService.class);
        when(service.listSeries()).thenReturn(List.of(
                new TrendService.TrendSeriesSummary("Dahlia", "stems", 8, 9,
                        LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 20)),
                new TrendService.TrendSeriesSummary("Nootka Rose", "stems", 6, 7,
                        LocalDate.of(2026, 6, 15), LocalDate.of(2026, 7, 20))));
        when(service.analyzeQuantityTrend("Nootka Rose", "stems", 6)).thenReturn(
                new TrendService.TrendResult(
                        82, "Selected series details", null,
                        "Nootka Rose", "stems", 6, 78,
                        LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 31),
                        List.of(
                                new TrendService.TrendPoint(
                                        LocalDate.of(2026, 6, 15), 48, 50),
                                new TrendService.TrendPoint(
                                        LocalDate.of(2026, 6, 22), 55, 55),
                                new TrendService.TrendPoint(
                                        LocalDate.of(2026, 6, 29), 61, 60),
                                new TrendService.TrendPoint(
                                        LocalDate.of(2026, 7, 6), 64, 65),
                                new TrendService.TrendPoint(
                                        LocalDate.of(2026, 7, 13), 72, 70),
                                new TrendService.TrendPoint(
                                        LocalDate.of(2026, 7, 20), 78, 75)),
                        5, 45, 0.91));
        TrendAnalysisTab tab = new TrendAnalysisTab(service, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> root[0] = tab.getUIComponent());
        awaitLabel(root[0], "2 recorded series · strongest history · all recorded history.");

        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> series = (JComboBox<?>) findNamed(
                    root[0], "trend.seriesSelector").get(0);
            JComboBox<?> window = (JComboBox<?>) findNamed(
                    root[0], "trend.windowSelector").get(0);
            series.setSelectedIndex(2);
            window.setSelectedIndex(1);
            button(root[0], "Analyze trends").doClick();
        });
        awaitLabel(root[0], "Forecast ready");

        assertThat(((JLabel) findNamed(root[0], "trend.seriesHint").get(0)).getText())
                .isEqualTo("6 recorded weeks · latest 6 weeks.");
        assertThat(((JLabel) findNamed(root[0], "trend.forecast").get(0)).getText())
                .isEqualTo("82 stems");
        verify(service).analyzeQuantityTrend("Nootka Rose", "stems", 6);
        verify(service, never()).analyzeQuantityTrend();
    }

    @Test
    @DisplayName("rose visualizer uses a resizeable farm workspace without a button wall")
    void roseVisualizerWorkspace() throws Exception {
        RoseVisualizerTab tab = new RoseVisualizerTab(
                mock(InventoryService.class), new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.applyRolePermissions(false);
        });

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "rose.visualizer.workspace"))
                .singleElement()
                .isInstanceOf(JSplitPane.class);
        assertThat(findNamed(root[0], "rose.visualizer.controls"))
                .singleElement()
                .isInstanceOf(javax.swing.JScrollPane.class);
        assertThat(findNamed(root[0], "farm.section")).hasSize(2);
        assertThat(button(root[0], "Grow / redraw")
                .getAccessibleContext().getAccessibleName()).isEqualTo("Grow / redraw");
        assertThat(button(root[0], "Grow / redraw").isEnabled()).isTrue();
        assertThat(button(root[0], "Evolve / mutate").isEnabled()).isFalse();
        assertThat(button(root[0], "Save ruleset…").isEnabled()).isFalse();
    }

    @Test
    @DisplayName("rose visualizer product refresh runs off the UI thread and applies only the newest snapshot")
    void roseVisualizerInventoryRefreshIsResponsiveAndCoalesced() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        Item oldProduct = new Item("Old Garden Rose", "Flowers/Plants",
                4, "stems", 1, 12, "");
        Item currentProduct = new Item("Nootka Rose", "Flowers/Plants",
                5, "stems", 1, 20, "");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean calledOnEdt = new AtomicBoolean(true);
        when(inventory.getAllItems()).thenAnswer(invocation -> {
            calledOnEdt.set(SwingUtilities.isEventDispatchThread());
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(2, TimeUnit.SECONDS)).isTrue();
                return List.of(oldProduct);
            }
            return List.of(currentProduct);
        });
        RoseVisualizerTab tab = new RoseVisualizerTab(inventory, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(findNamed(root[0], "rose.visualizer.inventorySku").get(0).isEnabled())
                    .isFalse();
            assertThat(button(root[0], "Refresh products").isEnabled()).isFalse();
            tab.startInventoryRefresh();
        });

        release.countDown();
        awaitLabel(root[0], "1 linkable product ready");

        JComboBox<?> products = (JComboBox<?>) findNamed(
                root[0], "rose.visualizer.inventorySku").get(0);
        assertThat(calledOnEdt).isFalse();
        assertThat(calls).hasValue(2);
        assertThat(comboItems(products))
                .containsExactly("(none — use variety preset)", "Nootka Rose");
        assertThat(products.isEnabled()).isTrue();
        assertThat(button(root[0], "Refresh products").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("rose visualizer keeps inventory choices and preview context when refresh fails")
    void roseVisualizerInventoryRefreshFailureKeepsWorkingState() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        Item product = new Item("Nootka Rose", "Flowers/Plants",
                5, "stems", 1, 20, "");
        when(inventory.getAllItems())
                .thenReturn(List.of(product))
                .thenThrow(new IllegalStateException("inventory session closed"));
        TrackingHost host = new TrackingHost();
        RoseVisualizerTab tab = new RoseVisualizerTab(inventory, host);
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitLabel(root[0], "1 linkable product ready");
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> products = (JComboBox<?>) findNamed(
                    root[0], "rose.visualizer.inventorySku").get(0);
            products.setSelectedItem("Nootka Rose");
            tab.startInventoryRefresh();
        });
        awaitLabel(root[0], "Product refresh failed · retry");

        JComboBox<?> products = (JComboBox<?>) findNamed(
                root[0], "rose.visualizer.inventorySku").get(0);
        JLabel status = (JLabel) findNamed(
                root[0], "rose.visualizer.inventoryStatus").get(0);
        assertThat(products.getSelectedItem()).isEqualTo("Nootka Rose");
        assertThat(products.isEnabled()).isTrue();
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("inventory session closed", "Existing product choices", "kept");
        assertThat(host.status).contains("refresh failed", "Existing choices were kept");
    }

    @Test
    @DisplayName("rose visualizer empty inventory state keeps variety presets usable")
    void roseVisualizerEmptyInventoryExplainsNextStep() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        when(inventory.getAllItems()).thenReturn(List.of());
        RoseVisualizerTab tab = new RoseVisualizerTab(inventory, new TestHost());
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> {
            root[0] = tab.getUIComponent();
            tab.initialize();
        });
        awaitLabel(root[0], "No flower or rose products found");

        JComboBox<?> products = (JComboBox<?>) findNamed(
                root[0], "rose.visualizer.inventorySku").get(0);
        JLabel status = (JLabel) findNamed(
                root[0], "rose.visualizer.inventoryStatus").get(0);
        assertThat(comboItems(products)).containsExactly("(none — use variety preset)");
        assertThat(status.getAccessibleContext().getAccessibleDescription())
                .contains("Add a flower or rose product in Inventory",
                        "Variety presets remain available");
        assertThat(button(root[0], "Grow / redraw").isEnabled()).isTrue();
        assertThat(button(root[0], "Refresh products").isEnabled()).isTrue();
    }

    @Test
    @DisplayName("pricing reference participates in the shared farm hierarchy")
    void pricingReferenceHierarchy() throws Exception {
        PricingInfoTab tab = new PricingInfoTab();
        JComponent[] root = new JComponent[1];

        SwingUtilities.invokeAndWait(() -> root[0] = tab.getUIComponent());

        assertThat(root[0].getName()).isEqualTo("farm.page");
        assertThat(findNamed(root[0], "farm.pageHeader")).hasSize(1);
        assertThat(findNamed(root[0], "farm.section")).hasSize(2);
        assertThat(findNamed(root[0], "pricing.units.table"))
                .singleElement()
                .isInstanceOf(JTable.class);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = (JTable) findNamed(root[0], "pricing.units.table").get(0);
            JComboBox<?> productType = find(root[0], JComboBox.class).stream()
                    .filter(combo -> "Product type for unit guidance".equals(
                            combo.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            for (SortOrder order : List.of(SortOrder.ASCENDING, SortOrder.DESCENDING)) {
                table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(0, order)));
                String selectedType = order == SortOrder.ASCENDING
                        ? "Bulk products" : "Equipment and labor";
                productType.setSelectedItem(selectedType);
                assertThat(table.getSelectedRow()).as("selected guidance row after %s sort", order)
                        .isGreaterThanOrEqualTo(0);
                assertThat(table.getValueAt(table.getSelectedRow(), 0))
                        .as("selected product after %s sort", order).isEqualTo(selectedType);
            }
        });
    }

    private static CustomerOrder orderWithLine(
            Customer customer, long orderId, long lineId, String product,
            double quantity, String unit, double price) {
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "");
        order.setId(orderId);
        OrderLine line = new OrderLine(product, quantity, unit, price);
        line.setId(lineId);
        order.addLine(line);
        return order;
    }

    private static CustomerOrder orderWithTwoLines(
            Customer customer, double roseQuantity, double rosePrice) {
        CustomerOrder order = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        order.setId(42L);
        OrderLine dahlias = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        dahlias.setId(101L);
        OrderLine roses = new OrderLine("Rose Bunch", roseQuantity, "bunches", rosePrice);
        roses.setId(102L);
        order.addLine(dahlias);
        order.addLine(roses);
        return order;
    }

    private static void awaitLabel(Component root, String text) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] found = new boolean[1];
            SwingUtilities.invokeAndWait(() -> found[0] = find(root, javax.swing.JLabel.class)
                    .stream().anyMatch(label -> text.equals(label.getText())));
            if (found[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for label: " + text);
    }

    private static void awaitComboSelection(
            Component root, String componentName, String text) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] found = new boolean[1];
            SwingUtilities.invokeAndWait(() -> {
                JComboBox<?> combo = (JComboBox<?>) findNamed(root, componentName).get(0);
                found[0] = combo.getSelectedItem() != null
                        && text.equals(combo.getSelectedItem().toString());
            });
            if (found[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for " + componentName
                + " selection: " + text);
    }

    private static void awaitOrderTableValue(
            Component root, int column, String value) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] found = new boolean[1];
            SwingUtilities.invokeAndWait(() -> {
                JTable table = (JTable) findNamed(root, "crm.orders.table").get(0);
                found[0] = table.getRowCount() > 0
                        && value.equals(String.valueOf(table.getValueAt(0, column)));
            });
            if (found[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for CRM order column " + column
                + " value: " + value);
    }

    private static void awaitHostStatus(TrackingHost host, String text) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            SwingUtilities.invokeAndWait(() -> { });
            if (text.equals(host.status)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for host status: " + text
                + "; last status was: " + host.status);
    }

    private static void awaitCrmRefresh(Component root) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String[] status = new String[1];
            SwingUtilities.invokeAndWait(() -> status[0] = ((JLabel) findNamed(
                    root, "crm.refreshStatus").get(0)).getText());
            if (status[0].startsWith("Customers and orders current")
                    || status[0].startsWith("CRM refresh failed")) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for CRM refresh");
    }

    private static void awaitHarvestRefresh(Component root) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String[] status = new String[1];
            SwingUtilities.invokeAndWait(() -> status[0] = ((JLabel) findNamed(
                    root, "harvest.refreshStatus").get(0)).getText());
            if ((!status[0].startsWith("Refreshing harvest history")
                    && !status[0].startsWith("Refresh queued"))
                    || status[0].startsWith("Harvest refresh failed")) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for harvest refresh");
    }

    private static void awaitActivityRefresh(Component root) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String[] status = new String[1];
            SwingUtilities.invokeAndWait(() -> status[0] = ((JLabel) findNamed(
                    root, "activity.refreshStatus").get(0)).getText());
            if (status[0].matches("\\d+ activities .*" )
                    || status[0].startsWith("Activity refresh failed")) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for activity refresh");
    }

    private static void awaitMarketPlan(Component root) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            String[] status = new String[1];
            SwingUtilities.invokeAndWait(() -> status[0] = ((JLabel) findNamed(
                    root, "marketDay.planStatus").get(0)).getText());
            if ((!status[0].startsWith("Building packing list")
                    && !status[0].startsWith("Refresh queued")
                    && !status[0].equals("Packing list not built"))
                    || status[0].startsWith("Packing list refresh failed")) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for market-day plan");
    }

    private static void awaitButtonEnabled(Component root, String text) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] enabled = new boolean[1];
            SwingUtilities.invokeAndWait(() -> enabled[0] = button(root, text).isEnabled());
            if (enabled[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for enabled button: " + text);
    }

    private static HarvestService emptyHarvestService() {
        HarvestService service = mock(HarvestService.class);
        when(service.filter(
                anyString(),
                anyString(),
                anyString(),
                nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        when(service.totalsByCrop()).thenReturn(Map.of());
        when(service.totalsByCrop(anyList())).thenReturn(Map.of());
        when(service.add(any(HarvestEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return service;
    }

    private static MarketDayPackingService.MarketDayPlan confirmedMarketPlan(LocalDate day) {
        return confirmedMarketPlan(day, 1L);
    }

    private static MarketDayPackingService.MarketDayPlan confirmedMarketPlan(
            LocalDate day, long orderId) {
        return confirmedMarketPlan(day, orderId, "");
    }

    private static MarketDayPackingService.MarketDayPlan confirmedMarketPlan(
            LocalDate day, long orderId, String accountNotes) {
        return new MarketDayPackingService.MarketDayPlan(
                day, day, day, "CONFIRMED", 1, 125,
                List.of(new MarketDayPackingService.CustomerPack(
                        orderId, "Kitsap Blooms", "FLORIST", "", "", "", accountNotes,
                        "CONFIRMED", day, "Keep upright", List.of(
                        new MarketDayPackingService.PackLine(
                                "Dahlia Mix", "stems", 12, 3.5, 42)), 125, "PICKUP")),
                List.of(new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "stems", 12, 20, false, 0)),
                0, "Packing list");
    }

    private static JButton button(Component root, String text) {
        return find(root, JButton.class).stream()
                .filter(candidate -> text.equals(candidate.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static JLabel label(Component root, String text) {
        return find(root, JLabel.class).stream()
                .filter(candidate -> text.equals(candidate.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static void submitOrderProductShortcut(Component root) {
        JComponent orderPanel = (JComponent) findNamed(root, "crm.orderPanel").get(0);
        orderPanel.getActionMap().get("crm.submitOrderProduct").actionPerformed(
                new java.awt.event.ActionEvent(orderPanel,
                        java.awt.event.ActionEvent.ACTION_PERFORMED, "Ctrl+Enter"));
    }

    private static List<Object> comboItems(JComboBox<?> combo) {
        List<Object> items = new ArrayList<>();
        for (int index = 0; index < combo.getItemCount(); index++) {
            items.add(combo.getItemAt(index));
        }
        return items;
    }

    private static List<Component> findNamed(Component root, String name) {
        List<Component> matches = new ArrayList<>();
        visit(root, component -> {
            if (name.equals(component.getName())) {
                matches.add(component);
            }
        });
        return matches;
    }

    private static <T extends Component> List<T> find(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        visit(root, component -> {
            if (type.isInstance(component)) {
                matches.add(type.cast(component));
            }
        });
        return matches;
    }

    private static JTextField textField(Component root, String accessibleName) {
        return find(root, JTextField.class).stream()
                .filter(field -> accessibleName.equals(
                        field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextArea textArea(Component root, String accessibleName) {
        return find(root, JTextArea.class).stream()
                .filter(area -> accessibleName.equals(
                        area.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static void visit(Component component,
                              java.util.function.Consumer<Component> consumer) {
        consumer.accept(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                visit(child, consumer);
            }
        }
    }

    private static class TestHost implements TabHost {
        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }

    private static final class TrackingHost extends TestHost {
        private String selectedTab;
        private String status;
        private List<String> inventoryProducts = List.of();
        private String inventoryReturnTab;
        private String inventoryReturnLabel;
        private String orderStatus;
        private String orderStatusReturnTab;
        private String orderStatusReturnLabel;
        private Long orderId;
        private Long orderLineId;
        private String orderLineProduct;
        private boolean orderLineCorrection;
        private String orderReturnTab;
        private String orderReturnLabel;
        private Long harvestId;
        private String harvestReturnTab;
        private String harvestReturnLabel;
        private String harvestEntryReturnTab;
        private String harvestEntryReturnLabel;
        private String marketDayReturnTab;
        private String marketDayReturnLabel;
        private String irrigationReturnTab;
        private String irrigationReturnLabel;
        private int refreshCount;

        @Override public void refreshAll() {
            refreshCount++;
        }

        @Override public void selectTab(String tabTitle) {
            selectedTab = tabTitle;
        }

        @Override public void setStatus(String message) {
            status = message;
        }

        @Override public void showInventoryProducts(List<String> productNames) {
            inventoryProducts = productNames == null ? List.of() : List.copyOf(productNames);
            selectTab("Inventory");
            setStatus(inventoryProducts.isEmpty()
                    ? "Opened Inventory to review stock."
                    : "Opened Inventory for market shortfalls: "
                            + String.join(", ", inventoryProducts) + ".");
        }

        @Override public void showInventoryProducts(
                List<String> productNames, String returnTab, String returnLabel) {
            inventoryReturnTab = returnTab;
            inventoryReturnLabel = returnLabel;
            showInventoryProducts(productNames);
        }

        @Override public void showOrdersByStatus(String status) {
            orderStatus = status;
            selectTab("Customers & Orders");
        }

        @Override public void showOrdersByStatus(
                String status, String returnTab, String returnLabel) {
            orderStatus = status;
            orderStatusReturnTab = returnTab;
            orderStatusReturnLabel = returnLabel;
            selectTab("Customers & Orders");
        }

        @Override public void showOrder(long orderId) {
            this.orderId = orderId;
            selectTab("Customers & Orders");
        }

        @Override public void showOrder(long orderId, String returnTab, String returnLabel) {
            this.orderId = orderId;
            this.orderReturnTab = returnTab;
            this.orderReturnLabel = returnLabel;
            selectTab("Customers & Orders");
        }

        @Override public void showOrderLine(long orderId, Long lineId, String productName,
                                            String returnTab, String returnLabel) {
            this.orderLineId = lineId;
            this.orderLineProduct = productName;
            showOrder(orderId, returnTab, returnLabel);
        }

        @Override public void showOrderLineCorrection(
                long orderId, Long lineId, String productName,
                String returnTab, String returnLabel) {
            this.orderLineCorrection = true;
            showOrderLine(orderId, lineId, productName, returnTab, returnLabel);
        }

        @Override public void showHarvest(long harvestId) {
            this.harvestId = harvestId;
            selectTab("Harvest Log");
        }

        @Override public void showHarvest(long harvestId,
                                          String returnTab, String returnLabel) {
            this.harvestId = harvestId;
            this.harvestReturnTab = returnTab;
            this.harvestReturnLabel = returnLabel;
            selectTab("Harvest Log");
        }

        @Override public void showHarvestEntry(String returnTab, String returnLabel) {
            harvestEntryReturnTab = returnTab;
            harvestEntryReturnLabel = returnLabel;
            selectTab("Harvest Log");
        }

        @Override public void showMarketDay(String returnTab, String returnLabel) {
            marketDayReturnTab = returnTab;
            marketDayReturnLabel = returnLabel;
            selectTab("Market Day");
        }

        @Override public void showIrrigation(String returnTab, String returnLabel) {
            irrigationReturnTab = returnTab;
            irrigationReturnLabel = returnLabel;
            selectTab("Irrigation & Care");
        }
    }

    private static class ReadOnlyHost extends TestHost {
        @Override public boolean canMutateData() { return false; }
        @Override public boolean canClearHistory() { return false; }
    }

    private static class HandHost extends TestHost {
        @Override public boolean canMutateData() { return true; }
        @Override public boolean canClearHistory() { return false; }
    }
}
