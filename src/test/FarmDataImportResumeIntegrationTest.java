package com.flowerfarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.farm.FarmDataImportReceiptService;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.SeasonPlanService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.headless=true",
                "spring.datasource.url=jdbc:h2:mem:farm-data-harness;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=validate",
                "flowerfarm.inventory.seed-csv=target/no-farm-data-harness-seed.csv",
                "flowerfarm.irrigation.live-weather=false",
                "flowerfarm.harness.enabled=true",
                "flowerfarm.harness.token=farm-data-harness-token-123456",
                "flowerfarm.harness.allow-sensitive-read=true",
                "flowerfarm.harness.allow-mutations=true",
                "flowerfarm.exports.directory=target/farm-data-harness-exports"
        })
class FarmDataImportResumeIntegrationTest {
    private static final String TOKEN = "farm-data-harness-token-123456";

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper mapper;
    @Autowired InventoryService inventory;
    @Autowired CustomerService customers;
    @Autowired ProductionStudioService production;
    @Autowired SeasonPlanService plantings;
    @Autowired FarmDataImportReceiptService receipts;

    @ParameterizedTest(name = "{0} preserves a missing target receipt on unchanged and corrected retries")
    @ValueSource(strings = {"inventory", "customers", "growing-templates", "planting-plans", "floral-recipes"})
    void rejectsUnchangedAndCorrectedRetriesOfDeletedTargets(String type) {
        String sourceSystem = "deleted-target-" + type;
        String action = "upsert-" + type;
        Map<String, Object> original = sourceRow(type);
        original.put("sourceKey", "missing-row");
        original.put("notes", "Original imported row");
        JsonNode saved = command(action, Map.of("source-system", sourceSystem,
                "records", List.of(original))).path("output");
        long id = saved.path("records").get(0).path("id").asLong();
        JsonNode originalReceipt = mapper.valueToTree(receipts.list(sourceSystem, type, 0, 20)
                .getContent().get(0));
        delete(type, id);
        long remaining = recordCount(type);

        Map<String, Object> correction = new LinkedHashMap<>(original);
        correction.put("notes", "Corrected after the target was deleted");
        for (Map<String, Object> row : List.of(original, correction)) {
            ResponseEntity<JsonNode> rejected = commandResponse(action,
                    Map.of("source-system", sourceSystem, "records", List.of(row)));

            assertThat(rejected.getStatusCode()).as("Retry: %s", row.get("notes"))
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThat(rejected.getBody().path("message").asText())
                    .contains("maps to missing", type, "#" + id);
            assertThat(recordCount(type)).isEqualTo(remaining);
            assertThat(targetExists(type, id)).isFalse();
            var retained = receipts.list(sourceSystem, type, 0, 20);
            assertThat(retained.getTotalElements()).isEqualTo(1);
            assertThat(mapper.<JsonNode>valueToTree(retained.getContent().get(0)))
                    .isEqualTo(originalReceipt);
        }
    }

    private Map<String, Object> sourceRow(String type) {
        return new LinkedHashMap<>(switch (type) {
            case "inventory" -> Map.of("name", "Deleted target inventory", "quantity", 5);
            case "customers" -> Map.of("name", "Deleted target customer", "customerType", "MARKET");
            case "growing-templates" -> Map.ofEntries(
                    Map.entry("name", "Deleted target template"), Map.entry("cropName", "Sunflower"),
                    Map.entry("spacingInches", 6), Map.entry("rowsPerBed", 2),
                    Map.entry("sowDaysFromFrost", -28), Map.entry("daysToHarvest", 70),
                    Map.entry("harvestWindowDays", 21), Map.entry("successionDays", 14),
                    Map.entry("stemsPerPlant", 1.5), Map.entry("germinationPercent", 90),
                    Map.entry("soilPrepLeadDays", 7), Map.entry("supportDaysAfterPlanting", 14));
            case "planting-plans" -> Map.ofEntries(
                    Map.entry("seasonYear", 2031), Map.entry("cropName", "Deleted target crop"),
                    Map.entry("bedOrField", "Deleted target bed"), Map.entry("sowDate", "2031-02-15"),
                    Map.entry("harvestStart", "2031-07-01"), Map.entry("harvestEnd", "2031-07-21"),
                    Map.entry("bedFeet", 50), Map.entry("expectedStems", 500));
            case "floral-recipes" -> Map.of("name", "Deleted target recipe", "designType", "Bouquet",
                    "ingredients", List.of(Map.of("itemName", "Sunflower", "stemsPerDesign", 3,
                            "packSize", 10, "packCost", 12)));
            default -> throw new IllegalArgumentException(type);
        });
    }

    private void delete(String type, long id) {
        switch (type) {
            case "inventory" -> inventory.deleteById(id);
            case "customers" -> customers.delete(id);
            case "growing-templates" -> production.deleteTemplate(id);
            case "planting-plans" -> plantings.delete(id);
            case "floral-recipes" -> production.deleteRecipe(id);
            default -> throw new IllegalArgumentException(type);
        }
    }

    private boolean targetExists(String type, long id) {
        return switch (type) {
            case "inventory" -> inventory.findById(id).isPresent();
            case "customers" -> customers.findById(id).isPresent();
            case "growing-templates" -> production.findTemplate(id).isPresent();
            case "planting-plans" -> plantings.findById(id).isPresent();
            case "floral-recipes" -> production.findRecipe(id).isPresent();
            default -> throw new IllegalArgumentException(type);
        };
    }

    private long recordCount(String type) {
        return command("list-records", Map.of("record-type", type, "limit", 1))
                .path("output").path("total").asLong();
    }

    private JsonNode command(String action, Map<String, Object> arguments) {
        ResponseEntity<JsonNode> response = commandResponse(action, arguments);
        assertThat(response.getStatusCode()).as("Harness response: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<JsonNode> commandResponse(String action, Map<String, Object> arguments) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HarnessController.TOKEN_HEADER, TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("http://127.0.0.1:" + port + "/api/harness/farm-data/commands",
                HttpMethod.POST, new HttpEntity<>(Map.of("requestId", UUID.randomUUID().toString(),
                        "action", action, "arguments", arguments), headers), JsonNode.class);
    }
}
