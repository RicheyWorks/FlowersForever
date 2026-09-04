package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executive Dashboard — inventory KPIs, week harvest/revenue, charts, alerts,
 * and quick actions into Harvest / CRM / Reports / Trends.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DashboardTab implements FlowerFarmTab {

    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final int MAX_BARS = 15;

    private final InventoryService inventoryService;
    private final HarvestService harvestService;
    private final OrderService orderService;
    private final IrrigationAdvisorService irrigationAdvisorService;
    private final MarketDayPackingService marketDayPackingService;
    private final MorningBriefingService morningBriefingService;
    private final DayCloseoutService dayCloseoutService;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final FarmFileChooser files;

    private JPanel panel;
    private JLabel totalItemsLabel;
    private JLabel totalValueLabel;
    private JLabel lowStockLabel;
    private JLabel weekHarvestLabel;
    private JLabel weekHarvestDeltaLabel;
    private JLabel weekRevenueLabel;
    private JLabel weekRevenuePipelineLabel;
    private JLabel lastUpdatedLabel;
    private JButton refreshOverviewButton;
    private boolean refreshingOverview;
    private boolean refreshQueued;
    private JTextArea alertsArea;
    private JLabel lowStockSubtitle;
    private JLabel priorityStatusLabel;
    private JLabel priorityDetailLabel;
    private JButton priorityActionButton;
    private JComboBox<String> priorityQueue;
    private JButton morningBriefingButton;
    private JButton dayCloseoutButton;
    private JLabel dailySheetStatusLabel;
    private boolean preparingDailySheet;
    private List<DashboardPriority> rankedPriorities = List.of();
    private boolean priorityQueueUpdating;
    private DashboardPriority selectedDashboardPriority;
    private volatile HarvestService.BedProductionReport lastGoodBedProduction;
    private volatile IrrigationAdvisorService.IrrigationAdvice lastGoodIrrigation;
    private volatile MarketDayPackingService.MarketDayPlan lastGoodPacking;

    private final JPanel[] kpiCards = new JPanel[4];
    private SparklinePanel weekHarvestSpark;
    private SparklinePanel weekRevenueSpark;

    private final DefaultPieDataset pieDataset = new DefaultPieDataset();
    private final DefaultCategoryDataset barDataset = new DefaultCategoryDataset();
    private JFreeChart inventoryValueChart;
    private JFreeChart stockQuantityChart;

    public DashboardTab(InventoryService inventoryService,
                        HarvestService harvestService,
                        OrderService orderService,
                        IrrigationAdvisorService irrigationAdvisorService,
                        MarketDayPackingService marketDayPackingService,
                        MorningBriefingService morningBriefingService,
                        DayCloseoutService dayCloseoutService,
                        TabHost host) {
        this(inventoryService, harvestService, orderService, irrigationAdvisorService,
                marketDayPackingService, morningBriefingService, dayCloseoutService, host,
                FarmDialogs.swing(), FarmFileChooser.swing());
    }

    public DashboardTab(InventoryService inventoryService,
                        HarvestService harvestService,
                        OrderService orderService,
                        IrrigationAdvisorService irrigationAdvisorService,
                        MarketDayPackingService marketDayPackingService,
                        MorningBriefingService morningBriefingService,
                        DayCloseoutService dayCloseoutService,
                        TabHost host,
                        FarmDialogs dialogs,
                        FarmFileChooser files) {
        this.inventoryService = inventoryService;
        this.harvestService = harvestService;
        this.orderService = orderService;
        this.irrigationAdvisorService = irrigationAdvisorService;
        this.marketDayPackingService = marketDayPackingService;
        this.morningBriefingService = morningBriefingService;
        this.dayCloseoutService = dayCloseoutService;
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
        this.files = java.util.Objects.requireNonNull(files, "files");
    }

    @Override public String getTabTitle() { return "Dashboard"; }

    @Override
    public String getDescription() {
        return "Weekly harvest and sales overview, alerts, and quick actions";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    @Override
    public void initialize() {
        refreshData();
    }

    @Override
    public void refreshData() {
        if (panel == null) {
            return;
        }
        startOverviewRefresh();
    }

    void startOverviewRefresh() {
        if (refreshingOverview) {
            refreshQueued = true;
            showOverviewState("Refresh queued · current snapshot finishing",
                    "A newer dashboard refresh will start as soon as the current snapshot returns.",
                    FarmUi.Tone.INFO);
            return;
        }
        refreshingOverview = true;
        refreshOverviewButton.setEnabled(false);
        lockPriorityActionsForRefresh();
        showOverviewState("Refreshing overview…",
                "Loading inventory, harvest, order, packing, and watering data in the background.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Refreshing the dashboard overview…");
        }

        new SwingWorker<DashboardSnapshot, Void>() {
            @Override
            protected DashboardSnapshot doInBackground() {
                return loadDashboardSnapshot();
            }

            @Override
            protected void done() {
                try {
                    DashboardSnapshot snapshot = get();
                    if (!refreshQueued) {
                        applyDashboardSnapshot(snapshot);
                        if (host != null) {
                            host.setStatus(snapshot.partialFailures().isEmpty()
                                    ? "Dashboard overview refreshed."
                                    : "Dashboard core metrics refreshed; "
                                            + partialSourceSummary(snapshot.partialFailures())
                                            + " unavailable. Retained context is marked when available.");
                        }
                    } else if (host != null) {
                        host.setStatus("A newer dashboard refresh is queued; discarding the older snapshot.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (!refreshQueued) {
                        failOverviewRefresh(exception);
                    }
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (!refreshQueued) {
                        failOverviewRefresh(exception.getCause() == null
                                ? exception : exception.getCause());
                    }
                } catch (RuntimeException exception) {
                    if (!refreshQueued) {
                        failOverviewRefresh(exception);
                    }
                } finally {
                    finishOverviewRefresh();
                }
            }
        }.execute();
    }

    private DashboardSnapshot loadDashboardSnapshot() {
        List<Item> items = List.copyOf(inventoryService.getAllItems());
        InventoryService.InventoryKpiSnapshot inv =
                inventoryService.inventoryKpis(LOW_STOCK_THRESHOLD);
        double weekHarvest = harvestService.totalQuantityLast7Days();
        double priorHarvest = harvestService.totalQuantityPrior7Days();
        OrderService.WeekRevenueSummary rev = orderService.weekRevenueSummary();
        double priorRev = orderService.realizedRevenuePrior7Days();
        double[] harvestDays = harvestService.dailyQuantitiesLast7Days();
        double[] revenueDays = orderService.dailyRevenueLast7Days();

        List<DashboardSourceFailure> partialFailures = new ArrayList<>();
        HarvestService.BedProductionReport beds = null;
        if (weekHarvest > 0) {
            try {
                beds = harvestService.productionByBedLast7Days();
            } catch (RuntimeException failure) {
                beds = lastGoodBedProduction;
                partialFailures.add(sourceFailure(
                        "Bed production", failure, beds != null));
            }
        }
        IrrigationAdvisorService.IrrigationAdvice irrigation = null;
        if (irrigationAdvisorService != null) {
            try {
                irrigation = irrigationAdvisorService.adviseClimatology();
            } catch (RuntimeException failure) {
                irrigation = lastGoodIrrigation;
                partialFailures.add(sourceFailure(
                        "Irrigation", failure, irrigation != null));
            }
        }
        MarketDayPackingService.MarketDayPlan packing = null;
        if (marketDayPackingService != null) {
            try {
                packing = marketDayPackingService.planForDay(LocalDate.now());
            } catch (RuntimeException failure) {
                packing = lastGoodPacking;
                partialFailures.add(sourceFailure(
                        "Market Day", failure, packing != null));
            }
        }
        return new DashboardSnapshot(items, inv, weekHarvest, priorHarvest,
                rev, priorRev, harvestDays, revenueDays, beds, irrigation, packing,
                List.copyOf(partialFailures));
    }

    private static DashboardSourceFailure sourceFailure(
            String source, RuntimeException failure, boolean retained) {
        return new DashboardSourceFailure(source,
                compactMessage(FarmUi.actionableError("Loading " + source + " context", failure)),
                retained);
    }

    private void applyDashboardSnapshot(DashboardSnapshot snapshot) {
        List<Item> items = snapshot.items();
        InventoryService.InventoryKpiSnapshot inv = snapshot.inventory();
        double weekHarvest = snapshot.weekHarvest();
        double priorHarvest = snapshot.priorHarvest();
        OrderService.WeekRevenueSummary rev = snapshot.revenue();
        double priorRev = snapshot.priorRevenue();

        totalItemsLabel.setText(String.format("%d products · %,d units · cost $%,.0f",
                inv.skuCount(), inv.totalUnits(), inv.costBasis()));
        totalValueLabel.setText(String.format("$%,.2f", inv.sellValue()));
        lowStockLabel.setText(String.valueOf(inv.lowStockCount()));
        weekHarvestLabel.setText(String.format("%,.0f", weekHarvest));
        if (weekHarvestDeltaLabel != null) {
            Double harvestPct = priorHarvest <= 0 ? null
                    : ((weekHarvest - priorHarvest) / priorHarvest) * 100.0;
            weekHarvestDeltaLabel.setText(formatWow("vs prior wk",
                    harvestPct, weekHarvest - priorHarvest, false));
        }
        // Primary week revenue = realized (FULFILLED only) — not inflated by unfulfilled pipeline
        weekRevenueLabel.setText(String.format("$%,.2f", rev.realized()));
        if (weekRevenuePipelineLabel != null) {
            Double revPct = priorRev <= 0 ? null
                    : ((rev.realized() - priorRev) / priorRev) * 100.0;
            String wow = formatWow("prior", revPct, rev.realized() - priorRev, true);
            weekRevenuePipelineLabel.setText(String.format(
                    "<html>confirmed $%,.2f · booked $%,.2f<br/>%s</html>",
                    rev.pipeline(), rev.booked(), wow));
        }

        long lowStockCount = inv.lowStockCount();

        if (weekHarvestSpark != null) {
            double[] harvestDays = snapshot.harvestDays();
            weekHarvestSpark.setValues(harvestDays);
            weekHarvestSpark.setToolTipText(formatSparkTip("Harvest amount", harvestDays, false));
        }
        if (weekRevenueSpark != null) {
            double[] revDays = snapshot.revenueDays(); // realized only
            weekRevenueSpark.setValues(revDays);
            weekRevenueSpark.setToolTipText(formatSparkTip("Completed sales $", revDays, true));
        }

        applyLowStockCardStyle(lowStockCount > 0);

        updateOptionalContextCache(snapshot);
        updateDashboardFreshness(snapshot.partialFailures());

        rebuildAlerts(items, rev, weekHarvest,
                snapshot.beds(), snapshot.irrigation(), snapshot.packing(),
                snapshot.partialFailures());
        rebuildValueByCategory(items);
        rebuildQuantityByItem(items);
    }

    private void updateOptionalContextCache(DashboardSnapshot snapshot) {
        if (!sourceFailed(snapshot.partialFailures(), "Bed production")
                && snapshot.beds() != null) {
            lastGoodBedProduction = snapshot.beds();
        }
        if (!sourceFailed(snapshot.partialFailures(), "Irrigation")
                && snapshot.irrigation() != null) {
            lastGoodIrrigation = snapshot.irrigation();
        }
        if (!sourceFailed(snapshot.partialFailures(), "Market Day")
                && snapshot.packing() != null) {
            lastGoodPacking = snapshot.packing();
        }
    }

    private void updateDashboardFreshness(List<DashboardSourceFailure> failures) {
        if (lastUpdatedLabel == null) {
            return;
        }
        String updated = "Updated "
                + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + " · 7-day view";
        if (failures.isEmpty()) {
            lastUpdatedLabel.setText(updated);
            lastUpdatedLabel.setToolTipText(
                    "Week metrics use the last 7 days; revenue includes completed orders only.");
            lastUpdatedLabel.getAccessibleContext().setAccessibleDescription(
                    "Dashboard snapshot updated successfully. Week metrics use the last 7 days; "
                            + "revenue includes completed orders only.");
            lastUpdatedLabel.setForeground(FarmUi.mutedColor());
            return;
        }
        String sources = partialSourceSummary(failures);
        String detail = failures.stream()
                .map(failure -> failure.source() + ": " + failure.detail()
                        + (failure.retained()
                        ? " Last-good context retained." : " No prior context available."))
                .collect(java.util.stream.Collectors.joining(" "));
        lastUpdatedLabel.setText(updated + " · " + failures.size()
                + " context" + (failures.size() == 1 ? "" : "s") + " unavailable");
        lastUpdatedLabel.setToolTipText(
                "Core inventory, harvest, and order metrics are current. " + detail);
        lastUpdatedLabel.getAccessibleContext().setAccessibleDescription(
                "Dashboard partially updated. Core inventory, harvest, and order metrics are current. "
                        + sources + " unavailable. " + detail);
        lastUpdatedLabel.setForeground(FarmUi.toneForegroundColor(FarmUi.Tone.WARNING));
    }

    private static String partialSourceSummary(List<DashboardSourceFailure> failures) {
        return failures.stream().map(DashboardSourceFailure::source)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static boolean sourceFailed(
            List<DashboardSourceFailure> failures, String source) {
        return failures.stream().anyMatch(failure -> failure.source().equals(source));
    }

    private void failOverviewRefresh(Throwable failure) {
        String detail = compactMessage(FarmUi.actionableError(
                "Refreshing the dashboard overview", failure));
        showOverviewState("Refresh failed · retry",
                detail + " Existing dashboard values were kept; retry when the data source is ready.",
                FarmUi.Tone.DANGER);
        restorePriorityActionsAfterFailure();
        if (host != null) {
            host.setStatus("Dashboard refresh failed. Existing values were kept; review the status and retry.");
        }
    }

    private void finishOverviewRefresh() {
        refreshingOverview = false;
        refreshOverviewButton.setEnabled(true);
        if (refreshQueued) {
            refreshQueued = false;
            startOverviewRefresh();
        }
    }

    private void showOverviewState(String message, String description, FarmUi.Tone tone) {
        lastUpdatedLabel.setText(message);
        lastUpdatedLabel.setToolTipText(description);
        lastUpdatedLabel.getAccessibleContext().setAccessibleDescription(description);
        lastUpdatedLabel.setForeground(FarmUi.toneForegroundColor(tone));
    }

    private void lockPriorityActionsForRefresh() {
        if (priorityStatusLabel == null || priorityDetailLabel == null
                || priorityQueue == null || priorityActionButton == null) {
            return;
        }
        FarmUi.updateStatusBadge(priorityStatusLabel,
                "Checking priorities…", FarmUi.Tone.INFO);
        priorityDetailLabel.setText(
                "Farm-wide snapshot is refreshing; priority actions are temporarily locked.");
        priorityDetailLabel.getAccessibleContext().setAccessibleDescription(
                "Farm-wide snapshot is refreshing. Priority actions will unlock when current data is ready.");
        priorityQueue.setEnabled(false);
        priorityActionButton.setEnabled(false);
        priorityActionButton.setToolTipText(
                "Wait for the current farm-wide snapshot before opening a priority.");
    }

    private void restorePriorityActionsAfterFailure() {
        if (priorityStatusLabel == null || priorityDetailLabel == null
                || priorityQueue == null || priorityActionButton == null) {
            return;
        }
        if (rankedPriorities.isEmpty()) {
            FarmUi.updateStatusBadge(priorityStatusLabel,
                    "Priorities unavailable · retry", FarmUi.Tone.DANGER);
            priorityDetailLabel.setText(
                    "No current priority is available; refresh again before starting dashboard work.");
            priorityDetailLabel.getAccessibleContext().setAccessibleDescription(
                    "Priority loading failed and no earlier snapshot is available. Refresh again before starting dashboard work.");
            priorityQueue.setEnabled(false);
            priorityActionButton.setEnabled(false);
            return;
        }
        int selected = priorityQueue.getSelectedIndex();
        if (selected < 0 || selected >= rankedPriorities.size()) {
            selected = 0;
            priorityQueue.setSelectedIndex(selected);
        }
        priorityQueue.setEnabled(true);
        selectDashboardPriority(selected);
        FarmUi.updateStatusBadge(priorityStatusLabel,
                "Priority " + (selected + 1) + " of " + rankedPriorities.size()
                        + " · last good snapshot",
                FarmUi.Tone.DANGER);
        priorityDetailLabel.setText(priorityDetailLabel.getText()
                + "  Snapshot refresh failed; verify conditions in the workspace.");
        priorityDetailLabel.getAccessibleContext().setAccessibleDescription(
                priorityDetailLabel.getAccessibleContext().getAccessibleDescription()
                        + " The snapshot refresh failed, so verify current conditions in the workspace.");
    }

    private record DashboardSnapshot(
            List<Item> items,
            InventoryService.InventoryKpiSnapshot inventory,
            double weekHarvest,
            double priorHarvest,
            OrderService.WeekRevenueSummary revenue,
            double priorRevenue,
            double[] harvestDays,
            double[] revenueDays,
            HarvestService.BedProductionReport beds,
            IrrigationAdvisorService.IrrigationAdvice irrigation,
            MarketDayPackingService.MarketDayPlan packing,
            List<DashboardSourceFailure> partialFailures) {
        private DashboardSnapshot {
            items = List.copyOf(items);
            harvestDays = harvestDays == null ? new double[0] : harvestDays.clone();
            revenueDays = revenueDays == null ? new double[0] : revenueDays.clone();
            partialFailures = List.copyOf(partialFailures);
        }
    }

    private record DashboardSourceFailure(String source, String detail, boolean retained) { }

    private static String formatWow(String prefix, Double pct, double absDelta, boolean money) {
        String abs = money
                ? String.format("%s$%,.0f", absDelta >= 0 ? "+" : "−", Math.abs(absDelta))
                : String.format("%s%,.0f", absDelta >= 0 ? "+" : "−", Math.abs(absDelta));
        if (pct == null) {
            return prefix + " · " + abs + " (no prior)";
        }
        return String.format("%s · %s (%.0f%%)", prefix, abs, pct);
    }

    private void applyLowStockCardStyle(boolean warning) {
        if (kpiCards[3] == null) {
            return;
        }
        FarmUi.Tone tone = warning ? FarmUi.Tone.DANGER : FarmUi.Tone.SUCCESS;
        kpiCards[3].setBackground(FarmUi.toneSurfaceColor(tone));
        kpiCards[3].setBorder(dashboardCardBorder(tone));
        if (lowStockLabel != null) {
            lowStockLabel.setForeground(warning
                    ? FarmUi.toneForegroundColor(FarmUi.Tone.DANGER)
                    : FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS));
        }
        if (lowStockSubtitle != null) {
            lowStockSubtitle.setText(warning
                    ? "items ≤ " + LOW_STOCK_THRESHOLD + " — restock!"
                    : "items ≤ " + LOW_STOCK_THRESHOLD);
        }
    }

    private static FarmUi.Tone kpiTone(int index) {
        return switch (index) {
            case 1 -> FarmUi.Tone.DANGER;
            case 2 -> FarmUi.Tone.INFO;
            case 3 -> FarmUi.Tone.WARNING;
            default -> FarmUi.Tone.SUCCESS;
        };
    }

    private static javax.swing.border.Border dashboardCardBorder(FarmUi.Tone tone) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(FarmUi.borderColor()),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(4, 0, 0, 0,
                                FarmUi.toneForegroundColor(tone)),
                        BorderFactory.createEmptyBorder(11, 15, 13, 15)));
    }

    private static String formatSparkTip(String prefix, double[] days, boolean money) {
        if (days == null || days.length == 0) {
            return prefix + " (no data)";
        }
        LocalDate from = LocalDate.now().minusDays(days.length - 1L);
        StringBuilder sb = new StringBuilder("<html><b>").append(prefix)
                .append("</b> (oldest → today)<br/>");
        for (int i = 0; i < days.length; i++) {
            sb.append(from.plusDays(i)).append(": ");
            if (money) {
                sb.append(String.format("$%.0f", days[i]));
            } else {
                sb.append(String.format("%.0f", days[i]));
            }
            sb.append("<br/>");
        }
        sb.append("</html>");
        return sb.toString();
    }

    private void rebuildAlerts(List<Item> items, OrderService.WeekRevenueSummary rev,
                               double weekHarvest,
                               HarvestService.BedProductionReport beds,
                               IrrigationAdvisorService.IrrigationAdvice irrigation,
                               MarketDayPackingService.MarketDayPlan packing,
                               List<DashboardSourceFailure> partialFailures) {
        StringBuilder alerts = new StringBuilder();
        List<DashboardPriority> priorities = new ArrayList<>();
        if (!partialFailures.isEmpty()) {
            alerts.append("⚠ Partial update: ")
                    .append(partialSourceSummary(partialFailures))
                    .append(" unavailable. Retained context is marked when available; "
                            + "core metrics are current.\n");
        }
        List<Item> lowItems = items.stream()
                .filter(i -> i.getQuantity() <= LOW_STOCK_THRESHOLD)
                .sorted((a, b) -> Integer.compare(a.getQuantity(), b.getQuantity()))
                .toList();
        for (Item item : lowItems) {
            alerts.append("• Low stock: ").append(item.getName())
                    .append(" — only ").append(item.getQuantity()).append(" left\n");
        }
        if (!lowItems.isEmpty()) {
            Item lowest = lowItems.get(0);
            priorities.add(new DashboardPriority(30,
                    "Restock " + lowest.getName() + " · " + lowest.getQuantity() + " left",
                    "Inventory",
                    "Opened inventory for the lowest-stock products.",
                    lowItems.stream().map(Item::getName).distinct().toList(), null));
        }

        if (rev != null) {
            if (rev.confirmedOrderCount() > 0) {
                alerts.append("• Ready to pack: ").append(rev.confirmedOrderCount())
                        .append(" confirmed order(s) — $")
                        .append(String.format("%,.2f", rev.pipeline())).append(" total\n");
                priorities.add(new DashboardPriority(20,
                        rev.confirmedOrderCount() + " confirmed order(s) ready to pack",
                        "Market Day",
                        "Opened Market Day for confirmed orders."));
            }
            if (rev.draftOrderCount() > 0) {
                alerts.append("• Needs confirmation: ").append(rev.draftOrderCount())
                        .append(" draft order(s) ($")
                        .append(String.format("%,.2f", rev.draft())).append(")\n");
                priorities.add(new DashboardPriority(50,
                        "Confirm " + rev.draftOrderCount() + " draft order(s)",
                        "Customers & Orders",
                        "Opened draft orders for confirmation.", List.of(), "DRAFT"));
            }
            if (rev.realized() <= 0 && rev.pipeline() <= 0) {
                alerts.append("• No completed or confirmed sales in the last 7 days\n");
            }
        }
        if (weekHarvest <= 0) {
            alerts.append("• No harvest logged in the last 7 days — open Harvest Log\n");
            priorities.add(new DashboardPriority(40,
                    "No harvest logged in the last 7 days",
                    "Harvest Log",
                    "Opened Harvest Log to record the latest cut."));
        } else if (beds != null && !beds.beds().isEmpty()) {
            HarvestService.BedProduction top = beds.beds().get(0);
            alerts.append("• Top bed this week: ").append(top.bed())
                    .append(" (").append(String.format("%.0f", top.totalQuantity()))
                    .append(" harvested) across ").append(beds.bedCount())
                    .append(" bed(s)")
                    .append(sourceFailed(partialFailures, "Bed production")
                            ? " · previous snapshot · verify\n" : "\n");
        }

        if (irrigation != null
                && (irrigation.priority() == IrrigationAdvisorService.Priority.HIGH
                || irrigation.priority() == IrrigationAdvisorService.Priority.MEDIUM)) {
            alerts.append("• Watering priority: ")
                    .append(irrigation.priority().name().toLowerCase())
                    .append(" — ").append(irrigation.headline())
                    .append(sourceFailed(partialFailures, "Irrigation")
                            ? " · previous snapshot · verify\n" : "\n");
            if (!sourceFailed(partialFailures, "Irrigation")) {
                priorities.add(new DashboardPriority(
                        irrigation.priority() == IrrigationAdvisorService.Priority.HIGH ? 10 : 45,
                        "Watering " + irrigation.priority().name().toLowerCase()
                                + " · " + irrigation.headline(),
                        "Irrigation & Care",
                        "Opened irrigation guidance for today."));
            }
        }

        if (packing != null && packing.orderCount() > 0) {
            alerts.append("• Pack today: ").append(packing.orderCount())
                    .append(" confirmed order(s) — $")
                    .append(String.format("%,.2f", packing.pipelineValue()))
                    .append(" total")
                    .append(sourceFailed(partialFailures, "Market Day")
                            ? " · previous snapshot · verify\n" : "\n");
            if (packing.shortfallSkuCount() > 0) {
                alerts.append("• Packing shortfall: ").append(packing.shortfallSkuCount())
                        .append(" product(s) under stock for today's load-out")
                        .append(sourceFailed(partialFailures, "Market Day")
                                ? " · previous snapshot · verify\n" : "\n");
                if (!sourceFailed(partialFailures, "Market Day")) {
                    priorities.add(new DashboardPriority(0,
                            "Resolve " + packing.shortfallSkuCount()
                                    + " packing shortfall(s)",
                            "Market Day",
                            "Opened Market Day to review packing shortfalls."));
                }
            } else if (!sourceFailed(partialFailures, "Market Day")) {
                priorities.add(new DashboardPriority(15,
                        "Pack " + packing.orderCount() + " order(s) for today",
                        "Market Day",
                        "Opened today’s market packing list."));
            }
        }

        if (alerts.length() == 0) {
            alerts.append("All clear — stock healthy, harvest moving, orders on track. Great job!");
        }
        alertsArea.setText(alerts.toString());
        alertsArea.setCaretPosition(0);
        updateDashboardPriority(priorities);
    }

    private void updateDashboardPriority(List<DashboardPriority> priorities) {
        if (priorityStatusLabel == null || priorityActionButton == null
                || priorityQueue == null) {
            return;
        }
        Map<String, DashboardPriority> bestByWorkspace = new LinkedHashMap<>();
        priorities.stream()
                .sorted(java.util.Comparator.comparingInt(DashboardPriority::rank))
                .forEach(priority -> bestByWorkspace.putIfAbsent(
                        priority.targetTab(), priority));
        rankedPriorities = List.copyOf(bestByWorkspace.values());

        priorityQueueUpdating = true;
        priorityQueue.removeAllItems();
        for (DashboardPriority priority : rankedPriorities) {
            priorityQueue.addItem(priority.summary());
        }
        priorityQueueUpdating = false;

        if (rankedPriorities.isEmpty()) {
            selectedDashboardPriority = null;
            FarmUi.updateStatusBadge(
                    priorityStatusLabel, "All caught up", FarmUi.Tone.SUCCESS);
            priorityQueue.setEnabled(false);
            priorityActionButton.setEnabled(false);
            priorityActionButton.setToolTipText("No urgent farm task is waiting.");
            priorityDetailLabel.setText(
                    "No operational task is waiting in the current farm snapshot.");
            priorityDetailLabel.getAccessibleContext().setAccessibleDescription(
                    "No operational task is waiting in the current farm snapshot.");
            return;
        }
        priorityQueue.setEnabled(true);
        priorityQueue.setSelectedIndex(0);
        selectDashboardPriority(0);
    }

    private void selectDashboardPriority(int index) {
        if (index < 0 || index >= rankedPriorities.size()) {
            return;
        }
        DashboardPriority priority = rankedPriorities.get(index);
        String urgency = priority.rank() <= 10 ? "Urgent"
                : priority.rank() <= 30 ? "Next up" : "Follow-up";
        selectedDashboardPriority = priority;
        FarmUi.updateStatusBadge(priorityStatusLabel,
                "Priority " + (index + 1) + " of " + rankedPriorities.size()
                        + " · " + urgency,
                priority.rank() <= 10 ? FarmUi.Tone.DANGER
                        : priority.rank() <= 30 ? FarmUi.Tone.WARNING : FarmUi.Tone.INFO);
        priorityDetailLabel.setText(
                "Do next: " + priority.summary() + "  →  " + priority.targetTab());
        priorityDetailLabel.getAccessibleContext().setAccessibleDescription(
                "Selected " + urgency.toLowerCase() + " priority. " + priority.summary()
                        + ". Opens " + priority.targetTab() + ".");
        priorityActionButton.setEnabled(true);
        priorityActionButton.setToolTipText(
                "Open " + priority.targetTab() + " to address: " + priority.summary());
    }

    private void rebuildValueByCategory(List<Item> items) {
        Map<String, Double> byCategory = new LinkedHashMap<>();
        for (Item item : items) {
            double value = item.getPrice() * item.getQuantity();
            byCategory.merge(item.getCategory(), value, Double::sum);
        }
        pieDataset.clear();
        for (Map.Entry<String, Double> entry : byCategory.entrySet()) {
            pieDataset.setValue(entry.getKey(), entry.getValue());
        }
        if (inventoryValueChart != null
                && inventoryValueChart.getPlot() instanceof PiePlot plot) {
            for (String category : byCategory.keySet()) {
                plot.setSectionPaint(category, categoryColor(category));
            }
        }
    }

    private void rebuildQuantityByItem(List<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Integer.compare(b.getQuantity(), a.getQuantity()));

        barDataset.clear();
        Map<String, Integer> seen = new LinkedHashMap<>();
        int count = 0;
        for (Item item : sorted) {
            if (count++ >= MAX_BARS) {
                break;
            }
            String key = item.getName();
            int dup = seen.merge(key, 1, Integer::sum);
            if (dup > 1) {
                key = key + " (" + dup + ")";
            }
            barDataset.setValue(item.getQuantity(), "Quantity", key);
        }
    }

    private void buildUI() {
        panel = FarmUi.page();

        lastUpdatedLabel = FarmUi.mutedLabel("Overview not refreshed yet");
        lastUpdatedLabel.setName("dashboard.overviewStatus");
        lastUpdatedLabel.getAccessibleContext().setAccessibleName("Dashboard refresh status");
        lastUpdatedLabel.setFont(lastUpdatedLabel.getFont().deriveFont(11f));
        refreshOverviewButton = FarmUi.primaryButton("Refresh overview");
        refreshOverviewButton.setName("dashboard.refreshOverview");
        refreshOverviewButton.setToolTipText(
                "Reload inventory, harvest, order, packing, and watering metrics.");
        refreshOverviewButton.addActionListener(e -> startOverviewRefresh());
        panel.add(FarmUi.pageHeader(
                "Today on the farm",
                "Harvest, sales, stock, packing, and watering priorities in one place.",
                FarmUi.toolbar(lastUpdatedLabel, refreshOverviewButton)), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));

        JPanel kpiPanel = new JPanel(new GridLayout(1, 4, 12, 0));
        kpiPanel.setOpaque(false);
        weekHarvestSpark = new SparklinePanel();
        weekHarvestSpark.setLineColor(new Color(34, 100, 54));
        weekHarvestDeltaLabel = FarmUi.mutedLabel("vs prior wk —");
        kpiCards[0] = createKPICard("Week harvest", weekHarvestLabel = new JLabel("—"), "amount in last 7 days",
                weekHarvestDeltaLabel, weekHarvestSpark, "Log harvest", "Harvest Log");
        weekRevenueSpark = new SparklinePanel();
        weekRevenueSpark.setLineColor(FarmUi.accentColor());
        weekRevenuePipelineLabel = FarmUi.mutedLabel("confirmed —");
        kpiCards[1] = createKPICard("Week revenue", weekRevenueLabel = new JLabel("—"), "from completed orders",
                weekRevenuePipelineLabel, weekRevenueSpark, "Create order", "Customers & Orders");
        totalItemsLabel = FarmUi.mutedLabel("products and units —");
        kpiCards[2] = createKPICard("Inventory value", totalValueLabel = new JLabel("—"), "at sell prices",
                totalItemsLabel, null, "Open Inventory", "Inventory");
        lowStockSubtitle = FarmUi.mutedLabel("items ≤ " + LOW_STOCK_THRESHOLD);
        kpiCards[3] = createKPICard("Low stock", lowStockLabel = new JLabel("—"), null,
                lowStockSubtitle, null, "Review stock", "Inventory");
        for (JPanel card : kpiCards) {
            kpiPanel.add(card);
        }
        center.add(kpiPanel, BorderLayout.NORTH);

        JPanel chartsPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        chartsPanel.add(buildPieChartPanel());
        chartsPanel.add(buildBarChartPanel());

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.setOpaque(false);

        alertsArea = FarmUi.displayArea(5, 40);
        alertsArea.getAccessibleContext().setAccessibleName("Farm operations alerts");
        JPanel alertsPanel = FarmUi.section(
                "Operations alerts",
                "Stock, orders, harvest, packing, and irrigation priorities.",
                new JScrollPane(alertsArea));
        south.add(alertsPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(2, 3, 8, 6));
        actions.setOpaque(false);
        morningBriefingButton = FarmUi.primaryButton("Morning briefing");
        morningBriefingButton.setName("dashboard.morningBriefing");
        FarmUi.initialFocus(morningBriefingButton);
        morningBriefingButton.setToolTipText(
                "Review packing, beds, watering, and low stock for the start of the day.");
        morningBriefingButton.addActionListener(e -> startMorningBriefing());
        actions.add(morningBriefingButton);
        actions.add(actionButton("Log harvest", "Harvest Log",
                "Open harvest log to record today's cut."));
        actions.add(actionButton("Market packing", "Market Day",
                "Build today's packing list / pick sheet for the van."));
        actions.add(actionButton("Create order", "Customers & Orders",
                "Opened Customers & Orders. Choose a customer and add the order details."));
        dayCloseoutButton = FarmUi.secondaryButton("Day closeout");
        dayCloseoutButton.setName("dashboard.dayCloseout");
        dayCloseoutButton.setToolTipText(
                "Review completed sales, harvest, and orders still waiting at day end.");
        dayCloseoutButton.addActionListener(e -> startDayCloseout());
        actions.add(dayCloseoutButton);
        JButton inventory = FarmUi.secondaryButton("Open inventory");
        inventory.addActionListener(e -> go("Inventory", "Opened inventory."));
        actions.add(inventory);

        JComboBox<String> moreTools = new JComboBox<>(new String[]{
                "Low-stock reorder", "Price list", "Weekly farm report",
                "Harvest forecast", "Activity history", "Rose visualizer"
        });
        moreTools.setName("dashboard.moreTools");
        moreTools.getAccessibleContext().setAccessibleName("More farm tools");
        JButton openTool = FarmUi.secondaryButton("Open tool");
        openTool.addActionListener(e -> openDashboardTool((String) moreTools.getSelectedItem()));
        JPanel moreToolsRow = FarmUi.toolbar(new JLabel("More farm tools"), moreTools, openTool);
        dailySheetStatusLabel = FarmUi.statusBadge(
                "Daily sheets ready", FarmUi.Tone.NEUTRAL);
        dailySheetStatusLabel.setName("dashboard.dailySheetStatus");
        dailySheetStatusLabel.getAccessibleContext().setAccessibleName(
                "Morning briefing and day closeout status");
        JPanel actionFooter = new JPanel(new BorderLayout(FarmUi.CONTROL_GAP, 0));
        actionFooter.setOpaque(false);
        actionFooter.add(dailySheetStatusLabel, BorderLayout.WEST);
        actionFooter.add(moreToolsRow, BorderLayout.CENTER);
        JPanel actionWorkspace = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        actionWorkspace.setOpaque(false);
        priorityStatusLabel = FarmUi.statusBadge(
                "Refresh to identify the top priority", FarmUi.Tone.NEUTRAL);
        priorityStatusLabel.setName("dashboard.priorityStatus");
        priorityStatusLabel.getAccessibleContext().setAccessibleName("Top farm priority");
        priorityDetailLabel = FarmUi.formHint(
                "Refresh the overview to build today’s ranked work queue.");
        priorityDetailLabel.setName("dashboard.priorityDetail");
        priorityDetailLabel.getAccessibleContext().setAccessibleName(
                "Selected farm priority details");
        priorityDetailLabel.setVisible(false);
        priorityQueue = new JComboBox<>();
        priorityQueue.setName("dashboard.priorityQueue");
        priorityQueue.setEnabled(false);
        priorityQueue.getAccessibleContext().setAccessibleName("Ranked farm priorities");
        priorityQueue.setToolTipText(priorityDetailLabel.getText());
        priorityQueue.getAccessibleContext().setAccessibleDescription(
                priorityDetailLabel.getText());
        priorityDetailLabel.addPropertyChangeListener("text", event -> {
            String detail = String.valueOf(event.getNewValue());
            priorityQueue.setToolTipText(detail);
            priorityQueue.getAccessibleContext().setAccessibleDescription(detail);
        });
        priorityQueue.addActionListener(event -> {
            if (!priorityQueueUpdating) {
                selectDashboardPriority(priorityQueue.getSelectedIndex());
            }
        });
        priorityActionButton = FarmUi.primaryButton("Open priority");
        priorityActionButton.setEnabled(false);
        priorityActionButton.addActionListener(event -> {
            startDashboardPriority(selectedDashboardPriority);
        });
        JPanel priorityRow = new JPanel(new BorderLayout(FarmUi.CONTROL_GAP, 0));
        priorityRow.setOpaque(false);
        priorityRow.add(priorityStatusLabel, BorderLayout.WEST);
        priorityRow.add(priorityQueue, BorderLayout.CENTER);
        priorityRow.add(priorityActionButton, BorderLayout.EAST);
        JPanel priorityChooser = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        priorityChooser.setOpaque(false);
        priorityChooser.add(priorityRow, BorderLayout.NORTH);
        priorityChooser.add(priorityDetailLabel, BorderLayout.SOUTH);
        actionWorkspace.add(priorityChooser, BorderLayout.NORTH);
        actionWorkspace.add(actions, BorderLayout.CENTER);
        actionWorkspace.add(actionFooter, BorderLayout.SOUTH);
        south.add(FarmUi.section(
                "Today’s work",
                null,
                actionWorkspace), BorderLayout.EAST);

        JTabbedPane workspace = new JTabbedPane();
        workspace.setName("dashboard.workspace");
        workspace.getAccessibleContext().setAccessibleName("Dashboard workspaces");
        workspace.addTab("Today’s work", south);
        workspace.addTab("Inventory charts", chartsPanel);
        center.add(workspace, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        applyTheme(false);
    }

    private JButton actionButton(String label, String tabTitle, String statusMsg) {
        JButton btn = FarmUi.secondaryButton(label);
        btn.getAccessibleContext().setAccessibleName(
                label + " from Today’s work actions");
        btn.setToolTipText(statusMsg);
        btn.addActionListener(e -> openDashboardDestination(tabTitle, statusMsg));
        return btn;
    }

    private void openDashboardDestination(String tabTitle, String statusMsg) {
        if (host == null) {
            return;
        }
        if ("Harvest Log".equals(tabTitle)) {
            host.showHarvestEntry("Dashboard", "Dashboard");
        } else if ("Market Day".equals(tabTitle)) {
            host.showMarketDay("Dashboard", "Dashboard");
        } else {
            go(tabTitle, statusMsg);
        }
    }

    private void openDashboardTool(String selection) {
        if (selection == null) {
            return;
        }
        switch (selection) {
            case "Low-stock reorder" -> go("Inventory",
                    "Open Inventory → Low-stock reorder for the restock sheet.");
            case "Price list" -> go("Inventory",
                    "Open Inventory → Price list for market or wholesale.");
            case "Weekly farm report" -> go("Reports", "Opened weekly farm reports.");
            case "Harvest forecast" -> {
                if (host != null) {
                    host.runTrendAnalysis();
                }
            }
            case "Activity history" -> go("Activity History", "Opened activity history.");
            case "Rose visualizer" -> go("Rose Visualizer", "Opened rose visualizer.");
            default -> {
                // The combo box is fixed; no fallback action is needed.
            }
        }
    }

    private void go(String tabTitle, String statusMsg) {
        if (host != null) {
            host.selectTab(tabTitle);
            host.setStatus(statusMsg);
        }
    }

    private void startDashboardPriority(DashboardPriority priority) {
        if (priority == null || host == null) {
            return;
        }
        if (!priority.inventoryProducts().isEmpty()) {
            host.showInventoryProducts(
                    priority.inventoryProducts(), "Dashboard", "Dashboard");
            return;
        }
        if (priority.orderStatus() != null && !priority.orderStatus().isBlank()) {
            host.showOrdersByStatus(priority.orderStatus(), "Dashboard", "Dashboard");
            return;
        }
        if ("Market Day".equals(priority.targetTab())) {
            host.showMarketDay("Dashboard", "Dashboard");
            return;
        }
        if ("Irrigation & Care".equals(priority.targetTab())) {
            host.showIrrigation("Dashboard", "Dashboard");
            return;
        }
        if ("Harvest Log".equals(priority.targetTab())) {
            host.showHarvestEntry("Dashboard", "Dashboard");
            return;
        }
        go(priority.targetTab(), priority.navigationMessage());
    }

    private record DashboardPriority(int rank, String summary, String targetTab,
                                     String navigationMessage,
                                     List<String> inventoryProducts,
                                     String orderStatus) {
        private DashboardPriority(int rank, String summary, String targetTab,
                                  String navigationMessage) {
            this(rank, summary, targetTab, navigationMessage, List.of(), null);
        }

        private DashboardPriority {
            inventoryProducts = inventoryProducts == null
                    ? List.of() : List.copyOf(inventoryProducts);
        }
    }

    void startMorningBriefing() {
        if (morningBriefingService == null) {
            return;
        }
        if (preparingDailySheet) {
            return;
        }
        beginDailySheet("Preparing morning briefing…",
                "Loading packing, watering, harvest, and stock priorities in the background.");
        new SwingWorker<MorningBriefingService.MorningBriefing, Void>() {
            @Override
            protected MorningBriefingService.MorningBriefing doInBackground() {
                return morningBriefingService.buildOffline();
            }

            @Override
            protected void done() {
                try {
                    completeMorningBriefing(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failDailySheet("Morning briefing", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failDailySheet("Morning briefing",
                            exception.getCause() == null ? exception : exception.getCause());
                } catch (RuntimeException exception) {
                    failDailySheet("Morning briefing", exception);
                }
            }
        }.execute();
    }

    private void completeMorningBriefing(MorningBriefingService.MorningBriefing briefing) {
        finishDailySheetControls();
        List<MorningAction> actions = morningActions(briefing);
        String summary = actions.isEmpty() ? "Morning briefing ready · farm ready"
                : "Morning briefing ready · " + actions.size() + " priorit"
                        + (actions.size() == 1 ? "y" : "ies");
        updateDailySheetStatus(summary,
                "Morning briefing prepared for " + briefing.date() + ". "
                        + (actions.isEmpty() ? "No startup task is waiting."
                        : "The first priority is " + actions.get(0).summary() + "."),
                actions.isEmpty() ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
        if (panel.isShowing()) {
            presentMorningBriefing(briefing);
        } else if (host != null) {
            host.setStatus("Briefing: " + actions.size() + " operational priority(s).");
        }
    }

    private void presentMorningBriefing(MorningBriefingService.MorningBriefing briefing) {
        try {
            List<MorningAction> morningActions = morningActions(briefing);
            JTextArea area = new JTextArea(briefing.plainText(), 24, 68);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setCaretPosition(0);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(680, 460));

            JLabel readiness = FarmUi.statusBadge(
                    morningActions.isEmpty() ? "Farm ready"
                            : morningActions.size() + " morning priorit"
                            + (morningActions.size() == 1 ? "y" : "ies"),
                    morningActions.isEmpty() ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
            JPanel briefingView = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
            briefingView.setOpaque(false);
            JPanel briefingGuidance = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
            briefingGuidance.setOpaque(false);
            briefingGuidance.add(FarmUi.toolbar(readiness, FarmUi.formHint(
                    morningActions.isEmpty()
                            ? "No operational startup task is waiting."
                            : "Choose today’s next job, then refresh after completing it.")),
                    BorderLayout.NORTH);
            MorningActionChooser actionChooser = morningActions.isEmpty()
                    ? null : buildMorningActionChooser(morningActions);
            if (actionChooser != null) {
                briefingGuidance.add(actionChooser.component(), BorderLayout.SOUTH);
            }
            briefingView.add(briefingGuidance, BorderLayout.NORTH);
            briefingView.add(scroll, BorderLayout.CENTER);

            Object[] options = morningActions.isEmpty()
                    ? new Object[]{"Export PDF…", "Close"}
                    : new Object[]{"Start selected task",
                        "Export PDF…", "Close"};
            int choice = dialogs.showOption(panel, briefingView,
                    "Morning briefing — " + briefing.date(),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[options.length - 1]);
            if (!morningActions.isEmpty() && choice == 0) {
                MorningAction next = actionChooser.selectedAction();
                startMorningAction(next);
                return;
            }
            int exportChoice = morningActions.isEmpty() ? 0 : 1;
            if (choice == exportChoice) {
                chooseMorningBriefingExport(briefing);
            } else if (host != null) {
                host.setStatus("Briefing: " + morningActions.size() + " operational priority(s).");
            }
        } catch (Exception ex) {
            failDailySheet("Morning briefing", ex);
        }
    }

    static List<MorningAction> morningActions(
            MorningBriefingService.MorningBriefing briefing) {
        List<MorningAction> actions = new ArrayList<>();
        MarketDayPackingService.MarketDayPlan marketDay = briefing.marketDay();
        if (marketDay != null && marketDay.orderCount() > 0) {
            if (marketDay.shortfallSkuCount() > 0) {
                actions.add(new MorningAction(0, "Market Day",
                        "Resolve " + marketDay.shortfallSkuCount() + " packing shortfall(s)",
                        "Opened Market Day to resolve today’s packing shortfalls.", List.of()));
            } else {
                actions.add(new MorningAction(15, "Market Day",
                        "Pack " + marketDay.orderCount() + " order(s)",
                        "Opened Market Day to begin today’s customer load-out.", List.of()));
            }
        }
        IrrigationAdvisorService.IrrigationAdvice irrigation = briefing.irrigation();
        if (irrigation != null && (irrigation.priority() == IrrigationAdvisorService.Priority.HIGH
                || irrigation.priority() == IrrigationAdvisorService.Priority.MEDIUM)) {
            int rank = irrigation.priority() == IrrigationAdvisorService.Priority.HIGH ? 10 : 45;
            actions.add(new MorningAction(rank, "Irrigation & Care",
                    "Watering " + irrigation.priority().name().toLowerCase()
                            + " · " + irrigation.headline(),
                    "Opened Irrigation & Care for today’s watering plan.", List.of()));
        }
        if (!briefing.lowStock().isEmpty()) {
            List<String> lowStockProducts = briefing.lowStock().stream()
                    .map(MorningBriefingService.LowStockLine::name)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .toList();
            actions.add(new MorningAction(30, "Inventory",
                    "Count or restock " + briefing.lowStock().size() + " low-stock item(s)",
                    "Opened Inventory with the morning low-stock queue.", lowStockProducts));
        }
        if (briefing.weekHarvestQty() <= 0) {
            actions.add(new MorningAction(40, "Harvest Log",
                    "No harvest logged in the last 7 days",
                    "Opened Harvest Log to record the morning cut or review the harvest plan.",
                    List.of()));
        }
        return actions.stream()
                .sorted(java.util.Comparator.comparingInt(MorningAction::rank))
                .toList();
    }

    record MorningAction(int rank, String targetTab, String summary,
                         String navigationMessage, List<String> inventoryProducts) {
        MorningAction {
            inventoryProducts = inventoryProducts == null
                    ? List.of() : List.copyOf(inventoryProducts);
        }

        @Override
        public String toString() {
            return summary + " → " + targetTab;
        }
    }

    void startMorningAction(MorningAction action) {
        if (action == null || host == null) {
            return;
        }
        if (!action.inventoryProducts().isEmpty()) {
            host.showInventoryProducts(
                    action.inventoryProducts(), "Dashboard", "Dashboard");
            host.setStatus(action.navigationMessage() + " "
                    + String.join(", ", action.inventoryProducts()) + ".");
            return;
        }
        if ("Market Day".equals(action.targetTab())) {
            host.showMarketDay("Dashboard", "Dashboard");
            return;
        }
        if ("Irrigation & Care".equals(action.targetTab())) {
            host.showIrrigation("Dashboard", "Dashboard");
            return;
        }
        if ("Harvest Log".equals(action.targetTab())) {
            host.showHarvestEntry("Dashboard", "Dashboard");
            return;
        }
        go(action.targetTab(), action.navigationMessage());
    }

    static MorningActionChooser buildMorningActionChooser(List<MorningAction> actions) {
        List<MorningAction> snapshot = List.copyOf(actions);
        JComboBox<MorningAction> queue = new JComboBox<>(
                snapshot.toArray(MorningAction[]::new));
        queue.setName("dashboard.morningTaskQueue");
        queue.getAccessibleContext().setAccessibleName("Ranked morning task queue");
        JLabel detail = FarmUi.formHint(" ");
        detail.setName("dashboard.morningTaskDetail");
        detail.getAccessibleContext().setAccessibleName(
                "Selected morning task destination and urgency");
        Runnable update = () -> {
            MorningAction selected = (MorningAction) queue.getSelectedItem();
            if (selected == null) {
                detail.setText("No morning task selected.");
                detail.getAccessibleContext().setAccessibleDescription(
                        "No morning task is selected.");
                return;
            }
            String urgency = selected.rank() <= 10 ? "Urgent"
                    : selected.rank() <= 30 ? "Next up" : "Follow-up";
            String guidance = urgency + " · Opens " + selected.targetTab()
                    + " · " + selected.navigationMessage();
            detail.setText(guidance);
            detail.getAccessibleContext().setAccessibleDescription(
                    "Selected " + urgency.toLowerCase() + " morning task: "
                            + selected.summary() + ". Opens " + selected.targetTab() + ". "
                            + selected.navigationMessage());
        };
        queue.addActionListener(event -> update.run());
        update.run();
        JPanel component = new JPanel(new BorderLayout(FarmUi.CONTROL_GAP, 0));
        component.setOpaque(false);
        JLabel label = new JLabel("Start:");
        label.setLabelFor(queue);
        label.setDisplayedMnemonic('S');
        component.add(label, BorderLayout.WEST);
        component.add(queue, BorderLayout.CENTER);
        component.add(detail, BorderLayout.SOUTH);
        return new MorningActionChooser(component, queue);
    }

    record MorningActionChooser(JPanel component, JComboBox<MorningAction> queue) {
        MorningAction selectedAction() {
            return (MorningAction) queue.getSelectedItem();
        }
    }

    void startDayCloseout() {
        if (dayCloseoutService == null) {
            return;
        }
        if (preparingDailySheet) {
            return;
        }
        beginDailySheet("Preparing day closeout…",
                "Loading completed sales, harvests, open orders, and stock in the background.");
        new SwingWorker<DayCloseoutService.DayCloseout, Void>() {
            @Override
            protected DayCloseoutService.DayCloseout doInBackground() {
                return dayCloseoutService.build();
            }

            @Override
            protected void done() {
                try {
                    completeDayCloseout(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failDailySheet("Day closeout", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failDailySheet("Day closeout",
                            exception.getCause() == null ? exception : exception.getCause());
                } catch (RuntimeException exception) {
                    failDailySheet("Day closeout", exception);
                }
            }
        }.execute();
    }

    private void completeDayCloseout(DayCloseoutService.DayCloseout closeout) {
        finishDailySheetControls();
        List<CloseoutAction> actions = closeoutActions(closeout);
        String summary = actions.isEmpty() ? "Day closeout ready · no blockers"
                : "Day closeout ready · " + actions.size() + " follow-ups";
        updateDailySheetStatus(summary,
                "Day closeout prepared for " + closeout.date() + ". "
                        + (actions.isEmpty() ? "No operational blocker remains."
                        : "Resolve first: " + actions.get(0).summary() + "."),
                actions.isEmpty() ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
        if (panel.isShowing()) {
            presentDayCloseout(closeout);
        } else if (host != null) {
            host.setStatus("Closeout: " + closeout.fulfilledCount() + " fulfilled · "
                    + actions.size() + " follow-up area(s).");
        }
    }

    private void presentDayCloseout(DayCloseoutService.DayCloseout closeout) {
        try {
            List<CloseoutAction> followUps = closeoutActions(closeout);
            JTextArea area = new JTextArea(closeout.plainText(), 24, 68);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setCaretPosition(0);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(680, 460));

            JLabel readiness = FarmUi.statusBadge(
                    followUps.isEmpty() ? "Ready to close"
                            : followUps.size() + " follow-up area"
                            + (followUps.size() == 1 ? "" : "s"),
                    followUps.isEmpty() ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
            JPanel closeoutView = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
            closeoutView.setOpaque(false);
            JPanel closeoutGuidance = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
            closeoutGuidance.setOpaque(false);
            closeoutGuidance.add(FarmUi.toolbar(readiness, FarmUi.formHint(
                    followUps.isEmpty()
                            ? "No operational blockers remain for today."
                            : "Choose a follow-up, resolve it, then rebuild closeout.")),
                    BorderLayout.NORTH);
            CloseoutActionChooser actionChooser = followUps.isEmpty()
                    ? null : buildCloseoutActionChooser(followUps);
            if (actionChooser != null) {
                closeoutGuidance.add(actionChooser.component(), BorderLayout.SOUTH);
            }
            closeoutView.add(closeoutGuidance, BorderLayout.NORTH);
            closeoutView.add(scroll, BorderLayout.CENTER);

            Object[] options = followUps.isEmpty()
                    ? new Object[]{"Export PDF…", "Close"}
                    : new Object[]{"Open selected follow-up",
                        "Export PDF…", "Close"};
            int choice = dialogs.showOption(panel, closeoutView,
                    "Day closeout — " + closeout.date(),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[options.length - 1]);
            if (!followUps.isEmpty() && choice == 0) {
                CloseoutAction next = actionChooser.selectedAction();
                startCloseoutAction(next);
                return;
            }
            int exportChoice = followUps.isEmpty() ? 0 : 1;
            if (choice == exportChoice) {
                chooseDayCloseoutExport(closeout);
            } else if (host != null) {
                host.setStatus("Closeout: " + closeout.fulfilledCount() + " fulfilled · "
                        + followUps.size() + " follow-up area(s).");
            }
        } catch (Exception ex) {
            failDailySheet("Day closeout", ex);
        }
    }

    void chooseMorningBriefingExport(MorningBriefingService.MorningBriefing briefing) {
        File suggested = new File("morning-briefing-" + briefing.date() + ".pdf");
        var selected = files.chooseSave(panel, "Save morning briefing PDF", suggested);
        if (selected.isEmpty()) {
            cancelDailySheetSave("Morning briefing", false);
            return;
        }
        File destination = selected.get();
        if (!approveDailySheetReplacement(destination, "morning briefing")) {
            cancelDailySheetSave("Morning briefing", true);
            return;
        }
        startMorningBriefingExport(briefing, destination.toPath());
    }

    void chooseDayCloseoutExport(DayCloseoutService.DayCloseout closeout) {
        File suggested = new File("day-closeout-" + closeout.date() + ".pdf");
        var selected = files.chooseSave(panel, "Save day closeout PDF", suggested);
        if (selected.isEmpty()) {
            cancelDailySheetSave("Day closeout", false);
            return;
        }
        File destination = selected.get();
        if (!approveDailySheetReplacement(destination, "day closeout")) {
            cancelDailySheetSave("Day closeout", true);
            return;
        }
        startDayCloseoutExport(closeout, destination.toPath());
    }

    private boolean approveDailySheetReplacement(File file, String sheet) {
        if (!file.exists()) {
            return true;
        }
        return dialogs.showConfirm(panel,
                "A file named " + file.getName() + " already exists. Replace it?",
                "Replace existing " + sheet + "?",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    private void cancelDailySheetSave(String sheet, boolean existingFile) {
        String message = existingFile
                ? sheet + " not saved · existing file kept"
                : sheet + " ready · save cancelled";
        String description = existingFile
                ? "The selected file was not replaced; the prepared daily sheet remains available."
                : "No file was written; prepare the daily sheet again when ready to save.";
        updateDailySheetStatus(message, description, FarmUi.Tone.NEUTRAL);
        if (host != null) {
            host.setStatus(existingFile
                    ? sheet + " was not saved. The existing file was kept."
                    : sheet + " save cancelled. No file was changed.");
        }
    }

    private void beginDailySheet(String label, String description) {
        preparingDailySheet = true;
        morningBriefingButton.setEnabled(false);
        dayCloseoutButton.setEnabled(false);
        updateDailySheetStatus(label, description, FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus(label);
        }
    }

    void startMorningBriefingExport(MorningBriefingService.MorningBriefing briefing,
                                    Path destination) {
        if (briefing == null || destination == null || preparingDailySheet) {
            return;
        }
        beginDailySheet("Saving morning briefing…",
                "Generating the PDF and safely writing the selected file in the background.");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return writeDailySheetPdf(
                        destination, morningBriefingService.generatePdf(briefing));
            }

            @Override
            protected void done() {
                finishDailySheetExport("Morning briefing", this);
            }
        }.execute();
    }

    void startDayCloseoutExport(DayCloseoutService.DayCloseout closeout,
                                Path destination) {
        if (closeout == null || destination == null || preparingDailySheet) {
            return;
        }
        beginDailySheet("Saving day closeout…",
                "Generating the PDF and safely writing the selected file in the background.");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return writeDailySheetPdf(destination, dayCloseoutService.generatePdf(closeout));
            }

            @Override
            protected void done() {
                finishDailySheetExport("Day closeout", this);
            }
        }.execute();
    }

    private void finishDailySheetExport(String sheet, SwingWorker<Path, Void> worker) {
        try {
            Path destination = java.util.Objects.requireNonNull(
                    worker.get(), "Daily-sheet export returned no destination.");
            finishDailySheetControls();
            Path destinationFileName = destination.getFileName();
            String fileName = destinationFileName == null
                    ? destination.toString() : destinationFileName.toString();
            updateDailySheetStatus(sheet + " saved · " + fileName,
                    sheet + " PDF saved successfully to " + destination.toAbsolutePath() + ".",
                    FarmUi.Tone.SUCCESS);
            if (host != null) {
                host.setStatus(sheet + " saved as “" + fileName + "”.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failDailySheetExport(sheet, exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            failDailySheetExport(sheet,
                    exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            failDailySheetExport(sheet, exception);
        }
    }

    private void failDailySheetExport(String sheet, Throwable failure) {
        finishDailySheetControls();
        String detail = compactMessage(FarmUi.actionableError(
                "Saving the " + sheet.toLowerCase(java.util.Locale.ROOT), failure));
        String recovery = detail
                + " Review the selected destination and retry the export.";
        updateDailySheetStatus(sheet + " export failed · retry available",
                recovery, FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus(sheet + " was not saved. Review the status and retry the export.");
        }
        if (panel.isShowing()) {
            dialogs.showMessage(panel, recovery,
                    sheet + " export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Path writeDailySheetPdf(Path destination, byte[] pdf) throws Exception {
        if (pdf == null || pdf.length == 0) {
            throw new IllegalStateException("The generated PDF was empty.");
        }
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("Choose an existing destination folder.");
        }
        Path targetFileName = target.getFileName();
        String leaf = targetFileName == null ? "daily-sheet" : targetFileName.toString();
        Path temporary = Files.createTempFile(parent, "." + leaf + "-", ".tmp");
        try {
            Files.write(temporary, pdf);
            try {
                return Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                return Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void finishDailySheetControls() {
        preparingDailySheet = false;
        morningBriefingButton.setEnabled(true);
        dayCloseoutButton.setEnabled(true);
    }

    private void failDailySheet(String sheet, Throwable failure) {
        finishDailySheetControls();
        String detail = FarmUi.actionableError("Preparing the "
                + sheet.toLowerCase(java.util.Locale.ROOT), failure);
        String recovery = compactMessage(detail)
                + " No daily sheet was opened; correct the data issue and retry.";
        updateDailySheetStatus(sheet + " failed · retry available",
                recovery, FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus("The " + sheet.toLowerCase(java.util.Locale.ROOT)
                    + " could not be prepared. Review the status and retry.");
        }
        if (panel.isShowing()) {
            dialogs.showMessage(panel, recovery,
                    sheet + " failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateDailySheetStatus(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(dailySheetStatusLabel, message, tone);
        dailySheetStatusLabel.setToolTipText(description);
        dailySheetStatusLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private static String compactMessage(String message) {
        String clean = message == null ? "The operation did not return a result."
                : message.replaceAll("\\s+", " ").trim();
        return clean.length() <= 220 ? clean : clean.substring(0, 217) + "…";
    }

    static List<CloseoutAction> closeoutActions(DayCloseoutService.DayCloseout closeout) {
        List<CloseoutAction> actions = new ArrayList<>();
        if (closeout.remainingConfirmedCount() > 0) {
            actions.add(new CloseoutAction(
                    "Market Day",
                    "Finish " + closeout.remainingConfirmedCount() + " confirmed order(s)",
                    "Opened Market Day to finish the confirmed orders still waiting at closeout.",
                    List.of()));
        }
        if (closeout.todayHarvestEntries() == 0) {
            actions.add(new CloseoutAction(
                    "Harvest Log",
                    "Review the missing harvest log",
                    "Opened Harvest Log to record today’s cut or confirm a rest day.", List.of()));
        }
        if (!closeout.lowStock().isEmpty()) {
            List<String> lowStockProducts = closeout.lowStock().stream()
                    .map(DayCloseoutService.LowStockLine::name)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .toList();
            actions.add(new CloseoutAction(
                    "Inventory",
                    "Count or restock " + closeout.lowStock().size() + " low-stock item(s)",
                    "Opened Inventory with the closeout low-stock queue.", lowStockProducts));
        }
        if (closeout.cancelledCount() > 0) {
            actions.add(new CloseoutAction(
                    "Customers & Orders",
                    "Follow up " + closeout.cancelledCount() + " cancelled order(s)",
                    "Opened Customers & Orders to review cancelled-order follow-up notes.",
                    List.of(), "CANCELLED"));
        }
        return List.copyOf(actions);
    }

    static CloseoutActionChooser buildCloseoutActionChooser(List<CloseoutAction> actions) {
        List<CloseoutAction> snapshot = List.copyOf(actions);
        JComboBox<CloseoutAction> queue = new JComboBox<>(
                snapshot.toArray(CloseoutAction[]::new));
        queue.setName("dashboard.closeoutFollowUps");
        queue.getAccessibleContext().setAccessibleName("Day closeout follow-up queue");
        JLabel detail = FarmUi.formHint(" ");
        detail.setName("dashboard.closeoutFollowUpDetail");
        detail.getAccessibleContext().setAccessibleName(
                "Selected closeout follow-up destination");
        Runnable update = () -> {
            CloseoutAction selected = (CloseoutAction) queue.getSelectedItem();
            if (selected == null) {
                detail.setText("No follow-up selected.");
                detail.getAccessibleContext().setAccessibleDescription(
                        "No closeout follow-up is selected.");
                return;
            }
            String guidance = "Opens " + selected.targetTab() + " · "
                    + selected.navigationMessage();
            detail.setText(guidance);
            detail.getAccessibleContext().setAccessibleDescription(
                    "Selected follow-up: " + selected.summary() + ". " + guidance);
        };
        queue.addActionListener(event -> update.run());
        update.run();
        JPanel component = new JPanel(new BorderLayout(FarmUi.CONTROL_GAP, 0));
        component.setOpaque(false);
        JLabel label = new JLabel("Resolve:");
        label.setLabelFor(queue);
        label.setDisplayedMnemonic('R');
        component.add(label, BorderLayout.WEST);
        component.add(queue, BorderLayout.CENTER);
        component.add(detail, BorderLayout.SOUTH);
        return new CloseoutActionChooser(component, queue);
    }

    record CloseoutAction(String targetTab, String summary, String navigationMessage,
                          List<String> inventoryProducts, String orderStatus) {
        CloseoutAction(String targetTab, String summary, String navigationMessage,
                       List<String> inventoryProducts) {
            this(targetTab, summary, navigationMessage, inventoryProducts, null);
        }

        CloseoutAction {
            inventoryProducts = inventoryProducts == null
                    ? List.of() : List.copyOf(inventoryProducts);
        }

        @Override
        public String toString() {
            return summary + " → " + targetTab;
        }
    }

    void startCloseoutAction(CloseoutAction action) {
        if (action == null || host == null) {
            return;
        }
        if (!action.inventoryProducts().isEmpty()) {
            host.showInventoryProducts(
                    action.inventoryProducts(), "Dashboard", "Dashboard");
            host.setStatus(action.navigationMessage() + " "
                    + String.join(", ", action.inventoryProducts()) + ".");
            return;
        }
        if (action.orderStatus() != null && !action.orderStatus().isBlank()) {
            host.showOrdersByStatus(action.orderStatus(), "Dashboard", "Dashboard");
            return;
        }
        if ("Market Day".equals(action.targetTab())) {
            host.showMarketDay("Dashboard", "Dashboard");
            return;
        }
        if ("Harvest Log".equals(action.targetTab())) {
            host.showHarvestEntry("Dashboard", "Dashboard");
            return;
        }
        go(action.targetTab(), action.navigationMessage());
    }

    record CloseoutActionChooser(JPanel component, JComboBox<CloseoutAction> queue) {
        CloseoutAction selectedAction() {
            return (CloseoutAction) queue.getSelectedItem();
        }
    }

    private ChartPanel buildPieChartPanel() {
        inventoryValueChart = ChartFactory.createPieChart(
                "Inventory Value by Category", pieDataset, true, true, false);
        inventoryValueChart.getPlot().setOutlineVisible(false);
        ChartPanel chartPanel = new ChartPanel(inventoryValueChart);
        chartPanel.setPreferredSize(new Dimension(420, 300));
        return chartPanel;
    }

    private ChartPanel buildBarChartPanel() {
        stockQuantityChart = ChartFactory.createBarChart(
                "Quantity by Item (top " + MAX_BARS + ")", "Item", "Quantity", barDataset);
        stockQuantityChart.getPlot().setOutlineVisible(false);
        ((CategoryPlot) stockQuantityChart.getPlot()).getRenderer()
                .setSeriesPaint(0, FarmUi.accentColor());
        ChartPanel chartPanel = new ChartPanel(stockQuantityChart);
        chartPanel.setPreferredSize(new Dimension(420, 300));
        return chartPanel;
    }

    public void applyTheme(boolean dark) {
        if (panel == null) {
            return;
        }
        Color mutedFg = FarmUi.mutedColor();

        if (lastUpdatedLabel != null) lastUpdatedLabel.setForeground(mutedFg);
        if (lowStockSubtitle != null) lowStockSubtitle.setForeground(mutedFg);
        if (weekHarvestSpark != null) {
            weekHarvestSpark.setLineColor(
                    FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS));
        }
        if (weekRevenueSpark != null) {
            weekRevenueSpark.setLineColor(FarmUi.accentColor());
        }

        styleChart(inventoryValueChart);
        styleChart(stockQuantityChart);

        for (int i = 0; i < kpiCards.length; i++) {
            JPanel card = kpiCards[i];
            if (card != null && i != 3) { // low-stock styled separately
                FarmUi.Tone tone = kpiTone(i);
                card.setBackground(FarmUi.toneSurfaceColor(tone));
                card.setBorder(dashboardCardBorder(tone));
            }
        }
        if (totalItemsLabel != null) totalItemsLabel.setForeground(mutedFg);
        if (totalValueLabel != null) totalValueLabel.setForeground(
                FarmUi.toneForegroundColor(FarmUi.Tone.INFO));
        if (weekHarvestLabel != null) weekHarvestLabel.setForeground(
                FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS));
        if (weekRevenueLabel != null) weekRevenueLabel.setForeground(
                FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS));
        if (weekRevenuePipelineLabel != null) weekRevenuePipelineLabel.setForeground(mutedFg);
        if (weekHarvestDeltaLabel != null) weekHarvestDeltaLabel.setForeground(mutedFg);

        // Re-apply low stock warning after theme change
        if (lowStockLabel != null) {
            try {
                long low = Long.parseLong(lowStockLabel.getText().replace(",", "").trim());
                applyLowStockCardStyle(low > 0);
            } catch (NumberFormatException ignored) {
                applyLowStockCardStyle(false);
            }
        }

        panel.revalidate();
        panel.repaint();
    }

    private static void styleChart(JFreeChart chart) {
        if (chart == null) {
            return;
        }
        Color surface = FarmUi.surfaceColor();
        Color foreground = UIManager.getColor("Label.foreground");
        if (foreground == null) {
            foreground = Color.DARK_GRAY;
        }
        chart.setBackgroundPaint(surface);
        chart.getPlot().setBackgroundPaint(surface);
        chart.getTitle().setPaint(foreground);
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(surface);
            chart.getLegend().setItemPaint(foreground);
        }
        if (chart.getPlot() instanceof CategoryPlot plot) {
            plot.setRangeGridlinePaint(FarmUi.borderColor());
            plot.getRenderer().setSeriesPaint(0, FarmUi.accentColor());
            plot.getDomainAxis().setLabelPaint(foreground);
            plot.getDomainAxis().setTickLabelPaint(foreground);
            plot.getRangeAxis().setLabelPaint(foreground);
            plot.getRangeAxis().setTickLabelPaint(foreground);
        } else if (chart.getPlot() instanceof PiePlot plot) {
            plot.setLabelPaint(foreground);
            plot.setLabelBackgroundPaint(surface);
            plot.setLabelOutlinePaint(FarmUi.borderColor());
        }
    }

    private static Color categoryColor(String category) {
        return switch (category == null ? "" : category) {
            case "Flowers/Plants" -> new Color(190, 78, 101);
            case "Fertilizers" -> new Color(92, 145, 79);
            case "Pest Control" -> new Color(211, 153, 52);
            case "Tools/Equipment" -> new Color(73, 121, 153);
            case "Rentals" -> new Color(131, 101, 162);
            case "Gas/Fuel" -> new Color(193, 111, 54);
            default -> new Color(115, 132, 121);
        };
    }

    /**
     * @param secondary optional second line under the value (e.g. cost basis / pipeline)
     * @param subtitle  optional fixed subtitle; if null, {@code secondary} is used alone
     */
    private JPanel createKPICard(String title, JLabel valueLabel, String subtitle,
                                 JLabel secondary, SparklinePanel spark,
                                 String actionLabel, String tabTitle) {
        JPanel card = new JPanel();
        card.setName("dashboard.metricCard");
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        FarmUi.Tone cardTone = switch (title) {
            case "Week revenue" -> FarmUi.Tone.SUCCESS;
            case "Inventory value" -> FarmUi.Tone.INFO;
            case "Low stock" -> FarmUi.Tone.WARNING;
            default -> FarmUi.Tone.SUCCESS;
        };
        card.setBackground(FarmUi.toneSurfaceColor(cardTone));
        card.setBorder(dashboardCardBorder(cardTone));

        JLabel titleLabel = FarmUi.eyebrowLabel(title, cardTone);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 27f));
        valueLabel.setForeground(FarmUi.toneForegroundColor(cardTone));
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(valueLabel);

        if (subtitle != null && !subtitle.isBlank()) {
            JLabel subLabel = FarmUi.mutedLabel(subtitle);
            subLabel.setFont(subLabel.getFont().deriveFont(11f));
            subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(Box.createVerticalStrut(4));
            card.add(subLabel);
        }

        if (secondary != null) {
            secondary.setFont(secondary.getFont().deriveFont(11f));
            secondary.setAlignmentX(Component.LEFT_ALIGNMENT);
            secondary.setForeground(FarmUi.mutedColor());
            card.add(Box.createVerticalStrut(2));
            card.add(secondary);
        }

        if (spark != null) {
            spark.setAlignmentX(Component.LEFT_ALIGNMENT);
            spark.setMaximumSize(new Dimension(145, 32));
            card.add(Box.createVerticalStrut(6));
            card.add(spark);
        }

        if (actionLabel != null && tabTitle != null) {
            JButton action = FarmUi.secondaryButton(actionLabel);
            action.getAccessibleContext().setAccessibleName(
                    actionLabel + " from " + title + " metric");
            action.setFont(action.getFont().deriveFont(11f));
            action.setAlignmentX(Component.LEFT_ALIGNMENT);
            action.setToolTipText(actionLabel + " in " + tabTitle + ".");
            action.addActionListener(e -> openDashboardDestination(
                    tabTitle, actionLabel + "…"));
            card.add(Box.createVerticalStrut(6));
            card.add(action);
        }

        return card;
    }
}
