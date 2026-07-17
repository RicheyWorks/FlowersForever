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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shopify Admin connector — dual mode:
 * <ul>
 *   <li><b>Local mirror</b> ({@code connector.shopify.local-file}) — JSON product
 *       catalog for offline demos without Admin API credentials.</li>
 *   <li><b>Remote REST</b> — shop name + Admin API token against Shopify Admin.</li>
 * </ul>
 *
 * <p>Maps each product (first variant) to an {@link Item}:
 * title → name, product_type → category, variants[0].price/qty → price/quantity.
 *
 * <pre>
 * connector.shopify.local-file=data/shopify-mirror.json
 * # or live:
 * connector.shopify.shop-name=your-store
 * connector.shopify.api-token=shpat_…
 * connector.shopify.api-version=2024-01
 * </pre>
 */
@Component
public class ShopifyConnector implements ExternalConnector<Map<String, Object>>, DualModeCapable {

    private static final Logger log = LoggerFactory.getLogger(ShopifyConnector.class);
    private static final Pattern LINK_NEXT =
            Pattern.compile("<([^>]+)>;\\s*rel=\"next\"", Pattern.CASE_INSENSITIVE);
    private static final int PAGE_LIMIT = 50;

    private final ConnectorConfig config;
    private final RestTemplate restTemplate;

    public ShopifyConnector(
            @Value("${connector.shopify.shop-name:}") String shopName,
            @Value("${connector.shopify.api-token:}") String apiToken,
            @Value("${connector.shopify.api-version:2024-01}") String apiVersion,
            @Value("${connector.shopify.local-file:}") String localFile) {
        this(shopName, apiToken, apiVersion, localFile, new RestTemplate());
    }

    /** Package-private constructor for unit tests with a mockable {@link RestTemplate}. */
    ShopifyConnector(String shopName, String apiToken, String apiVersion, RestTemplate restTemplate) {
        this(shopName, apiToken, apiVersion, "", restTemplate);
    }

    ShopifyConnector(String shopName, String apiToken, String apiVersion, String localFile,
                     RestTemplate restTemplate) {
        this.config = new ConnectorConfig("shopify")
                .set("shop-name", shopName == null ? "" : shopName.trim())
                .set("api-token", apiToken == null ? "" : apiToken.trim())
                .set("api-version", (apiVersion == null || apiVersion.isBlank()) ? "2024-01" : apiVersion.trim())
                .set("local-file", localFile == null ? "" : localFile.trim());
        this.restTemplate = restTemplate;
    }

    @Override public String getName() { return "shopify"; }

    @Override
    public String getDescription() {
        return isLocalMode()
                ? "Shopify (local JSON mirror) — offline product import/export/sync"
                : "Shopify Admin REST — import/export products as farm inventory SKUs";
    }

    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return isLocalMode() || config.hasAll("shop-name", "api-token");
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
        try {
            LocalJsonMirror mirror = new LocalJsonMirror(config.get("local-file"), "shopify");
            List<Map<String, Object>> rows = mirror.readRows();
            List<Item> items = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Item item = mapToItem(row);
                if (item != null) {
                    items.add(item);
                }
            }
            String msg = "Shopify local import — " + items.size()
                    + " product(s) from " + mirror.path().getFileName() + ".";
            log.info("[shopify] {}", msg);
            return ConnectorResult.ok(items, msg, getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Shopify local import failed.", e, getName());
        }
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult<List<Item>> importFromRemote() {
        List<Item> items = new ArrayList<>();
        String url = productsUrl() + "?limit=" + PAGE_LIMIT;
        int pages = 0;

        try {
            while (url != null && !url.isBlank()) {
                pages++;
                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

                Map<?, ?> body = response.getBody();
                if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                    return ConnectorResult.fail(
                            "Shopify import failed.",
                            "HTTP " + response.getStatusCode() + " on page " + pages,
                            getName());
                }

                Object productsObj = body.get("products");
                if (productsObj instanceof List<?> products) {
                    for (Object p : products) {
                        if (p instanceof Map<?, ?> productMap) {
                            Item item = mapToItem(castMap(productMap));
                            if (item != null) {
                                items.add(item);
                            }
                        }
                    }
                }

                url = extractNextLink(response.getHeaders().getFirst(HttpHeaders.LINK));
            }

            String msg = "Shopify REST import complete — " + items.size()
                    + " product(s) from " + pages + " page(s).";
            log.info("[shopify] {}", msg);
            return ConnectorResult.ok(items, msg, getName());

        } catch (RestClientResponseException e) {
            return ConnectorResult.fail(
                    "Shopify import failed.",
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Shopify import failed.", e, getName());
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
            LocalJsonMirror mirror = new LocalJsonMirror(config.get("local-file"), "shopify");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Item item : items) {
                rows.add(mapFromItem(item));
            }
            mirror.writeRows(rows);
            String msg = "Shopify local export — wrote " + items.size()
                    + " product(s) to " + mirror.path().getFileName() + ".";
            log.info("[shopify] {}", msg);
            return ConnectorResult.ok(items.size(), msg, getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Shopify local export failed.", e, getName());
        }
    }

    private ConnectorResult<Integer> exportToRemote(List<Item> items) {
        if (items.isEmpty()) {
            return ConnectorResult.ok(0, "No items to export to Shopify.", getName());
        }

        int created = 0;
        List<String> errors = new ArrayList<>();

        for (Item item : items) {
            try {
                createProduct(item);
                created++;
            } catch (RestClientResponseException e) {
                String detail = item.getName() + ": HTTP " + e.getStatusCode().value();
                errors.add(detail);
                log.warn("[shopify] Export skipped for '{}' — {}", item.getName(), detail);
            } catch (Exception e) {
                errors.add(item.getName() + ": " + e.getMessage());
                log.warn("[shopify] Export skipped for '{}' — {}", item.getName(), e.getMessage());
            }
        }

        if (created == 0 && !errors.isEmpty()) {
            return ConnectorResult.fail(
                    "Shopify export failed for all items.",
                    String.join("; ", errors),
                    getName());
        }

        String msg = "Shopify REST export complete — created " + created + " of " + items.size() + " product(s)."
                + (errors.isEmpty() ? "" : " " + errors.size() + " error(s).");
        log.info("[shopify] {}", msg);
        return ConnectorResult.ok(created, msg, getName());
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * Match products by title (case-insensitive). Local-only items are created
     * (or written to the mirror); shared items with different price/qty update.
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
                    "Shopify sync export step failed: " + export.getMessage(),
                    export.getErrorDetail(),
                    getName());
        }
        SyncSummary summary = new SyncSummary(created, updated, 0, skipped, 0);
        String msg = "Shopify sync complete. " + summary + " (local mirror)";
        log.info("[shopify] {}", msg);
        return ConnectorResult.ok(summary, msg, getName());
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult<SyncSummary> syncRemote(List<Item> localItems) {
        ConnectorResult<List<Map<String, Object>>> remoteResult = fetchAllProductsRaw();
        if (!remoteResult.isSuccess()) {
            return ConnectorResult.fail(
                    "Shopify sync failed during remote fetch.",
                    remoteResult.getErrorDetail(),
                    getName());
        }

        Map<String, Map<String, Object>> remoteByTitle = new LinkedHashMap<>();
        for (Map<String, Object> product : remoteResult.getPayload()) {
            Object title = product.get("title");
            if (title != null) {
                remoteByTitle.put(title.toString().trim().toLowerCase(Locale.ROOT), product);
            }
        }

        int created = 0, updated = 0, skipped = 0, errors = 0;

        for (Item local : localItems) {
            String key = local.getName().trim().toLowerCase(Locale.ROOT);
            Map<String, Object> remote = remoteByTitle.get(key);

            try {
                if (remote == null) {
                    createProduct(local);
                    created++;
                } else {
                    Item remoteItem = mapToItem(remote);
                    if (remoteItem == null
                            || remoteItem.getQuantity() != local.getQuantity()
                            || Double.compare(remoteItem.getPrice(), local.getPrice()) != 0
                            || !Objects.equals(remoteItem.getCategory(), local.getCategory())) {
                        updateProduct(remote, local);
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            } catch (Exception e) {
                errors++;
                log.warn("[shopify] Sync error for '{}': {}", local.getName(), e.getMessage());
            }
        }

        SyncSummary summary = new SyncSummary(created, updated, 0, skipped, errors);
        String msg = "Shopify sync complete. " + summary + " (REST)";
        log.info("[shopify] {}", msg);
        return ConnectorResult.ok(summary, msg, getName());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public Item mapToItem(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        Map<String, Object> flat = new LinkedHashMap<>(raw);
        // Flatten first variant fields for FieldMapper / direct access
        Object variantsObj = raw.get("variants");
        if (variantsObj instanceof List<?> variants && !variants.isEmpty()
                && variants.get(0) instanceof Map<?, ?> v0) {
            Map<String, Object> variant = castMap(v0);
            flat.putIfAbsent("price", variant.get("price"));
            flat.putIfAbsent("inventory_quantity", variant.get("inventory_quantity"));
            flat.putIfAbsent("sku", variant.get("sku"));
        }

        try {
            String name = str(flat.get("title"), "Unknown");
            String category = str(flat.get("product_type"), "Other");
            double price = parseDouble(flat.get("price"), 0.0);
            int qty = parseInt(flat.get("inventory_quantity"), 0);
            String notes = stripHtml(str(flat.get("body_html"), ""));
            return new Item(name, category, price, "Per Stem", 0.0, qty, notes);
        } catch (Exception ex) {
            log.warn("[shopify] Skipping unmappable product: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> mapFromItem(Item item) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("price", String.format(Locale.US, "%.2f", item.getPrice()));
        variant.put("inventory_management", "shopify");
        variant.put("inventory_quantity", item.getQuantity());
        if (item.getUnit() != null && !item.getUnit().isBlank()) {
            variant.put("sku", item.getUnit());
        }

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("title", item.getName());
        product.put("product_type", item.getCategory() == null ? "Other" : item.getCategory());
        product.put("body_html", item.getNotes() == null ? "" : item.getNotes());
        product.put("vendor", "FlowersForever PNW");
        product.put("status", "active");
        product.put("variants", List.of(variant));
        return product;
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private void createProduct(Item item) {
        Map<String, Object> body = Map.of("product", mapFromItem(item));
        restTemplate.exchange(
                productsUrl(),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                Map.class);
    }

    @SuppressWarnings("unchecked")
    private void updateProduct(Map<String, Object> remote, Item local) {
        Object id = remote.get("id");
        if (id == null) {
            throw new IllegalStateException("Remote product missing id");
        }

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("id", id);
        product.put("title", local.getName());
        product.put("product_type", local.getCategory());
        product.put("body_html", local.getNotes() == null ? "" : local.getNotes());

        // Update first variant price when present
        Object variantsObj = remote.get("variants");
        if (variantsObj instanceof List<?> variants && !variants.isEmpty()
                && variants.get(0) instanceof Map<?, ?> v0) {
            Map<String, Object> existing = castMap(v0);
            Map<String, Object> variantUpdate = new LinkedHashMap<>();
            variantUpdate.put("id", existing.get("id"));
            variantUpdate.put("price", String.format(Locale.US, "%.2f", local.getPrice()));
            // inventory_quantity on product update is best-effort; Shopify may require Inventory API
            variantUpdate.put("inventory_quantity", local.getQuantity());
            product.put("variants", List.of(variantUpdate));
        }

        restTemplate.exchange(
                productsUrl() + "/" + id + ".json",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("product", product), authHeaders()),
                Map.class);
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult<List<Map<String, Object>>> fetchAllProductsRaw() {
        List<Map<String, Object>> products = new ArrayList<>();
        String url = productsUrl() + "?limit=" + PAGE_LIMIT;

        try {
            while (url != null && !url.isBlank()) {
                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
                Map<?, ?> body = response.getBody();
                if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                    return ConnectorResult.fail(
                            "Failed to list Shopify products.",
                            "HTTP " + response.getStatusCode(),
                            getName());
                }
                Object productsObj = body.get("products");
                if (productsObj instanceof List<?> list) {
                    for (Object p : list) {
                        if (p instanceof Map<?, ?> m) {
                            products.add(castMap(m));
                        }
                    }
                }
                url = extractNextLink(response.getHeaders().getFirst(HttpHeaders.LINK));
            }
            return ConnectorResult.ok(products, "Fetched " + products.size() + " products.", getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Failed to list Shopify products.", e, getName());
        }
    }

    private String productsUrl() {
        String shop = config.get("shop-name", "").trim()
                .replace("https://", "")
                .replace("http://", "")
                .replaceAll("/+$", "");
        // Bare store name → {name}.myshopify.com; already-qualified hosts kept as-is
        String host = shop.endsWith(".myshopify.com") || shop.contains(".")
                ? shop
                : shop + ".myshopify.com";
        return "https://" + host + "/admin/api/" + config.get("api-version") + "/products.json";
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", config.get("api-token"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    static String extractNextLink(String linkHeader) {
        if (linkHeader == null || linkHeader.isBlank()) {
            return null;
        }
        Matcher m = LINK_NEXT.matcher(linkHeader);
        return m.find() ? m.group(1) : null;
    }

    @SuppressWarnings("unchecked")
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

    private static String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return html.replaceAll("(?is)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }
}
