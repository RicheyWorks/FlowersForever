package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.ConnectorResult;
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

@DisplayName("GoogleSheetsConnector")
class GoogleSheetsConnectorTest {

    private RestTemplate restTemplate;
    private GoogleSheetsConnector connector;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        connector = new GoogleSheetsConnector(
                "sheet-id-123", "Inventory", "AIza_test_key", "",
                "Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes",
                restTemplate);
    }

    @Test
    @DisplayName("isAvailable with spreadsheet-id + api-key")
    void availableWithApiKey() {
        assertThat(connector.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("isAvailable with spreadsheet-id + access-token")
    void availableWithToken() {
        GoogleSheetsConnector withToken = new GoogleSheetsConnector(
                "sheet-id-123", "Inventory", "", "ya29.token",
                "Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes",
                restTemplate);
        assertThat(withToken.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("not available without credentials")
    void notAvailable() {
        GoogleSheetsConnector empty = new GoogleSheetsConnector(
                "", "Inventory", "", "",
                "Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes",
                restTemplate);
        assertThat(empty.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("local mirror dual-mode export/import/sync")
    void localMirrorRoundTrip() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("gsheets-mirror", ".json");
        GoogleSheetsConnector local = new GoogleSheetsConnector(
                "", "Inventory", "", "", tmp.toString(),
                "Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes",
                restTemplate);
        assertThat(local.isAvailable()).isTrue();
        assertThat(local.isLocalMode()).isTrue();
        assertThat(local.getDescription()).containsIgnoringCase("local");

        List<Item> items = List.of(
                new Item("Nootka Rose", "Flowers/Plants", 3.5, "Per Stem", 1.5, 40, "sheet demo")
        );
        assertThat(local.exportItems(items).isSuccess()).isTrue();
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

    @Nested
    @DisplayName("mapToItem / mapFromItem")
    class Mapping {

        @Test
        @DisplayName("positional mapToItem")
        void positionalMap() {
            Item item = connector.mapToItem(List.of(
                    "Nootka Rose", "Flowers/Plants", "3.50", "Per Stem", "2.00", "50", "PNW"));
            assertThat(item.getName()).isEqualTo("Nootka Rose");
            assertThat(item.getPrice()).isEqualTo(3.50);
            assertThat(item.getQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("mapFromItem preserves column order")
        void mapFromItemOrder() {
            Item item = new Item("Rose", "Flowers/Plants", 3.5, "Per Stem", 2.0, 10, "n");
            List<Object> row = connector.mapFromItem(item);
            assertThat(row).hasSize(7);
            assertThat(row.get(0)).isEqualTo("Rose");
            assertThat(row.get(2)).isEqualTo("3.50");
        }
    }

    @Nested
    @DisplayName("importItems")
    class Import {

        @Test
        @DisplayName("parses header + data rows from values.get")
        @SuppressWarnings("unchecked")
        void importsRows() {
            Map<String, Object> body = Map.of("values", List.of(
                    List.of("Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes"),
                    List.of("Nootka Rose", "Flowers/Plants", "3.50", "Per Stem", "2.00", "50", "native"),
                    List.of("Compost", "Supplies", "15.00", "Per Bag", "8.00", "12", "")
            ));
            when(restTemplate.exchange(contains("spreadsheets"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

            ConnectorResult<List<Item>> result = connector.importItems();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).hasSize(2);
            assertThat(result.getPayload().get(0).getName()).isEqualTo("Nootka Rose");
            assertThat(result.getPayload().get(1).getCategory()).isEqualTo("Supplies");
        }
    }

    @Nested
    @DisplayName("exportItems")
    class Export {

        @Test
        @DisplayName("fails with clear message when only API key is configured")
        void exportNeedsAccessToken() {
            ConnectorResult<Integer> result = connector.exportItems(List.of(
                    new Item("Rose", "Other", 1.0, "Per Unit", 0.5, 1, "")));
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).containsIgnoringCase("access token");
        }

        @Test
        @DisplayName("PUTs values when access token is set")
        @SuppressWarnings("unchecked")
        void exportWithToken() {
            GoogleSheetsConnector writer = new GoogleSheetsConnector(
                    "sheet-id-123", "Inventory", "", "ya29.token",
                    "Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes",
                    restTemplate);
            when(restTemplate.exchange(contains("spreadsheets"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("updatedRows", 2), HttpStatus.OK));

            ConnectorResult<Integer> result = writer.exportItems(List.of(
                    new Item("Rose", "Other", 1.0, "Per Unit", 0.5, 1, "")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).isEqualTo(1);
            verify(restTemplate).exchange(anyString(), eq(HttpMethod.PUT), any(), eq(Map.class));
        }
    }

    @Nested
    @DisplayName("syncUpdates")
    class Sync {

        @Test
        @DisplayName("rewrites sheet and reports create/update/skip counts")
        @SuppressWarnings("unchecked")
        void syncCounts() {
            GoogleSheetsConnector writer = new GoogleSheetsConnector(
                    "sheet-id-123", "Inventory", "", "ya29.token",
                    "Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes",
                    restTemplate);

            Map<String, Object> remote = Map.of("values", List.of(
                    List.of("Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes"),
                    List.of("Keep", "Other", "1.00", "Per Unit", "0.50", "5", "")
            ));
            when(restTemplate.exchange(contains("spreadsheets"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(remote, HttpStatus.OK));
            when(restTemplate.exchange(contains("spreadsheets"), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("updatedRows", 3), HttpStatus.OK));

            // Remote only has Keep — Changed + New are created; Keep is skipped
            List<Item> local = List.of(
                    new Item("Keep", "Other", 1.00, "Per Unit", 0.50, 5, ""),
                    new Item("Changed", "Other", 2.00, "Per Unit", 1.00, 9, ""),
                    new Item("New SKU", "Other", 2.00, "Per Unit", 1.00, 3, "")
            );

            ConnectorResult<SyncSummary> result = writer.syncUpdates(local);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload().created()).isEqualTo(2);
            assertThat(result.getPayload().skipped()).isEqualTo(1);
        }
    }
}
