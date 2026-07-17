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
 * Floranext florist POS connector — dual mode:
 * <ul>
 *   <li><b>Local mirror</b> ({@code connector.floranext.local-file}) — JSON product
 *       catalog for offline import/export/sync demos.</li>
 *   <li><b>Remote REST</b> — API key + store URL against {@code /api/products}.</li>
 * </ul>
 *
 * <pre>
 * connector.floranext.local-file=data/floranext-mirror.json
 * # or
 * connector.floranext.api-key=…
 * connector.floranext.store-url=https://your-shop.floranext.com
 * </pre>
 */
@Component
public class FloranextConnector implements ExternalConnector<Map<String, Object>>, DualModeCapable {

    private static final Logger log = LoggerFactory.getLogger(FloranextConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConnectorConfig config;
    private final RestTemplate restTemplate;

    public FloranextConnector(
            @Value("${connector.floranext.api-key:}") String apiKey,
            @Value("${connector.floranext.store-url:}") String storeUrl,
            @Value("${connector.floranext.local-file:}") String localFile) {
        this(apiKey, storeUrl, localFile, new RestTemplate());
    }

    FloranextConnector(String apiKey, String storeUrl, String localFile, RestTemplate restTemplate) {
        this.config = new ConnectorConfig("floranext")
                .set("api-key", nullToEmpty(apiKey))
                .set("store-url", normalizeStore(storeUrl))
                .set("local-file", nullToEmpty(localFile));
        this.restTemplate = restTemplate;
    }

    @Override public String getName() { return "floranext"; }

    @Override
    public String getDescription() {
        return isLocalMode()
                ? "Floranext (local JSON mirror) — offline product import/export/sync"
                : "Floranext florist POS — products ↔ farm inventory (REST)";
    }

    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return isLocalMode() || config.hasAll("api-key", "store-url");
    }

    @Override
    public boolean isLocalMode() {
        return config.has("local-file");
    }

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
            return ConnectorResult.ok(List.of(),
                    "Floranext local mirror empty (file not found yet). Export to create it.", getName());
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
            String msg = "Floranext local import — " + items.size() + " product(s) from " + path.getFileName() + ".";
            log.info("[floranext] {}", msg);
            return ConnectorResult.ok(items, msg, getName());
        } catch (IOException e) {
            return ConnectorResult.fail("Floranext local import failed.", e, getName());
        }
    }

    private ConnectorResult<List<Item>> importFromRemote() {
        try {
            String url = config.get("store-url") + "/api/products?limit=200";
            ResponseEntity<?> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders()), Object.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return ConnectorResult.fail(
                        "Floranext import failed.",
                        "HTTP " + response.getStatusCode(),
                        getName());
            }

            List<Item> items = parseRemoteList(response.getBody());
            String msg = "Floranext REST import complete — " + items.size() + " product(s).";
            log.info("[floranext] {}", msg);
            return ConnectorResult.ok(items, msg, getName());

        } catch (RestClientResponseException e) {
            return ConnectorResult.fail(
                    "Floranext import failed.",
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Floranext import failed.", e, getName());
        }
    }

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
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Item item : items) {
                rows.add(mapFromItem(item));
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), rows);
            String msg = "Floranext local export — wrote " + items.size()
                    + " product(s) to " + path.getFileName() + ".";
            log.info("[floranext] {}", msg);
            return ConnectorResult.ok(items.size(), msg, getName());
        } catch (IOException e) {
            return ConnectorResult.fail("Floranext local export failed.", e, getName());
        }
    }

    private ConnectorResult<Integer> exportToRemote(List<Item> items) {
        if (items.isEmpty()) {
            return ConnectorResult.ok(0, "No items to export to Floranext.", getName());
        }
        int sent = 0;
        List<String> errors = new ArrayList<>();
        String url = config.get("store-url") + "/api/products";

        for (Item item : items) {
            try {
                Map<String, Object> body = Map.of("product", mapFromItem(item));
                restTemplate.exchange(
                        url, HttpMethod.POST, new HttpEntity<>(body, authHeaders()), Map.class);
                sent++;
            } catch (RestClientResponseException e) {
                errors.add(item.getName() + ": HTTP " + e.getStatusCode().value());
                log.warn("[floranext] Export skip '{}': HTTP {}", item.getName(), e.getStatusCode().value());
            } catch (Exception e) {
                errors.add(item.getName() + ": " + e.getMessage());
            }
        }

        if (sent == 0 && !errors.isEmpty()) {
            return ConnectorResult.fail(
                    "Floranext export failed for all items.",
                    String.join("; ", errors),
                    getName());
        }
        String msg = "Floranext REST export complete — sent " + sent + " of " + items.size() + " product(s)."
                + (errors.isEmpty() ? "" : " " + errors.size() + " error(s).");
        log.info("[floranext] {}", msg);
        return ConnectorResult.ok(sent, msg, getName());
    }

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
                    "Floranext sync aborted — could not read remote catalog.",
                    remote.getErrorDetail(),
                    getName());
        }

        int created = 0, updated = 0, skipped = 0;
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

        ConnectorResult<Integer> export = exportItems(localItems);
        if (!export.isSuccess()) {
            return ConnectorResult.fail(
                    "Floranext sync export step failed: " + export.getMessage(),
                    export.getErrorDetail(),
                    getName());
        }

        SyncSummary summary = new SyncSummary(created, updated, 0, skipped, 0);
        String msg = "Floranext sync complete. " + summary
                + (isLocalMode() ? " (local mirror)" : " (REST)");
        log.info("[floranext] {}", msg);
        return ConnectorResult.ok(summary, msg, getName());
    }

    @Override
    public Item mapToItem(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            String name = str(firstNonNull(raw.get("name"), raw.get("title"), raw.get("product_name")), "Unknown");
            String category = str(firstNonNull(raw.get("category"), raw.get("product_type"), raw.get("type")), "Other");
            double price = parseDouble(firstNonNull(raw.get("price"), raw.get("sale_price"), raw.get("amount")), 0);
            int qty = parseInt(firstNonNull(raw.get("stock"), raw.get("quantity"), raw.get("inventory")), 0);
            String unit = str(firstNonNull(raw.get("unit"), raw.get("uom")), "Per Stem");
            String notes = str(firstNonNull(raw.get("description"), raw.get("notes")), "");
            double cost = parseDouble(raw.get("cost"), 0);
            return new Item(name, category, price, unit, cost, qty, notes);
        } catch (Exception e) {
            log.warn("[floranext] Skip unmappable product: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> mapFromItem(Item item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", item.getName());
        m.put("product_type", item.getCategory());
        m.put("category", item.getCategory());
        m.put("price", item.getPrice());
        m.put("stock", item.getQuantity());
        m.put("quantity", item.getQuantity());
        m.put("unit", item.getUnit());
        m.put("description", item.getNotes() == null ? "" : item.getNotes());
        m.put("vendor", "FlowersForever PNW");
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Item> parseRemoteList(Object body) {
        List<Item> items = new ArrayList<>();
        List<?> list = null;
        if (body instanceof List<?> bare) {
            list = bare;
        } else if (body instanceof Map<?, ?> map) {
            Object data = firstNonNull(map.get("products"), map.get("data"), map.get("items"));
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
        h.set("X-Api-Key", config.get("api-key"));
        h.set("Authorization", "Bearer " + config.get("api-key"));
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    private static String normalizeStore(String storeUrl) {
        if (storeUrl == null || storeUrl.isBlank()) {
            return "";
        }
        String s = storeUrl.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            s = "https://" + s;
        }
        return s.replaceAll("/+$", "");
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
