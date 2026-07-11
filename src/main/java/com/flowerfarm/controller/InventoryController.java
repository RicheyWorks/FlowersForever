package com.flowerfarm.controller;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.InventoryService.LowStockReport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller exposing inventory operations over HTTP.
 *
 * <pre>
 * GET    /api/inventory              → list all items
 * GET    /api/inventory/search?q=…   → search by name/category
 * GET    /api/inventory/by-id/{id}   → get one item by primary key
 * POST   /api/inventory              → add item (JSON body)
 * PUT    /api/inventory/{index}      → edit by list index (legacy GUI)
 * PUT    /api/inventory/by-id/{id}   → edit by primary key (preferred)
 * DELETE /api/inventory/{index}      → delete by list index (legacy GUI)
 * DELETE /api/inventory/by-id/{id}   → delete by primary key (preferred)
 * POST   /api/inventory/export       → export CSV to server filesystem
 * POST   /api/inventory/sample-rose  → add the canonical Nootka Rose sample
 * </pre>
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Returns all inventory items as a JSON array (ordered by id). */
    @GetMapping
    public List<Item> getAll() {
        return inventoryService.getAllItems();
    }

    /** Dashboard-style inventory KPIs (sell value, cost basis, low stock). */
    @GetMapping("/kpis")
    public InventoryService.InventoryKpiSnapshot kpis(
            @RequestParam(value = "lowStockThreshold", defaultValue = "10") int lowStockThreshold) {
        return inventoryService.inventoryKpis(lowStockThreshold);
    }

    /**
     * Low-stock reorder sheet (JSON).
     * {@code threshold} defaults to 10 (same as dashboard low-stock KPI).
     */
    @GetMapping("/low-stock")
    public Map<String, Object> lowStock(
            @RequestParam(value = "threshold", defaultValue = "10") int threshold) {
        return inventoryService.buildLowStockReport(threshold).toMap();
    }

    @GetMapping(value = "/low-stock/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public String lowStockText(
            @RequestParam(value = "threshold", defaultValue = "10") int threshold) {
        return inventoryService.buildLowStockReport(threshold).plainText();
    }

    @GetMapping(value = "/low-stock/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> lowStockPdf(
            @RequestParam(value = "threshold", defaultValue = "10") int threshold) {
        LowStockReport report = inventoryService.buildLowStockReport(threshold);
        byte[] pdf = inventoryService.generateLowStockPdf(report);
        String filename = "low-stock-reorder-" + report.date() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * Market / wholesale price list.
     * {@code inStockOnly=true} omits zero-qty SKUs (default for booth sheet).
     */
    @GetMapping("/price-list")
    public Map<String, Object> priceList(
            @RequestParam(value = "inStockOnly", defaultValue = "true") boolean inStockOnly) {
        return inventoryService.buildPriceListReport(inStockOnly).toMap();
    }

    @GetMapping(value = "/price-list/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public String priceListText(
            @RequestParam(value = "inStockOnly", defaultValue = "true") boolean inStockOnly) {
        return inventoryService.buildPriceListReport(inStockOnly).plainText();
    }

    @GetMapping(value = "/price-list/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> priceListPdf(
            @RequestParam(value = "inStockOnly", defaultValue = "true") boolean inStockOnly) {
        var report = inventoryService.buildPriceListReport(inStockOnly);
        byte[] pdf = inventoryService.generatePriceListPdf(report);
        String filename = "price-list-" + report.date() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** Returns items matching the search query (name or category). */
    @GetMapping("/search")
    public List<Item> search(@RequestParam("q") String query) {
        return inventoryService.searchItems(query);
    }

    /** Returns a single item by database id. */
    @GetMapping("/by-id/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        return inventoryService.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No inventory item with id=" + id)));
    }

    /** Adds a new item from the JSON body. Returns 201 Created with the persisted item (incl. id). */
    @PostMapping
    public ResponseEntity<?> addItem(@RequestBody Item item) {
        try {
            Item saved = inventoryService.addItem(item);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Replaces the item at {@code index} with the JSON body. Returns 200 OK. */
    @PutMapping("/{index}")
    public ResponseEntity<?> editItem(@PathVariable int index, @RequestBody Item item) {
        try {
            Item saved = inventoryService.editItem(index, item);
            return ResponseEntity.ok(saved);
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Replaces the item with the given database id. Preferred over index-based edit. */
    @PutMapping("/by-id/{id}")
    public ResponseEntity<?> updateById(@PathVariable Long id, @RequestBody Item item) {
        try {
            Item saved = inventoryService.updateById(id, item);
            return ResponseEntity.ok(saved);
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Deletes the item at {@code index}. Returns 204 No Content. */
    @DeleteMapping("/{index}")
    public ResponseEntity<?> deleteItem(@PathVariable int index) {
        try {
            inventoryService.deleteItem(index);
            return ResponseEntity.noContent().build();
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /** Deletes the item with the given database id. Preferred over index-based delete. */
    @DeleteMapping("/by-id/{id}")
    public ResponseEntity<?> deleteById(@PathVariable Long id) {
        try {
            inventoryService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Triggers a server-side CSV export to {@code exported_inventory.csv}.
     * Returns 200 OK with a confirmation message, or 500 if the write fails.
     */
    @PostMapping("/export")
    public ResponseEntity<Map<String, String>> exportCsv() {
        try {
            inventoryService.exportToCsv("exported_inventory.csv");
            return ResponseEntity.ok(Map.of("message", "Exported to exported_inventory.csv"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Convenience endpoint: adds a Nootka Rose sample item so the front-end
     * can demonstrate a pre-populated entry without manual data entry.
     */
    @PostMapping("/sample-rose")
    public ResponseEntity<?> addSampleRose() {
        try {
            Item rose = new Item(
                    "Nootka Rose",
                    "Flowers/Plants",
                    3.50,
                    "Per Stem",
                    2.00,
                    50,
                    "Native PNW rose, pink blooms, hardy in wet soils"
            );
            Item saved = inventoryService.addItem(rose);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
