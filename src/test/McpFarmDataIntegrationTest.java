package com.flowerfarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.mcp.McpProtocolHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.headless=true",
                "spring.datasource.url=jdbc:h2:mem:mcp-farm-data;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=validate",
                "flowerfarm.inventory.seed-csv=target/no-mcp-farm-data-seed.csv",
                "flowerfarm.inventory.sample-enabled=true",
                "flowerfarm.irrigation.live-weather=false",
                "flowerfarm.harness.enabled=true",
                "flowerfarm.harness.token=mcp-farm-data-token-123456",
                "flowerfarm.harness.allow-sensitive-read=true",
                "flowerfarm.exports.directory=target/mcp-farm-data-exports"
        })
@ActiveProfiles("release-smoke")
@DisplayName("MCP to farm-data harness integration")
class McpFarmDataIntegrationTest {

    @Autowired
    McpProtocolHandler handler;

    @Autowired
    ObjectMapper mapper;

    @Test
    @DisplayName("MCP resources expose the configured farm plugin and typed gateway manifest")
    void readsFarmObservationAndManifestResources() throws Exception {
        JsonNode listed = handler.handle(mapper.readTree(request(
                "resources", "resources/list", "")));
        assertThat(listed.path("result").path("resources").findValuesAsText("uri"))
                .containsExactly("flowersforever://harness/manifest",
                        "flowersforever://harness/farm-data/observation");

        JsonNode observation = handler.handle(mapper.readTree(request(
                "observe", "resources/read",
                ",\"uri\":\"flowersforever://harness/farm-data/observation\"")));
        JsonNode snapshot = mapper.readTree(observation.path("result").path("contents")
                .get(0).path("text").asText());
        assertThat(snapshot.path("pluginId").asText()).isEqualTo("farm-data");
        assertThat(snapshot.path("metadata").path("ready").asBoolean()).isTrue();
        assertThat(snapshot.path("metadata").path("recordTypes")).hasSize(10);
        assertThat(observation.path("result").path("ttlMs").asLong()).isZero();

        JsonNode manifest = handler.handle(mapper.readTree(request(
                "manifest", "resources/read", ",\"uri\":\"flowersforever://harness/manifest\"")));
        JsonNode document = mapper.readTree(manifest.path("result").path("contents")
                .get(0).path("text").asText());
        assertThat(document.path("plugins").findValuesAsText("id")).containsExactly("farm-data");
        assertThat(document.path("policy").path("SENSITIVE_READ").asBoolean()).isTrue();
        assertThat(document.path("policy").path("MUTATE").asBoolean()).isFalse();
        assertThat(document.path("tools").findValuesAsText("name"))
                .contains("farm-data__get-data-catalog", "farm-data__upsert-inventory");
        assertThat(document.toString()).doesNotContain("mcp-farm-data-token-123456");
    }

    @Test
    @DisplayName("discovers and calls the farm data catalog without REST glue")
    void callsFarmDataCatalog() throws Exception {
        JsonNode listed = handler.handle(mapper.readTree(request(
                "list", "tools/list", "")));
        assertThat(listed.path("result").path("tools").findValuesAsText("name"))
                .contains("farm-data__get-data-catalog",
                        "farm-data__suggest-field-mapping",
                        "farm-data__preview-mapped-records",
                        "farm-data__get-production-costs",
                        "farm-data__preview-plantings",
                        "farm-data__estimate-recipe", "farm-data__get-availability",
                        "farm-data__list-handoffs",
                        "farm-data__create-record-export",
                        "farm-data__read-record-export")
                .doesNotContain("farm-data__upsert-inventory",
                        "farm-data__upsert-season-settings", "farm-data__commit-plantings",
                        "farm-data__create-handoff", "farm-data__update-task-status",
                        "farm-data__regenerate-tasks");

        JsonNode called = handler.handle(mapper.readTree(request(
                "catalog", "tools/call",
                ",\"name\":\"farm-data__get-data-catalog\",\"arguments\":{}")));
        assertThat(called.path("result").path("isError").asBoolean()).isFalse();
        assertThat(called.path("result").path("structuredContent")
                .path("output").path("recordTypes")).hasSize(10);

        JsonNode costs = handler.handle(mapper.readTree(request(
                "production-costs", "tools/call",
                ",\"name\":\"farm-data__get-production-costs\",\"arguments\":{"
                        + "\"season-year\":2026,\"labor-rate-per-hour\":27.5}")));
        assertThat(costs.path("result").path("isError").asBoolean()).isFalse();
        assertThat(costs.path("result").path("structuredContent")
                .path("output").path("report").path("seasonYear").asInt())
                .isEqualTo(2026);
        assertThat(costs.path("result").path("structuredContent")
                .path("output").path("report").path("laborRateSource").asText())
                .isEqualTo("REQUEST_OVERRIDE");
    }

    @Test
    @DisplayName("creates, recovers, and reads a durable export through MCP")
    void exportsFarmDataThroughMcp() throws Exception {
        String exportKey = "mcp-export-" + java.util.UUID.randomUUID();
        JsonNode created = handler.handle(mapper.readTree(request(
                "create-export", "tools/call",
                ",\"name\":\"farm-data__create-record-export\",\"arguments\":{"
                        + "\"export-key\":\"" + exportKey + "\","
                        + "\"record-types\":[\"inventory\"],\"chunk-records\":2}")));
        JsonNode export = created.path("result").path("structuredContent")
                .path("output").path("export");

        assertThat(created.path("result").path("isError").asBoolean()).isFalse();
        assertThat(export.path("recordCounts").path("inventory").asLong())
                .isGreaterThanOrEqualTo(4);
        assertThat(export.path("partCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(export.path("snapshotSha256").asText()).hasSize(64);

        String exportId = export.path("exportId").asText();
        JsonNode read = handler.handle(mapper.readTree(request(
                "read-export", "tools/call",
                ",\"name\":\"farm-data__read-record-export\",\"arguments\":{"
                        + "\"export-id\":\"" + exportId + "\",\"part\":0}")));
        JsonNode chunk = read.path("result").path("structuredContent")
                .path("output").path("chunk");
        assertThat(read.path("result").path("isError").asBoolean()).isFalse();
        assertThat(chunk.path("records")).hasSize(2);
        assertThat(chunk.path("partSha256").asText()).hasSize(64);
        assertThat(chunk.path("hasMore").asBoolean()).isTrue();

        JsonNode recovered = handler.handle(mapper.readTree(request(
                "get-export", "tools/call",
                ",\"name\":\"farm-data__get-record-export\",\"arguments\":{"
                        + "\"export-key\":\"" + exportKey + "\"}")));
        assertThat(recovered.path("result").path("structuredContent")
                .path("output").path("export").path("exportId").asText())
                .isEqualTo(exportId);
    }

    private static String request(String id, String method, String extraParams) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"method\":\""
                + method + "\",\"params\":{\"_meta\":{"
                + "\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                + "\"io.modelcontextprotocol/clientInfo\":{"
                + "\"name\":\"integration-test\",\"version\":\"1\"},"
                + "\"io.modelcontextprotocol/clientCapabilities\":{}}"
                + extraParams + "}}";
    }
}
