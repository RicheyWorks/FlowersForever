package com.flowerfarm.controller;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import org.springframework.http.HttpStatus;
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
 * POST   /api/inventory              → add item (JSON body)
 * PUT    /api/inventory/{index}      → edit item at index (JSON body)
 * DELETE /api/inventory/{index}      → delete item at index
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

    /** Returns all inventory items as a JSON array. */
    @GetMapping
    public List<Item> getAll() {
        return inventoryService.getAllItems();
    }

    /** Returns items matching the search query (name or category). */
    @GetMapping("/search")
    public List<Item> search(@RequestParam("q") String query) {
        return inventoryService.searchItems(query);
    }

    /** Adds a new item from the JSON body. Returns 201 Created with the item. */
    @PostMapping
    public ResponseEntity<?> addItem(@RequestBody Item item) {
        try {
            inventoryService.addItem(item);
            return ResponseEntity.status(HttpStatus.CREATED).body(item);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Replaces the item at {@code index} with the JSON body. Returns 200 OK. */
    @PutMapping("/{index}")
    public ResponseEntity<?> editItem(@PathVariable int index, @RequestBody Item item) {
        try {
            inventoryService.editItem(index, item);
            return ResponseEntity.ok(item);
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

    /**
     * Triggers a server-side CSV export to {@code exported_inventory.csv}.
     * Returns 200 OK with a confirmation message.
     */
    @PostMapping("/export")
    public ResponseEntity<Map<String, String>> exportCsv() {
        inventoryService.exportToCsv("exported_inventory.csv");
        return ResponseEntity.ok(Map.of("message", "Exported to exported_inventory.csv"));
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
            inventoryService.addItem(rose);
            return ResponseEntity.status(HttpStatus.CREATED).body(rose);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
