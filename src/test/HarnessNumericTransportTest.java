package com.flowerfarm.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessArgumentSpec;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessPlugin;
import com.flowerfarm.automation.HarnessPluginDescriptor;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.HarnessValueType;
import com.flowerfarm.automation.mcp.McpProtocolHandler;
import com.flowerfarm.automation.mcp.McpStdioServer;
import com.flowerfarm.controller.ApiExceptionHandler;
import com.flowerfarm.controller.HarnessController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HarnessNumericTransportTest {
    private static final String TOKEN = "numeric-transport-test-token-1234";
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    private final RecordingPlugin plugin = new RecordingPlugin();
    private final HarnessProperties properties = properties();
    private final HarnessGateway gateway = new HarnessGateway(
            new HarnessRegistry(List.of(plugin)), properties, mapper);

    @ParameterizedTest
    @ValueSource(strings = {"9007199254740993.25", "1.234567890123456789", "1e-400", "1e400"})
    void restDeliversExactDecimalArguments(String value) throws Exception {
        mvc().perform(post("/api/harness/numeric/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(restCommand("exact", value)))
                .andExpect(status().isOk());
        assertRecorded(0, value);
    }

    @Test
    void restRejectsChangedArgumentsThatWouldRoundToTheSameDouble() throws Exception {
        MockMvc mvc = mvc();
        for (int attempt = 0; attempt < 2; attempt++) {
            var result = mvc.perform(post("/api/harness/numeric/commands")
                    .header(HarnessController.TOKEN_HEADER, TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(restCommand("same", attempt == 0
                            ? "5.0000000000000001" : "5.0000000000000002")));
            if (attempt == 0) {
                result.andExpect(status().isOk());
            } else {
                result.andExpect(status().isBadRequest());
            }
        }
        assertThat(plugin.commands).hasSize(1);
    }

    @Test
    void restRetainsReplayForEquivalentDecimalSpellings() throws Exception {
        MockMvc mvc = mvc();
        for (String value : List.of("1.50", "1.5e0")) {
            mvc.perform(post("/api/harness/numeric/commands")
                            .header(HarnessController.TOKEN_HEADER, TOKEN)
                            .contentType(MediaType.APPLICATION_JSON).content(restCommand("scale", value)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.replayed").value(value.equals("1.5e0")));
        }
        assertThat(plugin.commands).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9007199254740993.25", "1.234567890123456789", "1e-400", "1e400"})
    void mcpDeliversExactDecimalArguments(String value) throws Exception {
        List<JsonNode> responses = stdio(call("\"exact\"", value));
        assertThat(responses.get(0).path("result").path("isError").asBoolean()).isFalse();
        assertRecorded(0, value);
    }

    @Test
    void mcpDoesNotConflateDistinctArgumentsOrTheirRetries() throws Exception {
        String first = call("\"same\"", "5.0000000000000001");
        String second = call("\"same\"", "5.0000000000000002");
        List<JsonNode> responses = stdio(first, second, first, second);
        assertThat(plugin.commands).hasSize(2);
        assertThat(responses).extracting(node -> node.path("result")
                        .path("structuredContent").path("replayed").asBoolean())
                .containsExactly(false, false, true, true);
        assertRecorded(0, "5.0000000000000001");
        assertRecorded(1, "5.0000000000000002");
    }

    @Test
    void mcpEchoesExactNumericIdsAndKeepsTheirExecutionsDistinct() throws Exception {
        String first = "9007199254740992.1";
        String second = "9007199254740992.2";
        List<JsonNode> responses = stdio(call(first, "1.5"), call(second, "1.5"));
        assertThat(plugin.commands).hasSize(2);
        assertThat(responses.get(0).path("id").decimalValue()).isEqualByComparingTo(first);
        assertThat(responses.get(1).path("id").decimalValue()).isEqualByComparingTo(second);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "1.0", "1.0e20"})
    void mcpRetainsExistingIdentityAndReplayForOrdinaryDecimalSpellings(String value) throws Exception {
        McpProtocolHandler direct = new McpProtocolHandler(gateway, properties, mapper);
        JsonNode request = mapper.readTree(call("\"ordinary\"", value));
        String legacyMaterial = request.path("id").toString() + '\u0000' + "numeric__record"
                + '\u0000' + mapper.writeValueAsString(request.path("params").path("arguments"));
        String legacyId = "mcp-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(legacyMaterial.getBytes(StandardCharsets.UTF_8)));
        JsonNode original = direct.handle(request);
        assertThat(original.path("result").path("structuredContent").path("requestId").asText())
                .isEqualTo(legacyId);
        BigDecimal number = new BigDecimal(value);
        List<JsonNode> responses = stdio(call("\"ordinary\"",
                        number.setScale(Math.max(1, number.scale()) + 1).toPlainString()),
                call("\"ordinary\"", number.toPlainString() + "e0"));
        assertThat(plugin.commands).hasSize(1);
        for (JsonNode response : responses) {
            assertThat(response.path("result").path("isError").asBoolean()).isFalse();
            assertThat(response.path("result").path("structuredContent").path("requestId"))
                    .isEqualTo(original.path("result").path("structuredContent").path("requestId"));
        }
    }

    @Test
    void decimalJsonTokensDoNotBecomeIntegerArgumentsAfterNormalization() throws Exception {
        String request = restCommand("fractional-token", "1.0")
                .replace("\"record\"", "\"integer\"");
        mvc().perform(post("/api/harness/numeric/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest());
        assertThat(plugin.commands).isEmpty();
    }

    private void assertRecorded(int index, String value) {
        assertThat(plugin.commands).hasSizeGreaterThan(index);
        Object recorded = plugin.commands.get(index).arguments().get("value");
        assertThat(new BigDecimal(recorded.toString())).isEqualByComparingTo(value);
    }

    private MockMvc mvc() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new MappingJackson2HttpMessageConverter(mapper));
        new HarnessJsonConfig().harnessCommandJson(mapper).extendMessageConverters(converters);
        return standaloneSetup(new HarnessController(gateway))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(converters.toArray(HttpMessageConverter<?>[]::new)).build();
    }

    private List<JsonNode> stdio(String... requests) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        McpStdioServer server = new McpStdioServer(
                new McpProtocolHandler(gateway, properties, mapper), mapper);
        try (PrintStream diagnostics = new PrintStream(new ByteArrayOutputStream())) {
            server.serve(new ByteArrayInputStream((String.join("\n", requests) + "\n")
                    .getBytes(StandardCharsets.UTF_8)), output, diagnostics);
        }
        ObjectMapper exact = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        List<JsonNode> responses = new ArrayList<>();
        for (String line : output.toString(StandardCharsets.UTF_8).lines().toList()) {
            responses.add(exact.readTree(line));
        }
        return responses;
    }

    private static String restCommand(String id, String value) {
        return "{\"requestId\":\"" + id + "\",\"action\":\"record\",\"arguments\":{\"value\":" + value + "}}";
    }

    private static String call(String id, String value) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{"
                + "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\""
                + McpProtocolHandler.CURRENT_PROTOCOL_VERSION
                + "\",\"io.modelcontextprotocol/clientCapabilities\":{}},"
                + "\"name\":\"numeric__record\",\"arguments\":{\"value\":" + value + "}}}";
    }

    private static HarnessProperties properties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowMutations(true);
        return properties;
    }

    private static final class RecordingPlugin implements HarnessPlugin {
        private final List<HarnessCommand> commands = new ArrayList<>();

        @Override public HarnessPluginDescriptor descriptor() {
            return new HarnessPluginDescriptor("numeric", "Numeric fixture", "Exact numeric commands", "1",
                    List.of(new HarnessActionSpec("record", "Record value", HarnessRisk.MUTATE,
                                    List.of(new HarnessArgumentSpec("value", HarnessValueType.NUMBER, "Value", true))),
                            new HarnessActionSpec("integer", "Record integer", HarnessRisk.MUTATE,
                                    List.of(new HarnessArgumentSpec("value", HarnessValueType.INTEGER, "Value", true)))));
        }

        @Override public HarnessSnapshot observe() {
            return new HarnessSnapshot("numeric", null, List.of(), List.of(), Map.of());
        }

        @Override public HarnessExecution execute(HarnessCommand command) {
            commands.add(command);
            return HarnessExecution.success("Recorded");
        }
    }
}
