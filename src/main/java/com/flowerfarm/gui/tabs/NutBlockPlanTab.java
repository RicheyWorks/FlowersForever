package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.NutBlockPlan;
import com.flowerfarm.model.NutBlockStage;
import com.flowerfarm.service.NutBlockPlanService;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Editable tree nut orchard plans, separate from harvested nuts and sales. */
public final class NutBlockPlanTab implements FlowerFarmTab {

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved nut block plan");

    private final NutBlockPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<NutBlockStage, JButton> stageTargets =
            new EnumMap<>(NutBlockStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField blockNameField;
    private JTextField nutCropField;
    private JTextField varietyField;
    private JTextField acresField;
    private JTextField pollinizerVarietyField;
    private JTextField treeCountField;
    private JTextField bloomStartField;
    private JTextField floorPrepDateField;
    private JTextField harvestStartField;
    private JTextField harvestEndField;
    private JTextField intendedDestinationField;
    private JComboBox<NutBlockStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextSeasonButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private final NotebookSaveRecovery saveRecovery = new NotebookSaveRecovery();
    private List<NutBlockPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public NutBlockPlanTab(NutBlockPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public NutBlockPlanTab(NutBlockPlanService service, TabHost host,
                             FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Nut blocks"; }

    @Override public String getDescription() {
        return "Tree nut orchard plans beside shared inventory and customer tools";
    }

    @Override public JComponent getUIComponent() {
        if (panel == null) buildUi();
        return panel;
    }

    @Override public void initialize() { refreshData(); }

    @Override public void refreshData() {
        if (panel == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refreshData);
            return;
        }
        if (loading || saving) {
            refreshQueued = true;
            return;
        }
        loading = true;
        updateActions();
        new SwingWorker<List<NutBlockPlan>, Void>() {
            @Override protected List<NutBlockPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<NutBlockPlan> fields = get();
                    showFields(fields == null ? List.of() : fields);
                    saveRecovery.refreshed();
                    showFeedback(hasUnsavedDraft()
                                    ? "Nut blocks refreshed; unsaved draft kept."
                                    : "Nut blocks refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load blocks. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load blocks. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } finally {
                    loading = false;
                    updateActions();
                    if (refreshQueued) {
                        refreshQueued = false;
                        refreshData();
                    }
                }
            }
        }.execute();
    }

    @Override public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        updateActions();
    }

    public boolean hasPendingSave() { return saving; }

    public String applicationExitWarning() {
        if (saving) return "A nut block plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved nut block plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("nutBlock.page");
        panel.getAccessibleContext().setAccessibleName("Nut blocks workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Nut blocks",
                "Plan tree nut blocks, bloom, floor preparation, and harvest windows.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("nutBlock.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("nutBlock.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("nutBlock.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "Nut block plans do not record actual yields, cleaning, stock, or orders. "
                        + "Pollinizer varieties are notes; compatibility is not checked.",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildFieldList(), BorderLayout.CENTER);
        listAndTargets.add(buildStageTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("nutBlock.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        FarmUi.revealFocusedControls(scroll);
        clearForm();
        updateActions();
    }

    private JComponent buildFieldList() {
        tableModel = new DefaultTableModel(new String[]{"Block", "Nut crop",
                "Variety", "Acres", "Trees", "Harvest start", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("nutBlock.blockTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Nut block plans table");
        table.setTransferHandler(new FieldDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectField();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New nut block plan");
        newButton.setName("nutBlock.new");
        newButton.addActionListener(event -> newField());
        nextSeasonButton = FarmUi.secondaryButton("Plan next season");
        nextSeasonButton.setName("nutBlock.nextSeason");
        nextSeasonButton.setToolTipText("Carry the block and orchard identity forward. "
                + "Enter a new harvest start before saving.");
        nextSeasonButton.addActionListener(event -> planNextSeason());
        refreshButton = FarmUi.secondaryButton("Refresh blocks");
        refreshButton.setName("nutBlock.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextSeasonButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved nut blocks",
                "Select a block to edit or plan its next season; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                NutBlockStage.values().length, 8, 0));
        targets.setName("nutBlock.stageTargets");
        targets.setOpaque(false);
        for (NutBlockStage stage : NutBlockStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("nutBlock.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved block here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected block to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedFieldTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved block",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        blockNameField = field("Block name", "e.g. South hazelnut block", 24);
        nutCropField = field("Nut crop", "e.g. hazelnut or walnut", 24);
        varietyField = field("Variety", "Optional orchard variety", 24);
        pollinizerVarietyField = field("Pollinizer variety", "Optional variety note", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        treeCountField = field("Tree count", "Optional whole number", 12);
        intendedDestinationField = field("Intended destination", "Optional, e.g. processor", 24);
        bloomStartField = field("Planned bloom start", "Optional YYYY-MM-DD", 12);
        floorPrepDateField = field("Planned floor prep", "Optional YYYY-MM-DD", 12);
        harvestStartField = field("Planned harvest start", "Required YYYY-MM-DD", 12);
        harvestEndField = field("Planned harvest end", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(NutBlockStage.values());
        stageField.setName("nutBlock.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("nutBlock.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Block notes");
        FarmUi.initialFocus(blockNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Block name *", blockNameField, blockNameField);
        addField(fields, 0, 1, "Nut crop *", nutCropField, nutCropField);
        addField(fields, 1, 0, "Variety", varietyField, varietyField);
        addField(fields, 1, 1, "Pollinizer variety", pollinizerVarietyField,
                pollinizerVarietyField);
        addField(fields, 2, 0, "Acres", acresField, acresField);
        addField(fields, 2, 1, "Tree count", treeCountField, treeCountField);
        addField(fields, 3, 0, "Intended destination", intendedDestinationField,
                intendedDestinationField);
        addField(fields, 3, 1, "Planned bloom start", bloomStartField, bloomStartField);
        addField(fields, 4, 0, "Planned floor prep", floorPrepDateField, floorPrepDateField);
        addField(fields, 4, 1, "Planned harvest start *", harvestStartField,
                harvestStartField);
        addField(fields, 5, 0, "Planned harvest end", harvestEndField,
                harvestEndField);
        addField(fields, 5, 1, "Planning stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 6, 0, "Block notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("nutBlock.save");
        saveButton.addActionListener(event -> saveField());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("nutBlock.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a nut block season",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("nutBlock.feedback");
        feedback.getAccessibleContext().setAccessibleName("Nut block plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.SOUTH);
        footer.add(feedback, BorderLayout.NORTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Season plan details",
                "Record orchard and seasonal details. Pollinizer compatibility is not checked; "
                        + "a planned harvest start is required.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("nutBlock." + name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_"));
        field.getAccessibleContext().setAccessibleName(name);
        FarmUi.entryControl(field, placeholder, "Local planning detail.");
        return field;
    }

    private static void addField(JPanel target, int row, int column, String label,
                                 JComponent control, JComponent labelTarget) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = column;
        cell.gridy = row;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.anchor = GridBagConstraints.NORTHWEST;
        cell.insets = new Insets(0, 4, 8, 4);
        target.add(FarmUi.entryField(label, control, labelTarget, '\0'), cell);
    }

    private void openGeneral(String title) {
        host.selectTab(title);
        host.setStatus("Opened general " + title + "; your nut block plan draft remains here.");
    }

    private void showFields(List<NutBlockPlan> fields) {
        lastView = List.copyOf(fields);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (NutBlockPlan field : lastView) {
                tableModel.addRow(new Object[]{field.getBlockName(),
                        field.getNutCrop(),
                        field.getVariety(),
                        field.getAcres() == null ? ""
                                : field.getAcres().stripTrailingZeros().toPlainString(),
                        field.getTreeCount() == null ? "" : field.getTreeCount(),
                        field.getPlannedHarvestStart() == null ? ""
                                : field.getPlannedHarvestStart(),
                        enumLabel(field.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadField);
        }
        updateActions();
    }

    private void selectField() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        NutBlockPlan selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadField(selected);
    }

    private void restoreSelection() {
        table.clearSelection();
        if (editingId == null) return;
        for (int row = 0; row < lastView.size(); row++) {
            if (editingId.equals(lastView.get(row).getId())) {
                table.setRowSelectionInterval(table.convertRowIndexToView(row),
                        table.convertRowIndexToView(row));
                return;
            }
        }
    }

    private void newField() {
        if (!canWrite || saving || loading) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        blockNameField.requestFocusInWindow();
        updateActions();
    }

    private void planNextSeason() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved block first.", FarmUi.Tone.INFO);
            return;
        }
        NutBlockPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null || !mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        blockNameField.setText(previous.getBlockName());
        nutCropField.setText(previous.getNutCrop());
        varietyField.setText(previous.getVariety());
        pollinizerVarietyField.setText(previous.getPollinizerVariety());
        acresField.setText(previous.getAcres() == null ? ""
                : previous.getAcres().toPlainString());
        treeCountField.setText(previous.getTreeCount() == null ? ""
                : previous.getTreeCount().toString());
        intendedDestinationField.setText(previous.getIntendedDestination());
        harvestStartField.requestFocusInWindow();
        showFeedback("Next season drafted for " + previous.getBlockName()
                + ". Review the orchard details and enter new bloom, floor prep, and harvest dates.",
                FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        blockNameField.setText("");
        nutCropField.setText("");
        varietyField.setText("");
        pollinizerVarietyField.setText("");
        acresField.setText("");
        treeCountField.setText("");
        bloomStartField.setText("");
        floorPrepDateField.setText("");
        harvestStartField.setText("");
        harvestEndField.setText("");
        intendedDestinationField.setText("");
        stageField.setSelectedItem(NutBlockStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a nut block season", FarmUi.Tone.NEUTRAL);
    }

    private void loadField(NutBlockPlan field) {
        editingId = field.getId();
        blockNameField.setText(field.getBlockName());
        nutCropField.setText(field.getNutCrop());
        varietyField.setText(field.getVariety());
        pollinizerVarietyField.setText(field.getPollinizerVariety());
        acresField.setText(field.getAcres() == null ? ""
                : field.getAcres().toPlainString());
        treeCountField.setText(field.getTreeCount() == null ? ""
                : field.getTreeCount().toString());
        bloomStartField.setText(field.getPlannedBloomStart() == null
                ? "" : field.getPlannedBloomStart().toString());
        floorPrepDateField.setText(field.getPlannedFloorPrepDate() == null
                ? "" : field.getPlannedFloorPrepDate().toString());
        harvestStartField.setText(field.getPlannedHarvestStart() == null
                ? "" : field.getPlannedHarvestStart().toString());
        harvestEndField.setText(field.getPlannedHarvestEnd() == null
                ? "" : field.getPlannedHarvestEnd().toString());
        intendedDestinationField.setText(field.getIntendedDestination());
        stageField.setSelectedItem(field.getStage());
        notesField.setText(field.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + field.getBlockName() + " · "
                + field.getNutCrop(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the block save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This nut block plan has unsaved planning changes. Discard them?",
                "Unsaved nut block plan draft", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        if (choice != 1) {
            return false;
        }
        if (!reviewed.stillCurrent(editingId, formState(), lastView,
                table.getSelectedRow(), canWrite, loading, saving)) {
            showFeedback("Draft kept because this notebook changed while the question was open. "
                    + "Review it and choose again.", FarmUi.Tone.WARNING);
            return false;
        }
        return true;
    }

    private void discardChanges() {
        if (!mayLeaveDraft()) return;
        if (editingId == null) {
            clearForm();
            return;
        }
        lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                .findFirst().ifPresentOrElse(this::loadField, this::clearForm);
    }

    private void saveField() {
        if (!canWrite || saving || loading) return;
        NutBlockPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving nut block plan…", FarmUi.Tone.INFO);
        new SwingWorker<NutBlockPlan, Void>() {
            @Override protected NutBlockPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    NutBlockPlan saved = get();
                    wrote = true;
                    editingId = saved.getId();
                    lastView = saveRecovery.remember(lastView, saved, NutBlockPlan::getId);
                    cleanForm = formState();
                    showFields(lastView);
                    showFeedback("Nut block plan saved · " + saved.getBlockName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Nut block plan “" + saved.getBlockName() + "” saved.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (wrote) {
                        showSavedRefreshFailure();
                    } else {
                        showSaveFailure();
                    }
                } catch (Exception exception) {
                    if (wrote) {
                        showSavedRefreshFailure();
                    } else {
                        showSaveFailure();
                    }
                } finally {
                    saving = false;
                    updateActions();
                    if (wrote || refreshQueued) {
                        refreshQueued = false;
                        refreshData();
                    }
                }
            }
        }.execute();
    }

    private NutBlockPlan readDraft() {
        String blockName = blockNameField.getText().trim();
        if (blockName.isEmpty()) {
            blockNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a block name.");
        }
        String crop = nutCropField.getText().trim();
        if (crop.isEmpty()) {
            nutCropField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a nut crop.");
        }
        BigDecimal acres = null;
        String acresText = acresField.getText().trim();
        if (!acresText.isEmpty()) {
            try {
                acres = new BigDecimal(acresText);
            } catch (NumberFormatException exception) {
                acresField.requestFocusInWindow();
                throw new IllegalArgumentException("Enter acres as a number, or leave it blank.");
            }
            if (acres.signum() <= 0 || acres.compareTo(new BigDecimal("1000000.00")) > 0
                    || acres.stripTrailingZeros().scale() > 2) {
                acresField.requestFocusInWindow();
                throw new IllegalArgumentException("Enter positive acres up to 1,000,000 with two decimals.");
            }
        }
        Integer treeCount = null;
        String treeCountText = treeCountField.getText().trim();
        if (!treeCountText.isEmpty()) {
            try {
                treeCount = Integer.valueOf(treeCountText);
            } catch (NumberFormatException exception) {
                treeCountField.requestFocusInWindow();
                throw new IllegalArgumentException("Enter tree count as a whole number, or leave it blank.");
            }
            if (treeCount <= 0) {
                treeCountField.requestFocusInWindow();
                throw new IllegalArgumentException("Tree count must be greater than zero.");
            }
        }
        LocalDate bloomStart = date(bloomStartField, "planned bloom start");
        LocalDate floorPrep = date(floorPrepDateField, "planned floor prep");
        LocalDate harvestStart = date(harvestStartField, "planned harvest start");
        if (harvestStart == null) {
            harvestStartField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a planned harvest start date.");
        }
        if (bloomStart != null && bloomStart.isAfter(harvestStart)) {
            bloomStartField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned bloom start cannot be after planned harvest start.");
        }
        if (floorPrep != null && floorPrep.isAfter(harvestStart)) {
            floorPrepDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned floor prep cannot be after planned harvest start.");
        }
        LocalDate harvestEnd = date(harvestEndField, "planned harvest end");
        if (harvestEnd != null && harvestEnd.isBefore(harvestStart)) {
            harvestEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest end cannot precede start.");
        }
        NutBlockPlan draft = new NutBlockPlan(blockName, crop,
                varietyField.getText().trim(), pollinizerVarietyField.getText().trim(),
                acres, treeCount, bloomStart, floorPrep, harvestStart, harvestEnd,
                intendedDestinationField.getText().trim(),
                (NutBlockStage) stageField.getSelectedItem(), notesField.getText().trim());
        draft.validate();
        return draft;
    }

    private static LocalDate date(JTextField field, String label) {
        if (field.getText().isBlank()) return null;
        try {
            return LocalDate.parse(field.getText().trim());
        } catch (DateTimeParseException exception) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter the " + label + " as YYYY-MM-DD.");
        }
    }

    private void moveSelectedFieldTo(NutBlockStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved nut block plan first.", FarmUi.Tone.INFO);
            return;
        }
        moveFieldToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, NutBlockStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveFieldToStage(Long id, NutBlockStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current block action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved block.",
                    FarmUi.Tone.WARNING);
            return;
        }
        NutBlockPlan source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved block is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getBlockName() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getBlockName() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<NutBlockPlan, Void>() {
            @Override protected NutBlockPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    NutBlockPlan saved = get();
                    wrote = true;
                    lastView = saveRecovery.remember(lastView, saved, NutBlockPlan::getId);
                    if (Objects.equals(editingId, saved.getId())) loadField(saved);
                    showFields(lastView);
                    showFeedback(saved.getBlockName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Nut block plan “" + saved.getBlockName()
                            + "” moved to " + enumLabel(saved.getStage()) + ".");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (wrote) {
                        showSavedRefreshFailure();
                    } else {
                        showFeedback("Stage was not changed. Retry the move.", FarmUi.Tone.DANGER);
                    }
                } catch (Exception exception) {
                    if (wrote) {
                        showSavedRefreshFailure();
                    } else {
                        showFeedback("Stage was not changed. Retry the move.", FarmUi.Tone.DANGER);
                    }
                } finally {
                    saving = false;
                    updateActions();
                    if (wrote || refreshQueued) {
                        refreshQueued = false;
                        refreshData();
                    }
                }
            }
        }.execute();
    }

    private final class FieldDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            NutBlockPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new FieldTransferable(
                    new StageDrag(NutBlockPlanTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final NutBlockStage stage;

        private StageDropHandler(NutBlockStage stage) { this.stage = stage; }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(FIELD_FLAVOR)) return false;
            try {
                return canMoveDrag((StageDrag) support.getTransferable()
                        .getTransferData(FIELD_FLAVOR), stage);
            } catch (UnsupportedFlavorException | IOException | ClassCastException exception) {
                return false;
            }
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                StageDrag drag = (StageDrag) support.getTransferable()
                        .getTransferData(FIELD_FLAVOR);
                moveFieldToStage(drag.plan().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(NutBlockPlanTab source, long revision,
                             NutBlockPlan plan) { }

    private record FieldTransferable(StageDrag drag) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{FIELD_FLAVOR};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return FIELD_FLAVOR.equals(flavor);
        }

        @Override public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return drag;
        }
    }

    private void showSavedRefreshFailure() {
        saveRecovery.refreshFailed();
        showFeedback("Save completed. Refresh the notebook to finish updating its display.",
                FarmUi.Tone.WARNING);
    }

    private void showSaveFailure() {
        showFeedback("Not saved · complete planning draft kept. "
                + "Check the local data store and retry.", FarmUi.Tone.DANGER);
        host.setStatus("Nut block plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, saveRecovery.feedback(text),
                    saveRecovery.needsRefresh() && tone != FarmUi.Tone.DANGER
                            ? FarmUi.Tone.WARNING : tone);
    }

    private FormState formState() {
        return new FormState(blockNameField.getText(), nutCropField.getText(),
                varietyField.getText(), pollinizerVarietyField.getText(),
                acresField.getText(), treeCountField.getText(),
                bloomStartField.getText(), floorPrepDateField.getText(),
                harvestStartField.getText(), harvestEndField.getText(),
                intendedDestinationField.getText(),
                (NutBlockStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || blockNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(blockNameField, nutCropField,
                varietyField, pollinizerVarietyField, acresField, treeCountField,
                bloomStartField, floorPrepDateField, harvestStartField,
                harvestEndField, intendedDestinationField,
                stageField, notesField)) control.setEnabled(editable);
        newButton.setEnabled(editable);
        nextSeasonButton.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
        saveButton.setEnabled(editable && !loading);
        discardButton.setEnabled(editable);
        refreshButton.setEnabled(!loading && !saving);
        for (JButton target : stageTargets.values()) {
            target.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
        }
        FarmUi.updateStatusBadge(accessBadge,
                canWrite ? "HAND / OWNER · planning edits" : "VIEWER · read only",
                canWrite ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
    }

    private static DefaultListCellRenderer enumRenderer() {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(javax.swing.JList<?> list,
                    Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focus);
                label.setText(value instanceof Enum<?> choice ? enumLabel(choice) : "");
                return label;
            }
        };
    }

    private static String enumLabel(Enum<?> value) {
        String words = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private record FormState(String blockName, String crop,
                             String variety, String pollinizerVariety,
                             String acres, String treeCount,
                             String bloomStart, String floorPrepDate,
                             String harvestStart, String harvestEnd,
                             String intendedDestination,
                             NutBlockStage stage,
                             String notes) { }

    private static final class WorkspaceBody extends JPanel implements Scrollable {
        private WorkspaceBody() {
            super(new BorderLayout(0, FarmUi.SECTION_GAP));
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect,
                                                         int orientation, int direction) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect,
                                                          int orientation, int direction) {
            return Math.max(24, visibleRect.height - 48);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
