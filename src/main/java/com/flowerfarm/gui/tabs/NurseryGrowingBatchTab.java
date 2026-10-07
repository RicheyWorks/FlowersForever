package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.NurseryBatch;
import com.flowerfarm.model.NurseryBatchStage;
import com.flowerfarm.model.NurseryPlantForm;
import com.flowerfarm.model.NurserySalesChannel;
import com.flowerfarm.service.NurseryBatchService;

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
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
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

/** Local nursery growing-batch planning, separate from stock and order records. */
public final class NurseryGrowingBatchTab implements FlowerFarmTab {

    private static final DataFlavor BATCH_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved nursery growing batch");

    private final NurseryBatchService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField batchField;
    private JTextField plantField;
    private JTextField cultivarField;
    private JComboBox<NurseryPlantForm> formField;
    private JTextField areaField;
    private JTextField startedDateField;
    private JTextField readyDateField;
    private JTextField countField;
    private JComboBox<NurserySalesChannel> channelField;
    private JComboBox<NurseryBatchStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private final Map<NurseryBatchStage, JButton> stageTargets =
            new EnumMap<>(NurseryBatchStage.class);
    private JLabel feedback;
    private JLabel accessBadge;
    private List<NurseryBatch> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private boolean selectionAdjusting;
    private boolean refreshQueued;
    private long listRevision;

    public NurseryGrowingBatchTab(NurseryBatchService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public NurseryGrowingBatchTab(NurseryBatchService service, TabHost host,
                                  FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public String getTabTitle() {
        return "Growing batches";
    }

    @Override
    public String getDescription() {
        return "Local nursery propagation and ready-date planning";
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
        new SwingWorker<List<NurseryBatch>, Void>() {
            @Override
            protected List<NurseryBatch> doInBackground() {
                return service.list();
            }

            @Override
            protected void done() {
                try {
                    List<NurseryBatch> batches = get();
                    showBatches(batches == null ? List.of() : batches);
                    showFeedback(hasUnsavedDraft()
                                    ? "Growing batches refreshed; unsaved draft kept."
                                    : "Growing batches refreshed.",
                            FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load batches. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load batches. Choose Refresh to retry.",
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
            return "A nursery growing batch save is in progress.";
        }
        return hasUnsavedDraft()
                ? "There is an unsaved nursery batch draft."
                : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("nurseryBatch.page");
        panel.getAccessibleContext().setAccessibleName("Nursery growing batches workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Growing batches",
                        "Plan live plant forms, growing areas, ready dates, and sales channels.",
                        accessBadge),
                BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("nurseryBatch.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("nurseryBatch.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JPanel scope = FarmUi.section("Planning only",
                "Growing batch plans do not add inventory or fulfill orders. "
                        + "Record stock and customer sales in the general tools separately.",
                FarmUi.toolbar(inventoryLink, ordersLink));

        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(buildBatchList(), BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("nurseryBatch.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildBatchList() {
        tableModel = new DefaultTableModel(new String[]{"Batch", "Plant", "Form",
                "Growing area", "Ready", "Started count", "Channel", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("nurseryBatch.planTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Nursery growing batches table");
        table.setTransferHandler(new BatchDragHandler());
        if (!GraphicsEnvironment.isHeadless()) {
            table.setDragEnabled(true);
        }
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !selectionAdjusting) {
                selectBatch();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 82));
        newButton = FarmUi.primaryButton("New growing batch");
        newButton.setName("nurseryBatch.new");
        newButton.addActionListener(event -> newBatch());
        refreshButton = FarmUi.secondaryButton("Refresh batches");
        refreshButton.setName("nurseryBatch.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        list.add(buildStageTargets(), BorderLayout.SOUTH);
        return FarmUi.section("Saved growing batches",
                "Select a row to edit details, or drag a saved batch to a stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new GridLayout(0, 4, 6, 6));
        targets.setOpaque(false);
        targets.setName("nurseryBatch.stageTargets");
        for (NurseryBatchStage stage : NurseryBatchStage.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(stage));
            target.setName("nurseryBatch.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved growing batch here, or select one and click. "
                    + "Stage change saves immediately.");
            target.getAccessibleContext().setAccessibleName(
                    "Move selected growing batch to " + enumLabel(stage));
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedBatchTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move by stage",
                "Drag a saved row onto a stage, or select a row and click. "
                        + "Stage changes save immediately; full edits remain below.", targets);
    }

    private JComponent buildForm() {
        batchField = field("Batch name", "e.g. Spring lavender starts", 24);
        plantField = field("Plant name", "e.g. lavender", 24);
        cultivarField = field("Cultivar", "Optional, e.g. Hidcote", 24);
        formField = new JComboBox<>(NurseryPlantForm.values());
        formField.setName("nurseryBatch.form");
        formField.getAccessibleContext().setAccessibleName("Plant form");
        formField.setRenderer(enumRenderer());
        areaField = field("Growing area", "Optional, e.g. Greenhouse 2", 24);
        countField = field("Starting count", "Optional, e.g. 240", 12);
        startedDateField = field("Started date", "Optional YYYY-MM-DD", 12);
        readyDateField = field("Planned ready date", "Optional YYYY-MM-DD", 12);
        channelField = new JComboBox<>(NurserySalesChannel.values());
        channelField.setName("nurseryBatch.channel");
        channelField.getAccessibleContext().setAccessibleName("Intended sales channel");
        channelField.setRenderer(enumRenderer());
        stageField = new JComboBox<>(NurseryBatchStage.values());
        stageField.setName("nurseryBatch.stage");
        stageField.getAccessibleContext().setAccessibleName("Batch stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("nurseryBatch.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Growing notes");
        FarmUi.initialFocus(batchField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Batch name *", batchField, batchField);
        addField(fields, 0, 1, "Plant name *", plantField, plantField);
        addField(fields, 1, 0, "Cultivar", cultivarField, cultivarField);
        addField(fields, 1, 1, "Plant form", formField, formField);
        addField(fields, 2, 0, "Growing area", areaField, areaField);
        addField(fields, 2, 1, "Starting count", countField, countField);
        addField(fields, 3, 0, "Started date", startedDateField, startedDateField);
        addField(fields, 3, 1, "Planned ready date", readyDateField, readyDateField);
        addField(fields, 4, 0, "Intended sales channel", channelField, channelField);
        addField(fields, 4, 1, "Batch stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 5, 0, 2, "Growing notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("nurseryBatch.save");
        saveButton.addActionListener(event -> saveBatch());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("nurseryBatch.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a growing batch",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("nurseryBatch.feedback");
        feedback.getAccessibleContext().setAccessibleName("Growing batch status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Batch details",
                "Local live-plant planning. Save a new batch or edit the selected one.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("nurseryBatch." + name.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_"));
        field.getAccessibleContext().setAccessibleName(name);
        FarmUi.entryControl(field, placeholder, "Local planning detail.");
        return field;
    }

    private static void addField(JPanel target, int row, int column, String label,
                                 JComponent control, JComponent labelTarget) {
        addField(target, row, column, 1, label, control, labelTarget);
    }

    private static void addField(JPanel target, int row, int column, int span,
                                 String label, JComponent control, JComponent labelTarget) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = column;
        cell.gridy = row;
        cell.gridwidth = span;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.anchor = GridBagConstraints.NORTHWEST;
        cell.insets = new Insets(0, 4, 8, 4);
        target.add(FarmUi.entryField(label, control, labelTarget, '\0'), cell);
    }

    private void openGeneral(String title) {
        host.selectTab(title);
        host.setStatus("Opened general " + title + "; your growing batch draft remains here.");
    }

    private void showBatches(List<NurseryBatch> batches) {
        lastView = List.copyOf(batches);
        listRevision++;
        selectionAdjusting = true;
        try {
            tableModel.setRowCount(0);
            for (NurseryBatch batch : lastView) {
                tableModel.addRow(new Object[]{batch.getBatchName(), batch.getPlantName(),
                        enumLabel(batch.getForm()), batch.getGrowingArea(),
                        batch.getPlannedReadyDate() == null
                                ? "" : batch.getPlannedReadyDate(),
                        batch.getStartingCount() == null ? "" : batch.getStartingCount(),
                        enumLabel(batch.getChannel()), enumLabel(batch.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjusting = false;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(batch -> editingId.equals(batch.getId()))
                    .findFirst().ifPresent(this::loadBatch);
        }
    }

    private void moveSelectedBatchTo(NurseryBatchStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved growing batch first.", FarmUi.Tone.INFO);
            return;
        }
        moveBatchToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, NurseryBatchStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.batch() != null
                && drag.batch().getId() != null && stage != null
                && drag.batch().getStage() != stage
                && lastView.stream().anyMatch(batch -> batch == drag.batch());
    }

    private void moveBatchToStage(Long id, NurseryBatchStage stage) {
        if (!canWrite) {
            return;
        }
        if (saving || loading) {
            showFeedback("Wait for the current batch action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before moving a saved batch.",
                    FarmUi.Tone.WARNING);
            return;
        }
        NurseryBatch source = lastView.stream()
                .filter(batch -> Objects.equals(id, batch.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved batch is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getBatchName() + " is already " + enumLabel(source.getStage())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getBatchName() + " to " + enumLabel(stage) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<NurseryBatch, Void>() {
            @Override protected NurseryBatch doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    NurseryBatch saved = get();
                    if (Objects.equals(editingId, saved.getId())) {
                        loadBatch(saved);
                    }
                    showFeedback(saved.getBatchName() + " moved to "
                            + enumLabel(saved.getStage()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Growing batch “" + saved.getBatchName()
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

    private final class BatchDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) {
                return null;
            }
            NurseryBatch batch = lastView.get(table.convertRowIndexToModel(row));
            return batch.getId() == null ? null : new BatchTransferable(
                    new StageDrag(NurseryGrowingBatchTab.this, listRevision, batch));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final NurseryBatchStage stage;

        private StageDropHandler(NurseryBatchStage stage) { this.stage = stage; }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(BATCH_FLAVOR)) {
                return false;
            }
            try {
                return canMoveDrag((StageDrag) support.getTransferable()
                        .getTransferData(BATCH_FLAVOR), stage);
            } catch (UnsupportedFlavorException | IOException | ClassCastException exception) {
                return false;
            }
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                StageDrag drag = (StageDrag) support.getTransferable()
                        .getTransferData(BATCH_FLAVOR);
                moveBatchToStage(drag.batch().getId(), stage);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StageDrag(NurseryGrowingBatchTab source, long revision,
                             NurseryBatch batch) { }

    private record BatchTransferable(StageDrag drag) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{BATCH_FLAVOR};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return BATCH_FLAVOR.equals(flavor);
        }

        @Override public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return drag;
        }
    }

    private void selectBatch() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= lastView.size()) {
            return;
        }
        NurseryBatch selected = lastView.get(table.convertRowIndexToModel(row));
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
        loadBatch(selected);
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

    private void newBatch() {
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
        batchField.requestFocusInWindow();
    }

    private void clearForm() {
        editingId = null;
        batchField.setText("");
        plantField.setText("");
        cultivarField.setText("");
        formField.setSelectedItem(NurseryPlantForm.UNSPECIFIED);
        areaField.setText("");
        startedDateField.setText("");
        readyDateField.setText("");
        countField.setText("");
        channelField.setSelectedItem(NurserySalesChannel.UNDECIDED);
        stageField.setSelectedItem(NurseryBatchStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a growing batch", FarmUi.Tone.NEUTRAL);
    }

    private void loadBatch(NurseryBatch batch) {
        editingId = batch.getId();
        batchField.setText(batch.getBatchName());
        plantField.setText(batch.getPlantName());
        cultivarField.setText(batch.getCultivar());
        formField.setSelectedItem(batch.getForm());
        areaField.setText(batch.getGrowingArea());
        startedDateField.setText(batch.getStartedDate() == null
                ? "" : batch.getStartedDate().toString());
        readyDateField.setText(batch.getPlannedReadyDate() == null
                ? "" : batch.getPlannedReadyDate().toString());
        countField.setText(batch.getStartingCount() == null
                ? "" : batch.getStartingCount().toString());
        channelField.setSelectedItem(batch.getChannel());
        stageField.setSelectedItem(batch.getStage());
        notesField.setText(batch.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing saved growing batch “" + batch.getBatchName() + "”",
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
                "This growing batch has unsaved planning changes. Discard them?",
                "Unsaved growing batch draft", JOptionPane.DEFAULT_OPTION,
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
        lastView.stream().filter(batch -> editingId.equals(batch.getId()))
                .findFirst().ifPresentOrElse(this::loadBatch, this::clearForm);
    }

    private void saveBatch() {
        if (!canWrite || saving || loading) {
            return;
        }
        NurseryBatch draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving growing batch…", FarmUi.Tone.INFO);
        new SwingWorker<NurseryBatch, Void>() {
            @Override
            protected NurseryBatch doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override
            protected void done() {
                boolean wrote = false;
                try {
                    NurseryBatch saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Plan saved · " + saved.getBatchName(), FarmUi.Tone.SUCCESS);
                    host.setStatus("Growing batch “" + saved.getBatchName() + "” saved.");
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

    private NurseryBatch readDraft() {
        String batchName = batchField.getText().trim();
        if (batchName.isEmpty()) {
            batchField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a batch name.");
        }
        String plantName = plantField.getText().trim();
        if (plantName.isEmpty()) {
            plantField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a plant name.");
        }
        Integer startingCount = null;
        String countText = countField.getText().trim();
        if (!countText.isEmpty()) {
            try {
                startingCount = Integer.valueOf(countText);
            } catch (NumberFormatException exception) {
                countField.requestFocusInWindow();
                throw new IllegalArgumentException(
                        "Enter starting count as a whole number, or leave it blank.");
            }
        }
        LocalDate started = date(startedDateField, "started date");
        LocalDate ready = date(readyDateField, "planned ready date");
        if (started != null && ready != null && ready.isBefore(started)) {
            readyDateField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned ready date cannot precede started date.");
        }
        return new NurseryBatch(batchName, plantName, cultivarField.getText().trim(),
                (NurseryPlantForm) formField.getSelectedItem(), areaField.getText().trim(),
                started, ready, startingCount,
                (NurserySalesChannel) channelField.getSelectedItem(),
                (NurseryBatchStage) stageField.getSelectedItem(), notesField.getText().trim());
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

    private void showSaveFailure() {
        showFeedback("Not saved · complete planning draft kept. "
                        + "Check the local data store and retry.",
                FarmUi.Tone.DANGER);
        host.setStatus("Growing batch plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) {
            FarmUi.updateStatusBadge(feedback, text, tone);
        }
    }

    private FormState formState() {
        return new FormState(batchField.getText(), plantField.getText(),
                cultivarField.getText(), (NurseryPlantForm) formField.getSelectedItem(),
                areaField.getText(), startedDateField.getText(), readyDateField.getText(),
                countField.getText(),
                (NurserySalesChannel) channelField.getSelectedItem(),
                (NurseryBatchStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || batchField == null) {
            return;
        }
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(batchField, plantField, cultivarField,
                formField, areaField, startedDateField, readyDateField, countField,
                channelField, stageField, notesField)) {
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
        if (value == NurseryPlantForm.STARTS_OR_PLUGS) {
            return "Starts or plugs";
        }
        if (value == NurseryPlantForm.BULBS_OR_CUTTINGS) {
            return "Bulbs or cuttings";
        }
        String words = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private record FormState(String batchName, String plantName, String cultivar,
                             NurseryPlantForm form, String growingArea, String startedDate,
                             String plannedReadyDate, String startingCount,
                             NurserySalesChannel channel, NurseryBatchStage stage, String notes) {
    }

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
