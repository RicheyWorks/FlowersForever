package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds today’s grower reminders: due field tasks, packing, quiet harvest weeks,
 * low stock, and Knowledge Log follow-ups.
 */
@Service
public class GrowerReminderService {

    private final ProductionStudioService productionStudioService;
    private final KnowledgeService knowledgeService;
    private final HarvestService harvestService;
    private final MarketDayPackingService marketDayPackingService;
    private final InventoryService inventoryService;
    private final FarmProfile farmProfile;

    public GrowerReminderService(ProductionStudioService productionStudioService,
                                 KnowledgeService knowledgeService,
                                 HarvestService harvestService,
                                 MarketDayPackingService marketDayPackingService,
                                 InventoryService inventoryService,
                                 FarmProfile farmProfile) {
        this.productionStudioService = productionStudioService;
        this.knowledgeService = knowledgeService;
        this.harvestService = harvestService;
        this.marketDayPackingService = marketDayPackingService;
        this.inventoryService = inventoryService;
        this.farmProfile = farmProfile;
    }

    @Transactional(readOnly = true)
    public GrowerReminders.Digest collect() {
        return collect(LocalDate.now(farmProfile.zoneId()));
    }

    @Transactional(readOnly = true)
    public GrowerReminders.Digest collect(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(farmProfile.zoneId()) : date;
        List<ProductionTask> tasks = productionStudioService.listOpenTasksDueThrough(day, 8);
        List<KnowledgeEntry> followUps = knowledgeService.search(new KnowledgeService.SearchRequest(
                "", "", "", "", null, null, true, 6));
        MarketDayPlan packing = marketDayPackingService.planForDay(day);
        boolean quietHarvest = harvestService.totalQuantityLast7Days() <= 0;
        List<Item> lowStock = lowStockItems(GrowerReminders.DEFAULT_LOW_STOCK);
        return GrowerReminders.assemble(day, tasks, followUps, packing, quietHarvest, lowStock,
                GrowerReminders.DEFAULT_LOW_STOCK);
    }

    private List<Item> lowStockItems(int threshold) {
        List<Item> low = new ArrayList<>();
        for (Item item : inventoryService.getAllItems()) {
            if (item != null && item.getQuantity() <= threshold) {
                low.add(item);
            }
        }
        low.sort(Comparator.comparingInt(Item::getQuantity).thenComparing(Item::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        if (low.size() > 6) {
            return List.copyOf(low.subList(0, 6));
        }
        return List.copyOf(low);
    }
}
