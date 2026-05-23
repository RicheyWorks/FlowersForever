package com.flowerfarm.gui;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.TrendService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Main Swing GUI — active in every profile except "cli".
 *
 * <p>Uses <em>composition</em> rather than extending {@link JFrame} so that
 * Spring can instantiate this bean without touching AWT during context
 * initialisation (which would throw {@link java.awt.HeadlessException} in
 * headless/CI environments). All Swing work is deferred to the first call of
 * {@link #run} and then dispatched onto the Event Dispatch Thread.
 */
@Component
@Profile("!cli")
public class FlowerFarmGUI implements ApplicationRunner {

    private final InventoryService  inventoryService;
    private final TrendService      trendService;
    private final ConnectorRegistry connectorRegistry;

    // Created lazily on the EDT inside initialise() — never touched before run()
    private JFrame frame;

    private JTextField       searchField;
    private JTextArea        displayArea;
    private DefaultTableModel tableModel;
    private JTable           inventoryTable;
    private JTabbedPane      tabbedPane;

    public FlowerFarmGUI(
            InventoryService inventoryService,
            TrendService trendService,
            ConnectorRegistry connectorRegistry) {
        // No AWT calls here — Spring safe to construct this bean at any time.
        this.inventoryService  = inventoryService;
        this.trendService      = trendService;
        this.connectorRegistry = connectorRegistry;
    }

    // ── ApplicationRunner ────────────────────────────────────────────────────

    @Override
    public void run(ApplicationArguments args) {
        SwingUtilities.invokeLater(this::initialise);
    }

    // ── GUI bootstrap ────────────────────────────────────────────────────────

    public void initialise() {
        frame = new JFrame("Flower Farm Manager — PNW West of Cascades, Port Orchard WA, Kitsap County");
        frame.setSize(1450, 850);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel searchPanel = new JPanel();
        searchField = new JTextField(30);
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(this::performSearchAction);
        searchPanel.add(new JLabel("Search Inventory: "));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        frame.add(searchPanel, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        setupInventoryTab();
        setupAddItemTab();
        setupPricingTab();
        setupIrrigationTab();
        setupTrendAnalysisTab();
        setupRoseVarietiesTab();
        frame.add(tabbedPane, BorderLayout.CENTER);

        displayArea = new JTextArea(5, 0);
        displayArea.setEditable(false);
        displayArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JScrollPane(displayArea), BorderLayout.CENTER);

        JButton importCsvButton           = new JButton("Import CSV");
        JButton importExcelButton         = new JButton("Import Excel");
        JButton exportCsvButton           = new JButton("Export CSV");
        JButton exportExcelButton         = new JButton("Export Excel");
        JButton webhookButton             = new JButton("Send Webhook");
        JButton importAirtableButton      = new JButton("Import Airtable");
        JButton exportAirtableButton      = new JButton("Export Airtable");
        JButton exportSquarespaceButton   = new JButton("Export Squarespace");
        JButton exportFarmbriteButton     = new JButton("Export Farmbrite");
        JButton exportVeggieCropperButton = new JButton("Export VeggieCropper");
        JButton exportFloranextButton     = new JButton("Export Floranext");
        JButton exportFloristWareButton   = new JButton("Export FloristWare");
        JButton exportIrisButton          = new JButton("Export IRIS");
        JButton exportGiftLogicButton     = new JButton("Export GiftLogic");

        importCsvButton.addActionListener(e           -> runImportConnector("csv",           "CSV"));
        importExcelButton.addActionListener(e         -> runImportConnector("excel",         "Excel"));
        exportCsvButton.addActionListener(e           -> exportInventoryAction());
        exportExcelButton.addActionListener(e         -> runExportConnector("excel",         "Excel"));
        webhookButton.addActionListener(e             -> runExportConnector("webhook",       "Webhook"));
        importAirtableButton.addActionListener(e      -> runImportConnector("airtable",      "Airtable"));
        exportAirtableButton.addActionListener(e      -> runExportConnector("airtable",      "Airtable"));
        exportSquarespaceButton.addActionListener(e   -> runExportConnector("squarespace",   "Squarespace"));
        exportFarmbriteButton.addActionListener(e     -> runExportConnector("farmbrite",     "Farmbrite"));
        exportVeggieCropperButton.addActionListener(e -> runExportConnector("veggiecropper","VeggieCropper"));
        exportFloranextButton.addActionListener(e     -> runExportConnector("floranext",     "Floranext"));
        exportFloristWareButton.addActionListener(e   -> runExportConnector("floristware",   "FloristWare"));
        exportIrisButton.addActionListener(e          -> runExportConnector("iris",          "IRIS"));
        exportGiftLogicButton.addActionListener(e     -> runExportConnector("giftlogic",     "GiftLogic"));

        JPanel exportWrap = new JPanel(new GridLayout(0, 5, 10, 8));
        exportWrap.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        exportWrap.add(importCsvButton);
        exportWrap.add(importExcelButton);
        exportWrap.add(exportCsvButton);
        exportWrap.add(exportExcelButton);
        exportWrap.add(webhookButton);
        exportWrap.add(importAirtableButton);
        exportWrap.add(exportAirtableButton);
        exportWrap.add(exportFarmbriteButton);
        exportWrap.add(exportSquarespaceButton);
        exportWrap.add(exportVeggieCropperButton);
        exportWrap.add(exportFloranextButton);
        exportWrap.add(exportFloristWareButton);
        exportWrap.add(exportIrisButton);
        exportWrap.add(exportGiftLogicButton);

        JScrollPane buttonScrollPane = new JScrollPane(exportWrap,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        buttonScrollPane.setPreferredSize(new Dimension(0, 95));

        bottomPanel.add(buttonScrollPane, BorderLayout.SOUTH);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // ── Tabs ─────────────────────────────────────────────────────────────────

    private void setupInventoryTab() {
        tableModel = new DefaultTableModel(
                new String[]{"Name", "Category", "Price", "Unit", "Cost", "Quantity", "Notes"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        inventoryTable = new JTable(tableModel);
        inventoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        refreshTable();

        JButton editButton   = new JButton("Edit Selected");
        JButton deleteButton = new JButton("Delete Selected");
        editButton.addActionListener(this::editSelectedItemAction);
        deleteButton.addActionListener(this::deleteSelectedItemAction);

        JPanel buttons = new JPanel();
        buttons.add(editButton);
        buttons.add(deleteButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(inventoryTable), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        tabbedPane.addTab("Inventory", panel);
    }

    private void setupAddItemTab() {
        JPanel addPanel = new JPanel(new GridLayout(10, 2, 6, 6));
        addPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextField nameField     = new JTextField();
        JTextField priceField    = new JTextField();
        JTextField costField     = new JTextField();
        JTextField quantityField = new JTextField();
        JTextField notesField    = new JTextField();

        String[] categories = {
                "Flowers/Plants", "Fertilizers", "Pest Control",
                "Tools/Equipment", "Rentals", "Gas/Fuel", "Other"
        };
        JComboBox<String> categoryCombo = new JComboBox<>(categories);

        String[] units = {"Per Stem", "Per Weight (lb)", "Per Unit", "Per Gallon", "Per Hour"};
        JComboBox<String> unitCombo = new JComboBox<>(units);

        String[] roseTypes = {
                "Alba", "Damask", "Gallicas", "Centifolia", "Moss",
                "Bourbon", "Hybrid Perpetual", "Portland",
                "Nootka", "Rosa rugosa", "New Dawn", "Queen Elizabeth"
        };
        JComboBox<String> roseTypeCombo = new JComboBox<>(roseTypes);
        roseTypeCombo.setEnabled(false);

        categoryCombo.addActionListener(e ->
                roseTypeCombo.setEnabled("Flowers/Plants".equals(categoryCombo.getSelectedItem())));

        addPanel.add(new JLabel("Item Name:"));          addPanel.add(nameField);
        addPanel.add(new JLabel("Category:"));            addPanel.add(categoryCombo);
        addPanel.add(new JLabel("Rose Type (opt.):"));   addPanel.add(roseTypeCombo);
        addPanel.add(new JLabel("Price:"));               addPanel.add(priceField);
        addPanel.add(new JLabel("Unit:"));                addPanel.add(unitCombo);
        addPanel.add(new JLabel("Cost:"));                addPanel.add(costField);
        addPanel.add(new JLabel("Quantity:"));            addPanel.add(quantityField);
        addPanel.add(new JLabel("Notes (PNW variety):")); addPanel.add(notesField);

        JButton addButton = new JButton("Add Item");
        addButton.addActionListener(e -> addItemAction(
                nameField, categoryCombo, priceField, unitCombo,
                costField, quantityField, notesField, roseTypeCombo));

        addPanel.add(new JLabel());
        addPanel.add(addButton);
        tabbedPane.addTab("Add Item", addPanel);
    }

    private void setupPricingTab() {
        JTextArea text = new JTextArea("""
                Pricing Guidelines
                ──────────────────
                • Per Stem        — cut flowers (roses, dahlias, tulips)
                • Per Weight (lb) — bulk herbs and mixed greens
                • Per Unit        — supplies, tools, single-item sales
                • Per Gallon      — liquid products (fertiliser, fuel)
                • Per Hour        — equipment / tractor rentals

                Tailored for PNW flowers: Roses, Dahlias, Tulips, Ranunculus, Peonies.
                Adjust prices seasonally for Kitsap County availability and demand.
                """);
        text.setEditable(false);
        tabbedPane.addTab("Pricing Info", new JScrollPane(text));
    }

    private void setupIrrigationTab() {
        JTextArea text = new JTextArea("""
                Irrigation Tips — PNW West of Cascades
                ────────────────────────────────────────
                • 1–2 inches/week in dry summers (July–August).
                • Use drip systems to prevent fungal issues in humid Kitsap County.
                • Mulch for moisture retention; deep soak every 3–5 days.
                • Monitor Port Orchard's maritime climate; reduce irrigation in wet winters.
                """);
        text.setEditable(false);
        tabbedPane.addTab("Irrigation & Care", new JScrollPane(text));
    }

    private void setupTrendAnalysisTab() {
        JTextArea trendText = new JTextArea(
                "Click 'Analyze Trends' to run ML-based (Weka LinearRegression) forecasting\n" +
                "on inventory quantities.");
        trendText.setEditable(false);

        JButton analyzeButton = new JButton("Analyze Trends");
        analyzeButton.addActionListener(e -> {
            analyzeButton.setEnabled(false);
            analyzeButton.setText("Analyzing…");

            SwingWorker<TrendService.TrendResult, Void> worker = new SwingWorker<>() {
                @Override protected TrendService.TrendResult doInBackground() {
                    return trendService.analyzeQuantityTrend();
                }
                @Override protected void done() {
                    try {
                        TrendService.TrendResult result = get();
                        trendText.setText(result.isSuccess() ? result.summary() : "Error: " + result.error());
                    } catch (Exception ex) {
                        trendText.setText("Error: " + ex.getMessage());
                    }
                    analyzeButton.setEnabled(true);
                    analyzeButton.setText("Analyze Trends");
                }
            };
            worker.execute();
        });

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(trendText), BorderLayout.CENTER);
        panel.add(analyzeButton, BorderLayout.SOUTH);
        tabbedPane.addTab("Trend Analysis", panel);
    }

    private void setupRoseVarietiesTab() {
        JTextArea roseText = new JTextArea(getRoseSuggestionsText());
        roseText.setEditable(false);
        roseText.setLineWrap(true);
        roseText.setWrapStyleWord(true);

        JButton sampleButton = new JButton("Add Sample Nootka Rose to Inventory");
        sampleButton.addActionListener(e -> addSampleRose());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(roseText), BorderLayout.CENTER);
        panel.add(sampleButton, BorderLayout.SOUTH);
        tabbedPane.addTab("Rose Varieties", panel);
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private void performSearchAction(ActionEvent e) {
        String query = searchField.getText().trim();
        List<Item> results = inventoryService.searchItems(query);
        if (results.isEmpty()) {
            displayArea.setText("No results found for '" + query + "'.");
        } else {
            StringBuilder sb = new StringBuilder();
            results.forEach(i -> sb.append(i).append('\n'));
            displayArea.setText(sb.toString());
        }
    }

    private void editSelectedItemAction(ActionEvent e) {
        int row = inventoryTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(frame, "Select a row to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            String name     = askInput("Edit Name:",     tableModel.getValueAt(row, 0));
            String category = askInput("Edit Category:", tableModel.getValueAt(row, 1));
            double price    = Double.parseDouble(askInput("Edit Price:",    tableModel.getValueAt(row, 2)));
            String unit     = askInput("Edit Unit:",     tableModel.getValueAt(row, 3));
            double cost     = Double.parseDouble(askInput("Edit Cost:",     tableModel.getValueAt(row, 4)));
            int    qty      = Integer.parseInt(askInput("Edit Quantity:",   tableModel.getValueAt(row, 5)));
            String notes    = askInput("Edit Notes:",    tableModel.getValueAt(row, 6));

            inventoryService.editItem(row, new Item(name, category, price, unit, cost, qty, notes));
            refreshTable();
            displayArea.setText("Edited: " + name);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Invalid input: " + ex.getMessage(),
                    "Edit Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedItemAction(ActionEvent e) {
        int row = inventoryTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(frame, "Select a row to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(frame,
                "Delete '" + tableModel.getValueAt(row, 0) + "'?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            inventoryService.deleteItem(row);
            refreshTable();
            displayArea.setText("Item deleted.");
        }
    }

    private void addItemAction(
            JTextField nameField, JComboBox<String> categoryCombo,
            JTextField priceField, JComboBox<String> unitCombo,
            JTextField costField, JTextField quantityField,
            JTextField notesField, JComboBox<String> roseTypeCombo) {
        try {
            String name = nameField.getText().trim();
            if (name.isEmpty()) throw new IllegalArgumentException("Name cannot be empty.");

            String category = (String) categoryCombo.getSelectedItem();
            double price    = Double.parseDouble(priceField.getText().trim());
            String unit     = (String) unitCombo.getSelectedItem();
            double cost     = Double.parseDouble(costField.getText().trim());
            int    qty      = Integer.parseInt(quantityField.getText().trim());
            String notes    = notesField.getText().trim();

            if ("Flowers/Plants".equals(category) && roseTypeCombo.isEnabled()
                    && roseTypeCombo.getSelectedItem() != null) {
                notes = "Type: " + roseTypeCombo.getSelectedItem() + "; " + notes;
            }

            inventoryService.addItem(new Item(name, category, price, unit, cost, qty, notes));
            refreshTable();
            displayArea.setText("Added: " + name);
            clearFields(nameField, priceField, costField, quantityField, notesField);

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Price, cost, and quantity must be valid numbers.",
                    "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(frame, "Invalid input: " + ex.getMessage(),
                    "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addSampleRose() {
        try {
            inventoryService.addItem(new Item(
                    "Nootka Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50,
                    "Native PNW rose, pink blooms, hardy in wet soils"));
            refreshTable();
            displayArea.setText("Sample rose added: Nootka Rose.");
            JOptionPane.showMessageDialog(frame, "Nootka Rose added to inventory.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(frame, "Error: " + ex.getMessage(),
                    "Add Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportInventoryAction() {
        inventoryService.exportToCsv("exported_inventory.csv");
        displayArea.setText("Exported to exported_inventory.csv");
    }

    private void runImportConnector(String connectorName, String displayName) {
        SwingWorker<ConnectorResult<List<Item>>, Void> worker = new SwingWorker<>() {
            @Override protected ConnectorResult<List<Item>> doInBackground() {
                return connectorRegistry.runImport(connectorName);
            }
            @Override protected void done() {
                try {
                    ConnectorResult<List<Item>> result = get();
                    if (result.isSuccess()) {
                        refreshTable();
                        int imported = result.getPayload() == null ? 0 : result.getPayload().size();
                        displayArea.setText(displayName + " import complete. Imported: " + imported
                                + "\n" + result.getMessage());
                        JOptionPane.showMessageDialog(frame,
                                displayName + " import complete.\nImported: " + imported,
                                displayName + " Import Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        displayArea.setText(displayName + " import failed: " + result.getMessage()
                                + "\n" + result.getErrorDetail());
                        JOptionPane.showMessageDialog(frame,
                                result.getMessage() + "\n" + result.getErrorDetail(),
                                displayName + " Import Failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    displayArea.setText(displayName + " import error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(frame,
                            displayName + " import error: " + ex.getMessage(),
                            displayName + " Import Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void runExportConnector(String connectorName, String displayName) {
        SwingWorker<ConnectorResult<Integer>, Void> worker = new SwingWorker<>() {
            @Override protected ConnectorResult<Integer> doInBackground() {
                return connectorRegistry.runExport(connectorName);
            }
            @Override protected void done() {
                try {
                    ConnectorResult<Integer> result = get();
                    if (result.isSuccess()) {
                        int exported = result.getPayload() == null ? 0 : result.getPayload();
                        displayArea.setText(displayName + " export complete. Exported: " + exported
                                + "\n" + result.getMessage());
                        JOptionPane.showMessageDialog(frame,
                                displayName + " export complete.\nExported: " + exported,
                                displayName + " Export Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        displayArea.setText(displayName + " export failed: " + result.getMessage()
                                + "\n" + result.getErrorDetail());
                        JOptionPane.showMessageDialog(frame,
                                result.getMessage() + "\n" + result.getErrorDetail(),
                                displayName + " Export Failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    displayArea.setText(displayName + " export error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(frame,
                            displayName + " export error: " + ex.getMessage(),
                            displayName + " Export Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (Item item : inventoryService.getAllItems()) {
            tableModel.addRow(new Object[]{
                    item.getName(),
                    item.getCategory(),
                    String.format("%.2f", item.getPrice()),
                    item.getUnit(),
                    String.format("%.2f", item.getCost()),
                    item.getQuantity(),
                    item.getNotes()
            });
        }
    }

    private String askInput(String prompt, Object current) {
        return JOptionPane.showInputDialog(frame, prompt, current);
    }

    private void clearFields(JTextField... fields) {
        for (JTextField f : fields) f.setText("");
    }

    private String getRoseSuggestionsText() {
        return """
                Rose Varieties — Oregon & Western Washington West of the Cascades
                ──────────────────────────────────────────────────────────────────

                ONE-TIME BLOOMERS (from course slides)
                  Alba       → Alba Maxima: White, fragrant, hardy in shade.
                  Damask     → Ispahan: Pink, strong scent, drought-tolerant.
                  Gallicas   → Charles de Mills: Crimson, compact shrub.
                  Centifolia → Fantin Latour: Pink, very full blooms.
                  Moss       → William Lobb: Purple, mossy buds, vigorous.

                REPEAT BLOOMERS (from course slides)
                  Bourbon          → Zephirine Drouhin: Pink climber, thornless, shade-tolerant.
                  Hybrid Perpetual → Reine des Violettes: Purple, recurrent, fragrant.
                  Portland         → Comte de Chambord: Pink, compact, spicy scent.

                REGIONAL / NATIVE (Hardy, Disease-Resistant for PNW)
                  Nootka Rose (Rosa nutkana)     — Native, pink, wildlife-friendly, tolerates wet soils.
                  Baldhip Rose (Rosa gymnocarpa) — Native, pink, small hips, shade-loving.
                  Cluster Rose (Rosa pisocarpa)  — Native, pink clusters, good for erosion control.
                  Rosa rugosa Hansa              — Purple, rugged, salt-tolerant.
                  New Dawn Climber               — Light pink, vigorous, blackspot-resistant.
                  Queen Elizabeth Hybrid Tea     — Pink, tall, hearty.
                  Strawberry Hill (D.Austin)     — Pink climber, fragrant.
                  Munstead Wood (D.Austin)       — Rich dark red, OGR-type fragrance.
                  Harison's Yellow Shrub         — Yellow, drought-tolerant.

                TIP: For humid PNW conditions choose disease-resistant varieties (rugosas,
                Nootka). Consult Portland Nursery or Swansons (Seattle) for availability.
                """;
    }
}
