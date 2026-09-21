package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.AddItemTab;
import com.flowerfarm.gui.tabs.CrmTab;
import com.flowerfarm.gui.tabs.DashboardTab;
import com.flowerfarm.gui.tabs.EventServicesTab;
import com.flowerfarm.gui.tabs.FlowerFarmTab;
import com.flowerfarm.gui.tabs.HarvestLogTab;
import com.flowerfarm.gui.tabs.InventoryTab;
import com.flowerfarm.gui.tabs.InventoryWorkspaceTab;
import com.flowerfarm.gui.tabs.IrrigationInfoTab;
import com.flowerfarm.gui.tabs.MarketDayTab;
import com.flowerfarm.gui.tabs.KnowledgeLogTab;
import com.flowerfarm.gui.tabs.PricingInfoTab;
import com.flowerfarm.gui.tabs.PnwPlantGuideTab;
import com.flowerfarm.gui.tabs.ProductionStudioTab;
import com.flowerfarm.gui.tabs.ReportsTab;
import com.flowerfarm.gui.tabs.ReferenceTab;
import com.flowerfarm.gui.tabs.RoseVarietiesTab;
import com.flowerfarm.gui.tabs.RoseVisualizerTab;
import com.flowerfarm.gui.tabs.SyncHistoryTab;
import com.flowerfarm.gui.tabs.SeasonPlannerTab;
import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.gui.tabs.TrendAnalysisTab;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.SeasonPlanService;
import com.flowerfarm.service.TrendService;

import java.util.List;

/**
 * Owns desktop feature composition and the stable primary-navigation order.
 */
public final class FarmTabCatalog {

    private final InventoryService inventoryService;
    private final TrendService trendService;
    private final HarvestService harvestService;
    private final SyncHistoryService syncHistoryService;
    private final CustomerService customerService;
    private final OrderService orderService;
    private final ReportService reportService;
    private final IrrigationAdvisorService irrigationService;
    private final MarketDayPackingService marketDayService;
    private final MorningBriefingService briefingService;
    private final DayCloseoutService closeoutService;
    private final CustomerStatementService statementService;
    private final KnowledgeService knowledgeService;
    private final SeasonPlanService seasonPlanService;
    private final ProductionStudioService productionStudioService;

    public FarmTabCatalog(InventoryService inventoryService, TrendService trendService,
                          HarvestService harvestService, SyncHistoryService syncHistoryService,
                          CustomerService customerService, OrderService orderService,
                          ReportService reportService, IrrigationAdvisorService irrigationService,
                          MarketDayPackingService marketDayService,
                          MorningBriefingService briefingService,
                          DayCloseoutService closeoutService,
                          CustomerStatementService statementService,
                          KnowledgeService knowledgeService,
                          SeasonPlanService seasonPlanService,
                          ProductionStudioService productionStudioService) {
        this.inventoryService = inventoryService;
        this.trendService = trendService;
        this.harvestService = harvestService;
        this.syncHistoryService = syncHistoryService;
        this.customerService = customerService;
        this.orderService = orderService;
        this.reportService = reportService;
        this.irrigationService = irrigationService;
        this.marketDayService = marketDayService;
        this.briefingService = briefingService;
        this.closeoutService = closeoutService;
        this.statementService = statementService;
        this.knowledgeService = knowledgeService;
        this.seasonPlanService = seasonPlanService;
        this.productionStudioService = productionStudioService;
    }

    public Catalog create(TabHost host) {
        return create(host, FarmDialogs.swing(), FarmFileChooser.swing());
    }

    /** Builds the complete desktop catalog with replaceable native-interaction boundaries. */
    public Catalog create(TabHost host, FarmDialogs dialogs, FarmFileChooser files) {
        java.util.Objects.requireNonNull(dialogs, "dialogs");
        java.util.Objects.requireNonNull(files, "files");
        DashboardTab dashboard = new DashboardTab(inventoryService, harvestService, orderService,
                irrigationService, marketDayService, briefingService, closeoutService, host,
                dialogs, files);
        TrendAnalysisTab trend = new TrendAnalysisTab(trendService, host);
        InventoryTab inventory = new InventoryTab(inventoryService, host, dialogs, files);
        AddItemTab addItem = new AddItemTab(inventoryService, host, dialogs);
        HarvestLogTab harvestLog = new HarvestLogTab(harvestService, host, dialogs, files);
        MarketDayTab marketDay = new MarketDayTab(marketDayService, host, dialogs, files);
        IrrigationInfoTab irrigation = new IrrigationInfoTab(irrigationService, host);
        InventoryWorkspaceTab inventoryWorkspace =
                new InventoryWorkspaceTab(inventory, addItem);
        PnwPlantGuideTab plantGuide = new PnwPlantGuideTab(host);
        EventServicesTab eventServices = new EventServicesTab(host);
        RoseVarietiesTab roseGuide = new RoseVarietiesTab(inventoryService, host, dialogs);
        PricingInfoTab pricing = new PricingInfoTab();
        RoseVisualizerTab visualizer = new RoseVisualizerTab(
                inventoryService, host, dialogs, files);
        ProductionStudioTab productionStudio = new ProductionStudioTab(
                productionStudioService, dialogs);
        ReferenceTab reference = new ReferenceTab(
                plantGuide, eventServices, productionStudio, roseGuide, pricing, visualizer);
        CrmTab crm = new CrmTab(customerService, orderService, statementService,
                inventoryService, host, dialogs, files);
        SeasonPlannerTab seasonPlanner = new SeasonPlannerTab(
                seasonPlanService, host, dialogs);
        List<FlowerFarmTab> tabs = List.of(
                dashboard,
                marketDay,
                seasonPlanner,
                harvestLog,
                inventoryWorkspace,
                irrigation,
                new KnowledgeLogTab(knowledgeService, host, dialogs),
                crm,
                new ReportsTab(reportService, host, dialogs, files),
                trend,
                new SyncHistoryTab(syncHistoryService, host, dialogs, files),
                reference);
        return new Catalog(tabs, dashboard, trend, inventory, addItem,
                harvestLog, marketDay, irrigation, crm, seasonPlanner, productionStudio);
    }

    public record Catalog(List<FlowerFarmTab> tabs, DashboardTab dashboard,
                          TrendAnalysisTab trend, InventoryTab inventory,
                          AddItemTab addItem, HarvestLogTab harvest,
                          MarketDayTab marketDay, IrrigationInfoTab irrigation,
                          CrmTab crm, SeasonPlannerTab seasonPlanner, ProductionStudioTab productionStudio) {
        public Catalog {
            tabs = List.copyOf(tabs);
        }
    }
}
