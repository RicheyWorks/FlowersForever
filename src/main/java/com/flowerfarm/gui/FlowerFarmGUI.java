package com.flowerfarm.gui;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.gui.tabs.*;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
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

    // Created lazily on the EDT inside initialise() — never touched before run().
    private JFrame frame;
    private JTabbedPane tabbedPane;
    private JLabel statusLabel;

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
                         ConnectorRegistry connectorRegistry) {
        // No AWT calls here — Spring safe to construct this bean at any time.
        this.inventoryService = inventoryService;
        this.trendService = trendService;
        this.connectorRegistry = connectorRegistry;
    }

    // ── ApplicationRunner ─────────────────────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) {
        SwingUtilities.invokeLater(this::initialise);
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
        dashboard = new DashboardTab(inventoryService, this);
        trendTab = new TrendAnalysisTab(trendService);
        tabs.add(dashboard);
        tabs.add(new InventoryTab(inventoryService, this));
        tabs.add(new AddItemTab(inventoryService, this));
        tabs.add(trendTab);
        tabs.add(new RoseVarietiesTab(inventoryService, this));
        tabs.add(new PricingInfoTab());
        tabs.add(new IrrigationInfoTab());

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
        statusLabel = new JLabel("Ready • Kitsap County Flower Farm • Spring Boot + Swing");
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        south.add(statusLabel, BorderLayout.SOUTH);
        frame.add(south, BorderLayout.SOUTH);

        frame.setJMenuBar(buildMenuBar());
        installShortcuts();

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

        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(menuItem("PNW Rose Growing Guide", e -> selectTab("Rose Varieties")));
        helpMenu.add(menuItem("About Flower Farm Manager", e -> JOptionPane.showMessageDialog(frame,
                "Flower Farm Manager\n"
                + "PNW West of the Cascades — Port Orchard, Kitsap County, WA\n\n"
                + "A Spring Boot + Swing inventory tool with external connector\n"
                + "sync and Weka-based quantity trend forecasting.",
                "About", JOptionPane.INFORMATION_MESSAGE)));
        menuBar.add(helpMenu);

        return menuBar;
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

    private JScrollPane buildConnectorBar() {
        JPanel grid = new JPanel(new GridLayout(0, 7, 8, 6));
        grid.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        grid.add(connButton("Import CSV",         () -> runImportConnector("csv", "CSV")));
        grid.add(connButton("Import Excel",       () -> runImportConnector("excel", "Excel")));
        grid.add(connButton("Export CSV",         this::exportAllToCsv));
        grid.add(connButton("Export Excel",       () -> runExportConnector("excel", "Excel")));
        grid.add(connButton("Send Webhook",       () -> runExportConnector("webhook", "Webhook")));
        grid.add(connButton("Import Airtable",    () -> runImportConnector("airtable", "Airtable")));
        grid.add(connButton("Export Airtable",    () -> runExportConnector("airtable", "Airtable")));
        grid.add(connButton("Export Farmbrite",   () -> runExportConnector("farmbrite", "Farmbrite")));
        grid.add(connButton("Export Squarespace", () -> runExportConnector("squarespace", "Squarespace")));
        grid.add(connButton("Export VeggieCropper", () -> runExportConnector("veggiecropper", "VeggieCropper")));
        grid.add(connButton("Export Floranext",   () -> runExportConnector("floranext", "Floranext")));
        grid.add(connButton("Export FloristWare", () -> runExportConnector("floristware", "FloristWare")));
        grid.add(connButton("Export IRIS",        () -> runExportConnector("iris", "IRIS")));
        grid.add(connButton("Export GiftLogic",   () -> runExportConnector("giftlogic", "GiftLogic")));

        JScrollPane scroll = new JScrollPane(grid,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(0, 95));
        scroll.setBorder(BorderFactory.createTitledBorder("Connectors — Import / Export / Sync"));
        return scroll;
    }

    private JButton connButton(String label, Runnable action) {
        JButton button = new JButton(label);
        button.setFont(button.getFont().deriveFont(11f));
        button.addActionListener(e -> action.run());
        return button;
    }

    // ── Local CSV export ──────────────────────────────────────────────────────

    private void exportAllToCsv() {
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
        setStatus("Running " + displayName + " import…");
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
        setStatus("Running " + displayName + " export…");
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
}
