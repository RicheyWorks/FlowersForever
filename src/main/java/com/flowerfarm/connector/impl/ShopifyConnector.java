package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Shopify e-commerce connector stub.
 * Configure credentials in application.properties.
 * Add full REST logic in importItems() / exportItems() once credentials are set.
 */
@Component
public class ShopifyConnector implements ExternalConnector<Map<String, Object>> {

    private final ConnectorConfig config;

    public ShopifyConnector(
            @Value("${connector.shopify.shop-name:}") String shop_name,
            @Value("${connector.shopify.api-token:}") String api_token
    ) {
        this.config = new ConnectorConfig("shopify")
                .set("shop-name", shop_name)
                .set("api-token", api_token);
    }

    @Override public String getName()        { return "shopify"; }
    @Override public String getDescription() { return "Shopify e-commerce"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return config.hasAll("shop-name", "api-token");
    }

    @Override
    public ConnectorResult<List<Item>> importItems() {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("shopify import not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (!isAvailable()) return ConnectorResult.unavailable(getName());
        return ConnectorResult.fail("shopify export not yet implemented.", "", getName());
    }

    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        ConnectorResult<Integer> r = exportItems(localItems);
        if (!r.isSuccess()) {
            return ConnectorResult.fail("shopify sync failed.", r.getErrorDetail(), getName());
        }
        int count = r.getPayload() != null ? r.getPayload() : 0;
        return ConnectorResult.ok(new SyncSummary(count, 0, 0, 0, 0), "shopify sync complete.", getName());
    }

    @Override public Item mapToItem(Map<String, Object> raw) { return null; }
    @Override public Map<String, Object> mapFromItem(Item item) { return Map.of(); }
}
