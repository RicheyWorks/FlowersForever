package com.flowerfarm.gui;

import com.flowerfarm.auth.DesktopSession;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.PnwPlantProfile;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.gui.automation.SwingHarnessBridge;
import com.flowerfarm.gui.presenter.ConnectorActionPresenter;
import com.flowerfarm.gui.tabs.*;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import com.flowerfarm.service.SeasonPlanService;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;

/**
 * Next-level Flower Farm Manager GUI — Kitsap County / PNW West of the Cascades.
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
    private final FarmDialogs dialogs;
    private final FarmFileChooser fileChooser;
    private final SwingHarnessBridge harnessBridge;

    // Created lazily on the EDT inside initialise() — never touched before run().
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private FarmTabNavigator navigator;
    private JLabel statusLabel;
    private JLabel sessionLabel;
    private JPanel connectorGrid;
    private JPanel brandHeader;
    private final List<AbstractButton> mutateButtons = new ArrayList<>();

    private final List<FlowerFarmTab> tabs = new ArrayList<>();
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

    private DesktopUpdateController updateController;

    private final Preferences prefs = Preferences.userNodeForPackage(FlowerFarmGUI.class);
    private boolean darkMode;
    private JCheckBoxMenuItem darkModeItem;

    private static final String PREF_WIDTH  = "window.width";
    private static final String PREF_HEIGHT = "window.height";
    private static final String PREF_TAB    = "tab.lastIndex";
    private static final String PREF_TAB_TITLE = "tab.lastTitle";
    private static final String PREF_DARK   = "ui.dark";
    private static final String PREF_INTEGRATIONS = "ui.integrationsExpanded";

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
                         GuiLoginGate loginGate,
                         DesktopSession session,
                         FarmProfile farmProfile,
                         SwingHarnessBridge harnessBridge) {
        this(inventoryService, trendService, connectorRegistry, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService, loginGate, session, farmProfile,
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
        // No AWT calls here — Spring safe to construct this bean at any time.
        this.tabCatalog = new FarmTabCatalog(inventoryService, trendService, harvestService,
                syncHistoryService, customerService, orderService, reportService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, customerStatementService, knowledgeService,
                seasonPlanService, productionStudioService);
        this.connectorActions = new ConnectorActionPresenter(inventoryService, connectorRegistry);
        this.loginGate = loginGate;
        this.session = session;
        this.farmProfile = farmProfile;
        this.harnessBridge = Objects.requireNonNull(harnessBridge, "harnessBridge");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.fileChooser = Objects.requireNonNull(fileChooser, "fileChooser");
    }

    /** Automatic updates are optional so headless tests and slices can build the frame without them. */
    @Autowired(required = false)
    void setUpdateController(DesktopUpdateController updateController) {
        this.updateController = updateController;
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

        frame = new JFrame(applicationTitle());
        frame.setSize(prefs.getInt(PREF_WIDTH, 1500), prefs.getInt(PREF_HEIGHT, 900));
        frame.setMinimumSize(new Dimension(1100, 700));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        brandHeader = buildBrandHeader();
        frame.add(brandHeader, BorderLayout.NORTH);

        // Build tabs (constructor injection of services + this TabHost).
        FarmTabCatalog.Catalog catalog = tabCatalog.create(this);
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
        tabs.clear();
        tabs.addAll(catalog.tabs());

        tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setName("farm.primaryNavigation");
        tabbedPane.getAccessibleContext().setAccessibleName("Primary farm workflows");
        tabbedPane.getAccessibleContext().setAccessibleDescription(
                "Use Alt plus the shown mnemonic, or Alt+1 through Alt+9, to switch workflows.");
        navigator = new FarmTabNavigator(tabbedPane, tabs);
        navigator.populate();
        tabbedPane.addChangeListener(e -> navigator.refreshSelected(index -> {
            prefs.putInt(PREF_TAB, index);
            prefs.put(PREF_TAB_TITLE, tabbedPane.getTitleAt(index));
        }));
        frame.add(tabbedPane, BorderLayout.CENTER);

        frame.add(buildSouthBar(), BorderLayout.SOUTH);

        frame.setJMenuBar(buildMenuBar());
        installShortcuts();
        applyRoleToUi();

        frame.setLocationRelativeTo(null);

        // Restore the last-open tab and re-assert custom component paints.
        String lastTitle = prefs.get(PREF_TAB_TITLE, "");
        if (lastTitle.isBlank() || !navigator.select(lastTitle)) {
            int lastTab = prefs.getInt(PREF_TAB, 0);
            if (lastTab >= 0 && lastTab < tabs.size()) {
                tabbedPane.setSelectedIndex(lastTab);
            }
        }
        if (dashboard != null) {
            dashboard.applyTheme(darkMode);
        }
        if (trendTab != null) {
            trendTab.applyTheme();
        }

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
        harnessBridge.attach(frame.getRootPane(), navigator::select);
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
    }

    // ── Menu bar ──────────────────────────────────────────────────────────────

    JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = menu("File", KeyEvent.VK_F,
                "Import farm data, export a local backup, or exit FlowersForever.");
        fileMenu.add(writeMenuItem("Import CSV…", KeyEvent.VK_C,
                "Import inventory records from a CSV file.",
                e -> runImportConnector("csv", "CSV")));
        fileMenu.add(writeMenuItem("Import Excel…", KeyEvent.VK_E,
                "Import inventory records from an Excel workbook.",
                e -> runImportConnector("excel", "Excel")));
        fileMenu.add(menuItem("Export All to CSV…", KeyEvent.VK_A,
                "Save all current inventory records to a CSV file.",
                e -> exportAllToCsv()));
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

        JMenu toolsMenu = menu("Tools", KeyEvent.VK_T,
                "Open farm analysis and planning tools.");
        toolsMenu.add(menuItem("Run Harvest Forecast", KeyEvent.VK_H,
                "Open Trend Analysis and calculate the latest harvest forecast.",
                e -> runTrendAnalysis()));
        menuBar.add(toolsMenu);

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
                "Open growing guidance or application information.");
        JMenuItem growingGuide = menuItem("PNW Plant Guide", KeyEvent.VK_P,
                "Open the sourced Pacific Northwest plant and crop reference.",
                e -> selectTab("PNW Plant Guide"));
        growingGuide.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        helpMenu.add(growingGuide);
        helpMenu.add(menuItem("Wedding & Memorial Services", KeyEvent.VK_W,
                "Open sourced floral, catering, décor, photography, and event-service starters.",
                e -> selectTab("Event Services")));
        helpMenu.add(menuItem("PNW Rose Growing Guide", KeyEvent.VK_G,
                "Open the Pacific Northwest rose variety reference.",
                e -> selectTab("Rose Varieties")));
        helpMenu.add(menuItem("Check for updates…", KeyEvent.VK_U,
                "See whether a newer FlowersForever is ready, or turn automatic checks on or off.",
                e -> checkForUpdates()));
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
                seasonPlannerTab == null ? null : seasonPlannerTab.applicationExitWarning(),
                productionStudioTab == null ? null : productionStudioTab.applicationExitWarning());
        if (warning != null) {
            boolean savePending = (productionStudioTab != null && productionStudioTab.hasPendingSave())
                    || (harvestTab != null && harvestTab.hasPendingSave())
                    || (inventoryTab != null && inventoryTab.hasPendingSave())
                    || (seasonPlannerTab != null && seasonPlannerTab.hasPendingSave())
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
        });
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
        if (dashboard != null) {
            dashboard.applyTheme(dark);   // re-assert custom card colors after the L&F reset
        }
        if (trendTab != null) {
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
                LocalDate.now(farmProfile.zoneId()).format(DateTimeFormatter.ofPattern(
                        "EEEE · MMMM d", Locale.US)));
    }

    private String applicationTitle() {
        return "FlowersForever — " + farmProfile.getName();
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
            navigator.select(resolved);
        }
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
        selectTab("Trend Analysis");
        if (trendTab != null) {
            trendTab.runAnalysis();
        }
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
