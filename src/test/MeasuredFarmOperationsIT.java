package com.flowerfarm.release;

import com.flowerfarm.FlowerFarmApp;
import com.flowerfarm.model.*;
import com.flowerfarm.service.*;
import com.flowerfarm.update.DataBackupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MeasuredFarmOperationsIT {
    @TempDir Path temporary;

    @Test void measuredHarvestSalesCountRetryAndBackupSurviveReopening() throws Exception {
        Path data = temporary.resolve("farm-data");
        Files.createDirectories(data);
        String url = "jdbc:h2:file:" + data.resolve("flowerfarm").toString().replace('\\', '/');
        Long itemId;
        Long orderId;
        Long harvestId;
        WorkerWorkflowService.WorkerInventoryCountRequest count =
                new WorkerWorkflowService.WorkerInventoryCountRequest("measured-count-regression", 102.875, 102.5, "grams");
        try (ConfigurableApplicationContext context = start(url)) {
            InventoryService stock = context.getBean(InventoryService.class);
            HarvestService harvests = context.getBean(HarvestService.class);
            OrderService orders = context.getBean(OrderService.class);
            WorkerWorkflowService workers = context.getBean(WorkerWorkflowService.class);
            var today = context.getBean(FarmProfile.class).today();
            assertThat(stock.getAllItems()).isEmpty();
            itemId = stock.addItem(new Item("Blue Dream flower", "Cannabis", 5, "grams", 1,
                    100.125, "Lot A")).getId();
            HarvestEntry harvest = harvests.add(new HarvestEntry(today, "Blue Dream flower", 3.5,
                    "grams", "Room A", "measured harvest", "WEIGHT-LOT-A", HarvestGrade.STANDARD,
                    0.125, "Dry room"));
            harvestId = harvest.getId();
            assertThat(quantity(stock, itemId)).isEqualTo(103.625);
            harvests.update(harvestId, new HarvestEntry(today, "Blue Dream flower", 3.75,
                    "grams", "Room A", "corrected", "WEIGHT-LOT-A", HarvestGrade.STANDARD,
                    0.125, "Dry room"));
            assertThat(quantity(stock, itemId)).isEqualTo(103.875);
            HarvestEntry history = harvests.importHistoricalBatch(List.of(new HarvestEntry(
                    today.minusDays(1), "Blue Dream flower", 7.125, "grams", "Room A", "history"))).get(0);
            harvests.update(history.getId(), new HarvestEntry(today.minusDays(1),
                    "Blue Dream flower", 8.25, "grams", "Room A", "corrected history"));
            assertThat(quantity(stock, itemId)).isEqualTo(103.875);
            harvests.delete(history.getId());
            assertThat(quantity(stock, itemId)).isEqualTo(103.875);
            Customer customer = context.getBean(CustomerService.class).add(new Customer(
                    "Measured buyer", "Alex", "buyer@example.test", "", "OTHER", ""));
            orderId = orders.create(customer.getId(), today, "CONFIRMED", "weighted pickup", "PICKUP",
                    List.of(new OrderLine("Blue Dream flower", 0.875, "grams", 5),
                            new OrderLine("Blue Dream flower", 0.125, "g", 5))).getId();
            orders.fulfill(orderId);
            orders.fulfill(orderId);
            assertThat(quantity(stock, itemId)).isEqualTo(102.875);
            assertThatThrownBy(() -> workers.countInventory(itemId,
                    new WorkerWorkflowService.WorkerInventoryCountRequest("changed-unit-count", 102.875, 102.5, "kg")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unit changed");
            assertThat(quantity(stock, itemId)).isEqualTo(102.875);
            assertThat(workers.countInventory(itemId, count).replayed()).isFalse();
            assertThat(workers.countInventory(itemId, count).replayed()).isTrue();
            assertThat(quantity(stock, itemId)).isEqualTo(102.5);
            Path csv = temporary.resolve("measured-stock.csv");
            stock.exportToCsv(csv.toString());
            assertThat(Files.readString(csv)).contains("Blue Dream flower,Cannabis,5.00,grams,1.00,102.5,");
            assertThat(harvests.totalsByProductUnit()).containsEntry("Blue Dream flower (gram)", 3.75);
            assertThat(context.getBean(ReportService.class).generateWeeklyReportPdf())
                    .startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        }
        shutdown(url);
        DataBackupService archives = new DataBackupService(Clock.systemUTC());
        Path backup = archives.backup(data, temporary.resolve("backups"), "measured").archive();
        archives.verifyChecksum(backup);
        try (ConfigurableApplicationContext reopened = start(url)) {
            InventoryService stock = reopened.getBean(InventoryService.class);
            assertThat(quantity(stock, itemId)).isEqualTo(102.5);
            assertThat(reopened.getBean(WorkerWorkflowService.class).countInventory(itemId, count).replayed()).isTrue();
            assertThat(quantity(stock, itemId)).isEqualTo(102.5);
            assertThat(reopened.getBean(OrderService.class).findById(orderId)).get()
                    .extracting(CustomerOrder::getStatus).isEqualTo("FULFILLED");
            reopened.getBean(HarvestService.class).delete(harvestId);
            assertThat(quantity(stock, itemId)).isEqualTo(98.75);
        }
        shutdown(url);
        assertThat(archives.restore(backup, data)).isDirectory();
        try (ConfigurableApplicationContext restored = start(url)) {
            assertThat(quantity(restored.getBean(InventoryService.class), itemId)).isEqualTo(102.5);
            assertThat(restored.getBean(HarvestService.class).findById(harvestId)).isPresent();
            assertThat(restored.getBean(WorkerWorkflowService.class).countInventory(itemId, count).replayed()).isTrue();
        }
        shutdown(url);
    }

    private double quantity(InventoryService stock, Long id) {
        return stock.findById(id).orElseThrow().getQuantity();
    }

    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(FlowerFarmApp.class).headless(true).web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + url, "--spring.jpa.hibernate.ddl-auto=validate",
                        "--flowerfarm.inventory.seed-csv=" + temporary.resolve("no-seed.csv"),
                        "--flowerfarm.inventory.sample-enabled=false", "--flowerfarm.irrigation.live-weather=false",
                        "--spring.main.banner-mode=off");
    }

    private void shutdown(String url) throws Exception {
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        }
    }
}
