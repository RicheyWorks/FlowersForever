package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.service.TrendService;

import javax.swing.*;
import java.awt.*;
import java.time.temporal.ChronoUnit;

/**
 * Trend Analysis tab — runs an ordinary least-squares forecast over
 * real weekly harvest totals on a background thread and shows the planning signal.
 *
 * <p>The forecast is exposed via the public {@link #runAnalysis()} method so it
 * can be triggered from elsewhere (the Dashboard quick action or the Tools
 * menu) without duplicating the SwingWorker logic.
 */
public class TrendAnalysisTab implements FlowerFarmTab {

    private final TrendService trendService;
    private final TabHost host;

    private JPanel panel;
    private JTextArea trendText;
    private JButton analyzeButton;
    private JLabel analysisStatus;
    private JLabel forecastLabel;
    private JLabel contextLabel;
    private JLabel guidanceLabel;

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
        analyzeButton.setToolTipText("Run a linear-regression forecast in the background.");
        analyzeButton.addActionListener(event -> runAnalysis());
        analysisStatus = FarmUi.statusBadge("Ready to analyze", FarmUi.Tone.NEUTRAL);
        panel.add(FarmUi.pageHeader(
                "Harvest production trend",
                "Project next week's output from a real crop-and-unit history",
                FarmUi.toolbar(analysisStatus, analyzeButton)), BorderLayout.NORTH);

        forecastLabel = resultLabel("trend.forecast", "No forecast yet", 22f);
        contextLabel = resultLabel("trend.context",
                "The crop with the strongest recorded weekly history will be analyzed.", 13f);
        guidanceLabel = resultLabel("trend.guidance",
                "Record the same crop and unit across at least three weeks.", 13f);

        JPanel resultHero = new JPanel();
        resultHero.setName("trend.resultSummary");
        resultHero.setOpaque(false);
        resultHero.setLayout(new BoxLayout(resultHero, BoxLayout.Y_AXIS));
        resultHero.setBorder(BorderFactory.createCompoundBorder(
                FarmUi.cardBorder(), BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        resultHero.add(forecastLabel);
        resultHero.add(Box.createVerticalStrut(5));
        resultHero.add(contextLabel);
        resultHero.add(Box.createVerticalStrut(5));
        resultHero.add(guidanceLabel);

        trendText = FarmUi.displayArea(18, 60);
        trendText.setName("trend.analysis.output");
        trendText.getAccessibleContext().setAccessibleName("Harvest trend analysis details");
        trendText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        trendText.setText(
                "Select Analyze trends to review weekly Harvest Log totals.\n"
                        + "No demo or sample quantities are substituted when history is incomplete.");
        JPanel resultWorkspace = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        resultWorkspace.setOpaque(false);
        resultWorkspace.add(resultHero, BorderLayout.NORTH);
        resultWorkspace.add(new JScrollPane(trendText), BorderLayout.CENTER);
        panel.add(FarmUi.section(
                "Production planning signal",
                "Use the estimate with weather, bed conditions, crop maturity, and committed orders.",
                resultWorkspace), BorderLayout.CENTER);

        JButton harvestButton = FarmUi.secondaryButton("Open Harvest Log");
        harvestButton.addActionListener(e -> openWorkflow(
                "Harvest Log", "Review or add weekly crop history for the next forecast."));
        JButton marketButton = FarmUi.secondaryButton("Open Market Day");
        marketButton.addActionListener(e -> openWorkflow(
                "Market Day", "Compare projected output with the current packing plan."));
        panel.add(FarmUi.toolbar(harvestButton, marketButton), BorderLayout.SOUTH);
    }

    /**
     * Runs the linear-regression forecast off the Event Dispatch Thread and
     * renders the result. Safe to call from any tab or menu; the UI is built
     * lazily if it does not yet exist.
     */
    public void runAnalysis() {
        getUIComponent(); // ensure UI is built before we touch its widgets
        analyzeButton.setEnabled(false);
        analyzeButton.setText("Analyzing…");
        FarmUi.updateStatusBadge(analysisStatus, "Analysis running", FarmUi.Tone.INFO);
        updateResultLabel(forecastLabel, "Analyzing harvest history…");
        updateResultLabel(contextLabel, "Grouping matching crops and units into recorded weeks.");
        updateResultLabel(guidanceLabel,
                "The rest of the farm app remains available while this runs.");
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
                    updateResultLabel(forecastLabel, "Forecast unavailable");
                    updateResultLabel(contextLabel, "The analysis could not be completed.");
                    updateResultLabel(guidanceLabel, "Review the error below, then try again.");
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
        String forecastPeriod = result.forecastWeekStart() == null
                ? "next week"
                : "week of " + result.forecastWeekStart();
        updateResultLabel(forecastLabel, String.format("Projected %s: %.0f %s",
                forecastPeriod, result.predictedQuantity(), result.unit()));
        String latestPeriod = result.latestWeekStart() == null
                ? ""
                : " · last week " + result.latestWeekStart();
        updateResultLabel(contextLabel, result.cropName() + " · " + result.observedWeeks()
                + " recorded weeks · latest " + formatQuantity(result.latestQuantity())
                + " " + result.unit() + latestPeriod);
        updateResultLabel(guidanceLabel, planningGuidance(result));
        trendText.setText(result.summary());
        if (host != null) {
            host.setStatus(String.format("Harvest forecast: %s · %.0f %s next week.",
                    result.cropName(), result.predictedQuantity(), result.unit()));
        }
    }

    private void applyUnavailable(TrendService.TrendResult result) {
        updateResultLabel(forecastLabel, "More harvest history needed");
        String subject = result.cropName() == null || result.cropName().isBlank()
                ? "No crop series recorded"
                : result.cropName() + " · " + result.observedWeeks() + " recorded weeks";
        updateResultLabel(contextLabel, subject);
        updateResultLabel(guidanceLabel,
                "Log the same crop and unit in at least three separate weeks.");
        trendText.setText(result.error());
        if (host != null) {
            host.setStatus("Harvest forecast needs more weekly history.");
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
        if (!Double.isFinite(result.latestQuantity()) || result.latestQuantity() <= 0) {
            return "Compare the projection with orders, labor, buckets, and cooler space.";
        }
        double change = (result.predictedQuantity() - result.latestQuantity())
                / result.latestQuantity();
        if (change > 0.10) {
            return "Higher output expected — confirm harvest labor, buckets, and cooler space.";
        }
        if (change < -0.10) {
            return "Lower output expected — prioritize committed orders and review bed conditions.";
        }
        return "Output looks steady — keep the current harvest and market plan under review.";
    }

    private void openWorkflow(String tabTitle, String status) {
        if (host != null) {
            host.selectTab(tabTitle);
            host.setStatus(status);
        }
    }

    private static JLabel resultLabel(String name, String text, float size) {
        JLabel label = new JLabel(text);
        label.setName(name);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(
                size >= 20 ? Font.BOLD : Font.PLAIN, size));
        label.getAccessibleContext().setAccessibleName(text);
        return label;
    }

    private static void updateResultLabel(JLabel label, String text) {
        label.setText(text);
        label.getAccessibleContext().setAccessibleName(text);
    }

    private static String formatQuantity(double quantity) {
        return Double.isFinite(quantity) ? String.format("%.0f", quantity) : "unknown";
    }

    @Override
    public void refreshData() {
        // Nothing to refresh until the user runs an analysis.
    }
}
