package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.ConnectorConfig;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.ExternalConnector;
import com.flowerfarm.connector.FieldMapper;
import com.flowerfarm.connector.SyncDirection;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AirtableConnector implements ExternalConnector<Map<String, Object>> {

    private static final String AIRTABLE_API_BASE = "https://api.airtable.com/v0";

    private final ConnectorConfig config;
    private final FieldMapper fieldMapper;
    private final RestTemplate restTemplate;

    public AirtableConnector(String apiToken, String baseId, String tableName) {
        this.config = new ConnectorConfig("airtable")
                .set("api-token", apiToken)
                .set("base-id", baseId)
                .set("table-name", tableName);

        this.restTemplate = new RestTemplate();

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
        return "Airtable connector — inventory table import/export using Airtable Web API";
    }

    @Override
    public SyncDirection getSupportedDirection() {
        return SyncDirection.BIDIRECTIONAL;
    }

    @Override
    public boolean isAvailable() {
        return config.hasAll("api-token", "base-id", "table-name");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) {
            return ConnectorResult.unavailable(getName());
        }

        List<Item> items = new ArrayList<>();

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

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    return ConnectorResult.fail(
                            "Airtable import failed.",
                            "HTTP status: " + response.getStatusCode(),
                            getName()
                    );
                }

                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> records =
                        (List<Map<String, Object>>) body.getOrDefault("records", List.of());

                for (Map<String, Object> record : records) {
                    Object fieldsObj = record.get("fields");

                    if (fieldsObj instanceof Map<?, ?> fieldsMap) {
                        Map<String, Object> fields = new LinkedHashMap<>();

                        for (Map.Entry<?, ?> entry : fieldsMap.entrySet()) {
                            fields.put(String.valueOf(entry.getKey()), entry.getValue());
                        }

                        Item item = mapToItem(fields);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }

                Object nextOffset = body.get("offset");
                offset = nextOffset == null ? null : nextOffset.toString();

            } while (offset != null && !offset.isBlank());

            return ConnectorResult.ok(
                    items,
                    "Airtable import successful. Imported " + items.size() + " items.",
                    getName()
            );

        } catch (Exception e) {
            return ConnectorResult.fail("Airtable import failed.", e, getName());
        }
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) {
            return ConnectorResult.unavailable(getName());
        }

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

            return ConnectorResult.ok(
                    exported,
                    "Airtable export successful. Exported " + exported + " items.",
                    getName()
            );

        } catch (Exception e) {
            return ConnectorResult.fail("Airtable export failed.", e, getName());
        }
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> exportResult = exportItems(localItems);

        if (!exportResult.isSuccess()) {
            return ConnectorResult.fail(
                    "Airtable sync failed.",
                    exportResult.getErrorDetail(),
                    getName()
            );
        }

        int exported = exportResult.getPayload() == null ? 0 : exportResult.getPayload();

        SyncSummary summary = new SyncSummary(
                exported,
                0,
                0,
                0,
                0
        );

        return ConnectorResult.ok(
                summary,
                "Airtable sync complete. Exported " + exported + " items.",
                getName()
        );
    }

    @Override
    public Item mapToItem(Map<String, Object> raw) {
        return fieldMapper.buildItem(raw);
    }

    @Override
    public Map<String, Object> mapFromItem(Item item) {
        return fieldMapper.toExternalMap(item);
    }

    @SuppressWarnings("unchecked")
    private int sendCreateBatch(List<Map<String, Object>> records) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("records", records);

        ResponseEntity<Map> response = restTemplate.exchange(
                recordsUrl(),
                HttpMethod.POST,
                new HttpEntity<>(payload, headers()),
                Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return 0;
        }

        Object created = response.getBody().get("records");

        if (created instanceof List<?> list) {
            return list.size();
        }

        return 0;
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
