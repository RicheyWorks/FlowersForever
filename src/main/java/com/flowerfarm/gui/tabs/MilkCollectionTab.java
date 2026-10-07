package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.MilkCollection;
import com.flowerfarm.model.MilkCollectionUnit;
import com.flowerfarm.service.MilkCollectionService;

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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Editable dairy collection log; entries do not change stock or orders. */
public final class MilkCollectionTab implements FlowerFarmTab {

    private final MilkCollectionService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField dateField;
    private JTextField groupField;
    private JTextField sessionField;
    private JTextField tankField;
    private JTextField amountField;
    private JComboBox<MilkCollectionUnit> unitBox;
    private JTextArea notesField;
    private JLabel amountPreview;
    private JButton newButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<MilkCollection> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private boolean selectionAdjusting;
    private boolean refreshQueued;

    public MilkCollectionTab(MilkCollectionService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public MilkCollectionTab(MilkCollectionService service, TabHost host,
                            FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public String getTabTitle() {
        return "Milk collections";
    }

    @Override
    public String getDescription() {
        return "Local milk collections by group, milking session, amount, and unit";
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
        new SwingWorker<List<MilkCollection>, Void>() {
            @Override
            protected List<MilkCollection> doInBackground() {
                return service.list();
            }

            @Override
            protected void done() {
                try {
                    List<MilkCollection> collections = get();
                    showCollections(collections == null ? List.of() : collections);
                    showFeedback(hasUnsavedDraft()
                                    ? "Milk collections refreshed; unsaved draft kept."
                                    : "Milk collections refreshed.",
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
            return "A milk collection save is in progress.";
        }
        return hasUnsavedDraft() ? "There is an unsaved milk collection draft." : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("milkCollection.page");
        panel.getAccessibleContext().setAccessibleName("Milk collections workspace");
        accessBadge = FarmUi.statusBadge("Collection log", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Milk collections",
                "Record each milking session with its measured amount and unit.",
                accessBadge), BorderLayout.NORTH);

        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("milkCollection.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("milkCollection.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JButton knowledgeLink = FarmUi.secondaryButton("Open Knowledge Log");
        knowledgeLink.setName("milkCollection.openKnowledge");
        knowledgeLink.addActionListener(event -> openGeneral("Knowledge Log"));
        JPanel scope = FarmUi.section("Milking collection log",
                "<html>Each entry is a local collection record. Amounts are kept in the "
                        + "unit you choose; no conversion or stock posting occurs.<br>"
                        + "Herd groups are separate plans when Livestock is in your mix. "
                        + "Use shared tools for stock, orders, and farm notes.<br>"
                        + "No milk quality or grade, tank sanitation, processor delivery, "
                        + "food-safety, or compliance tracking is provided.</html>",
                FarmUi.toolbar(inventoryLink, ordersLink, knowledgeLink));

        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(buildCollectionList(), BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("milkCollection.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildCollectionList() {
        tableModel = new DefaultTableModel(new String[]{"Date", "Milking group",
                "Session", "Tank / location", "Amount", "Unit"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("milkCollection.logTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Milk collections table");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !selectionAdjusting) {
                selectCollection();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 138));
        newButton = FarmUi.primaryButton("New collection");
        newButton.setName("milkCollection.new");
        newButton.addActionListener(event -> newCollection());
        refreshButton = FarmUi.secondaryButton("Refresh log");
        refreshButton.setName("milkCollection.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved collections",
                "Select a row to review or edit. Record separate sessions for the same group and day.",
                list);
    }

    private JComponent buildForm() {
        dateField = field("Collection date", "YYYY-MM-DD", 12);
        groupField = field("Milking group", "e.g. North dairy herd", 24);
        sessionField = field("Session", "e.g. Morning", 24);
        tankField = field("Tank / location", "Optional, e.g. Tank A", 24);
        amountField = field("Collected amount", "Positive amount, up to 2 decimals", 14);
        unitBox = new JComboBox<>(MilkCollectionUnit.values());
        unitBox.setName("milkCollection.unit");
        unitBox.getAccessibleContext().setAccessibleName("Amount unit");
        unitBox.setSelectedItem(MilkCollectionUnit.POUNDS);
        notesField = new JTextArea(3, 40);
        notesField.setName("milkCollection.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Collection notes");
        FarmUi.initialFocus(dateField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Collection date *", dateField, dateField);
        addField(fields, 0, 1, "Milking group *", groupField, groupField);
        addField(fields, 1, 0, "Session *", sessionField, sessionField);
        addField(fields, 1, 1, "Tank / location", tankField, tankField);
        addField(fields, 2, 0, "Collected amount *", amountField, amountField);
        addField(fields, 2, 1, "Amount unit *", unitBox, unitBox);
        amountPreview = FarmUi.wrappingStatusBadge("Enter an amount to preview this entry.",
                FarmUi.Tone.NEUTRAL);
        amountPreview.setName("milkCollection.amountPreview");
        amountPreview.getAccessibleContext().setAccessibleName("Milk collection amount preview");
        GridBagConstraints summary = new GridBagConstraints();
        summary.gridx = 0;
        summary.gridy = 3;
        summary.gridwidth = 2;
        summary.weightx = 1;
        summary.fill = GridBagConstraints.HORIZONTAL;
        summary.insets = new Insets(0, 4, 8, 4);
        fields.add(amountPreview, summary);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setName("milkCollection.notesScroll");
        notesScroll.setPreferredSize(new Dimension(300, 82));
        notesScroll.setMinimumSize(new Dimension(180, 82));
        addField(fields, 4, 0, 2, "Collection notes", notesScroll, notesField,
                GridBagConstraints.BOTH);
        DocumentListener previewListener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateAmountPreview(); }
            @Override public void removeUpdate(DocumentEvent event) { updateAmountPreview(); }
            @Override public void changedUpdate(DocumentEvent event) { updateAmountPreview(); }
        };
        amountField.getDocument().addDocumentListener(previewListener);
        unitBox.addActionListener(event -> updateAmountPreview());

        saveButton = FarmUi.primaryButton("Save collection");
        saveButton.setName("milkCollection.save");
        saveButton.addActionListener(event -> saveCollection());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("milkCollection.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to record a collection",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("milkCollection.feedback");
        feedback.getAccessibleContext().setAccessibleName("Milk collection status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Collection details",
                "Save a measured collection or edit the selected entry. Amounts remain separate from stock and orders.",
                form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("milkCollection." + name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_"));
        field.getAccessibleContext().setAccessibleName(name);
        FarmUi.entryControl(field, placeholder, "Local milk collection detail.");
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

    private void updateAmountPreview() {
        if (amountPreview == null) {
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(amountField.getText().trim());
            if (amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000.00")) > 0
                    || amount.scale() > 2) {
                FarmUi.updateStatusBadge(amountPreview,
                        "Use a positive amount up to 1,000,000.00 with at most two decimals.",
                        FarmUi.Tone.WARNING);
            } else {
                MilkCollectionUnit unit = (MilkCollectionUnit) unitBox.getSelectedItem();
                FarmUi.updateStatusBadge(amountPreview,
                        "Recorded amount · " + amount.toPlainString() + " "
                                + (unit == null ? "" : unit.symbol())
                                + " · No unit conversion or stock posting",
                        FarmUi.Tone.INFO);
            }
        } catch (NumberFormatException exception) {
            FarmUi.updateStatusBadge(amountPreview,
                    "Enter an amount to preview this entry.", FarmUi.Tone.NEUTRAL);
        }
    }

    private void openGeneral(String title) {
        host.selectTab(title);
        host.setStatus("Opened general " + title + "; your milk collection draft remains here.");
    }

    private void showCollections(List<MilkCollection> collections) {
        lastView = List.copyOf(collections);
        selectionAdjusting = true;
        try {
            tableModel.setRowCount(0);
            for (MilkCollection collection : lastView) {
                tableModel.addRow(new Object[]{collection.getCollectionDate(),
                        collection.getGroupName(), collection.getSessionName(),
                        collection.getTankLocation(), collection.getAmount().toPlainString(),
                        collection.getUnit().symbol()});
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
        MilkCollection selected = lastView.get(table.convertRowIndexToModel(row));
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
        groupField.setText("");
        sessionField.setText("");
        tankField.setText("");
        amountField.setText("");
        unitBox.setSelectedItem(MilkCollectionUnit.POUNDS);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to record a collection", FarmUi.Tone.NEUTRAL);
    }

    private void loadCollection(MilkCollection collection) {
        editingId = collection.getId();
        dateField.setText(collection.getCollectionDate().toString());
        groupField.setText(collection.getGroupName());
        sessionField.setText(collection.getSessionName());
        tankField.setText(collection.getTankLocation());
        amountField.setText(collection.getAmount().toPlainString());
        unitBox.setSelectedItem(collection.getUnit());
        notesField.setText(collection.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing " + collection.getGroupName() + " · "
                + collection.getSessionName() + " on " + collection.getCollectionDate(),
                FarmUi.Tone.INFO);
    }

    private boolean mayLeaveDraft() {
        if (saving) {
            showFeedback("Wait for the collection save to finish.", FarmUi.Tone.INFO);
            return false;
        }
        if (!hasUnsavedDraft()) {
            return true;
        }
        Object[] options = {"Keep editing", "Discard draft"};
        int choice = dialogs.showOption(panel,
                "This milk collection has unsaved changes. Discard them?",
                "Unsaved milk collection draft", JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        return choice == 1;
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
        MilkCollection draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving milk collection…", FarmUi.Tone.INFO);
        new SwingWorker<MilkCollection, Void>() {
            @Override
            protected MilkCollection doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override
            protected void done() {
                boolean wrote = false;
                try {
                    MilkCollection saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Collection saved · " + saved.getAmount().toPlainString()
                            + " " + saved.getUnit().symbol(), FarmUi.Tone.SUCCESS);
                    host.setStatus("Milk collection for “" + saved.getGroupName()
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

    private MilkCollection readDraft() {
        LocalDate date = date(dateField);
        String group = groupField.getText().trim();
        if (group.isEmpty()) {
            groupField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a milking group name.");
        }
        String session = sessionField.getText().trim();
        if (session.isEmpty()) {
            sessionField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a session name, such as Morning.");
        }
        BigDecimal amount = amount(amountField);
        MilkCollectionUnit unit = (MilkCollectionUnit) unitBox.getSelectedItem();
        if (unit == null) {
            unitBox.requestFocusInWindow();
            throw new IllegalArgumentException("Choose an amount unit.");
        }
        return new MilkCollection(date, group, session, tankField.getText().trim(),
                amount, unit, notesField.getText().trim());
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

    private static BigDecimal amount(JTextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter the collected amount.");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException exception) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException("Enter the amount as a number.");
        }
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("1000000.00")) > 0
                || value.scale() > 2) {
            field.requestFocusInWindow();
            throw new IllegalArgumentException(
                    "The amount must be above 0 and at most 1,000,000.00, with up to two decimals.");
        }
        return value;
    }

    private void showLoadFailure() {
        showFeedback("Could not load milk collections. Choose Refresh to retry.",
                FarmUi.Tone.DANGER);
    }

    private void showSaveFailure() {
        showFeedback("Not saved · complete collection draft kept. "
                + "Check the local data store and retry.", FarmUi.Tone.DANGER);
        host.setStatus("Milk collection was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) {
            FarmUi.updateStatusBadge(feedback, text, tone);
        }
    }

    private FormState formState() {
        return new FormState(dateField.getText(), groupField.getText(),
                sessionField.getText(), tankField.getText(), amountField.getText(),
                (MilkCollectionUnit) unitBox.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || dateField == null) {
            return;
        }
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(dateField, groupField,
                sessionField, tankField, amountField, unitBox, notesField)) {
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

    private record FormState(String date, String group, String session,
                             String tank, String amount, MilkCollectionUnit unit,
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
