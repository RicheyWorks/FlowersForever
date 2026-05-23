package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * GiftLogic retail POS connector stub.
 * Configure credentials in application.properties.
 * Add full REST logic in importItems() / exportItems() once credentials are set.
 */
@Component
public class GiftLogicConnector implements ExternalConnector<Map<String, Object>> {

    private final ConnectorConfig config;

    public GiftLogicConnector(
            @Value("${connector.giftlogic.api-key:}") String api_key,
            @Value("${connector.giftlogic.location-id:}") String location_id
    ) {
        this.config = new ConnectorConfig("giftlogic")
                .set("api-key", api_key)
                .set("location-id", location_id);
    }

    @Override public String getName()        { return "giftlogic"; }
    @Override public String getDescription() { return "GiftLogic retail POS"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.EXPORT_ONLY; }

    @Override
    public boolean isAvailable() {
        return config.hasAll("api-key", "location-id");
    }

    @Override
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("giftlogic import not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("giftlogic export not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) {
            return ConnectorResult.fail("giftlogic sync failed.", r.getErrorDetail(), getName());
        }
        int count = r.getPayload() != null ? r.getPayload() : 0;
        return ConnectorResult.ok(new SyncSummary(count, 0, 0, 0, 0), "giftlogic sync complete.", getName());
    }

    @Override public Item mapToItem(Map<String, Object> raw) { return null; }
    @Override public Map<String, Object> mapFromItem(Item item) { return Map.of(); }
}
