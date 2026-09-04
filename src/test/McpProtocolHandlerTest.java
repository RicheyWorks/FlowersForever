package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessManifest;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.HarnessToolAnnotations;
import com.flowerfarm.automation.HarnessToolDefinition;
import com.flowerfarm.service.FieldAttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MCP harness protocol adapter")
class McpProtocolHandlerTest {

    private static final String TOKEN = "mcp-test-harness-token-123456";

    private ObjectMapper mapper;
    private HarnessGateway gateway;
    private HarnessProperties properties;
    private McpProtocolHandler handler;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().findAndRegisterModules();
        gateway = mock(HarnessGateway.class);
        properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.validateConfiguration();
        handler = new McpProtocolHandler(gateway, properties, mapper);
        when(gateway.manifest(TOKEN)).thenReturn(manifest());
    }

    @Test
    @DisplayName("supports current stateless discovery and deterministic tool listing")
    void supportsCurrentProtocol() throws Exception {
        JsonNode discovery = handler.handle(mapper.readTree(modernRequest(
                "discover", "server/discover", "")));

        assertThat(discovery.path("result").path("supportedVersions").get(0).asText())
                .isEqualTo(McpProtocolHandler.CURRENT_PROTOCOL_VERSION);
        assertThat(discovery.path("result").path("capabilities").has("tools")).isTrue();
        assertThat(discovery.path("result").path("cacheScope").asText())
                .isEqualTo("private");
        assertThat(discovery.path("result").path("_meta")
                .path("io.modelcontextprotocol/serverInfo").path("name").asText())
                .isEqualTo(McpProtocolHandler.SERVER_NAME);

        JsonNode listed = handler.handle(mapper.readTree(modernRequest(
                "list", "tools/list", "")));
        JsonNode tools = listed.path("result").path("tools");
        assertThat(listed.path("result").path("resultType").asText())
                .isEqualTo("complete");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).path("name").asText()).isEqualTo("farm-data__catalog");
        assertThat(tools.get(0).path("annotations").path("readOnlyHint").asBoolean())
                .isTrue();
        assertThat(tools.toString()).doesNotContain(TOKEN, "disabled-tool");
    }

    @Test
    @DisplayName("calls the real gateway shape and returns text plus structured content")
    void callsGateway() throws Exception {
        HarnessResponse harnessResponse = new HarnessResponse("1.0", "ignored",
                "farm-data", true, false, "Catalog ready",
                new HarnessSnapshot("farm-data", Instant.parse("2026-09-02T12:00:00Z"),
                        List.of(), List.of(), Map.of("ready", true)),
                Map.of("recordTypes", List.of("inventory")));
        when(gateway.execute(eq(TOKEN), eq("farm-data"), any(HarnessCommand.class)))
                .thenReturn(harnessResponse);

        JsonNode response = handler.handle(mapper.readTree(modernRequest(
                "call-1", "tools/call",
                ",\"name\":\"farm-data__catalog\",\"arguments\":{}")));

        assertThat(response.path("result").path("isError").asBoolean()).isFalse();
        assertThat(response.path("result").path("structuredContent")
                .path("output").path("recordTypes").get(0).asText())
                .isEqualTo("inventory");
        assertThat(response.path("result").path("content").get(0).path("text").asText())
                .contains("Catalog ready", "recordTypes");
        ArgumentCaptor<HarnessCommand> command = ArgumentCaptor.forClass(HarnessCommand.class);
        verify(gateway).execute(eq(TOKEN), eq("farm-data"), command.capture());
        assertThat(command.getValue().requestId()).startsWith("mcp-").hasSize(68);
        assertThat(command.getValue().action()).isEqualTo("catalog");
        assertThat(command.getValue().arguments()).isEmpty();
    }

    @Test
    @DisplayName("supports legacy initialize clients without modern-only result fields")
    void supportsLegacyProtocol() throws Exception {
        JsonNode initialized = handler.handle(mapper.readTree("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                 "protocolVersion":"2025-11-25","capabilities":{},
                 "clientInfo":{"name":"legacy-test","version":"1"}}}
                """));
        JsonNode notification = handler.handle(mapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
        JsonNode listed = handler.handle(mapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"));

        assertThat(initialized.path("result").path("protocolVersion").asText())
                .isEqualTo(McpProtocolHandler.LEGACY_PROTOCOL_VERSION);
        assertThat(notification).isNull();
        assertThat(listed.path("result").path("tools")).hasSize(1);
        assertThat(listed.path("result").has("resultType")).isFalse();
        assertThat(listed.path("result").has("ttlMs")).isFalse();
    }

    @Test
    void acceptsLegacyProgressMetadataWithoutTreatingItAsAProtocolUpgrade() throws Exception {
        handler.handle(mapper.readTree("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                 "protocolVersion":"2025-11-25","capabilities":{},
                 "clientInfo":{"name":"legacy-test","version":"1"}}}
                """));
        JsonNode response = handler.handle(mapper.readTree("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list",
                 "params":{"_meta":{"progressToken":"list-progress"}}}
                """));
        assertThat(response.path("result").path("tools")).hasSize(1);
        assertThat(response.path("result").has("resultType")).isFalse();
        assertThat(handler.handle(mapper.readTree("""
                {"jsonrpc":"2.0","method":"notifications/cancelled","params":[]}
                """))).isNull();
    }

    @Test
    void canonicalizesObjectKeysAndDistinguishesStringFromNumericRequestIds() throws Exception {
        when(gateway.execute(eq(TOKEN), eq("farm-data"), any(HarnessCommand.class)))
                .thenReturn(new HarnessResponse("1.0", "ignored", "farm-data", true, false,
                        "Done", new HarnessSnapshot("farm-data", Instant.now(),
                        List.of(), List.of(), Map.of()), Map.of()));
        ObjectNode first = (ObjectNode) mapper.readTree(modernRequest("1", "tools/call",
                ",\"name\":\"farm-data__catalog\",\"arguments\":{\"rows\":[{\"b\":2,\"a\":1}]}"));
        ObjectNode reordered = (ObjectNode) mapper.readTree(modernRequest("1", "tools/call",
                ",\"name\":\"farm-data__catalog\",\"arguments\":{\"rows\":[{\"a\":1,\"b\":2}]}"));
        handler.handle(first);
        handler.handle(reordered);
        reordered.put("id", 1);
        handler.handle(reordered);

        ArgumentCaptor<HarnessCommand> commands = ArgumentCaptor.forClass(HarnessCommand.class);
        verify(gateway, times(3)).execute(eq(TOKEN), eq("farm-data"), commands.capture());
        assertThat(commands.getAllValues().get(0).requestId())
                .isEqualTo(commands.getAllValues().get(1).requestId())
                .isNotEqualTo(commands.getAllValues().get(2).requestId());
    }

    @Test
    @DisplayName("returns protocol errors and actionable tool execution errors")
    void reportsErrors() throws Exception {
        JsonNode unsupported = handler.handle(mapper.readTree("""
                {"jsonrpc":"2.0","id":"bad-version","method":"server/discover",
                 "params":{"_meta":{
                   "io.modelcontextprotocol/protocolVersion":"1900-01-01",
                   "io.modelcontextprotocol/clientCapabilities":{}}}}
                """));
        assertThat(unsupported.path("error").path("code").asInt()).isEqualTo(-32022);
        assertThat(unsupported.path("error").path("data").path("supported").get(0).asText())
                .isEqualTo(McpProtocolHandler.CURRENT_PROTOCOL_VERSION);

        JsonNode unknown = handler.handle(mapper.readTree(modernRequest(
                "missing", "tools/call",
                ",\"name\":\"missing__tool\",\"arguments\":{}")));
        assertThat(unknown.path("error").path("code").asInt()).isEqualTo(-32602);

        when(gateway.execute(eq(TOKEN), eq("farm-data"), any(HarnessCommand.class)))
                .thenThrow(new IllegalArgumentException("correct the source row"));
        JsonNode failed = handler.handle(mapper.readTree(modernRequest(
                "bad-row", "tools/call",
                ",\"name\":\"farm-data__catalog\",\"arguments\":{}")));
        assertThat(failed.path("result").path("isError").asBoolean()).isTrue();
        assertThat(failed.path("result").path("content").get(0).path("text").asText())
                .contains("correct the source row");

        JsonNode reserved = handler.handle(mapper.readTree(modernRequest(
                "reserved", "tools/call", ",\"name\":\"farm-data__catalog\","
                        + "\"arguments\":{\"_expiresAt\":"
                        + "\"2026-09-02T12:00:00Z\"}")));
        assertThat(reserved.path("result").path("isError").asBoolean()).isTrue();
        assertThat(reserved.path("result").path("content").get(0)
                .path("text").asText()).contains("does not accept _expiresAt");
    }

    @Test
    @DisplayName("adds and translates the reserved deadline for freshness tools")
    void translatesFreshnessDeadline() throws Exception {
        Map<String, Object> schema = Map.of(
                "type", "object", "properties", Map.of(),
                "required", List.of(), "additionalProperties", false);
        HarnessToolDefinition freshTool = new HarnessToolDefinition(
                "device__run-task", "device", "run-task", "Run a fresh task.",
                HarnessRisk.PHYSICAL, true,
                new HarnessToolAnnotations(false, true, false, true, true, true),
                schema);
        when(gateway.manifest(TOKEN)).thenReturn(new HarnessManifest(
                "1.0", 256, 8L * 1024 * 1024, true, 30,
                Map.of(HarnessRisk.PHYSICAL, true), List.of(), List.of(freshTool)));
        HarnessResponse harnessResponse = new HarnessResponse("1.0", "ignored",
                "device", true, false, "Task accepted",
                new HarnessSnapshot("device", Instant.now(), List.of(), List.of(),
                        Map.of()), Map.of());
        when(gateway.execute(eq(TOKEN), eq("device"), any(HarnessCommand.class)))
                .thenReturn(harnessResponse);

        JsonNode listed = handler.handle(mapper.readTree(modernRequest(
                "fresh-list", "tools/list", "")));
        JsonNode tool = listed.path("result").path("tools").get(0);
        assertThat(tool.path("inputSchema").path("required").toString())
                .contains("_expiresAt");
        assertThat(tool.path("inputSchema").path("properties").path("_expiresAt")
                .path("format").asText()).isEqualTo("date-time");

        Instant deadline = Instant.now().plusSeconds(10);
        JsonNode called = handler.handle(mapper.readTree(modernRequest(
                "fresh-call", "tools/call", ",\"name\":\"device__run-task\","
                        + "\"arguments\":{\"_expiresAt\":\"" + deadline + "\"}")));
        assertThat(called.path("result").path("isError").asBoolean()).isFalse();
        ArgumentCaptor<HarnessCommand> command = ArgumentCaptor.forClass(HarnessCommand.class);
        verify(gateway).execute(eq(TOKEN), eq("device"), command.capture());
        assertThat(command.getValue().expiresAt()).isEqualTo(deadline);
        assertThat(command.getValue().arguments()).doesNotContainKey("_expiresAt");
    }

    @Test
    @DisplayName("rejects uninitialized legacy traffic and unknown methods")
    void rejectsMalformedProtocolTraffic() throws Exception {
        JsonNode uninitialized = handler.handle(mapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
        assertThat(uninitialized.path("error").path("code").asInt()).isEqualTo(-32602);

        JsonNode unknownMethod = handler.handle(mapper.readTree(modernRequest(
                "unknown", "unknown/method", "")));
        assertThat(unknownMethod.path("error").path("code").asInt()).isEqualTo(-32601);

        JsonNode invalidRequest = handler.handle(mapper.readTree(
                "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"tools/list\"}"));
        assertThat(invalidRequest.path("error").path("code").asInt()).isEqualTo(-32600);
    }

    @Test
    @DisplayName("keeps stdout protocol-only and recovers after malformed JSON")
    void servesNewlineDelimitedStdio() throws Exception {
        String input = "{bad json}\n"
                + modernRequest("discover", "server/discover", "") + "\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\"}\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();

        new McpStdioServer(handler, mapper).serve(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output,
                new PrintStream(diagnostics, true, StandardCharsets.UTF_8));

        String[] messages = output.toString(StandardCharsets.UTF_8).strip().split("\\R");
        assertThat(messages).hasSize(2);
        assertThat(mapper.readTree(messages[0]).path("error").path("code").asInt())
                .isEqualTo(-32700);
        assertThat(mapper.readTree(messages[1]).path("result")
                .path("supportedVersions")).hasSize(1);
        assertThat(output.toString(StandardCharsets.UTF_8)).doesNotContain("adapter ready");
        assertThat(diagnostics.toString(StandardCharsets.UTF_8)).contains("adapter ready");
    }

    @Test
    void rejectsTrailingJsonAndDuplicateFieldsAndAlwaysEmitsSingleLineFrames() throws Exception {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String request = modernRequest("discovery", "server/discover", "");
        String input = request + " {}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":2,\"method\":\"ping\"}\n"
                + request + "\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new McpStdioServer(handler, mapper).serve(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output,
                new PrintStream(new ByteArrayOutputStream()));

        String[] frames = output.toString(StandardCharsets.UTF_8).strip().split("\\R");
        assertThat(frames).hasSize(3);
        assertThat(mapper.readTree(frames[0]).path("error").path("code").asInt()).isEqualTo(-32700);
        assertThat(mapper.readTree(frames[1]).path("error").path("code").asInt()).isEqualTo(-32700);
        assertThat(mapper.readTree(frames[2]).path("result").path("supportedVersions")).hasSize(1);
    }

    @Test
    void drainsOversizedFramesAndSupportsStringsUpToTheScopedFrameAllowance() throws Exception {
        int limit = 512;
        mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(8).build());
        String prefix = "{\"jsonrpc\":\"2.0\",\"id\":\"ok\",\"method\":\"ping\",\"padding\":\"";
        String suffix = "\"}";
        String exactFrame = prefix + "x".repeat(limit - prefix.length() - suffix.length()) + suffix;
        String oversizedFrame = prefix + "x".repeat(limit) + suffix;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new McpStdioServer(handler, mapper, limit).serve(
                new ByteArrayInputStream((oversizedFrame + "\r\n" + exactFrame + "\r\n")
                        .getBytes(StandardCharsets.UTF_8)), output,
                new PrintStream(new ByteArrayOutputStream()));

        ObjectMapper resultMapper = new ObjectMapper();
        String[] frames = output.toString(StandardCharsets.UTF_8).strip().split("\\R");
        assertThat(frames).hasSize(2);
        JsonNode rejected = resultMapper.readTree(frames[0]);
        assertThat(rejected.path("error").path("code").asInt()).isEqualTo(-32700);
        assertThat(rejected.path("error").path("message").asText()).contains("exceeds 512");
        JsonNode accepted = resultMapper.readTree(frames[1]);
        assertThat(accepted.path("id").asText()).isEqualTo("ok");
        assertThat(accepted.has("result")).isTrue();
        assertThat(mapper.getFactory().streamReadConstraints().getMaxStringLength()).isEqualTo(8);

        int largestAttachmentBase64 = 4 * ((FieldAttachmentService.MAX_ATTACHMENT_BYTES + 2) / 3);
        assertThat(McpStdioServer.MAX_FRAME_CHARACTERS)
                .isGreaterThan(largestAttachmentBase64 + 1024);
    }

    @Test
    void reportsAnOversizedUnterminatedFrameAtEndOfInput() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new McpStdioServer(handler, mapper, 64).serve(
                new ByteArrayInputStream("x".repeat(65).getBytes(StandardCharsets.UTF_8)), output,
                new PrintStream(new ByteArrayOutputStream()));
        JsonNode rejected = mapper.readTree(output.toString(StandardCharsets.UTF_8));
        assertThat(rejected.path("error").path("message").asText()).contains("exceeds 64");
    }

    private HarnessManifest manifest() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of(),
                "additionalProperties", false);
        HarnessToolDefinition catalog = new HarnessToolDefinition(
                "farm-data__catalog", "farm-data", "catalog", "Read data catalog.",
                HarnessRisk.READ, true,
                new HarnessToolAnnotations(true, false, true, false, false, false),
                schema);
        HarnessToolDefinition disabled = new HarnessToolDefinition(
                "farm-data__disabled-tool", "farm-data", "disabled-tool",
                "Disabled mutation.", HarnessRisk.MUTATE, false, schema);
        return new HarnessManifest("1.0", 256, 8L * 1024 * 1024, true, 30,
                Map.of(HarnessRisk.READ, true), List.of(), List.of(disabled, catalog));
    }

    private static String modernRequest(String id, String method, String extraParams) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"method\":\""
                + method + "\",\"params\":{\"_meta\":{"
                + "\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                + "\"io.modelcontextprotocol/clientInfo\":{"
                + "\"name\":\"test-client\",\"version\":\"1\"},"
                + "\"io.modelcontextprotocol/clientCapabilities\":{}}"
                + extraParams + "}}";
    }
}
