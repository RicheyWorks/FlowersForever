package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncDirection;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("SquareConnector")
class SquareConnectorTest {

    private RestTemplate restTemplate;
    private SquareConnector connector;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        connector = new SquareConnector(
                "sandbox-token", "LOC_123", "sandbox", "USD", restTemplate);
    }

    @Test
    @DisplayName("isAvailable with access-token only")
    void availableWithToken() {
        assertThat(connector.isAvailable()).isTrue();
        assertThat(new SquareConnector("", "LOC", "sandbox", "USD", restTemplate).isAvailable()).isFalse();
    }

    @Test
    @DisplayName("local mirror dual-mode export/import/sync")
    void localMirrorRoundTrip() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("square-mirror", ".json");
        SquareConnector local = new SquareConnector("", "", "sandbox", "USD", tmp.toString(), restTemplate);
        assertThat(local.isAvailable()).isTrue();
        assertThat(local.isLocalMode()).isTrue();
        assertThat(local.getDescription()).containsIgnoringCase("local");

        List<Item> items = List.of(
                new Item("Market Bouquet", "Bouquets", 18.0, "each", 8.0, 12, "sat market")
        );
        assertThat(local.exportItems(items).isSuccess()).isTrue();
        ConnectorResult<List<Item>> imported = local.importItems();
        assertThat(imported.isSuccess()).isTrue();
        assertThat(imported.getPayload()).hasSize(1);
        assertThat(imported.getPayload().get(0).getName()).isEqualTo("Market Bouquet");
        assertThat(imported.getPayload().get(0).getQuantity()).isEqualTo(12);

        ConnectorResult<SyncSummary> sync = local.syncUpdates(items);
        assertThat(sync.isSuccess()).isTrue();
        assertThat(sync.getMessage()).containsIgnoringCase("local");
        java.nio.file.Files.deleteIfExists(tmp);
    }

    @Test
    @DisplayName("metadata")
    void metadata() {
        assertThat(connector.getName()).isEqualTo("square");
        assertThat(connector.getSupportedDirection()).isEqualTo(SyncDirection.BIDIRECTIONAL);
        assertThat(connector.getDescription()).containsIgnoringCase("Square");
    }

    @Nested
    @DisplayName("mapToItem / mapFromItem")
    class Mapping {

        @Test
        @DisplayName("mapToItem from Catalog ITEM + inventory quantity")
        void mapToItem() {
            Map<String, Object> product = sampleCatalogItem("Nootka Rose", "Flowers/Plants", 350L, "VAR1");
            product.put("_quantity", 50);

            Item item = connector.mapToItem(product);

            assertThat(item).isNotNull();
            assertThat(item.getName()).isEqualTo("Nootka Rose");
            assertThat(item.getPrice()).isEqualTo(3.50);
            assertThat(item.getQuantity()).isEqualTo(50);
            assertThat(item.getUnit()).isEqualTo("Per Stem");
            assertThat(item.getNotes()).contains("PNW");
        }

        @Test
        @DisplayName("mapFromItem builds ITEM + ITEM_VARIATION with cents")
        void mapFromItem() {
            Item item = new Item("Damask Rose", "Flowers/Plants", 4.25, "Per Stem", 2.00, 12, "Fragrant");
            Map<String, Object> obj = connector.mapFromItem(item);

            assertThat(obj.get("type")).isEqualTo("ITEM");
            @SuppressWarnings("unchecked")
            Map<String, Object> itemData = (Map<String, Object>) obj.get("item_data");
            assertThat(itemData.get("name")).isEqualTo("Damask Rose");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> variations = (List<Map<String, Object>>) itemData.get("variations");
            @SuppressWarnings("unchecked")
            Map<String, Object> varData = (Map<String, Object>) variations.get(0).get("item_variation_data");
            @SuppressWarnings("unchecked")
            Map<String, Object> money = (Map<String, Object>) varData.get("price_money");
            assertThat(money.get("amount")).isEqualTo(425L);
            assertThat(money.get("currency")).isEqualTo("USD");
            assertThat(varData.get("track_inventory")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("importItems")
    class Import {

        @Test
        @DisplayName("returns unavailable without token")
        void unavailable() {
            SquareConnector empty = new SquareConnector("", "", "sandbox", "USD", restTemplate);
            assertThat(empty.importItems().isSuccess()).isFalse();
        }

        @Test
        @DisplayName("lists catalog items and attaches inventory counts")
        @SuppressWarnings("unchecked")
        void importWithInventory() {
            Map<String, Object> catalogItem = sampleCatalogItem("Rose A", "Flowers/Plants", 200L, "VAR_A");
            when(restTemplate.exchange(contains("/v2/catalog/list"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("objects", List.of(catalogItem)), HttpStatus.OK));

            when(restTemplate.exchange(contains("/v2/inventory/counts/batch-retrieve"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of(
                            "counts", List.of(Map.of(
                                    "catalog_object_id", "VAR_A",
                                    "quantity", "17",
                                    "state", "IN_STOCK"
                            ))
                    ), HttpStatus.OK));

            ConnectorResult<List<Item>> result = connector.importItems();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).hasSize(1);
            assertThat(result.getPayload().get(0).getName()).isEqualTo("Rose A");
            assertThat(result.getPayload().get(0).getQuantity()).isEqualTo(17);
            assertThat(result.getPayload().get(0).getPrice()).isEqualTo(2.00);
        }
    }

    @Nested
    @DisplayName("exportItems")
    class Export {

        @Test
        @DisplayName("upserts catalog object and sets inventory count")
        @SuppressWarnings("unchecked")
        void exportCreatesAndSetsQty() {
            Map<String, Object> created = sampleCatalogItem("New Rose", "Flowers/Plants", 300L, "VAR_NEW");
            created.put("id", "ITEM_NEW");
            when(restTemplate.exchange(contains("/v2/catalog/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("catalog_object", created), HttpStatus.OK));
            when(restTemplate.exchange(contains("/v2/inventory/changes/batch-create"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("counts", List.of()), HttpStatus.OK));

            ConnectorResult<Integer> result = connector.exportItems(List.of(
                    new Item("New Rose", "Flowers/Plants", 3.00, "Per Stem", 1.50, 8, "")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).isEqualTo(1);
            verify(restTemplate).exchange(contains("/v2/catalog/object"), eq(HttpMethod.POST), any(), eq(Map.class));
            verify(restTemplate).exchange(contains("/v2/inventory/changes/batch-create"), eq(HttpMethod.POST), any(), eq(Map.class));
        }
    }

    @Nested
    @DisplayName("syncUpdates")
    class Sync {

        @Test
        @DisplayName("creates missing items and skips identical ones")
        @SuppressWarnings("unchecked")
        void createAndSkip() {
            Map<String, Object> existing = sampleCatalogItem("Existing Rose", "Flowers/Plants", 300L, "VAR_E");
            existing.put("id", "ITEM_E");
            existing.put("_quantity", 10);

            when(restTemplate.exchange(contains("/v2/catalog/list"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("objects", List.of(existing)), HttpStatus.OK));
            when(restTemplate.exchange(contains("/v2/inventory/counts/batch-retrieve"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of(
                            "counts", List.of(Map.of("catalog_object_id", "VAR_E", "quantity", "10"))
                    ), HttpStatus.OK));
            when(restTemplate.exchange(contains("/v2/catalog/object"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of(
                            "catalog_object", sampleCatalogItem("Brand New", "Flowers/Plants", 400L, "VAR_N")
                    ), HttpStatus.OK));
            when(restTemplate.exchange(contains("/v2/inventory/changes/batch-create"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of(), HttpStatus.OK));

            List<Item> local = List.of(
                    new Item("Existing Rose", "Other", 3.00, "Per Stem", 1.0, 10, "PNW native rose"),
                    new Item("Brand New", "Flowers/Plants", 4.00, "Per Stem", 2.0, 5, "new")
            );

            ConnectorResult<SyncSummary> result = connector.syncUpdates(local);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload().created()).isEqualTo(1);
            assertThat(result.getPayload().skipped()).isEqualTo(1);
        }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static Map<String, Object> sampleCatalogItem(String name, String category, long priceCents, String variationId) {
        Map<String, Object> priceMoney = Map.of("amount", priceCents, "currency", "USD");
        Map<String, Object> varData = new java.util.LinkedHashMap<>();
        varData.put("name", "Regular");
        varData.put("pricing_type", "FIXED_PRICING");
        varData.put("price_money", priceMoney);
        varData.put("sku", "Per Stem");
        varData.put("track_inventory", true);

        Map<String, Object> variation = new java.util.LinkedHashMap<>();
        variation.put("type", "ITEM_VARIATION");
        variation.put("id", variationId);
        variation.put("item_variation_data", varData);

        Map<String, Object> itemData = new java.util.LinkedHashMap<>();
        itemData.put("name", name);
        itemData.put("description", "PNW native rose");
        itemData.put("category_name", category);
        itemData.put("variations", List.of(variation));

        Map<String, Object> obj = new java.util.LinkedHashMap<>();
        obj.put("type", "ITEM");
        obj.put("id", "ITEM_" + variationId);
        obj.put("item_data", itemData);
        return obj;
    }
}
