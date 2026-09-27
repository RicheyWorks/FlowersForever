package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.Locale;

/**
 * Rose varieties reference tab with a one-click "add sample Nootka Rose" action.
 *
 * <p>Content is curated for Oregon &amp; Western Washington (west of the
 * Cascades), drawing on course material and regionally hardy native selections.
 */
public class RoseVarietiesTab implements FlowerFarmTab {

    private static final Object[][] VARIETIES = {
            {"One-time", "Alba Maxima", "Shade and fragrance", "White, hardy"},
            {"One-time", "Ispahan", "Dry sites and scent", "Pink Damask"},
            {"One-time", "Charles de Mills", "Compact beds", "Crimson Gallica"},
            {"One-time", "Fantin Latour", "Full romantic blooms", "Pink Centifolia"},
            {"One-time", "William Lobb", "Vigorous hedges", "Purple Moss rose"},
            {"Repeat", "Zephirine Drouhin", "Shaded climbing", "Pink, thornless"},
            {"Repeat", "Reine des Violettes", "Fragrant cutting", "Purple recurrent"},
            {"Repeat", "Comte de Chambord", "Compact cutting beds", "Pink, spicy scent"},
            {"Native / regional", "Nootka Rose", "Wet soils and wildlife", "Native pink"},
            {"Native / regional", "Baldhip Rose", "Woodland shade", "Native, small hips"},
            {"Native / regional", "Cluster Rose", "Erosion control", "Native pink clusters"},
            {"Native / regional", "Rosa rugosa Hansa", "Salt and exposed sites", "Rugged purple"},
            {"Native / regional", "New Dawn Climber", "Trellises", "Blackspot-resistant"},
            {"Native / regional", "Queen Elizabeth", "Tall cutting rows", "Hardy pink"},
            {"Native / regional", "Strawberry Hill", "Fragrant climbing", "David Austin pink"},
            {"Native / regional", "Munstead Wood", "Dark fragrant cuts", "Deep red"},
            {"Native / regional", "Harison's Yellow", "Dry shrub borders",
                    "Yellow, drought tolerant"}
    };

    private final InventoryService inventoryService;
    private final TabHost host;
    private final FarmDialogs dialogs;

    private JPanel panel;
    private JButton sampleButton;
    private JButton openInventoryButton;
    private JLabel sampleStatusLabel;
    private boolean canWrite = true;
    private boolean savingSample;
    private boolean sampleAvailable = true;

    public RoseVarietiesTab(InventoryService inventoryService, TabHost host) {
        this(inventoryService, host, FarmDialogs.swing());
    }

    public RoseVarietiesTab(InventoryService inventoryService, TabHost host,
                            FarmDialogs dialogs) {
        this.inventoryService = inventoryService;
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public String getTabTitle() {
        return "Rose Varieties";
    }

    @Override
    public String getDescription() {
        return "PNW rose variety guide (west of the Cascades)";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        GuiPermissions.setWritable(canWrite && !savingSample && sampleAvailable, sampleButton);
    }

    private void buildUI() {
        panel = FarmUi.page();

        sampleButton = FarmUi.primaryButton("Add sample Nootka Rose");
        sampleButton.setName("rose.addSample");
        sampleButton.setToolTipText("Add a ready-to-edit native rose product to inventory.");
        sampleButton.addActionListener(event -> addSampleRose());
        sampleStatusLabel = FarmUi.wrappingStatusBadge("Inventory sample ready", FarmUi.Tone.NEUTRAL);
        sampleStatusLabel.setName("rose.sampleStatus");
        sampleStatusLabel.getAccessibleContext().setAccessibleName(
                "Rose sample inventory status");
        openInventoryButton = FarmUi.secondaryButton("Open Inventory");
        openInventoryButton.setName("rose.openInventory");
        openInventoryButton.setToolTipText(
                "Review or edit Nootka Rose and other flower products in Inventory.");
        openInventoryButton.addActionListener(event -> {
            if (host != null) {
                host.selectTab("Inventory");
                host.setStatus("Opened Inventory from the rose variety guide.");
            }
        });
        JButton visualizer = FarmUi.secondaryButton("Open Rose Visualizer");
        visualizer.setName("rose.openVisualizer");
        visualizer.setToolTipText(
                "Explore generative growth habits for Nootka, rugosa, Bourbon, and climbers.");
        visualizer.addActionListener(event -> {
            if (host != null) {
                host.selectTab("Rose Visualizer");
                host.setStatus("Rose Visualizer — grow & mutate PNW rose L-Systems.");
            }
        });
        panel.add(FarmUi.pageHeader(
                "Rose varieties",
                "Field notes for resilient roses west of the Cascades",
                null), BorderLayout.NORTH);
        JPanel actions = FarmUi.toolbar(sampleButton, openInventoryButton, visualizer);
        actions.setName("rose.actions");
        JPanel actionArea = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        actionArea.setName("rose.sampleWorkspace");
        actionArea.setOpaque(false);
        actionArea.add(actions, BorderLayout.NORTH);
        actionArea.add(sampleStatusLabel, BorderLayout.CENTER);

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Bloom habit", "Variety", "Best for", "Field notes"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (Object[] variety : VARIETIES) {
            model.addRow(variety);
        }
        JTable roseTable = new JTable(model);
        roseTable.setName("rose.varieties.guide");
        FarmUi.configureTable(roseTable, "Pacific Northwest rose variety table");
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        roseTable.setRowSorter(sorter);
        roseTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        roseTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        roseTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        roseTable.getColumnModel().getColumn(3).setPreferredWidth(220);

        JTextField search = FarmUi.searchField(
                "Rose variety search", "Search variety, habit, or field use", 24);
        search.setName("rose.search");
        FarmUi.initialFocus(search);
        JComboBox<String> habit = new JComboBox<>(new String[]{
                "All bloom habits", "One-time", "Repeat", "Native / regional"});
        habit.setName("rose.habitFilter");
        habit.getAccessibleContext().setAccessibleName("Rose bloom habit filter");
        JLabel results = FarmUi.statusBadge(
                VARIETIES.length + " varieties", FarmUi.Tone.INFO);
        results.setName("rose.results");
        Runnable applyFilter = () -> {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            String selectedHabit = String.valueOf(habit.getSelectedItem());
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(
                        Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                    boolean habitMatches = selectedHabit.startsWith("All")
                            || selectedHabit.equals(entry.getStringValue(0));
                    boolean textMatches = query.isBlank();
                    for (int column = 0;
                         column < entry.getValueCount() && !textMatches; column++) {
                        textMatches = entry.getStringValue(column)
                                .toLowerCase(Locale.ROOT).contains(query);
                    }
                    return habitMatches && textMatches;
                }
            });
            FarmUi.updateStatusBadge(results,
                    roseTable.getRowCount() + " varieties", FarmUi.Tone.INFO);
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { applyFilter.run(); }
            @Override public void removeUpdate(DocumentEvent event) { applyFilter.run(); }
            @Override public void changedUpdate(DocumentEvent event) { applyFilter.run(); }
        });
        habit.addActionListener(event -> applyFilter.run());
        JButton clear = FarmUi.secondaryButton("Clear filters");
        clear.setName("rose.clearFilters");
        clear.addActionListener(event -> {
            search.setText("");
            habit.setSelectedIndex(0);
        });
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setLabelFor(search);
        JLabel habitLabel = new JLabel("Bloom habit:");
        habitLabel.setLabelFor(habit);
        JPanel searchRow = FarmUi.toolbar(searchLabel, search, clear);
        searchRow.setName("rose.searchRow");
        JPanel habitRow = FarmUi.toolbar(habitLabel, habit, results);
        habitRow.setName("rose.habitRow");
        JPanel filters = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        filters.setName("rose.filters");
        filters.setOpaque(false);
        filters.add(searchRow, BorderLayout.NORTH);
        filters.add(habitRow, BorderLayout.CENTER);
        JPanel controls = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        controls.setOpaque(false);
        controls.add(actionArea, BorderLayout.NORTH);
        controls.add(FarmUi.section(
                "Find a resilient rose",
                "Filter by bloom habit or search the variety, field use, and notes.",
                filters), BorderLayout.CENTER);
        JPanel guide = FarmUi.section(
                "Regional variety guide",
                "Compare disease resilience, shade tolerance, wet-soil fit, and cutting use.",
                FarmUi.tableScroll(roseTable));
        JPanel content = new RoseBody(controls, guide);
        content.setName("rose.body");
        FarmUi.revealFocusedControls(content);
        JScrollPane scroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("rose.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
    }

    /** Keep the variety rows readable while shorter windows scroll the controls above them. */
    private static final class RoseBody extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        private final JComponent controls;

        private RoseBody(JComponent controls, JComponent guide) {
            super(new BorderLayout(0, FarmUi.SECTION_GAP));
            this.controls = controls;
            setOpaque(false);
            add(controls, BorderLayout.NORTH);
            add(guide, BorderLayout.CENTER);
        }

        @Override public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            preferred.height = controls.getPreferredSize().height + FarmUi.SECTION_GAP + 320;
            return preferred;
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    private void addSampleRose() {
        if (savingSample || !sampleAvailable) {
            return;
        }
        if (!GuiPermissions.requireWrite(host, panel, "add sample inventory items")) {
            return;
        }
        startAddSampleRose();
    }

    void startAddSampleRose() {
        if (savingSample || !sampleAvailable) {
            return;
        }
        savingSample = true;
        applyRolePermissions(canWrite);
        showSampleState("Checking Inventory…",
                "Looking for an existing Nootka Rose before adding the sample in the background.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Checking Inventory before adding the Nootka Rose sample…");
        }
        Item sample = new Item(
                "Nootka Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50,
                "Native PNW rose, pink blooms, hardy in wet soils");

        new SwingWorker<SampleResult, Void>() {
            @Override
            protected SampleResult doInBackground() {
                boolean exists = inventoryService.getAllItems().stream()
                        .anyMatch(item -> "Nootka Rose".equalsIgnoreCase(item.getName()));
                if (exists) {
                    return new SampleResult(false);
                }
                inventoryService.addItem(sample);
                return new SampleResult(true);
            }

            @Override
            protected void done() {
                try {
                    completeSampleSave(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failSampleSave(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failSampleSave(exception.getCause() == null
                            ? exception : exception.getCause());
                } catch (RuntimeException exception) {
                    failSampleSave(exception);
                }
            }
        }.execute();
    }

    private void completeSampleSave(SampleResult result) {
        savingSample = false;
        sampleAvailable = false;
        sampleButton.setText(result.added() ? "Nootka Rose added" : "Already in Inventory");
        applyRolePermissions(canWrite);
        String status = result.added()
                ? "Nootka Rose added · open Inventory to edit"
                : "Nootka Rose already exists · open Inventory to review";
        showSampleState(status,
                result.added()
                        ? "The sample was saved once. Open Inventory to adjust quantity, price, or notes."
                        : "No duplicate was added. Open Inventory to review the existing product.",
                result.added() ? FarmUi.Tone.SUCCESS : FarmUi.Tone.INFO);
        String refreshGuidance = "";
        if (host != null) {
            try {
                host.refreshAll();
            } catch (RuntimeException refreshFailure) {
                refreshGuidance = " Refresh Inventory manually. "
                        + FarmUi.actionableError("Refreshing the saved rose", refreshFailure);
            }
            host.setStatus((result.added()
                    ? "Nootka Rose added to Inventory."
                    : "Nootka Rose is already in Inventory; no duplicate was added.")
                    + refreshGuidance);
        }
        if (panel.isShowing()) {
            dialogs.showMessage(panel,
                    result.added()
                            ? "Nootka Rose was added to Inventory.\nOpen Inventory to edit its details."
                            : "Nootka Rose is already in Inventory.\nNo duplicate was added.",
                    result.added() ? "Sample added" : "Already in Inventory",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void failSampleSave(Throwable failure) {
        savingSample = false;
        applyRolePermissions(canWrite);
        String guidance = FarmUi.actionableError("Adding the Nootka Rose sample", failure);
        showSampleState("Sample not added · retry",
                guidance + " The variety guide and filters were kept.", FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus(guidance + " The sample button is ready to retry.");
        }
        if (panel.isShowing()) {
            dialogs.showMessage(panel, guidance, "Sample not added",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showSampleState(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(sampleStatusLabel, message, tone);
        sampleStatusLabel.setToolTipText(description);
        sampleStatusLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private record SampleResult(boolean added) {
    }

}
