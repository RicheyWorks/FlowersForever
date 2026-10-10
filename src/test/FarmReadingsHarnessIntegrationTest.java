package com.flowerfarm.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.testsupport.RestCsrfSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.main.headless=true", "spring.datasource.url=jdbc:h2:mem:farm-readings-http;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "flowerfarm.demo.seed=false",
        "flowerfarm.inventory.seed-sample=false", "flowerfarm.irrigation.live-weather=false",
        "flowerfarm.harness.enabled=true", "flowerfarm.readings.enabled=true",
        "flowerfarm.harness.token=readings-integration-token-123456",
        "flowerfarm.harness.allow-mutations=true", "flowerfarm.harness.allow-sensitive-read=true"
})
class FarmReadingsHarnessIntegrationTest {
    private static final String TOKEN = "readings-integration-token-123456";
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired HarnessGateway gateway;
    @Autowired HarnessProperties policy;
    @Autowired FarmReadingService service;

    private String url(String route) { return "http://localhost:" + port + route; }

    private HttpHeaders headers() {
        HttpHeaders headers = RestCsrfSupport.headers(rest, url("/api/auth/csrf"));
        headers.set(HarnessController.TOKEN_HEADER, TOKEN);
        return headers;
    }

    @Test void protectedHttpStoresExactReadingsAndReadsThemByCursor() {
        Map<String, Object> reading = Map.of("readingKey", "sample-1", "deviceId", "house-2",
                "metric", "air.temperature", "unit", "C", "value", 21.125,
                "observedAt", "2026-10-01T12:00:00Z", "rawFrame", "{\"t\":21.125}");
        String source = "http-" + UUID.randomUUID();
        String route = "/api/harness/farm-readings/commands";
        var command = new HarnessCommand(UUID.randomUUID().toString(), "record-readings",
                Map.of("source-id", source, "readings", List.of(reading)));
        var response = rest.postForEntity(url(route), new HttpEntity<>(command, headers()), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("output").path("receipt").path("inserted").asInt()).isEqualTo(1);
        assertThat(response.getBody().path("snapshot").path("metadata").toString()).doesNotContain("house-2", "21.125");
        var retry = new HarnessCommand(UUID.randomUUID().toString(), "record-readings", command.arguments());
        var retried = rest.postForEntity(url(route), new HttpEntity<>(retry, headers()), JsonNode.class);
        assertThat(retried.getBody().path("output").path("receipt").path("replayed").asInt()).isEqualTo(1);
        var read = new HarnessCommand(UUID.randomUUID().toString(), "list-readings", Map.of("source-id", source));
        var history = rest.postForEntity(url(route), new HttpEntity<>(read, headers()), JsonNode.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode record = history.getBody().path("output").path("page").path("readings").get(0);
        assertThat(record.path("unit").asText()).isEqualTo("C");
        assertThat(record.path("rawFrame").asText()).isEqualTo("{\"t\":21.125}");
        assertThat(service.history(source, 0, 1).readings()).hasSize(1);
        var denied = rest.postForEntity(url(route), new HttpEntity<>(command,
                RestCsrfSupport.headers(rest, url("/api/auth/csrf"))), JsonNode.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void reportsContractAndHonorsIndependentReadAndMutationPermissions() {
        var contract = gateway.execute(TOKEN, FarmReadingsHarnessPlugin.ID,
                new HarnessCommand(UUID.randomUUID().toString(), "get-reading-contract", Map.of()));
        assertThat(contract.output()).containsEntry("version", "1.0").containsEntry("equipmentControl", false);
        assertThat(gateway.manifest(TOKEN).plugins()).extracting(descriptor -> descriptor.id())
                .contains(FarmReadingsHarnessPlugin.ID);
        boolean read = policy.isAllowSensitiveRead();
        boolean mutate = policy.isAllowMutations();
        try {
            policy.setAllowSensitiveRead(false);
            assertThatThrownBy(() -> gateway.execute(TOKEN, FarmReadingsHarnessPlugin.ID,
                    new HarnessCommand(UUID.randomUUID().toString(), "list-readings", Map.of("source-id", "source"))))
                    .isInstanceOf(HarnessPolicyException.class);
            policy.setAllowMutations(false);
            assertThatThrownBy(() -> gateway.execute(TOKEN, FarmReadingsHarnessPlugin.ID,
                    new HarnessCommand(UUID.randomUUID().toString(), "record-readings",
                            Map.of("source-id", "source", "readings", List.of()))))
                    .isInstanceOf(HarnessPolicyException.class);
        } finally {
            policy.setAllowSensitiveRead(read);
            policy.setAllowMutations(mutate);
        }
        var bad = new HarnessCommand(UUID.randomUUID().toString(), "list-readings",
                Map.of("source-id", "source", "limit", Long.MAX_VALUE));
        assertThatThrownBy(() -> gateway.execute(TOKEN, FarmReadingsHarnessPlugin.ID, bad))
                .isInstanceOf(IllegalArgumentException.class);
        var unknown = new HarnessCommand(UUID.randomUUID().toString(), "start-pump", Map.of());
        assertThatThrownBy(() -> gateway.execute(TOKEN, FarmReadingsHarnessPlugin.ID, unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void csvAdapterReallyPostsExactNumericTokensAndReplaysTheSameFile(@TempDir Path directory)
            throws Exception {
        Path csv = directory.resolve("logger.csv");
        Files.writeString(csv, "timestamp,temperature_c\n2026-10-01T12:00:00Z,21.123456789012\n",
                StandardCharsets.UTF_8);
        String source = "csv-" + UUID.randomUUID();
        String python = System.getProperty("os.name").startsWith("Windows") ? "python" : "python3";
        for (int attempt = 0; attempt < 2; attempt++) {
            Path log = directory.resolve("adapter-" + attempt + ".log");
            ProcessBuilder builder = new ProcessBuilder(python, "-B", "scripts/import-farm-readings.py",
                    csv.toString(), "--source-id", source, "--device-id", "test-sensor",
                    "--metric", "air.temperature", "--unit", "C", "--columns",
                    "docs/examples/greenhouse-columns.json", "--url", "http://127.0.0.1:" + port, "--send");
            builder.environment().put("FLOWERFARM_HARNESS_TOKEN", TOKEN);
            builder.redirectErrorStream(true).redirectOutput(log.toFile());
            Process process = builder.start();
            try {
                assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("CSV adapter finished").isTrue();
                assertThat(process.exitValue()).as(Files.readString(log)).isZero();
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
        }
        List<FarmReading> rows = service.history(source, 0, 100).readings();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).value()).isEqualByComparingTo("21.123456789012");
        assertThat(rows.get(0).rawFrame()).contains("temperature_c", "21.123456789012");
    }
}
