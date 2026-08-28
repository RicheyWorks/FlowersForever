package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.service.TrendService;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Visual harvest outlook built from real weekly Harvest Log totals.
 *
 * <p>The primary surface answers three operational questions: what changed,
 * how much evidence supports the signal, and what the farmer should do next.
 * The underlying observations and ordinary least-squares details remain
 * available in drill-down tabs so the forecast stays auditable.
 */
public class TrendAnalysisTab implements FlowerFarmTab {

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d");

    private final TrendService trendService;
    private final TabHost host;

    private JPanel panel;
    private JTextArea trendText;
    private JButton analyzeButton;
    private JLabel analysisStatus;
    private JLabel forecastLabel;
    private JLabel forecastDetailLabel;
    private JLabel changeLabel;
    private JLabel changeDetailLabel;
    private JLabel coverageLabel;
    private JLabel coverageDetailLabel;
    private JLabel reliabilityLabel;
    private JLabel reliabilityDetailLabel;
    private JLabel contextLabel;
    private JLabel guidanceLabel;
    private JLabel evidenceLabel;
    private JTable weeklyTable;
    private DefaultTableModel weeklyModel;

    private final TimeSeries actualSeries = new TimeSeries("Actual harvest");
    private final TimeSeries fittedSeries = new TimeSeries("Fitted trend");
    private final TimeSeries forecastSeries = new TimeSeries("Next-week forecast");
    private final TimeSeriesCollection trendDataset = new TimeSeriesCollection();
    private JFreeChart trendChart;

    public TrendAnalysisTab(TrendService trendService) {
        this(trendService, null);
    }

    public TrendAnalysisTab(TrendService trendService, TabHost host) {
        this.trendService = trendService;
        this.host = host;
    }

    @Override
    public String getTabTitle() {
        return "Trend Analysis";
    }

    @Override
    public String getDescription() {
        return "Next-week production signal from recorded harvest history";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    private void buildUI() {
        panel = FarmUi.page();

        analyzeButton = FarmUi.primaryButton("Analyze trends");
        FarmUi.initialFocus(analyzeButton);
        analyzeButton.setToolTipText(
                "Build the next-week outlook from real weekly Harvest Log totals.");
        analyzeButton.addActionListener(event -> runAnalysis());
        analysisStatus = FarmUi.statusBadge("Ready to analyze", FarmUi.Tone.NEUTRAL);
        panel.add(FarmUi.pageHeader(
                "Harvest outlook",
                "See actual output, the fitted direction, and the next decision in one view.",
                FarmUi.toolbar(analysisStatus, analyzeButton)), BorderLayout.NORTH);

        forecastLabel = metricLabel("trend.forecast", "Not analyzed");
        forecastDetailLabel = detailLabel("Uses the best-supported crop and unit series");
        changeLabel = metricLabel("trend.change", "—");
        changeDetailLabel = detailLabel("Compared with the latest recorded week");
        coverageLabel = metricLabel("trend.coverage", "0 weeks");
        coverageDetailLabel = detailLabel("Three separate weeks required");
        reliabilityLabel = metricLabel("trend.reliability", "Not ready");
        reliabilityDetailLabel = detailLabel("Model fit appears after analysis");

        JPanel metrics = new JPanel(new GridLayout(1, 4, 12, 0));
        metrics.setOpaque(false);
        metrics.setName("trend.metrics");
        metrics.add(FarmUi.metricCard(
                "Next-week forecast", forecastLabel, forecastDetailLabel, FarmUi.Tone.SUCCESS));
        metrics.add(FarmUi.metricCard(
                "Change", changeLabel, changeDetailLabel, FarmUi.Tone.INFO));
        metrics.add(FarmUi.metricCard(
                "Data coverage", coverageLabel, coverageDetailLabel, FarmUi.Tone.NEUTRAL));
        metrics.add(FarmUi.metricCard(
                "Signal quality", reliabilityLabel, reliabilityDetailLabel, FarmUi.Tone.WARNING));

        trendDataset.addSeries(actualSeries);
        trendDataset.addSeries(fittedSeries);
        trendDataset.addSeries(forecastSeries);
        trendChart = ChartFactory.createTimeSeriesChart(
                null, "Recorded week", "Harvest quantity", trendDataset,
                true, true, false);
        styleChart();
        ChartPanel chartPanel = new ChartPanel(trendChart);
        chartPanel.setName("trend.chart");
        chartPanel.setPopupMenu(null);
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.setMinimumSize(new Dimension(420, 125));
        chartPanel.setPreferredSize(new Dimension(620, 170));
        chartPanel.getAccessibleContext().setAccessibleName(
                "Actual harvest, fitted trend, and next-week forecast chart");

        contextLabel = insightLabel("Run the analysis to see what changed.");
        contextLabel.setName("trend.context");
        evidenceLabel = insightLabel(
                "The chart uses recorded Harvest Log totals; it never substitutes sample quantities.");
        evidenceLabel.setName("trend.evidence");
        guidanceLabel = insightLabel(
                "Record the same crop and unit across at least three separate weeks.");
        guidanceLabel.setName("trend.guidance");

        JPanel insights = new JPanel();
        insights.setName("trend.insights");
        insights.setOpaque(false);
        insights.setLayout(new GridLayout(1, 3, 10, 0));
        insights.add(FarmUi.insightCard(
                "What changed", contextLabel, FarmUi.Tone.INFO));
        insights.add(FarmUi.insightCard(
                "Evidence", evidenceLabel, FarmUi.Tone.NEUTRAL));
        insights.add(FarmUi.insightCard(
                "Act next", guidanceLabel, FarmUi.Tone.SUCCESS));

        JPanel visualWorkspace = new JPanel(new BorderLayout(0, 10));
        visualWorkspace.setOpaque(false);
        visualWorkspace.setName("trend.visualWorkspace");
        visualWorkspace.add(chartPanel, BorderLayout.CENTER);
        visualWorkspace.add(insights, BorderLayout.SOUTH);

        weeklyModel = new DefaultTableModel(
                new String[]{"Week", "Actual", "Fitted direction"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column) {
                return column == 0 ? LocalDate.class : String.class;
            }
        };
        weeklyTable = new JTable(weeklyModel);
        weeklyTable.setName("trend.weeklyTable");
        FarmUi.configureTable(weeklyTable, "Weekly harvest observations");

        trendText = FarmUi.displayArea(7, 60);
        trendText.setName("trend.analysis.output");
        trendText.getAccessibleContext().setAccessibleName("Harvest trend model details");
        trendText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        trendText.setText(
                "Select Analyze trends to review weekly Harvest Log totals.\n"
                        + "No demo or sample quantities are substituted when history is incomplete.");

        JTabbedPane evidenceTabs = new JTabbedPane();
        evidenceTabs.setName("trend.evidenceTabs");
        evidenceTabs.getAccessibleContext().setAccessibleName(
                "Trend observations and model details");
        evidenceTabs.addTab("Weekly data", FarmUi.tableScroll(weeklyTable));
        evidenceTabs.addTab("Model details", new JScrollPane(trendText));
        // Keep the observations close at hand without allowing the drill-down
        // table to crowd the chart out of compact laptop-sized windows.
        evidenceTabs.setMinimumSize(new Dimension(0, 105));
        evidenceTabs.setPreferredSize(new Dimension(0, 120));

        JPanel analysisWorkspace = new JPanel(new BorderLayout(0, 12));
        analysisWorkspace.setOpaque(false);
        analysisWorkspace.add(metrics, BorderLayout.NORTH);
        analysisWorkspace.add(visualWorkspace, BorderLayout.CENTER);
        analysisWorkspace.add(evidenceTabs, BorderLayout.SOUTH);
        panel.add(FarmUi.section(
                "Production outlook",
                "Actual totals stay primary; fitted and forecast values are clearly distinguished.",
                analysisWorkspace), BorderLayout.CENTER);

        JButton harvestButton = FarmUi.secondaryButton("Open Harvest Log");
        harvestButton.addActionListener(e -> openWorkflow(
                "Harvest Log", "Review or add weekly crop history for the next forecast."));
        JButton marketButton = FarmUi.secondaryButton("Open Market Day");
        marketButton.addActionListener(e -> openWorkflow(
                "Market Day", "Compare projected output with the current packing plan."));
        panel.add(FarmUi.toolbar(harvestButton, marketButton), BorderLayout.SOUTH);
    }

    /** Runs the forecast off the Event Dispatch Thread and renders the result. */
    public void runAnalysis() {
        getUIComponent();
        analyzeButton.setEnabled(false);
        analyzeButton.setText("Analyzing…");
        FarmUi.updateStatusBadge(analysisStatus, "Analysis running", FarmUi.Tone.INFO);
        updateMetric(forecastLabel, "Analyzing…");
        updateMetric(changeLabel, "—");
        updateMetric(reliabilityLabel, "Checking");
        updateInsight(contextLabel, "Grouping matching crops and units into recorded weeks.");
        updateInsight(evidenceLabel, "Reading real Harvest Log entries in the background.");
        updateInsight(guidanceLabel, "The rest of the farm app remains available while this runs.");
        trendText.setText("Building a forecast from real Harvest Log entries…");

        SwingWorker<TrendService.TrendResult, Void> worker = new SwingWorker<>() {
            @Override
            protected TrendService.TrendResult doInBackground() {
                return trendService.analyzeQuantityTrend();
            }

            @Override
            protected void done() {
                try {
                    TrendService.TrendResult result = get();
                    if (result.isSuccess()) {
                        applyResult(result);
                    } else {
                        applyUnavailable(result);
                    }
                    FarmUi.updateStatusBadge(analysisStatus,
                            result.isSuccess() ? "Forecast ready" : "Analysis needs attention",
                            result.isSuccess() ? FarmUi.Tone.SUCCESS : FarmUi.Tone.DANGER);
                } catch (Exception ex) {
                    String message = FarmUi.actionableError("Analyzing harvest trends", ex);
                    updateMetric(forecastLabel, "Unavailable");
                    updateMetric(reliabilityLabel, "Not ready");
                    updateInsight(contextLabel, "The analysis could not be completed.");
                    updateInsight(evidenceLabel, "No forecast was displayed because the analysis failed.");
                    updateInsight(guidanceLabel, "Review the error below, then try again.");
                    trendText.setText(message);
                    FarmUi.updateStatusBadge(
                            analysisStatus, "Analysis needs attention", FarmUi.Tone.DANGER);
                    if (host != null) {
                        host.setStatus("Harvest trend analysis failed.");
                    }
                }
                trendText.setCaretPosition(0);
                analyzeButton.setEnabled(true);
                analyzeButton.setText("Analyze trends");
            }
        };
        worker.execute();
    }

    private void applyResult(TrendService.TrendResult result) {
        String unit = safeUnit(result.unit());
        updateMetric(forecastLabel, String.format("%,.0f %s",
                result.predictedQuantity(), unit));
        forecastDetailLabel.setText(result.forecastWeekStart() == null
                ? "Projected next recorded week"
                : "Week of " + result.forecastWeekStart().format(SHORT_DATE));

        Double change = percentChange(result.latestQuantity(), result.predictedQuantity());
        updateMetric(changeLabel, change == null ? "No baseline"
                : String.format(Locale.ROOT, "%+.0f%%", change));
        changeDetailLabel.setText(Double.isFinite(result.latestQuantity())
                ? String.format("Latest %,.0f → projected %,.0f %s",
                result.latestQuantity(), result.predictedQuantity(), unit)
                : "No latest-week baseline is available");

        updateMetric(coverageLabel, result.observedWeeks() + " weeks");
        coverageDetailLabel.setText(coverageDetail(result));
        updateMetric(reliabilityLabel, reliabilityName(result.rSquared()));
        reliabilityDetailLabel.setText(Double.isFinite(result.rSquared())
                ? String.format(Locale.ROOT, "R² %.2f · %s",
                result.rSquared(), reliabilityDetail(result.rSquared()))
                : "Model diagnostics available below");

        String latestPeriod = result.latestWeekStart() == null ? ""
                : " · latest week " + result.latestWeekStart();
        String accessibleContext = result.cropName() + " · " + result.observedWeeks()
                + " recorded weeks · latest " + formatQuantity(result.latestQuantity())
                + " " + unit + latestPeriod + ". " + compactChangeSentence(change);
        String visualPeriod = result.latestWeekStart() == null ? "latest"
                : "latest " + result.latestWeekStart().format(SHORT_DATE);
        updateInsight(contextLabel, result.cropName() + " · " + result.observedWeeks()
                + " weeks · " + visualPeriod + ": "
                + formatQuantity(result.latestQuantity()) + " " + unit + ". "
                + compactChangeSentence(change));
        contextLabel.getAccessibleContext().setAccessibleName(accessibleContext);
        updateInsight(evidenceLabel, evidenceSentence(result));
        updateInsight(guidanceLabel, planningGuidance(result));
        trendText.setText(result.summary());
        populateVisuals(result);
        if (host != null) {
            host.setStatus(String.format("Harvest forecast: %s · %.0f %s next week.",
                    result.cropName(), result.predictedQuantity(), unit));
        }
    }

    private void applyUnavailable(TrendService.TrendResult result) {
        updateMetric(forecastLabel, "Needs history");
        forecastDetailLabel.setText(result.observedWeeks() + " of 3 required weeks recorded");
        updateMetric(changeLabel, "—");
        changeDetailLabel.setText("A comparison appears once forecasting is available");
        updateMetric(coverageLabel, result.observedWeeks() + " weeks");
        coverageDetailLabel.setText("Record the same crop and unit in another week");
        updateMetric(reliabilityLabel, "Not ready");
        reliabilityDetailLabel.setText("At least three separate weeks are required");
        String subject = result.cropName() == null || result.cropName().isBlank()
                ? "No crop series recorded"
                : result.cropName() + " · " + result.observedWeeks() + " recorded weeks";
        updateInsight(contextLabel, subject);
        updateInsight(evidenceLabel,
                "The available observations are shown without a fabricated forecast.");
        updateInsight(guidanceLabel,
                "Log the same crop and unit in at least three separate weeks.");
        trendText.setText(result.error());
        populateVisuals(result);
        if (host != null) {
            host.setStatus("Harvest forecast needs more weekly history.");
        }
    }

    private void populateVisuals(TrendService.TrendResult result) {
        actualSeries.clear();
        fittedSeries.clear();
        forecastSeries.clear();
        weeklyModel.setRowCount(0);
        String unit = safeUnit(result.unit());
        for (TrendService.TrendPoint point : result.weeklyPoints()) {
            Day day = day(point.weekStart());
            actualSeries.addOrUpdate(day, point.quantity());
            if (Double.isFinite(point.trendQuantity())) {
                fittedSeries.addOrUpdate(day, point.trendQuantity());
            }
            weeklyModel.addRow(new Object[]{
                    point.weekStart(),
                    String.format("%,.0f %s", point.quantity(), unit),
                    Double.isFinite(point.trendQuantity())
                            ? String.format("%,.0f %s", point.trendQuantity(), unit) : "—"
            });
        }
        if (result.isSuccess() && result.latestWeekStart() != null
                && result.forecastWeekStart() != null
                && Double.isFinite(result.latestQuantity())) {
            forecastSeries.addOrUpdate(day(result.latestWeekStart()), result.latestQuantity());
            forecastSeries.addOrUpdate(day(result.forecastWeekStart()), result.predictedQuantity());
            weeklyModel.addRow(new Object[]{
                    result.forecastWeekStart(), "—",
                    String.format("%,.0f %s forecast", result.predictedQuantity(), unit)
            });
        }
        XYPlot plot = trendChart.getXYPlot();
        plot.getRangeAxis().setLabel(unit.equals("units") ? "Harvest quantity" : unit);
        trendChart.fireChartChanged();
    }

    private void styleChart() {
        trendChart.setBackgroundPaint(FarmUi.surfaceColor());
        trendChart.setBorderVisible(false);
        XYPlot plot = trendChart.getXYPlot();
        Color foreground = UIManager.getColor("Label.foreground");
        if (foreground == null) {
            foreground = Color.DARK_GRAY;
        }
        plot.setBackgroundPaint(FarmUi.surfaceColor());
        plot.setDomainGridlinePaint(FarmUi.borderColor());
        plot.setRangeGridlinePaint(FarmUi.borderColor());
        plot.setOutlineVisible(false);
        plot.setDomainCrosshairVisible(false);
        plot.setRangeCrosshairVisible(false);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, FarmUi.accentColor());
        renderer.setSeriesStroke(0, new BasicStroke(2.6f));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesPaint(1, FarmUi.mutedColor());
        renderer.setSeriesStroke(1, new BasicStroke(1.8f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 5f}, 0f));
        renderer.setSeriesShapesVisible(1, false);
        renderer.setSeriesPaint(2, FarmUi.roseAccentColor());
        renderer.setSeriesStroke(2, new BasicStroke(2.4f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 1f, new float[]{8f, 5f}, 0f));
        renderer.setSeriesShapesVisible(2, true);
        plot.setRenderer(renderer);
        plot.getDomainAxis().setLabelPaint(foreground);
        plot.getDomainAxis().setTickLabelPaint(foreground);
        plot.getRangeAxis().setLabelPaint(foreground);
        plot.getRangeAxis().setTickLabelPaint(foreground);
        if (plot.getDomainAxis() instanceof DateAxis dateAxis) {
            dateAxis.setDateFormatOverride(new SimpleDateFormat("MMM d"));
            dateAxis.setLowerMargin(0.04);
            dateAxis.setUpperMargin(0.08);
        }
        if (trendChart.getLegend() != null) {
            trendChart.getLegend().setBackgroundPaint(FarmUi.surfaceColor());
            trendChart.getLegend().setItemPaint(foreground);
        }
    }

    /** Re-applies chart paints after FlatLaf switches theme at runtime. */
    public void applyTheme() {
        if (trendChart != null) {
            styleChart();
            trendChart.fireChartChanged();
        }
    }

    private String planningGuidance(TrendService.TrendResult result) {
        if (result.latestWeekStart() != null && result.forecastWeekStart() != null) {
            long gap = ChronoUnit.WEEKS.between(
                    result.latestWeekStart(), result.forecastWeekStart());
            if (gap > 2) {
                return "History gap of " + gap
                        + " weeks — update the Harvest Log before relying on this projection.";
            }
        }
        Double change = percentChange(result.latestQuantity(), result.predictedQuantity());
        if (change == null) {
            return "Compare the projection with orders, labor, buckets, and cooler space.";
        }
        if (change > 10) {
            return "Higher output expected — confirm harvest labor, buckets, and cooler space.";
        }
        if (change < -10) {
            return "Lower output expected — prioritize committed orders and review bed conditions.";
        }
        return "Output looks steady — keep the current harvest and market plan under review.";
    }

    private static String evidenceSentence(TrendService.TrendResult result) {
        if (!Double.isFinite(result.rSquared())) {
            return "Real weekly totals are shown below; model diagnostics remain available in detail.";
        }
        return String.format(Locale.ROOT,
                "The fitted line explains %.0f%% of the recorded variation. %s",
                Math.max(0, Math.min(1, result.rSquared())) * 100,
                reliabilityExplanation(result.rSquared()));
    }

    private static String coverageDetail(TrendService.TrendResult result) {
        if (result.weeklyPoints().isEmpty()) {
            return "Best-supported crop and unit series";
        }
        LocalDate first = result.weeklyPoints().get(0).weekStart();
        LocalDate last = result.weeklyPoints().get(result.weeklyPoints().size() - 1).weekStart();
        return first.format(SHORT_DATE) + " – " + last.format(SHORT_DATE);
    }

    private static String reliabilityName(double rSquared) {
        if (!Double.isFinite(rSquared)) return "Available";
        if (rSquared >= 0.80) return "Strong";
        if (rSquared >= 0.50) return "Directional";
        return "Volatile";
    }

    private static String reliabilityExplanation(double rSquared) {
        if (rSquared >= 0.80) return "the recorded direction is consistent.";
        if (rSquared >= 0.50) return "use this as a directional planning signal.";
        return "weekly output varies widely; plan conservatively.";
    }

    private static String reliabilityDetail(double rSquared) {
        if (rSquared >= 0.80) return "consistent direction";
        if (rSquared >= 0.50) return "directional only";
        return "high weekly variation";
    }

    private static String compactChangeSentence(Double change) {
        if (change == null) return "No valid latest-week baseline yet.";
        if (Math.abs(change) > 1) return String.format(Locale.ROOT,
                "Forecast %+.0f%%.", change);
        return "Forecast steady.";
    }

    private static Double percentChange(double latest, double forecast) {
        if (!Double.isFinite(latest) || latest <= 0 || !Double.isFinite(forecast)) {
            return null;
        }
        return ((forecast - latest) / latest) * 100.0;
    }

    private void openWorkflow(String tabTitle, String status) {
        if (host != null) {
            host.selectTab(tabTitle);
            host.setStatus(status);
        }
    }

    private static JLabel metricLabel(String name, String text) {
        JLabel label = new JLabel(text);
        label.setName(name);
        label.getAccessibleContext().setAccessibleName(text);
        return label;
    }

    private static JLabel detailLabel(String text) {
        JLabel label = new JLabel(text);
        label.getAccessibleContext().setAccessibleName(text);
        return label;
    }

    private static JLabel insightLabel(String text) {
        JLabel label = new JLabel();
        updateInsight(label, text);
        return label;
    }

    private static void updateMetric(JLabel label, String text) {
        label.setText(text);
        label.getAccessibleContext().setAccessibleName(text);
    }

    private static void updateInsight(JLabel label, String text) {
        String safe = text == null ? "" : text;
        label.setText("<html><body style='width:245px'>" + escapeHtml(safe)
                + "</body></html>");
        label.getAccessibleContext().setAccessibleName(safe);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static Day day(LocalDate date) {
        return new Day(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private static String safeUnit(String unit) {
        return unit == null || unit.isBlank() ? "units" : unit;
    }

    private static String formatQuantity(double quantity) {
        return Double.isFinite(quantity) ? String.format("%,.0f", quantity) : "unknown";
    }

    @Override
    public void refreshData() {
        // The user controls when analysis runs so incomplete drafts never trigger a forecast.
    }
}
