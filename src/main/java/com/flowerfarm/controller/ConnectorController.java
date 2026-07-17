package com.flowerfarm.controller;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.service.SyncHistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API surface for the connector layer.
 *
 * <pre>
 * GET  /api/connectors                        List all registered connectors + availability
 * GET  /api/connectors/history                Audit log (filterable)
 * POST /api/connectors/history/export         Write audit CSV on server
 * GET  /api/connectors/{name}/status          Single connector availability check
 * POST /api/connectors/{name}/import          Pull data from external system → local inventory
 * POST /api/connectors/{name}/export          Push local inventory → external system
 * POST /api/connectors/{name}/sync            Bidirectional reconcile
 * DELETE /api/connectors/history              Clear audit log
 * </pre>
 *
 * All operations are delegated to {@link ConnectorRegistry}, which handles
 * discovery, direction validation, and {@link com.flowerfarm.service.InventoryService}
 * integration automatically. Every attempt is stored via {@link SyncHistoryService}.
 */
@RestController
@RequestMapping("/api/connectors")
public class ConnectorController {

    private final ConnectorRegistry registry;
    private final SyncHistoryService syncHistoryService;

    public ConnectorController(ConnectorRegistry registry, SyncHistoryService syncHistoryService) {
        this.registry = registry;
        this.syncHistoryService = syncHistoryService;
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
     * Audit log (newest first). Filters:
     * {@code connector}, {@code operation}, {@code success} (true/false),
     * {@code q} message/detail search, {@code limit} (default 100, max 500).
     */
    @GetMapping("/history")
    public List<SyncHistoryEntry> history(
            @RequestParam(value = "connector", required = false) String connector,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        boolean any = (connector != null && !connector.isBlank())
                || (operation != null && !operation.isBlank())
                || success != null
                || (query != null && !query.isBlank());
        if (!any) {
            return syncHistoryService.recentForConnector(connector, limit);
        }
        return syncHistoryService.filter(connector, operation, success, query, limit);
    }

    /** Server-side CSV export of the (optionally filtered) audit log. */
    @PostMapping("/history/export")
    public ResponseEntity<Map<String, String>> exportHistory(
            @RequestParam(value = "filename", defaultValue = "sync_history.csv") String filename,
            @RequestParam(value = "connector", required = false) String connector,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "500") int limit) {
        try {
            List<SyncHistoryEntry> rows = syncHistoryService.filter(
                    connector, operation, success, query, limit);
            syncHistoryService.exportToCsv(filename, rows);
            return ResponseEntity.ok(Map.of(
                    "message", "Exported " + rows.size() + " audit row(s) to " + filename,
                    "rows", String.valueOf(rows.size())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /** Audit history report JSON (same filters as {@code /history}). */
    @GetMapping("/history/report")
    public Map<String, Object> historyReport(
            @RequestParam(value = "connector", required = false) String connector,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return syncHistoryService
                .buildAuditReport(connector, operation, success, query, limit)
                .toMap();
    }

    @GetMapping(value = "/history/report.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String historyReportText(
            @RequestParam(value = "connector", required = false) String connector,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return syncHistoryService
                .buildAuditReport(connector, operation, success, query, limit)
                .plainText();
    }

    @GetMapping(value = "/history/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> historyReportPdf(
            @RequestParam(value = "connector", required = false) String connector,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        var report = syncHistoryService.buildAuditReport(
                connector, operation, success, query, limit);
        byte[] pdf = syncHistoryService.generateAuditPdf(report);
        String filename = "audit-history-" + report.generatedOn() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** Clears the entire sync history audit log. */
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, String>> clearHistory() {
        syncHistoryService.clearAll();
        return ResponseEntity.ok(Map.of("message", "Sync history cleared."));
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
                    try { available = c.isAvailable(); } catch (Exception e) { available = false; /* connector offline — expected in local mode */ }
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
        String message = r.getMessage() != null ? r.getMessage() : "Unknown error";
        HttpStatus status = resolveErrorStatus(message);

        return ResponseEntity.status(status).body(Map.of(
                "connector", name,
                "error",     message,
                "detail",    r.getErrorDetail() != null ? r.getErrorDetail() : ""
        ));
    }

    /**
     * Maps connector failure messages to HTTP status codes.
     * Only unknown-connector messages become 404; operational failures
     * (missing files, remote API errors, etc.) remain 502 Bad Gateway.
     * Unconfigured / credential gaps map to 503 Service Unavailable.
     */
    static HttpStatus resolveErrorStatus(String message) {
        if (message == null) {
            return HttpStatus.BAD_GATEWAY;
        }
        String lower = message.toLowerCase();
        // Registry lookup miss — message is always "Connector not found: …"
        if (lower.startsWith("connector not found")) {
            return HttpStatus.NOT_FOUND;
        }
        if (lower.contains("is not available")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_GATEWAY;
    }
}
