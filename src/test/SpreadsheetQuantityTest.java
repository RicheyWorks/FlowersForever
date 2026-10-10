package com.flowerfarm.connector.impl;

import com.flowerfarm.model.Item;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestTemplate;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SpreadsheetQuantityTest {
    @TempDir Path temporary;

    @Test void excelRoundTripRetainsNamedProductsAndSixDecimalWeights() {
        ExcelConnector excel = new ExcelConnector(temporary.resolve("inventory.xlsx").toString());
        Item item = new Item("Blue Dream flower", "Cannabis", 5, "grams", 1, 0.123456, "Lot A");
        assertThat(excel.exportItems(List.of(item)).isSuccess()).isTrue();
        var imported = excel.importItems();
        assertThat(imported.isSuccess()).isTrue();
        assertThat(imported.getPayload()).singleElement().satisfies(saved -> {
            assertThat(saved.getName()).isEqualTo("Blue Dream flower");
            assertThat(saved.getQuantity()).isEqualTo(0.123456);
            assertThat(saved.getUnit()).isEqualTo("grams");
        });
    }

    @Test void excelRejectsMalformedOrFractionalCountRowsInsteadOfSubstitutingZero() throws Exception {
        ExcelConnector excel = new ExcelConnector("unused.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var row = workbook.createSheet().createRow(0);
            row.createCell(0).setCellValue("Clone");
            row.createCell(1).setCellValue("Cannabis");
            row.createCell(3).setCellValue("plants");
            row.createCell(5).setCellValue(0.5);
            assertThatThrownBy(() -> excel.mapToItem(row)).hasMessageContaining("whole number");
            row.getCell(5).setCellValue("unknown");
            assertThatThrownBy(() -> excel.mapToItem(row)).hasMessageContaining("no stock value was substituted");
        }
    }

    @Test void sheetsRetainsDecimalTextAndRejectsMalformedQuantity() {
        var sheets = new GoogleSheetsConnector("", "Inventory", "", "", "Name", "Category",
                "Price", "Unit", "Cost", "Quantity", "Notes", new RestTemplate());
        assertThat(sheets.mapToItem(List.of("Blue Dream flower", "Cannabis", "5", "grams", "1", "0.123456", ""))
                .getQuantity()).isEqualTo(0.123456);
        assertThatThrownBy(() -> sheets.mapToItem(List.of("Blue Dream flower", "Cannabis", "5", "grams",
                "1", "unknown", ""))).hasMessageContaining("no stock value was substituted");
    }
}
