package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.EggCollection;
import com.flowerfarm.service.EggCollectionService;

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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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

/** Editable daily egg collection log; entries do not change stock or orders. */
public final class EggCollectionTab implements FlowerFarmTab {

    private final EggCollectionService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField dateField;
    private JTextField flockField;
    private JTextField locationField;
    private JTextField collectedField;
    private JTextField unusableField;
    private JTextArea notesField;
    private JLabel usablePreview;
    private JButton newButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<EggCollection> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private boolean selectionAdjusting;
    private boolean refreshQueued;

    public EggCollectionTab(EggCollectionService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public EggCollectionTab(EggCollectionService service, TabHost host,
                            FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public String getTabTitle() {
        return "Egg collections";
    }

    @Override
    public String getDescription() {
        return "Local poultry collection counts and flock notes";
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
        new SwingWorker<List<EggCollection>, Void>() {
            @Override
            protected List<EggCollection> doInBackground() {
                return service.list();
            }

            @Override
            protected void done() {
                try {
                    List<EggCollection> collections = get();
                    showCollections(collections == null ? List.of() : collections);
                    showFeedback(hasUnsavedDraft()
                                    ? "Egg collections refreshed; unsaved draft kept."
                                    : "Egg collections refreshed.",
                            FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showLoadFailure();
                } catch (Exception exception) {
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

    public String applicationExitWarning() {
        if (saving) {
            return "An egg collection save is in progress.";
        }
        return hasUnsavedDraft() ? "There is an unsaved egg collection draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("eggCollection.page");
        panel.getAccessibleContext().setAccessibleName("Egg collections workspace");
        accessBadge = FarmUi.statusBadge("Collection log", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Egg collections",
                "Record collections by day and flock, then review usable counts.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("eggCollection.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("eggCollection.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton knowledgeLink = FarmUi.secondaryButton("Open Knowledge Log");
        knowledgeLink.setName("eggCollection.openKnowledge");
        knowledgeLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Daily collection log",
                "<html>Usable eggs = collected minus unusable. This log does not add "
                        + "inventory, pack cartons, or create sales.<br>Use shared tools for "
                        + "stock, orders, and farm notes.</html>",
                FarmUi.toolbar(inventoryLink, ordersLink, knowledgeLink));

        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(buildCollectionList(), BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("eggCollection.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildCollectionList() {
        tableModel = new DefaultTableModel(new String[]{"Date", "Flock / coop",
                "Location", "Collected", "Unusable", "Usable"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("eggCollection.logTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Egg collections table");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !selectionAdjusting) {
                selectCollection();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 138));
        newButton = FarmUi.primaryButton("New collection");
        newButton.setName("eggCollection.new");
        newButton.addActionListener(event -> newCollection());
        refreshButton = FarmUi.secondaryButton("Refresh log");
        refreshButton.setName("eggCollection.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved collections",
                "Select a row to review or edit. More than one collection can be recorded per flock and day.",
                list);
    }

    private JComponent buildForm() {
        dateField = field("Collection date", "YYYY-MM-DD", 12);
        flockField = field("Flock / coop", "e.g. East coop hens", 24);
        locationField = field("Location", "Optional, e.g. East coop", 24);
        collectedField = field("Collected eggs", "Whole number, 0 if none", 12);
        unusableField = field("Unusable eggs", "Whole number, 0 if none", 12);
        notesField = new JTextArea(3, 40);
        notesField.setName("eggCollection.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Collection notes");
        FarmUi.initialFocus(dateField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Collection date *", dateField, dateField);
        addField(fields, 0, 1, "Flock / coop *", flockField, flockField);
        addField(fields, 1, 0, "Collected eggs *", collectedField, collectedField);
        addField(fields, 1, 1, "Unusable eggs *", unusableField, unusableField);
        addField(fields, 2, 0, 2, "Location", locationField, locationField);
        usablePreview = FarmUi.wrappingStatusBadge("Enter both counts to see usable eggs.",
                FarmUi.Tone.NEUTRAL);
        usablePreview.setName("eggCollection.usablePreview");
        usablePreview.getAccessibleContext().setAccessibleName("Derived usable egg count");
        GridBagConstraints summary = new GridBagConstraints();
        summary.gridx = 0;
        summary.gridy = 3;
        summary.gridwidth = 2;
        summary.weightx = 1;
        summary.fill = GridBagConstraints.HORIZONTAL;
        summary.insets = new Insets(0, 4, 8, 4);
        fields.add(usablePreview, summary);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setName("eggCollection.notesScroll");
        notesScroll.setPreferredSize(new Dimension(300, 82));
        notesScroll.setMinimumSize(new Dimension(180, 82));
        addField(fields, 4, 0, 2, "Collection notes", notesScroll, notesField,
                GridBagConstraints.BOTH);
        DocumentListener previewListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateUsablePreview(); }
            @Override public void removeUpdate(DocumentEvent event) { updateUsablePreview(); }
            @Override public void changedUpdate(DocumentEvent event) { updateUsablePreview(); }
        };
        collectedField.getDocument().addDocumentListener(previewListener);
        unusableField.getDocument().addDocumentListener(previewListener);

        saveButton = FarmUi.primaryButton("Save collection");
        saveButton.setName("eggCollection.save");
        saveButton.addActionListener(event -> saveCollection());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("eggCollection.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to record a collection",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("eggCollection.feedback");
        feedback.getAccessibleContext().setAccessibleName("Egg collection status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Collection details",
                "Save a collection or edit the selected entry. Counts remain separate from stock and orders.",
                form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("eggCollection." + name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_"));
        field.getAccessibleContext().setAccessibleName(name);
        FarmUi.entryControl(field, placeholder, "Local egg collection detail.");
        return field;
    }

    private static void addField(JPanel target, int row, int column, String label,
                                 JComponent control, JComponent labelTarget) {
        addField(target, row, column, 1, label, control, labelTarget);
    }

    private static void addField(JPanel target, int row, int column, int span,
                                 String label, JComponent control, JComponent labelTarget) {
        addField(target, row, column, span, label, control, labelTarget,
                GridBagConstraints.HORIZONTAL);
    }

    private static void addField(JPanel target, int row, int column, int span,
                                 String label, JComponent control, JComponent labelTarget,
                                 int fill) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = column;
        cell.gridy = row;
        cell.gridwidth = span;
        cell.weightx = 1;
        cell.fill = fill;
        cell.anchor = GridBagConstraints.NORTHWEST;
        cell.insets = new Insets(0, 4, 8, 4);
        target.add(FarmUi.entryField(label, control, labelTarget, '\0'), cell);
    }

    private void updateUsablePreview() {
        if (usablePreview == null) {
            return;
        }
        try {
            int collected = Integer.parseInt(collectedField.getText().trim());
            int unusable = Integer.parseInt(unusableField.getText().trim());
            if (collected < 0 || collected > 1_000_000 || unusable < 0
                    || unusable > 1_000_000 || unusable > collected) {
                FarmUi.updateStatusBadge(usablePreview,
                        "Unusable eggs must be between 0 and the collected count.",
                        FarmUi.Tone.WARNING);
            } else {
                FarmUi.updateStatusBadge(usablePreview,
                        "Usable to plan · " + (collected - unusable) + " eggs",
                        FarmUi.Tone.INFO);
            }
        } catch (NumberFormatException exception) {
            FarmUi.updateStatusBadge(usablePreview,
                    "Enter both counts to see usable eggs.", FarmUi.Tone.NEUTRAL);
        }
    }

    private void openGeneral(String title) {
        host.selectTab(title);
        host.setStatus("Opened general " + title + "; your egg collection draft remains here.");
    }

    private void showCollections(List<EggCollection> collections) {
        lastView = List.copyOf(collections);
        selectionAdjusting = true;
        try {
            tableModel.setRowCount(0);
            for (EggCollection collection : lastView) {
                tableModel.addRow(new Object[]{collection.getCollectionDate(),
                        collection.getFlockName(), collection.getLocation(),
                        collection.getCollectedCount(), collection.getUnusableCount(),
                        collection.getUsableCount()});
            }
            restoreSelection();
        } finally {
            selectionAdjusting = false;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(entry -> editingId.equals(entry.getId()))
                    .findFirst().ifPresent(this::loadCollection);
        }
    }

    private void selectCollection() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= lastView.size()) {
            return;
        }
        EggCollection selected = lastView.get(table.convertRowIndexToModel(row));
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
        loadCollection(selected);
    }

    private void restoreSelection() {
        table.clearSelection();
        if (editingId == null) {
            return;
        }
        for (int row = 0; row < lastView.size(); row++) {
            if (editingId.equals(lastView.get(row).getId())) {
                int viewRow = table.convertRowIndexToView(row);
                table.setRowSelectionInterval(viewRow, viewRow);
                return;
            }
        }
    }

    private void newCollection() {
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
        dateField.requestFocusInWindow();
    }

    private void clearForm() {
        editingId = null;
        dateField.setText(LocalDate.now().toString());
        flockField.setText("");
        locationField.setText("");
        collectedField.setText("");
        unusableField.setText("0");
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to record a collection", FarmUi.Tone.NEUTRAL);
    }

    private void loadCollection(EggCollection collection) {
        editingId = collection.getId();
        dateField.setText(collection.getCollectionDate().toString());
        flockField.setText(collection.getFlockName());
        locationField.setText(collection.getLocation());
        collectedField.setText(collection.getCollectedCount().toString());
        unusableField.setText(collection.getUnusableCount().toString());
        notesField.setText(collection.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + collection.getFlockName() + " collection on "
                + collection.getCollectionDate(), FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the collection save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) {
            return true;
        }
        DraftDecisionGuard reviewed = new DraftDecisionGuard(editingId, formState(),
                lastView, table.getSelectedRow(), canWrite, loading);
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This egg collection has unsaved changes. Discard them?",
                "Unsaved egg collection draft", JOptionPane.DEFAULT_OPTION,
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
        lastView.stream().filter(entry -> editingId.equals(entry.getId()))
                .findFirst().ifPresentOrElse(this::loadCollection, this::clearForm);
    }

    private void saveCollection() {
        if (!canWrite || saving || loading) {
            return;
        }
        EggCollection draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving egg collection…", FarmUi.Tone.INFO);
        new SwingWorker<EggCollection, Void>() {
            @Override
            protected EggCollection doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override
            protected void done() {
                boolean wrote = false;
                try {
                    EggCollection saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Collection saved · " + saved.getUsableCount()
                            + " usable eggs", FarmUi.Tone.SUCCESS);
                    host.setStatus("Egg collection for “" + saved.getFlockName()
                            + "” saved.");
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

    private EggCollection readDraft() {
        LocalDate date = date(dateField);
        String flock = flockField.getText().trim();
        if (flock.isEmpty()) {
            flockField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a flock or coop name.");
        }
        int collected = count(collectedField, "collected");
        int unusable = count(unusableField, "unusable");
        if (unusable > collected) {
            unusableField.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Unusable eggs cannot exceed collected eggs.");
        }
        return new EggCollection(date, flock, locationField.getText().trim(),
                collected, unusable, notesField.getText().trim());
    }

    private static LocalDate date(JTextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a collection date as YYYY-MM-DD.");
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter the collection date as YYYY-MM-DD.");
        }
    }

    private static int count(JTextField field, String label) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter the " + label + " count (0 if none).");
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "Enter the " + label + " count as a whole number.");
        }
        if (value < 0 || value > 1_000_000) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "The " + label + " count must be between 0 and 1,000,000.");
        }
        return value;
    }

    private void showLoadFailure() {
        showFeedback("Could not load egg collections. Choose Refresh to retry.",
                FarmUi.Tone.DANGER);
    }

    private void showSaveFailure() {
        showFeedback("Not saved · complete collection draft kept. "
                + "Check the local data store and retry.", FarmUi.Tone.DANGER);
        host.setStatus("Egg collection was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) {
            FarmUi.updateStatusBadge(feedback, text, tone);
        }
    }

    private FormState formState() {
        return new FormState(dateField.getText(), flockField.getText(),
                locationField.getText(), collectedField.getText(),
                unusableField.getText(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || dateField == null) {
            return;
        }
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(dateField, flockField,
                locationField, collectedField, unusableField, notesField)) {
            control.setEnabled(editable);
        }
        newButton.setEnabled(editable);
        saveButton.setEnabled(editable && !loading);
        discardButton.setEnabled(editable);
        refreshButton.setEnabled(!loading && !saving);
        FarmUi.updateStatusBadge(accessBadge,
                canWrite ? "HAND / OWNER · collection edits" : "VIEWER · read only",
                canWrite ? FarmUi.Tone.SUCCESS : FarmUi.Tone.WARNING);
    }

    private record FormState(String date, String flock, String location,
                             String collected, String unusable, String notes) {
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
