package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Extracted report and CSV collaborators")
class ReportCollaboratorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("harvest report generator renders both report families and escaped bed CSV")
    void harvestReports() {
        HarvestReportGenerator generator = new HarvestReportGenerator();
        HarvestService.BedProduction bed = new HarvestService.BedProduction(
                "Bed, A", 25, 1, Map.of("Nootka Rose", 25.0),
                "2026-08-20", "2026-08-20");
        HarvestService.BedProductionReport beds = new HarvestService.BedProductionReport(
                "2026-08-20", "2026-08-20", 1, 1, 25,
                List.of(bed), "bed report");
        HarvestEntry entry = new HarvestEntry(LocalDate.of(2026, 8, 20),
                "Nootka Rose", 25, "stems", "Bed, A", "market cut");
        HarvestService.HarvestLogReport log = new HarvestService.HarvestLogReport(
                "2026-08-20", "2026-08-20", 1, 25,
                Map.of("Nootka Rose", 25.0), List.of(entry), "harvest log");

        assertPdf(generator.generateBedProductionPdf(beds));
        assertPdf(generator.generateHarvestLogPdf(log));
        assertThat(generator.exportBedProductionCsv(beds))
                .contains("\"Bed, A\"")
                .contains("Nootka Rose");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateHarvestLogPdf(null));
    }

    @Test
    @DisplayName("harvest CSV exporter preserves quotes, commas, and the stable header")
    void harvestCsv() throws Exception {
        HarvestEntry entry = new HarvestEntry(LocalDate.of(2026, 8, 20),
                "Rose, field", 12, "stems", "Bed A", "said \"ready\"");
        Path output = temporaryDirectory.resolve("harvest.csv");

        new HarvestCsvExporter().export(output.toString(), List.of(entry));

        assertThat(Files.readString(output))
                .startsWith("Id,Date,Crop,Quantity,Unit,BedOrField,Notes")
                .contains("\"Rose, field\"")
                .contains("\"said \"\"ready\"\"\"");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HarvestCsvExporter().export(" ", List.of()));
    }

    @Test
    @DisplayName("inventory report generator renders reorder and price-list documents")
    void inventoryReports() {
        InventoryReportGenerator generator = new InventoryReportGenerator();
        InventoryService.LowStockLine lowLine = new InventoryService.LowStockLine(
                1L, "Nootka Rose", "Flowers", 2, "stems", 2.5, 1.0,
                5, 8, 8.0);
        InventoryService.LowStockReport low = new InventoryService.LowStockReport(
                LocalDate.of(2026, 8, 20), 5, 1, 1, 8.0,
                List.of(lowLine), "reorder");
        InventoryService.PriceListLine priceLine = new InventoryService.PriceListLine(
                1L, "Nootka Rose", "Flowers", 2.5, "stems", 2,
                5.0, 1.0, 2.0);
        InventoryService.PriceListReport prices = new InventoryService.PriceListReport(
                LocalDate.of(2026, 8, 20), 1, 2, 5.0, 2.0,
                true, List.of(priceLine), "prices");

        assertPdf(generator.generateLowStockPdf(low));
        assertPdf(generator.generatePriceListPdf(prices));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateLowStockPdf(null));
    }

    @Test
    @DisplayName("inventory CSV exporter writes the shared inventory contract")
    void inventoryCsv() throws Exception {
        Item item = new Item("Rose, field", "Flowers", 2.5,
                "Per Stem", 1.0, 12, "market");
        Path output = temporaryDirectory.resolve("inventory.csv");

        new InventoryCsvExporter().export(output.toString(), List.of(item));

        assertThat(Files.readString(output))
                .startsWith(InventoryService.CSV_HEADER)
                .contains("\"Rose, field\"");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InventoryCsvExporter().export(null, List.of()));
    }

    @Test
    @DisplayName("order report generator renders invoice text, PDF, and accounting CSV")
    void orderReports() throws Exception {
        Customer customer = new Customer("Kitsap, Blooms", "Sam", "sam@example.test",
                "360-555-0100", "FLORIST", "");
        CustomerOrder order = new CustomerOrder(customer, LocalDate.of(2026, 8, 20),
                "CONFIRMED", "bring \"buckets\"");
        order.setHandoffType("DELIVERY");
        order.setId(42L);
        order.addLine(new OrderLine("Nootka Rose", 10, "stems", 2.5));
        OrderReportGenerator generator = new OrderReportGenerator();
        Path output = temporaryDirectory.resolve("orders.csv");

        assertPdf(generator.generateInvoicePdf(order));
        assertThat(generator.formatInvoiceText(order))
                .contains("ORDER #42", "Kitsap, Blooms", "TOTAL  $25.00",
                         "Pickup / delivery / market date: 2026-08-20",
                         "Handoff: Delivery",
                        "Handoff instructions: bring \"buckets\"",
                        "Sam", "360-555-0100", "sam@example.test");
        generator.exportCsv(output.toString(), List.of(order));
        assertThat(Files.readString(output))
                .contains("\"Kitsap, Blooms\"")
                 .contains("Sam", "360-555-0100", "sam@example.test")
                 .contains(",DELIVERY,")
                .contains("10 stems Nootka Rose @ $2.50")
                .contains("\"bring \"\"buckets\"\"\"");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> generator.generateInvoicePdf(null));
    }

    @Test
    @DisplayName("compatibility facades retain read-only transaction boundaries")
    void facadeTransactions() throws Exception {
        assertReadOnly(InventoryService.class.getMethod("exportToCsv", String.class));
        assertReadOnly(HarvestService.class.getMethod(
                "exportToCsv", String.class, List.class));
        assertReadOnly(OrderService.class.getMethod(
                "exportToCsv", String.class, List.class));
        assertReadOnly(OrderService.class.getMethod("generateInvoicePdf", Long.class));
    }

    private static void assertPdf(byte[] document) {
        assertThat(document).hasSizeGreaterThan(100);
        assertThat(new String(document, 0, 4)).isEqualTo("%PDF");
    }

    private static void assertReadOnly(java.lang.reflect.Method method) {
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertThat(transaction).as(method.toGenericString()).isNotNull();
        assertThat(transaction.readOnly()).as(method.toGenericString()).isTrue();
    }
}
