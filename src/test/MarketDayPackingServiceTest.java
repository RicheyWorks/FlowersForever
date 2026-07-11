package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import com.flowerfarm.service.MarketDayPackingService.ProductNeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketDayPackingService")
class MarketDayPackingServiceTest {

    @Mock OrderService orderService;
    @Mock InventoryService inventoryService;
    MarketDayPackingService service;

    @BeforeEach
    void setUp() {
        service = new MarketDayPackingService(orderService, inventoryService);
    }

    @Test
    @DisplayName("aggregates CONFIRMED lines and flags inventory shortfall")
    void aggregatesAndShortfall() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer florist = new Customer("Kitsap Blooms", "Sam", "", "", "FLORIST", "");
        florist.setId(1L);
        Customer market = new Customer("Saturday Stall", "", "", "", "MARKET", "");
        market.setId(2L);

        CustomerOrder a = new CustomerOrder(florist, day, "CONFIRMED", "AM pickup");
        a.setId(10L);
        a.addLine(new OrderLine("Nootka Rose", 40, "stems", 2.5));
        a.addLine(new OrderLine("Dahlia mix", 10, "bunches", 12.0));

        CustomerOrder b = new CustomerOrder(market, day, "CONFIRMED", "");
        b.setId(11L);
        b.addLine(new OrderLine("Nootka Rose", 20, "stems", 2.5));

        CustomerOrder draft = new CustomerOrder(florist, day, "DRAFT", "ignore me");
        draft.setId(12L);
        draft.addLine(new OrderLine("Peony", 5, "stems", 4.0));

        when(orderService.findBetween(day, day)).thenReturn(List.of(a, b, draft));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 2.5, "stems", 1.0, 50, ""),
                new Item("Dahlia mix", "Flowers/Plants", 12.0, "bunches", 6.0, 5, "")
        ));

        MarketDayPlan plan = service.planForDay(day);

        assertThat(plan.orderCount()).isEqualTo(2);
        assertThat(plan.scope()).isEqualTo("CONFIRMED");
        assertThat(plan.pipelineValue()).isEqualTo(40 * 2.5 + 10 * 12.0 + 20 * 2.5);
        assertThat(plan.pickList()).hasSize(2);

        ProductNeed rose = plan.pickList().stream()
                .filter(p -> p.productName().equals("Nootka Rose")).findFirst().orElseThrow();
        assertThat(rose.neededQty()).isEqualTo(60.0);
        assertThat(rose.stockOnHand()).isEqualTo(50);
        assertThat(rose.shortfall()).isTrue();
        assertThat(rose.shortfallQty()).isEqualTo(10.0);

        ProductNeed dahlia = plan.pickList().stream()
                .filter(p -> p.productName().equals("Dahlia mix")).findFirst().orElseThrow();
        assertThat(dahlia.shortfall()).isTrue();
        assertThat(plan.shortfallSkuCount()).isEqualTo(2);

        assertThat(plan.customers()).extracting(c -> c.customerName())
                .containsExactly("Kitsap Blooms", "Saturday Stall");
        assertThat(plan.plainText()).contains("MARKET DAY PACKING LIST");
        assertThat(plan.plainText()).contains("Kitsap Blooms");
        assertThat(plan.plainText()).doesNotContain("Peony");
    }

    @Test
    @DisplayName("includeDraft pulls DRAFT orders into the plan")
    void includeDraft() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer c = new Customer("Buyer", "", "", "", "MARKET", "");
        c.setId(1L);
        CustomerOrder draft = new CustomerOrder(c, day, "DRAFT", "");
        draft.setId(1L);
        draft.addLine(new OrderLine("Tulip", 12, "stems", 1.5));

        when(orderService.findBetween(day, day)).thenReturn(List.of(draft));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Tulip", "Flowers/Plants", 1.5, "stems", 0.5, 100, "")
        ));

        MarketDayPlan plan = service.buildPlan(day, 1, true, false);
        assertThat(plan.orderCount()).isEqualTo(1);
        assertThat(plan.scope()).isEqualTo("CONFIRMED+DRAFT");
        assertThat(plan.pickList().get(0).shortfall()).isFalse();
    }

    @Test
    @DisplayName("CSV export has PICK and ORDER sections")
    void csvExport() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer c = new Customer("Buyer", "", "", "", "MARKET", "");
        c.setId(1L);
        CustomerOrder o = new CustomerOrder(c, day, "CONFIRMED", "");
        o.setId(7L);
        o.addLine(new OrderLine("Nootka Rose", 10, "stems", 2.0));

        when(orderService.findBetween(any(), any())).thenReturn(List.of(o));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 2.0, "stems", 1.0, 100, "")
        ));

        MarketDayPlan plan = service.planForDay(day);
        String csv = service.exportCsv(plan);
        assertThat(csv).contains("section,orderId");
        assertThat(csv).contains("PICK,");
        assertThat(csv).contains("ORDER,7,Buyer");
    }

    @Test
    @DisplayName("empty day produces zero orders and helpful plain text")
    void emptyDay() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        when(orderService.findBetween(day, day)).thenReturn(List.of());
        when(inventoryService.getAllItems()).thenReturn(List.of());

        MarketDayPlan plan = service.planForDay(day);
        assertThat(plan.orderCount()).isZero();
        assertThat(plan.pickList()).isEmpty();
        assertThat(plan.plainText()).contains("no confirmed orders");
    }

    @Test
    @DisplayName("fulfillConfirmedOrders fulfills CONFIRMED and skips others")
    void batchFulfill() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer c = new Customer("Kitsap Blooms", "", "", "", "FLORIST", "");
        c.setId(1L);
        CustomerOrder confirmed = new CustomerOrder(c, day, "CONFIRMED", "");
        confirmed.setId(10L);
        confirmed.addLine(new OrderLine("Nootka Rose", 5, "stems", 2.0));
        CustomerOrder draft = new CustomerOrder(c, day, "DRAFT", "");
        draft.setId(11L);
        draft.addLine(new OrderLine("Dahlia", 2, "stems", 3.0));

        when(orderService.findBetween(day, day)).thenReturn(List.of(confirmed, draft));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 2.0, "stems", 1.0, 100, "")
        ));
        when(orderService.fulfill(10L)).thenAnswer(inv -> {
            confirmed.setStatus("FULFILLED");
            return confirmed;
        });

        // Plan with drafts included so draft appears as skipped
        MarketDayPlan plan = service.buildPlan(day, 1, true, false);
        var result = service.fulfillConfirmedOrders(plan);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.fulfilled()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        verify(orderService).fulfill(eq(10L));
        assertThat(result.messages().stream().anyMatch(m -> m.contains("FULFILLED") || m.contains("→")))
                .isTrue();
    }

    @Test
    @DisplayName("generatePackingPdf returns PDF with magic header")
    void packingPdf() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer c = new Customer("Kitsap Blooms", "", "", "", "FLORIST", "");
        c.setId(1L);
        CustomerOrder o = new CustomerOrder(c, day, "CONFIRMED", "AM");
        o.setId(3L);
        o.addLine(new OrderLine("Nootka Rose", 40, "stems", 2.5));
        when(orderService.findBetween(any(), any())).thenReturn(List.of(o));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 2.5, "stems", 1.0, 20, "")
        ));

        MarketDayPlan plan = service.planForDay(day);
        byte[] pdf = service.generatePackingPdf(plan);
        assertThat(pdf).isNotNull();
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePackingPdf(null));
    }
}
