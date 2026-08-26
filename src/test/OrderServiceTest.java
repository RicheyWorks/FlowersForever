package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.repository.CustomerOrderRepository;
import com.flowerfarm.repository.CustomerRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock CustomerOrderRepository orderRepository;
    @Mock CustomerRepository customerRepository;
    @Mock InventoryService inventoryService;
    @Mock SyncHistoryService syncHistoryService;
    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, customerRepository, inventoryService,
                syncHistoryService, new OrderReportGenerator());
        lenient().when(syncHistoryService.record(any(), any(), anyBoolean(), any(), any(), any()))
                .thenAnswer(inv -> null);
    }

    @Test
    @DisplayName("create() builds order with lines for existing customer")
    void createOrder() {
        Customer customer = new Customer(
                "Market Stall", "", "", "", "MARKET", "North gate load-in");
        customer.setId(3L);
        when(customerRepository.findById(3L)).thenReturn(Optional.of(customer));
        when(orderRepository.save(any())).thenAnswer(inv -> {
            CustomerOrder o = inv.getArgument(0);
            o.setId(10L);
            return o;
        });

        CustomerOrder order = service.create(
                3L,
                LocalDate.of(2026, 7, 10),
                "CONFIRMED",
                "Sat market",
                "MARKET",
                List.of(new OrderLine("Nootka Rose", 20, "bunch", 12.0))
        );

        assertThat(order.getId()).isEqualTo(10L);
        assertThat(order.getLines()).hasSize(1);
        assertThat(order.lineTotal()).isEqualTo(240.0);
        assertThat(order.getHandoffType()).isEqualTo("MARKET");
        assertThat(OrderService.reviewSnapshot(order).handoffType()).isEqualTo("MARKET");
        assertThat(OrderService.reviewSnapshot(order).customerNotes())
                .isEqualTo("North gate load-in");
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_CREATE"), eq(true), any(), any(), eq(1));
    }

    @Test
    @DisplayName("reviewed creation rejects a concurrently added same-day active order")
    void reviewedCreateRejectsChangedDuplicateContext() {
        LocalDate day = LocalDate.of(2026, 9, 12);
        Customer customer = new Customer("Market Stall", "", "", "", "MARKET", "");
        customer.setId(3L);
        CustomerOrder concurrent = new CustomerOrder(
                customer, day, "DRAFT", "Phone order", "PICKUP");
        concurrent.setId(44L);
        when(customerRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomer(3L)).thenReturn(List.of(concurrent));

        assertThatThrownBy(() -> service.createReviewed(
                3L, day, "DRAFT", "", "PICKUP",
                List.of(new OrderLine("Dahlia", 2, "stems", 3)), Set.of(), false))
                .isInstanceOf(OrderService.DuplicateOrderReviewChangedException.class)
                .hasMessageContaining("No order was created")
                .hasMessageContaining("explicitly create a separate order");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("reviewed creation permits an explicitly approved unchanged split order")
    void reviewedCreateAllowsApprovedUnchangedSeparateOrder() {
        LocalDate day = LocalDate.of(2026, 9, 12);
        Customer customer = new Customer("Market Stall", "", "", "", "MARKET", "");
        customer.setId(3L);
        CustomerOrder reviewed = new CustomerOrder(
                customer, day, "CONFIRMED", "Wholesale", "DELIVERY");
        reviewed.setId(44L);
        when(customerRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(customer));
        when(customerRepository.findById(3L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomer(3L)).thenReturn(List.of(reviewed));
        when(orderRepository.save(any())).thenAnswer(invocation -> {
            CustomerOrder order = invocation.getArgument(0);
            order.setId(45L);
            return order;
        });

        CustomerOrder created = service.createReviewed(
                3L, day, "DRAFT", "Separate market load", "MARKET",
                List.of(new OrderLine("Dahlia", 2, "stems", 3)), Set.of(44L), true);

        assertThat(created.getId()).isEqualTo(45L);
        verify(orderRepository).save(any(CustomerOrder.class));
    }

    @Test
    @DisplayName("split-order creation rejects material changes behind an unchanged order id")
    void reviewedCreateRejectsChangedOrderSnapshotWithSameId() {
        LocalDate day = LocalDate.of(2026, 9, 12);
        Customer customer = new Customer("Market Stall", "Sam", "sam@example.test",
                "360-555-0100", "MARKET", "");
        customer.setId(3L);
        CustomerOrder existing = new CustomerOrder(
                customer, day, "DRAFT", "Original gate", "PICKUP");
        existing.setId(44L);
        existing.addLine(new OrderLine("Dahlia", 2, "stems", 3));
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(existing);
        existing.setNotes("Different gate");
        when(customerRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByCustomer(3L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createReviewed(
                3L, day, "DRAFT", "Separate load", "MARKET",
                List.of(new OrderLine("Cosmos", 1, "bunches", 12)),
                Map.of(44L, reviewed), true))
                .isInstanceOf(OrderService.DuplicateOrderReviewChangedException.class)
                .hasMessageContaining("No order was created");
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("fulfill() decrements inventory and marks FULFILLED")
    void fulfillDecrementsInventory() {
        Customer c = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder order = new CustomerOrder(c, LocalDate.now(), "CONFIRMED", "");
        order.setId(5L);
        order.setHandoffType("PICKUP");
        order.addLine(new OrderLine("Nootka Rose", 10, "stems", 2.5));

        when(orderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryService.decrementQuantityByNameStrict("Nootka Rose", 10, "stems"))
                .thenReturn(new Item("Nootka Rose", "Flowers/Plants", 2.5, "stems", 1, 40, ""));

        CustomerOrder result = service.fulfill(5L);

        assertThat(result.getStatus()).isEqualTo("FULFILLED");
        verify(inventoryService).decrementQuantityByNameStrict("Nootka Rose", 10, "stems");
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_FULFILL"), eq(true), any(), any(), eq(1));
    }

    @Test
    @DisplayName("fulfill rejects drafts and stock failures without changing order status")
    void fulfillRequiresConfirmedOrderAndStock() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder draft = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        draft.setId(6L);
        when(orderRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(draft));

        assertThatIllegalArgumentException().isThrownBy(() -> service.fulfill(6L))
                .withMessageContaining("CONFIRMED");
        assertThat(draft.getStatus()).isEqualTo("DRAFT");

        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Call on arrival");
        confirmed.setId(7L);
        confirmed.setHandoffType("DELIVERY");
        confirmed.addLine(new OrderLine("Nootka Rose", 10, "stems", 2.5));
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(confirmed));
        when(inventoryService.decrementQuantityByNameStrict("Nootka Rose", 10, "stems"))
                .thenThrow(new IllegalArgumentException("only 4 available"));

        assertThatIllegalArgumentException().isThrownBy(() -> service.fulfill(7L))
                .withMessageContaining("only 4 available");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        verify(orderRepository, never()).save(confirmed);
    }

    @Test
    @DisplayName("reviewed fulfillment rejects changed live load before inventory mutation")
    void fulfillReviewedRejectsStaleCustomerLoad() {
        Customer reviewedCustomer = new Customer(
                "Kitsap Blooms", "Sam", "sam@example.test", "360-555-0100",
                "WHOLESALE", "");
        reviewedCustomer.setId(1L);
        CustomerOrder reviewed = new CustomerOrder(
                reviewedCustomer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Back porch");
        reviewed.setId(15L);
        OrderLine reviewedLine = new OrderLine("Dahlia Mix", 4, "bunches", 12);
        reviewedLine.setId(101L);
        reviewed.addLine(reviewedLine);
        OrderService.OrderReviewSnapshot snapshot = OrderService.reviewSnapshot(reviewed);

        Customer changedCustomer = new Customer(
                "Kitsap Blooms", "Sam", "sam@example.test", "360-555-0199",
                "WHOLESALE", "");
        changedCustomer.setId(1L);
        CustomerOrder changed = new CustomerOrder(
                changedCustomer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Front desk");
        changed.setId(15L);
        OrderLine changedLine = new OrderLine("Dahlia Mix", 7, "bunches", 12);
        changedLine.setId(101L);
        changed.addLine(changedLine);
        when(orderRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(changed));
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(changedCustomer));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.fulfillReviewed(15L, snapshot))
                .withMessageContainingAll("Order #15 changed after completion review",
                        "No status or inventory was changed", "Refresh Customers and Orders");

        assertThat(changed.getStatus()).isEqualTo("CONFIRMED");
        verify(customerRepository).findByIdForUpdate(1L);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed fulfillment rejects a changed account delivery note")
    void fulfillReviewedRejectsStaleAccountDeliveryNote() {
        Customer reviewedCustomer = new Customer(
                "Harbor Florist", "", "", "", "FLORIST", "West loading dock");
        reviewedCustomer.setId(2L);
        CustomerOrder reviewed = new CustomerOrder(
                reviewedCustomer, LocalDate.now(), "CONFIRMED", "", "DELIVERY");
        reviewed.setId(17L);
        OrderLine reviewedLine = new OrderLine("Dahlia Mix", 4, "bunches", 12);
        reviewedLine.setId(103L);
        reviewed.addLine(reviewedLine);
        OrderService.OrderReviewSnapshot snapshot = OrderService.reviewSnapshot(reviewed);

        Customer changedCustomer = new Customer(
                "Harbor Florist", "", "", "", "FLORIST", "East loading dock");
        changedCustomer.setId(2L);
        CustomerOrder changed = new CustomerOrder(
                changedCustomer, LocalDate.now(), "CONFIRMED", "", "DELIVERY");
        changed.setId(17L);
        OrderLine changedLine = new OrderLine("Dahlia Mix", 4, "bunches", 12);
        changedLine.setId(103L);
        changed.addLine(changedLine);
        when(orderRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(changed));
        when(customerRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(changedCustomer));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.fulfillReviewed(17L, snapshot))
                .withMessageContainingAll("Order #17 changed after completion review",
                        "No status or inventory was changed", "Refresh Customers and Orders");

        assertThat(changed.getStatus()).isEqualTo("CONFIRMED");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed fulfillment decrements inventory only after an exact locked match")
    void fulfillReviewedCompletesExactCustomerLoad() {
        Customer customer = new Customer(
                "Kitsap Blooms", "Sam", "sam@example.test", "360-555-0100",
                "WHOLESALE", "");
        customer.setId(1L);
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Back porch");
        order.setId(16L);
        order.setHandoffType("DELIVERY");
        OrderLine line = new OrderLine("Dahlia Mix", 4, "bunches", 12);
        line.setId(102L);
        order.addLine(line);
        OrderService.OrderReviewSnapshot snapshot = OrderService.reviewSnapshot(order);
        when(orderRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(customer));
        when(inventoryService.decrementQuantityByNameStrict("Dahlia Mix", 4, "bunches"))
                .thenReturn(new Item(
                        "Dahlia Mix", "Flowers/Plants", 12, "bunches", 5, 16, ""));
        when(orderRepository.save(order)).thenReturn(order);

        CustomerOrder saved = service.fulfillReviewed(16L, snapshot);

        assertThat(saved.getStatus()).isEqualTo("FULFILLED");
        verify(customerRepository).findByIdForUpdate(1L);
        verify(inventoryService).decrementQuantityByNameStrict(
                "Dahlia Mix", 4, "bunches");
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("create() throws when customer missing")
    void createMissingCustomer() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> service.create(99L, LocalDate.now(), "DRAFT", "", List.of()));
    }

    @Test
    @DisplayName("create() rejects terminal and unknown statuses")
    void createRejectsInvalidInitialStatus() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.create(1L, LocalDate.now(), "FULFILLED", "", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.create(1L, LocalDate.now(), "CANCELLED", "", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() ->
                service.create(1L, LocalDate.now(), "BANANA", "", List.of()));
        verifyNoInteractions(customerRepository);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("create() rejects null lines before persistence")
    void createRejectsNullLines() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(
                1L, LocalDate.now(), "DRAFT", "",
                java.util.Arrays.asList(new OrderLine("Rose", 1, "stem", 2), null)));
        verifyNoInteractions(customerRepository, orderRepository);
    }

    @Test
    @DisplayName("create rejects a confirmed order without products before persistence")
    void createRejectsConfirmedOrderWithoutProducts() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(
                        1L, LocalDate.now(), "CONFIRMED", "", List.of()))
                .withMessageContaining("at least one product");

        verifyNoInteractions(customerRepository, orderRepository);
    }

    @Test
    @DisplayName("create rejects a context-free confirmed Delivery")
    void createRejectsContextFreeConfirmedDelivery() {
        Customer customer = new Customer(
                "Harbor Florist", "", "", "", "FLORIST", "");
        customer.setId(3L);
        when(customerRepository.findById(3L)).thenReturn(Optional.of(customer));

        assertThatIllegalArgumentException().isThrownBy(() -> service.create(
                        3L, LocalDate.now(), "CONFIRMED", "", "DELIVERY",
                        List.of(new OrderLine("Dahlia Mix", 4, "bunches", 12))))
                .withMessageContainingAll("New Delivery order", "customer contact",
                        "account delivery notes", "order delivery instructions",
                        "No status or inventory was changed");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("fulfill() rejects an empty confirmed order")
    void fulfillRejectsEmptyOrder() {
        CustomerOrder order = new CustomerOrder(
                new Customer("A", "", "", "", "WHOLESALE", ""),
                LocalDate.now(), "CONFIRMED", "");
        order.setId(12L);
        order.setHandoffType("PICKUP");
        when(orderRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(order));

        assertThatIllegalArgumentException().isThrownBy(() -> service.fulfill(12L))
                .withMessageContaining("no lines");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("fulfill rejects a context-free Delivery before inventory mutation")
    void fulfillRejectsContextFreeDelivery() {
        Customer customer = new Customer(
                "Harbor Florist", "", "", "", "FLORIST", "");
        customer.setId(3L);
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "", "DELIVERY");
        order.setId(18L);
        order.addLine(new OrderLine("Dahlia Mix", 4, "bunches", 12));
        when(orderRepository.findByIdForUpdate(18L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(customer));

        assertThatIllegalArgumentException().isThrownBy(() -> service.fulfill(18L))
                .withMessageContainingAll("Delivery order #18", "customer contact",
                        "account delivery notes", "order delivery instructions",
                        "No status or inventory was changed");

        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("fulfill rejects a saved line below one rounded inventory unit")
    void fulfillRejectsSubUnitLineWithoutMutation() {
        CustomerOrder order = new CustomerOrder(
                new Customer("A", "", "", "", "WHOLESALE", ""),
                LocalDate.now(), "CONFIRMED", "");
        order.setId(13L);
        order.setHandoffType("PICKUP");
        order.addLine(new OrderLine("Dahlia Mix", 0.4, "stems", 3.5));
        when(orderRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(order));

        assertThatIllegalArgumentException().isThrownBy(() -> service.fulfill(13L))
                .withMessageContaining("round to at least one inventory unit");
        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("findBetween() bounds database query windows")
    void findBetweenRejectsUnboundedRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.findBetween(
                LocalDate.of(2000, 1, 1), LocalDate.of(2026, 1, 1)))
                .withMessageContaining("10 years");
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("confirm() sets CONFIRMED and audits ORDER_STATUS")
    void confirm() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder order = new CustomerOrder(c, LocalDate.now(), "DRAFT", "");
        order.setId(8L);
        order.setHandoffType("MARKET");
        order.addLine(new OrderLine("Rose", 1, "stems", 2.0));
        when(orderRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerOrder saved = service.confirm(8L);
        assertThat(saved.getStatus()).isEqualTo("CONFIRMED");
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_STATUS"), eq(true), any(), any(), eq(1));
    }

    @Test
    @DisplayName("confirm rejects a context-free Delivery before status mutation")
    void confirmRejectsContextFreeDelivery() {
        Customer customer = new Customer(
                "Harbor Florist", "", "", "", "FLORIST", "");
        customer.setId(3L);
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "", "DELIVERY");
        order.setId(31L);
        order.addLine(new OrderLine("Dahlia Mix", 4, "bunches", 12));
        when(orderRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(customer));

        assertThatIllegalArgumentException().isThrownBy(() -> service.confirm(31L))
                .withMessageContainingAll("Delivery order #31", "customer contact",
                        "account delivery notes", "order delivery instructions",
                        "No status or inventory was changed");

        assertThat(order.getStatus()).isEqualTo("DRAFT");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed confirmation rejects changed live details before status mutation")
    void confirmReviewedRejectsStaleCustomerLoad() {
        Customer reviewedCustomer = new Customer(
                "Kitsap Blooms", "Sam", "sam@example.test", "360-555-0100",
                "WHOLESALE", "");
        reviewedCustomer.setId(1L);
        CustomerOrder reviewed = new CustomerOrder(
                reviewedCustomer, LocalDate.of(2026, 9, 12), "DRAFT", "Back porch");
        reviewed.setId(28L);
        OrderLine reviewedLine = new OrderLine("Dahlia Mix", 4, "bunches", 12);
        reviewedLine.setId(101L);
        reviewed.addLine(reviewedLine);
        OrderService.OrderReviewSnapshot snapshot = OrderService.reviewSnapshot(reviewed);

        Customer changedCustomer = new Customer(
                "Kitsap Blooms", "Sam", "sam@example.test", "360-555-0199",
                "WHOLESALE", "");
        changedCustomer.setId(1L);
        CustomerOrder changed = new CustomerOrder(
                changedCustomer, LocalDate.of(2026, 9, 12), "DRAFT", "Back porch");
        changed.setId(28L);
        OrderLine changedLine = new OrderLine("Dahlia Mix", 7, "bunches", 12);
        changedLine.setId(101L);
        changed.addLine(changedLine);
        when(orderRepository.findByIdForUpdate(28L)).thenReturn(Optional.of(changed));
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(changedCustomer));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.confirmReviewed(28L, snapshot))
                .withMessageContainingAll("Order #28 changed after confirmation review",
                        "No status was changed", "Refresh Customers and Orders");

        assertThat(changed.getStatus()).isEqualTo("DRAFT");
        verify(customerRepository).findByIdForUpdate(1L);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(syncHistoryService, inventoryService);
    }

    @Test
    @DisplayName("reviewed confirmation rejects a changed account delivery note")
    void confirmReviewedRejectsStaleAccountDeliveryNote() {
        Customer reviewedCustomer = new Customer(
                "Harbor Florist", "", "", "", "FLORIST", "West loading dock");
        reviewedCustomer.setId(2L);
        CustomerOrder reviewed = new CustomerOrder(
                reviewedCustomer, LocalDate.now(), "DRAFT", "", "DELIVERY");
        reviewed.setId(30L);
        OrderLine reviewedLine = new OrderLine("Dahlia Mix", 4, "bunches", 12);
        reviewedLine.setId(104L);
        reviewed.addLine(reviewedLine);
        OrderService.OrderReviewSnapshot snapshot = OrderService.reviewSnapshot(reviewed);

        Customer changedCustomer = new Customer(
                "Harbor Florist", "", "", "", "FLORIST", "East loading dock");
        changedCustomer.setId(2L);
        CustomerOrder changed = new CustomerOrder(
                changedCustomer, LocalDate.now(), "DRAFT", "", "DELIVERY");
        changed.setId(30L);
        OrderLine changedLine = new OrderLine("Dahlia Mix", 4, "bunches", 12);
        changedLine.setId(104L);
        changed.addLine(changedLine);
        when(orderRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(changed));
        when(customerRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(changedCustomer));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.confirmReviewed(30L, snapshot))
                .withMessageContainingAll("Order #30 changed after confirmation review",
                        "No status was changed", "Refresh Customers and Orders");

        assertThat(changed.getStatus()).isEqualTo("DRAFT");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(syncHistoryService, inventoryService);
    }

    @Test
    @DisplayName("reviewed confirmation ignores harmless database line ordering")
    void confirmReviewedUsesCanonicalLineOrder() {
        Customer customer = new Customer(
                "Kitsap Blooms", "Sam", "sam@example.test", "360-555-0100",
                "WHOLESALE", "");
        customer.setId(1L);
        CustomerOrder reviewed = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back porch");
        reviewed.setId(29L);
        reviewed.setHandoffType("PICKUP");
        OrderLine roses = new OrderLine("Rose Bunch", 2, "bunches", 18);
        roses.setId(102L);
        OrderLine dahlias = new OrderLine("Dahlia Mix", 4, "bunches", 12);
        dahlias.setId(101L);
        reviewed.addLine(roses);
        reviewed.addLine(dahlias);
        OrderService.OrderReviewSnapshot snapshot = OrderService.reviewSnapshot(reviewed);

        CustomerOrder live = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back porch");
        live.setId(29L);
        live.setHandoffType("PICKUP");
        OrderLine liveDahlias = new OrderLine("Dahlia Mix", 4, "bunches", 12);
        liveDahlias.setId(101L);
        OrderLine liveRoses = new OrderLine("Rose Bunch", 2, "bunches", 18);
        liveRoses.setId(102L);
        live.addLine(liveDahlias);
        live.addLine(liveRoses);
        when(orderRepository.findByIdForUpdate(29L)).thenReturn(Optional.of(live));
        when(customerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(customer));
        when(orderRepository.save(live)).thenReturn(live);

        CustomerOrder saved = service.confirmReviewed(29L, snapshot);

        assertThat(saved.getStatus()).isEqualTo("CONFIRMED");
        verify(orderRepository).save(live);
    }

    @Test
    @DisplayName("confirm rejects an empty draft without saving or auditing")
    void confirmRejectsEmptyDraftWithoutMutation() {
        Customer customer = new Customer("A", "", "", "", "WHOLESALE", "");
        customer.setId(1L);
        CustomerOrder order = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        order.setId(18L);
        when(orderRepository.findByIdForUpdate(18L)).thenReturn(Optional.of(order));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.confirm(18L))
                .withMessageContaining("at least one product");

        assertThat(order.getStatus()).isEqualTo("DRAFT");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(syncHistoryService, inventoryService);
    }

    @Test
    @DisplayName("confirmation and completion reject an unspecified handoff without mutation")
    void lifecycleRejectsUnspecifiedHandoffWithoutMutation() {
        Customer customer = new Customer("A", "", "", "", "WHOLESALE", "");
        customer.setId(1L);
        CustomerOrder draft = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        draft.setId(19L);
        draft.addLine(new OrderLine("Rose", 1, "stems", 2));
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "");
        confirmed.setId(20L);
        confirmed.addLine(new OrderLine("Rose", 1, "stems", 2));
        when(orderRepository.findByIdForUpdate(19L)).thenReturn(Optional.of(draft));
        when(orderRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(confirmed));

        assertThatIllegalArgumentException().isThrownBy(() -> service.confirm(19L))
                .withMessageContainingAll("PICKUP, DELIVERY, or MARKET", "order #19",
                        "No status or inventory was changed");
        assertThatIllegalArgumentException().isThrownBy(() -> service.fulfill(20L))
                .withMessageContainingAll("PICKUP, DELIVERY, or MARKET", "order #20",
                        "No status or inventory was changed");

        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("confirmed creation and handoff corrections cannot save unspecified")
    void writesRejectUnspecifiedOperationalHandoff() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(
                        1L, LocalDate.now(), "CONFIRMED", "",
                        List.of(new OrderLine("Rose", 1, "stems", 2))))
                .withMessageContaining("PICKUP, DELIVERY, or MARKET");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateHandoffType(22L, "UNSPECIFIED"))
                .withMessageContainingAll("PICKUP, DELIVERY, or MARKET", "cannot be saved");

        verifyNoInteractions(customerRepository, orderRepository, inventoryService);
    }

    @Test
    @DisplayName("cannot un-fulfill via status change")
    void cannotUnfulfill() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder order = new CustomerOrder(c, LocalDate.now(), "FULFILLED", "");
        order.setId(9L);
        when(orderRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(order));

        assertThatIllegalArgumentException().isThrownBy(() -> service.updateStatus(9L, "CANCELLED"));
    }

    @Test
    @DisplayName("terminal orders reject line additions and fulfilled deletion")
    void terminalOrdersRejectUnsafeMutations() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        CustomerOrder fulfilled = new CustomerOrder(c, LocalDate.now(), "FULFILLED", "");
        fulfilled.setId(11L);
        when(orderRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(fulfilled));

        assertThatIllegalArgumentException().isThrownBy(() -> service.addLine(
                11L, new OrderLine("Rose", 1, "stem", 2.0)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.updateLine(
                11L, 101L, new OrderLine("Rose", 2, "stems", 2.5)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.updateOrderDate(
                11L, LocalDate.now().plusDays(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.delete(11L));
        verify(orderRepository, never()).deleteById(11L);
    }

    @Test
    @DisplayName("reviewed cancellation changes only an unchanged order without inventory")
    void cancelReviewedCancelsExactOrder() {
        Customer customer = new Customer("Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "WHOLESALE", "");
        customer.setId(7L);
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Side gate");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(order);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));
        when(orderRepository.save(order)).thenReturn(order);

        CustomerOrder saved = service.cancelReviewed(42L, reviewed);

        assertThat(saved.getStatus()).isEqualTo("CANCELLED");
        verify(orderRepository).save(order);
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_STATUS"), eq(true),
                contains("CONFIRMED → CANCELLED"), contains("Side gate"), eq(1));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("reviewed cancellation rejects a changed load without cancelling it")
    void cancelReviewedRejectsStaleOrder() {
        Customer customer = new Customer("Kitsap Blooms", "Sam", "sam@example.test",
                "360-555-0100", "WHOLESALE", "");
        customer.setId(7L);
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Front desk");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 15, "stems", 3.5));
        OrderService.OrderReviewSnapshot reviewed = new OrderService.OrderReviewSnapshot(
                42L, 7L, "Kitsap Blooms", "Sam", "360-555-0100", "sam@example.test",
                "CONFIRMED", LocalDate.of(2026, 9, 12), "Side gate",
                List.of(new OrderService.OrderReviewLine(
                        null, "Dahlia Mix", 12, "stems", 3.5)));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.cancelReviewed(42L, reviewed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changed after cancellation review")
                .hasMessageContaining("not cancelled")
                .hasMessageContaining("inventory was unchanged")
                .hasMessageContaining("review the cancellation again");

        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed deletion removes only the unchanged selected order")
    void deleteReviewedDeletesExactOrder() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        customer.setId(7L);
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Side gate");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(order);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));

        service.deleteReviewed(42L, reviewed);

        verify(orderRepository).deleteById(42L);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed deletion rejects changed details without deleting anything")
    void deleteReviewedRejectsStaleOrder() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        customer.setId(7L);
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 14), "DRAFT", "Front desk");
        order.setId(42L);
        order.addLine(new OrderLine("Dahlia Mix", 15, "stems", 3.5));
        OrderService.OrderReviewSnapshot reviewed = new OrderService.OrderReviewSnapshot(
                42L, 7L, "Kitsap Blooms", "", "", "", "DRAFT",
                LocalDate.of(2026, 9, 12), "Side gate",
                List.of(new OrderService.OrderReviewLine(
                        null, "Dahlia Mix", 12, "stems", 3.5)));
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.deleteReviewed(42L, reviewed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changed after deletion review")
                .hasMessageContaining("not deleted")
                .hasMessageContaining("inventory was unchanged")
                .hasMessageContaining("review the deletion again");

        verify(orderRepository, never()).deleteById(anyLong());
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed product edit updates the matching captured line without inventory")
    void updateLineCorrectsCapturedProductWithoutInventoryChange() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(customer, LocalDate.now(), "CONFIRMED", "");
        order.setId(41L);
        OrderLine product = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        product.setId(100L);
        OrderLine untouched = new OrderLine("Rose Bunch", 2, "bunches", 18);
        untouched.setId(101L);
        order.addLine(product);
        order.addLine(untouched);
        when(orderRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderService.OrderReviewLine reviewedLine = OrderService.reviewLine(product);
        CustomerOrder saved = service.updateLineReviewed(
                41L, 100L, reviewedLine,
                new OrderLine("Dahlia Mix", 18, "stems", 4.25));

        assertThat(saved.getLines()).hasSize(2);
        assertThat(saved.getLines().get(0).getId()).isEqualTo(100L);
        assertThat(saved.getLines().get(0).getProductName()).isEqualTo("Dahlia Mix");
        assertThat(saved.getLines().get(0).getQuantity()).isEqualTo(18);
        assertThat(saved.getLines().get(0).getUnitPrice()).isEqualTo(4.25);
        assertThat(saved.getLines().get(1)).isSameAs(untouched);
        assertThat(saved.lineTotal()).isEqualTo(112.5);
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_LINE_UPDATE"), eq(true),
                contains("Dahlia Mix"), contains("lineId=100"), eq(1));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("reviewed product add writes only to the exact displayed order")
    void addLineReviewedAddsToExactDisplayedOrder() {
        Customer customer = new Customer(
                "Kitsap Blooms", "Sam", "sam@example.test", "360-555-0100",
                "WHOLESALE", "Call on arrival");
        customer.setId(7L);
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        order.setId(41L);
        OrderLine existing = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        existing.setId(100L);
        order.addLine(existing);
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(order);
        when(orderRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));
        when(orderRepository.save(order)).thenReturn(order);

        CustomerOrder saved = service.addLineReviewed(
                41L, reviewed, new OrderLine("Rose Bunch", 2, "bunches", 18));

        assertThat(saved.getLines()).hasSize(2);
        assertThat(saved.getLines().get(1).getProductName()).isEqualTo("Rose Bunch");
        assertThat(saved.lineTotal()).isEqualTo(78);
        verify(orderRepository).save(order);
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed product add rejects a lifecycle or load change without a partial write")
    void addLineReviewedRejectsChangedDisplayedOrder() {
        Customer customer = new Customer(
                "Kitsap Blooms", "Sam", "sam@example.test", "360-555-0100",
                "WHOLESALE", "");
        customer.setId(7L);
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        order.setId(41L);
        OrderLine existing = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        existing.setId(100L);
        order.addLine(existing);
        OrderService.OrderReviewSnapshot reviewed = OrderService.reviewSnapshot(order);
        order.setStatus("CONFIRMED");
        order.setNotes("Front counter");
        when(orderRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.addLineReviewed(
                41L, reviewed, new OrderLine("Rose Bunch", 2, "bunches", 18)))
                .isInstanceOfSatisfying(OrderService.StaleOrderReviewException.class,
                        exception -> assertThat(exception.orderMissing()).isFalse())
                .hasMessageContaining("changed after product entry review")
                .hasMessageContaining("product was not added")
                .hasMessageContaining("inventory was unchanged");

        assertThat(order.getLines()).singleElement().isSameAs(existing);
        assertThat(order.getStatus()).isEqualTo("CONFIRMED");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed product add reports an order deleted during entry without writing")
    void addLineReviewedRejectsDeletedOrder() {
        OrderService.OrderReviewSnapshot reviewed = new OrderService.OrderReviewSnapshot(
                41L, 7L, "Kitsap Blooms", "Sam", "360-555-0100",
                "sam@example.test", "DRAFT", LocalDate.of(2026, 9, 12),
                "Back dock", List.of());
        when(orderRepository.findByIdForUpdate(41L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addLineReviewed(
                41L, reviewed, new OrderLine("Rose Bunch", 2, "bunches", 18)))
                .isInstanceOfSatisfying(OrderService.StaleOrderReviewException.class,
                        exception -> assertThat(exception.orderMissing()).isTrue())
                .hasMessageContaining("changed after product entry review")
                .hasMessageContaining("product was not added");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(customerRepository, inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed product edit rejects a newer saved line without overwriting it")
    void updateLineReviewedRejectsLostUpdate() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(customer, LocalDate.now(), "CONFIRMED", "");
        order.setId(41L);
        OrderLine liveLine = new OrderLine("Dahlia Mix", 15, "stems", 3.75);
        liveLine.setId(100L);
        order.addLine(liveLine);
        when(orderRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(order));
        OrderService.OrderReviewLine reviewedLine = new OrderService.OrderReviewLine(
                100L, "Dahlia Mix", 12, "stems", 3.5);

        assertThatThrownBy(() -> service.updateLineReviewed(
                41L, 100L, reviewedLine,
                new OrderLine("Dahlia Mix", 18, "stems", 4.25)))
                .isInstanceOfSatisfying(OrderService.StaleOrderLineException.class, failure -> {
                    assertThat(failure.getMessage()).contains(
                            "changed after you started editing", "15.0 stems", "$3.75",
                            "No order or inventory value was changed");
                    assertThat(failure.currentLine()).isEqualTo(OrderService.reviewLine(liveLine));
                });

        assertThat(liveLine.getQuantity()).isEqualTo(15);
        assertThat(liveLine.getUnitPrice()).isEqualTo(3.75);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed product edit rejects changed order context without overwriting the line")
    void updateLineReviewedRejectsChangedOrderContext() {
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam",
                "sam@example.test", "360-555-0100", "WHOLESALE", "Call on arrival");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "Back dock");
        order.setId(41L);
        order.setHandoffType("PICKUP");
        OrderLine line = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        line.setId(100L);
        order.addLine(line);
        OrderService.OrderReviewSnapshot reviewedOrder = OrderService.reviewSnapshot(order);
        OrderService.OrderReviewLine reviewedLine = OrderService.reviewLine(line);
        order.setHandoffType("DELIVERY");
        order.setNotes("Deliver to the front counter");
        when(orderRepository.findByIdForUpdate(41L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.updateLineReviewed(
                41L, 100L, reviewedOrder, reviewedLine,
                new OrderLine("Dahlia Mix", 18, "stems", 4.25)))
                .isInstanceOf(OrderService.StaleOrderReviewException.class)
                .hasMessageContaining("changed after product correction review")
                .hasMessageContaining("correction was not saved")
                .hasMessageContaining("inventory was unchanged");

        assertThat(line.getQuantity()).isEqualTo(12);
        assertThat(line.getUnitPrice()).isEqualTo(3.5);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed removal deletes only the matching captured product without inventory")
    void removeLineRemovesCapturedDraftProductWithoutInventoryChange() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        order.setId(42L);
        OrderLine dahlias = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        dahlias.setId(101L);
        OrderLine roses = new OrderLine("Rose Bunch", 2, "bunches", 18);
        roses.setId(102L);
        order.addLine(dahlias);
        order.addLine(roses);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        CustomerOrder saved = service.removeLineReviewed(
                42L, 102L, OrderService.reviewLine(roses));

        assertThat(saved.getLines())
                .extracting(OrderLine::getId)
                .containsExactly(101L);
        assertThat(saved.lineTotal()).isEqualTo(42.0);
        verify(orderRepository).save(order);
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_LINE_REMOVE"), eq(true),
                contains("Rose Bunch"), contains("lineId=102"), eq(1));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("reviewed removal rejects a newer saved line without deleting it")
    void removeLineReviewedRejectsLostUpdate() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(customer, LocalDate.now(), "DRAFT", "");
        order.setId(42L);
        OrderLine keep = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        keep.setId(101L);
        OrderLine changed = new OrderLine("Rose Bunch", 3, "bunches", 20);
        changed.setId(102L);
        order.addLine(keep);
        order.addLine(changed);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        OrderService.OrderReviewLine reviewedLine = new OrderService.OrderReviewLine(
                102L, "Rose Bunch", 2, "bunches", 18);

        assertThatThrownBy(() -> service.removeLineReviewed(42L, 102L, reviewedLine))
                .isInstanceOfSatisfying(OrderService.StaleOrderLineException.class, failure -> {
                    assertThat(failure.getMessage()).contains(
                            "changed after you started removing it", "3.0 bunches", "$20.00",
                            "No order or inventory value was changed");
                    assertThat(failure.currentLine()).isEqualTo(OrderService.reviewLine(changed));
                });

        assertThat(order.getLines()).containsExactly(keep, changed);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed removal rejects changed order context without deleting the line")
    void removeLineReviewedRejectsChangedOrderContext() {
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam",
                "sam@example.test", "360-555-0100", "WHOLESALE", "Call on arrival");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "Back dock");
        order.setId(42L);
        order.setHandoffType("PICKUP");
        OrderLine keep = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        keep.setId(101L);
        OrderLine remove = new OrderLine("Rose Bunch", 2, "bunches", 18);
        remove.setId(102L);
        order.addLine(keep);
        order.addLine(remove);
        OrderService.OrderReviewSnapshot reviewedOrder = OrderService.reviewSnapshot(order);
        OrderService.OrderReviewLine reviewedLine = OrderService.reviewLine(remove);
        order.setHandoffType("DELIVERY");
        order.setNotes("Front counter");
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(order));
        when(customerRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.removeLineReviewed(
                42L, 102L, reviewedOrder, reviewedLine))
                .isInstanceOf(OrderService.StaleOrderReviewException.class)
                .hasMessageContaining("changed after product removal review")
                .hasMessageContaining("Nothing was removed")
                .hasMessageContaining("inventory was unchanged");

        assertThat(order.getLines()).containsExactly(keep, remove);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("removeLine() protects confirmed last products and terminal orders")
    void removeLineRejectsUnsafeLifecycleChanges() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "");
        confirmed.setId(43L);
        OrderLine lastProduct = new OrderLine("Dahlia Mix", 12, "stems", 3.5);
        lastProduct.setId(103L);
        confirmed.addLine(lastProduct);
        CustomerOrder fulfilled = new CustomerOrder(
                customer, LocalDate.now(), "FULFILLED", "");
        fulfilled.setId(44L);
        OrderLine shippedProduct = new OrderLine("Rose Bunch", 2, "bunches", 18);
        shippedProduct.setId(104L);
        fulfilled.addLine(shippedProduct);
        when(orderRepository.findByIdForUpdate(43L)).thenReturn(Optional.of(confirmed));
        when(orderRepository.findByIdForUpdate(44L)).thenReturn(Optional.of(fulfilled));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.removeLine(43L, 103L))
                .withMessageContaining("add the replacement first");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.removeLine(44L, 104L))
                .withMessageContaining("FULFILLED");

        assertThat(confirmed.getLines()).containsExactly(lastProduct);
        assertThat(fulfilled.getLines()).containsExactly(shippedProduct);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("updateStatus() rejects missing and unknown statuses")
    void rejectsUnknownStatus() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.updateStatus(1L, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.updateStatus(1L, "UNKNOWN"));
        verify(orderRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("filter() applies status and customer substring")
    void filterOrders() {
        Customer a = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        a.setId(1L);
        Customer b = new Customer("Market Stall", "", "", "", "MARKET", "");
        b.setId(2L);
        CustomerOrder o1 = new CustomerOrder(a, LocalDate.of(2026, 7, 1), "CONFIRMED", "");
        o1.setId(1L);
        o1.setHandoffType("UNSPECIFIED");
        CustomerOrder o2 = new CustomerOrder(b, LocalDate.of(2026, 7, 5), "DRAFT", "");
        o2.setId(2L);
        o2.setHandoffType("PICKUP");
        CustomerOrder o3 = new CustomerOrder(a, LocalDate.of(2026, 7, 8), "FULFILLED", "");
        o3.setId(3L);
        o3.setHandoffType("UNSPECIFIED");
        when(orderRepository.findAllNewestFirst()).thenReturn(List.of(o3, o2, o1));

        assertThat(service.filter("CONFIRMED", "kitsap", null, null)).hasSize(1);
        assertThat(service.filter("ALL", "market", null, null)).hasSize(1);
        assertThat(service.filter(null, null,
                LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 10))).hasSize(2);
        assertThat(service.filter("OPEN", "", null, null, "UNSPECIFIED"))
                .extracting(CustomerOrder::getId)
                .containsExactly(1L);
        assertThat(service.filter("ALL", "", null, null, "PICKUP"))
                .extracting(CustomerOrder::getId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("confirmed completion queue ignores UI filters and uses stable handoff priority")
    void confirmedCompletionQueueIsCompleteAndDeterministic() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder later = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 14), "CONFIRMED", "");
        later.setId(1L);
        CustomerOrder sameDaySecond = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "");
        sameDaySecond.setId(12L);
        CustomerOrder sameDayFirst = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "");
        sameDayFirst.setId(11L);
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 10), "DRAFT", "");
        draft.setId(2L);
        when(orderRepository.findAllNewestFirst()).thenReturn(
                List.of(later, draft, sameDaySecond, sameDayFirst));

        assertThat(service.confirmedCompletionQueue())
                .extracting(CustomerOrder::getId)
                .containsExactly(11L, 12L, 1L);
    }

    @Test
    @DisplayName("reviewed instructions persist and audit when the saved value still matches")
    void updateNotes() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder order = new CustomerOrder(c, LocalDate.now(), "DRAFT", "old");
        order.setId(3L);
        when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerOrder saved = service.updateNotesReviewed(3L, "old", "pickup Saturday");
        assertThat(saved.getNotes()).isEqualTo("pickup Saturday");
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_NOTES_UPDATE"), eq(true),
                eq("Updated instructions for order #3"),
                contains("inventoryChanged=false"), eq(1));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("updateNotes keeps completed and cancelled order instructions historical")
    void updateNotesRejectsTerminalOrders() {
        Customer customer = new Customer("A", "", "", "", "WHOLESALE", "");
        for (String status : List.of("FULFILLED", "CANCELLED")) {
            CustomerOrder order = new CustomerOrder(
                    customer, LocalDate.now(), status, "original instructions");
            order.setId(3L);
            when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.updateNotes(3L, "changed"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot change the instructions")
                    .hasMessageContaining(status);
        }
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("reviewed instructions reject a newer saved value without overwriting it")
    void updateNotesReviewedRejectsLostUpdate() {
        Customer customer = new Customer("A", "", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Use the side gate");
        order.setId(3L);
        when(orderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateNotesReviewed(
                3L, "Leave at packing shed", "Call before arrival"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instructions changed after you started editing")
                .hasMessageContaining("Use the side gate")
                .hasMessageContaining("No order or inventory value was changed")
                .hasMessageContaining("review the current instructions");

        assertThat(order.getNotes()).isEqualTo("Use the side gate");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed delivery date reschedules matching open work without inventory")
    void updateOrderDateReschedulesOpenOrderWithoutInventoryChange() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "pickup");
        order.setId(45L);
        when(orderRepository.findByIdForUpdate(45L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        CustomerOrder saved = service.updateOrderDateReviewed(
                45L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13));

        assertThat(saved.getOrderDate()).isEqualTo(LocalDate.of(2026, 9, 13));
        verify(orderRepository).save(order);
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_DATE_UPDATE"), eq(true),
                contains("2026-09-13"), contains("previous=2026-09-12"), eq(1));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("reviewed delivery date rejects a newer saved date without overwriting it")
    void updateOrderDateReviewedRejectsLostUpdate() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 14), "CONFIRMED", "pickup");
        order.setId(45L);
        when(orderRepository.findByIdForUpdate(45L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateOrderDateReviewed(
                45L, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 13)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pickup or delivery date changed after you started editing")
                .hasMessageContaining("saved date is now 2026-09-14")
                .hasMessageContaining("No order or inventory value was changed")
                .hasMessageContaining("review the current date");

        assertThat(order.getOrderDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("reviewed handoff correction updates matching open work without inventory")
    void updateHandoffTypeReviewedUpdatesOpenOrder() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "loading dock");
        order.setId(46L);
        order.setHandoffType("PICKUP");
        when(orderRepository.findByIdForUpdate(46L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        CustomerOrder saved = service.updateHandoffTypeReviewed(
                46L, "PICKUP", "DELIVERY");

        assertThat(saved.getHandoffType()).isEqualTo("DELIVERY");
        verify(orderRepository).save(order);
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_HANDOFF_UPDATE"), eq(true),
                contains("DELIVERY"), contains("previous=PICKUP, current=DELIVERY"), eq(1));
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("reviewed handoff correction rejects a newer saved value without overwriting it")
    void updateHandoffTypeReviewedRejectsLostUpdate() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "CONFIRMED", "loading dock");
        order.setId(46L);
        order.setHandoffType("MARKET");
        when(orderRepository.findByIdForUpdate(46L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.updateHandoffTypeReviewed(
                46L, "PICKUP", "DELIVERY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("handoff type changed after you started editing")
                .hasMessageContaining("saved handoff is now MARKET")
                .hasMessageContaining("No order or inventory value was changed")
                .hasMessageContaining("review the current handoff");

        assertThat(order.getHandoffType()).isEqualTo("MARKET");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("handoff correction rejects terminal orders")
    void updateHandoffTypeRejectsTerminalOrders() {
        Customer customer = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        for (String status : List.of("FULFILLED", "CANCELLED")) {
            CustomerOrder order = new CustomerOrder(
                    customer, LocalDate.of(2026, 9, 12), status, "");
            order.setId(47L);
            order.setHandoffType("PICKUP");
            when(orderRepository.findByIdForUpdate(47L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.updateHandoffType(47L, "DELIVERY"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot change the handoff type")
                    .hasMessageContaining(status);
        }
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(inventoryService, syncHistoryService);
    }

    @Test
    @DisplayName("fulfilledRevenueBetween sums CONFIRMED and FULFILLED (booked)")
    void revenue() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder ok = new CustomerOrder(c, LocalDate.now(), "FULFILLED", "");
        ok.addLine(new OrderLine("Roses", 10, "bunch", 5.0));
        CustomerOrder confirmed = new CustomerOrder(c, LocalDate.now(), "CONFIRMED", "");
        confirmed.addLine(new OrderLine("Roses", 2, "bunch", 10.0));
        CustomerOrder draft = new CustomerOrder(c, LocalDate.now(), "DRAFT", "");
        draft.addLine(new OrderLine("Roses", 100, "bunch", 5.0));

        when(orderRepository.findBetween(any(), any()))
                .thenReturn(List.of(ok, confirmed, draft));

        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now();
        assertThat(service.fulfilledRevenueBetween(from, to)).isEqualTo(70.0);
        assertThat(service.realizedRevenueBetween(from, to)).isEqualTo(50.0);
        assertThat(service.pipelineRevenueBetween(from, to)).isEqualTo(20.0);
    }

    @Test
    @DisplayName("dailyRevenueLast7Days defaults to FULFILLED (realized) only")
    void dailyRevenue() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        LocalDate today = LocalDate.now();
        CustomerOrder confirmed = new CustomerOrder(c, today, "CONFIRMED", "");
        confirmed.addLine(new OrderLine("Roses", 2, "bunch", 10.0));
        CustomerOrder old = new CustomerOrder(c, today.minusDays(3), "FULFILLED", "");
        old.addLine(new OrderLine("Dahlia", 1, "bunch", 15.0));
        CustomerOrder draft = new CustomerOrder(c, today, "DRAFT", "");
        draft.addLine(new OrderLine("Skip", 99, "bunch", 1.0));

        when(orderRepository.findBetween(any(), any()))
                .thenReturn(List.of(confirmed, old, draft));

        double[] realized = service.dailyRevenueLast7Days();
        assertThat(realized).hasSize(7);
        assertThat(realized[6]).isEqualTo(0.0); // confirmed not counted as realized
        assertThat(realized[3]).isEqualTo(15.0);
        assertThat(realized[0]).isEqualTo(0.0);

        double[] booked = service.dailyRevenueLast7Days(true, true);
        assertThat(booked[6]).isEqualTo(20.0);
        assertThat(booked[3]).isEqualTo(15.0);
    }

    @Test
    @DisplayName("weekRevenueSummary splits realized vs pipeline")
    void weekSummary() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder f = new CustomerOrder(c, LocalDate.now(), "FULFILLED", "");
        f.addLine(new OrderLine("Roses", 1, "bunch", 30.0));
        CustomerOrder conf = new CustomerOrder(c, LocalDate.now(), "CONFIRMED", "");
        conf.addLine(new OrderLine("Dahlia", 1, "bunch", 12.0));
        CustomerOrder d = new CustomerOrder(c, LocalDate.now(), "DRAFT", "");
        d.addLine(new OrderLine("X", 1, "bunch", 99.0));

        when(orderRepository.findBetween(any(), any()))
                .thenReturn(List.of(f, conf, d));

        OrderService.WeekRevenueSummary s = service.weekRevenueSummary();
        assertThat(s.realized()).isEqualTo(30.0);
        assertThat(s.pipeline()).isEqualTo(12.0);
        assertThat(s.draft()).isEqualTo(99.0);
        assertThat(s.booked()).isEqualTo(42.0);
        assertThat(s.fulfilledOrderCount()).isEqualTo(1);
        assertThat(s.confirmedOrderCount()).isEqualTo(1);
        assertThat(s.draftOrderCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("exportToCsv writes header and order rows")
    void exportCsv() throws Exception {
        Customer c = new Customer("Market Stall", "Alex", "alex@example.test",
                "360-555-0199", "MARKET", "Use west, loading dock");
        c.setId(1L);
        CustomerOrder o = new CustomerOrder(c, LocalDate.of(2026, 7, 10), "CONFIRMED", "pickup");
        o.setHandoffType("MARKET");
        o.setId(9L);
        o.addLine(new OrderLine("Nootka Rose", 5, "bunch", 12.0));
        o.addLine(new OrderLine("Dahlia, Sunset", 2, "stems", 3.5));
        when(orderRepository.findAllNewestFirst()).thenReturn(List.of(o));

        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("orders-export", ".csv");
        service.exportToCsv(tmp.toString());
        String body = java.nio.file.Files.readString(tmp);
        assertThat(body).contains("OrderId,PickupDeliveryMarketDate,HandoffType,Customer,"
                + "ContactName,Phone,Email,AccountDeliveryNote,Status,LineCount,Products,"
                + "Total,HandoffInstructions");
        assertThat(body).contains("Market Stall", "Alex", "360-555-0199",
                "alex@example.test", "2026-07-10", ",MARKET,",
                "\"Use west, loading dock\"");
        assertThat(body).contains("5 bunch Nootka Rose @ $12.00",
                "2 stems Dahlia, Sunset @ $3.50", "67.00", "pickup");
        assertThatIllegalArgumentException().isThrownBy(() -> service.exportToCsv(null));
        java.nio.file.Files.deleteIfExists(tmp);
    }

    @Test
    @DisplayName("generateInvoicePdf + formatInvoiceText for wholesale order")
    void invoicePdfAndText() throws Exception {
        Customer c = new Customer("Kitsap Blooms", "Sam Rivera", "sam@kitsapblooms.example",
                "360-555-0100", "FLORIST", "Deliver to west loading dock");
        c.setId(1L);
        CustomerOrder o = new CustomerOrder(c, LocalDate.of(2026, 7, 11), "CONFIRMED", "Saturday van");
        o.setHandoffType("PICKUP");
        o.setId(42L);
        o.addLine(new OrderLine("Nootka Rose", 20, "stems", 2.50));
        o.addLine(new OrderLine("Dahlia mix", 10, "stems", 3.00));
        when(orderRepository.findById(42L)).thenReturn(Optional.of(o));

        String text = service.formatInvoiceText(42L);
        assertThat(text).contains("INVOICE / ORDER #42");
        assertThat(text).contains("Kitsap Blooms");
        assertThat(text).contains("Nootka Rose");
        assertThat(text).contains("80.00"); // 50 + 30
        assertThat(text).contains(
                "Pickup / delivery / market date: 2026-07-11",
                "Handoff: Pickup",
                "Handoff instructions: Saturday van",
                "Account delivery note: Deliver to west loading dock",
                "Sam Rivera", "360-555-0100", "sam@kitsapblooms.example");

        byte[] pdf = service.generateInvoicePdf(42L);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains(
                    "Account delivery note: Deliver to west loading dock",
                    "Handoff instructions: Saturday van");
        } finally {
            reader.close();
        }

        assertThatIllegalArgumentException().isThrownBy(() -> service.generateInvoicePdf((Long) null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generateInvoicePdf((CustomerOrder) null));
        assertThatThrownBy(() -> service.generateInvoicePdf(99L))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
