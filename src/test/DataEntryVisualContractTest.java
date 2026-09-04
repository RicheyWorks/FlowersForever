package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.AddItemTab;
import com.flowerfarm.gui.tabs.CrmTab;
import com.flowerfarm.gui.tabs.DashboardTab;
import com.flowerfarm.gui.tabs.EventServicesTab;
import com.flowerfarm.gui.tabs.HarvestLogTab;
import com.flowerfarm.gui.tabs.InventoryTab;
import com.flowerfarm.gui.tabs.InventoryWorkspaceTab;
import com.flowerfarm.gui.tabs.IrrigationInfoTab;
import com.flowerfarm.gui.tabs.KnowledgeLogTab;
import com.flowerfarm.gui.tabs.MarketDayTab;
import com.flowerfarm.gui.tabs.PricingInfoTab;
import com.flowerfarm.gui.tabs.ReportsTab;
import com.flowerfarm.gui.tabs.ReferenceTab;
import com.flowerfarm.gui.tabs.RoseVarietiesTab;
import com.flowerfarm.gui.tabs.PnwPlantGuideTab;
import com.flowerfarm.gui.tabs.RoseVisualizerTab;
import com.flowerfarm.gui.tabs.SyncHistoryTab;
import com.flowerfarm.gui.tabs.SeasonPlannerTab;
import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.gui.tabs.TrendAnalysisTab;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import com.flowerfarm.service.SeasonPlanService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Rendered desktop data-entry contracts")
class DataEntryVisualContractTest {

    private static final Path OUTPUT = Path.of("target", "ui-previews");

    @BeforeAll
    static void createOutputDirectory() throws Exception {
        FlatLightLaf.setup();
        FarmUi.installThemeDefaults();
        Files.createDirectories(OUTPUT);
    }

    @Test
    @DisplayName("priority data-entry screens render at practical desktop sizes")
    void rendersPriorityScreens() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        HarvestService harvest = mock(HarvestService.class);
        MarketDayPackingService marketDay = mock(MarketDayPackingService.class);
        SyncHistoryService history = mock(SyncHistoryService.class);
        KnowledgeService knowledge = mock(KnowledgeService.class);
        SeasonPlanService seasonPlan = mock(SeasonPlanService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "sam@kitsapblooms.example", "360-555-0184", "FLORIST",
                "Thursday pickup");
        CustomerOrder customerOrder = new CustomerOrder(
                customer, LocalDate.now().minusDays(2), "CONFIRMED", "AM pickup");
        customerOrder.setId(42L);
        customerOrder.addLine(new OrderLine("Nootka Rose", 40, "stems", 4.50));
        customerOrder.addLine(new OrderLine("Dahlia Mix", 6, "bunches", 18.00));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(customerOrder));
        when(harvest.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(List.of(
                        new HarvestEntry(31L, LocalDate.now(), "Nootka Rose", 72,
                                "stems", "Field A", "Cut before 8 AM"),
                        new HarvestEntry(32L, LocalDate.now().minusDays(1), "Dahlia Mix", 14,
                                "bunches", "Field B", "Market grade"),
                        new HarvestEntry(33L, LocalDate.now().minusDays(2), "Sweet Pea", 48,
                                "stems", "Tunnel 2", "Keep cool")));
        when(harvest.totalsByCrop()).thenReturn(Map.of(
                "Nootka Rose", 72.0, "Dahlia Mix", 14.0, "Sweet Pea", 48.0));
        when(harvest.totalsByCrop(anyList())).thenReturn(Map.of(
                "Nootka Rose", 72.0, "Dahlia Mix", 14.0, "Sweet Pea", 48.0));

        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Nootka Rose", "Flowers/Plants", 4.50, "Per Stem", 1.10, 42,
                        "Field A"),
                new Item("Dahlia Mix", "Flowers/Plants", 3.25, "Per Stem", 0.80, 8,
                        "Field B"),
                new Item("Market Bouquet", "Flowers/Plants", 28.00, "Per Unit", 9.00, 14,
                        "Saturday market"),
                new Item("Floral Snips", "Tools/Equipment", 18.00, "Per Unit", 8.00, 5,
                        "Packing shed")));
        when(inventory.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(4, 1043.00, 326.20, 2, 69, 10));
        when(harvest.totalQuantityLast7Days()).thenReturn(386.0);
        when(harvest.totalQuantityPrior7Days()).thenReturn(312.0);
        when(harvest.dailyQuantitiesLast7Days())
                .thenReturn(new double[]{38, 45, 72, 40, 66, 54, 71});
        when(orders.weekRevenueSummary()).thenReturn(new OrderService.WeekRevenueSummary(
                LocalDate.now().minusDays(6), LocalDate.now(), 1268.00, 640.00, 225.00,
                9, 4, 2));
        when(orders.realizedRevenuePrior7Days()).thenReturn(980.00);
        when(orders.dailyRevenueLast7Days())
                .thenReturn(new double[]{120, 185, 0, 268, 210, 160, 325});
        LocalDate marketDate = LocalDate.now();
        MarketDayPackingService.MarketDayPlan marketPlan =
                new MarketDayPackingService.MarketDayPlan(
                        marketDate, marketDate, marketDate, "CONFIRMED", 2, 485.00,
                        List.of(new MarketDayPackingService.CustomerPack(
                                10L, "Kitsap Blooms", "FLORIST", "CONFIRMED",
                                marketDate, "AM pickup", List.of(), 285.00),
                                new MarketDayPackingService.CustomerPack(
                                        11L, "Saturday Stall", "MARKET", "CONFIRMED",
                                        marketDate, "Load by 7", List.of(), 200.00)),
                        List.of(new MarketDayPackingService.ProductNeed(
                                "Nootka Rose", "stems", 80, 96, false, 0),
                                new MarketDayPackingService.ProductNeed(
                                        "Dahlia Mix", "bunches", 12, 20, false, 0)),
                        0,
                        "MARKET DAY PACKING LIST\n\nPICK LIST\n"
                                + "Nootka Rose        80 stems      stock 96\n"
                                + "Dahlia Mix         12 bunches    stock 20\n\n"
                                + "CUSTOMER LOAD-OUT\nKitsap Blooms — AM pickup\n"
                                + "Saturday Stall — Load by 7");
        when(marketDay.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(marketPlan);
        List<SyncHistoryEntry> activities = List.of(
                new SyncHistoryEntry("square", "IMPORT", true,
                        "Imported Saturday market sales", "42 sales records", 42),
                new SyncHistoryEntry("airtable", "SYNC", false,
                        "Customer sync needs attention", "Authentication expired", 0));
        when(history.filter(anyString(), anyString(), nullable(Boolean.class),
                anyString(), anyInt())).thenReturn(activities);
        when(history.stats(anyList())).thenReturn(
                new SyncHistoryService.HistoryStats(2, 1, 1));
        when(history.distinctConnectors(anyInt())).thenReturn(List.of("square", "airtable"));
        when(history.distinctOperations(anyInt())).thenReturn(List.of("IMPORT", "SYNC"));
        KnowledgeEntry mildewLesson = new KnowledgeEntry(
                LocalDate.now().minusYears(1),
                "Powdery mildew slowed after earlier watering",
                KnowledgeKind.PROBLEM,
                "Nootka Rose", "", "Bed A", "Budding",
                "White patches returned after warm nights and late overhead watering.",
                "Removed affected leaves, increased spacing, and moved watering before 9 AM.",
                "Field crew",
                "No new patches appeared during the following seven-day check.",
                LocalDate.now().minusYears(1).plusDays(14),
                KnowledgeConfidence.PROMISING,
                List.of("mildew", "irrigation", "timing"));
        mildewLesson.setId(51L);
        KnowledgeEntry marketLesson = new KnowledgeEntry(
                LocalDate.now().minusDays(3),
                "Saturday customers asked for mixed rose bunches",
                KnowledgeKind.OPPORTUNITY,
                "Rose", "Mixed", "Market", "Market feedback",
                "Several customers wanted one ready-priced bunch with three rose colors.",
                "Reserved enough stems for a six-bunch trial at the next market.",
                "Market lead", "Trial is scheduled; sales result is not known yet.",
                LocalDate.now(), KnowledgeConfidence.NEEDS_REVIEW,
                List.of("market", "customer-request", "trial"));
        marketLesson.setId(52L);
        when(knowledge.search(any())).thenReturn(List.of(marketLesson, mildewLesson));
        when(knowledge.distinctTags()).thenReturn(List.of(
                "customer-request", "irrigation", "market", "mildew", "timing", "trial"));
        int seasonYear = LocalDate.now().getYear();
        List<SeasonPlanEntry> plantingBlocks = List.of(
                planting(61L, seasonYear, "Nootka Rose", "Schoener's Nutkana", "Bed A",
                        2, 12, 4, 1, 6, 15, 9, 20, 80, 640,
                        SeasonPlanStatus.HARVESTING),
                planting(62L, seasonYear, "Dahlia", "Cafe au Lait", "Bed B",
                        3, 5, 5, 12, 7, 20, 10, 10, 60, 480,
                        SeasonPlanStatus.PLANTED),
                planting(63L, seasonYear, "Sweet Pea", "Spencer Mix", "Tunnel 2",
                        1, 20, 3, 1, 5, 10, 7, 15, 45, 360,
                        SeasonPlanStatus.COMPLETE),
                planting(64L, seasonYear, "Sunflower", "ProCut Plum", "Bed B",
                        5, 18, 6, 2, 7, 28, 9, 5, 100, 900,
                        SeasonPlanStatus.SOWN));
        SeasonPlanService.SeasonSummary planSummary = new SeasonPlanService.SeasonSummary(
                4, 3, 3, 4, 285, 2_380,
                LocalDate.of(seasonYear, 5, 10), LocalDate.of(seasonYear, 10, 10));
        List<SeasonPlanService.PlanPerformance> planPerformance = List.of(
                performance(plantingBlocks.get(0), 410, 390,
                        SeasonPlanService.PlanPace.ON_TRACK),
                performance(plantingBlocks.get(1), 180, 220,
                        SeasonPlanService.PlanPace.ON_TRACK),
                performance(plantingBlocks.get(2), 372, 360,
                        SeasonPlanService.PlanPace.ON_TRACK),
                performance(plantingBlocks.get(3), 140, 240,
                        SeasonPlanService.PlanPace.BEHIND));
        SeasonPlanService.BedConflict bedConflict = new SeasonPlanService.BedConflict(
                plantingBlocks.get(1), plantingBlocks.get(3),
                LocalDate.of(seasonYear, 6, 2), LocalDate.of(seasonYear, 9, 5));
        when(seasonPlan.analyze(anyInt())).thenReturn(new SeasonPlanService.SeasonAnalysis(
                plantingBlocks, planSummary, planPerformance, List.of(bedConflict), List.of(),
                weeklyProduction(seasonYear), 1_102, 24, 1));
        when(seasonPlan.availableSeasons()).thenReturn(List.of(seasonYear));

        AddItemTab addItem = new AddItemTab(inventory, new Host());
        CrmTab crm = new CrmTab(customers, orders, null, new Host());
        HarvestLogTab harvestLog = new HarvestLogTab(harvest, new Host());
        InventoryTab inventoryTab = new InventoryTab(inventory, new Host());
        MarketDayTab marketDayTab = new MarketDayTab(marketDay, new Host());
        DashboardTab dashboard = new DashboardTab(inventory, harvest, orders,
                null, null, null, null, new Host());
        ReportsTab reports = new ReportsTab(mock(ReportService.class), new Host());
        SyncHistoryTab activity = new SyncHistoryTab(history, new Host());
        SyncHistoryTab activityViewer = new SyncHistoryTab(history, new ReadOnlyHost());
        KnowledgeLogTab knowledgeLog = new KnowledgeLogTab(knowledge, new Host());
        SeasonPlannerTab seasonPlanner = new SeasonPlannerTab(seasonPlan, new Host());
        TrendService trendService = mock(TrendService.class);
        when(trendService.analyzeQuantityTrend()).thenReturn(new TrendService.TrendResult(
                82.4,
                "FORECAST READY\n\nProjected next-period quantity: 82 stems\n"
                        + "Direction: steady growth\nConfidence note: use alongside harvest plans.",
                null, "Nootka Rose", "stems", 6, 78,
                LocalDate.now().minusWeeks(1), LocalDate.now().plusWeeks(1),
                List.of(
                        new TrendService.TrendPoint(LocalDate.now().minusWeeks(6), 48, 50),
                        new TrendService.TrendPoint(LocalDate.now().minusWeeks(5), 55, 55),
                        new TrendService.TrendPoint(LocalDate.now().minusWeeks(4), 61, 60),
                        new TrendService.TrendPoint(LocalDate.now().minusWeeks(3), 64, 65),
                        new TrendService.TrendPoint(LocalDate.now().minusWeeks(2), 72, 70),
                        new TrendService.TrendPoint(LocalDate.now().minusWeeks(1), 78, 75)),
                5, 45, 0.91));
        TrendAnalysisTab trends = new TrendAnalysisTab(trendService);
        IrrigationAdvisorService irrigationService = mock(IrrigationAdvisorService.class);
        when(irrigationService.advise(true)).thenReturn(new IrrigationAdvisorService.IrrigationAdvice(
                "Port Orchard / Kitsap County, WA", "LIVE", LocalDate.now().toString(),
                IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                IrrigationAdvisorService.Priority.HIGH,
                "Deep-water active beds before the afternoon heat.",
                List.of("Run drip irrigation before 10 AM.",
                        "Check Field B again near sunset."),
                List.of("Field A", "Field B"), 0.1, 1.2, 1.1, 86.0, 58.0,
                "Dry maritime summer pattern; protect freshly cut rows."));
        IrrigationInfoTab irrigation = new IrrigationInfoTab(irrigationService, new Host());
        IrrigationAdvisorService failingIrrigation = mock(IrrigationAdvisorService.class);
        when(failingIrrigation.advise(true))
                .thenThrow(new IllegalStateException("Forecast service timed out"));
        IrrigationInfoTab irrigationError = new IrrigationInfoTab(failingIrrigation, new Host());
        RoseVarietiesTab roseGuide = new RoseVarietiesTab(inventory, new Host());
        PnwPlantGuideTab plantGuide = new PnwPlantGuideTab();
        EventServicesTab eventServices = new EventServicesTab(new Host());
        PricingInfoTab pricing = new PricingInfoTab();
        RoseVisualizerTab roseVisualizer = new RoseVisualizerTab(inventory, new Host());
        ReferenceTab reference = new ReferenceTab(
                plantGuide, eventServices, roseGuide, pricing, roseVisualizer);
        InventoryWorkspaceTab inventoryWorkspace =
                new InventoryWorkspaceTab(inventoryTab, addItem);

        crm.getUIComponent();
        harvestLog.getUIComponent();
        inventoryTab.getUIComponent();
        marketDayTab.getUIComponent();
        crm.initialize();
        awaitButtonEnabled(crm.getUIComponent(), "Refresh CRM");
        harvestLog.initialize();
        awaitButtonEnabled(harvestLog.getUIComponent(), "Refresh");
        inventoryTab.initialize();
        awaitButtonEnabled(inventoryTab.getUIComponent(), "Refresh");
        marketDayTab.initialize();
        awaitButtonEnabled(marketDayTab.getUIComponent(), "Build packing list");
        activity.getUIComponent();
        activity.initialize();
        awaitButtonEnabled(activity.getUIComponent(), "Refresh");
        activityViewer.getUIComponent();
        activityViewer.initialize();
        awaitButtonEnabled(activityViewer.getUIComponent(), "Refresh");
        activityViewer.applyRolePermissions(false);
        knowledgeLog.getUIComponent();
        knowledgeLog.initialize();
        awaitButtonEnabled(knowledgeLog.getUIComponent(), "Search lessons");
        seasonPlanner.getUIComponent();
        seasonPlanner.initialize();
        awaitNamedLabelPrefix(seasonPlanner.getUIComponent(),
                "seasonPlanner.status", "1,102 actual / 2,380 planned stems");
        roseVisualizer.getUIComponent();
        roseVisualizer.initialize();
        awaitLabel(roseVisualizer.getUIComponent(), "3 linkable products ready");
        irrigation.getUIComponent();
        irrigation.initialize();
        awaitLabel(irrigation.getUIComponent(), "Mode: LIVE (Open-Meteo)");
        irrigationError.getUIComponent();
        irrigationError.initialize();
        awaitLabel(irrigationError.getUIComponent(), "Weather unavailable");
        trends.getUIComponent();
        trends.runAnalysis();
        awaitLabel(trends.getUIComponent(), "Forecast ready");
        render("add-item", addItem.getUIComponent(), new Dimension(1060, 700));
        assertThat(textField(addItem.getUIComponent(), "Item name").getHeight())
                .as("comfortable product-name input at laptop size").isGreaterThanOrEqualTo(36);
        assertThat(textField(addItem.getUIComponent(), "Inventory quantity").getHeight())
                .as("comfortable stock-count input at laptop size").isGreaterThanOrEqualTo(36);
        SwingUtilities.invokeAndWait(() -> {
            textField(addItem.getUIComponent(), "Item name").setText("Saturday Bouquet");
            textField(addItem.getUIComponent(), "Selling price").setText("28.00");
            JTextField quantity = textField(addItem.getUIComponent(), "Inventory quantity");
            quantity.setText("twelve");
            FarmUi.markFieldError(quantity, "Quantity must be a whole number.");
            FarmUi.updateStatusBadge(label(addItem.getUIComponent(), "Ready for a new item"),
                    "Check the highlighted field", FarmUi.Tone.DANGER);
        });
        render("add-item-validation", addItem.getUIComponent(), new Dimension(1060, 700));
        SwingUtilities.invokeAndWait(() -> button(addItem.getUIComponent(), "Reset form").doClick());
        JTabbedPane crmWorkspace = (JTabbedPane) findNamed(
                crm.getUIComponent(), "crm.workspace");
        crmWorkspace.setSelectedIndex(0);
        render("customers", crm.getUIComponent(), new Dimension(1120, 780));
        render("customers-compact", crm.getUIComponent(), new Dimension(1050, 700));
        crmWorkspace.setSelectedIndex(1);
        render("orders", crm.getUIComponent(), new Dimension(1120, 780));
        render("orders-compact", crm.getUIComponent(), new Dimension(1050, 700));
        assertThat(findNamed(crm.getUIComponent(), "crm.orders.table").getHeight())
                .as("visible order rows at compact laptop height")
                .isGreaterThanOrEqualTo(50);
        assertFormLabelsFit((Container) findNamed(
                crm.getUIComponent(), "crm.orderEntryForm"));
        render("harvest-log", harvestLog.getUIComponent(), new Dimension(1120, 780));
        assertThat(findNamed(harvestLog.getUIComponent(), "harvest.history.table").getHeight())
                .as("visible harvest history rows at laptop height")
                .isGreaterThanOrEqualTo(80);
        render("harvest-log-compact", harvestLog.getUIComponent(), new Dimension(1050, 700));
        Component cropInput = findNamed(harvestLog.getUIComponent(), "harvest.crop");
        Component historyTable = findNamed(harvestLog.getUIComponent(), "harvest.history.table");
        assertThat(SwingUtilities.convertPoint(cropInput, 0, 0, harvestLog.getUIComponent()).y)
                .as("new harvest entry appears before history on a compact laptop")
                .isLessThan(SwingUtilities.convertPoint(
                        historyTable, 0, 0, harvestLog.getUIComponent()).y);
        Component harvestUnit = findNamed(harvestLog.getUIComponent(), "harvest.unit");
        assertThat(SwingUtilities.convertPoint(harvestUnit, 0, 0, harvestLog.getUIComponent()).y)
                .as("harvest unit stays visible beside crop and quantity")
                .isEqualTo(SwingUtilities.convertPoint(cropInput, 0, 0, harvestLog.getUIComponent()).y);
        javax.swing.JViewport harvestViewport = (javax.swing.JViewport)
                SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class, cropInput);
        for (Component input : List.of(cropInput, harvestUnit,
                textField(harvestLog.getUIComponent(), "Harvest quantity"),
                textField(harvestLog.getUIComponent(), "Harvest date"))) {
            java.awt.Rectangle visibleBounds = SwingUtilities.convertRectangle(
                    input.getParent(), input.getBounds(), harvestViewport);
            assertThat(new java.awt.Rectangle(harvestViewport.getExtentSize())
                    .contains(visibleBounds))
                    .as("primary harvest input fully visible: %s", describe(input)).isTrue();
        }
        assertThat(findNamed(harvestLog.getUIComponent(), "harvest.history.table").getHeight())
                .as("visible harvest row at compact laptop height")
                .isGreaterThanOrEqualTo(50);
        JTabbedPane harvestWorkspace = (JTabbedPane) findNamed(
                harvestLog.getUIComponent(), "harvest.workspace");
        harvestWorkspace.setSelectedIndex(1);
        SwingUtilities.invokeAndWait(() -> textArea(
                harvestLog.getUIComponent(), "Batch harvest lines")
                .setText("Lavender,1.5,lbs"));
        render("harvest-batch-rounded-compact", harvestLog.getUIComponent(),
                new Dimension(1050, 700));
        assertThat(SwingUtilities.getAncestorOfClass(javax.swing.JViewport.class,
                textArea(harvestLog.getUIComponent(), "Batch harvest lines")).getHeight())
                .as("batch lines stay visible at compact height").isGreaterThanOrEqualTo(36);
        render("inventory", inventoryTab.getUIComponent(), new Dimension(1080, 720));
        render("inventory-compact", inventoryTab.getUIComponent(), new Dimension(1050, 700));
        render("market-day", marketDayTab.getUIComponent(), new Dimension(1050, 700));
        render("reports", reports.getUIComponent(), new Dimension(1050, 700));
        render("activity-history", activity.getUIComponent(), new Dimension(1120, 720));
        render("activity-history-viewer", activityViewer.getUIComponent(),
                new Dimension(1050, 700));
        render("knowledge-log", knowledgeLog.getUIComponent(), new Dimension(1120, 720));
        assertThat(findNamed(knowledgeLog.getUIComponent(), "knowledge.table").getHeight())
                .as("visible farm lessons at laptop height")
                .isGreaterThanOrEqualTo(80);
        render("knowledge-log-compact", knowledgeLog.getUIComponent(),
                new Dimension(1050, 700));
        render("season-planner", seasonPlanner.getUIComponent(),
                new Dimension(1120, 720));
        render("season-planner-compact", seasonPlanner.getUIComponent(),
                new Dimension(1050, 700));
        JTabbedPane seasonWorkspace = (JTabbedPane) findNamed(
                seasonPlanner.getUIComponent(), "seasonPlanner.workspace");
        seasonWorkspace.setSelectedIndex(1);
        render("season-planner-records", seasonPlanner.getUIComponent(),
                new Dimension(1120, 720));
        seasonWorkspace.setSelectedIndex(2);
        render("season-planner-weekly", seasonPlanner.getUIComponent(),
                new Dimension(1120, 720));
        seasonWorkspace.setSelectedIndex(3);
        render("season-planner-insights", seasonPlanner.getUIComponent(),
                new Dimension(1120, 720));
        seasonWorkspace.setSelectedIndex(0);
        render("trend-analysis", trends.getUIComponent(), new Dimension(1050, 700));
        render("irrigation", irrigation.getUIComponent(), new Dimension(1050, 700));
        render("irrigation-error", irrigationError.getUIComponent(),
                new Dimension(1050, 700));
        render("rose-guide", roseGuide.getUIComponent(), new Dimension(1050, 700));
        render("pricing-reference", pricing.getUIComponent(), new Dimension(1050, 700));
        render("rose-visualizer", roseVisualizer.getUIComponent(), new Dimension(1200, 800));
        render("destructive-confirmation", FarmUi.confirmationPanel(
                "Complete 4 confirmed orders?",
                "This deducts matching inventory. Review the packing list before continuing.",
                FarmUi.Tone.WARNING), new Dimension(520, 190));
        JTabbedPane inventoryTabs = (JTabbedPane) findNamed(
                inventoryWorkspace.getUIComponent(), "inventory.workspace");
        inventoryTabs.setSelectedIndex(0);
        render("inventory-workspace-stock", inventoryWorkspace.getUIComponent(),
                new Dimension(1120, 780));
        inventoryTabs.setSelectedIndex(1);
        render("inventory-workspace-add", inventoryWorkspace.getUIComponent(),
                new Dimension(1120, 780));
        inventoryTabs.setSelectedIndex(0);
        inventoryWorkspace.applyRolePermissions(false);
        render("inventory-workspace-viewer", inventoryWorkspace.getUIComponent(),
                new Dimension(1120, 780));
        inventoryWorkspace.applyRolePermissions(true);
        JTabbedPane referenceTabs = (JTabbedPane) findNamed(
                reference.getUIComponent(), "reference.workspace");
        referenceTabs.setSelectedIndex(0);
        render("reference-workspace-plants", reference.getUIComponent(),
                new Dimension(1280, 820));
        referenceTabs.setSelectedIndex(1);
        render("reference-workspace-events", reference.getUIComponent(),
                new Dimension(1280, 820));
        referenceTabs.setSelectedIndex(2);
        render("reference-workspace-rose", reference.getUIComponent(),
                new Dimension(1120, 780));
        referenceTabs.setSelectedIndex(3);
        render("reference-workspace-pricing", reference.getUIComponent(),
                new Dimension(1120, 780));
        referenceTabs.setSelectedIndex(4);
        render("reference-workspace-visualizer", reference.getUIComponent(),
                new Dimension(1200, 800));
        render("reference-workspace-visualizer-compact", reference.getUIComponent(),
                new Dimension(1050, 700));
        referenceTabs.setSelectedIndex(0);
        dashboard.getUIComponent();
        dashboard.initialize();
        awaitNamedLabelPrefix(dashboard.getUIComponent(),
                "dashboard.overviewStatus", "Updated ");
        JTabbedPane dashboardWorkspace = (JTabbedPane) findNamed(
                dashboard.getUIComponent(), "dashboard.workspace");
        dashboardWorkspace.setSelectedIndex(0);
        render("dashboard", dashboard.getUIComponent(), new Dimension(1500, 1000));
        dashboardWorkspace.setSelectedIndex(1);
        render("dashboard-charts", dashboard.getUIComponent(), new Dimension(1366, 768));
        dashboardWorkspace.setSelectedIndex(0);

        assertInteractiveControlsFit(addItem.getUIComponent());
        assertInteractiveControlsFit(crm.getUIComponent());
        assertInteractiveControlsFit(plantGuide.getUIComponent());
        assertInteractiveControlsFit(harvestLog.getUIComponent());
        assertInteractiveControlsFit(inventoryTab.getUIComponent());
        assertInteractiveControlsFit(marketDayTab.getUIComponent());
        assertInteractiveControlsFit(reports.getUIComponent());
        assertInteractiveControlsFit(activity.getUIComponent());
        assertInteractiveControlsFit(knowledgeLog.getUIComponent());
        assertInteractiveControlsFit(seasonPlanner.getUIComponent());
        assertInteractiveControlsFit(trends.getUIComponent());
        assertInteractiveControlsFit(irrigation.getUIComponent());
        assertInteractiveControlsFit(irrigationError.getUIComponent());
        assertInteractiveControlsFit(roseGuide.getUIComponent());
        assertInteractiveControlsFit(pricing.getUIComponent());
        assertInteractiveControlsFit(roseVisualizer.getUIComponent());
        assertAccessibleControls(addItem.getUIComponent());
        assertAccessibleControls(crm.getUIComponent());
        assertAccessibleControls(harvestLog.getUIComponent());
        assertAccessibleControls(inventoryTab.getUIComponent());
        assertAccessibleControls(marketDayTab.getUIComponent());
        assertAccessibleControls(reports.getUIComponent());
        assertAccessibleControls(activity.getUIComponent());
        assertAccessibleControls(knowledgeLog.getUIComponent());
        assertAccessibleControls(seasonPlanner.getUIComponent());
        assertAccessibleControls(trends.getUIComponent());
        assertAccessibleControls(irrigation.getUIComponent());
        assertAccessibleControls(roseGuide.getUIComponent());
        assertAccessibleControls(pricing.getUIComponent());
        assertAccessibleControls(roseVisualizer.getUIComponent());

        JTabbedPane navigation = new JTabbedPane(JTabbedPane.LEFT);
        navigation.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        new FarmTabNavigator(navigation, List.of(
                dashboard, marketDayTab, seasonPlanner, harvestLog, inventoryWorkspace,
                irrigation, crm, knowledgeLog, reports, trends, activity,
                reference)).populate();
        JPanel shell = new JPanel(new BorderLayout());
        JPanel shellHeader = FarmUi.brandHeader(
                "PORT ORCHARD, WA", "Monday · August 24");
        shell.add(shellHeader, BorderLayout.NORTH);
        shell.add(navigation, BorderLayout.CENTER);
        awaitNamedLabelPrefix(dashboard.getUIComponent(),
                "dashboard.overviewStatus", "Updated ");
        render("farm-shell", shell, new Dimension(1366, 768));
        render("farm-shell-compact", shell, new Dimension(1100, 700));
        java.awt.Rectangle finalNavigationTab = navigation.getBoundsAt(
                navigation.getTabCount() - 1);
        assertThat(finalNavigationTab.y + finalNavigationTab.height)
                .as("every primary workflow remains visible at minimum desktop height")
                .isLessThanOrEqualTo(navigation.getHeight());
        try {
            FlatDarkLaf.setup();
            FarmUi.installThemeDefaults();
            SwingUtilities.invokeAndWait(() -> {
                SwingUtilities.updateComponentTreeUI(shell);
                FarmUi.refreshTheme(shell);
                dashboard.applyTheme(true);
                trends.applyTheme();
                shell.remove(shellHeader);
                shell.add(FarmUi.brandHeader(
                        "PORT ORCHARD, WA", "Monday · August 24"), BorderLayout.NORTH);
                for (JComponent workflow : List.of(
                        inventoryWorkspace.getUIComponent(), marketDayTab.getUIComponent(),
                        seasonPlanner.getUIComponent(), harvestLog.getUIComponent(),
                        irrigation.getUIComponent(), irrigationError.getUIComponent(),
                        crm.getUIComponent(), knowledgeLog.getUIComponent(),
                        reports.getUIComponent(), trends.getUIComponent(),
                        activity.getUIComponent(), reference.getUIComponent())) {
                    SwingUtilities.updateComponentTreeUI(workflow);
                    FarmUi.refreshTheme(workflow);
                }
                trends.applyTheme();
            });
            render("farm-shell-dark-compact", shell, new Dimension(1100, 700));
            render("harvest-log-dark-compact", harvestLog.getUIComponent(),
                    new Dimension(1050, 700));
            render("inventory-dark-compact", inventoryTab.getUIComponent(),
                    new Dimension(1050, 700));
            render("orders-dark-compact", crm.getUIComponent(),
                    new Dimension(1050, 700));
            render("reports-dark-compact", reports.getUIComponent(),
                    new Dimension(1050, 700));
            render("season-planner-dark-compact", seasonPlanner.getUIComponent(),
                    new Dimension(1050, 700));
            render("add-item-dark-compact", addItem.getUIComponent(),
                    new Dimension(1050, 700));
            render("market-day-dark-compact", marketDayTab.getUIComponent(),
                    new Dimension(1050, 700));
            render("activity-history-dark-compact", activity.getUIComponent(),
                    new Dimension(1050, 700));
            render("knowledge-log-dark-compact", knowledgeLog.getUIComponent(),
                    new Dimension(1050, 700));
            render("trend-analysis-dark-compact", trends.getUIComponent(),
                    new Dimension(1050, 700));
            render("irrigation-dark-compact", irrigation.getUIComponent(),
                    new Dimension(1050, 700));
            render("rose-guide-dark-compact", roseGuide.getUIComponent(),
                    new Dimension(1050, 700));
            render("plant-guide-dark-compact", plantGuide.getUIComponent(),
                    new Dimension(1050, 700));
            render("pricing-reference-dark-compact", pricing.getUIComponent(),
                    new Dimension(1050, 700));
            render("rose-visualizer-dark-compact", roseVisualizer.getUIComponent(),
                    new Dimension(1050, 700));
            render("inventory-workspace-dark-compact", inventoryWorkspace.getUIComponent(),
                    new Dimension(1050, 700));
            render("reference-workspace-dark-compact", reference.getUIComponent(),
                    new Dimension(1050, 700));
        } finally {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        }

        assertThat(OUTPUT.resolve("add-item.png")).isRegularFile();
        assertThat(OUTPUT.resolve("add-item-validation.png")).isRegularFile();
        assertThat(OUTPUT.resolve("customers.png")).isRegularFile();
        assertThat(OUTPUT.resolve("customers-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("orders.png")).isRegularFile();
        assertThat(OUTPUT.resolve("orders-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("harvest-log.png")).isRegularFile();
        assertThat(OUTPUT.resolve("harvest-log-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("harvest-batch-rounded-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("farm-shell.png")).isRegularFile();
        assertThat(OUTPUT.resolve("farm-shell-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("farm-shell-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("harvest-log-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("inventory-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("orders-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("reports-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("season-planner-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("add-item-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("market-day-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("activity-history-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("knowledge-log-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("trend-analysis-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("irrigation-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("rose-guide-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("pricing-reference-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("rose-visualizer-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("inventory-workspace-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("reference-workspace-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("dashboard.png")).isRegularFile();
        assertThat(OUTPUT.resolve("dashboard-charts.png")).isRegularFile();
        assertThat(OUTPUT.resolve("inventory.png")).isRegularFile();
        assertThat(OUTPUT.resolve("inventory-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("market-day.png")).isRegularFile();
        assertThat(OUTPUT.resolve("reports.png")).isRegularFile();
        assertThat(OUTPUT.resolve("activity-history.png")).isRegularFile();
        assertThat(OUTPUT.resolve("activity-history-viewer.png")).isRegularFile();
        assertThat(OUTPUT.resolve("knowledge-log.png")).isRegularFile();
        assertThat(OUTPUT.resolve("knowledge-log-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("season-planner.png")).isRegularFile();
        assertThat(OUTPUT.resolve("season-planner-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("season-planner-records.png")).isRegularFile();
        assertThat(OUTPUT.resolve("season-planner-weekly.png")).isRegularFile();
        assertThat(OUTPUT.resolve("season-planner-insights.png")).isRegularFile();
        assertThat(OUTPUT.resolve("trend-analysis.png")).isRegularFile();
        assertThat(OUTPUT.resolve("irrigation.png")).isRegularFile();
        assertThat(OUTPUT.resolve("irrigation-error.png")).isRegularFile();
        assertThat(OUTPUT.resolve("rose-guide.png")).isRegularFile();
        assertThat(OUTPUT.resolve("plant-guide-dark-compact.png")).isRegularFile();
        assertThat(OUTPUT.resolve("pricing-reference.png")).isRegularFile();
        assertThat(OUTPUT.resolve("rose-visualizer.png")).isRegularFile();
        assertThat(OUTPUT.resolve("destructive-confirmation.png")).isRegularFile();
        assertThat(OUTPUT.resolve("inventory-workspace-stock.png")).isRegularFile();
        assertThat(OUTPUT.resolve("inventory-workspace-add.png")).isRegularFile();
        assertThat(OUTPUT.resolve("inventory-workspace-viewer.png")).isRegularFile();
        assertThat(OUTPUT.resolve("reference-workspace-rose.png")).isRegularFile();
        assertThat(OUTPUT.resolve("reference-workspace-plants.png")).isRegularFile();
        assertThat(OUTPUT.resolve("reference-workspace-events.png")).isRegularFile();
        assertThat(OUTPUT.resolve("reference-workspace-pricing.png")).isRegularFile();
        assertThat(OUTPUT.resolve("reference-workspace-visualizer.png")).isRegularFile();
        assertThat(OUTPUT.resolve("reference-workspace-visualizer-compact.png"))
                .isRegularFile();
    }

    private static SeasonPlanEntry planting(Long id, int year, String crop, String variety,
                                             String bed, int sowMonth, int sowDay,
                                             int transplantMonth, int transplantDay,
                                             int harvestMonth, int harvestDay,
                                             int harvestEndMonth, int harvestEndDay,
                                             double bedFeet, int stems,
                                             SeasonPlanStatus status) {
        SeasonPlanEntry entry = new SeasonPlanEntry(
                year, crop, variety, bed, LocalDate.of(year, sowMonth, sowDay),
                LocalDate.of(year, transplantMonth, transplantDay),
                LocalDate.of(year, harvestMonth, harvestDay),
                LocalDate.of(year, harvestEndMonth, harvestEndDay),
                bedFeet, stems, status, "Visual contract planning block");
        entry.setId(id);
        return entry;
    }

    private static SeasonPlanService.PlanPerformance performance(
            SeasonPlanEntry entry, double actual, double expectedToDate,
            SeasonPlanService.PlanPace pace) {
        double completion = actual * 100d / entry.getExpectedStems();
        return new SeasonPlanService.PlanPerformance(
                entry, actual, expectedToDate, actual - expectedToDate, completion, pace);
    }

    private static List<SeasonPlanService.WeeklyProduction> weeklyProduction(int year) {
        double[] planned = {20, 60, 80, 100, 120, 130, 140, 145, 150, 150, 145, 145,
                140, 130, 125, 120, 110, 100, 90, 75, 60, 45, 0};
        double[] actual = {30, 50, 65, 90, 100, 110, 125, 140, 120, 90, 80, 65, 37,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        List<SeasonPlanService.WeeklyProduction> result = new java.util.ArrayList<>();
        LocalDate firstWeek = LocalDate.of(year, 5, 4);
        for (int index = 0; index < planned.length; index++) {
            result.add(new SeasonPlanService.WeeklyProduction(firstWeek.plusWeeks(index),
                    planned[index], actual[index], actual[index] - planned[index]));
        }
        return List.copyOf(result);
    }

    private static void render(String name, JComponent component, Dimension size)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            component.addNotify();
            component.setSize(size);
            component.validate();
            layoutRecursively(component);
            BufferedImage image = new BufferedImage(
                    size.width, size.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                component.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            try {
                ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            } finally {
                component.removeNotify();
            }
        });
    }

    private static void layoutRecursively(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }

    private static void assertInteractiveControlsFit(Container parent) {
        for (Component child : parent.getComponents()) {
            boolean semanticButton = child instanceof AbstractButton button
                    && ((button.getText() != null && !button.getText().isBlank())
                    || (button.getAccessibleContext().getAccessibleName() != null
                    && !button.getAccessibleContext().getAccessibleName().isBlank()));
            if (child.isVisible() && (semanticButton
                    || child instanceof JTextField || child instanceof JComboBox<?>)) {
                assertThat(child.getWidth())
                        .as("usable width for %s", describe(child)).isGreaterThan(0);
                assertThat(child.getHeight())
                        .as("usable height for %s", describe(child)).isGreaterThan(0);
                assertThat(child.getX())
                        .as("left edge for %s", describe(child)).isGreaterThanOrEqualTo(0);
                assertThat(child.getY())
                        .as("top edge for %s", describe(child)).isGreaterThanOrEqualTo(0);
                assertThat(child.getX() + child.getWidth())
                        .as("right edge for %s", describe(child))
                        .isLessThanOrEqualTo(parent.getWidth());
                assertThat(child.getY() + child.getHeight())
                        .as("bottom edge for %s", describe(child))
                        .isLessThanOrEqualTo(parent.getHeight());
            }
            if (child instanceof Container container && child.isVisible()
                    && child.getWidth() > 0 && child.getHeight() > 0) {
                assertInteractiveControlsFit(container);
            }
        }
    }

    private static void assertFormLabelsFit(Container form) {
        for (Component child : form.getComponents()) {
            if (child instanceof JLabel label && label.getLabelFor() != null) {
                assertThat(label.getX())
                        .as("left edge for form label %s", label.getText())
                        .isGreaterThanOrEqualTo(0);
                assertThat(label.getX() + label.getWidth())
                        .as("right edge for form label %s", label.getText())
                        .isLessThanOrEqualTo(form.getWidth());
            }
        }
    }

    private static void assertAccessibleControls(Container parent) {
        for (Component child : parent.getComponents()) {
            boolean semanticButton = child instanceof AbstractButton button
                    && button.getText() != null && !button.getText().isBlank();
            boolean input = child instanceof JTextField
                    || child instanceof JComboBox<?>
                    || child instanceof JSpinner
                    || child instanceof JTable
                    || child instanceof JTextArea
                    || semanticButton;
            if (input && child.isVisible()) {
                assertThat(child.getAccessibleContext().getAccessibleName())
                        .as("accessible name for %s", describe(child))
                        .isNotBlank();
            }
            if (child instanceof Container container && child.isVisible()
                    && !(child instanceof JComboBox<?>)
                    && !(child instanceof JSpinner)
                    && !(child instanceof JTable)) {
                assertAccessibleControls(container);
            }
        }
    }

    private static String describe(Component component) {
        if (component.getName() != null) {
            return component.getName();
        }
        if (component instanceof AbstractButton button) {
            return button.getText();
        }
        String accessibleName = component.getAccessibleContext() == null ? null
                : component.getAccessibleContext().getAccessibleName();
        if (accessibleName != null && !accessibleName.isBlank()) {
            return component.getClass().getSimpleName() + " (" + accessibleName + ")";
        }
        return component.getClass().getSimpleName();
    }

    private static Component findNamed(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = findNamed(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JTextField textField(Component root, String accessibleName) {
        if (root instanceof JTextField field && accessibleName.equals(
                field.getAccessibleContext().getAccessibleName())) {
            return field;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return textField(child, accessibleName);
                } catch (IllegalArgumentException ignored) {
                    // Continue through the component tree.
                }
            }
        }
        throw new IllegalArgumentException("Text field not found: " + accessibleName);
    }

    private static JTextArea textArea(Component root, String accessibleName) {
        if (root instanceof JTextArea area && accessibleName.equals(
                area.getAccessibleContext().getAccessibleName())) {
            return area;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return textArea(child, accessibleName);
                } catch (IllegalArgumentException ignored) {
                    // Continue through the component tree.
                }
            }
        }
        throw new IllegalArgumentException("Text area not found: " + accessibleName);
    }

    private static javax.swing.JButton button(Component root, String text) {
        if (root instanceof javax.swing.JButton button && text.equals(button.getText())) {
            return button;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return button(child, text);
                } catch (IllegalArgumentException ignored) {
                    // Continue through the component tree.
                }
            }
        }
        throw new IllegalArgumentException("Button not found: " + text);
    }

    private static javax.swing.JLabel label(Component root, String text) {
        if (root instanceof javax.swing.JLabel label && text.equals(label.getText())) {
            return label;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                try {
                    return label(child, text);
                } catch (IllegalArgumentException ignored) {
                    // Continue through the component tree.
                }
            }
        }
        throw new IllegalArgumentException("Label not found: " + text);
    }

    private static void awaitLabel(Component root, String text) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] found = new boolean[1];
            SwingUtilities.invokeAndWait(() -> found[0] = containsLabel(root, text));
            if (found[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for label: " + text);
    }

    private static void awaitNamedLabelPrefix(Component root, String name,
                                              String prefix) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] found = new boolean[1];
            SwingUtilities.invokeAndWait(() -> {
                Component component = findNamed(root, name);
                found[0] = component instanceof JLabel label
                        && label.getText().startsWith(prefix);
            });
            if (found[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for label prefix: " + prefix);
    }

    private static void awaitButtonEnabled(Component root, String text) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] enabled = new boolean[1];
            SwingUtilities.invokeAndWait(() -> enabled[0] = button(root, text).isEnabled());
            if (enabled[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for enabled button: " + text);
    }

    private static boolean containsLabel(Component root, String text) {
        if (root instanceof javax.swing.JLabel label && text.equals(label.getText())) {
            return true;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (containsLabel(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class Host implements TabHost {
        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }

    private static final class ReadOnlyHost implements TabHost {
        @Override public void refreshAll() { }
        @Override public void selectTab(String tabTitle) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
        @Override public boolean canMutateData() { return false; }
        @Override public boolean canClearHistory() { return false; }
    }
}
