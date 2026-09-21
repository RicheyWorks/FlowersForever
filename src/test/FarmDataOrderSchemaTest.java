package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.mcp.McpProtocolHandler;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmDataImportReceipt;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class FarmDataOrderSchemaTest {

    private static final String TOKEN = "farm-order-schema-token-123456";
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 9, 20);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CustomerService customers = mock(CustomerService.class);
    private final OrderService orders = mock(OrderService.class);
    private final FarmDataImportReceiptService receipts = mock(FarmDataImportReceiptService.class);
    private final Customer customer = new Customer(8L, "Schema Market", "", "", "", "MARKET", "");
    private final FarmDataHarnessPlugin plugin = new FarmDataHarnessPlugin(
            null, customers, null, orders, null, null, null, receipts,
            new FarmDataMappingService(mapper), null, null, null, null, mapper);
    private final HarnessProperties properties = properties();
    private final HarnessGateway gateway = new HarnessGateway(
            new HarnessRegistry(List.of(plugin)), properties, mapper);

    @BeforeEach
    void setUpDomainFixtures() {
        when(customers.findById(8L)).thenReturn(Optional.of(customer));
        when(orders.create(eq(8L), any(), any(), any(), any(), any())).thenAnswer(invocation ->
                savedOrder(invocation.getArgument(2), invocation.getArgument(5)));
        when(orders.importHistorical(eq(8L), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> savedOrder(
                        invocation.getArgument(2), invocation.getArgument(5)));
        when(receipts.record(anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> new FarmDataImportReceipt(invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3),
                        invocation.getArgument(4), invocation.getArgument(5)));
    }

    @Test
    void restAndMcpPublishDistinctLiveAndHistoricalOrderStatusChoices() throws Exception {
        var mvc = standaloneSetup(new HarnessController(gateway)).build();
        String response = mvc.perform(get("/api/harness/manifest")
                        .header(HarnessController.TOKEN_HEADER, TOKEN))
                .andReturn().getResponse().getContentAsString();
        assertToolSchemas(mapper.readTree(response).path("tools"));

        McpProtocolHandler mcp = new McpProtocolHandler(gateway, properties, mapper);
        JsonNode listed = mcp.handle(mapper.readTree("""
                {"jsonrpc":"2.0","id":"order-schemas","method":"tools/list","params":{
                 "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
                          "io.modelcontextprotocol/clientCapabilities":{}}}}
                """));
        assertToolSchemas(listed.path("result").path("tools"));
    }

    @Test
    void catalogPreservesMigrationSchemaAndIdentifiesTheOperationalOrderSchema() {
        JsonNode catalog = mapper.valueToTree(gateway.execute(TOKEN, "farm-data",
                new HarnessCommand("catalog", "get-data-catalog", Map.of())).output());
        JsonNode orderCatalog = catalog.path("recordTypes").path("orders");
        assertThat(orderCatalog.path("migrationAction").asText()).isEqualTo("import-order-history");
        assertThat(orderCatalog.path("operationalAction").asText()).isEqualTo("create-orders");
        assertThat(statusChoices(orderCatalog.path("writeSchema")))
                .containsExactly("DRAFT", "CONFIRMED", "FULFILLED", "CANCELLED");
        assertThat(statusChoices(orderCatalog.path("operationalWriteSchema")))
                .containsExactly("DRAFT", "CONFIRMED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FULFILLED", "CANCELLED"})
    void rejectsTerminalStatusBeforeStartingAnyOrderInTheBatch(String terminalStatus) {
        HarnessCommand command = new HarnessCommand("reject-" + terminalStatus, "create-orders",
                Map.of("records", List.of(orderRecord("DRAFT"), orderRecord(terminalStatus))));
        assertThatThrownBy(() -> gateway.execute(TOKEN, "farm-data", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("records[1]", ".status")
                .hasMessageContaining("DRAFT", "CONFIRMED");
        verifyNoInteractions(customers, orders, receipts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CONFIRMED"})
    void acceptsEveryAdvertisedNewOrderStatus(String status) {
        var response = gateway.execute(TOKEN, "farm-data", new HarnessCommand(
                "live-" + status, "create-orders", Map.of("records", List.of(orderRecord(status)))));
        assertThat(response.success()).isTrue();
        verify(orders).create(eq(8L), eq(ORDER_DATE), eq(status), isNull(), eq("MARKET"), any());
        verify(orders, never()).importHistorical(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void permitsAnOmittedNewOrderStatusForTheExistingDraftDefault() {
        Map<String, Object> record = new LinkedHashMap<>(orderRecord("DRAFT"));
        record.remove("status");
        var response = gateway.execute(TOKEN, "farm-data", new HarnessCommand(
                "default-status", "create-orders", Map.of("records", List.of(record))));
        assertThat(response.success()).isTrue();
        verify(orders).create(eq(8L), eq(ORDER_DATE), isNull(), isNull(), eq("MARKET"), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CONFIRMED", "FULFILLED", "CANCELLED"})
    void historicalImportsRetainAllSupportedStates(String status) {
        var response = gateway.execute(TOKEN, "farm-data", new HarnessCommand(
                "history-" + status, "import-order-history",
                Map.of("records", List.of(orderRecord(status)))));
        assertThat(response.success()).isTrue();
        verify(orders).importHistorical(
                eq(8L), eq(ORDER_DATE), eq(status), isNull(), eq("MARKET"), any());
        verify(orders, never()).create(anyLong(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FULFILLED", "CANCELLED"})
    void mappedPreviewAndImportKeepTerminalStatesOnTheHistoricalRoute(String status) {
        Map<String, Object> record = new LinkedHashMap<>(orderRecord(status));
        record.put("sourceKey", "prior-order-" + status);
        Map<String, Object> arguments = Map.of("record-type", "orders", "source-system", "prior-app",
                "source-records", List.of(record));
        var preview = plugin.execute(new HarnessCommand("preview-" + status,
                "preview-mapped-records", arguments));
        assertThat(preview.output()).containsEntry("ready", true)
                .containsEntry("targetAction", "import-order-history");
        var imported = plugin.execute(new HarnessCommand("mapped-" + status,
                "import-mapped-records", arguments));
        assertThat(imported.output()).containsEntry("processed", 1)
                .containsEntry("targetAction", "import-order-history");
        verify(orders).importHistorical(
                eq(8L), eq(ORDER_DATE), eq(status), isNull(), eq("MARKET"), any());
        verify(orders, never()).create(anyLong(), any(), any(), any(), any(), any());
    }

    private void assertToolSchemas(JsonNode tools) {
        JsonNode creation = tool(tools, "farm-data__create-orders");
        JsonNode history = tool(tools, "farm-data__import-order-history");
        assertThat(statusChoices(creation.path("inputSchema").path("properties")
                .path("records").path("items"))).containsExactly("DRAFT", "CONFIRMED");
        assertThat(statusChoices(history.path("inputSchema").path("properties")
                .path("records").path("items")))
                .containsExactly("DRAFT", "CONFIRMED", "FULFILLED", "CANCELLED");
    }

    private static JsonNode tool(JsonNode tools, String name) {
        for (JsonNode tool : tools) {
            if (name.equals(tool.path("name").asText())) {
                return tool;
            }
        }
        throw new AssertionError("Missing tool " + name);
    }

    private static List<String> statusChoices(JsonNode schema) {
        List<String> choices = new ArrayList<>();
        schema.path("properties").path("status").path("enum")
                .forEach(value -> choices.add(value.asText()));
        return choices;
    }

    private static Map<String, Object> orderRecord(String status) {
        return Map.of("customerId", 8, "orderDate", ORDER_DATE.toString(), "status", status,
                "handoffType", "MARKET", "lines", List.of(Map.of(
                        "productName", "Dahlia", "quantity", 4, "unit", "stems", "unitPrice", 2.5)));
    }

    private CustomerOrder savedOrder(String status, List<OrderLine> lines) {
        CustomerOrder saved = new CustomerOrder(customer, ORDER_DATE, status, "", "MARKET");
        saved.setId(101L);
        lines.forEach(saved::addLine);
        return saved;
    }

    private static HarnessProperties properties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowMutations(true);
        return properties;
    }
}
