package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
 * Spring-managed service providing transparent harvest trend analysis.
 *
 * <p>Provides linear-regression trend analysis over real weekly harvest totals.
 * The crop/unit series with the strongest recorded history is selected. The
 * service refuses to invent a forecast when fewer than three distinct weeks
 * are available.
 *
 * <p>The regression is implemented directly because ordinary least squares is
 * sufficient for this single-variable forecast. Keeping this small calculation
 * in the product avoids bundling a general-purpose analytics workbench and makes
 * the displayed forecast straightforward to audit.
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
                List<TrendPoint> points = series == null ? List.of()
                        : dataPoints(series.weeklyTotals()).stream()
                        .map(point -> new TrendPoint(
                                point.weekStart(), point.quantity(), Double.NaN))
                        .toList();
                return new TrendResult(0, null,
                        "Record at least 3 separate weeks for the same crop and unit "
                                + "before forecasting. Best available series has "
                                + observed + " week" + (observed == 1 ? "" : "s") + ".",
                        crop, unit, observed, Double.NaN, null, null,
                        points, Double.NaN, Double.NaN, Double.NaN);
            }
            List<DataPoint> dataPoints = dataPoints(series.weeklyTotals());

            LinearModel model = fitLinearModel(dataPoints);

            LocalDate latestWeek = dataPoints.get(dataPoints.size() - 1).weekStart();
            LocalDate forecastWeek = weekStart(LocalDate.now(clock)).plusWeeks(1);
            if (!forecastWeek.isAfter(latestWeek)) {
                forecastWeek = latestWeek.plusWeeks(1);
            }
            LocalDate firstWeek = dataPoints.get(0).weekStart();
            int nextWeek = (int) ChronoUnit.WEEKS.between(firstWeek, forecastWeek) + 1;
            double predicted = Math.max(0, model.predict(nextWeek));

            double latest = dataPoints.get(dataPoints.size() - 1).quantity();
            String summary = buildSummary(
                    series, dataPoints, forecastWeek, predicted, model.diagnostics());
            log.info("Harvest trend complete — {} / {} predicted next week: {}",
                    series.cropName(), series.unit(), String.format("%.2f", predicted));
            List<TrendPoint> chartPoints = dataPoints.stream()
                    .map(point -> new TrendPoint(
                            point.weekStart(), point.quantity(),
                            Math.max(0, model.predict(point.weekOffset()))))
                    .toList();
            return new TrendResult(predicted, summary, null,
                    series.cropName(), series.unit(), dataPoints.size(), latest,
                    latestWeek, forecastWeek, chartPoints,
                    model.slope(), model.intercept(), model.rSquared());

        } catch (Exception e) {
            log.error("Trend analysis failed: {}", e.getMessage(), e);
            return new TrendResult(0, null,
                    "Trend analysis could not be completed. Check server logs and try again.",
                    "", "", 0, Double.NaN, null, null,
                    List.of(), Double.NaN, Double.NaN, Double.NaN);
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

    /**
     * Fits {@code quantity = intercept + slope * weekOffset} with ordinary least
     * squares. Observations always represent distinct weeks, so the horizontal
     * variance is non-zero once the minimum-history rule has passed.
     */
    private LinearModel fitLinearModel(List<DataPoint> points) {
        double meanWeek = points.stream().mapToDouble(DataPoint::weekOffset).average()
                .orElseThrow();
        double meanQuantity = points.stream().mapToDouble(DataPoint::quantity).average()
                .orElseThrow();

        double horizontalVariance = 0;
        double covariance = 0;
        for (DataPoint point : points) {
            double centeredWeek = point.weekOffset() - meanWeek;
            horizontalVariance += centeredWeek * centeredWeek;
            covariance += centeredWeek * (point.quantity() - meanQuantity);
        }
        if (horizontalVariance == 0) {
            throw new IllegalArgumentException("Trend history must span distinct weeks");
        }

        double slope = covariance / horizontalVariance;
        double intercept = meanQuantity - slope * meanWeek;
        double totalSquares = 0;
        double residualSquares = 0;
        for (DataPoint point : points) {
            double observedDelta = point.quantity() - meanQuantity;
            double residual = point.quantity()
                    - (intercept + slope * point.weekOffset());
            totalSquares += observedDelta * observedDelta;
            residualSquares += residual * residual;
        }
        double rSquared = totalSquares == 0 ? 1 : 1 - residualSquares / totalSquares;
        return new LinearModel(slope, intercept, rSquared);
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

    private record LinearModel(double slope, double intercept, double rSquared) {
        private double predict(double weekOffset) {
            return intercept + slope * weekOffset;
        }

        private String diagnostics() {
            return String.format(Locale.ROOT,
                    "Ordinary least-squares line: quantity = %.4f + %.4f × week; R² = %.4f",
                    intercept, slope, rSquared);
        }
    }

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
    /** One auditable weekly observation and its fitted trend value. */
    public record TrendPoint(LocalDate weekStart, double quantity, double trendQuantity) { }

    public record TrendResult(double predictedQuantity, String summary, String error,
                              String cropName, String unit, int observedWeeks,
                              double latestQuantity, LocalDate latestWeekStart,
                              LocalDate forecastWeekStart,
                              List<TrendPoint> weeklyPoints,
                              double slope, double intercept, double rSquared) {
        public TrendResult {
            weeklyPoints = weeklyPoints == null ? List.of() : List.copyOf(weeklyPoints);
        }

        public TrendResult(double predictedQuantity, String summary, String error) {
            this(predictedQuantity, summary, error,
                    "Farm output", "units", 0, Double.NaN, null, null,
                    List.of(), Double.NaN, Double.NaN, Double.NaN);
        }

        public TrendResult(double predictedQuantity, String summary, String error,
                           String cropName, String unit, int observedWeeks,
                           double latestQuantity) {
            this(predictedQuantity, summary, error,
                    cropName, unit, observedWeeks, latestQuantity, null, null,
                    List.of(), Double.NaN, Double.NaN, Double.NaN);
        }

        public TrendResult(double predictedQuantity, String summary, String error,
                           String cropName, String unit, int observedWeeks,
                           double latestQuantity, LocalDate latestWeekStart,
                           LocalDate forecastWeekStart) {
            this(predictedQuantity, summary, error,
                    cropName, unit, observedWeeks, latestQuantity,
                    latestWeekStart, forecastWeekStart,
                    List.of(), Double.NaN, Double.NaN, Double.NaN);
        }

        public boolean isSuccess() { return error == null; }
        public boolean needsHistory() {
            return error != null && error.startsWith("Record at least 3 separate weeks");
        }
    }
}
