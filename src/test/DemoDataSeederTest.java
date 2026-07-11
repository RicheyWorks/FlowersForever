package com.flowerfarm.config;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DemoDataSeeder")
class DemoDataSeederTest {

    @Mock CustomerService customerService;
    @Mock HarvestService harvestService;
    @Mock OrderService orderService;

    DemoDataSeeder seeder;
    List<Customer> customers = new ArrayList<>();
    List<HarvestEntry> harvests = new ArrayList<>();
    List<CustomerOrder> orders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        seeder = new DemoDataSeeder(customerService, harvestService, orderService);
        when(customerService.getAll()).thenAnswer(inv -> new ArrayList<>(customers));
        when(harvestService.getAll()).thenAnswer(inv -> new ArrayList<>(harvests));
        when(orderService.findBetween(any(), any())).thenAnswer(inv -> new ArrayList<>(orders));

        lenient().when(customerService.add(any())).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId((long) (customers.size() + 1));
            customers.add(c);
            return c;
        });
        lenient().when(harvestService.add(any())).thenAnswer(inv -> {
            HarvestEntry e = inv.getArgument(0);
            e.setId((long) (harvests.size() + 1));
            harvests.add(e);
            return e;
        });
        lenient().when(orderService.create(anyLong(), any(), any(), any(), any())).thenAnswer(inv -> {
            CustomerOrder o = new CustomerOrder(
                    customers.stream().filter(c -> c.getId().equals(inv.getArgument(0))).findFirst().orElseThrow(),
                    inv.getArgument(1),
                    inv.getArgument(2),
                    inv.getArgument(3));
            o.setId((long) (orders.size() + 1));
            List<OrderLine> lines = inv.getArgument(4);
            if (lines != null) {
                lines.forEach(o::addLine);
            }
            orders.add(o);
            return o;
        });
    }

    @Test
    @DisplayName("empty DB seeds customers, harvests, and today's orders")
    void seedsWhenEmpty() {
        seeder.run(new DefaultApplicationArguments(new String[]{}));

        assertThat(customers).hasSize(3);
        assertThat(harvests).hasSize(5);
        assertThat(orders).hasSize(3);
        assertThat(orders.stream().filter(o -> "CONFIRMED".equals(o.getStatus())).count()).isEqualTo(2);
        assertThat(orders.stream().anyMatch(o -> "DRAFT".equals(o.getStatus()))).isTrue();
        assertThat(harvests.stream().anyMatch(h -> "Bed A".equals(h.getBedOrField()))).isTrue();
        verify(orderService, atLeastOnce()).create(anyLong(), any(), eq("CONFIRMED"), any(), any());
    }

    @Test
    @DisplayName("populated DB is a no-op")
    void skipsWhenPopulated() {
        customers.add(new Customer("Existing", "", "", "", "MARKET", ""));
        customers.get(0).setId(1L);
        harvests.add(new HarvestEntry(LocalDate.now(), "X", 1, "stems", "B", ""));
        orders.add(new CustomerOrder(customers.get(0), LocalDate.now(), "CONFIRMED", ""));

        seeder.run(new DefaultApplicationArguments(new String[]{}));

        verify(customerService, never()).add(any());
        verify(harvestService, never()).add(any());
        verify(orderService, never()).create(anyLong(), any(), any(), any(), any());
    }
}
