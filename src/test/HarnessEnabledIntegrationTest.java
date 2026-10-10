package com.flowerfarm;

import com.flowerfarm.testsupport.RestCsrfSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.config.HarnessJsonConfig;
import com.flowerfarm.controller.HarnessController;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureTestRestTemplate
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.headless=true",
                "spring.datasource.url=jdbc:h2:mem:harness-enabled;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=validate",
                "flowerfarm.inventory.seed-csv=target/no-harness-enabled-seed.csv",
                "flowerfarm.irrigation.live-weather=false",
                "flowerfarm.harness.enabled=true",
                "flowerfarm.harness.token=integration-harness-token-123456"
        })
@DisplayName("Enabled AI harness integration")
class HarnessEnabledIntegrationTest {

    private static final String TOKEN = "integration-harness-token-123456";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    RequestMappingHandlerAdapter handlerAdapter;

    @Test
    @DisplayName("publishes typed discovery only to the independent harness token")
    void publishesTokenProtectedTypedDiscovery() {
        ResponseEntity<JsonNode> unauthorized = rest.getForEntity(url("/api/harness"),
                JsonNode.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = RestCsrfSupport.headers(rest, url("/api/auth/csrf"));
        headers.set(HarnessController.TOKEN_HEADER, TOKEN);
        ResponseEntity<JsonNode> discovery = rest.exchange(url("/api/harness"),
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

        assertThat(discovery.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode desktop = find(discovery.getBody(), "id", "swing-desktop");
        assertThat(desktop.get("id").asText()).isEqualTo("swing-desktop");
        assertThat(desktop.path("kind").asText()).isEqualTo("APPLICATION");
        assertThat(desktop.path("capabilities").toString())
                .contains("OBSERVATION", "COMMANDS", "VISUAL_PERCEPTION");
        assertThat(desktop.findValuesAsText("type"))
                .contains("string", "integer");
        assertThat(desktop.path("actions")).hasSize(30);
        assertThat(desktop.path("actions").findValuesAsText("name"))
                .contains("scroll", "set-divider", "set-toggle", "choose-option-index",
                        "clear-selection", "read-screen", "read-layout", "read-item", "edit-text");
        JsonNode farmData = find(discovery.getBody(), "id", "farm-data");
        assertThat(farmData.path("kind").asText()).isEqualTo("WORKFLOW");
        assertThat(farmData.findValuesAsText("name"))
                .contains("get-data-catalog", "suggest-field-mapping",
                        "preview-mapped-records", "import-mapped-records",
                        "list-records", "list-import-receipts", "get-production-costs",
                        "create-record-export", "get-record-export",
                        "read-record-export",
                        "upsert-inventory", "upsert-season-settings",
                        "upsert-customers", "upsert-growing-templates",
                        "upsert-planting-plans", "upsert-floral-recipes",
                        "import-harvest-history", "log-harvests",
                        "import-order-history", "create-orders", "record-knowledge",
                        "complete-tasks");

        ResponseEntity<JsonNode> manifest = rest.exchange(
                url("/api/harness/manifest"), HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(manifest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(manifest.getBody().path("protocolVersion").asText()).isEqualTo("1.0");
        assertThat(manifest.getBody().path("policy").path("DRAFT").asBoolean()).isFalse();
        assertThat(manifest.getBody().path("policy").path("SENSITIVE_READ").asBoolean())
                .isFalse();
        assertThat(manifest.getBody().path("policy").path("PHYSICAL").asBoolean())
                .isFalse();
        assertThat(manifest.getBody().path("physicalCommandMaxTtlSeconds").asLong())
                .isEqualTo(30);
        assertThat(manifest.getBody().path("replayCacheByteLimit").asLong())
                .isEqualTo(8L * 1024 * 1024);
        JsonNode tools = manifest.getBody().path("tools");
        assertThat(tools.size()).isEqualTo(68);
        JsonNode actionStatus = find(tools, "name", "swing-desktop__action-status");
        assertThat(actionStatus.path("allowed").asBoolean()).isTrue();
        assertThat(actionStatus.path("inputSchema").path("properties")
                .path("request-id").path("type").asText()).isEqualTo("string");
        JsonNode navigate = find(tools, "name", "swing-desktop__navigate");
        assertThat(navigate
                .path("annotations").path("idempotentHint").asBoolean()).isTrue();
        assertThat(find(tools, "name", "swing-desktop__replace-text")
                .path("allowed").asBoolean()).isFalse();
        assertThat(find(tools, "name", "swing-desktop__read-control")
                .path("allowed").asBoolean()).isFalse();
        assertThat(find(tools, "name", "swing-desktop__read-screen")
                .path("allowed").asBoolean()).isFalse();
        assertThat(find(tools, "name", "swing-desktop__read-item")
                .path("allowed").asBoolean()).isFalse();
        assertThat(find(tools, "name", "swing-desktop__read-layout")
                .path("allowed").asBoolean()).isTrue();
        assertThat(find(tools, "name", "swing-desktop__set-toggle")
                .path("allowed").asBoolean()).isFalse();
        assertThat(find(tools, "name", "swing-desktop__edit-text")
                .path("allowed").asBoolean()).isFalse();
        assertThat(find(tools, "name", "swing-desktop__choose-file")
                .path("allowed").asBoolean()).isFalse();
        assertThat(find(tools, "name", "swing-desktop__cancel-file-dialog")
                .path("allowed").asBoolean()).isTrue();
        JsonNode inventory = find(tools, "name", "farm-data__upsert-inventory");
        assertThat(inventory.path("allowed").asBoolean()).isFalse();
        assertThat(inventory.path("inputSchema").path("properties").path("records")
                .path("items").path("properties").path("quantity").path("type").asText())
                .isEqualTo("number");

        ResponseEntity<JsonNode> observation = rest.exchange(
                url("/api/harness/swing-desktop/observe"), HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(observation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(observation.getBody().path("metadata").path("ready").asBoolean())
                .isFalse();

        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> catalog = rest.exchange(
                url("/api/harness/farm-data/commands"), HttpMethod.POST,
                new HttpEntity<>("""
                        {"requestId":"integration-catalog",
                         "action":"get-data-catalog","arguments":{}}
                        """, headers), JsonNode.class);
        assertThat(catalog.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(catalog.getBody().path("output").path("recordTypes"))
                .hasSize(10);
        assertThat(catalog.getBody().path("output").path("mappingWorkflow")
                .path("importAction").asText()).isEqualTo("import-mapped-records");
        assertThat(catalog.getBody().path("output").path("recordTypes")
                .path("orders").path("operationalAction").asText())
                .isEqualTo("create-orders");
    }

    @Test
    void rejectsAmbiguousCommandBodiesThroughTheConfiguredRestTransport() {
        HttpHeaders headers = RestCsrfSupport.headers(rest, url("/api/auth/csrf"));
        headers.set(HarnessController.TOKEN_HEADER, TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (String body : List.of(
                "{\"requestId\":\"one\",\"requestId\":\"two\",\"action\":\"get-data-catalog\"}",
                "{\"requestId\":\"trailing\",\"action\":\"get-data-catalog\"} {}",
                "{\"requestId\":123,\"action\":\"get-data-catalog\"}",
                "{\"requestId\":\"unknown\",\"action\":\"get-data-catalog\",\"argumentz\":{}}",
                "{\"requestId\":\"expiry\",\"action\":\"get-data-catalog\",\"expiresAt\":42}")) {
            ResponseEntity<JsonNode> response = rest.exchange(
                    url("/api/harness/farm-data/commands"), HttpMethod.POST,
                    new HttpEntity<>(body, headers), JsonNode.class);
            assertThat(response.getStatusCode()).as("body: %s", body)
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().path("code").asText()).isEqualTo("MALFORMED_BODY");
        }
    }

    @Test
    void registeredCommandMappersRetainTheAdvertisedAttachmentAllowance() {
        var commandMappers = handlerAdapter.getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .flatMap(converter -> converter.getObjectMappersForType(HarnessCommand.class)
                        .values().stream()).toList();
        assertThat(commandMappers).isNotEmpty().allSatisfy(mapper ->
                assertThat(mapper.getFactory().streamReadConstraints().getMaxStringLength())
                        .isGreaterThanOrEqualTo(HarnessJsonConfig.MAX_ATTACHMENT_BASE64_CHARACTERS));
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode candidate : array) {
            if (value.equals(candidate.path(field).asText())) {
                return candidate;
            }
        }
        throw new AssertionError("No JSON object with " + field + "=" + value);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
