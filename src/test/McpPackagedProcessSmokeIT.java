package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Packaged MCP process")
class McpPackagedProcessSmokeIT {

    private static final String TOKEN = "mcp-process-smoke-token-123456";
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(45);

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path dataDirectory;

    @Test
    @Timeout(90)
    @DisplayName("doctor and simultaneous stdio processes share the same durable farm store")
    void doctorAndStdioUseRealApplicationGraph() throws Exception {
        JsonNode doctor = runDoctor();
        assertThat(doctor.path("ready").asBoolean()).isTrue();
        assertThat(doctor.path("dataDirectory").asText())
                .isEqualTo(dataDirectory.toAbsolutePath().normalize().toString());
        assertThat(doctor.path("schemaVersion").asText()).isEqualTo("47");
        assertThat(doctor.path("enabledFarmDataToolCount").asInt()).isEqualTo(38);
        assertThat(doctor.path("capabilities").path("readBackAndResume").asBoolean())
                .isTrue();
        assertThat(doctor.path("capabilities").path("migrateAndOperate").asBoolean())
                .isTrue();
        assertThat(doctor.toString()).doesNotContain(TOKEN);

        try (McpProcess process = new McpProcess(startProcess("--mcp"))) {
            JsonNode tools = process.call(request("tools", "tools/list", null));
            assertThat(tools.path("result").path("tools").findValuesAsText("name"))
                    .contains("farm-data__import-mapped-records",
                            "farm-data__list-import-receipts",
                            "farm-data__list-records",
                            "farm-data__upsert-season-settings",
                            "farm-data__get-production-costs",
                            "farm-data__get-season-settings",
                            "farm-data__count-inventory",
                            "farm-data__complete-order-handoff",
                            "farm-data__get-operation-receipt");

            JsonNode resources = process.call(request("resources", "resources/list", null));
            assertThat(resources.path("result").path("resources").findValuesAsText("uri"))
                    .contains("flowersforever://harness/manifest",
                            "flowersforever://harness/farm-data/observation");
            JsonNode observation = process.call(request("observe", "resources/read",
                    mapper.createObjectNode().put("uri", "flowersforever://harness/farm-data/observation")));
            JsonNode snapshot = mapper.readTree(observation.path("result").path("contents")
                    .get(0).path("text").asText());
            assertThat(snapshot.path("pluginId").asText()).isEqualTo("farm-data");

            ObjectNode invalidArguments = mapper.createObjectNode();
            invalidArguments.putArray("records").addObject()
                    .put("name", "Rejected UTF8 ? crop").put("quantity", 1);
            byte[] invalidFrame = mapper.writeValueAsBytes(request(
                    "invalid-utf8", "tools/call",
                    tool("farm-data__upsert-inventory", invalidArguments)));
            for (int index = 0; index < invalidFrame.length; index++) {
                if (invalidFrame[index] == '?') {
                    invalidFrame[index] = (byte) 0xff;
                    break;
                }
            }
            JsonNode rejected = process.callRaw(invalidFrame);
            assertThat(rejected.path("error").path("code").asInt()).isEqualTo(-32700);

            ObjectNode source = mapper.createObjectNode();
            source.put("Legacy Key", "inventory-row-17");
            source.put("Crop", "Process Smoke Zinnia");
            source.put("Qty", "12");
            ObjectNode arguments = mapper.createObjectNode();
            arguments.put("record-type", "inventory");
            arguments.put("source-system", "process-smoke-workbook");
            arguments.putArray("source-records").add(source);
            arguments.putObject("field-mapping")
                    .put("name", "Crop")
                    .put("quantity", "Qty");
            arguments.putArray("source-key-fields").add("Legacy Key");

            JsonNode imported = process.call(request(
                    "import", "tools/call", tool("farm-data__import-mapped-records",
                            arguments)));
            assertThat(imported.path("result").path("isError").asBoolean())
                    .as(imported.toPrettyString()).isFalse();
            assertThat(imported.path("result").path("structuredContent")
                    .path("output").path("created").asInt()).isEqualTo(1);

            ObjectNode pageArguments = mapper.createObjectNode();
            pageArguments.put("record-type", "inventory");
            pageArguments.put("offset", 0);
            pageArguments.put("limit", 200);
            ObjectNode countArguments = mapper.createObjectNode()
                    .put("operation-key", "packaged-count-operation-17")
                    .put("reviewed-quantity", 12)
                    .put("counted-quantity", 15);
            // A second JVM must connect while the first still owns the database.
            // This exercises H2 AUTO_SERVER rather than just sequential reopening.
            try (McpProcess peer = new McpProcess(startProcess("--mcp"))) {
                JsonNode page = peer.call(request(
                        "read-back", "tools/call", tool("farm-data__list-records",
                                pageArguments)));
                assertThat(page.path("result").path("structuredContent")
                        .path("output").path("records").findValuesAsText("name"))
                        .contains("Process Smoke Zinnia");
                assertThat(page.path("result").path("structuredContent")
                        .path("output").path("records").findValuesAsText("name"))
                        .noneMatch(name -> name.startsWith("Rejected UTF8"));

                for (JsonNode record : page.path("result").path("structuredContent")
                        .path("output").path("records")) {
                    if ("Process Smoke Zinnia".equals(record.path("name").asText())) {
                        countArguments.put("item-id", record.path("id").asLong());
                    }
                }
                assertThat(countArguments.path("item-id").asLong()).isPositive();
                JsonNode counted = peer.call(request("count", "tools/call",
                        tool("farm-data__count-inventory", countArguments)));
                JsonNode operation = counted.path("result").path("structuredContent")
                        .path("output").path("operation");
                assertThat(operation.path("replayed").asBoolean()).isFalse();
                assertThat(operation.path("result").path("quantity").asInt()).isEqualTo(15);

                ObjectNode receiptArguments = mapper.createObjectNode();
                receiptArguments.put("source-system", "process-smoke-workbook");
                receiptArguments.put("record-type", "inventory");
                JsonNode receipts = peer.call(request(
                        "receipts", "tools/call", tool("farm-data__list-import-receipts",
                                receiptArguments)));
                assertThat(receipts.path("result").path("structuredContent")
                        .path("output").path("total").asInt()).isEqualTo(1);
            }

            JsonNode afterPeerExit = process.call(request(
                    "read-after-peer-exit", "tools/call", tool("farm-data__list-records",
                            pageArguments)));
            assertThat(afterPeerExit.path("result").path("structuredContent")
                    .path("output").path("records").findValuesAsText("name"))
                    .contains("Process Smoke Zinnia");

            JsonNode countedAgain = process.call(request("retry-count", "tools/call",
                    tool("farm-data__count-inventory", countArguments)));
            JsonNode recoveredOperation = countedAgain.path("result").path("structuredContent")
                    .path("output").path("operation");
            assertThat(recoveredOperation.path("replayed").asBoolean()).isTrue();
            assertThat(recoveredOperation.path("result").path("quantity").asInt()).isEqualTo(15);

            JsonNode receipt = process.call(request("recover-count", "tools/call",
                    tool("farm-data__get-operation-receipt", mapper.createObjectNode()
                            .put("operation-key", "packaged-count-operation-17"))));
            assertThat(receipt.path("result").path("structuredContent")
                    .path("output").path("operation").path("result"))
                    .isEqualTo(recoveredOperation.path("result"));
        }

        assertThat(dataDirectory.resolve("flowerfarm.mv.db")).isRegularFile();
    }

    @Test
    @Timeout(60)
    @DisplayName("doctor reports incomplete setup as one JSON result and exit code two")
    void doctorDiagnosesMissingSettings() throws Exception {
        Process process = startProcess(false, "--mcp-doctor");
        process.getOutputStream().close();
        CompletableFuture<String> stdout = readAll(process.getInputStream());
        CompletableFuture<String> stderr = readAll(process.getErrorStream());
        try {
            assertThat(process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isEqualTo(2);
            String protocolOutput = stdout.get(5, TimeUnit.SECONDS).trim();
            assertThat(protocolOutput.lines()).hasSize(1);
            JsonNode report = mapper.readTree(protocolOutput);
            assertThat(report.path("ready").asBoolean()).isFalse();
            assertThat(report.path("tokenConfigured").asBoolean()).isFalse();
            assertThat(report.path("enabledFarmDataToolCount").asInt()).isEqualTo(3);
            assertThat(report.path("nextSteps").toString())
                    .contains("FLOWERFARM_HARNESS_TOKEN",
                            "FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ",
                            "FLOWERFARM_HARNESS_ALLOW_MUTATIONS");
            assertThat(stderr.get(5, TimeUnit.SECONDS))
                    .doesNotContain("mcp-doctor-placeholder-token-only");
        } finally {
            stop(process);
        }
    }

    private JsonNode runDoctor() throws Exception {
        Process process = startProcess("--mcp-doctor");
        process.getOutputStream().close();
        CompletableFuture<String> stdout = readAll(process.getInputStream());
        CompletableFuture<String> stderr = readAll(process.getErrorStream());
        try {
            assertThat(process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            String protocolOutput = stdout.get(5, TimeUnit.SECONDS).trim();
            String diagnostics = stderr.get(5, TimeUnit.SECONDS);
            assertThat(protocolOutput.lines()).hasSize(1);
            assertThat(diagnostics).doesNotContain(TOKEN);
            return mapper.readTree(protocolOutput);
        } finally {
            stop(process);
        }
    }

    private Process startProcess(String argument) throws IOException {
        return startProcess(true, argument);
    }

    private Process startProcess(boolean configured, String argument) throws IOException {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        String classpath = System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
        String packagedJar = System.getProperty("flowerfarm.mcp.executable-jar");
        ProcessBuilder builder;
        if (packagedJar == null) {
            builder = new ProcessBuilder(java.toString(), "-cp", classpath,
                    "com.flowerfarm.FlowerFarmApp", argument);
        } else {
            Path jar = Path.of(packagedJar).toAbsolutePath().normalize();
            assertThat(jar).as("Maven's freshly packaged executable").isRegularFile();
            builder = new ProcessBuilder(java.toString(), "-jar", jar.toString(), argument);
        }
        builder.directory(Path.of("").toAbsolutePath().toFile());
        Map<String, String> environment = builder.environment();
        environment.remove("SPRING_PROFILES_ACTIVE");
        environment.remove("FLOWERFARM_HARNESS_TOKEN");
        environment.remove("FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ");
        environment.remove("FLOWERFARM_HARNESS_ALLOW_MUTATIONS");
        environment.put("FLOWERFARM_DATA_DIR", dataDirectory.toString());
        if (configured) {
            environment.put("FLOWERFARM_HARNESS_TOKEN", TOKEN);
            environment.put("FLOWERFARM_HARNESS_ALLOW_SENSITIVE_READ", "true");
            environment.put("FLOWERFARM_HARNESS_ALLOW_MUTATIONS", "true");
        }
        environment.put("FLOWERFARM_INVENTORY_SEED_CSV",
                dataDirectory.resolve("no-seed.csv").toString());
        return builder.start();
    }

    private ObjectNode request(String id, String method, ObjectNode tool) {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        ObjectNode params = request.putObject("params");
        ObjectNode metadata = params.putObject("_meta");
        metadata.put("io.modelcontextprotocol/protocolVersion",
                McpProtocolHandler.CURRENT_PROTOCOL_VERSION);
        metadata.putObject("io.modelcontextprotocol/clientInfo")
                .put("name", "process-smoke")
                .put("version", "1");
        metadata.putObject("io.modelcontextprotocol/clientCapabilities");
        if (tool != null) {
            params.setAll(tool);
        }
        return request;
    }

    private ObjectNode tool(String name, ObjectNode arguments) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("name", name);
        tool.set("arguments", arguments);
        return tool;
    }

    private static CompletableFuture<String> readAll(java.io.InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Could not read child-process stream", exception);
            }
        });
    }

    private static void stop(Process process) {
        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    private final class McpProcess implements AutoCloseable {

        private final Process process;
        private final BufferedReader output;
        private final BufferedWriter input;
        private final CompletableFuture<String> diagnostics;
        private final List<String> protocolLines = new ArrayList<>();

        private McpProcess(Process process) {
            this.process = process;
            output = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8));
            input = new BufferedWriter(new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8));
            diagnostics = readAll(process.getErrorStream());
        }

        private JsonNode call(JsonNode request) throws Exception {
            input.write(mapper.writeValueAsString(request));
            input.newLine();
            input.flush();
            return readResponse();
        }

        private JsonNode callRaw(byte[] request) throws Exception {
            input.flush();
            process.getOutputStream().write(request);
            process.getOutputStream().write('\n');
            process.getOutputStream().flush();
            return readResponse();
        }

        private JsonNode readResponse() throws Exception {
            String line = CompletableFuture.supplyAsync(() -> {
                try {
                    return output.readLine();
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not read MCP response", exception);
                }
            }).get(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(line).isNotBlank().startsWith("{");
            protocolLines.add(line);
            return mapper.readTree(line);
        }

        @Override
        public void close() throws Exception {
            try {
                input.close();
                assertThat(process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
                assertThat(process.exitValue()).isZero();
                String diagnosticText = diagnostics.get(5, TimeUnit.SECONDS);
                assertThat(diagnosticText)
                        .contains("FlowersForever MCP stdio adapter ready")
                        .doesNotContain(TOKEN);
                assertThat(protocolLines).allMatch(line -> line.startsWith("{"));
                assertThat(output.readLine()).as("No extra stdout after protocol EOF").isNull();
            } finally {
                stop(process);
                output.close();
            }
        }
    }
}
