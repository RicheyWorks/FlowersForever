package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Square POS and e-commerce connector stub.
 * Configure credentials in application.properties.
 * Add full REST logic in importItems() / exportItems() once credentials are set.
 */
@Component
public class SquareConnector implements ExternalConnector<Map<String, Object>> {

    private final ConnectorConfig config;

    public SquareConnector(
            @Value("${connector.square.access-token:}") String access_token,
            @Value("${connector.square.location-id:}") String location_id
    ) {
        this.config = new ConnectorConfig("square")
                .set("access-token", access_token)
                .set("location-id", location_id);
    }

    @Override public String getName()        { return "square"; }
    @Override public String getDescription() { return "Square POS and e-commerce"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return config.hasAll("access-token", "location-id");
    }

    @Override
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("square import not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("square export not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) {
            return ConnectorResult.fail("square sync failed.", r.getErrorDetail(), getName());
        }
        int count = r.getPayload() != null ? r.getPayload() : 0;
        return ConnectorResult.ok(new SyncSummary(count, 0, 0, 0, 0), "square sync complete.", getName());
    }

    @Override public Item mapToItem(Map<String, Object> raw) { return null; }
    @Override public Map<String, Object> mapFromItem(Item item) { return Map.of(); }
}
