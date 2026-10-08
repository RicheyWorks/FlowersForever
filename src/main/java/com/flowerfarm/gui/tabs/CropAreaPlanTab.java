package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.CropAreaPlan;
import com.flowerfarm.model.CropAreaStage;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.service.CropAreaPlanService;

import javax.swing.BorderFactory;
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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/** Local field or orchard-block planning, without crop-specific production claims. */
public final class CropAreaPlanTab implements FlowerFarmTab {

    private static final DataFlavor PLAN_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.Long",
            "Saved crop area plan");

    private final CropAreaPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final FarmWorkspace workspace;
    private final Copy copy;

    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField areaField;
    private JTextField varietyField;
    private JTextField acresField;
    private JTextField plantingDateField;
    private JTextField harvestDateField;
    private JComboBox<CropAreaStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private final java.util.Map<CropAreaStage, JButton> stageTargets =
            new java.util.EnumMap<>(CropAreaStage.class);
    private JLabel feedback;
    private JLabel accessBadge;
    private final NotebookSaveRecovery saveRecovery = new NotebookSaveRecovery();
    private List<CropAreaPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private boolean selectionAdjusting;
    private boolean refreshQueued;

    public CropAreaPlanTab(CropAreaPlanService service, TabHost host, FarmWorkspace workspace) {
        this(service, host, FarmDialogs.swing(), workspace);
    }

    public CropAreaPlanTab(CropAreaPlanService service, TabHost host,
                           FarmDialogs dialogs, FarmWorkspace workspace) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.copy = switch (workspace) {
            case POTATO_FARM -> new Copy("Field plans", "Potato field plans",
                    "Plan potato fields and review their broad season timing.",
                    "Field name", "Potato variety", "Field notes", "field plan",
                    "This notebook does not grade potatoes, manage storage, or create packout records.");
            case APPLE_ORCHARD -> new Copy("Orchard blocks", "Apple orchard blocks",
                    "Plan orchard blocks and review their broad season timing.",
                    "Orchard block", "Apple variety", "Block notes", "orchard block",
                    "This notebook does not track tree-level yields, grading, cold storage, or packout.");
            default -> throw new IllegalArgumentException(
                    "Crop area plans require a potato farm or apple orchard workspace.");
        };
    }

    @Override
    public String getTabTitle() {
        return copy.tabTitle();
    }

    @Override
    public String getDescription() {
        return "Local " + copy.recordName() + " planning and general record links";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUi();
        }
        return panel;
    }

    @Override
    public void initialize() {
        refreshData();
    }

    @Override
    public void refreshData() {
        if (panel == null) {
            return;
        }
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
        new SwingWorker<List<CropAreaPlan>, Void>() {
            @Override
            protected List<CropAreaPlan> doInBackground() {
                return service.listByWorkspace(workspace);
            }

            @Override
            protected void done() {
                try {
                    List<CropAreaPlan> plans = get();
                    showPlans(plans == null ? List.of() : plans);
                    saveRecovery.refreshed();
                    showFeedback(hasUnsavedDraft()
                                    ? copy.pageTitle() + " refreshed; unsaved draft kept."
                                    : copy.pageTitle() + " refreshed.",
                            FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load plans. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load plans. Choose Refresh to retry.",
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

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        updateActions();
    }

    public boolean hasPendingSave() {
        return saving;
    }

    /** Used by the frame close guard to protect local, unsaved planning work. */
    public String applicationExitWarning() {
        if (saving) {
            return copy.pageTitle() + " has a plan save in progress.";
        }
        return hasUnsavedDraft()
                ? copy.pageTitle() + " has an unsaved " + copy.recordName() + " draft."
                : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("cropArea.page");
        panel.getAccessibleContext().setAccessibleName(copy.pageTitle() + " workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader(copy.pageTitle(), copy.subtitle(), accessBadge),
                BorderLayout.NORTH);

        JButton harvestLink = FarmUi.secondaryButton("Open Harvest Log");
        harvestLink.setName("cropArea.openHarvest");
        harvestLink.addActionListener(event -> openGeneral("Harvest Log"));
        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("cropArea.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("cropArea.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JPanel scope = FarmUi.section("Planning only",
                copy.limit() + " Plans do not update harvest, stock, or orders.",
                FarmUi.toolbar(harvestLink, inventoryLink, ordersLink));

        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(buildPlanList(), BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("cropArea.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        FarmUi.revealFocusedControls(scroll);
        clearForm();
        updateActions();
    }

    private JComponent buildPlanList() {
        tableModel = new DefaultTableModel(new String[]{copy.areaLabel(), copy.varietyLabel(),
                "Acres", "Planting date", "Target harvest", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("cropArea.planTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, copy.pageTitle() + " table");
        table.setTransferHandler(new PlanTransferHandler());
        if (!GraphicsEnvironment.isHeadless()) {
            table.setDragEnabled(true);
        }
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !selectionAdjusting) {
                selectPlan();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New " + copy.recordName());
        newButton.setName("cropArea.new");
        newButton.addActionListener(event -> newPlan());
        refreshButton = FarmUi.secondaryButton("Refresh plans");
        refreshButton.setName("cropArea.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        list.add(buildStageTargets(), BorderLayout.SOUTH);
        return FarmUi.section("Saved " + copy.tabTitle().toLowerCase(java.util.Locale.ROOT),
                "Select a row to review details, or drag it to a planning stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        targets.setOpaque(false);
        targets.setName("cropArea.stageTargets");
        for (CropAreaStage stage : CropAreaStage.values()) {
            JButton target = FarmUi.secondaryButton(stage.getLabel());
            target.setName("cropArea.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved " + copy.recordName()
                    + " here, or select one and click. Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName(
                    "Move selected " + copy.recordName() + " to " + stage.getLabel());
            target.setTransferHandler(new StageTransferHandler(stage));
            target.addActionListener(event -> moveSelectedPlanTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move by stage",
                "Drag a saved row onto a stage, or select a row and click. Stage changes save immediately; full edits remain below.",
                targets);
    }

    private JComponent buildForm() {
        areaField = field(copy.areaLabel(), "e.g. " + (workspace == FarmWorkspace.POTATO_FARM
                ? "Field A" : "North block"), 24);
        varietyField = field(copy.varietyLabel(), "Optional variety", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        plantingDateField = field("Planting date", "Optional YYYY-MM-DD", 12);
        harvestDateField = field("Target harvest date", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(CropAreaStage.values());
        stageField.setName("cropArea.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        notesField = new JTextArea(3, 40);
        notesField.setName("cropArea.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName(copy.notesLabel());
        FarmUi.initialFocus(areaField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, copy.areaLabel() + " *", areaField, areaField);
        addField(fields, 0, 1, copy.varietyLabel(), varietyField, varietyField);
        addField(fields, 1, 0, "Acres", acresField, acresField);
        addField(fields, 1, 1, "Planning stage", stageField, stageField);
        addField(fields, 2, 0, "Planting date", plantingDateField, plantingDateField);
        addField(fields, 2, 1, "Target harvest date", harvestDateField, harvestDateField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 3, 0, copy.notesLabel(), notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("cropArea.save");
        saveButton.addActionListener(event -> savePlan());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("cropArea.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a " + copy.recordName(),
                FarmUi.Tone.NEUTRAL);
        feedback.setName("cropArea.feedback");
        feedback.getAccessibleContext().setAccessibleName("Crop area plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.SOUTH);
        footer.add(feedback, BorderLayout.NORTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Plan details",
                "Local planning fields. Save a new plan or edit the selected one.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("cropArea." + name.toLowerCase(java.util.Locale.ROOT)
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
        host.setStatus("Opened general " + title + "; your " + copy.recordName()
                + " draft remains here.");
    }

    private void showPlans(List<CropAreaPlan> plans) {
        lastView = List.copyOf(plans);
        selectionAdjusting = true;
        try {
            tableModel.setRowCount(0);
            for (CropAreaPlan plan : lastView) {
                tableModel.addRow(new Object[]{plan.getAreaName(), plan.getVariety(),
                        plan.getAcres() == null ? ""
                                : plan.getAcres().stripTrailingZeros().toPlainString(),
                        plan.getPlantingDate() == null ? "" : plan.getPlantingDate(),
                        plan.getTargetHarvestDate() == null ? "" : plan.getTargetHarvestDate(),
                        plan.getStage()});
            }
            restoreSelection();
        } finally {
            selectionAdjusting = false;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadPlan);
        }
        updateActions();
    }

    private void moveSelectedPlanTo(CropAreaStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved " + copy.recordName() + " first.", FarmUi.Tone.INFO);
            return;
        }
        movePlanToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMovePlanToStage(Long id, CropAreaStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft() && id != null
                && stage != null && lastView.stream().anyMatch(plan ->
                id.equals(plan.getId()) && plan.getWorkspace() == workspace);
    }

    private void movePlanToStage(Long id, CropAreaStage stage) {
        if (!canWrite) {
            return;
        }
        if (saving || loading) {
            showFeedback("Wait for the current plan action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved plan.",
                    FarmUi.Tone.WARNING);
            return;
        }
        CropAreaPlan source = lastView.stream().filter(plan ->
                Objects.equals(id, plan.getId()) && plan.getWorkspace() == workspace)
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved plan is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getAreaName() + " is already " + source.getStage() + ".",
                    FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getAreaName() + " to " + stage + "…", FarmUi.Tone.INFO);
        new SwingWorker<CropAreaPlan, Void>() {
            @Override protected CropAreaPlan doInBackground() {
                return service.updateStage(id, workspace, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    CropAreaPlan saved = get();
                    wrote = true;
                    lastView = saveRecovery.remember(lastView, saved, CropAreaPlan::getId);
                    if (Objects.equals(editingId, saved.getId())) {
                        loadPlan(saved);
                    }
                    showPlans(lastView);
                    showFeedback(saved.getAreaName() + " moved to " + saved.getStage() + ".",
                            FarmUi.Tone.SUCCESS);
                    host.setStatus(copy.pageTitle() + " “" + saved.getAreaName()
                            + "” moved to " + saved.getStage() + ".");
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

    private final class PlanTransferHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading) {
                return null;
            }
            Long id = lastView.get(table.convertRowIndexToModel(row)).getId();
            return id == null ? null : new PlanTransferable(id);
        }
    }

    private final class StageTransferHandler extends TransferHandler {
        private final CropAreaStage stage;

        private StageTransferHandler(CropAreaStage stage) { this.stage = stage; }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(PLAN_FLAVOR)) {
                return false;
            }
            try {
                return canMovePlanToStage((Long) support.getTransferable()
                        .getTransferData(PLAN_FLAVOR), stage);
            } catch (UnsupportedFlavorException | IOException | ClassCastException exception) {
                return false;
            }
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                movePlanToStage((Long) support.getTransferable().getTransferData(PLAN_FLAVOR),
                        stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record PlanTransferable(Long id) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{PLAN_FLAVOR};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return PLAN_FLAVOR.equals(flavor);
        }

        @Override public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return id;
        }
    }

    private void selectPlan() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= lastView.size()) {
            return;
        }
        CropAreaPlan selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) {
            return;
        }
        if (!mayLeaveDraft()) {
            selectionAdjusting = true;
            try {
                restoreSelection();
            } finally {
                selectionAdjusting = false;
            }
            return;
        }
        loadPlan(selected);
    }

    private void restoreSelection() {
        table.clearSelection();
        if (editingId == null) {
            return;
        }
        for (int row = 0; row < lastView.size(); row++) {
            if (editingId.equals(lastView.get(row).getId())) {
                table.setRowSelectionInterval(table.convertRowIndexToView(row),
                        table.convertRowIndexToView(row));
                return;
            }
        }
    }

    private void newPlan() {
        if (!mayLeaveDraft()) {
            return;
        }
        selectionAdjusting = true;
        try {
            table.clearSelection();
        } finally {
            selectionAdjusting = false;
        }
        clearForm();
        updateActions();
        areaField.requestFocusInWindow();
    }

    private void clearForm() {
        editingId = null;
        areaField.setText("");
        varietyField.setText("");
        acresField.setText("");
        plantingDateField.setText("");
        harvestDateField.setText("");
        stageField.setSelectedItem(CropAreaStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a " + copy.recordName(), FarmUi.Tone.NEUTRAL);
    }

    private void loadPlan(CropAreaPlan plan) {
        editingId = plan.getId();
        areaField.setText(plan.getAreaName());
        varietyField.setText(plan.getVariety());
        acresField.setText(plan.getAcres() == null ? "" : plan.getAcres().toPlainString());
        plantingDateField.setText(plan.getPlantingDate() == null
                ? "" : plan.getPlantingDate().toString());
        harvestDateField.setText(plan.getTargetHarvestDate() == null
                ? "" : plan.getTargetHarvestDate().toString());
        stageField.setSelectedItem(plan.getStage());
        notesField.setText(plan.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing saved " + copy.recordName() + " “" + plan.getAreaName() + "”",
                FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the plan save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) {
            return true;
        }
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This " + copy.recordName() + " has unsaved planning changes. Discard them?",
                "Unsaved " + copy.recordName() + " draft", JOptionPane.DEFAULT_OPTION,
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
        if (!mayLeaveDraft()) {
            return;
        }
        if (editingId == null) {
            clearForm();
            return;
        }
        lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                .findFirst().ifPresentOrElse(this::loadPlan, this::clearForm);
    }

    private void savePlan() {
        if (!canWrite || saving || loading) {
            return;
        }
        CropAreaPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving " + copy.recordName() + "…", FarmUi.Tone.INFO);
        new SwingWorker<CropAreaPlan, Void>() {
            @Override
            protected CropAreaPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override
            protected void done() {
                boolean wrote = false;
                try {
                    CropAreaPlan saved = get();
                    wrote = true;
                    editingId = saved.getId();
                    lastView = saveRecovery.remember(lastView, saved, CropAreaPlan::getId);
                    cleanForm = formState();
                    showPlans(lastView);
                    showFeedback("Plan saved · " + saved.getAreaName(), FarmUi.Tone.SUCCESS);
                    host.setStatus(copy.pageTitle() + " plan “" + saved.getAreaName() + "” saved.");
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

    private CropAreaPlan readDraft() {
        String area = areaField.getText().trim();
        if (area.isEmpty()) {
            areaField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a " + copy.areaLabel().toLowerCase(
                    java.util.Locale.ROOT) + ".");
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
        }
        LocalDate planting = date(plantingDateField, "planting date");
        LocalDate harvest = date(harvestDateField, "target harvest date");
        if (planting != null && harvest != null && harvest.isBefore(planting)) {
            harvestDateField.requestFocusInWindow();
            throw new IllegalArgumentException("Target harvest cannot precede planting.");
        }
        return new CropAreaPlan(workspace, area, varietyField.getText().trim(), acres,
                planting, harvest, (CropAreaStage) stageField.getSelectedItem(),
                notesField.getText().trim());
    }

    private static LocalDate date(JTextField field, String label) {
        if (field.getText().isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(field.getText().trim());
        } catch (DateTimeParseException exception) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter the " + label + " as YYYY-MM-DD.");
        }
    }

    private void showSavedRefreshFailure() {
        saveRecovery.refreshFailed();
        showFeedback("Save completed. Refresh the notebook to finish updating its display.",
                FarmUi.Tone.WARNING);
    }

    private void showSaveFailure() {
        showFeedback("Not saved · complete planning draft kept. Check the local data store and retry.",
                FarmUi.Tone.DANGER);
        host.setStatus(copy.pageTitle() + " plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) {
            FarmUi.updateStatusBadge(feedback, saveRecovery.feedback(text),
                    saveRecovery.needsRefresh() && tone != FarmUi.Tone.DANGER
                            ? FarmUi.Tone.WARNING : tone);
        }
    }

    private FormState formState() {
        return new FormState(areaField.getText(), varietyField.getText(), acresField.getText(),
                plantingDateField.getText(), harvestDateField.getText(),
                (CropAreaStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || areaField == null) {
            return;
        }
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(areaField, varietyField, acresField,
                plantingDateField, harvestDateField, stageField, notesField)) {
            control.setEnabled(editable);
        }
        newButton.setEnabled(editable);
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

    private record Copy(String tabTitle, String pageTitle, String subtitle,
                        String areaLabel, String varietyLabel, String notesLabel,
                        String recordName, String limit) {
    }

    private record FormState(String area, String variety, String acres,
                             String plantingDate, String harvestDate,
                             CropAreaStage stage, String notes) {
    }

    private static final class WorkspaceBody extends JPanel implements Scrollable {
        private WorkspaceBody() {
            super(new BorderLayout(0, FarmUi.SECTION_GAP));
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
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
