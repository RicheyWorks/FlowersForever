package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.CranberryBedPlan;
import com.flowerfarm.model.CranberryBedStage;
import com.flowerfarm.model.CranberryHarvestMethod;
import com.flowerfarm.service.CranberryBedPlanService;

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

/** Editable cranberry bed plans, separate from harvests and sales. */
public final class CranberryBedTab implements FlowerFarmTab {

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved cranberry bed plan");

    private final CranberryBedPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<CranberryBedStage, JButton> stageTargets =
            new EnumMap<>(CranberryBedStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField bedNameField;
    private JTextField varietyField;
    private JTextField acresField;
    private JTextField harvestStartField;
    private JTextField harvestEndField;
    private JTextField intendedChannelField;
    private JComboBox<CranberryHarvestMethod> harvestMethodField;
    private JComboBox<CranberryBedStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextSeasonButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<CranberryBedPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public CranberryBedTab(CranberryBedPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public CranberryBedTab(CranberryBedPlanService service, TabHost host,
                           FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Cranberry beds"; }

    @Override public String getDescription() {
        return "Cranberry bed plans beside shared inventory and customer tools";
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
        new SwingWorker<List<CranberryBedPlan>, Void>() {
            @Override protected List<CranberryBedPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<CranberryBedPlan> beds = get();
                    showBeds(beds == null ? List.of() : beds);
                    showFeedback(hasUnsavedDraft()
                                    ? "Cranberry beds refreshed; unsaved draft kept."
                                    : "Cranberry beds refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load cranberry beds. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load cranberry beds. Choose Refresh to retry.",
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
        if (saving) return "A cranberry bed plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved cranberry bed plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("cranberryBed.page");
        panel.getAccessibleContext().setAccessibleName("Cranberry beds workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Cranberry beds",
                "Plan cranberry beds, harvest method, and seasonal harvest windows.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("cranberryBed.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("cranberryBed.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("cranberryBed.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "<html>These are grower-entered bed plans. Wet or dry harvest method and "
                        + "planned dates do not log a completed harvest.<br>This screen "
                        + "does not record actual yield, stock, or sales.</html>",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildBedList(), BorderLayout.CENTER);
        listAndTargets.add(buildStageTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("cranberryBed.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildBedList() {
        tableModel = new DefaultTableModel(new String[]{"Bed", "Variety",
                "Acres", "Harvest method", "Harvest start", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("cranberryBed.bedTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Cranberry bed plans table");
        table.setTransferHandler(new BedDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectBed();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New cranberry bed");
        newButton.setName("cranberryBed.new");
        newButton.addActionListener(event -> newBed());
        nextSeasonButton = FarmUi.secondaryButton("Plan next season");
        nextSeasonButton.setName("cranberryBed.nextSeason");
        nextSeasonButton.setToolTipText("Carry bed, variety, acres, harvest method, and "
                + "channel forward; enter new planned harvest dates.");
        nextSeasonButton.addActionListener(event -> planNextSeason());
        refreshButton = FarmUi.secondaryButton("Refresh beds");
        refreshButton.setName("cranberryBed.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextSeasonButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved cranberry beds",
                "Select a bed to edit or draft its next season; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                CranberryBedStage.values().length, 8, 0));
        targets.setName("cranberryBed.stageTargets");
        targets.setOpaque(false);
        for (CranberryBedStage stage : CranberryBedStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("cranberryBed.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved bed here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected bed to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedBedTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved bed",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        bedNameField = field("Bed name", "e.g. North bog bed", 24);
        varietyField = field("Variety", "Optional, e.g. Stevens", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        harvestMethodField = new JComboBox<>(CranberryHarvestMethod.values());
        harvestMethodField.setName("cranberryBed.harvestMethod");
        harvestMethodField.getAccessibleContext().setAccessibleName("Planned harvest method");
        harvestMethodField.setRenderer(enumRenderer());
        harvestStartField = field("Planned harvest start", "Optional YYYY-MM-DD", 12);
        harvestEndField = field("Planned harvest end", "Optional YYYY-MM-DD", 12);
        intendedChannelField = field("Intended channel", "Optional buyer or market note", 24);
        stageField = new JComboBox<>(CranberryBedStage.values());
        stageField.setName("cranberryBed.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("cranberryBed.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Bed notes");
        FarmUi.initialFocus(bedNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Bed name *", bedNameField, bedNameField);
        addField(fields, 0, 1, "Variety", varietyField, varietyField);
        addField(fields, 1, 0, "Acres", acresField, acresField);
        addField(fields, 1, 1, "Planned harvest method", harvestMethodField,
                harvestMethodField);
        addField(fields, 2, 0, "Planned harvest start", harvestStartField,
                harvestStartField);
        addField(fields, 2, 1, "Planned harvest end", harvestEndField,
                harvestEndField);
        addField(fields, 3, 0, "Intended channel", intendedChannelField,
                intendedChannelField);
        addField(fields, 3, 1, "Planning stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 4, 0, "Bed notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("cranberryBed.save");
        saveButton.addActionListener(event -> saveBed());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("cranberryBed.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a cranberry bed",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("cranberryBed.feedback");
        feedback.getAccessibleContext().setAccessibleName("Cranberry bed plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Bed plan details",
                "Record a grower-entered cranberry bed plan. Bed name is required; "
                        + "method and dates can be adjusted as plans change.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("cranberryBed." + name.toLowerCase(Locale.ROOT)
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
                + "; your cranberry bed plan draft remains here.");
    }

    private void showBeds(List<CranberryBedPlan> beds) {
        lastView = List.copyOf(beds);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (CranberryBedPlan bed : lastView) {
                tableModel.addRow(new Object[]{bed.getBedName(), bed.getVariety(),
                        bed.getAcres() == null ? ""
                                : bed.getAcres().stripTrailingZeros().toPlainString(),
                        enumLabel(bed.getHarvestMethod()),
                        bed.getPlannedHarvestStart() == null ? ""
                                : bed.getPlannedHarvestStart(), enumLabel(bed.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadBed);
        }
        updateActions();
    }

    private void selectBed() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        CranberryBedPlan selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadBed(selected);
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

    private void newBed() {
        if (!canWrite || saving || loading) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        bedNameField.requestFocusInWindow();
        updateActions();
    }

    private void planNextSeason() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved cranberry bed first.", FarmUi.Tone.INFO);
            return;
        }
        CranberryBedPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        bedNameField.setText(previous.getBedName());
        varietyField.setText(previous.getVariety());
        acresField.setText(previous.getAcres() == null ? ""
                : previous.getAcres().toPlainString());
        harvestMethodField.setSelectedItem(previous.getHarvestMethod());
        intendedChannelField.setText(previous.getIntendedChannel());
        harvestStartField.requestFocusInWindow();
        showFeedback("Next season drafted for " + previous.getBedName()
                + ". Review the method and enter this season's planned harvest dates.",
                FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        bedNameField.setText("");
        varietyField.setText("");
        acresField.setText("");
        harvestMethodField.setSelectedItem(CranberryHarvestMethod.UNDECIDED);
        harvestStartField.setText("");
        harvestEndField.setText("");
        intendedChannelField.setText("");
        stageField.setSelectedItem(CranberryBedStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a cranberry bed", FarmUi.Tone.NEUTRAL);
    }

    private void loadBed(CranberryBedPlan bed) {
        editingId = bed.getId();
        bedNameField.setText(bed.getBedName());
        varietyField.setText(bed.getVariety());
        acresField.setText(bed.getAcres() == null ? "" : bed.getAcres().toPlainString());
        harvestMethodField.setSelectedItem(bed.getHarvestMethod());
        harvestStartField.setText(bed.getPlannedHarvestStart() == null
                ? "" : bed.getPlannedHarvestStart().toString());
        harvestEndField.setText(bed.getPlannedHarvestEnd() == null
                ? "" : bed.getPlannedHarvestEnd().toString());
        intendedChannelField.setText(bed.getIntendedChannel());
        stageField.setSelectedItem(bed.getStage());
        notesField.setText(bed.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + bed.getBedName() + " · "
                + enumLabel(bed.getHarvestMethod()), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the bed plan save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This cranberry bed plan has unsaved planning changes. Discard them?",
                "Unsaved cranberry bed plan draft", JOptionPane.DEFAULT_OPTION,
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
                .findFirst().ifPresentOrElse(this::loadBed, this::clearForm);
    }

    private void saveBed() {
        if (!canWrite || saving || loading) return;
        CranberryBedPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving cranberry bed plan…", FarmUi.Tone.INFO);
        new SwingWorker<CranberryBedPlan, Void>() {
            @Override protected CranberryBedPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    CranberryBedPlan saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Cranberry bed plan saved · " + saved.getBedName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Cranberry bed plan “" + saved.getBedName() + "” saved.");
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

    private CranberryBedPlan readDraft() {
        String bedName = bedNameField.getText().trim();
        if (bedName.isEmpty()) {
            bedNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a bed name.");
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
        LocalDate harvestStart = date(harvestStartField, "planned harvest start");
        LocalDate harvestEnd = date(harvestEndField, "planned harvest end");
        if (harvestEnd != null && harvestStart == null) {
            harvestEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest end requires a start date.");
        }
        if (harvestEnd != null && harvestEnd.isBefore(harvestStart)) {
            harvestEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest end cannot precede start.");
        }
        CranberryBedPlan draft = new CranberryBedPlan(bedName,
                varietyField.getText().trim(), acres,
                (CranberryHarvestMethod) harvestMethodField.getSelectedItem(),
                harvestStart, harvestEnd, intendedChannelField.getText().trim(),
                (CranberryBedStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedBedTo(CranberryBedStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved cranberry bed plan first.", FarmUi.Tone.INFO);
            return;
        }
        moveBedToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, CranberryBedStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveBedToStage(Long id, CranberryBedStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current bed action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved bed.",
                    FarmUi.Tone.WARNING);
            return;
        }
        CranberryBedPlan source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved bed is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getBedName() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getBedName() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<CranberryBedPlan, Void>() {
            @Override protected CranberryBedPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    CranberryBedPlan saved = get();
                    if (Objects.equals(editingId, saved.getId())) loadBed(saved);
                    showFeedback(saved.getBedName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Cranberry bed plan “" + saved.getBedName()
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

    private final class BedDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            CranberryBedPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new BedTransferable(
                    new StageDrag(CranberryBedTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final CranberryBedStage stage;

        private StageDropHandler(CranberryBedStage stage) { this.stage = stage; }

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
                moveBedToStage(drag.plan().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(CranberryBedTab source, long revision,
                             CranberryBedPlan plan) { }

    private record BedTransferable(StageDrag drag) implements Transferable {
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
        host.setStatus("Cranberry bed plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, text, tone);
    }

    private FormState formState() {
        return new FormState(bedNameField.getText(), varietyField.getText(),
                acresField.getText(),
                (CranberryHarvestMethod) harvestMethodField.getSelectedItem(),
                harvestStartField.getText(), harvestEndField.getText(),
                intendedChannelField.getText(),
                (CranberryBedStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || bedNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(bedNameField, varietyField,
                acresField, harvestMethodField, harvestStartField,
                harvestEndField, intendedChannelField, stageField, notesField)) {
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

    private record FormState(String bedName, String variety, String acres,
                             CranberryHarvestMethod harvestMethod,
                             String harvestStart, String harvestEnd,
                             String intendedChannel, CranberryBedStage stage,
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
