package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import com.flowerfarm.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
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

    /** Standard CSV header shared by export and legacy seed files. */
    public static final String CSV_HEADER = "Name,Category,Price,Unit,Cost,Quantity,Notes";

    private final InventoryRepository repository;
    private final String seedCsvPath;

    public InventoryService(
            InventoryRepository repository,
            @Value("${flowerfarm.inventory.seed-csv:farm_inventory.csv}") String seedCsvPath) {
        this.repository = repository;
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
        // Force insert
        item.setId(null);
        Item saved = repository.save(item);
        log.debug("Added item id={} name={}", saved.getId(), saved.getName());
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
        Item existing = itemAtIndex(index);
        existing.copyBusinessFieldsFrom(newItem);
        Item saved = repository.save(existing);
        log.debug("Edited item at index {} id={}", index, saved.getId());
        return saved;
    }

    @Transactional
    public synchronized Item updateById(Long id, Item newItem) {
        if (newItem == null) {
            throw new IllegalArgumentException("Replacement item must not be null.");
        }
        Item existing = repository.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No inventory item with id=" + id));
        existing.copyBusinessFieldsFrom(newItem);
        Item saved = repository.save(existing);
        log.debug("Updated item id={}", id);
        return saved;
    }

    @Transactional
    public synchronized void deleteItem(int index) {
        Item existing = itemAtIndex(index);
        repository.deleteById(existing.getId());
        log.debug("Deleted item at index {} id={}", index, existing.getId());
    }

    @Transactional
    public synchronized void deleteById(Long id) {
        if (repository.findById(id).isEmpty()) {
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
        Optional<Item> found = findByNameIgnoreCase(name);
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
        Optional<Item> found = findByNameIgnoreCase(name);
        if (found.isPresent()) {
            Item item = found.get();
            item.setQuantity(item.getQuantity() + amount);
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
        int threshold = Math.max(0, lowStockThreshold);
        List<Item> items = getAllItems();
        int skuCount = items.size();
        double sellValue = 0;
        double costBasis = 0;
        int lowStock = 0;
        int totalUnits = 0;
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
            int totalUnits,
            int lowStockThreshold
    ) {}

    @Transactional(readOnly = true)
    public synchronized void exportToCsv(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }

        List<Item> inventory = repository.findAllOrdered();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename.trim()))) {
            bw.write(CSV_HEADER);
            bw.newLine();
            for (Item item : inventory) {
                bw.write(item.toCsv());
                bw.newLine();
            }
            log.info("Inventory exported to '{}' ({} item(s)).", filename, inventory.size());
        } catch (IOException e) {
            log.error("Export to '{}' failed: {}", filename, e.getMessage());
            throw new IllegalStateException("Export to '" + filename + "' failed: " + e.getMessage(), e);
        }
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

    private List<Item> loadSeedFromCsv() {
        File file = new File(seedCsvPath);
        if (!file.exists()) {
            return List.of();
        }

        List<Item> items = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstDataLine = true;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = parseCsvLine(line);
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
                        log.warn("Skipping malformed seed CSV line: '{}' — {}", line, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Could not read seed CSV '{}': {}", seedCsvPath, e.getMessage());
            return List.of();
        }
        return items;
    }

    private static boolean isHeaderRow(String[] parts) {
        return parts.length > 0 && "name".equalsIgnoreCase(parts[0].trim());
    }

    private String[] parseCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString().trim());
        return parts.toArray(new String[0]);
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
