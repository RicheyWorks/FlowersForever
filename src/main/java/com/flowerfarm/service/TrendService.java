package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import weka.classifiers.functions.LinearRegression;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.time.DayOfWeek;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Spring-managed service wrapping Weka ML functionality.
 *
 * <p>Provides linear-regression trend analysis over real weekly harvest totals.
 * The crop/unit series with the strongest recorded history is selected. The
 * service refuses to invent a forecast when fewer than three distinct weeks
 * are available.
 *
 * <p>Extracted from the original GUI/CLI classes to keep ML concerns in one
 * place and make the logic independently testable.
 */
@Service
public class TrendService {

    private static final Logger log = LoggerFactory.getLogger(TrendService.class);

    private static final int MIN_OBSERVED_WEEKS = 3;

    private final HarvestService harvestService;
    private final Clock clock;

    @Autowired
    public TrendService(HarvestService harvestService) {
        this(harvestService, Clock.systemDefaultZone());
    }

    TrendService(HarvestService harvestService, Clock clock) {
        this.harvestService = harvestService;
        this.clock = clock;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Runs a linear regression over weekly totals for the best-supported
     * crop/unit series in the harvest log, then predicts the next calendar week.
     *
     * @return A {@link TrendResult} containing the prediction and a summary.
     */
    public TrendResult analyzeQuantityTrend() {
        try {
            HarvestSeries series = buildSeries();
            if (series == null || series.weeklyTotals().size() < MIN_OBSERVED_WEEKS) {
                int observed = series == null ? 0 : series.weeklyTotals().size();
                String crop = series == null ? "" : series.cropName();
                String unit = series == null ? "" : series.unit();
                return new TrendResult(0, null,
                        "Record at least 3 separate weeks for the same crop and unit "
                                + "before forecasting. Best available series has "
                                + observed + " week" + (observed == 1 ? "" : "s") + ".",
                        crop, unit, observed, Double.NaN, null, null);
            }
            List<DataPoint> dataPoints = dataPoints(series.weeklyTotals());

            ArrayList<Attribute> attributes = new ArrayList<>();
            attributes.add(new Attribute("Week"));
            attributes.add(new Attribute("Quantity"));

            Instances dataset = new Instances("TrendData", attributes, 0);
            dataset.setClassIndex(1);

            for (DataPoint point : dataPoints) {
                DenseInstance instance = new DenseInstance(2);
                instance.setValue(0, point.weekOffset());
                instance.setValue(1, point.quantity());
                dataset.add(instance);
            }

            LinearRegression model = new LinearRegression();
            model.buildClassifier(dataset);

            LocalDate latestWeek = dataPoints.get(dataPoints.size() - 1).weekStart();
            LocalDate forecastWeek = weekStart(LocalDate.now(clock)).plusWeeks(1);
            if (!forecastWeek.isAfter(latestWeek)) {
                forecastWeek = latestWeek.plusWeeks(1);
            }
            LocalDate firstWeek = dataPoints.get(0).weekStart();
            int nextWeek = (int) ChronoUnit.WEEKS.between(firstWeek, forecastWeek) + 1;
            DenseInstance future = new DenseInstance(2);
            future.setValue(0, nextWeek);
            future.setDataset(dataset);
            double predicted = Math.max(0, model.classifyInstance(future));

            double latest = dataPoints.get(dataPoints.size() - 1).quantity();
            String summary = buildSummary(
                    series, dataPoints, forecastWeek, predicted, model.toString());
            log.info("Harvest trend complete — {} / {} predicted next week: {}",
                    series.cropName(), series.unit(), String.format("%.2f", predicted));
            return new TrendResult(predicted, summary, null,
                    series.cropName(), series.unit(), dataPoints.size(), latest,
                    latestWeek, forecastWeek);

        } catch (Exception e) {
            log.error("Trend analysis failed: {}", e.getMessage(), e);
            return new TrendResult(0, null,
                    "Trend analysis could not be completed. Check server logs and try again.",
                    "", "", 0, Double.NaN, null, null);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Groups real harvest entries by normalized crop and unit, then selects the
     * series with the most distinct recorded weeks (and most entries as a tie-breaker).
     */
    private HarvestSeries buildSeries() {
        Map<String, HarvestSeriesBuilder> grouped = new LinkedHashMap<>();
        for (HarvestEntry entry : harvestService.getAll()) {
            String key = entry.getCropName().toLowerCase(Locale.ROOT) + "\u0000"
                    + entry.getUnit().toLowerCase(Locale.ROOT);
            HarvestSeriesBuilder builder = grouped.computeIfAbsent(key,
                    ignored -> new HarvestSeriesBuilder(entry.getCropName(), entry.getUnit()));
            builder.add(entry);
        }
        return grouped.values().stream()
                .max(Comparator.comparingInt(HarvestSeriesBuilder::weekCount)
                        .thenComparingInt(HarvestSeriesBuilder::entryCount))
                .map(HarvestSeriesBuilder::build)
                .orElse(null);
    }

    private List<DataPoint> dataPoints(Map<LocalDate, Double> weeklyTotals) {
        LocalDate firstWeek = weeklyTotals.keySet().iterator().next();
        List<DataPoint> points = new ArrayList<>();
        weeklyTotals.forEach((week, quantity) -> points.add(new DataPoint(
                (int) ChronoUnit.WEEKS.between(firstWeek, week) + 1,
                week,
                quantity)));
        return points;
    }

    private String buildSummary(HarvestSeries series, List<DataPoint> points,
                                LocalDate forecastWeek, double predicted,
                                String modelDetails) {
        StringBuilder sb = new StringBuilder();
        sb.append("Harvest production forecast\n");
        sb.append("───────────────────────────\n");
        sb.append("Crop: ").append(series.cropName()).append('\n');
        sb.append("Unit: ").append(series.unit()).append('\n');
        sb.append("Source: real Harvest Log entries (no sample substitution)\n\n");
        sb.append("Recorded weekly totals:\n");
        for (DataPoint point : points) {
            sb.append(String.format("  Week of %s: %.0f %s%n",
                    point.weekStart(), point.quantity(), series.unit()));
        }
        sb.append(String.format("%nProjected week of %s: %.0f %s%n%n",
                forecastWeek, predicted, series.unit()));
        sb.append("Planning note: use this directional estimate alongside weather, bed ");
        sb.append("conditions, committed orders, and crop maturity.\n\n");
        sb.append("Model diagnostics:\n").append(modelDetails).append('\n');
        return sb.toString();
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private record DataPoint(int weekOffset, LocalDate weekStart, double quantity) { }

    private record HarvestSeries(String cropName, String unit,
                                 Map<LocalDate, Double> weeklyTotals) { }

    private static final class HarvestSeriesBuilder {
        private final String cropName;
        private final String unit;
        private final Map<LocalDate, Double> weeklyTotals = new TreeMap<>();
        private int entryCount;

        private HarvestSeriesBuilder(String cropName, String unit) {
            this.cropName = cropName;
            this.unit = unit;
        }

        private void add(HarvestEntry entry) {
            weeklyTotals.merge(weekStart(entry.getHarvestDate()), entry.getQuantity(), Double::sum);
            entryCount++;
        }

        private int weekCount() { return weeklyTotals.size(); }
        private int entryCount() { return entryCount; }

        private HarvestSeries build() {
            return new HarvestSeries(cropName, unit, new TreeMap<>(weeklyTotals));
        }
    }

    // ── Result DTO ─────────────────────────────────────────────────────────────

    /**
     * Immutable result object returned by {@link #analyzeQuantityTrend()}.
     * {@code error} is {@code null} on success; {@code summary} is {@code null} on failure.
     */
    public record TrendResult(double predictedQuantity, String summary, String error,
                              String cropName, String unit, int observedWeeks,
                              double latestQuantity, LocalDate latestWeekStart,
                              LocalDate forecastWeekStart) {
        public TrendResult(double predictedQuantity, String summary, String error) {
            this(predictedQuantity, summary, error,
                    "Farm output", "units", 0, Double.NaN, null, null);
        }

        public TrendResult(double predictedQuantity, String summary, String error,
                           String cropName, String unit, int observedWeeks,
                           double latestQuantity) {
            this(predictedQuantity, summary, error,
                    cropName, unit, observedWeeks, latestQuantity, null, null);
        }

        public boolean isSuccess() { return error == null; }
        public boolean needsHistory() {
            return error != null && error.startsWith("Record at least 3 separate weeks");
        }
    }
}
