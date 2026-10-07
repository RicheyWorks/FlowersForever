package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.HayCutting;
import com.flowerfarm.model.HayCuttingStage;
import com.flowerfarm.service.HayCuttingService;

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

/** Repeat hay cutting plans, separate from harvest lots, stock, and orders. */
public final class HayCuttingTab implements FlowerFarmTab {

    private static final DataFlavor CUTTING_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved hay cutting");

    private final HayCuttingService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<HayCuttingStage, JButton> stageTargets =
            new EnumMap<>(HayCuttingStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField fieldField;
    private JTextField cuttingNumberField;
    private JTextField cropField;
    private JTextField acresField;
    private JTextField cutDateField;
    private JTextField baleDateField;
    private JComboBox<HayCuttingStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextCutButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<HayCutting> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public HayCuttingTab(HayCuttingService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public HayCuttingTab(HayCuttingService service, TabHost host,
                         FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Hay cuttings"; }

    @Override public String getDescription() {
        return "Repeat forage cutting plans and shared record links";
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
        new SwingWorker<List<HayCutting>, Void>() {
            @Override protected List<HayCutting> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<HayCutting> cuttings = get();
                    showCuttings(cuttings == null ? List.of() : cuttings);
                    showFeedback(hasUnsavedDraft()
                                    ? "Hay cuttings refreshed; unsaved draft kept."
                                    : "Hay cuttings refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load cuttings. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load cuttings. Choose Refresh to retry.",
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
        if (saving) return "A hay cutting save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved hay cutting draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("hayCutting.page");
        panel.getAccessibleContext().setAccessibleName("Hay cuttings workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Hay cuttings",
                "Plan each cut of a forage field, then move saved cuttings through the work stages.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("hayCutting.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("hayCutting.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("hayCutting.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "Cutting plans and stages do not record actual yields or create harvest lots, "
                        + "stock, or orders. Use shared records separately.",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildCuttingList(), BorderLayout.CENTER);
        listAndTargets.add(buildStageTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("hayCutting.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildCuttingList() {
        tableModel = new DefaultTableModel(new String[]{"Field", "Forage crop", "Cut #",
                "Acres", "Planned cut", "Planned bale", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("hayCutting.cuttingTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Hay cutting plans table");
        table.setTransferHandler(new CuttingDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectCutting();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New hay cutting");
        newButton.setName("hayCutting.new");
        newButton.addActionListener(event -> newCutting());
        nextCutButton = FarmUi.secondaryButton("Plan next cut");
        nextCutButton.setName("hayCutting.nextCut");
        nextCutButton.setToolTipText("Draft the next numbered cut for the selected field. Enter its planned cut date before saving.");
        nextCutButton.addActionListener(event -> planNextCut());
        refreshButton = FarmUi.secondaryButton("Refresh cuttings");
        refreshButton.setName("hayCutting.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextCutButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved hay cuttings",
                "Select a cutting to edit or plan its next cut; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                HayCuttingStage.values().length, 8, 0));
        targets.setName("hayCutting.stageTargets");
        targets.setOpaque(false);
        for (HayCuttingStage stage : HayCuttingStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("hayCutting.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved cutting here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected cutting to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedCuttingTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved cutting",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        fieldField = field("Field name", "e.g. North meadow", 24);
        cuttingNumberField = field("Cutting number", "1 to 99", 12);
        cropField = field("Forage crop", "e.g. Alfalfa", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        cutDateField = field("Planned cut date", "Required YYYY-MM-DD", 12);
        baleDateField = field("Planned bale date", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(HayCuttingStage.values());
        stageField.setName("hayCutting.stage");
        stageField.getAccessibleContext().setAccessibleName("Cutting stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("hayCutting.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Cutting notes");
        FarmUi.initialFocus(fieldField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Field name *", fieldField, fieldField);
        addField(fields, 0, 1, "Forage crop *", cropField, cropField);
        addField(fields, 1, 0, "Cutting number *", cuttingNumberField, cuttingNumberField);
        addField(fields, 1, 1, "Acres", acresField, acresField);
        addField(fields, 2, 0, "Planned cut date *", cutDateField,
                cutDateField);
        addField(fields, 2, 1, "Planned bale date", baleDateField,
                baleDateField);
        addField(fields, 3, 0, "Cutting stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 3, 1, "Cutting notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save cutting");
        saveButton.setName("hayCutting.save");
        saveButton.addActionListener(event -> saveCutting());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("hayCutting.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a hay cutting",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("hayCutting.feedback");
        feedback.getAccessibleContext().setAccessibleName("Hay cutting status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Cutting details",
                "Save each numbered cut separately. A planned date is required for every draft.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("hayCutting." + name.toLowerCase(Locale.ROOT)
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
        host.setStatus("Opened general " + title + "; your hay cutting draft remains here.");
    }

    private void showCuttings(List<HayCutting> cuttings) {
        lastView = List.copyOf(cuttings);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (HayCutting cutting : lastView) {
                tableModel.addRow(new Object[]{cutting.getFieldName(),
                        cutting.getForageCrop(), cutting.getCuttingNumber(),
                        cutting.getAcres() == null ? ""
                                : cutting.getAcres().stripTrailingZeros().toPlainString(),
                        cutting.getPlannedCutDate() == null ? ""
                                : cutting.getPlannedCutDate(),
                        cutting.getPlannedBaleDate() == null ? ""
                                : cutting.getPlannedBaleDate(),
                        enumLabel(cutting.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadCutting);
        }
        updateActions();
    }

    private void selectCutting() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        HayCutting selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadCutting(selected);
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

    private void newCutting() {
        if (!canWrite || saving || loading) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        fieldField.requestFocusInWindow();
        updateActions();
    }

    private void planNextCut() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved cutting first.", FarmUi.Tone.INFO);
            return;
        }
        HayCutting previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null || !mayLeaveDraft()) return;
        if (previous.getCuttingNumber() >= 99) {
            showFeedback("Cutting 99 is the last numbered cut. Edit an existing plan instead.",
                    FarmUi.Tone.WARNING);
            return;
        }
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        fieldField.setText(previous.getFieldName());
        cropField.setText(previous.getForageCrop());
        cuttingNumberField.setText(Integer.toString(previous.getCuttingNumber() + 1));
        acresField.setText(previous.getAcres() == null ? ""
                : previous.getAcres().toPlainString());
        cutDateField.requestFocusInWindow();
        showFeedback("Cut " + (previous.getCuttingNumber() + 1)
                + " drafted. Enter its planned cut date before saving.", FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        fieldField.setText("");
        cuttingNumberField.setText("1");
        cropField.setText("");
        acresField.setText("");
        cutDateField.setText("");
        baleDateField.setText("");
        stageField.setSelectedItem(HayCuttingStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a hay cutting", FarmUi.Tone.NEUTRAL);
    }

    private void loadCutting(HayCutting cutting) {
        editingId = cutting.getId();
        fieldField.setText(cutting.getFieldName());
        cuttingNumberField.setText(Integer.toString(cutting.getCuttingNumber()));
        cropField.setText(cutting.getForageCrop());
        acresField.setText(cutting.getAcres() == null ? ""
                : cutting.getAcres().toPlainString());
        cutDateField.setText(cutting.getPlannedCutDate() == null
                ? "" : cutting.getPlannedCutDate().toString());
        baleDateField.setText(cutting.getPlannedBaleDate() == null
                ? "" : cutting.getPlannedBaleDate().toString());
        stageField.setSelectedItem(cutting.getStage());
        notesField.setText(cutting.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + cutting.getFieldName() + " · cut "
                + cutting.getCuttingNumber(),
                FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the cutting save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This hay cutting has unsaved planning changes. Discard them?",
                "Unsaved hay cutting draft", JOptionPane.DEFAULT_OPTION,
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
                .findFirst().ifPresentOrElse(this::loadCutting, this::clearForm);
    }

    private void saveCutting() {
        if (!canWrite || saving || loading) return;
        HayCutting draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving hay cutting…", FarmUi.Tone.INFO);
        new SwingWorker<HayCutting, Void>() {
            @Override protected HayCutting doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    HayCutting saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Cutting saved · " + saved.getFieldName() + " · cut "
                            + saved.getCuttingNumber(), FarmUi.Tone.SUCCESS);
                    host.setStatus("Hay cutting “" + saved.getFieldName() + "” cut "
                            + saved.getCuttingNumber() + " saved.");
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

    private HayCutting readDraft() {
        String fieldName = fieldField.getText().trim();
        if (fieldName.isEmpty()) {
            fieldField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a field name.");
        }
        String crop = cropField.getText().trim();
        if (crop.isEmpty()) {
            cropField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a forage crop.");
        }
        int cuttingNumber;
        try {
            cuttingNumber = Integer.parseInt(cuttingNumberField.getText().trim());
        } catch (NumberFormatException exception) {
            cuttingNumberField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a cutting number from 1 to 99.");
        }
        if (cuttingNumber < 1 || cuttingNumber > 99) {
            cuttingNumberField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a cutting number from 1 to 99.");
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
        LocalDate cutDate = date(cutDateField, "planned cut date");
        if (cutDate == null) {
            cutDateField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a planned cut date.");
        }
        LocalDate baleDate = date(baleDateField, "planned bale date");
        if (baleDate != null && baleDate.isBefore(cutDate)) {
            baleDateField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned bale date cannot precede cut date.");
        }
        HayCutting draft = new HayCutting(fieldName, crop, cuttingNumber, acres,
                cutDate, baleDate,
                (HayCuttingStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedCuttingTo(HayCuttingStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved hay cutting first.", FarmUi.Tone.INFO);
            return;
        }
        moveCuttingToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, HayCuttingStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveCuttingToStage(Long id, HayCuttingStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current cutting action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved cutting.",
                    FarmUi.Tone.WARNING);
            return;
        }
        HayCutting source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved cutting is no longer in this list. Refresh and try again.",
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
        new SwingWorker<HayCutting, Void>() {
            @Override protected HayCutting doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    HayCutting saved = get();
                    if (Objects.equals(editingId, saved.getId())) loadCutting(saved);
                    showFeedback(saved.getFieldName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Hay cutting “" + saved.getFieldName()
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

    private final class CuttingDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            HayCutting plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new CuttingTransferable(
                    new StageDrag(HayCuttingTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final HayCuttingStage stage;

        private StageDropHandler(HayCuttingStage stage) { this.stage = stage; }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(CUTTING_FLAVOR)) return false;
            try {
                return canMoveDrag((StageDrag) support.getTransferable()
                        .getTransferData(CUTTING_FLAVOR), stage);
            } catch (UnsupportedFlavorException | IOException | ClassCastException exception) {
                return false;
            }
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                StageDrag drag = (StageDrag) support.getTransferable()
                        .getTransferData(CUTTING_FLAVOR);
                moveCuttingToStage(drag.plan().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(HayCuttingTab source, long revision,
                             HayCutting plan) { }

    private record CuttingTransferable(StageDrag drag) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{CUTTING_FLAVOR};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return CUTTING_FLAVOR.equals(flavor);
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
        host.setStatus("Hay cutting plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, text, tone);
    }

    private FormState formState() {
        return new FormState(fieldField.getText(), cuttingNumberField.getText(),
                cropField.getText(), acresField.getText(), cutDateField.getText(),
                baleDateField.getText(),
                (HayCuttingStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || fieldField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(fieldField, cuttingNumberField, cropField,
                acresField, cutDateField, baleDateField,
                stageField, notesField)) control.setEnabled(editable);
        newButton.setEnabled(editable);
        nextCutButton.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
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

    private record FormState(String fieldName, String cuttingNumber, String crop,
                             String acres, String cutDate, String baleDate,
                             HayCuttingStage stage,
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
