package com.flowerfarm.gui.tabs;

import com.flowerfarm.service.TrendService;

import javax.swing.*;
import java.awt.*;

/**
 * Trend Analysis tab — runs a Weka {@code LinearRegression} forecast over
 * inventory quantities on a background thread and shows the model summary.
 *
 * <p>The forecast is exposed via the public {@link #runAnalysis()} method so it
 * can be triggered from elsewhere (the Dashboard quick action or the Tools
 * menu) without duplicating the SwingWorker logic.
 */
public class TrendAnalysisTab implements FlowerFarmTab {

    private final TrendService trendService;

    private JPanel panel;
    private JTextArea trendText;
    private JButton analyzeButton;

    public TrendAnalysisTab(TrendService trendService) {
        this.trendService = trendService;
    }

    @Override
    public String getTabTitle() {
        return "Trend Analysis";
    }

    @Override
    public String getDescription() {
        return "ML forecast (Weka) of inventory quantity trends";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout());

        trendText = new JTextArea(
                "Click 'Analyze Trends' to run ML-based (Weka LinearRegression) forecasting\n"
                + "on inventory quantities.");
        trendText.setEditable(false);
        trendText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(trendText), BorderLayout.CENTER);

        analyzeButton = new JButton("Analyze Trends");
        analyzeButton.addActionListener(e -> runAnalysis());
        panel.add(analyzeButton, BorderLayout.SOUTH);
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
        trendText.setText("Analyzing inventory quantities with Weka LinearRegression…");

        SwingWorker<TrendService.TrendResult, Void> worker = new SwingWorker<>() {
            @Override
            protected TrendService.TrendResult doInBackground() {
                return trendService.analyzeQuantityTrend();
            }

            @Override
            protected void done() {
                try {
                    TrendService.TrendResult result = get();
                    trendText.setText(result.isSuccess()
                            ? result.summary()
                            : "Error: " + result.error());
                } catch (Exception ex) {
                    trendText.setText("Error: " + ex.getMessage());
                }
                trendText.setCaretPosition(0);
                analyzeButton.setEnabled(true);
                analyzeButton.setText("Analyze Trends");
            }
        };
        worker.execute();
    }

    @Override
    public void refreshData() {
        // Nothing to refresh until the user runs an analysis.
    }
}
