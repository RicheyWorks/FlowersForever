package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.ConnectorHttpClient;
import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;

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
    private static final int PAGE_LIMIT = 50;
    private static final int MAX_PAGES = 1000;
    private static final int MAX_LINK_HEADER_LENGTH = 16384;
    private static final int MAX_PAGE_URL_LENGTH = 8192;
    private static final int MAX_HTML_LENGTH = 1_000_000;

    private final ConnectorConfig config;
    private final RestTemplate restTemplate;

    @Autowired
    public ShopifyConnector(
            @Value("${connector.shopify.shop-name:}") String shopName,
            @Value("${connector.shopify.api-token:}") String apiToken,
            @Value("${connector.shopify.api-version:2024-01}") String apiVersion,
            @Value("${connector.shopify.local-file:}") String localFile) {
        this(shopName, apiToken, apiVersion, localFile, new RestTemplate(new ShopifyRequestFactory()));
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
        int pages = 0;

        try {
            String endpoint = productsUrl();
            String url = endpoint + "?limit=" + PAGE_LIMIT;
            Set<String> visited = new HashSet<>();
            while (url != null && !url.isBlank()) {
                if (pages >= MAX_PAGES || !visited.add(url)) {
                    throw new IllegalArgumentException("Shopify pagination repeated a page or exceeded its limit");
                }
                pages++;
                ResponseEntity<Map> response = restTemplate.exchange(
                        URI.create(url), HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);

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

                url = nextPageUrl(endpoint, response.getHeaders());
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

        try {
            String endpoint = productsUrl();
            String url = endpoint + "?limit=" + PAGE_LIMIT;
            Set<String> visited = new HashSet<>();
            while (url != null && !url.isBlank()) {
                if (visited.size() >= MAX_PAGES || !visited.add(url)) {
                    throw new IllegalArgumentException("Shopify pagination repeated a page or exceeded its limit");
                }
                ResponseEntity<Map> response = restTemplate.exchange(
                        URI.create(url), HttpMethod.GET, new HttpEntity<>(authHeaders()), Map.class);
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
                url = nextPageUrl(endpoint, response.getHeaders());
            }
            return ConnectorResult.ok(products, "Fetched " + products.size() + " products.", getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Failed to list Shopify products.", e, getName());
        }
    }

    private String productsUrl() {
        String shop = config.get("shop-name", "");
        if (shop.isBlank() || shop.length() > 255) {
            throw new IllegalArgumentException("A valid Shopify store name is required");
        }
        String supplied = shop.contains("://") ? shop
                : "https://" + (shop.contains(".") ? shop : shop + ".myshopify.com");
        URI configured = URI.create(supplied);
        String host = configured.getHost();
        String path = configured.getRawPath();
        if (!"https".equalsIgnoreCase(configured.getScheme()) || host == null
                || configured.getRawUserInfo() != null || configured.getRawQuery() != null
                || configured.getRawFragment() != null || !allowedHttpsPort(configured.getPort())
                || path == null || !(path.isEmpty() || path.equals("/"))) {
            throw new IllegalArgumentException("Shopify requires the store's HTTPS myshopify.com address");
        }
        host = host.toLowerCase(Locale.ROOT);
        String suffix = ".myshopify.com";
        if (!host.endsWith(suffix) || !storeLabel(host.substring(0, host.length() - suffix.length()))) {
            throw new IllegalArgumentException("Shopify requires the store's HTTPS myshopify.com address");
        }
        String version = config.get("api-version");
        if (version.length() != 7 || version.charAt(4) != '-') {
            throw new IllegalArgumentException("Shopify API version must be YYYY-MM");
        }
        for (int at = 0; at < version.length(); at++) {
            if (at != 4 && !asciiDigit(version.charAt(at))) {
                throw new IllegalArgumentException("Shopify API version must be YYYY-MM");
            }
        }
        int month = Integer.parseInt(version.substring(5));
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Shopify API version must be YYYY-MM");
        }
        return "https://" + host + "/admin/api/" + version + "/products.json";
    }

    private static boolean storeLabel(String label) {
        if (label.isEmpty() || label.length() > 63 || label.charAt(0) == '-'
                || label.charAt(label.length() - 1) == '-') {
            return false;
        }
        for (int at = 0; at < label.length(); at++) {
            char value = label.charAt(at);
            if (!(value >= 'a' && value <= 'z') && !asciiDigit(value) && value != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean allowedHttpsPort(int port) {
        return port == -1 || port == 443;
    }

    private static boolean asciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static String nextPageUrl(String endpoint, HttpHeaders headers) {
        List<String> values = headers.get(HttpHeaders.LINK);
        if (values == null || values.isEmpty()) {
            return null;
        }
        int length = values.size() - 1;
        for (String value : values) {
            if (value == null || value.length() > MAX_LINK_HEADER_LENGTH
                    || length > MAX_LINK_HEADER_LENGTH - value.length()) {
                throw new IllegalArgumentException("Shopify pagination header is too long or invalid");
            }
            length += value.length();
        }
        String next = extractNextLink(String.join(",", values));
        if (next == null) {
            return null;
        }
        URI base = URI.create(endpoint);
        URI candidate = URI.create(next);
        String host = base.getHost();
        String path = base.getRawPath();
        if (!"https".equalsIgnoreCase(candidate.getScheme()) || candidate.getHost() == null
                || host == null || !host.equalsIgnoreCase(candidate.getHost())
                || !allowedHttpsPort(candidate.getPort()) || candidate.getRawUserInfo() != null
                || candidate.getRawFragment() != null || path == null || !path.equals(candidate.getRawPath())) {
            throw new IllegalArgumentException("Shopify pagination left the configured store's product endpoint");
        }
        // Preserve the opaque page_info query exactly. Authority and path always
        // come from the admitted configured store, never the provider's Link URL.
        String query = candidate.getRawQuery();
        return query == null ? endpoint : endpoint + "?" + query;
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
        if (linkHeader.length() > MAX_LINK_HEADER_LENGTH) {
            throw new IllegalArgumentException("Shopify pagination header is too long");
        }
        String next = null;
        int at = 0;
        while (at < linkHeader.length()) {
            at = skipLinkWhitespace(linkHeader, at);
            if (at == linkHeader.length() || linkHeader.charAt(at++) != '<') {
                throw new IllegalArgumentException("Shopify pagination header is malformed");
            }
            int end = linkHeader.indexOf('>', at);
            if (end < 0 || end == at || end - at > MAX_PAGE_URL_LENGTH) {
                throw new IllegalArgumentException("Shopify pagination URL is malformed or too long");
            }
            String url = linkHeader.substring(at, end);
            at = end + 1;
            String relation = null;
            while (true) {
                at = skipLinkWhitespace(linkHeader, at);
                if (at == linkHeader.length() || linkHeader.charAt(at) == ',') {
                    break;
                }
                if (linkHeader.charAt(at++) != ';') {
                    throw new IllegalArgumentException("Shopify pagination header is malformed");
                }
                at = skipLinkWhitespace(linkHeader, at);
                int nameStart = at;
                while (at < linkHeader.length() && linkParameterCharacter(linkHeader.charAt(at))) at++;
                String name = linkHeader.substring(nameStart, at);
                at = skipLinkWhitespace(linkHeader, at);
                if (name.isEmpty() || at == linkHeader.length() || linkHeader.charAt(at++) != '=') {
                    throw new IllegalArgumentException("Shopify pagination parameter is malformed");
                }
                at = skipLinkWhitespace(linkHeader, at);
                StringBuilder value = new StringBuilder();
                if (at < linkHeader.length() && linkHeader.charAt(at) == '"') {
                    at++;
                    boolean closed = false;
                    while (at < linkHeader.length()) {
                        char current = linkHeader.charAt(at++);
                        if (current == '"') { closed = true; break; }
                        if (current == '\\') {
                            if (at == linkHeader.length()) break;
                            current = linkHeader.charAt(at++);
                        }
                        if (current < 32 || current == 127) {
                            throw new IllegalArgumentException("Shopify pagination parameter is malformed");
                        }
                        value.append(current);
                    }
                    if (!closed) throw new IllegalArgumentException("Shopify pagination parameter is malformed");
                } else {
                    while (at < linkHeader.length() && linkParameterCharacter(linkHeader.charAt(at))) {
                        value.append(linkHeader.charAt(at++));
                    }
                    if (value.isEmpty()) throw new IllegalArgumentException("Shopify pagination parameter is malformed");
                }
                if (name.equalsIgnoreCase("rel")) {
                    if (relation != null) throw new IllegalArgumentException("Shopify pagination relation is ambiguous");
                    relation = value.toString();
                }
            }
            if (relation == null) throw new IllegalArgumentException("Shopify pagination relation is missing");
            if (relation.equalsIgnoreCase("next")) {
                if (next != null) throw new IllegalArgumentException("Shopify pagination has multiple next pages");
                next = url;
            }
            if (at < linkHeader.length()) {
                at++;
                if (skipLinkWhitespace(linkHeader, at) == linkHeader.length()) {
                    throw new IllegalArgumentException("Shopify pagination header is malformed");
                }
            }
        }
        return next;
    }

    private static int skipLinkWhitespace(String header, int at) {
        while (at < header.length() && (header.charAt(at) == ' ' || header.charAt(at) == '\t')) at++;
        return at;
    }

    private static boolean linkParameterCharacter(char value) {
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                || asciiDigit(value) || value == '-' || value == '_' || value == '*';
    }

    /** Provider redirects are never allowed to carry the Admin API token elsewhere. */
    static final class ShopifyRequestFactory extends SimpleClientHttpRequestFactory {
        ShopifyRequestFactory() {
            setConnectTimeout(ConnectorHttpClient.CONNECT_TIMEOUT_MILLIS);
            setReadTimeout(ConnectorHttpClient.READ_TIMEOUT_MILLIS);
        }

        @Override
        protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
            super.prepareConnection(connection, method);
            connection.setInstanceFollowRedirects(false);
        }
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

    static String stripHtml(String html) {
        if (html == null) return "";
        if (html.length() > MAX_HTML_LENGTH) {
            throw new IllegalArgumentException("Shopify product description exceeds the supported size");
        }
        if (html.isBlank()) return "";
        StringBuilder stripped = new StringBuilder(html.length());
        int at = 0;
        while (at < html.length()) {
            char current = html.charAt(at);
            if (current == '<') {
                int end = html.indexOf('>', at + 1);
                if (end < 0) {
                    stripped.append(html, at, html.length());
                    break;
                }
                if (end > at + 1) {
                    stripped.append(' ');
                    at = end + 1;
                    continue;
                }
            }
            stripped.append(current);
            at++;
        }
        StringBuilder result = new StringBuilder(stripped.length());
        boolean whitespace = false;
        for (int index = 0; index < stripped.length(); index++) {
            char current = stripped.charAt(index);
            if (current == ' ' || current == '\t' || current == '\n' || current == '\r'
                    || current == '\f' || current == 11) {
                if (!whitespace) result.append(' ');
                whitespace = true;
            } else {
                result.append(current);
                whitespace = false;
            }
        }
        return result.toString().trim();
    }
}
