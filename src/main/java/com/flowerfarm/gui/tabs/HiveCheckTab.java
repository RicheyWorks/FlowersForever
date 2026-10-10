package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.HiveCheck;
import com.flowerfarm.model.HiveCheckStatus;
import com.flowerfarm.service.HiveCheckService;

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

/** Repeatable grower-entered hive observations, separate from treatment and stock. */
public final class HiveCheckTab implements FlowerFarmTab {

    private static final DataFlavor CHECK_FLAVOR = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + StatusDrag.class.getName(),
            "Saved hive check");

    private final HiveCheckService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final Map<HiveCheckStatus, JButton> statusTargets =
            new EnumMap<>(HiveCheckStatus.class);
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField apiaryNameField;
    private JTextField hiveLabelField;
    private JTextField checkDateField;
    private JTextField purposeField;
    private JTextField nextCheckDateField;
    private JComboBox<HiveCheckStatus> statusField;
    private JTextArea observationsField;
    private JTextArea actionsField;
    private JButton newButton;
    private JButton nextCheckButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private final NotebookSaveRecovery saveRecovery = new NotebookSaveRecovery();
    private List<HiveCheck> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private int selectionAdjustmentDepth;
    private boolean refreshQueued;
    private long listRevision;

    public HiveCheckTab(HiveCheckService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public HiveCheckTab(HiveCheckService service, TabHost host,
                        FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override public String getTabTitle() { return "Hive checks"; }

    @Override public String getDescription() {
        return "Dated apiary hive checks beside shared inventory and customer tools";
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
        new SwingWorker<List<HiveCheck>, Void>() {
            @Override protected List<HiveCheck> doInBackground() {
                return service.list();
            }

            @Override protected void done() {
                try {
                    List<HiveCheck> checks = get();
                    showChecks(checks == null ? List.of() : checks);
                    saveRecovery.refreshed();
                    showFeedback(hasUnsavedDraft()
                                    ? "Hive checks refreshed; unsaved draft kept."
                                    : "Hive checks refreshed.", FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load hive checks. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    saveRecovery.refreshFailed();
                    showFeedback("Could not load hive checks. Choose Refresh to retry.",
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
        if (saving) return "A hive check save is in progress.";
        return hasUnsavedDraft() ? "There is an unsaved hive check draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("hiveCheck.page");
        panel.getAccessibleContext().setAccessibleName("Hive checks workspace");
        accessBadge = FarmUi.statusBadge("Observation log", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Hive checks",
                "Record repeatable observations and the next planned check for each hive.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("hiveCheck.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("hiveCheck.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton lessonsLink = FarmUi.secondaryButton("Open Knowledge Log");
        lessonsLink.setName("hiveCheck.openKnowledge");
        lessonsLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Observations only",
                "<html>Hive checks are grower-entered observations. Status is a local note, "
                        + "not a diagnosis.<br>This screen does not manage treatments, "
                        + "registration, pollination contracts, honey stock, or sales.</html>",
                FarmUi.toolbar(inventoryLink, ordersLink, lessonsLink));

        JPanel listAndTargets = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        listAndTargets.setOpaque(false);
        listAndTargets.add(buildCheckList(), BorderLayout.CENTER);
        listAndTargets.add(buildStatusTargets(), BorderLayout.SOUTH);
        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(listAndTargets, BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("hiveCheck.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        FarmUi.revealFocusedControls(scroll);
        clearForm();
        updateActions();
    }

    private JComponent buildCheckList() {
        tableModel = new DefaultTableModel(new String[]{"Apiary", "Hive",
                "Check date", "Purpose", "Next check", "Status"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("hiveCheck.checkTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Hive checks table");
        table.setTransferHandler(new CheckDragHandler());
        if (!GraphicsEnvironment.isHeadless()) table.setDragEnabled(true);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && selectionAdjustmentDepth == 0) {
                selectCheck();
                updateActions();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New hive check");
        newButton.setName("hiveCheck.new");
        newButton.addActionListener(event -> newCheck());
        nextCheckButton = FarmUi.secondaryButton("Start next check");
        nextCheckButton.setName("hiveCheck.nextCheck");
        nextCheckButton.setToolTipText("Carry apiary, hive, and purpose forward; "
                + "enter a new check date and observations.");
        nextCheckButton.addActionListener(event -> startNextCheck());
        refreshButton = FarmUi.secondaryButton("Refresh checks");
        refreshButton.setName("hiveCheck.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, nextCheckButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved hive checks",
                "Select a check to edit or draft the next visit; drag a saved row to a status.", list);
    }

    private JComponent buildStatusTargets() {
        JPanel targets = new JPanel(new java.awt.GridLayout(1,
                HiveCheckStatus.values().length, 8, 0));
        targets.setName("hiveCheck.statusTargets");
        targets.setOpaque(false);
        for (HiveCheckStatus status : HiveCheckStatus.values()) {
            JButton target = FarmUi.secondaryButton(enumLabel(status));
            target.setName("hiveCheck.statusTarget." + status.name());
            target.setToolTipText("Drop a saved check here, or select one and click. "
                    + "Status change saves immediately.");
            target.getAccessibleContext().setAccessibleName("Set selected check status to "
                    + enumLabel(status));
            target.setTransferHandler(new StatusDropHandler(status));
            target.addActionListener(event -> moveSelectedCheckTo(status));
            statusTargets.put(status, target);
            targets.add(target);
        }
        return FarmUi.section("Update a saved check status",
                "Drag a saved row onto a status, or select a row and click. "
                        + "Save or discard form changes first.", targets);
    }

    private JComponent buildForm() {
        apiaryNameField = field("Apiary name", "e.g. North orchard apiary", 24);
        hiveLabelField = field("Hive label", "e.g. Hive 12", 24);
        checkDateField = field("Check date", "Required YYYY-MM-DD", 12);
        purposeField = field("Purpose", "Optional, e.g. spring inspection", 24);
        nextCheckDateField = field("Next check date", "Optional YYYY-MM-DD", 12);
        statusField = new JComboBox<>(HiveCheckStatus.values());
        statusField.setName("hiveCheck.status");
        statusField.getAccessibleContext().setAccessibleName("Hive check status");
        statusField.setRenderer(enumRenderer());
        observationsField = new JTextArea(3, 40);
        observationsField.setName("hiveCheck.observations");
        observationsField.setLineWrap(true);
        observationsField.setWrapStyleWord(true);
        observationsField.getAccessibleContext().setAccessibleName("Observations");
        actionsField = new JTextArea(3, 40);
        actionsField.setName("hiveCheck.actions");
        actionsField.setLineWrap(true);
        actionsField.setWrapStyleWord(true);
        actionsField.getAccessibleContext().setAccessibleName("Actions noted");
        FarmUi.initialFocus(apiaryNameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Apiary name *", apiaryNameField, apiaryNameField);
        addField(fields, 0, 1, "Hive label *", hiveLabelField, hiveLabelField);
        addField(fields, 1, 0, "Check date *", checkDateField, checkDateField);
        addField(fields, 1, 1, "Next check date", nextCheckDateField, nextCheckDateField);
        addField(fields, 2, 0, "Purpose", purposeField, purposeField);
        addField(fields, 2, 1, "Status", statusField, statusField);
        JScrollPane observationsScroll = new JScrollPane(observationsField);
        observationsScroll.setPreferredSize(new Dimension(300, 84));
        addField(fields, 3, 0, "Observations", observationsScroll, observationsField);
        JScrollPane actionsScroll = new JScrollPane(actionsField);
        actionsScroll.setPreferredSize(new Dimension(300, 84));
        addField(fields, 3, 1, "Actions noted", actionsScroll, actionsField);

        saveButton = FarmUi.primaryButton("Save check");
        saveButton.setName("hiveCheck.save");
        saveButton.addActionListener(event -> saveCheck());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("hiveCheck.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to record a hive check",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("hiveCheck.feedback");
        feedback.getAccessibleContext().setAccessibleName("Hive check save status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.SOUTH);
        footer.add(feedback, BorderLayout.NORTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Hive check details",
                "Record observations from one dated visit. Apiary, hive, and check date "
                        + "are required.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("hiveCheck." + name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_"));
        field.getAccessibleContext().setAccessibleName(name);
        FarmUi.entryControl(field, placeholder, "Local hive check detail.");
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
        host.setStatus("Opened general " + title + "; your hive check draft remains here.");
    }

    private void showChecks(List<HiveCheck> checks) {
        lastView = List.copyOf(checks);
        listRevision++;
        selectionAdjustmentDepth++;
        try {
            tableModel.setRowCount(0);
            for (HiveCheck check : lastView) {
                tableModel.addRow(new Object[]{check.getApiaryName(),
                        check.getHiveLabel(), check.getCheckDate(), check.getPurpose(),
                        check.getNextCheckDate() == null ? ""
                                : check.getNextCheckDate(), enumLabel(check.getStatus())});
            }
            restoreSelection();
        } finally {
            selectionAdjustmentDepth--;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadCheck);
        }
        updateActions();
    }

    private void selectCheck() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) return;
        HiveCheck selected = lastView.get(table.convertRowIndexToModel(row));
        if (Objects.equals(editingId, selected.getId())) return;
        if (!mayLeaveDraft()) {
            selectionAdjustmentDepth++;
            try { restoreSelection(); } finally { selectionAdjustmentDepth--; }
            return;
        }
        loadCheck(selected);
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

    private void newCheck() {
        if (!canWrite || saving || loading) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        apiaryNameField.requestFocusInWindow();
        updateActions();
    }

    private void startNextCheck() {
        if (!canWrite || saving || loading) return;
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved hive check first.", FarmUi.Tone.INFO);
            return;
        }
        HiveCheck previous = lastView.get(table.convertRowIndexToModel(row));
        if (previous.getId() == null) return;
        if (!mayLeaveDraft()) return;
        selectionAdjustmentDepth++;
        try { table.clearSelection(); } finally { selectionAdjustmentDepth--; }
        clearForm();
        apiaryNameField.setText(previous.getApiaryName());
        hiveLabelField.setText(previous.getHiveLabel());
        purposeField.setText(previous.getPurpose());
        checkDateField.requestFocusInWindow();
        showFeedback("Next check drafted for " + previous.getHiveLabel()
                + ". Enter the visit date, observations, actions, and optional next check date.",
                FarmUi.Tone.INFO);
        updateActions();
    }

    private void clearForm() {
        editingId = null;
        apiaryNameField.setText("");
        hiveLabelField.setText("");
        checkDateField.setText("");
        purposeField.setText("");
        nextCheckDateField.setText("");
        statusField.setSelectedItem(HiveCheckStatus.NOT_ASSESSED);
        observationsField.setText("");
        actionsField.setText("");
        cleanForm = formState();
        showFeedback("Ready to record a hive check", FarmUi.Tone.NEUTRAL);
    }

    private void loadCheck(HiveCheck check) {
        editingId = check.getId();
        apiaryNameField.setText(check.getApiaryName());
        hiveLabelField.setText(check.getHiveLabel());
        checkDateField.setText(check.getCheckDate().toString());
        purposeField.setText(check.getPurpose());
        nextCheckDateField.setText(check.getNextCheckDate() == null
                ? "" : check.getNextCheckDate().toString());
        statusField.setSelectedItem(check.getStatus());
        observationsField.setText(check.getObservations());
        actionsField.setText(check.getActions());
        cleanForm = formState();
        showFeedback("Reviewing " + check.getHiveLabel() + " · "
                + check.getCheckDate(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the hive check save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) return true;
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This hive check has unsaved changes. Discard them?",
                "Unsaved hive check draft", JOptionPane.DEFAULT_OPTION,
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
                .findFirst().ifPresentOrElse(this::loadCheck, this::clearForm);
    }

    private void saveCheck() {
        if (!canWrite || saving || loading) return;
        HiveCheck draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving hive check…", FarmUi.Tone.INFO);
        new SwingWorker<HiveCheck, Void>() {
            @Override protected HiveCheck doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    HiveCheck saved = get();
                    wrote = true;
                    editingId = saved.getId();
                    lastView = saveRecovery.remember(lastView, saved, HiveCheck::getId);
                    cleanForm = formState();
                    showChecks(lastView);
                    showFeedback("Hive check saved · " + saved.getHiveLabel()
                            + " · " + saved.getCheckDate(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Hive check for “" + saved.getHiveLabel() + "” saved.");
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

    private HiveCheck readDraft() {
        String apiaryName = apiaryNameField.getText().trim();
        if (apiaryName.isEmpty()) {
            apiaryNameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter an apiary name.");
        }
        String hiveLabel = hiveLabelField.getText().trim();
        if (hiveLabel.isEmpty()) {
            hiveLabelField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a hive label.");
        }
        LocalDate checkDate = date(checkDateField, "check date");
        if (checkDate == null) {
            checkDateField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a check date.");
        }
        LocalDate nextCheckDate = date(nextCheckDateField, "next check date");
        if (nextCheckDate != null && nextCheckDate.isBefore(checkDate)) {
            nextCheckDateField.requestFocusInWindow();
            throw new IllegalArgumentException("Next check date cannot precede check date.");
        }
        HiveCheck draft = new HiveCheck(apiaryName, hiveLabel, checkDate,
                purposeField.getText().trim(),
                (HiveCheckStatus) statusField.getSelectedItem(), nextCheckDate,
                observationsField.getText().trim(), actionsField.getText().trim());
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

    private void moveSelectedCheckTo(HiveCheckStatus status) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= table.getRowCount()) {
            showFeedback("Select a saved hive check first.", FarmUi.Tone.INFO);
            return;
        }
        moveCheckToStatus(lastView.get(table.convertRowIndexToModel(row)).getId(), status);
    }

    private boolean canMoveDrag(StatusDrag drag, HiveCheckStatus status) {
        return canWrite && !saving && !loading && !hasUnsavedDraft()
                && drag != null && drag.source() == this
                && drag.revision() == listRevision && drag.plan() != null
                && drag.plan().getId() != null && status != null
                && drag.plan().getStatus() != status
                && lastView.stream().anyMatch(plan -> plan == drag.plan());
    }

    private void moveCheckToStatus(Long id, HiveCheckStatus status) {
        if (!canWrite) return;
        if (saving || loading) {
            showFeedback("Wait for the current hive check action to finish.", FarmUi.Tone.INFO);
            return;
        }
        if (hasUnsavedDraft()) {
            showFeedback("Save or discard the open draft before changing a saved status.",
                    FarmUi.Tone.WARNING);
            return;
        }
        HiveCheck source = lastView.stream()
                .filter(plan -> Objects.equals(id, plan.getId()))
                .findFirst().orElse(null);
        if (source == null) {
            showFeedback("That saved check is no longer in this list. Refresh and try again.",
                    FarmUi.Tone.WARNING);
            return;
        }
        if (status == null || source.getStatus() == status) {
            showFeedback(source.getHiveLabel() + " is already " + enumLabel(source.getStatus())
                    + ".", FarmUi.Tone.INFO);
            return;
        }
        saving = true;
        updateActions();
        showFeedback("Setting " + source.getHiveLabel() + " to " + enumLabel(status) + "…",
                FarmUi.Tone.INFO);
        new SwingWorker<HiveCheck, Void>() {
            @Override protected HiveCheck doInBackground() {
                return service.updateStatus(id, status);
            }

            @Override protected void done() {
                boolean wrote = false;
                try {
                    HiveCheck saved = get();
                    wrote = true;
                    lastView = saveRecovery.remember(lastView, saved, HiveCheck::getId);
                    if (Objects.equals(editingId, saved.getId())) loadCheck(saved);
                    showChecks(lastView);
                    showFeedback(saved.getHiveLabel() + " status set to "
                            + enumLabel(saved.getStatus()) + ".", FarmUi.Tone.SUCCESS);
                    host.setStatus("Hive check “" + saved.getHiveLabel()
                            + "” status set to " + enumLabel(saved.getStatus()) + ".");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    if (wrote) {
                        showSavedRefreshFailure();
                    } else {
                        showFeedback("Status was not changed. Retry the update.", FarmUi.Tone.DANGER);
                    }
                } catch (Exception exception) {
                    if (wrote) {
                        showSavedRefreshFailure();
                    } else {
                        showFeedback("Status was not changed. Retry the update.", FarmUi.Tone.DANGER);
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

    private final class CheckDragHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent component) { return COPY; }

        @Override protected Transferable createTransferable(JComponent component) {
            int row = table.getSelectedRow();
            if (row < 0 || row >= table.getRowCount() || !canWrite || saving || loading
                    || hasUnsavedDraft()) return null;
            HiveCheck plan = lastView.get(table.convertRowIndexToModel(row));
            return plan.getId() == null ? null : new CheckTransferable(
                    new StatusDrag(HiveCheckTab.this, listRevision, plan));
        }
    }

    private final class StatusDropHandler extends TransferHandler {
        private final HiveCheckStatus status;

        private StatusDropHandler(HiveCheckStatus status) { this.status = status; }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(CHECK_FLAVOR)) return false;
            try {
                return canMoveDrag((StatusDrag) support.getTransferable()
                        .getTransferData(CHECK_FLAVOR), status);
            } catch (UnsupportedFlavorException | IOException | ClassCastException exception) {
                return false;
            }
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                StatusDrag drag = (StatusDrag) support.getTransferable()
                        .getTransferData(CHECK_FLAVOR);
                moveCheckToStatus(drag.plan().getId(), status);
                return true;
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
        }
    }

    private record StatusDrag(HiveCheckTab source, long revision,
                             HiveCheck plan) { }

    private record CheckTransferable(StatusDrag drag) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{CHECK_FLAVOR};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return CHECK_FLAVOR.equals(flavor);
        }

        @Override public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return drag;
        }
    }

    private void showSavedRefreshFailure() {
        saveRecovery.refreshFailed();
        showFeedback("Save completed. Refresh the notebook to finish updating its display.",
                FarmUi.Tone.WARNING);
    }

    private void showSaveFailure() {
        showFeedback("Not saved · complete hive check draft kept. "
                + "Check the local data store and retry.", FarmUi.Tone.DANGER);
        host.setStatus("Hive check was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) FarmUi.updateStatusBadge(feedback, saveRecovery.feedback(text),
                    saveRecovery.needsRefresh() && tone != FarmUi.Tone.DANGER
                            ? FarmUi.Tone.WARNING : tone);
    }

    private FormState formState() {
        return new FormState(apiaryNameField.getText(), hiveLabelField.getText(),
                checkDateField.getText(), purposeField.getText(),
                (HiveCheckStatus) statusField.getSelectedItem(),
                nextCheckDateField.getText(), observationsField.getText(),
                actionsField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || apiaryNameField == null) return;
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(apiaryNameField, hiveLabelField,
                checkDateField, purposeField, nextCheckDateField, statusField,
                observationsField, actionsField)) control.setEnabled(editable);
        newButton.setEnabled(editable);
        nextCheckButton.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
        saveButton.setEnabled(editable && !loading);
        discardButton.setEnabled(editable);
        refreshButton.setEnabled(!loading && !saving);
        for (JButton target : statusTargets.values()) {
            target.setEnabled(editable && !loading && table.getSelectedRow() >= 0);
        }
        FarmUi.updateStatusBadge(accessBadge,
                canWrite ? "HAND / OWNER · hive check edits" : "VIEWER · read only",
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

    private record FormState(String apiaryName, String hiveLabel,
                             String checkDate, String purpose, HiveCheckStatus status,
                             String nextCheckDate, String observations,
                             String actions) { }

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
