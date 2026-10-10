package com.flowerfarm;

import com.flowerfarm.testsupport.RestCsrfSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.repository.ProductionTaskJpaRepository;
import com.flowerfarm.repository.FieldAttachmentJpaRepository;
import com.flowerfarm.repository.WorkerActionReceiptJpaRepository;
import com.flowerfarm.repository.KnowledgeJpaRepository;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.FieldAttachmentService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.SeasonPlanService;
import com.flowerfarm.service.WorkerWorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

@AutoConfigureTestRestTemplate
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.headless=true",
                "spring.datasource.url=jdbc:h2:mem:worker-workflow;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=validate",
                "flowerfarm.inventory.seed-csv=target/no-worker-workflow-seed.csv",
                "flowerfarm.irrigation.live-weather=false",
                "flowerfarm.harness.enabled=true",
                "flowerfarm.harness.token=worker-workflow-harness-token-123456",
                "flowerfarm.harness.allow-sensitive-read=true",
                "flowerfarm.harness.allow-mutations=true",
                "flowerfarm.attachments.directory=target/test-data/worker-attachments",
                "flowerfarm.profile.time-zone=Pacific/Kiritimati"
        })
@DisplayName("Phone and tablet field workflow")
class WorkerWorkflowIntegrationTest {

    private static final String HARNESS_TOKEN =
            "worker-workflow-harness-token-123456";

    @LocalServerPort int port;

    @Autowired TestRestTemplate rest;
    @Autowired InventoryService inventoryService;
    @Autowired KnowledgeService knowledgeService;
    @Autowired KnowledgeJpaRepository knowledge;
    @Autowired HarvestService harvestService;
    @Autowired CustomerService customerService;
    @Autowired OrderService orderService;
    @Autowired ProductionStudioService productionService;
    @Autowired SeasonPlanService seasonPlanService;
    @Autowired FieldAttachmentService attachmentService;
    @Autowired FarmProfile farmProfile;
    @Autowired ProductionTaskJpaRepository tasks;
    @Autowired WorkerActionReceiptJpaRepository receipts;
    @Autowired FieldAttachmentJpaRepository attachments;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("a lost harvest response replays once and the AI can read the result")
    void logsHarvestOnceAcrossRetryAndAiReadback() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String crop = "Mobile Dahlia " + suffix;
        String key = "mobile-harvest-" + suffix;
        String lotCode = "MOB-" + suffix.toUpperCase();
        String body = """
                {"operationKey":"%s","harvestDate":"%s",
                  "cropName":"%s","quantity":12,"unit":"stems",
                  "bedOrField":"Mobile Bed","notes":"Cut from phone",
                  "lotCode":"%s","grade":"SELECT","lossQuantity":3,
                  "storageLocation":"Cooler 2 / Rack A"}
                """.formatted(key, farmToday(), crop, lotCode);
        long receiptCount = receipts.count();

        ResponseEntity<JsonNode> created = post("/api/worker/harvests", body);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().path("replayed").asBoolean()).isFalse();
        long harvestId = created.getBody().path("result").path("id").asLong();
        assertThat(harvestId).isPositive();
        JsonNode createdHarvest = created.getBody().path("result");
        assertThat(createdHarvest.path("lotCode").asText()).isEqualTo(lotCode);
        assertThat(createdHarvest.path("grade").asText()).isEqualTo("SELECT");
        assertThat(createdHarvest.path("lossQuantity").asDouble()).isEqualTo(3);
        assertThat(createdHarvest.path("grossQuantity").asDouble()).isEqualTo(15);
        assertThat(createdHarvest.path("storageLocation").asText())
                .isEqualTo("Cooler 2 / Rack A");
        assertThat(inventoryService.findByNameIgnoreCase(crop).orElseThrow().getQuantity())
                .isEqualTo(12);

        ResponseEntity<JsonNode> replay = post("/api/worker/harvests", body);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().path("replayed").asBoolean()).isTrue();
        assertThat(replay.getBody().path("result").path("id").asLong())
                .isEqualTo(harvestId);
        assertThat(inventoryService.findByNameIgnoreCase(crop).orElseThrow().getQuantity())
                .isEqualTo(12);
        assertThat(receipts.count()).isEqualTo(receiptCount + 1);
        assertThat(receipts.findAll().stream()
                .noneMatch(receipt -> key.equals(receipt.getOperationKeyHash()))).isTrue();

        WorkerWorkflowService restarted = new WorkerWorkflowService(
                harvestService, productionService, inventoryService, orderService,
                attachmentService, knowledgeService, farmProfile, receipts, mapper,
                transactionManager);
        WorkerWorkflowService.WorkerActionResult recovered = restarted.recover(key);
        assertThat(recovered.replayed()).isTrue();
        assertThat(recovered.result().path("id").asLong()).isEqualTo(harvestId);

        ResponseEntity<JsonNode> changed = post("/api/worker/harvests", body.replace(
                "\"quantity\":12", "\"quantity\":14"));
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changed.getBody().path("code").asText())
                .isEqualTo("WORKER_ACTION_CONFLICT");
        assertThat(inventoryService.findByNameIgnoreCase(crop).orElseThrow().getQuantity())
                .isEqualTo(12);

        String duplicateLotBody = body.replace(
                "\"operationKey\":\"" + key + "\"",
                "\"operationKey\":\"duplicate-lot-" + suffix + "\"");
        ResponseEntity<JsonNode> duplicateLot = post(
                "/api/worker/harvests", duplicateLotBody);
        assertThat(duplicateLot.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicateLot.getBody().path("code").asText())
                .isEqualTo("INVALID_REQUEST");
        assertThat(duplicateLot.getBody().path("error").asText())
                .contains("Harvest lot code already exists");
        assertThat(inventoryService.findByNameIgnoreCase(crop).orElseThrow().getQuantity())
                .isEqualTo(12);
        assertThat(receipts.count()).isEqualTo(receiptCount + 1);

        JsonNode farmData = farmDataCommand(
                "worker-ai-harvest-" + suffix,
                "list-records",
                "{\"record-type\":\"harvests\",\"offset\":0,\"limit\":200}");
        assertThat(farmData.path("output").path("records")
                .findValuesAsText("cropName")).contains(crop);

        JsonNode lotLookup = farmDataCommand(
                "worker-ai-lot-" + suffix,
                "lookup-harvest-lot",
                "{\"lot-code\":\"" + lotCode.toLowerCase() + "\"}");
        assertThat(lotLookup.path("output").path("harvest").path("id").asLong())
                .isEqualTo(harvestId);
        assertThat(lotLookup.path("output").path("harvest").path("lossQuantity").asDouble())
                .isEqualTo(3);
        assertThat(lotLookup.path("output").path("labelPath").asText())
                .isEqualTo("/api/harvest/" + harvestId + "/label.pdf");

        ResponseEntity<byte[]> label = rest.getForEntity(
                url("/api/harvest/" + harvestId + "/label.pdf"), byte[].class);
        assertThat(label.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(label.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(label.getBody()).startsWith('%', 'P', 'D', 'F').hasSizeGreaterThan(1_000);
    }

    @Test
    @DisplayName("due work can be completed once and disappears from the field list")
    void completesDueTaskOnce() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = farmToday();
        ProductionTask task = tasks.saveAndFlush(new ProductionTask(
                null, null, today.minusDays(1), ProductionTaskType.HARVEST_PREP,
                ProductionTaskStatus.TODO, "Mobile Zinnia " + suffix,
                "Field 7", 1, false, "Bring clean buckets"));

        ResponseEntity<JsonNode> before = rest.getForEntity(
                url("/api/worker/today?date=" + today), JsonNode.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(before.getBody().path("tasks").findValuesAsText("id"))
                .contains(task.getId().toString());

        String key = "mobile-task-" + suffix;
        String body = """
                {"operationKey":"%s","actualMinutes":37,"inputCost":4.25,
                 "fieldObservation":"Buckets staged at wash station"}
                """.formatted(key);
        String path = "/api/worker/tasks/" + task.getId() + "/complete";
        ResponseEntity<JsonNode> created = post(path, body);
        ResponseEntity<JsonNode> replayed = post(path, body);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().path("result").path("status").asText())
                .isEqualTo("DONE");
        assertThat(created.getBody().path("result").path("actualMinutes").asInt())
                .isEqualTo(37);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayed.getBody().path("replayed").asBoolean()).isTrue();
        assertThat(replayed.getBody().path("recordedAt").asText())
                .isEqualTo(created.getBody().path("recordedAt").asText());

        ResponseEntity<JsonNode> recovered = rest.getForEntity(
                url("/api/worker/operations/" + key), JsonNode.class);
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recovered.getBody().path("result").path("id").asLong())
                .isEqualTo(task.getId());

        ResponseEntity<JsonNode> after = rest.getForEntity(
                url("/api/worker/today?date=" + today), JsonNode.class);
        assertThat(after.getBody().path("tasks").findValuesAsText("id"))
                .doesNotContain(task.getId().toString());

        JsonNode farmData = farmDataCommand(
                "worker-ai-task-" + suffix,
                "list-records",
                "{\"record-type\":\"production-tasks\",\"offset\":0,\"limit\":200}");
        JsonNode saved = findById(farmData.path("output").path("records"), task.getId());
        assertThat(saved.path("status").asText()).isEqualTo("DONE");
        assertThat(saved.path("fieldObservation").asText())
                .isEqualTo("Buckets staged at wash station");
    }

    @Test
    @DisplayName("a reviewed stock count is retry-safe, conflict-safe, and AI-readable")
    void countsInventoryAcrossMobileAndAiBoundaries() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Item item = inventoryService.addItem(new Item(
                "Counted Snapdragon " + suffix, "Flowers/Plants", 3.0,
                "stems", 0.8, 18, "Cooler A"));

        ResponseEntity<JsonNode> listing = rest.getForEntity(
                url("/api/worker/inventory?query=" + suffix + "&limit=10"),
                JsonNode.class);
        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listing.getBody().path("matchingTotal").asLong()).isEqualTo(1);
        JsonNode listed = listing.getBody().path("items").get(0);
        assertThat(listed.path("id").asLong()).isEqualTo(item.getId());
        assertThat(listed.path("quantity").asInt()).isEqualTo(18);
        assertThat(listed.has("cost")).isFalse();
        assertThat(listed.has("notes")).isFalse();
        ResponseEntity<JsonNode> bounded = rest.getForEntity(
                url("/api/worker/inventory?limit=1"), JsonNode.class);
        assertThat(bounded.getBody().path("items")).hasSize(1);
        assertThat(bounded.getBody().path("matchingTotal").asLong()).isGreaterThan(1);
        assertThat(bounded.getBody().path("hasMore").asBoolean()).isTrue();
        assertThat(rest.getForEntity(
                url("/api/worker/inventory?limit=101"), JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        String key = "mobile-count-" + suffix;
        String body = """
                {"operationKey":"%s","reviewedQuantity":18,"countedQuantity":11}
                """.formatted(key);
        String path = "/api/worker/inventory/" + item.getId() + "/count";
        long receiptCount = receipts.count();

        ResponseEntity<JsonNode> created = post(path, body);
        ResponseEntity<JsonNode> replayed = post(path, body);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().path("action").asText())
                .isEqualTo("COUNT_INVENTORY");
        assertThat(created.getBody().path("result").path("quantity").asInt())
                .isEqualTo(11);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayed.getBody().path("replayed").asBoolean()).isTrue();
        assertThat(inventoryService.findById(item.getId()).orElseThrow().getQuantity())
                .isEqualTo(11);
        assertThat(receipts.count()).isEqualTo(receiptCount + 1);

        WorkerWorkflowService restarted = new WorkerWorkflowService(
                harvestService, productionService, inventoryService, orderService,
                attachmentService, knowledgeService, farmProfile, receipts, mapper, transactionManager);
        WorkerWorkflowService.WorkerActionResult recovered = restarted.recover(key);
        assertThat(recovered.replayed()).isTrue();
        assertThat(recovered.result().path("quantity").asInt()).isEqualTo(11);

        ResponseEntity<JsonNode> changedReuse = post(
                path, body.replace("\"countedQuantity\":11", "\"countedQuantity\":9"));
        assertThat(changedReuse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changedReuse.getBody().path("code").asText())
                .isEqualTo("WORKER_ACTION_CONFLICT");

        JsonNode farmData = farmDataCommand(
                "worker-ai-count-" + suffix,
                "list-records",
                "{\"record-type\":\"inventory\",\"offset\":0,\"limit\":200}");
        JsonNode saved = findById(
                farmData.path("output").path("records"), item.getId());
        assertThat(saved.path("quantity").asInt()).isEqualTo(11);

        inventoryService.recordPhysicalCount(item.getId(), 11, 13);
        long beforeStaleReceipt = receipts.count();
        ResponseEntity<JsonNode> stale = post(path, """
                {"operationKey":"mobile-stale-%s",
                 "reviewedQuantity":11,"countedQuantity":6}
                """.formatted(suffix));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().path("code").asText())
                .isEqualTo("INVENTORY_COUNT_CONFLICT");
        assertThat(stale.getBody().path("currentQuantity").asInt()).isEqualTo(13);
        assertThat(receipts.count()).isEqualTo(beforeStaleReceipt);
        assertThat(inventoryService.findById(item.getId()).orElseThrow().getQuantity())
                .isEqualTo(13);
    }

    @Test
    @DisplayName("a due order handoff is exact, retry-safe, stock-safe, and AI-readable")
    void completesDueOrderHandoffAcrossMobileAndAiBoundaries() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = farmToday();
        String product = "Handoff Ranunculus " + suffix;
        Item stock = inventoryService.addItem(new Item(
                product, "Flowers/Plants", 5.0, "stems", 1.2, 20, "Cooler B"));
        Customer customer = customerService.add(new Customer(
                "Mobile Florist " + suffix, "Alex", "alex@example.test", "555-0198",
                "FLORIST", "Use the south loading door"));
        CustomerOrder order = orderService.create(
                customer.getId(), today, "CONFIRMED", "Call five minutes before delivery",
                "DELIVERY", java.util.List.of(new OrderLine(product, 6, "stems", 4.5)));
        CustomerOrder future = orderService.create(
                customer.getId(), today.plusDays(1), "CONFIRMED", "Tomorrow only",
                "PICKUP", java.util.List.of(new OrderLine(product, 1, "stems", 4.5)));

        ResponseEntity<JsonNode> listing = rest.getForEntity(
                url("/api/worker/orders?through=" + today + "&limit=10"), JsonNode.class);
        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listed = findById(listing.getBody().path("orders"), order.getId());
        assertThat(listing.getBody().path("orders").findValuesAsText("id"))
                .doesNotContain(future.getId().toString());
        assertThat(listed.path("customerName").asText()).isEqualTo(customer.getName());
        assertThat(listed.path("contactName").asText()).isEqualTo("Alex");
        assertThat(listed.path("customerNotes").asText())
                .isEqualTo("Use the south loading door");
        assertThat(listed.path("instructions").asText())
                .isEqualTo("Call five minutes before delivery");
        assertThat(listed.path("lines").get(0).path("productName").asText())
                .isEqualTo(product);
        assertThat(listed.path("lines").get(0).has("unitPrice")).isFalse();
        String reviewToken = listed.path("reviewToken").asText();
        assertThat(reviewToken).matches("[0-9a-f]{64}");
        assertThat(rest.getForEntity(
                url("/api/worker/orders?through=" + today + "&limit=101"), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String key = "mobile-order-" + suffix;
        String body = """
                {"operationKey":"%s","reviewToken":"%s"}
                """.formatted(key, reviewToken);
        String path = "/api/worker/orders/" + order.getId() + "/complete";
        long receiptCount = receipts.count();

        ResponseEntity<JsonNode> created = post(path, body);
        ResponseEntity<JsonNode> replayed = post(path, body);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().path("action").asText()).isEqualTo("COMPLETE_ORDER");
        assertThat(created.getBody().path("result").path("status").asText())
                .isEqualTo("FULFILLED");
        assertThat(created.getBody().path("result").path("alreadyFulfilled").asBoolean())
                .isFalse();
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayed.getBody().path("replayed").asBoolean()).isTrue();
        assertThat(inventoryService.findById(stock.getId()).orElseThrow().getQuantity())
                .isEqualTo(14);
        assertThat(receipts.count()).isEqualTo(receiptCount + 1);

        WorkerWorkflowService restarted = new WorkerWorkflowService(
                harvestService, productionService, inventoryService, orderService,
                attachmentService, knowledgeService, farmProfile, receipts, mapper, transactionManager);
        assertThat(restarted.recover(key).result().path("status").asText())
                .isEqualTo("FULFILLED");

        ResponseEntity<JsonNode> changedReuse = post(path, body.replace(
                reviewToken, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertThat(changedReuse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changedReuse.getBody().path("code").asText())
                .isEqualTo("WORKER_ACTION_CONFLICT");
        assertThat(inventoryService.findById(stock.getId()).orElseThrow().getQuantity())
                .isEqualTo(14);

        String secondKey = "mobile-order-already-" + suffix;
        ResponseEntity<JsonNode> alreadyCompleted = post(path, body.replace(key, secondKey));
        assertThat(alreadyCompleted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(alreadyCompleted.getBody().path("result")
                .path("alreadyFulfilled").asBoolean()).isTrue();
        assertThat(inventoryService.findById(stock.getId()).orElseThrow().getQuantity())
                .isEqualTo(14);

        JsonNode farmData = farmDataCommand(
                "worker-ai-order-" + suffix,
                "list-records",
                "{\"record-type\":\"orders\",\"offset\":0,\"limit\":200}");
        assertThat(findById(farmData.path("output").path("records"), order.getId())
                .path("status").asText()).isEqualTo("FULFILLED");

        String staleProduct = "Stale Handoff Stock " + suffix;
        Item staleStock = inventoryService.addItem(new Item(
                staleProduct, "Flowers/Plants", 3.0, "stems", 0.8, 12, "Cooler C"));
        CustomerOrder staleOrder = orderService.create(
                customer.getId(), today, "CONFIRMED", "Original gate",
                "DELIVERY", java.util.List.of(new OrderLine(staleProduct, 4, "stems", 3)));
        JsonNode staleListed = findById(rest.getForEntity(
                url("/api/worker/orders?through=" + today + "&limit=100"), JsonNode.class)
                .getBody().path("orders"), staleOrder.getId());
        String staleToken = staleListed.path("reviewToken").asText();
        orderService.updateNotes(staleOrder.getId(), "Changed gate after mobile review");
        long beforeConflict = receipts.count();

        ResponseEntity<JsonNode> conflict = post(
                "/api/worker/orders/" + staleOrder.getId() + "/complete", """
                {"operationKey":"mobile-order-stale-%s","reviewToken":"%s"}
                """.formatted(suffix, staleToken));

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().path("code").asText())
                .isEqualTo("ORDER_HANDOFF_CONFLICT");
        assertThat(conflict.getBody().path("orderId").asLong()).isEqualTo(staleOrder.getId());
        assertThat(receipts.count()).isEqualTo(beforeConflict);
        assertThat(inventoryService.findById(staleStock.getId()).orElseThrow().getQuantity())
                .isEqualTo(12);
        assertThat(orderService.findById(staleOrder.getId()).orElseThrow().getStatus())
                .isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("a task photo survives retry and is readable by phone and AI")
    void storesTaskPhotoAcrossMobileAndAiBoundaries() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ProductionTask task = tasks.saveAndFlush(new ProductionTask(
                null, null, farmToday(), ProductionTaskType.CUSTOM,
                ProductionTaskStatus.TODO, "Photo Zinnia " + suffix,
                "North Bed", 1, false, "Photograph the leaf undersides"));
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        String key = "mobile-photo-" + suffix;
        long receiptCount = receipts.count();
        long attachmentCount = attachments.count();

        ResponseEntity<JsonNode> created = postAttachment(
                key, "PRODUCTION_TASK", task.getId(), "north-bed.png", "image/png",
                "Aphids below threshold", png);
        ResponseEntity<JsonNode> replayed = postAttachment(
                key, "PRODUCTION_TASK", task.getId(), "north-bed.png", "image/png",
                "Aphids below threshold", png);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().path("action").asText()).isEqualTo("ADD_ATTACHMENT");
        JsonNode saved = created.getBody().path("result");
        long attachmentId = saved.path("id").asLong();
        assertThat(attachmentId).isPositive();
        assertThat(saved.path("targetType").asText()).isEqualTo("PRODUCTION_TASK");
        assertThat(saved.path("targetRecordId").asLong()).isEqualTo(task.getId());
        assertThat(saved.path("originalFilename").asText()).isEqualTo("north-bed.png");
        assertThat(saved.path("byteSize").asLong()).isEqualTo(png.length);
        assertThat(saved.path("sha256").asText()).hasSize(64);
        assertThat(saved.has("storageKey")).isFalse();
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayed.getBody().path("replayed").asBoolean()).isTrue();
        assertThat(attachments.count()).isEqualTo(attachmentCount + 1);
        assertThat(receipts.count()).isEqualTo(receiptCount + 1);

        WorkerWorkflowService restarted = new WorkerWorkflowService(
                harvestService, productionService, inventoryService, orderService,
                attachmentService, knowledgeService, farmProfile, receipts, mapper, transactionManager);
        assertThat(restarted.recover(key).result().path("id").asLong())
                .isEqualTo(attachmentId);

        ResponseEntity<JsonNode> changedReuse = postAttachment(
                key, "PRODUCTION_TASK", task.getId(), "north-bed.png", "image/png",
                "Changed caption", png);
        assertThat(changedReuse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(changedReuse.getBody().path("code").asText())
                .isEqualTo("WORKER_ACTION_CONFLICT");
        assertThat(attachments.count()).isEqualTo(attachmentCount + 1);

        ResponseEntity<JsonNode> targetListing = rest.getForEntity(url(
                "/api/worker/attachments?targetType=PRODUCTION_TASK&targetId="
                        + task.getId()), JsonNode.class);
        assertThat(targetListing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(targetListing.getBody()).hasSize(1);
        assertThat(targetListing.getBody().get(0).path("id").asLong())
                .isEqualTo(attachmentId);

        ResponseEntity<byte[]> content = rest.getForEntity(
                url("/api/worker/attachments/" + attachmentId + "/content"), byte[].class);
        assertThat(content.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(content.getHeaders().getFirst("X-Content-SHA256"))
                .isEqualTo(saved.path("sha256").asText());
        assertThat(content.getBody()).containsExactly(png);

        JsonNode listedByAi = farmDataCommand(
                "worker-ai-photo-list-" + suffix, "list-records",
                "{\"record-type\":\"attachments\",\"offset\":0,\"limit\":200}");
        JsonNode aiMetadata = findById(
                listedByAi.path("output").path("records"), attachmentId);
        assertThat(aiMetadata.path("caption").asText())
                .isEqualTo("Aphids below threshold");
        assertThat(aiMetadata.has("storageKey")).isFalse();

        JsonNode aiContent = farmDataCommand(
                "worker-ai-photo-read-" + suffix, "read-attachment-content",
                "{\"attachment-id\":" + attachmentId
                        + ",\"offset\":0,\"max-bytes\":16}");
        assertThat(aiContent.path("output").path("byteCount").asInt()).isEqualTo(16);
        assertThat(aiContent.path("output").path("hasMore").asBoolean()).isTrue();
        int nextOffset = aiContent.path("output").path("nextOffset").asInt();
        assertThat(nextOffset).isEqualTo(16);

        String aiKey = "ai-photo-" + suffix;
        String aiArguments = mapper.writeValueAsString(Map.of(
                "operation-key", aiKey,
                "target-type", "PRODUCTION_TASK",
                "target-id", task.getId(),
                "filename", "ai-copy.png",
                "media-type", "image/png",
                "caption", "Uploaded by connected farm AI",
                "captured-at", Instant.parse("2026-09-02T15:30:00Z").toString(),
                "content-base64", Base64.getEncoder().encodeToString(png)));
        JsonNode aiUpload = farmDataCommand(
                "worker-ai-photo-upload-" + suffix, "upload-attachment", aiArguments);
        JsonNode aiReplay = farmDataCommand(
                "worker-ai-photo-upload-retry-" + suffix,
                "upload-attachment", aiArguments);
        assertThat(aiUpload.path("output").path("operationReplayed").asBoolean()).isFalse();
        assertThat(aiUpload.path("output").path("attachment").path("id").asLong())
                .isPositive().isNotEqualTo(attachmentId);
        assertThat(aiReplay.path("output").path("operationReplayed").asBoolean()).isTrue();
        assertThat(attachments.count()).isEqualTo(attachmentCount + 2);

        long receiptsBeforeRejected = receipts.count();
        ResponseEntity<JsonNode> disguised = postAttachment(
                "mobile-photo-disguised-" + suffix,
                "PRODUCTION_TASK", task.getId(), "not-jpeg.jpg", "image/jpeg",
                "", png);
        assertThat(disguised.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(receipts.count()).isEqualTo(receiptsBeforeRejected);

        byte[] oversized = new byte[FieldAttachmentService.MAX_ATTACHMENT_BYTES + 1];
        ResponseEntity<JsonNode> tooLarge = postAttachment(
                "mobile-photo-too-large-" + suffix,
                "PRODUCTION_TASK", task.getId(), "too-large.png", "image/png",
                "", oversized);
        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(tooLarge.getBody().path("code").asText())
                .isEqualTo("ATTACHMENT_TOO_LARGE");
        assertThat(receipts.count()).isEqualTo(receiptsBeforeRejected);
    }

    @Test
    @DisplayName("task regeneration retains open work that already owns a field photo")
    void retainsAttachedTaskDuringRegeneration() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate sowDate = farmToday();
        GrowingTemplate template = productionService.createTemplate(new GrowingTemplate(
                "Evidence program " + suffix, "Evidence Zinnia " + suffix,
                "Field Mix", 9, 4, 0, 7, 60, 21, 14,
                3, 85, 0, 10, true, "Regeneration evidence test"));
        SeasonPlanEntry plan = seasonPlanService.create(new SeasonPlanEntry(
                sowDate.getYear(), template.getCropName(), template.getVariety(),
                "Evidence Bed " + suffix, sowDate, sowDate.plusDays(7),
                sowDate.plusDays(60), sowDate.plusDays(80),
                20, 200, SeasonPlanStatus.PLANNED, "Regeneration evidence test"));
        ProductionTask task = tasks.saveAndFlush(new ProductionTask(
                plan.getId(), template.getId(), sowDate, ProductionTaskType.SOW,
                ProductionTaskStatus.TODO, template.getCropName(), plan.getBedOrField(),
                1, true, "Sow"));
        attachmentService.add(
                "PRODUCTION_TASK", task.getId(), "evidence.png", "image/png",
                "Retain this task", Instant.parse("2026-09-02T15:00:00Z"),
                Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));

        var regenerated = productionService.regenerateTasks(plan.getId(), template.getId());

        assertThat(tasks.findById(task.getId())).isPresent();
        assertThat(regenerated).extracting(ProductionTask::getTaskType)
                .doesNotContain(ProductionTaskType.SOW);
        assertThat(attachments.existsByTargetTypeAndTargetRecordId(
                com.flowerfarm.model.FieldAttachmentTargetType.PRODUCTION_TASK,
                task.getId())).isTrue();
    }

    @Test
    @DisplayName("defaults the work list date to the configured farm time zone")
    void defaultsTodayToFarmTimeZone() {
        ResponseEntity<JsonNode> response = rest.getForEntity(
                url("/api/worker/today"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("date").asText())
                .isEqualTo(LocalDate.now(farmProfile.zoneId()).toString());
    }

    @Test
    @DisplayName("a standalone field note survives retry and restart and reaches the Knowledge Log")
    void savesFieldNoteWithDurableReplayAndAiReadback() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String key = "field-note-" + suffix;
        String observation = "Leaning dahlia stems after rain " + suffix;
        String body = """
                {"operationKey":"%s","observedOn":"%s","kind":"PROBLEM",
                 "observation":"%s","cropName":"Dahlia","bedOrField":"Bed 4",
                 "actionTaken":"Added support netting","performedBy":"Field crew",
                 "followUpDate":"%s"}
                """.formatted(key, farmToday(), observation, farmToday().plusDays(2));
        long notesBefore = knowledge.count();
        long receiptsBefore = receipts.count();

        ResponseEntity<JsonNode> created = post("/api/worker/field-notes", body);
        assertThat(created.getStatusCode()).as("%s", created.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode saved = created.getBody().path("result");
        long noteId = saved.path("id").asLong();
        assertThat(saved.path("summary").asText()).isEqualTo(observation);
        assertThat(saved.path("observedOn").asText()).isEqualTo(farmToday().toString());
        assertThat(saved.path("kind").asText()).isEqualTo("PROBLEM");
        assertThat(saved.path("tags").toString()).contains("field-note");
        assertThat(knowledgeService.findById(noteId).orElseThrow().getActionTaken())
                .isEqualTo("Added support netting");

        ResponseEntity<JsonNode> retry = post("/api/worker/field-notes", body);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody().path("replayed").asBoolean()).isTrue();
        assertThat(retry.getBody().path("result")).isEqualTo(saved);
        WorkerWorkflowService restarted = new WorkerWorkflowService(
                harvestService, productionService, inventoryService, orderService,
                attachmentService, knowledgeService, farmProfile, receipts, mapper, transactionManager);
        assertThat(restarted.addFieldNote(mapper.readValue(
                body, WorkerWorkflowService.WorkerFieldNoteRequest.class)).result()).isEqualTo(saved);
        assertThat(restarted.recover(key).result()).isEqualTo(saved);
        assertThat(knowledge.count()).isEqualTo(notesBefore + 1);
        assertThat(receipts.count()).isEqualTo(receiptsBefore + 1);

        ResponseEntity<JsonNode> conflict = post("/api/worker/field-notes",
                body.replace("Added support netting", "Changed after saving"));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode recent = rest.getForObject(url("/api/worker/field-notes?limit=1"), JsonNode.class);
        assertThat(recent.path("through").asText()).isEqualTo(farmToday().toString());
        assertThat(recent.path("notes")).hasSize(1);
        assertThat(recent.path("notes").get(0).path("id").asLong()).isEqualTo(noteId);
        JsonNode readback = farmDataCommand("field-note-ai-" + suffix, "list-records",
                "{\"record-type\":\"knowledge\",\"limit\":200}");
        assertThat(readback.path("output").path("records").toString()).contains(observation);
    }

    @Test
    @DisplayName("invalid field notes cannot create records or receipts")
    void rejectsInvalidFieldNotes() {
        long notesBefore = knowledge.count();
        long receiptsBefore = receipts.count();
        String prefix = "invalid-note-" + UUID.randomUUID();
        for (String body : java.util.List.of(
                "{\"operationKey\":\"" + prefix + "-blank\",\"observedOn\":\"" + farmToday()
                        + "\",\"observation\":\"   \"}",
                "{\"operationKey\":\"" + prefix + "-future\",\"observedOn\":\""
                        + farmToday().plusDays(2) + "\",\"observation\":\"Future note\"}",
                "{\"operationKey\":\"" + prefix + "-followup\",\"observedOn\":\"" + farmToday()
                        + "\",\"observation\":\"Check support\",\"followUpDate\":\""
                        + farmToday().minusDays(1) + "\"}")) {
            assertThat(post("/api/worker/field-notes", body).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(rest.getForEntity(url("/api/worker/field-notes?limit=51"), JsonNode.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(knowledge.count()).isEqualTo(notesBefore);
        assertThat(receipts.count()).isEqualTo(receiptsBefore);
    }

    @Test
    @DisplayName("field note creation rolls back if its retry receipt cannot commit")
    void fieldNoteAndReceiptAreAtomic() {
        WorkerActionReceiptJpaRepository unavailable = mock(
                WorkerActionReceiptJpaRepository.class, delegatesTo(receipts));
        doThrow(new IllegalStateException("Receipt storage unavailable"))
                .when(unavailable).saveAndFlush(any());
        WorkerWorkflowService service = new WorkerWorkflowService(
                harvestService, productionService, inventoryService, orderService,
                attachmentService, knowledgeService, farmProfile, unavailable, mapper, transactionManager);
        long notesBefore = knowledge.count();

        assertThatThrownBy(() -> service.addFieldNote(
                new WorkerWorkflowService.WorkerFieldNoteRequest(
                        "atomic-note-" + UUID.randomUUID(), farmToday(), null, null,
                        "Dahlia", "Bed 4", "Support needs adjustment", null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Receipt storage unavailable");
        assertThat(knowledge.count()).isEqualTo(notesBefore);
    }

    @Test
    @DisplayName("serves the responsive field shell and its offline assets")
    void servesWorkerWebApp() {
        ResponseEntity<String> redirect = rest.getForEntity(url("/worker"), String.class);
        ResponseEntity<String> page = rest.getForEntity(url("/worker/"), String.class);
        ResponseEntity<String> script = rest.getForEntity(
                url("/worker/worker.js"), String.class);
        ResponseEntity<String> styles = rest.getForEntity(
                url("/worker/worker.css"), String.class);
        ResponseEntity<String> serviceWorker = rest.getForEntity(
                url("/worker/sw.js"), String.class);

        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(redirect.getBody()).contains("Field Work", "Log a harvest");
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getHeaders().getContentType()).isNotNull();
        assertThat(page.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML))
                .isTrue();
        assertThat(page.getBody()).contains(
                "Field Work", "Log a harvest", "Open tasks", "Count inventory",
                "Physical count", "Due handoffs", "Complete handoff &amp; deduct stock",
                "Field photo (optional)", "Photo caption", "up to 15 MiB",
                "Marketable quantity", "Lot code", "Loss / culls", "Storage location",
                "Pending on this device");
        assertThat(page.getBody()).contains("Add a field note", "What did you notice?",
                "Recent field notes", "capture-workspace", "farm-crop-options");
        assertThat(script.getBody()).contains(
                "flowersforever.worker.pending.v1", "operationKey", "flushQueue",
                "error.retryable = true", "Saved on this device", "alreadyQueued",
                "Server unavailable", "queueReadable", "INVENTORY_COUNT_CONFLICT",
                "Review & retry count", "reviewedQuantity", "ORDER_HANDOFF_CONFLICT",
                "Review & retry handoff", "reviewToken", "pendingOrderHandoff",
                "Discard from device", "flowersforever.worker.files.v1",
                "indexedDB", "prepareAttachment", "FormData", "lossQuantity",
                "storageLocation", "response.result.lotCode");
        assertThat(styles.getBody()).contains(
                "min-height: 44px", ".order-summary", ".handoff-actions",
                ".attachment-fields", "@media (max-width: 639px)");
        assertThat(serviceWorker.getBody()).contains(
                "flowersforever-worker-v10", "/worker/worker.js");
    }

    private ResponseEntity<JsonNode> post(String path, String body) {
        HttpHeaders headers = RestCsrfSupport.headers(rest, url("/api/auth/csrf"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url(path), HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> postAttachment(
            String operationKey,
            String targetType,
            Long targetId,
            String filename,
            String mediaType,
            String caption,
            byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("operationKey", operationKey);
        body.add("targetType", targetType);
        body.add("targetId", targetId.toString());
        body.add("caption", caption);
        body.add("capturedAt", "2026-09-02T15:00:00Z");
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(mediaType));
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        HttpHeaders headers = RestCsrfSupport.headers(rest, url("/api/auth/csrf"));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url("/api/worker/attachments"), HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
    }

    private JsonNode farmDataCommand(String requestId, String action, String arguments) {
        HttpHeaders headers = RestCsrfSupport.headers(rest, url("/api/auth/csrf"));
        headers.set(HarnessController.TOKEN_HEADER, HARNESS_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"requestId\":\"" + requestId + "\",\"action\":\""
                + action + "\",\"arguments\":" + arguments + "}";
        ResponseEntity<JsonNode> response = rest.exchange(
                url("/api/harness/farm-data/commands"), HttpMethod.POST,
                new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode())
                .as("Harness response: %s", response.getBody())
                .isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static JsonNode findById(JsonNode records, long id) {
        for (JsonNode record : records) {
            if (record.path("id").asLong() == id) {
                return record;
            }
        }
        throw new AssertionError("No record with id=" + id);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static LocalDate farmToday() {
        return LocalDate.now(ZoneId.of("Pacific/Kiritimati"));
    }
}
