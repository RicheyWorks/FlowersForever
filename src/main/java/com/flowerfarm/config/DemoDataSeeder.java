package com.flowerfarm.config;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Portfolio / show-and-tell seed data for empty CRM + harvest (profile {@code demo}).
 *
 * <p>Does not wipe existing data. Skips sections that already have rows so a
 * second launch on the same H2 file is a no-op.
 */
@Component
@Profile("demo")
@Order(50)
@ConditionalOnProperty(name = "flowerfarm.demo.seed", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final ZoneId PNW = ZoneId.of("America/Los_Angeles");

    private final CustomerService customerService;
    private final HarvestService harvestService;
    private final OrderService orderService;

    public DemoDataSeeder(CustomerService customerService,
                          HarvestService harvestService,
                          OrderService orderService) {
        this.customerService = customerService;
        this.harvestService = harvestService;
        this.orderService = orderService;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(PNW);
        int customers = seedCustomersIfEmpty();
        int harvests = seedHarvestsIfEmpty(today);
        int orders = seedOrdersIfEmpty(today);
        log.info("Demo seed complete — customers+{}, harvests+{}, orders+{} (today={})",
                customers, harvests, orders, today);
        if (customers + harvests + orders == 0) {
            log.info("Demo seed: nothing to add (CRM/harvest/orders already populated). "
                    + "Use a fresh ./data folder for a clean demo.");
        } else {
            log.info("Demo ready: open Market Day, Morning briefing, and Harvest → Bed production.");
        }
    }

    private int seedCustomersIfEmpty() {
        if (!customerService.getAll().isEmpty()) {
            return 0;
        }
        customerService.add(new Customer(
                "Kitsap Blooms", "Sam Rivera", "sam@kitsapblooms.example",
                "360-555-0100", "FLORIST", "Weekly wholesale — Port Orchard"));
        customerService.add(new Customer(
                "Saturday Market Stall", "Alex Chen", "",
                "360-555-0142", "MARKET", "Port Orchard Farmers Market"));
        customerService.add(new Customer(
                "Nootka & Co. Events", "Jordan Lee", "events@nootka.example",
                "360-555-0199", "WHOLESALE", "Weddings / farm dinners"));
        return 3;
    }

    private int seedHarvestsIfEmpty(LocalDate today) {
        if (!harvestService.getAll().isEmpty()) {
            return 0;
        }
        harvestService.add(new HarvestEntry(
                today.minusDays(2), "Nootka Rose", 80, "stems", "Bed A", "demo morning cut"));
        harvestService.add(new HarvestEntry(
                today.minusDays(1), "Nootka Rose", 60, "stems", "Bed A", "demo"));
        harvestService.add(new HarvestEntry(
                today.minusDays(1), "Dahlia mix", 25, "bunches", "Bed C", "demo"));
        harvestService.add(new HarvestEntry(
                today, "Damask Rose", 40, "stems", "Tunnel 1", "demo today's cut"));
        harvestService.add(new HarvestEntry(
                today, "Nootka Rose", 50, "stems", "Bed A", "demo today's cut"));
        return 5;
    }

    private int seedOrdersIfEmpty(LocalDate today) {
        List<CustomerOrder> existing = orderService.findBetween(today.minusDays(6), today);
        if (!existing.isEmpty()) {
            return 0;
        }
        List<Customer> customers = customerService.getAll();
        if (customers.isEmpty()) {
            return 0;
        }
        Customer florist = customers.stream()
                .filter(c -> "FLORIST".equalsIgnoreCase(c.getCustomerType()))
                .findFirst().orElse(customers.get(0));
        Customer market = customers.stream()
                .filter(c -> "MARKET".equalsIgnoreCase(c.getCustomerType()))
                .findFirst().orElse(customers.get(Math.min(1, customers.size() - 1)));
        Customer wholesale = customers.stream()
                .filter(c -> "WHOLESALE".equalsIgnoreCase(c.getCustomerType()))
                .findFirst().orElse(customers.get(0));

        orderService.create(
                florist.getId(),
                today,
                "CONFIRMED",
                "demo — AM pickup",
                List.of(
                        new OrderLine("Nootka Rose", 40, "stems", 2.50),
                        new OrderLine("Damask Rose", 12, "stems", 3.00)
                ));
        orderService.create(
                market.getId(),
                today,
                "CONFIRMED",
                "demo — Saturday stall",
                List.of(
                        new OrderLine("Nootka Rose", 30, "stems", 2.50),
                        new OrderLine("Dahlia mix", 8, "bunches", 12.00)
                ));
        orderService.create(
                wholesale.getId(),
                today,
                "DRAFT",
                "demo — hold for confirm",
                List.of(new OrderLine("Nootka Rose", 20, "stems", 2.20)));
        return 3;
    }
}
