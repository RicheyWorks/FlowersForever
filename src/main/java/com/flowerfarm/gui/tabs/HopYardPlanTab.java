package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.HopYardPlan;
import com.flowerfarm.model.HopYardStage;
import com.flowerfarm.service.HopYardPlanService;

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

/** Hop yard seasons and planned harvest windows, separate from stock and orders. */
public final class HopYardPlanTab implements FlowerFarmTab {

    private static final DataFlavor YARD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved hop yard plan");

    private final HopYardPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<HopYardStage, JButton> stageTargets =
            new EnumMap<>(HopYardStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField yardNameField;
    private JTextField varietyField;
    private JTextField acresField;
    private JTextField stringingDateField;
    private JTextField harvestStartField;
    private JTextField harvestEndField;
    private JComboBox<HopYardStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextSeasonButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<HopYardPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public HopYardPlanTab(HopYardPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public HopYardPlanTab(HopYardPlanService service, TabHost host,
                         FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Hop yards"; }

    @Override public String getDescription() {
        return "Hop yard season plans and shared record links";
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
        new SwingWorker<List<HopYardPlan>, Void>() {
            @Override protected List<HopYardPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<HopYardPlan> yards = get();
                    showYards(yards == null ? List.of() : yards);
                    showFeedback(hasUnsavedDraft()
                                    ? "Hop yards refreshed; unsaved draft kept."
                                    : "Hop yards refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load yards. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load yards. Choose Refresh to retry.",
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
        if (saving) return "A hop yard plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved hop yard plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("hopYard.page");
        panel.getAccessibleContext().setAccessibleName("Hop yards workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Hop yards",
                "Plan hop yard seasons, stringing dates, and harvest windows.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("hopYard.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("hopYard.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("hopYard.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "Yard plans and stages do not record actual cone yields or create harvest lots, "
                        + "stock, or orders. Use shared records separately.",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildYardList(), BorderLayout.CENTER);
        listAndTargets.add(buildStageTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("hopYard.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildYardList() {
        tableModel = new DefaultTableModel(new String[]{"Yard", "Hop variety",
                "Acres", "Stringing", "Harvest start", "Harvest end", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("hopYard.yardTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Hop yard plans table");
        table.setTransferHandler(new YardDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectYard();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New hop yard plan");
        newButton.setName("hopYard.new");
        newButton.addActionListener(event -> newYard());
        nextSeasonButton = FarmUi.secondaryButton("Plan next season");
        nextSeasonButton.setName("hopYard.nextSeason");
        nextSeasonButton.setToolTipText("Copy the selected yard and variety into a new season. Enter a new harvest start date before saving.");
        nextSeasonButton.addActionListener(event -> planNextSeason());
        refreshButton = FarmUi.secondaryButton("Refresh yards");
        refreshButton.setName("hopYard.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextSeasonButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved hop yards",
                "Select a yard to edit or plan its next season; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                HopYardStage.values().length, 8, 0));
        targets.setName("hopYard.stageTargets");
        targets.setOpaque(false);
        for (HopYardStage stage : HopYardStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("hopYard.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved yard here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected yard to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedYardTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved yard",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        yardNameField = field("Yard name", "e.g. North yard", 24);
        varietyField = field("Hop variety", "e.g. Cascade", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        stringingDateField = field("Planned stringing date", "Optional YYYY-MM-DD", 12);
        harvestStartField = field("Planned harvest start", "Required YYYY-MM-DD", 12);
        harvestEndField = field("Planned harvest end", "Optional YYYY-MM-DD", 12);
        stageField = new JComboBox<>(HopYardStage.values());
        stageField.setName("hopYard.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("hopYard.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Yard notes");
        FarmUi.initialFocus(yardNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Yard name *", yardNameField, yardNameField);
        addField(fields, 0, 1, "Hop variety *", varietyField, varietyField);
        addField(fields, 1, 1, "Acres", acresField, acresField);
        addField(fields, 1, 0, "Planned stringing date", stringingDateField,
                stringingDateField);
        addField(fields, 2, 0, "Planned harvest start *", harvestStartField,
                harvestStartField);
        addField(fields, 2, 1, "Planned harvest end", harvestEndField,
                harvestEndField);
        addField(fields, 3, 0, "Planning stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 3, 1, "Yard notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("hopYard.save");
        saveButton.addActionListener(event -> saveYard());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("hopYard.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a hop yard season",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("hopYard.feedback");
        feedback.getAccessibleContext().setAccessibleName("Hop yard plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Season plan details",
                "Save each season separately. A harvest start date is required for every plan.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("hopYard." + name.toLowerCase(Locale.ROOT)
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
        host.setStatus("Opened general " + title + "; your hop yard plan draft remains here.");
    }

    private void showYards(List<HopYardPlan> yards) {
        lastView = List.copyOf(yards);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (HopYardPlan yard : lastView) {
                tableModel.addRow(new Object[]{yard.getYardName(),
                        yard.getHopVariety(),
                        yard.getAcres() == null ? ""
                                : yard.getAcres().stripTrailingZeros().toPlainString(),
                        yard.getPlannedStringingDate() == null ? ""
                                : yard.getPlannedStringingDate(),
                        yard.getPlannedHarvestStart() == null ? ""
                                : yard.getPlannedHarvestStart(),
                        yard.getPlannedHarvestEnd() == null ? ""
                                : yard.getPlannedHarvestEnd(),
                        enumLabel(yard.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadYard);
        }
        updateActions();
    }

    private void selectYard() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        HopYardPlan selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadYard(selected);
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

    private void newYard() {
        if (!canWrite || saving || loading) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        yardNameField.requestFocusInWindow();
        updateActions();
    }

    private void planNextSeason() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved yard first.", FarmUi.Tone.INFO);
            return;
        }
        HopYardPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null || !mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        yardNameField.setText(previous.getYardName());
        varietyField.setText(previous.getHopVariety());
        acresField.setText(previous.getAcres() == null ? ""
                : previous.getAcres().toPlainString());
        harvestStartField.requestFocusInWindow();
        showFeedback("Next season drafted for " + previous.getYardName()
                + ". Enter a new harvest start date before saving.", FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        yardNameField.setText("");
        varietyField.setText("");
        acresField.setText("");
        stringingDateField.setText("");
        harvestStartField.setText("");
        harvestEndField.setText("");
        stageField.setSelectedItem(HopYardStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a hop yard season", FarmUi.Tone.NEUTRAL);
    }

    private void loadYard(HopYardPlan yard) {
        editingId = yard.getId();
        yardNameField.setText(yard.getYardName());
        varietyField.setText(yard.getHopVariety());
        acresField.setText(yard.getAcres() == null ? ""
                : yard.getAcres().toPlainString());
        stringingDateField.setText(yard.getPlannedStringingDate() == null
                ? "" : yard.getPlannedStringingDate().toString());
        harvestStartField.setText(yard.getPlannedHarvestStart() == null
                ? "" : yard.getPlannedHarvestStart().toString());
        harvestEndField.setText(yard.getPlannedHarvestEnd() == null
                ? "" : yard.getPlannedHarvestEnd().toString());
        stageField.setSelectedItem(yard.getStage());
        notesField.setText(yard.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + yard.getYardName() + " · "
                + yard.getHopVariety(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the yard save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This hop yard plan has unsaved planning changes. Discard them?",
                "Unsaved hop yard plan draft", JOptionPane.DEFAULT_OPTION,
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
                .findFirst().ifPresentOrElse(this::loadYard, this::clearForm);
    }

    private void saveYard() {
        if (!canWrite || saving || loading) return;
        HopYardPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving hop yard plan…", FarmUi.Tone.INFO);
        new SwingWorker<HopYardPlan, Void>() {
            @Override protected HopYardPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    HopYardPlan saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Yard plan saved · " + saved.getYardName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Hop yard plan “" + saved.getYardName() + "” saved.");
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

    private HopYardPlan readDraft() {
        String yardName = yardNameField.getText().trim();
        if (yardName.isEmpty()) {
            yardNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a yard name.");
        }
        String variety = varietyField.getText().trim();
        if (variety.isEmpty()) {
            varietyField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a hop variety.");
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
        LocalDate stringingDate = date(stringingDateField, "planned stringing date");
        LocalDate harvestStart = date(harvestStartField, "planned harvest start");
        if (harvestStart == null) {
            harvestStartField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a planned harvest start date.");
        }
        if (stringingDate != null && stringingDate.isAfter(harvestStart)) {
            stringingDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned stringing date cannot be after planned harvest start.");
        }
        LocalDate harvestEnd = date(harvestEndField, "planned harvest end");
        if (harvestEnd != null && harvestEnd.isBefore(harvestStart)) {
            harvestEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest end cannot precede start.");
        }
        HopYardPlan draft = new HopYardPlan(yardName, variety, acres,
                stringingDate, harvestStart, harvestEnd,
                (HopYardStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedYardTo(HopYardStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved hop yard plan first.", FarmUi.Tone.INFO);
            return;
        }
        moveYardToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, HopYardStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveYardToStage(Long id, HopYardStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current yard action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved yard.",
                    FarmUi.Tone.WARNING);
            return;
        }
        HopYardPlan source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved yard is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getYardName() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getYardName() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<HopYardPlan, Void>() {
            @Override protected HopYardPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    HopYardPlan saved = get();
                    if (Objects.equals(editingId, saved.getId())) loadYard(saved);
                    showFeedback(saved.getYardName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Hop yard plan “" + saved.getYardName()
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

    private final class YardDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            HopYardPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new YardTransferable(
                    new StageDrag(HopYardPlanTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final HopYardStage stage;

        private StageDropHandler(HopYardStage stage) { this.stage = stage; }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(YARD_FLAVOR)) return false;
            try {
                return canMoveDrag((StageDrag) support.getTransferable()
                        .getTransferData(YARD_FLAVOR), stage);
            } catch (UnsupportedFlavorException | IOException | ClassCastException exception) {
                return false;
            }
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                StageDrag drag = (StageDrag) support.getTransferable()
                        .getTransferData(YARD_FLAVOR);
                moveYardToStage(drag.plan().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(HopYardPlanTab source, long revision,
                             HopYardPlan plan) { }

    private record YardTransferable(StageDrag drag) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{YARD_FLAVOR};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return YARD_FLAVOR.equals(flavor);
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
        host.setStatus("Hop yard plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, text, tone);
    }

    private FormState formState() {
        return new FormState(yardNameField.getText(), varietyField.getText(),
                acresField.getText(), stringingDateField.getText(),
                harvestStartField.getText(), harvestEndField.getText(),
                (HopYardStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || yardNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(yardNameField, varietyField,
                acresField, stringingDateField, harvestStartField, harvestEndField,
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

    private record FormState(String yardName, String variety,
                             String acres, String stringingDate, String harvestStart,
                             String harvestEnd,
                             HopYardStage stage,
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
