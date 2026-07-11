package com.flowerfarm.controller;

import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Combined executive KPIs for the farm dashboard (inventory + harvest week + revenue week).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final int DEFAULT_LOW_STOCK = 10;

    private final InventoryService inventoryService;
    private final HarvestService harvestService;
    private final OrderService orderService;

    public DashboardController(InventoryService inventoryService,
                               HarvestService harvestService,
                               OrderService orderService) {
        this.inventoryService = inventoryService;
        this.harvestService = harvestService;
        this.orderService = orderService;
    }

    /**
     * Snapshot used by UI / mobile / external tools.
     * {@code lowStockThreshold} defaults to 10 (matches Swing dashboard).
     */
    @GetMapping
    public Map<String, Object> snapshot(
            @RequestParam(value = "lowStockThreshold", defaultValue = "10") int lowStockThreshold) {
        int threshold = lowStockThreshold > 0 ? lowStockThreshold : DEFAULT_LOW_STOCK;
        InventoryService.InventoryKpiSnapshot inv = inventoryService.inventoryKpis(threshold);
        OrderService.WeekRevenueSummary rev = orderService.weekRevenueSummary();
        double harvestWeek = harvestService.totalQuantityLast7Days();
        double harvestPrior = harvestService.totalQuantityPrior7Days();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inventory", Map.of(
                "skuCount", inv.skuCount(),
                "sellValue", inv.sellValue(),
                "costBasis", inv.costBasis(),
                "lowStockCount", inv.lowStockCount(),
                "totalUnits", inv.totalUnits(),
                "lowStockThreshold", inv.lowStockThreshold()
        ));

        Map<String, Object> harvest = new LinkedHashMap<>();
        harvest.put("weekQuantity", harvestWeek);
        harvest.put("priorWeekQuantity", harvestPrior);
        harvest.put("weekOverWeekPercent", harvestService.weekOverWeekPercentChange());
        harvest.put("dailyQuantities", harvestService.dailyQuantitiesLast7Days());
        body.put("harvest", harvest);

        Map<String, Object> revenue = new LinkedHashMap<>();
        revenue.put("from", rev.from().toString());
        revenue.put("to", rev.to().toString());
        revenue.put("realized", rev.realized());
        revenue.put("pipeline", rev.pipeline());
        revenue.put("draft", rev.draft());
        revenue.put("booked", rev.booked());
        revenue.put("priorWeekRealized", orderService.realizedRevenuePrior7Days());
        revenue.put("weekOverWeekPercent", orderService.realizedWeekOverWeekPercentChange());
        revenue.put("fulfilledOrderCount", rev.fulfilledOrderCount());
        revenue.put("confirmedOrderCount", rev.confirmedOrderCount());
        revenue.put("draftOrderCount", rev.draftOrderCount());
        revenue.put("dailyRealized", orderService.dailyRevenueLast7Days());
        body.put("revenue", revenue);

        return body;
    }
}
