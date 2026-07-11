package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.repository.CustomerJpaRepository;
import com.flowerfarm.repository.CustomerOrderJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock CustomerOrderJpaRepository orderRepository;
    @Mock CustomerJpaRepository customerRepository;
    @Mock InventoryService inventoryService;
    @Mock SyncHistoryService syncHistoryService;
    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, customerRepository, inventoryService, syncHistoryService);
        lenient().when(syncHistoryService.record(any(), any(), anyBoolean(), any(), any(), any()))
                .thenAnswer(inv -> null);
    }

    @Test
    @DisplayName("create() builds order with lines for existing customer")
    void createOrder() {
        Customer customer = new Customer("Market Stall", "", "", "", "MARKET", "");
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
                List.of(new OrderLine("Nootka Rose", 20, "bunch", 12.0))
        );

        assertThat(order.getId()).isEqualTo(10L);
        assertThat(order.getLines()).hasSize(1);
        assertThat(order.lineTotal()).isEqualTo(240.0);
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_CREATE"), eq(true), any(), any(), eq(1));
    }

    @Test
    @DisplayName("fulfill() decrements inventory and marks FULFILLED")
    void fulfillDecrementsInventory() {
        Customer c = new Customer("Kitsap Blooms", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder order = new CustomerOrder(c, LocalDate.now(), "CONFIRMED", "");
        order.setId(5L);
        order.addLine(new OrderLine("Nootka Rose", 10, "stems", 2.5));

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryService.decrementQuantityByName("Nootka Rose", 10))
                .thenReturn(Optional.of(new Item("Nootka Rose", "Flowers/Plants", 2.5, "stems", 1, 40, "")));

        CustomerOrder result = service.fulfill(5L);

        assertThat(result.getStatus()).isEqualTo("FULFILLED");
        verify(inventoryService).decrementQuantityByName("Nootka Rose", 10);
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_FULFILL"), eq(true), any(), any(), eq(1));
    }

    @Test
    @DisplayName("create() throws when customer missing")
    void createMissingCustomer() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatExceptionOfType(IndexOutOfBoundsException.class)
                .isThrownBy(() -> service.create(99L, LocalDate.now(), "DRAFT", "", List.of()));
    }

    @Test
    @DisplayName("confirm() sets CONFIRMED and audits ORDER_STATUS")
    void confirm() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder order = new CustomerOrder(c, LocalDate.now(), "DRAFT", "");
        order.setId(8L);
        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerOrder saved = service.confirm(8L);
        assertThat(saved.getStatus()).isEqualTo("CONFIRMED");
        verify(syncHistoryService).record(eq("crm"), eq("ORDER_STATUS"), eq(true), any(), any(), eq(1));
    }

    @Test
    @DisplayName("cannot un-fulfill via status change")
    void cannotUnfulfill() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder order = new CustomerOrder(c, LocalDate.now(), "FULFILLED", "");
        order.setId(9L);
        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));

        assertThatIllegalArgumentException().isThrownBy(() -> service.updateStatus(9L, "CANCELLED"));
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
        CustomerOrder o2 = new CustomerOrder(b, LocalDate.of(2026, 7, 5), "DRAFT", "");
        o2.setId(2L);
        CustomerOrder o3 = new CustomerOrder(a, LocalDate.of(2026, 7, 8), "FULFILLED", "");
        o3.setId(3L);
        when(orderRepository.findAllByOrderByOrderDateDescIdDesc()).thenReturn(List.of(o3, o2, o1));

        assertThat(service.filter("CONFIRMED", "kitsap", null, null)).hasSize(1);
        assertThat(service.filter("ALL", "market", null, null)).hasSize(1);
        assertThat(service.filter(null, null,
                LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 10))).hasSize(2);
    }

    @Test
    @DisplayName("updateNotes persists notes")
    void updateNotes() {
        Customer c = new Customer("A", "", "", "", "WHOLESALE", "");
        c.setId(1L);
        CustomerOrder order = new CustomerOrder(c, LocalDate.now(), "DRAFT", "old");
        order.setId(3L);
        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerOrder saved = service.updateNotes(3L, "pickup Saturday");
        assertThat(saved.getNotes()).isEqualTo("pickup Saturday");
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

        when(orderRepository.findByOrderDateBetweenOrderByOrderDateDescIdDesc(any(), any()))
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

        when(orderRepository.findByOrderDateBetweenOrderByOrderDateDescIdDesc(any(), any()))
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

        when(orderRepository.findByOrderDateBetweenOrderByOrderDateDescIdDesc(any(), any()))
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
        Customer c = new Customer("Market Stall", "", "", "", "MARKET", "");
        c.setId(1L);
        CustomerOrder o = new CustomerOrder(c, LocalDate.of(2026, 7, 10), "CONFIRMED", "pickup");
        o.setId(9L);
        o.addLine(new OrderLine("Nootka Rose", 5, "bunch", 12.0));
        when(orderRepository.findAllByOrderByOrderDateDescIdDesc()).thenReturn(List.of(o));

        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("orders-export", ".csv");
        service.exportToCsv(tmp.toString());
        String body = java.nio.file.Files.readString(tmp);
        assertThat(body).contains("OrderId,Date,Customer,Status,LineCount,Total,Notes");
        assertThat(body).contains("Market Stall");
        assertThat(body).contains("60.00");
        assertThat(body).contains("pickup");
        assertThatIllegalArgumentException().isThrownBy(() -> service.exportToCsv(null));
        java.nio.file.Files.deleteIfExists(tmp);
    }

    @Test
    @DisplayName("generateInvoicePdf + formatInvoiceText for wholesale order")
    void invoicePdfAndText() {
        Customer c = new Customer("Kitsap Blooms", "Sam Rivera", "sam@kitsapblooms.example",
                "360-555-0100", "FLORIST", "Weekly wholesale");
        c.setId(1L);
        CustomerOrder o = new CustomerOrder(c, LocalDate.of(2026, 7, 11), "CONFIRMED", "Saturday van");
        o.setId(42L);
        o.addLine(new OrderLine("Nootka Rose", 20, "stems", 2.50));
        o.addLine(new OrderLine("Dahlia mix", 10, "stems", 3.00));
        when(orderRepository.findById(42L)).thenReturn(Optional.of(o));

        String text = service.formatInvoiceText(42L);
        assertThat(text).contains("INVOICE / ORDER #42");
        assertThat(text).contains("Kitsap Blooms");
        assertThat(text).contains("Nootka Rose");
        assertThat(text).contains("80.00"); // 50 + 30

        byte[] pdf = service.generateInvoicePdf(42L);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        assertThatIllegalArgumentException().isThrownBy(() -> service.generateInvoicePdf((Long) null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.generateInvoicePdf((CustomerOrder) null));
        assertThatThrownBy(() -> service.generateInvoicePdf(99L))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
