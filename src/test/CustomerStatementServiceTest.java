package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerStatementService.CustomerStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerStatementService")
class CustomerStatementServiceTest {

    @Mock CustomerService customerService;
    @Mock OrderService orderService;

    CustomerStatementService service;

    @BeforeEach
    void setUp() {
        service = new CustomerStatementService(customerService, orderService);
    }

    @Test
    @DisplayName("build aggregates status totals and PDF")
    void buildAndPdf() {
        Customer c = new Customer("Kitsap Blooms", "Sam", "sam@example.com",
                "360-555-0100", "FLORIST", "");
        c.setId(3L);
        when(customerService.findById(3L)).thenReturn(Optional.of(c));

        LocalDate today = LocalDate.of(2026, 7, 11);
        CustomerOrder fulfilled = new CustomerOrder(c, today.minusDays(5), "FULFILLED", "van");
        fulfilled.setId(1L);
        fulfilled.addLine(new OrderLine("Nootka Rose", 10, "stems", 5.0));

        CustomerOrder confirmed = new CustomerOrder(c, today.minusDays(1), "CONFIRMED", "");
        confirmed.setId(2L);
        confirmed.addLine(new OrderLine("Dahlia", 4, "stems", 3.0));

        CustomerOrder old = new CustomerOrder(c, today.minusDays(200), "FULFILLED", "old");
        old.setId(9L);
        old.addLine(new OrderLine("Ancient", 1, "stems", 100.0));

        when(orderService.findByCustomer(eq(3L))).thenReturn(List.of(fulfilled, confirmed, old));

        CustomerStatement statement = service.build(3L, today.minusDays(90), today);
        assertThat(statement.plainText()).contains("CUSTOMER STATEMENT");
        assertThat(statement.customerName()).isEqualTo("Kitsap Blooms");
        assertThat(statement.orderCount()).isEqualTo(2);
        assertThat(statement.fulfilledTotal()).isEqualTo(50.0);
        assertThat(statement.pipelineTotal()).isEqualTo(12.0);
        assertThat(statement.grandTotal()).isEqualTo(62.0);
        assertThat(statement.orders()).extracting(CustomerStatementService.StatementLine::orderId)
                .containsExactly(1L, 2L);
        assertThat(statement.toMap()).containsKeys("orders", "fulfilledTotal");

        byte[] pdf = service.generatePdf(statement);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        assertThatIllegalArgumentException().isThrownBy(() -> service.generatePdf(null));
        when(customerService.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.build(99L, null, null))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    @DisplayName("default date window is trailing 90 days")
    void defaultWindow() {
        Customer c = new Customer("Market Stall", "", "", "", "MARKET", "");
        c.setId(1L);
        when(customerService.findById(1L)).thenReturn(Optional.of(c));
        when(orderService.findByCustomer(1L)).thenReturn(List.of());

        CustomerStatement s = service.build(1L, null, null);
        assertThat(s.to()).isEqualTo(LocalDate.now());
        assertThat(s.from()).isEqualTo(LocalDate.now().minusDays(90));
        assertThat(s.orderCount()).isZero();
    }
}
