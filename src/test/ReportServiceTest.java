package com.flowerfarm.service;

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
        reportService = new ReportService(harvestService, orderService);
    }

    @Test
    @DisplayName("generateWeeklyReportPdf returns non-empty PDF bytes")
    void weeklyPdf() {
        when(harvestService.findBetween(any(), any())).thenReturn(List.of(
                new HarvestEntry(LocalDate.now(), "Nootka Rose", 100, "stems", "A", "")
        ));
        when(harvestService.totalsByCrop()).thenReturn(Map.of("Nootka Rose", 100.0));

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
}
