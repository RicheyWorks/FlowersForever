package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.MarketDayPackingService.CustomerPack;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import com.flowerfarm.service.MarketDayPackingService.ProductNeed;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
        Customer florist = new Customer("Kitsap Blooms", "Sam Rivera",
                "sam@kitsap.example", "360-555-0142", "FLORIST",
                "Deliver to west loading dock");
        florist.setId(1L);
        Customer market = new Customer("Saturday Stall", "", "", "", "MARKET", "");
        market.setId(2L);

        CustomerOrder a = new CustomerOrder(florist, day, "CONFIRMED", "AM pickup");
        a.setHandoffType("DELIVERY");
        a.setId(10L);
        OrderLine floristRose = new OrderLine("Nootka Rose", 40, "stems", 2.5);
        floristRose.setId(101L);
        a.addLine(floristRose);
        a.addLine(new OrderLine("Dahlia mix", 10, "bunches", 12.0));

        CustomerOrder b = new CustomerOrder(market, day, "CONFIRMED", "");
        b.setHandoffType("PICKUP");
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
        CustomerPack floristPack = plan.customers().get(0);
        assertThat(floristPack.contactName()).isEqualTo("Sam Rivera");
        assertThat(floristPack.phone()).isEqualTo("360-555-0142");
        assertThat(floristPack.email()).isEqualTo("sam@kitsap.example");
        assertThat(floristPack.accountNotes()).isEqualTo("Deliver to west loading dock");
        assertThat(floristPack.handoffType()).isEqualTo("DELIVERY");
        assertThat(floristPack.lines()).extracting(
                MarketDayPackingService.PackLine::lineId).contains(101L);
        assertThat(plan.toMap().toString())
                .contains("Sam Rivera", "360-555-0142", "sam@kitsap.example",
                        "accountNotes=Deliver to west loading dock", "DELIVERY");
        assertThat(plan.plainText()).contains("MARKET DAY PACKING LIST");
        assertThat(plan.plainText()).contains(
                "Kitsap Blooms", "Sam Rivera", "360-555-0142", "sam@kitsap.example",
                "account delivery note: Deliver to west loading dock");
        assertThat(plan.plainText()).contains("handoff: Delivery · date: 2026-07-12");
        assertThat(service.exportCsv(plan)).contains(
                "contactName,phone,email,accountNotes,notes,orderDate",
                ",DELIVERY,", "Sam Rivera,360-555-0142,sam@kitsap.example,"
                        + "Deliver to west loading dock,AM pickup,2026-07-12");
        assertThat(plan.plainText()).doesNotContain("Peony");
    }

    @Test
    @DisplayName("orders unresolved handoffs first, then market, delivery, and pickup loads")
    void sequencesMixedHandoffsForLoadOut() {
        LocalDate day = LocalDate.of(2026, 9, 12);
        Customer customer = new Customer("Buyer", "", "", "", "WHOLESALE", "");
        CustomerOrder pickup = packingOrder(customer, 14L, day, "PICKUP", "Pickup stems");
        CustomerOrder delivery = packingOrder(customer, 13L, day, "DELIVERY", "Delivery stems");
        CustomerOrder market = packingOrder(customer, 12L, day, "MARKET", "Market stems");
        CustomerOrder unspecified = packingOrder(
                customer, 11L, day, "UNSPECIFIED", "Review stems");
        when(orderService.findBetween(day, day))
                .thenReturn(List.of(pickup, delivery, market, unspecified));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Pickup stems", "Flowers/Plants", 1, "stems", 1, 10, ""),
                new Item("Delivery stems", "Flowers/Plants", 1, "stems", 1, 10, ""),
                new Item("Market stems", "Flowers/Plants", 1, "stems", 1, 10, ""),
                new Item("Review stems", "Flowers/Plants", 1, "stems", 1, 10, "")));

        MarketDayPlan plan = service.planForDay(day);

        assertThat(plan.customers()).extracting(CustomerPack::orderId)
                .containsExactly(11L, 12L, 13L, 14L);
        assertThat(plan.customers()).extracting(CustomerPack::handoffType)
                .containsExactly("UNSPECIFIED", "MARKET", "DELIVERY", "PICKUP");
        String text = plan.plainText();
        assertThat(text).contains(
                "Unspecified handoffs: 1 confirmed order(s) need Pickup, Delivery, or Market");
        assertThat(text.indexOf("handoff: Unspecified"))
                .isLessThan(text.indexOf("handoff: Market"));
        assertThat(text.indexOf("handoff: Market"))
                .isLessThan(text.indexOf("handoff: Delivery"));
        assertThat(text.indexOf("handoff: Delivery"))
                .isLessThan(text.indexOf("handoff: Pickup"));
    }

    private static CustomerOrder packingOrder(
            Customer customer, long id, LocalDate day, String handoff, String product) {
        CustomerOrder order = new CustomerOrder(customer, day, "CONFIRMED", "");
        order.setId(id);
        order.setHandoffType(handoff);
        order.addLine(new OrderLine(product, 1, "stems", 1));
        return order;
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
    @DisplayName("draft preview demand cannot block covered confirmed completion")
    void previewDemandIsSeparatedFromConfirmedCompletionDemand() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer customer = new Customer("Buyer", "", "", "", "MARKET", "");
        CustomerOrder confirmed = new CustomerOrder(customer, day, "CONFIRMED", "Load first");
        confirmed.setId(1L);
        confirmed.addLine(new OrderLine("Tulip", 5, "stems", 2));
        CustomerOrder draft = new CustomerOrder(customer, day, "DRAFT", "Preview only");
        draft.setId(2L);
        draft.addLine(new OrderLine("Tulip", 10, "stems", 2));
        CustomerOrder fulfilled = new CustomerOrder(customer, day, "FULFILLED", "Already left");
        fulfilled.setId(3L);
        fulfilled.addLine(new OrderLine("Tulip", 100, "stems", 2));
        when(orderService.findBetween(day, day))
                .thenReturn(List.of(confirmed, draft, fulfilled));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Tulip", "Flowers/Plants", 2, "stems", 1, 6, "")));

        MarketDayPlan plan = service.buildPlan(day, 1, true, true);

        assertThat(plan.orderCount()).isEqualTo(3);
        assertThat(plan.pipelineValue()).isEqualTo(30);
        assertThat(plan.confirmedOrderCount()).isEqualTo(1);
        assertThat(plan.confirmedValue()).isEqualTo(10);
        assertThat(plan.pickList()).singleElement().satisfies(need -> {
            assertThat(need.neededQty()).isEqualTo(15);
            assertThat(need.shortfallQty()).isEqualTo(9);
        });
        assertThat(plan.shortfallSkuCount()).isEqualTo(1);
        assertThat(plan.confirmedPickList()).singleElement().satisfies(need -> {
            assertThat(need.neededQty()).isEqualTo(5);
            assertThat(need.shortfall()).isFalse();
        });
        assertThat(plan.confirmedShortfallSkuCount()).isZero();
        assertThat(plan.toMap()).containsEntry("confirmedOrderCount", 1)
                .containsEntry("confirmedValue", 10.0)
                .containsEntry("confirmedShortfallSkuCount", 0)
                .containsKeys("pickList", "confirmedPickList");
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
        String[] rows = csv.lines().toArray(String[]::new);
        assertThat(rows).hasSize(3);
        assertThat(rows[0].split(",", -1)).hasSize(21);
        assertThat(rows[1].split(",", -1)).hasSize(21);
        assertThat(rows[1].split(",", -1)[5]).isEqualTo("Nootka Rose");
        assertThat(rows[2].split(",", -1)).hasSize(21);
        assertThat(rows[2].split(",", -1)[5]).isEqualTo("Nootka Rose");
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
    @DisplayName("legacy confirmed orders without products block the entire reviewed batch")
    void emptyConfirmedOrderBlocksBatchBeforeAnyFulfillment() {
        LocalDate day = LocalDate.of(2026, 9, 14);
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder empty = new CustomerOrder(customer, day, "CONFIRMED", "Delivery at noon");
        empty.setId(44L);
        when(orderService.findBetween(day, day)).thenReturn(List.of(empty));
        when(inventoryService.getAllItems()).thenReturn(List.of());
        MarketDayPlan plan = service.planForDay(day);

        assertThat(plan.customers()).singleElement().satisfies(pack -> {
            assertThat(pack.orderId()).isEqualTo(44L);
            assertThat(pack.lines()).isEmpty();
        });
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.fulfillConfirmedOrders(plan))
                .withMessageContainingAll("#44", "Kitsap Blooms", "no saved products",
                        "Add at least one product", "rebuild the packing list");

        verify(orderService, never()).fulfill(any());
    }

    @Test
    @DisplayName("context-free confirmed Delivery blocks batch before any inventory write")
    void contextFreeDeliveryBlocksBatchBeforeAnyFulfillment() {
        LocalDate day = LocalDate.of(2026, 9, 14);
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder delivery = new CustomerOrder(
                customer, day, "CONFIRMED", "", "DELIVERY");
        delivery.setId(45L);
        delivery.addLine(new OrderLine("Dahlia Mix", 4, "stems", 3.5));
        when(orderService.findBetween(day, day)).thenReturn(List.of(delivery));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 3.5, "stems", 1, 20, "")));
        MarketDayPlan plan = service.planForDay(day);

        assertThat(plan.customers()).singleElement().satisfies(pack -> {
            assertThat(pack.orderId()).isEqualTo(45L);
            assertThat(pack.accountNotes()).isBlank();
            assertThat(MarketDayPackingService.missingDeliveryContext(pack)).isTrue();
        });
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.fulfillConfirmedOrders(plan))
                .withMessageContainingAll("#45", "Kitsap Blooms", "no contact or destination",
                        "customer contact", "order delivery instructions", "rebuild");

        verify(orderService, never()).fulfillReviewed(eq(45L), any());
    }

    @Test
    @DisplayName("packing stock uses the exact SKU and never a similar product name")
    void similarProductNameDoesNotCountAsStock() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer customer = new Customer("Buyer", "", "", "", "MARKET", "");
        CustomerOrder order = new CustomerOrder(customer, day, "CONFIRMED", "");
        order.setId(8L);
        order.addLine(new OrderLine("Spray Rose", 6, "stems", 3));
        when(orderService.findBetween(day, day)).thenReturn(List.of(order));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Rose", "Flowers/Plants", 2, "stems", 1, 100, "")));

        MarketDayPlan plan = service.planForDay(day);

        assertThat(plan.shortfallSkuCount()).isEqualTo(1);
        assertThat(plan.pickList()).singleElement().satisfies(need -> {
            assertThat(need.productName()).isEqualTo("Spray Rose");
            assertThat(need.stockOnHand()).isZero();
            assertThat(need.shortfallQty()).isEqualTo(6);
        });
    }

    @Test
    @DisplayName("packing demand aggregates each line using fulfillment rounding")
    void packingDemandMatchesPerLineInventoryDeductions() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer customer = new Customer("Buyer", "", "", "", "MARKET", "");
        CustomerOrder order = new CustomerOrder(customer, day, "CONFIRMED", "");
        order.setId(9L);
        order.addLine(new OrderLine("Dahlia Mix", 0.6, "bunches", 12));
        order.addLine(new OrderLine("dahlia mix", 0.6, "bunches", 12));
        when(orderService.findBetween(day, day)).thenReturn(List.of(order));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 12, "bunches", 5, 1, "")));

        MarketDayPlan plan = service.planForDay(day);

        assertThat(plan.pickList()).singleElement().satisfies(need -> {
            assertThat(need.neededQty()).isEqualTo(2);
            assertThat(need.stockOnHand()).isEqualTo(1);
            assertThat(need.shortfallQty()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("packing blocks a saved line below one rounded inventory unit")
    void packingBlocksSubUnitQuantity() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer customer = new Customer("Buyer", "", "", "", "MARKET", "");
        CustomerOrder order = new CustomerOrder(customer, day, "CONFIRMED", "");
        order.setId(11L);
        order.addLine(new OrderLine("Nootka Rose", 0.4, "stems", 2));
        when(orderService.findBetween(day, day)).thenReturn(List.of(order));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 2, "stems", 1, 50, "")));

        MarketDayPlan plan = service.planForDay(day);

        assertThat(plan.shortfallSkuCount()).isEqualTo(1);
        assertThat(plan.pickList()).singleElement().satisfies(need -> {
            assertThat(need.neededQty()).isZero();
            assertThat(need.quantityInvalid()).isTrue();
            assertThat(need.blockingIssue()).isTrue();
        });
        assertThat(plan.plainText()).contains("INVALID QTY");
        when(orderService.findById(11L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.fulfillConfirmedOrders(plan))
                .hasMessageContaining("below one rounded inventory unit")
                .hasMessageContaining("rebuild the packing list");
        verify(orderService, never()).fulfill(any());
    }

    @Test
    @DisplayName("packing blocks an order unit that differs from its inventory SKU")
    void packingBlocksInventoryUnitMismatch() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer customer = new Customer("Buyer", "", "", "", "MARKET", "");
        CustomerOrder order = new CustomerOrder(customer, day, "CONFIRMED", "");
        order.setId(10L);
        order.addLine(new OrderLine("Nootka Rose", 4, "bunches", 2));
        when(orderService.findBetween(day, day)).thenReturn(List.of(order));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 2, "stems", 1, 50, "")));

        MarketDayPlan plan = service.planForDay(day);

        assertThat(plan.shortfallSkuCount()).isEqualTo(1);
        assertThat(plan.pickList()).singleElement().satisfies(need -> {
            assertThat(need.shortfall()).isFalse();
            assertThat(need.unitMismatch()).isTrue();
            assertThat(need.unit()).isEqualTo("bunches");
            assertThat(need.inventoryUnit()).isEqualTo("stems");
            assertThat(need.blockingIssue()).isTrue();
        });
        assertThat(plan.plainText()).contains("UNIT MISMATCH");
        when(orderService.findById(10L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.fulfillConfirmedOrders(plan))
                .hasMessageContaining("unit mismatch")
                .hasMessageContaining("order bunches")
                .hasMessageContaining("inventory stems");
        verify(orderService, never()).fulfill(any());
    }

    @Test
    @DisplayName("packing accepts legacy per-unit labels equivalent to order units")
    void packingAcceptsEquivalentInventoryUnitLabel() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer customer = new Customer("Buyer", "", "", "", "MARKET", "");
        CustomerOrder order = new CustomerOrder(customer, day, "CONFIRMED", "");
        order.setId(12L);
        order.addLine(new OrderLine("Nootka Rose", 4, "stem", 2));
        when(orderService.findBetween(day, day)).thenReturn(List.of(order));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 2, "Per Stem", 1, 50, "")));

        MarketDayPlan plan = service.planForDay(day);

        assertThat(plan.shortfallSkuCount()).isZero();
        assertThat(plan.pickList()).singleElement().satisfies(need -> {
            assertThat(need.unitMismatch()).isFalse();
            assertThat(need.blockingIssue()).isFalse();
            assertThat(need.neededQty()).isEqualTo(4);
        });
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
        when(orderService.fulfillReviewed(eq(10L),
                any(OrderService.OrderReviewSnapshot.class))).thenAnswer(inv -> {
            confirmed.setStatus("FULFILLED");
            return confirmed;
        });
        when(orderService.findById(10L)).thenReturn(Optional.of(confirmed));

        // Plan with drafts included so draft appears as skipped
        MarketDayPlan plan = service.buildPlan(day, 1, true, false);
        var result = service.fulfillConfirmedOrders(plan);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.fulfilled()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.fulfilledOrderIds()).containsExactly(10L);
        assertThat(result.failedOrderIds()).isEmpty();
        ArgumentCaptor<OrderService.OrderReviewSnapshot> review =
                ArgumentCaptor.forClass(OrderService.OrderReviewSnapshot.class);
        verify(orderService).fulfillReviewed(eq(10L), review.capture());
        assertThat(review.getValue()).satisfies(snapshot -> {
            assertThat(snapshot.orderId()).isEqualTo(10L);
            assertThat(snapshot.customerId()).isEqualTo(1L);
            assertThat(snapshot.customerName()).isEqualTo("Kitsap Blooms");
            assertThat(snapshot.status()).isEqualTo("CONFIRMED");
            assertThat(snapshot.orderDate()).isEqualTo(day);
            assertThat(snapshot.lines()).singleElement().satisfies(line -> {
                assertThat(line.productName()).isEqualTo("Nootka Rose");
                assertThat(line.quantity()).isEqualTo(5);
                assertThat(line.unit()).isEqualTo("stems");
                assertThat(line.unitPrice()).isEqualTo(2);
            });
        });
        verify(orderService, never()).fulfill(any());
        assertThat(result.messages().stream().anyMatch(m -> m.contains("FULFILLED") || m.contains("→")))
                .isTrue();
    }

    @Test
    @DisplayName("batch fulfillment reports one failed order without hiding successful orders")
    void batchFulfillReportsPartialFailure() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        var good = new CustomerPack(10L, "Good Farm", "WHOLESALE", "CONFIRMED",
                day, "", List.of(new MarketDayPackingService.PackLine(
                        "Rose", "stems", 1, 2, 2)), 2);
        var shortStock = new CustomerPack(11L, "Short Farm", "WHOLESALE", "CONFIRMED",
                day, "", List.of(new MarketDayPackingService.PackLine(
                        "Tulip", "stems", 1, 3, 3)), 3);
        var plan = new MarketDayPlan(day, day, day, "CONFIRMED", 2, 5,
                List.of(good, shortStock), List.of(), 0, "");

        Customer customer = new Customer("Good Farm", "", "", "", "WHOLESALE", "");
        CustomerOrder currentGood = new CustomerOrder(customer, day, "CONFIRMED", "");
        currentGood.setId(10L);
        currentGood.addLine(new OrderLine("Rose", 1, "stems", 2));
        Customer shortCustomer = new Customer("Short Farm", "", "", "", "WHOLESALE", "");
        CustomerOrder currentShort = new CustomerOrder(shortCustomer, day, "CONFIRMED", "");
        currentShort.setId(11L);
        currentShort.addLine(new OrderLine("Tulip", 1, "stems", 3));
        CustomerOrder fulfilled = new CustomerOrder(customer, day, "FULFILLED", "");
        fulfilled.setId(10L);
        when(orderService.findById(10L)).thenReturn(Optional.of(currentGood));
        when(orderService.findById(11L)).thenReturn(Optional.of(currentShort));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Rose", "Flowers/Plants", 2, "stems", 1, 10, ""),
                new Item("Tulip", "Flowers/Plants", 3, "stems", 1, 10, "")));
        when(orderService.fulfillReviewed(eq(10L),
                any(OrderService.OrderReviewSnapshot.class))).thenReturn(fulfilled);
        when(orderService.fulfillReviewed(eq(11L),
                any(OrderService.OrderReviewSnapshot.class))).thenThrow(
                new IllegalArgumentException("only 2 available"));

        var result = service.fulfillConfirmedOrders(plan);

        assertThat(result.attempted()).isEqualTo(2);
        assertThat(result.fulfilled()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.fulfilledOrderIds()).containsExactly(10L);
        assertThat(result.failedOrderIds()).containsExactly(11L);
        assertThat(result.messages()).anyMatch(message -> message.contains("FAILED"));
        verify(orderService, never()).fulfill(any());
    }

    @Test
    @DisplayName("batch completion rejects an order changed after preflight without moving inventory")
    void batchCompletionBindsEachWriteToPreflightReview() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer customer = new Customer("Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "FLORIST", "Gate code 12");
        customer.setId(7L);
        CustomerOrder order = new CustomerOrder(customer, day, "CONFIRMED", "Back porch");
        order.setId(31L);
        order.addLine(new OrderLine("Dahlia Mix", 4, "bunches", 12));
        when(orderService.findBetween(day, day)).thenReturn(List.of(order));
        when(orderService.findById(31L)).thenReturn(Optional.of(order));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 12, "bunches", 5, 20, "")));
        when(orderService.fulfillReviewed(eq(31L),
                any(OrderService.OrderReviewSnapshot.class))).thenThrow(
                new IllegalArgumentException(
                        "Order #31 changed after completion review. No status or inventory was changed. "
                                + "Refresh Customers and Orders, review the current details, then complete it again."));

        MarketDayPlan plan = service.planForDay(day);
        var result = service.fulfillConfirmedOrders(plan);

        assertThat(result.attempted()).isEqualTo(1);
        assertThat(result.fulfilled()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.messages()).singleElement().asString()
                .contains("#31 Kitsap Blooms FAILED", "changed after completion review",
                        "No status or inventory was changed");
        verify(orderService).fulfillReviewed(eq(31L),
                any(OrderService.OrderReviewSnapshot.class));
        verify(orderService, never()).fulfill(any());
    }

    @Test
    @DisplayName("batch completion rejects changed live packing details before any write")
    void batchCompletionRejectsChangedPackingDetails() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer customer = new Customer("Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "FLORIST", "");
        CustomerOrder reviewed = new CustomerOrder(customer, day, "CONFIRMED", "Back porch");
        reviewed.setId(21L);
        reviewed.addLine(new OrderLine("Dahlia Mix", 4, "bunches", 12));
        when(orderService.findBetween(day, day)).thenReturn(List.of(reviewed));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 12, "bunches", 5, 20, "")));
        MarketDayPlan plan = service.planForDay(day);

        CustomerOrder changed = new CustomerOrder(customer, day, "CONFIRMED", "Back porch");
        changed.setId(21L);
        changed.addLine(new OrderLine("Dahlia Mix", 7, "bunches", 12));
        when(orderService.findById(21L)).thenReturn(Optional.of(changed));

        assertThatThrownBy(() -> service.fulfillConfirmedOrders(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Packing details changed")
                .hasMessageContaining("#21 Kitsap Blooms")
                .hasMessageContaining("Rebuild the packing list");
        verify(orderService, never()).fulfill(any());
    }

    @Test
    @DisplayName("batch completion rejects a newly confirmed in-window order before any write")
    void batchCompletionRejectsUnreviewedConfirmedOrder() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer reviewedCustomer = new Customer(
                "Kitsap Blooms", "", "", "", "FLORIST", "");
        CustomerOrder reviewed = new CustomerOrder(
                reviewedCustomer, day, "CONFIRMED", "Back porch");
        reviewed.setId(21L);
        reviewed.addLine(new OrderLine("Dahlia Mix", 4, "bunches", 12));

        Customer newCustomer = new Customer(
                "Harbor Florist", "", "", "", "FLORIST", "");
        CustomerOrder newlyConfirmed = new CustomerOrder(
                newCustomer, day, "CONFIRMED", "Front desk");
        newlyConfirmed.setId(22L);
        newlyConfirmed.addLine(new OrderLine("Tulips", 10, "stems", 3));

        when(orderService.findBetween(day, day))
                .thenReturn(List.of(reviewed), List.of(reviewed, newlyConfirmed));
        when(inventoryService.getAllItems()).thenReturn(List.of(
                new Item("Dahlia Mix", "Flowers/Plants", 12, "bunches", 5, 20, "")));
        MarketDayPlan plan = service.planForDay(day);

        assertThatThrownBy(() -> service.fulfillConfirmedOrders(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("New confirmed work entered this Market Day window")
                .hasMessageContaining("#22 Harbor Florist")
                .hasMessageContaining("Rebuild the packing list")
                .hasMessageContaining("pack every current confirmed load");
        verify(orderService, never()).fulfill(any());
        verify(orderService, never()).findById(any());
    }

    @Test
    @DisplayName("batch completion rechecks aggregate stock before any write")
    void batchCompletionRejectsLiveStockShortfall() {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "FLORIST", "");
        CustomerOrder order = new CustomerOrder(customer, day, "CONFIRMED", "");
        order.setId(22L);
        order.addLine(new OrderLine("Nootka Rose", 8, "stems", 2));
        when(orderService.findBetween(day, day)).thenReturn(List.of(order));
        when(inventoryService.getAllItems()).thenReturn(
                List.of(new Item("Nootka Rose", "Flowers/Plants", 2, "stems", 1, 20, "")),
                List.of(new Item("Nootka Rose", "Flowers/Plants", 2, "stems", 1, 3, "")));
        MarketDayPlan plan = service.planForDay(day);
        when(orderService.findById(22L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.fulfillConfirmedOrders(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current inventory no longer covers")
                .hasMessageContaining("Nootka Rose short 5.0 stems")
                .hasMessageContaining("rebuild the packing list");
        verify(orderService, never()).fulfill(any());
    }

    @Test
    @DisplayName("batch result exposes exact successful and failed order identities")
    void batchResultExposesExactOutcomeOrderIds() {
        var result = new MarketDayPackingService.FulfillBatchResult(
                3, 1, 0, 1, List.of(
                "#41 Kitsap Blooms → FULFILLED ($42.00)",
                "#57 Harbor Florist FAILED: only 2 stems available",
                "#bad malformed → FULFILLED",
                "unstructured success text"));

        assertThat(result.fulfilledOrderIds()).containsExactly(41L);
        assertThat(result.failedOrderIds()).containsExactly(57L);
        assertThat(result.toMap()).containsEntry("fulfilledOrderIds", Set.of(41L))
                .containsEntry("failedOrderIds", Set.of(57L));

        var structured = new MarketDayPackingService.FulfillBatchResult(
                1, 1, 0, 0, List.of("receipt wording can change"),
                Set.of(91L), Set.of());
        assertThat(structured.fulfilledOrderIds()).containsExactly(91L);
    }

    @Test
    @DisplayName("generatePackingPdf returns PDF with magic header")
    void packingPdf() throws Exception {
        LocalDate day = LocalDate.of(2026, 7, 12);
        Customer c = new Customer("Kitsap Blooms", "", "", "", "FLORIST",
                "Deliver to west loading dock");
        c.setId(1L);
        CustomerOrder o = new CustomerOrder(c, day, "CONFIRMED", "AM");
        o.setHandoffType("DELIVERY");
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
        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();
        assertThat(text).contains(
                "Account delivery note: Deliver to west loading dock", "Notes: AM");
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePackingPdf(null));
    }
}
