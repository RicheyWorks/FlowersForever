package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.SeedFieldPlan;
import com.flowerfarm.model.SeedFieldStage;
import com.flowerfarm.service.SeedFieldPlanService;

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

/** Editable seed-field season plans, separate from harvested seed and sales. */
public final class SeedFieldPlanTab implements FlowerFarmTab {

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved seed field plan");

    private final SeedFieldPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<SeedFieldStage, JButton> stageTargets =
            new EnumMap<>(SeedFieldStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField fieldNameField;
    private JTextField cropField;
    private JTextField varietyField;
    private JTextField acresField;
    private JTextField previousCropField;
    private JTextField sowingDateField;
    private JTextField floweringStartField;
    private JTextField floweringEndField;
    private JTextField harvestStartField;
    private JTextField harvestEndField;
    private JTextField intendedUseField;
    private JComboBox<SeedFieldStage> stageField;
    private JTextArea isolationNotesField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextSeasonButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<SeedFieldPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public SeedFieldPlanTab(SeedFieldPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public SeedFieldPlanTab(SeedFieldPlanService service, TabHost host,
                             FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Seed fields"; }

    @Override public String getDescription() {
        return "Seed field season and flowering plans with shared record links";
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
        new SwingWorker<List<SeedFieldPlan>, Void>() {
            @Override protected List<SeedFieldPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<SeedFieldPlan> fields = get();
                    showFields(fields == null ? List.of() : fields);
                    showFeedback(hasUnsavedDraft()
                                    ? "Seed fields refreshed; unsaved draft kept."
                                    : "Seed fields refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load fields. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load fields. Choose Refresh to retry.",
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
        if (saving) return "A seed field plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved seed field plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("seedField.page");
        panel.getAccessibleContext().setAccessibleName("Seed fields workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Seed fields",
                "Plan annual or established perennial seed fields, flowering, and harvest windows.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("seedField.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("seedField.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("seedField.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "Field plans do not record actual seed yields, create lots, stock, or orders. "
                        + "Isolation notes are manual; this screen does not check distances or certify seed.",
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
        scroll.setName("seedField.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildFieldList() {
        tableModel = new DefaultTableModel(new String[]{"Field", "Seed crop",
                "Variety", "Acres", "Previous crop", "Harvest start", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("seedField.fieldTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Seed field plans table");
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
        newButton = FarmUi.primaryButton("New seed field plan");
        newButton.setName("seedField.new");
        newButton.addActionListener(event -> newField());
        nextSeasonButton = FarmUi.secondaryButton("Plan next season");
        nextSeasonButton.setName("seedField.nextSeason");
        nextSeasonButton.setToolTipText("Start another season for the same field and crop. Review the field history and enter new dates and isolation notes before saving.");
        nextSeasonButton.addActionListener(event -> planNextSeason());
        refreshButton = FarmUi.secondaryButton("Refresh fields");
        refreshButton.setName("seedField.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextSeasonButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved seed fields",
                "Select a field to edit or plan its next season; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                SeedFieldStage.values().length, 8, 0));
        targets.setName("seedField.stageTargets");
        targets.setOpaque(false);
        for (SeedFieldStage stage : SeedFieldStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("seedField.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved field here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected field to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedFieldTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved field",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        fieldNameField = field("Field name", "e.g. North field", 24);
        cropField = field("Seed crop", "e.g. clover or vegetable seed", 24);
        varietyField = field("Variety", "Optional cultivar or variety", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        previousCropField = field("Previous crop", "Optional field history", 24);
        intendedUseField = field("Intended use", "Optional, e.g. planting seed", 24);
        sowingDateField = field("Planned sowing date", "Optional YYYY-MM-DD", 12);
        floweringStartField = field("Planned flowering start", "Optional YYYY-MM-DD", 12);
        floweringEndField = field("Planned flowering end", "Optional YYYY-MM-DD", 12);
        harvestStartField = field("Planned harvest start", "Required YYYY-MM-DD", 12);
        harvestEndField = field("Planned harvest end", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(SeedFieldStage.values());
        stageField.setName("seedField.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        isolationNotesField = new JTextArea(3, 40);
        isolationNotesField.setName("seedField.isolationNotes");
        isolationNotesField.setLineWrap(true);
        isolationNotesField.setWrapStyleWord(true);
        isolationNotesField.getAccessibleContext().setAccessibleName("Isolation notes");
        notesField = new JTextArea(3, 40);
        notesField.setName("seedField.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Field notes");
        FarmUi.initialFocus(fieldNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Field name *", fieldNameField, fieldNameField);
        addField(fields, 0, 1, "Seed crop *", cropField, cropField);
        addField(fields, 1, 0, "Variety", varietyField, varietyField);
        addField(fields, 1, 1, "Acres", acresField, acresField);
        addField(fields, 2, 0, "Previous crop", previousCropField, previousCropField);
        addField(fields, 2, 1, "Intended use", intendedUseField, intendedUseField);
        addField(fields, 3, 0, "Planned sowing date", sowingDateField, sowingDateField);
        addField(fields, 3, 1, "Planned flowering start", floweringStartField,
                floweringStartField);
        addField(fields, 4, 0, "Planned flowering end", floweringEndField,
                floweringEndField);
        addField(fields, 4, 1, "Planned harvest start *", harvestStartField,
                harvestStartField);
        addField(fields, 5, 0, "Planned harvest end", harvestEndField,
                harvestEndField);
        addField(fields, 5, 1, "Planning stage", stageField, stageField);
        JScrollPane isolationScroll = new JScrollPane(isolationNotesField);
        isolationScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 6, 0, "Isolation notes", isolationScroll, isolationNotesField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 6, 1, "Field notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("seedField.save");
        saveButton.addActionListener(event -> saveField());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("seedField.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a seed field season",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("seedField.feedback");
        feedback.getAccessibleContext().setAccessibleName("Seed field plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Season plan details",
                "Sowing is optional for established perennial fields. Record isolation observations manually; a harvest start is required.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("seedField." + name.toLowerCase(Locale.ROOT)
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
        host.setStatus("Opened general " + title + "; your seed field plan draft remains here.");
    }

    private void showFields(List<SeedFieldPlan> fields) {
        lastView = List.copyOf(fields);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (SeedFieldPlan field : lastView) {
                tableModel.addRow(new Object[]{field.getFieldName(),
                        field.getSeedCrop(),
                        field.getVariety(),
                        field.getAcres() == null ? ""
                                : field.getAcres().stripTrailingZeros().toPlainString(),
                        field.getPreviousCrop(),
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
        SeedFieldPlan selected = lastView.get(table.convertRowIndexToModel(row));
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
        fieldNameField.requestFocusInWindow();
        updateActions();
    }

    private void planNextSeason() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved field first.", FarmUi.Tone.INFO);
            return;
        }
        SeedFieldPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null || !mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        fieldNameField.setText(previous.getFieldName());
        cropField.setText(previous.getSeedCrop());
        varietyField.setText(previous.getVariety());
        acresField.setText(previous.getAcres() == null ? ""
                : previous.getAcres().toPlainString());
        harvestStartField.requestFocusInWindow();
        showFeedback("Next season drafted for " + previous.getFieldName()
                + ". Review the crop and field history, then enter the new harvest window and isolation observations.",
                FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        fieldNameField.setText("");
        cropField.setText("");
        varietyField.setText("");
        acresField.setText("");
        previousCropField.setText("");
        sowingDateField.setText("");
        floweringStartField.setText("");
        floweringEndField.setText("");
        harvestStartField.setText("");
        harvestEndField.setText("");
        intendedUseField.setText("");
        stageField.setSelectedItem(SeedFieldStage.PLANNED);
        isolationNotesField.setText("");
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a seed field season", FarmUi.Tone.NEUTRAL);
    }

    private void loadField(SeedFieldPlan field) {
        editingId = field.getId();
        fieldNameField.setText(field.getFieldName());
        cropField.setText(field.getSeedCrop());
        varietyField.setText(field.getVariety());
        acresField.setText(field.getAcres() == null ? ""
                : field.getAcres().toPlainString());
        previousCropField.setText(field.getPreviousCrop());
        sowingDateField.setText(field.getPlannedSowingDate() == null
                ? "" : field.getPlannedSowingDate().toString());
        floweringStartField.setText(field.getPlannedFloweringStart() == null
                ? "" : field.getPlannedFloweringStart().toString());
        floweringEndField.setText(field.getPlannedFloweringEnd() == null
                ? "" : field.getPlannedFloweringEnd().toString());
        harvestStartField.setText(field.getPlannedHarvestStart() == null
                ? "" : field.getPlannedHarvestStart().toString());
        harvestEndField.setText(field.getPlannedHarvestEnd() == null
                ? "" : field.getPlannedHarvestEnd().toString());
        intendedUseField.setText(field.getIntendedUse());
        stageField.setSelectedItem(field.getStage());
        isolationNotesField.setText(field.getIsolationNotes());
        notesField.setText(field.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + field.getFieldName() + " · "
                + field.getSeedCrop(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the field save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This seed field plan has unsaved planning changes. Discard them?",
                "Unsaved seed field plan draft", JOptionPane.DEFAULT_OPTION,
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
        SeedFieldPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving seed field plan…", FarmUi.Tone.INFO);
        new SwingWorker<SeedFieldPlan, Void>() {
            @Override protected SeedFieldPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    SeedFieldPlan saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Field plan saved · " + saved.getFieldName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Seed field plan “" + saved.getFieldName() + "” saved.");
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

    private SeedFieldPlan readDraft() {
        String fieldName = fieldNameField.getText().trim();
        if (fieldName.isEmpty()) {
            fieldNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a field name.");
        }
        String crop = cropField.getText().trim();
        if (crop.isEmpty()) {
            cropField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a seed crop.");
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
        LocalDate sowingDate = date(sowingDateField, "planned sowing date");
        LocalDate floweringStart = date(floweringStartField, "planned flowering start");
        LocalDate floweringEnd = date(floweringEndField, "planned flowering end");
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
        if (floweringStart != null && floweringStart.isAfter(harvestStart)) {
            floweringStartField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned flowering start cannot be after planned harvest start.");
        }
        if (floweringEnd != null && floweringEnd.isAfter(harvestStart)) {
            floweringEndField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned flowering end cannot be after planned harvest start.");
        }
        if (floweringStart != null && floweringEnd != null
                && floweringEnd.isBefore(floweringStart)) {
            floweringEndField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned flowering end cannot precede start.");
        }
        if (sowingDate != null && floweringStart != null
                && floweringStart.isBefore(sowingDate)) {
            floweringStartField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned flowering start cannot be before planned sowing date.");
        }
        if (sowingDate != null && floweringEnd != null
                && floweringEnd.isBefore(sowingDate)) {
            floweringEndField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned flowering end cannot be before planned sowing date.");
        }
        LocalDate harvestEnd = date(harvestEndField, "planned harvest end");
        if (harvestEnd != null && harvestEnd.isBefore(harvestStart)) {
            harvestEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest end cannot precede start.");
        }
        SeedFieldPlan draft = new SeedFieldPlan(fieldName, crop,
                varietyField.getText().trim(), acres, previousCropField.getText().trim(),
                sowingDate, floweringStart, floweringEnd, harvestStart, harvestEnd,
                intendedUseField.getText().trim(), isolationNotesField.getText().trim(),
                (SeedFieldStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedFieldTo(SeedFieldStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved seed field plan first.", FarmUi.Tone.INFO);
            return;
        }
        moveFieldToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, SeedFieldStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveFieldToStage(Long id, SeedFieldStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current field action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved field.",
                    FarmUi.Tone.WARNING);
            return;
        }
        SeedFieldPlan source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved field is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getFieldName() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getFieldName() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<SeedFieldPlan, Void>() {
            @Override protected SeedFieldPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    SeedFieldPlan saved = get();
                    if (Objects.equals(editingId, saved.getId())) loadField(saved);
                    showFeedback(saved.getFieldName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Seed field plan “" + saved.getFieldName()
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

    private final class FieldDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            SeedFieldPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new FieldTransferable(
                    new StageDrag(SeedFieldPlanTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final SeedFieldStage stage;

        private StageDropHandler(SeedFieldStage stage) { this.stage = stage; }

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

    private record StageDrag(SeedFieldPlanTab source, long revision,
                             SeedFieldPlan plan) { }

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

    private void showSaveFailure() {
        showFeedback("Not saved · complete planning draft kept. "
                + "Check the local data store and retry.", FarmUi.Tone.DANGER);
        host.setStatus("Seed field plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, text, tone);
    }

    private FormState formState() {
        return new FormState(fieldNameField.getText(), cropField.getText(),
                varietyField.getText(), acresField.getText(),
                previousCropField.getText(), sowingDateField.getText(),
                floweringStartField.getText(), floweringEndField.getText(),
                harvestStartField.getText(), harvestEndField.getText(),
                intendedUseField.getText(), isolationNotesField.getText(),
                (SeedFieldStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || fieldNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(fieldNameField, cropField,
                varietyField, acresField, previousCropField, sowingDateField,
                floweringStartField, floweringEndField, harvestStartField,
                harvestEndField, intendedUseField, isolationNotesField,
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

    private record FormState(String fieldName, String crop,
                             String variety, String acres, String previousCrop,
                             String sowingDate, String floweringStart,
                             String floweringEnd, String harvestStart,
                             String harvestEnd, String intendedUse,
                             String isolationNotes,
                             SeedFieldStage stage,
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
