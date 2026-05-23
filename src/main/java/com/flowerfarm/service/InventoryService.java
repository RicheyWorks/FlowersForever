package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Spring-managed service for flower farm inventory operations.
 *
 * <p>Replaces the original {@code InventoryManager} with full Spring lifecycle
 * management. Inventory is persisted to a CSV file and loaded eagerly
 * on startup via {@link PostConstruct}.
 *
 * <p>All mutating methods are {@code synchronized} to guard against concurrent
 * REST requests modifying the list simultaneously.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** Default file safe for manual, Spring, and production use. */
    private String dataFile = "farm_inventory.csv";

    private final List<Item> inventory = new ArrayList<>();

    /**
     * Default constructor used by Spring.
     */
    public InventoryService() {
    }

    /**
     * Package-private constructor for tests.
     * Allows InventoryServiceTest to inject a temporary CSV path without reflection.
     */
    InventoryService(String dataFile) {
        if (dataFile == null || dataFile.trim().isEmpty()) {
            throw new IllegalArgumentException("Data file path must not be null or empty.");
        }
        this.dataFile = dataFile.trim();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        List<Item> loaded = loadFromCsv();
        synchronized (this) {
            inventory.clear();
            inventory.addAll(loaded);
        }
        log.info("InventoryService initialised — {} items loaded from '{}'", inventory.size(), dataFile);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public synchronized List<Item> getAllItems() {
        return new ArrayList<>(inventory);
    }

    public synchronized List<Item> searchItems(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String q = query.trim().toLowerCase();
        List<Item> results = new ArrayList<>();

        for (Item item : inventory) {
            if (item.getName().toLowerCase().contains(q)
                    || item.getCategory().toLowerCase().contains(q)) {
                results.add(item);
            }
        }

        return results;
    }

    public synchronized void addItem(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }

        inventory.add(item);
        saveToCsv();
        log.debug("Added item: {}", item.getName());
    }

    public synchronized void editItem(int index, Item newItem) {
        if (newItem == null) {
            throw new IllegalArgumentException("Replacement item must not be null.");
        }

        validateIndex(index);
        inventory.set(index, newItem);
        saveToCsv();
        log.debug("Edited item at index {}: {}", index, newItem.getName());
    }

    public synchronized void deleteItem(int index) {
        validateIndex(index);
        Item removed = inventory.remove(index);
        saveToCsv();
        log.debug("Deleted item: {}", removed.getName());
    }

    public synchronized void exportToCsv(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename.trim()))) {
            bw.write("Name,Category,Price,Unit,Cost,Quantity,Notes");
            bw.newLine();

            for (Item item : inventory) {
                bw.write(item.toCsv());
                bw.newLine();
            }

            log.info("Inventory exported to '{}'", filename);
        } catch (IOException e) {
            log.error("Export to '{}' failed: {}", filename, e.getMessage());
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    private List<Item> loadFromCsv() {
        File file = new File(dataFile);

        if (!file.exists()) {
            log.info("'{}' not found — loading sample inventory.", dataFile);
            return buildSampleInventory();
        }

        List<Item> items = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;

            while ((line = br.readLine()) != null) {
                String[] parts = parseCsvLine(line);

                if (parts.length == 7) {
                    try {
                        items.add(new Item(
                                parts[0],
                                parts[1],
                                Double.parseDouble(parts[2]),
                                parts[3],
                                Double.parseDouble(parts[4]),
                                Integer.parseInt(parts[5]),
                                parts[6]
                        ));
                    } catch (Exception e) {
                        log.warn("Skipping malformed CSV line: '{}' — {}", line, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error reading '{}': {} — falling back to sample data.", dataFile, e.getMessage());
            return buildSampleInventory();
        }

        return items;
    }

    private void saveToCsv() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(dataFile))) {
            for (Item item : inventory) {
                bw.write(item.toCsv());
                bw.newLine();
            }
        } catch (IOException e) {
            log.error("Failed to save inventory to '{}': {}", dataFile, e.getMessage());
        }
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

    private void validateIndex(int index) {
        if (index < 0 || index >= inventory.size()) {
            throw new IndexOutOfBoundsException(
                    "Invalid inventory index: " + index + " (size=" + inventory.size() + ")"
            );
        }
    }
}
