package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Google Sheets spreadsheet connector stub.
 * Configure credentials in application.properties.
 * Add full REST logic in importItems() / exportItems() once credentials are set.
 */
@Component
public class GoogleSheetsConnector implements ExternalConnector<Map<String, Object>> {

    private final ConnectorConfig config;

    public GoogleSheetsConnector(
            @Value("${connector.google-sheets.spreadsheet-id:}") String spreadsheet_id,
            @Value("${connector.google-sheets.api-key:}") String api_key
    ) {
        this.config = new ConnectorConfig("google-sheets")
                .set("spreadsheet-id", spreadsheet_id)
                .set("api-key", api_key);
    }

    @Override public String getName()        { return "google-sheets"; }
    @Override public String getDescription() { return "Google Sheets spreadsheet"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return config.hasAll("spreadsheet-id", "api-key");
    }

    @Override
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("google-sheets import not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("google-sheets export not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) {
            return ConnectorResult.fail("google-sheets sync failed.", r.getErrorDetail(), getName());
        }
        int count = r.getPayload() != null ? r.getPayload() : 0;
        return ConnectorResult.ok(new SyncSummary(count, 0, 0, 0, 0), "google-sheets sync complete.", getName());
    }

    @Override public Item mapToItem(Map<String, Object> raw) { return null; }
    @Override public Map<String, Object> mapFromItem(Item item) { return Map.of(); }
}
