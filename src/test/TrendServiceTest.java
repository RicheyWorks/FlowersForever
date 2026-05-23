package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrendService")
class TrendServiceTest {

    @Mock
    private InventoryService inventoryService;

    private TrendService trendService;

    @BeforeEach
    void setUp() {
        trendService = new TrendService(inventoryService);
    }

    // ── Fallback path (< 3 items) ────────────────────────────────────────────

    @Test
    @DisplayName("analyzeQuantityTrend() succeeds using hardcoded fallback when inventory has 0 items")
    void fallbackOnEmptyInventory() {
        when(inventoryService.getAllItems()).thenReturn(List.of());

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.summary()).isNotBlank();
        assertThat(result.predictedQuantity()).isGreaterThan(0);
    }

    @Test
    @DisplayName("analyzeQuantityTrend() succeeds using fallback when inventory has exactly 1 item")
    void fallbackOnSingleItem() {
        when(inventoryService.getAllItems()).thenReturn(
                List.of(new Item("Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 40, "")));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("analyzeQuantityTrend() succeeds using fallback when inventory has exactly 2 items")
    void fallbackOnTwoItems() {
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Rose A", "Flowers/Plants", 3.50, "Per Stem", 2.00, 40, ""),
                new Item("Rose B", "Flowers/Plants", 2.50, "Per Stem", 1.50, 60, "")
        ));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isTrue();
    }

    // ── Live inventory path (≥ 3 items) ─────────────────────────────────────

    @Test
    @DisplayName("analyzeQuantityTrend() uses live inventory quantities when ≥ 3 items available")
    void usesLiveInventoryForRegression() {
        when(inventoryService.getAllItems()).thenReturn(buildItemsWithQuantities(50, 60, 70, 80, 90));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isTrue();
        // Linear trend: each week +10, so week 6 should be ~100
        assertThat(result.predictedQuantity()).isGreaterThan(80);
    }

    @Test
    @DisplayName("summary contains 'Predicted quantity' when analysis succeeds")
    void summaryContainsPrediction() {
        when(inventoryService.getAllItems()).thenReturn(buildItemsWithQuantities(100, 120, 110, 130));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.summary())
                .contains("Predicted quantity")
                .contains("Week 1")
                .contains("Model details");
    }

    @Test
    @DisplayName("summary lists every data point from live inventory")
    void summaryListsAllDataPoints() {
        List<Item> items = buildItemsWithQuantities(10, 20, 30);
        when(inventoryService.getAllItems()).thenReturn(items);

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.summary())
                .contains("Week 1:")
                .contains("Week 2:")
                .contains("Week 3:");
    }

    @Test
    @DisplayName("predictedQuantity() is reasonable for a perfectly linear ascending dataset")
    void predictionIsReasonableForLinearData() {
        // quantities 10, 20, 30, 40, 50 → next should be ~60
        when(inventoryService.getAllItems()).thenReturn(buildItemsWithQuantities(10, 20, 30, 40, 50));

        TrendService.TrendResult result = trendService.analyzeQuantityTrend();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.predictedQuantity()).isBetween(55.0, 65.0);
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

    private List<Item> buildItemsWithQuantities(int... quantities) {
        List<Item> items = new ArrayList<>();
        int i = 1;
        for (int qty : quantities) {
            items.add(new Item("Item-" + i++, "Other", 1.00, "Per Unit", 0.50, qty, ""));
        }
        return items;
    }
}
