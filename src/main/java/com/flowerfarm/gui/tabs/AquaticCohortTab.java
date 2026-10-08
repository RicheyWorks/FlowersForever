package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.AquaticCohortPlan;
import com.flowerfarm.model.AquaticCohortStage;
import com.flowerfarm.service.AquaticCohortPlanService;

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

/** Editable aquatic cohort plans, separate from harvests and sales. */
public final class AquaticCohortTab implements FlowerFarmTab {

    private static final DataFlavor FIELD_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved aquatic cohort plan");

    private final AquaticCohortPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<AquaticCohortStage, JButton> stageTargets =
            new EnumMap<>(AquaticCohortStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField cohortNameField;
    private JTextField speciesField;
    private JTextField growingSiteField;
    private JTextField cultureMethodField;
    private JTextField stockingDateField;
    private JTextField harvestStartField;
    private JTextField harvestEndField;
    private JTextField intendedDestinationField;
    private JComboBox<AquaticCohortStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton nextCohortButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<AquaticCohortPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public AquaticCohortTab(AquaticCohortPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public AquaticCohortTab(AquaticCohortPlanService service, TabHost host,
                             FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Aquatic cohorts"; }

    @Override public String getDescription() {
        return "Aquaculture cohort plans beside shared inventory and customer tools";
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
        new SwingWorker<List<AquaticCohortPlan>, Void>() {
            @Override protected List<AquaticCohortPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<AquaticCohortPlan> fields = get();
                    showCohorts(fields == null ? List.of() : fields);
                    showFeedback(hasUnsavedDraft()
                                    ? "Aquatic cohorts refreshed; unsaved draft kept."
                                    : "Aquatic cohorts refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load cohorts. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load cohorts. Choose Refresh to retry.",
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
        if (saving) return "An aquatic cohort plan save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved aquatic cohort plan draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("aquaticCohort.page");
        panel.getAccessibleContext().setAccessibleName("Aquatic cohorts workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Aquatic cohorts",
                "Plan stocking and harvest windows for fish, shellfish, and aquatic plants.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("aquaticCohort.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("aquaticCohort.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("aquaticCohort.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "<html>Grower-entered plans do not record completed stocking, harvest, stock, "
                        + "or sales.<br>Shellfish harvest-area clearance, licensing, and "
                        + "regulatory reporting require separate checks.</html>",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildCohortList(), BorderLayout.CENTER);
        listAndTargets.add(buildStageTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("aquaticCohort.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildCohortList() {
        tableModel = new DefaultTableModel(new String[]{"Cohort", "Species",
                "Growing site", "Stocking date", "Harvest start", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("aquaticCohort.cohortTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Aquatic cohort plans table");
        table.setTransferHandler(new CohortDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectCohort();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New aquatic cohort plan");
        newButton.setName("aquaticCohort.new");
        newButton.addActionListener(event -> newCohort());
        nextCohortButton = FarmUi.secondaryButton("Plan next cohort");
        nextCohortButton.setName("aquaticCohort.nextCohort");
        nextCohortButton.setToolTipText("Carry species, site, method, and intended destination "
                + "forward, then enter a new cohort name and planned dates.");
        nextCohortButton.addActionListener(event -> planNextCohort());
        refreshButton = FarmUi.secondaryButton("Refresh cohorts");
        refreshButton.setName("aquaticCohort.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextCohortButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved aquatic cohorts",
                "Select a cohort to edit or draft its next cohort; drag a saved row to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                AquaticCohortStage.values().length, 8, 0));
        targets.setName("aquaticCohort.stageTargets");
        targets.setOpaque(false);
        for (AquaticCohortStage stage : AquaticCohortStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("aquaticCohort.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved cohort here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected cohort to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedCohortTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved cohort",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        cohortNameField = field("Cohort name", "e.g. Spring oyster cohort", 24);
        speciesField = field("Species", "e.g. Pacific oyster or rainbow trout", 24);
        growingSiteField = field("Growing site", "e.g. South bay lease or tank 2", 24);
        cultureMethodField = field("Culture method", "Optional, e.g. bags, rafts, or tanks", 24);
        stockingDateField = field("Planned stocking date", "Optional YYYY-MM-DD", 12);
        harvestStartField = field("Planned harvest start", "Required YYYY-MM-DD", 12);
        harvestEndField = field("Planned harvest end", "Optional YYYY-MM-DD", 12);
        intendedDestinationField = field("Intended destination", "Optional market or buyer note", 24);
        stageField = new JComboBox<>(AquaticCohortStage.values());
        stageField.setName("aquaticCohort.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("aquaticCohort.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Cohort notes");
        FarmUi.initialFocus(cohortNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Cohort name *", cohortNameField, cohortNameField);
        addField(fields, 0, 1, "Species *", speciesField, speciesField);
        addField(fields, 1, 0, "Growing site *", growingSiteField, growingSiteField);
        addField(fields, 1, 1, "Culture method", cultureMethodField, cultureMethodField);
        addField(fields, 2, 0, "Planned stocking date", stockingDateField,
                stockingDateField);
        addField(fields, 2, 1, "Planned harvest start *", harvestStartField,
                harvestStartField);
        addField(fields, 3, 0, "Planned harvest end", harvestEndField,
                harvestEndField);
        addField(fields, 3, 1, "Intended destination", intendedDestinationField,
                intendedDestinationField);
        addField(fields, 4, 0, "Planning stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 4, 1, "Cohort notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("aquaticCohort.save");
        saveButton.addActionListener(event -> saveCohort());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("aquaticCohort.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan an aquatic cohort",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("aquaticCohort.feedback");
        feedback.getAccessibleContext().setAccessibleName("Aquatic cohort plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Cohort plan details",
                "Record a grower-entered cohort plan. Species, growing site, and planned "
                        + "harvest start are required.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("aquaticCohort." + name.toLowerCase(Locale.ROOT)
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
        host.setStatus("Opened general " + title + "; your aquatic cohort plan draft remains here.");
    }

    private void showCohorts(List<AquaticCohortPlan> cohorts) {
        lastView = List.copyOf(cohorts);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (AquaticCohortPlan cohort : lastView) {
                tableModel.addRow(new Object[]{cohort.getCohortName(),
                        cohort.getSpecies(), cohort.getGrowingSite(),
                        cohort.getPlannedStockingDate() == null ? ""
                                : cohort.getPlannedStockingDate(),
                        cohort.getPlannedHarvestStart(), enumLabel(cohort.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadCohort);
        }
        updateActions();
    }

    private void selectCohort() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        AquaticCohortPlan selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadCohort(selected);
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

    private void newCohort() {
        if (!canWrite || saving || loading) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        cohortNameField.requestFocusInWindow();
        updateActions();
    }

    private void planNextCohort() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved cohort first.", FarmUi.Tone.INFO);
            return;
        }
        AquaticCohortPlan previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        speciesField.setText(previous.getSpecies());
        growingSiteField.setText(previous.getGrowingSite());
        cultureMethodField.setText(previous.getCultureMethod());
        intendedDestinationField.setText(previous.getIntendedDestination());
        cohortNameField.requestFocusInWindow();
        showFeedback("Next cohort drafted from " + previous.getCohortName()
                + ". Enter a new cohort name and planned stocking and harvest dates.",
                FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        cohortNameField.setText("");
        speciesField.setText("");
        growingSiteField.setText("");
        cultureMethodField.setText("");
        stockingDateField.setText("");
        harvestStartField.setText("");
        harvestEndField.setText("");
        intendedDestinationField.setText("");
        stageField.setSelectedItem(AquaticCohortStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan an aquatic cohort", FarmUi.Tone.NEUTRAL);
    }

    private void loadCohort(AquaticCohortPlan cohort) {
        editingId = cohort.getId();
        cohortNameField.setText(cohort.getCohortName());
        speciesField.setText(cohort.getSpecies());
        growingSiteField.setText(cohort.getGrowingSite());
        cultureMethodField.setText(cohort.getCultureMethod());
        stockingDateField.setText(cohort.getPlannedStockingDate() == null
                ? "" : cohort.getPlannedStockingDate().toString());
        harvestStartField.setText(cohort.getPlannedHarvestStart() == null
                ? "" : cohort.getPlannedHarvestStart().toString());
        harvestEndField.setText(cohort.getPlannedHarvestEnd() == null
                ? "" : cohort.getPlannedHarvestEnd().toString());
        intendedDestinationField.setText(cohort.getIntendedDestination());
        stageField.setSelectedItem(cohort.getStage());
        notesField.setText(cohort.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + cohort.getCohortName() + " · "
                + cohort.getSpecies(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the cohort save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This aquatic cohort plan has unsaved planning changes. Discard them?",
                "Unsaved aquatic cohort plan draft", JOptionPane.DEFAULT_OPTION,
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
                .findFirst().ifPresentOrElse(this::loadCohort, this::clearForm);
    }

    private void saveCohort() {
        if (!canWrite || saving || loading) return;
        AquaticCohortPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving aquatic cohort plan…", FarmUi.Tone.INFO);
        new SwingWorker<AquaticCohortPlan, Void>() {
            @Override protected AquaticCohortPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    AquaticCohortPlan saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Aquatic cohort plan saved · " + saved.getCohortName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Aquatic cohort plan “" + saved.getCohortName() + "” saved.");
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

    private AquaticCohortPlan readDraft() {
        String cohortName = cohortNameField.getText().trim();
        if (cohortName.isEmpty()) {
            cohortNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a cohort name.");
        }
        String species = speciesField.getText().trim();
        if (species.isEmpty()) {
            speciesField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a species name.");
        }
        String growingSite = growingSiteField.getText().trim();
        if (growingSite.isEmpty()) {
            growingSiteField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a growing site.");
        }
        LocalDate stockingDate = date(stockingDateField, "planned stocking date");
        LocalDate harvestStart = date(harvestStartField, "planned harvest start");
        if (harvestStart == null) {
            harvestStartField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a planned harvest start date.");
        }
        if (stockingDate != null && stockingDate.isAfter(harvestStart)) {
            stockingDateField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Planned stocking date cannot be after planned harvest start.");
        }
        LocalDate harvestEnd = date(harvestEndField, "planned harvest end");
        if (harvestEnd != null && harvestEnd.isBefore(harvestStart)) {
            harvestEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest end cannot precede start.");
        }
        AquaticCohortPlan draft = new AquaticCohortPlan(cohortName, species,
                growingSite, cultureMethodField.getText().trim(), stockingDate,
                harvestStart, harvestEnd, intendedDestinationField.getText().trim(),
                (AquaticCohortStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedCohortTo(AquaticCohortStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved aquatic cohort plan first.", FarmUi.Tone.INFO);
            return;
        }
        moveCohortToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, AquaticCohortStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveCohortToStage(Long id, AquaticCohortStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current cohort action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved cohort.",
                    FarmUi.Tone.WARNING);
            return;
        }
        AquaticCohortPlan source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved cohort is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getCohortName() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getCohortName() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<AquaticCohortPlan, Void>() {
            @Override protected AquaticCohortPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    AquaticCohortPlan saved = get();
                    if (Objects.equals(editingId, saved.getId())) loadCohort(saved);
                    showFeedback(saved.getCohortName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Aquatic cohort plan “" + saved.getCohortName()
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

    private final class CohortDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            AquaticCohortPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new CohortTransferable(
                    new StageDrag(AquaticCohortTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final AquaticCohortStage stage;

        private StageDropHandler(AquaticCohortStage stage) { this.stage = stage; }

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
                moveCohortToStage(drag.plan().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(AquaticCohortTab source, long revision,
                             AquaticCohortPlan plan) { }

    private record CohortTransferable(StageDrag drag) implements Transferable {
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
        host.setStatus("Aquatic cohort plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, text, tone);
    }

    private FormState formState() {
        return new FormState(cohortNameField.getText(), speciesField.getText(),
                growingSiteField.getText(), cultureMethodField.getText(),
                stockingDateField.getText(), harvestStartField.getText(),
                harvestEndField.getText(), intendedDestinationField.getText(),
                (AquaticCohortStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || cohortNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(cohortNameField, speciesField,
                growingSiteField, cultureMethodField, stockingDateField,
                harvestStartField, harvestEndField, intendedDestinationField,
                stageField, notesField)) control.setEnabled(editable);
        newButton.setEnabled(editable);
        nextCohortButton.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
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

    private record FormState(String cohortName, String species,
                             String growingSite, String cultureMethod,
                             String stockingDate, String harvestStart,
                             String harvestEnd, String intendedDestination,
                             AquaticCohortStage stage,
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
