package com.flowerfarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.repository.ProductionRepository;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.SeasonPlanService;

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
@DisplayName("AI farm data REST migration")
class FarmDataHarnessIntegrationTest {

    private static final String TOKEN = "farm-data-harness-token-123456";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    CustomerService customerService;

    @Autowired
    HarvestService harvestService;

    @Autowired
    InventoryService inventoryService;

    @Autowired
    ProductionStudioService productionStudioService;

    @Autowired
    SeasonPlanService seasonPlanService;

    @Autowired
    ProductionRepository productionRepository;

    @Test
    @DisplayName("migrates master data, creates operations, reads records, and replays safely")
    void migratesAndOperatesThroughCanonicalTools() {
        JsonNode customer = command("customer-import", "upsert-customers", """
                {"records":[{"name":"AI Migration Market","contactName":"Pat",
                 "email":"pat@example.test","customerType":"MARKET"}]}
                """);
        assertThat(customer.path("success").asBoolean()).isTrue();
        assertThat(customer.path("output").path("created").asInt()).isEqualTo(1);

        JsonNode replay = command("customer-import", "upsert-customers", """
                {"records":[{"name":"AI Migration Market","contactName":"Pat",
                 "email":"pat@example.test","customerType":"MARKET"}]}
                """);
        assertThat(replay.path("replayed").asBoolean()).isTrue();

        JsonNode inventory = command("inventory-import", "upsert-inventory", """
                {"records":[{"name":"AI Migration Dahlia","category":"Cut flower",
                 "quantity":40,"unit":"stem","cost":0.75,"price":2.5}]}
                """);
        assertThat(inventory.path("output").path("created").asInt()).isEqualTo(1);

        JsonNode historicalHarvest = command(
                "harvest-history", "import-harvest-history", """
                {"records":[{"harvestDate":"2026-08-12",
                 "cropName":"AI Migration Dahlia","quantity":100,
                 "unit":"stem","bedOrField":"Old field"}]}
                """);
        assertThat(historicalHarvest.path("output").path("processed").asInt())
                .isEqualTo(1);

        JsonNode historicalOrder = command("order-history", "import-order-history", """
                {"records":[{"customerName":"AI Migration Market",
                 "orderDate":"2026-08-14","status":"FULFILLED","handoffType":"MARKET",
                 "lines":[{"productName":"AI Migration Dahlia","quantity":20,
                 "unit":"stem","unitPrice":2.25}]}]}
                """);
        assertThat(historicalOrder.path("output").path("processed").asInt()).isEqualTo(1);

        JsonNode order = command("order-import", "create-orders", """
                {"records":[{"customerName":"AI Migration Market",
                 "orderDate":"2026-09-12","status":"DRAFT","handoffType":"MARKET",
                 "lines":[{"productName":"AI Migration Dahlia","quantity":10,
                 "unit":"stem","unitPrice":2.5}]}]}
                """);
        assertThat(order.path("output").path("processed").asInt()).isEqualTo(1);

        JsonNode harvest = command("harvest-upload", "log-harvests", """
                {"records":[{"harvestDate":"2026-09-02",
                 "cropName":"AI Migration Dahlia","quantity":12,
                 "unit":"stem","bedOrField":"AI Bed","lotCode":"AI-LOT-0902",
                 "grade":"SELECT","lossQuantity":2,"storageLocation":"Cooler AI-1"}]}
                """);
        assertThat(harvest.path("output").path("processed").asInt()).isEqualTo(1);
        JsonNode savedHarvest = harvest.path("output").path("records").get(0);
        assertThat(savedHarvest.path("lotCode").asText()).isEqualTo("AI-LOT-0902");
        assertThat(savedHarvest.path("grade").asText()).isEqualTo("SELECT");
        assertThat(savedHarvest.path("lossQuantity").asDouble()).isEqualTo(2);

        JsonNode traced = command("harvest-lot-lookup", "lookup-harvest-lot", """
                {"lot-code":"ai-lot-0902"}
                """);
        assertThat(traced.path("output").path("harvest").path("id").asLong())
                .isEqualTo(savedHarvest.path("id").asLong());
        assertThat(traced.path("output").path("harvest").path("storageLocation").asText())
                .isEqualTo("Cooler AI-1");

        JsonNode knowledge = command("knowledge-upload", "record-knowledge", """
                {"records":[{"observedOn":"2026-09-02",
                 "summary":"AI migration bed note",
                 "observation":"Strong stems after irrigation","kind":"PRACTICE",
                 "tags":["migration","irrigation"]}]}
                """);
        assertThat(knowledge.path("output").path("processed").asInt()).isEqualTo(1);

        JsonNode listed = command("inventory-read", "list-records", """
                {"record-type":"inventory","offset":0,"limit":200}
                """);
        assertThat(listed.path("output").path("records").toString())
                .contains("AI Migration Dahlia");
        assertThat(listed.path("output").path("records").toString())
                .contains("\"quantity\":52");
    }

    @Test
    @DisplayName("turns mobile-style task actuals and matched harvests into AI-readable costs")
    void reportsProductionCostsThroughHarness() {
        JsonNode settings = command("cost-settings", "upsert-season-settings", """
                {"season-year":2026,"last-spring-frost":"2026-04-15",
                 "first-fall-frost":"2026-10-15","source":"Cost test station",
                 "loaded-labor-rate-per-hour":30}
                """);
        assertThat(settings.path("output").path("seasonSettings")
                .path("loadedLaborRatePerHour").asDouble()).isEqualTo(30);
        JsonNode plan = command("cost-plan", "upsert-planting-plans", """
                {"records":[{"seasonYear":2026,"cropName":"Cost Test Amaranth",
                 "variety":"Hot Biscuits","bedOrField":"Cost Bed",
                 "sowDate":"2026-04-01","transplantDate":"2026-05-15",
                 "harvestStart":"2026-08-20","harvestEnd":"2026-09-10",
                 "bedFeet":20,"expectedStems":100,"status":"HARVESTING"}]}
                """);
        long planId = plan.path("output").path("records").get(0).path("id").asLong();
        ProductionTask task = productionRepository.saveTask(new ProductionTask(
                planId, null, java.time.LocalDate.of(2026, 9, 1),
                ProductionTaskType.CUSTOM, ProductionTaskStatus.TODO,
                "Cost Test Amaranth", "Cost Bed", 1, false, "Install support"));

        JsonNode completed = command("cost-task", "complete-tasks", """
                {"records":[{"taskId":%d,"actualMinutes":90,"inputCost":12,
                 "fieldObservation":"Support installed"}]}
                """.formatted(task.getId()));
        assertThat(completed.path("output").path("records").get(0)
                .path("actualMinutes").asInt()).isEqualTo(90);
        command("cost-harvest", "log-harvests", """
                {"records":[{"harvestDate":"2026-09-02",
                 "cropName":"Cost Test Amaranth","quantity":20,"unit":"stems",
                 "bedOrField":"Cost Bed","lotCode":"COST-AMARANTH-0902"}]}
                """);

        JsonNode costs = command("cost-report", "get-production-costs", """
                {"season-year":2026}
                """).path("output").path("report");

        assertThat(costs.path("laborRateSource").asText()).isEqualTo("SEASON_SETTINGS");
        assertThat(costs.path("actualMinutes").asInt()).isEqualTo(90);
        assertThat(costs.path("laborCost").asDouble()).isEqualTo(45);
        assertThat(costs.path("inputCost").asDouble()).isEqualTo(12);
        assertThat(costs.path("allocatedProductionCost").asDouble()).isEqualTo(57);
        assertThat(costs.path("costPerHarvestedStem").asDouble()).isEqualTo(2.85);
        assertThat(costs.path("plantings")).hasSize(1);
    }

    @Test
    @DisplayName("continues canonical exports from arbitrary database offsets")
    void pagesCanonicalRecordsInDatabase() {
        JsonNode first = command("database-page-1", "list-records", """
                {"record-type":"inventory","offset":1,"limit":2}
                """);

        assertThat(first.path("output").path("offset").asInt()).isEqualTo(1);
        assertThat(first.path("output").path("records")).hasSize(2);
        assertThat(first.path("output").path("total").asLong()).isGreaterThanOrEqualTo(4);
        assertThat(first.path("output").path("hasMore").asBoolean()).isTrue();
        assertThat(first.path("output").path("nextOffset").asLong()).isEqualTo(3);

        JsonNode second = command("database-page-2", "list-records", """
                {"record-type":"inventory","offset":3,"limit":2}
                """);

        assertThat(second.path("output").path("offset").asInt()).isEqualTo(3);
        assertThat(second.path("output").path("total").asLong())
                .isEqualTo(first.path("output").path("total").asLong());
        assertThat(second.path("output").path("records").findValuesAsText("id"))
                .doesNotContainAnyElementsOf(
                        first.path("output").path("records").findValuesAsText("id"));
    }

    @Test
    @DisplayName("creates and resumes an immutable export through REST")
    void createsDurableRecordExport() {
        String key = "rest-export-" + java.util.UUID.randomUUID();
        String arguments = "{\"export-key\":\"" + key + "\","
                + "\"record-types\":[\"inventory\"],\"chunk-records\":2}";
        JsonNode created = command(
                "create-durable-export", "create-record-export", arguments);
        JsonNode export = created.path("output").path("export");

        assertThat(created.path("output").path("replayed").asBoolean()).isFalse();
        assertThat(export.path("recordCounts").path("inventory").asLong())
                .isGreaterThanOrEqualTo(4);
        assertThat(export.path("partCount").asInt()).isGreaterThanOrEqualTo(2);

        JsonNode replayed = command(
                "replay-durable-export", "create-record-export", arguments);
        assertThat(replayed.path("output").path("replayed").asBoolean()).isTrue();
        assertThat(replayed.path("output").path("export").path("exportId").asText())
                .isEqualTo(export.path("exportId").asText());

        JsonNode chunk = command(
                "read-durable-export", "read-record-export",
                "{\"export-id\":\"" + export.path("exportId").asText()
                        + "\",\"part\":0}")
                .path("output").path("chunk");
        assertThat(chunk.path("records")).hasSize(2);
        assertThat(chunk.path("snapshotSha256").asText())
                .isEqualTo(export.path("snapshotSha256").asText());

        ResponseEntity<JsonNode> conflict = commandResponse(
                "conflict-durable-export", "create-record-export",
                "{\"export-key\":\"" + key + "\","
                        + "\"record-types\":[\"customers\"],\"chunk-records\":2}");
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().path("message").asText())
                .contains("different completed snapshot");

        String completeKey = "rest-complete-export-" + java.util.UUID.randomUUID();
        JsonNode complete = command(
                "create-complete-export", "create-record-export",
                "{\"export-key\":\"" + completeKey + "\",\"chunk-records\":50}")
                .path("output").path("export");
        assertThat(complete.path("recordTypes")).hasSize(10);
        assertThat(complete.path("recordCounts")).hasSize(10);
        assertThat(complete.path("totalRecords").asLong()).isPositive();

        long recordsRead = 0;
        int partCount = complete.path("partCount").asInt();
        for (int part = 0; part < partCount; part++) {
            JsonNode completeChunk = command(
                    "read-complete-export-" + part, "read-record-export",
                    "{\"export-id\":\"" + complete.path("exportId").asText()
                            + "\",\"part\":" + part + "}")
                    .path("output").path("chunk");
            assertThat(completeChunk.path("partNumber").asInt()).isEqualTo(part);
            assertThat(completeChunk.path("partSha256").asText()).hasSize(64);
            recordsRead += completeChunk.path("records").size();
            if (part + 1 < partCount) {
                assertThat(completeChunk.path("hasMore").asBoolean()).isTrue();
                assertThat(completeChunk.path("nextPart").asInt()).isEqualTo(part + 1);
            } else {
                assertThat(completeChunk.path("hasMore").asBoolean()).isFalse();
                assertThat(completeChunk.path("nextPart").asInt()).isEqualTo(-1);
            }
        }
        assertThat(recordsRead).isEqualTo(complete.path("totalRecords").asLong());
    }

    @Test
    @DisplayName("source keys survive request changes, update masters, and reject history drift")
    void resumesUsingDurableSourceKeys() {
        JsonNode originalCustomer = command(
                "durable-customer-1", "upsert-customers", """
                {"source-system":"legacy-excel-a","records":[{
                 "sourceKey":"customer-row-17","name":"Original Farm Stand",
                 "contactName":"Alex","customerType":"MARKET"}]}
                """);
        long customerId = originalCustomer.path("output").path("records").get(0)
                .path("id").asLong();

        JsonNode renamedCustomer = command(
                "durable-customer-2", "upsert-customers", """
                {"source-system":"legacy-excel-a","records":[{
                 "sourceKey":"customer-row-17","name":"Renamed Farm Stand",
                 "contactName":"Alexis","customerType":"MARKET"}]}
                """);
        assertThat(renamedCustomer.path("replayed").asBoolean()).isFalse();
        assertThat(renamedCustomer.path("output").path("updated").asInt()).isEqualTo(1);
        assertThat(renamedCustomer.path("output").path("records").get(0)
                .path("id").asLong()).isEqualTo(customerId);
        assertThat(customerService.findById(customerId).orElseThrow().getName())
                .isEqualTo("Renamed Farm Stand");

        command("durable-inventory-1", "upsert-inventory", """
                {"source-system":"legacy-excel-a","records":[{
                 "sourceKey":"inventory-row-9","name":"Durable Dahlia",
                 "category":"Cut flower","quantity":15,"unit":"stem"}]}
                """);

        String harvestArguments = """
                {"source-system":"legacy-excel-a","records":[{
                 "sourceKey":"harvest-row-88","harvestDate":"2026-07-10",
                 "cropName":"Durable Dahlia","quantity":25,"unit":"stem"}]}
                """;
        JsonNode firstHarvest = command(
                "durable-harvest-1", "import-harvest-history", harvestArguments);
        JsonNode resumedHarvest = command(
                "durable-harvest-2", "import-harvest-history", harvestArguments);

        assertThat(firstHarvest.path("output").path("created").asInt()).isEqualTo(1);
        assertThat(resumedHarvest.path("replayed").asBoolean()).isFalse();
        assertThat(resumedHarvest.path("output").path("created").asInt()).isZero();
        assertThat(resumedHarvest.path("output").path("replayed").asInt()).isEqualTo(1);
        assertThat(harvestService.getAll().stream()
                .filter(entry -> entry.getCropName().equals("Durable Dahlia")))
                .hasSize(1);
        assertThat(inventoryService.findByNameIgnoreCase("Durable Dahlia").orElseThrow()
                .getQuantity()).isEqualTo(15);

        ResponseEntity<JsonNode> conflict = commandResponse(
                "durable-harvest-conflict", "import-harvest-history", """
                {"source-system":"legacy-excel-a","records":[{
                 "sourceKey":"harvest-row-88","harvestDate":"2026-07-10",
                 "cropName":"Durable Dahlia","quantity":26,"unit":"stem"}]}
                """);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().path("message").asText())
                .contains("already maps", "different content");
        assertThat(harvestService.getAll().stream()
                .filter(entry -> entry.getCropName().equals("Durable Dahlia")))
                .hasSize(1);

        JsonNode receipts = command(
                "durable-receipts", "list-import-receipts", """
                {"source-system":"legacy-excel-a","record-type":"harvests",
                 "page":0,"limit":20}
                """);
        assertThat(receipts.path("output").path("total").asInt()).isEqualTo(1);
        assertThat(receipts.path("output").path("receipts").get(0)
                .path("sourceKey").asText()).isEqualTo("harvest-row-88");

        ResponseEntity<JsonNode> missingKey = commandResponse(
                "durable-missing-key", "record-knowledge", """
                {"source-system":"legacy-excel-a","records":[{
                 "observedOn":"2026-09-01","summary":"Missing source key",
                 "observation":"Should fail before save"}]}
                """);
        assertThat(missingKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingKey.getBody().path("message").asText())
                .contains("source-system", "sourceKey");
    }

    @Test
    @DisplayName("migrates production templates, planting plans, and nested recipes")
    void migratesProductionStudioRecords() {
        String templateArguments = """
                {"source-system":"production-sheet-b","records":[{
                 "sourceKey":"template-row-4","name":"AI Sunflower Program",
                 "cropName":"Sunflower","variety":"ProCut Orange",
                 "spacingInches":6,"rowsPerBed":2,"sowDaysFromFrost":-28,
                 "transplantDaysFromFrost":14,"daysToHarvest":70,
                 "harvestWindowDays":21,"successionDays":14,
                 "stemsPerPlant":1.5,"germinationPercent":90,
                 "soilPrepLeadDays":7,"supportDaysAfterPlanting":14,
                 "active":true,"notes":"Imported production assumptions"}]}
                """;
        JsonNode template = command(
                "studio-template-1", "upsert-growing-templates", templateArguments);
        long templateId = template.path("output").path("records").get(0).path("id").asLong();
        JsonNode templateReplay = command(
                "studio-template-2", "upsert-growing-templates", templateArguments);

        assertThat(template.path("output").path("created").asInt()).isEqualTo(1);
        assertThat(templateReplay.path("output").path("replayed").asInt()).isEqualTo(1);

        JsonNode templateUpdate = command(
                "studio-template-3", "upsert-growing-templates", """
                {"source-system":"production-sheet-b","records":[{
                 "sourceKey":"template-row-4","name":"AI Sunflower Program Renamed",
                 "cropName":"Sunflower","variety":"ProCut Orange",
                 "spacingInches":6,"rowsPerBed":2,"sowDaysFromFrost":-28,
                 "transplantDaysFromFrost":14,"daysToHarvest":72,
                 "harvestWindowDays":21,"successionDays":14,
                 "stemsPerPlant":1.5,"germinationPercent":90,
                 "soilPrepLeadDays":7,"supportDaysAfterPlanting":14,
                 "active":true,"notes":"Corrected after import"}]}
                """);
        assertThat(templateUpdate.path("output").path("records").get(0)
                .path("id").asLong()).isEqualTo(templateId);
        assertThat(productionStudioService.findTemplate(templateId).orElseThrow().getName())
                .isEqualTo("AI Sunflower Program Renamed");

        JsonNode plan = command("studio-plan-1", "upsert-planting-plans", """
                {"source-system":"production-sheet-b","records":[{
                 "sourceKey":"planting-row-11","seasonYear":2031,
                 "cropName":"Sunflower","variety":"ProCut Orange",
                 "bedOrField":"AI Bed 11","sowDate":"2031-02-15",
                 "transplantDate":"2031-04-15","harvestStart":"2031-07-01",
                 "harvestEnd":"2031-07-21","bedFeet":50,"expectedStems":500,
                 "status":"PLANNED","notes":"Imported planting block"}]}
                """);
        long planId = plan.path("output").path("records").get(0).path("id").asLong();
        JsonNode planUpdate = command("studio-plan-2", "upsert-planting-plans", """
                {"source-system":"production-sheet-b","records":[{
                 "sourceKey":"planting-row-11","seasonYear":2031,
                 "cropName":"Sunflower","variety":"ProCut Orange",
                 "bedOrField":"AI Bed 11","sowDate":"2031-02-15",
                 "transplantDate":"2031-04-15","harvestStart":"2031-07-01",
                 "harvestEnd":"2031-07-28","bedFeet":50,"expectedStems":550,
                 "status":"PLANTED","notes":"Field correction"}]}
                """);
        assertThat(planUpdate.path("output").path("records").get(0)
                .path("id").asLong()).isEqualTo(planId);
        assertThat(seasonPlanService.findById(planId).orElseThrow().getExpectedStems())
                .isEqualTo(550);

        JsonNode recipe = command("studio-recipe-1", "upsert-floral-recipes", """
                {"source-system":"production-sheet-b","records":[{
                 "sourceKey":"recipe-row-7","name":"AI Market Bouquet",
                 "designType":"Bouquet","salePrice":38,"laborMinutes":12,
                 "laborRatePerHour":24,"overheadPercent":15,"wastePercent":10,
                 "notes":"Imported bouquet build","ingredients":[
                   {"itemName":"Sunflower","stemsPerDesign":3,
                    "packSize":10,"packCost":12,"notes":"Focal"},
                   {"itemName":"Basil","stemsPerDesign":2,
                    "packSize":10,"packCost":8,"notes":"Foliage"}]}]}
                """);
        long recipeId = recipe.path("output").path("records").get(0).path("id").asLong();
        JsonNode recipeUpdate = command("studio-recipe-2", "upsert-floral-recipes", """
                {"source-system":"production-sheet-b","records":[{
                 "sourceKey":"recipe-row-7","name":"AI Market Bouquet",
                 "designType":"Bouquet","salePrice":42,"laborMinutes":14,
                 "laborRatePerHour":24,"overheadPercent":15,"wastePercent":10,
                 "notes":"Corrected bouquet build","ingredients":[
                   {"itemName":"Sunflower","stemsPerDesign":4,
                    "packSize":10,"packCost":12,"notes":"Focal"},
                   {"itemName":"Basil","stemsPerDesign":2,
                    "packSize":10,"packCost":8,"notes":"Foliage"}]}]}
                """);
        assertThat(recipeUpdate.path("output").path("records").get(0)
                .path("id").asLong()).isEqualTo(recipeId);
        assertThat(productionStudioService.findRecipe(recipeId).orElseThrow().getIngredients())
                .hasSize(2);

        JsonNode listed = command("studio-list", "list-records", """
                {"record-type":"floral-recipes","offset":0,"limit":200}
                """);
        assertThat(listed.path("output").path("records").toString())
                .contains("AI Market Bouquet", "Sunflower", "Basil");
    }

    @Test
    @DisplayName("previews unfamiliar columns and atomically imports corrected source rows")
    void mapsAndImportsProviderRecords() {
        JsonNode suggestion = command(
                "mapped-suggest", "suggest-field-mapping", """
                {"record-type":"inventory","source-fields":[
                 "Legacy Key","Crop Label","Qty Available","Unit Price","UOM"]}
                """);
        assertThat(suggestion.path("output").path("fieldMapping").path("price").asText())
                .isEqualTo("Unit Price");
        assertThat(suggestion.path("output").path("fieldMapping").path("unit").asText())
                .isEqualTo("UOM");

        JsonNode preview = command(
                "mapped-preview", "preview-mapped-records", """
                {"record-type":"inventory",
                 "source-records":[
                  {"Legacy Key":"mapped-41","Crop Label":"Mapped Lisianthus",
                   "Qty Available":"80","Unit Price":"$1.95","UOM":"stem"},
                  {"Legacy Key":"mapped-42","Crop Label":"Broken Crop",
                   "Qty Available":"many","Unit Price":"$2.10","UOM":"stem"}],
                 "field-mapping":{"name":"Crop Label","quantity":"Qty Available"},
                 "defaults":{"category":"Cut flower"},
                 "source-key-fields":["Legacy Key"]}
                """);
        assertThat(preview.path("output").path("ready").asBoolean()).isFalse();
        assertThat(preview.path("output").path("invalidCount").asInt()).isEqualTo(1);
        assertThat(preview.path("output").path("issues").get(0).path("row").asInt())
                .isEqualTo(2);
        assertThat(inventoryService.findByNameIgnoreCase("Mapped Lisianthus")).isEmpty();

        ResponseEntity<JsonNode> rejected = commandResponse(
                "mapped-invalid-import", "import-mapped-records", """
                {"record-type":"inventory","source-system":"legacy-excel-mapped",
                 "source-records":[
                  {"Legacy Key":"mapped-41","Crop Label":"Mapped Lisianthus",
                   "Qty Available":"80","Unit Price":"$1.95","UOM":"stem"},
                  {"Legacy Key":"mapped-42","Crop Label":"Broken Crop",
                   "Qty Available":"many","Unit Price":"$2.10","UOM":"stem"}],
                 "field-mapping":{"name":"Crop Label","quantity":"Qty Available"},
                 "defaults":{"category":"Cut flower"},
                 "source-key-fields":["Legacy Key"]}
                """);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().path("message").asText())
                .contains("invalid row", "Row 2", "quantity");
        assertThat(inventoryService.findByNameIgnoreCase("Mapped Lisianthus")).isEmpty();

        String original = """
                {"record-type":"inventory","source-system":"legacy-excel-mapped",
                 "source-records":[
                  {"Legacy Key":"mapped-41","Crop Label":"Mapped Lisianthus",
                   "Qty Available":"80","Unit Price":"$1.95","UOM":"stem"}],
                 "field-mapping":{"name":"Crop Label","quantity":"Qty Available"},
                 "defaults":{"category":"Cut flower"},
                 "source-key-fields":["Legacy Key"]}
                """;
        JsonNode imported = command(
                "mapped-import-1", "import-mapped-records", original);
        long itemId = imported.path("output").path("records").get(0).path("id").asLong();
        assertThat(imported.path("output").path("targetAction").asText())
                .isEqualTo("upsert-inventory");
        assertThat(imported.path("output").path("created").asInt()).isEqualTo(1);
        assertThat(inventoryService.findById(itemId).orElseThrow().getQuantity())
                .isEqualTo(80);

        JsonNode corrected = command(
                "mapped-import-2", "import-mapped-records", """
                {"record-type":"inventory","source-system":"legacy-excel-mapped",
                 "source-records":[
                  {"Legacy Key":"mapped-41","Crop Label":"Mapped Lisianthus",
                   "Qty Available":"95","Unit Price":"$2.15","UOM":"stem"}],
                 "field-mapping":{"name":"Crop Label","quantity":"Qty Available"},
                 "defaults":{"category":"Cut flower"},
                 "source-key-fields":["Legacy Key"]}
                """);
        assertThat(corrected.path("output").path("updated").asInt()).isEqualTo(1);
        assertThat(corrected.path("output").path("records").get(0).path("id").asLong())
                .isEqualTo(itemId);
        assertThat(inventoryService.findById(itemId).orElseThrow().getQuantity())
                .isEqualTo(95);
    }

    @Test
    @DisplayName("rejects fractional canonical counts before writing any batch row")
    void rejectsFractionalCanonicalCounts() {
        ResponseEntity<JsonNode> rejected = commandResponse(
                "fractional-canonical-count", "upsert-inventory", """
                {"records":[{"name":"Schema Batch First","quantity":5},
                 {"name":"Schema Fractional Count","quantity":1.75}]}
                """);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().path("message").asText()).contains("quantity");
        assertThat(inventoryService.findByNameIgnoreCase("Schema Batch First")).isEmpty();
        assertThat(inventoryService.findByNameIgnoreCase("Schema Fractional Count")).isEmpty();
    }

    @Test
    @DisplayName("requires labor minutes instead of completing a task with an invented zero")
    void rejectsMissingRequiredTaskMinutes() {
        ProductionTask task = productionRepository.saveTask(new ProductionTask(
                null, null, java.time.LocalDate.of(2024, 3, 1),
                ProductionTaskType.CUSTOM, ProductionTaskStatus.TODO,
                "Schema Test Crop", "Schema Bed", 1, false, "Record actual labor"));

        ResponseEntity<JsonNode> rejected = commandResponse(
                "missing-task-minutes", "complete-tasks",
                "{\"records\":[{\"taskId\":" + task.getId() + "}]}");

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().path("message").asText()).contains("actualMinutes");
        ProductionTask unchanged = productionStudioService.findTask(task.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ProductionTaskStatus.TODO);
        assertThat(unchanged.getActualMinutes()).isNull();
        assertThat(unchanged.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("rolls back prior farm writes and import receipts after a later domain failure")
    void rollsBackBatchAndReceiptsOnDomainFailure() {
        ResponseEntity<JsonNode> rejected = commandResponse(
                "atomic-domain-failure", "upsert-inventory", """
                {"source-system":"atomic-domain-test","records":[
                 {"sourceKey":"first","name":"Atomic First Crop","quantity":5},
                 {"sourceKey":"second","name":"Atomic Invalid Crop","quantity":-1}]}
                """);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(inventoryService.findByNameIgnoreCase("Atomic First Crop")).isEmpty();
        assertThat(inventoryService.findByNameIgnoreCase("Atomic Invalid Crop")).isEmpty();
        JsonNode receipts = command("atomic-receipt-check", "list-import-receipts", """
                {"source-system":"atomic-domain-test","record-type":"inventory"}
                """);
        assertThat(receipts.path("output").path("total").asLong()).isZero();
    }

    @Test
    @DisplayName("previews and commits plantings through AI while rejecting stale or duplicate work")
    void plansAndReconcilesProductionThroughHarness() {
        command("planning-settings", "upsert-season-settings", """
                {"season-year":2037,"last-spring-frost":"2037-04-15",
                 "first-fall-frost":"2037-10-15"}
                """);
        JsonNode template = command("planning-template", "upsert-growing-templates", """
                {"records":[{"name":"Harness field program","cropName":"Harness Zinnia",
                 "spacingInches":9,"rowsPerBed":2,"sowDaysFromFrost":0,
                 "daysToHarvest":70,"harvestWindowDays":21,"successionDays":14,
                 "stemsPerPlant":2,"germinationPercent":90,
                 "soilPrepLeadDays":7,"supportDaysAfterPlanting":14}]}
                """);
        long templateId = template.path("output").path("records").get(0).path("id").asLong();
        String arguments = """
                {"template-id":%d,"season-year":2037,"bed-or-field":"Harness Field",
                 "bed-feet":20,"successions":2}
                """.formatted(templateId).trim();
        JsonNode preview = command("planning-preview", "preview-plantings", arguments);
        assertThat(preview.path("output").path("drafts")).hasSize(2);
        assertThat(preview.path("output").path("committed").asBoolean()).isFalse();
        assertThat(seasonPlanService.listForSeason(2037)).isEmpty();
        String fingerprint = preview.path("output").path("previewFingerprint").asText();
        assertThat(fingerprint).hasSize(64);
        assertThat(commandResponse("planning-no-review", "commit-plantings", arguments)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        command("planning-changed-frost", "upsert-season-settings", """
                {"season-year":2037,"last-spring-frost":"2037-04-20",
                 "first-fall-frost":"2037-10-15"}
                """);
        String stale = arguments.substring(0, arguments.length() - 1)
                + ",\"preview-fingerprint\":\"" + fingerprint + "\"}";
        assertThat(commandResponse("planning-stale", "commit-plantings", stale)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(seasonPlanService.listForSeason(2037)).isEmpty();

        fingerprint = command("planning-refreshed", "preview-plantings", arguments)
                .path("output").path("previewFingerprint").asText();
        String reviewed = arguments.substring(0, arguments.length() - 1)
                + ",\"preview-fingerprint\":\"" + fingerprint + "\"}";
        JsonNode committed = command("planning-commit", "commit-plantings", reviewed);
        assertThat(committed.path("output").path("plantings")).hasSize(2);
        assertThat(command("planning-commit", "commit-plantings", reviewed)
                .path("replayed").asBoolean()).isTrue();
        assertThat(seasonPlanService.listForSeason(2037)).hasSize(2);
        assertThat(commandResponse("planning-duplicate", "commit-plantings", reviewed)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode planting = committed.path("output").path("plantings").get(0);
        long planId = planting.path("plan").path("id").asLong();
        long taskId = planting.path("tasks").get(0).path("id").asLong();
        command("planning-actuals", "complete-tasks", """
                {"records":[{"taskId":%d,"actualMinutes":25,"inputCost":3,
                 "fieldObservation":"Crew completed soil prep"}]}
                """.formatted(taskId));
        JsonNode reopened = command("planning-reopen", "update-task-status", """
                {"task-id":%d,"status":"TODO"}
                """.formatted(taskId));
        assertThat(reopened.path("output").path("task").path("actualMinutes").asInt())
                .isEqualTo(25);
        command("planning-regenerate", "regenerate-tasks", """
                {"plan-id":%d,"template-id":%d}
                """.formatted(planId, templateId));
        assertThat(productionRepository.findTasksForPlan(planId))
                .anySatisfy(task -> {
                    assertThat(task.getId()).isEqualTo(taskId);
                    assertThat(task.getActualMinutes()).isEqualTo(25);
                });
        assertThat(commandResponse("planning-invalid-status", "update-task-status", """
                {"task-id":%d,"status":"FINISHED"}
                """.formatted(taskId)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("estimates bouquets and records local handoffs without consuming or publishing stock")
    void estimatesAndRecordsHandoffsThroughHarness() {
        command("estimate-stock", "upsert-inventory", """
                {"records":[{"name":"Harness Focal","quantity":30,"unit":"stem",
                 "price":3,"cost":1}]}
                """);
        JsonNode recipe = command("estimate-recipe-create", "upsert-floral-recipes", """
                {"records":[{"name":"Harness Friday Bouquet","designType":"Bouquet",
                 "salePrice":30,"laborMinutes":5,"laborRatePerHour":24,
                 "overheadPercent":10,"wastePercent":5,"ingredients":[
                 {"itemName":"Harness Focal","stemsPerDesign":3,"packSize":10,"packCost":12}]}]}
                """);
        long recipeId = recipe.path("output").path("records").get(0).path("id").asLong();
        JsonNode estimate = command("estimate-requirements", "estimate-recipe", """
                {"recipe-id":%d,"count":4,"event-date":"2037-07-03"}
                """.formatted(recipeId));
        assertThat(estimate.path("output").path("estimate").path("designCount").asInt()).isEqualTo(4);
        assertThat(estimate.path("output").path("estimate").path("totalCost").asDouble())
                .isPositive();
        assertThat(estimate.path("output").path("stockReserved").asBoolean()).isFalse();
        JsonNode available = command("estimate-availability", "get-availability", """
                {"week-of":"2037-07-03","limit":1}
                """);
        assertThat(available.path("output").path("availability")).hasSize(1);
        assertThat(available.path("output").path("nextOffset").asInt()).isEqualTo(1);
        assertThat(inventoryService.findByNameIgnoreCase("Harness Focal").orElseThrow().getQuantity())
                .isEqualTo(30);
        String handoff = """
                {"handoff-type":"MARKETPLACE","provider":"Grower selected provider",
                 "status":"DRAFT","related-recipe-id":%d,
                 "summary":"Four Friday bouquets; confirm actual stock before listing."}
                """.formatted(recipeId);
        JsonNode created = command("estimate-handoff", "create-handoff", handoff);
        assertThat(created.path("output").path("externalActionPerformed").asBoolean()).isFalse();
        long handoffId = created.path("output").path("handoff").path("id").asLong();
        assertThat(command("estimate-handoff", "create-handoff", handoff)
                .path("replayed").asBoolean()).isTrue();
        JsonNode listed = command("estimate-handoff-list", "list-handoffs", "{\"limit\":200}");
        assertThat(listed.path("output").path("handoffs").findValuesAsText("id"))
                .contains(Long.toString(handoffId));
        assertThat(commandResponse("estimate-invalid-date", "get-availability",
                "{\"week-of\":\"2037-02-30\"}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(commandResponse("estimate-invalid-limit", "list-handoffs",
                "{\"limit\":201}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private JsonNode command(String requestId, String action, String arguments) {
        ResponseEntity<JsonNode> response = commandResponse(requestId, action, arguments);
        assertThat(response.getStatusCode())
                .as("Harness response: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<JsonNode> commandResponse(
            String requestId, String action, String arguments) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HarnessController.TOKEN_HEADER, TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"requestId\":\"" + requestId + "\",\"action\":\""
                + action + "\",\"arguments\":" + arguments + "}";
        return rest.exchange(
                "http://127.0.0.1:" + port + "/api/harness/farm-data/commands",
                HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
