package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.DayCloseoutService.DayCloseout;
import com.flowerfarm.service.OrderService.WeekRevenueSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DayCloseoutService")
class DayCloseoutServiceTest {

    private static final ZoneId PNW = ZoneId.of("America/Los_Angeles");

    @Mock InventoryService inventoryService;
    @Mock HarvestService harvestService;
    @Mock OrderService orderService;

    DayCloseoutService service;

    @BeforeEach
    void setUp() {
        service = new DayCloseoutService(inventoryService, harvestService, orderService);
    }

    @Test
    @DisplayName("build aggregates fulfilled, harvest, pipeline, stock into next steps + PDF")
    void buildAndPdf() {
        LocalDate today = LocalDate.now(PNW);

        Customer kitsap = new Customer("Kitsap Blooms", "Sam", "", "", "FLORIST", "");
        kitsap.setId(1L);
        CustomerOrder done = new CustomerOrder(kitsap, today, "FULFILLED", "van");
        done.setId(10L);
        done.addLine(new OrderLine("Nootka Rose", 20, "stems", 2.5));

        Customer stall = new Customer("Market Stall", "Alex", "", "", "MARKET", "");
        stall.setId(2L);
        CustomerOrder open = new CustomerOrder(stall, today, "CONFIRMED", "");
        open.setId(11L);
        open.addLine(new OrderLine("Dahlia", 10, "stems", 3.0));

        CustomerOrder cancelled = new CustomerOrder(kitsap, today, "CANCELLED", "no-show");
        cancelled.setId(12L);

        when(orderService.findBetween(eq(today), eq(today)))
                .thenReturn(List.of(done, open, cancelled));
        when(orderService.weekRevenueSummary()).thenReturn(
                new WeekRevenueSummary(today.minusDays(6), today, 250.0, 30.0, 0.0, 2, 1, 0));

        when(harvestService.findBetween(eq(today), eq(today))).thenReturn(List.of(
                new HarvestEntry(today, "Nootka Rose", 40, "stems", "Bed A", "")));
        when(harvestService.totalQuantityLast7Days()).thenReturn(120.0);

        when(inventoryService.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(4, 500, 200, 1, 80, 10));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Low SKU", "Other", 1, "each", 0.5, 2, ""),
                new Item("OK SKU", "Other", 1, "each", 0.5, 50, "")
        ));

        DayCloseout closeout = service.build();
        assertThat(closeout.plainText()).contains("DAY CLOSEOUT");
        assertThat(closeout.fulfilledCount()).isEqualTo(1);
        assertThat(closeout.fulfilledRevenue()).isEqualTo(50.0);
        assertThat(closeout.remainingConfirmedCount()).isEqualTo(1);
        assertThat(closeout.remainingPipelineRevenue()).isEqualTo(30.0);
        assertThat(closeout.cancelledCount()).isEqualTo(1);
        assertThat(closeout.todayHarvestQty()).isEqualTo(40.0);
        assertThat(closeout.lowStock()).extracting(DayCloseoutService.LowStockLine::name)
                .contains("Low SKU");
        assertThat(closeout.nextSteps()).isNotEmpty();
        assertThat(closeout.toMap()).containsKeys("fulfilledOrders", "nextSteps", "lowStock");

        byte[] pdf = service.generatePdf(closeout);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePdf(null));
    }

    @Test
    @DisplayName("quiet day still produces actionable next steps")
    void quietDay() {
        LocalDate today = LocalDate.now(PNW);
        when(orderService.findBetween(any(), any())).thenReturn(List.of());
        when(orderService.weekRevenueSummary()).thenReturn(
                new WeekRevenueSummary(today.minusDays(6), today, 0, 0, 0, 0, 0, 0));
        when(harvestService.findBetween(any(), any())).thenReturn(List.of());
        when(harvestService.totalQuantityLast7Days()).thenReturn(0.0);
        when(inventoryService.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(0, 0, 0, 0, 0, 10));
        when(inventoryService.getAllItems()).thenReturn(List.of());

        DayCloseout closeout = service.build();
        assertThat(closeout.fulfilledCount()).isZero();
        assertThat(closeout.nextSteps()).anyMatch(s -> s.toLowerCase().contains("no sales")
                || s.toLowerCase().contains("harvest")
                || s.toLowerCase().contains("quiet"));
        assertThat(service.generatePdf(closeout)).startsWith("%PDF".getBytes());
    }
}
