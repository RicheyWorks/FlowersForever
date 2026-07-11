package com.flowerfarm.controller;

import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

/**
 * Market-day packing list for Kitsap growers.
 *
 * <ul>
 *   <li>{@code GET /api/market-day} — JSON plan</li>
 *   <li>{@code GET /api/market-day/text} — plain-text pack sheet</li>
 *   <li>{@code GET /api/market-day/export.csv} — CSV pick + order lines</li>
 *   <li>{@code GET /api/market-day/packing.pdf} — printable packing PDF</li>
 *   <li>{@code POST /api/market-day/fulfill} — fulfill all CONFIRMED orders in the plan</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/market-day")
public class MarketDayController {

    private final MarketDayPackingService packingService;

    public MarketDayController(MarketDayPackingService packingService) {
        this.packingService = packingService;
    }

    @GetMapping
    public Map<String, Object> plan(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "windowDays", defaultValue = "1") int windowDays,
            @RequestParam(value = "includeDraft", defaultValue = "false") boolean includeDraft,
            @RequestParam(value = "includeFulfilled", defaultValue = "false") boolean includeFulfilled) {
        return build(date, windowDays, includeDraft, includeFulfilled).toMap();
    }

    @GetMapping(value = "/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public String plainText(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "windowDays", defaultValue = "1") int windowDays,
            @RequestParam(value = "includeDraft", defaultValue = "false") boolean includeDraft,
            @RequestParam(value = "includeFulfilled", defaultValue = "false") boolean includeFulfilled) {
        return build(date, windowDays, includeDraft, includeFulfilled).plainText();
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "windowDays", defaultValue = "1") int windowDays,
            @RequestParam(value = "includeDraft", defaultValue = "false") boolean includeDraft,
            @RequestParam(value = "includeFulfilled", defaultValue = "false") boolean includeFulfilled) {
        MarketDayPlan plan = build(date, windowDays, includeDraft, includeFulfilled);
        byte[] body = packingService.exportCsv(plan).getBytes(StandardCharsets.UTF_8);
        String filename = "market-day-" + plan.marketDate() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping(value = "/packing.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> packingPdf(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "windowDays", defaultValue = "1") int windowDays,
            @RequestParam(value = "includeDraft", defaultValue = "false") boolean includeDraft,
            @RequestParam(value = "includeFulfilled", defaultValue = "false") boolean includeFulfilled) {
        MarketDayPlan plan = build(date, windowDays, includeDraft, includeFulfilled);
        byte[] pdf = packingService.generatePackingPdf(plan);
        String filename = "market-day-packing-" + plan.marketDate() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Post-market: fulfill every CONFIRMED order in the packing window
     * (deducts inventory, records ORDER_FULFILL audit).
     */
    @PostMapping("/fulfill")
    public Map<String, Object> fulfillConfirmed(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "windowDays", defaultValue = "1") int windowDays) {
        // Always CONFIRMED-only plan for safety (ignore draft/fulfilled checkboxes)
        MarketDayPlan plan = build(date, windowDays, false, false);
        return packingService.fulfillConfirmedOrders(plan).toMap();
    }

    private MarketDayPlan build(String date, int windowDays, boolean includeDraft, boolean includeFulfilled) {
        LocalDate d = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date.trim());
        return packingService.buildPlan(d, windowDays, includeDraft, includeFulfilled);
    }
}
