package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import weka.classifiers.functions.LinearRegression;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring-managed service wrapping Weka ML functionality.
 *
 * <p>Provides linear-regression trend analysis over inventory quantities.
 * Sample data is used when fewer than 3 data points are available in live
 * inventory, guaranteeing the model always has enough instances to train.
 *
 * <p>Extracted from the original GUI/CLI classes to keep ML concerns in one
 * place and make the logic independently testable.
 */
@Service
public class TrendService {

    private static final Logger log = LoggerFactory.getLogger(TrendService.class);

    private final InventoryService inventoryService;

    public TrendService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Runs a linear regression over weekly quantity data derived from the
     * live inventory, then predicts the quantity for the next week.
     *
     * @return A {@link TrendResult} containing the prediction and a summary.
     */
    public TrendResult analyzeQuantityTrend() {
        try {
            List<double[]> dataPoints = buildDataPoints();

            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("Week"));
            attributes.add(new Attribute("Quantity"));

            Instances dataset = new Instances("TrendData", attributes, 0);
            dataset.setClassIndex(1);

            for (double[] point : dataPoints) {
                DenseInstance instance = new DenseInstance(2);
                instance.setValue(0, point[0]);
                instance.setValue(1, point[1]);
                dataset.add(instance);
            }

            LinearRegression model = new LinearRegression();
            model.buildClassifier(dataset);

            int nextWeek = dataPoints.size() + 1;
            DenseInstance future = new DenseInstance(2);
            future.setValue(0, nextWeek);
            future.setDataset(dataset);
            double predicted = model.classifyInstance(future);

            String summary = buildSummary(dataPoints, predicted, model.toString());
            log.info("Trend analysis complete — predicted quantity for week {}: {:.2f}", nextWeek, predicted);
            return new TrendResult(predicted, summary, null);

        } catch (Exception e) {
            log.error("Trend analysis failed: {}", e.getMessage(), e);
            return new TrendResult(0, null, e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Derives weekly data points from inventory quantities grouped by order of
     * insertion. Falls back to hardcoded sample data if fewer than 3 live items
     * are available (Weka needs at least a few instances to regress sensibly).
     */
    private List<double[]> buildDataPoints() {
        List<Item> items = inventoryService.getAllItems();

        if (items.size() >= 3) {
            List<double[]> points = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                points.add(new double[]{i + 1, items.get(i).getQuantity()});
            }
            return points;
        }

        // Hardcoded fallback sample data
        return List.of(
                new double[]{1, 100},
                new double[]{2, 120},
                new double[]{3, 110},
                new double[]{4, 130},
                new double[]{5, 140},
                new double[]{6, 135}
        );
    }

    private String buildSummary(List<double[]> points, double predicted, String modelDetails) {
        StringBuilder sb = new StringBuilder();
        sb.append("Trend Analysis — Linear Regression on Inventory Quantities\n");
        sb.append("─────────────────────────────────────────────────────────\n");
        sb.append("Historical data points (Week → Quantity):\n");
        for (double[] p : points) {
            sb.append(String.format("  Week %d: %.0f\n", (int) p[0], p[1]));
        }
        sb.append(String.format("\nPredicted quantity for next week: %.2f\n\n", predicted));
        sb.append("Model details:\n").append(modelDetails).append("\n");
        sb.append("Note: For accurate farm forecasting, log real historical sales data ");
        sb.append("and feed it into this service via a dedicated CSV or database table.");
        return sb.toString();
    }

    // ── Result DTO ─────────────────────────────────────────────────────────────

    /**
     * Immutable result object returned by {@link #analyzeQuantityTrend()}.
     * {@code error} is {@code null} on success; {@code summary} is {@code null} on failure.
     */
    public record TrendResult(double predictedQuantity, String summary, String error) {
        public boolean isSuccess() { return error == null; }
    }
}
