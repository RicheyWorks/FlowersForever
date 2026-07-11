package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Square Catalog + Inventory connector — dual mode:
 * <ul>
 *   <li><b>Local mirror</b> ({@code connector.square.local-file}) — JSON catalog
 *       for offline market-POS demos without Square credentials.</li>
 *   <li><b>Remote REST</b> — access token (+ optional location) against Catalog/Inventory APIs.</li>
 * </ul>
 *
 * <pre>
 * connector.square.local-file=data/square-mirror.json
 * # or live:
 * connector.square.access-token=EAAAl…
 * connector.square.location-id=LXXXXXXXX
 * connector.square.environment=sandbox
 * </pre>
 */
@Component
public class SquareConnector implements ExternalConnector<Map<String, Object>>, DualModeCapable {

    private static final Logger log = LoggerFactory.getLogger(SquareConnector.class);
    private static final String SQUARE_VERSION = "2024-01-18";
    private static final String PROD_HOST = "https://connect.squareup.com";
    private static final String SANDBOX_HOST = "https://connect.squareupsandbox.com";

    private final ConnectorConfig config;
    private final RestTemplate restTemplate;

    public SquareConnector(
            @Value("${connector.square.access-token:}") String accessToken,
            @Value("${connector.square.location-id:}") String locationId,
            @Value("${connector.square.environment:sandbox}") String environment,
            @Value("${connector.square.currency:USD}") String currency,
            @Value("${connector.square.local-file:}") String localFile) {
        this(accessToken, locationId, environment, currency, localFile, new RestTemplate());
    }

    /** Package-private for unit tests with a mockable {@link RestTemplate}. */
    SquareConnector(String accessToken, String locationId, String environment,
                    String currency, RestTemplate restTemplate) {
        this(accessToken, locationId, environment, currency, "", restTemplate);
    }

    SquareConnector(String accessToken, String locationId, String environment,
                    String currency, String localFile, RestTemplate restTemplate) {
        String env = (environment == null || environment.isBlank()) ? "sandbox" : environment.trim().toLowerCase(Locale.ROOT);
        this.config = new ConnectorConfig("square")
                .set("access-token", nullToEmpty(accessToken))
                .set("location-id", nullToEmpty(locationId))
                .set("environment", env)
                .set("currency", (currency == null || currency.isBlank()) ? "USD" : currency.trim().toUpperCase(Locale.ROOT))
                .set("local-file", nullToEmpty(localFile));
        this.restTemplate = restTemplate;
    }

    @Override public String getName() { return "square"; }

    @Override
    public String getDescription() {
        return isLocalMode()
                ? "Square (local JSON mirror) — offline catalog import/export/sync"
                : "Square Catalog + Inventory — POS items as farm inventory SKUs";
    }

    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return isLocalMode() || config.has("access-token");
    }

    @Override
    public boolean isLocalMode() {
        return config.has("local-file");
    }

    private boolean hasLocation() {
        return config.has("location-id");
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
        try {
            LocalJsonMirror mirror = new LocalJsonMirror(config.get("local-file"), "square");
            List<Map<String, Object>> rows = mirror.readRows();
            List<Item> items = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Item item = mapToItem(row);
                if (item != null) {
                    items.add(item);
                }
            }
            String msg = "Square local import — " + items.size()
                    + " item(s) from " + mirror.path().getFileName() + ".";
            log.info("[square] {}", msg);
            return ConnectorResult.ok(items, msg, getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Square local import failed.", e, getName());
        }
    }

    private ConnectorResult<List<Item>> importFromRemote() {
        try {
            List<Map<String, Object>> catalogItems = listAllItems();
            Map<String, Integer> qtyByVariation = hasLocation()
                    ? fetchInventoryCounts(collectVariationIds(catalogItems))
                    : Map.of();

            List<Item> items = new ArrayList<>();
            for (Map<String, Object> catalogObject : catalogItems) {
                Item item = mapToItem(enrichWithQuantity(catalogObject, qtyByVariation));
                if (item != null) {
                    items.add(item);
                }
            }

            String msg = "Square REST import complete — " + items.size() + " item(s)"
                    + (hasLocation() ? " (with inventory counts)." : " (no location-id; quantities default to 0).");
            log.info("[square] {}", msg);
            return ConnectorResult.ok(items, msg, getName());

        } catch (RestClientResponseException e) {
            return ConnectorResult.fail(
                    "Square import failed.",
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Square import failed.", e, getName());
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
        try {
            LocalJsonMirror mirror = new LocalJsonMirror(config.get("local-file"), "square");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Item item : items) {
                // Flattened shape mapToItem already accepts for local round-trip
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", item.getName());
                row.put("category", item.getCategory());
                row.put("price", item.getPrice());
                row.put("unit", item.getUnit());
                row.put("cost", item.getCost());
                row.put("quantity", item.getQuantity());
                row.put("notes", item.getNotes() == null ? "" : item.getNotes());
                row.put("source", "FlowersForever");
                rows.add(row);
            }
            mirror.writeRows(rows);
            String msg = "Square local export — wrote " + items.size()
                    + " item(s) to " + mirror.path().getFileName() + ".";
            log.info("[square] {}", msg);
            return ConnectorResult.ok(items.size(), msg, getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Square local export failed.", e, getName());
        }
    }

    private ConnectorResult<Integer> exportToRemote(List<Item> items) {
        if (items.isEmpty()) {
            return ConnectorResult.ok(0, "No items to export to Square.", getName());
        }

        int created = 0;
        List<String> errors = new ArrayList<>();

        for (Item item : items) {
            try {
                Map<String, Object> createdObj = upsertCatalogItem(item, null);
                created++;
                if (hasLocation()) {
                    String variationId = firstVariationId(createdObj);
                    if (variationId != null && !variationId.startsWith("#")) {
                        setInventoryCount(variationId, item.getQuantity());
                    }
                }
            } catch (RestClientResponseException e) {
                String detail = item.getName() + ": HTTP " + e.getStatusCode().value();
                errors.add(detail);
                log.warn("[square] Export skipped for '{}' — {}", item.getName(), detail);
            } catch (Exception e) {
                errors.add(item.getName() + ": " + e.getMessage());
                log.warn("[square] Export skipped for '{}' — {}", item.getName(), e.getMessage());
            }
        }

        if (created == 0 && !errors.isEmpty()) {
            return ConnectorResult.fail(
                    "Square export failed for all items.",
                    String.join("; ", errors),
                    getName());
        }

        String msg = "Square REST export complete — created " + created + " of " + items.size() + " item(s)."
                + (errors.isEmpty() ? "" : " " + errors.size() + " error(s).");
        log.info("[square] {}", msg);
        return ConnectorResult.ok(created, msg, getName());
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * Match catalog / mirror items by name (case-insensitive).
     */
    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        if (!isAvailable()) {
            return ConnectorResult.unavailable(getName());
        }
        if (localItems == null) {
            localItems = List.of();
        }
        return isLocalMode() ? syncLocal(localItems) : syncRemote(localItems);
    }

    private ConnectorResult<SyncSummary> syncLocal(List<Item> localItems) {
        ConnectorResult<List<Item>> remote = importItems();
        Map<String, Item> remoteByName = new LinkedHashMap<>();
        if (remote.isSuccess() && remote.getPayload() != null) {
            remote.getPayload().forEach(i -> remoteByName.put(i.getName().toLowerCase(Locale.ROOT), i));
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
                    "Square sync export step failed: " + export.getMessage(),
                    export.getErrorDetail(),
                    getName());
        }
        SyncSummary summary = new SyncSummary(created, updated, 0, skipped, 0);
        String msg = "Square sync complete. " + summary + " (local mirror)";
        log.info("[square] {}", msg);
        return ConnectorResult.ok(summary, msg, getName());
    }

    private ConnectorResult<SyncSummary> syncRemote(List<Item> localItems) {
        try {
            List<Map<String, Object>> remoteItems = listAllItems();
            Map<String, Integer> qtyByVariation = hasLocation()
                    ? fetchInventoryCounts(collectVariationIds(remoteItems))
                    : Map.of();

            Map<String, Map<String, Object>> remoteByName = new LinkedHashMap<>();
            for (Map<String, Object> obj : remoteItems) {
                Item mapped = mapToItem(enrichWithQuantity(obj, qtyByVariation));
                if (mapped != null) {
                    remoteByName.put(mapped.getName().toLowerCase(Locale.ROOT),
                            enrichWithQuantity(obj, qtyByVariation));
                }
            }

            int created = 0, updated = 0, skipped = 0, errors = 0;

            for (Item local : localItems) {
                String key = local.getName().trim().toLowerCase(Locale.ROOT);
                Map<String, Object> remote = remoteByName.get(key);
                try {
                    if (remote == null) {
                        Map<String, Object> createdObj = upsertCatalogItem(local, null);
                        if (hasLocation()) {
                            String variationId = firstVariationId(createdObj);
                            if (variationId != null && !variationId.startsWith("#")) {
                                setInventoryCount(variationId, local.getQuantity());
                            }
                        }
                        created++;
                    } else {
                        Item remoteItem = mapToItem(remote);
                        boolean priceDiff = remoteItem == null
                                || Double.compare(remoteItem.getPrice(), local.getPrice()) != 0;
                        boolean qtyDiff = remoteItem == null
                                || remoteItem.getQuantity() != local.getQuantity();

                        if (priceDiff || qtyDiff) {
                            if (priceDiff) {
                                upsertCatalogItem(local, remote);
                            }
                            if (qtyDiff && hasLocation()) {
                                String variationId = firstVariationId(remote);
                                if (variationId != null) {
                                    setInventoryCount(variationId, local.getQuantity());
                                }
                            }
                            updated++;
                        } else {
                            skipped++;
                        }
                    }
                } catch (Exception e) {
                    errors++;
                    log.warn("[square] Sync error for '{}': {}", local.getName(), e.getMessage());
                }
            }

            SyncSummary summary = new SyncSummary(created, updated, 0, skipped, errors);
            String msg = "Square sync complete. " + summary + " (REST)";
            log.info("[square] {}", msg);
            return ConnectorResult.ok(summary, msg, getName());

        } catch (RestClientResponseException e) {
            return ConnectorResult.fail(
                    "Square sync failed.",
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Square sync failed.", e, getName());
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * Expects a Square CatalogObject map of type ITEM, optionally enriched with
     * {@code _quantity} from the Inventory API.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Item mapToItem(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        try {
            Map<String, Object> itemData = asMap(raw.get("item_data"));
            if (itemData.isEmpty() && raw.containsKey("name")) {
                // Already-flattened map
                return new Item(
                        str(raw.get("name"), "Unknown"),
                        str(raw.get("category"), "Other"),
                        parseDouble(raw.get("price"), 0.0),
                        str(raw.get("unit"), "Per Stem"),
                        parseDouble(raw.get("cost"), 0.0),
                        parseInt(raw.get("quantity"), 0),
                        str(raw.get("notes"), "")
                );
            }

            String name = str(itemData.get("name"), "Unknown");
            String notes = str(itemData.get("description"), "");
            String category = str(itemData.get("category_name"), "Other");
            if ("Other".equals(category) && itemData.get("categories") instanceof List<?> cats && !cats.isEmpty()) {
                // Best-effort: Square may return category objects or ids
                category = str(cats.get(0), "Other");
            }

            Map<String, Object> variation = firstVariation(raw);
            Map<String, Object> varData = asMap(variation.get("item_variation_data"));
            double price = moneyToDollars(asMap(varData.get("price_money")));
            String unit = str(varData.get("sku"), "Per Stem");
            if (unit.isBlank()) {
                unit = "Per Stem";
            }
            int qty = parseInt(raw.get("_quantity"), 0);

            return new Item(name, category, price, unit, 0.0, qty, notes);
        } catch (Exception e) {
            log.warn("[square] Skipping unmappable catalog object: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Builds a Square CatalogObject payload (type ITEM with one variation)
     * suitable for {@code UpsertCatalogObject}.
     */
    @Override
    public Map<String, Object> mapFromItem(Item item) {
        String itemTempId = "#item_" + sanitizeId(item.getName());
        String varTempId = "#var_" + sanitizeId(item.getName());

        Map<String, Object> priceMoney = new LinkedHashMap<>();
        priceMoney.put("amount", Math.round(item.getPrice() * 100));
        priceMoney.put("currency", config.get("currency", "USD"));

        Map<String, Object> variationData = new LinkedHashMap<>();
        variationData.put("item_id", itemTempId);
        variationData.put("name", "Regular");
        variationData.put("pricing_type", "FIXED_PRICING");
        variationData.put("price_money", priceMoney);
        variationData.put("track_inventory", true);
        if (item.getUnit() != null && !item.getUnit().isBlank()) {
            variationData.put("sku", item.getUnit());
        }

        Map<String, Object> variation = new LinkedHashMap<>();
        variation.put("type", "ITEM_VARIATION");
        variation.put("id", varTempId);
        variation.put("item_variation_data", variationData);

        Map<String, Object> itemData = new LinkedHashMap<>();
        itemData.put("name", item.getName());
        itemData.put("description", item.getNotes() == null ? "" : item.getNotes());
        itemData.put("variations", List.of(variation));
        // Store farm category in description prefix if useful — also as product_type-like abbreviation
        if (item.getCategory() != null && !item.getCategory().isBlank()) {
            itemData.put("product_type", item.getCategory());
        }

        Map<String, Object> catalogObject = new LinkedHashMap<>();
        catalogObject.put("type", "ITEM");
        catalogObject.put("id", itemTempId);
        catalogObject.put("item_data", itemData);
        return catalogObject;
    }

    // ── Catalog HTTP ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listAllItems() {
        List<Map<String, Object>> items = new ArrayList<>();
        String cursor = null;

        do {
            String url = baseUrl() + "/v2/catalog/list?types=ITEM";
            if (cursor != null && !cursor.isBlank()) {
                url += "&cursor=" + cursor;
            }

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("ListCatalog HTTP " + response.getStatusCode());
            }

            Object objects = response.getBody().get("objects");
            if (objects instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        Map<String, Object> obj = castMap(m);
                        if ("ITEM".equals(String.valueOf(obj.get("type")))) {
                            items.add(obj);
                        }
                    }
                }
            }

            Object next = response.getBody().get("cursor");
            cursor = next == null ? null : next.toString();
        } while (cursor != null && !cursor.isBlank());

        return items;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> upsertCatalogItem(Item item, Map<String, Object> existingRemote) {
        Map<String, Object> catalogObject = mapFromItem(item);

        if (existingRemote != null && existingRemote.get("id") != null) {
            // Update existing: reuse permanent ids
            String itemId = String.valueOf(existingRemote.get("id"));
            catalogObject.put("id", itemId);
            Map<String, Object> itemData = asMap(catalogObject.get("item_data"));
            Map<String, Object> existingVar = firstVariation(existingRemote);
            String varId = existingVar.get("id") != null
                    ? String.valueOf(existingVar.get("id"))
                    : "#var_update";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> variations = (List<Map<String, Object>>) itemData.get("variations");
            if (variations != null && !variations.isEmpty()) {
                Map<String, Object> v = new LinkedHashMap<>(variations.get(0));
                v.put("id", varId);
                Map<String, Object> vd = new LinkedHashMap<>(asMap(v.get("item_variation_data")));
                vd.put("item_id", itemId);
                v.put("item_variation_data", vd);
                itemData.put("variations", List.of(v));
            }
            if (existingRemote.get("version") != null) {
                catalogObject.put("version", existingRemote.get("version"));
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotency_key", UUID.randomUUID().toString());
        body.put("object", catalogObject);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/v2/catalog/object",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                Map.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("UpsertCatalogObject HTTP " + response.getStatusCode());
        }

        Object catalogObj = response.getBody().get("catalog_object");
        if (catalogObj instanceof Map<?, ?> m) {
            return castMap(m);
        }
        return catalogObject;
    }

    // ── Inventory HTTP ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Integer> fetchInventoryCounts(List<String> variationIds) {
        Map<String, Integer> counts = new HashMap<>();
        if (variationIds.isEmpty() || !hasLocation()) {
            return counts;
        }

        // Square allows large batches; chunk to stay safe
        final int chunkSize = 100;
        for (int i = 0; i < variationIds.size(); i += chunkSize) {
            List<String> chunk = variationIds.subList(i, Math.min(i + chunkSize, variationIds.size()));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("catalog_object_ids", chunk);
            body.put("location_ids", List.of(config.get("location-id")));

            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl() + "/v2/inventory/counts/batch-retrieve",
                    HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders()),
                    Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[square] Inventory batch-retrieve HTTP {}", response.getStatusCode());
                continue;
            }

            Object countsObj = response.getBody().get("counts");
            if (countsObj instanceof List<?> list) {
                for (Object c : list) {
                    if (c instanceof Map<?, ?> m) {
                        Map<String, Object> count = castMap(m);
                        String id = str(count.get("catalog_object_id"), "");
                        int qty = parseInt(count.get("quantity"), 0);
                        // Sum if multiple states returned
                        counts.merge(id, qty, Integer::sum);
                    }
                }
            }
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    private void setInventoryCount(String variationId, int quantity) {
        Map<String, Object> physicalCount = new LinkedHashMap<>();
        physicalCount.put("catalog_object_id", variationId);
        physicalCount.put("location_id", config.get("location-id"));
        physicalCount.put("quantity", String.valueOf(Math.max(0, quantity)));
        physicalCount.put("occurred_at", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        physicalCount.put("state", "IN_STOCK");

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("type", "PHYSICAL_COUNT");
        change.put("physical_count", physicalCount);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotency_key", UUID.randomUUID().toString());
        body.put("changes", List.of(change));

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/v2/inventory/changes/batch-create",
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                Map.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("BatchCreateInventoryChanges HTTP " + response.getStatusCode());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String baseUrl() {
        return "production".equalsIgnoreCase(config.get("environment"))
                ? PROD_HOST
                : SANDBOX_HOST;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.get("access-token"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Square-Version", SQUARE_VERSION);
        return headers;
    }

    private List<String> collectVariationIds(List<Map<String, Object>> catalogItems) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> item : catalogItems) {
            String id = firstVariationId(item);
            if (id != null && !id.startsWith("#")) {
                ids.add(id);
            }
        }
        return ids;
    }

    private Map<String, Object> enrichWithQuantity(Map<String, Object> catalogObject,
                                                   Map<String, Integer> qtyByVariation) {
        Map<String, Object> copy = new LinkedHashMap<>(catalogObject);
        String varId = firstVariationId(catalogObject);
        if (varId != null) {
            copy.put("_quantity", qtyByVariation.getOrDefault(varId, 0));
        } else {
            copy.put("_quantity", 0);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstVariation(Map<String, Object> catalogObject) {
        Map<String, Object> itemData = asMap(catalogObject.get("item_data"));
        Object variations = itemData.get("variations");
        if (variations instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> m) {
            return castMap(m);
        }
        return Map.of();
    }

    private String firstVariationId(Map<String, Object> catalogObject) {
        Map<String, Object> variation = firstVariation(catalogObject);
        Object id = variation.get("id");
        return id == null ? null : id.toString();
    }

    private static double moneyToDollars(Map<String, Object> money) {
        if (money == null || money.isEmpty()) {
            return 0.0;
        }
        long cents = Math.round(parseDouble(money.get("amount"), 0.0));
        return cents / 100.0;
    }

    private static String sanitizeId(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return castMap(m);
        }
        return Map.of();
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
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int parseInt(Object v, int def) {
        if (v == null) return def;
        try {
            return (int) Math.round(Double.parseDouble(v.toString().trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
