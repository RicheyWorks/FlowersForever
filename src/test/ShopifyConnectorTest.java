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

@DisplayName("ShopifyConnector")
class ShopifyConnectorTest {

    private RestTemplate restTemplate;
    private ShopifyConnector connector;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        connector = new ShopifyConnector("demo-farm", "shpat_test_token", "2024-01", restTemplate);
    }

    @Test
    @DisplayName("isAvailable when shop-name and api-token are set")
    void availableWhenConfigured() {
        assertThat(connector.isAvailable()).isTrue();
        assertThat(new ShopifyConnector("", "", "2024-01", restTemplate).isAvailable()).isFalse();
    }

    @Test
    @DisplayName("getName / direction / description")
    void metadata() {
        assertThat(connector.getName()).isEqualTo("shopify");
        assertThat(connector.getSupportedDirection()).isEqualTo(SyncDirection.BIDIRECTIONAL);
        assertThat(connector.getDescription()).containsIgnoringCase("Shopify");
    }

    @Nested
    @DisplayName("mapToItem / mapFromItem")
    class Mapping {

        @Test
        @DisplayName("mapToItem flattens first variant into Item fields")
        void mapToItemFromProduct() {
            Map<String, Object> product = Map.of(
                    "title", "Nootka Rose",
                    "product_type", "Flowers/Plants",
                    "body_html", "<p>PNW native</p>",
                    "variants", List.of(Map.of(
                            "price", "3.50",
                            "inventory_quantity", 50
                    ))
            );

            Item item = connector.mapToItem(product);

            assertThat(item).isNotNull();
            assertThat(item.getName()).isEqualTo("Nootka Rose");
            assertThat(item.getCategory()).isEqualTo("Flowers/Plants");
            assertThat(item.getPrice()).isEqualTo(3.50);
            assertThat(item.getQuantity()).isEqualTo(50);
            assertThat(item.getNotes()).contains("PNW native");
        }

        @Test
        @DisplayName("mapFromItem builds Shopify product payload")
        void mapFromItemBuildsProduct() {
            Item item = new Item("Damask Rose", "Flowers/Plants", 4.00, "Per Stem", 2.00, 25, "Fragrant");
            Map<String, Object> product = connector.mapFromItem(item);

            assertThat(product.get("title")).isEqualTo("Damask Rose");
            assertThat(product.get("product_type")).isEqualTo("Flowers/Plants");
            assertThat(product.get("vendor")).isEqualTo("FlowersForever PNW");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> variants = (List<Map<String, Object>>) product.get("variants");
            assertThat(variants).hasSize(1);
            assertThat(variants.get(0).get("price")).isEqualTo("4.00");
            assertThat(variants.get(0).get("inventory_quantity")).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("local mirror dual-mode")
    class LocalMode {

        @Test
        @DisplayName("isAvailable with local-file only")
        void availableWithLocalFile() throws Exception {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("shopify-mirror", ".json");
            ShopifyConnector local = new ShopifyConnector("", "", "2024-01", tmp.toString(), restTemplate);
            assertThat(local.isAvailable()).isTrue();
            assertThat(local.isLocalMode()).isTrue();
            assertThat(local.getDescription()).containsIgnoringCase("local");
            java.nio.file.Files.deleteIfExists(tmp);
        }

        @Test
        @DisplayName("export then import round-trips products")
        void exportImportRoundTrip() throws Exception {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("shopify-mirror", ".json");
            ShopifyConnector local = new ShopifyConnector("", "", "2024-01", tmp.toString(), restTemplate);
            List<Item> items = List.of(
                    new Item("Nootka Rose", "Flowers/Plants", 3.5, "Per Stem", 1.0, 40, "local")
            );
            ConnectorResult<Integer> exported = local.exportItems(items);
            assertThat(exported.isSuccess()).isTrue();
            assertThat(exported.getPayload()).isEqualTo(1);

            ConnectorResult<List<Item>> imported = local.importItems();
            assertThat(imported.isSuccess()).isTrue();
            assertThat(imported.getPayload()).hasSize(1);
            assertThat(imported.getPayload().get(0).getName()).isEqualTo("Nootka Rose");
            assertThat(imported.getPayload().get(0).getQuantity()).isEqualTo(40);

            ConnectorResult<SyncSummary> sync = local.syncUpdates(items);
            assertThat(sync.isSuccess()).isTrue();
            assertThat(sync.getMessage()).containsIgnoringCase("local");
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }

    @Nested
    @DisplayName("importItems")
    class Import {

        @Test
        @DisplayName("returns unavailable without credentials")
        void unavailable() {
            ShopifyConnector empty = new ShopifyConnector("", "", "2024-01", restTemplate);
            ConnectorResult<List<Item>> result = empty.importItems();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("not available");
        }

        @Test
        @DisplayName("paginates products via Link header and maps items")
        @SuppressWarnings("unchecked")
        void paginatesAndMaps() {
            Map<String, Object> page1 = Map.of("products", List.of(
                    Map.of("title", "Rose A", "product_type", "Flowers/Plants",
                            "variants", List.of(Map.of("price", "2.00", "inventory_quantity", 10)))
            ));
            Map<String, Object> page2 = Map.of("products", List.of(
                    Map.of("title", "Rose B", "product_type", "Flowers/Plants",
                            "variants", List.of(Map.of("price", "3.00", "inventory_quantity", 5)))
            ));

            HttpHeaders h1 = new HttpHeaders();
            h1.set(HttpHeaders.LINK,
                    "<https://demo-farm.myshopify.com/admin/api/2024-01/products.json?page_info=abc&limit=50>; rel=\"next\"");

            when(restTemplate.exchange(contains("products.json"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(page1, h1, HttpStatus.OK))
                    .thenReturn(new ResponseEntity<>(page2, HttpStatus.OK));

            ConnectorResult<List<Item>> result = connector.importItems();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).hasSize(2);
            assertThat(result.getPayload()).extracting(Item::getName).containsExactly("Rose A", "Rose B");
            verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(), eq(Map.class));
        }
    }

    @Nested
    @DisplayName("exportItems")
    class Export {

        @Test
        @DisplayName("POSTs one product per item")
        @SuppressWarnings("unchecked")
        void postsEachProduct() {
            when(restTemplate.exchange(contains("products.json"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("product", Map.of("id", 1)), HttpStatus.CREATED));

            List<Item> items = List.of(
                    new Item("A", "Other", 1.0, "Per Unit", 0.5, 1, ""),
                    new Item("B", "Other", 2.0, "Per Unit", 1.0, 2, "")
            );

            ConnectorResult<Integer> result = connector.exportItems(items);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).isEqualTo(2);
            verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Map.class));
        }
    }

    @Nested
    @DisplayName("syncUpdates")
    class Sync {

        @Test
        @DisplayName("creates missing remote products and skips identical ones")
        @SuppressWarnings("unchecked")
        void createAndSkip() {
            Map<String, Object> existing = Map.of(
                    "id", 99L,
                    "title", "Existing Rose",
                    "product_type", "Flowers/Plants",
                    "body_html", "",
                    "variants", List.of(Map.of("id", 1L, "price", "3.00", "inventory_quantity", 10))
            );
            when(restTemplate.exchange(contains("products.json"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("products", List.of(existing)), HttpStatus.OK));
            when(restTemplate.exchange(contains("products.json"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("product", Map.of("id", 100)), HttpStatus.CREATED));

            List<Item> local = List.of(
                    new Item("Existing Rose", "Flowers/Plants", 3.00, "Per Stem", 1.0, 10, ""),
                    new Item("Brand New Rose", "Flowers/Plants", 4.00, "Per Stem", 2.0, 20, "new")
            );

            ConnectorResult<SyncSummary> result = connector.syncUpdates(local);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload().created()).isEqualTo(1);
            assertThat(result.getPayload().skipped()).isEqualTo(1);
            verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(), eq(Map.class));
        }
    }

    @Test
    @DisplayName("extractNextLink parses Shopify Link header")
    void extractNextLink() {
        String link = "<https://x.myshopify.com/admin/api/2024-01/products.json?page_info=n&limit=50>; rel=\"next\", "
                + "<https://x.myshopify.com/admin/api/2024-01/products.json?page_info=p&limit=50>; rel=\"previous\"";
        assertThat(ShopifyConnector.extractNextLink(link))
                .contains("page_info=n");
        assertThat(ShopifyConnector.extractNextLink(null)).isNull();
    }
}
