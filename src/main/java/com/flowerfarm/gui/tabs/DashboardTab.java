package com.flowerfarm.gui.tabs;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executive Dashboard tab — the at-a-glance overview of the GUI.
 *
 * <p>Shows KPI cards (SKU count, inventory value, low-stock count), two live
 * JFreeChart visualizations (inventory value by category as a pie, quantity by
 * item as a bar chart), a low-stock alert list, and a quick action to run the
 * ML trend forecast.
 *
 * <p>Charts use a fixed light palette so they stay readable under either the
 * light or dark application theme; {@link #applyTheme(boolean)} restyles the KPI
 * cards to match the active theme.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class DashboardTab implements FlowerFarmTab {

    /** Items at or below this quantity are treated as "low stock". */
    private static final int LOW_STOCK_THRESHOLD = 10;
    /** Cap on the number of bars in the quantity chart to keep the axis legible. */
    private static final int MAX_BARS = 15;

    private final InventoryService inventoryService;
    private final TabHost host;

    private JPanel panel;
    private JLabel totalItemsLabel;
    private JLabel totalValueLabel;
    private JLabel lowStockLabel;
    private JTextArea alertsArea;

    private final JPanel[] kpiCards = new JPanel[3];

    private final DefaultPieDataset pieDataset = new DefaultPieDataset();
    private final DefaultCategoryDataset barDataset = new DefaultCategoryDataset();

    public DashboardTab(InventoryService inventoryService, TabHost host) {
        this.inventoryService = inventoryService;
        this.host = host;
    }

    @Override
    public String getTabTitle() {
        return "Dashboard";
    }

    @Override
    public String getDescription() {
        return "KPIs, charts, low-stock alerts, and quick actions";
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

        long totalItems = items.size();
        double totalValue = items.stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();
        long lowStockCount = items.stream()
                .filter(i -> i.getQuantity() <= LOW_STOCK_THRESHOLD)
                .count();

        totalItemsLabel.setText(String.valueOf(totalItems));
        totalValueLabel.setText(String.format("$%,.2f", totalValue));
        lowStockLabel.setText(String.valueOf(lowStockCount));

        rebuildAlerts(items);
        rebuildValueByCategory(items);
        rebuildQuantityByItem(items);
    }

    private void rebuildAlerts(List<Item> items) {
        StringBuilder alerts = new StringBuilder();
        items.stream()
                .filter(i -> i.getQuantity() <= LOW_STOCK_THRESHOLD)
                .sorted((a, b) -> Integer.compare(a.getQuantity(), b.getQuantity()))
                .forEach(i -> alerts.append("• ").append(i.getName())
                        .append(" — only ").append(i.getQuantity()).append(" left\n"));

        alertsArea.setText(alerts.length() == 0
                ? "All inventory levels look healthy. Great job!"
                : alerts.toString());
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
            // Ensure a unique column key even if two items share a name.
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

        JLabel header = new JLabel("Flower Farm Dashboard — Kitsap County, WA");
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        panel.add(header, BorderLayout.NORTH);

        // Center: KPI cards on top, charts filling the rest.
        JPanel center = new JPanel(new BorderLayout(0, 12));

        JPanel kpiPanel = new JPanel(new GridLayout(1, 3, 15, 10));
        kpiCards[0] = createKPICard("Total SKUs", totalItemsLabel = new JLabel("—"), "items in inventory");
        kpiCards[1] = createKPICard("Inventory Value", totalValueLabel = new JLabel("—"), "at current prices");
        kpiCards[2] = createKPICard("Low Stock Alerts", lowStockLabel = new JLabel("—"),
                "items ≤ " + LOW_STOCK_THRESHOLD + " units");
        for (JPanel card : kpiCards) {
            kpiPanel.add(card);
        }
        center.add(kpiPanel, BorderLayout.NORTH);

        JPanel chartsPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        chartsPanel.add(buildPieChartPanel());
        chartsPanel.add(buildBarChartPanel());
        center.add(chartsPanel, BorderLayout.CENTER);

        panel.add(center, BorderLayout.CENTER);

        // South: alerts + quick action.
        JPanel alertsPanel = new JPanel(new BorderLayout());
        alertsPanel.setBorder(BorderFactory.createTitledBorder("Low Stock & Action Items"));

        alertsArea = new JTextArea(5, 40);
        alertsArea.setEditable(false);
        alertsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        alertsPanel.add(new JScrollPane(alertsArea), BorderLayout.CENTER);

        JButton analyzeTrendsBtn = new JButton("Run Trend Analysis");
        analyzeTrendsBtn.addActionListener(e -> {
            if (host != null) {
                host.runTrendAnalysis();
            }
        });
        alertsPanel.add(analyzeTrendsBtn, BorderLayout.SOUTH);

        panel.add(alertsPanel, BorderLayout.SOUTH);

        applyTheme(false); // light defaults; orchestrator re-applies the saved theme
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

    /**
     * Restyle the KPI cards so their custom colors stay legible under the given
     * theme. Called after the look-and-feel changes (the L&amp;F refresh resets
     * component colors, so this must run afterwards to win).
     */
    public void applyTheme(boolean dark) {
        if (panel == null) {
            return;
        }
        Color cardBg = dark ? new Color(60, 63, 65) : new Color(250, 250, 250);
        Color valueFg = dark ? new Color(120, 220, 120) : new Color(0, 100, 0);
        Color border = dark ? new Color(90, 93, 95) : new Color(200, 200, 200);

        for (JPanel card : kpiCards) {
            if (card != null) {
                card.setBackground(cardBg);
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(border, 1),
                        BorderFactory.createEmptyBorder(12, 15, 12, 15)));
            }
        }
        if (totalItemsLabel != null) totalItemsLabel.setForeground(valueFg);
        if (totalValueLabel != null) totalValueLabel.setForeground(valueFg);
        if (lowStockLabel != null) lowStockLabel.setForeground(valueFg);

        panel.revalidate();
        panel.repaint();
    }

    private JPanel createKPICard(String title, JLabel valueLabel, String subtitle) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(12, 15, 12, 15)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        valueLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLabel = new JLabel(subtitle);
        subLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(valueLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(subLabel);

        return card;
    }
}
