package com.flowerfarm.gui.automation;

import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumn;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingTableHeaderTest {
    @Test
    void readsCurrentHeaderValuesInViewOrderAfterTheyAreRenamedAndReordered() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = table();
            table.moveColumn(1, 0);
            assertThat(SwingControlReader.read(table, "records", null, null, null, null))
                    .containsEntry("columns", List.of("Quantity", "Product"))
                    .containsEntry("rows", List.of(List.of("2", "Roses"), List.of("3", "Dahlias")));
        });
    }

    @Test
    void sortsByTheVisibleRenamedHeaderAndRejectsDuplicateVisibleHeaders() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = table();
            table.moveColumn(1, 0);
            SwingHarnessBridge bridge = new SwingHarnessBridge();
            bridge.attach(table, title -> false);
            try {
                bridge.sortTable("records", "Product", "ASCENDING");
                assertThat(table.getValueAt(0, 1)).isEqualTo("Dahlias");
                assertThat(table.getRowSorter().getSortKeys()).singleElement()
                        .satisfies(key -> assertThat(key.getColumn()).isZero());

                TableColumn quantity = table.getColumnModel().getColumn(0);
                quantity.setHeaderValue("Product");
                var originalSort = List.copyOf(table.getRowSorter().getSortKeys());
                assertThatThrownBy(() -> bridge.sortTable("records", "Product", "DESCENDING"))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Several columns");
                assertThat(table.getRowSorter().getSortKeys()).isEqualTo(originalSort);

                quantity.setHeaderValue("Quantity");
                bridge.sortTable("records", "Product", "DESCENDING");
                assertThat(table.getValueAt(0, 1)).isEqualTo("Roses");
            } finally {
                bridge.detach();
            }
        });
    }

    private static JTable table() {
        JTable table = new JTable(new Object[][]{{"Roses", 2}, {"Dahlias", 3}},
                new String[]{"internal_product", "internal_quantity"});
        table.setName("records");
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setHeaderValue("Product");
        table.getColumnModel().getColumn(1).setHeaderValue("Quantity");
        return table;
    }
}
