package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.OrderLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService")
class ReportServiceTest {

    @Mock HarvestService harvestService;
    @Mock OrderService orderService;
    ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(harvestService, orderService, new FarmProfile());
    }

    @Test
    @DisplayName("generateWeeklyReportPdf returns non-empty PDF bytes")
    void weeklyPdf() {
        when(harvestService.findBetween(any(), any())).thenReturn(List.of(
                new HarvestEntry(LocalDate.now(), "Nootka Rose", 100, "stems", "A", "")
        ));
        when(harvestService.productionByBed(any(), any())).thenReturn(
                new HarvestService.BedProductionReport(
                        LocalDate.now().minusDays(6).toString(),
                        LocalDate.now().toString(),
                        1,
                        1,
                        100.0,
                        List.of(new HarvestService.BedProduction(
                                "Bed A", 100.0, 1,
                                Map.of("Nootka Rose", 100.0),
                                LocalDate.now().toString(),
                                LocalDate.now().toString())),
                        "BED / FIELD PRODUCTION"
                ));

        Customer c = new Customer("Florist Co", "", "", "", "FLORIST", "");
        c.setId(1L);
        CustomerOrder order = new CustomerOrder(c, LocalDate.now(), "FULFILLED", "");
        order.addLine(new OrderLine("Nootka Rose", 10, "bunch", 15.0));
        when(orderService.findBetween(any(), any())).thenReturn(List.of(order));

        byte[] pdf = reportService.generateWeeklyReportPdf();

        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        // PDF magic header
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("generateReportPdf rejects inverted range")
    void badRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> reportService.generateReportPdf(
                        LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1)));
    }

    @Test
    @DisplayName("preview separates committed and fulfilled revenue before export")
    void previewSeparatesRevenueStates() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 7);
        when(harvestService.findBetween(from, to)).thenReturn(List.of(
                new HarvestEntry(from, "Dahlia", 40, "stems", "Bed A", ""),
                new HarvestEntry(to, "Nootka Rose", 24, "stems", "Bed B", "")));

        Customer customer = new Customer("Stem & Vine", "", "", "", "FLORIST", "");
        customer.setId(7L);
        CustomerOrder confirmed = new CustomerOrder(customer, from, "CONFIRMED", "");
        confirmed.addLine(new OrderLine("Dahlia", 10, "stems", 4.0));
        CustomerOrder fulfilled = new CustomerOrder(customer, to, "FULFILLED", "");
        fulfilled.addLine(new OrderLine("Nootka Rose", 8, "stems", 5.0));
        CustomerOrder draft = new CustomerOrder(customer, to, "DRAFT", "");
        draft.addLine(new OrderLine("Market Bouquet", 2, "each", 25.0));
        when(orderService.findBetween(from, to))
                .thenReturn(List.of(confirmed, fulfilled, draft));

        ReportService.ReportPreview preview = reportService.preview(from, to);

        assertThat(preview.harvestEntries()).isEqualTo(2);
        assertThat(preview.orders()).isEqualTo(3);
        assertThat(preview.confirmedOrders()).isEqualTo(1);
        assertThat(preview.fulfilledOrders()).isEqualTo(1);
        assertThat(preview.committedRevenue()).isEqualTo(80.0);
        assertThat(preview.fulfilledRevenue()).isEqualTo(40.0);
        assertThat(preview.products()).hasSize(2);
        assertThat(preview.products()).filteredOn(
                product -> product.productName().equals("Dahlia")).singleElement()
                .satisfies(product -> {
            assertThat(product.productName()).isEqualTo("Dahlia");
            assertThat(product.quantity()).isEqualTo(10.0);
            assertThat(product.orderCount()).isEqualTo(1);
            assertThat(product.committedRevenue()).isEqualTo(40.0);
            assertThat(product.averageUnitPrice()).isEqualTo(4.0);
        });
        assertThat(preview.customers()).singleElement().satisfies(customerRow -> {
            assertThat(customerRow.customerName()).isEqualTo("Stem & Vine");
            assertThat(customerRow.orderCount()).isEqualTo(2);
            assertThat(customerRow.committedRevenue()).isEqualTo(80.0);
            assertThat(customerRow.fulfilledRevenue()).isEqualTo(40.0);
            assertThat(customerRow.averageOrderValue()).isEqualTo(40.0);
        });
    }
}
