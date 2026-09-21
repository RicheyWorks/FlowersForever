package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessAuthenticationException;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessManifest;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessToolDefinition;
import com.flowerfarm.automation.farm.FarmDataImportConflictException;
import com.flowerfarm.controller.ApiExceptionHandler;
import com.flowerfarm.controller.HarnessController;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HarnessTransportErrorSafetyTest {

    private static final String TOKEN = "mcp-errors-contract-token-123456";
    private static final String DETAIL = "Adapter password=example-password "
            + "Authorization: Bearer example-bearer\r\n"
            + "https://operator:example-url-password@localhost/failure " + "detail ".repeat(100);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void redactsAndBoundsMcpExecutionFailuresInBothResultRepresentations() throws Exception {
        HarnessGateway gateway = mock(HarnessGateway.class);
        when(gateway.manifest(TOKEN)).thenReturn(manifest());
        when(gateway.execute(eq(TOKEN), eq("example"), any()))
                .thenThrow(new IllegalStateException(DETAIL));
        assertSafeMcpError(handler(gateway).handle(call()));
    }

    @Test
    void redactsAndBoundsMcpManifestFailures() throws Exception {
        HarnessGateway gateway = mock(HarnessGateway.class);
        when(gateway.manifest(TOKEN)).thenThrow(new IllegalStateException(DETAIL));
        assertSafeMcpError(handler(gateway).handle(call()));
    }

    @Test
    void preservesRestStatusWhileRedactingEveryHarnessExceptionCategory() throws Exception {
        for (ErrorCase errorCase : List.of(
                new ErrorCase(new IllegalArgumentException(DETAIL), 400),
                new ErrorCase(new IllegalStateException(DETAIL), 400),
                new ErrorCase(new HarnessAuthenticationException(DETAIL), 401),
                new ErrorCase(new HarnessPolicyException(DETAIL), 403),
                new ErrorCase(new FarmDataImportConflictException(DETAIL), 409))) {
            HarnessGateway gateway = mock(HarnessGateway.class);
            when(gateway.discover(TOKEN)).thenThrow(errorCase.exception());
            var mvc = standaloneSetup(new HarnessController(gateway))
                    .setControllerAdvice(new ApiExceptionHandler()).build();
            var response = mvc.perform(get("/api/harness")
                    .header(HarnessController.TOKEN_HEADER, TOKEN)).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(errorCase.status());
            JsonNode body = mapper.readTree(response.getContentAsString());
            assertSafeMessage(body.path("message").asText());
            assertThat(body.path("status").asInt()).isEqualTo(errorCase.status());
        }
    }

    @Test
    void blankMcpAndRestErrorsRetainActionableFallbacks() throws Exception {
        HarnessGateway gateway = mock(HarnessGateway.class);
        when(gateway.manifest(TOKEN)).thenThrow(new IllegalStateException(" \r\n"));
        JsonNode response = handler(gateway).handle(call());
        assertThat(response.path("result").path("structuredContent").path("error").asText())
                .isEqualTo("Tool execution failed");

        when(gateway.discover(TOKEN)).thenThrow(new IllegalStateException(" \r\n"));
        var mvc = standaloneSetup(new HarnessController(gateway))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        var rest = mvc.perform(get("/api/harness").header(HarnessController.TOKEN_HEADER, TOKEN))
                .andReturn().getResponse();
        assertThat(mapper.readTree(rest.getContentAsString()).path("message").asText())
                .isEqualTo("Harness request failed");
    }

    private void assertSafeMcpError(JsonNode response) throws Exception {
        assertThat(response.path("result").path("isError").asBoolean()).isTrue();
        JsonNode structured = response.path("result").path("structuredContent");
        assertSafeMessage(structured.path("error").asText());
        JsonNode textCopy = mapper.readTree(response.path("result").path("content")
                .get(0).path("text").asText());
        assertThat(textCopy).isEqualTo(structured);
    }

    private static void assertSafeMessage(String message) {
        assertThat(message).contains("password=[REDACTED]", "Bearer [REDACTED]")
                .doesNotContain("example-password", "example-bearer", "example-url-password",
                        "\r", "\n")
                .hasSizeLessThanOrEqualTo(240);
    }

    private McpProtocolHandler handler(HarnessGateway gateway) {
        HarnessProperties properties = new HarnessProperties();
        properties.setToken(TOKEN);
        return new McpProtocolHandler(gateway, properties, mapper);
    }

    private JsonNode call() throws Exception {
        return mapper.readTree("""
                {"jsonrpc":"2.0","id":"error","method":"tools/call","params":{
                 "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",
                          "io.modelcontextprotocol/clientCapabilities":{}},
                 "name":"example__read","arguments":{}}}
                """);
    }

    private static HarnessManifest manifest() {
        HarnessToolDefinition tool = new HarnessToolDefinition("example__read", "example",
                "read", "Read example", HarnessRisk.READ, true, Map.of("type", "object"));
        return new HarnessManifest("1.0", 256, true, Map.of(HarnessRisk.READ, true),
                List.of(), List.of(tool));
    }

    private record ErrorCase(RuntimeException exception, int status) {
    }
}
