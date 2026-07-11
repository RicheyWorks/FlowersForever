package com.flowerfarm.gui;

import com.flowerfarm.auth.FarmSession;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.gui.tabs.*;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
@Profile("!cli")
public class FlowerFarmGUI implements ApplicationRunner, TabHost {

    private final InventoryService inventoryService;
    private final TrendService trendService;
    private final ConnectorRegistry connectorRegistry;
    private final HarvestService harvestService;
    private final SyncHistoryService syncHistoryService;
    private final CustomerService customerService;
    private final OrderService orderService;
    private final ReportService reportService;
    private final IrrigationAdvisorService irrigationAdvisorService;
    private final MarketDayPackingService marketDayPackingService;
    private final MorningBriefingService morningBriefingService;
    private final DayCloseoutService dayCloseoutService;
    private final GuiLoginGate loginGate;

    // Created lazily on the EDT inside initialise() — never touched before run().
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private JLabel statusLabel;
    private JLabel sessionLabel;
    private JPanel connectorGrid;
    private final List<JButton> mutateButtons = new ArrayList<>();

    private final List<FlowerFarmTab> tabs = new ArrayList<>();
    private DashboardTab dashboard;
    private TrendAnalysisTab trendTab;

    private final Preferences prefs = Preferences.userNodeForPackage(FlowerFarmGUI.class);
    private boolean darkMode;
    private JCheckBoxMenuItem darkModeItem;

    private static final String PREF_WIDTH  = "window.width";
    private static final String PREF_HEIGHT = "window.height";
    private static final String PREF_TAB    = "tab.lastIndex";
    private static final String PREF_DARK   = "ui.dark";

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
                         GuiLoginGate loginGate) {
        // No AWT calls here — Spring safe to construct this bean at any time.
        this.inventoryService = inventoryService;
        this.trendService = trendService;
        this.connectorRegistry = connectorRegistry;
        this.harvestService = harvestService;
        this.syncHistoryService = syncHistoryService;
        this.customerService = customerService;
        this.orderService = orderService;
        this.reportService = reportService;
        this.irrigationAdvisorService = irrigationAdvisorService;
        this.marketDayPackingService = marketDayPackingService;
        this.morningBriefingService = morningBriefingService;
        this.dayCloseoutService = dayCloseoutService;
        this.loginGate = loginGate;
    }

    // ── ApplicationRunner ─────────────────────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) {
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

        frame = new JFrame("🌸 Flower Farm Manager — Port Orchard, Kitsap County WA | PNW West of the Cascades");
        frame.setSize(prefs.getInt(PREF_WIDTH, 1500), prefs.getInt(PREF_HEIGHT, 900));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Build tabs (constructor injection of services + this TabHost).
        dashboard = new DashboardTab(inventoryService, harvestService, orderService,
                irrigationAdvisorService, marketDayPackingService, morningBriefingService,
                dayCloseoutService, this);
        trendTab = new TrendAnalysisTab(trendService);
        tabs.add(dashboard);
        tabs.add(new InventoryTab(inventoryService, this));
        tabs.add(new AddItemTab(inventoryService, this));
        tabs.add(new HarvestLogTab(harvestService, this));
        tabs.add(new CrmTab(customerService, orderService, this));
        tabs.add(new MarketDayTab(marketDayPackingService, this));
        tabs.add(trendTab);
        tabs.add(new SyncHistoryTab(syncHistoryService, this));
        tabs.add(new ReportsTab(reportService, this));
        tabs.add(new RoseVarietiesTab(inventoryService, this));
        tabs.add(new RoseVisualizerTab(inventoryService, this));
        tabs.add(new PricingInfoTab());
        tabs.add(new IrrigationInfoTab(irrigationAdvisorService, this));

        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        for (FlowerFarmTab tab : tabs) {
            tabbedPane.addTab(tab.getTabTitle(), tab.getIcon(), tab.getUIComponent(), tab.getDescription());
            tab.initialize();
        }
        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx >= 0 && idx < tabs.size()) {
                tabs.get(idx).refreshData();
                prefs.putInt(PREF_TAB, idx);
            }
        });
        frame.add(tabbedPane, BorderLayout.CENTER);

        // South region: connector button bar (top) + status bar (bottom).
        JPanel south = new JPanel(new BorderLayout());
        south.add(buildConnectorBar(), BorderLayout.CENTER);
        JPanel statusRow = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready • Kitsap County Flower Farm • Spring Boot + Swing");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        sessionLabel = new JLabel(sessionBadgeText());
        sessionLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        sessionLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        sessionLabel.setFont(sessionLabel.getFont().deriveFont(Font.BOLD));
        statusRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 210)));
        statusRow.add(statusLabel, BorderLayout.CENTER);
        statusRow.add(sessionLabel, BorderLayout.EAST);
        south.add(statusRow, BorderLayout.SOUTH);
        frame.add(south, BorderLayout.SOUTH);

        frame.setJMenuBar(buildMenuBar());
        installShortcuts();
        applyRoleToUi();

        frame.setLocationRelativeTo(null);

        // Restore the last-open tab and re-assert the saved theme on the dashboard.
        int lastTab = prefs.getInt(PREF_TAB, 0);
        if (lastTab >= 0 && lastTab < tabs.size()) {
            tabbedPane.setSelectedIndex(lastTab);
        }
        if (dashboard != null) {
            dashboard.applyTheme(darkMode);
        }

        // Persist window size + last tab when the window closes.
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                savePreferences();
            }
        });

        frame.setVisible(true);
        refreshAll();
        if (loginGate.isEnabled()) {
            setStatus("Signed in as " + FarmSession.displayName() + " · " + FarmSession.roleHint());
        } else {
            setStatus("Ready · local mode (auth off)");
        }
    }

    private String sessionBadgeText() {
        if (!loginGate.isEnabled()) {
            return "🔓 Auth off";
        }
        return "👤 " + FarmSession.displayName();
    }

    private void applyRoleToUi() {
        boolean canWrite = canMutateData();
        for (JButton b : mutateButtons) {
            GuiPermissions.setWritable(canWrite, b);
        }
        for (FlowerFarmTab tab : tabs) {
            tab.applyRolePermissions(canWrite);
        }
        if (sessionLabel != null) {
            sessionLabel.setText(sessionBadgeText());
            sessionLabel.setToolTipText(FarmSession.roleHint());
            if (!canWrite && loginGate.isEnabled()) {
                sessionLabel.setForeground(new Color(140, 90, 20));
            } else {
                sessionLabel.setForeground(UIManager.getColor("Label.foreground"));
            }
        }
        if (frame != null && loginGate.isEnabled()) {
            frame.setTitle("🌸 Flower Farm Manager — " + FarmSession.displayName()
                    + " | Port Orchard, Kitsap County WA");
        }
    }

    // ── Menu bar ──────────────────────────────────────────────────────────────

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.add(menuItem("Import CSV…", e -> runImportConnector("csv", "CSV")));
        fileMenu.add(menuItem("Import Excel…", e -> runImportConnector("excel", "Excel")));
        fileMenu.add(menuItem("Export All to CSV…", e -> exportAllToCsv()));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Exit", e -> {
            savePreferences();
            frame.dispose();
            System.exit(0);
        }));
        menuBar.add(fileMenu);

        JMenu viewMenu = new JMenu("View");
        JMenuItem refresh = menuItem("Refresh All Tabs", e -> {
            refreshAll();
            setStatus("All tabs refreshed.");
        });
        refresh.setAccelerator(KeyStroke.getKeyStroke("F5"));
        viewMenu.add(refresh);
        viewMenu.addSeparator();
        darkModeItem = new JCheckBoxMenuItem("Dark Mode", darkMode);
        darkModeItem.addActionListener(e -> toggleDarkMode(darkModeItem.isSelected()));
        viewMenu.add(darkModeItem);
        menuBar.add(viewMenu);

        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.add(menuItem("Run Trend Analysis", e -> runTrendAnalysis()));
        menuBar.add(toolsMenu);

        JMenu accountMenu = new JMenu("Account");
        accountMenu.add(menuItem("Who am I?", e -> showWhoAmI()));
        if (loginGate.isEnabled()) {
            accountMenu.add(menuItem("Switch user…", e -> switchUser()));
            accountMenu.add(menuItem("Sign out", e -> signOut()));
        } else {
            JMenuItem disabled = new JMenuItem("Auth disabled (start with --spring.profiles.active=auth)");
            disabled.setEnabled(false);
            accountMenu.add(disabled);
        }
        menuBar.add(accountMenu);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(menuItem("PNW Rose Growing Guide", e -> selectTab("Rose Varieties")));
        helpMenu.add(menuItem("About Flower Farm Manager", e -> JOptionPane.showMessageDialog(frame,
                "Flower Farm Manager\n"
                + "PNW West of the Cascades — Port Orchard, Kitsap County, WA\n\n"
                + "A Spring Boot + Swing inventory tool with external connector\n"
                + "sync and Weka-based quantity trend forecasting.\n\n"
                + "Session: " + FarmSession.displayName() + "\n"
                + FarmSession.roleHint(),
                "About", JOptionPane.INFORMATION_MESSAGE)));
        menuBar.add(helpMenu);

        return menuBar;
    }

    private void showWhoAmI() {
        String accounts = loginGate.isEnabled() ? loginGate.accountHint() : "(auth profile not active)";
        JOptionPane.showMessageDialog(frame,
                "Signed in as: " + FarmSession.displayName() + "\n"
                        + "Permissions: " + FarmSession.roleHint() + "\n"
                        + "Can write: " + canMutateData() + "\n"
                        + "Can clear audit: " + canClearHistory() + "\n\n"
                        + "Configured accounts:\n" + accounts,
                "Who am I?", JOptionPane.INFORMATION_MESSAGE);
    }

    private void switchUser() {
        if (!loginGate.promptSwitchUser()) {
            setStatus("Switch user cancelled.");
            return;
        }
        applyRoleToUi();
        refreshAll();
        setStatus("Signed in as " + FarmSession.displayName() + " · " + FarmSession.roleHint());
    }

    private void signOut() {
        int ok = JOptionPane.showConfirmDialog(frame,
                "Sign out and exit?", "Sign out", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }
        FarmSession.clear();
        savePreferences();
        frame.dispose();
        System.exit(0);
    }

    private JMenuItem menuItem(String label, java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(action);
        return item;
    }

    private void installShortcuts() {
        JRootPane root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F5"), "refreshAll");
        root.getActionMap().put("refreshAll", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                refreshAll();
                setStatus("All tabs refreshed.");
            }
        });
    }

    // ── Theming & preferences ──────────────────────────────────────────────────

    private void applyLookAndFeel(boolean dark) {
        try {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
        } catch (Exception ex) {
            // Fall back silently to the platform default if FlatLaf is unavailable.
        }
    }

    private void toggleDarkMode(boolean dark) {
        darkMode = dark;
        applyLookAndFeel(dark);
        if (frame != null) {
            SwingUtilities.updateComponentTreeUI(frame);
        }
        if (dashboard != null) {
            dashboard.applyTheme(dark);   // re-assert custom card colors after the L&F reset
        }
        prefs.putBoolean(PREF_DARK, dark);
        setStatus(dark ? "Dark mode on." : "Light mode on.");
    }

    private void savePreferences() {
        if (frame != null) {
            prefs.putInt(PREF_WIDTH, frame.getWidth());
            prefs.putInt(PREF_HEIGHT, frame.getHeight());
        }
        if (tabbedPane != null) {
            prefs.putInt(PREF_TAB, tabbedPane.getSelectedIndex());
        }
        prefs.putBoolean(PREF_DARK, darkMode);
    }

    // ── Connector bar ───────────────────────────────────────────────────────

    /**
     * Connector bar for implemented connectors. Farmbrite, Floranext, Shopify,
     * and Square support dual mode (local JSON mirror by default + optional REST).
     */
    private JScrollPane buildConnectorBar() {
        mutateButtons.clear();
        connectorGrid = new JPanel(new GridLayout(0, 6, 8, 6));
        connectorGrid.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // Local files
        connectorGrid.add(connButton("Import CSV",     () -> runImportConnector("csv", "CSV"), true));
        connectorGrid.add(connButton("Export CSV",     this::exportAllToCsv, true));
        connectorGrid.add(connButton("Import Excel",   () -> runImportConnector("excel", "Excel"), true));
        connectorGrid.add(connButton("Export Excel",   () -> runExportConnector("excel", "Excel"), true));
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
        scroll.setPreferredSize(new Dimension(0, 110));
        scroll.setBorder(BorderFactory.createTitledBorder(
                "Connectors — dual-mode local mirrors (Farmbrite/Floranext/Shopify/Square/Sheets/Airtable/Webhook)"));
        return scroll;
    }

    private JButton connButton(String label, Runnable action, boolean requiresWrite) {
        JButton button = new JButton(label);
        button.setFont(button.getFont().deriveFont(11f));
        button.addActionListener(e -> {
            if (requiresWrite && !canMutateData()) {
                denyWrite("connectors");
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
        JOptionPane.showMessageDialog(frame,
                "Your role is VIEWER (read-only).\n"
                        + "Sign in as HAND or OWNER to change " + area + ".",
                "Permission denied", JOptionPane.WARNING_MESSAGE);
        setStatus("Blocked: VIEWER cannot modify " + area + ".");
    }

    // ── Local CSV export ──────────────────────────────────────────────────────

    private void exportAllToCsv() {
        // Export is read of local data — allowed for VIEWER (accounting / backup)
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Full Inventory to CSV");
        chooser.setSelectedFile(new File("exported_inventory.csv"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String path = chooser.getSelectedFile().getAbsolutePath();
        try {
            inventoryService.exportToCsv(path);
            setStatus("Inventory exported to " + path);
            JOptionPane.showMessageDialog(frame, "Inventory exported to\n" + path,
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Connector operations (async via SwingWorker) ───────────────────────────

    private void runImportConnector(String connectorName, String displayName) {
        setStatus("⏳ Running " + displayName + " import — please wait…");
        SwingWorker<ConnectorResult<List<Item>>, Void> worker = new SwingWorker<>() {
            @Override protected ConnectorResult<List<Item>> doInBackground() {
                return connectorRegistry.runImport(connectorName);
            }
            @Override protected void done() {
                try {
                    ConnectorResult<List<Item>> result = get();
                    if (result.isSuccess()) {
                        refreshAll();
                        int imported = result.getPayload() == null ? 0 : result.getPayload().size();
                        setStatus(displayName + " import complete — imported " + imported + " item(s).");
                        JOptionPane.showMessageDialog(frame,
                                displayName + " import complete.\nImported: " + imported,
                                displayName + " Import Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        setStatus(displayName + " import failed: " + result.getMessage());
                        JOptionPane.showMessageDialog(frame,
                                result.getMessage() + "\n" + result.getErrorDetail(),
                                displayName + " Import Failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    setStatus(displayName + " import error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(frame,
                            displayName + " import error: " + ex.getMessage(),
                            displayName + " Import Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void runExportConnector(String connectorName, String displayName) {
        setStatus("⏳ Running " + displayName + " export — please wait…");
        SwingWorker<ConnectorResult<Integer>, Void> worker = new SwingWorker<>() {
            @Override protected ConnectorResult<Integer> doInBackground() {
                return connectorRegistry.runExport(connectorName);
            }
            @Override protected void done() {
                try {
                    ConnectorResult<Integer> result = get();
                    if (result.isSuccess()) {
                        int exported = result.getPayload() == null ? 0 : result.getPayload();
                        setStatus(displayName + " export complete — exported " + exported + " item(s).");
                        JOptionPane.showMessageDialog(frame,
                                displayName + " export complete.\nExported: " + exported,
                                displayName + " Export Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        setStatus(displayName + " export failed: " + result.getMessage());
                        JOptionPane.showMessageDialog(frame,
                                result.getMessage() + "\n" + result.getErrorDetail(),
                                displayName + " Export Failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    setStatus(displayName + " export error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(frame,
                            displayName + " export error: " + ex.getMessage(),
                            displayName + " Export Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void runSyncConnector(String connectorName, String displayName) {
        setStatus("⏳ Running " + displayName + " sync — please wait…");
        SwingWorker<ConnectorResult<com.flowerfarm.connector.SyncSummary>, Void> worker = new SwingWorker<>() {
            @Override protected ConnectorResult<com.flowerfarm.connector.SyncSummary> doInBackground() {
                return connectorRegistry.runSync(connectorName);
            }
            @Override protected void done() {
                try {
                    ConnectorResult<com.flowerfarm.connector.SyncSummary> result = get();
                    if (result.isSuccess()) {
                        refreshAll();
                        com.flowerfarm.connector.SyncSummary s = result.getPayload();
                        String detail = s == null ? result.getMessage() : s.toString();
                        setStatus(displayName + " sync complete — " + detail);
                        JOptionPane.showMessageDialog(frame,
                                displayName + " sync complete.\n" + detail,
                                displayName + " Sync Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        setStatus(displayName + " sync failed: " + result.getMessage());
                        JOptionPane.showMessageDialog(frame,
                                result.getMessage() + "\n" + result.getErrorDetail(),
                                displayName + " Sync Failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    setStatus(displayName + " sync error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(frame,
                            displayName + " sync error: " + ex.getMessage(),
                            displayName + " Sync Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ── TabHost implementation ─────────────────────────────────────────────────

    @Override
    public void refreshAll() {
        for (FlowerFarmTab tab : tabs) {
            tab.refreshData();
        }
    }

    @Override
    public void selectTab(String tabTitle) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getTabTitle().equals(tabTitle)) {
                tabbedPane.setSelectedIndex(i);
                return;
            }
        }
    }

    @Override
    public void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
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
        return FarmSession.canMutateData();
    }

    @Override
    public boolean canClearHistory() {
        return FarmSession.canClearHistory();
    }
}
