package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.AddItemTab;
import com.flowerfarm.gui.tabs.BerryBlockPlanTab;
import com.flowerfarm.gui.tabs.NurseryGrowingBatchTab;
import com.flowerfarm.gui.tabs.HerdGroupTab;
import com.flowerfarm.gui.tabs.EggCollectionTab;
import com.flowerfarm.gui.tabs.MilkCollectionTab;
import com.flowerfarm.gui.tabs.VineyardBlockPlanTab;
import com.flowerfarm.gui.tabs.HayCuttingTab;
import com.flowerfarm.gui.tabs.HopYardPlanTab;
import com.flowerfarm.gui.tabs.GrainFieldPlanTab;
import com.flowerfarm.gui.tabs.SeedFieldPlanTab;
import com.flowerfarm.gui.tabs.NutBlockPlanTab;
import com.flowerfarm.gui.tabs.VegetablePlantingTab;
import com.flowerfarm.gui.tabs.AquaticCohortTab;
import com.flowerfarm.gui.tabs.ChristmasTreeBlockTab;
import com.flowerfarm.gui.tabs.HiveCheckTab;
import com.flowerfarm.gui.tabs.MushroomBatchTab;
import com.flowerfarm.gui.tabs.CranberryBedTab;
import com.flowerfarm.gui.tabs.PumpkinPatchTab;
import com.flowerfarm.gui.tabs.FieldScienceTab;
import com.flowerfarm.gui.tabs.CannabisCultivationTab;
import com.flowerfarm.gui.tabs.CropAreaPlanTab;
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
import com.flowerfarm.gui.tabs.WorkspaceHomeTab;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.reference.FieldScienceCatalog;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.BerryBlockPlanService;
import com.flowerfarm.service.NurseryBatchService;
import com.flowerfarm.service.HerdGroupService;
import com.flowerfarm.service.EggCollectionService;
import com.flowerfarm.service.MilkCollectionService;
import com.flowerfarm.service.VineyardBlockPlanService;
import com.flowerfarm.service.HayCuttingService;
import com.flowerfarm.service.HopYardPlanService;
import com.flowerfarm.service.GrainFieldPlanService;
import com.flowerfarm.service.SeedFieldPlanService;
import com.flowerfarm.service.NutBlockPlanService;
import com.flowerfarm.service.VegetablePlantingPlanService;
import com.flowerfarm.service.AquaticCohortPlanService;
import com.flowerfarm.service.ChristmasTreeBlockPlanService;
import com.flowerfarm.service.HiveCheckService;
import com.flowerfarm.service.MushroomBatchPlanService;
import com.flowerfarm.service.CranberryBedPlanService;
import com.flowerfarm.service.PumpkinPatchPlanService;
import com.flowerfarm.service.FieldScienceExportService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.CannabisCultivationService;
import com.flowerfarm.service.CropAreaPlanService;
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
 * Owns desktop feature composition and workspace-specific primary navigation.
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
    private final CannabisCultivationService cannabisCultivationService;
    private final CropAreaPlanService cropAreaPlanService;
    private final BerryBlockPlanService berryBlockPlanService;
    private final NurseryBatchService nurseryBatchService;
    private final HerdGroupService herdGroupService;
    private final EggCollectionService eggCollectionService;
    private final MilkCollectionService milkCollectionService;
    private final VineyardBlockPlanService vineyardBlockPlanService;
    private final HayCuttingService hayCuttingService;
    private final HopYardPlanService hopYardPlanService;
    private final GrainFieldPlanService grainFieldPlanService;
    private final SeedFieldPlanService seedFieldPlanService;
    private final NutBlockPlanService nutBlockPlanService;
    private final VegetablePlantingPlanService vegetablePlantingService;
    private final AquaticCohortPlanService aquaticCohortService;
    private final ChristmasTreeBlockPlanService christmasTreeBlockService;
    private final HiveCheckService hiveCheckService;
    private final MushroomBatchPlanService mushroomBatchPlanService;
    private final CranberryBedPlanService cranberryBedPlanService;
    private final PumpkinPatchPlanService pumpkinPatchPlanService;
    private FieldScienceExportService fieldScienceExportService;

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
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, grainFieldPlanService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService,
                          SeedFieldPlanService seedFieldPlanService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, grainFieldPlanService, seedFieldPlanService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService,
                          SeedFieldPlanService seedFieldPlanService,
                          NutBlockPlanService nutBlockPlanService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, grainFieldPlanService, seedFieldPlanService,
                nutBlockPlanService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService,
                          SeedFieldPlanService seedFieldPlanService,
                          NutBlockPlanService nutBlockPlanService,
                          VegetablePlantingPlanService vegetablePlantingService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, grainFieldPlanService, seedFieldPlanService,
                nutBlockPlanService, vegetablePlantingService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService,
                          SeedFieldPlanService seedFieldPlanService,
                          NutBlockPlanService nutBlockPlanService,
                          VegetablePlantingPlanService vegetablePlantingService,
                          AquaticCohortPlanService aquaticCohortService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, grainFieldPlanService, seedFieldPlanService,
                nutBlockPlanService, vegetablePlantingService, aquaticCohortService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService,
                          SeedFieldPlanService seedFieldPlanService,
                          NutBlockPlanService nutBlockPlanService,
                          VegetablePlantingPlanService vegetablePlantingService,
                          AquaticCohortPlanService aquaticCohortService,
                          ChristmasTreeBlockPlanService christmasTreeBlockService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, grainFieldPlanService, seedFieldPlanService,
                nutBlockPlanService, vegetablePlantingService, aquaticCohortService,
                christmasTreeBlockService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService,
                          SeedFieldPlanService seedFieldPlanService,
                          NutBlockPlanService nutBlockPlanService,
                          VegetablePlantingPlanService vegetablePlantingService,
                          AquaticCohortPlanService aquaticCohortService,
                          ChristmasTreeBlockPlanService christmasTreeBlockService,
                          HiveCheckService hiveCheckService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, grainFieldPlanService, seedFieldPlanService,
                nutBlockPlanService, vegetablePlantingService, aquaticCohortService,
                christmasTreeBlockService, hiveCheckService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService,
                          SeedFieldPlanService seedFieldPlanService,
                          NutBlockPlanService nutBlockPlanService,
                          VegetablePlantingPlanService vegetablePlantingService,
                          AquaticCohortPlanService aquaticCohortService,
                          ChristmasTreeBlockPlanService christmasTreeBlockService,
                          HiveCheckService hiveCheckService,
                          MushroomBatchPlanService mushroomBatchPlanService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, grainFieldPlanService, seedFieldPlanService,
                nutBlockPlanService, vegetablePlantingService, aquaticCohortService,
                christmasTreeBlockService, hiveCheckService, mushroomBatchPlanService,
                null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService,
                          SeedFieldPlanService seedFieldPlanService,
                          NutBlockPlanService nutBlockPlanService,
                          VegetablePlantingPlanService vegetablePlantingService,
                          AquaticCohortPlanService aquaticCohortService,
                          ChristmasTreeBlockPlanService christmasTreeBlockService,
                          HiveCheckService hiveCheckService,
                          MushroomBatchPlanService mushroomBatchPlanService,
                          CranberryBedPlanService cranberryBedPlanService) {
        this(inventoryService, trendService, harvestService, syncHistoryService,
                customerService, orderService, reportService, irrigationService,
                marketDayService, briefingService, closeoutService, statementService,
                knowledgeService, seasonPlanService, productionStudioService,
                cannabisCultivationService, cropAreaPlanService, berryBlockPlanService,
                nurseryBatchService, herdGroupService, eggCollectionService,
                milkCollectionService, vineyardBlockPlanService, hayCuttingService,
                hopYardPlanService, grainFieldPlanService, seedFieldPlanService,
                nutBlockPlanService, vegetablePlantingService, aquaticCohortService,
                christmasTreeBlockService, hiveCheckService, mushroomBatchPlanService,
                cranberryBedPlanService, null);
    }

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
                          ProductionStudioService productionStudioService,
                          CannabisCultivationService cannabisCultivationService,
                          CropAreaPlanService cropAreaPlanService,
                          BerryBlockPlanService berryBlockPlanService,
                          NurseryBatchService nurseryBatchService,
                          HerdGroupService herdGroupService,
                          EggCollectionService eggCollectionService,
                          MilkCollectionService milkCollectionService,
                          VineyardBlockPlanService vineyardBlockPlanService,
                          HayCuttingService hayCuttingService,
                          HopYardPlanService hopYardPlanService,
                          GrainFieldPlanService grainFieldPlanService,
                          SeedFieldPlanService seedFieldPlanService,
                          NutBlockPlanService nutBlockPlanService,
                          VegetablePlantingPlanService vegetablePlantingService,
                          AquaticCohortPlanService aquaticCohortService,
                          ChristmasTreeBlockPlanService christmasTreeBlockService,
                          HiveCheckService hiveCheckService,
                          MushroomBatchPlanService mushroomBatchPlanService,
                          CranberryBedPlanService cranberryBedPlanService,
                          PumpkinPatchPlanService pumpkinPatchPlanService) {
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
        this.cannabisCultivationService = cannabisCultivationService;
        this.cropAreaPlanService = cropAreaPlanService;
        this.berryBlockPlanService = berryBlockPlanService;
        this.nurseryBatchService = nurseryBatchService;
        this.herdGroupService = herdGroupService;
        this.eggCollectionService = eggCollectionService;
        this.milkCollectionService = milkCollectionService;
        this.vineyardBlockPlanService = vineyardBlockPlanService;
        this.hayCuttingService = hayCuttingService;
        this.hopYardPlanService = hopYardPlanService;
        this.grainFieldPlanService = grainFieldPlanService;
        this.seedFieldPlanService = seedFieldPlanService;
        this.nutBlockPlanService = nutBlockPlanService;
        this.vegetablePlantingService = vegetablePlantingService;
        this.aquaticCohortService = aquaticCohortService;
        this.christmasTreeBlockService = christmasTreeBlockService;
        this.hiveCheckService = hiveCheckService;
        this.mushroomBatchPlanService = mushroomBatchPlanService;
        this.cranberryBedPlanService = cranberryBedPlanService;
        this.pumpkinPatchPlanService = pumpkinPatchPlanService;
    }

    void setFieldScienceExportService(FieldScienceExportService service) {
        this.fieldScienceExportService = java.util.Objects.requireNonNull(service, "service");
    }

    public Catalog create(TabHost host) {
        return create(host, FarmDialogs.swing(), FarmFileChooser.swing(),
                FarmWorkspace.ROSE_AND_EVENTS);
    }

    /** Builds navigation for the selected farm or shop workspace. */
    public Catalog create(TabHost host, FarmWorkspace workspace) {
        return create(host, FarmDialogs.swing(), FarmFileChooser.swing(), workspace, null);
    }

    public Catalog create(TabHost host, FarmWorkspace workspace,
                          FarmOperationSettings settings) {
        return create(host, FarmDialogs.swing(), FarmFileChooser.swing(), workspace, settings);
    }

    /** Builds the complete desktop catalog with replaceable native-interaction boundaries. */
    public Catalog create(TabHost host, FarmDialogs dialogs, FarmFileChooser files) {
        return create(host, dialogs, files, FarmWorkspace.ROSE_AND_EVENTS);
    }

    public Catalog create(TabHost host, FarmDialogs dialogs, FarmFileChooser files,
                          FarmWorkspace workspace) {
        return create(host, dialogs, files, workspace, null);
    }

    public Catalog create(TabHost host, FarmDialogs dialogs, FarmFileChooser files,
                          FarmWorkspace workspace, FarmOperationSettings settings) {
        java.util.Objects.requireNonNull(dialogs, "dialogs");
        java.util.Objects.requireNonNull(files, "files");
        java.util.Objects.requireNonNull(workspace, "workspace");
        java.util.Set<FarmWorkspace> enabled = settings != null
                && settings.getWorkspace() == workspace
                ? settings.getEnabledWorkspaces() : java.util.Set.of(workspace);
        DashboardTab dashboard = new DashboardTab(inventoryService, harvestService, orderService,
                irrigationService, marketDayService, briefingService, closeoutService, host,
                dialogs, files);
        TrendAnalysisTab trend = new TrendAnalysisTab(trendService, host);
        InventoryTab inventory = new InventoryTab(inventoryService, host, dialogs, files);
        inventory.setWorkspace(workspace);
        inventory.setEnabledWorkspaces(enabled);
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
        KnowledgeLogTab knowledge = new KnowledgeLogTab(knowledgeService, host, dialogs);
        knowledge.setWorkspace(workspace);
        ReportsTab reports = new ReportsTab(reportService, host, dialogs, files);
        reports.setWorkspace(workspace);
        SyncHistoryTab history = new SyncHistoryTab(syncHistoryService, host, dialogs, files);
        CannabisCultivationTab cultivation = enabled.contains(FarmWorkspace.CANNABIS_FARM)
                ? new CannabisCultivationTab(java.util.Objects.requireNonNull(
                        cannabisCultivationService,
                        "Cannabis cultivation service is required for the cannabis workspace."),
                        host, dialogs)
                : null;
        CropAreaPlanTab potatoArea = enabled.contains(FarmWorkspace.POTATO_FARM)
                ? new CropAreaPlanTab(java.util.Objects.requireNonNull(
                        cropAreaPlanService,
                        "Crop area service is required for field and orchard workspaces."),
                        host, dialogs, FarmWorkspace.POTATO_FARM)
                : null;
        CropAreaPlanTab appleArea = enabled.contains(FarmWorkspace.APPLE_ORCHARD)
                ? new CropAreaPlanTab(java.util.Objects.requireNonNull(
                        cropAreaPlanService,
                        "Crop area service is required for field and orchard workspaces."),
                        host, dialogs, FarmWorkspace.APPLE_ORCHARD)
                : null;
        CropAreaPlanTab cropArea = workspace == FarmWorkspace.APPLE_ORCHARD
                ? appleArea : potatoArea != null ? potatoArea : appleArea;
        BerryBlockPlanTab berryBlocks = enabled.contains(FarmWorkspace.BERRY_FARM)
                ? new BerryBlockPlanTab(java.util.Objects.requireNonNull(
                        berryBlockPlanService,
                        "Berry block service is required for the berry workspace."),
                        host, dialogs)
                : null;
        NurseryGrowingBatchTab nurseryBatches = enabled.contains(FarmWorkspace.NURSERY_FARM)
                ? new NurseryGrowingBatchTab(java.util.Objects.requireNonNull(
                        nurseryBatchService,
                        "Nursery batch service is required for the nursery workspace."),
                        host, dialogs)
                : null;
        HerdGroupTab herdGroups = enabled.contains(FarmWorkspace.LIVESTOCK_FARM)
                ? new HerdGroupTab(java.util.Objects.requireNonNull(herdGroupService,
                        "Herd group service is required for the livestock workspace."),
                        host, dialogs)
                : null;
        EggCollectionTab eggCollections = enabled.contains(FarmWorkspace.POULTRY_FARM)
                ? new EggCollectionTab(java.util.Objects.requireNonNull(eggCollectionService,
                        "Egg collection service is required for the poultry workspace."),
                        host, dialogs)
                : null;
        MilkCollectionTab milkCollections = enabled.contains(FarmWorkspace.DAIRY_FARM)
                ? new MilkCollectionTab(java.util.Objects.requireNonNull(milkCollectionService,
                        "Milk collection service is required for the dairy workspace."),
                        host, dialogs)
                : null;
        VineyardBlockPlanTab vineyardBlocks = enabled.contains(FarmWorkspace.VINEYARD_FARM)
                ? new VineyardBlockPlanTab(java.util.Objects.requireNonNull(
                        vineyardBlockPlanService,
                        "Vineyard block service is required for the vineyard workspace."),
                        host, dialogs)
                : null;
        HayCuttingTab hayCuttings = enabled.contains(FarmWorkspace.HAY_FARM)
                ? new HayCuttingTab(java.util.Objects.requireNonNull(hayCuttingService,
                        "Hay cutting service is required for the hay workspace."),
                        host, dialogs)
                : null;
        HopYardPlanTab hopYards = enabled.contains(FarmWorkspace.HOPS_FARM)
                ? new HopYardPlanTab(java.util.Objects.requireNonNull(hopYardPlanService,
                        "Hop yard service is required for the hops workspace."),
                        host, dialogs)
                : null;
        GrainFieldPlanTab grainFields = enabled.contains(FarmWorkspace.GRAIN_FARM)
                ? new GrainFieldPlanTab(java.util.Objects.requireNonNull(grainFieldPlanService,
                        "Grain field service is required for the grains workspace."),
                        host, dialogs)
                : null;
        SeedFieldPlanTab seedFields = enabled.contains(FarmWorkspace.SEED_FARM)
                ? new SeedFieldPlanTab(java.util.Objects.requireNonNull(seedFieldPlanService,
                        "Seed field service is required for the seed workspace."),
                        host, dialogs)
                : null;
        NutBlockPlanTab nutBlocks = enabled.contains(FarmWorkspace.NUT_ORCHARD)
                ? new NutBlockPlanTab(java.util.Objects.requireNonNull(nutBlockPlanService,
                        "Nut block service is required for the tree nut workspace."),
                        host, dialogs)
                : null;
        VegetablePlantingTab vegetablePlantings =
                enabled.contains(FarmWorkspace.VEGETABLE_FARM)
                ? new VegetablePlantingTab(java.util.Objects.requireNonNull(
                        vegetablePlantingService,
                        "Vegetable planting service is required for the vegetable workspace."),
                        host, dialogs)
                : null;
        AquaticCohortTab aquaticCohorts = enabled.contains(FarmWorkspace.AQUACULTURE_FARM)
                ? new AquaticCohortTab(java.util.Objects.requireNonNull(
                        aquaticCohortService,
                        "Aquatic cohort service is required for the aquaculture workspace."),
                        host, dialogs)
                : null;
        ChristmasTreeBlockTab treeBlocks = enabled.contains(FarmWorkspace.CHRISTMAS_TREE_FARM)
                ? new ChristmasTreeBlockTab(java.util.Objects.requireNonNull(
                        christmasTreeBlockService,
                        "Christmas tree block service is required for the Christmas tree workspace."),
                        host, dialogs)
                : null;
        HiveCheckTab hiveChecks = enabled.contains(FarmWorkspace.APIARY_FARM)
                ? new HiveCheckTab(java.util.Objects.requireNonNull(hiveCheckService,
                        "Hive check service is required for the apiary workspace."),
                        host, dialogs)
                : null;
        MushroomBatchTab mushroomBatches = enabled.contains(FarmWorkspace.MUSHROOM_FARM)
                ? new MushroomBatchTab(java.util.Objects.requireNonNull(mushroomBatchPlanService,
                        "Mushroom batch service is required for the mushroom workspace."),
                        host, dialogs)
                : null;
        CranberryBedTab cranberryBeds = enabled.contains(FarmWorkspace.CRANBERRY_FARM)
                ? new CranberryBedTab(java.util.Objects.requireNonNull(cranberryBedPlanService,
                        "Cranberry bed service is required for the cranberry workspace."),
                        host, dialogs)
                : null;
        PumpkinPatchTab pumpkinPatches = enabled.contains(FarmWorkspace.PUMPKIN_PATCH)
                ? new PumpkinPatchTab(java.util.Objects.requireNonNull(pumpkinPatchPlanService,
                        "Pumpkin patch service is required for the pumpkin workspace."),
                        host, dialogs)
                : null;
        FieldScienceTab fieldScience = (FieldScienceCatalog.hasTools(enabled)
                || fieldScienceExportService != null)
                ? new FieldScienceTab(enabled, host, fieldScienceExportService,
                        dialogs, files) : null;
        addItem.setWorkspace(workspace);
        addItem.setEnabledWorkspaces(enabled);
        if (enabled.contains(FarmWorkspace.FLOWER_SHOP)
                && !enabled.contains(FarmWorkspace.ROSE_AND_EVENTS)
                && !enabled.contains(FarmWorkspace.WILDFLOWER_FARM)) {
            productionStudio.setRetailMode(true);
        }
        com.flowerfarm.model.FarmProductProfile productProfile =
                com.flowerfarm.model.FarmProductProfile.combined(workspace, enabled);
        crm.setDefaultUnit(productProfile.defaultUnit());
        harvestLog.setProductProfile(productProfile);
        List<FlowerFarmTab> tabs = enabled.size() > 1
                ? combinedTabs(enabled, workspace, customMixFocus(settings), host,
                        dashboard, marketDay,
                        seasonPlanner, harvestLog, inventoryWorkspace, irrigation,
                        knowledge, crm, reports, trend, history,
                        plantGuide, roseGuide, pricing, visualizer,
                        eventServices, productionStudio,
                        cultivation, potatoArea, appleArea, berryBlocks, nurseryBatches,
                        herdGroups, eggCollections, milkCollections, vineyardBlocks,
                        hayCuttings, hopYards, grainFields, seedFields, nutBlocks,
                        vegetablePlantings, aquaticCohorts, treeBlocks, hiveChecks,
                        mushroomBatches, cranberryBeds, pumpkinPatches, fieldScience)
                : switch (workspace) {
            case ROSE_AND_EVENTS -> List.of(
                    dashboard, marketDay, seasonPlanner, harvestLog, inventoryWorkspace,
                    irrigation, knowledge, crm, reports, trend, history, reference);
            case WILDFLOWER_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Market Day", "Season Planner",
                            "Harvest Log", "Inventory", "Customers & Orders"), host),
                    marketDay, seasonPlanner, harvestLog, inventoryWorkspace,
                    knowledge, crm, reports, trend, history,
                    new ReferenceTab(List.of(plantGuide, pricing)));
            case FLOWER_SHOP -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Inventory", "Customers & Orders",
                            "Event Services", "Production Studio"), host),
                    inventoryWorkspace, crm, eventServices, productionStudio,
                    knowledge, reports, history);
            case CANNABIS_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Cultivation", "Harvest Log",
                            "Inventory", "Customers & Orders"), host),
                    cultivation, harvestLog, inventoryWorkspace, knowledge, crm, reports, history);
            case POTATO_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Field plans", "Harvest Log",
                            "Inventory", "Customers & Orders"), host),
                    cropArea, harvestLog, inventoryWorkspace, knowledge, crm, reports, history);
            case APPLE_ORCHARD -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Orchard blocks", "Harvest Log",
                            "Inventory", "Customers & Orders"), host),
                    cropArea, harvestLog, inventoryWorkspace, knowledge, crm, reports, history);
            case BERRY_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Berry blocks", "Harvest Log",
                            "Inventory", "Customers & Orders"), host),
                    berryBlocks, harvestLog, inventoryWorkspace, knowledge, crm, reports, history);
            case NURSERY_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Growing batches", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    nurseryBatches, inventoryWorkspace, knowledge, crm, reports, history);
            case LIVESTOCK_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Herd groups", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    herdGroups, inventoryWorkspace, knowledge, crm, reports, history);
            case POULTRY_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Egg collections", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    eggCollections, inventoryWorkspace, knowledge, crm, reports, history);
            case DAIRY_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Milk collections", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    milkCollections, inventoryWorkspace, knowledge, crm, reports, history);
            case VINEYARD_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Vineyard blocks", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    vineyardBlocks, inventoryWorkspace, knowledge, crm, reports, history);
            case HAY_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Hay cuttings", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    hayCuttings, inventoryWorkspace, knowledge, crm, reports, history);
            case HOPS_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Hop yards", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    hopYards, inventoryWorkspace, knowledge, crm, reports, history);
            case GRAIN_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Grain fields", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    grainFields, inventoryWorkspace, knowledge, crm, reports, history);
            case SEED_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Seed fields", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    seedFields, inventoryWorkspace, knowledge, crm, reports, history);
            case NUT_ORCHARD -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Nut blocks", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host),
                    nutBlocks, inventoryWorkspace, knowledge, crm, reports, history);
            case VEGETABLE_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Vegetable plantings",
                            "Inventory", "Customers & Orders", "Knowledge Log"), host),
                    vegetablePlantings, inventoryWorkspace, knowledge, crm, reports, history);
            case AQUACULTURE_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Aquatic cohorts",
                            "Inventory", "Customers & Orders", "Knowledge Log"), host),
                    aquaticCohorts, inventoryWorkspace, knowledge, crm, reports, history);
            case CHRISTMAS_TREE_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Tree blocks",
                            "Inventory", "Customers & Orders", "Knowledge Log"), host),
                    treeBlocks, inventoryWorkspace, knowledge, crm, reports, history);
            case APIARY_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Hive checks",
                            "Inventory", "Customers & Orders", "Knowledge Log"), host),
                    hiveChecks, inventoryWorkspace, knowledge, crm, reports, history);
            case MUSHROOM_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Mushroom batches",
                            "Inventory", "Customers & Orders", "Knowledge Log"), host),
                    mushroomBatches, inventoryWorkspace, knowledge, crm, reports, history);
            case CRANBERRY_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Cranberry beds",
                            "Inventory", "Customers & Orders", "Knowledge Log"), host),
                    cranberryBeds, inventoryWorkspace, knowledge, crm, reports, history);
            case PUMPKIN_PATCH -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Pumpkin patches",
                            "Inventory", "Customers & Orders", "Knowledge Log"), host),
                    pumpkinPatches, inventoryWorkspace, knowledge, crm, reports, history);
            case CUSTOM_FARM -> List.of(
                    new WorkspaceHomeTab(workspace, List.of("Harvest Log", "Inventory",
                            "Customers & Orders", "Knowledge Log"), host,
                            customFocus(settings)),
                    harvestLog, inventoryWorkspace, knowledge, crm, reports, history);
        };
        if (fieldScience != null && enabled.size() == 1) {
            List<FlowerFarmTab> withScience = new java.util.ArrayList<>(tabs);
            int beforeInventory = 0;
            while (beforeInventory < withScience.size()
                    && !"Inventory".equals(withScience.get(beforeInventory).getTabTitle())) {
                beforeInventory++;
            }
            withScience.add(beforeInventory, fieldScience);
            tabs = List.copyOf(withScience);
        }
        List<CropAreaPlanTab> cropAreas = new java.util.ArrayList<>(2);
        if (potatoArea != null) cropAreas.add(potatoArea);
        if (appleArea != null) cropAreas.add(appleArea);
        return new Catalog(tabs, dashboard, trend, inventory, addItem,
                harvestLog, marketDay, irrigation, crm, seasonPlanner, productionStudio,
                cultivation, cropArea, cropAreas, berryBlocks, nurseryBatches,
                herdGroups, eggCollections, milkCollections, vineyardBlocks,
                hayCuttings, hopYards, grainFields, seedFields, nutBlocks,
                vegetablePlantings, aquaticCohorts, treeBlocks, hiveChecks,
                mushroomBatches, cranberryBeds, pumpkinPatches,
                knowledge, eventServices, fieldScience);
    }

    private static List<FlowerFarmTab> combinedTabs(java.util.Set<FarmWorkspace> enabled,
            FarmWorkspace primary, String customFocus, TabHost host,
            DashboardTab dashboard,
            MarketDayTab marketDay, SeasonPlannerTab seasonPlanner,
            HarvestLogTab harvestLog, InventoryWorkspaceTab inventory,
            IrrigationInfoTab irrigation, KnowledgeLogTab knowledge, CrmTab crm,
            ReportsTab reports, TrendAnalysisTab trend, SyncHistoryTab history,
            PnwPlantGuideTab plantGuide, RoseVarietiesTab roseGuide,
            PricingInfoTab pricing, RoseVisualizerTab visualizer,
            EventServicesTab eventServices, ProductionStudioTab productionStudio,
            CannabisCultivationTab cultivation, CropAreaPlanTab potatoArea,
            CropAreaPlanTab appleArea, BerryBlockPlanTab berryBlocks,
            NurseryGrowingBatchTab nurseryBatches, HerdGroupTab herdGroups,
            EggCollectionTab eggCollections, MilkCollectionTab milkCollections,
            VineyardBlockPlanTab vineyardBlocks, HayCuttingTab hayCuttings,
            HopYardPlanTab hopYards, GrainFieldPlanTab grainFields,
            SeedFieldPlanTab seedFields, NutBlockPlanTab nutBlocks,
            VegetablePlantingTab vegetablePlantings,
            AquaticCohortTab aquaticCohorts,
            ChristmasTreeBlockTab treeBlocks,
            HiveCheckTab hiveChecks,
            MushroomBatchTab mushroomBatches,
            CranberryBedTab cranberryBeds,
            PumpkinPatchTab pumpkinPatches,
            FieldScienceTab fieldScience) {
        boolean roses = enabled.contains(FarmWorkspace.ROSE_AND_EVENTS);
        boolean wildflowers = enabled.contains(FarmWorkspace.WILDFLOWER_FARM);
        boolean shop = enabled.contains(FarmWorkspace.FLOWER_SHOP);
        boolean growing = roses || wildflowers
                || enabled.contains(FarmWorkspace.CUSTOM_FARM)
                || cultivation != null || potatoArea != null || appleArea != null
                || berryBlocks != null;
        List<FlowerFarmTab> workflows = new java.util.ArrayList<>();
        if (roses) workflows.add(dashboard);
        if (roses || wildflowers) {
            workflows.add(marketDay);
            workflows.add(seasonPlanner);
        }
        if (cultivation != null) workflows.add(cultivation);
        if (potatoArea != null) workflows.add(potatoArea);
        if (appleArea != null) workflows.add(appleArea);
        if (berryBlocks != null) workflows.add(berryBlocks);
        if (nurseryBatches != null) workflows.add(nurseryBatches);
        if (herdGroups != null) workflows.add(herdGroups);
        if (eggCollections != null) workflows.add(eggCollections);
        if (milkCollections != null) workflows.add(milkCollections);
        if (vineyardBlocks != null) workflows.add(vineyardBlocks);
        if (hayCuttings != null) workflows.add(hayCuttings);
        if (hopYards != null) workflows.add(hopYards);
        if (grainFields != null) workflows.add(grainFields);
        if (seedFields != null) workflows.add(seedFields);
        if (nutBlocks != null) workflows.add(nutBlocks);
        if (vegetablePlantings != null) workflows.add(vegetablePlantings);
        if (aquaticCohorts != null) workflows.add(aquaticCohorts);
        if (treeBlocks != null) workflows.add(treeBlocks);
        if (hiveChecks != null) workflows.add(hiveChecks);
        if (mushroomBatches != null) workflows.add(mushroomBatches);
        if (cranberryBeds != null) workflows.add(cranberryBeds);
        if (pumpkinPatches != null) workflows.add(pumpkinPatches);
        if (growing) workflows.add(harvestLog);
        if (fieldScience != null) workflows.add(fieldScience);
        workflows.add(inventory);
        if (roses) workflows.add(irrigation);
        workflows.add(knowledge);
        workflows.add(crm);
        if (roses || shop) {
            workflows.add(eventServices);
            workflows.add(productionStudio);
        }
        workflows.add(reports);
        if (roses || wildflowers) workflows.add(trend);
        workflows.add(history);
        if (roses) {
            workflows.add(new ReferenceTab(plantGuide, roseGuide, pricing,
                    visualizer));
        } else if (wildflowers) {
            workflows.add(new ReferenceTab(List.of(plantGuide, pricing)));
        }
        List<String> destinations = workflows.stream()
                .map(FlowerFarmTab::getTabTitle).toList();
        List<FlowerFarmTab> result = new java.util.ArrayList<>(workflows.size() + 1);
        result.add(new WorkspaceHomeTab(primary, destinations, host,
                customFocus, enabled));
        result.addAll(workflows);
        return List.copyOf(result);
    }

    static String customFocus(FarmOperationSettings settings) {
        if (settings == null) {
            return "";
        }
        String specialties = settings.getSpecialtyCrops().stream().limit(2)
                .collect(java.util.stream.Collectors.joining(" · "));
        return specialties.isBlank()
                ? FarmSetupDialog.enterpriseLabel(settings.getPrimaryEnterprise())
                : specialties;
    }

    static String customMixFocus(FarmOperationSettings settings) {
        if (settings == null) {
            return "";
        }
        return settings.getSpecialtyCrops().stream().limit(3)
                .collect(java.util.stream.Collectors.joining(" · "));
    }

    public record Catalog(List<FlowerFarmTab> tabs, DashboardTab dashboard,
                          TrendAnalysisTab trend, InventoryTab inventory,
                          AddItemTab addItem, HarvestLogTab harvest,
                          MarketDayTab marketDay, IrrigationInfoTab irrigation,
                          CrmTab crm, SeasonPlannerTab seasonPlanner,
                          ProductionStudioTab productionStudio,
                          CannabisCultivationTab cultivation,
                          CropAreaPlanTab cropArea,
                          List<CropAreaPlanTab> cropAreas,
                          BerryBlockPlanTab berryBlocks,
                          NurseryGrowingBatchTab nurseryBatches,
                          HerdGroupTab herdGroups,
                          EggCollectionTab eggCollections,
                          MilkCollectionTab milkCollections,
                          VineyardBlockPlanTab vineyardBlocks,
                          HayCuttingTab hayCuttings,
                          HopYardPlanTab hopYards,
                          GrainFieldPlanTab grainFields,
                          SeedFieldPlanTab seedFields,
                          NutBlockPlanTab nutBlocks,
                          VegetablePlantingTab vegetablePlantings,
                          AquaticCohortTab aquaticCohorts,
                          ChristmasTreeBlockTab treeBlocks,
                          HiveCheckTab hiveChecks,
                          MushroomBatchTab mushroomBatches,
                          CranberryBedTab cranberryBeds,
                          PumpkinPatchTab pumpkinPatches,
                          KnowledgeLogTab knowledge, EventServicesTab eventServices,
                          FieldScienceTab fieldScience) {
        public Catalog {
            tabs = List.copyOf(tabs);
            cropAreas = List.copyOf(cropAreas);
        }
    }
}
