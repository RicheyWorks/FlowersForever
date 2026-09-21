package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessPlugin;
import com.flowerfarm.automation.HarnessPluginCapability;
import com.flowerfarm.automation.HarnessPluginDescriptor;
import com.flowerfarm.automation.HarnessPluginKind;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpOptionalDeadlineTest {

    private static final String TOKEN = "mcp-optional-deadline-token-123456";
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = mock(Clock.class);
    private final DeadlinePlugin plugin = new DeadlinePlugin();

    @Test
    void discoveryPublishesOptionalOrdinaryAndRequiredPhysicalDeadlines() {
        McpProtocolHandler handler = handler();
        ObjectNode request = request("list", "tools/list");

        JsonNode tools = handler.handle(request).path("result").path("tools");

        assertThat(tools).hasSize(3);
        for (JsonNode tool : tools) {
            JsonNode schema = tool.path("inputSchema");
            assertThat(schema.path("properties").path("_expiresAt").path("format").asText())
                    .isEqualTo("date-time");
            if (tool.path("name").asText().endsWith("__move")) {
                assertThat(schema.path("required").toString()).contains("_expiresAt");
                assertThat(schema.path("properties").path("_expiresAt")
                        .path("description").asText()).contains("30 seconds");
            } else {
                assertThat(schema.path("required").toString()).doesNotContain("_expiresAt");
                assertThat(schema.path("properties").path("_expiresAt")
                        .path("description").asText()).contains("Optional command deadline");
            }
        }
    }

    @Test
    void translatesOrdinaryDeadlinesAndKeepsThemOptionalWithoutAPhysicalTtl() {
        McpProtocolHandler handler = handler();

        output(handler.handle(call("no-deadline", "write", null)));
        assertThat(plugin.lastCommand.expiresAt()).isNull();
        Instant deadline = NOW.plusSeconds(3_600);
        output(handler.handle(call("later", "write", deadline.toString())));

        assertThat(plugin.lastCommand.expiresAt()).isEqualTo(deadline);
        assertThat(plugin.lastCommand.arguments()).isEmpty();
        assertThat(plugin.executions).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "write"})
    void rejectsStaleAndExactBoundaryDeadlinesBeforeOrdinaryExecution(String action) {
        McpProtocolHandler handler = handler();

        for (Instant deadline : List.of(NOW.minusNanos(1), NOW)) {
            assertToolError(handler.handle(call("expired-" + deadline, action, deadline.toString())),
                    "expired");
        }

        assertThat(plugin.executions).isZero();
        output(handler.handle(call("recovered", action, NOW.plusNanos(1).toString())));
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void exactOrdinaryReceiptStillReplaysAfterItsDeadline() {
        McpProtocolHandler handler = handler();
        ObjectNode command = call("receipt", "write", NOW.plusSeconds(1).toString());
        JsonNode accepted = output(handler.handle(command));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        JsonNode replay = output(handler.handle(command));

        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(replay.path("requestId")).isEqualTo(accepted.path("requestId"));
        assertThat(plugin.executions).isEqualTo(1);
        assertToolError(handler.handle(call("fresh", "write", NOW.plusSeconds(1).toString())),
                "expired");
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void equivalentOrdinaryDeadlineInstantsKeepTheirReplayIdentity() {
        McpProtocolHandler handler = handler();
        JsonNode accepted = output(handler.handle(call("equivalent", "write",
                "2026-09-21T12:00:15Z")));

        JsonNode replay = output(handler.handle(call("equivalent", "write",
                "2026-09-21T14:00:15.000+02:00")));

        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(replay.path("requestId")).isEqualTo(accepted.path("requestId"));
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void rejectsMalformedOrdinaryDeadlinesBeforeExecution() {
        McpProtocolHandler handler = handler();
        for (JsonNode invalid : List.of(mapper.nullNode(), mapper.getNodeFactory().numberNode(123),
                mapper.getNodeFactory().textNode("next week"))) {
            ObjectNode command = call("malformed", "write", null);
            command.withObject("params").withObject("arguments").set("_expiresAt", invalid);

            assertToolError(handler.handle(command), "ISO-8601");
        }
        assertThat(plugin.executions).isZero();
    }

    @Test
    void physicalToolsStillRequireDeadlineAndEnforceMaximumTtl() {
        McpProtocolHandler handler = handler();

        assertToolError(handler.handle(call("missing", "move", null)), "expiresAt");
        assertToolError(handler.handle(call("too-far", "move", NOW.plusSeconds(31).toString())),
                "30 seconds");
        assertThat(plugin.executions).isZero();
        output(handler.handle(call("physical", "move", NOW.plusSeconds(30).toString())));
        assertThat(plugin.executions).isEqualTo(1);
    }

    private McpProtocolHandler handler() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        properties.setAllowPhysicalActions(true);
        when(clock.instant()).thenReturn(NOW);
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties, clock);
        return new McpProtocolHandler(gateway, properties, mapper);
    }

    private ObjectNode call(String id, String action, String expiry) {
        ObjectNode request = request(id, "tools/call");
        ObjectNode params = request.withObject("params");
        params.put("name", "deadline-device__" + action);
        ObjectNode arguments = params.putObject("arguments");
        if (expiry != null) {
            arguments.put("_expiresAt", expiry);
        }
        return request;
    }

    private ObjectNode request(String id, String method) {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        ObjectNode metadata = request.putObject("params").putObject("_meta");
        metadata.put("io.modelcontextprotocol/protocolVersion", McpProtocolHandler.CURRENT_PROTOCOL_VERSION);
        metadata.putObject("io.modelcontextprotocol/clientCapabilities");
        return request;
    }

    private static JsonNode output(JsonNode response) {
        assertThat(response.has("error")).as("%s", response).isFalse();
        assertThat(response.path("result").path("isError").asBoolean()).as("%s", response).isFalse();
        return response.path("result").path("structuredContent");
    }

    private static void assertToolError(JsonNode response, String detail) {
        assertThat(response.path("result").path("isError").asBoolean()).as("%s", response).isTrue();
        assertThat(response.path("result").path("structuredContent").path("error").asText())
                .contains(detail);
    }

    private static final class DeadlinePlugin implements HarnessPlugin {
        private int executions;
        private HarnessCommand lastCommand;

        @Override
        public HarnessPluginDescriptor descriptor() {
            return new HarnessPluginDescriptor("deadline-device", "Deadline device",
                    "Deadline contract fixture", "1.0", HarnessPluginKind.DEVICE,
                    Set.of(HarnessPluginCapability.COMMANDS, HarnessPluginCapability.OBSERVATION,
                            HarnessPluginCapability.PHYSICAL_ACTUATION),
                    List.of(new HarnessActionSpec("read", "Read", HarnessRisk.READ, List.of()),
                            new HarnessActionSpec("write", "Write", HarnessRisk.MUTATE, List.of()),
                            new HarnessActionSpec("move", "Move", HarnessRisk.PHYSICAL, List.of())));
        }

        @Override
        public HarnessSnapshot observe() {
            return new HarnessSnapshot("deadline-device", NOW, List.of(), List.of(), Map.of());
        }

        @Override
        public HarnessExecution execute(HarnessCommand command) {
            executions++;
            lastCommand = command;
            return HarnessExecution.success("Completed", Map.of());
        }
    }
}
