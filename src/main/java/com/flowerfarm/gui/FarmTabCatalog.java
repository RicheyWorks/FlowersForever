package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.AddItemTab;
import com.flowerfarm.gui.tabs.CrmTab;
import com.flowerfarm.gui.tabs.DashboardTab;
import com.flowerfarm.gui.tabs.FlowerFarmTab;
import com.flowerfarm.gui.tabs.HarvestLogTab;
import com.flowerfarm.gui.tabs.InventoryTab;
import com.flowerfarm.gui.tabs.InventoryWorkspaceTab;
import com.flowerfarm.gui.tabs.IrrigationInfoTab;
import com.flowerfarm.gui.tabs.MarketDayTab;
import com.flowerfarm.gui.tabs.KnowledgeLogTab;
import com.flowerfarm.gui.tabs.PricingInfoTab;
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

    public FarmTabCatalog(InventoryService inventoryService, TrendService trendService,
                          HarvestService harvestService, SyncHistoryService syncHistoryService,
                          CustomerService customerService, OrderService orderService,
                          ReportService reportService, IrrigationAdvisorService irrigationService,
                          MarketDayPackingService marketDayService,
                          MorningBriefingService briefingService,
                          DayCloseoutService closeoutService,
                          CustomerStatementService statementService,
                          KnowledgeService knowledgeService,
                          SeasonPlanService seasonPlanService) {
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
    }

    public Catalog create(TabHost host) {
        DashboardTab dashboard = new DashboardTab(inventoryService, harvestService, orderService,
                irrigationService, marketDayService, briefingService, closeoutService, host);
        TrendAnalysisTab trend = new TrendAnalysisTab(trendService, host);
        InventoryTab inventory = new InventoryTab(inventoryService, host);
        AddItemTab addItem = new AddItemTab(inventoryService, host);
        HarvestLogTab harvestLog = new HarvestLogTab(harvestService, host);
        MarketDayTab marketDay = new MarketDayTab(marketDayService, host);
        IrrigationInfoTab irrigation = new IrrigationInfoTab(irrigationService, host);
        InventoryWorkspaceTab inventoryWorkspace =
                new InventoryWorkspaceTab(inventory, addItem);
        RoseVarietiesTab roseGuide = new RoseVarietiesTab(inventoryService, host);
        PricingInfoTab pricing = new PricingInfoTab();
        RoseVisualizerTab visualizer = new RoseVisualizerTab(inventoryService, host);
        ReferenceTab reference = new ReferenceTab(roseGuide, pricing, visualizer);
        CrmTab crm = new CrmTab(customerService, orderService, statementService,
                inventoryService, host);
        List<FlowerFarmTab> tabs = List.of(
                dashboard,
                marketDay,
                new SeasonPlannerTab(seasonPlanService, host),
                harvestLog,
                inventoryWorkspace,
                irrigation,
                new KnowledgeLogTab(knowledgeService, host),
                crm,
                new ReportsTab(reportService, host),
                trend,
                new SyncHistoryTab(syncHistoryService, host),
                reference);
        return new Catalog(tabs, dashboard, trend, inventory, addItem,
                harvestLog, marketDay, irrigation, crm);
    }

    public record Catalog(List<FlowerFarmTab> tabs, DashboardTab dashboard,
                          TrendAnalysisTab trend, InventoryTab inventory,
                          AddItemTab addItem, HarvestLogTab harvest,
                          MarketDayTab marketDay, IrrigationInfoTab irrigation,
                          CrmTab crm) {
        public Catalog {
            tabs = List.copyOf(tabs);
        }
    }
}
