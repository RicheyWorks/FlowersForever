package com.flowerfarm.connector.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Farmbrite farm-management connector — dual mode:
 * <ul>
 *   <li><b>Local mirror</b> ({@code connector.farmbrite.local-file}) — JSON file
 *       store for offline demo / full import→edit→export round-trips without credentials.</li>
 *   <li><b>Remote REST</b> — API key + account id against a configurable base URL.</li>
 * </ul>
 *
 * <pre>
 * # Offline demo (recommended for development)
 * connector.farmbrite.local-file=data/farmbrite-mirror.json
 *
 * # Live API
 * connector.farmbrite.api-key=…
 * connector.farmbrite.account-id=…
 * connector.farmbrite.base-url=https://www.farmbrite.com/api/v1
 * </pre>
 */
@Component
public class FarmbriteConnector implements ExternalConnector<Map<String, Object>>, DualModeCapable {

    private static final Logger log = LoggerFactory.getLogger(FarmbriteConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConnectorConfig config;
    private final RestTemplate restTemplate;

    public FarmbriteConnector(
            @Value("${connector.farmbrite.api-key:}") String apiKey,
            @Value("${connector.farmbrite.account-id:}") String accountId,
            @Value("${connector.farmbrite.base-url:https://www.farmbrite.com/api/v1}") String baseUrl,
            @Value("${connector.farmbrite.local-file:}") String localFile) {
        this(apiKey, accountId, baseUrl, localFile, new RestTemplate());
    }

    FarmbriteConnector(String apiKey, String accountId, String baseUrl, String localFile,
                       RestTemplate restTemplate) {
        this.config = new ConnectorConfig("farmbrite")
                .set("api-key", nullToEmpty(apiKey))
                .set("account-id", nullToEmpty(accountId))
                .set("base-url", (baseUrl == null || baseUrl.isBlank())
                        ? "https://www.farmbrite.com/api/v1" : baseUrl.trim().replaceAll("/+$", ""))
                .set("local-file", nullToEmpty(localFile));
        this.restTemplate = restTemplate;
    }

    @Override public String getName() { return "farmbrite"; }

    @Override
    public String getDescription() {
        return isLocalMode()
                ? "Farmbrite (local JSON mirror) — offline inventory import/export/sync"
                : "Farmbrite farm management — crops/resources ↔ inventory (REST)";
    }

    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return isLocalMode() || config.hasAll("api-key", "account-id");
    }

    @Override
    public boolean isLocalMode() {
        return config.has("local-file");
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @Override
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) {
            return ConnectorResult.unavailable(getName());
        }
        return isLocalMode() ? importFromLocalFile() : importFromRemote();
    }

    private ConnectorResult<List<Item>> importFromLocalFile() {
        Path path = Path.of(config.get("local-file"));
        if (!Files.exists(path)) {
            log.info("[farmbrite] Local mirror '{}' not found — returning empty inventory.", path);
            return ConnectorResult.ok(List.of(),
                    "Farmbrite local mirror empty (file not found yet). Export to create it.", getName());
        }
        try {
            List<Map<String, Object>> rows = MAPPER.readValue(
                    path.toFile(), new TypeReference<List<Map<String, Object>>>() {});
            List<Item> items = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Item item = mapToItem(row);
                if (item != null) {
                    items.add(item);
                }
            }
            String msg = "Farmbrite local import — " + items.size() + " item(s) from " + path.getFileName() + ".";
            log.info("[farmbrite] {}", msg);
            return ConnectorResult.ok(items, msg, getName());
        } catch (IOException e) {
            return ConnectorResult.fail("Farmbrite local import failed.", e, getName());
        }
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult<List<Item>> importFromRemote() {
        try {
            String url = config.get("base-url") + "/accounts/" + config.get("account-id")
                    + "/inventory?limit=250";
            ResponseEntity<?> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders()), Object.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return ConnectorResult.fail(
                        "Farmbrite import failed.",
                        "HTTP " + response.getStatusCode(),
                        getName());
            }

            List<Item> items = parseRemoteList(response.getBody());
            String msg = "Farmbrite REST import complete — " + items.size() + " item(s).";
            log.info("[farmbrite] {}", msg);
            return ConnectorResult.ok(items, msg, getName());

        } catch (RestClientResponseException e) {
            return ConnectorResult.fail(
                    "Farmbrite import failed.",
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Farmbrite import failed.", e, getName());
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) {
            return ConnectorResult.unavailable(getName());
        }
        if (items == null) {
            items = List.of();
        }
        return isLocalMode() ? exportToLocalFile(items) : exportToRemote(items);
    }

    private ConnectorResult<Integer> exportToLocalFile(List<Item> items) {
        Path path = Path.of(config.get("local-file"));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Item item : items) {
                rows.add(mapFromItem(item));
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), rows);
            String msg = "Farmbrite local export — wrote " + items.size()
                    + " item(s) to " + path.getFileName() + ".";
            log.info("[farmbrite] {}", msg);
            return ConnectorResult.ok(items.size(), msg, getName());
        } catch (IOException e) {
            return ConnectorResult.fail("Farmbrite local export failed.", e, getName());
        }
    }

    private ConnectorResult<Integer> exportToRemote(List<Item> items) {
        if (items.isEmpty()) {
            return ConnectorResult.ok(0, "No items to export to Farmbrite.", getName());
        }
        int sent = 0;
        List<String> errors = new ArrayList<>();
        String url = config.get("base-url") + "/accounts/" + config.get("account-id") + "/inventory";

        for (Item item : items) {
            try {
                Map<String, Object> body = Map.of("item", mapFromItem(item));
                restTemplate.exchange(
                        url, HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Map.class);
                sent++;
            } catch (RestClientResponseException e) {
                errors.add(item.getName() + ": HTTP " + e.getStatusCode().value());
                log.warn("[farmbrite] Export skip '{}': HTTP {}", item.getName(), e.getStatusCode().value());
            } catch (Exception e) {
                errors.add(item.getName() + ": " + e.getMessage());
            }
        }

        if (sent == 0 && !errors.isEmpty()) {
            return ConnectorResult.fail(
                    "Farmbrite export failed for all items.",
                    String.join("; ", errors),
                    getName());
        }
        String msg = "Farmbrite REST export complete — sent " + sent + " of " + items.size() + " item(s)."
                + (errors.isEmpty() ? "" : " " + errors.size() + " error(s).");
        log.info("[farmbrite] {}", msg);
        return ConnectorResult.ok(sent, msg, getName());
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        if (!isAvailable()) {
            return ConnectorResult.unavailable(getName());
        }
        if (localItems == null) {
            localItems = List.of();
        }

        ConnectorResult<List<Item>> remote = importItems();
        Map<String, Item> remoteByName = new LinkedHashMap<>();
        if (remote.isSuccess() && remote.getPayload() != null) {
            remote.getPayload().forEach(i -> remoteByName.put(i.getName().toLowerCase(Locale.ROOT), i));
        } else if (!remote.isSuccess() && !isLocalMode()) {
            return ConnectorResult.fail(
                    "Farmbrite sync aborted — could not read remote inventory.",
                    remote.getErrorDetail(),
                    getName());
        }

        int created = 0, updated = 0, skipped = 0;
        // Local is source of truth for export payload; count vs remote
        List<Item> merged = new ArrayList<>(localItems);
        for (Item local : localItems) {
            Item r = remoteByName.get(local.getName().toLowerCase(Locale.ROOT));
            if (r == null) {
                created++;
            } else if (r.getQuantity() != local.getQuantity()
                    || Double.compare(r.getPrice(), local.getPrice()) != 0) {
                updated++;
            } else {
                skipped++;
            }
        }

        ConnectorResult<Integer> export = exportItems(merged);
        if (!export.isSuccess()) {
            return ConnectorResult.fail(
                    "Farmbrite sync export step failed: " + export.getMessage(),
                    export.getErrorDetail(),
                    getName());
        }

        SyncSummary summary = new SyncSummary(created, updated, 0, skipped, 0);
        String msg = "Farmbrite sync complete. " + summary
                + (isLocalMode() ? " (local mirror)" : " (REST)");
        log.info("[farmbrite] {}", msg);
        return ConnectorResult.ok(summary, msg, getName());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    @Override
    public Item mapToItem(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> src = raw;
            if (raw.get("attributes") instanceof Map<?, ?> attrs) {
                src = castMap(attrs);
            }
            String name = str(firstNonNull(src.get("name"), src.get("title"), src.get("crop_name")), "Unknown");
            String category = str(firstNonNull(src.get("category"), src.get("type"), src.get("resource_type")), "Other");
            double price = parseDouble(firstNonNull(src.get("unit_price"), src.get("price"), src.get("sale_price")), 0);
            int qty = parseInt(firstNonNull(src.get("quantity_on_hand"), src.get("quantity"), src.get("qty")), 0);
            String unit = str(firstNonNull(src.get("unit"), src.get("uom")), "Per Unit");
            double cost = parseDouble(firstNonNull(src.get("cost"), src.get("unit_cost")), 0);
            String notes = str(firstNonNull(src.get("notes"), src.get("description")), "");
            return new Item(name, category, price, unit, cost, qty, notes);
        } catch (Exception e) {
            log.warn("[farmbrite] Skip unmappable row: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> mapFromItem(Item item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", item.getName());
        m.put("category", item.getCategory());
        m.put("unit_price", item.getPrice());
        m.put("unit_cost", item.getCost());
        m.put("quantity_on_hand", item.getQuantity());
        m.put("unit", item.getUnit());
        m.put("notes", item.getNotes() == null ? "" : item.getNotes());
        m.put("source", "FlowersForever");
        return m;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Item> parseRemoteList(Object body) {
        List<Item> items = new ArrayList<>();
        List<?> list = null;
        if (body instanceof List<?> bare) {
            list = bare;
        } else if (body instanceof Map<?, ?> map) {
            Object data = firstNonNull(
                    map.get("data"), map.get("items"), map.get("records"), map.get("inventory"));
            if (data instanceof List<?> nested) {
                list = nested;
            }
        }
        if (list == null) {
            return items;
        }
        for (Object row : list) {
            if (row instanceof Map<?, ?> m) {
                Item item = mapToItem(castMap(m));
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + config.get("api-key"));
        h.set("X-Account-Id", config.get("account-id"));
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    private static Object firstNonNull(Object... vals) {
        for (Object v : vals) {
            if (v != null) return v;
        }
        return null;
    }

    private static Map<String, Object> castMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = v.toString().trim();
        return s.isEmpty() ? def : s;
    }

    private static double parseDouble(Object v, double def) {
        if (v == null) return def;
        try {
            return Double.parseDouble(v.toString().replaceAll("[^\\d.\\-]", ""));
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseInt(Object v, int def) {
        if (v == null) return def;
        try {
            return (int) Math.round(Double.parseDouble(v.toString().replaceAll("[^\\d.\\-]", "")));
        } catch (Exception e) {
            return def;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
