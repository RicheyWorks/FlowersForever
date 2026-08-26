package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.gui.presenter.HarvestLogPresenter;
import com.flowerfarm.gui.view.HarvestBatchView;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.HarvestService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harvest log tab — add / edit / filter history, inventory-aware updates,
 * season totals, and CSV export for Kitsap market prep.
 */
public class HarvestLogTab implements FlowerFarmTab {

    private final HarvestLogPresenter presenter;
    private final TabHost host;

    private JPanel panel;
    private DefaultTableModel tableModel;
    private JTable table;

    private JTextField dateField;
    private JTextField cropField;
    private JComboBox<String> cropSuggest;
    private JTextField qtyField;
    private JTextField unitField;
    private JTextField bedField;
    private JTextField notesField;

    private JTextField filterCropField;
    private JTextField filterBedField;
    private JTextField filterNotesField;
    private JTextField filterFromField;
    private JTextField filterToField;
    private JLabel filterStatusLabel;
    private JLabel accessLabel;
    private JLabel entryStatusLabel;
    private JCheckBox filterTotalsOnly;

    private JTextArea totalsArea;
    private JTextArea batchArea;
    private JTextField batchDateField;
    private JTextField batchUnitField;
    private JTextField batchBedField;
    private JTextField batchNotesField;
    private JLabel batchStatusLabel;
    private JComboBox<String> unitSuggest;
    private JButton batchBtn;
    private JButton fillSampleBtn;
    private JButton addBtn;
    private JButton loadBtn;
    private JButton saveBtn;
    private JCheckBox keepContextBox;
    private JComboBox<String> entryTools;
    private JButton runEntryToolBtn;
    private JButton refreshHistoryBtn;
    private JComboBox<String> reportTools;
    private JButton createReportBtn;
    private JLabel reportStatusLabel;
    private JLabel newSkuStatusLabel;
    private JButton reviewNewSkuBtn;
    private JPanel harvestHandoffBanner;
    private JLabel harvestHandoffLabel;
    private JButton returnFromHarvestBtn;
    private JTabbedPane workbench;
    private Long editingId; // null = add mode
    private Long pendingHarvestSelectionId;
    private Long handoffReturnHarvestId;
    private String handoffReturnTab;
    private String handoffReturnLabel;
    private boolean canWrite = true;
    private boolean batchLogging;
    private boolean savingEntry;
    private boolean deletingEntry;
    private boolean refreshingHistory;
    private boolean historyRefreshQueued;
    private boolean runningReport;
    private String historyRefreshFailure;
    private List<HarvestEntry> lastView = List.of();
    private Map<String, Double> filteredTotals = Map.of();
    private Map<String, Double> allTotals = Map.of();
    private List<String> newInventoryProducts = List.of();
    private CardLayout tableCardLayout;
    private JPanel tableState;

    public HarvestLogTab(HarvestService harvestService, TabHost host) {
        this.presenter = new HarvestLogPresenter(harvestService);
        this.host = host;
    }

    @Override public String getTabTitle() { return "Harvest Log"; }

    @Override
    public String getDescription() {
        return "Log, edit, filter, and export harvests — inventory stays in sync";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    @Override
    public void initialize() {
        refreshData();
    }

    @Override
    public void refreshData() {
        startHistoryRefresh();
    }

    void startHistoryRefresh() {
        if (refreshingHistory) {
            historyRefreshQueued = true;
            showHistoryRefreshState("Refresh queued · current harvest load finishing",
                    "One newer harvest snapshot will load with the latest filters.",
                    FarmUi.Tone.INFO);
            return;
        }
        HarvestLogPresenter.FilterDraft request = currentFilterDraft();
        refreshingHistory = true;
        historyRefreshFailure = null;
        refreshHistoryBtn.setEnabled(false);
        updateEntryActionAvailability();
        updateEntryToolActions();
        updateReportActions();
        showHistoryRefreshState("Refreshing harvest history…",
                "Loading filtered harvests, production totals, and recent crops in the background.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Refreshing harvest history…");
        }

        new SwingWorker<HistorySnapshot, Void>() {
            @Override
            protected HistorySnapshot doInBackground() {
                HarvestLogPresenter.HarvestView view = presenter.filter(request);
                return new HistorySnapshot(view,
                        stableTotals(presenter.totalsFor(view.sourceEntries())),
                        stableTotals(presenter.totalsByCrop()));
            }

            @Override
            protected void done() {
                try {
                    HistorySnapshot snapshot = get();
                    if (!historyRefreshQueued) {
                        applyHistorySnapshot(snapshot);
                    } else if (host != null) {
                        host.setStatus("A newer harvest refresh is queued; discarding the older snapshot.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (!historyRefreshQueued) {
                        failHistoryRefresh(exception);
                    }
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (!historyRefreshQueued) {
                        failHistoryRefresh(exception.getCause() == null
                                ? exception : exception.getCause());
                    }
                } catch (RuntimeException exception) {
                    if (!historyRefreshQueued) {
                        failHistoryRefresh(exception);
                    }
                } finally {
                    finishHistoryRefresh();
                }
            }
        }.execute();
    }

    private HarvestLogPresenter.FilterDraft currentFilterDraft() {
        return new HarvestLogPresenter.FilterDraft(
                filterCropField == null ? "" : filterCropField.getText(),
                filterBedField == null ? "" : filterBedField.getText(),
                filterNotesField == null ? "" : filterNotesField.getText(),
                filterFromField == null ? "" : filterFromField.getText(),
                filterToField == null ? "" : filterToField.getText());
    }

    private void applyHistorySnapshot(HistorySnapshot snapshot) {
        Long selectedId = pendingHarvestSelectionId != null
                ? pendingHarvestSelectionId : selectedHarvestId();
        FarmUi.clearFieldError(filterFromField);
        FarmUi.clearFieldError(filterToField);
        lastView = snapshot.view().sourceEntries();
        filteredTotals = snapshot.filteredTotals();
        allTotals = snapshot.allTotals();
        tableModel.setRowCount(0);
        for (HarvestLogPresenter.HarvestRow row : snapshot.view().rows()) {
            tableModel.addRow(row.cells());
        }
        rebuildTotals();
        refreshCropSuggestions();
        showHistoryRefreshState(String.format("%d entr%s · %.0f total",
                        snapshot.view().rows().size(),
                        snapshot.view().rows().size() == 1 ? "y" : "ies",
                        snapshot.view().quantity()),
                "Harvest history and production totals are current.", FarmUi.Tone.SUCCESS);
        if (tableCardLayout != null && tableState != null) {
            tableCardLayout.show(tableState,
                    snapshot.view().rows().isEmpty() ? "empty" : "table");
        }
        boolean restored = restoreHarvestSelection(selectedId);
        if (pendingHarvestSelectionId != null
                && pendingHarvestSelectionId.equals(selectedId)) {
            Long requestedId = pendingHarvestSelectionId;
            pendingHarvestSelectionId = null;
            if (host != null) {
                host.setStatus(restored
                        ? "Opened harvest #" + requestedId
                                + " from Activity History; filters were cleared."
                        : "Harvest #" + requestedId
                                + " is no longer available in the current history.");
            }
        }
    }

    /** Reveal one saved harvest without allowing existing filters to hide it. */
    public void showHarvest(long harvestId) {
        showHarvest(harvestId, null, null);
    }

    /** Reveal one saved harvest and retain a route back to its source workflow. */
    public void showHarvest(long harvestId, String returnTab, String returnLabel) {
        getUIComponent();
        configureHarvestHandoff(harvestId, returnTab, returnLabel);
        pendingHarvestSelectionId = harvestId;
        filterCropField.setText("");
        filterBedField.setText("");
        filterNotesField.setText("");
        filterFromField.setText("");
        filterToField.setText("");
        filterTotalsOnly.setSelected(false);
        startHistoryRefresh();
        table.requestFocusInWindow();
    }

    /** Open the retained single-entry form without clearing unfinished work. */
    public void showEntryFrom(String returnTab, String returnLabel) {
        getUIComponent();
        configureHarvestEntryHandoff(returnTab, returnLabel);
        workbench.setSelectedIndex(0);
        cropField.requestFocusInWindow();
        if (host != null) {
            host.setStatus("Opened retained Harvest Log entry work from "
                    + handoffReturnLabel + ".");
        }
    }

    private void configureHarvestEntryHandoff(String returnTab, String returnLabel) {
        String safeTab = returnTab == null ? "" : returnTab.trim();
        String safeLabel = returnLabel == null ? "" : returnLabel.trim();
        if (safeTab.isEmpty()) {
            handoffReturnHarvestId = null;
            handoffReturnTab = null;
            handoffReturnLabel = null;
            harvestHandoffBanner.setVisible(false);
            return;
        }
        handoffReturnHarvestId = null;
        handoffReturnTab = safeTab;
        handoffReturnLabel = safeLabel.isEmpty() ? safeTab : safeLabel;
        String context = "Harvest entry opened from " + handoffReturnLabel;
        harvestHandoffLabel.setText(context);
        harvestHandoffLabel.setToolTipText(
                "The current entry draft and saved-harvest selection were preserved.");
        harvestHandoffLabel.getAccessibleContext().setAccessibleDescription(context);
        returnFromHarvestBtn.setText("Return to " + handoffReturnLabel);
        returnFromHarvestBtn.setToolTipText(
                "Return without rebuilding the Dashboard or daily checklist.");
        returnFromHarvestBtn.getAccessibleContext().setAccessibleName(
                "Return to " + handoffReturnLabel + " from harvest entry");
        harvestHandoffBanner.setVisible(true);
    }

    private void configureHarvestHandoff(long harvestId,
                                         String returnTab, String returnLabel) {
        String safeTab = returnTab == null ? "" : returnTab.trim();
        String safeLabel = returnLabel == null ? "" : returnLabel.trim();
        if (safeTab.isEmpty()) {
            handoffReturnHarvestId = null;
            handoffReturnTab = null;
            handoffReturnLabel = null;
            harvestHandoffBanner.setVisible(false);
            return;
        }
        handoffReturnHarvestId = harvestId;
        handoffReturnTab = safeTab;
        handoffReturnLabel = safeLabel.isEmpty() ? safeTab : safeLabel;
        String context = "Reviewing harvest #" + harvestId + " from " + handoffReturnLabel;
        harvestHandoffLabel.setText(context);
        harvestHandoffLabel.setToolTipText(
                "Filters were cleared so this exact harvest can be reviewed before returning.");
        harvestHandoffLabel.getAccessibleContext().setAccessibleDescription(context);
        returnFromHarvestBtn.setText("Return to " + handoffReturnLabel);
        returnFromHarvestBtn.setToolTipText(
                "Return to " + handoffReturnLabel + " without rebuilding its current view.");
        returnFromHarvestBtn.getAccessibleContext().setAccessibleName(
                "Return to " + handoffReturnLabel + " after reviewing harvest " + harvestId);
        harvestHandoffBanner.setVisible(true);
    }

    private void returnFromHarvestHandoff() {
        if (host == null || handoffReturnTab == null) {
            return;
        }
        String returnTab = handoffReturnTab;
        String returnLabel = handoffReturnLabel;
        Long harvestId = handoffReturnHarvestId;
        handoffReturnHarvestId = null;
        handoffReturnTab = null;
        handoffReturnLabel = null;
        harvestHandoffBanner.setVisible(false);
        host.selectTab(returnTab);
        host.setStatus("Returned to " + returnLabel
                + (harvestId == null ? "." : " after reviewing harvest #" + harvestId + "."));
    }

    private void failHistoryRefresh(Throwable failure) {
        if (failure instanceof HarvestLogPresenter.InputValidationException validation
                && ("filterFrom".equals(validation.field())
                || "filterTo".equals(validation.field()))) {
            showHarvestFilterValidation(validation);
            return;
        }
        String detail = FarmUi.actionableError("Refreshing harvest history", failure);
        historyRefreshFailure = detail
                + " Existing rows, totals, filters, selection, and entry details were kept; retry when ready.";
        showHistoryRefreshState("Harvest refresh failed · retry",
                historyRefreshFailure, FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus("Harvest refresh failed. Existing work was kept; review the status and retry.");
        }
    }

    private void finishHistoryRefresh() {
        refreshingHistory = false;
        refreshHistoryBtn.setEnabled(true);
        updateEntryActionAvailability();
        updateEntryToolActions();
        updateReportActions();
        if (historyRefreshFailure != null) {
            showHistoryRefreshState("Harvest refresh failed · retry",
                    historyRefreshFailure, FarmUi.Tone.DANGER);
        }
        if (historyRefreshQueued) {
            historyRefreshQueued = false;
            startHistoryRefresh();
        }
    }

    private void showHistoryRefreshState(String message, String description,
                                         FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(filterStatusLabel, message, tone);
        filterStatusLabel.setToolTipText(description);
        filterStatusLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private static Map<String, Double> stableTotals(Map<String, Double> totals) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(totals));
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        boolean writable = canWrite && !savingEntry && !batchLogging && !deletingEntry;
        // Filter / export / refresh stay enabled for VIEWER
        GuiPermissions.setWritable(writable,
                dateField, cropField, cropSuggest, qtyField, unitField, unitSuggest,
                bedField, notesField, keepContextBox, batchArea,
                batchDateField, batchUnitField, batchBedField, batchNotesField,
                batchBtn, fillSampleBtn, addBtn, loadBtn, saveBtn);
        if (batchLogging || savingEntry || deletingEntry) {
            batchArea.setEnabled(false);
            fillSampleBtn.setEnabled(false);
            batchBtn.setEnabled(false);
        }
        updateBatchPreview();
        setEntryToolChoices(canWrite);
        if (accessLabel != null) {
            FarmUi.updateStatusBadge(accessLabel,
                    canWrite ? "HAND / OWNER · editing available" : "VIEWER · read and export only",
                    canWrite ? FarmUi.Tone.SUCCESS : FarmUi.Tone.NEUTRAL);
        }
        updateEntryMode();
        updateEntryToolActions();
    }

    private void buildUI() {
        panel = FarmUi.page();

        JPanel filterBar = buildFilterBar();
        refreshHistoryBtn = FarmUi.secondaryButton("Refresh");
        refreshHistoryBtn.setToolTipText("Reload harvest history using the current filters");
        refreshHistoryBtn.addActionListener(e -> applyFilter());
        accessLabel = FarmUi.statusBadge("Checking access…", FarmUi.Tone.NEUTRAL);
        newSkuStatusLabel = FarmUi.statusBadge(
                "New inventory setup needed", FarmUi.Tone.WARNING);
        newSkuStatusLabel.setName("harvest.newSkuStatus");
        newSkuStatusLabel.setVisible(false);
        newSkuStatusLabel.getAccessibleContext().setAccessibleName(
                "Harvest-created inventory setup status");
        reviewNewSkuBtn = FarmUi.secondaryButton("Review new Inventory SKU");
        reviewNewSkuBtn.setName("harvest.reviewNewSku");
        reviewNewSkuBtn.setVisible(false);
        reviewNewSkuBtn.setToolTipText(
                "Open newly auto-created Inventory products to set price, cost, and catalog details.");
        reviewNewSkuBtn.getAccessibleContext().setAccessibleName(
                "Review inventory products created from harvest logging");
        reviewNewSkuBtn.addActionListener(event -> reviewNewInventoryProducts());
        panel.add(FarmUi.pageHeader(
                "Harvest log",
                "Record each cut, keep inventory synchronized, and review production by bed.",
                FarmUi.toolbar(accessLabel, filterStatusLabel,
                        reportStatusLabel, newSkuStatusLabel,
                        reviewNewSkuBtn, refreshHistoryBtn)), BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
                new String[]{"Id", "Date", "Crop", "Quantity", "Unit", "Bed / Field", "Notes"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column) {
                return switch (column) {
                    case 0 -> Long.class;
                    case 1 -> LocalDate.class;
                    case 3 -> Double.class;
                    default -> String.class;
                };
            }
        };
        table = new JTable(tableModel);
        table.setName("harvest.history.table");
        FarmUi.configureTable(table, "Harvest history table");
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(6).setPreferredWidth(180);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateEntryMode();
                updateEntryToolActions();
            }
        });
        // Double-click loads into form for edit
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadSelectedIntoForm();
                }
            }
        });
        tableCardLayout = new CardLayout();
        tableState = new JPanel(tableCardLayout);
        tableState.add(FarmUi.tableScroll(table), "table");
        tableState.add(FarmUi.emptyState(
                "No harvests match these filters",
                "Clear the filters or log today's first cut below."), "empty");

        JPanel history = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        history.setOpaque(false);
        harvestHandoffLabel = FarmUi.statusBadge(
                "Exact harvest review", FarmUi.Tone.INFO);
        harvestHandoffLabel.getAccessibleContext().setAccessibleName(
                "Exact harvest handoff context");
        returnFromHarvestBtn = FarmUi.secondaryButton("Return to previous workflow");
        returnFromHarvestBtn.setName("harvest.returnFromHandoff");
        returnFromHarvestBtn.addActionListener(event -> returnFromHarvestHandoff());
        harvestHandoffBanner = FarmUi.toolbar(harvestHandoffLabel, returnFromHarvestBtn);
        harvestHandoffBanner.setName("harvest.handoffBanner");
        harvestHandoffBanner.setVisible(false);
        JPanel historyTop = new JPanel();
        historyTop.setOpaque(false);
        historyTop.setLayout(new BoxLayout(historyTop, BoxLayout.Y_AXIS));
        historyTop.add(harvestHandoffBanner);
        historyTop.add(FarmUi.section(
                "Filter harvest history",
                "Use crop, bed, notes, or date range; press Enter in a field to apply.",
                filterBar));
        history.add(historyTop, BorderLayout.NORTH);
        history.add(FarmUi.section(
                "Harvest history",
                "Select a row for actions; click a column heading to change the work order.",
                tableState), BorderLayout.CENTER);

        totalsArea = FarmUi.displayArea(8, 26);
        totalsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        totalsArea.getAccessibleContext().setAccessibleName("Harvest totals by crop");
        workbench = new JTabbedPane();
        workbench.setName("harvest.workspace");
        workbench.addTab("Single entry", FarmUi.section(
                "Add or edit a harvest",
                null,
                buildForm()));
        workbench.addTab("Batch entry", FarmUi.section(
                "Log a morning cut in one pass",
                "Enter Crop, Quantity, optional Unit, and optional Bed on each line.",
                buildBatchPanel()));
        workbench.addTab("Production totals", FarmUi.section(
                "Season totals by crop",
                "Totals follow the filtered view when that option is selected.",
                new JScrollPane(totalsArea)));
        workbench.setMnemonicAt(0, 'S');
        workbench.setMnemonicAt(1, 'B');
        workbench.setMnemonicAt(2, 'P');
        workbench.getAccessibleContext().setAccessibleName("Harvest entry and totals workspace");
        history.setMinimumSize(new Dimension(0, 305));
        workbench.setMinimumSize(new Dimension(0, 300));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, history, workbench);
        split.setBorder(null);
        split.setResizeWeight(0.50);
        split.setDividerLocation(0.50);
        split.setContinuousLayout(true);
        split.setOneTouchExpandable(true);
        panel.add(split, BorderLayout.CENTER);
        applyRolePermissions(host == null || host.canMutateData());
    }

    private JPanel buildBatchPanel() {
        HarvestBatchView view = new HarvestBatchView(this::logBatch);
        batchArea = view.batchArea();
        batchDateField = view.dateField();
        batchUnitField = view.unitField();
        batchBedField = view.bedField();
        batchNotesField = view.notesField();
        batchBtn = view.logButton();
        fillSampleBtn = view.sampleButton();
        batchStatusLabel = view.statusLabel();
        javax.swing.event.DocumentListener previewListener = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) {
                updateBatchPreview();
            }

            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) {
                updateBatchPreview();
            }

            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) {
                updateBatchPreview();
            }
        };
        batchArea.getDocument().addDocumentListener(previewListener);
        batchDateField.getDocument().addDocumentListener(previewListener);
        batchUnitField.getDocument().addDocumentListener(previewListener);
        batchBedField.getDocument().addDocumentListener(previewListener);
        batchNotesField.getDocument().addDocumentListener(previewListener);
        updateBatchPreview();
        return view;
    }

    private void updateBatchPreview() {
        if (batchArea == null || batchBtn == null || batchStatusLabel == null
                || batchDateField == null || batchUnitField == null
                || batchBedField == null || batchNotesField == null) {
            return;
        }
        if (batchLogging || savingEntry || deletingEntry) {
            batchBtn.setEnabled(false);
            return;
        }
        HarvestLogPresenter.BatchPreview preview = presenter.previewBatch(
                new HarvestLogPresenter.BatchDraft(
                        batchDateField.getText(), batchUnitField.getText(), batchBedField.getText(),
                        batchNotesField.getText(), batchArea.getText()));
        FarmUi.updateStatusBadge(batchStatusLabel, preview.message(),
                preview.valid() ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
        batchStatusLabel.setToolTipText(preview.message());
        batchStatusLabel.getAccessibleContext().setAccessibleDescription(
                "Batch harvest readiness: " + preview.message());
        batchBtn.setEnabled(canWrite && !refreshingHistory
                && !savingEntry && !batchLogging && !deletingEntry && preview.valid());
        batchBtn.setToolTipText(preview.valid()
                ? "Creates " + preview.rowCount()
                        + (preview.rowCount() == 1 ? " harvest entry" : " harvest entries")
                        + " and updates inventory."
                : preview.message());
    }

    private void logBatch() {
        if (batchLogging || savingEntry || deletingEntry) {
            return;
        }
        if (!GuiPermissions.requireWrite(host, panel, "batch-log harvests")) {
            return;
        }
        try {
            List<HarvestEntry> batch = presenter.parseBatch(new HarvestLogPresenter.BatchDraft(
                    batchDateField.getText(), batchUnitField.getText(), batchBedField.getText(),
                    batchNotesField.getText(), batchArea.getText()));
            if (host != null) {
                host.setStatus("⏳ Logging batch harvest (" + batch.size()
                        + (batch.size() == 1 ? " row" : " rows") + ")…");
            }
            startBatchLogging(batch);
        } catch (DateTimeParseException ex) {
            showError("Batch date must be YYYY-MM-DD.");
        } catch (NumberFormatException ex) {
            showError("Each batch line needs a numeric quantity: Crop,Quantity[,Unit][,Bed]");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void startBatchLogging(List<HarvestEntry> batch) {
        batchLogging = true;
        applyRolePermissions(canWrite);
        batchBtn.setText("Logging batch…");
        batchBtn.setToolTipText(
                "Batch logging is in progress; duplicate submission is disabled.");
        String rows = batch.size() + (batch.size() == 1 ? " row" : " rows");
        FarmUi.updateStatusBadge(
                batchStatusLabel, "Logging " + rows + " · inventory updating", FarmUi.Tone.INFO);
        batchStatusLabel.setToolTipText(
                "The batch is saving in the background; entered lines cannot be changed yet.");
        batchStatusLabel.getAccessibleContext().setAccessibleDescription(
                "Logging " + rows + " in the background. Duplicate submission is disabled.");

        SwingWorker<BatchSaveResult, Void> worker = new SwingWorker<>() {
            @Override
            protected BatchSaveResult doInBackground() {
                List<String> missing = presenter.missingInventoryProducts(batch);
                return new BatchSaveResult(presenter.addBatch(batch), missing);
            }

            @Override
            protected void done() {
                try {
                    completeBatchLogging(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failBatchLogging(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failBatchLogging(exception.getCause() == null
                            ? exception : exception.getCause());
                }
            }
        };
        worker.execute();
    }

    private void completeBatchLogging(BatchSaveResult result) {
        List<HarvestEntry> saved = result == null ? List.of() : result.saved();
        List<String> createdProducts = result == null
                ? List.of() : result.newInventoryProducts();
        int count = saved.size();
        finishBatchLoggingControls();
        batchArea.setText("");

        boolean catalogSetupNeeded = !createdProducts.isEmpty();
        String success = count + " harvest " + (count == 1 ? "entry" : "entries")
                + " logged · " + (catalogSetupNeeded
                ? createdProducts.size() + " new Inventory SKU"
                        + (createdProducts.size() == 1 ? " needs" : "s need") + " setup"
                : "inventory up to date");
        FarmUi.updateStatusBadge(batchStatusLabel, success,
                catalogSetupNeeded ? FarmUi.Tone.WARNING : FarmUi.Tone.SUCCESS);
        batchStatusLabel.setToolTipText(success);
        batchStatusLabel.getAccessibleContext().setAccessibleDescription(
                success + ". The batch entry area was cleared to prevent duplicate logging.");
        showNewInventoryProducts(createdProducts);
        try {
            applyFilter();
            if (host != null) {
                host.refreshAll();
                host.setStatus(count + " harvest entr"
                        + (count == 1 ? "y" : "ies")
                        + " logged. " + (catalogSetupNeeded
                        ? "Review new Inventory setup for "
                                + String.join(", ", createdProducts) + "."
                        : "Inventory is up to date."));
            }
        } catch (RuntimeException refreshFailure) {
            String message = count + " logged · refresh history to see the update";
            FarmUi.updateStatusBadge(batchStatusLabel, message, FarmUi.Tone.WARNING);
            batchStatusLabel.getAccessibleContext().setAccessibleDescription(
                    message + ". " + FarmUi.actionableError(
                            "Refreshing harvest history", refreshFailure));
            if (host != null) {
                host.setStatus("Batch saved, but harvest history could not refresh: "
                        + safeMessage(refreshFailure));
            }
        }
    }

    private void failBatchLogging(Throwable failure) {
        finishBatchLoggingControls();
        updateBatchPreview();
        String detail = safeMessage(failure);
        String status = "Batch not logged · " + compactMessage(detail) + " · try again";
        FarmUi.updateStatusBadge(batchStatusLabel, status, FarmUi.Tone.DANGER);
        String guidance = FarmUi.actionableError("Logging the harvest batch", failure);
        batchStatusLabel.setToolTipText(guidance);
        batchStatusLabel.getAccessibleContext().setAccessibleDescription(
                status + ". The batch lines were kept. " + guidance);
        batchBtn.setToolTipText("Nothing was saved. Review the message and try this batch again.");
        if (host != null) {
            host.setStatus(guidance + " The batch lines were kept.");
        }
        if (canWrite) {
            batchArea.requestFocusInWindow();
        }
    }

    private void finishBatchLoggingControls() {
        batchLogging = false;
        batchBtn.setText("Log batch harvest");
        applyRolePermissions(canWrite);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure == null ? null : failure.getMessage();
        return message == null || message.isBlank()
                ? "The application did not provide additional details" : message.trim();
    }

    private static String compactMessage(String message) {
        return message.length() <= 64 ? message : message.substring(0, 61) + "…";
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel();
        bar.setOpaque(false);
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        filterCropField = FarmUi.searchField("Filter by crop", "Any crop", 10);
        filterBedField = FarmUi.searchField("Filter by bed", "Any bed", 8);
        filterNotesField = FarmUi.searchField("Filter by notes", "Notes contain…", 10);
        filterFromField = new JTextField(LocalDate.now().minusDays(30).toString(), 10);
        filterToField = new JTextField(LocalDate.now().toString(), 10);
        filterFromField.setName("harvest.filterFrom");
        filterToField.setName("harvest.filterTo");
        filterFromField.getAccessibleContext().setAccessibleName("Harvest filter start date");
        filterToField.getAccessibleContext().setAccessibleName("Harvest filter end date");
        filterStatusLabel = FarmUi.statusBadge("Not loaded", FarmUi.Tone.NEUTRAL);
        filterStatusLabel.setName("harvest.refreshStatus");
        filterStatusLabel.getAccessibleContext().setAccessibleName(
                "Harvest history refresh status");
        clearFilterErrorOnEdit(filterFromField);
        clearFilterErrorOnEdit(filterToField);
        filterTotalsOnly = new JCheckBox("Totals = filtered view", true);
        filterTotalsOnly.setToolTipText("When checked, season totals panel uses the filtered rows only.");
        filterTotalsOnly.addActionListener(e -> rebuildTotals());

        java.awt.event.ActionListener applyListener = e -> applyFilter();
        filterCropField.addActionListener(applyListener);
        filterBedField.addActionListener(applyListener);
        filterNotesField.addActionListener(applyListener);
        filterFromField.addActionListener(applyListener);
        filterToField.addActionListener(applyListener);

        JPanel fields = FarmUi.toolbar(
                fieldLabel("Crop", filterCropField, 'C'), filterCropField,
                fieldLabel("Bed", filterBedField, 'B'), filterBedField,
                fieldLabel("Notes", filterNotesField, 'N'), filterNotesField,
                fieldLabel("From", filterFromField, 'F'), filterFromField,
                fieldLabel("To", filterToField, 'T'), filterToField);

        JButton apply = FarmUi.primaryButton("Apply filters");
        apply.addActionListener(applyListener);
        JButton clear = FarmUi.secondaryButton("Show all");
        clear.addActionListener(e -> {
            filterCropField.setText("");
            filterBedField.setText("");
            filterNotesField.setText("");
            filterFromField.setText("");
            filterToField.setText("");
            applyFilter();
        });
        JButton thisWeek = FarmUi.secondaryButton("This week");
        thisWeek.setToolTipText("Filter to trailing 7 days (incl. today).");
        thisWeek.addActionListener(e -> {
            filterFromField.setText(LocalDate.now().minusDays(6).toString());
            filterToField.setText(LocalDate.now().toString());
            applyFilter();
        });
        reportTools = new JComboBox<>(new String[]{
                "Export filtered CSV", "Export all CSV",
                "Bed production report", "Harvest log PDF"
        });
        reportTools.setName("harvest.reportTools");
        reportTools.getAccessibleContext().setAccessibleName("Harvest reports and exports");
        createReportBtn = FarmUi.secondaryButton("Create report");
        createReportBtn.setName("harvest.createReport");
        createReportBtn.addActionListener(
                e -> runHarvestReport((String) reportTools.getSelectedItem()));
        reportStatusLabel = FarmUi.statusBadge("Reports ready", FarmUi.Tone.NEUTRAL);
        reportStatusLabel.setName("harvest.reportStatus");
        reportStatusLabel.getAccessibleContext().setAccessibleName(
                "Harvest report and export status");
        JPanel actions = FarmUi.toolbar(
                apply, clear, thisWeek, new JLabel("Reports & exports"), reportTools,
                createReportBtn, filterTotalsOnly);
        bar.add(fields);
        bar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        bar.add(actions);
        return bar;
    }

    private void showHarvestFilterValidation(
            HarvestLogPresenter.InputValidationException validation) {
        FarmUi.clearFieldError(filterFromField);
        FarmUi.clearFieldError(filterToField);
        JTextField field = "filterTo".equals(validation.field())
                ? filterToField : filterFromField;
        String label = field == filterToField ? "To date" : "From date";
        FarmUi.markFieldError(field, validation.getMessage());
        String recovery = validation.getMessage()
                + " Existing harvest rows, totals, selection, and entry work were kept. "
                + "Correct the highlighted date, then apply again.";
        showHistoryRefreshState("Check " + label, recovery, FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus("Harvest filters not applied. " + recovery);
        }
    }

    private void clearFilterErrorOnEdit(JTextField field) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                if (!"error".equals(field.getClientProperty("JComponent.outline"))) {
                    return;
                }
                FarmUi.clearFieldError(field);
                showHistoryRefreshState("Changed · ready to apply filters",
                        "The highlighted date changed. Apply filters again to validate the range.",
                        FarmUi.Tone.INFO);
            }

            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
    }

    private void runHarvestReport(String selection) {
        if (selection == null) {
            return;
        }
        switch (selection) {
            case "Export filtered CSV" -> exportCsv(true);
            case "Export all CSV" -> exportCsv(false);
            case "Bed production report" -> showBedProduction();
            case "Harvest log PDF" -> showHarvestLogPdf();
            default -> {
                // The report list is fixed.
            }
        }
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        dateField = new JTextField(LocalDate.now().toString(), 10);
        cropField = new JTextField(14);
        FarmUi.initialFocus(cropField);
        cropSuggest = new JComboBox<>();
        cropSuggest.setEditable(false);
        cropSuggest.setPrototypeDisplayValue("Recent crop…");
        cropSuggest.addActionListener(e -> {
            Object sel = cropSuggest.getSelectedItem();
            if (sel != null && !"Recent crop…".equals(sel.toString()) && !sel.toString().isBlank()) {
                cropField.setText(sel.toString());
            }
        });
        qtyField = new JTextField("1", 6);
        unitField = new JTextField("stems", 8);
        unitSuggest = new JComboBox<>(new String[]{
                "stems", "bunches", "buckets", "lbs", "oz", "each"
        });
        unitSuggest.setSelectedItem("stems");
        unitSuggest.addActionListener(e -> {
            Object u = unitSuggest.getSelectedItem();
            if (u != null) {
                unitField.setText(u.toString());
            }
        });
        bedField = new JTextField(10);
        notesField = new JTextField(18);
        dateField.getAccessibleContext().setAccessibleName("Harvest date");
        cropField.getAccessibleContext().setAccessibleName("Harvest crop or variety");
        qtyField.getAccessibleContext().setAccessibleName("Harvest quantity");
        qtyField.getAccessibleContext().setAccessibleDescription(
                "Recorded quantity; inventory stores whole units and rounds to the nearest unit.");
        qtyField.setToolTipText(
                "Decimals are recorded exactly; inventory rounds each harvest to the nearest whole unit.");
        unitField.getAccessibleContext().setAccessibleName("Harvest unit");
        bedField.getAccessibleContext().setAccessibleName("Harvest bed or field");
        notesField.getAccessibleContext().setAccessibleName("Harvest notes");
        clearEntryErrorOnEdit(dateField);
        clearEntryErrorOnEdit(cropField);
        clearEntryErrorOnEdit(qtyField);
        clearEntryErrorOnEdit(unitField);
        notesField.addActionListener(event -> {
            if (editingId == null) {
                addHarvest();
            } else {
                saveEdit();
            }
        });

        int row = 0;
        addField(form, c, row, 0, "Date (YYYY-MM-DD)", dateField, 'D');
        addField(form, c, row++, 1, "Crop / variety", cropField, 'C');
        addField(form, c, row, 0, "Recent crops", cropSuggest, 'R');
        addField(form, c, row++, 1, "Bed / field", bedField, 'B');
        addField(form, c, row, 0, "Quantity", qtyField, 'Q');
        addField(form, c, row++, 1, "Unit", unitField, 'U');
        addField(form, c, row, 0, "Unit presets", unitSuggest, 'P');
        addField(form, c, row++, 1, "Notes", notesField, 'N');

        addBtn = FarmUi.primaryButton("Add harvest");
        addBtn.addActionListener(e -> addHarvest());
        keepContextBox = new JCheckBox("Keep harvest context", true);
        keepContextBox.setName("harvest.keepContext");
        keepContextBox.setOpaque(false);
        keepContextBox.setToolTipText(
                "After adding, keep the date, crop, unit, and bed; reset quantity and notes.");
        keepContextBox.getAccessibleContext().setAccessibleName(
                "Keep harvest context after adding");
        loadBtn = FarmUi.secondaryButton("Load selected");
        loadBtn.addActionListener(e -> loadSelectedIntoForm());
        saveBtn = FarmUi.primaryButton("Save edit");
        saveBtn.setToolTipText("Save the changes and adjust inventory automatically.");
        saveBtn.addActionListener(e -> saveEdit());
        JPanel buttons = FarmUi.toolbar(addBtn, loadBtn, saveBtn);
        entryTools = new JComboBox<>();
        setEntryToolChoices(true);
        entryTools.setName("harvest.entryTools");
        entryTools.getAccessibleContext().setAccessibleName("More harvest entry actions");
        entryTools.addActionListener(event -> updateEntryToolActions());
        runEntryToolBtn = FarmUi.secondaryButton("Run entry action");
        runEntryToolBtn.addActionListener(e -> runEntryTool((String) entryTools.getSelectedItem()));
        entryStatusLabel = FarmUi.statusBadge("Ready for a new harvest", FarmUi.Tone.NEUTRAL);
        updateEntryMode();
        JPanel actions = FarmUi.toolbar(
                buttons, entryStatusLabel, entryTools, runEntryToolBtn);
        updateEntryToolActions();

        c.gridx = 0;
        c.gridy = row++;
        c.weightx = 1;
        c.gridwidth = 4;
        form.add(FarmUi.toolbar(keepContextBox, FarmUi.formHint(
                "Retains date, crop, unit, and bed; quantity and notes always reset.")), c);

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 4;
        form.add(actions, c);
        return form;
    }

    private void runEntryTool(String selection) {
        switch (selection == null ? "" : selection) {
            case "Clear form" -> clearForm();
            case "Duplicate selected as today" -> duplicateSelectedAsToday();
            case "Refresh history" -> applyFilter();
            case "Delete selected harvest" -> deleteSelected();
            default -> {
                // The entry action list is fixed.
            }
        }
    }

    private void setEntryToolChoices(boolean writable) {
        if (entryTools == null) {
            return;
        }
        String selected = String.valueOf(entryTools.getSelectedItem());
        String[] choices = writable
                ? new String[]{"Clear form", "Duplicate selected as today",
                    "Refresh history", "Delete selected harvest"}
                : new String[]{"Refresh history"};
        entryTools.setModel(new DefaultComboBoxModel<>(choices));
        for (String choice : choices) {
            if (choice.equals(selected)) {
                entryTools.setSelectedItem(choice);
                break;
            }
        }
    }

    private void updateEntryToolActions() {
        if (entryTools == null || runEntryToolBtn == null) {
            return;
        }
        String selected = String.valueOf(entryTools.getSelectedItem());
        boolean selectionRequired = "Duplicate selected as today".equals(selected)
                || "Delete selected harvest".equals(selected);
        boolean refreshAction = "Refresh history".equals(selected);
        runEntryToolBtn.setEnabled((!refreshingHistory || refreshAction)
                && (!savingEntry || refreshAction)
                && (!batchLogging || refreshAction)
                && (!deletingEntry || refreshAction)
                && (!selectionRequired
                || (table != null && table.getSelectedRow() >= 0)));
    }

    private void updateReportActions() {
        if (reportTools == null || createReportBtn == null) {
            return;
        }
        boolean enabled = !refreshingHistory && !runningReport && !deletingEntry;
        reportTools.setEnabled(enabled);
        createReportBtn.setEnabled(enabled);
    }

    private void addField(JPanel form, GridBagConstraints c, int row, int pair,
                          String label, JComponent field, char mnemonic) {
        c.gridx = pair * 2;
        c.gridy = row;
        c.weightx = 0;
        c.gridwidth = 1;
        form.add(fieldLabel(label, field, mnemonic), c);
        c.gridx = pair * 2 + 1;
        c.weightx = 1;
        form.add(field, c);
    }

    private static JLabel fieldLabel(String text, JComponent field, char mnemonic) {
        JLabel label = new JLabel(text + ":");
        label.setLabelFor(field);
        if (mnemonic != '\0') {
            label.setDisplayedMnemonic(mnemonic);
        }
        return label;
    }

    private void applyFilter() {
        if (tableModel == null) {
            return;
        }
        startHistoryRefresh();
    }

    private Long selectedHarvestId() {
        int modelRow = selectedHarvestModelRow();
        if (modelRow < 0) {
            return null;
        }
        Object value = tableModel.getValueAt(modelRow, 0);
        return value instanceof Long id ? id : null;
    }

    private int selectedHarvestModelRow() {
        if (table == null || table.getSelectedRow() < 0) {
            return -1;
        }
        return table.convertRowIndexToModel(table.getSelectedRow());
    }

    private boolean restoreHarvestSelection(Long id) {
        if (id == null) {
            return false;
        }
        for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
            if (id.equals(tableModel.getValueAt(modelRow, 0))) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private void refreshCropSuggestions() {
        if (cropSuggest == null) {
            return;
        }
        Object previous = cropSuggest.getSelectedItem();
        cropSuggest.removeAllItems();
        cropSuggest.addItem("Recent crop…");
        for (String crop : allTotals.keySet()) {
            cropSuggest.addItem(crop);
        }
        if (previous != null) {
            cropSuggest.setSelectedItem(previous);
        }
    }

    private void addHarvest() {
        if (savingEntry || batchLogging || deletingEntry) {
            return;
        }
        if (!GuiPermissions.requireWrite(host, panel, "log harvests")) {
            return;
        }
        try {
            HarvestEntry entry = readFormEntry();
            startEntrySave(EntryMutation.ADD, null, entry, keepContextBox.isSelected());
        } catch (HarvestLogPresenter.InputValidationException ex) {
            showEntryValidation(ex);
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void prepareAfterAdd(HarvestEntry entry, boolean keepContext) {
        if (!keepContext) {
            clearForm();
            updateEntryStatus("Harvest logged · inventory updated", FarmUi.Tone.SUCCESS);
            entryStatusLabel.getAccessibleContext().setAccessibleDescription(
                    "The captured harvest was saved once and the form was cleared.");
            cropField.requestFocusInWindow();
            return;
        }
        editingId = null;
        dateField.setText(entry.getHarvestDate().toString());
        cropField.setText(entry.getCropName());
        qtyField.setText("1");
        unitField.setText(entry.getUnit());
        bedField.setText(entry.getBedOrField());
        notesField.setText("");
        clearEntryValidation();
        updateEntryMode();
        updateEntryStatus(
                "Saved · context kept for next cut", FarmUi.Tone.SUCCESS);
        qtyField.requestFocusInWindow();
        qtyField.selectAll();
    }

    private void loadSelectedIntoForm() {
        int row = selectedHarvestModelRow();
        if (row < 0) {
            showError("Select a harvest row to load (or double-click it).");
            return;
        }
        editingId = (Long) tableModel.getValueAt(row, 0);
        dateField.setText(String.valueOf(tableModel.getValueAt(row, 1)));
        cropField.setText(String.valueOf(tableModel.getValueAt(row, 2)));
        qtyField.setText(String.valueOf(tableModel.getValueAt(row, 3)));
        unitField.setText(String.valueOf(tableModel.getValueAt(row, 4)));
        bedField.setText(nullToEmpty(tableModel.getValueAt(row, 5)));
        notesField.setText(nullToEmpty(tableModel.getValueAt(row, 6)));
        if (host != null) {
            host.setStatus("Editing the selected " + cropField.getText()
                    + " harvest. Change the fields, then choose Save edit.");
        }
        updateEntryMode();
    }

    /** Prefill form from selected row with today's date (add mode, not edit). */
    private void duplicateSelectedAsToday() {
        int row = selectedHarvestModelRow();
        if (row < 0) {
            showError("Select a harvest row to duplicate.");
            return;
        }
        editingId = null;
        dateField.setText(LocalDate.now().toString());
        cropField.setText(String.valueOf(tableModel.getValueAt(row, 2)));
        qtyField.setText(String.valueOf(tableModel.getValueAt(row, 3)));
        unitField.setText(String.valueOf(tableModel.getValueAt(row, 4)));
        bedField.setText(nullToEmpty(tableModel.getValueAt(row, 5)));
        notesField.setText(nullToEmpty(tableModel.getValueAt(row, 6)));
        if (host != null) {
            host.setStatus("Copied into today's form. Adjust the quantity if needed, then add it.");
        }
        updateEntryMode();
        updateEntryStatus("Copy ready to add", FarmUi.Tone.WARNING);
    }

    private void saveEdit() {
        if (savingEntry || batchLogging || deletingEntry) {
            return;
        }
        if (!GuiPermissions.requireWrite(host, panel, "edit harvests")) {
            return;
        }
        if (editingId == null) {
            showError("Load a harvest row first (Load selected / double-click), then Save edit.");
            return;
        }
        try {
            HarvestEntry entry = readFormEntry();
            startEntrySave(EntryMutation.UPDATE, editingId, entry, false);
        } catch (HarvestLogPresenter.InputValidationException ex) {
            showEntryValidation(ex);
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            showError(ex.getMessage());
        }
    }

    void startEntrySave(EntryMutation mutation, Long targetId,
                        HarvestEntry entry, boolean keepContext) {
        if (savingEntry || batchLogging || deletingEntry) {
            return;
        }
        savingEntry = true;
        applyRolePermissions(canWrite);
        String action = mutation == EntryMutation.ADD ? "Logging harvest" : "Saving harvest edit";
        updateEntryStatus(action + "…", FarmUi.Tone.INFO);
        entryStatusLabel.setToolTipText(
                "The captured harvest and inventory adjustment are saving in the background.");
        entryStatusLabel.getAccessibleContext().setAccessibleDescription(
                action + " in the background. Form fields and duplicate submission are locked.");
        if (host != null) {
            host.setStatus("⏳ " + action + " and updating inventory…");
        }

        new SwingWorker<EntrySaveResult, Void>() {
            @Override
            protected EntrySaveResult doInBackground() {
                List<String> missing = mutation == EntryMutation.ADD
                        ? presenter.missingInventoryProducts(List.of(entry)) : List.of();
                HarvestEntry saved = mutation == EntryMutation.ADD
                        ? presenter.add(entry) : presenter.update(targetId, entry);
                return new EntrySaveResult(saved, missing);
            }

            @Override
            protected void done() {
                try {
                    completeEntrySave(mutation, get(), keepContext);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failEntrySave(mutation, exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failEntrySave(mutation, exception.getCause() == null
                            ? exception : exception.getCause());
                } catch (RuntimeException exception) {
                    failEntrySave(mutation, exception);
                }
            }
        }.execute();
    }

    private void completeEntrySave(EntryMutation mutation, EntrySaveResult result,
                                   boolean keepContext) {
        HarvestEntry saved = result == null ? null : result.saved();
        if (saved == null) {
            throw new IllegalStateException("The harvest service returned no saved entry.");
        }
        List<String> createdProducts = result.newInventoryProducts();
        finishEntrySaveControls();
        if (mutation == EntryMutation.ADD) {
            prepareAfterAdd(saved, keepContext);
            if (!createdProducts.isEmpty()) {
                updateEntryStatus("Harvest logged · new Inventory SKU needs setup",
                        FarmUi.Tone.WARNING);
                String guidance = "Inventory created “" + createdProducts.get(0)
                        + "” with zero price and cost. Review its catalog details before sales or reports.";
                entryStatusLabel.setToolTipText(guidance);
                entryStatusLabel.getAccessibleContext().setAccessibleDescription(guidance);
            }
        } else {
            clearForm();
            updateEntryStatus("Harvest updated · inventory corrected", FarmUi.Tone.SUCCESS);
            entryStatusLabel.getAccessibleContext().setAccessibleDescription(
                    "The captured edit was saved once and its inventory difference was applied.");
        }
        String refreshGuidance = "";
        try {
            applyFilter();
            if (host != null) {
                host.refreshAll();
            }
        } catch (RuntimeException refreshFailure) {
            refreshGuidance = " Refresh harvest history manually. "
                    + FarmUi.actionableError("Refreshing the saved harvest", refreshFailure);
        }
        showNewInventoryProducts(createdProducts);
        if (host != null) {
            host.setStatus(mutation == EntryMutation.ADD
                    ? "Harvest logged: " + saved.getCropName() + " × "
                    + saved.getQuantity() + " " + saved.getUnit()
                    + (createdProducts.isEmpty()
                    ? ". Inventory is up to date."
                    : ". A new Inventory SKU was created with zero price and cost; review its setup.")
                    + (keepContext ? " Context kept for the next cut." : "")
                    + refreshGuidance
                    : "Harvest updated. Inventory has been corrected automatically."
                    + refreshGuidance);
        }
    }

    private void failEntrySave(EntryMutation mutation, Throwable failure) {
        finishEntrySaveControls();
        String detail = safeMessage(failure);
        String status = mutation == EntryMutation.ADD
                ? "Harvest not logged · retry" : "Edit not saved · retry";
        updateEntryStatus(status, FarmUi.Tone.DANGER);
        String guidance = FarmUi.actionableError(
                mutation == EntryMutation.ADD ? "Logging this harvest" : "Saving this harvest edit",
                failure);
        entryStatusLabel.setToolTipText(guidance);
        entryStatusLabel.getAccessibleContext().setAccessibleDescription(
                status + ". Date, crop, quantity, unit, bed, notes, edit target, and "
                        + "keep-context choice were kept. " + guidance);
        if (host != null) {
            host.setStatus(guidance + " Harvest form and target were kept for retry.");
        }
        if (panel.isShowing()) {
            JOptionPane.showMessageDialog(panel, guidance,
                    mutation == EntryMutation.ADD ? "Harvest not logged" : "Edit not saved",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void finishEntrySaveControls() {
        savingEntry = false;
        applyRolePermissions(canWrite);
    }

    private HarvestEntry readFormEntry() {
        return presenter.fromForm(new HarvestLogPresenter.EntryDraft(
                dateField.getText(), cropField.getText(), qtyField.getText(),
                unitField.getText(), bedField.getText(), notesField.getText()));
    }

    private void clearForm() {
        editingId = null;
        dateField.setText(LocalDate.now().toString());
        cropField.setText("");
        qtyField.setText("1");
        unitField.setText("stems");
        bedField.setText("");
        notesField.setText("");
        clearEntryValidation();
        updateEntryMode();
    }

    private void updateEntryMode() {
        if (addBtn == null || saveBtn == null) {
            return;
        }
        boolean editing = editingId != null;
        int selectedRow = selectedHarvestModelRow();
        boolean selected = selectedRow >= 0;
        updateEntryActionAvailability();
        if (editing) {
            updateEntryStatus(
                    "Editing #" + editingId + " · "
                            + compactCrop(cropField.getText().trim()),
                    FarmUi.Tone.WARNING);
            entryStatusLabel.getAccessibleContext().setAccessibleDescription(
                    "Save edit will update harvest " + editingId + ", "
                            + cropField.getText().trim() + " on "
                            + dateField.getText().trim() + ".");
            entryStatusLabel.setToolTipText("Editing harvest #" + editingId + " · "
                    + cropField.getText().trim() + " · " + dateField.getText().trim());
        } else if (selected) {
            String target = "Selected #" + tableModel.getValueAt(selectedRow, 0)
                    + " · " + compactCrop(String.valueOf(
                            tableModel.getValueAt(selectedRow, 2)))
                    + " · " + tableModel.getValueAt(selectedRow, 3) + " "
                    + tableModel.getValueAt(selectedRow, 4);
            updateEntryStatus(target, FarmUi.Tone.INFO);
            entryStatusLabel.getAccessibleContext().setAccessibleDescription(
                    "Load, duplicate, and delete actions apply to harvest "
                            + tableModel.getValueAt(selectedRow, 0) + ", "
                            + tableModel.getValueAt(selectedRow, 2) + " on "
                            + tableModel.getValueAt(selectedRow, 1) + ".");
            entryStatusLabel.setToolTipText("Selected harvest #"
                    + tableModel.getValueAt(selectedRow, 0) + " · "
                    + tableModel.getValueAt(selectedRow, 1) + " · "
                    + tableModel.getValueAt(selectedRow, 2) + " · "
                    + tableModel.getValueAt(selectedRow, 3) + " "
                    + tableModel.getValueAt(selectedRow, 4));
            loadBtn.setToolTipText("Load harvest #"
                    + tableModel.getValueAt(selectedRow, 0) + " into the edit form.");
        } else {
            updateEntryStatus(
                    "Ready for a new harvest", FarmUi.Tone.NEUTRAL);
            entryStatusLabel.getAccessibleContext().setAccessibleDescription(
                    "The form will create a new harvest.");
            loadBtn.setToolTipText("Select a harvest row to load into the edit form.");
        }
    }

    private void updateEntryActionAvailability() {
        if (addBtn == null || saveBtn == null) {
            return;
        }
        boolean current = !refreshingHistory && !savingEntry
                && !batchLogging && !deletingEntry;
        boolean editing = editingId != null;
        boolean selected = selectedHarvestModelRow() >= 0;
        addBtn.setEnabled(canWrite && current && !editing);
        loadBtn.setEnabled(canWrite && current && selected);
        saveBtn.setEnabled(canWrite && current && editing);
    }

    private static String compactCrop(String crop) {
        return crop.length() <= 18 ? crop : crop.substring(0, 17) + "…";
    }

    private void updateEntryStatus(String text, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(entryStatusLabel, text, tone);
        entryStatusLabel.getAccessibleContext().setAccessibleName(
                "Harvest entry mode and target");
    }

    private void showNewInventoryProducts(List<String> products) {
        if (products == null || products.isEmpty()
                || newSkuStatusLabel == null || reviewNewSkuBtn == null) {
            return;
        }
        Map<String, String> combined = new LinkedHashMap<>();
        for (String product : newInventoryProducts) {
            combined.put(product.toLowerCase(java.util.Locale.ROOT), product);
        }
        for (String product : products) {
            if (product != null && !product.isBlank()) {
                String name = product.trim();
                combined.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), name);
            }
        }
        newInventoryProducts = List.copyOf(combined.values());
        String names = String.join(", ", newInventoryProducts);
        String message = newInventoryProducts.size() + " new Inventory SKU"
                + (newInventoryProducts.size() == 1 ? " needs" : "s need") + " setup";
        String guidance = message + ": " + names
                + ". Harvest created each missing product with zero price and cost; review catalog details.";
        FarmUi.updateStatusBadge(newSkuStatusLabel, message, FarmUi.Tone.WARNING);
        newSkuStatusLabel.setVisible(true);
        newSkuStatusLabel.setToolTipText(guidance);
        newSkuStatusLabel.getAccessibleContext().setAccessibleDescription(guidance);
        reviewNewSkuBtn.setText(newInventoryProducts.size() == 1
                ? "Review new Inventory SKU" : "Review new Inventory SKUs");
        reviewNewSkuBtn.setVisible(true);
        reviewNewSkuBtn.setToolTipText(guidance);
    }

    private void reviewNewInventoryProducts() {
        if (host == null || newInventoryProducts.isEmpty()) {
            return;
        }
        List<String> products = List.copyOf(newInventoryProducts);
        host.showInventoryProducts(products, "Harvest Log", "Harvest Log");
        host.setStatus("Opened Inventory for harvest-created SKU setup: "
                + String.join(", ", products)
                + ". Set price, cost, unit, and notes before returning to Harvest Log.");
        newInventoryProducts = List.of();
        newSkuStatusLabel.setVisible(false);
        reviewNewSkuBtn.setVisible(false);
    }

    private void showEntryValidation(HarvestLogPresenter.InputValidationException exception) {
        clearEntryValidation();
        JTextField field = switch (exception.field()) {
            case "date" -> dateField;
            case "crop" -> cropField;
            case "quantity" -> qtyField;
            case "unit" -> unitField;
            default -> null;
        };
        FarmUi.markFieldError(field, exception.getMessage());
        String target = "form".equals(exception.field())
                ? "harvest entry" : exception.field();
        updateEntryStatus("Check " + target, FarmUi.Tone.DANGER);
        String recovery = exception.getMessage()
                + " Your entry is still in the form. Correct the highlighted value, then "
                + (editingId == null ? "choose Add harvest." : "choose Save edit.");
        entryStatusLabel.setToolTipText(recovery);
        entryStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        if (host != null) {
            host.setStatus("Harvest not saved. " + recovery);
        }
    }

    private void clearEntryValidation() {
        FarmUi.clearFieldError(dateField);
        FarmUi.clearFieldError(cropField);
        FarmUi.clearFieldError(qtyField);
        FarmUi.clearFieldError(unitField);
    }

    private void clearEntryErrorOnEdit(JTextField field) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                if ("error".equals(field.getClientProperty("JComponent.outline"))) {
                    FarmUi.clearFieldError(field);
                    updateEntryStatus("Changed · ready to retry", FarmUi.Tone.INFO);
                    String guidance = "The highlighted value changed. Submit the harvest again "
                            + "to validate and save it.";
                    entryStatusLabel.setToolTipText(guidance);
                    entryStatusLabel.getAccessibleContext()
                            .setAccessibleDescription(guidance);
                }
            }

            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
    }

    private void deleteSelected() {
        if (!GuiPermissions.requireWrite(host, panel, "delete harvests")) {
            return;
        }
        int row = selectedHarvestModelRow();
        if (row < 0) {
            showError("Select a harvest row to delete.");
            return;
        }
        Long id = (Long) tableModel.getValueAt(row, 0);
        String crop = String.valueOf(tableModel.getValueAt(row, 2));
        int confirm = JOptionPane.showConfirmDialog(panel,
                FarmUi.confirmationPanel(
                        "Delete this " + crop + " harvest?",
                        "The " + tableModel.getValueAt(row, 1)
                                + " entry will be deleted and its amount removed from inventory.",
                        FarmUi.Tone.DANGER),
                "Delete harvest?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        startHarvestDelete(id, crop, String.valueOf(tableModel.getValueAt(row, 1)));
    }

    /** Deletes one captured harvest and reverses inventory without blocking Swing. */
    void startHarvestDelete(long harvestId, String crop, String date) {
        if (deletingEntry || savingEntry || batchLogging) {
            return;
        }
        String targetCrop = crop == null || crop.isBlank() ? "harvest" : crop.trim();
        String targetDate = date == null ? "" : date.trim();
        deletingEntry = true;
        table.setEnabled(false);
        runEntryToolBtn.setText("Deleting harvest…");
        applyRolePermissions(canWrite);
        updateReportActions();
        updateEntryStatus("Deleting #" + harvestId + " · " + compactCrop(targetCrop) + "…",
                FarmUi.Tone.INFO);
        String busy = "Deleting harvest #" + harvestId + " for " + targetCrop
                + (targetDate.isBlank() ? "" : " on " + targetDate)
                + " and reversing its inventory adjustment in the background. "
                + "The selected target is locked to prevent a duplicate reversal.";
        entryStatusLabel.setToolTipText(busy);
        entryStatusLabel.getAccessibleContext().setAccessibleDescription(busy);
        if (host != null) {
            host.setStatus("Undoing the " + targetCrop
                    + " harvest and reversing inventory…");
        }

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                presenter.delete(harvestId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    completeHarvestDelete(harvestId, targetCrop);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failHarvestDelete(harvestId, targetCrop, exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failHarvestDelete(harvestId, targetCrop,
                            exception.getCause() == null ? exception : exception.getCause());
                }
            }
        }.execute();
    }

    private void completeHarvestDelete(long harvestId, String crop) {
        finishHarvestDeleteControls();
        if (editingId != null && editingId.equals(harvestId)) {
            clearForm();
        }
        table.clearSelection();
        applyFilter();
        updateEntryStatus("Harvest #" + harvestId + " deleted · inventory reversed",
                FarmUi.Tone.SUCCESS);
        String success = "The " + crop + " harvest was deleted once and its inventory "
                + "quantity was reversed automatically.";
        entryStatusLabel.setToolTipText(success);
        entryStatusLabel.getAccessibleContext().setAccessibleDescription(success);
        if (host != null) {
            host.refreshAll();
            host.setStatus("Harvest deleted. Inventory has been adjusted automatically.");
        }
    }

    private void failHarvestDelete(long harvestId, String crop, Throwable failure) {
        finishHarvestDeleteControls();
        restoreHarvestSelection(harvestId);
        String detail = FarmUi.actionableError("Deleting this harvest", failure);
        String recovery = detail + " The " + crop + " harvest remains selected and no "
                + "inventory reversal result was received; review the reason and retry.";
        updateEntryStatus("Harvest #" + harvestId + " not deleted · retry",
                FarmUi.Tone.DANGER);
        entryStatusLabel.setToolTipText(recovery);
        entryStatusLabel.getAccessibleContext().setAccessibleDescription(recovery);
        if (host != null) {
            host.setStatus("Harvest #" + harvestId
                    + " was not deleted. It remains selected for review or retry.");
        }
        if (panel.isShowing()) {
            JOptionPane.showMessageDialog(panel, recovery,
                    "Harvest not deleted", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void finishHarvestDeleteControls() {
        deletingEntry = false;
        runEntryToolBtn.setText("Run entry action");
        table.setEnabled(true);
        applyRolePermissions(canWrite);
        updateReportActions();
    }

    private void exportCsv(boolean filteredOnly) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(filteredOnly ? "harvest_filtered.csv" : "harvest_log.csv"));
        chooser.setDialogTitle(filteredOnly
                ? "Export filtered harvest view to CSV"
                : "Export full harvest history to CSV");
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        startCsvExport(chooser.getSelectedFile(), filteredOnly);
    }

    void startCsvExport(File file, boolean filteredOnly) {
        if (file == null || !beginReportWork("Exporting harvest CSV…",
                "The captured harvest rows are being written in the background.")) {
            return;
        }
        List<HarvestEntry> snapshot = List.copyOf(lastView);
        startReportFileExport(file,
                () -> {
                    if (filteredOnly) {
                        presenter.exportView(file.getAbsolutePath(), snapshot);
                    } else {
                        presenter.exportAll(file.getAbsolutePath());
                    }
                },
                "Harvest CSV saved · " + (filteredOnly ? snapshot.size() + " visible rows" : "all rows"),
                "Harvest log exported → " + file.getName()
                        + (filteredOnly ? " (filtered view)" : " (all rows)"),
                "Harvest CSV export");
    }

    private void rebuildTotals() {
        boolean filtered = filterTotalsOnly != null && filterTotalsOnly.isSelected();
        Map<String, Double> totals = filtered ? filteredTotals : allTotals;
        if (totals.isEmpty()) {
            totalsArea.setText(filtered
                    ? "(no rows in current filter)\nClear filter or log a harvest."
                    : "(no harvests logged yet)\nTip: log Nootka rose stems after morning cut.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(filtered ? "Current filtered totals\n" : "All-time season totals\n");
        double sum = 0;
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            sum += e.getValue();
            sb.append(String.format("%-20s %8.1f%n", e.getKey(), e.getValue()));
        }
        sb.append(String.format("%n%-20s %8.1f%n", "TOTAL", sum));
        totalsArea.setText(sb.toString());
    }

    private void showBedProduction() {
        try {
            ReportRange range = captureReportRange();
            startBedProductionReport(range.from(), range.to());
        } catch (RuntimeException exception) {
            failReportInput("Bed-production report", exception);
        }
    }

    void startBedProductionReport(LocalDate from, LocalDate to) {
        if (!beginReportWork("Preparing bed production…",
                "Harvest quantities are being grouped by bed and crop in the background.")) {
            return;
        }
        new SwingWorker<HarvestService.BedProductionReport, Void>() {
            @Override
            protected HarvestService.BedProductionReport doInBackground() {
                return from == null && to == null
                        ? presenter.bedProductionLast7Days()
                        : presenter.bedProduction(from, to);
            }

            @Override
            protected void done() {
                try {
                    HarvestService.BedProductionReport report = get();
                    completeReportWork(report.bedCount() + " bed"
                                    + (report.bedCount() == 1 ? "" : "s") + " · "
                                    + report.entryCount() + " harvest entries",
                            "Bed-production totals are ready for review or export.",
                            FarmUi.Tone.SUCCESS,
                            "Bed production: " + report.bedCount() + " bed(s), total "
                                    + report.grandTotal() + ".");
                    if (panel.isShowing()) {
                        presentBedProduction(report);
                    }
                } catch (Exception exception) {
                    failReportWork("Bed-production report", unwrapWorkerFailure(exception));
                }
            }
        }.execute();
    }

    private void presentBedProduction(HarvestService.BedProductionReport report) {
        JTextArea area = reportArea(report.plainText());
        Object[] options = {"Export CSV…", "Export PDF…", "Close"};
        int choice = JOptionPane.showOptionDialog(panel, new JScrollPane(area),
                "Bed / field production — " + report.from() + " → " + report.to(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[2]);
        if (choice == 0) {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("bed-production-" + report.to() + ".csv"));
            if (chooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION
                    && beginReportWork("Exporting bed production CSV…",
                    "The reviewed bed-production snapshot is being written in the background.")) {
                File file = chooser.getSelectedFile();
                startReportFileExport(file,
                        () -> java.nio.file.Files.writeString(
                                file.toPath(), presenter.bedProductionCsv(report)),
                        "Bed production CSV saved", "Bed production CSV → " + file.getName(),
                        "Bed-production CSV export");
            }
        } else if (choice == 1) {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("bed-production-" + report.to() + ".pdf"));
            if (chooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION
                    && beginReportWork("Creating bed production PDF…",
                    "The reviewed bed-production snapshot is being rendered in the background.")) {
                File file = chooser.getSelectedFile();
                startReportFileExport(file,
                        () -> java.nio.file.Files.write(
                                file.toPath(), presenter.bedProductionPdf(report)),
                        "Bed production PDF saved", "Bed production PDF → " + file.getName(),
                        "Bed-production PDF export");
            }
        }
    }

    private void showHarvestLogPdf() {
        try {
            ReportRange range = captureReportRange();
            startHarvestLogReport(range.from(), range.to());
        } catch (RuntimeException exception) {
            failReportInput("Harvest-log report", exception);
        }
    }

    void startHarvestLogReport(LocalDate from, LocalDate to) {
        if (!beginReportWork("Preparing harvest log…",
                "The printable harvest history is being assembled in the background.")) {
            return;
        }
        new SwingWorker<HarvestService.HarvestLogReport, Void>() {
            @Override
            protected HarvestService.HarvestLogReport doInBackground() {
                return from == null && to == null
                        ? presenter.harvestLogLast7Days()
                        : presenter.harvestLog(from, to);
            }

            @Override
            protected void done() {
                try {
                    HarvestService.HarvestLogReport report = get();
                    completeReportWork(report.entryCount() + " harvest entr"
                                    + (report.entryCount() == 1 ? "y" : "ies") + " ready",
                            "The printable harvest-log snapshot is ready for review or export.",
                            FarmUi.Tone.SUCCESS,
                            "Harvest log: " + report.entryCount() + " entr"
                                    + (report.entryCount() == 1 ? "y" : "ies")
                                    + ", total " + report.totalQuantity() + ".");
                    if (panel.isShowing()) {
                        presentHarvestLog(report);
                    }
                } catch (Exception exception) {
                    failReportWork("Harvest-log report", unwrapWorkerFailure(exception));
                }
            }
        }.execute();
    }

    private void presentHarvestLog(HarvestService.HarvestLogReport report) {
        Object[] options = {"Export PDF…", "Close"};
        int choice = JOptionPane.showOptionDialog(panel,
                new JScrollPane(reportArea(report.plainText())),
                "Harvest log — " + report.from() + " → " + report.to(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);
        if (choice == 0) {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("harvest-log-" + report.to() + ".pdf"));
            if (chooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION
                    && beginReportWork("Creating harvest log PDF…",
                    "The reviewed harvest-log snapshot is being rendered in the background.")) {
                File file = chooser.getSelectedFile();
                startReportFileExport(file,
                        () -> java.nio.file.Files.write(
                                file.toPath(), presenter.harvestLogPdf(report)),
                        "Harvest log PDF saved",
                        "Harvest log PDF → " + file.getName()
                                + " (" + report.entryCount() + " rows)",
                        "Harvest-log PDF export");
            }
        }
    }

    private ReportRange captureReportRange() {
        FarmUi.clearFieldError(filterFromField);
        FarmUi.clearFieldError(filterToField);
        LocalDate from;
        LocalDate to;
        try {
            from = HarvestLogPresenter.parseOptionalDate(filterFromField.getText());
        } catch (DateTimeParseException exception) {
            FarmUi.markFieldError(filterFromField, "Use YYYY-MM-DD or leave blank.");
            throw new IllegalArgumentException("Report start date must use YYYY-MM-DD.", exception);
        }
        try {
            to = HarvestLogPresenter.parseOptionalDate(filterToField.getText());
        } catch (DateTimeParseException exception) {
            FarmUi.markFieldError(filterToField, "Use YYYY-MM-DD or leave blank.");
            throw new IllegalArgumentException("Report end date must use YYYY-MM-DD.", exception);
        }
        if (from != null && to != null && to.isBefore(from)) {
            FarmUi.markFieldError(filterToField,
                    "End date must be on or after the report start date.");
            throw new IllegalArgumentException(
                    "Report end date must be on or after the start date.");
        }
        return new ReportRange(from, to);
    }

    private boolean beginReportWork(String message, String description) {
        if (runningReport) {
            return false;
        }
        runningReport = true;
        updateReportActions();
        showReportState(message, description, FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus(message);
        }
        return true;
    }

    private void startReportFileExport(File file, ReportWriter writer,
                                       String successMessage, String hostMessage,
                                       String errorAction) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                writer.write();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    completeReportWork(successMessage,
                            "Saved to " + file.getAbsolutePath(), FarmUi.Tone.SUCCESS,
                            hostMessage);
                    if (panel.isShowing()) {
                        JOptionPane.showMessageDialog(panel,
                                "Saved to:\n" + file.getAbsolutePath(),
                                "Export complete", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception exception) {
                    failReportWork(errorAction, unwrapWorkerFailure(exception));
                }
            }
        }.execute();
    }

    private void completeReportWork(String message, String description,
                                    FarmUi.Tone tone, String hostMessage) {
        runningReport = false;
        updateReportActions();
        showReportState(message, description, tone);
        if (host != null) {
            host.setStatus(hostMessage);
        }
    }

    private void failReportWork(String action, Throwable failure) {
        runningReport = false;
        updateReportActions();
        String detail = FarmUi.actionableError(action, failure);
        showReportState("Report or export failed · retry",
                detail + " Current harvest rows, filters, and entry work were kept.",
                FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus(action + " failed. Current harvest work was kept; retry when ready.");
        }
        if (panel.isShowing()) {
            showError(detail);
        }
    }

    private void failReportInput(String action, RuntimeException failure) {
        showReportState("Report dates need attention",
                failure.getMessage() + " Correct the highlighted date and retry.",
                FarmUi.Tone.DANGER);
        if (panel.isShowing()) {
            showError(FarmUi.actionableError(action, failure));
        }
    }

    private void showReportState(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(reportStatusLabel, message, tone);
        reportStatusLabel.setToolTipText(description);
        reportStatusLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private static Throwable unwrapWorkerFailure(Exception exception) {
        return exception instanceof java.util.concurrent.ExecutionException
                && exception.getCause() != null ? exception.getCause() : exception;
    }

    private static JTextArea reportArea(String text) {
        JTextArea area = new JTextArea(text, 22, 64);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        return area;
    }

    private static String nullToEmpty(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private record HistorySnapshot(HarvestLogPresenter.HarvestView view,
                                   Map<String, Double> filteredTotals,
                                   Map<String, Double> allTotals) {
    }

    private record EntrySaveResult(HarvestEntry saved,
                                   List<String> newInventoryProducts) {
        private EntrySaveResult {
            newInventoryProducts = newInventoryProducts == null
                    ? List.of() : List.copyOf(newInventoryProducts);
        }
    }

    private record BatchSaveResult(List<HarvestEntry> saved,
                                   List<String> newInventoryProducts) {
        private BatchSaveResult {
            saved = saved == null ? List.of() : List.copyOf(saved);
            newInventoryProducts = newInventoryProducts == null
                    ? List.of() : List.copyOf(newInventoryProducts);
        }
    }

    private record ReportRange(LocalDate from, LocalDate to) {
    }

    enum EntryMutation {
        ADD, UPDATE
    }

    @FunctionalInterface
    private interface ReportWriter {
        void write() throws Exception;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(panel, msg, "Harvest log", JOptionPane.ERROR_MESSAGE);
    }
}
