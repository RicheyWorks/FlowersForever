package com.flowerfarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.farm.FarmDataImportReceiptService;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.main.headless=true",
        "spring.datasource.url=jdbc:h2:mem:harness-single-connection;DB_CLOSE_DELAY=-1",
        "spring.flyway.url=jdbc:h2:mem:harness-single-connection;DB_CLOSE_DELAY=-1",
        "spring.flyway.user=sa",
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=500",
        "spring.jpa.hibernate.ddl-auto=validate",
        "flowerfarm.inventory.seed-csv=target/no-single-connection-seed.csv",
        "flowerfarm.harness.enabled=true",
        "flowerfarm.harness.token=harness-single-connection-token-123456",
        "flowerfarm.harness.allow-sensitive-read=true",
        "flowerfarm.harness.allow-mutations=true"
})
@ActiveProfiles("release-smoke")
@Timeout(15)
class FarmDataHarnessTransactionTest {

    private static final String TOKEN = "harness-single-connection-token-123456";

    @TempDir
    static Path exportDirectory;

    @DynamicPropertySource
    static void exportDirectory(DynamicPropertyRegistry properties) {
        properties.add("flowerfarm.exports.directory", exportDirectory::toString);
    }

    @Autowired
    HarnessGateway gateway;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    DataSource dataSource;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    FarmDataImportReceiptService receipts;

    @Test
    void exportsWithOneConnectionAndReadsCompletedFilesWithoutDatabaseAccess() throws Exception {
        command("upsert-inventory", Map.of("records", List.of(
                Map.of("name", "Single connection export crop", "quantity", 3))));
        String key = "single-connection-" + UUID.randomUUID();
        Map<String, Object> arguments = Map.of(
                "export-key", key, "record-types", List.of("inventory"), "chunk-records", 2);
        HarnessCommand create = new HarnessCommand("single-connection-export",
                "create-record-export", arguments);
        HarnessResponse created = gateway.execute(TOKEN, "farm-data", create);
        assertThat(created.success()).isTrue();
        JsonNode export = mapper.valueToTree(created.output().get("export"));
        assertThat(export.path("recordCounts").path("inventory").asLong()).isPositive();
        assertThat(gateway.execute(TOKEN, "farm-data", create).replayed()).isTrue();
        assertThat(command("create-record-export", arguments).output().get("replayed"))
                .isEqualTo(true);

        try (Connection held = dataSource.getConnection()) {
            assertThat(held.isClosed()).isFalse();
            assertThat(command("get-record-export", Map.of("export-key", key)).success())
                    .isTrue();
            HarnessResponse chunk = command("read-record-export", Map.of(
                    "export-id", export.path("exportId").asText(), "part", 0));
            assertThat(chunk.success()).isTrue();
            JsonNode content = mapper.valueToTree(chunk.output().get("chunk"));
            assertThat(content.path("records").size()).isPositive();
        }
    }

    @Test
    void catalogDoesNotBorrowADatabaseConnection() throws Exception {
        try (Connection held = dataSource.getConnection()) {
            assertThat(held.isClosed()).isFalse();
            assertThat(command("get-data-catalog", Map.of()).success()).isTrue();
        }
    }

    @Test
    void laterInvalidRowRollsBackEarlierWritesAndReceipts() {
        assertThatThrownBy(() -> command("upsert-inventory", Map.of(
                "source-system", "single-connection-atomic",
                "records", List.of(
                        Map.of("sourceKey", "first", "name", "Single connection rollback crop",
                                "quantity", 5),
                        Map.of("sourceKey", "second", "name", "Single connection invalid crop",
                                "quantity", -1)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(inventoryService.findByNameIgnoreCase("Single connection rollback crop"))
                .isEmpty();
        assertThat(receipts.list("single-connection-atomic", "inventory", 0, 20)
                .getTotalElements()).isZero();
    }

    private HarnessResponse command(String action, Map<String, Object> arguments) {
        return gateway.execute(TOKEN, "farm-data",
                new HarnessCommand(UUID.randomUUID().toString(), action, arguments));
    }
}
