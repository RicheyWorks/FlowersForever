package com.flowerfarm.automation.mcp;

import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessManifest;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessToolDefinition;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Inspects the real MCP application graph and reports operator-actionable readiness. */
@Component
@Profile("mcp-doctor")
public class McpDoctor {

    private static final String FARM_PREFIX = "farm-data__";
    private static final Set<String> CONNECTION_TOOLS = Set.of(
            "farm-data__get-data-catalog",
            "farm-data__suggest-field-mapping",
            "farm-data__preview-mapped-records");
    private static final Set<String> RECONCILIATION_TOOLS = Set.of(
            "farm-data__list-records",
            "farm-data__list-import-receipts",
            "farm-data__create-record-export",
            "farm-data__get-record-export",
            "farm-data__read-record-export",
            "farm-data__read-attachment-content",
            "farm-data__lookup-harvest-lot",
            "farm-data__get-season-settings",
            "farm-data__get-production-costs",
            "farm-data__preview-plantings",
            "farm-data__estimate-recipe",
            "farm-data__get-availability",
            "farm-data__list-handoffs",
            "farm-data__list-order-handoffs",
            "farm-data__get-operation-receipt");
    private static final Set<String> WRITE_TOOLS = Set.of(
            "farm-data__import-mapped-records",
            "farm-data__upsert-inventory",
            "farm-data__upsert-customers",
            "farm-data__upsert-growing-templates",
            "farm-data__upsert-planting-plans",
            "farm-data__upsert-floral-recipes",
            "farm-data__import-harvest-history",
            "farm-data__log-harvests",
            "farm-data__import-order-history",
            "farm-data__create-orders",
            "farm-data__record-knowledge",
            "farm-data__complete-tasks",
            "farm-data__upload-attachment",
            "farm-data__upsert-season-settings",
            "farm-data__commit-plantings",
            "farm-data__regenerate-tasks",
            "farm-data__update-task-status",
            "farm-data__create-handoff",
            "farm-data__count-inventory",
            "farm-data__complete-order-handoff");

    private final HarnessGateway gateway;
    private final HarnessProperties properties;
    private final DataSource dataSource;
    private final Flyway flyway;
    private final Environment environment;

    public McpDoctor(
            HarnessGateway gateway,
            HarnessProperties properties,
            DataSource dataSource,
            Flyway flyway,
            Environment environment) {
        this.gateway = gateway;
        this.properties = properties;
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.environment = environment;
    }

    /** Returns a stable JSON-compatible diagnostic report without exposing the token. */
    public Map<String, Object> inspect(boolean tokenConfigured) {
        Map<String, Object> report = new LinkedHashMap<>();
        Path dataDirectory = Path.of(environment.getRequiredProperty(
                "flowerfarm.data-dir")).toAbsolutePath().normalize();
        DatabaseStatus database = databaseStatus();
        HarnessManifest manifest = gateway.manifest(properties.getToken());
        Set<String> enabledTools = manifest.tools().stream()
                .filter(HarnessToolDefinition::allowed)
                .map(HarnessToolDefinition::name)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        Set<String> farmTools = enabledTools.stream()
                .filter(name -> name.startsWith(FARM_PREFIX))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        boolean dataDirectoryReady = Files.isDirectory(dataDirectory)
                && Files.isWritable(dataDirectory);

        boolean connectionReady = database.ready()
                && enabledTools.containsAll(CONNECTION_TOOLS);
        boolean reconciliationReady = connectionReady
                && enabledTools.containsAll(RECONCILIATION_TOOLS);
        boolean writesReady = connectionReady && enabledTools.containsAll(WRITE_TOOLS);
        boolean ready = tokenConfigured && dataDirectoryReady && reconciliationReady && writesReady;

        Set<String> expectedTools = new TreeSet<>(CONNECTION_TOOLS);
        expectedTools.addAll(RECONCILIATION_TOOLS);
        expectedTools.addAll(WRITE_TOOLS);
        expectedTools.removeAll(enabledTools);

        report.put("ready", ready);
        report.put("status", ready ? "READY" : "ACTION_REQUIRED");
        report.put("server", McpProtocolHandler.SERVER_NAME);
        report.put("serverVersion", McpProtocolHandler.SERVER_VERSION);
        report.put("protocolVersion", McpProtocolHandler.CURRENT_PROTOCOL_VERSION);
        report.put("dataDirectory", dataDirectory.toString());
        report.put("dataDirectoryReady", dataDirectoryReady);
        report.put("databaseReady", database.ready());
        report.put("databaseProduct", database.product());
        report.put("schemaVersion", schemaVersion());
        report.put("tokenConfigured", tokenConfigured);
        report.put("sensitiveReadEnabled", properties.isAllowSensitiveRead());
        report.put("mutationsEnabled", properties.isAllowMutations());
        report.put("installedPlugins", manifest.plugins().stream()
                .map(plugin -> plugin.id()).sorted().toList());
        report.put("publishedToolCount", manifest.tools().size());
        report.put("enabledToolCount", enabledTools.size());
        report.put("enabledFarmDataToolCount", farmTools.size());
        report.put("capabilities", Map.of(
                "connectAndMap", connectionReady,
                "readBackAndResume", reconciliationReady,
                "migrateAndOperate", writesReady));
        report.put("missingFarmDataTools", List.copyOf(expectedTools));
        report.put("nextSteps", nextSteps(tokenConfigured, database, dataDirectoryReady,
                reconciliationReady, writesReady, expectedTools));
        return java.util.Collections.unmodifiableMap(report);
    }

    private DatabaseStatus databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return new DatabaseStatus(connection.isValid(2),
                    connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            return new DatabaseStatus(false, "unavailable");
        }
    }

    private String schemaVersion() {
        var current = flyway.info().current();
        return current == null || current.getVersion() == null
                ? "none" : current.getVersion().getVersion();
    }

    private List<String> nextSteps(
            boolean tokenConfigured,
            DatabaseStatus database,
            boolean dataDirectoryReady,
            boolean reconciliationReady,
            boolean writesReady,
            Set<String> missingTools) {
        List<String> steps = new ArrayList<>();
        if (!tokenConfigured) {
            steps.add("Set FLOWERFARM_HARNESS_TOKEN to at least 24 characters.");
        }
        if (!dataDirectoryReady) {
            steps.add("Set FLOWERFARM_DATA_DIR to the writable FlowersForever data folder.");
        }
        if (!database.ready()) {
            steps.add("Close conflicting database copies and verify data-folder access.");
        }
        if (!reconciliationReady && !properties.isAllowSensitiveRead()) {
            steps.add("Set FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ=true for read-back, "
                    + "receipts, and exports.");
        }
        if (!writesReady && !properties.isAllowMutations()) {
            steps.add("Set FLOWERFARM_HARNESS_ALLOW_MUTATIONS=true for imports and "
                    + "farm operations.");
        }
        if (!missingTools.isEmpty()
                && (properties.isAllowSensitiveRead() && properties.isAllowMutations())) {
            steps.add("Repair the installation; required farm-data tools are missing.");
        }
        if (steps.isEmpty()) {
            steps.add("Register this launcher in the AI client's local MCP configuration.");
        }
        return List.copyOf(steps);
    }

    private record DatabaseStatus(boolean ready, String product) {
    }
}
