package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.Item;
import com.flowerfarm.repository.InventoryRepository;
import com.flowerfarm.util.CsvSupport;
import com.flowerfarm.util.InventoryUnits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Flower-farm inventory operations backed by {@link InventoryRepository}.
 *
 * <p>Default production store is JPA + file H2. On first launch (empty DB) the
 * service seeds from {@code flowerfarm.inventory.seed-csv} when present, otherwise
 * from a small PNW sample set. CSV export remains available for backups and
 * spreadsheet workflows; the CSV connector is unchanged.
 *
 * <p>Index-based edit/delete are preserved for the Swing GUI; id-based methods
 * are preferred for REST and new code.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final int MAX_LOW_STOCK_THRESHOLD = 1_000_000;

    /** Standard CSV header shared by export and legacy seed files. */
    public static final String CSV_HEADER = "Name,Category,Price,Unit,Cost,Quantity,Notes";

    private final InventoryRepository repository;
    private final String seedCsvPath;
    private final InventoryReportGenerator reportGenerator;
    private final InventoryCsvExporter csvExporter;
    private final FarmProfile farmProfile;

    public InventoryService(
            InventoryRepository repository,
            @Value("${flowerfarm.inventory.seed-csv:farm_inventory.csv}") String seedCsvPath,
            InventoryReportGenerator reportGenerator,
            InventoryCsvExporter csvExporter,
            FarmProfile farmProfile) {
        this.repository = repository;
        this.reportGenerator = reportGenerator;
        this.csvExporter = csvExporter;
        this.farmProfile = farmProfile;
        this.seedCsvPath = seedCsvPath == null || seedCsvPath.isBlank()
                ? "farm_inventory.csv"
                : seedCsvPath.trim();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @PostConstruct
    @Transactional
    public void init() {
        long existing = repository.count();
        if (existing > 0) {
            log.info("InventoryService initialised — {} item(s) already in database.", existing);
            return;
        }

        List<Item> seed = loadSeedFromCsv();
        if (seed.isEmpty()) {
            seed = buildSampleInventory();
            log.info("No seed CSV at '{}' — loading {} sample PNW inventory item(s).",
                    seedCsvPath, seed.size());
        } else {
            log.info("Seeding database from '{}' ({} item(s)).", seedCsvPath, seed.size());
        }
        repository.saveAll(seed);
        log.info("InventoryService initialised — {} item(s) persisted.", repository.count());
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public synchronized List<Item> getAllItems() {
        return new ArrayList<>(repository.findAllOrdered());
    }

    @Transactional(readOnly = true)
    public synchronized Optional<Item> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public synchronized List<Item> searchItems(String query) {
        return new ArrayList<>(repository.search(query));
    }

    @Transactional
    public synchronized Item addItem(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        ensureNameAvailable(item.getName(), null);
        // Force insert
        item.setId(null);
        Item saved = repository.save(item);
        log.debug("Added item id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    /** Raised when an interactive add would create an ambiguous exact-name SKU. */
    public static final class DuplicateItemNameException extends IllegalArgumentException {
        private final Long existingId;
        private final String itemName;

        public DuplicateItemNameException(Long existingId, String itemName) {
            super("Inventory already contains ‘" + itemName
                    + "’. Open the existing item to count, replenish, or edit it.");
            this.existingId = existingId;
            this.itemName = itemName;
        }

        public Long existingId() {
            return existingId;
        }

        public String itemName() {
            return itemName;
        }
    }

    /**
     * Idempotent connector-import boundary: update an exact-name SKU when it
     * exists, otherwise insert it. The database lookup is locked for updates.
     */
    @Transactional
    public synchronized Item upsertByName(Item incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        Optional<Item> existing = repository
                .findAllByNameIgnoreCaseForUpdate(incoming.getName()).stream().findFirst();
        if (existing.isPresent()) {
            Item target = existing.get();
            target.copyBusinessFieldsFrom(incoming);
            Item saved = repository.save(target);
            log.debug("Upsert updated inventory id={} name={}", saved.getId(), saved.getName());
            return saved;
        }
        incoming.setId(null);
        Item saved = repository.save(incoming);
        log.debug("Upsert inserted inventory id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Replaces the item at list position {@code index} (ordered by id).
     * Prefer {@link #updateById(Long, Item)} when the id is known.
     */
    @Transactional
    public synchronized Item editItem(int index, Item newItem) {
        if (newItem == null) {
            throw new IllegalArgumentException("Replacement item must not be null.");
        }
        Long id = itemAtIndex(index).getId();
        Item saved = updateById(id, newItem);
        log.debug("Edited item at index {} id={}", index, saved.getId());
        return saved;
    }

    @Transactional
    public synchronized Item updateById(Long id, Item newItem) {
        if (newItem == null) {
            throw new IllegalArgumentException("Replacement item must not be null.");
        }
        Item existing = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No inventory item with id=" + id));
        ensureNameAvailable(newItem.getName(), id);
        existing.copyBusinessFieldsFrom(newItem);
        Item saved = repository.save(existing);
        log.debug("Updated item id={}", id);
        return saved;
    }

    /**
     * Replaces one on-hand quantity only when the worker's reviewed quantity is
     * still current. The row lock prevents harvest, fulfillment, imports, or a
     * second count from being silently overwritten between review and commit.
     */
    @Transactional
    public synchronized Item recordPhysicalCount(
            Long id, int reviewedQuantity, int countedQuantity) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Inventory item id must be positive.");
        }
        if (reviewedQuantity < 0) {
            throw new IllegalArgumentException("Reviewed quantity cannot be negative.");
        }
        if (countedQuantity < 0) {
            throw new IllegalArgumentException("Counted quantity cannot be negative.");
        }
        Item item = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No inventory item with id=" + id));
        if (item.getQuantity() != reviewedQuantity) {
            throw new InventoryCountConflictException(item.getName(), item.getQuantity());
        }
        item.setQuantity(countedQuantity);
        Item saved = repository.save(item);
        log.info("Recorded physical count for inventory '{}' id={}: {} -> {} {}",
                saved.getName(), saved.getId(), reviewedQuantity,
                saved.getQuantity(), saved.getUnit());
        return saved;
    }

    private void ensureNameAvailable(String name, Long excludedId) {
        Item conflict = repository.findAllByNameIgnoreCaseForUpdate(name).stream()
                .filter(item -> excludedId == null || !excludedId.equals(item.getId()))
                .findFirst().orElse(null);
        if (conflict != null) {
            throw new DuplicateItemNameException(conflict.getId(), conflict.getName());
        }
    }

    @Transactional
    public synchronized void deleteItem(int index) {
        Long id = itemAtIndex(index).getId();
        Item existing = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "Inventory item at index " + index + " no longer exists."));
        repository.deleteById(existing.getId());
        log.debug("Deleted item at index {} id={}", index, existing.getId());
    }

    @Transactional
    public synchronized void deleteById(Long id) {
        if (repository.findByIdForUpdate(id).isEmpty()) {
            throw new IndexOutOfBoundsException("No inventory item with id=" + id);
        }
        repository.deleteById(id);
        log.debug("Deleted item id={}", id);
    }

    /**
     * Case-insensitive exact name match (first hit). Used by CRM fulfillment.
     */
    @Transactional(readOnly = true)
    public synchronized Optional<Item> findByNameIgnoreCase(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String key = name.trim().toLowerCase();
        return repository.findAllOrdered().stream()
                .filter(i -> i.getName() != null && i.getName().toLowerCase().equals(key))
                .findFirst();
    }

    /**
     * Decrements quantity for the named SKU (floored at zero). Returns empty if
     * no matching inventory row exists.
     */
    @Transactional
    public synchronized Optional<Item> decrementQuantityByName(String name, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Decrement amount cannot be negative.");
        }
        Optional<Item> found = repository.findAllByNameIgnoreCaseForUpdate(name).stream().findFirst();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Item item = found.get();
        int next = Math.max(0, item.getQuantity() - amount);
        item.setQuantity(next);
        Item saved = repository.save(item);
        log.info("Inventory '{}' qty → {} (decremented by {})", saved.getName(), next, amount);
        return Optional.of(saved);
    }

    /**
     * Decrements a required SKU without silently overselling it.
     * Used by order fulfillment, where a missing SKU or shortfall must abort the
     * enclosing transaction instead of producing a partially deducted order.
     */
    @Transactional
    public synchronized Item decrementQuantityByNameStrict(String name, int amount) {
        return decrementQuantityByNameStrict(name, amount, null);
    }

    /**
     * Strict fulfillment decrement that also verifies the order and inventory units agree.
     */
    @Transactional
    public synchronized Item decrementQuantityByNameStrict(
            String name, int amount, String expectedUnit) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Inventory item name is required.");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Decrement amount cannot be negative.");
        }
        Item item = repository.findAllByNameIgnoreCaseForUpdate(name).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cannot fulfill: no inventory SKU matches '" + name.trim() + "'."));
        if (expectedUnit != null && !expectedUnit.isBlank()
                && !InventoryUnits.equivalent(item.getUnit(), expectedUnit)) {
            throw new IllegalArgumentException(
                    "Cannot fulfill '" + item.getName() + "': order unit "
                            + expectedUnit.trim() + " does not match inventory unit "
                            + item.getUnit() + ".");
        }
        if (item.getQuantity() < amount) {
            throw new IllegalArgumentException(
                    "Cannot fulfill '" + item.getName() + "': requires " + amount
                            + " " + item.getUnit() + " but only " + item.getQuantity()
                            + " available.");
        }
        item.setQuantity(item.getQuantity() - amount);
        Item saved = repository.save(item);
        log.info("Inventory '{}' qty → {} (strict decrement by {})",
                saved.getName(), saved.getQuantity(), amount);
        return saved;
    }

    /**
     * Increases quantity for the named SKU. If no SKU exists, creates one under
     * category {@code Flowers/Plants} so harvest logging always grows stock.
     */
    @Transactional
    public synchronized Item incrementQuantityByName(String name, int amount, String unit, String notesHint) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name is required.");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Increment amount cannot be negative.");
        }
        Optional<Item> found = repository.findAllByNameIgnoreCaseForUpdate(name).stream().findFirst();
        if (found.isPresent()) {
            Item item = found.get();
            final int next;
            try {
                next = Math.addExact(item.getQuantity(), amount);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "Inventory quantity would exceed the supported maximum.", exception);
            }
            item.setQuantity(next);
            Item saved = repository.save(item);
            log.info("Inventory '{}' qty → {} (incremented by {})", saved.getName(), saved.getQuantity(), amount);
            return saved;
        }
        String u = (unit == null || unit.isBlank()) ? "stems" : unit.trim();
        String notes = notesHint == null || notesHint.isBlank()
                ? "Auto-created from harvest log"
                : notesHint.trim();
        Item created = new Item(name.trim(), "Flowers/Plants", 0.0, u, 0.0, amount, notes);
        Item saved = repository.save(created);
        log.info("Inventory created '{}' with qty {} from harvest", saved.getName(), amount);
        return saved;
    }

    /**
     * Snapshot of inventory KPIs for the dashboard / API.
     */
    @Transactional(readOnly = true)
    public InventoryKpiSnapshot inventoryKpis(int lowStockThreshold) {
        int threshold = normalizeLowStockThreshold(lowStockThreshold);
        List<Item> items = getAllItems();
        int skuCount = items.size();
        double sellValue = 0;
        double costBasis = 0;
        int lowStock = 0;
        long totalUnits = 0;
        for (Item i : items) {
            sellValue += i.getPrice() * i.getQuantity();
            costBasis += i.getCost() * i.getQuantity();
            totalUnits += i.getQuantity();
            if (i.getQuantity() <= threshold) {
                lowStock++;
            }
        }
        return new InventoryKpiSnapshot(skuCount, sellValue, costBasis, lowStock, totalUnits, threshold);
    }

    public record InventoryKpiSnapshot(
            int skuCount,
            double sellValue,
            double costBasis,
            int lowStockCount,
            long totalUnits,
            int lowStockThreshold
    ) {}

    /**
     * One SKU on the barn reorder sheet: current qty vs threshold and a
     * simple restock suggestion (bring back to at least 2× threshold).
     */
    public record LowStockLine(
            Long id,
            String name,
            String category,
            int quantity,
            String unit,
            double unitPrice,
            double unitCost,
            int threshold,
            int suggestedOrderQty,
            double suggestedOrderCost
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("category", category);
            m.put("quantity", quantity);
            m.put("unit", unit);
            m.put("unitPrice", unitPrice);
            m.put("unitCost", unitCost);
            m.put("threshold", threshold);
            m.put("suggestedOrderQty", suggestedOrderQty);
            m.put("suggestedOrderCost", suggestedOrderCost);
            return m;
        }
    }

    public record LowStockReport(
            LocalDate date,
            int threshold,
            int skuCount,
            int lowStockCount,
            double suggestedOrderCostTotal,
            List<LowStockLine> lines,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date.toString());
            m.put("threshold", threshold);
            m.put("skuCount", skuCount);
            m.put("lowStockCount", lowStockCount);
            m.put("suggestedOrderCostTotal", suggestedOrderCostTotal);
            m.put("lines", lines.stream().map(LowStockLine::toMap).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    /** Items with quantity ≤ threshold, lowest stock first. */
    @Transactional(readOnly = true)
    public List<Item> findLowStock(int threshold) {
        int t = normalizeLowStockThreshold(threshold);
        return getAllItems().stream()
                .filter(i -> i.getQuantity() <= t)
                .sorted(Comparator.comparingInt(Item::getQuantity)
                        .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Barn reorder sheet: SKUs at/under threshold with suggested restock qty
     * (target = max(threshold × 2, threshold + 1) − on-hand).
     */
    @Transactional(readOnly = true)
    public LowStockReport buildLowStockReport(int threshold) {
        int t = normalizeLowStockThreshold(threshold);
        List<Item> all = getAllItems();
        List<LowStockLine> lines = new ArrayList<>();
        double costTotal = 0;
        for (Item i : findLowStock(t)) {
            int target = Math.max(t * 2, t + 1);
            int suggest = Math.max(0, target - i.getQuantity());
            double lineCost = suggest * i.getCost();
            costTotal += lineCost;
            lines.add(new LowStockLine(
                    i.getId(),
                    i.getName(),
                    i.getCategory() == null ? "" : i.getCategory(),
                    i.getQuantity(),
                    i.getUnit() == null ? "" : i.getUnit(),
                    i.getPrice(),
                    i.getCost(),
                    t,
                    suggest,
                    lineCost
            ));
        }
        String text = formatLowStockText(t, all.size(), lines, costTotal);
        return new LowStockReport(
                LocalDate.now(farmProfile.zoneId()),
                t,
                all.size(),
                lines.size(),
                costTotal,
                List.copyOf(lines),
                text
        );
    }

    public byte[] generateLowStockPdf(LowStockReport report) {
        return reportGenerator.generateLowStockPdf(report);
    }

    /**
     * One line on the market / wholesale price list.
     */
    public record PriceListLine(
            Long id,
            String name,
            String category,
            double unitPrice,
            String unit,
            int quantity,
            double lineSellValue,
            double unitCost,
            double lineCostBasis
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("category", category);
            m.put("unitPrice", unitPrice);
            m.put("unit", unit);
            m.put("quantity", quantity);
            m.put("lineSellValue", lineSellValue);
            m.put("unitCost", unitCost);
            m.put("lineCostBasis", lineCostBasis);
            return m;
        }
    }

    public record PriceListReport(
            LocalDate date,
            int skuCount,
            int totalUnits,
            double sellValue,
            double costBasis,
            boolean inStockOnly,
            List<PriceListLine> lines,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date.toString());
            m.put("skuCount", skuCount);
            m.put("totalUnits", totalUnits);
            m.put("sellValue", sellValue);
            m.put("costBasis", costBasis);
            m.put("inStockOnly", inStockOnly);
            m.put("lines", lines.stream().map(PriceListLine::toMap).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    /**
     * Market / wholesale price list from inventory.
     * When {@code inStockOnly}, zero-qty SKUs are omitted (booth sheet).
     */
    @Transactional(readOnly = true)
    public PriceListReport buildPriceListReport(boolean inStockOnly) {
        List<Item> items = getAllItems().stream()
                .filter(i -> !inStockOnly || i.getQuantity() > 0)
                .sorted(Comparator
                        .comparing((Item i) -> i.getCategory() == null ? "" : i.getCategory(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<PriceListLine> lines = new ArrayList<>();
        double sell = 0;
        double cost = 0;
        int units = 0;
        for (Item i : items) {
            double lineSell = i.getPrice() * i.getQuantity();
            double lineCost = i.getCost() * i.getQuantity();
            sell += lineSell;
            cost += lineCost;
            units += i.getQuantity();
            lines.add(new PriceListLine(
                    i.getId(),
                    i.getName(),
                    i.getCategory() == null ? "" : i.getCategory(),
                    i.getPrice(),
                    i.getUnit() == null ? "" : i.getUnit(),
                    i.getQuantity(),
                    lineSell,
                    i.getCost(),
                    lineCost
            ));
        }
        String text = formatPriceListText(inStockOnly, lines, sell, cost, units);
        return new PriceListReport(
                LocalDate.now(farmProfile.zoneId()),
                lines.size(),
                units,
                sell,
                cost,
                inStockOnly,
                List.copyOf(lines),
                text
        );
    }

    public byte[] generatePriceListPdf(PriceListReport report) {
        return reportGenerator.generatePriceListPdf(report);
    }

    private String formatPriceListText(boolean inStockOnly, List<PriceListLine> lines,
                                       double sell, double cost, int units) {
        StringBuilder sb = new StringBuilder();
        sb.append(farmProfile.documentBanner("Price List").toUpperCase(Locale.ROOT))
                .append('\n');
        sb.append("═══════════════════════════════════════\n");
        sb.append("Date: ").append(LocalDate.now(farmProfile.zoneId()))
                .append(inStockOnly ? "  ·  In-stock only" : "  ·  All SKUs")
                .append('\n');
        sb.append(String.format(Locale.US,
                "SKUs: %d  ·  Units: %d  ·  Sell $%,.2f  ·  Cost $%,.2f%n",
                lines.size(), units, sell, cost));
        sb.append('\n');
        if (lines.isEmpty()) {
            sb.append("  (empty)\n");
        } else {
            sb.append(String.format(Locale.US, "%-22s %-14s %8s %6s %8s%n",
                    "SKU", "Category", "Unit $", "Qty", "Ext. $"));
            sb.append("-".repeat(64)).append('\n');
            for (PriceListLine l : lines) {
                sb.append(String.format(Locale.US, "%-22s %-14s %8.2f %6d %8.2f%n",
                        truncate(l.name(), 22),
                        truncate(l.category(), 14),
                        l.unitPrice(),
                        l.quantity(),
                        l.lineSellValue()));
            }
            sb.append("-".repeat(64)).append('\n');
            sb.append(String.format(Locale.US, "TOTAL SELL  $%,.2f%n", sell));
        }
        sb.append("\nTip: print for the market table; pair with packing PDF on van days.\n");
        return sb.toString();
    }

    private String formatLowStockText(int threshold, int skuCount,
                                      List<LowStockLine> lines, double costTotal) {
        StringBuilder sb = new StringBuilder();
        sb.append(farmProfile.documentBanner("Low-Stock Reorder").toUpperCase(Locale.ROOT))
                .append('\n');
        sb.append("═══════════════════════════════════════════════\n");
        sb.append("Date: ").append(LocalDate.now(farmProfile.zoneId()))
                .append("  ·  Threshold: ≤ ").append(threshold)
                .append("  ·  SKUs total: ").append(skuCount).append('\n');
        sb.append(String.format(Locale.US,
                "Low SKUs: %d  ·  Suggested restock cost: $%,.2f%n",
                lines.size(), costTotal));
        sb.append('\n');
        if (lines.isEmpty()) {
            sb.append("  All clear — nothing under threshold.\n");
        } else {
            sb.append(String.format(Locale.US, "%-24s %6s %8s %10s %10s%n",
                    "SKU", "OnHand", "Order", "Unit", "Est.Cost"));
            sb.append("-".repeat(64)).append('\n');
            for (LowStockLine l : lines) {
                sb.append(String.format(Locale.US, "%-24s %6d %8d %10s %10.2f%n",
                        truncate(l.name(), 24),
                        l.quantity(),
                        l.suggestedOrderQty(),
                        truncate(l.unit(), 10),
                        l.suggestedOrderCost()));
            }
            sb.append("-".repeat(64)).append('\n');
            sb.append(String.format(Locale.US, "TOTAL SUGGESTED COST  $%,.2f%n", costTotal));
        }
        sb.append("\nTip: print PDF for the cooler door; harvest zeros first.\n");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @Transactional(readOnly = true)
    public synchronized void exportToCsv(String filename) {
        csvExporter.export(filename, repository.findAllOrdered());

    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Item itemAtIndex(int index) {
        List<Item> all = repository.findAllOrdered();
        if (index < 0 || index >= all.size()) {
            throw new IndexOutOfBoundsException(
                    "Invalid inventory index: " + index + " (size=" + all.size() + ")");
        }
        return all.get(index);
    }

    private static int normalizeLowStockThreshold(int threshold) {
        return Math.min(Math.max(0, threshold), MAX_LOW_STOCK_THRESHOLD);
    }

    private List<Item> loadSeedFromCsv() {
        File file = new File(seedCsvPath);
        if (!file.exists()) {
            return List.of();
        }

        List<Item> items = new ArrayList<>();
        try (Reader reader = new FileReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            boolean firstDataLine = true;
            int rowNumber = 0;
            for (List<String> row : CsvSupport.readAll(reader, ',')) {
                rowNumber++;
                String[] parts = row.toArray(new String[0]);
                if (firstDataLine && isHeaderRow(parts)) {
                    firstDataLine = false;
                    continue;
                }
                firstDataLine = false;
                if (parts.length == 7) {
                    try {
                        items.add(new Item(
                                parts[0], parts[1],
                                Double.parseDouble(parts[2]),
                                parts[3],
                                Double.parseDouble(parts[4]),
                                Integer.parseInt(parts[5]),
                                parts[6]
                        ));
                    } catch (Exception e) {
                        log.warn("Skipping malformed seed CSV row {} — {}", rowNumber, e.getMessage());
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Could not read seed CSV '{}': {}", seedCsvPath, e.getMessage());
            return List.of();
        }
        return items;
    }

    private static boolean isHeaderRow(String[] parts) {
        return parts.length > 0 && "name".equalsIgnoreCase(parts[0].trim());
    }

    private List<Item> buildSampleInventory() {
        List<Item> items = new ArrayList<>();
        items.add(new Item("Organic Fertilizer", "Fertilizers", 15.00, "Per Unit", 10.00, 50, "For PNW soil amendment"));
        items.add(new Item("Neem Oil Spray", "Pest Control", 20.00, "Per Unit", 12.00, 20, "Natural aphid control"));
        items.add(new Item("Nootka Rose", "Flowers/Plants", 2.20, "Per Stem", 1.10, 50, "Native PNW rose"));
        items.add(new Item("Rosa rugosa Hansa", "Flowers/Plants", 2.60, "Per Stem", 1.30, 60, "Disease-resistant shrub"));
        return items;
    }
}
