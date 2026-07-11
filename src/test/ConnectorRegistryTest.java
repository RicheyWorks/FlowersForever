package com.flowerfarm.connector;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.SyncHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConnectorRegistry")
class ConnectorRegistryTest {

    @Mock private InventoryService inventoryService;
    @Mock private SyncHistoryService syncHistoryService;

    // Two mock connectors with distinct names and directions
    private ExternalConnector<Object> importConnector;
    private ExternalConnector<Object> exportConnector;
    private ExternalConnector<Object> biConnector;

    private ConnectorRegistry registry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        importConnector = mock(ExternalConnector.class);
        when(importConnector.getName()).thenReturn("MockImporter");
        lenient().when(importConnector.getDescription()).thenReturn("Import-only mock");
        when(importConnector.getSupportedDirection()).thenReturn(SyncDirection.IMPORT_ONLY);

        exportConnector = mock(ExternalConnector.class);
        when(exportConnector.getName()).thenReturn("MockExporter");
        lenient().when(exportConnector.getDescription()).thenReturn("Export-only mock");
        when(exportConnector.getSupportedDirection()).thenReturn(SyncDirection.EXPORT_ONLY);

        biConnector = mock(ExternalConnector.class);
        when(biConnector.getName()).thenReturn("MockBidi");
        lenient().when(biConnector.getDescription()).thenReturn("Bidirectional mock");
        when(biConnector.getSupportedDirection()).thenReturn(SyncDirection.BIDIRECTIONAL);

        registry = new ConnectorRegistry(
                List.of(importConnector, exportConnector, biConnector),
                inventoryService,
                syncHistoryService
        );
        // History recording is best-effort; keep stubs quiet unless asserted
        lenient().doNothing().when(syncHistoryService).recordResult(anyString(), anyString(), any());
    }

    // ── Discovery ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("find() locates a connector by case-insensitive name")
    void findCaseInsensitive() {
        assertThat(registry.find("mockimporter")).isPresent();
        assertThat(registry.find("MOCKIMPORTER")).isPresent();
        assertThat(registry.find("MockImporter")).isPresent();
    }

    @Test
    @DisplayName("find() returns empty Optional for unknown connector")
    void findReturnsEmptyForUnknown() {
        assertThat(registry.find("does-not-exist")).isEmpty();
    }

    @Test
    @DisplayName("getConnectorNames() returns all registered names (lower-cased)")
    void getConnectorNamesReturnsAll() {
        assertThat(registry.getConnectorNames())
                .containsExactlyInAnyOrder("mockimporter", "mockexporter", "mockbidi");
    }

    @Test
    @DisplayName("listConnectorInfo() includes all expected metadata fields")
    void listConnectorInfoHasAllFields() {
        List<Map<String, Object>> info = registry.listConnectorInfo();

        assertThat(info).hasSize(3);
        for (Map<String, Object> entry : info) {
            assertThat(entry).containsKeys("name", "description", "direction",
                    "canImport", "canExport", "canSync", "available", "mode", "localMode");
        }
    }

    @Test
    @DisplayName("listConnectorInfo() reflects correct import/export/sync flags")
    void listConnectorInfoReflectsDirectionFlags() {
        Map<String, Object> importInfo = registry.listConnectorInfo().stream()
                .filter(m -> "MockImporter".equals(m.get("name")))
                .findFirst().orElseThrow();

        assertThat(importInfo.get("canImport")).isEqualTo(true);
        assertThat(importInfo.get("canExport")).isEqualTo(false);
        assertThat(importInfo.get("canSync")).isEqualTo(false);
    }

    // ── runImport ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("runImport()")
    class RunImportTests {

        @Test
        @DisplayName("succeeds and persists each imported item via InventoryService")
        void importsPersistsItems() {
            List<Item> incoming = List.of(
                    new Item("Rose A", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, ""),
                    new Item("Rose B", "Flowers/Plants", 2.50, "Per Stem", 1.50, 30, "")
            );
            ConnectorResult<List<Item>> ok = ConnectorResult.ok(incoming, "Imported 2");
            when(importConnector.importItems()).thenReturn(ok);

            ConnectorResult<List<Item>> result = registry.runImport("mockimporter");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).hasSize(2);
            verify(inventoryService, times(2)).addItem(any(Item.class));
        }

        @Test
        @DisplayName("returns failure for unknown connector name")
        void failsOnUnknownConnector() {
            ConnectorResult<List<Item>> result = registry.runImport("no-such-connector");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("not found");
            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("returns failure when connector does not support IMPORT")
        void failsOnExportOnlyConnector() {
            ConnectorResult<List<Item>> result = registry.runImport("mockexporter");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("does not support IMPORT");
            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("does not crash when connector returns null payload")
        void handlesNullPayload() {
            when(importConnector.importItems())
                    .thenReturn(ConnectorResult.ok(null, "Empty result"));

            ConnectorResult<List<Item>> result = registry.runImport("mockimporter");

            assertThat(result.isSuccess()).isTrue();
            verifyNoInteractions(inventoryService);
        }

        @Test
        @DisplayName("continues importing remaining items when one fails to persist")
        void continuesOnPersistFailure() {
            List<Item> incoming = List.of(
                    new Item("Good Rose", "Flowers/Plants", 3.00, "Per Stem", 1.50, 20, ""),
                    new Item("Also Good", "Flowers/Plants", 2.00, "Per Stem", 1.00, 10, "")
            );
            when(importConnector.importItems())
                    .thenReturn(ConnectorResult.ok(incoming, "Imported 2"));
            when(inventoryService.addItem(any()))
                    .thenThrow(new IllegalArgumentException("DB error"))
                    .thenAnswer(inv -> inv.getArgument(0));

            ConnectorResult<List<Item>> result = registry.runImport("mockimporter");

            assertThat(result.isSuccess()).isTrue();
            verify(inventoryService, times(2)).addItem(any());
        }
    }

    // ── runExport ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("runExport()")
    class RunExportTests {

        @Test
        @DisplayName("fetches live inventory and delegates to connector")
        void exportsDelegatesToConnector() {
            List<Item> liveInventory = List.of(
                    new Item("Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, "")
            );
            when(inventoryService.getAllItems()).thenReturn(liveInventory);
            when(exportConnector.exportItems(liveInventory))
                    .thenReturn(ConnectorResult.ok(1, "Exported 1"));

            ConnectorResult<Integer> result = registry.runExport("mockexporter");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload()).isEqualTo(1);
            verify(exportConnector).exportItems(liveInventory);
        }

        @Test
        @DisplayName("returns failure for unknown connector name")
        void failsOnUnknownConnector() {
            ConnectorResult<Integer> result = registry.runExport("nope");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("not found");
        }

        @Test
        @DisplayName("returns failure when connector does not support EXPORT")
        void failsOnImportOnlyConnector() {
            ConnectorResult<Integer> result = registry.runExport("mockimporter");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("does not support EXPORT");
        }
    }

    // ── runSync ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("runSync()")
    class RunSyncTests {

        @Test
        @DisplayName("delegates to connector and returns SyncSummary")
        void syncDelegatesToConnector() {
            SyncSummary summary = new SyncSummary(2, 1, 0, 3, 0);
            List<Item> liveInventory = List.of(
                    new Item("Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, "")
            );
            when(inventoryService.getAllItems()).thenReturn(liveInventory);
            when(biConnector.syncUpdates(liveInventory))
                    .thenReturn(ConnectorResult.ok(summary, "Sync done"));

            ConnectorResult<SyncSummary> result = registry.runSync("mockbidi");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getPayload().created()).isEqualTo(2);
            assertThat(result.getPayload().updated()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns failure when connector does not support SYNC")
        void failsOnExportOnlyConnector() {
            ConnectorResult<SyncSummary> result = registry.runSync("mockexporter");

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("does not support SYNC");
        }

        @Test
        @DisplayName("returns failure for unknown connector name")
        void failsOnUnknownConnector() {
            ConnectorResult<SyncSummary> result = registry.runSync("phantom");

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ── Availability ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("listConnectorInfo() marks connector unavailable when isAvailable() throws")
    void availabilityFaultTolerance() {
        when(importConnector.isAvailable()).thenThrow(new RuntimeException("network down"));

        List<Map<String, Object>> info = registry.listConnectorInfo();

        Optional<Map<String, Object>> importInfo = info.stream()
                .filter(m -> "MockImporter".equals(m.get("name")))
                .findFirst();

        assertThat(importInfo).isPresent();
        assertThat(importInfo.get().get("available")).isEqualTo(false);
    }
}
