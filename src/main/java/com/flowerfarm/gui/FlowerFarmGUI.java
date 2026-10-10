package com.flowerfarm.gui;

import com.flowerfarm.auth.DesktopSession;
import com.flowerfarm.model.FieldScienceExportSummary;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.model.PnwPlantProfile;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.gui.automation.SwingHarnessBridge;
import com.flowerfarm.gui.presenter.ConnectorActionPresenter;
import com.flowerfarm.gui.tabs.*;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CannabisCultivationService;
import com.flowerfarm.service.CropAreaPlanService;
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
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.FarmOperationSettingsService;
import com.flowerfarm.service.FieldScienceExportService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import com.flowerfarm.service.SeasonPlanService;
import com.flowerfarm.service.CoolerShelfLife;
import com.flowerfarm.service.FloristAvailabilityExport;
import com.flowerfarm.service.StemDemand;
import com.flowerfarm.support.SupportGuide;
import com.flowerfarm.update.AppVersion;
import com.flowerfarm.update.UpdateMessages;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;

/**
 * Desktop shell for the selected farm or floral retail workspace.
 *
 * <p>This class is a <b>lightweight orchestrator</b>. Each major feature lives
 * in its own {@link FlowerFarmTab} implementation under {@code gui.tabs}; the
 * orchestrator only owns frame-level concerns: the tabbed pane, menu bar,
 * connector button bar, status bar, and keyboard shortcuts. It implements
 * {@link TabHost} so tabs can request global refreshes, tab switches, status
 * updates, and trend forecasts without coupling to the frame.
 *
 * <p>Uses <em>composition</em> rather than extending {@link JFrame} so Spring
 * can instantiate this bean without touching AWT during context initialisation
 * (which would throw {@link java.awt.HeadlessException} in headless/CI
 * environments). All Swing work is deferred to the first call of {@link #run}
 * and then dispatched onto the Event Dispatch Thread.
 */
@Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class FlowerFarmGUI implements ApplicationRunner, TabHost {

    private final FarmTabCatalog tabCatalog;
    private final ConnectorActionPresenter connectorActions;
    private final GuiLoginGate loginGate;
    private final DesktopSession session;
    private final FarmProfile farmProfile;
    private final FarmOperationSettingsService farmOperationSettingsService;
    private final FarmDialogs dialogs;
    private final FarmClipboard clipboard;
    private final FarmMailer mailer;
    private final FarmBrowser browser;
    private final FarmFileChooser fileChooser;
    private final InventoryService inventoryService;
    private final HarvestService harvestService;
    private final SwingHarnessBridge harnessBridge;

    // Created lazily on the EDT inside initialise() — never touched before run().
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private JPanel contentDeck;
    private FarmTypeLauncher farmTypeLauncher;
    private FarmTabNavigator navigator;
    private JLabel statusLabel;
    private JLabel sessionLabel;
    private JPanel connectorGrid;
    private JPanel brandHeader;
    private final List<AbstractButton> mutateButtons = new ArrayList<>();

    private final List<FlowerFarmTab> tabs = new ArrayList<>();
    private FarmWorkspace currentWorkspace = FarmWorkspace.ROSE_AND_EVENTS;
    private FarmOperationSettings currentOperationSettings;
    private boolean workspaceLoadFailed;
    private DashboardTab dashboard;
    private TrendAnalysisTab trendTab;
    private InventoryTab inventoryTab;
    private AddItemTab addItemTab;
    private HarvestLogTab harvestTab;
    private MarketDayTab marketDayTab;
    private IrrigationInfoTab irrigationTab;
    private CrmTab crmTab;
    private SeasonPlannerTab seasonPlannerTab;
    private ProductionStudioTab productionStudioTab;
    private CannabisCultivationTab cultivationTab;
    private CropAreaPlanTab cropAreaTab;
    private List<CropAreaPlanTab> cropAreaTabs = List.of();
    private BerryBlockPlanTab berryBlockPlanTab;
    private NurseryGrowingBatchTab nurseryGrowingBatchTab;
    private HerdGroupTab herdGroupTab;
    private EggCollectionTab eggCollectionTab;
    private MilkCollectionTab milkCollectionTab;
    private VineyardBlockPlanTab vineyardBlockPlanTab;
    private HayCuttingTab hayCuttingTab;
    private HopYardPlanTab hopYardPlanTab;
    private GrainFieldPlanTab grainFieldPlanTab;
    private SeedFieldPlanTab seedFieldPlanTab;
    private NutBlockPlanTab nutBlockPlanTab;
    private VegetablePlantingTab vegetablePlantingTab;
    private AquaticCohortTab aquaticCohortTab;
    private ChristmasTreeBlockTab christmasTreeBlockTab;
    private HiveCheckTab hiveCheckTab;
    private MushroomBatchTab mushroomBatchTab;
    private CranberryBedTab cranberryBedTab;
    private PumpkinPatchTab pumpkinPatchTab;
    private KnowledgeLogTab knowledgeTab;
    private FieldScienceTab fieldScienceTab;
    private EventServicesTab eventServicesTab;

    private DesktopUpdateController updateController;
    private DesktopBackupController backupController;
    private DesktopRemindersController remindersController;
    private DesktopFloristExportController floristExportController;
    private DesktopCoolerReviewController coolerReviewController;
    private DesktopStemDemandController stemDemandController;
    private DesktopScoutingController scoutingController;

    private final Preferences prefs = Preferences.userNodeForPackage(FlowerFarmGUI.class);
    private boolean darkMode;
    private JCheckBoxMenuItem darkModeItem;

    private static final String PREF_WIDTH  = "window.width";
    private static final String PREF_HEIGHT = "window.height";
    private static final String PREF_TAB    = "tab.lastIndex";
    private static final String PREF_TAB_TITLE = "tab.lastTitle";
    private static final String PREF_WORKSPACE = "tab.lastWorkspace";
    private static final String PREF_DARK   = "ui.dark";
    private static final String PREF_INTEGRATIONS = "ui.integrationsExpanded";
    /** Once true, the automatic first-run Start here dialog stays quiet. */
    private static final String PREF_START_HERE_DONE = StartHereGuide.PREF_DONE;
    private static final String PREF_SUPPORT_EMAIL = SupportGuide.PREF_EMAIL;
    private static final String LAUNCHER_CARD = "farm-type-launcher";
    private static final String WORKSPACE_CARD = "farm-workspace";

    @Autowired
    public FlowerFarmGUI(InventoryService inventoryService,
                         TrendService trendService,
                         ConnectorRegistry connectorRegistry,
                         HarvestService harvestService,
                         SyncHistoryService syncHistoryService,
                         CustomerService customerService,
                         OrderService orderService,
                         ReportService reportService,
                         IrrigationAdvisorService irrigationAdvisorService,
                         MarketDayPackingService marketDayPackingService,
                         MorningBriefingService morningBriefingService,
                         DayCloseoutService dayCloseoutService,
                         CustomerStatementService customerStatementService,
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
                         PumpkinPatchPlanService pumpkinPatchPlanService,
                         GuiLoginGate loginGate,
                         DesktopSession session,
                         FarmProfile farmProfile,
                         FarmOperationSettingsService farmOperationSettingsService,
                         SwingHarnessBridge harnessBridge) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, nutBlockPlanService,
                vegetablePlantingService, aquaticCohortService, christmasTreeBlockService,
                hiveCheckService, mushroomBatchPlanService, cranberryBedPlanService,
                pumpkinPatchPlanService,
                loginGate, session, farmProfile,
                farmOperationSettingsService,
                harnessBridge,
                FarmDialogs.swing(), FarmFileChooser.swing());
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
                  KnowledgeService knowledgeService,
                  SeasonPlanService seasonPlanService,
                  ProductionStudioService productionStudioService,
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, loginGate, session, farmProfile,
                null, harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
                  KnowledgeService knowledgeService,
                  SeasonPlanService seasonPlanService,
                  ProductionStudioService productionStudioService,
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, null, null,
                loginGate, session, farmProfile,
                farmOperationSettingsService, harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
                  KnowledgeService knowledgeService,
                  SeasonPlanService seasonPlanService,
                  ProductionStudioService productionStudioService,
                  CannabisCultivationService cannabisCultivationService,
                  CropAreaPlanService cropAreaPlanService,
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
                  KnowledgeService knowledgeService,
                  SeasonPlanService seasonPlanService,
                  ProductionStudioService productionStudioService,
                  CannabisCultivationService cannabisCultivationService,
                  CropAreaPlanService cropAreaPlanService,
                  BerryBlockPlanService berryBlockPlanService,
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
                  KnowledgeService knowledgeService,
                  SeasonPlanService seasonPlanService,
                  ProductionStudioService productionStudioService,
                  CannabisCultivationService cannabisCultivationService,
                  CropAreaPlanService cropAreaPlanService,
                  BerryBlockPlanService berryBlockPlanService,
                  NurseryBatchService nurseryBatchService,
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                null, loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
                  KnowledgeService knowledgeService,
                  SeasonPlanService seasonPlanService,
                  ProductionStudioService productionStudioService,
                  CannabisCultivationService cannabisCultivationService,
                  CropAreaPlanService cropAreaPlanService,
                  BerryBlockPlanService berryBlockPlanService,
                  NurseryBatchService nurseryBatchService,
                  HerdGroupService herdGroupService,
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, null, loginGate, session, farmProfile,
                farmOperationSettingsService, harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
                  KnowledgeService knowledgeService,
                  SeasonPlanService seasonPlanService,
                  ProductionStudioService productionStudioService,
                  CannabisCultivationService cannabisCultivationService,
                  CropAreaPlanService cropAreaPlanService,
                  BerryBlockPlanService berryBlockPlanService,
                  NurseryBatchService nurseryBatchService,
                  HerdGroupService herdGroupService,
                  EggCollectionService eggCollectionService,
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, null, loginGate, session,
                farmProfile, farmOperationSettingsService, harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                null, loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, null, loginGate, session, farmProfile,
                farmOperationSettingsService, harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, null, loginGate, session, farmProfile,
                farmOperationSettingsService, harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, nutBlockPlanService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, nutBlockPlanService,
                vegetablePlantingService, null, loginGate, session, farmProfile,
                farmOperationSettingsService, harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, nutBlockPlanService,
                vegetablePlantingService, aquaticCohortService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, nutBlockPlanService,
                vegetablePlantingService, aquaticCohortService,
                christmasTreeBlockService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, nutBlockPlanService,
                vegetablePlantingService, aquaticCohortService,
                christmasTreeBlockService, hiveCheckService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, nutBlockPlanService,
                vegetablePlantingService, aquaticCohortService,
                christmasTreeBlockService, hiveCheckService, mushroomBatchPlanService,
                null, loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, nutBlockPlanService,
                vegetablePlantingService, aquaticCohortService,
                christmasTreeBlockService, hiveCheckService, mushroomBatchPlanService,
                cranberryBedPlanService, null,
                loginGate, session, farmProfile, farmOperationSettingsService,
                harnessBridge, dialogs, fileChooser);
    }

    FlowerFarmGUI(InventoryService inventoryService,
                  TrendService trendService,
                  ConnectorRegistry connectorRegistry,
                  HarvestService harvestService,
                  SyncHistoryService syncHistoryService,
                  CustomerService customerService,
                  OrderService orderService,
                  ReportService reportService,
                  IrrigationAdvisorService irrigationAdvisorService,
                  MarketDayPackingService marketDayPackingService,
                  MorningBriefingService morningBriefingService,
                  DayCloseoutService dayCloseoutService,
                  CustomerStatementService customerStatementService,
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
                  PumpkinPatchPlanService pumpkinPatchPlanService,
                  GuiLoginGate loginGate,
                  DesktopSession session,
                  FarmProfile farmProfile,
                  FarmOperationSettingsService farmOperationSettingsService,
                  SwingHarnessBridge harnessBridge,
                  FarmDialogs dialogs,
                  FarmFileChooser fileChooser) {
        // No AWT calls here — Spring safe to construct this bean at any time.
        this.tabCatalog = new FarmTabCatalog(inventoryService, trendService, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, cannabisCultivationService,
                cropAreaPlanService, berryBlockPlanService, nurseryBatchService,
                herdGroupService, eggCollectionService, milkCollectionService,
                vineyardBlockPlanService, hayCuttingService, hopYardPlanService,
                grainFieldPlanService, seedFieldPlanService, nutBlockPlanService,
                vegetablePlantingService, aquaticCohortService,
                christmasTreeBlockService, hiveCheckService, mushroomBatchPlanService,
                cranberryBedPlanService, pumpkinPatchPlanService);
        this.connectorActions = new ConnectorActionPresenter(inventoryService, connectorRegistry);
        this.loginGate = loginGate;
        this.session = session;
        this.farmProfile = farmProfile;
        this.farmOperationSettingsService = farmOperationSettingsService;
        this.harnessBridge = Objects.requireNonNull(harnessBridge, "harnessBridge");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.clipboard = FarmClipboard.system();
        this.mailer = FarmMailer.desktop();
        this.browser = FarmBrowser.desktop();
        this.fileChooser = Objects.requireNonNull(fileChooser, "fileChooser");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.harvestService = Objects.requireNonNull(harvestService, "harvestService");
    }

    /** Automatic updates are optional so headless tests and slices can build the frame without them. */
    @Autowired(required = false)
    void setUpdateController(DesktopUpdateController updateController) {
        this.updateController = updateController;
    }

    /** In-app backups are optional for the same reason. */
    @Autowired(required = false)
    void setBackupController(DesktopBackupController backupController) {
        this.backupController = backupController;
    }

    /** Today’s reminders banner, Help menu, and optional tray nudge. */
    @Autowired(required = false)
    void setRemindersController(DesktopRemindersController remindersController) {
        this.remindersController = remindersController;
    }

    /** Florist availability CSV / clipboard / email share. */
    @Autowired(required = false)
    void setFloristExportController(DesktopFloristExportController floristExportController) {
        this.floristExportController = floristExportController;
    }

    /** Stem demand board: stems by crop and date for upcoming orders. */
    @Autowired(required = false)
    void setStemDemandController(DesktopStemDemandController stemDemandController) {
        this.stemDemandController = stemDemandController;
    }

    /** Cooler lot shelf-life review (aging, intervals, QR labels). */
    @Autowired(required = false)
    void setCoolerReviewController(DesktopCoolerReviewController coolerReviewController) {
        this.coolerReviewController = coolerReviewController;
    }

    /** Scouting log, lab sample tracking, sample guides, and lab directory. */
    @Autowired(required = false)
    void setScoutingController(DesktopScoutingController scoutingController) {
        this.scoutingController = scoutingController;
    }

    @Autowired
    void configureFieldScienceExportService(FieldScienceExportService service) {
        tabCatalog.setFieldScienceExportService(service);
    }

    // ── ApplicationRunner ─────────────────────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) {
        // Headless environments (Docker, CI, ssh) get REST-only mode — no Swing (ADR-001).
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("Headless environment detected — skipping GUI, REST API only.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!loginGate.promptUntilAuthenticatedOrCancel()) {
                System.err.println("Login cancelled or failed — GUI not started.");
                System.exit(1);
                return;
            }
            initialise();
        });
    }

    // ── GUI bootstrap ─────────────────────────────────────────────────────────

    public void initialise() {
        // Apply the saved theme before any Swing components are created.
        darkMode = prefs.getBoolean(PREF_DARK, false);
        applyLookAndFeel(darkMode);
        currentWorkspace = loadWorkspace();

        frame = new JFrame(applicationTitle());
        frame.setSize(prefs.getInt(PREF_WIDTH, 1500), prefs.getInt(PREF_HEIGHT, 900));
        frame.setMinimumSize(new Dimension(1100, 700));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        brandHeader = buildBrandHeader();
        frame.add(brandHeader, BorderLayout.NORTH);

        // Build tabs (constructor injection of services + this TabHost).
        contentDeck = new JPanel(new CardLayout());
        contentDeck.setName("farm.contentDeck");
        frame.add(contentDeck, BorderLayout.CENTER);
        buildPrimaryNavigation();

        frame.add(buildSouthBar(), BorderLayout.SOUTH);

        frame.setJMenuBar(buildMenuBar());
        installShortcuts();
        applyRoleToUi();

        frame.setLocationRelativeTo(null);

        // Restore a tab only when the saved navigation belongs to this workspace.
        String savedWorkspace = prefs.get(PREF_WORKSPACE, "");
        String lastTitle = prefs.get(PREF_TAB_TITLE, "");
        boolean sameWorkspace = currentWorkspace.name().equals(savedWorkspace)
                || (savedWorkspace.isBlank()
                        && currentWorkspace == FarmWorkspace.ROSE_AND_EVENTS);
        if (!sameWorkspace || lastTitle.isBlank() || !navigator.select(lastTitle)) {
            int lastTab = sameWorkspace && currentWorkspace == FarmWorkspace.ROSE_AND_EVENTS
                    ? prefs.getInt(PREF_TAB, 0) : 0;
            if (lastTab >= 0 && lastTab < tabs.size()) {
                tabbedPane.setSelectedIndex(lastTab);
            }
        }
        if (dashboard != null && currentWorkspace == FarmWorkspace.ROSE_AND_EVENTS) {
            dashboard.applyTheme(darkMode);
        }
        if (trendTab != null && (currentWorkspace == FarmWorkspace.ROSE_AND_EVENTS
                || currentWorkspace == FarmWorkspace.WILDFLOWER_FARM)) {
            trendTab.applyTheme();
        }
        showFarmTypeLauncher();

        // Preserve meaningful CRM drafts unless the worker explicitly discards them.
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                closeApplication(false);
            }

            @Override public void windowClosed(WindowEvent event) {
                harnessBridge.detach();
            }
        });

        frame.setVisible(true);
        harnessBridge.attach(frame.getRootPane(), title -> {
            boolean selected = navigator.select(title);
            if (selected) {
                showWorkspace();
            }
            return selected;
        });
        refreshAll();
        if (loginGate.isEnabled()) {
            setStatus("Signed in as " + session.displayName() + " · " + session.roleHint());
        } else {
            setStatus("Ready · local mode (auth off)");
        }
        if (updateController != null) {
            updateController.attach(new DesktopUpdateController.Host(frame, dialogs, this::setStatus,
                    () -> confirmApplicationClose(false), this::savePreferences));
        }
        if (backupController != null) {
            backupController.attach(new DesktopBackupController.Host(frame, dialogs, fileChooser, this::setStatus,
                    () -> confirmApplicationClose(false), this::savePreferences));
        }
        if (remindersController != null) {
            remindersController.attach(new DesktopRemindersController.Host(
                    frame, dialogs, this::setStatus, this::openReminderTarget,
                    () -> showHarvestEntry(null, null)));
        }
        if (floristExportController != null) {
            floristExportController.attach(new DesktopFloristExportController.Host(
                    frame, dialogs, fileChooser, clipboard, mailer, this::setStatus));
        }
        if (stemDemandController != null) {
            stemDemandController.attach(new DesktopStemDemandController.Host(
                    frame, dialogs, fileChooser, clipboard, this::setStatus));
        }
        if (coolerReviewController != null) {
            coolerReviewController.attach(new DesktopCoolerReviewController.Host(
                    frame, dialogs, fileChooser, this::setStatus));
        }
        if (scoutingController != null) {
            scoutingController.attach(new DesktopScoutingController.Host(
                    frame, dialogs, fileChooser, this::setStatus));
        }
        bindCurrentWorkspaceActions();
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (remindersController != null) {
                remindersController.maybeNotifyOnStartup();
            }
        });
        if (workspaceLoadFailed) {
            setStatus("Farm workspace could not be loaded; showing the default rose view. "
                    + "Check the local data store.");
        }
    }

    /** Rebind actions after the selected farm type rebuilds its navigation. */
    private void bindCurrentWorkspaceActions() {
        if (remindersController != null) {
            if (dashboard != null && tabs.contains(dashboard)) {
                dashboard.bindReminders(remindersController::openReminder,
                        remindersController::hideBannerForToday,
                        remindersController::refreshBanner);
                remindersController.attachDashboardBanner(dashboard.remindersPanel());
            } else {
                remindersController.attachDashboardBanner(null);
            }
        }
        if (floristExportController != null && inventoryTab != null) {
            inventoryTab.bindFloristShare(floristExportController::shareAvailability);
        }
    }

    private FarmWorkspace loadWorkspace() {
        if (farmOperationSettingsService == null) {
            return FarmWorkspace.ROSE_AND_EVENTS;
        }
        try {
            var settings = farmOperationSettingsService.getSettings();
            currentOperationSettings = settings;
            return settings == null ? FarmWorkspace.ROSE_AND_EVENTS : settings.getWorkspace();
        } catch (RuntimeException exception) {
            workspaceLoadFailed = true;
            return FarmWorkspace.ROSE_AND_EVENTS;
        }
    }

    private void buildPrimaryNavigation() {
        FarmTabCatalog.Catalog catalog = tabCatalog.create(
                this, dialogs, fileChooser, currentWorkspace,
                currentOperationSettings);
        dashboard = catalog.dashboard();
        trendTab = catalog.trend();
        inventoryTab = catalog.inventory();
        addItemTab = catalog.addItem();
        harvestTab = catalog.harvest();
        marketDayTab = catalog.marketDay();
        irrigationTab = catalog.irrigation();
        crmTab = catalog.crm();
        seasonPlannerTab = catalog.seasonPlanner();
        productionStudioTab = catalog.productionStudio();
        cultivationTab = catalog.cultivation();
        cropAreaTab = catalog.cropArea();
        cropAreaTabs = catalog.cropAreas();
        berryBlockPlanTab = catalog.berryBlocks();
        nurseryGrowingBatchTab = catalog.nurseryBatches();
        herdGroupTab = catalog.herdGroups();
        eggCollectionTab = catalog.eggCollections();
        milkCollectionTab = catalog.milkCollections();
        vineyardBlockPlanTab = catalog.vineyardBlocks();
        hayCuttingTab = catalog.hayCuttings();
        hopYardPlanTab = catalog.hopYards();
        grainFieldPlanTab = catalog.grainFields();
        seedFieldPlanTab = catalog.seedFields();
        nutBlockPlanTab = catalog.nutBlocks();
        vegetablePlantingTab = catalog.vegetablePlantings();
        aquaticCohortTab = catalog.aquaticCohorts();
        christmasTreeBlockTab = catalog.treeBlocks();
        hiveCheckTab = catalog.hiveChecks();
        mushroomBatchTab = catalog.mushroomBatches();
        cranberryBedTab = catalog.cranberryBeds();
        pumpkinPatchTab = catalog.pumpkinPatches();
        knowledgeTab = catalog.knowledge();
        fieldScienceTab = catalog.fieldScience();
        eventServicesTab = catalog.eventServices();
        tabs.clear();
        tabs.addAll(catalog.tabs());

        if (tabbedPane != null) {
            contentDeck.remove(tabbedPane);
        }
        tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setName("farm.primaryNavigation");
        tabbedPane.getAccessibleContext().setAccessibleName("Primary farm workflows");
        tabbedPane.getAccessibleContext().setAccessibleDescription(
                "Use Alt plus the shown mnemonic, or Alt+1 through Alt+9, to switch workflows.");
        navigator = new FarmTabNavigator(tabbedPane, tabs, currentWorkspace,
                currentOperationSettings != null
                        && currentOperationSettings.getEnabledWorkspaces().size() > 1);
        navigator.populate();
        JTabbedPane activePane = tabbedPane;
        FarmTabNavigator activeNavigator = navigator;
        tabbedPane.addChangeListener(e -> {
            if (activePane != tabbedPane) {
                return;
            }
            activeNavigator.refreshSelected(index -> {
            prefs.putInt(PREF_TAB, index);
            prefs.put(PREF_TAB_TITLE, activePane.getTitleAt(index));
            prefs.put(PREF_WORKSPACE, currentWorkspace.name());
            });
        });
        contentDeck.add(tabbedPane, WORKSPACE_CARD);
    }

    /** Frame-level integrations and status chrome, kept separate for theme verification. */
    JPanel buildSouthBar() {
        JPanel south = new JPanel(new BorderLayout());
        south.setName("farm.southBar");
        JScrollPane connectorScroll = buildConnectorBar();
        boolean integrationsExpanded = prefs.getBoolean(PREF_INTEGRATIONS, false);
        connectorScroll.setVisible(integrationsExpanded);
        JToggleButton integrationsToggle = new JToggleButton(
                integrationsExpanded ? "Hide integrations" : "Show integrations",
                integrationsExpanded);
        integrationsToggle.setToolTipText(
                "Open connector actions for CSV, commerce, sheets, and farm systems.");
        integrationsToggle.getAccessibleContext().setAccessibleName("Toggle integrations tray");
        integrationsToggle.getAccessibleContext().setAccessibleDescription(
                "Show or hide import, export, and synchronization actions.");
        JPanel integrationHeader = FarmUi.toolbar(
                integrationsToggle,
                FarmUi.mutedLabel("CSV · Excel · Shopify · Square · Sheets · Airtable · farm systems"));
        integrationHeader.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        JPanel integrations = new JPanel(new BorderLayout());
        integrations.add(integrationHeader, BorderLayout.NORTH);
        integrations.add(connectorScroll, BorderLayout.CENTER);
        integrationsToggle.addActionListener(e -> {
            boolean expanded = integrationsToggle.isSelected();
            connectorScroll.setVisible(expanded);
            integrationsToggle.setText(expanded ? "Hide integrations" : "Show integrations");
            prefs.putBoolean(PREF_INTEGRATIONS, expanded);
            integrations.revalidate();
            integrations.repaint();
        });
        south.add(integrations, BorderLayout.CENTER);
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setName("farm.statusBar");
        statusRow.setBackground(FarmUi.surfaceColor());
        statusLabel = FarmUi.mutedLabel("Ready · " + farmProfile.getName());
        statusLabel.setName("farm.statusMessage");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        statusLabel.getAccessibleContext().setAccessibleName("Application status");
        sessionLabel = FarmUi.statusBadge(sessionBadgeText(), FarmUi.Tone.NEUTRAL);
        sessionLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(4, 10, 4, 10), sessionLabel.getBorder()));
        sessionLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        statusRow.setBorder(BorderFactory.createMatteBorder(
                1, 0, 0, 0, FarmUi.borderColor()));
        statusRow.add(statusLabel, BorderLayout.CENTER);
        statusRow.add(sessionLabel, BorderLayout.EAST);
        south.add(statusRow, BorderLayout.SOUTH);
        return south;
    }

    private String sessionBadgeText() {
        if (!loginGate.isEnabled()) {
            return "Local mode · OWNER";
        }
        return "👤 " + session.displayName();
    }

    void applyRoleToUi() {
        boolean canWrite = canMutateData();
        for (AbstractButton b : mutateButtons) {
            GuiPermissions.setWritable(canWrite, b);
        }
        for (FlowerFarmTab tab : tabs) {
            tab.applyRolePermissions(canWrite);
        }
        if (sessionLabel != null) {
            sessionLabel.setText(sessionBadgeText());
            sessionLabel.setToolTipText(session.roleHint());
            if (!canWrite && loginGate.isEnabled()) {
                FarmUi.styleStatusBadge(sessionLabel, FarmUi.Tone.WARNING);
            } else if (loginGate.isEnabled()) {
                FarmUi.styleStatusBadge(sessionLabel, FarmUi.Tone.SUCCESS);
            } else {
                FarmUi.styleStatusBadge(sessionLabel, FarmUi.Tone.NEUTRAL);
            }
        }
        if (frame != null && loginGate.isEnabled()) {
            frame.setTitle(applicationTitle() + " · " + session.displayName());
        }
        if (farmTypeLauncher != null && farmTypeLauncher.isShowing()) {
            showFarmTypeLauncher();
        }
    }

    // ── Menu bar ──────────────────────────────────────────────────────────────

    JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        Set<FarmWorkspace> enabled = currentOperationSettings == null
                ? Set.of(currentWorkspace)
                : currentOperationSettings.getEnabledWorkspaces();

        JMenu fileMenu = menu("File", KeyEvent.VK_F,
                "Import farm data, export a local backup, or exit FlowersForever.");
        JMenuItem chooseFarmType = menuItem("Choose or combine farm types…", KeyEvent.VK_T,
                "Open the farm-type menu to enter, combine, or change workspaces.",
                e -> showFarmTypeLauncher());
        chooseFarmType.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        fileMenu.add(chooseFarmType);
        fileMenu.addSeparator();
        fileMenu.add(writeMenuItem("Import from spreadsheet…", KeyEvent.VK_I,
                "Bring products from a CSV or Excel spreadsheet you already use.",
                e -> importFromSpreadsheet()));
        fileMenu.add(writeMenuItem("Import CSV…", KeyEvent.VK_C,
                "Import products from a CSV spreadsheet.",
                e -> runImportConnector("csv", "CSV")));
        fileMenu.add(writeMenuItem("Import Excel…", KeyEvent.VK_E,
                "Import products from an Excel spreadsheet.",
                e -> runImportConnector("excel", "Excel")));
        fileMenu.add(menuItem("Export All to CSV…", KeyEvent.VK_A,
                "Save all current inventory records to a CSV file.",
                e -> exportAllToCsv()));
        if (enabled.contains(FarmWorkspace.ROSE_AND_EVENTS)
                || enabled.contains(FarmWorkspace.WILDFLOWER_FARM)
                || enabled.contains(FarmWorkspace.FLOWER_SHOP)) {
            fileMenu.add(menuItem("Share availability with florists…", KeyEvent.VK_L,
                    "Copy or save a Florlist-friendly live stock list with this week’s planned stems.",
                    e -> shareFloristAvailability()));
            fileMenu.add(menuItem("Stem demand board…", KeyEvent.VK_D,
                    "Total the stems upcoming orders need by crop and date, see what the cooler and "
                            + "planned harvest cover, and copy or save the buying list.",
                    e -> openStemDemandBoard()));
            fileMenu.add(menuItem("Buying list by event…", KeyEvent.VK_Y,
                    "For the same dates, see each event's stems: what comes from the cooler, what to "
                            + "cut, and what to buy, with cooler stock shared once.",
                    e -> openBuyingListByEvent()));
        }
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Back up now…", KeyEvent.VK_B,
                "Save a checked backup of all farm records while you keep working.",
                e -> backUpNow()));
        fileMenu.add(writeMenuItem("Restore from a backup…", KeyEvent.VK_R,
                "Check a backup, save the current records, then reopen with the backup's records.",
                e -> restoreFromBackup()));
        fileMenu.add(writeMenuItem("Automatic backups…", KeyEvent.VK_U,
                "Choose a folder or USB drive for regular automatic backups.",
                e -> automaticBackups()));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Farm setup…", KeyEvent.VK_S,
                "Choose a workspace, farm state, enterprises, and specialties. "
                        + "Only an OWNER can save changes.",
                e -> showFarmSetup()));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Exit", KeyEvent.VK_X,
                "Close FlowersForever after reviewing any unfinished work.",
                e -> closeApplication(false)));
        menuBar.add(fileMenu);

        JMenu viewMenu = menu("View", KeyEvent.VK_V,
                "Refresh farm data or switch between light and dark mode.");
        JMenuItem refresh = menuItem("Refresh All Tabs", KeyEvent.VK_R,
                "Reload every workflow from the latest saved farm records.", e -> {
            refreshAll();
            setStatus("All tabs refreshed.");
        });
        refresh.setAccelerator(KeyStroke.getKeyStroke("F5"));
        viewMenu.add(refresh);
        viewMenu.addSeparator();
        darkModeItem = new JCheckBoxMenuItem("Dark Mode", darkMode);
        configureMenuItem(darkModeItem, KeyEvent.VK_D,
                "Toggle the entire desktop between light and dark mode.");
        darkModeItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        darkModeItem.addActionListener(e -> toggleDarkMode(darkModeItem.isSelected()));
        viewMenu.add(darkModeItem);
        menuBar.add(viewMenu);

        if (enabled.contains(FarmWorkspace.ROSE_AND_EVENTS)
                || enabled.contains(FarmWorkspace.WILDFLOWER_FARM)) {
            JMenu toolsMenu = menu("Tools", KeyEvent.VK_T,
                    "Open farm analysis and planning tools.");
            toolsMenu.add(menuItem("Run Harvest Forecast", KeyEvent.VK_H,
                    "Open Trend Analysis and calculate the latest harvest forecast.",
                    e -> runTrendAnalysis()));
            menuBar.add(toolsMenu);
        }

        JMenu accountMenu = menu("Account", KeyEvent.VK_A,
                "Review the active worker, role, and sign-in options.");
        JMenuItem myAccess = menuItem("My access", KeyEvent.VK_M,
                "Review the current worker and available farm permissions.",
                e -> showWhoAmI());
        myAccess.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        accountMenu.add(myAccess);
        if (loginGate.isEnabled()) {
            accountMenu.add(menuItem("Switch user…", KeyEvent.VK_S,
                    "Choose another configured farm account.", e -> switchUser()));
            accountMenu.add(menuItem("Sign out", KeyEvent.VK_O,
                    "Review unfinished work, then sign out and close FlowersForever.",
                    e -> signOut()));
        } else {
            JMenuItem disabled = new JMenuItem("Local mode — full OWNER access");
            disabled.setEnabled(false);
            disabled.getAccessibleContext().setAccessibleDescription(
                    "Authentication is disabled; this desktop has full owner access.");
            accountMenu.add(disabled);
        }
        menuBar.add(accountMenu);

        JMenu helpMenu = menu("Help", KeyEvent.VK_H,
                "Open growing guidance, contact support, or application information.");
        helpMenu.add(menuItem(GettingStartedGuide.MENU_LABEL, KeyEvent.VK_N,
                GettingStartedGuide.menuDescription(),
                e -> openGettingStarted()));
        if (enabled.contains(FarmWorkspace.ROSE_AND_EVENTS)
                || enabled.contains(FarmWorkspace.WILDFLOWER_FARM)) {
            helpMenu.add(menuItem("Start here…", KeyEvent.VK_S,
                    "Set up a cut-flower farm: log today’s cut, import a spreadsheet, or add a product.",
                    e -> openStartHere()));
            helpMenu.addSeparator();
        }
        if (enabled.contains(FarmWorkspace.ROSE_AND_EVENTS)
                || enabled.contains(FarmWorkspace.WILDFLOWER_FARM)) {
            JMenuItem growingGuide = menuItem("PNW Plant Guide", KeyEvent.VK_P,
                    "Open the sourced Pacific Northwest plant and crop reference.",
                    e -> selectTab("PNW Plant Guide"));
            growingGuide.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
            helpMenu.add(growingGuide);
        }
        if (enabled.contains(FarmWorkspace.ROSE_AND_EVENTS)
                || enabled.contains(FarmWorkspace.FLOWER_SHOP)) {
            helpMenu.add(menuItem("Wedding & Memorial Services", KeyEvent.VK_W,
                    "Open sourced floral, catering, décor, photography, and event-service starters.",
                    e -> selectTab("Event Services")));
        }
        if (enabled.contains(FarmWorkspace.ROSE_AND_EVENTS)) {
            helpMenu.add(menuItem("PNW Rose Growing Guide", KeyEvent.VK_G,
                    "Open the Pacific Northwest rose variety reference.",
                    e -> selectTab("Rose Varieties")));
        }
        helpMenu.add(menuItem("Check for updates…", KeyEvent.VK_U,
                "See whether a newer FlowersForever is ready, or turn automatic checks on or off.",
                e -> checkForUpdates()));
        helpMenu.add(menuItem("Contact / Report a problem…", KeyEvent.VK_C,
                SupportGuide.menuDescription(),
                e -> openSupport()));
        helpMenu.add(menuItem("Today’s reminders…", KeyEvent.VK_T,
                "See due field tasks, packing, harvest catch-up, low stock, and Knowledge follow-ups.",
                e -> openReminders()));
        helpMenu.add(menuItem("Cooler lot review…", KeyEvent.VK_O,
                CoolerShelfLife.menuDescription(),
                e -> openCoolerReview()));
        helpMenu.add(menuItem("Scouting & lab samples…", KeyEvent.VK_L,
                "Log pests and diseases by bed, track samples sent to labs, and see how to pack them.",
                e -> openScouting()));
        helpMenu.add(menuItem("About FlowersForever", KeyEvent.VK_A,
                "Show the farm profile, application purpose, and active session.",
                e -> dialogs.showMessage(frame,
                "FlowersForever\n"
                + farmProfile.getName() + " · " + farmProfile.locationLabel() + "\n\n"
                + "Plan harvests, manage inventory and customer orders,\n"
                + "prepare market days, and keep printable farm records.\n\n"
                + "Session: " + session.displayName() + "\n"
                + session.roleHint(),
                "About", JOptionPane.INFORMATION_MESSAGE)));
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void checkForUpdates() {
        if (updateController == null) {
            dialogs.showMessage(frame, UpdateMessages.NOT_CONFIGURED, "FlowersForever updates",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        updateController.checkNow();
    }

    private void showFarmTypeLauncher() {
        if (contentDeck == null) {
            return;
        }
        if (farmTypeLauncher != null) {
            contentDeck.remove(farmTypeLauncher);
        }
        boolean canChange = farmOperationSettingsService != null
                && !workspaceLoadFailed && session.role() == com.flowerfarm.auth.FarmRole.OWNER;
        Set<FarmWorkspace> included = currentOperationSettings == null
                ? Set.of(currentWorkspace)
                : currentOperationSettings.getEnabledWorkspaces();
        farmTypeLauncher = new FarmTypeLauncher(currentWorkspace, included, canChange,
                this::selectFarmType, this::showFarmSetup, this::showFarmCombination);
        contentDeck.add(farmTypeLauncher, LAUNCHER_CARD);
        ((CardLayout) contentDeck.getLayout()).show(contentDeck, LAUNCHER_CARD);
        contentDeck.revalidate();
        contentDeck.repaint();
        SwingUtilities.invokeLater(() -> {
            if (farmTypeLauncher != null && farmTypeLauncher.isShowing()) {
                farmTypeLauncher.currentEnterButton().requestFocusInWindow();
            }
        });
    }

    private void showWorkspace() {
        if (contentDeck == null) {
            return;
        }
        ((CardLayout) contentDeck.getLayout()).show(contentDeck, WORKSPACE_CARD);
        contentDeck.revalidate();
        contentDeck.repaint();
        SwingUtilities.invokeLater(this::maybeOfferStartHere);
    }

    /** Called by a launcher card; never discards a workflow draft or an in-flight save. */
    void selectFarmType(FarmWorkspace chosen) {
        Objects.requireNonNull(chosen, "chosen");
        if (chosen == currentWorkspace) {
            showWorkspace();
            setStatus("Opened " + FarmSetupDialog.workspaceLabel(chosen) + " workspace.");
            return;
        }
        if (currentOperationSettings != null
                && currentOperationSettings.getEnabledWorkspaces().size() > 1
                && currentOperationSettings.getEnabledWorkspaces().contains(chosen)) {
            showWorkspace();
            if (navigator != null) {
                navigator.select(startingTabFor(chosen));
            }
            setStatus("Opened " + FarmSetupDialog.workspaceLabel(chosen)
                    + " tools in the combined farm.");
            return;
        }
        if (currentOperationSettings != null
                && currentOperationSettings.getEnabledWorkspaces().size() > 1) {
            showFarmCombination(chosen);
            return;
        }
        if (session.role() != com.flowerfarm.auth.FarmRole.OWNER) {
            dialogs.showMessage(frame, "Only an owner can change the farm type.",
                    "Farm type unchanged", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (farmOperationSettingsService == null || workspaceLoadFailed) {
            dialogs.showMessage(frame, "The farm type cannot be changed until farm setup "
                    + "can read the local data store.", "Farm type unavailable",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        String unfinished = workspaceSwitchWarning();
        if (unfinished != null) {
            dialogs.showMessage(frame,
                    "Finish the current unfinished work before selecting a different farm type. "
                            + "Your current draft and farm type were kept.\n\n" + unfinished,
                    "Farm type unchanged", JOptionPane.INFORMATION_MESSAGE);
            setStatus("Farm type unchanged; finish the current draft or pending save first.");
            return;
        }
        FarmOperationSettings saved;
        try {
            FarmOperationSettings latest = farmOperationSettingsService.getSettings();
            FarmOperationSettings selected = FarmTypeLauncher.settingsFor(latest, chosen);
            saved = farmOperationSettingsService.saveSettings(selected);
        } catch (RuntimeException exception) {
            dialogs.showMessage(frame,
                    "Farm type was not changed. Check the local data store and try again.",
                    "Farm type unavailable", JOptionPane.ERROR_MESSAGE);
            setStatus("Farm type unchanged; save failed.");
            return;
        }
        applyWorkspaceChange(saved);
        showWorkspace();
        setStatus("Opened " + FarmSetupDialog.workspaceLabel(chosen)
                + " workspace. Farm type saved.");
    }

    private static String startingTabFor(FarmWorkspace workspace) {
        return switch (workspace) {
            case ROSE_AND_EVENTS -> "Dashboard";
            case WILDFLOWER_FARM -> "Market Day";
            case FLOWER_SHOP -> "Production Studio";
            case CANNABIS_FARM -> "Cultivation";
            case POTATO_FARM -> "Field plans";
            case APPLE_ORCHARD -> "Orchard blocks";
            case BERRY_FARM -> "Berry blocks";
            case NURSERY_FARM -> "Growing batches";
            case LIVESTOCK_FARM -> "Herd groups";
            case POULTRY_FARM -> "Egg collections";
            case DAIRY_FARM -> "Milk collections";
            case VINEYARD_FARM -> "Vineyard blocks";
            case HAY_FARM -> "Hay cuttings";
            case HOPS_FARM -> "Hop yards";
            case GRAIN_FARM -> "Grain fields";
            case SEED_FARM -> "Seed fields";
            case NUT_ORCHARD -> "Nut blocks";
            case VEGETABLE_FARM -> "Vegetable plantings";
            case AQUACULTURE_FARM -> "Aquatic cohorts";
            case CHRISTMAS_TREE_FARM -> "Tree blocks";
            case APIARY_FARM -> "Hive checks";
            case MUSHROOM_FARM -> "Mushroom batches";
            case CRANBERRY_FARM -> "Cranberry beds";
            case PUMPKIN_PATCH -> "Pumpkin patches";
            case CUSTOM_FARM -> "Workspace";
        };
    }

    /** Saves a chosen mix only after open workflows can safely be rebuilt. */
    void showFarmCombination() {
        showFarmCombination(null);
    }

    /** Opens the existing mix with one requested type already selected. */
    private void showFarmCombination(FarmWorkspace addCandidate) {
        if (session.role() != com.flowerfarm.auth.FarmRole.OWNER) {
            dialogs.showMessage(frame, "Only an owner can change the farm mix.",
                    "Farm mix unchanged", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (farmOperationSettingsService == null || workspaceLoadFailed) {
            dialogs.showMessage(frame, "The farm mix cannot be changed until farm setup "
                            + "can read the local data store.", "Farm mix unavailable",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        String unfinished = workspaceSwitchWarning();
        if (unfinished != null) {
            dialogs.showMessage(frame,
                    "Finish unfinished work before editing the farm mix. "
                            + "Your current draft and farm mix were kept.\n\n" + unfinished,
                    "Farm mix unchanged", JOptionPane.INFORMATION_MESSAGE);
            setStatus("Farm mix unchanged; finish the current draft or pending save first.");
            return;
        }
        FarmCombinationDialog picker = new FarmCombinationDialog(dialogs);
        LinkedHashSet<FarmWorkspace> included = new LinkedHashSet<>(
                currentOperationSettings == null
                        ? Set.of(currentWorkspace)
                        : currentOperationSettings.getEnabledWorkspaces());
        if (addCandidate != null) {
            included.add(addCandidate);
        }
        FarmCombinationDialog.Selection selection = picker
                .show(frame, currentWorkspace, included, session.role());
        while (selection != null) {
            if (selection.primary() == currentWorkspace && currentOperationSettings != null
                    && selection.included().equals(
                            currentOperationSettings.getEnabledWorkspaces())) {
                showWorkspace();
                return;
            }
            FarmOperationSettings saved;
            try {
                FarmOperationSettings latest = farmOperationSettingsService.getSettings();
                FarmOperationSettings selected = FarmTypeLauncher.settingsForCombination(
                        latest, selection.primary(), selection.included());
                saved = farmOperationSettingsService.saveSettings(selected);
            } catch (RuntimeException exception) {
                Object[] options = {"Retry save", "Edit mix", "Cancel"};
                int choice = dialogs.showOption(frame,
                        "Farm mix was not saved. Your selections are kept here. "
                                + "Check the local data store, then retry or edit the mix.",
                        "Farm mix not saved", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, options, options[0]);
                if (choice == 0) {
                    continue;
                }
                if (choice == 1) {
                    selection = picker.show(frame, selection.primary(),
                            selection.included(), session.role());
                    continue;
                }
                setStatus("Farm mix unchanged; save cancelled.");
                return;
            }
            applyWorkspaceChange(saved);
            showWorkspace();
            setStatus(saved.getEnabledWorkspaces().size() > 1
                    ? "Combined farm saved · " + saved.getEnabledWorkspaces().size()
                            + " farm types available."
                    : "Farm type saved · "
                            + FarmSetupDialog.workspaceLabel(saved.getWorkspace()) + ".");
            return;
        }
        if (addCandidate != null) {
            setStatus("Farm mix unchanged; add cancelled.");
        }
    }

    private void showFarmSetup() {
        if (farmOperationSettingsService == null) {
            dialogs.showMessage(frame, "Farm setup is unavailable in this session.",
                    "Farm setup unavailable", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (session.role() == com.flowerfarm.auth.FarmRole.OWNER) {
            String unfinished = workspaceSwitchWarning();
            if (unfinished != null) {
                dialogs.showMessage(frame,
                        "Finish unfinished work before changing farm setup. "
                                + "Your current draft and settings were kept.\n\n"
                                + unfinished,
                        "Farm setup unchanged", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }
        var saved = new FarmSetupDialog(farmOperationSettingsService, dialogs)
                .show(frame, farmProfile, session.role(), this::showFarmTypeLauncher);
        if (saved != null) {
            boolean navigationChanged = saved.getWorkspace() != currentWorkspace
                    || currentOperationSettings == null
                    || !saved.getEnabledWorkspaces().equals(
                            currentOperationSettings.getEnabledWorkspaces());
            if (!navigationChanged) {
                currentOperationSettings = saved;
            }
            if (!navigationChanged
                    && saved.getEnabledWorkspaces().contains(FarmWorkspace.CUSTOM_FARM)) {
                tabs.stream().filter(tab -> tab instanceof WorkspaceHomeTab)
                        .map(tab -> (WorkspaceHomeTab) tab)
                        .findFirst().ifPresent(home -> home.setCustomFocus(
                                saved.getEnabledWorkspaces().size() > 1
                                        ? FarmTabCatalog.customMixFocus(saved)
                                        : FarmTabCatalog.customFocus(saved)));
            }
            if (navigationChanged) {
                applyWorkspaceChange(saved);
            }
            showWorkspace();
            setStatus("Farm setup saved · " + FarmSetupDialog.stateLabel(saved.getState())
                    + " · " + FarmSetupDialog.workspaceLabel(saved.getWorkspace())
                    + " · " + FarmSetupDialog.enterpriseLabel(saved.getPrimaryEnterprise()));
        }
    }

    private String workspaceSwitchWarning() {
        return combinedExitWarning(
                crmTab == null ? null : crmTab.applicationExitWarning(),
                marketDayTab == null ? null : marketDayTab.applicationExitWarning(),
                harvestTab == null ? null : harvestTab.applicationExitWarning(),
                inventoryTab == null ? null : inventoryTab.applicationExitWarning(),
                addItemTab == null ? null : addItemTab.applicationExitWarning(),
                seasonPlannerTab == null ? null : seasonPlannerTab.applicationExitWarning(),
                productionStudioTab == null ? null : productionStudioTab.applicationExitWarning(),
                cultivationTab == null ? null : cultivationTab.applicationExitWarning(),
                cropAreaWarning(),
                berryBlockPlanTab == null ? null : berryBlockPlanTab.applicationExitWarning(),
                nurseryGrowingBatchTab == null ? null
                        : nurseryGrowingBatchTab.applicationExitWarning(),
                herdGroupTab == null ? null : herdGroupTab.applicationExitWarning(),
                eggCollectionTab == null ? null : eggCollectionTab.applicationExitWarning(),
                milkCollectionTab == null ? null : milkCollectionTab.applicationExitWarning(),
                vineyardBlockPlanTab == null ? null : vineyardBlockPlanTab.applicationExitWarning(),
                hayCuttingTab == null ? null : hayCuttingTab.applicationExitWarning(),
                hopYardPlanTab == null ? null : hopYardPlanTab.applicationExitWarning(),
                grainFieldPlanTab == null ? null : grainFieldPlanTab.applicationExitWarning(),
                seedFieldPlanTab == null ? null : seedFieldPlanTab.applicationExitWarning(),
                nutBlockPlanTab == null ? null : nutBlockPlanTab.applicationExitWarning(),
                vegetablePlantingTab == null ? null
                        : vegetablePlantingTab.applicationExitWarning(),
                aquaticCohortTab == null ? null
                        : aquaticCohortTab.applicationExitWarning(),
                christmasTreeBlockTab == null ? null
                        : christmasTreeBlockTab.applicationExitWarning(),
                hiveCheckTab == null ? null : hiveCheckTab.applicationExitWarning(),
                mushroomBatchTab == null ? null : mushroomBatchTab.applicationExitWarning(),
                cranberryBedTab == null ? null : cranberryBedTab.applicationExitWarning(),
                pumpkinPatchTab == null ? null : pumpkinPatchTab.applicationExitWarning(),
                knowledgeTab == null ? null : knowledgeTab.applicationExitWarning(),
                fieldScienceTab == null ? null : fieldScienceTab.applicationExitWarning(),
                eventServicesTab == null ? null : eventServicesTab.applicationExitWarning());
    }

    private String cropAreaWarning() {
        if (cropAreaTabs.isEmpty()) {
            return cropAreaTab == null ? null : cropAreaTab.applicationExitWarning();
        }
        return combinedExitWarning(cropAreaTabs.stream()
                .map(CropAreaPlanTab::applicationExitWarning).toArray(String[]::new));
    }

    private boolean cropAreaPendingSave() {
        if (cropAreaTabs.isEmpty()) {
            return cropAreaTab != null && cropAreaTab.hasPendingSave();
        }
        return cropAreaTabs.stream().anyMatch(CropAreaPlanTab::hasPendingSave);
    }

    private void applyWorkspaceChange(FarmOperationSettings saved) {
        currentOperationSettings = Objects.requireNonNull(saved, "saved");
        currentWorkspace = saved.getWorkspace();
        workspaceLoadFailed = false;
        buildPrimaryNavigation();
        bindCurrentWorkspaceActions();
        frame.remove(brandHeader);
        brandHeader = buildBrandHeader();
        frame.add(brandHeader, BorderLayout.NORTH);
        mutateButtons.removeIf(button -> button instanceof JMenuItem);
        frame.setJMenuBar(buildMenuBar());
        installShortcuts();
        applyRoleToUi();
        frame.setTitle(applicationTitle()
                + (loginGate.isEnabled() ? " · " + session.displayName() : ""));
        tabbedPane.setSelectedIndex(0);
        prefs.putInt(PREF_TAB, 0);
        prefs.put(PREF_TAB_TITLE, tabbedPane.getTitleAt(0));
        prefs.put(PREF_WORKSPACE, currentWorkspace.name());
        frame.revalidate();
        frame.repaint();
        if (dashboard != null && tabs.contains(dashboard)) {
            dashboard.applyTheme(darkMode);
        }
        if (trendTab != null && tabs.contains(trendTab)) {
            trendTab.applyTheme();
        }
        refreshAll();
    }

    private void showWhoAmI() {
        String accounts = loginGate.isEnabled() ? loginGate.accountHint() : "Local mode";
        dialogs.showMessage(frame,
                "Signed in as: " + session.displayName() + "\n"
                        + "Access: " + session.roleHint() + "\n"
                        + (canMutateData()
                                ? "You can add and change farm records.\n"
                                : "You can view, filter, print, and export records.\n")
                        + (canClearHistory()
                                ? "You can also clear activity history.\n\n"
                                : "\n")
                        + "Available accounts:\n" + accounts,
                "My access", JOptionPane.INFORMATION_MESSAGE);
    }

    private void switchUser() {
        if (!loginGate.promptSwitchUser()) {
            setStatus("Switch user cancelled.");
            return;
        }
        applyRoleToUi();
        refreshAll();
        setStatus("Signed in as " + session.displayName() + " · " + session.roleHint());
    }

    private void signOut() {
        closeApplication(true);
    }

    private void closeApplication(boolean signingOut) {
        if (!confirmApplicationClose(signingOut)) {
            return;
        }
        if (signingOut) {
            session.clear();
        }
        savePreferences();
        frame.dispose();
        System.exit(0);
    }

    /** Runs the installed close decision without disposing the frame or changing the session. */
    boolean confirmApplicationClose(boolean signingOut) {
        String warning = combinedExitWarning(
                crmTab == null ? null : crmTab.applicationExitWarning(),
                marketDayTab == null ? null : marketDayTab.applicationExitWarning(),
                harvestTab == null ? null : harvestTab.applicationExitWarning(),
                inventoryTab == null ? null : inventoryTab.applicationExitWarning(),
                addItemTab == null ? null : addItemTab.applicationExitWarning(),
                seasonPlannerTab == null ? null : seasonPlannerTab.applicationExitWarning(),
                productionStudioTab == null ? null : productionStudioTab.applicationExitWarning(),
                cultivationTab == null ? null : cultivationTab.applicationExitWarning(),
                cropAreaWarning(),
                berryBlockPlanTab == null ? null : berryBlockPlanTab.applicationExitWarning(),
                nurseryGrowingBatchTab == null ? null
                        : nurseryGrowingBatchTab.applicationExitWarning(),
                herdGroupTab == null ? null : herdGroupTab.applicationExitWarning(),
                eggCollectionTab == null ? null : eggCollectionTab.applicationExitWarning(),
                milkCollectionTab == null ? null : milkCollectionTab.applicationExitWarning(),
                vineyardBlockPlanTab == null ? null : vineyardBlockPlanTab.applicationExitWarning(),
                hayCuttingTab == null ? null : hayCuttingTab.applicationExitWarning(),
                hopYardPlanTab == null ? null : hopYardPlanTab.applicationExitWarning(),
                grainFieldPlanTab == null ? null : grainFieldPlanTab.applicationExitWarning(),
                seedFieldPlanTab == null ? null : seedFieldPlanTab.applicationExitWarning(),
                nutBlockPlanTab == null ? null : nutBlockPlanTab.applicationExitWarning(),
                vegetablePlantingTab == null ? null
                        : vegetablePlantingTab.applicationExitWarning(),
                aquaticCohortTab == null ? null
                        : aquaticCohortTab.applicationExitWarning(),
                christmasTreeBlockTab == null ? null
                        : christmasTreeBlockTab.applicationExitWarning(),
                hiveCheckTab == null ? null : hiveCheckTab.applicationExitWarning(),
                mushroomBatchTab == null ? null : mushroomBatchTab.applicationExitWarning(),
                cranberryBedTab == null ? null : cranberryBedTab.applicationExitWarning(),
                pumpkinPatchTab == null ? null : pumpkinPatchTab.applicationExitWarning(),
                knowledgeTab == null ? null : knowledgeTab.applicationExitWarning(),
                fieldScienceTab == null ? null : fieldScienceTab.applicationExitWarning(),
                eventServicesTab == null ? null : eventServicesTab.applicationExitWarning());
        if (warning != null) {
            boolean savePending = (productionStudioTab != null && productionStudioTab.hasPendingSave())
                    || (cultivationTab != null && cultivationTab.hasPendingSave())
                    || cropAreaPendingSave()
                    || (berryBlockPlanTab != null && berryBlockPlanTab.hasPendingSave())
                    || (nurseryGrowingBatchTab != null
                            && nurseryGrowingBatchTab.hasPendingSave())
                    || (herdGroupTab != null && herdGroupTab.hasPendingSave())
                    || (eggCollectionTab != null && eggCollectionTab.hasPendingSave())
                    || (milkCollectionTab != null && milkCollectionTab.hasPendingSave())
                    || (vineyardBlockPlanTab != null && vineyardBlockPlanTab.hasPendingSave())
                    || (hayCuttingTab != null && hayCuttingTab.hasPendingSave())
                    || (hopYardPlanTab != null && hopYardPlanTab.hasPendingSave())
                    || (grainFieldPlanTab != null && grainFieldPlanTab.hasPendingSave())
                    || (seedFieldPlanTab != null && seedFieldPlanTab.hasPendingSave())
                    || (nutBlockPlanTab != null && nutBlockPlanTab.hasPendingSave())
                    || (vegetablePlantingTab != null
                            && vegetablePlantingTab.hasPendingSave())
                    || (aquaticCohortTab != null
                            && aquaticCohortTab.hasPendingSave())
                    || (christmasTreeBlockTab != null
                            && christmasTreeBlockTab.hasPendingSave())
                    || (hiveCheckTab != null && hiveCheckTab.hasPendingSave())
                    || (mushroomBatchTab != null && mushroomBatchTab.hasPendingSave())
                    || (cranberryBedTab != null && cranberryBedTab.hasPendingSave())
                    || (pumpkinPatchTab != null && pumpkinPatchTab.hasPendingSave())
                    || (harvestTab != null && harvestTab.hasPendingSave())
                    || (inventoryTab != null && inventoryTab.hasPendingSave())
                    || (addItemTab != null && addItemTab.hasPendingSave())
                    || (seasonPlannerTab != null && seasonPlannerTab.hasPendingSave())
                    || (knowledgeTab != null && knowledgeTab.hasPendingSave())
                    || (fieldScienceTab != null && fieldScienceTab.hasPendingSave())
                    || (crmTab != null && crmTab.hasPendingSave())
                    || (marketDayTab != null && marketDayTab.hasPendingSave());
            Object[] options = {"Keep working", savePending ? "Exit before result"
                    : signingOut ? "Discard and sign out" : "Discard and exit"};
            String prompt = savePending ? pendingExitPrompt(warning) : exitPrompt(warning, signingOut);
            int choice = dialogs.showOption(
                    frame, prompt,
                    signingOut ? "Unfinished work — sign out" : "Unfinished work — exit",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
            if (choice != 1) {
                setStatus("Exit cancelled. Your unfinished work was kept.");
                return false;
            }
        } else if (signingOut) {
            int choice = dialogs.showConfirm(
                    frame, "Sign out and exit?", "Sign out",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        return true;
    }

    static String combinedExitWarning(String... warnings) {
        if (warnings == null) {
            return null;
        }
        String combined = java.util.Arrays.stream(warnings)
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return combined.isEmpty() ? null : combined;
    }

    static String exitPrompt(String warning, boolean signingOut) {
        String action = signingOut ? "signing out" : "exiting";
        return warning + "\n\nChoose Keep working to return without losing anything. "
                + "Choose Discard and " + (signingOut ? "sign out" : "exit")
                + " only if losing this unfinished work is intentional before " + action + ".";
    }

    static String pendingExitPrompt(String warning) {
        return warning + "\n\nExiting does not undo records already saved. "
                + "If you exit before the result, check the affected records or output files "
                + "when you reopen before trying again. Any unsaved drafts will be lost. "
                + "Choose Keep working to wait for the result.";
    }

    private static JMenu menu(String label, int mnemonic, String description) {
        JMenu menu = new JMenu(label);
        menu.setMnemonic(mnemonic);
        menu.setToolTipText(description);
        menu.getAccessibleContext().setAccessibleDescription(description);
        return menu;
    }

    private JMenuItem menuItem(String label, int mnemonic, String description,
                               java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(label);
        configureMenuItem(item, mnemonic, description);
        item.addActionListener(action);
        return item;
    }

    private JMenuItem writeMenuItem(String label, int mnemonic, String description,
                                    java.awt.event.ActionListener action) {
        JMenuItem item = menuItem(label, mnemonic, description, action);
        mutateButtons.add(item);
        return item;
    }

    private static <T extends JMenuItem> T configureMenuItem(
            T item, int mnemonic, String description) {
        item.setMnemonic(mnemonic);
        item.setToolTipText(description);
        item.getAccessibleContext().setAccessibleDescription(description);
        return item;
    }

    private void installShortcuts() {
        navigator.installShortcuts(frame.getRootPane(), () -> {
            refreshAll();
            setStatus("All tabs refreshed.");
        }, () -> farmTypeLauncher == null || !farmTypeLauncher.isVisible());
    }

    // ── Theming & preferences ──────────────────────────────────────────────────

    private void applyLookAndFeel(boolean dark) {
        try {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
            FarmUi.installThemeDefaults();
        } catch (Exception ex) {
            // Fall back silently to the platform default if FlatLaf is unavailable.
        }
    }

    private void toggleDarkMode(boolean dark) {
        darkMode = dark;
        applyLookAndFeel(dark);
        if (frame != null) {
            SwingUtilities.updateComponentTreeUI(frame);
            FarmUi.refreshTheme(frame);
            frame.remove(brandHeader);
            brandHeader = buildBrandHeader();
            frame.add(brandHeader, BorderLayout.NORTH);
            frame.revalidate();
            frame.repaint();
        }
        if (dashboard != null && tabs.contains(dashboard)) {
            dashboard.applyTheme(dark);   // re-assert custom card colors after the L&F reset
        }
        if (trendTab != null && tabs.contains(trendTab)) {
            trendTab.applyTheme();
        }
        prefs.putBoolean(PREF_DARK, dark);
        setStatus(dark ? "Dark mode on." : "Light mode on.");
    }

    private JPanel buildBrandHeader() {
        return FarmUi.brandHeader(
                farmProfile.getName(),
                farmProfile.getTagline(),
                farmProfile.locationLabel(),
                farmProfile.today().format(DateTimeFormatter.ofPattern(
                        "EEEE · MMMM d", Locale.US)),
                FarmSetupDialog.workspaceLabel(currentWorkspace));
    }

    private String applicationTitle() {
        String title = "FlowersForever — " + farmProfile.getName();
        return currentWorkspace == FarmWorkspace.ROSE_AND_EVENTS ? title
                : title + " · " + FarmSetupDialog.workspaceLabel(currentWorkspace);
    }

    private void savePreferences() {
        if (frame != null) {
            prefs.putInt(PREF_WIDTH, frame.getWidth());
            prefs.putInt(PREF_HEIGHT, frame.getHeight());
        }
        if (tabbedPane != null) {
            prefs.putInt(PREF_TAB, tabbedPane.getSelectedIndex());
            if (tabbedPane.getSelectedIndex() >= 0) {
                prefs.put(PREF_TAB_TITLE,
                        tabbedPane.getTitleAt(tabbedPane.getSelectedIndex()));
            }
        }
        prefs.putBoolean(PREF_DARK, darkMode);
        prefs.put(PREF_WORKSPACE, currentWorkspace.name());
    }

    // ── Connector bar ───────────────────────────────────────────────────────

    /**
     * Connector bar for implemented connectors. Farmbrite, Floranext, Shopify,
     * and Square support dual mode (local JSON mirror by default + optional REST).
     */
    JScrollPane buildConnectorBar() {
        mutateButtons.clear();
        connectorGrid = new JPanel(new GridLayout(0, 6, 8, 6));
        connectorGrid.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // Local files
        connectorGrid.add(connButton("Import CSV",     () -> runImportConnector("csv", "CSV"), true));
        connectorGrid.add(connButton("Export CSV",     this::exportAllToCsv, false));
        connectorGrid.add(connButton("Import Excel",   () -> runImportConnector("excel", "Excel"), true));
        connectorGrid.add(connButton("Export Excel",   () -> runExportConnector("excel", "Excel"), false));
        // Retail / POS
        connectorGrid.add(connButton("Import Shopify", () -> runImportConnector("shopify", "Shopify"), true));
        connectorGrid.add(connButton("Export Shopify", () -> runExportConnector("shopify", "Shopify"), true));
        connectorGrid.add(connButton("Sync Shopify",   () -> runSyncConnector("shopify", "Shopify"), true));
        connectorGrid.add(connButton("Import Square",  () -> runImportConnector("square", "Square"), true));
        connectorGrid.add(connButton("Export Square",  () -> runExportConnector("square", "Square"), true));
        connectorGrid.add(connButton("Sync Square",    () -> runSyncConnector("square", "Square"), true));
        // Sheets / web
        connectorGrid.add(connButton("Import Sheets",  () -> runImportConnector("google-sheets", "Google Sheets"), true));
        connectorGrid.add(connButton("Export Sheets",  () -> runExportConnector("google-sheets", "Google Sheets"), true));
        connectorGrid.add(connButton("Import Airtable",  () -> runImportConnector("airtable", "Airtable"), true));
        connectorGrid.add(connButton("Export Airtable",  () -> runExportConnector("airtable", "Airtable"), true));
        connectorGrid.add(connButton("Send Webhook",     () -> runExportConnector("webhook", "Webhook"), true));
        // Farm / florist tools
        connectorGrid.add(connButton("Import Farmbrite", () -> runImportConnector("farmbrite", "Farmbrite"), true));
        connectorGrid.add(connButton("Export Farmbrite", () -> runExportConnector("farmbrite", "Farmbrite"), true));
        connectorGrid.add(connButton("Sync Farmbrite",   () -> runSyncConnector("farmbrite", "Farmbrite"), true));
        connectorGrid.add(connButton("Import Floranext", () -> runImportConnector("floranext", "Floranext"), true));
        connectorGrid.add(connButton("Export Floranext", () -> runExportConnector("floranext", "Floranext"), true));
        connectorGrid.add(connButton("Sync Floranext",   () -> runSyncConnector("floranext", "Floranext"), true));

        JScrollPane scroll = new JScrollPane(connectorGrid,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("farm.connectorScroll");
        scroll.setPreferredSize(new Dimension(0, 110));
        scroll.setBorder(BorderFactory.createMatteBorder(
                1, 0, 0, 0, FarmUi.borderColor()));
        scroll.getAccessibleContext().setAccessibleName("Connected tools import and export actions");
        scroll.getAccessibleContext().setAccessibleDescription(
                "Use Tab to move through local files, commerce, sheets, and farm-system actions.");
        return scroll;
    }

    private JButton connButton(String label, Runnable action, boolean requiresWrite) {
        JButton button = FarmUi.secondaryButton(label);
        button.setFont(button.getFont().deriveFont(11f));
        String access = requiresWrite ? " Requires HAND or OWNER access."
                : " Available to every farm role.";
        String guidance = label + "." + access;
        button.setToolTipText(guidance);
        button.getAccessibleContext().setAccessibleDescription(guidance);
        button.addActionListener(e -> {
            if (requiresWrite && !canMutateData()) {
                denyWrite("imports and exports");
                return;
            }
            action.run();
        });
        if (requiresWrite) {
            mutateButtons.add(button);
        }
        return button;
    }

    private void denyWrite(String area) {
        dialogs.showMessage(frame,
                "Your role is VIEWER (read-only).\n"
                        + "Sign in as HAND or OWNER to change " + area + ".",
                "Permission denied", JOptionPane.WARNING_MESSAGE);
        setStatus("Blocked: VIEWER cannot modify " + area + ".");
    }

    // ── Local CSV export ──────────────────────────────────────────────────────

    private void exportAllToCsv() {
        // Export is read of local data — allowed for VIEWER (accounting / backup)
        var selected = fileChooser.chooseSave(frame, "Export Full Inventory to CSV",
                new File("exported_inventory.csv"));
        if (selected.isEmpty()) {
            setStatus("Export cancelled. No file was changed.");
            return;
        }
        File destination = selected.get();
        String path = destination.getAbsolutePath();
        if (destination.exists()) {
            int overwrite = dialogs.showConfirm(frame,
                    "Replace the existing file?\n" + path,
                    "Replace existing export",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                setStatus("Export cancelled. Existing file was not replaced.");
                return;
            }
        }
        try {
            connectorActions.exportInventory(path);
            setStatus("Inventory exported to " + path);
            dialogs.showMessage(frame, "Inventory exported to\n" + path,
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            dialogs.showMessage(frame, "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Connector operations (async via SwingWorker) ───────────────────────────

    private void runImportConnector(String connectorName, String displayName) {
        if (!canMutateData()) {
            denyWrite("inventory through " + displayName);
            return;
        }
        runConnectorAsync(displayName, "import",
                () -> connectorActions.importFrom(connectorName, displayName));
    }

    private void runExportConnector(String connectorName, String displayName) {
        runConnectorAsync(displayName, "export",
                () -> connectorActions.exportTo(connectorName, displayName));
    }

    private void runSyncConnector(String connectorName, String displayName) {
        if (!canMutateData()) {
            denyWrite(displayName + " synchronization");
            return;
        }
        runConnectorAsync(displayName, "sync",
                () -> connectorActions.syncWith(connectorName, displayName));
    }

    private void runConnectorAsync(String displayName, String operation,
                                   Callable<ConnectorActionPresenter.Feedback> action) {
        setStatus("⏳ Running " + displayName + " " + operation + " — please wait…");
        SwingWorker<ConnectorActionPresenter.Feedback, Void> worker = new SwingWorker<>() {
            @Override protected ConnectorActionPresenter.Feedback doInBackground() throws Exception {
                return action.call();
            }

            @Override protected void done() {
                try {
                    ConnectorActionPresenter.Feedback feedback = get();
                    if (feedback.refreshRequired()) {
                        refreshAll();
                    }
                    setStatus(feedback.status());
                    dialogs.showMessage(frame, feedback.message(), feedback.title(),
                            feedback.success()
                                    ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                } catch (Exception ex) {
                    setStatus(displayName + " " + operation + " error: " + ex.getMessage());
                    dialogs.showMessage(frame,
                            displayName + " " + operation + " error: " + ex.getMessage(),
                            displayName + " " + operation + " Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ── TabHost implementation ─────────────────────────────────────────────────

    @Override
    public void refreshAll() {
        if (navigator != null) {
            navigator.refreshAll();
        }
    }

    @Override
    public void selectTab(String tabTitle) {
        if (navigator != null) {
            String resolved = "CRM".equals(tabTitle) ? "Customers & Orders"
                    : "Sync History".equals(tabTitle) ? "Activity History" : tabTitle;
            if (navigator.select(resolved)) {
                showWorkspace();
            } else {
                setStatus(resolved + " is not shown in the "
                        + FarmSetupDialog.workspaceLabel(currentWorkspace) + " workspace.");
            }
        }
    }

    @Override
    public void openStartHere() {
        showStartHereDialog(false);
    }

    private void openSupport() {
        String version = AppVersion.current(null).toString();
        String diagnostics = SupportGuide.liveDiagnostics(version);
        String stored = prefs.get(PREF_SUPPORT_EMAIL, SupportGuide.DEFAULT_EMAIL);
        String email = SupportGuide.sanitizeStoredEmail(stored);
        if (!email.equals(SupportGuide.normalizeEmail(stored)) && !stored.isBlank()) {
            prefs.put(PREF_SUPPORT_EMAIL, "");
            setStatus(SupportGuide.statusClearedLegacy());
        }
        SupportPanel panel = new SupportPanel(email, version, diagnostics, clipboard, mailer, browser,
                this::setStatus);
        Object[] options = {"Close"};
        dialogs.showOption(frame, panel, SupportGuide.TITLE,
                javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);
        String chosen = SupportGuide.sanitizeStoredEmail(panel.email());
        String previous = email;
        prefs.put(PREF_SUPPORT_EMAIL, chosen);
        if (!chosen.equals(previous)) {
            setStatus(SupportGuide.statusSaved(chosen));
        }
    }

    /** Reminder targets are tab titles, except scouting reminders, which open their own window. */
    private void openReminderTarget(String target) {
        if (com.flowerfarm.scouting.Scouting.TITLE.equals(target)) {
            openScouting();
        } else {
            selectTab(target);
        }
    }

    /** Help → Getting started guide…: the bundled one-page guide in a read-only dialog. */
    void openGettingStarted() {
        JEditorPane page = new JEditorPane("text/html", GettingStartedGuide.html());
        page.setEditable(false);
        page.setCaretPosition(0);
        page.getAccessibleContext().setAccessibleName(GettingStartedGuide.TITLE);
        page.getAccessibleContext().setAccessibleDescription(GettingStartedGuide.menuDescription());
        JScrollPane scroll = new JScrollPane(page);
        scroll.setPreferredSize(new java.awt.Dimension(560, 520));
        Object[] options = {"Close"};
        dialogs.showOption(frame, scroll, GettingStartedGuide.TITLE,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
    }

    private void openScouting() {
        if (scoutingController == null) {
            dialogs.showMessage(frame,
                    "Scouting & lab samples is not available in this session.",
                    com.flowerfarm.scouting.Scouting.TITLE, javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        scoutingController.open();
    }

    private void openCoolerReview() {
        if (coolerReviewController == null) {
            dialogs.showMessage(frame,
                    "Cooler lot review is not available in this session.",
                    CoolerShelfLife.TITLE, javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        coolerReviewController.openReview();
    }

    private void openReminders() {
        if (remindersController == null) {
            dialogs.showMessage(frame,
                    "Reminders are not available in this session.",
                    RemindersPanel.TITLE, javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        remindersController.showToday();
    }

    private void openStemDemandBoard() {
        if (stemDemandController == null) {
            dialogs.showMessage(frame,
                    "The stem demand board is not available in this session.",
                    StemDemand.TITLE, javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        stemDemandController.openBoard();
    }

    private void openBuyingListByEvent() {
        if (stemDemandController == null) {
            dialogs.showMessage(frame,
                    "The buying list by event is not available in this session.",
                    StemDemand.EVENTS_TITLE, javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        stemDemandController.openBuyingList();
    }

    private void shareFloristAvailability() {
        if (floristExportController == null) {
            dialogs.showMessage(frame,
                    "Florist availability export is not available in this session.",
                    FloristAvailabilityExport.TITLE, javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        floristExportController.shareAvailability();
    }


    @Override
    public void importFromSpreadsheet() {
        Object[] options = {"CSV file…", "Excel workbook…", "Cancel"};
        int choice = dialogs.showOption(frame,
                "Bring products from a spreadsheet into Inventory.\n\n"
                        + "CSV works for simple lists. Excel works for .xlsx workbooks.\n"
                        + "You can also use File > Import CSV… or Import Excel… directly.",
                "Import from spreadsheet",
                javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
                javax.swing.JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice == 0) {
            runImportConnector("csv", "CSV");
        } else if (choice == 1) {
            runImportConnector("excel", "Excel");
        }
    }

    private void maybeOfferStartHere() {
        if (!hasCutFlowerHarvestWorkspace()
                || prefs.getBoolean(PREF_START_HERE_DONE, false)) {
            return;
        }
        if (!StartHereGuide.looksEmpty(inventoryService.getAllItems().size(),
                harvestService.getAll().size())) {
            return;
        }
        showStartHereDialog(true);
    }

    private boolean hasCutFlowerHarvestWorkspace() {
        Set<FarmWorkspace> enabled = currentOperationSettings == null
                ? Set.of(currentWorkspace) : currentOperationSettings.getEnabledWorkspaces();
        return enabled.contains(FarmWorkspace.ROSE_AND_EVENTS)
                || enabled.contains(FarmWorkspace.WILDFLOWER_FARM);
    }

    private void showStartHereDialog(boolean automatic) {
        Object[] options = StartHereGuide.dialogOptions();
        int choice = dialogs.showOption(frame, StartHereGuide.body(), StartHereGuide.TITLE,
                javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
        StartHereGuide.Choice selected = StartHereGuide.choiceFromDialogIndex(choice);
        if (automatic || selected == StartHereGuide.Choice.DISMISS) {
            prefs.putBoolean(PREF_START_HERE_DONE, true);
        }
        applyStartHereChoice(selected);
    }

    private void applyStartHereChoice(StartHereGuide.Choice choice) {
        switch (choice) {
            case LOG_HARVEST -> showHarvestEntry(null, null);
            case IMPORT_SPREADSHEET -> importFromSpreadsheet();
            case ADD_PRODUCT -> showAddInventoryItem("");
            case DISMISS -> { }
        }
        setStatus(StartHereGuide.statusFor(choice));
    }

    private void backUpNow() {
        if (backupController == null) {
            showBackupsUnavailable();
            return;
        }
        backupController.backUpNow();
    }

    private void restoreFromBackup() {
        if (backupController == null) {
            showBackupsUnavailable();
            return;
        }
        backupController.restoreFromBackup();
    }

    private void automaticBackups() {
        if (backupController == null) {
            showBackupsUnavailable();
            return;
        }
        backupController.automaticBackups();
    }

    private void showBackupsUnavailable() {
        dialogs.showMessage(frame, "Backups from the menu are not available in this copy of FlowersForever. "
                + "Use scripts/backup-data.ps1 instead (see docs/BACKUP_RESTORE.md).", "Backups",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void setStatus(String message) {
        if (statusLabel != null) {
            String safe = message == null || message.isBlank() ? "Ready" : message;
            statusLabel.setText(safe);
            statusLabel.setToolTipText(safe);
            statusLabel.getAccessibleContext().setAccessibleDescription(safe);
        }
    }

    @Override
    public void showInventoryProducts(List<String> productNames) {
        showInventoryProducts(productNames, null, null);
    }

    @Override
    public void showInventoryProducts(List<String> productNames,
                                      String returnTab, String returnLabel) {
        selectTab("Inventory");
        if (inventoryTab != null) {
            inventoryTab.showProductQueue(productNames, returnTab, returnLabel);
        }
        List<String> products = productNames == null ? List.of() : productNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        setStatus(products.isEmpty()
                ? "Opened Inventory to review stock."
                : "Market shortfall queue: " + String.join(", ", products) + ".");
    }

    @Override
    public void showAddInventoryItem(String productName) {
        selectTab("Add Item");
        if (addItemTab != null) {
            addItemTab.prepareMissingItem(productName);
        }
        String safeName = productName == null ? "" : productName.trim();
        setStatus(safeName.isEmpty()
                ? "Opened Add Item."
                : "Opened Add Item for missing product “" + safeName + "”.");
    }

    @Override
    public void showOrdersByStatus(String status) {
        showOrdersByStatus(status, null, null);
    }

    @Override
    public void showOrdersByStatus(String status, String returnTab, String returnLabel) {
        selectTab("Customers & Orders");
        if (crmTab != null) {
            crmTab.showOrdersByStatus(status, returnTab, returnLabel);
        } else {
            String safeStatus = status == null || status.isBlank() ? "all" : status.trim();
            setStatus("Opened Customers & Orders for " + safeStatus + " orders.");
        }
    }

    @Override
    public void showOrder(long orderId) {
        showOrder(orderId, null, null);
    }

    @Override
    public void showOrder(long orderId, String returnTab, String returnLabel) {
        selectTab("Customers & Orders");
        if (crmTab != null) {
            crmTab.showOrder(orderId, returnTab, returnLabel);
        } else {
            setStatus("Opened order #" + orderId
                    + " for packing and customer-detail review.");
        }
    }

    @Override
    public boolean showEventOrderDraft(String occasion, String services, String brief) {
        selectTab("Customers & Orders");
        String safeOccasion = occasion == null || occasion.isBlank()
                ? "event" : occasion.trim();
        String safeServices = services == null || services.isBlank()
                ? "selected services" : services.trim();
        if (crmTab != null && crmTab.prepareEventOrderBrief(brief)) {
            setStatus("Prepared a " + safeOccasion + " CRM brief for " + safeServices
                    + ". Add the customer, date, and physical flower products before saving.");
            return true;
        } else {
            setStatus("Opened Customers & Orders, but its existing new-order instructions were "
                    + "kept. Copy the " + safeOccasion + " brief when that draft is ready.");
            return false;
        }
    }

    @Override
    public boolean showSeasonPlanDraft(PnwPlantProfile plant) {
        if (plant == null) {
            return false;
        }
        selectTab("Season Planner");
        if (seasonPlannerTab == null) {
            setStatus("Opened Season Planner, but the plant reference draft is unavailable.");
            return false;
        }
        boolean accepted = seasonPlannerTab.preparePlantingFromReference(plant);
        if (!accepted) {
            setStatus("Opened Season Planner for “" + plant.commonName()
                    + "”; no planting block was saved.");
        }
        return accepted;
    }

    @Override
    public void showFieldScienceFinding(String instrumentTitle, String sourceUrl) {
        selectTab("Knowledge Log");
        if (knowledgeTab != null) {
            knowledgeTab.recordFieldScienceFinding(instrumentTitle, sourceUrl);
        } else {
            setStatus("Knowledge Log is unavailable for this farm workspace.");
        }
    }

    @Override
    public void showFieldScienceFindingFromExport(FieldScienceExportSummary export) {
        if (export == null || export.id() == null || export.id() <= 0) {
            setStatus("Select a saved field science export before recording a finding.");
            return;
        }
        if (!canMutateData()) {
            setStatus("This session cannot record a field science finding.");
            return;
        }
        if (knowledgeTab == null) {
            setStatus("Knowledge Log is unavailable for this farm workspace.");
            return;
        }
        selectTab("Knowledge Log");
        knowledgeTab.recordFieldScienceFindingFromExport(export);
    }

    @Override
    public void showFieldScienceExport(long exportId) {
        if (exportId <= 0 || fieldScienceTab == null) {
            setStatus("The linked field science export is unavailable in this workspace.");
            return;
        }
        selectTab("Field science");
        fieldScienceTab.showSavedExport(exportId);
    }

    @Override
    public void showOrderLine(long orderId, Long lineId, String productName,
                              String returnTab, String returnLabel) {
        selectTab("Customers & Orders");
        if (crmTab != null) {
            crmTab.showOrderLine(orderId, lineId, productName, returnTab, returnLabel);
        } else {
            String safeProduct = productName == null ? "" : productName.trim();
            setStatus("Opened order #" + orderId + (safeProduct.isEmpty()
                    ? " for saved-product review."
                    : " to review saved product “" + safeProduct + "”."));
        }
    }

    @Override
    public void showOrderLineCorrection(long orderId, Long lineId, String productName,
                                        String returnTab, String returnLabel) {
        selectTab("Customers & Orders");
        if (crmTab != null) {
            crmTab.showOrderLineCorrection(
                    orderId, lineId, productName, returnTab, returnLabel);
        } else {
            showOrderLine(orderId, lineId, productName, returnTab, returnLabel);
        }
    }

    @Override
    public void showHarvest(long harvestId) {
        showHarvest(harvestId, null, null);
    }

    @Override
    public void showHarvest(long harvestId, String returnTab, String returnLabel) {
        selectTab("Harvest Log");
        if (harvestTab != null) {
            harvestTab.showHarvest(harvestId, returnTab, returnLabel);
        }
        setStatus("Opening harvest #" + harvestId + " for Activity History review.");
    }

    @Override
    public void showMarketDay(String returnTab, String returnLabel) {
        selectTab("Market Day");
        if (marketDayTab != null) {
            marketDayTab.showFrom(returnTab, returnLabel);
        } else {
            setStatus("Opened Market Day packing work.");
        }
    }

    @Override
    public void showIrrigation(String returnTab, String returnLabel) {
        selectTab("Irrigation & Care");
        if (irrigationTab != null) {
            irrigationTab.showFrom(returnTab, returnLabel);
        } else {
            setStatus("Opened Irrigation & Care.");
        }
    }

    @Override
    public void showHarvestEntry(String returnTab, String returnLabel) {
        selectTab("Harvest Log");
        if (harvestTab != null) {
            harvestTab.showEntryFrom(returnTab, returnLabel);
        } else {
            setStatus("Opened Harvest Log for entry.");
        }
    }

    @Override
    public void runTrendAnalysis() {
        if (trendTab == null || !tabs.contains(trendTab)) {
            setStatus("Trend Analysis is not in this workspace.");
            return;
        }
        selectTab("Trend Analysis");
        trendTab.runAnalysis();
    }

    @Override
    public boolean canMutateData() {
        return session.canMutateData();
    }

    @Override
    public boolean canClearHistory() {
        return session.canClearHistory();
    }

    @Override
    public String sessionDisplayName() {
        return session.displayName();
    }
}
