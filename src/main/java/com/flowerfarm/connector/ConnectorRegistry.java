package com.flowerfarm.connector;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
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
 */
@Component
public class ConnectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectorRegistry.class);

    private final Map<String, ExternalConnector<?>> registry = new LinkedHashMap<>();
    private final InventoryService inventoryService;

    /**
     * Spring injects all {@code ExternalConnector} beans here.
     * Raw-type list is intentional: the generic parameter {@code R} is the
     * external record format and is irrelevant at the registry level.
     */
    @SuppressWarnings("rawtypes")
    public ConnectorRegistry(List<ExternalConnector> connectors,
                             InventoryService inventoryService) {
        this.inventoryService = inventoryService;
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
            try { available = c.isAvailable(); } catch (Exception ignored) {}
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name",        c.getName());
            entry.put("description", c.getDescription());
            entry.put("direction",   c.getSupportedDirection().name());
            entry.put("available",   available);
            info.add(entry);
        }
        return info;
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
        return find(name)
                .map(c -> {
                    if (!c.getSupportedDirection().canImport()) {
                        return ConnectorResult.<List<Item>>fail(
                                "Connector '" + name + "' does not support import.",
                                "Supported direction: " + c.getSupportedDirection(), name);
                    }
                    ConnectorResult<List<Item>> result =
                            ((ExternalConnector<Object>) c).importItems();
                    if (result.isSuccess() && result.getPayload() != null) {
                        result.getPayload().forEach(inventoryService::addItem);
                        log.info("[{}] Import complete — {} items added to inventory.",
                                name, result.getPayload().size());
                    }
                    return result;
                })
                .orElseGet(() -> ConnectorResult.fail(
                        "Connector not found: " + name,
                        "Registered connectors: " + registry.keySet(), name));
    }

    /**
     * Push the full local inventory to the named connector.
     *
     * @param name connector name (case-insensitive)
     * @return result with the count of exported records, or an error result
     */
    @SuppressWarnings("unchecked")
    public ConnectorResult<Integer> runExport(String name) {
        return find(name)
                .map(c -> {
                    if (!c.getSupportedDirection().canExport()) {
                        return ConnectorResult.<Integer>fail(
                                "Connector '" + name + "' does not support export.",
                                "Supported direction: " + c.getSupportedDirection(), name);
                    }
                    List<Item> items = inventoryService.getAllItems();
                    ConnectorResult<Integer> result =
                            ((ExternalConnector<Object>) c).exportItems(items);
                    if (result.isSuccess()) {
                        log.info("[{}] Export complete — {} items sent.", name, result.getPayload());
                    }
                    return result;
                })
                .orElseGet(() -> ConnectorResult.fail(
                        "Connector not found: " + name,
                        "Registered connectors: " + registry.keySet(), name));
    }

    /**
     * Run a bidirectional sync between the local inventory and the named connector.
     *
     * @param name connector name (case-insensitive)
     * @return result with a {@link SyncSummary}, or an error result
     */
    @SuppressWarnings("unchecked")
    public ConnectorResult<SyncSummary> runSync(String name) {
        return find(name)
                .map(c -> {
                    if (!c.getSupportedDirection().canSync()) {
                        return ConnectorResult.<SyncSummary>fail(
                                "Connector '" + name + "' does not support sync.",
                                "Supported direction: " + c.getSupportedDirection(), name);
                    }
                    List<Item> items = inventoryService.getAllItems();
                    ConnectorResult<SyncSummary> result =
                            ((ExternalConnector<Object>) c).syncUpdates(items);
                    if (result.isSuccess() && result.getPayload() != null) {
                        log.info("[{}] Sync complete — {}", name, result.getPayload());
                    }
                    return result;
                })
                .orElseGet(() -> ConnectorResult.fail(
                        "Connector not found: " + name,
                        "Registered connectors: " + registry.keySet(), name));
    }
}
