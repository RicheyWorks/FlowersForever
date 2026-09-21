package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessManifest;
import com.flowerfarm.automation.HarnessProperties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.main.headless=true",
        "spring.datasource.url=jdbc:h2:mem:doctor-readiness;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "flowerfarm.inventory.seed-csv=target/no-doctor-readiness-seed.csv",
        "flowerfarm.irrigation.live-weather=false",
        "flowerfarm.harness.enabled=true",
        "flowerfarm.harness.token=doctor-readiness-token-123456",
        "flowerfarm.harness.allow-sensitive-read=true",
        "flowerfarm.harness.allow-mutations=true"
})
class McpDoctorReadinessTest {

    @Autowired HarnessGateway gateway;
    @Autowired HarnessProperties properties;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired ObjectMapper mapper;
    @TempDir Path directory;

    @Test
    void reportsReadyOnlyWhenTheConfiguredDirectoryAndRealFarmToolsAreAvailable() {
        Map<String, Object> report = doctor(gateway, environment(directory)).inspect(true);

        assertThat(report).containsEntry("ready", true).containsEntry("status", "READY")
                .containsEntry("dataDirectoryReady", true).containsEntry("databaseReady", true)
                .containsEntry("enabledFarmDataToolCount", 38);
        assertThat(report.get("missingFarmDataTools")).isEqualTo(java.util.List.of());
    }

    @Test
    void missingDirectoryCannotProduceReadyEvenWhenDatabaseAndToolsWork() {
        Map<String, Object> report = doctor(gateway, environment(directory.resolve("missing"))).inspect(true);

        assertThat(report).containsEntry("ready", false).containsEntry("status", "ACTION_REQUIRED")
                .containsEntry("dataDirectoryReady", false).containsEntry("databaseReady", true);
        assertThat(report.get("nextSteps").toString()).contains("FLOWERFARM_DATA_DIR")
                .doesNotContain("Register this launcher");
    }

    @Test
    void runnerReturnsActionRequiredForAFileAtTheDirectoryPath() throws Exception {
        Path file = Files.writeString(directory.resolve("not-a-directory"), "fixture");
        MockEnvironment environment = environment(file);
        McpDoctorRunner runner = new McpDoctorRunner(doctor(gateway, environment), mapper, environment);
        PrintStream previous = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            runner.run(new DefaultApplicationArguments(new String[0]));
        } finally {
            System.setOut(previous);
        }

        assertThat(runner.getExitCode()).isEqualTo(2);
        var report = mapper.readTree(output.toString(StandardCharsets.UTF_8));
        assertThat(report.path("status").asText()).isEqualTo("ACTION_REQUIRED");
        assertThat(report.path("dataDirectoryReady").asBoolean()).isFalse();
        assertThat(report.toString()).doesNotContain(properties.getToken());
    }

    @Test
    void missingProductionOrWorkerWiringCannotHideBehindCoreMigrationTools() {
        HarnessManifest actual = gateway.manifest(properties.getToken());
        HarnessGateway incomplete = mock(HarnessGateway.class);
        for (String omitted : java.util.List.of("farm-data__commit-plantings",
                "farm-data__get-season-settings", "farm-data__count-inventory",
                "farm-data__get-operation-receipt", "farm-data__complete-order-handoff")) {
            HarnessManifest manifest = new HarnessManifest(actual.protocolVersion(),
                    actual.replayCacheLimit(), actual.replayCacheByteLimit(), actual.strictArguments(),
                    actual.physicalCommandMaxTtlSeconds(), actual.policy(), actual.plugins(),
                    actual.tools().stream().filter(tool -> !tool.name().equals(omitted)).toList());
            when(incomplete.manifest(properties.getToken())).thenReturn(manifest);

            Map<String, Object> report = doctor(incomplete, environment(directory)).inspect(true);

            assertThat(report).as(omitted).containsEntry("ready", false);
            assertThat(report.get("missingFarmDataTools")).isEqualTo(java.util.List.of(omitted));
            assertThat(report.get("nextSteps").toString()).contains("Repair the installation");
        }
    }

    private MockEnvironment environment(Path path) {
        return new MockEnvironment().withProperty("flowerfarm.data-dir", path.toString())
                .withProperty(McpDoctorBootstrap.TOKEN_VALID_PROPERTY, "true");
    }

    private McpDoctor doctor(HarnessGateway suppliedGateway, MockEnvironment environment) {
        return new McpDoctor(suppliedGateway, properties, dataSource, flyway, environment);
    }
}
