package com.flowerfarm;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.headless=true",
                "spring.datasource.url=jdbc:h2:mem:context-smoke;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=validate",
                "flowerfarm.inventory.seed-csv=target/no-context-smoke-seed.csv",
                "flowerfarm.irrigation.live-weather=false"
        })
@DisplayName("Application context smoke test")
class ApplicationContextSmokeTest {

    @Autowired
    ConnectorRegistry connectorRegistry;

    @Autowired CustomerService customerService;
    @Autowired OrderService orderService;
    @Autowired InventoryService inventoryService;
    @Autowired UserDetailsService userDetailsService;

    @Test
    @DisplayName("starts the full REST application and registers every connector")
    void contextStartsWithAllConnectors() {
        assertThat(connectorRegistry.getConnectorNames()).containsExactlyInAnyOrder(
                "airtable",
                "csv",
                "excel",
                "farmbrite",
                "floranext",
                "google-sheets",
                "shopify",
                "square",
                "webhook");
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unused-default-user"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("serializes concurrent fulfillment so inventory is deducted once")
    void concurrentFulfillmentIsIdempotent() throws Exception {
        Customer customer = customerService.add(
                new Customer("Concurrency Test", "", "", "", "WHOLESALE", ""));
        CustomerOrder order = orderService.create(
                customer.getId(),
                LocalDate.now(),
                "CONFIRMED",
                "concurrency regression",
                "PICKUP",
                List.of(new OrderLine("Nootka Rose", 1, "stem", 2.5)));
        int quantityBefore = inventoryService.findByNameIgnoreCase("Nootka Rose")
                .map(Item::getQuantity)
                .orElseThrow();

        int requestCount = 12;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<CustomerOrder>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return orderService.fulfill(order.getId());
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<CustomerOrder> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS).getStatus()).isEqualTo("FULFILLED");
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(inventoryService.findByNameIgnoreCase("Nootka Rose"))
                .get()
                .extracting(Item::getQuantity)
                .isEqualTo(quantityBefore - 1);
    }
}
