package com.flowerfarm.controller;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API surface for the connector layer.
 *
 * <pre>
 * GET  /api/connectors                        List all registered connectors + availability
 * GET  /api/connectors/{name}/status          Single connector availability check
 * POST /api/connectors/{name}/import          Pull data from external system → local inventory
 * POST /api/connectors/{name}/export          Push local inventory → external system
 * POST /api/connectors/{name}/sync            Bidirectional reconcile
 * </pre>
 *
 * All operations are delegated to {@link ConnectorRegistry}, which handles
 * discovery, direction validation, and {@link com.flowerfarm.service.InventoryService}
 * integration automatically.
 */
@RestController
@RequestMapping("/api/connectors")
public class ConnectorController {

    private final ConnectorRegistry registry;

    public ConnectorController(ConnectorRegistry registry) {
        this.registry = registry;
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    /**
     * Returns a JSON array describing every registered connector:
     * name, description, supported direction, and live availability.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listConnectors() {
        return ResponseEntity.ok(registry.listConnectorInfo());
    }

    /**
     * Runs the availability check for a single connector.
     * 200 if available, 503 if not.
     */
    @GetMapping("/{name}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String name) {
        return registry.find(name)
                .map(c -> {
                    boolean available = false;
                    try { available = c.isAvailable(); } catch (Exception ignored) {}
                    Map<String, Object> body = Map.of(
                            "connector", name,
                            "available", available,
                            "direction", c.getSupportedDirection().name()
                    );
                    return available
                            ? ResponseEntity.ok(body)
                            : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Connector not found: " + name)));
    }

    // ── Operations ────────────────────────────────────────────────────────────

    /**
     * Pull records from the named external system and add them to the local inventory.
     * Returns the list of imported {@link Item} objects.
     */
    @PostMapping("/{name}/import")
    public ResponseEntity<?> runImport(@PathVariable String name) {
        ConnectorResult<List<Item>> result = registry.runImport(name);
        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "connector", name,
                    "message",   result.getMessage(),
                    "imported",  result.getPayload() != null ? result.getPayload().size() : 0,
                    "items",     result.getPayload() != null ? result.getPayload() : List.of()
            ));
        }
        return errorResponse(name, result);
    }

    /**
     * Push the current local inventory to the named external system.
     * Returns the count of records successfully sent.
     */
    @PostMapping("/{name}/export")
    public ResponseEntity<?> runExport(@PathVariable String name) {
        ConnectorResult<Integer> result = registry.runExport(name);
        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "connector", name,
                    "message",   result.getMessage(),
                    "exported",  result.getPayload() != null ? result.getPayload() : 0
            ));
        }
        return errorResponse(name, result);
    }

    /**
     * Run a bidirectional sync between local inventory and the named external system.
     * Returns a {@link SyncSummary} with created / updated / skipped / error counts.
     */
    @PostMapping("/{name}/sync")
    public ResponseEntity<?> runSync(@PathVariable String name) {
        ConnectorResult<SyncSummary> result = registry.runSync(name);
        if (result.isSuccess()) {
            SyncSummary s = result.getPayload();
            return ResponseEntity.ok(Map.of(
                    "connector", name,
                    "message",   result.getMessage(),
                    "summary", s != null ? Map.of(
                            "created", s.created(),
                            "updated", s.updated(),
                            "deleted", s.deleted(),
                            "skipped", s.skipped(),
                            "errors",  s.errors(),
                            "total",   s.total()
                    ) : Map.of()
            ));
        }
        return errorResponse(name, result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> errorResponse(String name, ConnectorResult<?> r) {
        HttpStatus status = r.getMessage() != null && r.getMessage().contains("not found")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_GATEWAY;

        return ResponseEntity.status(status).body(Map.of(
                "connector",   name,
                "error",       r.getMessage() != null ? r.getMessage() : "Unknown error",
                "detail",      r.getErrorDetail() != null ? r.getErrorDetail() : ""
        ));
    }
}
