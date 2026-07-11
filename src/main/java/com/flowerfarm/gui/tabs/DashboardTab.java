package com.flowerfarm.gui.tabs;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
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
    private final TabHost host;

    private JPanel panel;
    private JLabel totalItemsLabel;
    private JLabel totalValueLabel;
    private JLabel totalCostLabel;
    private JLabel lowStockLabel;
    private JLabel weekHarvestLabel;
    private JLabel weekHarvestDeltaLabel;
    private JLabel weekRevenueLabel;
    private JLabel weekRevenuePipelineLabel;
    private JLabel lastUpdatedLabel;
    private JTextArea alertsArea;
    private JLabel lowStockSubtitle;

    private final JPanel[] kpiCards = new JPanel[5];
    private SparklinePanel weekHarvestSpark;
    private SparklinePanel weekRevenueSpark;
    private boolean darkTheme;

    private final DefaultPieDataset pieDataset = new DefaultPieDataset();
    private final DefaultCategoryDataset barDataset = new DefaultCategoryDataset();

    public DashboardTab(InventoryService inventoryService,
                        HarvestService harvestService,
                        OrderService orderService,
                        IrrigationAdvisorService irrigationAdvisorService,
                        MarketDayPackingService marketDayPackingService,
                        MorningBriefingService morningBriefingService,
                        TabHost host) {
        this.inventoryService = inventoryService;
        this.harvestService = harvestService;
        this.orderService = orderService;
        this.irrigationAdvisorService = irrigationAdvisorService;
        this.marketDayPackingService = marketDayPackingService;
        this.morningBriefingService = morningBriefingService;
        this.host = host;
    }

    @Override public String getTabTitle() { return "Dashboard"; }

    @Override
    public String getDescription() {
        return "KPIs, week harvest/revenue, charts, alerts, and quick actions";
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

        List<Item> items = inventoryService.getAllItems();
        InventoryService.InventoryKpiSnapshot inv =
                inventoryService.inventoryKpis(LOW_STOCK_THRESHOLD);

        double weekHarvest = harvestService.totalQuantityLast7Days();
        double priorHarvest = harvestService.totalQuantityPrior7Days();
        OrderService.WeekRevenueSummary rev = orderService.weekRevenueSummary();
        double priorRev = orderService.realizedRevenuePrior7Days();

        totalItemsLabel.setText(String.valueOf(inv.skuCount()));
        totalValueLabel.setText(String.format("$%,.2f", inv.sellValue()));
        if (totalCostLabel != null) {
            totalCostLabel.setText(String.format("cost basis $%,.2f · %d units",
                    inv.costBasis(), inv.totalUnits()));
        }
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
                    "<html><center>pipeline $%,.2f · booked $%,.2f<br/>%s</center></html>",
                    rev.pipeline(), rev.booked(), wow));
        }

        long lowStockCount = inv.lowStockCount();

        if (weekHarvestSpark != null) {
            double[] harvestDays = harvestService.dailyQuantitiesLast7Days();
            weekHarvestSpark.setValues(harvestDays);
            weekHarvestSpark.setToolTipText(formatSparkTip("Harvest qty", harvestDays, false));
        }
        if (weekRevenueSpark != null) {
            double[] revDays = orderService.dailyRevenueLast7Days(); // realized only
            weekRevenueSpark.setValues(revDays);
            weekRevenueSpark.setToolTipText(formatSparkTip("Fulfilled $", revDays, true));
        }

        applyLowStockCardStyle(lowStockCount > 0);

        if (lastUpdatedLabel != null) {
            lastUpdatedLabel.setText("KPIs updated "
                    + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                    + "  ·  Week = last 7 days · Revenue = FULFILLED (pipeline shown under)");
        }

        rebuildAlerts(items, rev, weekHarvest);
        rebuildValueByCategory(items);
        rebuildQuantityByItem(items);
    }

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
        if (kpiCards[2] == null) {
            return;
        }
        Color border = warning
                ? new Color(200, 80, 60)
                : (darkTheme ? new Color(90, 93, 95) : new Color(200, 200, 200));
        Color bg = warning
                ? (darkTheme ? new Color(90, 50, 45) : new Color(255, 240, 235))
                : (darkTheme ? new Color(60, 63, 65) : new Color(250, 250, 250));
        kpiCards[2].setBackground(bg);
        kpiCards[2].setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, warning ? 2 : 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        if (lowStockLabel != null) {
            lowStockLabel.setForeground(warning
                    ? new Color(180, 40, 30)
                    : (darkTheme ? new Color(120, 220, 120) : new Color(0, 100, 0)));
        }
        if (lowStockSubtitle != null) {
            lowStockSubtitle.setText(warning
                    ? "items ≤ " + LOW_STOCK_THRESHOLD + " — restock!"
                    : "items ≤ " + LOW_STOCK_THRESHOLD);
        }
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

    private void rebuildAlerts(List<Item> items, OrderService.WeekRevenueSummary rev, double weekHarvest) {
        StringBuilder alerts = new StringBuilder();
        items.stream()
                .filter(i -> i.getQuantity() <= LOW_STOCK_THRESHOLD)
                .sorted((a, b) -> Integer.compare(a.getQuantity(), b.getQuantity()))
                .forEach(i -> alerts.append("• LOW  ").append(i.getName())
                        .append(" — only ").append(i.getQuantity()).append(" left\n"));

        if (rev != null) {
            if (rev.confirmedOrderCount() > 0) {
                alerts.append("• PIPE ").append(rev.confirmedOrderCount())
                        .append(" confirmed order(s) awaiting fulfill — $")
                        .append(String.format("%,.2f", rev.pipeline())).append(" pipeline\n");
            }
            if (rev.draftOrderCount() > 0) {
                alerts.append("• DRAFT ").append(rev.draftOrderCount())
                        .append(" draft order(s) ($")
                        .append(String.format("%,.2f", rev.draft())).append(") not in revenue KPI\n");
            }
            if (rev.realized() <= 0 && rev.pipeline() <= 0) {
                alerts.append("• Week revenue quiet — no fulfilled/confirmed orders in 7 days\n");
            }
        }
        if (weekHarvest <= 0) {
            alerts.append("• No harvest logged in the last 7 days — open Harvest Log\n");
        } else {
            try {
                HarvestService.BedProductionReport beds =
                        harvestService.productionByBedLast7Days();
                if (!beds.beds().isEmpty()) {
                    HarvestService.BedProduction top = beds.beds().get(0);
                    alerts.append("• BEDS top this week: ").append(top.bed())
                            .append(" (").append(String.format("%.0f", top.totalQuantity()))
                            .append(" qty) across ").append(beds.bedCount())
                            .append(" bed(s) — Harvest Log → Bed production\n");
                }
            } catch (Exception ignored) {
                // bed rollup is best-effort
            }
        }

        if (irrigationAdvisorService != null) {
            try {
                // Climatology only on dashboard refresh (fast, offline-safe)
                IrrigationAdvisorService.IrrigationAdvice tip =
                        irrigationAdvisorService.adviseClimatology();
                if (tip.priority() == IrrigationAdvisorService.Priority.HIGH
                        || tip.priority() == IrrigationAdvisorService.Priority.MEDIUM) {
                    alerts.append("• WATER ").append(tip.priority().name())
                            .append(" — ").append(tip.headline())
                            .append(" (Irrigation & Care)\n");
                }
            } catch (Exception ignored) {
                // advisory is best-effort on the dashboard
            }
        }

        if (marketDayPackingService != null) {
            try {
                MarketDayPackingService.MarketDayPlan plan =
                        marketDayPackingService.planForDay(LocalDate.now());
                if (plan.orderCount() > 0) {
                    alerts.append("• PACK ").append(plan.orderCount())
                            .append(" CONFIRMED order(s) for today — $")
                            .append(String.format("%,.2f", plan.pipelineValue()))
                            .append(" pipeline (Market Day)\n");
                    if (plan.shortfallSkuCount() > 0) {
                        alerts.append("• PACK SHORT ").append(plan.shortfallSkuCount())
                                .append(" product(s) under stock for today's load-out\n");
                    }
                }
            } catch (Exception ignored) {
                // packing plan is best-effort on the dashboard
            }
        }

        if (alerts.length() == 0) {
            alerts.append("All clear — stock healthy, harvest moving, orders on track. Great job, Kitsap!");
        }
        alertsArea.setText(alerts.toString());
        alertsArea.setCaretPosition(0);
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
        panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel north = new JPanel(new BorderLayout());
        JLabel header = new JLabel("Flower Farm Dashboard — Port Orchard / Kitsap County, WA");
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        north.add(header, BorderLayout.WEST);
        JPanel northEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        lastUpdatedLabel = new JLabel("KPIs not refreshed yet");
        lastUpdatedLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        lastUpdatedLabel.setForeground(new Color(90, 100, 90));
        JButton refreshKpis = new JButton("Refresh KPIs");
        refreshKpis.setToolTipText("Reload inventory, harvest, and order metrics.");
        refreshKpis.addActionListener(e -> {
            refreshData();
            if (host != null) {
                host.setStatus("Dashboard KPIs refreshed.");
            }
        });
        northEast.add(lastUpdatedLabel);
        northEast.add(refreshKpis);
        north.add(northEast, BorderLayout.EAST);
        panel.add(north, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));

        JPanel kpiPanel = new JPanel(new GridLayout(1, 5, 10, 10));
        kpiCards[0] = createKPICard("Total SKUs", totalItemsLabel = new JLabel("—"), "items in inventory",
                null, null, "Open Inventory", "Inventory");
        totalCostLabel = new JLabel("cost basis —");
        kpiCards[1] = createKPICard("Inventory Value", totalValueLabel = new JLabel("—"), "at sell prices",
                totalCostLabel, null, "Open Inventory", "Inventory");
        lowStockSubtitle = new JLabel("items ≤ " + LOW_STOCK_THRESHOLD);
        kpiCards[2] = createKPICard("Low Stock", lowStockLabel = new JLabel("—"), null,
                lowStockSubtitle, null, "Review stock", "Inventory");
        weekHarvestSpark = new SparklinePanel();
        weekHarvestSpark.setLineColor(new Color(34, 100, 54));
        weekHarvestDeltaLabel = new JLabel("vs prior wk —");
        kpiCards[3] = createKPICard("Week Harvest", weekHarvestLabel = new JLabel("—"), "qty last 7 days",
                weekHarvestDeltaLabel, weekHarvestSpark, "Log harvest", "Harvest Log");
        weekRevenueSpark = new SparklinePanel();
        weekRevenueSpark.setLineColor(new Color(30, 90, 160));
        weekRevenuePipelineLabel = new JLabel("pipeline —");
        kpiCards[4] = createKPICard("Week Revenue", weekRevenueLabel = new JLabel("—"), "realized (fulfilled)",
                weekRevenuePipelineLabel, weekRevenueSpark, "Create order", "CRM");
        for (JPanel card : kpiCards) {
            kpiPanel.add(card);
        }
        center.add(kpiPanel, BorderLayout.NORTH);

        JPanel chartsPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        chartsPanel.add(buildPieChartPanel());
        chartsPanel.add(buildBarChartPanel());
        center.add(chartsPanel, BorderLayout.CENTER);

        panel.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 8));

        JPanel alertsPanel = new JPanel(new BorderLayout());
        alertsPanel.setBorder(BorderFactory.createTitledBorder(
                "Ops alerts (stock · pipeline · harvest · pack · water)"));
        alertsArea = new JTextArea(5, 40);
        alertsArea.setEditable(false);
        alertsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        alertsPanel.add(new JScrollPane(alertsArea), BorderLayout.CENTER);
        south.add(alertsPanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(0, 2, 8, 6));
        actions.setBorder(BorderFactory.createTitledBorder("Quick actions"));
        actions.add(actionButton("Log Harvest", "Harvest Log", "Open harvest log to record today's cut."));
        actions.add(actionButton("Create Order", "CRM", "Open CRM to create a wholesale / market order."));
        actions.add(actionButton("Market packing", "Market Day",
                "Build today's packing list / pick sheet for the van."));
        JButton briefing = new JButton("Morning briefing");
        briefing.setToolTipText("Pack + beds + water + low stock — start-of-day sheet (PDF).");
        briefing.addActionListener(e -> showMorningBriefing());
        actions.add(briefing);
        actions.add(actionButton("Fulfill pipeline", "CRM", "Open CRM to fulfill confirmed orders."));
        actions.add(actionButton("Weekly PDF Report", "Reports", "Generate harvest + sales PDF report."));
        actions.add(actionButton("Rose Visualizer", "Rose Visualizer", "Grow generative L-System roses."));
        JButton trends = new JButton("Run Trend Analysis");
        trends.addActionListener(e -> {
            if (host != null) {
                host.setStatus("Running trend analysis…");
                host.runTrendAnalysis();
            }
        });
        actions.add(trends);
        JButton inventory = new JButton("Open Inventory");
        inventory.addActionListener(e -> go("Inventory", "Opened inventory."));
        actions.add(inventory);
        JButton syncHist = new JButton("Sync / Audit");
        syncHist.addActionListener(e -> go("Sync History", "Opened audit trail."));
        actions.add(syncHist);
        south.add(actions, BorderLayout.EAST);

        panel.add(south, BorderLayout.SOUTH);

        applyTheme(false);
    }

    private JButton actionButton(String label, String tabTitle, String statusMsg) {
        JButton btn = new JButton(label);
        btn.addActionListener(e -> go(tabTitle, statusMsg));
        return btn;
    }

    private void go(String tabTitle, String statusMsg) {
        if (host != null) {
            host.selectTab(tabTitle);
            host.setStatus(statusMsg);
        }
    }

    private void showMorningBriefing() {
        if (morningBriefingService == null) {
            return;
        }
        try {
            if (host != null) {
                host.setStatus("⏳ Building morning briefing…");
            }
            MorningBriefingService.MorningBriefing briefing =
                    morningBriefingService.buildOffline();
            JTextArea area = new JTextArea(briefing.plainText(), 24, 68);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            area.setCaretPosition(0);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(680, 460));

            Object[] options = {"Export PDF…", "Close"};
            int choice = JOptionPane.showOptionDialog(panel, scroll,
                    "Morning briefing — " + briefing.date(),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[1]);
            if (choice == 0) {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File("morning-briefing-" + briefing.date() + ".pdf"));
                if (chooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
                    byte[] pdf = morningBriefingService.generatePdf(briefing);
                    Files.write(chooser.getSelectedFile().toPath(), pdf);
                    if (host != null) {
                        host.setStatus("Morning briefing PDF → "
                                + chooser.getSelectedFile().getName());
                    }
                }
            } else if (host != null) {
                host.setStatus("Briefing: " + briefing.actionItems().size() + " action(s).");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(),
                    "Morning briefing failed", JOptionPane.ERROR_MESSAGE);
            if (host != null) {
                host.setStatus("Briefing failed: " + ex.getMessage());
            }
        }
    }

    private ChartPanel buildPieChartPanel() {
        JFreeChart chart = ChartFactory.createPieChart(
                "Inventory Value by Category", pieDataset, true, true, false);
        chart.setBackgroundPaint(Color.WHITE);
        chart.getPlot().setBackgroundPaint(Color.WHITE);
        chart.getPlot().setOutlineVisible(false);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(420, 300));
        return chartPanel;
    }

    private ChartPanel buildBarChartPanel() {
        JFreeChart chart = ChartFactory.createBarChart(
                "Quantity by Item (top " + MAX_BARS + ")", "Item", "Quantity", barDataset);
        chart.setBackgroundPaint(Color.WHITE);
        chart.getPlot().setBackgroundPaint(new Color(250, 250, 250));
        chart.getPlot().setOutlineVisible(false);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(420, 300));
        return chartPanel;
    }

    public void applyTheme(boolean dark) {
        this.darkTheme = dark;
        if (panel == null) {
            return;
        }
        Color cardBg = dark ? new Color(60, 63, 65) : new Color(250, 250, 250);
        Color valueFg = dark ? new Color(120, 220, 120) : new Color(0, 100, 0);
        Color mutedFg = dark ? new Color(160, 170, 160) : new Color(90, 100, 90);
        Color border = dark ? new Color(90, 93, 95) : new Color(200, 200, 200);

        for (int i = 0; i < kpiCards.length; i++) {
            JPanel card = kpiCards[i];
            if (card != null && i != 2) { // low-stock styled separately
                card.setBackground(cardBg);
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(border, 1),
                        BorderFactory.createEmptyBorder(12, 12, 12, 12)));
            }
        }
        if (totalItemsLabel != null) totalItemsLabel.setForeground(valueFg);
        if (totalValueLabel != null) totalValueLabel.setForeground(valueFg);
        if (totalCostLabel != null) totalCostLabel.setForeground(mutedFg);
        if (weekHarvestLabel != null) weekHarvestLabel.setForeground(valueFg);
        if (weekRevenueLabel != null) weekRevenueLabel.setForeground(valueFg);
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

    /**
     * @param secondary optional second line under the value (e.g. cost basis / pipeline)
     * @param subtitle  optional fixed subtitle; if null, {@code secondary} is used alone
     */
    private JPanel createKPICard(String title, JLabel valueLabel, String subtitle,
                                 JLabel secondary, SparklinePanel spark,
                                 String actionLabel, String tabTitle) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(valueLabel);

        if (subtitle != null && !subtitle.isBlank()) {
            JLabel subLabel = new JLabel(subtitle);
            subLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(Box.createVerticalStrut(4));
            card.add(subLabel);
        }

        if (secondary != null) {
            secondary.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            secondary.setAlignmentX(Component.CENTER_ALIGNMENT);
            secondary.setForeground(new Color(90, 100, 90));
            card.add(Box.createVerticalStrut(2));
            card.add(secondary);
        }

        if (spark != null) {
            spark.setAlignmentX(Component.CENTER_ALIGNMENT);
            spark.setMaximumSize(new Dimension(100, 30));
            card.add(Box.createVerticalStrut(6));
            card.add(spark);
        }

        if (actionLabel != null && tabTitle != null) {
            JButton action = new JButton(actionLabel);
            action.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            action.setAlignmentX(Component.CENTER_ALIGNMENT);
            action.setFocusable(false);
            action.addActionListener(e -> go(tabTitle, actionLabel + "…"));
            card.add(Box.createVerticalStrut(6));
            card.add(action);
        }

        return card;
    }
}
