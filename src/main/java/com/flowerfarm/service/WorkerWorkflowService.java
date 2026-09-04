package com.flowerfarm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.HarvestGrade;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.WorkerActionReceipt;
import com.flowerfarm.repository.WorkerActionReceiptJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Mobile field workflows with durable, exact retry semantics. */
@Service
public class WorkerWorkflowService {

    public static final int MAX_TODAY_TASKS = 100;
    public static final int MAX_INVENTORY_ITEMS = 100;
    public static final int MAX_DUE_ORDERS = 100;

    private static final String LOG_HARVEST = "LOG_HARVEST";
    private static final String COMPLETE_TASK = "COMPLETE_TASK";
    private static final String COUNT_INVENTORY = "COUNT_INVENTORY";
    private static final String COMPLETE_ORDER = "COMPLETE_ORDER";
    private static final String ADD_ATTACHMENT = "ADD_ATTACHMENT";
    private static final String ADD_FIELD_NOTE = "ADD_FIELD_NOTE";
    private static final int MAX_RESPONSE_BYTES = 1_000_000;

    private final HarvestService harvestService;
    private final ProductionStudioService productionService;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final FieldAttachmentService attachmentService;
    private final KnowledgeService knowledgeService;
    private final FarmProfile farmProfile;
    private final WorkerActionReceiptJpaRepository receipts;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;

    public WorkerWorkflowService(
            HarvestService harvestService,
            ProductionStudioService productionService,
            InventoryService inventoryService,
            OrderService orderService,
            FieldAttachmentService attachmentService,
            KnowledgeService knowledgeService,
            FarmProfile farmProfile,
            WorkerActionReceiptJpaRepository receipts,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.harvestService = harvestService;
        this.productionService = productionService;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.attachmentService = attachmentService;
        this.knowledgeService = knowledgeService;
        this.farmProfile = farmProfile;
        this.receipts = receipts;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public WorkerToday today(LocalDate date) {
        LocalDate requested = date == null ? LocalDate.now(farmProfile.zoneId()) : date;
        List<ProductionTask> tasks = productionService.listOpenTasksDueThrough(
                requested, MAX_TODAY_TASKS);
        long total = productionService.countOpenTasksDueThrough(requested);
        return new WorkerToday(
                requested, Instant.now(), tasks, total, total > tasks.size());
    }

    public WorkerFieldNotes fieldNotes(LocalDate date, int limit) {
        LocalDate through = date == null ? LocalDate.now(farmProfile.zoneId()) : date;
        return new WorkerFieldNotes(through, knowledgeService.recentThrough(through, limit));
    }

    /** Creates a shared Knowledge Log entry and durable retry receipt in one transaction. */
    public synchronized WorkerActionResult addFieldNote(WorkerFieldNoteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Field note is required.");
        }
        String observation = request.observation() == null ? "" : request.observation().trim();
        String summary = request.summary() == null ? "" : request.summary().trim();
        if (summary.isBlank()) {
            summary = observation.lines().findFirst().orElse("");
            if (summary.length() > 180) summary = summary.substring(0, 180);
        }
        KnowledgeEntry entry = new KnowledgeEntry(
                request.observedOn(), summary, request.kind(), request.cropName(), "",
                request.bedOrField(), "", observation, request.actionTaken(),
                request.performedBy(), "", request.followUpDate(),
                KnowledgeConfidence.NEEDS_REVIEW, List.of("field-note"));
        if (entry.getFollowUpDate() != null
                && entry.getFollowUpDate().isBefore(entry.getObservedOn())) {
            throw new IllegalArgumentException("Follow-up date cannot be before the observation.");
        }
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("observedOn", entry.getObservedOn());
        canonical.put("summary", entry.getSummary());
        canonical.put("kind", entry.getKind());
        canonical.put("cropName", entry.getCropName());
        canonical.put("bedOrField", entry.getBedOrField());
        canonical.put("observation", entry.getObservation());
        canonical.put("actionTaken", entry.getActionTaken());
        canonical.put("performedBy", entry.getPerformedBy());
        canonical.put("followUpDate", entry.getFollowUpDate());
        return transact(request.operationKey(), ADD_FIELD_NOTE, canonical,
                () -> knowledgeService.create(entry));
    }

    /** Returns confirmed pickup, delivery, and market handoffs due through the work date. */
    public WorkerOrders orders(LocalDate date, int limit) {
        LocalDate requested = date == null ? LocalDate.now(farmProfile.zoneId()) : date;
        int requestedLimit = requireDueOrderLimit(limit);
        List<WorkerOrder> visible = orderService.confirmedDueThrough(requested, requestedLimit)
                .stream()
                .map(order -> WorkerOrder.from(order, false))
                .toList();
        long total = orderService.countConfirmedDueThrough(requested);
        return new WorkerOrders(requested, Instant.now(), visible,
                total, total > visible.size());
    }

    /** Returns only the stock fields needed for a phone/tablet physical count. */
    public WorkerInventory inventory(String query, int limit) {
        int requestedLimit = requireInventoryLimit(limit);
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() > 120) {
            throw new IllegalArgumentException("Inventory search cannot exceed 120 characters.");
        }
        List<Item> matches = normalizedQuery.isEmpty()
                ? inventoryService.getAllItems()
                : inventoryService.searchItems(normalizedQuery);
        List<WorkerInventoryItem> visible = matches.stream()
                .limit(requestedLimit)
                .map(WorkerInventoryItem::from)
                .toList();
        return new WorkerInventory(normalizedQuery, Instant.now(), visible,
                matches.size(), matches.size() > visible.size());
    }

    /** Commits the harvest and durable receipt in one transaction before releasing the key lock. */
    public synchronized WorkerActionResult logHarvest(WorkerHarvestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Harvest submission is required.");
        }
        HarvestEntry entry = new HarvestEntry(
                request.harvestDate(), request.cropName(), request.quantity(),
                request.unit(), request.bedOrField(), request.notes(),
                request.lotCode(), request.grade(), request.lossQuantity(),
                request.storageLocation());
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("harvestDate", entry.getHarvestDate());
        canonical.put("cropName", entry.getCropName());
        canonical.put("quantity", entry.getQuantity());
        canonical.put("unit", entry.getUnit());
        canonical.put("bedOrField", entry.getBedOrField());
        canonical.put("notes", entry.getNotes());
        canonical.put("lotCode", entry.getLotCode());
        canonical.put("grade", entry.getGrade());
        canonical.put("lossQuantity", entry.getLossQuantity());
        canonical.put("storageLocation", entry.getStorageLocation());
        return transact(request.operationKey(), LOG_HARVEST, canonical,
                () -> harvestService.add(entry));
    }

    /** Commits task actuals and the durable receipt together for safe mobile retries. */
    public synchronized WorkerActionResult completeTask(
            Long taskId, WorkerTaskCompletionRequest request) {
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("Production task id must be positive.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Task completion is required.");
        }
        ProductionStudioService.TaskActuals actuals =
                new ProductionStudioService.TaskActuals(
                        request.actualMinutes(), request.inputCost(),
                        request.fieldObservation());
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("taskId", taskId);
        canonical.put("actualMinutes", actuals.actualMinutes());
        canonical.put("inputCost", actuals.inputCost());
        canonical.put("fieldObservation", actuals.fieldObservation());
        return transact(request.operationKey(), COMPLETE_TASK, canonical,
                () -> productionService.recordTaskActuals(taskId, actuals));
    }

    /** Commits a concurrency-checked physical count and its retry receipt together. */
    public synchronized WorkerActionResult countInventory(
            Long itemId, WorkerInventoryCountRequest request) {
        if (itemId == null || itemId <= 0) {
            throw new IllegalArgumentException("Inventory item id must be positive.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Inventory count is required.");
        }
        if (request.reviewedQuantity() < 0) {
            throw new IllegalArgumentException("Reviewed quantity cannot be negative.");
        }
        if (request.countedQuantity() < 0) {
            throw new IllegalArgumentException("Counted quantity cannot be negative.");
        }
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("itemId", itemId);
        canonical.put("reviewedQuantity", request.reviewedQuantity());
        canonical.put("countedQuantity", request.countedQuantity());
        return transact(request.operationKey(), COUNT_INVENTORY, canonical, () -> {
            Item saved = inventoryService.recordPhysicalCount(
                    itemId, request.reviewedQuantity(), request.countedQuantity());
            return WorkerInventoryItem.from(saved);
        });
    }

    /** Commits an exact reviewed order handoff, inventory deduction, and retry receipt once. */
    public synchronized WorkerActionResult completeOrder(
            Long orderId, WorkerOrderCompletionRequest request) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Order id must be positive.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Order handoff completion is required.");
        }
        String reviewToken = request.reviewToken() == null ? "" : request.reviewToken().trim();
        if (!reviewToken.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Order handoff review token is invalid.");
        }
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("orderId", orderId);
        canonical.put("reviewToken", reviewToken);
        return transact(request.operationKey(), COMPLETE_ORDER, canonical, () -> {
            try {
                OrderService.WorkerFulfillmentOutcome outcome =
                        orderService.fulfillWorkerReviewed(orderId, reviewToken);
                return WorkerOrder.from(outcome.order(), outcome.alreadyFulfilled());
            } catch (OrderService.StaleOrderReviewException exception) {
                throw new WorkerOrderConflictException(orderId, exception.getMessage());
            }
        });
    }

    /** Commits immutable photo metadata and its durable retry receipt together. */
    public synchronized WorkerActionResult addAttachment(
            WorkerAttachmentRequest request, byte[] content) {
        if (request == null) {
            throw new IllegalArgumentException("Attachment submission is required.");
        }
        byte[] bytes = content == null ? new byte[0] : content.clone();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Attachment file is empty.");
        }
        if (bytes.length > FieldAttachmentService.MAX_ATTACHMENT_BYTES) {
            throw new AttachmentTooLargeException(
                    "Attachment file cannot exceed 15 MiB.");
        }
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("targetType", request.targetType());
        canonical.put("targetId", request.targetId());
        canonical.put("originalFilename", request.originalFilename());
        canonical.put("mediaType", request.mediaType());
        canonical.put("caption", request.caption());
        canonical.put("capturedAt", request.capturedAt());
        canonical.put("byteSize", bytes.length);
        canonical.put("contentSha256", FieldAttachmentService.sha256(bytes));
        return transact(request.operationKey(), ADD_ATTACHMENT, canonical,
                () -> attachmentService.add(
                        request.targetType(), request.targetId(), request.originalFilename(),
                        request.mediaType(), request.caption(), request.capturedAt(), bytes));
    }

    /** Recovers the exact stored result after a response loss or application restart. */
    public WorkerActionResult recover(String operationKey) {
        WorkerActionReceipt receipt = receipts.findByOperationKeyHash(
                        operationKeyHash(operationKey))
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No worker operation was found for that key."));
        return result(receipt, true);
    }

    private WorkerActionResult transact(
            String operationKey,
            String action,
            Map<String, Object> canonicalRequest,
            Supplier<?> operation) {
        String keyHash = operationKeyHash(operationKey);
        Map<String, Object> fingerprintInput = new LinkedHashMap<>();
        fingerprintInput.put("action", action);
        fingerprintInput.put("request", canonicalRequest);
        String requestFingerprint = digest(jsonBytes(fingerprintInput));
        WorkerActionResult committed = transactions.execute(status -> {
            WorkerActionReceipt existing = receipts.findByOperationKeyHash(keyHash)
                    .orElse(null);
            if (existing != null) {
                if (!action.equals(existing.getAction())
                        || !requestFingerprint.equals(existing.getRequestFingerprint())) {
                    throw new WorkerActionConflictException(
                            "Operation key already identifies a different worker submission.");
                }
                return result(existing, true);
            }

            Object saved = operation.get();
            JsonNode result = mapper.valueToTree(saved);
            byte[] responseBytes = jsonBytes(result);
            if (responseBytes.length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("Worker operation result exceeds its size limit.");
            }
            Long targetId = targetId(result);
            WorkerActionReceipt receipt = receipts.saveAndFlush(new WorkerActionReceipt(
                    targetId, action, keyHash, requestFingerprint,
                    digest(responseBytes), new String(responseBytes, StandardCharsets.UTF_8)));
            return result(receipt, false);
        });
        if (committed == null) {
            throw new IllegalStateException("Worker operation transaction returned no result.");
        }
        return committed;
    }

    private WorkerActionResult result(WorkerActionReceipt receipt, boolean replayed) {
        byte[] bytes = receipt.getResponseJson().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_RESPONSE_BYTES
                || !digest(bytes).equals(receipt.getResponseSha256())) {
            throw new IllegalStateException("Stored worker operation result is corrupt.");
        }
        try {
            JsonNode result = mapper.readTree(bytes);
            if (!result.isObject()
                    || result.path("id").asLong(-1) != receipt.getTargetRecordId()) {
                throw new IllegalStateException(
                        "Stored worker operation target does not match its receipt.");
            }
            return new WorkerActionResult(
                    receipt.getAction(), replayed, receipt.getCreatedAt(), result);
        } catch (IOException exception) {
            throw new IllegalStateException("Stored worker operation result is corrupt.", exception);
        }
    }

    private byte[] jsonBytes(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize worker operation.", exception);
        }
    }

    private static Long targetId(JsonNode result) {
        if (result == null || !result.isObject() || !result.hasNonNull("id")
                || !result.path("id").canConvertToLong()
                || result.path("id").asLong() <= 0) {
            throw new IllegalStateException("Worker operation returned no saved record id.");
        }
        return result.path("id").asLong();
    }

    private static String operationKeyHash(String operationKey) {
        String normalized = operationKey == null ? "" : operationKey.trim();
        if (!normalized.matches("[A-Za-z0-9._~-]{1,160}")) {
            throw new IllegalArgumentException(
                    "operationKey must contain 1 to 160 URL-safe letters, numbers, '.', '_', "
                            + "'~', or '-' characters.");
        }
        return digest(("flowersforever-worker-operation\u0000" + normalized)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static int requireInventoryLimit(int limit) {
        if (limit < 1 || limit > MAX_INVENTORY_ITEMS) {
            throw new IllegalArgumentException(
                    "Inventory limit must be between 1 and " + MAX_INVENTORY_ITEMS + ".");
        }
        return limit;
    }

    private static int requireDueOrderLimit(int limit) {
        if (limit < 1 || limit > MAX_DUE_ORDERS) {
            throw new IllegalArgumentException(
                    "Order handoff limit must be between 1 and " + MAX_DUE_ORDERS + ".");
        }
        return limit;
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record WorkerToday(
            LocalDate date,
            Instant generatedAt,
            List<ProductionTask> tasks,
            long openTaskTotal,
            boolean hasMore) {
        public WorkerToday {
            tasks = List.copyOf(tasks);
        }
    }

    public record WorkerHarvestRequest(
            String operationKey,
            LocalDate harvestDate,
            String cropName,
            double quantity,
            String unit,
            String bedOrField,
            String notes,
            String lotCode,
            HarvestGrade grade,
            double lossQuantity,
            String storageLocation) {
    }

    public record WorkerFieldNoteRequest(
            String operationKey, LocalDate observedOn, String summary, KnowledgeKind kind,
            String cropName, String bedOrField, String observation, String actionTaken,
            String performedBy, LocalDate followUpDate) {
    }

    public record WorkerFieldNotes(LocalDate through, List<KnowledgeEntry> notes) {
        public WorkerFieldNotes {
            notes = List.copyOf(notes);
        }
    }

    public record WorkerTaskCompletionRequest(
            String operationKey,
            int actualMinutes,
            double inputCost,
            String fieldObservation) {
    }

    public record WorkerInventoryCountRequest(
            String operationKey,
            int reviewedQuantity,
            int countedQuantity) {
    }

    public record WorkerOrderCompletionRequest(
            String operationKey,
            String reviewToken) {
    }

    public record WorkerAttachmentRequest(
            String operationKey,
            String targetType,
            Long targetId,
            String originalFilename,
            String mediaType,
            String caption,
            Instant capturedAt) {
    }

    public record WorkerOrders(
            LocalDate throughDate,
            Instant generatedAt,
            List<WorkerOrder> orders,
            long dueTotal,
            boolean hasMore) {
        public WorkerOrders {
            orders = List.copyOf(orders);
        }
    }

    public record WorkerOrder(
            Long id,
            LocalDate orderDate,
            String handoffType,
            String status,
            String customerName,
            String contactName,
            String phone,
            String email,
            String customerNotes,
            String instructions,
            List<WorkerOrderLine> lines,
            String reviewToken,
            boolean alreadyFulfilled) {
        public WorkerOrder {
            lines = List.copyOf(lines);
        }

        static WorkerOrder from(CustomerOrder order, boolean alreadyFulfilled) {
            Customer customer = order.getCustomer();
            return new WorkerOrder(
                    order.getId(), order.getOrderDate(), order.getHandoffType(),
                    order.getStatus(), customer == null ? "" : customer.getName(),
                    customer == null ? "" : customer.getContactName(),
                    customer == null ? "" : customer.getPhone(),
                    customer == null ? "" : customer.getEmail(),
                    customer == null ? "" : customer.getNotes(), order.getNotes(),
                    order.getLines().stream().map(WorkerOrderLine::from).toList(),
                    OrderService.handoffReviewToken(order), alreadyFulfilled);
        }
    }

    public record WorkerOrderLine(
            Long id,
            String productName,
            double quantity,
            String unit) {
        static WorkerOrderLine from(OrderLine line) {
            return new WorkerOrderLine(
                    line.getId(), line.getProductName(), line.getQuantity(), line.getUnit());
        }
    }

    public record WorkerInventory(
            String query,
            Instant generatedAt,
            List<WorkerInventoryItem> items,
            long matchingTotal,
            boolean hasMore) {
        public WorkerInventory {
            items = List.copyOf(items);
        }
    }

    public record WorkerInventoryItem(
            Long id,
            String name,
            String category,
            int quantity,
            String unit) {
        static WorkerInventoryItem from(Item item) {
            return new WorkerInventoryItem(
                    item.getId(), item.getName(), item.getCategory(),
                    item.getQuantity(), item.getUnit());
        }
    }

    public record WorkerActionResult(
            String action,
            boolean replayed,
            Instant recordedAt,
            JsonNode result) {
    }
}
