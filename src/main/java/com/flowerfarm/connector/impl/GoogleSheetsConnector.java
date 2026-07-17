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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Google Sheets inventory connector — dual mode:
 * <ul>
 *   <li><b>Local mirror</b> ({@code connector.google-sheets.local-file}) — JSON
 *       row maps for offline shared-sheet demos without Google credentials.</li>
 *   <li><b>Remote REST</b> — spreadsheet id + API key and/or OAuth access token.</li>
 * </ul>
 *
 * <pre>
 * connector.google-sheets.local-file=data/google-sheets-mirror.json
 * # or live:
 * connector.google-sheets.spreadsheet-id=1BxiM…
 * connector.google-sheets.api-key=AIza…              # import public sheets
 * connector.google-sheets.access-token=ya29.…        # export/sync
 * </pre>
 */
@Component
public class GoogleSheetsConnector implements ExternalConnector<List<Object>>, DualModeCapable {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsConnector.class);
    private static final String SHEETS_API =
            "https://sheets.googleapis.com/v4/spreadsheets";

    private final ConnectorConfig config;
    private final FieldMapper fieldMapper;
    private final RestTemplate restTemplate;

    private final String colName;
    private final String colCategory;
    private final String colPrice;
    private final String colUnit;
    private final String colCost;
    private final String colQuantity;
    private final String colNotes;

    public GoogleSheetsConnector(
            @Value("${connector.google-sheets.spreadsheet-id:}") String spreadsheetId,
            @Value("${connector.google-sheets.sheet-name:Inventory}") String sheetName,
            @Value("${connector.google-sheets.api-key:}") String apiKey,
            @Value("${connector.google-sheets.access-token:}") String accessToken,
            @Value("${connector.google-sheets.local-file:}") String localFile,
            @Value("${connector.google-sheets.col.name:Name}") String colName,
            @Value("${connector.google-sheets.col.category:Category}") String colCategory,
            @Value("${connector.google-sheets.col.price:Price}") String colPrice,
            @Value("${connector.google-sheets.col.unit:Unit}") String colUnit,
            @Value("${connector.google-sheets.col.cost:Cost}") String colCost,
            @Value("${connector.google-sheets.col.quantity:Quantity}") String colQuantity,
            @Value("${connector.google-sheets.col.notes:Notes}") String colNotes) {
        this(spreadsheetId, sheetName, apiKey, accessToken, localFile,
                colName, colCategory, colPrice, colUnit, colCost, colQuantity, colNotes,
                new RestTemplate());
    }

    /** Package-private for unit tests (remote mode — empty local-file). */
    GoogleSheetsConnector(
            String spreadsheetId, String sheetName, String apiKey, String accessToken,
            String colName, String colCategory, String colPrice, String colUnit,
            String colCost, String colQuantity, String colNotes,
            RestTemplate restTemplate) {
        this(spreadsheetId, sheetName, apiKey, accessToken, "",
                colName, colCategory, colPrice, colUnit, colCost, colQuantity, colNotes,
                restTemplate);
    }

    GoogleSheetsConnector(
            String spreadsheetId, String sheetName, String apiKey, String accessToken,
            String localFile,
            String colName, String colCategory, String colPrice, String colUnit,
            String colCost, String colQuantity, String colNotes,
            RestTemplate restTemplate) {
        this.colName = colName;
        this.colCategory = colCategory;
        this.colPrice = colPrice;
        this.colUnit = colUnit;
        this.colCost = colCost;
        this.colQuantity = colQuantity;
        this.colNotes = colNotes;
        this.restTemplate = restTemplate;
        this.config = new ConnectorConfig("google-sheets")
                .set("spreadsheet-id", nullToEmpty(spreadsheetId))
                .set("sheet-name", (sheetName == null || sheetName.isBlank()) ? "Inventory" : sheetName.trim())
                .set("api-key", nullToEmpty(apiKey))
                .set("access-token", nullToEmpty(accessToken))
                .set("local-file", nullToEmpty(localFile));

        this.fieldMapper = new FieldMapper()
                .registerOutbound(colName, Item::getName)
                .registerOutbound(colCategory, Item::getCategory)
                .registerOutbound(colPrice, item -> String.format(Locale.US, "%.2f", item.getPrice()))
                .registerOutbound(colUnit, Item::getUnit)
                .registerOutbound(colCost, item -> String.format(Locale.US, "%.2f", item.getCost()))
                .registerOutbound(colQuantity, Item::getQuantity)
                .registerOutbound(colNotes, Item::getNotes);
    }

    @Override public String getName() { return "google-sheets"; }

    @Override
    public String getDescription() {
        return isLocalMode()
                ? "Google Sheets (local JSON mirror) — offline spreadsheet import/export/sync"
                : "Google Sheets — shared farm inventory spreadsheet (import/export/sync)";
    }

    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return isLocalMode()
                || (config.has("spreadsheet-id")
                && (config.has("api-key") || config.has("access-token")));
    }

    @Override
    public boolean isLocalMode() {
        return config.has("local-file");
    }

    private boolean canWrite() {
        return isLocalMode() || (config.has("spreadsheet-id") && config.has("access-token"));
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
            LocalJsonMirror mirror = new LocalJsonMirror(config.get("local-file"), "google-sheets");
            List<Map<String, Object>> rows = mirror.readRows();
            List<Item> items = new ArrayList<>();
            int skipped = 0;
            for (Map<String, Object> row : rows) {
                try {
                    items.add(mapFromRowMap(row));
                } catch (Exception e) {
                    skipped++;
                    log.warn("[google-sheets] Skip local row: {}", e.getMessage());
                }
            }
            String msg = "Google Sheets local import — " + items.size() + " item(s) from "
                    + mirror.path().getFileName()
                    + (skipped == 0 ? "." : " (" + skipped + " skipped).");
            log.info("[google-sheets] {}", msg);
            return ConnectorResult.ok(items, msg, getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Google Sheets local import failed.", e, getName());
        }
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult<List<Item>> importFromRemote() {
        try {
            String url = valuesUrl("GET");
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(authHeaders(false)), Map.class);

            Map<?, ?> body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                return ConnectorResult.fail(
                        "Google Sheets import failed.",
                        "HTTP " + response.getStatusCode(),
                        getName());
            }

            Object valuesObj = body.get("values");
            if (!(valuesObj instanceof List<?> rows) || rows.isEmpty()) {
                return ConnectorResult.ok(List.of(), "Google Sheet is empty.", getName());
            }

            List<List<Object>> table = new ArrayList<>();
            for (Object row : rows) {
                if (row instanceof List<?> cells) {
                    table.add(new ArrayList<>(cells));
                }
            }

            if (table.isEmpty()) {
                return ConnectorResult.ok(List.of(), "Google Sheet has no rows.", getName());
            }

            Map<String, Integer> headerIndex = buildHeaderIndex(table.get(0));
            List<Item> items = new ArrayList<>();
            int skipped = 0;

            for (int i = 1; i < table.size(); i++) {
                try {
                    items.add(mapToItem(table.get(i), headerIndex));
                } catch (Exception e) {
                    skipped++;
                    log.warn("[google-sheets] Skipping row {}: {}", i + 1, e.getMessage());
                }
            }

            String msg = "Google Sheets REST import complete — " + items.size() + " item(s)"
                    + (skipped == 0 ? "." : " (" + skipped + " row(s) skipped).");
            log.info("[google-sheets] {}", msg);
            return ConnectorResult.ok(items, msg, getName());

        } catch (RestClientResponseException e) {
            return ConnectorResult.fail(
                    "Google Sheets import failed.",
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Google Sheets import failed.", e, getName());
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!canWrite()) {
            if (isAvailable()) {
                return ConnectorResult.fail(
                        "Google Sheets export requires an access token.",
                        "Set connector.google-sheets.access-token (OAuth). API keys are read-only.",
                        getName());
            }
            return ConnectorResult.unavailable(getName());
        }
        if (items == null) {
            items = List.of();
        }
        return isLocalMode() ? exportToLocalFile(items) : exportToRemote(items);
    }

    private ConnectorResult<Integer> exportToLocalFile(List<Item> items) {
        try {
            LocalJsonMirror mirror = new LocalJsonMirror(config.get("local-file"), "google-sheets");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Item item : items) {
                rows.add(toRowMap(item));
            }
            mirror.writeRows(rows);
            String msg = "Google Sheets local export — wrote " + items.size()
                    + " item(s) to " + mirror.path().getFileName() + ".";
            log.info("[google-sheets] {}", msg);
            return ConnectorResult.ok(items.size(), msg, getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Google Sheets local export failed.", e, getName());
        }
    }

    private ConnectorResult<Integer> exportToRemote(List<Item> items) {
        try {
            List<List<Object>> values = new ArrayList<>();
            List<Object> header = new ArrayList<>(fieldMapper.outboundFields());
            values.add(header);

            for (Item item : items) {
                values.add(Arrays.asList(mapFromItem(item).toArray()));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("range", sheetRange());
            body.put("majorDimension", "ROWS");
            body.put("values", values);

            String url = UriComponentsBuilder
                    .fromHttpUrl(SHEETS_API + "/" + config.get("spreadsheet-id") + "/values/"
                            + encodeRange(sheetRange()))
                    .queryParam("valueInputOption", "USER_ENTERED")
                    .toUriString();

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.PUT, new HttpEntity<>(body, authHeaders(true)), Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                return ConnectorResult.fail(
                        "Google Sheets export failed.",
                        "HTTP " + response.getStatusCode(),
                        getName());
            }

            String msg = "Google Sheets REST export complete — wrote " + items.size() + " item(s) to '"
                    + config.get("sheet-name") + "'.";
            log.info("[google-sheets] {}", msg);
            return ConnectorResult.ok(items.size(), msg, getName());

        } catch (RestClientResponseException e) {
            return ConnectorResult.fail(
                    "Google Sheets export failed.",
                    "HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString(),
                    getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Google Sheets export failed.", e, getName());
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * Compares local inventory to the sheet/mirror by name, then rewrites with
     * the local snapshot (local is source of truth).
     */
    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        if (!canWrite()) {
            if (isAvailable()) {
                return ConnectorResult.fail(
                        "Google Sheets sync requires an access token for write-back.",
                        "Set connector.google-sheets.access-token.",
                        getName());
            }
            return ConnectorResult.unavailable(getName());
        }
        if (localItems == null) {
            localItems = List.of();
        }

        ConnectorResult<List<Item>> remote = importItems();
        Map<String, Item> sheetByName = new LinkedHashMap<>();
        if (remote.isSuccess() && remote.getPayload() != null) {
            remote.getPayload().forEach(i -> sheetByName.put(i.getName().toLowerCase(Locale.ROOT), i));
        }

        int created = 0, updated = 0, skipped = 0;
        for (Item local : localItems) {
            Item existing = sheetByName.get(local.getName().toLowerCase(Locale.ROOT));
            if (existing == null) {
                created++;
            } else if (existing.getQuantity() != local.getQuantity()
                    || Double.compare(existing.getPrice(), local.getPrice()) != 0) {
                updated++;
            } else {
                skipped++;
            }
        }

        ConnectorResult<Integer> export = exportItems(localItems);
        if (!export.isSuccess()) {
            return ConnectorResult.fail(
                    "Google Sheets sync export step failed: " + export.getMessage(),
                    export.getErrorDetail(),
                    getName());
        }

        SyncSummary summary = new SyncSummary(created, updated, 0, skipped, 0);
        String msg = "Google Sheets sync complete. " + summary
                + (isLocalMode() ? " (local mirror)" : " (REST)");
        log.info("[google-sheets] {}", msg);
        return ConnectorResult.ok(summary, msg, getName());
    }

    private Map<String, Object> toRowMap(Item item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(colName, item.getName());
        m.put(colCategory, item.getCategory());
        m.put(colPrice, item.getPrice());
        m.put(colUnit, item.getUnit());
        m.put(colCost, item.getCost());
        m.put(colQuantity, item.getQuantity());
        m.put(colNotes, item.getNotes() == null ? "" : item.getNotes());
        return m;
    }

    private Item mapFromRowMap(Map<String, Object> row) {
        return new Item(
                strMap(row, colName, "Unknown"),
                strMap(row, colCategory, "Other"),
                parseDouble(String.valueOf(row.getOrDefault(colPrice, "0")), 0.0),
                strMap(row, colUnit, "Per Unit"),
                parseDouble(String.valueOf(row.getOrDefault(colCost, "0")), 0.0),
                parseInt(String.valueOf(row.getOrDefault(colQuantity, "0")), 0),
                strMap(row, colNotes, "")
        );
    }

    private static String strMap(Map<String, Object> row, String key, String def) {
        Object v = row.get(key);
        if (v == null) {
            // case-insensitive fallback
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) {
                    v = e.getValue();
                    break;
                }
            }
        }
        if (v == null) return def;
        String s = v.toString().trim();
        return s.isEmpty() ? def : s;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    @Override
    public Item mapToItem(List<Object> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("Empty sheet row");
        }
        // Positional fallback: Name, Category, Price, Unit, Cost, Quantity, Notes
        String name = cell(raw, 0, "Unknown");
        String category = cell(raw, 1, "Other");
        double price = parseDouble(cell(raw, 2, "0"), 0.0);
        String unit = cell(raw, 3, "Per Unit");
        double cost = parseDouble(cell(raw, 4, "0"), 0.0);
        int qty = parseInt(cell(raw, 5, "0"), 0);
        String notes = cell(raw, 6, "");
        return new Item(name, category, price, unit, cost, qty, notes);
    }

    private Item mapToItem(List<Object> row, Map<String, Integer> idx) {
        return new Item(
                col(row, idx, colName, "Unknown"),
                col(row, idx, colCategory, "Other"),
                parseDouble(col(row, idx, colPrice, "0"), 0.0),
                col(row, idx, colUnit, "Per Unit"),
                parseDouble(col(row, idx, colCost, "0"), 0.0),
                parseInt(col(row, idx, colQuantity, "0"), 0),
                col(row, idx, colNotes, "")
        );
    }

    @Override
    public List<Object> mapFromItem(Item item) {
        Map<String, Object> fields = fieldMapper.toExternalMap(item);
        return new ArrayList<>(fields.values());
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String valuesUrl(String ignoredMethod) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(SHEETS_API + "/" + config.get("spreadsheet-id") + "/values/"
                        + encodeRange(sheetRange()));
        if (config.has("api-key") && !config.has("access-token")) {
            b.queryParam("key", config.get("api-key"));
        }
        return b.toUriString();
    }

    private String sheetRange() {
        // Full used columns A–G on the named tab
        return config.get("sheet-name") + "!A:G";
    }

    private static String encodeRange(String range) {
        return URLEncoder.encode(range, StandardCharsets.UTF_8);
    }

    private HttpHeaders authHeaders(boolean write) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (write || config.has("access-token")) {
            headers.setBearerAuth(config.get("access-token"));
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return headers;
    }

    private Map<String, Integer> buildHeaderIndex(List<Object> headerRow) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            idx.put(String.valueOf(headerRow.get(i)).trim(), i);
        }
        return idx;
    }

    private String col(List<Object> row, Map<String, Integer> idx, String col, String def) {
        Integer i = idx.get(col);
        if (i == null || i >= row.size()) return def;
        return cell(row, i, def);
    }

    private static String cell(List<Object> row, int i, String def) {
        if (i < 0 || i >= row.size() || row.get(i) == null) return def;
        String s = row.get(i).toString().trim();
        return s.isEmpty() ? def : s;
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.replaceAll("[^\\d.\\-]", ""));
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return (int) Math.round(Double.parseDouble(s.replaceAll("[^\\d.\\-]", "")));
        } catch (Exception e) {
            return def;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
