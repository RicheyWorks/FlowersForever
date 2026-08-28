package com.flowerfarm.release;

import com.flowerfarm.FlowerFarmApp;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Release operational acceptance")
class OperationalAcceptanceIT {

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("multi-day farm operations survive restart without lost concurrent stock changes")
    void realisticFarmDaySurvivesRestart() throws Exception {
        Path database = temporaryDirectory.resolve("operational-store");
        Map<String, Object> properties = Map.of(
                "spring.datasource.url", "jdbc:h2:file:"
                        + database.toString().replace('\\', '/') + ";DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "flowerfarm.inventory.seed-csv", temporaryDirectory.resolve("missing.csv").toString(),
                "flowerfarm.irrigation.live-weather", "false",
                "spring.main.banner-mode", "off");

        Long customerId;
        Long persistentItemId;
        try (ConfigurableApplicationContext context = start(properties)) {
            InventoryService inventory = context.getBean(InventoryService.class);
            HarvestService harvest = context.getBean(HarvestService.class);
            CustomerService customers = context.getBean(CustomerService.class);
            OrderService orders = context.getBean(OrderService.class);

            Item persistent = inventory.addItem(new Item(
                    "Release Candidate Lisianthus", "Flowers/Plants", 3.5,
                    "stems", 0.8, 120, "operational acceptance"));
            persistentItemId = persistent.getId();
            Item concurrent = inventory.addItem(new Item(
                    "Concurrent Market Stem", "Flowers/Plants", 2.0,
                    "stems", 0.5, 100, "crew stock test"));

            var workers = Executors.newFixedThreadPool(4);
            for (int i = 0; i < 20; i++) {
                workers.submit(() -> inventory.incrementQuantityByName(
                        concurrent.getName(), 1, concurrent.getUnit(), "crew stock test"));
            }
            workers.shutdown();
            assertThat(workers.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
            assertThat(inventory.findById(concurrent.getId())).get()
                    .extracting(Item::getQuantity).isEqualTo(120);

            LocalDate today = LocalDate.now();
            harvest.add(new HarvestEntry(today.minusDays(2),
                    "Release Candidate Lisianthus", 18, "stems", "Bed RC-1", "first cut"));
            harvest.add(new HarvestEntry(today,
                    "Release Candidate Lisianthus", 24, "stems", "Bed RC-1", "market cut"));
            Customer customer = customers.add(new Customer(
                    "Release Florist", "Morgan", "morgan@example.test", "360-555-0119",
                    "FLORIST", "acceptance account"));
            customerId = customer.getId();
            CustomerOrder order = orders.create(customerId, today, "CONFIRMED", "market pickup",
                    "MARKET",
                    java.util.List.of(new OrderLine(
                            "Release Candidate Lisianthus", 12, "stems", 3.5)));
            orders.fulfill(order.getId());

            MorningBriefingService briefing = context.getBean(MorningBriefingService.class);
            assertThat(briefing.generatePdf(briefing.buildOffline()))
                    .startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
            DayCloseoutService closeout = context.getBean(DayCloseoutService.class);
            assertThat(closeout.generatePdf(closeout.build()))
                    .startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
            Path export = temporaryDirectory.resolve("inventory-acceptance.csv");
            inventory.exportToCsv(export.toString());
            assertThat(Files.readString(export)).contains("Release Candidate Lisianthus");
        }

        try (ConfigurableApplicationContext restarted = start(properties)) {
            InventoryService inventory = restarted.getBean(InventoryService.class);
            HarvestService harvest = restarted.getBean(HarvestService.class);
            CustomerService customers = restarted.getBean(CustomerService.class);
            OrderService orders = restarted.getBean(OrderService.class);

            assertThat(inventory.findById(persistentItemId)).isPresent();
            assertThat(harvest.filter("", "Bed RC-1", null, null)).hasSize(2);
            assertThat(customers.findById(customerId)).isPresent();
            assertThat(orders.findBetween(LocalDate.now(), LocalDate.now()))
                    .anySatisfy(order -> assertThat(order.getStatus()).isEqualTo("FULFILLED"));
        }
    }

    private static ConfigurableApplicationContext start(Map<String, Object> properties) {
        String[] arguments = properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(FlowerFarmApp.class)
                .headless(true)
                .web(WebApplicationType.NONE)
                .run(arguments);
    }
}
