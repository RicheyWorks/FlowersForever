package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessManifest;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpProtocolErrorSafetyTest {

    private static final String TOKEN = "mcp-protocol-errors-token-123456";
    private static final String DETAIL = "password=protocol-password "
            + "Authorization: Bearer protocol-bearer\r\n"
            + "https://operator:protocol-url-password@localhost/failure " + "detail ".repeat(100);
    private static final String PROTOCOL_KEY = "io.modelcontextprotocol/protocolVersion";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HarnessGateway gateway = mock(HarnessGateway.class);

    @Test
    void redactsAndBoundsUnknownMethodsWithoutChangingTheRequestId() {
        ObjectNode request = request("unknown/" + DETAIL);
        request.put("id", "request password=correlation-value\r\n" + "id ".repeat(100));

        JsonNode response = handler().handle(request);

        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32601);
        assertSafeMessage(response.path("error").path("message").asText());
        assertThat(response.path("id")).isEqualTo(request.path("id"));
    }

    @Test
    void redactsAndBoundsUnknownToolsWithoutExecutingThem() {
        when(gateway.manifest(TOKEN)).thenReturn(manifest(List.of()));
        ObjectNode request = request("tools/call");
        request.withObject("params").put("name", "missing__" + DETAIL);

        JsonNode response = handler().handle(request);

        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32602);
        assertSafeMessage(response.path("error").path("message").asText());
        verify(gateway, never()).execute(any(), any(), any());
    }

    @Test
    void redactsUnavailableToolsAfterReplayMissWithoutExecutingThem() {
        String name = "example__" + DETAIL.trim();
        HarnessToolDefinition tool = new HarnessToolDefinition(name, "example", "read",
                "Read example", HarnessRisk.READ, false, Map.of("type", "object"));
        when(gateway.manifest(TOKEN)).thenReturn(manifest(List.of(tool)));
        ObjectNode request = request("tools/call");
        request.withObject("params").put("name", name);

        JsonNode response = handler().handle(request);

        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32602);
        assertSafeMessage(response.path("error").path("message").asText());
        verify(gateway, atLeastOnce()).replay(eq(TOKEN), eq("example"), any());
        verify(gateway, never()).execute(any(), any(), any());
    }

    @Test
    void redactsAndBoundsEchoedUnsupportedProtocolVersions() {
        ObjectNode request = request("server/discover");
        request.withObject("params").withObject("_meta").put(PROTOCOL_KEY, DETAIL);

        JsonNode response = handler().handle(request);

        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32022);
        assertThat(response.path("error").path("message").asText())
                .isEqualTo("Unsupported protocol version");
        assertSafeMessage(response.path("error").path("data").path("requested").asText());
        assertThat(response.path("error").path("data").path("supported").get(0).asText())
                .isEqualTo(McpProtocolHandler.CURRENT_PROTOCOL_VERSION);
    }

    @Test
    void preservesOrdinaryProtocolDetailsAndNumericCorrelationIds() {
        ObjectNode request = request("server/discover");
        request.put("id", Long.MAX_VALUE);
        request.withObject("params").withObject("_meta").put(PROTOCOL_KEY, "2020-01-01");

        JsonNode response = handler().handle(request);

        assertThat(response.path("error").path("data").path("requested").asText())
                .isEqualTo("2020-01-01");
        assertThat(response.path("id")).isEqualTo(request.path("id"));
        assertThat(handler().parseError().path("error").path("message").asText())
                .isEqualTo("Parse error: invalid JSON");
    }

    private ObjectNode request(String method) {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", "protocol-error");
        request.put("method", method);
        ObjectNode metadata = request.putObject("params").putObject("_meta");
        metadata.put(PROTOCOL_KEY, McpProtocolHandler.CURRENT_PROTOCOL_VERSION);
        metadata.putObject("io.modelcontextprotocol/clientCapabilities");
        return request;
    }

    private McpProtocolHandler handler() {
        HarnessProperties properties = new HarnessProperties();
        properties.setToken(TOKEN);
        return new McpProtocolHandler(gateway, properties, mapper);
    }

    private static HarnessManifest manifest(List<HarnessToolDefinition> tools) {
        return new HarnessManifest("1.0", 256, true, Map.of(HarnessRisk.READ, true), List.of(), tools);
    }

    private static void assertSafeMessage(String message) {
        assertThat(message).contains("password=[REDACTED]", "Bearer [REDACTED]")
                .doesNotContain("protocol-password", "protocol-bearer", "protocol-url-password",
                        "\r", "\n")
                .hasSizeLessThanOrEqualTo(240);
    }
}
