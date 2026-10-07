package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.MushroomBatchPlan;
import com.flowerfarm.model.MushroomBatchStage;
import com.flowerfarm.service.MushroomBatchPlanService;

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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Editable mushroom batch plans, separate from harvests and sales. */
public final class MushroomBatchTab implements FlowerFarmTab {

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved mushroom batch plan");

    private final MushroomBatchPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<MushroomBatchStage, JButton> stageTargets =
            new EnumMap<>(MushroomBatchStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField batchNameField;
    private JTextField mushroomSpeciesField;
    private JTextField growingSiteField;
    private JTextField growingMethodField;
    private JTextField substrateField;
    private JTextField inoculationDateField;
    private JTextField fruitingStartField;
    private JTextField fruitingEndField;
    private JTextField intendedChannelField;
    private JComboBox<MushroomBatchStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextBatchButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<MushroomBatchPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public MushroomBatchTab(MushroomBatchPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public MushroomBatchTab(MushroomBatchPlanService service, TabHost host,
                            FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Mushroom batches"; }

    @Override public String getDescription() {
        return "Mushroom batch plans beside shared inventory and customer tools";
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
        new SwingWorker<List<MushroomBatchPlan>, Void>() {
            @Override protected List<MushroomBatchPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<MushroomBatchPlan> batches = get();
                    showBatches(batches == null ? List.of() : batches);
                    showFeedback(hasUnsavedDraft()
                                    ? "Mushroom batches refreshed; unsaved draft kept."
                                    : "Mushroom batches refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load mushroom batches. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load mushroom batches. Choose Refresh to retry.",
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
        if (saving) return "A mushroom batch plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved mushroom batch plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("mushroomBatch.page");
        panel.getAccessibleContext().setAccessibleName("Mushroom batches workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Mushroom batches",
                "Plan inoculation, growing area, and fruiting windows by batch.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("mushroomBatch.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("mushroomBatch.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("mushroomBatch.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "<html>These are grower-entered batch plans. An inoculation or fruiting "
                        + "window is not a completed event.<br>This screen does not record "
                        + "actual yields, stock, or sales.</html>",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildBatchList(), BorderLayout.CENTER);
        listAndTargets.add(buildStageTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("mushroomBatch.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildBatchList() {
        tableModel = new DefaultTableModel(new String[]{"Batch", "Species",
                "Growing area", "Inoculation", "Fruiting start", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("mushroomBatch.batchTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Mushroom batch plans table");
        table.setTransferHandler(new BatchDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectBatch();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New mushroom batch");
        newButton.setName("mushroomBatch.new");
        newButton.addActionListener(event -> newBatch());
        nextBatchButton = FarmUi.secondaryButton("Plan next batch");
        nextBatchButton.setName("mushroomBatch.nextBatch");
        nextBatchButton.setToolTipText("Carry species, method, substrate, area, and channel "
                + "forward, then enter a new batch name and planned dates.");
        nextBatchButton.addActionListener(event -> planNextBatch());
        refreshButton = FarmUi.secondaryButton("Refresh batches");
        refreshButton.setName("mushroomBatch.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextBatchButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved mushroom batches",
                "Select a batch to edit or draft its next batch; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                MushroomBatchStage.values().length, 8, 0));
        targets.setName("mushroomBatch.stageTargets");
        targets.setOpaque(false);
        for (MushroomBatchStage stage : MushroomBatchStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("mushroomBatch.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved batch here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected batch to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedBatchTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved batch",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        batchNameField = field("Batch name", "e.g. Spring oyster mushroom batch", 24);
        mushroomSpeciesField = field("Mushroom species", "e.g. oyster or shiitake", 24);
        growingMethodField = field("Growing method", "Optional, e.g. bags or logs", 24);
        substrateField = field("Substrate", "Optional, e.g. straw or hardwood", 24);
        growingSiteField = field("Growing area", "Optional, e.g. fruiting room 2", 24);
        intendedChannelField = field("Intended channel", "Optional market or buyer note", 24);
        inoculationDateField = field("Planned inoculation date", "Optional YYYY-MM-DD", 12);
        fruitingStartField = field("Planned fruiting start", "Optional YYYY-MM-DD", 12);
        fruitingEndField = field("Planned fruiting end", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(MushroomBatchStage.values());
        stageField.setName("mushroomBatch.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("mushroomBatch.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Batch notes");
        FarmUi.initialFocus(batchNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Batch name *", batchNameField, batchNameField);
        addField(fields, 0, 1, "Mushroom species *", mushroomSpeciesField,
                mushroomSpeciesField);
        addField(fields, 1, 0, "Growing method", growingMethodField, growingMethodField);
        addField(fields, 1, 1, "Substrate", substrateField, substrateField);
        addField(fields, 2, 0, "Growing area", growingSiteField, growingSiteField);
        addField(fields, 2, 1, "Intended channel", intendedChannelField,
                intendedChannelField);
        addField(fields, 3, 0, "Planned inoculation date", inoculationDateField,
                inoculationDateField);
        addField(fields, 3, 1, "Planned fruiting start", fruitingStartField,
                fruitingStartField);
        addField(fields, 4, 0, "Planned fruiting end", fruitingEndField,
                fruitingEndField);
        addField(fields, 4, 1, "Planning stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 5, 0, "Batch notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("mushroomBatch.save");
        saveButton.addActionListener(event -> saveBatch());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("mushroomBatch.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a mushroom batch",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("mushroomBatch.feedback");
        feedback.getAccessibleContext().setAccessibleName("Mushroom batch plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Batch plan details",
                "Record a grower-entered batch plan. Batch name and mushroom species "
                        + "are required.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("mushroomBatch." + name.toLowerCase(Locale.ROOT)
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
                + "; your mushroom batch plan draft remains here.");
    }

    private void showBatches(List<MushroomBatchPlan> batches) {
        lastView = List.copyOf(batches);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (MushroomBatchPlan batch : lastView) {
                tableModel.addRow(new Object[]{batch.getBatchName(),
                        batch.getMushroomSpecies(), batch.getGrowingSite(),
                        batch.getPlannedInoculationDate() == null ? ""
                                : batch.getPlannedInoculationDate(),
                        batch.getPlannedFruitingStart() == null ? ""
                                : batch.getPlannedFruitingStart(),
                        enumLabel(batch.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadBatch);
        }
        updateActions();
    }

    private void selectBatch() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        MushroomBatchPlan selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadBatch(selected);
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

    private void newBatch() {
        if (!canWrite || saving || loading) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        batchNameField.requestFocusInWindow();
        updateActions();
    }

    private void planNextBatch() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved mushroom batch first.", FarmUi.Tone.INFO);
            return;
        }
        MushroomBatchPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        mushroomSpeciesField.setText(previous.getMushroomSpecies());
        growingSiteField.setText(previous.getGrowingSite());
        growingMethodField.setText(previous.getGrowingMethod());
        substrateField.setText(previous.getSubstrate());
        intendedChannelField.setText(previous.getIntendedChannel());
        batchNameField.requestFocusInWindow();
        showFeedback("Next batch drafted from " + previous.getBatchName()
                + ". Enter a new batch name and planned inoculation and fruiting dates.",
                FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        batchNameField.setText("");
        mushroomSpeciesField.setText("");
        growingSiteField.setText("");
        growingMethodField.setText("");
        substrateField.setText("");
        inoculationDateField.setText("");
        fruitingStartField.setText("");
        fruitingEndField.setText("");
        intendedChannelField.setText("");
        stageField.setSelectedItem(MushroomBatchStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a mushroom batch", FarmUi.Tone.NEUTRAL);
    }

    private void loadBatch(MushroomBatchPlan batch) {
        editingId = batch.getId();
        batchNameField.setText(batch.getBatchName());
        mushroomSpeciesField.setText(batch.getMushroomSpecies());
        growingSiteField.setText(batch.getGrowingSite());
        growingMethodField.setText(batch.getGrowingMethod());
        substrateField.setText(batch.getSubstrate());
        inoculationDateField.setText(batch.getPlannedInoculationDate() == null
                ? "" : batch.getPlannedInoculationDate().toString());
        fruitingStartField.setText(batch.getPlannedFruitingStart() == null
                ? "" : batch.getPlannedFruitingStart().toString());
        fruitingEndField.setText(batch.getPlannedFruitingEnd() == null
                ? "" : batch.getPlannedFruitingEnd().toString());
        intendedChannelField.setText(batch.getIntendedChannel());
        stageField.setSelectedItem(batch.getStage());
        notesField.setText(batch.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + batch.getBatchName() + " · "
                + batch.getMushroomSpecies(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the batch save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This mushroom batch plan has unsaved planning changes. Discard them?",
                "Unsaved mushroom batch plan draft", JOptionPane.DEFAULT_OPTION,
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
                .findFirst().ifPresentOrElse(this::loadBatch, this::clearForm);
    }

    private void saveBatch() {
        if (!canWrite || saving || loading) return;
        MushroomBatchPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving mushroom batch plan…", FarmUi.Tone.INFO);
        new SwingWorker<MushroomBatchPlan, Void>() {
            @Override protected MushroomBatchPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    MushroomBatchPlan saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Mushroom batch plan saved · " + saved.getBatchName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Mushroom batch plan “" + saved.getBatchName() + "” saved.");
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

    private MushroomBatchPlan readDraft() {
        String batchName = batchNameField.getText().trim();
        if (batchName.isEmpty()) {
            batchNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a batch name.");
        }
        String species = mushroomSpeciesField.getText().trim();
        if (species.isEmpty()) {
            mushroomSpeciesField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a mushroom species.");
        }
        LocalDate inoculationDate = date(inoculationDateField, "planned inoculation date");
        LocalDate fruitingStart = date(fruitingStartField, "planned fruiting start");
        if (inoculationDate != null && fruitingStart != null
                && inoculationDate.isAfter(fruitingStart)) {
            inoculationDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned inoculation date cannot be after planned fruiting start.");
        }
        LocalDate fruitingEnd = date(fruitingEndField, "planned fruiting end");
        if (fruitingEnd != null && fruitingStart == null) {
            fruitingEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned fruiting end requires a start date.");
        }
        if (fruitingEnd != null && fruitingEnd.isBefore(fruitingStart)) {
            fruitingEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned fruiting end cannot precede start.");
        }
        MushroomBatchPlan draft = new MushroomBatchPlan(batchName, species,
                growingMethodField.getText().trim(), substrateField.getText().trim(),
                growingSiteField.getText().trim(), inoculationDate,
                fruitingStart, fruitingEnd, intendedChannelField.getText().trim(),
                (MushroomBatchStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedBatchTo(MushroomBatchStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved mushroom batch plan first.", FarmUi.Tone.INFO);
            return;
        }
        moveBatchToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, MushroomBatchStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveBatchToStage(Long id, MushroomBatchStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current batch action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved batch.",
                    FarmUi.Tone.WARNING);
            return;
        }
        MushroomBatchPlan source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved batch is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getBatchName() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getBatchName() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<MushroomBatchPlan, Void>() {
            @Override protected MushroomBatchPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    MushroomBatchPlan saved = get();
                    if (Objects.equals(editingId, saved.getId())) loadBatch(saved);
                    showFeedback(saved.getBatchName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Mushroom batch plan “" + saved.getBatchName()
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

    private final class BatchDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            MushroomBatchPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new BatchTransferable(
                    new StageDrag(MushroomBatchTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final MushroomBatchStage stage;

        private StageDropHandler(MushroomBatchStage stage) { this.stage = stage; }

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
                moveBatchToStage(drag.plan().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(MushroomBatchTab source, long revision,
                             MushroomBatchPlan plan) { }

    private record BatchTransferable(StageDrag drag) implements Transferable {
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
        host.setStatus("Mushroom batch plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, text, tone);
    }

    private FormState formState() {
        return new FormState(batchNameField.getText(), mushroomSpeciesField.getText(),
                growingSiteField.getText(), growingMethodField.getText(),
                substrateField.getText(),
                inoculationDateField.getText(), fruitingStartField.getText(),
                fruitingEndField.getText(), intendedChannelField.getText(),
                (MushroomBatchStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || batchNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(batchNameField, mushroomSpeciesField,
                growingSiteField, growingMethodField, substrateField, inoculationDateField,
                fruitingStartField, fruitingEndField, intendedChannelField,
                stageField, notesField)) control.setEnabled(editable);
        newButton.setEnabled(editable);
        nextBatchButton.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
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

    private record FormState(String batchName, String mushroomSpecies,
                             String growingSite, String growingMethod, String substrate,
                             String inoculationDate, String fruitingStart,
                             String fruitingEnd, String intendedChannel,
                             MushroomBatchStage stage,
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
