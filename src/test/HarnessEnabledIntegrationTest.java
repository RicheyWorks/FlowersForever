package com.flowerfarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowerfarm.controller.HarnessController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("publishes typed discovery only to the independent harness token")
    void publishesTokenProtectedTypedDiscovery() {
        ResponseEntity<JsonNode> unauthorized = rest.getForEntity(url("/api/harness"),
                JsonNode.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders headers = new HttpHeaders();
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
        assertThat(tools.size()).isEqualTo(54);
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
        assertThat(find(tools, "name", "swing-desktop__choose-file")
                .path("allowed").asBoolean()).isFalse();
        assertThat(find(tools, "name", "swing-desktop__cancel-file-dialog")
                .path("allowed").asBoolean()).isTrue();
        JsonNode inventory = find(tools, "name", "farm-data__upsert-inventory");
        assertThat(inventory.path("allowed").asBoolean()).isFalse();
        assertThat(inventory.path("inputSchema").path("properties").path("records")
                .path("items").path("properties").path("quantity").path("type").asText())
                .isEqualTo("integer");

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
