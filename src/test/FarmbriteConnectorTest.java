package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncDirection;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("FarmbriteConnector")
class FarmbriteConnectorTest {

    @Nested
    @DisplayName("local mirror mode")
    class LocalMode {

        @TempDir Path temp;
        FarmbriteConnector connector;
        Path mirror;

        @BeforeEach
        void setUp() {
            mirror = temp.resolve("farmbrite-mirror.json");
            connector = new FarmbriteConnector("", "", "https://example.com/v1",
                    mirror.toString(), new RestTemplate());
        }

        @Test
        @DisplayName("available with local-file only")
        void available() {
            assertThat(connector.isAvailable()).isTrue();
            assertThat(connector.isLocalMode()).isTrue();
            assertThat(connector.getSupportedDirection()).isEqualTo(SyncDirection.BIDIRECTIONAL);
        }

        @Test
        @DisplayName("full round-trip: export → import → sync")
        void roundTrip() {
            List<Item> local = List.of(
                    new Item("Nootka Rose", "Flowers/Plants", 2.5, "stems", 1.0, 100, "PNW"),
                    new Item("Compost", "Supplies", 15.0, "bag", 8.0, 12, "")
            );

            ConnectorResult<Integer> exported = connector.exportItems(local);
            assertThat(exported.isSuccess()).isTrue();
            assertThat(exported.getPayload()).isEqualTo(2);
            assertThat(mirror).exists();

            ConnectorResult<List<Item>> imported = connector.importItems();
            assertThat(imported.isSuccess()).isTrue();
            assertThat(imported.getPayload()).hasSize(2);
            assertThat(imported.getPayload()).extracting(Item::getName)
                    .containsExactlyInAnyOrder("Nootka Rose", "Compost");

            // Local change: higher qty on Nootka Rose
            List<Item> changed = List.of(
                    new Item("Nootka Rose", "Flowers/Plants", 2.5, "stems", 1.0, 150, "PNW"),
                    new Item("Compost", "Supplies", 15.0, "bag", 8.0, 12, "")
            );
            ConnectorResult<SyncSummary> sync = connector.syncUpdates(changed);
            assertThat(sync.isSuccess()).isTrue();
            assertThat(sync.getPayload().updated() + sync.getPayload().skipped() + sync.getPayload().created())
                    .isEqualTo(2);

            ConnectorResult<List<Item>> again = connector.importItems();
            assertThat(again.getPayload().stream()
                    .filter(i -> i.getName().equals("Nootka Rose"))
                    .findFirst().orElseThrow().getQuantity()).isEqualTo(150);
        }

        @Test
        @DisplayName("import empty when mirror missing")
        void importMissingFile() {
            ConnectorResult<List<Item>> result = connector.importItems();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).isEmpty();
        }
    }

    @Nested
    @DisplayName("remote REST mode")
    class RemoteMode {

        RestTemplate restTemplate;
        FarmbriteConnector connector;

        @BeforeEach
        void setUp() {
            restTemplate = mock(RestTemplate.class);
            connector = new FarmbriteConnector("key", "acct-1", "https://api.example.com/v1",
                    "", restTemplate);
        }

        @Test
        @DisplayName("not available without credentials")
        void unavailable() {
            assertThat(new FarmbriteConnector("", "", "https://x", "", restTemplate).isAvailable()).isFalse();
        }

        @Test
        @DisplayName("import parses data array")
        @SuppressWarnings("unchecked")
        void importRemote() {
            when(restTemplate.exchange(contains("/inventory"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("data", List.of(
                            Map.of("name", "Rose A", "quantity_on_hand", 12, "unit_price", 2.0)
                    )), HttpStatus.OK));

            ConnectorResult<List<Item>> result = connector.importItems();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).hasSize(1);
        }

        @Test
        @DisplayName("mapToItem / mapFromItem")
        void mapping() {
            Item item = connector.mapToItem(Map.of(
                    "name", "Nootka Rose",
                    "category", "Flowers/Plants",
                    "unit_price", 3.5,
                    "quantity_on_hand", 50,
                    "unit", "stems"
            ));
            assertThat(item.getQuantity()).isEqualTo(50);
            assertThat(connector.mapFromItem(item).get("source")).isEqualTo("FlowersForever");
        }
    }
}
