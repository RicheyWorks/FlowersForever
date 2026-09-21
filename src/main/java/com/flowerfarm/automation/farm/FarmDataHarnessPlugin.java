package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.automation.HarnessActionEffect;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessArgumentSpec;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessPlugin;
import com.flowerfarm.automation.HarnessPluginCapability;
import com.flowerfarm.automation.HarnessPluginDescriptor;
import com.flowerfarm.automation.HarnessPluginKind;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.HarnessValueType;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmDataImportReceipt;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.HarvestGrade;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.FieldAttachmentService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.SeasonPlanService;
import com.flowerfarm.service.WorkerWorkflowService;

import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Domain-level AI bridge for data migration and ongoing farm operations.
 *
 * <p>This plugin deliberately calls application services rather than Swing controls. An AI can
 * therefore map Excel, CSV, or a previous application's records into canonical JSON and use the
 * same contract from a desktop, phone, tablet, MCP server, or provider-specific adapter.
 */
@Component
public class FarmDataHarnessPlugin implements HarnessPlugin {

    public static final String ID = "farm-data";
    private static final int MAX_BATCH_SIZE = 500;
    private static final int MAX_PAGE_SIZE = 200;
    private static final List<String> RECORD_TYPES = FarmDataPageService.RECORD_TYPES;
    private static final List<String> MAPPABLE_RECORD_TYPES = List.of(
            "inventory", "customers", "harvests", "orders", "knowledge",
            "production-tasks", "growing-templates", "planting-plans",
            "floral-recipes");

    private static final Map<String, Object> INVENTORY_SCHEMA = objectSchema(
            List.of("name"), Map.ofEntries(
                    entry("sourceKey", sourceKeyField()),
                    entry("name", textField("Unique crop, product, or supply name.")),
                    entry("category", textField("Inventory category; defaults to Other.")),
                    entry("price", numberField("Selling price; defaults to 0.")),
                    entry("unit", textField("Selling or stock unit; defaults to Per Unit.")),
                    entry("cost", numberField("Unit cost; defaults to 0.")),
                    entry("quantity", integerField("Current quantity; defaults to 0.")),
                    entry("notes", textField("Farm notes."))));

    private static final Map<String, Object> CUSTOMER_SCHEMA = objectSchema(
            List.of("name"), Map.ofEntries(
                    entry("sourceKey", sourceKeyField()),
                    entry("name", textField("Unique customer or account name.")),
                    entry("contactName", textField("Primary contact person.")),
                    entry("email", textField("Email address.")),
                    entry("phone", textField("Phone number.")),
                    entry("customerType", enumField("WHOLESALE", "MARKET", "FLORIST", "OTHER")),
                    entry("notes", textField("Customer notes."))));

    private static final Map<String, Object> HARVEST_SCHEMA = objectSchema(
            List.of("harvestDate", "cropName", "quantity"), Map.ofEntries(
                    entry("sourceKey", sourceKeyField()),
                    entry("harvestDate", dateField("ISO-8601 harvest date.")),
                    entry("cropName", textField("Crop or inventory name.")),
                    entry("quantity", numberField("Positive harvested quantity.")),
                    entry("unit", textField("Harvest unit; defaults to stems.")),
                    entry("bedOrField", textField("Bed, field, or greenhouse location.")),
                    entry("lotCode", textField(
                            "Optional existing lot code; a unique code is generated when blank.")),
                    entry("grade", enumField(
                            "UNGRADED", "SELECT", "STANDARD", "PROCESSING")),
                    entry("lossQuantity", numberField(
                            "Non-negative unusable quantity; excluded from inventory.")),
                    entry("storageLocation", textField(
                            "Cooler, bucket, shelf, or other current storage location.")),
                    entry("notes", textField("Harvest notes."))));

    private static final Map<String, Object> ORDER_LINE_SCHEMA = objectSchema(
            List.of("productName", "quantity"), Map.ofEntries(
                    entry("productName", textField("Inventory or custom product name.")),
                    entry("quantity", numberField("Positive ordered quantity.")),
                    entry("unit", textField("Order unit; defaults to bunch.")),
                    entry("unitPrice", numberField("Non-negative price per unit."))));

    private static final Map<String, Object> ORDER_SCHEMA = orderSchema(
            "DRAFT", "CONFIRMED", "FULFILLED", "CANCELLED");
    private static final Map<String, Object> CREATE_ORDER_SCHEMA = orderSchema("DRAFT", "CONFIRMED");

    private static final Map<String, Object> KNOWLEDGE_SCHEMA = objectSchema(
            List.of("observedOn", "summary", "observation"), Map.ofEntries(
                    entry("sourceKey", sourceKeyField()),
                    entry("observedOn", dateField("ISO-8601 observation date.")),
                    entry("summary", textField("Short description of what happened.")),
                    entry("kind", enumField("PROBLEM", "OPPORTUNITY", "PRACTICE")),
                    entry("cropName", textField("Related crop.")),
                    entry("variety", textField("Related variety.")),
                    entry("bedOrField", textField("Related bed or field.")),
                    entry("productionStage", textField("Production stage.")),
                    entry("observation", textField("What the worker observed.")),
                    entry("actionTaken", textField("Action taken in response.")),
                    entry("performedBy", textField("Worker or team.")),
                    entry("result", textField("Observed result.")),
                    entry("followUpDate", dateField("Optional ISO-8601 follow-up date.")),
                    entry("confidence", enumField(
                            "NEEDS_REVIEW", "EARLY_SIGNAL", "PROMISING", "PROVEN")),
                    entry("tags", stringArrayField("Searchable knowledge tags.")),
                    entry("harvestId", integerField("Optional linked harvest id.")),
                    entry("inventoryItemId", integerField("Optional linked inventory id.")),
                    entry("orderId", integerField("Optional linked order id.")),
                    entry("customerId", integerField("Optional linked customer id."))));

    private static final Map<String, Object> TASK_ACTUAL_SCHEMA = objectSchema(
            List.of("taskId", "actualMinutes"), Map.ofEntries(
                    entry("sourceKey", sourceKeyField()),
                    entry("taskId", integerField("Production task id.")),
                    entry("actualMinutes", integerField("Crew minutes from 0 to 10,080.")),
                    entry("inputCost", numberField("Task-specific input cost.")),
                    entry("fieldObservation", textField("Field result or worker observation."))));

    private static final Map<String, Object> GROWING_TEMPLATE_SCHEMA = objectSchema(
            List.of("name", "cropName", "spacingInches", "rowsPerBed",
                    "sowDaysFromFrost", "daysToHarvest", "harvestWindowDays",
                    "successionDays", "stemsPerPlant", "germinationPercent",
                    "soilPrepLeadDays", "supportDaysAfterPlanting"), Map.ofEntries(
                    entry("sourceKey", sourceKeyField()),
                    entry("name", textField("Unique farm-facing template name.")),
                    entry("cropName", textField("Crop governed by the template.")),
                    entry("variety", textField("Optional variety.")),
                    entry("spacingInches", numberField("Plant spacing from 1 to 72 inches.")),
                    entry("rowsPerBed", integerField("Rows per bed from 1 to 12.")),
                    entry("sowDaysFromFrost", integerField(
                            "Sowing offset from last spring frost, from -365 to 365.")),
                    entry("transplantDaysFromFrost", integerField(
                            "Optional transplant offset from last spring frost.")),
                    entry("daysToHarvest", integerField("Days from establishment to harvest.")),
                    entry("harvestWindowDays", integerField("Expected harvest-window length.")),
                    entry("successionDays", integerField("Days between successions; zero disables.")),
                    entry("stemsPerPlant", numberField("Expected usable stems per plant.")),
                    entry("germinationPercent", numberField("Expected germination percentage.")),
                    entry("soilPrepLeadDays", integerField("Soil-preparation lead time.")),
                    entry("supportDaysAfterPlanting", integerField(
                            "Support or netting task offset after planting.")),
                    entry("active", booleanField("Whether this template is active; defaults true.")),
                    entry("notes", textField("Farm-owned growing notes."))));

    private static final Map<String, Object> PLANTING_PLAN_SCHEMA = objectSchema(
            List.of("seasonYear", "cropName", "bedOrField", "sowDate",
                    "harvestStart", "harvestEnd", "bedFeet", "expectedStems"),
            Map.ofEntries(
                    entry("sourceKey", sourceKeyField()),
                    entry("seasonYear", integerField("Season year from 2000 to 2200.")),
                    entry("cropName", textField("Crop assigned to this planting block.")),
                    entry("variety", textField("Optional variety.")),
                    entry("bedOrField", textField("Bed, field, or greenhouse location.")),
                    entry("sowDate", dateField("ISO-8601 sowing date.")),
                    entry("transplantDate", dateField("Optional ISO-8601 transplant date.")),
                    entry("harvestStart", dateField("ISO-8601 harvest-window start.")),
                    entry("harvestEnd", dateField("ISO-8601 harvest-window end.")),
                    entry("bedFeet", numberField("Positive bed length in feet.")),
                    entry("expectedStems", integerField("Expected usable stem count.")),
                    entry("status", enumField(
                            "PLANNED", "SOWN", "PLANTED", "HARVESTING", "COMPLETE")),
                    entry("notes", textField("Planting-block notes."))));

    private static final Map<String, Object> RECIPE_INGREDIENT_SCHEMA = objectSchema(
            List.of("itemName", "stemsPerDesign", "packSize"), Map.ofEntries(
                    entry("itemName", textField("Inventory crop or purchased material name.")),
                    entry("stemsPerDesign", numberField("Quantity needed per finished design.")),
                    entry("packSize", integerField("Purchased units per pack.")),
                    entry("packCost", numberField("Non-negative cost per purchased pack.")),
                    entry("notes", textField("Ingredient notes or substitution guidance."))));

    private static final Map<String, Object> FLORAL_RECIPE_SCHEMA = objectSchema(
            List.of("name", "designType", "ingredients"), Map.ofEntries(
                    entry("sourceKey", sourceKeyField()),
                    entry("name", textField("Recipe name.")),
                    entry("designType", textField("Bouquet, arrangement, arch, or other design.")),
                    entry("salePrice", numberField("Selling price per design.")),
                    entry("laborMinutes", integerField("Crew minutes per design.")),
                    entry("laborRatePerHour", numberField("Loaded hourly labor rate.")),
                    entry("overheadPercent", numberField("Overhead percentage.")),
                    entry("wastePercent", numberField("Expected material waste percentage.")),
                    entry("notes", textField("Recipe and construction notes.")),
                    entry("ingredients", arrayField(
                            "Required crops and materials.", RECIPE_INGREDIENT_SCHEMA))));

    private static final Map<String, Object> SOURCE_RECORD_SCHEMA = Map.of(
            "type", "object",
            "description", "One flat spreadsheet row or nested provider record.",
            "additionalProperties", true);

    private static final HarnessPluginDescriptor DESCRIPTOR = new HarnessPluginDescriptor(
            ID,
            "FlowersForever farm data",
            "Read canonical farm records and migrate or record operational data without driving UI.",
            "1.9",
            HarnessPluginKind.WORKFLOW,
            Set.of(HarnessPluginCapability.OBSERVATION,
                    HarnessPluginCapability.COMMANDS),
            List.of(
                    action("get-data-catalog",
                            "Return canonical record types, JSON schemas, and write behavior.",
                            HarnessRisk.READ, true, List.of()),
                    action("suggest-field-mapping",
                            "Suggest canonical destinations for spreadsheet or provider fields.",
                            HarnessRisk.READ, true, List.of(
                                    argument("record-type", HarnessValueType.STRING,
                                            "Target canonical record type.", true),
                                    new HarnessArgumentSpec(
                                            "source-fields", HarnessValueType.ARRAY,
                                            "Column names or provider field paths.", true,
                                            HarnessValueType.STRING))),
                    action("preview-mapped-records",
                            "Map and validate source rows without changing farm data.",
                            HarnessRisk.READ, true, mappingArguments(false)),
                    action("import-mapped-records",
                            "Atomically map and import valid source rows with durable identity.",
                            HarnessRisk.MUTATE, true, mappingArguments(true)),
                    action("list-records",
                            "Read a bounded page of canonical farm records for AI context or export.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("record-type", HarnessValueType.STRING,
                                            "One record type published by get-data-catalog.", true),
                                    argument("offset", HarnessValueType.INTEGER,
                                            "Zero-based page offset; defaults to 0.", false),
                                    argument("limit", HarnessValueType.INTEGER,
                                            "Page size from 1 to 200; defaults to 100.", false),
                                    argument("from", HarnessValueType.STRING,
                                            "Optional ISO date for production-tasks.", false),
                                    argument("to", HarnessValueType.STRING,
                                            "Optional ISO date for production-tasks.", false))),
                    action("read-attachment-content",
                            "Read one verified, bounded base64 chunk of a field attachment.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("attachment-id", HarnessValueType.INTEGER,
                                            "Positive signed 64-bit attachment id returned by list-records.", true),
                                    argument("offset", HarnessValueType.INTEGER,
                                            "Zero-based byte offset; defaults to 0.", false),
                                    argument("max-bytes", HarnessValueType.INTEGER,
                                             "Chunk size from 1 to 262144 bytes.", false))),
                    action("lookup-harvest-lot",
                            "Read one harvest by its exact traceability lot code.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("lot-code", HarnessValueType.STRING,
                                            "Printed or scanned harvest lot code.", true))),
                    action("get-production-costs",
                            "Join task actuals and matched harvests into planting and season costs.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("season-year", HarnessValueType.INTEGER,
                                            "Season year from 2000 to 2200.", true),
                                    argument("labor-rate-per-hour", HarnessValueType.NUMBER,
                                            "Optional loaded labor-rate override; the saved season rate is used when omitted.",
                                            false))),
                    action("preview-plantings",
                            "Review frost-aware planting drafts and obtain their commit fingerprint.",
                            HarnessRisk.SENSITIVE_READ, true, plantingArguments(false)),
                    action("commit-plantings",
                            "Save reviewed planting blocks and generated work; reject changed previews.",
                            HarnessRisk.MUTATE, false, plantingArguments(true)),
                    action("regenerate-tasks",
                            "Replace open generated tasks while preserving completed work and actuals.",
                            HarnessRisk.MUTATE, false, List.of(
                                    argument("plan-id", HarnessValueType.INTEGER,
                                            "Saved planting plan id.", true),
                                    argument("template-id", HarnessValueType.INTEGER,
                                            "Growing template that owns this plan's tasks.", true))),
                    action("update-task-status",
                            "Mark work TODO, DONE, or SKIPPED while retaining recorded actuals.",
                            HarnessRisk.MUTATE, false, List.of(
                                    argument("task-id", HarnessValueType.INTEGER,
                                            "Saved production task id.", true),
                                    argument("status", HarnessValueType.STRING,
                                            "TODO, DONE, or SKIPPED.", true))),
                    action("estimate-recipe",
                            "Calculate recipe stems, pack rounding, labor, costs, and margin.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("recipe-id", HarnessValueType.INTEGER,
                                            "Saved floral recipe id.", true),
                                    argument("count", HarnessValueType.INTEGER,
                                            "Number of designs to make.", true),
                                    argument("event-date", HarnessValueType.STRING,
                                            "ISO date used for planned availability.", true))),
                    action("get-availability",
                            "Read planned weekly stems and live inventory without reserving stock.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("week-of", HarnessValueType.STRING,
                                            "ISO date in the requested week.", true),
                                    argument("offset", HarnessValueType.INTEGER,
                                            "Zero-based offset; defaults to 0.", false),
                                    argument("limit", HarnessValueType.INTEGER,
                                            "Page size from 1 to 200; defaults to 100.", false))),
                    action("list-handoffs",
                            "Read a bounded page of locally recorded provider handoffs.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("offset", HarnessValueType.INTEGER,
                                            "Zero-based offset; defaults to 0.", false),
                                    argument("limit", HarnessValueType.INTEGER,
                                            "Page size from 1 to 200; defaults to 100.", false))),
                    action("create-handoff",
                            "Record a local provider brief; does not send, publish, sign, or charge.",
                            HarnessRisk.MUTATE, false, List.of(
                                    argument("handoff-type", HarnessValueType.STRING,
                                            "CONTRACT, PAYMENT, or MARKETPLACE.", true),
                                    argument("provider", HarnessValueType.STRING,
                                            "Provider name, at most 80 characters.", true),
                                    argument("status", HarnessValueType.STRING,
                                            "Provider-specific local status, such as DRAFT.", true),
                                    argument("external-reference", HarnessValueType.STRING,
                                            "Required after sending or completing in the provider.", false),
                                    argument("related-order-id", HarnessValueType.INTEGER,
                                            "Optional saved customer order id.", false),
                                    argument("related-recipe-id", HarnessValueType.INTEGER,
                                            "Optional saved recipe id.", false),
                                    argument("amount", HarnessValueType.NUMBER,
                                            "Optional amount; payment amounts must be positive.", false),
                                    argument("currency", HarnessValueType.STRING,
                                            "Three-letter currency code; defaults to USD.", false),
                                    argument("summary", HarnessValueType.STRING,
                                            "Operational brief without credentials or legal contract text.", false))),
                    action("list-import-receipts",
                            "Page durable source-to-farm record mappings for import resume.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("source-system", HarnessValueType.STRING,
                                            "Optional normalized upstream system name.", false),
                                    argument("record-type", HarnessValueType.STRING,
                                            "Optional canonical record type.", false),
                                    argument("page", HarnessValueType.INTEGER,
                                            "Zero-based receipt page; defaults to 0.", false),
                                    argument("limit", HarnessValueType.INTEGER,
                                            "Page size from 1 to 200; defaults to 100.", false))),
                    action("create-record-export",
                            "Create or durably replay an immutable canonical farm snapshot.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("export-key", HarnessValueType.STRING,
                                            "Stable caller key for restart-safe replay.", true),
                                    new HarnessArgumentSpec(
                                            "record-types", HarnessValueType.ARRAY,
                                            "Optional readable types; defaults to all ten.",
                                            false, HarnessValueType.STRING),
                                    argument("chunk-records", HarnessValueType.INTEGER,
                                            "Maximum records per chunk from 1 to 200.", false),
                                    argument("task-from", HarnessValueType.STRING,
                                            "Optional inclusive production-task start date.", false),
                                    argument("task-to", HarnessValueType.STRING,
                                            "Optional inclusive production-task end date.", false))),
                    action("get-record-export",
                            "Recover a completed canonical snapshot by its stable export key.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("export-key", HarnessValueType.STRING,
                                            "Stable caller key used to create the export.", true))),
                    action("read-record-export",
                            "Read and verify one bounded chunk from a completed snapshot.",
                            HarnessRisk.SENSITIVE_READ, true, List.of(
                                    argument("export-id", HarnessValueType.STRING,
                                            "Opaque export id returned by create or get.", true),
                                    argument("part", HarnessValueType.INTEGER,
                                            "Zero-based chunk number; defaults to 0.", false))),
                    action("upload-attachment",
                            "Durably attach a base64 field photo to a production task.",
                            HarnessRisk.MUTATE, true, List.of(
                                    argument("operation-key", HarnessValueType.STRING,
                                            "Stable key for exact retry and restart recovery.", true),
                                    argument("target-type", HarnessValueType.STRING,
                                            "PRODUCTION_TASK.", true),
                                    argument("target-id", HarnessValueType.INTEGER,
                                            "Positive signed 64-bit id of an existing production task.", true),
                                    argument("filename", HarnessValueType.STRING,
                                            "Original user-facing filename.", true),
                                    argument("media-type", HarnessValueType.STRING,
                                            "Declared image media type.", true),
                                    argument("caption", HarnessValueType.STRING,
                                            "Optional field context up to 500 characters.", false),
                                    argument("captured-at", HarnessValueType.STRING,
                                            "Optional ISO-8601 capture instant.", false),
                                    argument("content-base64", HarnessValueType.STRING,
                                            "JPEG, PNG, WebP, HEIC, or HEIF bytes as base64.", true))),
                    action("upsert-inventory",
                            "Insert or replace inventory records by case-insensitive exact name.",
                            HarnessRisk.MUTATE, true,
                            writeArguments("Canonical inventory records.", INVENTORY_SCHEMA)),
                    action("upsert-season-settings",
                            "Save one season's frost anchors and loaded field-labor rate.",
                            HarnessRisk.MUTATE, true, List.of(
                                    argument("season-year", HarnessValueType.INTEGER,
                                            "Season year from 2000 to 2200.", true),
                                    argument("last-spring-frost", HarnessValueType.STRING,
                                            "ISO-8601 last spring frost date.", true),
                                    argument("first-fall-frost", HarnessValueType.STRING,
                                            "ISO-8601 first fall frost date.", true),
                                    argument("source", HarnessValueType.STRING,
                                            "Farm weather station or reviewed source.", false),
                                    argument("loaded-labor-rate-per-hour", HarnessValueType.NUMBER,
                                            "Loaded field-labor rate from 0 to 10,000.", false))),
                    action("upsert-customers",
                            "Insert or replace customer records by case-insensitive exact name.",
                            HarnessRisk.MUTATE, true,
                            writeArguments("Canonical customer records.", CUSTOMER_SCHEMA)),
                    action("upsert-growing-templates",
                            "Insert or update growing templates by exact name.",
                            HarnessRisk.MUTATE, true,
                            writeArguments("Canonical growing templates.",
                                    GROWING_TEMPLATE_SCHEMA)),
                    action("upsert-planting-plans",
                            "Insert or update explicit planting blocks and harvest windows.",
                            HarnessRisk.MUTATE, true,
                            writeArguments("Canonical planting plans.",
                                    PLANTING_PLAN_SCHEMA)),
                    action("upsert-floral-recipes",
                            "Insert or update floral recipes including ingredient requirements.",
                            HarnessRisk.MUTATE, true,
                            writeArguments("Canonical floral recipes.",
                                    FLORAL_RECIPE_SCHEMA)),
                    action("import-harvest-history",
                            "Import historical harvest records without changing current inventory.",
                            HarnessRisk.MUTATE, false,
                            writeArguments("Canonical historical harvest records.",
                                    HARVEST_SCHEMA)),
                    action("log-harvests",
                            "Append harvest records and apply their inventory increments atomically.",
                            HarnessRisk.MUTATE, false,
                            writeArguments("Canonical harvest records.", HARVEST_SCHEMA)),
                    action("import-order-history",
                            "Import historical orders in any state without changing inventory.",
                            HarnessRisk.MUTATE, false,
                            writeArguments("Canonical historical order records.", ORDER_SCHEMA)),
                    action("create-orders",
                            "Create draft or confirmed orders for existing customers atomically.",
                            HarnessRisk.MUTATE, false,
                            writeArguments("Canonical order records.", CREATE_ORDER_SCHEMA)),
                    action("record-knowledge",
                            "Append farm observations and lessons atomically.",
                            HarnessRisk.MUTATE, false,
                            writeArguments("Canonical farm knowledge records.", KNOWLEDGE_SCHEMA)),
                    action("complete-tasks",
                            "Complete production tasks with worker labor, cost, and field results.",
                            HarnessRisk.MUTATE, false,
                            writeArguments("Production task actuals.", TASK_ACTUAL_SCHEMA))));

    private final InventoryService inventoryService;
    private final CustomerService customerService;
    private final HarvestService harvestService;
    private final OrderService orderService;
    private final KnowledgeService knowledgeService;
    private final ProductionStudioService productionStudioService;
    private final SeasonPlanService seasonPlanService;
    private final FarmDataImportReceiptService receiptService;
    private final FarmDataMappingService mappingService;
    private final FarmDataPageService pageService;
    private final FarmDataExportService exportService;
    private final FieldAttachmentService attachmentService;
    private final WorkerWorkflowService workerWorkflowService;
    private final ObjectMapper mapper;
    private final FarmDataCommandTransactions transactions;

    /** Retains unproxied construction for embedded callers and isolated service tests. */
    public FarmDataHarnessPlugin(
            InventoryService inventoryService,
            CustomerService customerService,
            HarvestService harvestService,
            OrderService orderService,
            KnowledgeService knowledgeService,
            ProductionStudioService productionStudioService,
            SeasonPlanService seasonPlanService,
            FarmDataImportReceiptService receiptService,
            FarmDataMappingService mappingService,
            FarmDataPageService pageService,
            FarmDataExportService exportService,
            FieldAttachmentService attachmentService,
            WorkerWorkflowService workerWorkflowService,
            ObjectMapper mapper) {
        this(inventoryService, customerService, harvestService, orderService,
                knowledgeService, productionStudioService, seasonPlanService,
                receiptService, mappingService, pageService, exportService,
                attachmentService, workerWorkflowService, mapper, new FarmDataCommandTransactions());
    }

    @Autowired
    public FarmDataHarnessPlugin(
            InventoryService inventoryService,
            CustomerService customerService,
            HarvestService harvestService,
            OrderService orderService,
            KnowledgeService knowledgeService,
            ProductionStudioService productionStudioService,
            SeasonPlanService seasonPlanService,
            FarmDataImportReceiptService receiptService,
            FarmDataMappingService mappingService,
            FarmDataPageService pageService,
            FarmDataExportService exportService,
            FieldAttachmentService attachmentService,
            WorkerWorkflowService workerWorkflowService,
            ObjectMapper mapper,
            FarmDataCommandTransactions transactions) {
        this.inventoryService = inventoryService;
        this.customerService = customerService;
        this.harvestService = harvestService;
        this.orderService = orderService;
        this.knowledgeService = knowledgeService;
        this.productionStudioService = productionStudioService;
        this.seasonPlanService = seasonPlanService;
        this.receiptService = receiptService;
        this.mappingService = mappingService;
        this.pageService = pageService;
        this.exportService = exportService;
        this.attachmentService = attachmentService;
        this.workerWorkflowService = workerWorkflowService;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.transactions = transactions;
    }

    @Override
    public HarnessPluginDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public HarnessSnapshot observe() {
        return new HarnessSnapshot(ID, Instant.now(), List.of("Farm data"), List.of(), Map.of(
                "ready", true,
                "recordTypes", RECORD_TYPES,
                "durableSourceKeys", true,
                "guidedMapping", true,
                "databaseBackedPaging", true,
                "durableRecordExports", true,
                "maxBatchSize", MAX_BATCH_SIZE,
                "maxPageSize", MAX_PAGE_SIZE));
    }

    @Override
    public synchronized HarnessExecution execute(HarnessCommand command) {
        // Export creation owns a repeatable-read transaction; wrapping it here
        // would hold a second connection while that transaction waits for one.
        // File reads and schema discovery do not need a database transaction.
        return switch (command.action()) {
            case "create-record-export", "get-record-export", "read-record-export",
                    "get-data-catalog", "suggest-field-mapping" -> executeCommand(command);
            default -> transactions.execute(() -> executeCommand(command));
        };
    }

    private HarnessExecution executeCommand(HarnessCommand command) {
        return switch (command.action()) {
            case "get-data-catalog" -> catalog();
            case "suggest-field-mapping" -> suggestFieldMapping(command);
            case "preview-mapped-records" -> previewMappedRecords(command);
            case "import-mapped-records" -> importMappedRecords(command);
            case "list-records" -> listRecords(command);
            case "read-attachment-content" -> readAttachmentContent(command);
            case "lookup-harvest-lot" -> lookupHarvestLot(command);
            case "get-production-costs" -> productionCosts(command);
            case "preview-plantings" -> previewPlantings(command);
            case "commit-plantings" -> commitPlantings(command);
            case "regenerate-tasks" -> HarnessExecution.success("Regenerated open planting work",
                    Map.of("tasks", productionStudioService.regenerateTasks(
                            requiredId(command, "plan-id"), requiredId(command, "template-id"))));
            case "update-task-status" -> updateTaskStatus(command);
            case "estimate-recipe" -> HarnessExecution.success("Calculated recipe requirements",
                    Map.of("estimate", productionStudioService.estimateRecipe(
                            requiredId(command, "recipe-id"), command.integer("count"),
                            requiredDate(command, "event-date")), "stockReserved", false));
            case "get-availability" -> HarnessExecution.success(
                    "Read planned and live availability; confirm stock before promising stems",
                    productionPage(command, productionStudioService.availability(
                            requiredDate(command, "week-of")), "availability"));
            case "list-handoffs" -> HarnessExecution.success("Read local provider handoffs",
                    productionPage(command, productionStudioService.listHandoffs(), "handoffs"));
            case "create-handoff" -> createHandoff(command);
            case "list-import-receipts" -> listImportReceipts(command);
            case "create-record-export" -> createRecordExport(command);
            case "get-record-export" -> getRecordExport(command);
            case "read-record-export" -> readRecordExport(command);
            case "upload-attachment" -> uploadAttachment(command);
            case "upsert-inventory" -> upsertInventory(command);
            case "upsert-season-settings" -> upsertSeasonSettings(command);
            case "upsert-customers" -> upsertCustomers(command);
            case "upsert-growing-templates" -> upsertGrowingTemplates(command);
            case "upsert-planting-plans" -> upsertPlantingPlans(command);
            case "upsert-floral-recipes" -> upsertFloralRecipes(command);
            case "import-harvest-history" -> importHarvestHistory(command);
            case "log-harvests" -> logHarvests(command);
            case "import-order-history" -> importOrderHistory(command);
            case "create-orders" -> createOrders(command);
            case "record-knowledge" -> recordKnowledge(command);
            case "complete-tasks" -> completeTasks(command);
            default -> throw new IllegalArgumentException(
                    "Unsupported farm data action '" + command.action() + "'");
        };
    }

    private HarnessExecution catalog() {
        Map<String, Object> types = new LinkedHashMap<>();
        types.put("inventory", catalogEntry(
                "upsert-inventory", "UPSERT_BY_NAME", INVENTORY_SCHEMA));
        types.put("customers", catalogEntry(
                "upsert-customers", "UPSERT_BY_NAME", CUSTOMER_SCHEMA));
        types.put("harvests", historyCatalogEntry(
                "import-harvest-history", "log-harvests", HARVEST_SCHEMA));
        types.put("orders", historyCatalogEntry(
                "import-order-history", "create-orders", ORDER_SCHEMA, CREATE_ORDER_SCHEMA));
        types.put("knowledge", catalogEntry(
                "record-knowledge", "APPEND", KNOWLEDGE_SCHEMA));
        types.put("production-tasks", Map.of(
                "readAction", "list-records",
                "writeAction", "complete-tasks",
                "writeBehavior", "UPDATE_ACTUALS_BY_TASK_ID",
                "writeSchema", TASK_ACTUAL_SCHEMA));
        types.put("growing-templates", catalogEntry(
                "upsert-growing-templates", "UPSERT_BY_TEMPLATE_NAME",
                GROWING_TEMPLATE_SCHEMA));
        types.put("planting-plans", catalogEntry(
                "upsert-planting-plans", "UPSERT_BY_SOURCE_KEY_OR_PLANTING_IDENTITY",
                PLANTING_PLAN_SCHEMA));
        types.put("floral-recipes", catalogEntry(
                "upsert-floral-recipes", "UPSERT_BY_DESIGN_TYPE_AND_NAME",
                FLORAL_RECIPE_SCHEMA));
        types.put("attachments", Map.of(
                "readAction", "list-records",
                "contentAction", "read-attachment-content",
                "writeAction", "upload-attachment",
                "writeBehavior", "APPEND_IMMUTABLE_CONTENT_BY_OPERATION_KEY",
                "targetTypes", List.of("PRODUCTION_TASK"),
                "maxBytes", FieldAttachmentService.MAX_ATTACHMENT_BYTES,
                "maxReadChunkBytes", FieldAttachmentService.MAX_CONTENT_CHUNK_BYTES));
        return HarnessExecution.success("Canonical farm data catalog", Map.of(
                "recordTypes", Map.copyOf(types),
                "mappingWorkflow", Map.of(
                        "suggestAction", "suggest-field-mapping",
                        "previewAction", "preview-mapped-records",
                        "importAction", "import-mapped-records",
                        "mappingDirection", "CANONICAL_FIELD_TO_SOURCE_PATH",
                        "sourceRecordsArgument", "source-records",
                        "sourceKeyFieldsArgument", "source-key-fields"),
                "durableSourceKeys", Map.of(
                        "sourceSystemArgument", "source-system",
                        "sourceKeyField", "sourceKey",
                        "resumeAction", "list-import-receipts"),
                "databaseBackedPaging", true,
                "exportWorkflow", Map.of(
                        "createAction", "create-record-export",
                        "recoverAction", "get-record-export",
                        "readAction", "read-record-export",
                        "defaultRecordTypes", RECORD_TYPES,
                        "maxChunkRecords", FarmDataExportService.MAX_CHUNK_RECORDS,
                        "maxChunkBytes", FarmDataExportService.MAX_CHUNK_BYTES),
                "maxBatchSize", MAX_BATCH_SIZE,
                "maxPageSize", MAX_PAGE_SIZE));
    }

    private HarnessExecution suggestFieldMapping(HarnessCommand command) {
        String type = mappingRecordType(command.text("record-type"));
        List<String> sourceFields = command.texts("source-fields");
        if (sourceFields.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "source-fields cannot contain more than " + MAX_BATCH_SIZE + " names");
        }
        FarmDataMappingService.MappingSuggestion suggestion = mappingService.suggest(
                schemaFor(type), sourceFields);
        return HarnessExecution.success("Suggested field mapping for " + type, Map.of(
                "recordType", type,
                "targetAction", migrationActionFor(type),
                "fieldMapping", suggestion.fieldMapping(),
                "matches", suggestion.matches(),
                "ambiguous", suggestion.ambiguous(),
                "requiredUnmapped", suggestion.requiredUnmapped(),
                "unmappedSourceFields", suggestion.unmappedSourceFields()));
    }

    private HarnessExecution previewMappedRecords(HarnessCommand command) {
        ValidatedMapping mapped = validatedMapping(command);
        return HarnessExecution.success(
                "Mapped " + mapped.preview().rowCount() + " source record(s); "
                        + mapped.invalidCount() + " invalid",
                mappingOutput(mapped, true));
    }

    private HarnessExecution importMappedRecords(HarnessCommand command) {
        ValidatedMapping mapped = validatedMapping(command);
        if (!mapped.ready()) {
            FarmDataMappingService.MappingIssue first = mapped.issues().get(0);
            throw new IllegalArgumentException(
                    "Mapped import has " + mapped.invalidCount()
                            + " invalid row(s). Run preview-mapped-records first. Row "
                            + first.row() + " field '" + first.field() + "': "
                            + first.message());
        }
        if (!mapped.preview().durableSourceKeysReady()) {
            throw new IllegalArgumentException(
                    "Mapped import requires a stable source key for every row. Map sourceKey "
                            + "or provide source-key-fields.");
        }
        String sourceSystem = command.text("source-system");
        String targetAction = migrationActionFor(mapped.preview().recordType());
        HarnessCommand canonical = new HarnessCommand(
                command.requestId(), targetAction, Map.of(
                        "source-system", sourceSystem,
                        "records", mapped.preview().records()));
        HarnessExecution written = executeCanonicalWrite(canonical);
        Map<String, Object> output = new LinkedHashMap<>(written.output());
        output.put("sourceRecordCount", mapped.preview().rowCount());
        output.put("fieldMapping", mapped.preview().fieldMapping());
        output.put("sourceKeyFields", mapped.preview().sourceKeyFields());
        output.put("targetAction", targetAction);
        output.put("validationIssues", List.of());
        return HarnessExecution.success(
                "Mapped and imported " + mapped.preview().rowCount() + " "
                        + mapped.preview().recordType() + " record(s)", output);
    }

    private ValidatedMapping validatedMapping(HarnessCommand command) {
        String type = mappingRecordType(command.text("record-type"));
        FarmDataMappingService.MappingPreview preview = mappingService.preview(
                type, schemaFor(type), optionalObject(command, "field-mapping"),
                sourceRecords(command), optionalObject(command, "defaults"),
                optionalTexts(command, "source-key-fields"));
        List<FarmDataMappingService.MappingIssue> issues = new ArrayList<>(preview.issues());
        issues.addAll(domainIssues(type, preview.records(), issues));
        Set<Integer> invalidRows = new java.util.HashSet<>();
        issues.forEach(issue -> invalidRows.add(issue.row()));
        return new ValidatedMapping(
                preview, List.copyOf(issues), invalidRows.size(), issues.isEmpty());
    }

    private List<FarmDataMappingService.MappingIssue> domainIssues(
            String type, List<Map<String, Object>> records,
            List<FarmDataMappingService.MappingIssue> structuralIssues) {
        Set<Integer> structurallyInvalid = new java.util.HashSet<>();
        structuralIssues.forEach(issue -> structurallyInvalid.add(issue.row()));
        List<FarmDataMappingService.MappingIssue> issues = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            int row = index + 1;
            if (structurallyInvalid.contains(row)) {
                continue;
            }
            try {
                validateMappedRecord(type, records.get(index));
            } catch (RuntimeException exception) {
                issues.add(new FarmDataMappingService.MappingIssue(
                        row, "$record", rootMessage(exception)));
            }
        }
        return List.copyOf(issues);
    }

    private void validateMappedRecord(String type, Map<String, Object> record) {
        switch (type) {
            case "inventory" -> mapper.convertValue(record, InventoryRow.class).toItem();
            case "customers" -> mapper.convertValue(record, CustomerRow.class).toCustomer();
            case "harvests" -> harvestService.validateBatch(List.of(
                    mapper.convertValue(record, HarvestRow.class).toHarvest()));
            case "orders" -> validateMappedOrder(
                    mapper.convertValue(record, OrderRow.class));
            case "knowledge" -> mapper.convertValue(record, KnowledgeRow.class).toEntry();
            case "production-tasks" -> validateMappedTask(
                    mapper.convertValue(record, TaskActualRow.class));
            case "growing-templates" -> mapper.convertValue(
                    record, GrowingTemplateRow.class).toTemplate();
            case "planting-plans" -> mapper.convertValue(
                    record, PlantingPlanRow.class).toPlan();
            case "floral-recipes" -> mapper.convertValue(
                    record, FloralRecipeRow.class).toRecipe();
            default -> throw new IllegalArgumentException(
                    "Unsupported mapped record type '" + type + "'");
        }
    }

    private void validateMappedOrder(OrderRow row) {
        Long customerId = resolveCustomer(row);
        Customer customer = customerService.findById(customerId).orElseThrow();
        CustomerOrder order = new CustomerOrder(
                customer, row.orderDate(), row.status(), row.notes(), row.handoffType());
        row.orderLines().forEach(order::addLine);
    }

    private void validateMappedTask(TaskActualRow row) {
        if (row.taskId() == null || row.taskId() <= 0) {
            throw new IllegalArgumentException("Production task id must be positive.");
        }
        if (productionStudioService.findTask(row.taskId()).isEmpty()) {
            throw new IllegalArgumentException(
                    "No production task with id=" + row.taskId());
        }
        new ProductionStudioService.TaskActuals(
                row.actualMinutes(), value(row.inputCost(), 0), row.fieldObservation());
    }

    private Map<String, Object> mappingOutput(
            ValidatedMapping mapped, boolean includeRecords) {
        FarmDataMappingService.MappingPreview preview = mapped.preview();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("recordType", preview.recordType());
        output.put("targetAction", migrationActionFor(preview.recordType()));
        output.put("fieldMapping", preview.fieldMapping());
        output.put("defaults", preview.defaults());
        output.put("sourceKeyFields", preview.sourceKeyFields());
        output.put("rowCount", preview.rowCount());
        output.put("validCount", preview.rowCount() - mapped.invalidCount());
        output.put("invalidCount", mapped.invalidCount());
        output.put("ready", mapped.ready());
        output.put("durableSourceKeysReady", preview.durableSourceKeysReady());
        output.put("issues", mapped.issues());
        output.put("requiredUnmapped", preview.requiredUnmapped());
        output.put("unmappedCanonicalFields", preview.unmappedCanonicalFields());
        output.put("unmappedSourceFields", preview.unmappedSourceFields());
        if (includeRecords) {
            output.put("records", preview.records());
        }
        return Map.copyOf(output);
    }

    private HarnessExecution executeCanonicalWrite(HarnessCommand command) {
        return switch (command.action()) {
            case "upsert-inventory" -> upsertInventory(command);
            case "upsert-customers" -> upsertCustomers(command);
            case "upsert-growing-templates" -> upsertGrowingTemplates(command);
            case "upsert-planting-plans" -> upsertPlantingPlans(command);
            case "upsert-floral-recipes" -> upsertFloralRecipes(command);
            case "import-harvest-history" -> importHarvestHistory(command);
            case "import-order-history" -> importOrderHistory(command);
            case "record-knowledge" -> recordKnowledge(command);
            case "complete-tasks" -> completeTasks(command);
            default -> throw new IllegalArgumentException(
                    "No mapped import action for '" + command.action() + "'");
        };
    }

    private HarnessExecution listRecords(HarnessCommand command) {
        String type = recordType(command.text("record-type"));
        int offset = optionalInteger(command, "offset", 0, 0, Integer.MAX_VALUE);
        int limit = optionalInteger(command, "limit", 100, 1, MAX_PAGE_SIZE);
        LocalDate from = null;
        LocalDate to = null;
        if ("production-tasks".equals(type)) {
            LocalDate today = LocalDate.now();
            from = optionalDate(command, "from", today.minusDays(180));
            to = optionalDate(command, "to", today.plusDays(1_640));
        }
        FarmDataPageService.RecordPage<?> page = pageService.list(
                type, offset, limit, from, to);
        return HarnessExecution.success(
                "Returned " + page.records().size() + " " + type + " record(s)",
                Map.of(
                        "recordType", type,
                        "offset", offset,
                        "limit", limit,
                        "nextOffset", page.hasMore()
                                ? (long) offset + page.records().size() : -1,
                        "total", page.total(),
                        "hasMore", page.hasMore(),
                        "records", page.records()));
    }

    private HarnessExecution readAttachmentContent(HarnessCommand command) {
        long attachmentId = requiredId(command, "attachment-id");
        int offset = optionalInteger(command, "offset", 0, 0, Integer.MAX_VALUE);
        int maxBytes = optionalInteger(
                command, "max-bytes", FieldAttachmentService.MAX_CONTENT_CHUNK_BYTES,
                1, FieldAttachmentService.MAX_CONTENT_CHUNK_BYTES);
        FieldAttachmentService.AttachmentContentChunk chunk =
                attachmentService.readChunk(attachmentId, offset, maxBytes);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("attachment", chunk.attachment());
        output.put("offset", chunk.offset());
        output.put("byteCount", chunk.bytes().length);
        output.put("totalBytes", chunk.attachment().byteSize());
        output.put("nextOffset", chunk.nextOffset());
        output.put("hasMore", chunk.hasMore());
        output.put("contentBase64", Base64.getEncoder().encodeToString(chunk.bytes()));
        return HarnessExecution.success(
                "Returned " + chunk.bytes().length + " verified attachment byte(s)", output);
    }

    private HarnessExecution lookupHarvestLot(HarnessCommand command) {
        HarvestEntry harvest = harvestService.findByLotCode(command.text("lot-code"))
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No harvest lot with code=" + command.text("lot-code")));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("recordType", "harvests");
        output.put("harvest", harvest);
        output.put("labelPath", "/api/harvest/" + harvest.getId() + "/label.pdf");
        return HarnessExecution.success(
                "Found harvest lot " + harvest.getLotCode(), output);
    }

    private HarnessExecution productionCosts(HarnessCommand command) {
        int seasonYear = command.integer("season-year");
        Double laborRate = optionalNumber(command, "labor-rate-per-hour");
        ProductionStudioService.ProductionCostReport report =
                productionStudioService.productionCosts(seasonYear, laborRate);
        return HarnessExecution.success(
                "Calculated production costs for " + seasonYear,
                Map.of(
                        "report", report,
                        "costBasis", "TASK_ACTUALS_AND_MATCHED_HARVESTS",
                        "revenueIncluded", false));
    }

    private HarnessExecution previewPlantings(HarnessCommand command) {
        var drafts = productionStudioService.draftPlantings(
                requiredId(command, "template-id"), command.integer("season-year"),
                command.text("bed-or-field"), requiredNumber(command, "bed-feet"),
                command.integer("successions"));
        return HarnessExecution.success("Review planting dates, yield, and frost warnings before saving",
                Map.of("drafts", drafts,
                        "previewFingerprint", productionStudioService.plantingFingerprint(drafts),
                        "committed", false));
    }

    private HarnessExecution commitPlantings(HarnessCommand command) {
        var committed = productionStudioService.commitPlantings(
                requiredId(command, "template-id"), command.integer("season-year"),
                command.text("bed-or-field"), requiredNumber(command, "bed-feet"),
                command.integer("successions"), command.text("preview-fingerprint"));
        return HarnessExecution.success("Saved reviewed plantings and generated work",
                Map.of("plantings", committed, "committed", true));
    }

    private HarnessExecution updateTaskStatus(HarnessCommand command) {
        ProductionTaskStatus status;
        try {
            status = ProductionTaskStatus.valueOf(command.text("status").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Status must be TODO, DONE, or SKIPPED.", exception);
        }
        return HarnessExecution.success("Updated production task status",
                Map.of("task", productionStudioService.updateTaskStatus(
                        requiredId(command, "task-id"), status)));
    }

    private HarnessExecution createHandoff(HarnessCommand command) {
        String currency = optionalText(command, "currency");
        var request = new ProductionStudioService.HandoffRequest(
                command.text("handoff-type"), command.text("provider"), command.text("status"),
                optionalText(command, "external-reference"), optionalId(command, "related-order-id"),
                optionalId(command, "related-recipe-id"), optionalNumber(command, "amount"),
                currency.isBlank() ? "USD" : currency, optionalText(command, "summary"));
        return HarnessExecution.success("Recorded a local handoff; complete external work in the provider",
                Map.of("handoff", productionStudioService.createHandoff(request),
                        "externalActionPerformed", false));
    }

    private static Map<String, Object> productionPage(
            HarnessCommand command, List<?> records, String key) {
        int offset = optionalInteger(command, "offset", 0, 0, Integer.MAX_VALUE);
        int limit = optionalInteger(command, "limit", 100, 1, MAX_PAGE_SIZE);
        int start = Math.min(offset, records.size());
        int end = start + Math.min(limit, records.size() - start);
        return Map.of(key, List.copyOf(records.subList(start, end)), "total", records.size(),
                "offset", offset, "limit", limit, "hasMore", end < records.size(),
                "nextOffset", end, "stockReserved", false);
    }

    private static long requiredId(HarnessCommand command, String name) {
        Object raw = command.arguments().get(name);
        if (!HarnessValueType.INTEGER.accepts(raw)) {
            throw new IllegalArgumentException(name + " must be a JSON integer.");
        }
        long id;
        try {
            id = raw instanceof java.math.BigInteger integer
                    ? integer.longValueExact() : ((Number) raw).longValue();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " must fit in a signed 64-bit integer.", exception);
        }
        if (id <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
        return id;
    }

    private static Long optionalId(HarnessCommand command, String name) {
        return command.arguments().containsKey(name) ? requiredId(command, name) : null;
    }

    private static double requiredNumber(HarnessCommand command, String name) {
        Double value = optionalNumber(command, name);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    private static List<HarnessArgumentSpec> plantingArguments(boolean commit) {
        List<HarnessArgumentSpec> arguments = new ArrayList<>(List.of(
                argument("template-id", HarnessValueType.INTEGER, "Saved growing template id.", true),
                argument("season-year", HarnessValueType.INTEGER, "Season year from 2000 to 2200.", true),
                argument("bed-or-field", HarnessValueType.STRING, "Farm-owned bed or field name.", true),
                argument("bed-feet", HarnessValueType.NUMBER, "Positive planted bed length in feet.", true),
                argument("successions", HarnessValueType.INTEGER, "Number of succession plantings.", true)));
        if (commit) {
            arguments.add(argument("preview-fingerprint", HarnessValueType.STRING,
                    "Exact fingerprint from the reviewed preview-plantings result.", true));
        }
        return List.copyOf(arguments);
    }

    private HarnessExecution uploadAttachment(HarnessCommand command) {
        String encoded = command.text("content-base64");
        int maxEncodedLength = 4 * ((FieldAttachmentService.MAX_ATTACHMENT_BYTES + 2) / 3);
        if (encoded.length() > maxEncodedLength) {
            throw new IllegalArgumentException("Attachment file cannot exceed 15 MiB.");
        }
        byte[] content;
        try {
            content = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "content-base64 must contain valid base64 image bytes.", exception);
        }
        long targetId = requiredId(command, "target-id");
        WorkerWorkflowService.WorkerAttachmentRequest request =
                new WorkerWorkflowService.WorkerAttachmentRequest(
                        command.text("operation-key"), command.text("target-type"),
                        targetId, command.text("filename"),
                        command.text("media-type"), optionalText(command, "caption"),
                        optionalInstant(command, "captured-at"));
        WorkerWorkflowService.WorkerActionResult result =
                workerWorkflowService.addAttachment(request, content);
        return HarnessExecution.success(
                result.replayed()
                        ? "Recovered the existing field attachment"
                        : "Stored the field attachment",
                Map.of(
                        "operationReplayed", result.replayed(),
                        "recordedAt", result.recordedAt(),
                        "attachment", result.result()));
    }

    private HarnessExecution upsertSeasonSettings(HarnessCommand command) {
        int seasonYear = command.integer("season-year");
        LocalDate springFrost = requiredDate(command, "last-spring-frost");
        LocalDate fallFrost = requiredDate(command, "first-fall-frost");
        Optional<FarmSeasonSettings> existing =
                productionStudioService.findSeasonSettings(seasonYear);
        String source = optionalText(command, "source");
        if (source.isBlank() && existing.isPresent()) {
            source = existing.orElseThrow().getSource();
        }
        Double requestedRate = optionalNumber(command, "loaded-labor-rate-per-hour");
        double laborRate = requestedRate == null
                ? existing.map(FarmSeasonSettings::getLoadedLaborRatePerHour).orElse(0d)
                : requestedRate;
        FarmSeasonSettings saved = productionStudioService.saveSeasonSettings(
                new FarmSeasonSettings(seasonYear, springFrost, fallFrost, source, laborRate));
        return HarnessExecution.success(
                "Saved production settings for " + seasonYear,
                Map.of("seasonSettings", saved));
    }

    private HarnessExecution listImportReceipts(HarnessCommand command) {
        String sourceSystem = optionalText(command, "source-system");
        String type = optionalText(command, "record-type");
        if (!type.isBlank()) {
            type = recordType(type);
        }
        int pageNumber = optionalInteger(command, "page", 0, 0, Integer.MAX_VALUE);
        int limit = optionalInteger(command, "limit", 100, 1, MAX_PAGE_SIZE);
        Page<FarmDataImportReceipt> page = receiptService.list(
                sourceSystem, type, pageNumber, limit);
        return HarnessExecution.success(
                "Returned " + page.getNumberOfElements() + " import receipt(s)", Map.of(
                        "sourceSystem", sourceSystem,
                        "recordType", type,
                        "page", pageNumber,
                        "limit", limit,
                        "total", page.getTotalElements(),
                        "totalPages", page.getTotalPages(),
                        "hasMore", page.hasNext(),
                        "receipts", List.copyOf(page.getContent())));
    }

    private HarnessExecution createRecordExport(HarnessCommand command) {
        List<String> types = command.arguments().containsKey("record-types")
                ? command.texts("record-types") : null;
        int chunkRecords = optionalInteger(
                command, "chunk-records", 100, 1,
                FarmDataExportService.MAX_CHUNK_RECORDS);
        LocalDate taskFrom = optionalDate(command, "task-from", null);
        LocalDate taskTo = optionalDate(command, "task-to", null);
        FarmDataExportService.CreatedExport created = exportService.create(
                command.text("export-key"), types, chunkRecords, taskFrom, taskTo);
        return HarnessExecution.success(
                created.replayed() ? "Recovered existing farm-data export"
                        : "Created complete farm-data export",
                Map.of("replayed", created.replayed(), "export", created.export()));
    }

    private HarnessExecution getRecordExport(HarnessCommand command) {
        FarmDataExportService.ExportSummary export = exportService.get(
                command.text("export-key"));
        return HarnessExecution.success(
                "Recovered farm-data export", Map.of("export", export));
    }

    private HarnessExecution readRecordExport(HarnessCommand command) {
        int part = optionalInteger(command, "part", 0, 0, Integer.MAX_VALUE);
        FarmDataExportService.ExportChunk chunk = exportService.read(
                command.text("export-id"), part);
        return HarnessExecution.success(
                "Read farm-data export part " + part, Map.of("chunk", chunk));
    }

    private HarnessExecution upsertInventory(HarnessCommand command) {
        List<InventoryRow> rows = rows(command, InventoryRow.class);
        List<Item> saved = new ArrayList<>();
        List<FarmDataImportReceipt> receipts = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int replayed = 0;
        for (InventoryRow row : rows) {
            SourceState source = sourceState(command, "inventory", row);
            if (source.hasReceipt() && source.matches(command.action())) {
                saved.add(inventoryService.findById(source.receipt().getTargetRecordId())
                        .orElseThrow(() -> missingTarget(source)));
                receipts.add(source.receipt());
                replayed++;
                continue;
            }
            if (source.hasReceipt()) {
                Item incoming = row.toItem();
                Item item = updateMappedTarget(source, () -> inventoryService.updateById(
                        source.receipt().getTargetRecordId(), incoming));
                saved.add(item);
                receipts.add(receiptService.update(source.receipt(), command.action(),
                        source.fingerprint(), item.getId()));
                updated++;
                continue;
            }
            boolean exists = inventoryService.findByNameIgnoreCase(row.name()).isPresent();
            Item item = inventoryService.upsertByName(row.toItem());
            saved.add(item);
            remember(source, command.action(), item.getId()).ifPresent(receipts::add);
            if (!exists) {
                created++;
            } else {
                updated++;
            }
        }
        return upsertResult("inventory", saved, created, updated, replayed, receipts);
    }

    private HarnessExecution upsertCustomers(HarnessCommand command) {
        List<CustomerRow> rows = rows(command, CustomerRow.class);
        List<Customer> saved = new ArrayList<>();
        List<FarmDataImportReceipt> receipts = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int replayed = 0;
        for (CustomerRow row : rows) {
            SourceState source = sourceState(command, "customers", row);
            if (source.hasReceipt() && source.matches(command.action())) {
                saved.add(customerService.findById(source.receipt().getTargetRecordId())
                        .orElseThrow(() -> missingTarget(source)));
                receipts.add(source.receipt());
                replayed++;
                continue;
            }
            if (source.hasReceipt()) {
                Customer incoming = row.toCustomer();
                Customer customer = updateMappedTarget(source, () -> customerService.update(
                        source.receipt().getTargetRecordId(), incoming));
                saved.add(customer);
                receipts.add(receiptService.update(source.receipt(), command.action(),
                        source.fingerprint(), customer.getId()));
                updated++;
                continue;
            }
            boolean exists = customerService.getAll().stream()
                    .anyMatch(customer -> customer.getName().equalsIgnoreCase(row.name()));
            Customer customer = customerService.upsertByName(row.toCustomer());
            saved.add(customer);
            remember(source, command.action(), customer.getId()).ifPresent(receipts::add);
            if (!exists) {
                created++;
            } else {
                updated++;
            }
        }
        return upsertResult("customers", saved, created, updated, replayed, receipts);
    }

    private HarnessExecution upsertGrowingTemplates(HarnessCommand command) {
        List<GrowingTemplateRow> rows = rows(command, GrowingTemplateRow.class);
        List<GrowingTemplate> saved = new ArrayList<>();
        List<FarmDataImportReceipt> receipts = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int replayed = 0;
        for (GrowingTemplateRow row : rows) {
            SourceState source = sourceState(command, "growing-templates", row);
            if (source.hasReceipt() && source.matches(command.action())) {
                saved.add(productionStudioService.findTemplate(
                                source.receipt().getTargetRecordId())
                        .orElseThrow(() -> missingTarget(source)));
                receipts.add(source.receipt());
                replayed++;
                continue;
            }
            GrowingTemplate incoming = row.toTemplate();
            if (source.hasReceipt()) {
                GrowingTemplate template = updateMappedTarget(source,
                        () -> productionStudioService.updateTemplate(
                                source.receipt().getTargetRecordId(), incoming));
                saved.add(template);
                receipts.add(receiptService.update(source.receipt(), command.action(),
                        source.fingerprint(), template.getId()));
                updated++;
                continue;
            }
            Optional<GrowingTemplate> existing = productionStudioService.listTemplates().stream()
                    .filter(template -> sameText(template.getName(), row.name()))
                    .findFirst();
            GrowingTemplate template = existing.isPresent()
                    ? productionStudioService.updateTemplate(existing.get().getId(), incoming)
                    : productionStudioService.createTemplate(incoming);
            saved.add(template);
            remember(source, command.action(), template.getId()).ifPresent(receipts::add);
            if (existing.isPresent()) {
                updated++;
            } else {
                created++;
            }
        }
        return upsertResult(
                "growing-templates", saved, created, updated, replayed, receipts);
    }

    private HarnessExecution upsertPlantingPlans(HarnessCommand command) {
        List<PlantingPlanRow> rows = rows(command, PlantingPlanRow.class);
        List<SeasonPlanEntry> saved = new ArrayList<>();
        List<FarmDataImportReceipt> receipts = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int replayed = 0;
        for (PlantingPlanRow row : rows) {
            SourceState source = sourceState(command, "planting-plans", row);
            if (source.hasReceipt() && source.matches(command.action())) {
                saved.add(seasonPlanService.findById(source.receipt().getTargetRecordId())
                        .orElseThrow(() -> missingTarget(source)));
                receipts.add(source.receipt());
                replayed++;
                continue;
            }
            SeasonPlanEntry incoming = row.toPlan();
            if (source.hasReceipt()) {
                SeasonPlanEntry plan = updateMappedTarget(source, () -> seasonPlanService.update(
                        source.receipt().getTargetRecordId(), incoming));
                saved.add(plan);
                receipts.add(receiptService.update(source.receipt(), command.action(),
                        source.fingerprint(), plan.getId()));
                updated++;
                continue;
            }
            Optional<SeasonPlanEntry> existing = seasonPlanService
                    .listForSeason(row.seasonYear()).stream()
                    .filter(plan -> samePlanting(plan, row))
                    .findFirst();
            SeasonPlanEntry plan = existing.isPresent()
                    ? seasonPlanService.update(existing.get().getId(), incoming)
                    : seasonPlanService.create(incoming);
            saved.add(plan);
            remember(source, command.action(), plan.getId()).ifPresent(receipts::add);
            if (existing.isPresent()) {
                updated++;
            } else {
                created++;
            }
        }
        return upsertResult("planting-plans", saved, created, updated, replayed, receipts);
    }

    private HarnessExecution upsertFloralRecipes(HarnessCommand command) {
        List<FloralRecipeRow> rows = rows(command, FloralRecipeRow.class);
        List<FloralRecipe> saved = new ArrayList<>();
        List<FarmDataImportReceipt> receipts = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int replayed = 0;
        for (FloralRecipeRow row : rows) {
            SourceState source = sourceState(command, "floral-recipes", row);
            if (source.hasReceipt() && source.matches(command.action())) {
                saved.add(productionStudioService.findRecipe(
                                source.receipt().getTargetRecordId())
                        .orElseThrow(() -> missingTarget(source)));
                receipts.add(source.receipt());
                replayed++;
                continue;
            }
            FloralRecipe incoming = row.toRecipe();
            if (source.hasReceipt()) {
                FloralRecipe recipe = updateMappedTarget(source,
                        () -> productionStudioService.updateRecipe(
                                source.receipt().getTargetRecordId(), incoming));
                saved.add(recipe);
                receipts.add(receiptService.update(source.receipt(), command.action(),
                        source.fingerprint(), recipe.getId()));
                updated++;
                continue;
            }
            Optional<FloralRecipe> existing = productionStudioService.listRecipes().stream()
                    .filter(recipe -> sameText(recipe.getName(), row.name())
                            && sameText(recipe.getDesignType(), row.designType()))
                    .findFirst();
            FloralRecipe recipe = existing.isPresent()
                    ? productionStudioService.updateRecipe(existing.get().getId(), incoming)
                    : productionStudioService.createRecipe(incoming);
            saved.add(recipe);
            remember(source, command.action(), recipe.getId()).ifPresent(receipts::add);
            if (existing.isPresent()) {
                updated++;
            } else {
                created++;
            }
        }
        return upsertResult("floral-recipes", saved, created, updated, replayed, receipts);
    }

    private HarnessExecution logHarvests(HarnessCommand command) {
        return writeHarvests(command, false);
    }

    private HarnessExecution importHarvestHistory(HarnessCommand command) {
        return writeHarvests(command, true);
    }

    private HarnessExecution writeHarvests(HarnessCommand command, boolean historical) {
        List<HarvestRow> rows = rows(command, HarvestRow.class);
        List<HarvestEntry> saved = new ArrayList<>();
        List<FarmDataImportReceipt> receipts = new ArrayList<>();
        int created = 0;
        int replayed = 0;
        for (HarvestRow row : rows) {
            SourceState source = sourceState(command, "harvests", row);
            if (existingAppend(source, command.action())) {
                saved.add(harvestService.findById(source.receipt().getTargetRecordId())
                        .orElseThrow(() -> missingTarget(source)));
                receipts.add(source.receipt());
                replayed++;
                continue;
            }
            List<HarvestEntry> written = historical
                    ? harvestService.importHistoricalBatch(List.of(row.toHarvest()))
                    : harvestService.addBatch(List.of(row.toHarvest()));
            HarvestEntry entry = written.get(0);
            saved.add(entry);
            remember(source, command.action(), entry.getId()).ifPresent(receipts::add);
            created++;
        }
        return createdResult(historical ? "historical harvests" : "harvests",
                "harvests", saved, created, replayed, receipts);
    }

    private HarnessExecution createOrders(HarnessCommand command) {
        return writeOrders(command, false);
    }

    private HarnessExecution importOrderHistory(HarnessCommand command) {
        return writeOrders(command, true);
    }

    private HarnessExecution writeOrders(HarnessCommand command, boolean historical) {
        List<OrderRow> rows = rows(command, OrderRow.class);
        List<CustomerOrder> saved = new ArrayList<>();
        List<FarmDataImportReceipt> receipts = new ArrayList<>();
        int created = 0;
        int replayed = 0;
        for (OrderRow row : rows) {
            SourceState source = sourceState(command, "orders", row);
            if (existingAppend(source, command.action())) {
                saved.add(orderService.findById(source.receipt().getTargetRecordId())
                        .orElseThrow(() -> missingTarget(source)));
                receipts.add(source.receipt());
                replayed++;
                continue;
            }
            Long customerId = resolveCustomer(row);
            CustomerOrder order = historical
                    ? orderService.importHistorical(
                            customerId, row.orderDate(), row.status(), row.notes(),
                            row.handoffType(), row.orderLines())
                    : orderService.create(
                            customerId, row.orderDate(), row.status(), row.notes(),
                            row.handoffType(), row.orderLines());
            saved.add(order);
            remember(source, command.action(), order.getId()).ifPresent(receipts::add);
            created++;
        }
        return createdResult(historical ? "historical orders" : "orders",
                "orders", saved, created, replayed, receipts);
    }

    private HarnessExecution recordKnowledge(HarnessCommand command) {
        List<KnowledgeRow> rows = rows(command, KnowledgeRow.class);
        List<KnowledgeEntry> saved = new ArrayList<>();
        List<FarmDataImportReceipt> receipts = new ArrayList<>();
        int created = 0;
        int replayed = 0;
        for (KnowledgeRow row : rows) {
            SourceState source = sourceState(command, "knowledge", row);
            if (existingAppend(source, command.action())) {
                saved.add(knowledgeService.findById(source.receipt().getTargetRecordId())
                        .orElseThrow(() -> missingTarget(source)));
                receipts.add(source.receipt());
                replayed++;
                continue;
            }
            KnowledgeEntry entry = knowledgeService.create(row.toEntry());
            saved.add(entry);
            remember(source, command.action(), entry.getId()).ifPresent(receipts::add);
            created++;
        }
        return createdResult("knowledge", "knowledge", saved,
                created, replayed, receipts);
    }

    private HarnessExecution completeTasks(HarnessCommand command) {
        List<TaskActualRow> rows = rows(command, TaskActualRow.class);
        List<ProductionTask> saved = new ArrayList<>();
        List<FarmDataImportReceipt> receipts = new ArrayList<>();
        int updated = 0;
        int replayed = 0;
        for (TaskActualRow row : rows) {
            if (row.taskId() == null) {
                throw new IllegalArgumentException("Production task id is required.");
            }
            SourceState source = sourceState(command, "production-tasks", row);
            if (existingAppend(source, command.action())) {
                saved.add(productionStudioService.findTask(
                        source.receipt().getTargetRecordId())
                        .orElseThrow(() -> missingTarget(source)));
                receipts.add(source.receipt());
                replayed++;
                continue;
            }
            ProductionTask task = productionStudioService.recordTaskActuals(row.taskId(),
                    new ProductionStudioService.TaskActuals(
                            row.actualMinutes(), value(row.inputCost(), 0),
                            text(row.fieldObservation())));
            saved.add(task);
            remember(source, command.action(), task.getId()).ifPresent(receipts::add);
            updated++;
        }
        return HarnessExecution.success("Completed " + saved.size() + " production task(s)",
                Map.of("recordType", "production-tasks",
                        "processed", saved.size(),
                        "updated", updated,
                        "replayed", replayed,
                        "records", List.copyOf(saved),
                        "receipts", List.copyOf(receipts)));
    }

    private Long resolveCustomer(OrderRow row) {
        if (row.customerId() != null) {
            return customerService.findById(row.customerId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Customer #" + row.customerId() + " was not found."))
                    .getId();
        }
        String name = text(row.customerName());
        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "Each order requires customerId or exact customerName.");
        }
        return customerService.getAll().stream()
                .filter(customer -> customer.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Customer '" + name + "' was not found. Import customers first."))
                .getId();
    }

    private <T> List<T> rows(HarnessCommand command, Class<T> type) {
        Object raw = command.arguments().get("records");
        if (!(raw instanceof Collection<?> records) || records.isEmpty()) {
            throw new IllegalArgumentException("records must contain at least one object");
        }
        if (records.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "records cannot contain more than " + MAX_BATCH_SIZE + " objects");
        }
        List<T> converted = new ArrayList<>(records.size());
        int index = 0;
        for (Object record : records) {
            try {
                converted.add(mapper.convertValue(record, type));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Record " + (index + 1)
                        + " is invalid: " + rootMessage(exception), exception);
            }
            index++;
        }
        return List.copyOf(converted);
    }

    private SourceState sourceState(
            HarnessCommand command, String recordType, SourceRow row) {
        String sourceSystem = optionalText(command, "source-system");
        String sourceKey = text(row.sourceKey());
        if (sourceSystem.isBlank() && sourceKey.isBlank()) {
            return SourceState.disabled(recordType);
        }
        if (sourceSystem.isBlank() || sourceKey.isBlank()) {
            throw new IllegalArgumentException(
                    "source-system and every record sourceKey must be supplied together");
        }
        String normalizedSystem = FarmDataImportReceipt.normalizedSourceSystem(sourceSystem);
        String normalizedKey = FarmDataImportReceipt.normalizedSourceKey(sourceKey);
        String fingerprint = fingerprint(command.action(), row);
        FarmDataImportReceipt receipt = receiptService.findForUpdate(
                normalizedSystem, recordType, normalizedKey).orElse(null);
        return new SourceState(
                normalizedSystem, recordType, normalizedKey, fingerprint, receipt);
    }

    private boolean existingAppend(SourceState source, String action) {
        if (!source.hasReceipt()) {
            return false;
        }
        if (!source.matches(action)) {
            throw new FarmDataImportConflictException(
                    "Source record '" + source.sourceSystem() + "/" + source.sourceKey()
                            + "' already maps to " + source.recordType() + " #"
                            + source.receipt().getTargetRecordId()
                            + " with different content or action.");
        }
        return true;
    }

    private Optional<FarmDataImportReceipt> remember(
            SourceState source, String action, Long targetRecordId) {
        if (!source.enabled()) {
            return Optional.empty();
        }
        return Optional.of(receiptService.record(
                source.sourceSystem(), source.recordType(), source.sourceKey(), action,
                source.fingerprint(), targetRecordId));
    }

    private <T> T updateMappedTarget(SourceState source, Supplier<T> update) {
        try {
            return update.get();
        } catch (IndexOutOfBoundsException exception) {
            // These updates lock the receipt's target before changing it. A deletion
            // remains an import conflict for corrected rows as well as exact retries.
            FarmDataImportConflictException conflict = missingTarget(source);
            conflict.initCause(exception);
            throw conflict;
        }
    }

    private FarmDataImportConflictException missingTarget(SourceState source) {
        return new FarmDataImportConflictException(
                "Source record '" + source.sourceSystem() + "/" + source.sourceKey()
                        + "' maps to missing " + source.recordType() + " #"
                        + source.receipt().getTargetRecordId() + ".");
    }

    private String fingerprint(String action, SourceRow row) {
        try {
            ObjectNode canonical = mapper.valueToTree(row);
            canonical.remove("sourceKey");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(action.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(mapper.writeValueAsBytes(canonical));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "Could not canonicalize the source record", exception);
        }
    }

    private static HarnessExecution upsertResult(
            String type, List<?> saved, int created, int updated, int replayed,
            List<FarmDataImportReceipt> receipts) {
        return HarnessExecution.success(
                "Upserted " + saved.size() + " " + type + " record(s)", Map.of(
                        "recordType", type,
                        "processed", saved.size(),
                        "created", created,
                        "updated", updated,
                        "replayed", replayed,
                        "records", List.copyOf(saved),
                        "receipts", List.copyOf(receipts)));
    }

    private static HarnessExecution createdResult(
            String label, String recordType, List<?> saved, int created, int replayed,
            List<FarmDataImportReceipt> receipts) {
        return HarnessExecution.success(
                "Processed " + saved.size() + " " + label + " record(s)", Map.of(
                        "recordType", recordType,
                        "processed", saved.size(),
                        "created", created,
                        "replayed", replayed,
                        "records", List.copyOf(saved),
                        "receipts", List.copyOf(receipts)));
    }

    private static String recordType(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!RECORD_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown record type '" + value
                    + "'. Expected one of " + RECORD_TYPES + ".");
        }
        return normalized;
    }

    private static String mappingRecordType(String value) {
        String normalized = recordType(value);
        if (!MAPPABLE_RECORD_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Record type '" + value + "' uses upload-attachment instead of "
                            + "spreadsheet field mapping.");
        }
        return normalized;
    }

    private static int optionalInteger(
            HarnessCommand command, String name, int defaultValue, int min, int max) {
        Object raw = command.arguments().get(name);
        if (raw == null) {
            return defaultValue;
        }
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        long value = number.longValue();
        if (value < min || value > max || number.doubleValue() != value) {
            throw new IllegalArgumentException(name + " must be between " + min
                    + " and " + max);
        }
        return (int) value;
    }

    private static LocalDate optionalDate(
            HarnessCommand command, String name, LocalDate defaultValue) {
        Object raw = command.arguments().get(name);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(String.valueOf(raw));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 date", exception);
        }
    }

    private static LocalDate requiredDate(HarnessCommand command, String name) {
        try {
            return LocalDate.parse(command.text(name));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 date", exception);
        }
    }

    private static Double optionalNumber(HarnessCommand command, String name) {
        Object raw = command.arguments().get(name);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a number", exception);
        }
    }

    private static Instant optionalInstant(HarnessCommand command, String name) {
        String raw = optionalText(command, name);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    name + " must be an ISO-8601 instant", exception);
        }
    }

    private static String optionalText(HarnessCommand command, String name) {
        Object raw = command.arguments().get(name);
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? failure.getMessage() : current.getMessage();
    }

    private static Map<String, Object> catalogEntry(
            String writeAction, String behavior, Map<String, Object> schema) {
        return Map.of(
                "readAction", "list-records",
                "writeAction", writeAction,
                "writeBehavior", behavior,
                "writeSchema", schema);
    }

    private static Map<String, Object> historyCatalogEntry(
            String migrationAction, String operationalAction, Map<String, Object> schema) {
        return Map.of(
                "readAction", "list-records",
                "migrationAction", migrationAction,
                "migrationBehavior", "APPEND_WITHOUT_CURRENT_INVENTORY_EFFECT",
                "operationalAction", operationalAction,
                "operationalBehavior", "APPEND_WITH_NORMAL_CURRENT_INVENTORY_EFFECTS",
                "writeSchema", schema);
    }

    private static Map<String, Object> historyCatalogEntry(
            String migrationAction, String operationalAction, Map<String, Object> migrationSchema,
            Map<String, Object> operationalSchema) {
        Map<String, Object> entry = new LinkedHashMap<>(
                historyCatalogEntry(migrationAction, operationalAction, migrationSchema));
        entry.put("operationalWriteSchema", operationalSchema);
        return Map.copyOf(entry);
    }

    private static HarnessActionSpec action(
            String name, String description, HarnessRisk risk, boolean idempotent,
            List<HarnessArgumentSpec> arguments) {
        return new HarnessActionSpec(name, description, risk,
                HarnessActionEffect.defaultFor(risk), idempotent, false, arguments);
    }

    private static HarnessArgumentSpec argument(
            String name, HarnessValueType type, String description, boolean required) {
        return new HarnessArgumentSpec(name, type, description, required);
    }

    private static HarnessArgumentSpec recordsArgument(
            String description, Map<String, Object> itemSchema) {
        return new HarnessArgumentSpec("records", HarnessValueType.ARRAY,
                description + " Maximum 500 per command.", true,
                HarnessValueType.OBJECT, itemSchema);
    }

    private static List<HarnessArgumentSpec> writeArguments(
            String description, Map<String, Object> itemSchema) {
        return List.of(
                argument("source-system", HarnessValueType.STRING,
                        "Optional upstream system name for durable per-record replay.", false),
                recordsArgument(description, itemSchema));
    }

    private static List<HarnessArgumentSpec> mappingArguments(boolean importing) {
        List<HarnessArgumentSpec> arguments = new ArrayList<>();
        arguments.add(argument("record-type", HarnessValueType.STRING,
                "Target canonical record type.", true));
        if (importing) {
            arguments.add(argument("source-system", HarnessValueType.STRING,
                    "Stable upstream workbook, app, or provider name.", true));
        }
        arguments.add(new HarnessArgumentSpec(
                "source-records", HarnessValueType.ARRAY,
                "Raw spreadsheet rows or provider objects. Maximum 500 per command.", true,
                HarnessValueType.OBJECT, SOURCE_RECORD_SCHEMA));
        arguments.add(argument("field-mapping", HarnessValueType.OBJECT,
                "Optional canonical-field to source-column/path mapping. "
                        + "Unspecified fields use deterministic suggestions.", false));
        arguments.add(argument("defaults", HarnessValueType.OBJECT,
                "Optional canonical values used when a mapped source value is blank.", false));
        arguments.add(new HarnessArgumentSpec(
                "source-key-fields", HarnessValueType.ARRAY,
                "Optional stable source columns/paths combined into each durable sourceKey.",
                false, HarnessValueType.STRING));
        return List.copyOf(arguments);
    }

    private static Map<String, Object> schemaFor(String type) {
        return switch (type) {
            case "inventory" -> INVENTORY_SCHEMA;
            case "customers" -> CUSTOMER_SCHEMA;
            case "harvests" -> HARVEST_SCHEMA;
            case "orders" -> ORDER_SCHEMA;
            case "knowledge" -> KNOWLEDGE_SCHEMA;
            case "production-tasks" -> TASK_ACTUAL_SCHEMA;
            case "growing-templates" -> GROWING_TEMPLATE_SCHEMA;
            case "planting-plans" -> PLANTING_PLAN_SCHEMA;
            case "floral-recipes" -> FLORAL_RECIPE_SCHEMA;
            default -> throw new IllegalArgumentException(
                    "Unsupported canonical schema for '" + type + "'");
        };
    }

    private static String migrationActionFor(String type) {
        return switch (type) {
            case "inventory" -> "upsert-inventory";
            case "customers" -> "upsert-customers";
            case "harvests" -> "import-harvest-history";
            case "orders" -> "import-order-history";
            case "knowledge" -> "record-knowledge";
            case "production-tasks" -> "complete-tasks";
            case "growing-templates" -> "upsert-growing-templates";
            case "planting-plans" -> "upsert-planting-plans";
            case "floral-recipes" -> "upsert-floral-recipes";
            default -> throw new IllegalArgumentException(
                    "Unsupported migration action for '" + type + "'");
        };
    }

    private static Map<?, ?> optionalObject(HarnessCommand command, String name) {
        Object raw = command.arguments().get(name);
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return values;
    }

    private static Collection<?> sourceRecords(HarnessCommand command) {
        Object raw = command.arguments().get("source-records");
        if (!(raw instanceof Collection<?> records) || records.isEmpty()) {
            throw new IllegalArgumentException(
                    "source-records must contain at least one object");
        }
        if (records.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "source-records cannot contain more than " + MAX_BATCH_SIZE + " objects");
        }
        return records;
    }

    private static List<String> optionalTexts(HarnessCommand command, String name) {
        Object raw = command.arguments().get(name);
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof Collection<?> collection)) {
            throw new IllegalArgumentException(name + " must be an array of strings");
        }
        List<String> values = new ArrayList<>();
        for (Object value : collection) {
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(
                        name + " must be an array of non-blank strings");
            }
            values.add(text.trim());
        }
        return List.copyOf(values);
    }

    private static Map.Entry<String, Object> entry(
            String name, Map<String, Object> schema) {
        return Map.entry(name, schema);
    }

    private static Map<String, Object> orderSchema(String... statuses) {
        return objectSchema(List.of("orderDate"), Map.ofEntries(
                entry("sourceKey", sourceKeyField()),
                entry("customerId", integerField("FlowersForever customer id.")),
                entry("customerName", textField(
                        "Exact customer name when customerId is unavailable.")),
                entry("orderDate", dateField("ISO-8601 order date.")),
                entry("status", enumField(statuses)),
                entry("handoffType", enumField("PICKUP", "DELIVERY", "MARKET", "UNSPECIFIED")),
                entry("notes", textField("Order notes.")),
                entry("lines", arrayField("Order product lines.", ORDER_LINE_SCHEMA))));
    }

    private static Map<String, Object> objectSchema(
            List<String> required, Map<String, Object> properties) {
        return Map.of(
                "type", "object",
                "properties", Map.copyOf(properties),
                "required", List.copyOf(required),
                "additionalProperties", false);
    }

    private static Map<String, Object> textField(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> dateField(String description) {
        return Map.of("type", "string", "format", "date", "description", description);
    }

    private static Map<String, Object> numberField(String description) {
        return Map.of("type", "number", "description", description);
    }

    private static Map<String, Object> integerField(String description) {
        return Map.of("type", "integer", "description", description);
    }

    private static Map<String, Object> booleanField(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    private static Map<String, Object> sourceKeyField() {
        return textField(
                "Stable upstream row id. Required on every row when source-system is supplied.");
    }

    private static Map<String, Object> enumField(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> arrayField(
            String description, Map<String, Object> itemSchema) {
        return Map.of("type", "array", "description", description, "items", itemSchema);
    }

    private static Map<String, Object> stringArrayField(String description) {
        return Map.of("type", "array", "description", description,
                "items", Map.of("type", "string"));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean sameText(String first, String second) {
        return text(first).equalsIgnoreCase(text(second));
    }

    private static boolean samePlanting(SeasonPlanEntry plan, PlantingPlanRow row) {
        return plan.getSeasonYear() == row.seasonYear()
                && sameText(plan.getCropName(), row.cropName())
                && sameText(plan.getVariety(), row.variety())
                && sameText(plan.getBedOrField(), row.bedOrField())
                && java.util.Objects.equals(plan.getSowDate(), row.sowDate());
    }

    private static double value(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private static int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static boolean value(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported " + type.getSimpleName()
                    + " value '" + raw + "'.", exception);
        }
    }

    private interface SourceRow {
        String sourceKey();
    }

    private record SourceState(
            String sourceSystem, String recordType, String sourceKey,
            String fingerprint, FarmDataImportReceipt receipt) {

        static SourceState disabled(String recordType) {
            return new SourceState("", recordType, "", "", null);
        }

        boolean enabled() {
            return !sourceSystem.isBlank();
        }

        boolean hasReceipt() {
            return receipt != null;
        }

        boolean matches(String action) {
            return receipt != null && receipt.matches(action, fingerprint);
        }
    }

    private record ValidatedMapping(
            FarmDataMappingService.MappingPreview preview,
            List<FarmDataMappingService.MappingIssue> issues,
            int invalidCount,
            boolean ready) {
    }

    private record InventoryRow(String sourceKey, String name, String category,
                                Double price, String unit, Double cost, Integer quantity,
                                String notes) implements SourceRow {
        Item toItem() {
            return new Item(name, category, value(price, 0), unit,
                    value(cost, 0), value(quantity, 0), notes);
        }
    }

    private record CustomerRow(String sourceKey, String name, String contactName,
                               String email, String phone, String customerType,
                               String notes) implements SourceRow {
        Customer toCustomer() {
            return new Customer(name, contactName, email, phone, customerType, notes);
        }
    }

    private record GrowingTemplateRow(
            String sourceKey, String name, String cropName, String variety,
            double spacingInches, int rowsPerBed, int sowDaysFromFrost,
            Integer transplantDaysFromFrost, int daysToHarvest,
            int harvestWindowDays, int successionDays, double stemsPerPlant,
            double germinationPercent, int soilPrepLeadDays,
            int supportDaysAfterPlanting, Boolean active, String notes) implements SourceRow {
        GrowingTemplate toTemplate() {
            return new GrowingTemplate(
                    name, cropName, variety, spacingInches, rowsPerBed,
                    sowDaysFromFrost, transplantDaysFromFrost, daysToHarvest,
                    harvestWindowDays, successionDays, stemsPerPlant,
                    germinationPercent, soilPrepLeadDays, supportDaysAfterPlanting,
                    value(active, true), notes);
        }
    }

    private record PlantingPlanRow(
            String sourceKey, int seasonYear, String cropName, String variety,
            String bedOrField, LocalDate sowDate, LocalDate transplantDate,
            LocalDate harvestStart, LocalDate harvestEnd, double bedFeet,
            int expectedStems, String status, String notes) implements SourceRow {
        SeasonPlanEntry toPlan() {
            return new SeasonPlanEntry(
                    seasonYear, cropName, variety, bedOrField, sowDate,
                    transplantDate, harvestStart, harvestEnd, bedFeet,
                    expectedStems, enumValue(
                            SeasonPlanStatus.class, status, SeasonPlanStatus.PLANNED), notes);
        }
    }

    private record RecipeIngredientRow(
            String itemName, double stemsPerDesign, int packSize,
            Double packCost, String notes) {
        FloralRecipeIngredient toIngredient() {
            return new FloralRecipeIngredient(
                    itemName, stemsPerDesign, packSize, value(packCost, 0), notes);
        }
    }

    private record FloralRecipeRow(
            String sourceKey, String name, String designType, Double salePrice,
            Integer laborMinutes, Double laborRatePerHour, Double overheadPercent,
            Double wastePercent, String notes,
            List<RecipeIngredientRow> ingredients) implements SourceRow {
        FloralRecipe toRecipe() {
            FloralRecipe recipe = new FloralRecipe(
                    name, designType, value(salePrice, 0), value(laborMinutes, 0),
                    value(laborRatePerHour, 0), value(overheadPercent, 0),
                    value(wastePercent, 0), notes);
            if (ingredients != null) {
                ingredients.stream().map(RecipeIngredientRow::toIngredient)
                        .forEach(recipe::addIngredient);
            }
            return recipe;
        }
    }

    private record HarvestRow(String sourceKey, LocalDate harvestDate, String cropName,
                              Double quantity, String unit, String bedOrField,
                              String notes, String lotCode, HarvestGrade grade,
                              Double lossQuantity, String storageLocation) implements SourceRow {
        HarvestEntry toHarvest() {
            if (quantity == null) {
                throw new IllegalArgumentException("Harvest quantity is required.");
            }
            return new HarvestEntry(
                    harvestDate, cropName, quantity, unit, bedOrField, notes,
                    lotCode, grade, value(lossQuantity, 0), storageLocation);
        }
    }

    private record OrderLineRow(String productName, Double quantity, String unit,
                                Double unitPrice) {
        OrderLine toLine() {
            if (quantity == null) {
                throw new IllegalArgumentException("Order line quantity is required.");
            }
            return new OrderLine(productName, quantity, unit, value(unitPrice, 0));
        }
    }

    private record OrderRow(String sourceKey, Long customerId, String customerName,
                            LocalDate orderDate, String status, String notes,
                            String handoffType, List<OrderLineRow> lines) implements SourceRow {
        List<OrderLine> orderLines() {
            return lines == null ? List.of() : lines.stream().map(OrderLineRow::toLine).toList();
        }
    }

    private record KnowledgeRow(
            String sourceKey, LocalDate observedOn, String summary, String kind, String cropName,
            String variety, String bedOrField, String productionStage, String observation,
            String actionTaken, String performedBy, String result, LocalDate followUpDate,
            String confidence, List<String> tags, Long harvestId, Long inventoryItemId,
            Long orderId, Long customerId) implements SourceRow {
        KnowledgeEntry toEntry() {
            KnowledgeEntry entry = new KnowledgeEntry(
                    observedOn, summary,
                    enumValue(KnowledgeKind.class, kind, KnowledgeKind.PRACTICE),
                    cropName, variety, bedOrField, productionStage, observation,
                    actionTaken, performedBy, result, followUpDate,
                    enumValue(KnowledgeConfidence.class, confidence,
                            KnowledgeConfidence.NEEDS_REVIEW),
                    tags == null ? List.of() : tags);
            entry.setHarvestId(harvestId);
            entry.setInventoryItemId(inventoryItemId);
            entry.setOrderId(orderId);
            entry.setCustomerId(customerId);
            return entry;
        }
    }

    private record TaskActualRow(String sourceKey, Long taskId, int actualMinutes,
                                 Double inputCost, String fieldObservation) implements SourceRow {
    }
}
