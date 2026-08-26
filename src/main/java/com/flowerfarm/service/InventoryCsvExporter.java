package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes inventory snapshots using the application's stable CSV contract. */
@Component
public class InventoryCsvExporter {

    private static final Logger log = LoggerFactory.getLogger(InventoryCsvExporter.class);

    public void export(String filename, List<Item> inventory) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }
        List<Item> rows = inventory == null ? List.of() : inventory;
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(filename.trim(), StandardCharsets.UTF_8))) {
            writer.write(InventoryService.CSV_HEADER);
            writer.newLine();
            for (Item item : rows) {
                writer.write(item.toCsv());
                writer.newLine();
            }
            log.info("Inventory exported to '{}' ({} item(s)).", filename, rows.size());
        } catch (IOException ex) {
            log.error("Export to '{}' failed: {}", filename, ex.getMessage());
            throw new IllegalStateException(
                    "Export to '" + filename + "' failed: " + ex.getMessage(), ex);
        }
    }
}
