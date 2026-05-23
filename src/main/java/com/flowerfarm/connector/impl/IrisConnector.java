package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Iris Works studio management connector stub.
 * Configure credentials in application.properties.
 * Add full REST logic in importItems() / exportItems() once credentials are set.
 */
@Component
public class IrisConnector implements ExternalConnector<Map<String, Object>> {

    private final ConnectorConfig config;

    public IrisConnector(
            @Value("${connector.iris.api-key:}") String api_key,
            @Value("${connector.iris.studio-id:}") String studio_id
    ) {
        this.config = new ConnectorConfig("iris")
                .set("api-key", api_key)
                .set("studio-id", studio_id);
    }

    @Override public String getName()        { return "iris"; }
    @Override public String getDescription() { return "Iris Works studio management"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.EXPORT_ONLY; }

    @Override
    public boolean isAvailable() {
        return config.hasAll("api-key", "studio-id");
    }

    @Override
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("iris import not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("iris export not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) {
            return ConnectorResult.fail("iris sync failed.", r.getErrorDetail(), getName());
        }
        int count = r.getPayload() != null ? r.getPayload() : 0;
        return ConnectorResult.ok(new SyncSummary(count, 0, 0, 0, 0), "iris sync complete.", getName());
    }

    @Override public Item mapToItem(Map<String, Object> raw) { return null; }
    @Override public Map<String, Object> mapFromItem(Item item) { return Map.of(); }
}
