package com.flowerfarm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Florist availability export — Florlist-friendly CSV and share text")
class FloristAvailabilityExportTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 5);
    private static final LocalDate WEEK = LocalDate.of(2026, 10, 5); // Monday

    @Test
    @DisplayName("CSV header is Florlist-friendly and rows carry live qty plus planned stems")
    void csvShape() {
        FloristAvailabilityExport.Sheet sheet = sampleSheet();
        String csv = FloristAvailabilityExport.csv(sheet);
        assertThat(csv).startsWith(
                "Product,Quantity,Unit,Price,Week start,Week end,Planned stems,Notes\n");
        assertThat(csv).contains("Nootka Rose,96,stems,1.25,2026-10-05,2026-10-11,120,");
        assertThat(csv).contains("Dahlia mix,20,bunches,8.00,");
    }

    @Test
    @DisplayName("share text names the farm and separates live from planned")
    void shareText() {
        String text = FloristAvailabilityExport.shareText(sampleSheet());
        assertThat(text).contains("Kitsap Blooms", "flower availability", "Contact:")
                .contains("Nootka Rose")
                .contains("Florlist")
                .containsIgnoringCase("live cooler stock");
    }

    @Test
    @DisplayName("merge keeps only in-stock products and attaches planned stems by crop name")
    void mergeLiveAndPlanned() {
        List<InventoryService.PriceListLine> live = List.of(
                new InventoryService.PriceListLine(1L, "Nootka Rose", "Cut flowers",
                        1.25, "stems", 96, 120, 0.4, 38.4),
                new InventoryService.PriceListLine(2L, "Zero Stock", "Cut flowers",
                        1.0, "stems", 0, 0, 0.2, 0));
        List<ProductionStudioService.AvailabilityLine> planned = List.of(
                new ProductionStudioService.AvailabilityLine(
                        "Nootka Rose", WEEK, WEEK.plusDays(6), 120, 96, 216,
                        "2026-09-01–2026-10-30"));
        List<FloristAvailabilityExport.Line> lines = FloristAvailabilityExport.merge(
                live, planned, WEEK, WEEK.plusDays(6));
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).product()).isEqualTo("Nootka Rose");
        assertThat(lines.get(0).liveQuantity()).isEqualTo(96);
        assertThat(lines.get(0).plannedStems()).isEqualTo(120);
        assertThat(lines.get(0).notes()).contains("Harvest window");
    }

    @Test
    @DisplayName("empty sheet explains there is nothing to share")
    void emptyShare() {
        FloristAvailabilityExport.Sheet empty = new FloristAvailabilityExport.Sheet(
                "Farm", "", DAY, WEEK, WEEK.plusDays(6), List.of());
        assertThat(empty.isEmpty()).isTrue();
        assertThat(FloristAvailabilityExport.shareText(empty)).contains("No in-stock");
        assertThat(FloristAvailabilityExport.suggestedCsvFileName(empty))
                .isEqualTo("florist-availability-2026-10-05.csv");
    }

    private static FloristAvailabilityExport.Sheet sampleSheet() {
        return new FloristAvailabilityExport.Sheet(
                "Kitsap Blooms",
                "360-555-0100 · hello@kitsap.example",
                DAY,
                WEEK,
                WEEK.plusDays(6),
                List.of(
                        new FloristAvailabilityExport.Line(
                                "Nootka Rose", "stems", 96, 1.25, 120, WEEK, WEEK.plusDays(6),
                                "Harvest window: 2026-09-01–2026-10-30"),
                        new FloristAvailabilityExport.Line(
                                "Dahlia mix", "bunches", 20, 8.00, 0, WEEK, WEEK.plusDays(6),
                                "Live stock")));
    }
}
