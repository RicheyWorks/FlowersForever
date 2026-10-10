package com.flowerfarm.config;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.SeasonPlanService;
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
    @Mock KnowledgeService knowledgeService;
    @Mock SeasonPlanService seasonPlanService;

    DemoDataSeeder seeder;
    List<Customer> customers = new ArrayList<>();
    List<HarvestEntry> harvests = new ArrayList<>();
    List<CustomerOrder> orders = new ArrayList<>();
    List<KnowledgeEntry> knowledge = new ArrayList<>();
    List<SeasonPlanEntry> seasonPlans = new ArrayList<>();

    @BeforeEach
    void setUp() {
        seeder = new DemoDataSeeder(
                customerService, harvestService, orderService, knowledgeService,
                seasonPlanService);
        when(customerService.getAll()).thenAnswer(inv -> new ArrayList<>(customers));
        when(harvestService.getAll()).thenAnswer(inv -> new ArrayList<>(harvests));
        when(orderService.findBetween(any(), any())).thenAnswer(inv -> new ArrayList<>(orders));
        when(knowledgeService.search(any())).thenAnswer(inv -> new ArrayList<>(knowledge));
        when(seasonPlanService.listForSeason(anyInt()))
                .thenAnswer(inv -> new ArrayList<>(seasonPlans));

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
        lenient().when(orderService.create(
                anyLong(), any(), any(), any(), anyString(), anyList())).thenAnswer(inv -> {
            CustomerOrder o = new CustomerOrder(
                    customers.stream().filter(c -> c.getId().equals(inv.getArgument(0))).findFirst().orElseThrow(),
                    inv.getArgument(1),
                    inv.getArgument(2),
                    inv.getArgument(3),
                    inv.getArgument(4));
            o.setId((long) (orders.size() + 1));
            List<OrderLine> lines = inv.getArgument(5);
            if (lines != null) {
                lines.forEach(o::addLine);
            }
            orders.add(o);
            return o;
        });
        lenient().when(knowledgeService.create(any())).thenAnswer(inv -> {
            KnowledgeEntry entry = inv.getArgument(0);
            entry.setId((long) (knowledge.size() + 1));
            knowledge.add(entry);
            return entry;
        });
        lenient().when(seasonPlanService.create(any())).thenAnswer(inv -> {
            SeasonPlanEntry entry = inv.getArgument(0);
            entry.setId((long) (seasonPlans.size() + 1));
            seasonPlans.add(entry);
            return entry;
        });
    }

    @Test
    @DisplayName("empty DB seeds customers, harvests, orders, lessons, and season plans")
    void seedsWhenEmpty() {
        seeder.run(new DefaultApplicationArguments(new String[]{}));

        assertThat(customers).hasSize(3);
        assertThat(harvests).hasSize(5);
        assertThat(orders).hasSize(3);
        assertThat(knowledge).hasSize(3);
        assertThat(seasonPlans).hasSize(4);
        assertThat(orders.stream().filter(o -> "CONFIRMED".equals(o.getStatus())).count()).isEqualTo(2);
        assertThat(orders.stream().anyMatch(o -> "DRAFT".equals(o.getStatus()))).isTrue();
        assertThat(orders).extracting(CustomerOrder::getHandoffType)
                .containsExactly("PICKUP", "MARKET", "DELIVERY");
        assertThat(harvests.stream().anyMatch(h -> "Bed A".equals(h.getBedOrField()))).isTrue();
        assertThat(knowledge).extracting(KnowledgeEntry::getSummary)
                .anyMatch(summary -> summary.contains("mildew"));
        verify(orderService, atLeastOnce()).create(
                anyLong(), any(), eq("CONFIRMED"), any(), anyString(), anyList());
    }

    @Test
    @DisplayName("populated DB is a no-op")
    void skipsWhenPopulated() {
        customers.add(new Customer("Existing", "", "", "", "MARKET", ""));
        customers.get(0).setId(1L);
        harvests.add(new HarvestEntry(LocalDate.now(), "X", 1, "stems", "B", ""));
        orders.add(new CustomerOrder(customers.get(0), LocalDate.now(), "CONFIRMED", ""));
        knowledge.add(new com.flowerfarm.model.KnowledgeEntry(
                LocalDate.now(), "Existing lesson", com.flowerfarm.model.KnowledgeKind.PRACTICE,
                "Rose", "", "A", "Harvest", "Existing observation", "", "", "",
                null, com.flowerfarm.model.KnowledgeConfidence.NEEDS_REVIEW, List.of()));
        seasonPlans.add(new SeasonPlanEntry(
                LocalDate.now().getYear(), "Existing crop", "", "Bed A",
                LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(1),
                LocalDate.now(), LocalDate.now().plusMonths(1),
                50, 200, SeasonPlanStatus.HARVESTING, ""));

        seeder.run(new DefaultApplicationArguments(new String[]{}));

        verify(customerService, never()).add(any());
        verify(harvestService, never()).add(any());
        verify(orderService, never()).create(
                anyLong(), any(), any(), any(), anyString(), anyList());
        verify(knowledgeService, never()).create(any());
        verify(seasonPlanService, never()).create(any());
    }
}
