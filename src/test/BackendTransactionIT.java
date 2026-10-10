package com.flowerfarm.integration;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.headless=true",
                "spring.datasource.url=jdbc:h2:mem:backend-transactions;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "flowerfarm.inventory.seed-csv=target/no-backend-transaction-seed.csv",
                "flowerfarm.irrigation.live-weather=false",
                "flowerfarm.auth.enabled=false"
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Backend transaction integrity")
class BackendTransactionIT {

    @Autowired InventoryService inventoryService;
    @Autowired CustomerService customerService;
    @Autowired OrderService orderService;
    @Autowired MarketDayPackingService marketDayPackingService;
    @Autowired HarvestService harvestService;
    @Autowired FarmProfile farmProfile;

    @Test
    @DisplayName("market batch preflight prevents partial completion when aggregate stock is short")
    void marketBatchPreflightPreventsPartialCompletion() {
        LocalDate day = LocalDate.now(farmProfile.zoneId());
        Item stock = inventoryService.addItem(new Item(
                "Transaction Rose", "Flowers/Plants", 2, "stems", 1, 5, ""));
        Customer customer = customerService.add(new Customer(
                "Transaction Customer", "", "", "", "WHOLESALE", ""));
        var good = orderService.create(customer.getId(), day, "CONFIRMED", "", "MARKET",
                List.of(new OrderLine("Transaction Rose", 2, "stems", 2)));
        var missing = orderService.create(customer.getId(), day, "CONFIRMED", "", "MARKET",
                List.of(new OrderLine("Missing Transaction Rose", 1, "stems", 2)));

        var plan = marketDayPackingService.planForDay(day);

        assertThat(plan.shortfallSkuCount()).isEqualTo(1);
        assertThatThrownBy(() -> marketDayPackingService.fulfillConfirmedOrders(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current inventory no longer covers")
                .hasMessageContaining("Missing Transaction Rose")
                .hasMessageContaining("rebuild the packing list");

        assertThat(orderService.findById(good.getId()).orElseThrow().getStatus())
                .isEqualTo("CONFIRMED");
        assertThat(orderService.findById(missing.getId()).orElseThrow().getStatus())
                .isEqualTo("CONFIRMED");
        assertThat(inventoryService.findById(stock.getId()).orElseThrow().getQuantity())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("concurrent harvest edits keep the harvest row and inventory in agreement")
    void concurrentHarvestEditsRemainConsistent() throws Exception {
        LocalDate day = LocalDate.now(farmProfile.zoneId());
        HarvestEntry original = harvestService.add(new HarvestEntry(
                day, "Concurrent Rose", 10, "stems", "Bed C", ""));
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return harvestService.update(original.getId(), new HarvestEntry(
                        day, "Concurrent Rose", 20, "stems", "Bed C", "first"));
            });
            var second = executor.submit(() -> {
                start.await();
                return harvestService.update(original.getId(), new HarvestEntry(
                        day, "Concurrent Rose", 30, "stems", "Bed C", "second"));
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        HarvestEntry finalHarvest = harvestService.findById(original.getId()).orElseThrow();
        Item finalStock = inventoryService.findByNameIgnoreCase("Concurrent Rose").orElseThrow();
        assertThat(finalStock.getQuantity())
                .isEqualTo((int) Math.round(finalHarvest.getQuantity()));
    }
}
