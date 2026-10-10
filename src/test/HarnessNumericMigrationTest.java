package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessArgumentSpec;
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
import com.flowerfarm.automation.HarnessValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessNumericMigrationTest {
    private static final String TOKEN = "numeric-migration-token-123456";
    private static final Instant NOW = Instant.parse("2026-09-14T05:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(20);

    @ParameterizedTest
    @ValueSource(strings = {"5.0000000000000001", "9007199254740993.25", "-1e-400"})
    void refusesAmbiguousRoundedReceiptsWithoutRepeatingPhysicalEffects(String value) throws Exception {
        for (boolean pretty : List.of(false, true)) {
            Fixture fixture = new Fixture();
            String request = call("\"old-precision\"", value, "{}");
            fixture.seedLegacyReceipt(request, pretty);

            JsonNode response = fixture.restartedCall(request);

            assertAmbiguousReceipt(response, fixture);
        }
    }

    @Test
    void refusesARoundedLegacyNumericRpcIdWithoutRepeatingItsEffect() throws Exception {
        Fixture fixture = new Fixture();
        String request = call("9007199254740992.1", "1.5", "{}");
        fixture.seedLegacyReceipt(request, false);

        JsonNode response = fixture.restartedCall(request);

        assertAmbiguousReceipt(response, fixture);
    }

    @Test
    void retainsNegativeZeroIdentityAtMultipleNestedPathsWithoutCombinatorialAliases() throws Exception {
        Fixture fixture = new Fixture();
        String request = call("-0.0", "-0.0",
                "{\"zeros\":[-0.0,{\"a/b~c\":-0e2}],\"text\":\"-0.0\"}");
        String originalId = fixture.seedLegacyReceipt(request, false);

        JsonNode response = fixture.restartedCall(request);

        assertThat(response.path("result").path("isError").asBoolean()).isFalse();
        JsonNode output = response.path("result").path("structuredContent");
        assertThat(output.path("replayed").asBoolean()).isTrue();
        assertThat(output.path("requestId").asText()).isEqualTo(originalId);
        assertThat(fixture.plugin.executed).hasSize(1);
    }

    @Test
    void retainsDurableReplayForLosslessLegacyDecimalSpellings() throws Exception {
        for (String value : List.of("1.5", "1.0", "1.0e20")) {
            Fixture fixture = new Fixture();
            String request = call("\"ordinary\"", value, "{}");
            String originalId = fixture.seedLegacyReceipt(request, false);

            JsonNode response = fixture.restartedCall(request);

            assertThat(response.path("result").path("isError").asBoolean()).isFalse();
            JsonNode output = response.path("result").path("structuredContent");
            assertThat(output.path("replayed").asBoolean())
                    .as("Value %s; seeded %s; response %s", value, originalId, response).isTrue();
            assertThat(output.path("requestId").asText()).isEqualTo(originalId);
            assertThat(fixture.plugin.executed).hasSize(1);
        }
    }

    @Test
    void executesAndReplaysNewExactRequestsWhenNoRoundedReceiptExists() throws Exception {
        Fixture fixture = new Fixture();
        String request = call("\"new-precision\"", "5.0000000000000001", "{}");

        JsonNode original = fixture.restartedCall(request);
        JsonNode retried = fixture.restartedCall(request);

        assertThat(original.path("result").path("isError").asBoolean()).isFalse();
        assertThat(original.path("result").path("structuredContent").path("replayed").asBoolean())
                .isFalse();
        assertThat(retried.path("result").path("isError").asBoolean()).isFalse();
        assertThat(retried.path("result").path("structuredContent").path("replayed").asBoolean())
                .isTrue();
        assertThat(fixture.plugin.executed).hasSize(1);
    }

    private static void assertAmbiguousReceipt(JsonNode response, Fixture fixture) {
        assertThat(response.path("result").path("isError").asBoolean())
                .as("Response: %s", response).isTrue();
        assertThat(response.path("result").path("structuredContent").path("error").asText())
                .containsIgnoringCase("legacy");
        assertThat(fixture.plugin.executed).hasSize(1);
    }

    private static String call(String id, String value, String payload) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\""
                + McpProtocolHandler.CURRENT_PROTOCOL_VERSION
                + "\",\"io.modelcontextprotocol/clientCapabilities\":{}},"
                + "\"name\":\"numeric-migration__record\",\"arguments\":{"
                + "\"value\":" + value + ",\"payload\":" + payload
                + ",\"_expiresAt\":\"" + DEADLINE + "\"}}}";
    }

    private static final class Fixture {
        private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        private final DurablePlugin plugin = new DurablePlugin();
        private final HarnessProperties properties = properties();

        private String seedLegacyReceipt(String wire, boolean pretty) throws Exception {
            JsonNode request = mapper.readTree(wire);
            ObjectNode rawArguments = (ObjectNode) request.path("params").path("arguments");
            ObjectWriter writer = pretty ? mapper.writerWithDefaultPrettyPrinter() : mapper.writer();
            String material = request.path("id").toString() + '\u0000' + "numeric-migration__record"
                    + '\u0000' + writer.writeValueAsString(sorted(rawArguments));
            String id = "mcp-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
            ObjectNode arguments = rawArguments.deepCopy();
            arguments.remove("_expiresAt");
            Map<String, Object> values = mapper.convertValue(arguments,
                    mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            gateway().execute(TOKEN, "numeric-migration",
                    new HarnessCommand(id, "record", values, DEADLINE));
            return id;
        }

        private JsonNode restartedCall(String wire) throws Exception {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            McpStdioServer server = new McpStdioServer(
                    new McpProtocolHandler(gateway(), properties, mapper), mapper);
            try (PrintStream diagnostics = new PrintStream(new ByteArrayOutputStream())) {
                server.serve(new ByteArrayInputStream((wire + "\n").getBytes(StandardCharsets.UTF_8)),
                        output, diagnostics);
            }
            return mapper.readTree(output.toByteArray());
        }

        private HarnessGateway gateway() {
            return new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private JsonNode sorted(JsonNode node) {
            if (node.isObject()) {
                ObjectNode ordered = mapper.createObjectNode();
                TreeSet<String> fields = new TreeSet<>();
                node.fieldNames().forEachRemaining(fields::add);
                fields.forEach(field -> ordered.set(field, sorted(node.get(field))));
                return ordered;
            }
            if (node.isArray()) {
                ArrayNode ordered = mapper.createArrayNode();
                node.forEach(value -> ordered.add(sorted(value)));
                return ordered;
            }
            return node;
        }

        private static HarnessProperties properties() {
            HarnessProperties settings = new HarnessProperties();
            settings.setEnabled(true);
            settings.setToken(TOKEN);
            settings.setAllowMutations(true);
            settings.setAllowDestructive(true);
            settings.setAllowPhysicalActions(true);
            return settings;
        }
    }

    private static final class DurablePlugin implements HarnessPlugin {
        private final List<HarnessCommand> executed = new ArrayList<>();
        private final Map<String, HarnessExecution> receipts = new LinkedHashMap<>();

        @Override public HarnessPluginDescriptor descriptor() {
            return new HarnessPluginDescriptor("numeric-migration", "Numeric migration fixture", "Durable effects", "1",
                    HarnessPluginKind.DEVICE, Set.of(HarnessPluginCapability.OBSERVATION,
                            HarnessPluginCapability.COMMANDS, HarnessPluginCapability.PHYSICAL_ACTUATION),
                    List.of(new HarnessActionSpec("record", "Record physical value", HarnessRisk.PHYSICAL,
                            List.of(new HarnessArgumentSpec("value", HarnessValueType.NUMBER, "Value", true),
                                    new HarnessArgumentSpec("payload", HarnessValueType.OBJECT, "Payload", true)))));
        }

        @Override public HarnessSnapshot observe() {
            return new HarnessSnapshot("numeric-migration", null, List.of(), List.of(), Map.of());
        }

        @Override public Optional<HarnessExecution> replay(HarnessCommand command) {
            // The old MCP hash is the persisted identity; the original unrounded wire value is unavailable.
            return Optional.ofNullable(receipts.get(command.requestId()));
        }

        @Override public HarnessExecution execute(HarnessCommand command) {
            executed.add(command);
            HarnessExecution result = HarnessExecution.success("Physical effect recorded");
            receipts.put(command.requestId(), result);
            return result;
        }
    }
}
