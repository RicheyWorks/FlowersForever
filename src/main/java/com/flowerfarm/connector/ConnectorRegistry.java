package com.flowerfarm.connector;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.SyncHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Central registry for all {@link ExternalConnector} beans.
 *
 * <p>Spring auto-collects every {@code ExternalConnector} bean in the context
 * (declared via {@code @Component} or produced by a {@code @Configuration}
 * factory method) and indexes them by {@link ExternalConnector#getName()}.
 *
 * <p>The three public run-methods ({@link #runImport}, {@link #runExport},
 * {@link #runSync}) resolve the connector by name, validate the requested
 * direction, and delegate to the connector implementation.  Import results
 * are automatically merged into the local {@link InventoryService}.
 * Every attempt is recorded in {@link SyncHistoryService}.
 */
@Component
public class ConnectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectorRegistry.class);

    private final Map<String, ExternalConnector<?>> registry = new LinkedHashMap<>();
    private final InventoryService inventoryService;
    private final SyncHistoryService syncHistoryService;

    /**
     * Spring injects all {@code ExternalConnector} beans here.
     * Raw-type list is intentional: the generic parameter {@code R} is the
     * external record format and is irrelevant at the registry level.
     */
    @SuppressWarnings("rawtypes")
    public ConnectorRegistry(List<ExternalConnector> connectors,
                             InventoryService inventoryService,
                             SyncHistoryService syncHistoryService) {
        this.inventoryService = inventoryService;
        this.syncHistoryService = syncHistoryService;
        for (ExternalConnector<?> c : connectors) {
            registry.put(c.getName().toLowerCase(), c);
            log.info("Registered connector: '{}' ({})", c.getName(), c.getSupportedDirection());
        }
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    public Optional<ExternalConnector<?>> find(String name) {
        return Optional.ofNullable(registry.get(name.toLowerCase()));
    }

    /**
     * Returns metadata for every registered connector, including a live
     * availability check.  Used by {@link com.flowerfarm.controller.ConnectorController}.
     */
    public List<Map<String, Object>> listConnectorInfo() {
        List<Map<String, Object>> info = new ArrayList<>();
        for (ExternalConnector<?> c : registry.values()) {
            boolean available = false;
            try { available = c.isAvailable(); } catch (Exception e) { available = false; /* connector offline — expected in local mode */ }
            SyncDirection direction = c.getSupportedDirection();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name",        c.getName());
            entry.put("description", c.getDescription());
            entry.put("direction",   direction.name());
            entry.put("canImport",   direction.canImport());
            entry.put("canExport",   direction.canExport());
            entry.put("canSync",     direction.canSync());
            entry.put("available",   available);
            if (c instanceof DualModeCapable dual) {
                entry.put("mode", dual.operatingMode());
                entry.put("localMode", dual.isLocalMode());
            } else {
                entry.put("mode", available ? "remote" : "unconfigured");
                entry.put("localMode", false);
            }
            info.add(entry);
        }
        return info;
    }

    /**
     * Returns the names of every registered connector, as the lower-cased keys
     * used for lookup.
     */
    public Set<String> getConnectorNames() {
        return new LinkedHashSet<>(registry.keySet());
    }

    // ── Operations ────────────────────────────────────────────────────────────

    /**
     * Pull records from the named connector and add them to the local inventory.
     *
     * @param name connector name (case-insensitive)
     * @return result with the list of imported items, or an error result
     */
    @SuppressWarnings("unchecked")
    public ConnectorResult<List<Item>> runImport(String name) {
        ConnectorResult<List<Item>> result = find(name)
                .map(c -> {
                    if (!c.getSupportedDirection().canImport()) {
                        return ConnectorResult.<List<Item>>fail(
                                "Connector '" + name + "' does not support IMPORT.",
                                "Supported direction: " + c.getSupportedDirection(), name);
                    }
                    ConnectorResult<List<Item>> importResult =
                            ((ExternalConnector<Object>) c).importItems();
                    if (importResult.isSuccess() && importResult.getPayload() != null) {
                        int persisted = 0;
                        for (Item item : importResult.getPayload()) {
                            try {
                                inventoryService.addItem(item);
                                persisted++;
                            } catch (RuntimeException ex) {
                                log.warn("[{}] Failed to persist '{}': {} — continuing with remaining items.",
                                        name, item.getName(), ex.getMessage());
                            }
                        }
                        log.info("[{}] Import complete — {} of {} items added to inventory.",
                                name, persisted, importResult.getPayload().size());
                    }
                    return importResult;
                })
                .orElseGet(() -> ConnectorResult.fail(
                        "Connector not found: " + name,
                        "Registered connectors: " + registry.keySet(), name));

        recordHistory(name, "IMPORT", result);
        return result;
    }

    /**
     * Push the full local inventory to the named connector.
     *
     * @param name connector name (case-insensitive)
     * @return result with the count of exported records, or an error result
     */
    @SuppressWarnings("unchecked")
    public ConnectorResult<Integer> runExport(String name) {
        ConnectorResult<Integer> result = find(name)
                .map(c -> {
                    if (!c.getSupportedDirection().canExport()) {
                        return ConnectorResult.<Integer>fail(
                                "Connector '" + name + "' does not support EXPORT.",
                                "Supported direction: " + c.getSupportedDirection(), name);
                    }
                    List<Item> items = inventoryService.getAllItems();
                    ConnectorResult<Integer> exportResult =
                            ((ExternalConnector<Object>) c).exportItems(items);
                    if (exportResult.isSuccess()) {
                        log.info("[{}] Export complete — {} items sent.", name, exportResult.getPayload());
                    }
                    return exportResult;
                })
                .orElseGet(() -> ConnectorResult.fail(
                        "Connector not found: " + name,
                        "Registered connectors: " + registry.keySet(), name));

        recordHistory(name, "EXPORT", result);
        return result;
    }

    /**
     * Run a bidirectional sync between the local inventory and the named connector.
     *
     * @param name connector name (case-insensitive)
     * @return result with a {@link SyncSummary}, or an error result
     */
    @SuppressWarnings("unchecked")
    public ConnectorResult<SyncSummary> runSync(String name) {
        ConnectorResult<SyncSummary> result = find(name)
                .map(c -> {
                    if (!c.getSupportedDirection().canSync()) {
                        return ConnectorResult.<SyncSummary>fail(
                                "Connector '" + name + "' does not support SYNC.",
                                "Supported direction: " + c.getSupportedDirection(), name);
                    }
                    List<Item> items = inventoryService.getAllItems();
                    ConnectorResult<SyncSummary> syncResult =
                            ((ExternalConnector<Object>) c).syncUpdates(items);
                    if (syncResult.isSuccess() && syncResult.getPayload() != null) {
                        log.info("[{}] Sync complete — {}", name, syncResult.getPayload());
                    }
                    return syncResult;
                })
                .orElseGet(() -> ConnectorResult.fail(
                        "Connector not found: " + name,
                        "Registered connectors: " + registry.keySet(), name));

        recordHistory(name, "SYNC", result);
        return result;
    }

    private void recordHistory(String name, String operation, ConnectorResult<?> result) {
        try {
            syncHistoryService.recordResult(name, operation, result);
        } catch (Exception e) {
            // History must never break a connector operation
            log.warn("Failed to record sync history for {} {}: {}", name, operation, e.getMessage());
        }
    }
}
