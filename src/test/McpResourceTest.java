package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessArgumentSpec;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class McpResourceTest {

    private static final String TOKEN = "mcp-resource-test-token-123456";
    private static final String MANIFEST_URI = "flowersforever://harness/manifest";
    private static final String SENSOR_URI = "flowersforever://harness/sensor/observation";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final HarnessProperties properties = new HarnessProperties();
    private final SensorPlugin sensor = new SensorPlugin("sensor", true);
    private HarnessGateway gateway;
    private McpProtocolHandler handler;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        gateway = new HarnessGateway(new HarnessRegistry(List.of(
                sensor, new SensorPlugin("commands-only", false),
                new SensorPlugin("alpha", true))), properties);
        handler = new McpProtocolHandler(gateway, properties, mapper);
    }

    @Test
    void advertisesOnlyImplementedResourceCapabilities() {
        JsonNode discovery = handler.handle(request("server/discover"));
        JsonNode capabilities = discovery.path("result").path("capabilities");

        assertThat(capabilities.path("resources").isObject()).isTrue();
        assertThat(capabilities.path("resources").size()).isZero();
        assertThat(capabilities.has("tools")).isTrue();
        assertThat(handler.handle(request("resources/subscribe"))
                .path("error").path("code").asInt()).isEqualTo(-32601);
    }

    @Test
    void listsObservationOnlyPluginsInDeterministicOrderWithoutSamplingThem() {
        JsonNode result = handler.handle(request("resources/list")).path("result");

        assertThat(result.path("resources").findValuesAsText("uri")).containsExactly(
                MANIFEST_URI, "flowersforever://harness/alpha/observation", SENSOR_URI);
        assertThat(result.path("resources").findValuesAsText("name")).containsExactly(
                "harness-manifest", "alpha-observation", "sensor-observation");
        assertThat(result.path("resources").findValuesAsText("mimeType"))
                .containsOnly("application/json");
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
        assertThat(result.path("ttlMs").asLong()).isEqualTo(300_000);
        assertThat(result.path("cacheScope").asText()).isEqualTo("private");
        assertThat(result.has("nextCursor")).isFalse();
        assertThat(result.toString()).doesNotContain(TOKEN, "private-location");
        assertThat(sensor.samples).isZero();
        assertThat(handler.handle(request("tools/list")).path("result").path("tools"))
                .isEmpty();
    }

    @Test
    void manifestIncludesTypedObservationAndActionSchemasAndCurrentPolicy() throws Exception {
        JsonNode first = content(handler.handle(read(MANIFEST_URI)));

        assertThat(first).isEqualTo(mapper.readTree(mapper.writeValueAsString(gateway.manifest(TOKEN))));
        JsonNode signal = first.path("plugins").get(0).path("observations").get(0);
        assertThat(signal.path("name").asText()).isEqualTo("temperature");
        assertThat(signal.path("type").asText()).isEqualTo("number");
        assertThat(signal.path("unit").asText()).isEqualTo("C");
        JsonNode tool = first.path("tools").get(0);
        assertThat(tool.path("allowed").asBoolean()).isFalse();
        assertThat(tool.path("inputSchema").path("properties").path("label").path("type").asText())
                .isEqualTo("string");
        assertThat(first.toString()).doesNotContain(TOKEN);

        properties.setAllowDrafts(true);
        JsonNode second = content(handler.handle(read(MANIFEST_URI)));
        assertThat(second.path("tools").get(0).path("allowed").asBoolean()).isTrue();
        assertThat(second.path("policy").path("DRAFT").asBoolean()).isTrue();
    }

    @Test
    void repeatedResourceReadsResampleAndApplyRevokedSensitivePolicy() throws Exception {
        properties.setAllowSensitiveRead(true);
        ObjectNode request = read(SENSOR_URI);
        JsonNode first = handler.handle(request);
        assertThat(content(first).path("metadata").path("location").asText())
                .isEqualTo("private-location");
        assertThat(content(first).path("metadata").path("temperature").asInt()).isEqualTo(21);

        properties.setAllowSensitiveRead(false);
        JsonNode second = handler.handle(request);
        assertThat(content(second).path("metadata").has("location")).isFalse();
        assertThat(content(second).path("metadata").path("temperature").asInt()).isEqualTo(22);
        assertThat(second.path("result").path("ttlMs").asLong()).isZero();
        assertThat(second.path("result").path("cacheScope").asText()).isEqualTo("private");
        assertThat(second.path("result").path("contents").get(0).path("uri").asText())
                .isEqualTo(SENSOR_URI);
        assertThat(second.path("result").path("contents").get(0).path("mimeType").asText())
                .isEqualTo("application/json");
        assertThat(second.path("result").path("contents").get(0).has("blob")).isFalse();
        assertThat(sensor.samples).isEqualTo(2);
    }

    @Test
    void legacyClientsInitializeAndReceiveLegacyResourceEnvelopes() throws Exception {
        ObjectNode uninitialized = request("resources/list");
        uninitialized.withObject("params").remove("_meta");
        assertThat(handler.handle(uninitialized).path("error").path("code").asInt())
                .isEqualTo(-32602);
        ObjectNode initialize = mapper.createObjectNode();
        initialize.put("jsonrpc", "2.0").put("id", 1).put("method", "initialize");
        ObjectNode params = initialize.putObject("params");
        params.put("protocolVersion", McpProtocolHandler.LEGACY_PROTOCOL_VERSION);
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", "resource-test").put("version", "1");
        assertThat(handler.handle(initialize).path("result").path("capabilities")
                .path("resources").isObject()).isTrue();

        JsonNode listed = handler.handle(uninitialized).path("result");
        assertThat(listed.path("resources")).hasSize(3);
        assertThat(listed.has("ttlMs")).isFalse();
        assertThat(listed.has("cacheScope")).isFalse();
        assertThat(listed.has("resultType")).isFalse();
        ObjectNode read = read(SENSOR_URI);
        read.withObject("params").remove("_meta");
        JsonNode response = handler.handle(read);
        assertThat(content(response).path("pluginId").asText()).isEqualTo("sensor");
        assertThat(response.path("result").has("_meta")).isFalse();
        read.withObject("params").put("uri", "flowersforever://harness/missing/observation");
        assertThat(handler.handle(read).path("error").path("code").asInt()).isEqualTo(-32002);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "true", "[]", "{}", "\"\"", "\" \""})
    void rejectsNonTextOrBlankResourceUris(String json) throws Exception {
        ObjectNode read = read(SENSOR_URI);
        read.withObject("params").set("uri", mapper.readTree(json));

        assertInvalid(handler.handle(read));
        assertThat(sensor.samples).isZero();
    }

    @Test
    void rejectsMissingAndOversizedResourceUrisWithoutEchoingThem() {
        assertInvalid(handler.handle(request("resources/read")));
        JsonNode response = handler.handle(read("password=private-resource-value" + "x".repeat(256)));
        assertInvalid(response);
        assertThat(response.toString()).doesNotContain("private-resource-value");
        assertThat(sensor.samples).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "flowersforever://harness/missing/observation",
            "flowersforever://harness/commands-only/observation",
            "flowersforever://harness/sensor/observation?token=secret",
            "flowersforever://harness/sensor/observation#private",
            "flowersforever://harness/sensor/../sensor/observation",
            "flowersforever://harness/%73ensor/observation",
            "flowersforever://harness/SENSOR/observation",
            "flowersforever://harness/sensor/observation/",
            "file:///private-data.json", "https://localhost/private-data.json"
    })
    void refusesEveryUriOutsideTheAuthenticatedResourceList(String uri) {
        JsonNode response = handler.handle(read(uri));

        assertInvalid(response);
        assertThat(response.path("error").path("message").asText()).isEqualTo("Resource not found");
        assertThat(response.has("result")).isFalse();
        assertThat(response.toString()).doesNotContain(uri);
        assertThat(sensor.samples).isZero();
    }

    @Test
    void rejectsUnissuedPaginationCursors() {
        ObjectNode list = request("resources/list");
        list.withObject("params").put("cursor", "arbitrary");
        assertInvalid(handler.handle(list));
        list.withObject("params").putNull("cursor");
        assertInvalid(handler.handle(list));
    }

    @Test
    void everyResourceOperationRequiresEnabledHarnessAuthentication() {
        properties.setEnabled(false);
        for (ObjectNode request : List.of(request("resources/list"), read(MANIFEST_URI), read(SENSOR_URI))) {
            JsonNode response = handler.handle(request);
            assertThat(response.path("error").path("code").asInt()).isEqualTo(-32603);
            assertThat(response.has("result")).isFalse();
            assertThat(response.toString()).doesNotContain(TOKEN, "private-location");
        }
        assertThat(sensor.samples).isZero();
    }

    @Test
    void configuredTransportTokenCannotBypassGatewayAuthentication() {
        HarnessProperties wrongToken = new HarnessProperties();
        wrongToken.setToken("different-resource-token-123456");
        McpProtocolHandler unauthorized = new McpProtocolHandler(gateway, wrongToken, mapper);

        assertThat(unauthorized.handle(read(SENSOR_URI)).path("error").path("code").asInt())
                .isEqualTo(-32603);
        assertThat(sensor.samples).isZero();
    }

    @Test
    void unexpectedObservationFailuresReturnNoRawDetailsAndAllowTheNextRead() throws Exception {
        sensor.fail = true;
        JsonNode failed = handler.handle(read(SENSOR_URI));
        assertThat(failed.path("error").path("code").asInt()).isEqualTo(-32603);
        assertThat(failed.toString()).doesNotContain("private-password");
        sensor.fail = false;

        assertThat(content(handler.handle(read(SENSOR_URI))).path("pluginId").asText()).isEqualTo("sensor");
    }

    @Test
    void stdioExchangesResourceDiscoveryAndFreshJsonContent() throws Exception {
        String input = mapper.writeValueAsString(request("resources/list")) + "\n"
                + mapper.writeValueAsString(read(SENSOR_URI)) + "\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream diagnostics = new PrintStream(new ByteArrayOutputStream())) {
            new McpStdioServer(handler, mapper).serve(new ByteArrayInputStream(
                    input.getBytes(StandardCharsets.UTF_8)), output, diagnostics);
        }
        List<String> responses = output.toString(StandardCharsets.UTF_8).lines().toList();

        assertThat(responses).hasSize(2);
        assertThat(mapper.readTree(responses.get(0)).path("result").path("resources")).hasSize(3);
        assertThat(content(mapper.readTree(responses.get(1))).path("metadata").has("location"))
                .isFalse();
    }

    private ObjectNode request(String method) {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0").put("id", "resource-request").put("method", method);
        ObjectNode metadata = request.putObject("params").putObject("_meta");
        metadata.put("io.modelcontextprotocol/protocolVersion", McpProtocolHandler.CURRENT_PROTOCOL_VERSION);
        metadata.putObject("io.modelcontextprotocol/clientCapabilities");
        return request;
    }

    private ObjectNode read(String uri) {
        ObjectNode request = request("resources/read");
        request.withObject("params").put("uri", uri);
        return request;
    }

    private JsonNode content(JsonNode response) throws Exception {
        assertThat(response.has("error")).as(response.toString()).isFalse();
        assertThat(response.path("result").path("contents")).hasSize(1);
        return mapper.readTree(response.path("result").path("contents").get(0).path("text").asText());
    }

    private static void assertInvalid(JsonNode response) {
        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32602);
    }

    private static final class SensorPlugin implements HarnessPlugin {
        private final String id;
        private final boolean observation;
        private int samples;
        private boolean fail;

        private SensorPlugin(String id, boolean observation) {
            this.id = id;
            this.observation = observation;
        }

        @Override
        public HarnessPluginDescriptor descriptor() {
            return new HarnessPluginDescriptor(id, "Sensor " + id, "Test extension", "1.0",
                    HarnessPluginKind.DEVICE,
                    observation ? Set.of(HarnessPluginCapability.OBSERVATION)
                            : Set.of(HarnessPluginCapability.COMMANDS),
                    observation ? List.of(
                            new HarnessObservationSpec("temperature", HarnessValueType.NUMBER,
                                    "Current sensor temperature", "C", false),
                            new HarnessObservationSpec("location", HarnessValueType.STRING,
                                    "Private device location", null, true)) : List.of(),
                    observation ? List.of() : List.of(new HarnessActionSpec("draft",
                            "Draft sensor label", HarnessRisk.DRAFT, List.of(new HarnessArgumentSpec(
                                    "label", HarnessValueType.STRING, "New sensor label", true)))));
        }

        @Override
        public HarnessSnapshot observe() {
            if (fail) {
                throw new IllegalStateException("password=private-password");
            }
            return new HarnessSnapshot(id, Instant.now(), List.of(), List.of(),
                    Map.of("temperature", 20 + ++samples, "location", "private-location"));
        }

        @Override
        public HarnessExecution execute(HarnessCommand command) {
            throw new AssertionError("Resource reads must never dispatch an action");
        }
    }
}
