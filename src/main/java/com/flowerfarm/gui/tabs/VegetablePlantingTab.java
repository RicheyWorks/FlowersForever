package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.VegetablePlantingPlan;
import com.flowerfarm.model.VegetablePlantingStage;
import com.flowerfarm.service.VegetablePlantingPlanService;

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

/** Editable vegetable planting plans, separate from harvests and sales. */
public final class VegetablePlantingTab implements FlowerFarmTab {

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved vegetable planting plan");

    private final VegetablePlantingPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<VegetablePlantingStage, JButton> stageTargets =
            new EnumMap<>(VegetablePlantingStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField fieldOrBedField;
    private JTextField vegetableCropField;
    private JTextField varietyField;
    private JTextField acresField;
    private JTextField previousCropField;
    private JTextField successionNumberField;
    private JTextField sowingDateField;
    private JTextField transplantDateField;
    private JTextField harvestStartField;
    private JTextField harvestEndField;
    private JTextField intendedChannelField;
    private JComboBox<VegetablePlantingStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextSuccessionButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private final NotebookSaveRecovery saveRecovery = new NotebookSaveRecovery();
    private List<VegetablePlantingPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public VegetablePlantingTab(VegetablePlantingPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public VegetablePlantingTab(VegetablePlantingPlanService service, TabHost host,
                             FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Vegetable plantings"; }

    @Override public String getDescription() {
        return "Vegetable farm plans beside shared inventory and customer tools";
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
        new SwingWorker<List<VegetablePlantingPlan>, Void>() {
            @Override protected List<VegetablePlantingPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<VegetablePlantingPlan> fields = get();
                    showFields(fields == null ? List.of() : fields);
                    saveRecovery.refreshed();
                    showFeedback(hasUnsavedDraft()
                                    ? "Vegetable plantings refreshed; unsaved draft kept."
                                    : "Vegetable plantings refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load plantings. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load plantings. Choose Refresh to retry.",
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
        if (saving) return "A vegetable planting plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved vegetable planting plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("vegetablePlanting.page");
        panel.getAccessibleContext().setAccessibleName("Vegetable plantings workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Vegetable plantings",
                "Plan vegetable beds, sowing and transplanting, and harvest windows.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("vegetablePlanting.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("vegetablePlanting.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("vegetablePlanting.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "These are grower-entered plans. They do not record actual harvests, "
                        + "grading, wash and pack work, storage, stock, or orders.",
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
        scroll.setName("vegetablePlanting.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        FarmUi.revealFocusedControls(scroll);
        clearForm();
        updateActions();
    }

    private JComponent buildFieldList() {
        tableModel = new DefaultTableModel(new String[]{"Field or bed", "Vegetable crop",
                "Variety", "Succession", "Acres", "Harvest start", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("vegetablePlanting.plantingTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Vegetable planting plans table");
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
        newButton = FarmUi.primaryButton("New vegetable planting plan");
        newButton.setName("vegetablePlanting.new");
        newButton.addActionListener(event -> newField());
        nextSuccessionButton = FarmUi.secondaryButton("Plan next succession");
        nextSuccessionButton.setName("vegetablePlanting.nextSuccession");
        nextSuccessionButton.setToolTipText("Carry the field, crop, and channel forward, "
                + "increase the succession number, then enter new planned dates.");
        nextSuccessionButton.addActionListener(event -> planNextSuccession());
        refreshButton = FarmUi.secondaryButton("Refresh plantings");
        refreshButton.setName("vegetablePlanting.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextSuccessionButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved vegetable plantings",
                "Select a planting to edit or plan its next succession; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                VegetablePlantingStage.values().length, 8, 0));
        targets.setName("vegetablePlanting.stageTargets");
        targets.setOpaque(false);
        for (VegetablePlantingStage stage : VegetablePlantingStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("vegetablePlanting.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved planting here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected planting to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedFieldTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved planting",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        fieldOrBedField = field("Field or bed", "e.g. North field or Bed 4", 24);
        vegetableCropField = field("Vegetable crop", "e.g. onions, carrots, or lettuce", 24);
        varietyField = field("Variety", "Optional crop variety", 24);
        previousCropField = field("Previous crop", "Optional rotation note", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        successionNumberField = field("Succession number", "Required positive whole number", 12);
        intendedChannelField = field("Intended channel", "Optional, e.g. fresh or processing", 24);
        sowingDateField = field("Planned sowing date", "Optional YYYY-MM-DD", 12);
        transplantDateField = field("Planned transplant date", "Optional YYYY-MM-DD", 12);
        harvestStartField = field("Planned harvest start", "Required YYYY-MM-DD", 12);
        harvestEndField = field("Planned harvest end", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(VegetablePlantingStage.values());
        stageField.setName("vegetablePlanting.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("vegetablePlanting.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Planting notes");
        FarmUi.initialFocus(fieldOrBedField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Field or bed *", fieldOrBedField, fieldOrBedField);
        addField(fields, 0, 1, "Vegetable crop *", vegetableCropField, vegetableCropField);
        addField(fields, 1, 0, "Variety", varietyField, varietyField);
        addField(fields, 1, 1, "Previous crop", previousCropField,
                previousCropField);
        addField(fields, 2, 0, "Acres", acresField, acresField);
        addField(fields, 2, 1, "Succession number *", successionNumberField,
                successionNumberField);
        addField(fields, 3, 0, "Intended channel", intendedChannelField,
                intendedChannelField);
        addField(fields, 3, 1, "Planned sowing date", sowingDateField, sowingDateField);
        addField(fields, 4, 0, "Planned transplant date", transplantDateField,
                transplantDateField);
        addField(fields, 4, 1, "Planned harvest start *", harvestStartField,
                harvestStartField);
        addField(fields, 5, 0, "Planned harvest end", harvestEndField,
                harvestEndField);
        addField(fields, 5, 1, "Planning stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 6, 0, "Planting notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("vegetablePlanting.save");
        saveButton.addActionListener(event -> saveField());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("vegetablePlanting.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a vegetable planting",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("vegetablePlanting.feedback");
        feedback.getAccessibleContext().setAccessibleName("Vegetable planting plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.SOUTH);
        footer.add(feedback, BorderLayout.NORTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Planting plan details",
                "Record a grower-entered succession plan. A positive succession number "
                        + "and planned harvest start are required.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("vegetablePlanting." + name.toLowerCase(Locale.ROOT)
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
        host.setStatus("Opened general " + title + "; your vegetable planting plan draft remains here.");
    }

    private void showFields(List<VegetablePlantingPlan> fields) {
        lastView = List.copyOf(fields);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (VegetablePlantingPlan field : lastView) {
                tableModel.addRow(new Object[]{field.getFieldOrBed(),
                        field.getVegetableCrop(),
                        field.getVariety(),
                        field.getSuccessionNumber(),
                        field.getAcres() == null ? ""
                                : field.getAcres().stripTrailingZeros().toPlainString(),
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
        VegetablePlantingPlan selected = lastView.get(table.convertRowIndexToModel(row));
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
        fieldOrBedField.requestFocusInWindow();
        updateActions();
    }

    private void planNextSuccession() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved planting first.", FarmUi.Tone.INFO);
            return;
        }
        VegetablePlantingPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null) return;
        if (previous.getSuccessionNumber() == Integer.MAX_VALUE) {
            showFeedback("This succession number cannot be increased. Start a new plan.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        fieldOrBedField.setText(previous.getFieldOrBed());
        vegetableCropField.setText(previous.getVegetableCrop());
        varietyField.setText(previous.getVariety());
        previousCropField.setText(previous.getPreviousCrop());
        acresField.setText(previous.getAcres() == null ? ""
                : previous.getAcres().toPlainString());
        successionNumberField.setText(Integer.toString(previous.getSuccessionNumber() + 1));
        intendedChannelField.setText(previous.getIntendedChannel());
        harvestStartField.requestFocusInWindow();
        showFeedback("Next succession drafted for " + previous.getFieldOrBed()
                + ". Review the crop and enter new sowing, transplant, and harvest dates.",
                FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        fieldOrBedField.setText("");
        vegetableCropField.setText("");
        varietyField.setText("");
        previousCropField.setText("");
        acresField.setText("");
        successionNumberField.setText("1");
        sowingDateField.setText("");
        transplantDateField.setText("");
        harvestStartField.setText("");
        harvestEndField.setText("");
        intendedChannelField.setText("");
        stageField.setSelectedItem(VegetablePlantingStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a vegetable planting", FarmUi.Tone.NEUTRAL);
    }

    private void loadField(VegetablePlantingPlan field) {
        editingId = field.getId();
        fieldOrBedField.setText(field.getFieldOrBed());
        vegetableCropField.setText(field.getVegetableCrop());
        varietyField.setText(field.getVariety());
        previousCropField.setText(field.getPreviousCrop());
        acresField.setText(field.getAcres() == null ? ""
                : field.getAcres().toPlainString());
        successionNumberField.setText(Integer.toString(field.getSuccessionNumber()));
        sowingDateField.setText(field.getPlannedSowingDate() == null
                ? "" : field.getPlannedSowingDate().toString());
        transplantDateField.setText(field.getPlannedTransplantDate() == null
                ? "" : field.getPlannedTransplantDate().toString());
        harvestStartField.setText(field.getPlannedHarvestStart() == null
                ? "" : field.getPlannedHarvestStart().toString());
        harvestEndField.setText(field.getPlannedHarvestEnd() == null
                ? "" : field.getPlannedHarvestEnd().toString());
        intendedChannelField.setText(field.getIntendedChannel());
        stageField.setSelectedItem(field.getStage());
        notesField.setText(field.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + field.getFieldOrBed() + " · "
                + field.getVegetableCrop(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the planting save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This vegetable planting plan has unsaved planning changes. Discard them?",
                "Unsaved vegetable planting plan draft", JOptionPane.DEFAULT_OPTION,
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
        VegetablePlantingPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving vegetable planting plan…", FarmUi.Tone.INFO);
        new SwingWorker<VegetablePlantingPlan, Void>() {
            @Override protected VegetablePlantingPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    VegetablePlantingPlan saved = get();
                    wrote = true;
                    editingId = saved.getId();
                    lastView = saveRecovery.remember(lastView, saved, VegetablePlantingPlan::getId);
                    cleanForm = formState();
                    showFields(lastView);
                    showFeedback("Vegetable planting plan saved · " + saved.getFieldOrBed(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Vegetable planting plan “" + saved.getFieldOrBed() + "” saved.");
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

    private VegetablePlantingPlan readDraft() {
        String fieldOrBed = fieldOrBedField.getText().trim();
        if (fieldOrBed.isEmpty()) {
            fieldOrBedField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a field or bed name.");
        }
        String crop = vegetableCropField.getText().trim();
        if (crop.isEmpty()) {
            vegetableCropField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a vegetable crop.");
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
        int successionNumber;
        try {
            successionNumber = Integer.parseInt(successionNumberField.getText().trim());
        } catch (NumberFormatException exception) {
            successionNumberField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter succession number as a positive whole number.");
        }
        if (successionNumber <= 0) {
            successionNumberField.requestFocusInWindow();
            throw new IllegalArgumentException("Succession number must be greater than zero.");
        }
        LocalDate sowingDate = date(sowingDateField, "planned sowing date");
        LocalDate transplantDate = date(transplantDateField, "planned transplant date");
        LocalDate harvestStart = date(harvestStartField, "planned harvest start");
        if (harvestStart == null) {
            harvestStartField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a planned harvest start date.");
        }
        if (sowingDate != null && sowingDate.isAfter(harvestStart)) {
            sowingDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned sowing date cannot be after planned harvest start.");
        }
        if (transplantDate != null && transplantDate.isAfter(harvestStart)) {
            transplantDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned transplant date cannot be after planned harvest start.");
        }
        if (sowingDate != null && transplantDate != null
                && sowingDate.isAfter(transplantDate)) {
            sowingDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned sowing date cannot be after planned transplant date.");
        }
        LocalDate harvestEnd = date(harvestEndField, "planned harvest end");
        if (harvestEnd != null && harvestEnd.isBefore(harvestStart)) {
            harvestEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest end cannot precede start.");
        }
        VegetablePlantingPlan draft = new VegetablePlantingPlan(fieldOrBed, crop,
                varietyField.getText().trim(), previousCropField.getText().trim(),
                acres, successionNumber, sowingDate, transplantDate,
                harvestStart, harvestEnd,
                intendedChannelField.getText().trim(),
                (VegetablePlantingStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedFieldTo(VegetablePlantingStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved vegetable planting plan first.", FarmUi.Tone.INFO);
            return;
        }
        moveFieldToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, VegetablePlantingStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveFieldToStage(Long id, VegetablePlantingStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current planting action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved planting.",
                    FarmUi.Tone.WARNING);
            return;
        }
        VegetablePlantingPlan source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved planting is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getFieldOrBed() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getFieldOrBed() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<VegetablePlantingPlan, Void>() {
            @Override protected VegetablePlantingPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    VegetablePlantingPlan saved = get();
                    wrote = true;
                    lastView = saveRecovery.remember(lastView, saved, VegetablePlantingPlan::getId);
                    if (Objects.equals(editingId, saved.getId())) loadField(saved);
                    showFields(lastView);
                    showFeedback(saved.getFieldOrBed() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Vegetable planting plan “" + saved.getFieldOrBed()
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
            VegetablePlantingPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new FieldTransferable(
                    new StageDrag(VegetablePlantingTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final VegetablePlantingStage stage;

        private StageDropHandler(VegetablePlantingStage stage) { this.stage = stage; }

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

    private record StageDrag(VegetablePlantingTab source, long revision,
                             VegetablePlantingPlan plan) { }

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
        host.setStatus("Vegetable planting plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, saveRecovery.feedback(text),
                    saveRecovery.needsRefresh() && tone != FarmUi.Tone.DANGER
                            ? FarmUi.Tone.WARNING : tone);
    }

    private FormState formState() {
        return new FormState(fieldOrBedField.getText(), vegetableCropField.getText(),
                varietyField.getText(), previousCropField.getText(),
                acresField.getText(), successionNumberField.getText(),
                sowingDateField.getText(), transplantDateField.getText(),
                harvestStartField.getText(), harvestEndField.getText(),
                intendedChannelField.getText(),
                (VegetablePlantingStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || fieldOrBedField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(fieldOrBedField, vegetableCropField,
                varietyField, previousCropField, acresField, successionNumberField,
                sowingDateField, transplantDateField, harvestStartField,
                harvestEndField, intendedChannelField,
                stageField, notesField)) control.setEnabled(editable);
        newButton.setEnabled(editable);
        nextSuccessionButton.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
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

    private record FormState(String fieldOrBed, String vegetableCrop,
                             String variety, String previousCrop,
                             String acres, String successionNumber,
                             String sowingDate, String transplantDate,
                             String harvestStart, String harvestEnd,
                             String intendedChannel,
                             VegetablePlantingStage stage,
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
