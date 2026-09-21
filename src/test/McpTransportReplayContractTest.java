package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessArgumentSpec;
import com.flowerfarm.automation.HarnessAuthenticationException;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessObservationSpec;
import com.flowerfarm.automation.HarnessPlugin;
import com.flowerfarm.automation.HarnessPluginCapability;
import com.flowerfarm.automation.HarnessPluginDescriptor;
import com.flowerfarm.automation.HarnessPluginKind;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.HarnessValueType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpTransportReplayContractTest {

    private static final String TOKEN = "mcp-replay-contract-token-123456";
    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
    private static final String DEADLINE = NOW.plusSeconds(15).toString();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HarnessProperties properties = enabledProperties();
    private final ReceiptPlugin plugin = new ReceiptPlugin();

    @Test
    void cachedPhysicalReceiptSurvivesRevocationWithoutDisclosingSensitiveObservation() {
        McpProtocolHandler handler = handler(gateway(NOW), mapper);
        ObjectNode command = call("physical", "move", DEADLINE);
        assertThat(output(handler.handle(command)).path("replayed").asBoolean()).isFalse();

        properties.setAllowPhysicalActions(false);
        properties.setAllowSensitiveRead(false);
        JsonNode replay = output(handler.handle(command));
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(replay.path("snapshot").path("metadata").has("camera-frame")).isFalse();
        assertThat(plugin.executions).isEqualTo(1);
        assertUnavailable(handler.handle(call("fresh", "move", DEADLINE)));
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void durablePhysicalReceiptSurvivesRestartExpiredDeadlineAndRevocation() {
        ObjectNode command = call("durable", "move", DEADLINE);
        JsonNode first = output(handler(gateway(NOW), mapper).handle(command));
        properties.setAllowPhysicalActions(false);

        JsonNode replay = output(handler(gateway(NOW.plusSeconds(60)), mapper).handle(command));
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(replay.path("requestId").asText()).isEqualTo(first.path("requestId").asText());
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void revokedSensitiveReadCannotReplayEitherCachedOrDurableOutput() {
        ObjectNode command = call("sensitive", "read-secret", null);
        HarnessGateway gateway = gateway(NOW);
        McpProtocolHandler handler = handler(gateway, mapper);
        assertThat(output(handler.handle(command)).path("output").path("detail").asText())
                .isEqualTo("private camera information");

        properties.setAllowSensitiveRead(false);
        assertUnavailable(handler.handle(command));
        assertUnavailable(handler(gateway(NOW), mapper).handle(command));
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void equivalentExpiryRepresentationsKeepTheSamePhysicalCommandIdentity() {
        McpProtocolHandler handler = handler(gateway(NOW), mapper);
        JsonNode first = output(handler.handle(call("equivalent", "move", DEADLINE)));
        JsonNode replay = output(handler.handle(call("equivalent", "move",
                "2026-09-14T14:00:15.000+02:00")));
        assertThat(replay.path("requestId").asText()).isEqualTo(first.path("requestId").asText());
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void mapperFormattingCannotChangeDurableCommandIdentity() {
        ObjectNode command = call("formatting", "move", DEADLINE);
        JsonNode first = output(handler(gateway(NOW), mapper).handle(command));
        ObjectMapper prettyMapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        JsonNode replay = output(handler(gateway(NOW), prettyMapper).handle(command));
        assertThat(replay.path("requestId").asText()).isEqualTo(first.path("requestId").asText());
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void ordinaryArgumentStringsRemainDistinctWhenTheyResembleTimestamps() {
        McpProtocolHandler handler = handler(gateway(NOW), mapper);
        ObjectNode first = call("labels", "move", DEADLINE);
        ((ObjectNode) first.path("params").path("arguments")).put("label", DEADLINE);
        ObjectNode second = first.deepCopy();
        ((ObjectNode) second.path("params").path("arguments"))
                .put("label", "2026-09-14T12:00:15+00:00");
        assertThat(output(handler.handle(first)).path("replayed").asBoolean()).isFalse();
        assertThat(output(handler.handle(second)).path("replayed").asBoolean()).isFalse();
        assertThat(plugin.executions).isEqualTo(2);
    }

    @Test
    void retainsTheExistingCompactUtcIdentity() throws Exception {
        ObjectNode command = call("existing-utc", "move", DEADLINE);
        JsonNode response = output(handler(gateway(NOW), mapper).handle(command));
        assertThat(response.path("requestId").asText()).isEqualTo(legacyCommand(command, mapper)
                .requestId());
    }

    @Test
    void exactLegacyOffsetRetryRecoversItsOriginalDurableReceipt() throws Exception {
        ObjectNode command = call("existing-offset", "move", "2026-09-14T14:00:15+02:00");
        HarnessCommand legacy = legacyCommand(command, mapper);
        gateway(NOW).execute(TOKEN, "receipt-robot", legacy);

        JsonNode response = output(handler(gateway(NOW), mapper).handle(command));
        assertThat(response.path("replayed").asBoolean()).isTrue();
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void exactLegacyPrettyRetryRecoversAfterSwitchingToCompactFormatting() throws Exception {
        ObjectNode command = call("existing-pretty", "move", DEADLINE);
        ObjectMapper prettyMapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        gateway(NOW).execute(TOKEN, "receipt-robot", legacyCommand(command, prettyMapper));

        JsonNode response = output(handler(gateway(NOW), mapper).handle(command));
        assertThat(response.path("replayed").asBoolean()).isTrue();
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void mapperUnicodeEscapingCannotChangeTheIdentityOfOrdinaryStringArguments() {
        ObjectNode command = call("unicode", "move", DEADLINE);
        ((ObjectNode) command.path("params").path("arguments")).put("label", "Fleurs caf\u00e9");
        JsonNode first = output(handler(gateway(NOW), mapper).handle(command));
        ObjectMapper escaped = mapper.copy();
        escaped.getFactory().configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), true);
        JsonNode replay = output(handler(gateway(NOW), escaped).handle(command));
        assertThat(replay.path("requestId").asText()).isEqualTo(first.path("requestId").asText());
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void replayLookupCannotExecuteAMissingExpiredOrDeniedCommand() {
        properties.setAllowPhysicalActions(false);
        HarnessCommand missing = new HarnessCommand("lookup-only", "move", Map.of(),
                NOW.minusSeconds(10));
        assertThat(gateway(NOW).replay(TOKEN, "receipt-robot", missing)).isEmpty();
        assertThat(plugin.executions).isZero();
    }

    @Test
    void replayLookupAuthenticatesBeforeReturningAnExistingReceipt() {
        HarnessGateway gateway = gateway(NOW);
        HarnessCommand command = new HarnessCommand("protected", "move", Map.of(),
                Instant.parse(DEADLINE));
        gateway.execute(TOKEN, "receipt-robot", command);
        assertThatThrownBy(() -> gateway.replay("wrong-token", "receipt-robot", command))
                .isInstanceOf(HarnessAuthenticationException.class);
        assertThat(plugin.executions).isEqualTo(1);
    }

    @Test
    void replayLookupRejectsReusingAnExistingIdForDifferentArguments() {
        HarnessGateway gateway = gateway(NOW);
        HarnessCommand command = new HarnessCommand("bound", "move", Map.of(),
                Instant.parse(DEADLINE));
        gateway.execute(TOKEN, "receipt-robot", command);
        HarnessCommand changed = new HarnessCommand("bound", "move", Map.of("label", "different"),
                Instant.parse(DEADLINE));
        assertThatThrownBy(() -> gateway.replay(TOKEN, "receipt-robot", changed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different command");
        assertThat(plugin.executions).isEqualTo(1);
    }

    private static HarnessCommand legacyCommand(ObjectNode request, ObjectMapper legacyMapper)
            throws Exception {
        JsonNode params = request.path("params");
        JsonNode arguments = params.path("arguments");
        String material = request.path("id").toString() + '\u0000'
                + params.path("name").asText() + '\u0000'
                + legacyMapper.writeValueAsString(arguments);
        String requestId = "mcp-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8)));
        return new HarnessCommand(requestId, "move", Map.of(),
                Instant.parse(arguments.path("_expiresAt").asText()));
    }

    private HarnessGateway gateway(Instant now) {
        return new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private McpProtocolHandler handler(HarnessGateway gateway, ObjectMapper objectMapper) {
        return new McpProtocolHandler(gateway, properties, objectMapper);
    }

    private ObjectNode call(String id, String action, String expiry) {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", "tools/call");
        ObjectNode params = request.putObject("params");
        ObjectNode metadata = params.putObject("_meta");
        metadata.put("io.modelcontextprotocol/protocolVersion",
                McpProtocolHandler.CURRENT_PROTOCOL_VERSION);
        metadata.putObject("io.modelcontextprotocol/clientCapabilities");
        params.put("name", "receipt-robot__" + action);
        ObjectNode arguments = params.putObject("arguments");
        if (expiry != null) {
            arguments.put("_expiresAt", expiry);
        }
        return request;
    }

    private static JsonNode output(JsonNode response) {
        assertThat(response.has("error")).as("protocol response: %s", response).isFalse();
        assertThat(response.path("result").path("isError").asBoolean())
                .as("tool response: %s", response).isFalse();
        return response.path("result").path("structuredContent");
    }

    private static void assertUnavailable(JsonNode response) {
        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(response.path("error").path("message").asText()).contains("unavailable");
        assertThat(response.toString()).doesNotContain("private camera information");
    }

    private static HarnessProperties enabledProperties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        properties.setAllowPhysicalActions(true);
        properties.setAllowSensitiveRead(true);
        return properties;
    }

    private static final class ReceiptPlugin implements HarnessPlugin {
        private final Map<HarnessCommand, HarnessExecution> receipts = new HashMap<>();
        private int executions;

        @Override
        public HarnessPluginDescriptor descriptor() {
            return new HarnessPluginDescriptor("receipt-robot", "Receipt robot",
                    "Physical replay fixture", "1.0", HarnessPluginKind.ROBOT,
                    Set.of(HarnessPluginCapability.COMMANDS, HarnessPluginCapability.OBSERVATION,
                            HarnessPluginCapability.PHYSICAL_ACTUATION),
                    List.of(new HarnessObservationSpec("camera-frame", HarnessValueType.STRING,
                            "Private camera information", null, true)),
                    List.of(new HarnessActionSpec("move", "Move", HarnessRisk.PHYSICAL,
                                    List.of(new HarnessArgumentSpec("label", HarnessValueType.STRING,
                                            "Opaque operator label", false))),
                            new HarnessActionSpec("read-secret", "Read private information",
                                    HarnessRisk.SENSITIVE_READ, List.of())));
        }

        @Override
        public HarnessSnapshot observe() {
            return new HarnessSnapshot("receipt-robot", NOW, List.of(), List.of(),
                    Map.of("camera-frame", "private camera information"));
        }

        @Override
        public Optional<HarnessExecution> replay(HarnessCommand command) {
            return Optional.ofNullable(receipts.get(command));
        }

        @Override
        public HarnessExecution execute(HarnessCommand command) {
            executions++;
            HarnessExecution execution = HarnessExecution.success("Completed",
                    command.action().equals("read-secret")
                            ? Map.of("detail", "private camera information") : Map.of());
            receipts.put(command, execution);
            return execution;
        }
    }
}
