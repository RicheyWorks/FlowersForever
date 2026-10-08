package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.CannabisCultivationBatch;
import com.flowerfarm.model.CannabisCultivationStage;
import com.flowerfarm.service.CannabisCultivationService;

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
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Internal cultivation planning records, separate from regulated traceability. */
public final class CannabisCultivationTab implements FlowerFarmTab {

    private static final DataFlavor BATCH_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StageDrag.class.getName(),
            "Saved cultivation planning batch");

    private static final String[] COLUMNS = {
            "Batch", "Cultivar", "Area", "Plants", "Stage", "Start", "Planned harvest"
    };

    private final CannabisCultivationService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JPanel listState;
    private CardLayout listCards;
    private JTextField nameField;
    private JTextField cultivarField;
    private JTextField areaField;
    private JTextField plantCountField;
    private JTextField startDateField;
    private JTextField plannedHarvestField;
    private JComboBox<CannabisCultivationStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private final Map<CannabisCultivationStage, JButton> stageTargets =
            new EnumMap<>(CannabisCultivationStage.class);
    private JLabel feedback;
    private JLabel accessBadge;
    private final NotebookSaveRecovery saveRecovery = new NotebookSaveRecovery();
    private List<CannabisCultivationBatch> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private boolean selectionAdjusting;
    private boolean refreshQueued;
    private boolean failedSave;
    private boolean preserveSaveFeedback;
    private long listRevision;

    public CannabisCultivationTab(CannabisCultivationService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public CannabisCultivationTab(CannabisCultivationService service, TabHost host,
                                  FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public String getTabTitle() {
        return "Cultivation";
    }

    @Override
    public String getDescription() {
        return "Internal batch planning notebook without regulated tracking or reporting";
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
        new SwingWorker<List<CannabisCultivationBatch>, Void>() {
            @Override
            protected List<CannabisCultivationBatch> doInBackground() {
                return service.listAll();
            }

            @Override
            protected void done() {
                try {
                    List<CannabisCultivationBatch> batches = get();
                    boolean recovering = saveRecovery.needsRefresh();
                    showBatches(batches == null ? List.of() : batches);
                    saveRecovery.refreshed();
                    if (recovering) {
                        showFeedback(hasUnsavedDraft()
                                        ? "Batches refreshed; unsaved draft kept."
                                        : "Batches refreshed.", FarmUi.Tone.SUCCESS);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load batches. Try Refresh.", FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load batches. Try Refresh.", FarmUi.Tone.DANGER);
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

    /** Allows the frame's close guard to protect a local, unsaved planning draft. */
    public String applicationExitWarning() {
        if (saving) {
            return "Cultivation has a batch save in progress.";
        }
        return hasUnsavedDraft()
                ? "Cultivation has an unsaved batch planning draft."
                : null;
    }

    public boolean hasPendingSave() {
        return saving;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("cultivation.page");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Cultivation notebook",
                "Plan batches and review basic progress in one place.", accessBadge),
                BorderLayout.NORTH);

        JButton harvestLink = FarmUi.secondaryButton("Open Harvest Log");
        harvestLink.setName("cultivation.openHarvest");
        harvestLink.addActionListener(event -> {
            host.selectTab("Harvest Log");
            host.setStatus("Opened the general Harvest Log; your cultivation draft remains here.");
        });
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("cultivation.openOrders");
        ordersLink.addActionListener(event -> {
            host.selectTab("Customers & Orders");
            host.setStatus("Opened general customer and order records; your cultivation draft remains here.");
        });
        JButton soilRecipesLink = FarmUi.secondaryButton("Open soil recipes");
        soilRecipesLink.setName("cultivation.openSoilRecipes");
        soilRecipesLink.getAccessibleContext().setAccessibleDescription(
                "Open Field science for Subcool's Super Soil and other sourced grower recipes.");
        soilRecipesLink.addActionListener(event -> {
            host.selectTab("Field science");
            host.setStatus("Opened sourced grower recipes; your cultivation draft remains here.");
        });
        JPanel scope = FarmUi.section("Planning only",
                "This notebook does not provide regulated tracking, compliance reports, or a regulated sales system.",
                FarmUi.toolbar(harvestLink, ordersLink, soilRecipesLink));

        JPanel work = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        work.setOpaque(false);
        work.add(scope, BorderLayout.NORTH);
        work.add(buildBatchList(), BorderLayout.CENTER);
        work.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(work,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("cultivation.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        FarmUi.revealFocusedControls(scroll);
        clearForm();
        updateActions();
    }

    private JComponent buildBatchList() {
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setName("cultivation.batchTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Cultivation planning batches");
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
        tableScroll.setPreferredSize(new Dimension(740, 150));
        JPanel empty = new JPanel(new BorderLayout(0, 3));
        empty.setName("cultivation.emptyBatches");
        empty.setOpaque(false);
        empty.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(FarmUi.borderColor()),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)));
        JLabel emptyTitle = new JLabel("No planning batches yet");
        emptyTitle.setFont(emptyTitle.getFont().deriveFont(java.awt.Font.BOLD));
        empty.add(emptyTitle, BorderLayout.NORTH);
        empty.add(FarmUi.mutedLabel("Create the first batch below; saved batches appear here."),
                BorderLayout.CENTER);
        listCards = new CardLayout();
        listState = new JPanel(listCards);
        listState.setName("cultivation.listState");
        listState.setOpaque(false);
        listState.add(empty, "empty");
        listState.add(tableScroll, "table");
        listState.setPreferredSize(new Dimension(740, 72));
        listCards.show(listState, "empty");
        newButton = FarmUi.primaryButton("New batch");
        newButton.setName("cultivation.new");
        newButton.addActionListener(event -> newBatch());
        refreshButton = FarmUi.secondaryButton("Refresh batches");
        refreshButton.setName("cultivation.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, refreshButton), BorderLayout.NORTH);
        list.add(listState, BorderLayout.CENTER);
        list.add(buildStageTargets(), BorderLayout.SOUTH);
        return FarmUi.section("Saved batches",
                "Select a batch to edit, or drag a saved row to a planning stage.", list);
    }

    private JComponent buildStageTargets() {
        JPanel targets = new JPanel(new GridLayout(0, 4, 6, 6));
        targets.setOpaque(false);
        targets.setName("cultivation.stageTargets");
        for (CannabisCultivationStage stage : CannabisCultivationStage.values()) {
            JButton target = FarmUi.secondaryButton(stage.toString());
            target.setName("cultivation.stageTarget." + stage.name());
            target.setToolTipText("Drop a saved cultivation batch here, or select one and click. "
                    + "The planning stage saves immediately; no harvest or stock record is created.");
            target.getAccessibleContext().setAccessibleName(
                    "Move selected cultivation batch planning stage to " + stage);
            target.setTransferHandler(new StageDropHandler(stage));
            target.addActionListener(event -> moveSelectedBatchTo(stage));
            stageTargets.put(stage, target);
            targets.add(target);
        }
        return FarmUi.section("Move planning stage",
                "Drag a saved row to a stage, or select one and click. "
                        + "Stage changes save immediately in this planning notebook only.", targets);
    }

    private JComponent buildForm() {
        nameField = field("Batch name", "e.g. Greenhouse batch A", 24);
        cultivarField = field("Cultivar", "Optional cultivar name", 24);
        areaField = field("Growing area", "e.g. House 1", 24);
        plantCountField = field("Plant count", "0", 10);
        startDateField = field("Start date", "YYYY-MM-DD", 12);
        plannedHarvestField = field("Planned harvest date", "YYYY-MM-DD", 12);
        stageField = new JComboBox<>(CannabisCultivationStage.values());
        stageField.setName("cultivation.stage");
        stageField.getAccessibleContext().setAccessibleName("Growth stage");
        notesField = new JTextArea(3, 40);
        notesField.setName("cultivation.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Planning notes");
        FarmUi.initialFocus(nameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Batch name *", nameField, nameField);
        addField(fields, 0, 1, "Cultivar", cultivarField, cultivarField);
        addField(fields, 1, 0, "Growing area *", areaField, areaField);
        addField(fields, 1, 1, "Plant count *", plantCountField, plantCountField);
        addField(fields, 2, 0, "Start date *", startDateField, startDateField);
        addField(fields, 2, 1, "Planned harvest date *", plannedHarvestField, plannedHarvestField);
        addField(fields, 3, 0, "Growth stage", stageField, stageField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 3, 1, "Planning notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save batch");
        saveButton.setName("cultivation.save");
        saveButton.addActionListener(event -> saveBatch());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("cultivation.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a batch", FarmUi.Tone.NEUTRAL);
        feedback.setName("cultivation.feedback");
        feedback.getAccessibleContext().setAccessibleName("Cultivation batch status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.SOUTH);
        footer.add(feedback, BorderLayout.NORTH);
        form.add(footer, BorderLayout.SOUTH);
        installDraftTracking();
        return FarmUi.section("Batch details",
                "Planning fields only. Save a new batch or edit the selected batch.", form);
    }

    private void installDraftTracking() {
        DocumentListener listener = new DocumentListener() {
            private void changed() {
                if (!saving && hasUnsavedDraft()) {
                    showFeedback(failedSave
                            ? "Not saved · draft changed and ready to retry"
                            : "Unsaved planning changes", FarmUi.Tone.WARNING);
                }
                updateActions();
            }

            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        };
        for (JTextComponent field : List.<JTextComponent>of(nameField, cultivarField,
                areaField, plantCountField, startDateField, plannedHarvestField, notesField)) {
            field.getDocument().addDocumentListener(listener);
        }
        stageField.addActionListener(event -> {
            if (!saving && hasUnsavedDraft()) {
                showFeedback("Unsaved planning changes", FarmUi.Tone.WARNING);
            }
            updateActions();
        });
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("cultivation." + name.toLowerCase(java.util.Locale.ROOT)
                .replace(' ', '_'));
        field.getAccessibleContext().setAccessibleName(name);
        FarmUi.entryControl(field, placeholder, "Internal planning detail.");
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

    private void showBatches(List<CannabisCultivationBatch> batches) {
        lastView = List.copyOf(batches);
        listRevision++;
        boolean empty = lastView.isEmpty();
        listCards.show(listState, empty ? "empty" : "table");
        listState.setPreferredSize(new Dimension(740, empty ? 72 : 150));
        listState.revalidate();
        selectionAdjusting = true;
        try {
            tableModel.setRowCount(0);
            for (CannabisCultivationBatch batch : lastView) {
                tableModel.addRow(new Object[]{
                        batch.getBatchName(), batch.getCultivar(), batch.getArea(),
                        batch.getPlantCount(), batch.getStage(), batch.getStartDate(),
                        batch.getPlannedHarvestDate()
                });
            }
            restoreSelection();
        } finally {
            selectionAdjusting = false;
        }
        if (editingId != null && !hasUnsavedDraft() && !preserveSaveFeedback) {
            lastView.stream().filter(batch -> editingId.equals(batch.getId()))
                    .findFirst().ifPresent(this::loadBatch);
        }
        preserveSaveFeedback = false;
    }

    private void moveSelectedBatchTo(CannabisCultivationStage stage) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved cultivation batch first.", FarmUi.Tone.INFO);
            return;
        }
        moveBatchToStage(lastView.get(table.convertRowIndexToModel(row)).getId(), stage);
    }

    private boolean canMoveDrag(StageDrag drag, CannabisCultivationStage stage) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.batch() != null
                && drag.batch().getId() != null && stage != null
                && drag.batch().getStage() != stage
                && lastView.stream().anyMatch(batch -> batch == drag.batch());
    }

    private void moveBatchToStage(Long id, CannabisCultivationStage stage) {
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
        CannabisCultivationBatch source = lastView.stream()
                .filter(batch -> Objects.equals(id, batch.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved batch is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (stage == null || source.getStage() == stage) {
            showFeedback(source.getBatchName() + " is already " + source.getStage() + ".",
                    FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Moving " + source.getBatchName() + " to " + stage + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<CannabisCultivationBatch, Void>() {
            @Override protected CannabisCultivationBatch doInBackground() {
                return service.updateStage(id, stage);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    CannabisCultivationBatch saved = get();
                    wrote = true;
                    lastView = saveRecovery.remember(lastView, saved, CannabisCultivationBatch::getId);
                    if (Objects.equals(editingId, saved.getId())) {
                        loadBatch(saved);
                    }
                    preserveSaveFeedback = true;
                    showBatches(lastView);
                    preserveSaveFeedback = true;
                    showFeedback(saved.getBatchName() + " moved to planning stage "
                            + saved.getStage() + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Cultivation batch “" + saved.getBatchName()
                            + "” moved to planning stage " + saved.getStage() + ".");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (wrote) {
                        showSavedRefreshFailure();
                    } else {
                        showFeedback("Planning stage was not changed. Retry the move.",
                                FarmUi.Tone.DANGER);
                    }
                } catch (Exception exception) {
                    if (wrote) {
                        showSavedRefreshFailure();
                    } else {
                        showFeedback("Planning stage was not changed. Retry the move.",
                                FarmUi.Tone.DANGER);
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

    private final class BatchDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) {
                return null;
            }
            CannabisCultivationBatch batch = lastView.get(table.convertRowIndexToModel(row));
            return batch.getId() == null ? null : new BatchTransferable(
                    new StageDrag(CannabisCultivationTab.this, listRevision, batch));
        }
    }

    private final class StageDropHandler extends TransferHandler {
        private final CannabisCultivationStage stage;

        private StageDropHandler(CannabisCultivationStage stage) { this.stage = stage; }

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

    private record StageDrag(CannabisCultivationTab source, long revision,
                             CannabisCultivationBatch batch) { }

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
        CannabisCultivationBatch selected = lastView.get(table.convertRowIndexToModel(row));
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
                table.setRowSelectionInterval(row, row);
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
        nameField.requestFocusInWindow();
    }

    private void clearForm() {
        editingId = null;
        failedSave = false;
        preserveSaveFeedback = false;
        nameField.setText("");
        cultivarField.setText("");
        areaField.setText("");
        plantCountField.setText("0");
        startDateField.setText("");
        plannedHarvestField.setText("");
        stageField.setSelectedItem(CannabisCultivationStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a batch", FarmUi.Tone.NEUTRAL);
    }

    private void loadBatch(CannabisCultivationBatch batch) {
        editingId = batch.getId();
        failedSave = false;
        preserveSaveFeedback = false;
        nameField.setText(batch.getBatchName());
        cultivarField.setText(batch.getCultivar());
        areaField.setText(batch.getArea());
        plantCountField.setText(String.valueOf(batch.getPlantCount()));
        startDateField.setText(String.valueOf(batch.getStartDate()));
        plannedHarvestField.setText(String.valueOf(batch.getPlannedHarvestDate()));
        stageField.setSelectedItem(batch.getStage());
        notesField.setText(batch.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing saved batch “" + batch.getBatchName() + "”", FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the batch save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) {
            return true;
        }
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This batch has unsaved planning changes. Discard them?",
                "Unsaved cultivation draft", JOptionPane.DEFAULT_OPTION,
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
        CannabisCultivationBatch draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving batch…", FarmUi.Tone.INFO);
        new SwingWorker<CannabisCultivationBatch, Void>() {
            @Override
            protected CannabisCultivationBatch doInBackground() {
                return targetId == null ? service.create(draft)
                        : service.update(targetId, draft);
            }

            @Override
            protected void done() {
                boolean wrote = false;
                try {
                    CannabisCultivationBatch saved = get();
                    wrote = true;
                    editingId = saved.getId();
                    lastView = saveRecovery.remember(lastView, saved, CannabisCultivationBatch::getId);
                    failedSave = false;
                    preserveSaveFeedback = true;
                    cleanForm = formState();
                    showBatches(lastView);
                    preserveSaveFeedback = true;
                    showFeedback("Batch saved · " + saved.getBatchName(), FarmUi.Tone.SUCCESS);
                    host.setStatus("Cultivation batch “" + saved.getBatchName() + "” saved.");
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

    private CannabisCultivationBatch readDraft() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            nameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a batch name.");
        }
        String area = areaField.getText().trim();
        if (area.isEmpty()) {
            areaField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a growing area.");
        }
        int plants;
        try {
            plants = Integer.parseInt(plantCountField.getText().trim());
        } catch (NumberFormatException exception) {
            plantCountField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a whole-number plant count.");
        }
        if (plants < 0) {
            plantCountField.requestFocusInWindow();
            throw new IllegalArgumentException("Plant count cannot be negative.");
        }
        LocalDate start = date(startDateField, "start date");
        LocalDate planned = date(plannedHarvestField, "planned harvest date");
        if (planned.isBefore(start)) {
            plannedHarvestField.requestFocusInWindow();
            throw new IllegalArgumentException("Planned harvest cannot precede the start date.");
        }
        return new CannabisCultivationBatch(name, cultivarField.getText().trim(), area,
                plants, start, planned,
                (CannabisCultivationStage) stageField.getSelectedItem(),
                notesField.getText().trim());
    }

    private static LocalDate date(JTextField field, String label) {
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
        failedSave = true;
        showFeedback("Not saved · complete planning draft kept. Check the local data store and retry.",
                FarmUi.Tone.DANGER);
        host.setStatus("Cultivation batch was not saved; its planning draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) {
            FarmUi.updateStatusBadge(feedback, saveRecovery.feedback(text),
                    saveRecovery.needsRefresh() && tone != FarmUi.Tone.DANGER
                            ? FarmUi.Tone.WARNING : tone);
        }
    }

    private FormState formState() {
        return new FormState(nameField.getText(), cultivarField.getText(), areaField.getText(),
                plantCountField.getText(), startDateField.getText(),
                plannedHarvestField.getText(),
                (CannabisCultivationStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || nameField == null) {
            return;
        }
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(nameField, cultivarField, areaField,
                plantCountField, startDateField, plannedHarvestField, stageField, notesField)) {
            control.setEnabled(editable);
        }
        newButton.setEnabled(editable);
        saveButton.setEnabled(editable && !loading);
        discardButton.setEnabled(editable && hasUnsavedDraft());
        refreshButton.setEnabled(!loading && !saving);
        int selectedRow = table.getSelectedRow();
        CannabisCultivationBatch selected = selectedRow >= 0
                && selectedRow < table.getRowCount()
                ? lastView.get(table.convertRowIndexToModel(selectedRow)) : null;
        boolean canMove = editable && !loading && !hasUnsavedDraft()
                && selected != null && selected.getId() != null;
        stageTargets.forEach((stage, target) -> target.setEnabled(
                canMove && selected.getStage() != stage));
        FarmUi.updateStatusBadge(accessBadge,
                canWrite ? "HAND / OWNER · planning edits" : "VIEWER · read only",
                canWrite ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
    }

    private record FormState(String name, String cultivar, String area, String plantCount,
                             String startDate, String plannedHarvestDate,
                             CannabisCultivationStage stage, String notes) {
    }
}
