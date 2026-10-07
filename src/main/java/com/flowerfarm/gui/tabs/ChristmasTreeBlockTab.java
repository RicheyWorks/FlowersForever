package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.ChristmasTreeBlockPlan;
import com.flowerfarm.model.ChristmasTreeBlockStage;
import com.flowerfarm.service.ChristmasTreeBlockPlanService;

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

/** Editable Christmas tree block plans, separate from cutting, stock, and sales. */
public final class ChristmasTreeBlockTab implements FlowerFarmTab {

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved Christmas tree block plan");

    private final ChristmasTreeBlockPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<ChristmasTreeBlockStage, JButton> stageTargets =
            new EnumMap<>(ChristmasTreeBlockStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField blockNameField;
    private JTextField treeSpeciesField;
    private JTextField plantingYearField;
    private JTextField acresField;
    private JTextField plantedTreeCountField;
    private JTextField targetHarvestYearField;
    private JTextField nextShearingDateField;
    private JTextField targetHeightField;
    private JTextField cutStartField;
    private JTextField cutEndField;
    private JTextField intendedChannelField;
    private JComboBox<ChristmasTreeBlockStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton replacementButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<ChristmasTreeBlockPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public ChristmasTreeBlockTab(ChristmasTreeBlockPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public ChristmasTreeBlockTab(ChristmasTreeBlockPlanService service, TabHost host,
                                 FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Tree blocks"; }

    @Override public String getDescription() {
        return "Christmas tree block plans beside shared inventory and customer tools";
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
        new SwingWorker<List<ChristmasTreeBlockPlan>, Void>() {
            @Override protected List<ChristmasTreeBlockPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<ChristmasTreeBlockPlan> blocks = get();
                    showBlocks(blocks == null ? List.of() : blocks);
                    showFeedback(hasUnsavedDraft()
                                    ? "Tree blocks refreshed; unsaved draft kept."
                                    : "Tree blocks refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load tree blocks. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load tree blocks. Choose Refresh to retry.",
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
        if (saving) return "A Christmas tree block plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved Christmas tree block plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("christmasTreeBlock.page");
        panel.getAccessibleContext().setAccessibleName("Tree blocks workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Tree blocks",
                "Plan tree species, shearing, and a future cutting season for each block.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("christmasTreeBlock.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("christmasTreeBlock.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("christmasTreeBlock.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "<html>These grower-entered plans do not record actual cut counts, "
                        + "stock, or sales.<br>Licensing, inspection, and export "
                        + "clearance require separate checks.</html>",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildBlockList(), BorderLayout.CENTER);
        listAndTargets.add(buildStageTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("christmasTreeBlock.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildBlockList() {
        tableModel = new DefaultTableModel(new String[]{"Block", "Tree species",
                "Planting year", "Tree count", "Target harvest", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("christmasTreeBlock.blockTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Christmas tree block plans table");
        table.setTransferHandler(new BlockDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectBlock();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New Christmas tree block plan");
        newButton.setName("christmasTreeBlock.new");
        newButton.addActionListener(event -> newBlock());
        replacementButton = FarmUi.secondaryButton("Plan replacement planting");
        replacementButton.setName("christmasTreeBlock.replacement");
        replacementButton.setToolTipText("Carry block, species, acres, target height, "
                + "and channel forward. Enter a new planting and target harvest year.");
        replacementButton.addActionListener(event -> planReplacement());
        refreshButton = FarmUi.secondaryButton("Refresh blocks");
        refreshButton.setName("christmasTreeBlock.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, replacementButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved tree blocks",
                "Select a block to edit or plan replacement planting; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                ChristmasTreeBlockStage.values().length, 8, 0));
        targets.setName("christmasTreeBlock.stageTargets");
        targets.setOpaque(false);
        for (ChristmasTreeBlockStage stage : ChristmasTreeBlockStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("christmasTreeBlock.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved block here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected block to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedBlockTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved block",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        blockNameField = field("Block name", "e.g. North slope", 24);
        treeSpeciesField = field("Tree species", "e.g. Douglas fir or noble fir", 24);
        plantingYearField = field("Planting year", "Optional YYYY", 12);
        plantedTreeCountField = field("Planted tree count", "Optional positive whole number", 12);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        targetHarvestYearField = field("Target harvest year", "Required YYYY", 12);
        intendedChannelField = field("Intended channel", "Optional, e.g. choose and cut", 24);
        nextShearingDateField = field("Next shearing date", "Optional YYYY-MM-DD", 12);
        targetHeightField = field("Target height", "Optional, e.g. 6–7 ft", 12);
        cutStartField = field("Planned cut start", "Optional YYYY-MM-DD", 12);
        cutEndField = field("Planned cut end", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(ChristmasTreeBlockStage.values());
        stageField.setName("christmasTreeBlock.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("christmasTreeBlock.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Block notes");
        FarmUi.initialFocus(blockNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Block name *", blockNameField, blockNameField);
        addField(fields, 0, 1, "Tree species *", treeSpeciesField, treeSpeciesField);
        addField(fields, 1, 0, "Planting year", plantingYearField, plantingYearField);
        addField(fields, 1, 1, "Target harvest year *", targetHarvestYearField,
                targetHarvestYearField);
        addField(fields, 2, 0, "Acres", acresField, acresField);
        addField(fields, 2, 1, "Planted tree count", plantedTreeCountField,
                plantedTreeCountField);
        addField(fields, 3, 0, "Target height", targetHeightField,
                targetHeightField);
        addField(fields, 3, 1, "Next shearing date", nextShearingDateField,
                nextShearingDateField);
        addField(fields, 4, 0, "Planned cut start", cutStartField,
                cutStartField);
        addField(fields, 4, 1, "Planned cut end", cutEndField,
                cutEndField);
        addField(fields, 5, 0, "Intended channel", intendedChannelField,
                intendedChannelField);
        addField(fields, 5, 1, "Planning stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 6, 0, "Block notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("christmasTreeBlock.save");
        saveButton.addActionListener(event -> saveBlock());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("christmasTreeBlock.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a Christmas tree block",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("christmasTreeBlock.feedback");
        feedback.getAccessibleContext().setAccessibleName("Christmas tree block plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Tree block details",
                "Record a grower-entered block plan. Block, species, and target "
                        + "harvest year are required.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("christmasTreeBlock." + name.toLowerCase(Locale.ROOT)
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
        host.setStatus("Opened general " + title
                + "; your Christmas tree block draft remains here.");
    }

    private void showBlocks(List<ChristmasTreeBlockPlan> blocks) {
        lastView = List.copyOf(blocks);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (ChristmasTreeBlockPlan block : lastView) {
                tableModel.addRow(new Object[]{block.getBlockName(),
                        block.getTreeSpecies(),
                        block.getPlantingYear() == null ? "" : block.getPlantingYear(),
                        block.getPlantedTreeCount() == null ? ""
                                : block.getPlantedTreeCount(),
                        block.getTargetHarvestYear(), enumLabel(block.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadBlock);
        }
        updateActions();
    }

    private void selectBlock() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        ChristmasTreeBlockPlan selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadBlock(selected);
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

    private void newBlock() {
        if (!canWrite || saving || loading) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        blockNameField.requestFocusInWindow();
        updateActions();
    }

    private void planReplacement() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved tree block first.", FarmUi.Tone.INFO);
            return;
        }
        ChristmasTreeBlockPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        blockNameField.setText(previous.getBlockName());
        treeSpeciesField.setText(previous.getTreeSpecies());
        acresField.setText(previous.getAcres() == null ? ""
                : previous.getAcres().toPlainString());
        targetHeightField.setText(previous.getTargetHeight());
        intendedChannelField.setText(previous.getIntendedChannel());
        plantingYearField.requestFocusInWindow();
        showFeedback("Replacement planting drafted for " + previous.getBlockName()
                + ". Enter a new planting year and target harvest year.",
                FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        blockNameField.setText("");
        treeSpeciesField.setText("");
        plantingYearField.setText("");
        plantedTreeCountField.setText("");
        acresField.setText("");
        targetHarvestYearField.setText("");
        nextShearingDateField.setText("");
        targetHeightField.setText("");
        cutStartField.setText("");
        cutEndField.setText("");
        intendedChannelField.setText("");
        stageField.setSelectedItem(ChristmasTreeBlockStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a Christmas tree block", FarmUi.Tone.NEUTRAL);
    }

    private void loadBlock(ChristmasTreeBlockPlan block) {
        editingId = block.getId();
        blockNameField.setText(block.getBlockName());
        treeSpeciesField.setText(block.getTreeSpecies());
        plantingYearField.setText(block.getPlantingYear() == null
                ? "" : block.getPlantingYear().toString());
        plantedTreeCountField.setText(block.getPlantedTreeCount() == null
                ? "" : block.getPlantedTreeCount().toString());
        acresField.setText(block.getAcres() == null ? ""
                : block.getAcres().toPlainString());
        targetHarvestYearField.setText(Integer.toString(block.getTargetHarvestYear()));
        nextShearingDateField.setText(block.getNextShearingDate() == null
                ? "" : block.getNextShearingDate().toString());
        targetHeightField.setText(block.getTargetHeight());
        cutStartField.setText(block.getPlannedCutStart() == null
                ? "" : block.getPlannedCutStart().toString());
        cutEndField.setText(block.getPlannedCutEnd() == null
                ? "" : block.getPlannedCutEnd().toString());
        intendedChannelField.setText(block.getIntendedChannel());
        stageField.setSelectedItem(block.getStage());
        notesField.setText(block.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + block.getBlockName() + " · "
                + block.getTreeSpecies(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the tree block save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This Christmas tree block plan has unsaved planning changes. Discard them?",
                "Unsaved Christmas tree block plan draft", JOptionPane.DEFAULT_OPTION,
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
                .findFirst().ifPresentOrElse(this::loadBlock, this::clearForm);
    }

    private void saveBlock() {
        if (!canWrite || saving || loading) return;
        ChristmasTreeBlockPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving Christmas tree block plan…", FarmUi.Tone.INFO);
        new SwingWorker<ChristmasTreeBlockPlan, Void>() {
            @Override protected ChristmasTreeBlockPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    ChristmasTreeBlockPlan saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Christmas tree block plan saved · " + saved.getBlockName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Christmas tree block plan “" + saved.getBlockName() + "” saved.");
                    wrote = true;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showSaveFailure();
                } catch (Exception exception) {
                    showSaveFailure();
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

    private ChristmasTreeBlockPlan readDraft() {
        String blockName = blockNameField.getText().trim();
        if (blockName.isEmpty()) {
            blockNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a block name.");
        }
        String treeSpecies = treeSpeciesField.getText().trim();
        if (treeSpecies.isEmpty()) {
            treeSpeciesField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a tree species.");
        }
        Integer plantingYear = year(plantingYearField, "planting year", false);
        Integer targetHarvestYear = year(targetHarvestYearField, "target harvest year", true);
        if (plantingYear != null && plantingYear > targetHarvestYear) {
            plantingYearField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planting year cannot be after target harvest year.");
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
            if (acres.signum() <= 0 || acres.compareTo(new BigDecimal("9999999999.99")) > 0
                    || acres.stripTrailingZeros().scale() > 2) {
                acresField.requestFocusInWindow();
                throw new IllegalArgumentException(
                        "Enter positive acres with at most two decimal places.");
            }
        }
        Integer plantedTreeCount = positiveInteger(plantedTreeCountField,
                "planted tree count");
        LocalDate nextShearingDate = date(nextShearingDateField, "next shearing date");
        if (nextShearingDate != null && nextShearingDate.getYear() > targetHarvestYear) {
            nextShearingDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Next shearing date cannot be after the target harvest year.");
        }
        LocalDate cutStart = date(cutStartField, "planned cut start");
        LocalDate cutEnd = date(cutEndField, "planned cut end");
        if (cutStart != null && cutStart.getYear() != targetHarvestYear) {
            cutStartField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned cut start must fall in the target harvest year.");
        }
        if (cutEnd != null && cutStart == null) {
            cutEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned cut end requires a planned cut start.");
        }
        if (cutEnd != null && (cutEnd.getYear() != targetHarvestYear
                || cutEnd.isBefore(cutStart))) {
            cutEndField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned cut end must follow the start in the target harvest year.");
        }
        if (nextShearingDate != null && cutStart != null
                && nextShearingDate.isAfter(cutStart)) {
            nextShearingDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Next shearing date cannot be after planned cut start.");
        }
        ChristmasTreeBlockPlan draft = new ChristmasTreeBlockPlan(blockName, treeSpecies,
                plantingYear, acres, plantedTreeCount,
                targetHeightField.getText().trim(), nextShearingDate,
                targetHarvestYear, cutStart, cutEnd,
                intendedChannelField.getText().trim(),
                (ChristmasTreeBlockStage) stageField.getSelectedItem(),
                notesField.getText().trim());
        draft.validate();
        return draft;
    }

    private static Integer year(JTextField field, String label, boolean required) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            if (!required) return null;
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a " + label + ".");
        }
        try {
            int value = Integer.parseInt(text);
            if (value >= 1900 && value <= 2200) return value;
        } catch (NumberFormatException exception) {
            // The same concise feedback covers invalid digits and out-of-range years.
        }
        field.requestFocusInWindow();
        throw new IllegalArgumentException("Enter the " + label + " between 1900 and 2200.");
    }

    private static Integer positiveInteger(JTextField field, String label) {
        String text = field.getText().trim();
        if (text.isEmpty()) return null;
        try {
            int value = Integer.parseInt(text);
            if (value > 0) return value;
        } catch (NumberFormatException exception) {
            // Use the same message for letters, fractions, and overflow.
        }
        field.requestFocusInWindow();
        throw new IllegalArgumentException("Enter the " + label + " as a positive whole number.");
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

    private void moveSelectedBlockTo(ChristmasTreeBlockStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved Christmas tree block plan first.", FarmUi.Tone.INFO);
            return;
        }
        moveBlockToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, ChristmasTreeBlockStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveBlockToStage(Long id, ChristmasTreeBlockStage stage) {
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
        ChristmasTreeBlockPlan source = lastView.stream()
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
        new SwingWorker<ChristmasTreeBlockPlan, Void>() {
            @Override protected ChristmasTreeBlockPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    ChristmasTreeBlockPlan saved = get();
                    if (Objects.equals(editingId, saved.getId())) loadBlock(saved);
                    showFeedback(saved.getBlockName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Christmas tree block plan “" + saved.getBlockName()
                            + "” moved to " + enumLabel(saved.getStage()) + ".");
                    wrote = true;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Stage was not changed. Retry the move.", FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Stage was not changed. Retry the move.", FarmUi.Tone.DANGER);
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

    private final class BlockDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            ChristmasTreeBlockPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new BlockTransferable(
                    new StageDrag(ChristmasTreeBlockTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final ChristmasTreeBlockStage stage;

        private StageDropHandler(ChristmasTreeBlockStage stage) { this.stage = stage; }

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
                moveBlockToStage(drag.plan().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(ChristmasTreeBlockTab source, long revision,
                             ChristmasTreeBlockPlan plan) { }

    private record BlockTransferable(StageDrag drag) implements Transferable {
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

    private void showSaveFailure() {
        showFeedback("Not saved · complete planning draft kept. "
                + "Check the local data store and retry.", FarmUi.Tone.DANGER);
        host.setStatus("Christmas tree block plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, text, tone);
    }

    private FormState formState() {
        return new FormState(blockNameField.getText(), treeSpeciesField.getText(),
                plantingYearField.getText(), plantedTreeCountField.getText(),
                acresField.getText(), targetHarvestYearField.getText(),
                nextShearingDateField.getText(), targetHeightField.getText(),
                cutStartField.getText(), cutEndField.getText(),
                intendedChannelField.getText(),
                (ChristmasTreeBlockStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || blockNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(blockNameField, treeSpeciesField,
                plantingYearField, plantedTreeCountField, acresField, targetHarvestYearField,
                nextShearingDateField, targetHeightField, cutStartField,
                cutEndField, intendedChannelField,
                stageField, notesField)) control.setEnabled(editable);
        newButton.setEnabled(editable);
        replacementButton.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
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

    private record FormState(String blockName, String treeSpecies,
                             String plantingYear, String plantedTreeCount,
                             String acres, String targetHarvestYear,
                             String nextShearingDate, String targetHeight,
                             String cutStart, String cutEnd,
                             String intendedChannel,
                             ChristmasTreeBlockStage stage,
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
