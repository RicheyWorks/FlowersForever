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

@DisplayName("FloranextConnector")
class FloranextConnectorTest {

    @Nested
    @DisplayName("local mirror mode")
    class LocalMode {

        @TempDir Path temp;
        FloranextConnector connector;
        Path mirror;

        @BeforeEach
        void setUp() {
            mirror = temp.resolve("floranext-mirror.json");
            connector = new FloranextConnector("", "", mirror.toString(), new RestTemplate());
        }

        @Test
        @DisplayName("available with local-file only")
        void available() {
            assertThat(connector.isAvailable()).isTrue();
            assertThat(connector.isLocalMode()).isTrue();
            assertThat(connector.getSupportedDirection()).isEqualTo(SyncDirection.BIDIRECTIONAL);
        }

        @Test
        @DisplayName("full round-trip export → import → sync")
        void roundTrip() {
            List<Item> local = List.of(
                    new Item("Bouquet Mix", "Arrangements", 45.0, "bunch", 20.0, 8, "market")
            );
            assertThat(connector.exportItems(local).isSuccess()).isTrue();
            assertThat(connector.importItems().getPayload()).hasSize(1);

            List<Item> changed = List.of(
                    new Item("Bouquet Mix", "Arrangements", 48.0, "bunch", 20.0, 6, "market"),
                    new Item("Corsage", "Arrangements", 12.0, "each", 5.0, 20, "")
            );
            ConnectorResult<SyncSummary> sync = connector.syncUpdates(changed);
            assertThat(sync.isSuccess()).isTrue();
            assertThat(sync.getPayload().created()).isEqualTo(1);

            assertThat(connector.importItems().getPayload()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("remote REST mode")
    class RemoteMode {

        RestTemplate restTemplate;
        FloranextConnector connector;

        @BeforeEach
        void setUp() {
            restTemplate = mock(RestTemplate.class);
            connector = new FloranextConnector("key", "https://shop.floranext.com", "", restTemplate);
        }

        @Test
        @DisplayName("import products array")
        @SuppressWarnings("unchecked")
        void importRemote() {
            when(restTemplate.exchange(contains("/api/products"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                    .thenReturn(new ResponseEntity<>(Map.of("products", List.of(
                            Map.of("name", "Peony", "price", 4.0, "stock", 20)
                    )), HttpStatus.OK));

            ConnectorResult<List<Item>> result = connector.importItems();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload().get(0).getName()).isEqualTo("Peony");
        }

        @Test
        @DisplayName("export POSTs products")
        @SuppressWarnings("unchecked")
        void exportRemote() {
            when(restTemplate.exchange(contains("/api/products"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(Map.of(), HttpStatus.CREATED));

            ConnectorResult<Integer> result = connector.exportItems(List.of(
                    new Item("Rose", "Flowers/Plants", 3.0, "stem", 1.0, 10, "")
            ));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).isEqualTo(1);
        }
    }
}
