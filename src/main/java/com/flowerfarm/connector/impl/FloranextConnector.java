package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Floranext florist POS connector stub.
 * Configure credentials in application.properties.
 * Add full REST logic in importItems() / exportItems() once credentials are set.
 */
@Component
public class FloranextConnector implements ExternalConnector<Map<String, Object>> {

    private final ConnectorConfig config;

    public FloranextConnector(
            @Value("${connector.floranext.api-key:}") String api_key,
            @Value("${connector.floranext.store-url:}") String store_url
    ) {
        this.config = new ConnectorConfig("floranext")
                .set("api-key", api_key)
                .set("store-url", store_url);
    }

    @Override public String getName()        { return "floranext"; }
    @Override public String getDescription() { return "Floranext florist POS"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.EXPORT_ONLY; }

    @Override
    public boolean isAvailable() {
        return config.hasAll("api-key", "store-url");
    }

    @Override
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("floranext import not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("floranext export not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) {
            return ConnectorResult.fail("floranext sync failed.", r.getErrorDetail(), getName());
        }
        int count = r.getPayload() != null ? r.getPayload() : 0;
        return ConnectorResult.ok(new SyncSummary(count, 0, 0, 0, 0), "floranext sync complete.", getName());
    }

    @Override public Item mapToItem(Map<String, Object> raw) { return null; }
    @Override public Map<String, Object> mapFromItem(Item item) { return Map.of(); }
}
