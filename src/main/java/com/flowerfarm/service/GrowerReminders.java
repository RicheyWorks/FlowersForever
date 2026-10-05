package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure assembly of today’s grower reminders from already-loaded farm facts.
 * Keeps wording shared by tests, the dashboard banner, and the Help dialog.
 */
public final class GrowerReminders {

    public static final int DEFAULT_LOW_STOCK = 10;
    public static final int MAX_ITEMS = 12;

    private GrowerReminders() {
    }

    public record Digest(LocalDate date, List<GrowerReminder> items, String headline, String guidance) {
        public Digest {
            date = Objects.requireNonNull(date, "date");
            items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
            headline = headline == null || headline.isBlank()
                    ? (items.isEmpty() ? "No reminders for today" : items.size() + " reminder"
                    + (items.size() == 1 ? "" : "s") + " for today")
                    : headline.trim();
            guidance = guidance == null ? "" : guidance.trim();
        }

        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    public static Digest assemble(LocalDate date,
                                  List<ProductionTask> dueTasks,
                                  List<KnowledgeEntry> followUps,
                                  MarketDayPlan packing,
                                  boolean harvestQuietWeek,
                                  List<Item> lowStock,
                                  int lowStockThreshold) {
        List<GrowerReminder> items = new ArrayList<>();
        addTasks(items, dueTasks, date);
        addPack(items, packing);
        addHarvest(items, harvestQuietWeek);
        addStock(items, lowStock, lowStockThreshold);
        addKnowledge(items, followUps);
        if (items.size() > MAX_ITEMS) {
            items = new ArrayList<>(items.subList(0, MAX_ITEMS));
        }
        String guidance = items.isEmpty()
                ? "Morning briefing and the priority queue still cover deeper planning."
                : "Open a reminder to jump to that screen. Help > Today’s reminders… keeps this list handy.";
        String headline = items.isEmpty()
                ? "You’re caught up for today"
                : items.size() + " reminder" + (items.size() == 1 ? "" : "s") + " for "
                + date;
        return new Digest(date, items, headline, guidance);
    }

    public static String taskTitle(ProductionTask task) {
        Objects.requireNonNull(task, "task");
        return taskLabel(task.getTaskType()) + " · " + safe(task.getCropName())
                + " (" + safe(task.getBedOrField()) + ")";
    }

    public static String taskLabel(ProductionTaskType type) {
        if (type == null) {
            return "Field task";
        }
        return switch (type) {
            case SOIL_PREP -> "Soil prep";
            case SOW -> "Sow";
            case TRANSPLANT -> "Transplant";
            case SUPPORT -> "Support / netting";
            case HARVEST_PREP -> "Harvest prep";
            case FIRST_HARVEST -> "First harvest";
            case CUSTOM -> "Field task";
        };
    }

    public static String traySummary(Digest digest) {
        if (digest == null || digest.isEmpty()) {
            return "No farm reminders waiting.";
        }
        GrowerReminder first = digest.items().get(0);
        if (digest.items().size() == 1) {
            return first.title();
        }
        return first.title() + " · +" + (digest.items().size() - 1) + " more";
    }

    private static void addTasks(List<GrowerReminder> items, List<ProductionTask> dueTasks,
                                 LocalDate date) {
        if (dueTasks == null) {
            return;
        }
        for (ProductionTask task : dueTasks) {
            if (task == null || task.getDueDate() == null) {
                continue;
            }
            boolean overdue = task.getDueDate().isBefore(date);
            String detail = overdue
                    ? "Due " + task.getDueDate() + " — open Production Studio to finish or skip."
                    : "Due today — open Production Studio Field work.";
            items.add(new GrowerReminder(GrowerReminder.Kind.FIELD_TASK, taskTitle(task), detail,
                    "Production Studio"));
        }
    }

    private static void addPack(List<GrowerReminder> items, MarketDayPlan packing) {
        if (packing == null || packing.orderCount() <= 0) {
            return;
        }
        String title = packing.shortfallSkuCount() > 0
                ? "Pack today · " + packing.shortfallSkuCount() + " shortfall(s)"
                : "Pack today · " + packing.orderCount() + " order(s)";
        String detail = packing.shortfallSkuCount() > 0
                ? "Confirmed orders need packing review; some products are short."
                : "Confirmed orders are ready for Market Day packing.";
        items.add(new GrowerReminder(GrowerReminder.Kind.PACK, title, detail, "Market Day"));
    }

    private static void addHarvest(List<GrowerReminder> items, boolean harvestQuietWeek) {
        if (!harvestQuietWeek) {
            return;
        }
        items.add(new GrowerReminder(GrowerReminder.Kind.HARVEST,
                "No harvest logged in the last 7 days",
                "Log today’s cut if you harvested — or open Harvest Log to catch up.",
                "Harvest Log"));
    }

    private static void addStock(List<GrowerReminder> items, List<Item> lowStock, int threshold) {
        if (lowStock == null || lowStock.isEmpty()) {
            return;
        }
        Item first = lowStock.get(0);
        String title = lowStock.size() == 1
                ? "Low stock · " + safe(first.getName())
                : "Low stock · " + lowStock.size() + " products under " + threshold;
        String detail = lowStock.size() == 1
                ? safe(first.getName()) + " is at " + first.getQuantity()
                + " " + safe(first.getUnit()) + "."
                : "Review Inventory for products at or under " + threshold + ".";
        items.add(new GrowerReminder(GrowerReminder.Kind.STOCK, title, detail, "Inventory"));
    }

    private static void addKnowledge(List<GrowerReminder> items, List<KnowledgeEntry> followUps) {
        if (followUps == null) {
            return;
        }
        for (KnowledgeEntry entry : followUps) {
            if (entry == null || entry.getFollowUpDate() == null) {
                continue;
            }
            String crop = entry.getCropName() == null || entry.getCropName().isBlank()
                    ? "Farm lesson" : entry.getCropName().trim();
            items.add(new GrowerReminder(GrowerReminder.Kind.KNOWLEDGE,
                    "Follow-up · " + crop,
                    "Knowledge Log follow-up was due " + entry.getFollowUpDate() + ".",
                    "Knowledge Log"));
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }
}
