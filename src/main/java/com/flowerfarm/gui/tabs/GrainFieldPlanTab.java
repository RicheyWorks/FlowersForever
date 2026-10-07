package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.GrainFieldPlan;
import com.flowerfarm.model.GrainFieldStage;
import com.flowerfarm.service.GrainFieldPlanService;

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

/** Grain field rotations and planned harvest windows, separate from stock and orders. */
public final class GrainFieldPlanTab implements FlowerFarmTab {

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved grain field plan");

    private final GrainFieldPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<GrainFieldStage, JButton> stageTargets =
            new EnumMap<>(GrainFieldStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField fieldNameField;
    private JTextField cropField;
    private JTextField varietyField;
    private JTextField acresField;
    private JTextField previousCropField;
    private JTextField seedingDateField;
    private JTextField harvestStartField;
    private JTextField harvestEndField;
    private JComboBox<GrainFieldStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextRotationButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<GrainFieldPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public GrainFieldPlanTab(GrainFieldPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public GrainFieldPlanTab(GrainFieldPlanService service, TabHost host,
                             FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Grain fields"; }

    @Override public String getDescription() {
        return "Grain field rotation plans and shared record links";
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
        new SwingWorker<List<GrainFieldPlan>, Void>() {
            @Override protected List<GrainFieldPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<GrainFieldPlan> fields = get();
                    showFields(fields == null ? List.of() : fields);
                    showFeedback(hasUnsavedDraft()
                                    ? "Grain fields refreshed; unsaved draft kept."
                                    : "Grain fields refreshed.", FarmUi.Tone.SUCCESS);
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
        if (saving) return "A grain field plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved grain field plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("grainField.page");
        panel.getAccessibleContext().setAccessibleName("Grain fields workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Grain fields",
                "Plan grain field rotations, seeding dates, and harvest windows.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("grainField.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("grainField.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("grainField.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "Field plans and stages do not record actual grain yields or create harvest lots, "
                        + "stock, or orders. Use shared records separately.",
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
        scroll.setName("grainField.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildFieldList() {
        tableModel = new DefaultTableModel(new String[]{"Field", "Grain crop",
                "Variety", "Acres", "Previous crop", "Harvest start", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("grainField.fieldTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Grain field plans table");
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
        newButton = FarmUi.primaryButton("New grain field plan");
        newButton.setName("grainField.new");
        newButton.addActionListener(event -> newField());
        nextRotationButton = FarmUi.secondaryButton("Plan next rotation");
        nextRotationButton.setName("grainField.nextRotation");
        nextRotationButton.setToolTipText("Start a new rotation in this field. Previous grain crop is remembered; choose the next crop and dates before saving.");
        nextRotationButton.addActionListener(event -> planNextRotation());
        refreshButton = FarmUi.secondaryButton("Refresh fields");
        refreshButton.setName("grainField.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextRotationButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved grain fields",
                "Select a field to edit or plan its next rotation; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                GrainFieldStage.values().length, 8, 0));
        targets.setName("grainField.stageTargets");
        targets.setOpaque(false);
        for (GrainFieldStage stage : GrainFieldStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("grainField.stageTarget." + stage.name());
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
        cropField = field("Grain crop", "e.g. wheat or barley", 24);
        varietyField = field("Variety", "Optional, e.g. soft white winter", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        previousCropField = field("Previous crop", "Optional, e.g. peas", 24);
        seedingDateField = field("Planned seeding date", "Optional YYYY-MM-DD", 12);
        harvestStartField = field("Planned harvest start", "Required YYYY-MM-DD", 12);
        harvestEndField = field("Planned harvest end", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(GrainFieldStage.values());
        stageField.setName("grainField.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("grainField.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Field notes");
        FarmUi.initialFocus(fieldNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Field name *", fieldNameField, fieldNameField);
        addField(fields, 0, 1, "Grain crop *", cropField, cropField);
        addField(fields, 1, 0, "Variety", varietyField, varietyField);
        addField(fields, 1, 1, "Acres", acresField, acresField);
        addField(fields, 2, 0, "Previous crop", previousCropField,
                previousCropField);
        addField(fields, 2, 1, "Planned seeding date", seedingDateField,
                seedingDateField);
        addField(fields, 3, 0, "Planned harvest start *", harvestStartField,
                harvestStartField);
        addField(fields, 3, 1, "Planned harvest end", harvestEndField,
                harvestEndField);
        addField(fields, 4, 0, "Planning stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 4, 1, "Field notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("grainField.save");
        saveButton.addActionListener(event -> saveField());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("grainField.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a grain field rotation",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("grainField.feedback");
        feedback.getAccessibleContext().setAccessibleName("Grain field plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Rotation plan details",
                "Save each rotation separately. A harvest start date is required for every plan.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("grainField." + name.toLowerCase(Locale.ROOT)
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
        host.setStatus("Opened general " + title + "; your grain field plan draft remains here.");
    }

    private void showFields(List<GrainFieldPlan> fields) {
        lastView = List.copyOf(fields);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (GrainFieldPlan field : lastView) {
                tableModel.addRow(new Object[]{field.getFieldName(),
                        field.getGrainCrop(),
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
        GrainFieldPlan selected = lastView.get(table.convertRowIndexToModel(row));
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

    private void planNextRotation() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved field first.", FarmUi.Tone.INFO);
            return;
        }
        GrainFieldPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null || !mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        fieldNameField.setText(previous.getFieldName());
        acresField.setText(previous.getAcres() == null ? ""
                : previous.getAcres().toPlainString());
        previousCropField.setText(previous.getGrainCrop());
        cropField.requestFocusInWindow();
        showFeedback("Next rotation drafted for " + previous.getFieldName()
                + ". Choose the next grain crop and harvest start date before saving.",
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
        seedingDateField.setText("");
        harvestStartField.setText("");
        harvestEndField.setText("");
        stageField.setSelectedItem(GrainFieldStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a grain field rotation", FarmUi.Tone.NEUTRAL);
    }

    private void loadField(GrainFieldPlan field) {
        editingId = field.getId();
        fieldNameField.setText(field.getFieldName());
        cropField.setText(field.getGrainCrop());
        varietyField.setText(field.getVariety());
        acresField.setText(field.getAcres() == null ? ""
                : field.getAcres().toPlainString());
        previousCropField.setText(field.getPreviousCrop());
        seedingDateField.setText(field.getPlannedSeedingDate() == null
                ? "" : field.getPlannedSeedingDate().toString());
        harvestStartField.setText(field.getPlannedHarvestStart() == null
                ? "" : field.getPlannedHarvestStart().toString());
        harvestEndField.setText(field.getPlannedHarvestEnd() == null
                ? "" : field.getPlannedHarvestEnd().toString());
        stageField.setSelectedItem(field.getStage());
        notesField.setText(field.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + field.getFieldName() + " · "
                + field.getGrainCrop(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the field save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This grain field plan has unsaved planning changes. Discard them?",
                "Unsaved grain field plan draft", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        return choice == 1;
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
        GrainFieldPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving grain field plan…", FarmUi.Tone.INFO);
        new SwingWorker<GrainFieldPlan, Void>() {
            @Override protected GrainFieldPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    GrainFieldPlan saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Field plan saved · " + saved.getFieldName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Grain field plan “" + saved.getFieldName() + "” saved.");
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

    private GrainFieldPlan readDraft() {
        String fieldName = fieldNameField.getText().trim();
        if (fieldName.isEmpty()) {
            fieldNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a field name.");
        }
        String crop = cropField.getText().trim();
        if (crop.isEmpty()) {
            cropField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a grain crop.");
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
        LocalDate seedingDate = date(seedingDateField, "planned seeding date");
        LocalDate harvestStart = date(harvestStartField, "planned harvest start");
        if (harvestStart == null) {
            harvestStartField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a planned harvest start date.");
        }
        if (seedingDate != null && seedingDate.isAfter(harvestStart)) {
            seedingDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned seeding date cannot be after planned harvest start.");
        }
        LocalDate harvestEnd = date(harvestEndField, "planned harvest end");
        if (harvestEnd != null && harvestEnd.isBefore(harvestStart)) {
            harvestEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest end cannot precede start.");
        }
        GrainFieldPlan draft = new GrainFieldPlan(fieldName, crop,
                varietyField.getText().trim(), acres, previousCropField.getText().trim(),
                seedingDate, harvestStart, harvestEnd,
                (GrainFieldStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedFieldTo(GrainFieldStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved grain field plan first.", FarmUi.Tone.INFO);
            return;
        }
        moveFieldToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, GrainFieldStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveFieldToStage(Long id, GrainFieldStage stage) {
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
        GrainFieldPlan source = lastView.stream()
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
        new SwingWorker<GrainFieldPlan, Void>() {
            @Override protected GrainFieldPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    GrainFieldPlan saved = get();
                    if (Objects.equals(editingId, saved.getId())) loadField(saved);
                    showFeedback(saved.getFieldName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Grain field plan “" + saved.getFieldName()
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
            GrainFieldPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new FieldTransferable(
                    new StageDrag(GrainFieldPlanTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final GrainFieldStage stage;

        private StageDropHandler(GrainFieldStage stage) { this.stage = stage; }

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

    private record StageDrag(GrainFieldPlanTab source, long revision,
                             GrainFieldPlan plan) { }

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
        host.setStatus("Grain field plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, text, tone);
    }

    private FormState formState() {
        return new FormState(fieldNameField.getText(), cropField.getText(),
                varietyField.getText(), acresField.getText(),
                previousCropField.getText(), seedingDateField.getText(),
                harvestStartField.getText(), harvestEndField.getText(),
                (GrainFieldStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || fieldNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(fieldNameField, cropField,
                varietyField, acresField, previousCropField, seedingDateField,
                harvestStartField, harvestEndField,
                stageField, notesField)) control.setEnabled(editable);
        newButton.setEnabled(editable);
        nextRotationButton.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
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
                             String seedingDate, String harvestStart,
                             String harvestEnd,
                             GrainFieldStage stage,
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
