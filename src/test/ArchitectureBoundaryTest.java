package com.flowerfarm.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Application package boundaries")
class ArchitectureBoundaryTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "com", "flowerfarm");
    private static final Pattern FARM_IMPORT = Pattern.compile(
            "^import com\\.flowerfarm\\.([a-zA-Z0-9_]+)\\.", Pattern.MULTILINE);
    private static final Pattern SPRING_DATA_IMPORT = Pattern.compile(
            "^import org\\.springframework\\.data\\.", Pattern.MULTILINE);

    private static final Map<String, List<String>> FORBIDDEN_DEPENDENCIES = Map.of(
            "model", List.of("auth", "cli", "config", "connector", "controller", "gui",
                    "repository", "service"),
            "repository", List.of("auth", "cli", "config", "connector", "controller", "gui",
                    "service"),
            "service", List.of("cli", "config", "connector", "controller", "gui"),
            "connector", List.of("cli", "config", "controller", "gui", "repository"),
            "controller", List.of("cli", "config", "gui", "repository"),
            "gui", List.of("cli", "config", "controller", "repository"),
            "cli", List.of("config", "controller", "gui", "repository")
    );

    @Test
    @DisplayName("dependencies point inward through declared boundaries")
    void dependenciesPointInward() throws IOException {
        assertThat(SOURCES).isDirectory();
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(SOURCES)) {
            for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                String owner = SOURCES.relativize(source).getName(0).toString();
                List<String> forbidden = FORBIDDEN_DEPENDENCIES.get(owner);
                if (forbidden == null) {
                    continue;
                }
                String content = Files.readString(source);
                Matcher imports = FARM_IMPORT.matcher(content);
                while (imports.find()) {
                    String dependency = imports.group(1);
                    if (forbidden.contains(dependency)) {
                        violations.add(SOURCES.relativize(source) + " imports " + dependency);
                    }
                }
                if ("service".equals(owner) && SPRING_DATA_IMPORT.matcher(content).find()) {
                    violations.add(SOURCES.relativize(source) + " imports Spring Data");
                }
            }
        }

        assertThat(violations).as("package boundary violations").isEmpty();
    }

    @Test
    @DisplayName("workflow services do not own document rendering or file writing")
    void workflowServicesDelegateOutputFormatting() throws IOException {
        for (String service : List.of("HarvestService.java", "InventoryService.java",
                "OrderService.java")) {
            String source = Files.readString(SOURCES.resolve("service").resolve(service));
            assertThat(source)
                    .as(service + " output adapter dependencies")
                    .doesNotContain("com.lowagie", "PdfWriter", "FileWriter", "BufferedWriter");
        }
    }

    @Test
    @DisplayName("maintained connectors use one component-scanned lifecycle")
    void connectorsUseComponentScanning() throws IOException {
        Path implementations = SOURCES.resolve("connector").resolve("impl");
        for (String connector : List.of("AirtableConnector.java", "CsvInventoryConnector.java",
                "ExcelConnector.java", "FarmbriteConnector.java", "FloranextConnector.java",
                "GoogleSheetsConnector.java", "ShopifyConnector.java", "SquareConnector.java",
                "WebhookConnector.java")) {
            assertThat(Files.readString(implementations.resolve(connector)))
                    .as(connector + " registration")
                    .contains("@Component");
        }
        assertThat(SOURCES.resolve("config").resolve("ConnectorBeansConfig.java"))
                .doesNotExist();
    }

    @Test
    @DisplayName("desktop session state is instance-owned and stays out of tabs")
    void desktopSessionIsInstanceOwned() throws IOException {
        Path auth = SOURCES.resolve("auth");
        assertThat(auth.resolve("FarmSession.java")).doesNotExist();
        assertThat(Files.readString(auth.resolve("DesktopSession.java")))
                .doesNotContain("static volatile", "static FarmUser");
        try (var paths = Files.walk(SOURCES.resolve("gui").resolve("tabs"))) {
            for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(source))
                        .as("tab session boundary: " + source.getFileName())
                        .doesNotContain("DesktopSession", "FarmSession");
            }
        }
    }

    @Test
    @DisplayName("desktop interaction shells delegate workflows and navigation")
    void desktopShellsDelegateInteractionResponsibilities() throws IOException {
        Path gui = SOURCES.resolve("gui");
        String crm = Files.readString(gui.resolve("tabs").resolve("CrmTab.java"));
        String harvest = Files.readString(gui.resolve("tabs").resolve("HarvestLogTab.java"));
        String inventory = Files.readString(gui.resolve("tabs").resolve("InventoryTab.java"));
        String addItem = Files.readString(gui.resolve("tabs").resolve("AddItemTab.java"));
        String frame = Files.readString(gui.resolve("FlowerFarmGUI.java"));
        String cli = Files.readString(SOURCES.resolve("cli").resolve("FlowerFarmCLI.java"));

        assertThat(crm).doesNotContain("customerService.", "orderService.");
        assertThat(harvest).doesNotContain("harvestService.");
        assertThat(inventory).doesNotContain("showInputDialog", "inventoryService.editItem",
                "inventoryService.deleteItem");
        assertThat(addItem).doesNotContain("inventoryService.");
        assertThat(Files.readString(gui.resolve("tabs").resolve("RoseVisualizerTab.java")))
                .doesNotContain("showInputDialog");
        assertThat(inventory).contains("InventoryItemFormView", "InventoryItemPresenter");
        assertThat(addItem).contains("InventoryItemFormView", "InventoryItemPresenter");
        assertThat(frame).doesNotContain("connectorRegistry.run", "new DashboardTab");
        assertThat(cli).doesNotContain("switch (choice)", "generatePriceListPdf",
                "generateInvoicePdf", "generateAuditPdf");
        assertThat(cli).contains("CliCommandRouter", "CliReportCommands");
        assertThat(Pattern.compile("\\.register\\(\"").matcher(cli).results().count())
                .as("non-exit CLI commands")
                .isEqualTo(21);
        assertThat(cli).contains(".exit(\"22\", \"Exit\"");
    }
}
