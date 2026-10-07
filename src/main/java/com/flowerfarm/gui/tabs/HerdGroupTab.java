package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.HerdGroup;
import com.flowerfarm.service.HerdGroupService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Local livestock group planning snapshots, separate from stock and sales records. */
public final class HerdGroupTab implements FlowerFarmTab {

    private final HerdGroupService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField nameField;
    private JTextField speciesField;
    private JTextField countField;
    private JTextField locationField;
    private JTextField purposeField;
    private JTextField checkDateField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private final NotebookSaveRecovery saveRecovery = new NotebookSaveRecovery();
    private List<HerdGroup> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private boolean selectionAdjusting;
    private boolean refreshQueued;

    public HerdGroupTab(HerdGroupService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public HerdGroupTab(HerdGroupService service, TabHost host, FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public String getTabTitle() {
        return "Herd groups";
    }

    @Override
    public String getDescription() {
        return "Local livestock group and next-check planning";
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
        new SwingWorker<List<HerdGroup>, Void>() {
            @Override
            protected List<HerdGroup> doInBackground() {
                return service.list();
            }

            @Override
            protected void done() {
                try {
                    List<HerdGroup> groups = get();
                    showGroups(groups == null ? List.of() : groups);
                    saveRecovery.refreshed();
                    showFeedback(hasUnsavedDraft()
                                    ? "Herd groups refreshed; unsaved draft kept."
                                    : "Herd groups refreshed.",
                            FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    saveRecovery.refreshFailed();
                    showLoadFailure();
                } catch (Exception exception) {
                    saveRecovery.refreshFailed();
                    showLoadFailure();
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
            return "A herd group save is in progress.";
        }
        return hasUnsavedDraft() ? "There is an unsaved herd group draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("herdGroup.page");
        panel.getAccessibleContext().setAccessibleName("Herd groups workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Herd groups",
                        "Plan livestock groups, locations, counts, and next check dates.",
                        accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("herdGroup.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("herdGroup.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton knowledgeLink = FarmUi.secondaryButton("Open Knowledge Log");
        knowledgeLink.setName("herdGroup.openKnowledge");
        knowledgeLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Group planning only",
                "<html>Group snapshots do not track animal health, breeding, sales, "
                        + "or regulatory records.<br>Use shared tools for stock, orders, "
                        + "and farm notes.</html>",
                FarmUi.toolbar(inventoryLink, ordersLink, knowledgeLink));

        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(buildGroupList(), BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("herdGroup.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        FarmUi.revealFocusedControls(scroll);
        clearForm();
        updateActions();
    }

    private JComponent buildGroupList() {
        tableModel = new DefaultTableModel(new String[]{"Group", "Species", "Heads",
                "Location", "Purpose", "Next check"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("herdGroup.planTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Herd groups table");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !selectionAdjusting) {
                selectGroup();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 138));
        newButton = FarmUi.primaryButton("New herd group");
        newButton.setName("herdGroup.new");
        newButton.addActionListener(event -> newGroup());
        refreshButton = FarmUi.secondaryButton("Refresh groups");
        refreshButton.setName("herdGroup.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved herd groups",
                "Select a row to review or change its local planning details.", list);
    }

    private JComponent buildForm() {
        nameField = field("Group name", "e.g. North pasture flock", 24);
        speciesField = field("Species", "e.g. cattle, sheep, or goats", 24);
        countField = field("Head count", "e.g. 24 (0 if empty)", 12);
        locationField = field("Location", "Optional, e.g. North pasture", 24);
        purposeField = field("Purpose", "Optional, e.g. grazing rotation", 24);
        checkDateField = field("Next check date", "Optional YYYY-MM-DD", 12);
        notesField = new JTextArea(3, 40);
        notesField.setName("herdGroup.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Planning notes");
        FarmUi.initialFocus(nameField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Group name *", nameField, nameField);
        addField(fields, 0, 1, "Species *", speciesField, speciesField);
        addField(fields, 1, 0, "Head count *", countField, countField);
        addField(fields, 1, 1, "Location", locationField, locationField);
        addField(fields, 2, 0, "Purpose", purposeField, purposeField);
        addField(fields, 2, 1, "Next check date", checkDateField, checkDateField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 3, 0, 2, "Planning notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save group plan");
        saveButton.setName("herdGroup.save");
        saveButton.addActionListener(event -> saveGroup());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("herdGroup.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a herd group",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("herdGroup.feedback");
        feedback.getAccessibleContext().setAccessibleName("Herd group status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.SOUTH);
        footer.add(feedback, BorderLayout.NORTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Group details",
                "Save a new group snapshot or edit the selected one.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("herdGroup." + name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_"));
        field.getAccessibleContext().setAccessibleName(name);
        FarmUi.entryControl(field, placeholder, "Local group planning detail.");
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
        host.setStatus("Opened general " + title + "; your herd group draft remains here.");
    }

    private void showGroups(List<HerdGroup> groups) {
        lastView = List.copyOf(groups);
        selectionAdjusting = true;
        try {
            tableModel.setRowCount(0);
            for (HerdGroup group : lastView) {
                tableModel.addRow(new Object[]{group.getGroupName(), group.getSpecies(),
                        group.getHeadCount() == null ? "" : group.getHeadCount(),
                        group.getLocation(), group.getPurpose(),
                        group.getNextCheckDate() == null ? "" : group.getNextCheckDate()});
            }
            restoreSelection();
        } finally {
            selectionAdjusting = false;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(group -> editingId.equals(group.getId()))
                    .findFirst().ifPresent(this::loadGroup);
        }
    }

    private void selectGroup() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= lastView.size()) {
            return;
        }
        HerdGroup selected = lastView.get(table.convertRowIndexToModel(row));
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
        loadGroup(selected);
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

    private void newGroup() {
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
        nameField.setText("");
        speciesField.setText("");
        countField.setText("");
        locationField.setText("");
        purposeField.setText("");
        checkDateField.setText("");
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a herd group", FarmUi.Tone.NEUTRAL);
    }

    private void loadGroup(HerdGroup group) {
        editingId = group.getId();
        nameField.setText(group.getGroupName());
        speciesField.setText(group.getSpecies());
        countField.setText(group.getHeadCount() == null
                ? "" : group.getHeadCount().toString());
        locationField.setText(group.getLocation());
        purposeField.setText(group.getPurpose());
        checkDateField.setText(group.getNextCheckDate() == null
                ? "" : group.getNextCheckDate().toString());
        notesField.setText(group.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing saved herd group “" + group.getGroupName() + "”",
                FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the group save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) {
            return true;
        }
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This herd group has unsaved planning changes. Discard them?",
                "Unsaved herd group draft", JOptionPane.DEFAULT_OPTION,
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
        lastView.stream().filter(group -> editingId.equals(group.getId()))
                .findFirst().ifPresentOrElse(this::loadGroup, this::clearForm);
    }

    private void saveGroup() {
        if (!canWrite || saving || loading) {
            return;
        }
        HerdGroup draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving herd group…", FarmUi.Tone.INFO);
        new SwingWorker<HerdGroup, Void>() {
            @Override
            protected HerdGroup doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override
            protected void done() {
                boolean wrote = false;
                try {
                    HerdGroup saved = get();
                    wrote = true;
                    editingId = saved.getId();
                    lastView = saveRecovery.remember(lastView, saved, HerdGroup::getId);
                    cleanForm = formState();
                    showGroups(lastView);
                    showFeedback("Group plan saved · " + saved.getGroupName(),
                            FarmUi.Tone.SUCCESS);
                    host.setStatus("Herd group “" + saved.getGroupName() + "” saved.");
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

    private HerdGroup readDraft() {
        String groupName = nameField.getText().trim();
        if (groupName.isEmpty()) {
            nameField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a group name.");
        }
        String species = speciesField.getText().trim();
        if (species.isEmpty()) {
            speciesField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a species.");
        }
        String countText = countField.getText().trim();
        if (countText.isEmpty()) {
            countField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a head count (0 if empty).");
        }
        Integer headCount;
        try {
            headCount = Integer.valueOf(countText);
        } catch (NumberFormatException exception) {
            countField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter head count as a whole number.");
        }
        if (headCount < 0 || headCount > 1_000_000) {
            countField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Head count must be between 0 and 1,000,000.");
        }
        LocalDate nextCheck = date(checkDateField, "next check date");
        return new HerdGroup(groupName, species, headCount,
                locationField.getText().trim(), purposeField.getText().trim(), nextCheck,
                notesField.getText().trim());
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

    private void showLoadFailure() {
        showFeedback("Could not load groups. Choose Refresh to retry.",
                FarmUi.Tone.DANGER);
    }

    private void showSavedRefreshFailure() {
        saveRecovery.refreshFailed();
        showFeedback("Save completed. Refresh the notebook to finish updating its display.",
                FarmUi.Tone.WARNING);
    }

    private void showSaveFailure() {
        showFeedback("Not saved · complete planning draft kept. "
                        + "Check the local data store and retry.",
                FarmUi.Tone.DANGER);
        host.setStatus("Herd group plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) {
            FarmUi.updateStatusBadge(feedback, saveRecovery.feedback(text),
                    saveRecovery.needsRefresh() && tone != FarmUi.Tone.DANGER
                            ? FarmUi.Tone.WARNING : tone);
        }
    }

    private FormState formState() {
        return new FormState(nameField.getText(), speciesField.getText(), countField.getText(),
                locationField.getText(), purposeField.getText(), checkDateField.getText(),
                notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || nameField == null) {
            return;
        }
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(nameField, speciesField, countField,
                locationField, purposeField, checkDateField, notesField)) {
            control.setEnabled(editable);
        }
        newButton.setEnabled(editable);
        saveButton.setEnabled(editable && !loading);
        discardButton.setEnabled(editable);
        refreshButton.setEnabled(!loading && !saving);
        FarmUi.updateStatusBadge(accessBadge,
                canWrite ? "HAND / OWNER · planning edits" : "VIEWER · read only",
                canWrite ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
    }

    private record FormState(String groupName, String species, String headCount,
                             String location, String purpose, String nextCheckDate,
                             String notes) {
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
