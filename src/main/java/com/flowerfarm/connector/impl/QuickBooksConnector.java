package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * QuickBooks Online accounting connector stub.
 * Configure credentials in application.properties.
 * Add full REST logic in importItems() / exportItems() once credentials are set.
 */
@Component
public class QuickBooksConnector implements ExternalConnector<Map<String, Object>> {

    private final ConnectorConfig config;

    public QuickBooksConnector(
            @Value("${connector.quickbooks.client-id:}") String client_id,
            @Value("${connector.quickbooks.client-secret:}") String client_secret
    ) {
        this.config = new ConnectorConfig("quickbooks")
                .set("client-id", client_id)
                .set("client-secret", client_secret);
    }

    @Override public String getName()        { return "quickbooks"; }
    @Override public String getDescription() { return "QuickBooks Online accounting"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return config.hasAll("client-id", "client-secret");
    }

    @Override
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("quickbooks import not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("quickbooks export not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) {
            return ConnectorResult.fail("quickbooks sync failed.", r.getErrorDetail(), getName());
        }
        int count = r.getPayload() != null ? r.getPayload() : 0;
        return ConnectorResult.ok(new SyncSummary(count, 0, 0, 0, 0), "quickbooks sync complete.", getName());
    }

    @Override public Item mapToItem(Map<String, Object> raw) { return null; }
    @Override public Map<String, Object> mapFromItem(Item item) { return Map.of(); }
}
