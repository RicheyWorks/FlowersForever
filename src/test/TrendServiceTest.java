package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrendService")
class TrendServiceTest {

    @Mock
    private HarvestService harvestService;

    private TrendService trendService;

    @BeforeEach
    void setUp() {
        trendService = new TrendService(harvestService,
                Clock.fixed(LocalDate.of(2026, 8, 24)
                        .atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    // ── Honest insufficient-history states ───────────────────────────────────

    @Test
    @DisplayName("empty harvest history does not substitute sample data")
    void emptyHistoryNeedsRealData() {
        when(harvestService.getAll()).thenReturn(List.of());

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.needsHistory()).isTrue();
        assertThat(result.observedWeeks()).isZero();
        assertThat(result.error()).contains("at least 3 separate weeks");
    }

    @Test
    @DisplayName("multiple entries in one week still count as one observed week")
    void oneObservedWeekIsInsufficient() {
        when(harvestService.getAll()).thenReturn(List.of(
                harvest(0, "Rose", 20), harvest(0, "Rose", 30)));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.observedWeeks()).isEqualTo(1);
        assertThat(result.cropName()).isEqualTo("Rose");
    }

    @Test
    @DisplayName("two recorded weeks return actionable history guidance")
    void twoWeeksAreInsufficient() {
        when(harvestService.getAll()).thenReturn(List.of(
                harvest(0, "Rose", 40), harvest(1, "Rose", 60)));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.observedWeeks()).isEqualTo(2);
        assertThat(result.error()).contains("Best available series has 2 weeks");
    }

    // ── Real weekly harvest path ─────────────────────────────────────────────

    @Test
    @DisplayName("uses real weekly harvest totals for regression")
    void usesWeeklyHarvestTotals() {
        when(harvestService.getAll()).thenReturn(weeklyHarvests("Nootka Rose", 50, 60, 70, 80, 90));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.predictedQuantity()).isBetween(125.0, 135.0);
        assertThat(result.cropName()).isEqualTo("Nootka Rose");
        assertThat(result.unit()).isEqualTo("stems");
        assertThat(result.observedWeeks()).isEqualTo(5);
        assertThat(result.latestQuantity()).isEqualTo(90);
        assertThat(result.latestWeekStart()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(result.forecastWeekStart()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("summary contains 'Predicted quantity' when analysis succeeds")
    void summaryContainsPrediction() {
        when(harvestService.getAll()).thenReturn(weeklyHarvests("Dahlia", 100, 120, 110, 130));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.summary())
                .contains("Projected week of 2026-08-31")
                .contains("Crop: Dahlia")
                .contains("real Harvest Log entries")
                .contains("Model diagnostics");
    }

    @Test
    @DisplayName("same-week harvests are aggregated before forecasting")
    void aggregatesSameWeekEntries() {
        when(harvestService.getAll()).thenReturn(List.of(
                harvest(0, "Rose", 10), harvest(0, "Rose", 15),
                harvest(1, "Rose", 30), harvest(2, "Rose", 40)));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.summary())
                .contains("Week of 2026-07-06: 25 stems")
                .contains("Week of 2026-07-13: 30 stems")
                .contains("Week of 2026-07-20: 40 stems");
    }

    @Test
    @DisplayName("predictedQuantity() is reasonable for a perfectly linear ascending dataset")
    void predictionIsReasonableForLinearData() {
        // quantities 10, 20, 30, 40, 50 → next should be ~60
        when(harvestService.getAll()).thenReturn(weeklyHarvests("Rose", 10, 20, 30, 40, 50));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.predictedQuantity()).isBetween(85.0, 95.0);
    }

    @Test
    @DisplayName("selects the crop/unit series with the strongest weekly history")
    void selectsBestSupportedSeries() {
        List<HarvestEntry> entries = new java.util.ArrayList<>(weeklyHarvests("Rose", 10, 20));
        entries.addAll(weeklyHarvests("Dahlia", 30, 35, 40, 45));
        when(harvestService.getAll()).thenReturn(entries);

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.cropName()).isEqualTo("Dahlia");
        assertThat(result.observedWeeks()).isEqualTo(4);
    }

    // ── TrendResult record ───────────────────────────────────────────────────

    @Test
    @DisplayName("TrendResult.isSuccess() is true when error is null")
    void trendResultIsSuccessWhenNoError() {
        TrendService.TrendResult r = new TrendService.TrendResult(100.0, "summary text", null);
        assertThat(r.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("TrendResult.isSuccess() is false when error is non-null")
    void trendResultIsFailureWhenErrorPresent() {
        TrendService.TrendResult r = new TrendService.TrendResult(0, null, "Weka exploded");
        assertThat(r.isSuccess()).isFalse();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<HarvestEntry> weeklyHarvests(String crop, int... quantities) {
        List<HarvestEntry> entries = new java.util.ArrayList<>();
        for (int week = 0; week < quantities.length; week++) {
            entries.add(harvest(week, crop, quantities[week]));
        }
        return entries;
    }

    private HarvestEntry harvest(int week, String crop, double quantity) {
        return new HarvestEntry(LocalDate.of(2026, 7, 6).plusWeeks(week),
                crop, quantity, "stems", "Field A", "");
    }
}
