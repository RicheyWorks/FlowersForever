package com.flowerfarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.mcp.McpProtocolHandler;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.repository.ProductionTaskJpaRepository;
import com.flowerfarm.repository.WorkerActionReceiptJpaRepository;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ProductionStudioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.main.headless=true",
        "spring.datasource.url=jdbc:h2:mem:farm-operations-harness;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "flowerfarm.inventory.seed-csv=target/no-farm-operations-harness-seed.csv",
        "flowerfarm.irrigation.live-weather=false",
        "flowerfarm.harness.enabled=true",
        "flowerfarm.harness.token=farm-operations-harness-token-123456",
        "flowerfarm.harness.allow-sensitive-read=true",
        "flowerfarm.harness.allow-mutations=true"
})
class FarmDataOperationsHarnessIntegrationTest {

    private static final String TOKEN = "farm-operations-harness-token-123456";
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 9, 21);

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;
    @Autowired McpProtocolHandler mcp;
    @Autowired HarnessRegistry registry;
    @Autowired InventoryService inventory;
    @Autowired CustomerService customers;
    @Autowired OrderService orders;
    @Autowired ProductionStudioService production;
    @Autowired ProductionTaskJpaRepository tasks;
    @Autowired WorkerActionReceiptJpaRepository receipts;

    @Test
    void countsStockThroughMcpAndRecoversAcrossFreshGatewayWithoutOverwritingLaterStock() {
        Item item = stock(20);
        String key = key();
        Map<String, Object> arguments = countArguments(item, key, 20, 14);
        long before = receipts.count();

        JsonNode counted = mcpCall("count-inventory", arguments);
        assertThat(counted.path("replayed").asBoolean()).isFalse();
        assertThat(counted.path("output").path("operation").path("result")
                .path("quantity").asInt()).isEqualTo(14);
        inventory.recordPhysicalCount(item.getId(), 14, 18);

        HarnessGateway restarted = gateway(true, true);
        JsonNode replay = mapper.valueToTree(restarted.execute(TOKEN, "farm-data",
                new HarnessCommand(key(), "count-inventory", arguments)));
        JsonNode operation = replay.path("output").path("operation");
        assertThat(operation.path("replayed").asBoolean()).isTrue();
        assertThat(operation.path("result").path("quantity").asInt()).isEqualTo(14);
        assertThat(inventory.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(18);
        assertThat(receipts.count()).isEqualTo(before + 1);
        assertThat(success("get-operation-receipt", Map.of("operation-key", key))
                .path("output").path("operation")).isEqualTo(operation);
    }

    @Test
    void recoversPhoneCountAndRejectsChangedArgumentsOrActionWithoutNewReceipts() {
        Item item = stock(12);
        String key = key();
        long before = receipts.count();
        ResponseEntity<JsonNode> phone = post("/api/worker/inventory/" + item.getId() + "/count",
                Map.of("operationKey", key, "reviewedQuantity", 12, "countedQuantity", 9), false);
        assertThat(phone.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode recovered = success("count-inventory", countArguments(item, key, 12, 9));
        assertThat(recovered.path("output").path("operation").path("replayed").asBoolean()).isTrue();
        assertThat(recovered.path("output").path("operation").path("result"))
                .isEqualTo(phone.getBody().path("result"));
        ResponseEntity<JsonNode> changed = command("count-inventory", countArguments(item, key, 12, 8));
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changed.getBody().path("code").asText()).isEqualTo("WORKER_ACTION_CONFLICT");
        ResponseEntity<JsonNode> changedAction = command("complete-order-handoff", Map.of(
                "operation-key", key, "order-id", 1, "review-token", "a".repeat(64)));
        assertThat(changedAction.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changedAction.getBody().path("code").asText()).isEqualTo("WORKER_ACTION_CONFLICT");
        assertThat(inventory.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(9);
        assertThat(receipts.count()).isEqualTo(before + 1);
    }

    @Test
    void staleAndInvalidCountsLeaveInventoryAndOperationKeysAvailableForCorrection() {
        Item item = stock(10);
        String key = key();
        long before = receipts.count();
        ResponseEntity<JsonNode> stale = command("count-inventory", countArguments(item, key, 9, 7));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().path("code").asText()).isEqualTo("INVENTORY_COUNT_CONFLICT");
        assertThat(stale.getBody().path("currentQuantity").asInt()).isEqualTo(10);
        assertThat(command("count-inventory", countArguments(item, key, 10, -1)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(receipts.count()).isEqualTo(before);
        assertThat(inventory.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(success("count-inventory", countArguments(item, key, 10, 7))
                .path("output").path("operation").path("replayed").asBoolean()).isFalse();
    }

    @Test
    void reviewsAndCompletesAnOrderAcrossMcpRestAndPhoneWithoutDuplicateStockDeduction() {
        Item item = stock(20);
        CustomerOrder order = order(item, WORK_DATE);
        CustomerOrder future = order(item, WORK_DATE.plusDays(1));
        JsonNode listed = mcpCall("list-order-handoffs", Map.of("through", WORK_DATE.toString(), "limit", 100));
        JsonNode handoffs = listed.path("output").path("handoffs");
        JsonNode reviewed = findById(handoffs.path("orders"), order.getId());
        assertThat(handoffs.path("orders").findValuesAsText("id")).doesNotContain(future.getId().toString());
        String token = reviewed.path("reviewToken").asText();
        assertThat(token).matches("[0-9a-f]{64}");
        String key = key();
        long before = receipts.count();

        JsonNode completed = success("complete-order-handoff", Map.of(
                "operation-key", key, "order-id", order.getId(), "review-token", token));
        assertThat(completed.path("output").path("operation").path("result")
                .path("status").asText()).isEqualTo("FULFILLED");
        ResponseEntity<JsonNode> phoneReplay = post("/api/worker/orders/" + order.getId() + "/complete",
                Map.of("operationKey", key, "reviewToken", token), false);
        assertThat(phoneReplay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(phoneReplay.getBody().path("replayed").asBoolean()).isTrue();
        assertThat(inventory.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(14);
        assertThat(receipts.count()).isEqualTo(before + 1);
        assertThat(mcpCall("get-operation-receipt", Map.of("operation-key", key))
                .path("output").path("operation").path("result"))
                .isEqualTo(phoneReplay.getBody().path("result"));
        assertThat(command("complete-order-handoff", Map.of(
                "operation-key", key, "order-id", order.getId(), "review-token", "b".repeat(64)))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void refusesChangedOrderReviewsWithoutFulfillmentOrReceipts() {
        Item item = stock(15);
        CustomerOrder order = order(item, WORK_DATE);
        String token = findById(success("list-order-handoffs", Map.of("through", WORK_DATE.toString()))
                .path("output").path("handoffs").path("orders"), order.getId())
                .path("reviewToken").asText();
        orders.updateNotes(order.getId(), "Use the other gate");
        long before = receipts.count();
        ResponseEntity<JsonNode> stale = command("complete-order-handoff", Map.of(
                "operation-key", key(), "order-id", order.getId(), "review-token", token));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().path("code").asText()).isEqualTo("ORDER_HANDOFF_CONFLICT");
        assertThat(stale.getBody().path("orderId").asLong()).isEqualTo(order.getId());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo("CONFIRMED");
        assertThat(inventory.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(15);
        assertThat(receipts.count()).isEqualTo(before);
        assertThat(command("list-order-handoffs", Map.of("limit", 101)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(command("list-order-handoffs", Map.of("through", "not-a-date")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void readsSeasonSettingsAcrossRestAndMcpAndReportsAbsentYearsWithoutCreatingThem() {
        JsonNode absent = success("get-season-settings", Map.of("season-year", 2093)).path("output");
        assertThat(absent.path("found").asBoolean()).isFalse();
        assertThat(absent.has("seasonSettings")).isFalse();
        success("upsert-season-settings", Map.of("season-year", 2093,
                "last-spring-frost", "2093-04-15", "first-fall-frost", "2093-10-20",
                "source", "Farm station", "loaded-labor-rate-per-hour", 31.5));
        JsonNode read = mcpCall("get-season-settings", Map.of("season-year", 2093)).path("output");
        assertThat(read.path("found").asBoolean()).isTrue();
        assertThat(read.path("seasonSettings").path("lastSpringFrost").asText()).isEqualTo("2093-04-15");
        assertThat(read.path("seasonSettings").path("source").asText()).isEqualTo("Farm station");
        assertThat(read.path("seasonSettings").path("loadedLaborRatePerHour").asDouble()).isEqualTo(31.5);
        assertThat(command("get-season-settings", Map.of("season-year", 2201)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void taskPreviewChecksSkippedStateAndImportReplaySurvivesLaterStateChanges() {
        ProductionTask task = tasks.saveAndFlush(new ProductionTask(
                null, null, WORK_DATE, ProductionTaskType.CUSTOM, ProductionTaskStatus.SKIPPED,
                "Preview crop", "Preview bed", 1, false, "Inspect crop"));
        Map<String, Object> previewArguments = Map.of("record-type", "production-tasks",
                "source-records", List.of(Map.of("sourceKey", "task-1", "taskId", task.getId(),
                        "actualMinutes", 25, "inputCost", 4)));
        JsonNode refused = success("preview-mapped-records", previewArguments).path("output");
        assertThat(refused.path("ready").asBoolean()).isFalse();
        assertThat(refused.path("invalidCount").asInt()).isEqualTo(1);
        assertThat(refused.path("issues").toString()).contains("Reopen skipped work");
        assertThat(production.findTask(task.getId()).orElseThrow().getActualMinutes()).isNull();
        Map<String, Object> importArguments = new LinkedHashMap<>(previewArguments);
        importArguments.put("source-system", key());
        assertThat(command("import-mapped-records", importArguments).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        production.updateTaskStatus(task.getId(), ProductionTaskStatus.TODO);
        assertThat(success("preview-mapped-records", previewArguments).path("output")
                .path("ready").asBoolean()).isTrue();
        assertThat(success("import-mapped-records", importArguments).path("output").path("updated").asInt())
                .isEqualTo(1);
        production.updateTaskStatus(task.getId(), ProductionTaskStatus.TODO);
        production.updateTaskStatus(task.getId(), ProductionTaskStatus.SKIPPED);
        assertThat(success("import-mapped-records", importArguments).path("output").path("replayed").asInt())
                .isEqualTo(1);
        assertThat(production.findTask(task.getId()).orElseThrow().getStatus())
                .isEqualTo(ProductionTaskStatus.SKIPPED);
    }

    @Test
    void newOperationsEnforceReadAndMutationPolicyAndRemainDiscoverable() {
        HarnessGateway readOnly = gateway(true, false);
        assertThatThrownBy(() -> readOnly.execute(TOKEN, "farm-data", new HarnessCommand(
                key(), "count-inventory", Map.of("operation-key", key(), "item-id", 1,
                        "reviewed-quantity", 0, "counted-quantity", 0))))
                .isInstanceOf(HarnessPolicyException.class);
        HarnessGateway privateDataDenied = gateway(false, true);
        for (Map.Entry<String, Map<String, Object>> action : Map.of(
                "get-season-settings", Map.<String, Object>of("season-year", 2093),
                "list-order-handoffs", Map.<String, Object>of(),
                "get-operation-receipt", Map.<String, Object>of("operation-key", key())).entrySet()) {
            assertThatThrownBy(() -> privateDataDenied.execute(TOKEN, "farm-data",
                    new HarnessCommand(key(), action.getKey(), action.getValue())))
                    .isInstanceOf(HarnessPolicyException.class);
        }
        JsonNode catalog = success("get-data-catalog", Map.of()).path("output");
        assertThat(catalog.path("operationalWorkflow").path("recoverAction").asText())
                .isEqualTo("get-operation-receipt");
        assertThat(catalog.path("seasonSettingsWorkflow").path("readAction").asText())
                .isEqualTo("get-season-settings");
    }

    @Test
    void receiptReadRecoversAcceptedWorkAfterMutationPolicyChangesWithoutExecuting() {
        Item item = stock(8);
        String key = key();
        Map<String, Object> arguments = countArguments(item, key, 8, 6);
        success("count-inventory", arguments);
        HarnessGateway readOnly = gateway(true, false);

        assertThatThrownBy(() -> readOnly.execute(TOKEN, "farm-data",
                new HarnessCommand(key(), "count-inventory", arguments)))
                .isInstanceOf(HarnessPolicyException.class);
        JsonNode recovered = mapper.valueToTree(readOnly.execute(TOKEN, "farm-data",
                new HarnessCommand(key(), "get-operation-receipt", Map.of("operation-key", key))));
        assertThat(recovered.path("output").path("operation").path("replayed").asBoolean()).isTrue();
        assertThat(recovered.path("output").path("operation").path("result").path("quantity").asInt())
                .isEqualTo(6);
        assertThat(inventory.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(6);
        assertThat(command("get-operation-receipt", Map.of("operation-key", key())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Item stock(int quantity) {
        return inventory.addItem(new Item("Harness stock " + key(), "Flowers/Plants",
                4, "stems", 1, quantity, "Cooler"));
    }

    private CustomerOrder order(Item item, LocalDate date) {
        Customer customer = customers.add(new Customer("Harness customer " + key(),
                "Alex", "alex@example.test", "555-0198", "FLORIST", "Use south gate"));
        return orders.create(customer.getId(), date, "CONFIRMED", "Call first", "DELIVERY",
                List.of(new OrderLine(item.getName(), 6, "stems", 4)));
    }

    private static Map<String, Object> countArguments(Item item, String key, int reviewed, int counted) {
        return Map.of("operation-key", key, "item-id", item.getId(),
                "reviewed-quantity", reviewed, "counted-quantity", counted);
    }

    private HarnessGateway gateway(boolean sensitive, boolean mutations) {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowSensitiveRead(sensitive);
        properties.setAllowMutations(mutations);
        return new HarnessGateway(registry, properties, mapper);
    }

    private JsonNode mcpCall(String action, Map<String, Object> arguments) {
        JsonNode response = mcp.handle(mapper.valueToTree(Map.of(
                "jsonrpc", "2.0", "id", key(), "method", "tools/call", "params", Map.of(
                        "name", "farm-data__" + action, "arguments", arguments,
                        "_meta", Map.of("io.modelcontextprotocol/protocolVersion", "2026-07-28",
                                "io.modelcontextprotocol/clientInfo", Map.of("name", "wiring-test", "version", "1"),
                                "io.modelcontextprotocol/clientCapabilities", Map.of())))));
        assertThat(response.path("result").path("isError").asBoolean()).as("%s", response).isFalse();
        assertThat(response.has("error")).as("%s", response).isFalse();
        return response.path("result").path("structuredContent");
    }

    private JsonNode success(String action, Map<String, Object> arguments) {
        ResponseEntity<JsonNode> response = command(action, arguments);
        assertThat(response.getStatusCode()).as("%s", response.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("success").asBoolean()).isTrue();
        return response.getBody();
    }

    private ResponseEntity<JsonNode> command(String action, Map<String, Object> arguments) {
        return post("/api/harness/farm-data/commands",
                Map.of("requestId", key(), "action", action, "arguments", arguments), true);
    }

    private ResponseEntity<JsonNode> post(String path, Map<String, Object> body, boolean harness) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (harness) {
            headers.set(HarnessController.TOKEN_HEADER, TOKEN);
        }
        return rest.exchange("http://127.0.0.1:" + port + path, HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private static JsonNode findById(JsonNode records, long id) {
        for (JsonNode record : records) {
            if (record.path("id").asLong() == id) {
                return record;
            }
        }
        throw new AssertionError("Missing record " + id);
    }

    private static String key() {
        return "wiring-" + UUID.randomUUID();
    }
}
