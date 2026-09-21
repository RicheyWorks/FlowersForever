package com.flowerfarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowerfarm.automation.farm.FarmDataImportReceiptService;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class FarmDataMappingNumericTextIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired InventoryService inventory;
    @Autowired FarmDataImportReceiptService receipts;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "quantity | 1,5", "quantity | $12", "quantity | 12%",
            "quantity | 1e2147483647", "quantity | 1e-2147483647",
            "price | 1$2", "price | %50", "price | 5%%", "price | $1,20.50",
            "price | 1e-2147483647"
    })
    void rejectsTheWholeBatchWithoutSavingEarlierRowsOrImportReceipts(String field, String input) {
        String source = "numeric-text-" + UUID.randomUUID();
        String firstName = "Valid numeric row " + UUID.randomUUID();
        String secondName = "Invalid numeric row " + UUID.randomUUID();
        Map<String, Object> second = new LinkedHashMap<>(Map.of(
                "sourceKey", "second", "name", secondName, "quantity", "12", "price", "$3.50"));
        second.put(field, input);
        Map<String, Object> arguments = new LinkedHashMap<>(Map.of(
                "record-type", "inventory",
                "source-records", List.of(
                        Map.of("sourceKey", "first", "name", firstName,
                                "quantity", "1,250", "price", "$1,200.50"), second)));

        ResponseEntity<JsonNode> preview = command("preview-mapped-records", arguments);

        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode output = preview.getBody().path("output");
        assertThat(output.path("ready").asBoolean()).isFalse();
        assertThat(output.path("rowCount").asInt()).isEqualTo(2);
        assertThat(output.path("invalidCount").asInt()).isEqualTo(1);
        assertThat(output.path("issues")).allSatisfy(issue -> {
            assertThat(issue.path("row").asInt()).isEqualTo(2);
            assertThat(issue.path("field").asText()).isEqualTo(field);
        });

        arguments.put("source-system", source);
        ResponseEntity<JsonNode> imported = command("import-mapped-records", arguments);

        assertThat(imported.getStatusCode()).as("%s", imported.getBody())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(imported.getBody().path("message").asText())
                .contains("1 invalid row(s)", "Row 2", field);
        assertThat(inventory.findByNameIgnoreCase(firstName)).isEmpty();
        assertThat(inventory.findByNameIgnoreCase(secondName)).isEmpty();
        assertThat(receipts.list(source, "inventory", 0, 20).getTotalElements()).isZero();
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
