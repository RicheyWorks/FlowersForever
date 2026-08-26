package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.model.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvInventoryConnectorTest {

    @TempDir
    Path tempDir;

    @Test
    void exportImportRoundTripPreservesQuotedFieldsAndNewlines() {
        Path csv = tempDir.resolve("inventory.csv");
        CsvInventoryConnector connector = connector(csv);
        Item item = new Item("Rose, red", "Flowers\nPlants", 4.25,
                "Stem \"large\"", 1.75, 7, "First line\nSecond, \"quoted\" line");

        assertThat(connector.exportItems(List.of(item)).isSuccess()).isTrue();
        ConnectorResult<List<Item>> imported = connector.importItems();

        assertThat(imported.isSuccess()).isTrue();
        assertThat(imported.getPayload()).singleElement().satisfies(actual -> {
            assertThat(actual.getName()).isEqualTo(item.getName());
            assertThat(actual.getCategory()).isEqualTo(item.getCategory());
            assertThat(actual.getUnit()).isEqualTo(item.getUnit());
            assertThat(actual.getNotes()).isEqualTo(item.getNotes());
            assertThat(actual.getPrice()).isEqualTo(item.getPrice());
            assertThat(actual.getCost()).isEqualTo(item.getCost());
            assertThat(actual.getQuantity()).isEqualTo(item.getQuantity());
        });
    }

    @Test
    void syncPreservesRowsThatExistOnlyInCsv() {
        Path csv = tempDir.resolve("inventory.csv");
        CsvInventoryConnector connector = connector(csv);
        assertThat(connector.exportItems(List.of(
                new Item("Remote Only", "Other", 2.0, "each", 1.0, 3, "keep me"),
                new Item("Shared", "Other", 3.0, "each", 1.0, 4, "")
        )).isSuccess()).isTrue();

        ConnectorResult<SyncSummary> result = connector.syncUpdates(List.of(
                new Item("Shared", "Other", 3.5, "each", 1.0, 9, ""),
                new Item("Local Only", "Other", 5.0, "each", 2.0, 2, "")
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getPayload().created()).isEqualTo(1);
        assertThat(result.getPayload().updated()).isEqualTo(1);
        assertThat(connector.importItems().getPayload())
                .extracting(Item::getName)
                .containsExactly("Shared", "Local Only", "Remote Only");
    }

    private CsvInventoryConnector connector(Path csv) {
        return new CsvInventoryConnector(
                csv.toString(), csv.toString(), ",",
                "Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes");
    }
}
