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
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Airtable connector — dual mode:
 * <ul>
 *   <li><b>Local mirror</b> — JSON field maps for offline demos.</li>
 *   <li><b>Remote REST</b> — Airtable Web API with personal access token.</li>
 * </ul>
 */
@Component
public class AirtableConnector implements ExternalConnector<Map<String, Object>>, DualModeCapable {

    private static final Logger log = LoggerFactory.getLogger(AirtableConnector.class);
    private static final String AIRTABLE_API_BASE = "https://api.airtable.com/v0";

    private final ConnectorConfig config;
    private final FieldMapper fieldMapper;
    private final RestTemplate restTemplate;

    public AirtableConnector(String apiToken, String baseId, String tableName) {
        this(apiToken, baseId, tableName, "", ConnectorHttpClient.create());
    }

    @Autowired
    public AirtableConnector(
            @Value("${connector.airtable.api-key:}") String apiToken,
            @Value("${connector.airtable.base-id:}") String baseId,
            @Value("${connector.airtable.table-name:Table 1}") String tableName,
            @Value("${connector.airtable.local-file:}") String localFile) {
        this(apiToken, baseId, tableName, localFile, ConnectorHttpClient.create());
    }

    AirtableConnector(String apiToken, String baseId, String tableName, String localFile,
                      RestTemplate restTemplate) {
        this.config = new ConnectorConfig("airtable")
                .set("api-token", nullToEmpty(apiToken))
                .set("base-id", nullToEmpty(baseId))
                .set("table-name", (tableName == null || tableName.isBlank()) ? "Table 1" : tableName.trim())
                .set("local-file", nullToEmpty(localFile));

        this.restTemplate = restTemplate;

        this.fieldMapper = new FieldMapper()
                .registerOutbound("Name", Item::getName)
                .registerOutbound("Category", Item::getCategory)
                .registerOutbound("Price", Item::getPrice)
                .registerOutbound("Unit", Item::getUnit)
                .registerOutbound("Cost", Item::getCost)
                .registerOutbound("Quantity", Item::getQuantity)
                .registerOutbound("Notes", Item::getNotes)
                .registerInbound("name", raw -> raw.getOrDefault("Name", "Unknown Item"))
                .registerInbound("category", raw -> raw.getOrDefault("Category", "Other"))
                .registerInbound("price", raw -> raw.getOrDefault("Price", "0"))
                .registerInbound("unit", raw -> raw.getOrDefault("Unit", "Per Unit"))
                .registerInbound("cost", raw -> raw.getOrDefault("Cost", "0"))
                .registerInbound("quantity", raw -> raw.getOrDefault("Quantity", "0"))
                .registerInbound("notes", raw -> raw.getOrDefault("Notes", ""));
    }

    @Override
    public String getName() {
        return "airtable";
    }

    @Override
    public String getDescription() {
        return isLocalMode()
                ? "Airtable (local JSON mirror) — offline table import/export/sync"
                : "Airtable connector — inventory table import/export using Airtable Web API";
    }

    @Override
    public SyncDirection getSupportedDirection() {
        return SyncDirection.BIDIRECTIONAL;
    }

    @Override
    public boolean isAvailable() {
        return isLocalMode() || config.hasAll("api-token", "base-id", "table-name");
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
        try {
            LocalJsonMirror mirror = new LocalJsonMirror(config.get("local-file"), "airtable");
            List<Map<String, Object>> rows = mirror.readRows();
            List<Item> items = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Item item = mapToItem(row);
                if (item != null) {
                    items.add(item);
                }
            }
            String msg = "Airtable local import — " + items.size()
                    + " item(s) from " + mirror.path().getFileName() + ".";
            log.info("[airtable] {}", msg);
            return ConnectorResult.ok(items, msg, getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Airtable local import failed.", e, getName());
        }
    }

    private ConnectorResult<List<Item>> importFromRemote() {
        ConnectorResult<List<Map<String, Object>>> fetched = fetchRemoteRecords();
        if (!fetched.isSuccess()) {
            return ConnectorResult.fail(
                    "Airtable import failed.",
                    fetched.getErrorDetail(),
                    getName());
        }

        List<Item> items = new ArrayList<>();
        for (Map<String, Object> record : fetched.getPayload()) {
            Map<String, Object> fields = recordFields(record);
            Item item = mapToItem(fields);
            if (item != null) {
                items.add(item);
            }
        }

        String msg = "Airtable REST import successful. Imported " + items.size() + " items.";
        log.info("[airtable] {}", msg);
        return ConnectorResult.ok(items, msg, getName());
    }

    @SuppressWarnings("unchecked")
    private ConnectorResult<List<Map<String, Object>>> fetchRemoteRecords() {
        List<Map<String, Object>> allRecords = new ArrayList<>();

        try {
            String offset = null;

            do {
                String url = recordsUrl();

                if (offset != null && !offset.isBlank()) {
                    url += "?offset=" + encode(offset);
                }

                ResponseEntity<Map> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers()),
                        Map.class
                );

                Map<String, Object> body = response.getBody();
                if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                    return ConnectorResult.fail(
                            "Airtable import failed.",
                            "HTTP status: " + response.getStatusCode(),
                            getName()
                    );
                }

                Object recordsObject = body.get("records");
                if (recordsObject instanceof List<?> records) {
                    for (Object rawRecord : records) {
                        if (rawRecord instanceof Map<?, ?> record) {
                            allRecords.add(stringKeyMap(record));
                        }
                    }
                }

                Object nextOffset = body.get("offset");
                offset = nextOffset == null ? null : nextOffset.toString();

            } while (offset != null && !offset.isBlank());

            return ConnectorResult.ok(allRecords,
                    "Fetched " + allRecords.size() + " Airtable record(s).",
                    getName());

        } catch (Exception e) {
            return ConnectorResult.fail("Airtable fetch failed.", e, getName());
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
        try {
            LocalJsonMirror mirror = new LocalJsonMirror(config.get("local-file"), "airtable");
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Item item : items) {
                rows.add(mapFromItem(item));
            }
            mirror.writeRows(rows);
            String msg = "Airtable local export — wrote " + items.size()
                    + " item(s) to " + mirror.path().getFileName() + ".";
            log.info("[airtable] {}", msg);
            return ConnectorResult.ok(items.size(), msg, getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Airtable local export failed.", e, getName());
        }
    }

    private ConnectorResult<Integer> exportToRemote(List<Item> items) {
        int exported = 0;

        try {
            List<Map<String, Object>> batch = new ArrayList<>();

            for (Item item : items) {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("fields", mapFromItem(item));
                batch.add(record);

                if (batch.size() == 10) {
                    exported += sendCreateBatch(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                exported += sendCreateBatch(batch);
            }

            String msg = "Airtable REST export successful. Exported " + exported + " items.";
            log.info("[airtable] {}", msg);
            return ConnectorResult.ok(exported, msg, getName());

        } catch (Exception e) {
            return ConnectorResult.fail("Airtable export failed.", e, getName());
        }
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        if (localItems == null) {
            localItems = List.of();
        }
        if (isLocalMode()) {
            ConnectorResult<List<Item>> remote = importItems();
            Map<String, Item> byName = new LinkedHashMap<>();
            if (remote.isSuccess() && remote.getPayload() != null) {
                remote.getPayload().forEach(i -> byName.put(i.getName().toLowerCase(Locale.ROOT), i));
            }
            int created = 0, updated = 0, skipped = 0;
            for (Item local : localItems) {
                Item r = byName.get(local.getName().toLowerCase(Locale.ROOT));
                if (r == null) {
                    created++;
                } else if (!sameItem(r, local)) {
                    updated++;
                } else {
                    skipped++;
                }
            }
            ConnectorResult<Integer> export = exportItems(localItems);
            if (!export.isSuccess()) {
                return ConnectorResult.fail("Airtable sync failed.", export.getErrorDetail(), getName());
            }
            SyncSummary summary = new SyncSummary(created, updated, 0, skipped, 0);
            String msg = "Airtable sync complete. " + summary + " (local mirror)";
            log.info("[airtable] {}", msg);
            return ConnectorResult.ok(summary, msg, getName());
        }

        return syncRemote(localItems);
    }

    private ConnectorResult<SyncSummary> syncRemote(List<Item> localItems) {
        ConnectorResult<List<Map<String, Object>>> fetched = fetchRemoteRecords();
        if (!fetched.isSuccess()) {
            return ConnectorResult.fail(
                    "Airtable sync aborted because existing records could not be read.",
                    fetched.getErrorDetail(),
                    getName());
        }

        Map<String, Map<String, Object>> remoteByName = new LinkedHashMap<>();
        for (Map<String, Object> record : fetched.getPayload()) {
            Item remoteItem = mapToItem(recordFields(record));
            if (remoteItem != null) {
                remoteByName.putIfAbsent(remoteItem.getName().toLowerCase(Locale.ROOT), record);
            }
        }

        List<Map<String, Object>> creates = new ArrayList<>();
        List<Map<String, Object>> updates = new ArrayList<>();
        int skipped = 0;

        for (Item local : localItems) {
            Map<String, Object> remoteRecord = remoteByName.get(local.getName().toLowerCase(Locale.ROOT));
            if (remoteRecord == null) {
                creates.add(Map.of("fields", mapFromItem(local)));
                continue;
            }

            Item remoteItem = mapToItem(recordFields(remoteRecord));
            if (sameItem(local, remoteItem)) {
                skipped++;
                continue;
            }

            Object id = remoteRecord.get("id");
            if (id == null || id.toString().isBlank()) {
                return ConnectorResult.fail(
                        "Airtable sync aborted because a changed remote record has no id.",
                        "Record name: " + local.getName(),
                        getName());
            }
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("id", id.toString());
            update.put("fields", mapFromItem(local));
            updates.add(update);
        }

        try {
            int created = sendBatches(creates, HttpMethod.POST);
            int updated = sendBatches(updates, HttpMethod.PATCH);
            SyncSummary summary = new SyncSummary(created, updated, 0, skipped, 0);
            String msg = "Airtable sync complete. " + summary + " (REST)";
            log.info("[airtable] {}", msg);
            return ConnectorResult.ok(summary, msg, getName());
        } catch (Exception e) {
            return ConnectorResult.fail("Airtable sync failed.", e, getName());
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public Item mapToItem(Map<String, Object> raw) {
        return fieldMapper.buildItem(raw);
    }

    @Override
    public Map<String, Object> mapFromItem(Item item) {
        return fieldMapper.toExternalMap(item);
    }

    private int sendCreateBatch(List<Map<String, Object>> records) {
        return sendBatch(records, HttpMethod.POST);
    }

    private int sendBatches(List<Map<String, Object>> records, HttpMethod method) {
        int sent = 0;
        for (int start = 0; start < records.size(); start += 10) {
            int end = Math.min(start + 10, records.size());
            sent += sendBatch(new ArrayList<>(records.subList(start, end)), method);
        }
        return sent;
    }

    @SuppressWarnings("unchecked")
    private int sendBatch(List<Map<String, Object>> records, HttpMethod method) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("records", records);

        ResponseEntity<Map> response = restTemplate.exchange(
                recordsUrl(),
                method,
                new HttpEntity<>(payload, headers()),
                Map.class
        );

        Map<?, ?> respBody = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || respBody == null) {
            throw new IllegalStateException(
                    "Airtable " + method + " failed with HTTP " + response.getStatusCode());
        }

        Object created = respBody.get("records");

        if (created instanceof List<?> list) {
            return list.size();
        }

        throw new IllegalStateException("Airtable " + method + " response did not contain records.");
    }

    private Map<String, Object> recordFields(Map<String, Object> record) {
        Object fields = record.get("fields");
        return fields instanceof Map<?, ?> map ? stringKeyMap(map) : Map.of();
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private boolean sameItem(Item left, Item right) {
        return right != null
                && left.getName().equalsIgnoreCase(right.getName())
                && Objects.equals(left.getCategory(), right.getCategory())
                && Double.compare(left.getPrice(), right.getPrice()) == 0
                && Objects.equals(left.getUnit(), right.getUnit())
                && Double.compare(left.getCost(), right.getCost()) == 0
                && left.getQuantity() == right.getQuantity()
                && Objects.equals(left.getNotes(), right.getNotes());
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(config.get("api-token"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String recordsUrl() {
        return AIRTABLE_API_BASE + "/"
                + encode(config.get("base-id")) + "/"
                + encode(config.get("table-name"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
