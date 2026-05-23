package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Squarespace website commerce connector stub.
 * Configure credentials in application.properties.
 * Add full REST logic in importItems() / exportItems() once credentials are set.
 */
@Component
public class SquarespaceConnector implements ExternalConnector<Map<String, Object>> {

    private final ConnectorConfig config;

    public SquarespaceConnector(
            @Value("${connector.squarespace.url:}") String url,
            @Value("${connector.squarespace.secret:}") String secret
    ) {
        this.config = new ConnectorConfig("squarespace")
                .set("url", url)
                .set("secret", secret);
    }

    @Override public String getName()        { return "squarespace"; }
    @Override public String getDescription() { return "Squarespace website commerce"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.EXPORT_ONLY; }

    @Override
    public boolean isAvailable() {
        return config.hasAll("url", "secret");
    }

    @Override
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("squarespace import not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("squarespace export not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) {
            return ConnectorResult.fail("squarespace sync failed.", r.getErrorDetail(), getName());
        }
        int count = r.getPayload() != null ? r.getPayload() : 0;
        return ConnectorResult.ok(new SyncSummary(count, 0, 0, 0, 0), "squarespace sync complete.", getName());
    }

    @Override public Item mapToItem(Map<String, Object> raw) { return null; }
    @Override public Map<String, Object> mapFromItem(Item item) { return Map.of(); }
}
