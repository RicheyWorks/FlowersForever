package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.service.SyncHistoryService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * User-facing activity timeline with filters and export options.
 */
public class SyncHistoryTab implements FlowerFarmTab {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int VIEW_LIMIT = 300;
    private static final java.util.regex.Pattern HARVEST_ID_PATTERN =
            java.util.regex.Pattern.compile(
                    "(?i)\\bharvest(?:id\\s*=|\\s+(?:id\\s*=|#))\\s*(\\d+)");
    private static final java.util.regex.Pattern ORDER_ID_PATTERN =
            java.util.regex.Pattern.compile(
                    "(?i)\\border(?:id\\s*=|\\s*#)\\s*(\\d+)");

    private final SyncHistoryService syncHistoryService;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final FarmFileChooser files;

    private JPanel panel;
    private DefaultTableModel tableModel;
    private JTable table;
    private JComboBox<String> connectorFilter;
    private JComboBox<String> operationFilter;
    private JComboBox<String> resultFilter;
    private JTextField messageSearch;
    private JLabel statsLabel;
    private JLabel accessLabel;
    private JButton clearHistBtn;
    private JButton refreshBtn;
    private JButton exportCsvBtn;
    private JButton exportPdfBtn;
    private JLabel exportStatusLabel;
    private List<SyncHistoryEntry> lastView = List.of();
    private CardLayout tableCardLayout;
    private JPanel tableState;
    private JTextArea detailArea;
    private JLabel selectedStatus;
    private JLabel recoveryLabel;
    private JButton openWorkflowBtn;
    private String selectedWorkflow;
    private String selectedOrderStatus;
    private Long selectedHarvestId;
    private Long selectedOrderId;
    private boolean refreshingHistory;
    private boolean exportingHistory;
    private boolean historyRefreshQueued;
    private boolean updatingFilterControls;
    private String historyRefreshFailure;

    public SyncHistoryTab(SyncHistoryService syncHistoryService, TabHost host) {
        this(syncHistoryService, host, FarmDialogs.swing(), FarmFileChooser.swing());
    }

    public SyncHistoryTab(SyncHistoryService syncHistoryService, TabHost host,
                          FarmDialogs dialogs) {
        this(syncHistoryService, host, dialogs, FarmFileChooser.swing());
    }

    public SyncHistoryTab(SyncHistoryService syncHistoryService, TabHost host,
                          FarmDialogs dialogs, FarmFileChooser files) {
        this.syncHistoryService = syncHistoryService;
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
        this.files = java.util.Objects.requireNonNull(files, "files");
    }

    @Override public String getTabTitle() { return "Activity History"; }

    @Override
    public String getDescription() {
        return "Review imports, exports, harvest changes, and customer-order activity";
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        // Clear history is OWNER-only when auth is on; HAND can write elsewhere but not clear
        boolean canClear = (host == null || host.canClearHistory()) && !refreshingHistory;
        GuiPermissions.setWritable(canClear, GuiPermissions.OWNER_CLEAR_TIP, clearHistBtn);
        if (accessLabel != null) {
            FarmUi.updateStatusBadge(accessLabel,
                    canClear ? "OWNER · history management" : "VIEWER / HAND · read and export only",
                    canClear ? FarmUi.Tone.SUCCESS : FarmUi.Tone.NEUTRAL);
        }
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
        if (tableModel == null) {
            return;
        }
        startActivityRefresh();
    }

    private void applyFilter() {
        if (tableModel == null) {
            return;
        }
        startActivityRefresh();
    }

    void startActivityRefresh() {
        if (refreshingHistory) {
            historyRefreshQueued = true;
            showActivityRefreshState("Refresh queued · current activity load finishing",
                    "One newer activity snapshot will load with the latest filters.",
                    FarmUi.Tone.INFO);
            return;
        }
        ActivityRequest request = currentActivityRequest();
        refreshingHistory = true;
        historyRefreshFailure = null;
        refreshBtn.setEnabled(false);
        setExportControls(false);
        applyRolePermissions(host == null || host.canMutateData());
        showActivityRefreshState("Refreshing activity history…",
                "Loading timeline rows, summary counts, sources, and activities in the background.",
                FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Refreshing activity history…");
        }

        new SwingWorker<ActivitySnapshot, Void>() {
            @Override
            protected ActivitySnapshot doInBackground() {
                List<SyncHistoryEntry> entries = List.copyOf(syncHistoryService.filter(
                        request.connector(), request.operation(), request.successOnly(),
                        request.query(), VIEW_LIMIT));
                return new ActivitySnapshot(entries, syncHistoryService.stats(entries),
                        List.copyOf(syncHistoryService.distinctConnectors(VIEW_LIMIT)),
                        List.copyOf(syncHistoryService.distinctOperations(VIEW_LIMIT)));
            }

            @Override
            protected void done() {
                try {
                    ActivitySnapshot snapshot = get();
                    if (!historyRefreshQueued) {
                        applyActivitySnapshot(snapshot);
                    } else if (host != null) {
                        host.setStatus("A newer activity refresh is queued; discarding the older snapshot.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (!historyRefreshQueued) {
                        failActivityRefresh(exception);
                    }
                } catch (java.util.concurrent.ExecutionException exception) {
                    if (!historyRefreshQueued) {
                        failActivityRefresh(exception.getCause() == null
                                ? exception : exception.getCause());
                    }
                } catch (RuntimeException exception) {
                    if (!historyRefreshQueued) {
                        failActivityRefresh(exception);
                    }
                } finally {
                    finishActivityRefresh();
                }
            }
        }.execute();
    }

    private ActivityRequest currentActivityRequest() {
        return new ActivityRequest(comboValue(connectorFilter), comboValue(operationFilter),
                parseResultFilter().orElse(null),
                messageSearch == null ? "" : messageSearch.getText().trim());
    }

    private void applyActivitySnapshot(ActivitySnapshot snapshot) {
        Long selectedId = selectedActivityId();
        replaceFilterOptions(snapshot.connectors(), snapshot.operations());
        lastView = snapshot.entries();
        tableModel.setRowCount(0);
        for (SyncHistoryEntry entry : snapshot.entries()) {
            tableModel.addRow(activityCells(entry));
        }
        SyncHistoryService.HistoryStats stats = snapshot.stats();
        showActivityRefreshState(String.format(
                        "%d activities · %d completed · %d need attention",
                        stats.total(), stats.ok(), stats.fail()),
                "Activity rows, summary counts, and filter choices are current.",
                stats.fail() > 0 ? FarmUi.Tone.WARNING : FarmUi.Tone.SUCCESS);
        if (tableCardLayout != null && tableState != null) {
            tableCardLayout.show(tableState, snapshot.entries().isEmpty() ? "empty" : "table");
        }
        if (snapshot.entries().isEmpty()) {
            table.clearSelection();
            showNoSelection();
        } else if (!restoreActivitySelection(selectedId)) {
            table.setRowSelectionInterval(0, 0);
            updateSelectedActivity();
        }
    }

    private void failActivityRefresh(Throwable failure) {
        String detail = FarmUi.actionableError("Refreshing activity history", failure);
        historyRefreshFailure = detail
                + " Existing rows, filters, selection, and diagnostic details were kept; retry when ready.";
        showActivityRefreshState("Activity refresh failed · retry",
                historyRefreshFailure, FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus("Activity history refresh failed. Existing diagnostics were kept; retry when ready.");
        }
    }

    private void finishActivityRefresh() {
        refreshingHistory = false;
        refreshBtn.setEnabled(true);
        setExportControls(!exportingHistory);
        applyRolePermissions(host == null || host.canMutateData());
        if (historyRefreshFailure != null) {
            showActivityRefreshState("Activity refresh failed · retry",
                    historyRefreshFailure, FarmUi.Tone.DANGER);
        }
        if (historyRefreshQueued) {
            historyRefreshQueued = false;
            startActivityRefresh();
        }
    }

    private void showActivityRefreshState(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(statsLabel, message, tone);
        statsLabel.setToolTipText(description);
        statsLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private void replaceFilterOptions(List<String> connectors, List<String> operations) {
        Object prevConn = connectorFilter.getSelectedItem();
        Object prevOp = operationFilter.getSelectedItem();
        withoutFilterEvents(() -> {
            connectorFilter.removeAllItems();
            connectorFilter.addItem("All sources");
            for (String connector : connectors) {
                connectorFilter.addItem(connector);
            }
            if (prevConn != null) {
                connectorFilter.setSelectedItem(prevConn);
            }
            operationFilter.removeAllItems();
            operationFilter.addItem("All activities");
            for (String operation : operations) {
                operationFilter.addItem(operation);
            }
            if (prevOp != null) {
                operationFilter.setSelectedItem(prevOp);
            }
        });
    }

    private void withoutFilterEvents(Runnable change) {
        updatingFilterControls = true;
        try {
            change.run();
        } finally {
            updatingFilterControls = false;
        }
    }

    private static Object[] activityCells(SyncHistoryEntry entry) {
        return new Object[]{
                entry.getId(),
                entry.getOccurredAt() == null ? "" : FMT.format(entry.getOccurredAt()),
                friendlySource(entry.getConnectorName()),
                friendlyOperation(entry.getOperation()),
                entry.isSuccess() ? "Completed" : "Needs attention",
                entry.getRecordCount() == null ? "" : entry.getRecordCount(),
                entry.getMessage()
        };
    }

    private Long selectedActivityId() {
        if (table == null || table.getSelectedRow() < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
        if (modelRow < 0 || modelRow >= lastView.size()) {
            return null;
        }
        return lastView.get(modelRow).getId();
    }

    private boolean restoreActivitySelection(Long id) {
        if (id == null) {
            return false;
        }
        for (int modelRow = 0; modelRow < lastView.size(); modelRow++) {
            if (id.equals(lastView.get(modelRow).getId())) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                    updateSelectedActivity();
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private static String comboValue(JComboBox<String> box) {
        if (box == null || box.getSelectedItem() == null) {
            return "";
        }
        String selected = String.valueOf(box.getSelectedItem());
        return selected.startsWith("All ") ? "" : selected;
    }

    private Optional<Boolean> parseResultFilter() {
        if (resultFilter == null) {
            return Optional.empty();
        }
        String v = String.valueOf(resultFilter.getSelectedItem());
        if ("Completed only".equals(v)) {
            return Optional.of(true);
        }
        if ("Needs attention only".equals(v)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    private void buildUI() {
        panel = FarmUi.page();
        statsLabel = FarmUi.statusBadge("Activity not loaded", FarmUi.Tone.NEUTRAL);
        statsLabel.setName("activity.refreshStatus");
        statsLabel.getAccessibleContext().setAccessibleName("Activity history refresh status");
        accessLabel = FarmUi.statusBadge("Checking access…", FarmUi.Tone.NEUTRAL);
        JPanel filterBar = buildFilterBar();
        panel.add(FarmUi.pageHeader(
                "Activity history",
                "See when imports, exports, harvests, and customer orders succeeded or need attention.",
                FarmUi.toolbar(accessLabel, statsLabel)), BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
                new String[]{"Id", "When", "Source", "Activity", "Result", "Records", "Message"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        FarmUi.configureTable(table, "Farm activity history table");
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectedActivity();
            }
        });
        tableCardLayout = new CardLayout();
        tableState = new JPanel(tableCardLayout);
        tableState.add(FarmUi.tableScroll(table), "table");
        tableState.add(FarmUi.emptyState(
                "No activities match these filters",
                "Clear the filters or return after an import, harvest, or order change."), "empty");
        tableState.setMinimumSize(new Dimension(0, 170));
        tableState.setPreferredSize(new Dimension(720, 210));

        JPanel inspector = buildActivityInspector();
        JSplitPane timelineWorkspace = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, tableState, inspector);
        timelineWorkspace.setName("activity.timelineWorkspace");
        timelineWorkspace.setResizeWeight(0.55);
        timelineWorkspace.setContinuousLayout(true);

        JPanel filters = FarmUi.section(
                "Filter the timeline",
                "Narrow by source, operation, result, or text in the message and detail.",
                filterBar);
        JPanel timeline = FarmUi.section(
                "Farm activity timeline",
                "Select an activity to read its full details and recovery guidance.",
                timelineWorkspace);
        JPanel content = new ActivityBody(filters, timeline);
        content.setName("activity.body");
        FarmUi.revealFocusedControls(content);
        JScrollPane scroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("activity.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        applyRolePermissions(host == null || host.canMutateData());
    }

    /** Preserve room for both timeline rows and their readable details on short screens. */
    private static final class ActivityBody extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        private final JComponent filters;

        private ActivityBody(JComponent filters, JComponent timeline) {
            super(new BorderLayout(0, FarmUi.SECTION_GAP));
            this.filters = filters;
            setOpaque(false);
            add(filters, BorderLayout.NORTH);
            add(timeline, BorderLayout.CENTER);
        }

        @Override public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            preferred.height = filters.getPreferredSize().height + FarmUi.SECTION_GAP + 540;
            return preferred;
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    private JPanel buildActivityInspector() {
        selectedStatus = FarmUi.statusBadge("No activity selected", FarmUi.Tone.NEUTRAL);
        recoveryLabel = FarmUi.wrappingStatusBadge(
                "Select a row to review what happened and what to do next.", FarmUi.Tone.NEUTRAL);
        recoveryLabel.setName("activity.recovery");
        recoveryLabel.getAccessibleContext().setAccessibleName("Form guidance");
        recoveryLabel.getAccessibleContext().setAccessibleDescription(recoveryLabel.getText());
        openWorkflowBtn = FarmUi.secondaryButton("Open related workflow");
        openWorkflowBtn.setName("activity.openWorkflow");
        openWorkflowBtn.setEnabled(false);
        openWorkflowBtn.addActionListener(e -> openSelectedWorkflow());

        JPanel actions = new JPanel(new BorderLayout(FarmUi.CONTROL_GAP, 0));
        actions.setOpaque(false);
        actions.add(FarmUi.toolbar(selectedStatus), BorderLayout.CENTER);
        actions.add(openWorkflowBtn, BorderLayout.EAST);
        JPanel heading = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        heading.setName("activity.inspectorHeading");
        heading.setOpaque(false);
        heading.add(actions, BorderLayout.NORTH);
        heading.add(recoveryLabel, BorderLayout.CENTER);

        detailArea = FarmUi.displayArea(5, 60);
        detailArea.setName("activity.selectedDetail");
        detailArea.getAccessibleContext().setAccessibleName("Selected activity details");
        detailArea.setFont(UIManager.getFont("Label.font").deriveFont(13f));
        detailArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        detailArea.setText("Select an activity to review its full message and diagnostic details.");

        JPanel inspector = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        inspector.setName("activity.inspector");
        inspector.setOpaque(false);
        inspector.setBorder(BorderFactory.createCompoundBorder(
                FarmUi.cardBorder(), BorderFactory.createEmptyBorder(2, 2, 2, 2)));
        inspector.add(heading, BorderLayout.NORTH);
        inspector.add(new JScrollPane(detailArea), BorderLayout.CENTER);
        inspector.setMinimumSize(new Dimension(0, 240));
        inspector.setPreferredSize(new Dimension(720, 260));
        return inspector;
    }

    private JPanel buildFilterBar() {
        JPanel filterBar = new JPanel();
        filterBar.setOpaque(false);
        filterBar.setLayout(new BoxLayout(filterBar, BoxLayout.Y_AXIS));

        connectorFilter = new JComboBox<>(new String[]{"All sources"});
        connectorFilter.setName("activity.sourceFilter");
        operationFilter = new JComboBox<>(new String[]{"All activities"});
        operationFilter.setName("activity.operationFilter");
        resultFilter = new JComboBox<>(new String[]{"Any result", "Completed only", "Needs attention only"});
        resultFilter.setName("activity.resultFilter");
        messageSearch = FarmUi.searchField(
                "Activity message search", "Search messages or details", 18);
        FarmUi.initialFocus(messageSearch);
        messageSearch.setToolTipText("Search message or detail text");
        messageSearch.addActionListener(e -> applyFilter());

        java.awt.event.ActionListener apply = e -> {
            if (!updatingFilterControls) {
                applyFilter();
            }
        };
        connectorFilter.addActionListener(apply);
        operationFilter.addActionListener(apply);
        resultFilter.addActionListener(apply);

        JLabel sourceLabel = new JLabel("Source:");
        sourceLabel.setLabelFor(connectorFilter);
        JLabel operationLabel = new JLabel("Operation:");
        operationLabel.setLabelFor(operationFilter);
        JLabel resultLabel = new JLabel("Result:");
        resultLabel.setLabelFor(resultFilter);
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setLabelFor(messageSearch);
        searchLabel.setDisplayedMnemonic('S');
        JPanel fields = FarmUi.toolbar(
                sourceLabel, connectorFilter,
                operationLabel, operationFilter,
                resultLabel, resultFilter);
        JPanel search = FarmUi.toolbar(searchLabel, messageSearch);

        JButton applyBtn = FarmUi.primaryButton("Apply filters");
        applyBtn.addActionListener(apply);
        JButton failures = FarmUi.secondaryButton("Needs attention");
        failures.addActionListener(e -> {
            withoutFilterEvents(() -> resultFilter.setSelectedItem("Needs attention only"));
            applyFilter();
        });
        JButton clear = FarmUi.secondaryButton("Clear filters");
        clear.addActionListener(e -> {
            withoutFilterEvents(() -> {
                connectorFilter.setSelectedItem("All sources");
                operationFilter.setSelectedItem("All activities");
                resultFilter.setSelectedItem("Any result");
                messageSearch.setText("");
            });
            applyFilter();
        });
        refreshBtn = FarmUi.secondaryButton("Refresh");
        refreshBtn.addActionListener(e -> refreshData());
        exportCsvBtn = FarmUi.secondaryButton("Save a spreadsheet (CSV)…");
        exportCsvBtn.addActionListener(e -> exportCsv());
        exportPdfBtn = FarmUi.secondaryButton("Export PDF…");
        exportPdfBtn.setToolTipText("Save a printable copy of the current activity list.");
        exportPdfBtn.addActionListener(e -> exportPdf());
        exportStatusLabel = FarmUi.statusBadge("Exports ready", FarmUi.Tone.NEUTRAL);
        exportStatusLabel.setName("activity.exportStatus");
        exportStatusLabel.getAccessibleContext().setAccessibleName(
                "Activity history export status");
        clearHistBtn = FarmUi.dangerButton("Clear history…");
        clearHistBtn.addActionListener(e -> clearHistory());

        JPanel actions = FarmUi.toolbar(applyBtn, failures, clear, refreshBtn);
        JPanel exports = FarmUi.toolbar(exportCsvBtn, exportPdfBtn, clearHistBtn, exportStatusLabel);
        filterBar.add(fields);
        filterBar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        filterBar.add(search);
        filterBar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        filterBar.add(actions);
        filterBar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        filterBar.add(exports);
        return filterBar;
    }

    private void exportCsv() {
        if (exportingHistory) {
            return;
        }
        List<SyncHistoryEntry> snapshot = List.copyOf(lastView);
        var selected = files.chooseSave(panel, "Export the current activity list",
                new File("farm_activity_history.csv"));
        if (selected.isEmpty()) {
            showExportState("Spreadsheet not saved · try saving again",
                    "No destination was changed; the filtered activity rows were kept.",
                    FarmUi.Tone.NEUTRAL);
            return;
        }
        File file = selected.get();
        if (approveReplacement(file)) {
            startActivityCsvExport(file, snapshot);
        } else {
            showExportState("Spreadsheet not saved · existing file kept",
                    "The selected file was not replaced; the filtered activity rows were kept.",
                    FarmUi.Tone.NEUTRAL);
        }
    }

    private void exportPdf() {
        if (exportingHistory) {
            return;
        }
        startActivityPdfPreview(List.copyOf(lastView), currentExportScope());
    }

    void startActivityCsvExport(File destination, List<SyncHistoryEntry> entries) {
        if (!beginExport("Saving an activity spreadsheet…",
                "Writing the captured filtered activity rows in the background.")) {
            return;
        }
        List<SyncHistoryEntry> snapshot = List.copyOf(entries);
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return writeExportAtomically(destination.toPath(), temporary ->
                        syncHistoryService.exportToCsv(
                                temporary.toAbsolutePath().toString(), snapshot));
            }

            @Override
            protected void done() {
                finishFileExport(this, "CSV", snapshot.size());
            }
        }.execute();
    }

    void startActivityPdfExport(File destination, List<SyncHistoryEntry> entries, String scope) {
        if (!beginExport("Saving activity PDF…",
                "Building and writing the captured filtered activity report in the background.")) {
            return;
        }
        List<SyncHistoryEntry> snapshot = List.copyOf(entries);
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                SyncHistoryService.AuditReport report =
                        syncHistoryService.buildAuditReport(snapshot, scope);
                return writePdfReport(destination.toPath(), report);
            }

            @Override
            protected void done() {
                finishFileExport(this, "PDF", snapshot.size());
            }
        }.execute();
    }

    private void startActivityPdfPreview(List<SyncHistoryEntry> snapshot, String scope) {
        if (!beginExport("Preparing activity summary…",
                "Building the captured filtered activity preview in the background.")) {
            return;
        }
        new SwingWorker<SyncHistoryService.AuditReport, Void>() {
            @Override
            protected SyncHistoryService.AuditReport doInBackground() {
                return syncHistoryService.buildAuditReport(snapshot, scope);
            }

            @Override
            protected void done() {
                try {
                    showPdfPreview(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failExport(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    failExport(exception.getCause() == null
                            ? exception : exception.getCause());
                } catch (RuntimeException exception) {
                    failExport(exception);
                }
            }
        }.execute();
    }

    private void showPdfPreview(SyncHistoryService.AuditReport report) {
        if (!panel.isShowing()) {
            finishExportControls();
            showExportState("PDF preview ready · " + report.total() + " rows",
                    "Open Export PDF again when the Activity History screen is visible to save it.",
                    FarmUi.Tone.SUCCESS);
            return;
        }
        JTextArea area = new JTextArea(report.plainText(), 20, 70);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(700, 400));
        Object[] options = {"Save PDF…", "Close"};
        int choice = dialogs.showOption(panel, scroll,
                "Activity history — " + report.total() + " row(s)",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);
        if (choice != 0) {
            finishExportControls();
            showExportState("PDF preview closed · exports ready",
                    "No file was written; the captured activity summary remains unchanged.",
                    FarmUi.Tone.NEUTRAL);
            if (host != null) {
                host.setStatus("Activity summary: " + report.ok() + " completed / "
                        + report.fail() + " need attention");
            }
            return;
        }
        var selected = files.chooseSave(panel, "Save activity history PDF",
                new File("activity-history-" + report.generatedOn() + ".pdf"));
        if (selected.isEmpty() || !approveReplacement(selected.get())) {
            finishExportControls();
            showExportState("PDF not saved · exports ready",
                    "No destination was changed; the reviewed activity rows and filters were kept.",
                    FarmUi.Tone.NEUTRAL);
            return;
        }
        continuePdfExport(selected.get(), report);
    }

    private void continuePdfExport(File destination, SyncHistoryService.AuditReport report) {
        showExportState("Saving activity PDF…",
                "Rendering and writing the reviewed activity summary in the background.",
                FarmUi.Tone.INFO);
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return writePdfReport(destination.toPath(), report);
            }

            @Override
            protected void done() {
                finishFileExport(this, "PDF", report.total());
            }
        }.execute();
    }

    private Path writePdfReport(Path destination, SyncHistoryService.AuditReport report)
            throws Exception {
        return writeExportAtomically(destination, temporary -> {
            byte[] pdf = syncHistoryService.generateAuditPdf(report);
            if (pdf == null || pdf.length == 0) {
                throw new IllegalStateException("The generated activity PDF was empty.");
            }
            Files.write(temporary, pdf);
        });
    }

    private void finishFileExport(SwingWorker<Path, Void> worker, String kind, int rows) {
        try {
            Path saved = worker.get();
            finishExportControls();
            showExportState(kind + " saved · " + rows + " rows",
                    "Saved the captured filtered activity rows to " + saved + ".",
                    FarmUi.Tone.SUCCESS);
            if (host != null) {
                host.setStatus("Activity " + kind + " saved → " + saved
                        + " (" + rows + " rows)");
            }
            if (panel.isShowing()) {
                dialogs.showMessage(panel,
                        "Exported " + rows + " row(s) to:\n" + saved,
                        "Export complete", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failExport(exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            failExport(exception.getCause() == null ? exception : exception.getCause());
        } catch (RuntimeException exception) {
            failExport(exception);
        }
    }

    private boolean beginExport(String message, String description) {
        if (exportingHistory) {
            return false;
        }
        exportingHistory = true;
        setExportControls(false);
        showExportState(message, description, FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus(message);
        }
        return true;
    }

    private void finishExportControls() {
        exportingHistory = false;
        setExportControls(!refreshingHistory);
    }

    private void setExportControls(boolean enabled) {
        if (exportCsvBtn != null) {
            exportCsvBtn.setEnabled(enabled);
        }
        if (exportPdfBtn != null) {
            exportPdfBtn.setEnabled(enabled);
        }
    }

    private void failExport(Throwable failure) {
        finishExportControls();
        String guidance = FarmUi.actionableError("Activity-history export", failure);
        showExportState("Export failed · retry",
                guidance + " Existing rows, filters, selection, and destination file were kept.",
                FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus(guidance + " Activity filters and diagnostics were kept for retry.");
        }
        if (panel.isShowing()) {
            dialogs.showMessage(panel, guidance, "Export failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showExportState(String message, String description, FarmUi.Tone tone) {
        FarmUi.updateStatusBadge(exportStatusLabel, message, tone);
        exportStatusLabel.setToolTipText(description);
        exportStatusLabel.getAccessibleContext().setAccessibleDescription(description);
    }

    private String currentExportScope() {
        String scope = "Current desktop filters";
        Object conn = connectorFilter == null ? null : connectorFilter.getSelectedItem();
        Object op = operationFilter == null ? null : operationFilter.getSelectedItem();
        Object res = resultFilter == null ? null : resultFilter.getSelectedItem();
        if (conn != null && !"All sources".equals(String.valueOf(conn))) scope += " · " + conn;
        if (op != null && !"All activities".equals(String.valueOf(op))) scope += " · " + op;
        if (res != null && !"Any result".equals(String.valueOf(res))) scope += " · " + res;
        return scope;
    }

    private boolean approveReplacement(File file) {
        if (!file.exists()) {
            return true;
        }
        return dialogs.showConfirm(panel,
                file.getName() + " already exists. Replace it?",
                "Replace existing export?", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static Path writeExportAtomically(Path destination, ExportWriter writer)
            throws Exception {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("The selected export folder does not exist.");
        }
        Path temporary = Files.createTempFile(parent, ".activity-export-", ".tmp");
        try {
            writer.write(temporary);
            if (!Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                throw new IllegalStateException("The generated activity export was empty.");
            }
            try {
                return Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                return Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void clearHistory() {
        if (!com.flowerfarm.gui.GuiPermissions.requireOwnerClear(host, panel)) {
            return;
        }
        int confirm = dialogs.showConfirm(panel,
                "Delete the entire activity history? This cannot be undone.",
                "Clear all activity history?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        syncHistoryService.clearAll();
        refreshData();
        if (host != null) {
            host.setStatus("Activity history cleared.");
        }
    }

    private void updateSelectedActivity() {
        if (table == null || detailArea == null) {
            return;
        }
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            showNoSelection();
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= lastView.size()) {
            showNoSelection();
            return;
        }
        SyncHistoryEntry entry = lastView.get(modelRow);
        boolean success = entry.isSuccess();
        FarmUi.updateStatusBadge(selectedStatus,
                success ? "Completed activity" : "Needs attention",
                success ? FarmUi.Tone.SUCCESS : FarmUi.Tone.DANGER);
        selectedWorkflow = relatedWorkflow(entry);
        selectedOrderStatus = relatedOrderStatus(entry);
        selectedHarvestId = relatedRecordId(entry, HARVEST_ID_PATTERN);
        selectedOrderId = relatedRecordId(entry, ORDER_ID_PATTERN);
        openWorkflowBtn.setEnabled(selectedWorkflow != null);
        openWorkflowBtn.setToolTipText(selectedWorkflow == null
                ? "This activity has no dedicated farm tab. Follow the recovery guidance."
                : selectedOrderStatus != null
                        ? "Open " + selectedWorkflow + " with Cancelled orders selected."
                        : selectedHarvestId != null
                                ? "Open Harvest Log with harvest #" + selectedHarvestId
                                        + " selected."
                                : selectedOrderId != null
                                        ? "Open Customers & Orders with order #"
                                                + selectedOrderId + " selected."
                                        : "Open " + selectedWorkflow + " for this activity.");
        String guidance = recoveryGuidance(entry);
        recoveryLabel.setText(guidance);
        recoveryLabel.getAccessibleContext().setAccessibleName(guidance);

        String occurred = entry.getOccurredAt() == null ? "Unknown" : FMT.format(entry.getOccurredAt());
        String records = entry.getRecordCount() == null ? "Not recorded"
                : String.valueOf(entry.getRecordCount());
        detailArea.setText("When: " + occurred
                + "\nSource: " + friendlySource(entry.getConnectorName())
                + "\nActivity: " + friendlyOperation(entry.getOperation())
                + "\nRecords: " + records
                + "\nMessage: " + safeText(entry.getMessage())
                + "\nDetails: " + safeText(entry.getDetail()));
        detailArea.setCaretPosition(0);
    }

    private void showNoSelection() {
        selectedWorkflow = null;
        selectedOrderStatus = null;
        selectedHarvestId = null;
        selectedOrderId = null;
        if (selectedStatus != null) {
            FarmUi.updateStatusBadge(
                    selectedStatus, "No activity selected", FarmUi.Tone.NEUTRAL);
        }
        if (recoveryLabel != null) {
            String text = "Select a row to review what happened and what to do next.";
            recoveryLabel.setText(text);
            recoveryLabel.getAccessibleContext().setAccessibleName(text);
        }
        if (openWorkflowBtn != null) {
            openWorkflowBtn.setEnabled(false);
        }
        if (detailArea != null) {
            detailArea.setText("No matching activity is selected.");
        }
    }

    private void openSelectedWorkflow() {
        if (host == null || selectedWorkflow == null) {
            return;
        }
        if (selectedOrderStatus != null) {
            host.showOrdersByStatus(
                    selectedOrderStatus, "Activity History", "Activity History");
            return;
        }
        if (selectedHarvestId != null) {
            host.showHarvest(selectedHarvestId, "Activity History", "Activity History");
            return;
        }
        if (selectedOrderId != null) {
            host.showOrder(selectedOrderId, "Activity History", "Activity History");
            return;
        }
        host.selectTab(selectedWorkflow);
        host.setStatus("Opened " + selectedWorkflow + " from Activity History.");
    }

    private static String relatedWorkflow(SyncHistoryEntry entry) {
        String activity = ((entry.getOperation() == null ? "" : entry.getOperation()) + " "
                + (entry.getConnectorName() == null ? "" : entry.getConnectorName()))
                .toUpperCase(java.util.Locale.ROOT);
        if (activity.contains("HARVEST")) return "Harvest Log";
        if (activity.contains("ORDER") || activity.contains("CRM")
                || activity.contains("CUSTOMER")) return "Customers & Orders";
        if (activity.contains("INVENTORY") || activity.contains("STOCK")) return "Inventory";
        if (activity.contains("MARKET")) return "Market Day";
        if (activity.contains("IRRIGATION") || activity.contains("WEATHER")) return "Irrigation & Care";
        if (activity.contains("REPORT") || activity.contains("PDF")) return "Reports";
        return null;
    }

    private static String relatedOrderStatus(SyncHistoryEntry entry) {
        String operation = entry.getOperation() == null
                ? "" : entry.getOperation().toUpperCase(java.util.Locale.ROOT);
        String message = entry.getMessage() == null
                ? "" : entry.getMessage().toUpperCase(java.util.Locale.ROOT);
        if ("ORDER_STATUS".equals(operation)
                && (message.contains("→ CANCELLED") || message.contains("-> CANCELLED"))) {
            return "CANCELLED";
        }
        return null;
    }

    private static Long relatedRecordId(SyncHistoryEntry entry,
                                        java.util.regex.Pattern pattern) {
        String searchable = safeText(entry.getMessage()) + " " + safeText(entry.getDetail());
        java.util.regex.Matcher matcher = pattern.matcher(searchable);
        if (!matcher.find()) {
            return null;
        }
        try {
            long id = Long.parseLong(matcher.group(1));
            return id > 0 ? id : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String recoveryGuidance(SyncHistoryEntry entry) {
        if (entry.isSuccess()) {
            return "No recovery needed · keep this entry for the audit trail.";
        }
        String operation = entry.getOperation() == null ? ""
                : entry.getOperation().toUpperCase(java.util.Locale.ROOT);
        String workflow = relatedWorkflow(entry);
        if (workflow != null) {
            return "Review " + workflow + " · correct the underlying record, then retry the action.";
        }
        if (operation.contains("EXPORT")) {
            return "Check the destination and file permissions, then repeat the export.";
        }
        if (operation.contains("IMPORT") || operation.contains("SYNC")) {
            return "Check connector credentials, network access, and field mapping before retrying.";
        }
        return "Review the diagnostic details, correct the cause, and retry the original action.";
    }

    private static String safeText(String value) {
        return value == null || value.isBlank() ? "(none provided)" : value;
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                boolean ok = "Completed".equals(String.valueOf(value));
                c.setForeground(ok
                        ? FarmUi.toneForegroundColor(FarmUi.Tone.SUCCESS)
                        : FarmUi.toneForegroundColor(FarmUi.Tone.DANGER));
            }
            return c;
        }
    }

    private static String friendlySource(String source) {
        if (source == null || source.isBlank()) {
            return "Farm app";
        }
        return source.replace('_', ' ');
    }

    private static String friendlyOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return "Activity";
        }
        String words = operation.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private record ActivityRequest(String connector, String operation,
                                   Boolean successOnly, String query) {
    }

    private record ActivitySnapshot(List<SyncHistoryEntry> entries,
                                    SyncHistoryService.HistoryStats stats,
                                    List<String> connectors,
                                    List<String> operations) {
    }

    @FunctionalInterface
    private interface ExportWriter {
        void write(Path temporary) throws Exception;
    }
}
