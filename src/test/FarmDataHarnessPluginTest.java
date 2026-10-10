package com.flowerfarm.automation.farm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessPluginKind;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessToolDefinition;
import com.flowerfarm.automation.mcp.McpProtocolHandler;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmDataImportReceipt;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FieldAttachmentTargetType;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.HarvestGrade;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.IntegrationHandoff;
import com.flowerfarm.model.KnowledgeEntry;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.time.LocalDate;
import java.time.Instant;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
@DisplayName("Domain-level AI farm data bridge")
class FarmDataHarnessPluginTest {

    @Mock InventoryService inventoryService;
    @Mock CustomerService customerService;
    @Mock HarvestService harvestService;
    @Mock OrderService orderService;
    @Mock KnowledgeService knowledgeService;
    @Mock ProductionStudioService productionStudioService;
    @Mock SeasonPlanService seasonPlanService;
    @Mock FarmDataImportReceiptService receiptService;
    @Mock FarmDataPageService pageService;
    @Mock FarmDataExportService exportService;
    @Mock FieldAttachmentService attachmentService;
    @Mock WorkerWorkflowService workerWorkflowService;

    FarmDataHarnessPlugin plugin;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        plugin = new FarmDataHarnessPlugin(
                inventoryService, customerService, harvestService, orderService,
                knowledgeService, productionStudioService, seasonPlanService, receiptService,
                new FarmDataMappingService(objectMapper), pageService, exportService,
                attachmentService, workerWorkflowService,
                objectMapper);
    }

    @Test
    void neverReturnsTheSameContinuationOffsetForAnEmptyPage() {
        doReturn(new FarmDataPageService.RecordPage<>(List.of(), 25, 25, 26))
                .when(pageService).list(eq("inventory"), eq(25), eq(25), isNull(), isNull());

        assertThatThrownBy(() -> plugin.execute(command("empty-page", "list-records", Map.of(
                "record-type", "inventory", "offset", 25, "limit", 25))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("retry this read");
    }

    @Test
    @DisplayName("publishes canonical nested JSON schemas as provider tools")
    void publishesCanonicalSchemas() {
        assertThat(plugin.descriptor().id()).isEqualTo("farm-data");
        assertThat(plugin.descriptor().kind()).isEqualTo(HarnessPluginKind.WORKFLOW);
        assertThat(plugin.descriptor().actions())
                .extracting(HarnessActionSpec::name)
                .containsExactly(
                        "get-data-catalog", "suggest-field-mapping",
                        "preview-mapped-records", "import-mapped-records",
                        "list-records", "read-attachment-content", "lookup-harvest-lot",
                        "get-season-settings", "list-order-handoffs", "count-inventory",
                        "complete-order-handoff", "get-operation-receipt",
                        "get-production-costs",
                        "preview-plantings", "commit-plantings", "regenerate-tasks",
                        "update-task-status", "estimate-recipe", "get-availability",
                        "list-handoffs", "create-handoff",
                        "list-import-receipts",
                        "create-record-export", "get-record-export", "read-record-export",
                        "upload-attachment",
                        "upsert-inventory",
                        "upsert-season-settings",
                        "upsert-customers", "upsert-growing-templates",
                        "upsert-planting-plans", "upsert-floral-recipes",
                        "import-harvest-history", "log-harvests",
                        "import-order-history", "create-orders", "record-knowledge",
                        "complete-tasks");

        HarnessActionSpec action = plugin.descriptor().action("create-orders");
        HarnessToolDefinition tool = HarnessToolDefinition.from(
                plugin.descriptor(), action, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>)
                tool.inputSchema().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> records = (Map<String, Object>) properties.get("records");
        @SuppressWarnings("unchecked")
        Map<String, Object> items = (Map<String, Object>) records.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) items.get("properties");

        assertThat(items).containsEntry("additionalProperties", false);
        assertThat(fields).containsKeys(
                "sourceKey", "customerId", "customerName", "orderDate",
                "handoffType", "lines");
        assertThat(tool.annotations().idempotentHint()).isFalse();
    }

    @Test
    @DisplayName("exposes production costs to AI with an explicit rate override")
    void reportsProductionCosts() {
        ProductionStudioService.ProductionCostReport report =
                new ProductionStudioService.ProductionCostReport(
                        2026, 30, "REQUEST_OVERRIDE", List.of(), 100, 75,
                        2, 2, 180, 90, 15, 105, 105, 1.05, 1.4, 100,
                        0, 0, 0, 0, 0, 0, 0, 0, List.of());
        when(productionStudioService.productionCosts(2026, 30d)).thenReturn(report);

        HarnessExecution result = plugin.execute(command(
                "production-costs", "get-production-costs",
                Map.of("season-year", 2026, "labor-rate-per-hour", 30)));

        assertThat(result.output())
                .containsEntry("report", report)
                .containsEntry("revenueIncluded", false)
                .containsEntry("costBasis", "TASK_ACTUALS_AND_MATCHED_HARVESTS");
        verify(productionStudioService).productionCosts(2026, 30d);
    }

    @Test
    @DisplayName("lets AI persist the season assumptions used by plans and cost reports")
    void upsertsSeasonSettings() {
        when(productionStudioService.findSeasonSettings(2026)).thenReturn(Optional.empty());
        when(productionStudioService.saveSeasonSettings(any())).thenAnswer(
                invocation -> invocation.getArgument(0));

        HarnessExecution result = plugin.execute(command(
                "season-settings", "upsert-season-settings", Map.of(
                        "season-year", 2026,
                        "last-spring-frost", "2026-04-20",
                        "first-fall-frost", "2026-10-15",
                        "source", "Farm station",
                        "loaded-labor-rate-per-hour", 28.5)));

        FarmSeasonSettings saved = (FarmSeasonSettings)
                result.output().get("seasonSettings");
        assertThat(saved.getSeasonYear()).isEqualTo(2026);
        assertThat(saved.getLoadedLaborRatePerHour()).isEqualTo(28.5);
        verify(productionStudioService).saveSeasonSettings(any(FarmSeasonSettings.class));
    }

    @Test
    @DisplayName("suggests, previews, and imports unfamiliar spreadsheet columns")
    void mapsUnfamiliarSourceRecords() {
        Map<String, Object> source = Map.of(
                "Legacy ID", "sku-17",
                "Product Name", "Mapped Dahlia",
                "On Hand", "1,250",
                "Retail Price", "$2.75");

        HarnessExecution suggestion = plugin.execute(command(
                "suggest", "suggest-field-mapping", Map.of(
                        "record-type", "inventory",
                        "source-fields", List.copyOf(source.keySet()))));
        HarnessExecution preview = plugin.execute(command(
                "preview", "preview-mapped-records", Map.of(
                        "record-type", "inventory",
                        "source-records", List.of(source),
                        "defaults", Map.of(
                                "category", "Cut flower", "unit", "stem"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> suggested = (Map<String, Object>)
                suggestion.output().get("fieldMapping");
        assertThat(suggested)
                .containsEntry("name", "Product Name")
                .containsEntry("quantity", "On Hand")
                .containsEntry("sourceKey", "Legacy ID");
        assertThat(preview.output())
                .containsEntry("ready", true)
                .containsEntry("durableSourceKeysReady", true)
                .containsEntry("invalidCount", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> mapped = ((List<Map<String, Object>>)
                preview.output().get("records")).get(0);
        assertThat(mapped)
                .containsEntry("name", "Mapped Dahlia")
                .containsEntry("quantity", 1_250d)
                .containsEntry("price", 2.75);

        when(inventoryService.findByNameIgnoreCase("Mapped Dahlia"))
                .thenReturn(Optional.empty());
        when(inventoryService.upsertByName(any())).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            item.setId(71L);
            return item;
        });
        when(receiptService.findForUpdate("legacy-workbook", "inventory", "sku-17"))
                .thenReturn(Optional.empty());
        when(receiptService.record(
                eq("legacy-workbook"), eq("inventory"), eq("sku-17"),
                eq("upsert-inventory"), any(), eq(71L)))
                .thenAnswer(invocation -> new FarmDataImportReceipt(
                        invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2), invocation.getArgument(3),
                        invocation.getArgument(4), invocation.getArgument(5)));

        HarnessExecution imported = plugin.execute(command(
                "import", "import-mapped-records", Map.of(
                        "record-type", "inventory",
                        "source-system", "legacy-workbook",
                        "source-records", List.of(source),
                        "defaults", Map.of(
                                "category", "Cut flower", "unit", "stem"))));

        assertThat(imported.output())
                .containsEntry("created", 1)
                .containsEntry("targetAction", "upsert-inventory")
                .containsEntry("sourceRecordCount", 1);
        ArgumentCaptor<Item> item = ArgumentCaptor.forClass(Item.class);
        verify(inventoryService).upsertByName(item.capture());
        assertThat(item.getValue().getQuantity()).isEqualTo(1_250);
    }

    @Test
    @DisplayName("returns the migration catalog and paged canonical records")
    void catalogsAndPagesRecords() {
        Item first = new Item("Dahlia", "Flower", 2, "stem", 0.5, 20, "");
        first.setId(1L);
        Item second = new Item("Rose", "Flower", 3, "stem", 1, 10, "");
        second.setId(2L);
        doReturn(new FarmDataPageService.RecordPage<>(List.of(second), 1, 1, 2))
                .when(pageService).list(
                        eq("inventory"), eq(1), eq(1),
                        isNull(), isNull());

        HarnessExecution catalog = plugin.execute(command(
                "catalog", "get-data-catalog", Map.of()));
        HarnessExecution page = plugin.execute(command("page", "list-records", Map.of(
                "record-type", "inventory", "offset", 1, "limit", 1)));

        assertThat(catalog.output()).containsKeys(
                "recordTypes", "maxBatchSize", "maxPageSize", "databaseBackedPaging");
        assertThat(page.output())
                .containsEntry("recordType", "inventory")
                .containsEntry("offset", 1)
                .containsEntry("nextOffset", -1L)
                .containsEntry("total", 2L)
                .containsEntry("hasMore", false);
        assertThat((List<?>) page.output().get("records"))
                .hasSize(1)
                .first().isSameAs(second);
    }

    @Test
    @DisplayName("upserts inventory and customers through their natural keys")
    void upsertsMasterData() {
        when(inventoryService.findByNameIgnoreCase("Dahlia")).thenReturn(Optional.empty());
        when(inventoryService.upsertByName(any())).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            item.setId(7L);
            return item;
        });
        Customer existing = new Customer(
                "Kitsap Market", "Old", "", "", "MARKET", "");
        existing.setId(8L);
        when(customerService.getAll()).thenReturn(List.of(existing));
        when(customerService.upsertByName(any())).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(8L);
            return customer;
        });

        HarnessExecution inventory = plugin.execute(command("inventory", "upsert-inventory",
                Map.of("records", List.of(Map.of(
                        "name", "Dahlia", "category", "Cut flower",
                        "quantity", 120, "price", 2.5)))));
        HarnessExecution customers = plugin.execute(command("customers", "upsert-customers",
                Map.of("records", List.of(Map.of(
                        "name", "Kitsap Market", "contactName", "Pat",
                        "customerType", "MARKET")))));

        assertThat(inventory.output())
                .containsEntry("created", 1)
                .containsEntry("updated", 0);
        assertThat(customers.output())
                .containsEntry("created", 0)
                .containsEntry("updated", 1);
        ArgumentCaptor<Item> item = ArgumentCaptor.forClass(Item.class);
        verify(inventoryService).upsertByName(item.capture());
        assertThat(item.getValue().getQuantity()).isEqualTo(120);
        ArgumentCaptor<Customer> customer = ArgumentCaptor.forClass(Customer.class);
        verify(customerService).upsertByName(customer.capture());
        assertThat(customer.getValue().getContactName()).isEqualTo("Pat");
    }

    @Test
    @DisplayName("writes production templates, plans, and nested recipes through services")
    void writesProductionStudioRecords() {
        when(productionStudioService.listTemplates()).thenReturn(List.of());
        when(productionStudioService.createTemplate(any())).thenAnswer(invocation -> {
            GrowingTemplate template = invocation.getArgument(0);
            template.setId(31L);
            return template;
        });
        when(seasonPlanService.listForSeason(2031)).thenReturn(List.of());
        when(seasonPlanService.create(any())).thenAnswer(invocation -> {
            SeasonPlanEntry plan = invocation.getArgument(0);
            plan.setId(32L);
            return plan;
        });
        when(productionStudioService.listRecipes()).thenReturn(List.of());
        when(productionStudioService.createRecipe(any())).thenAnswer(invocation -> {
            FloralRecipe recipe = invocation.getArgument(0);
            recipe.setId(33L);
            return recipe;
        });

        HarnessExecution templates = plugin.execute(command(
                "templates", "upsert-growing-templates", Map.of("records", List.of(
                        Map.ofEntries(
                                Map.entry("name", "Sunflower program"),
                                Map.entry("cropName", "Sunflower"),
                                Map.entry("spacingInches", 6),
                                Map.entry("rowsPerBed", 2),
                                Map.entry("sowDaysFromFrost", -28),
                                Map.entry("daysToHarvest", 70),
                                Map.entry("harvestWindowDays", 21),
                                Map.entry("successionDays", 14),
                                Map.entry("stemsPerPlant", 1.5),
                                Map.entry("germinationPercent", 90),
                                Map.entry("soilPrepLeadDays", 7),
                                Map.entry("supportDaysAfterPlanting", 14))))));
        HarnessExecution plans = plugin.execute(command(
                "plans", "upsert-planting-plans", Map.of("records", List.of(Map.ofEntries(
                        Map.entry("seasonYear", 2031),
                        Map.entry("cropName", "Sunflower"),
                        Map.entry("bedOrField", "Bed 31"),
                        Map.entry("sowDate", "2031-02-15"),
                        Map.entry("harvestStart", "2031-07-01"),
                        Map.entry("harvestEnd", "2031-07-21"),
                        Map.entry("bedFeet", 50),
                        Map.entry("expectedStems", 500),
                        Map.entry("status", "PLANTED"))))));
        HarnessExecution recipes = plugin.execute(command(
                "recipes", "upsert-floral-recipes", Map.of("records", List.of(Map.of(
                        "name", "Market bouquet",
                        "designType", "Bouquet",
                        "salePrice", 38,
                        "ingredients", List.of(Map.of(
                                "itemName", "Sunflower",
                                "stemsPerDesign", 3,
                                "packSize", 10,
                                "packCost", 12)))))));

        assertThat(templates.output()).containsEntry("created", 1);
        assertThat(plans.output()).containsEntry("created", 1);
        assertThat(recipes.output()).containsEntry("created", 1);
        ArgumentCaptor<GrowingTemplate> template =
                ArgumentCaptor.forClass(GrowingTemplate.class);
        verify(productionStudioService).createTemplate(template.capture());
        assertThat(template.getValue().isActive()).isTrue();
        ArgumentCaptor<SeasonPlanEntry> plan =
                ArgumentCaptor.forClass(SeasonPlanEntry.class);
        verify(seasonPlanService).create(plan.capture());
        assertThat(plan.getValue().getStatus()).isEqualTo(SeasonPlanStatus.PLANTED);
        ArgumentCaptor<FloralRecipe> recipe = ArgumentCaptor.forClass(FloralRecipe.class);
        verify(productionStudioService).createRecipe(recipe.capture());
        assertThat(recipe.getValue().getIngredients()).singleElement()
                .extracting(ingredient -> ingredient.getItemName())
                .isEqualTo("Sunflower");
    }

    @Test
    @DisplayName("records harvest, orders, knowledge, and task actuals through services")
    void recordsOperationalWork() {
        when(harvestService.addBatch(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(harvestService.importHistoricalBatch(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Customer customer = new Customer(
                "Kitsap Market", "Pat", "", "", "MARKET", "");
        customer.setId(8L);
        when(customerService.findById(8L)).thenReturn(Optional.of(customer));
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.of(2026, 9, 12), "DRAFT", "", "MARKET");
        order.setId(12L);
        when(orderService.create(eq(8L), any(), any(), any(), any(), any()))
                .thenReturn(order);
        when(orderService.importHistorical(eq(8L), any(), any(), any(), any(), any()))
                .thenReturn(order);
        when(knowledgeService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ProductionTask task = new ProductionTask(
                3L, 4L, LocalDate.of(2026, 9, 3),
                com.flowerfarm.model.ProductionTaskType.FIRST_HARVEST,
                com.flowerfarm.model.ProductionTaskStatus.TODO,
                "Dahlia", "Bed 2", 1, true, "Harvest");
        task.setId(21L);
        when(productionStudioService.recordTaskActuals(eq(21L), any())).thenReturn(task);

        HarnessExecution harvest = plugin.execute(command("harvest", "log-harvests",
                Map.of("records", List.of(Map.of(
                        "harvestDate", "2026-09-02", "cropName", "Dahlia",
                        "quantity", 80, "unit", "stems", "bedOrField", "Bed 2",
                        "lotCode", "KITSAP-26-0902-A", "grade", "SELECT",
                        "lossQuantity", 6, "storageLocation", "Cooler 1 / Rack B")))));
        HarnessExecution harvestHistory = plugin.execute(command(
                "harvest-history", "import-harvest-history",
                Map.of("records", List.of(Map.of(
                        "harvestDate", "2026-08-01", "cropName", "Dahlia",
                        "quantity", 40, "unit", "stems")))));
        HarnessExecution orders = plugin.execute(command("orders", "create-orders",
                Map.of("records", List.of(Map.of(
                        "customerId", 8, "orderDate", "2026-09-12",
                        "status", "DRAFT", "handoffType", "MARKET",
                        "lines", List.of(Map.of(
                                "productName", "Dahlia", "quantity", 10,
                                "unit", "stem", "unitPrice", 2.5)))))));
        HarnessExecution orderHistory = plugin.execute(command(
                "order-history", "import-order-history",
                Map.of("records", List.of(Map.of(
                        "customerId", 8, "orderDate", "2026-08-01",
                        "status", "FULFILLED", "handoffType", "MARKET",
                        "lines", List.of(Map.of(
                                "productName", "Dahlia", "quantity", 5,
                                "unit", "stem", "unitPrice", 2.25)))))));
        HarnessExecution knowledge = plugin.execute(command("knowledge", "record-knowledge",
                Map.of("records", List.of(Map.of(
                        "observedOn", "2026-09-01", "summary", "Strong stems",
                        "observation", "Bed 2 responded well", "kind", "PRACTICE")))));
        HarnessExecution tasks = plugin.execute(command("tasks", "complete-tasks",
                Map.of("records", List.of(Map.of(
                        "taskId", 21, "actualMinutes", 55, "inputCost", 4.75,
                        "fieldObservation", "Cut before heat")))));

        assertThat(harvest.output()).containsEntry("processed", 1);
        assertThat(harvestHistory.output()).containsEntry("processed", 1);
        assertThat(orders.output()).containsEntry("processed", 1);
        assertThat(orderHistory.output()).containsEntry("processed", 1);
        assertThat(knowledge.output()).containsEntry("processed", 1);
        assertThat(tasks.output()).containsEntry("processed", 1);
        ArgumentCaptor<List<HarvestEntry>> harvestRows = ArgumentCaptor.forClass(List.class);
        verify(harvestService).addBatch(harvestRows.capture());
        assertThat(harvestRows.getValue().get(0)).satisfies(row -> {
            assertThat(row.getCropName()).isEqualTo("Dahlia");
            assertThat(row.getLotCode()).isEqualTo("KITSAP-26-0902-A");
            assertThat(row.getGrade()).isEqualTo(HarvestGrade.SELECT);
            assertThat(row.getLossQuantity()).isEqualTo(6);
            assertThat(row.getStorageLocation()).isEqualTo("Cooler 1 / Rack B");
        });
        verify(harvestService).importHistoricalBatch(any());
        ArgumentCaptor<List<OrderLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(orderService).create(eq(8L), eq(LocalDate.of(2026, 9, 12)),
                eq("DRAFT"), eq(null), eq("MARKET"), lines.capture());
        assertThat(lines.getValue()).singleElement()
                .extracting(OrderLine::getProductName).isEqualTo("Dahlia");
        verify(orderService).importHistorical(eq(8L), eq(LocalDate.of(2026, 8, 1)),
                eq("FULFILLED"), eq(null), eq("MARKET"), any());
        ArgumentCaptor<KnowledgeEntry> entry = ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(knowledgeService).create(entry.capture());
        assertThat(entry.getValue().getSummary()).isEqualTo("Strong stems");
        ArgumentCaptor<ProductionStudioService.TaskActuals> actuals =
                ArgumentCaptor.forClass(ProductionStudioService.TaskActuals.class);
        verify(productionStudioService).recordTaskActuals(eq(21L), actuals.capture());
        assertThat(actuals.getValue().actualMinutes()).isEqualTo(55);
    }

    @Test
    @DisplayName("looks up an exact harvest lot for a scanner or AI")
    void looksUpHarvestLot() {
        HarvestEntry entry = new HarvestEntry(
                LocalDate.of(2026, 9, 2), "Dahlia", 80, "stems", "Bed 2", "",
                "KITSAP-26-0902-A", HarvestGrade.SELECT, 6, "Cooler 1 / Rack B");
        entry.setId(77L);
        when(harvestService.findByLotCode("kitsap-26-0902-a"))
                .thenReturn(Optional.of(entry));

        HarnessExecution result = plugin.execute(command(
                "lot", "lookup-harvest-lot", Map.of(
                        "lot-code", "kitsap-26-0902-a")));

        assertThat(result.output())
                .containsEntry("recordType", "harvests")
                .containsEntry("harvest", entry)
                .containsEntry("labelPath", "/api/harvest/77/label.pdf");
    }

    @Test
    @DisplayName("rejects unknown columns and oversized batches before service calls")
    void rejectsBadMigrationRows() {
        assertThatThrownBy(() -> plugin.execute(command("unknown", "upsert-inventory",
                Map.of("records", List.of(Map.of(
                        "name", "Dahlia", "spreadsheetTypo", 2))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Record 1 is invalid")
                .hasMessageContaining("spreadsheetTypo");

        List<Map<String, Object>> records = java.util.stream.IntStream.rangeClosed(1, 501)
                .mapToObj(index -> Map.<String, Object>of("name", "Crop " + index))
                .toList();
        assertThatThrownBy(() -> plugin.execute(command("large", "upsert-inventory",
                Map.of("records", records))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than 500");
    }

    @Test
    void preservesFullWidthProductionIdsAndRejectsOverflowOrFractionalIds() {
        ProductionTask task = mock(ProductionTask.class);
        when(productionStudioService.updateTaskStatus(anyLong(), eq(ProductionTaskStatus.TODO)))
                .thenReturn(task);
        for (Object id : List.of(1L + Integer.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE))) {
            HarnessExecution result = plugin.execute(command("wide-id-" + id, "update-task-status",
                    Map.of("task-id", id, "status", "TODO")));
            assertThat(result.output().get("task")).isSameAs(task);
        }
        for (Object invalid : List.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE), 0L, -1L, 1.5, "1")) {
            assertThatThrownBy(() -> plugin.execute(command("invalid-id", "update-task-status",
                    Map.of("task-id", invalid, "status", "TODO"))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("task-id");
        }
        verify(productionStudioService).updateTaskStatus(1L + Integer.MAX_VALUE, ProductionTaskStatus.TODO);
        verify(productionStudioService).updateTaskStatus(Long.MAX_VALUE, ProductionTaskStatus.TODO);
        verifyNoMoreInteractions(productionStudioService);
    }

    @Test
    void copiesProductionPagesWithoutRetainingTheWholeServiceList() {
        IntegrationHandoff first = mock(IntegrationHandoff.class);
        IntegrationHandoff second = mock(IntegrationHandoff.class);
        List<IntegrationHandoff> source = new ArrayList<>(List.of(first, second));
        when(productionStudioService.listHandoffs()).thenReturn(source);
        HarnessExecution result = plugin.execute(command("handoff-page", "list-handoffs",
                Map.of("offset", 1, "limit", 1)));
        source.clear();

        assertThat(result.output().get("handoffs")).asList().containsExactly(second);
        assertThat(result.output()).containsEntry("total", 2)
                .containsEntry("nextOffset", 2).containsEntry("hasMore", false);
    }

    @Test
    void readsAttachmentContentWithFullWidthIds() {
        for (Object id : List.of(1L + Integer.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE))) {
            long expected = ((Number) id).longValue();
            var attachment = attachmentRecord(expected);
            when(attachmentService.readChunk(expected, 0, 2)).thenReturn(
                    new FieldAttachmentService.AttachmentContentChunk(attachment, 0, new byte[]{1, 2}, -1));

            HarnessExecution result = plugin.execute(command("wide-read-" + id, "read-attachment-content",
                    Map.of("attachment-id", id, "max-bytes", 2)));

            assertThat(result.output()).containsEntry("attachment", attachment)
                    .containsEntry("contentBase64", "AQI=").containsEntry("hasMore", false);
            verify(attachmentService).readChunk(expected, 0, 2);
        }
        verifyNoMoreInteractions(attachmentService);
    }

    @Test
    void uploadsAttachmentToFullWidthTargetIds() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        for (Object id : List.of(1L + Integer.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE))) {
            long expected = ((Number) id).longValue();
            var request = new WorkerWorkflowService.WorkerAttachmentRequest(
                    "photo-" + id, "PRODUCTION_TASK", expected, "crop.png", "image/png", "", null);
            when(workerWorkflowService.addAttachment(eq(request), any(byte[].class))).thenReturn(
                    new WorkerWorkflowService.WorkerActionResult("ATTACHMENT", false, Instant.EPOCH,
                            mapper.valueToTree(attachmentRecord(expected))));

            HarnessExecution result = plugin.execute(command("wide-upload-" + id, "upload-attachment",
                    uploadArguments(id, "photo-" + id)));

            assertThat(result.success()).isTrue();
            verify(workerWorkflowService).addAttachment(request, new byte[]{1, 2});
        }
        verifyNoMoreInteractions(workerWorkflowService);
    }

    @Test
    void rejectsInvalidAttachmentIdsBeforeCallingServices() {
        for (Object id : List.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE), 0L, -1L, 1.0, 1.5, "1")) {
            assertThatThrownBy(() -> plugin.execute(command("bad-read", "read-attachment-content",
                    Map.of("attachment-id", id))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("attachment-id");
            assertThatThrownBy(() -> plugin.execute(command("bad-upload", "upload-attachment",
                    uploadArguments(id, "bad-photo"))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("target-id");
        }
        verifyNoInteractions(attachmentService, workerWorkflowService);
    }

    @Test
    void retainsExactAttachmentIdsAndReplayAcrossRestAndMcp() throws Exception {
        long id = Long.MAX_VALUE;
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken("attachment-wire-token-123456");
        properties.setAllowSensitiveRead(true);
        properties.setAllowMutations(true);
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties, mapper);
        // Standalone MockMvc skips Boot's converters, and Spring Framework 7 would pick Jackson 3 by
        // default. Use the Jackson 2 converter the app configures (preferred-json-mapper=jackson2).
        var mvc = standaloneSetup(new HarnessController(gateway))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
        McpProtocolHandler mcp = new McpProtocolHandler(gateway, properties, mapper);
        when(attachmentService.readChunk(id, 0, 2)).thenReturn(
                new FieldAttachmentService.AttachmentContentChunk(attachmentRecord(id), 0, new byte[]{1, 2}, -1));
        when(workerWorkflowService.addAttachment(any(), any(byte[].class))).thenReturn(
                new WorkerWorkflowService.WorkerActionResult("ATTACHMENT", false, Instant.EPOCH,
                        mapper.valueToTree(attachmentRecord(id))));

        for (String action : List.of("read-attachment-content", "upload-attachment")) {
            Map<String, Object> arguments = action.equals("read-attachment-content")
                    ? Map.of("attachment-id", id, "max-bytes", 2) : uploadArguments(id, "wire-photo");
            String restBody = mapper.writeValueAsString(command("rest-" + action, action, arguments));
            String mcpBody = mapper.writeValueAsString(Map.of("jsonrpc", "2.0", "id", "mcp-" + action,
                    "method", "tools/call", "params", Map.of("name", "farm-data__" + action,
                            "arguments", arguments, "_meta", Map.of(
                                    "io.modelcontextprotocol/protocolVersion", McpProtocolHandler.CURRENT_PROTOCOL_VERSION,
                                    "io.modelcontextprotocol/clientCapabilities", Map.of()))));
            for (boolean replay : List.of(false, true)) {
                String response = mvc.perform(post("/api/harness/farm-data/commands")
                                .header(HarnessController.TOKEN_HEADER, properties.getToken())
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(restBody))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                var restResult = mapper.readTree(response);
                var mcpResponse = mcp.handle(mapper.readTree(mcpBody));
                assertThat(mcpResponse.path("result").path("isError").asBoolean()).isFalse();
                var mcpResult = mcpResponse.path("result").path("structuredContent");
                for (var result : List.of(restResult, mcpResult)) {
                    assertThat(result.path("success").asBoolean()).isTrue();
                    assertThat(result.path("replayed").asBoolean()).isEqualTo(replay);
                    assertThat(result.path("output").path("attachment").path("id").longValue()).isEqualTo(id);
                    assertThat(result.path("output").path("attachment").path("targetRecordId").longValue())
                            .isEqualTo(id);
                }
            }
        }
        verify(attachmentService, org.mockito.Mockito.times(2)).readChunk(id, 0, 2);
        var requests = ArgumentCaptor.forClass(WorkerWorkflowService.WorkerAttachmentRequest.class);
        verify(workerWorkflowService, org.mockito.Mockito.times(2)).addAttachment(requests.capture(), eq(new byte[]{1, 2}));
        assertThat(requests.getAllValues()).extracting(WorkerWorkflowService.WorkerAttachmentRequest::targetId)
                .containsExactly(id, id);
        verifyNoMoreInteractions(attachmentService, workerWorkflowService);
    }

    private static Map<String, Object> uploadArguments(Object id, String key) {
        return Map.of("operation-key", key, "target-type", "PRODUCTION_TASK", "target-id", id,
                "filename", "crop.png", "media-type", "image/png", "content-base64", "AQI=");
    }

    private static FieldAttachmentService.AttachmentRecord attachmentRecord(long id) {
        return new FieldAttachmentService.AttachmentRecord(id, FieldAttachmentTargetType.PRODUCTION_TASK,
                id, "crop.png", "image/png", 2, "0".repeat(64), "", Instant.EPOCH, Instant.EPOCH);
    }

    private static HarnessCommand command(
            String requestId, String action, Map<String, Object> arguments) {
        return new HarnessCommand(requestId, action, arguments);
    }
}
