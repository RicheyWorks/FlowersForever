package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.PumpkinPatchPlan;
import com.flowerfarm.model.PumpkinPatchStage;
import com.flowerfarm.model.PumpkinSalesMode;
import com.flowerfarm.service.PumpkinPatchPlanService;

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

/** Editable pumpkin patch plans, separate from harvests and sales. */
public final class PumpkinPatchTab implements FlowerFarmTab {

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved pumpkin patch plan");

    private final PumpkinPatchPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<PumpkinPatchStage, JButton> stageTargets =
            new EnumMap<>(PumpkinPatchStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField patchNameField;
    private JTextField varietyField;
    private JTextField acresField;
    private JTextField plantingDateField;
    private JTextField pickingStartField;
    private JTextField pickingEndField;
    private JComboBox<PumpkinSalesMode> salesModeField;
    private JComboBox<PumpkinPatchStage> stageField;
    private JTextArea visitorAreaNotesField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextSeasonButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private final NotebookSaveRecovery saveRecovery = new NotebookSaveRecovery();
    private List<PumpkinPatchPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public PumpkinPatchTab(PumpkinPatchPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public PumpkinPatchTab(PumpkinPatchPlanService service, TabHost host,
                           FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Pumpkin patches"; }

    @Override public String getDescription() {
        return "Pumpkin patch plans beside shared inventory and customer tools";
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
        new SwingWorker<List<PumpkinPatchPlan>, Void>() {
            @Override protected List<PumpkinPatchPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<PumpkinPatchPlan> patches = get();
                    showPatches(patches == null ? List.of() : patches);
                    saveRecovery.refreshed();
                    showFeedback(hasUnsavedDraft()
                                    ? "Pumpkin patches refreshed; unsaved draft kept."
                                    : "Pumpkin patches refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load pumpkin patches. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load pumpkin patches. Choose Refresh to retry.",
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
        if (saving) return "A pumpkin patch plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved pumpkin patch plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("pumpkinPatch.page");
        panel.getAccessibleContext().setAccessibleName("Pumpkin patches workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Pumpkin patches",
                "Plan planting, pumpkin picking, sales mode, and visitor areas.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("pumpkinPatch.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("pumpkinPatch.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("pumpkinPatch.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "<html>These are grower-entered patch plans. Picking windows and visitor "
                        + "areas are plans, not bookings.<br>This screen does not record "
                        + "completed harvests, stock, or sales.</html>",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildPatchList(), BorderLayout.CENTER);
        listAndTargets.add(buildStageTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("pumpkinPatch.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        FarmUi.revealFocusedControls(scroll);
        clearForm();
        updateActions();
    }

    private JComponent buildPatchList() {
        tableModel = new DefaultTableModel(new String[]{"Patch", "Variety",
                "Acres", "Sales mode", "Picking start", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("pumpkinPatch.patchTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Pumpkin patch plans table");
        table.setTransferHandler(new PatchDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectPatch();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New pumpkin patch");
        newButton.setName("pumpkinPatch.new");
        newButton.addActionListener(event -> newPatch());
        nextSeasonButton = FarmUi.secondaryButton("Plan next season");
        nextSeasonButton.setName("pumpkinPatch.nextSeason");
        nextSeasonButton.setToolTipText("Carry patch, variety, acres, sales mode, and "
                + "visitor area forward; enter new planting and picking dates.");
        nextSeasonButton.addActionListener(event -> planNextSeason());
        refreshButton = FarmUi.secondaryButton("Refresh patches");
        refreshButton.setName("pumpkinPatch.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextSeasonButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved pumpkin patches",
                "Select a patch to edit or draft its next season; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                PumpkinPatchStage.values().length, 8, 0));
        targets.setName("pumpkinPatch.stageTargets");
        targets.setOpaque(false);
        for (PumpkinPatchStage stage : PumpkinPatchStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("pumpkinPatch.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved patch here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected patch to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedPatchTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved patch",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        patchNameField = field("Patch name", "e.g. South field pumpkins", 24);
        varietyField = field("Variety", "Optional, e.g. Howden", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        salesModeField = new JComboBox<>(PumpkinSalesMode.values());
        salesModeField.setName("pumpkinPatch.salesMode");
        salesModeField.getAccessibleContext().setAccessibleName("Planned sales mode");
        salesModeField.setRenderer(enumRenderer());
        plantingDateField = field("Planned planting date", "Optional YYYY-MM-DD", 12);
        pickingStartField = field("Planned picking start", "Optional YYYY-MM-DD", 12);
        pickingEndField = field("Planned picking end", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(PumpkinPatchStage.values());
        stageField.setName("pumpkinPatch.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        visitorAreaNotesField = new JTextArea(3, 40);
        visitorAreaNotesField.setName("pumpkinPatch.visitorAreaNotes");
        visitorAreaNotesField.setLineWrap(true);
        visitorAreaNotesField.setWrapStyleWord(true);
        visitorAreaNotesField.getAccessibleContext().setAccessibleName("Visitor area notes");
        notesField = new JTextArea(3, 40);
        notesField.setName("pumpkinPatch.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Patch notes");
        FarmUi.initialFocus(patchNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Patch name *", patchNameField, patchNameField);
        addField(fields, 0, 1, "Variety", varietyField, varietyField);
        addField(fields, 1, 0, "Acres", acresField, acresField);
        addField(fields, 1, 1, "Planned sales mode", salesModeField,
                salesModeField);
        addField(fields, 2, 0, "Planned planting date", plantingDateField,
                plantingDateField);
        addField(fields, 2, 1, "Planned picking start", pickingStartField,
                pickingStartField);
        addField(fields, 3, 0, "Planned picking end", pickingEndField,
                pickingEndField);
        addField(fields, 3, 1, "Planning stage", stageField, stageField);
        JScrollPane visitorScroll = new JScrollPane(visitorAreaNotesField);
        visitorScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 4, 0, "Visitor area notes", visitorScroll, visitorAreaNotesField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 4, 1, "Patch notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("pumpkinPatch.save");
        saveButton.addActionListener(event -> savePatch());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("pumpkinPatch.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a pumpkin patch",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("pumpkinPatch.feedback");
        feedback.getAccessibleContext().setAccessibleName("Pumpkin patch plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.SOUTH);
        footer.add(feedback, BorderLayout.NORTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Patch plan details",
                "Record a grower-entered pumpkin patch plan. Patch name is required; "
                        + "mode and dates can be adjusted as plans change.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("pumpkinPatch." + name.toLowerCase(Locale.ROOT)
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
                + "; your pumpkin patch plan draft remains here.");
    }

    private void showPatches(List<PumpkinPatchPlan> patches) {
        lastView = List.copyOf(patches);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (PumpkinPatchPlan patch : lastView) {
                tableModel.addRow(new Object[]{patch.getPatchName(), patch.getVariety(),
                        patch.getAcres() == null ? ""
                                : patch.getAcres().stripTrailingZeros().toPlainString(),
                        enumLabel(patch.getSalesMode()),
                        patch.getPlannedPickingStart() == null ? ""
                                : patch.getPlannedPickingStart(), enumLabel(patch.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadPatch);
        }
        updateActions();
    }

    private void selectPatch() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        PumpkinPatchPlan selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadPatch(selected);
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

    private void newPatch() {
        if (!canWrite || saving || loading) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        patchNameField.requestFocusInWindow();
        updateActions();
    }

    private void planNextSeason() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved pumpkin patch first.", FarmUi.Tone.INFO);
            return;
        }
        PumpkinPatchPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        patchNameField.setText(previous.getPatchName());
        varietyField.setText(previous.getVariety());
        acresField.setText(previous.getAcres() == null ? ""
                : previous.getAcres().toPlainString());
        salesModeField.setSelectedItem(previous.getSalesMode());
        visitorAreaNotesField.setText(previous.getVisitorAreaNotes());
        plantingDateField.requestFocusInWindow();
        showFeedback("Next season drafted for " + previous.getPatchName()
                + ". Review the mode and enter new planting and picking dates.",
                FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        patchNameField.setText("");
        varietyField.setText("");
        acresField.setText("");
        salesModeField.setSelectedItem(PumpkinSalesMode.UNDECIDED);
        plantingDateField.setText("");
        pickingStartField.setText("");
        pickingEndField.setText("");
        visitorAreaNotesField.setText("");
        stageField.setSelectedItem(PumpkinPatchStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a pumpkin patch", FarmUi.Tone.NEUTRAL);
    }

    private void loadPatch(PumpkinPatchPlan patch) {
        editingId = patch.getId();
        patchNameField.setText(patch.getPatchName());
        varietyField.setText(patch.getVariety());
        acresField.setText(patch.getAcres() == null ? "" : patch.getAcres().toPlainString());
        salesModeField.setSelectedItem(patch.getSalesMode());
        plantingDateField.setText(patch.getPlannedPlantingDate() == null
                ? "" : patch.getPlannedPlantingDate().toString());
        pickingStartField.setText(patch.getPlannedPickingStart() == null
                ? "" : patch.getPlannedPickingStart().toString());
        pickingEndField.setText(patch.getPlannedPickingEnd() == null
                ? "" : patch.getPlannedPickingEnd().toString());
        visitorAreaNotesField.setText(patch.getVisitorAreaNotes());
        stageField.setSelectedItem(patch.getStage());
        notesField.setText(patch.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + patch.getPatchName() + " · "
                + enumLabel(patch.getSalesMode()), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the patch plan save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This pumpkin patch plan has unsaved planning changes. Discard them?",
                "Unsaved pumpkin patch plan draft", JOptionPane.DEFAULT_OPTION,
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
                .findFirst().ifPresentOrElse(this::loadPatch, this::clearForm);
    }

    private void savePatch() {
        if (!canWrite || saving || loading) return;
        PumpkinPatchPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving pumpkin patch plan…", FarmUi.Tone.INFO);
        new SwingWorker<PumpkinPatchPlan, Void>() {
            @Override protected PumpkinPatchPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    PumpkinPatchPlan saved = get();
                    wrote = true;
                    editingId = saved.getId();
                    lastView = saveRecovery.remember(lastView, saved, PumpkinPatchPlan::getId);
                    cleanForm = formState();
                    showPatches(lastView);
                    showFeedback("Pumpkin patch plan saved · " + saved.getPatchName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Pumpkin patch plan “" + saved.getPatchName() + "” saved.");
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

    private PumpkinPatchPlan readDraft() {
        String patchName = patchNameField.getText().trim();
        if (patchName.isEmpty()) {
            patchNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a patch name.");
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
        LocalDate plantingDate = date(plantingDateField, "planned planting date");
        LocalDate pickingStart = date(pickingStartField, "planned picking start");
        LocalDate pickingEnd = date(pickingEndField, "planned picking end");
        if (plantingDate != null && pickingStart != null
                && plantingDate.isAfter(pickingStart)) {
            plantingDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned planting date cannot be after planned picking start.");
        }
        if (pickingEnd != null && pickingStart == null) {
            pickingEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned picking end requires a start date.");
        }
        if (pickingEnd != null && pickingEnd.isBefore(pickingStart)) {
            pickingEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned picking end cannot precede start.");
        }
        PumpkinPatchPlan draft = new PumpkinPatchPlan(patchName,
                varietyField.getText().trim(), acres,
                plantingDate, pickingStart, pickingEnd,
                (PumpkinSalesMode) salesModeField.getSelectedItem(),
                visitorAreaNotesField.getText().trim(),
                (PumpkinPatchStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedPatchTo(PumpkinPatchStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved pumpkin patch plan first.", FarmUi.Tone.INFO);
            return;
        }
        movePatchToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, PumpkinPatchStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void movePatchToStage(Long id, PumpkinPatchStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current patch action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved patch.",
                    FarmUi.Tone.WARNING);
            return;
        }
        PumpkinPatchPlan source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved patch is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getPatchName() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getPatchName() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<PumpkinPatchPlan, Void>() {
            @Override protected PumpkinPatchPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    PumpkinPatchPlan saved = get();
                    wrote = true;
                    lastView = saveRecovery.remember(lastView, saved, PumpkinPatchPlan::getId);
                    if (Objects.equals(editingId, saved.getId())) loadPatch(saved);
                    showPatches(lastView);
                    showFeedback(saved.getPatchName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Pumpkin patch plan “" + saved.getPatchName()
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

    private final class PatchDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            PumpkinPatchPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new PatchTransferable(
                    new StageDrag(PumpkinPatchTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final PumpkinPatchStage stage;

        private StageDropHandler(PumpkinPatchStage stage) { this.stage = stage; }

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
                movePatchToStage(drag.plan().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(PumpkinPatchTab source, long revision,
                             PumpkinPatchPlan plan) { }

    private record PatchTransferable(StageDrag drag) implements Transferable {
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
        host.setStatus("Pumpkin patch plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, saveRecovery.feedback(text),
                    saveRecovery.needsRefresh() && tone != FarmUi.Tone.DANGER
                            ? FarmUi.Tone.WARNING : tone);
    }

    private FormState formState() {
        return new FormState(patchNameField.getText(), varietyField.getText(),
                acresField.getText(), plantingDateField.getText(),
                (PumpkinSalesMode) salesModeField.getSelectedItem(),
                pickingStartField.getText(), pickingEndField.getText(),
                visitorAreaNotesField.getText(),
                (PumpkinPatchStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || patchNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(patchNameField, varietyField,
                acresField, salesModeField, plantingDateField, pickingStartField,
                pickingEndField, visitorAreaNotesField, stageField, notesField)) {
            control.setEnabled(editable);
        }
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

    private record FormState(String patchName, String variety, String acres,
                             String plantingDate, PumpkinSalesMode salesMode,
                             String pickingStart, String pickingEnd,
                             String visitorAreaNotes, PumpkinPatchStage stage,
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
