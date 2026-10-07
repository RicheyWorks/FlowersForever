package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.VineyardBlockPlan;
import com.flowerfarm.model.VineyardBlockStage;
import com.flowerfarm.model.VineyardDestination;
import com.flowerfarm.service.VineyardBlockPlanService;

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

/** Vineyard block and planned harvest windows, separate from stock and orders. */
public final class VineyardBlockPlanTab implements FlowerFarmTab {

    private static final DataFlavor BLOCK_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved vineyard block");

    private final VineyardBlockPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<VineyardBlockStage, JButton> stageTargets =
            new EnumMap<>(VineyardBlockStage.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField blockField;
    private JTextField locationField;
    private JTextField grapeField;
    private JTextField acresField;
    private JTextField harvestStartField;
    private JTextField harvestEndField;
    private JComboBox<VineyardDestination> destinationField;
    private JComboBox<VineyardBlockStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<VineyardBlockPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public VineyardBlockPlanTab(VineyardBlockPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public VineyardBlockPlanTab(VineyardBlockPlanService service, TabHost host,
                                FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Vineyard blocks"; }

    @Override public String getDescription() {
        return "Local vineyard block planning and shared record links";
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
        new SwingWorker<List<VineyardBlockPlan>, Void>() {
            @Override protected List<VineyardBlockPlan> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<VineyardBlockPlan> plans = get();
                    showPlans(plans == null ? List.of() : plans);
                    showFeedback(hasUnsavedDraft()
                                    ? "Vineyard blocks refreshed; unsaved draft kept."
                                    : "Vineyard blocks refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load blocks. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load blocks. Choose Refresh to retry.",
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
        if (saving) return "A vineyard block save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved vineyard block draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("vineyardBlock.page");
        panel.getAccessibleContext().setAccessibleName("Vineyard block plans workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Vineyard block plans",
                "Plan grape blocks, harvest windows, and intended destinations.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("vineyardBlock.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("vineyardBlock.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("vineyardBlock.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Planning only",
                "Block plans and dates do not create harvest lots, stock, winery deliveries, "
                        + "or orders. Use shared records separately.",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildPlanList(), BorderLayout.CENTER);
        listAndTargets.add(buildStageTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("vineyardBlock.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildPlanList() {
        tableModel = new DefaultTableModel(new String[]{"Block", "Location", "Grape",
                "Acres", "Harvest start", "Harvest end", "Destination", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("vineyardBlock.planTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Vineyard block plans table");
        table.setTransferHandler(new BlockDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectPlan();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New vineyard block");
        newButton.setName("vineyardBlock.new");
        newButton.addActionListener(event -> newPlan());
        refreshButton = FarmUi.secondaryButton("Refresh blocks");
        refreshButton.setName("vineyardBlock.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved vineyard blocks",
                "Select a row to edit details, or drag a saved block to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                VineyardBlockStage.values().length, 8, 0));
        targets.setName("vineyardBlock.stageTargets");
        targets.setOpaque(false);
        for (VineyardBlockStage stage : VineyardBlockStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("vineyardBlock.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved block here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Move selected block to "
                    + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedPlanTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move a saved block",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        blockField = field("Block name", "e.g. East slope", 24);
        locationField = field("Location", "Optional field or parcel", 24);
        grapeField = field("Grape variety", "e.g. Pinot noir", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        harvestStartField = field("Planned harvest start", "Optional YYYY-MM-DD", 12);
        harvestEndField = field("Planned harvest end", "Optional YYYY-MM-DD", 12);
        destinationField = new JComboBox<>(VineyardDestination.values());
        destinationField.setName("vineyardBlock.destination");
        destinationField.getAccessibleContext().setAccessibleName("Intended destination");
        destinationField.setRenderer(enumRenderer());
        stageField = new JComboBox<>(VineyardBlockStage.values());
        stageField.setName("vineyardBlock.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("vineyardBlock.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Block notes");
        FarmUi.initialFocus(blockField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Block name *", blockField, blockField);
        addField(fields, 0, 1, "Grape variety *", grapeField, grapeField);
        addField(fields, 1, 0, "Location", locationField, locationField);
        addField(fields, 1, 1, "Acres", acresField, acresField);
        addField(fields, 2, 0, "Planned harvest start", harvestStartField,
                harvestStartField);
        addField(fields, 2, 1, "Planned harvest end", harvestEndField,
                harvestEndField);
        addField(fields, 3, 0, "Intended destination", destinationField,
                destinationField);
        addField(fields, 3, 1, "Planning stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 4, 0, "Block notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("vineyardBlock.save");
        saveButton.addActionListener(event -> savePlan());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("vineyardBlock.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a vineyard block",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("vineyardBlock.feedback");
        feedback.getAccessibleContext().setAccessibleName("Vineyard block plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Plan details",
                "Local planning fields. Save a new block or edit the selected one.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("vineyardBlock." + name.toLowerCase(Locale.ROOT)
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
        host.setStatus("Opened general " + title + "; your vineyard block draft remains here.");
    }

    private void showPlans(List<VineyardBlockPlan> plans) {
        lastView = List.copyOf(plans);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (VineyardBlockPlan plan : lastView) {
                tableModel.addRow(new Object[]{plan.getBlockName(), plan.getLocation(),
                        plan.getGrapeVariety(),
                        plan.getAcres() == null ? ""
                                : plan.getAcres().stripTrailingZeros().toPlainString(),
                        plan.getPlannedHarvestStart() == null ? ""
                                : plan.getPlannedHarvestStart(),
                        plan.getPlannedHarvestEnd() == null ? ""
                                : plan.getPlannedHarvestEnd(),
                        enumLabel(plan.getDestination()), enumLabel(plan.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadPlan);
        }
        updateActions();
    }

    private void selectPlan() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        VineyardBlockPlan selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadPlan(selected);
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

    private void newPlan() {
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        blockField.requestFocusInWindow();
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        blockField.setText("");
        locationField.setText("");
        grapeField.setText("");
        acresField.setText("");
        harvestStartField.setText("");
        harvestEndField.setText("");
        destinationField.setSelectedItem(VineyardDestination.UNDECIDED);
        stageField.setSelectedItem(VineyardBlockStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a vineyard block", FarmUi.Tone.NEUTRAL);
    }

    private void loadPlan(VineyardBlockPlan plan) {
        editingId = plan.getId();
        blockField.setText(plan.getBlockName());
        locationField.setText(plan.getLocation());
        grapeField.setText(plan.getGrapeVariety());
        acresField.setText(plan.getAcres() == null ? "" : plan.getAcres().toPlainString());
        harvestStartField.setText(plan.getPlannedHarvestStart() == null
                ? "" : plan.getPlannedHarvestStart().toString());
        harvestEndField.setText(plan.getPlannedHarvestEnd() == null
                ? "" : plan.getPlannedHarvestEnd().toString());
        destinationField.setSelectedItem(plan.getDestination());
        stageField.setSelectedItem(plan.getStage());
        notesField.setText(plan.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing saved vineyard block “" + plan.getBlockName() + "”",
                FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the block save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This vineyard block has unsaved planning changes. Discard them?",
                "Unsaved vineyard block draft", JOptionPane.DEFAULT_OPTION,
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
                .findFirst().ifPresentOrElse(this::loadPlan, this::clearForm);
    }

    private void savePlan() {
        if (!canWrite || saving || loading) return;
        VineyardBlockPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving vineyard block…", FarmUi.Tone.INFO);
        new SwingWorker<VineyardBlockPlan, Void>() {
            @Override protected VineyardBlockPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    VineyardBlockPlan saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Plan saved · " + saved.getBlockName(), FarmUi.Tone.SUCCESS);
                    host.setStatus("Vineyard block “" + saved.getBlockName() + "” saved.");
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

    private VineyardBlockPlan readDraft() {
        String block = blockField.getText().trim();
        if (block.isEmpty()) {
            blockField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a block name.");
        }
        String grape = grapeField.getText().trim();
        if (grape.isEmpty()) {
            grapeField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a grape variety.");
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
        LocalDate start = date(harvestStartField, "planned harvest start");
        LocalDate end = date(harvestEndField, "planned harvest end");
        if (start != null && end != null && end.isBefore(start)) {
            harvestEndField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest end cannot precede start.");
        }
        VineyardBlockPlan draft = new VineyardBlockPlan(block, locationField.getText().trim(),
                grape, acres, start, end,
                (VineyardDestination) destinationField.getSelectedItem(),
                (VineyardBlockStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void moveSelectedPlanTo(VineyardBlockStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved vineyard block first.", FarmUi.Tone.INFO);
            return;
        }
        movePlanToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, VineyardBlockStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && stage != null
                && drag.plan().getStage() != stage
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void movePlanToStage(Long id, VineyardBlockStage stage) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current block action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved block.",
                    FarmUi.Tone.WARNING);
            return;
        }
        VineyardBlockPlan source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved block is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getBlockName() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getBlockName() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<VineyardBlockPlan, Void>() {
            @Override protected VineyardBlockPlan doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    VineyardBlockPlan saved = get();
                    if (Objects.equals(editingId, saved.getId())) loadPlan(saved);
                    showFeedback(saved.getBlockName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Vineyard block “" + saved.getBlockName()
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

    private final class BlockDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            VineyardBlockPlan plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new BlockTransferable(
                    new StageDrag(VineyardBlockPlanTab.this, listRevision, plan));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final VineyardBlockStage stage;

        private StageDropHandler(VineyardBlockStage stage) { this.stage = stage; }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(BLOCK_FLAVOR)) return false;
            try {
                return canMoveDrag((StageDrag) support.getTransferable()
                        .getTransferData(BLOCK_FLAVOR), stage);
            } catch (UnsupportedFlavorException | IOException | ClassCastException exception) {
                return false;
            }
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                StageDrag drag = (StageDrag) support.getTransferable()
                        .getTransferData(BLOCK_FLAVOR);
                movePlanToStage(drag.plan().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(VineyardBlockPlanTab source, long revision,
                             VineyardBlockPlan plan) { }

    private record BlockTransferable(StageDrag drag) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{BLOCK_FLAVOR};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return BLOCK_FLAVOR.equals(flavor);
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
        host.setStatus("Vineyard block plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, text, tone);
    }

    private FormState formState() {
        return new FormState(blockField.getText(), locationField.getText(),
                grapeField.getText(), acresField.getText(), harvestStartField.getText(),
                harvestEndField.getText(),
                (VineyardDestination) destinationField.getSelectedItem(),
                (VineyardBlockStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || blockField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(blockField, locationField, grapeField,
                acresField, harvestStartField, harvestEndField, destinationField,
                stageField, notesField)) control.setEnabled(editable);
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

    private record FormState(String block, String location, String grape, String acres,
                             String harvestStart, String harvestEnd,
                             VineyardDestination destination, VineyardBlockStage stage,
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
