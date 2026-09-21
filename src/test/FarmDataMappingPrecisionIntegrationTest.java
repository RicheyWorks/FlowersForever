package com.flowerfarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowerfarm.automation.farm.FarmDataImportReceiptService;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.service.ProductionStudioService;
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

import java.util.List;
import java.util.LinkedHashMap;
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
class FarmDataMappingPrecisionIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ProductionStudioService production;
    @Autowired FarmDataImportReceiptService receipts;

    @ParameterizedTest
    @ValueSource(strings = {"5.0000000000000001", "4.9999999999999999"})
    void refusesFractionalJsonCellPackSizesWithoutSavingRecipesOrReceipts(String packSize) {
        String source = "precise-cell-" + UUID.randomUUID();
        String recipeName = "Precise mapped recipe " + UUID.randomUUID();
        String materials = "[{\"itemName\":\"Tulip\",\"stemsPerDesign\":3,"
                + "\"packSize\":" + packSize + ",\"packCost\":12}]";
        Map<String, Object> arguments = new LinkedHashMap<>(Map.of(
                "record-type", "floral-recipes",
                "field-mapping", Map.of("ingredients", "Materials"),
                "source-records", List.of(Map.of("sourceKey", "recipe-1", "name", recipeName,
                        "designType", "Bouquet", "Materials", materials))));
        int existingCount = production.listRecipes().size();

        ResponseEntity<JsonNode> preview = command("preview-mapped-records", arguments);

        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode output = preview.getBody().path("output");
        assertThat(output.path("ready").asBoolean()).isFalse();
        assertThat(output.path("invalidCount").asInt()).isEqualTo(1);
        assertThat(output.path("issues").toString()).contains("ingredients", "whole number");

        arguments.put("source-system", source);
        ResponseEntity<JsonNode> imported = command("import-mapped-records", arguments);

        assertThat(imported.getStatusCode()).as("%s", imported.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(imported.getBody().path("message").asText()).contains("whole number");
        assertThat(production.listRecipes()).hasSize(existingCount)
                .noneSatisfy(recipe -> assertThat(recipe.getName()).isEqualTo(recipeName));
        assertThat(receipts.list(source, "floral-recipes", 0, 20).getTotalElements()).isZero();
    }

    private ResponseEntity<JsonNode> command(String action, Map<String, Object> arguments) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HarnessController.TOKEN_HEADER, "farm-data-harness-token-123456");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("http://127.0.0.1:" + port + "/api/harness/farm-data/commands",
                HttpMethod.POST, new HttpEntity<>(Map.of("requestId", UUID.randomUUID().toString(),
                        "action", action, "arguments", arguments), headers), JsonNode.class);
    }
}
