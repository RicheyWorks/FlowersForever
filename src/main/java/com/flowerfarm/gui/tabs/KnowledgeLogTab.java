package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.service.KnowledgeService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Search, read, record, and maintain the farm's practical lessons. */
public class KnowledgeLogTab implements FlowerFarmTab {

    private static final String ALL_TAGS = "All tags";
    private static final String[] COLUMNS = {
            "Date", "What happened", "Crop / variety", "Bed / field",
            "Tags", "Confidence", "Follow-up"
    };

    private final KnowledgeService knowledgeService;
    private final TabHost host;
    private final FarmDialogs dialogs;

    private JPanel panel;
    private DefaultTableModel tableModel;
    private JTable table;
    private JTextField searchField;
    private JComboBox<String> tagFilter;
    private JCheckBox dueOnly;
    private JTextArea detailArea;
    private JLabel statusBadge;
    private JButton searchButton;
    private JButton clearButton;
    private JButton refreshButton;
    private JButton recordButton;
    private JButton editButton;
    private JButton deleteButton;
    private List<KnowledgeEntry> lastView = List.of();
    private boolean canWrite = true;
    private boolean loading;
    private boolean saving;
    private boolean savingDraft;
    private boolean updatingTags;
    private boolean searchQueued;
    private Long requestedSelectionId;
    private Long retrySaveId;
    private KnowledgeEntry retrySaveDraft;

    public KnowledgeLogTab(KnowledgeService knowledgeService, TabHost host) {
        this(knowledgeService, host, FarmDialogs.swing());
    }

    public KnowledgeLogTab(KnowledgeService knowledgeService, TabHost host,
                           FarmDialogs dialogs) {
        this.knowledgeService = knowledgeService;
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public String getTabTitle() {
        return "Knowledge Log";
    }

    @Override
    public String getDescription() {
        return "Keep farm observations, actions, results, and reusable lessons together";
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
        if (tableModel != null && !saving) {
            startSearch();
        }
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        updateActions();
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("knowledge.page");

        recordButton = FarmUi.primaryButton("Record a lesson");
        recordButton.setName("knowledge.record");
        refreshButton = FarmUi.secondaryButton("Refresh");
        refreshButton.setName("knowledge.refresh");
        panel.add(FarmUi.pageHeader(
                "Farm Knowledge Log",
                "Keep the lessons in the ground—what happened, what you tried, and what worked.",
                FarmUi.toolbar(recordButton, refreshButton)), BorderLayout.NORTH);

        JPanel content = new KnowledgeBody(buildSearchSection(), buildWorkspace());
        FarmUi.revealFocusedControls(content);
        JScrollPane scroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("knowledge.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        statusBadge = FarmUi.wrappingStatusBadge(
                "Ready to search the farm's lessons", FarmUi.Tone.NEUTRAL);
        statusBadge.setName("knowledge.status");
        panel.add(statusBadge, BorderLayout.SOUTH);
        panel.add(scroll, BorderLayout.CENTER);

        recordButton.addActionListener(event -> recordLesson());
        refreshButton.addActionListener(event -> startSearch());
        searchButton.addActionListener(event -> startSearch());
        clearButton.addActionListener(event -> clearSearch());
        searchField.addActionListener(event -> startSearch());
        tagFilter.addActionListener(event -> {
            if (!updatingTags) {
                startSearch();
            }
        });
        dueOnly.addActionListener(event -> startSearch());
        editButton.addActionListener(event -> editSelected());
        deleteButton.addActionListener(event -> deleteSelected());
        updateActions();
    }

    /** Keeps a readable lesson workspace when the window is shorter than the form. */
    private static final class KnowledgeBody extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;
        private static final int MINIMUM_WORKSPACE_HEIGHT = 300;
        private final JComponent search;

        private KnowledgeBody(JComponent search, JComponent workspace) {
            super(new BorderLayout(0, FarmUi.SECTION_GAP));
            this.search = search;
            setOpaque(false);
            add(search, BorderLayout.NORTH);
            add(workspace, BorderLayout.CENTER);
        }

        @Override public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            preferred.height = getInsets().top + getInsets().bottom
                    + search.getPreferredSize().height + FarmUi.SECTION_GAP
                    + MINIMUM_WORKSPACE_HEIGHT;
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

    private JPanel buildSearchSection() {
        searchField = FarmUi.initialFocus(FarmUi.searchField(
                "Search farm knowledge",
                "Try: What worked for mildew on this rose last August?", 38));
        searchField.setName("knowledge.search");
        tagFilter = new JComboBox<>(new String[]{ALL_TAGS});
        tagFilter.setName("knowledge.tagFilter");
        tagFilter.getAccessibleContext().setAccessibleName("Knowledge tag filter");
        dueOnly = new JCheckBox("Follow-up due");
        dueOnly.setName("knowledge.dueOnly");
        dueOnly.setOpaque(false);
        searchButton = FarmUi.primaryButton("Search lessons");
        searchButton.setName("knowledge.searchButton");
        clearButton = FarmUi.secondaryButton("Clear");
        clearButton.setName("knowledge.clearSearch");
        JPanel queryRow = FarmUi.toolbar(new JLabel("Ask or search:"), searchField);
        queryRow.setName("knowledge.queryRow");
        queryRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel filterRow = FarmUi.toolbar(
                new JLabel("Tag:"), tagFilter, dueOnly, searchButton, clearButton);
        filterRow.setName("knowledge.filterRow");
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(queryRow);
        controls.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        controls.add(filterRow);
        return FarmUi.section(
                "Find what the farm already learned",
                "Use ordinary words, crop names, months, beds, actions, results, or tags.",
                controls);
    }

    private JSplitPane buildWorkspace() {
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                int column = columnAtPoint(event.getPoint());
                if (row >= 0 && column >= 0) {
                    Object value = getValueAt(row, column);
                    if (value != null && !value.toString().isBlank()) {
                        String text = value.toString().replace("&", "&amp;")
                                .replace("<", "&lt;").replace(">", "&gt;");
                        return "<html>" + text + "</html>";
                    }
                }
                return super.getToolTipText(event);
            }
        };
        table.setName("knowledge.table");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        FarmUi.configureTable(table, "Farm knowledge lessons table");
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] columnWidths = {108, 270, 180, 120, 160, 140, 108};
        for (int index = 0; index < columnWidths.length; index++) {
            TableColumn column = table.getColumnModel().getColumn(index);
            column.setPreferredWidth(columnWidths[index]);
            column.setMinWidth(index == 0 || index == 6 ? 108 : 100);
        }
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelected();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        JPanel list = FarmUi.section(
                "Saved lessons",
                "Newest observations appear first. Select one to read the full lesson.",
                tableScroll);
        list.setMinimumSize(new Dimension(300, 0));

        detailArea = FarmUi.displayArea(18, 42);
        detailArea.setName("knowledge.detail");
        detailArea.setFont(detailArea.getFont().deriveFont(14f));
        detailArea.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        detailArea.getAccessibleContext().setAccessibleName("Selected farm lesson details");
        detailArea.setText("Select a saved lesson to see the observation, action, result, and follow-up.");
        editButton = FarmUi.secondaryButton("Edit selected");
        editButton.setName("knowledge.edit");
        deleteButton = FarmUi.dangerButton("Remove selected");
        deleteButton.setName("knowledge.delete");
        JPanel detailContent = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        detailContent.setOpaque(false);
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createLineBorder(FarmUi.borderColor()));
        detailScroll.getVerticalScrollBar().setUnitIncrement(18);
        detailContent.add(detailScroll, BorderLayout.CENTER);
        detailContent.add(FarmUi.toolbar(editButton, deleteButton), BorderLayout.SOUTH);
        JPanel detail = FarmUi.section(
                "Selected lesson",
                "Facts stay separate from confidence so the crew can judge what to repeat.",
                detailContent);
        detail.setMinimumSize(new Dimension(320, 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, list, detail) {
            private boolean dividerPlaced;

            @Override
            public void doLayout() {
                if (!dividerPlaced && getWidth() > 0) {
                    setDividerLocation(0.58);
                    dividerPlaced = true;
                }
                super.doLayout();
            }
        };
        split.setName("knowledge.workspace");
        split.setResizeWeight(0.58);
        split.setContinuousLayout(true);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private void startSearch() {
        if (tableModel == null) {
            return;
        }
        if (loading) {
            searchQueued = true;
            FarmUi.updateStatusBadge(statusBadge,
                    "Search queued · current lesson search finishing", FarmUi.Tone.INFO);
            return;
        }
        KnowledgeService.SearchRequest request = currentRequest();
        Long selectedId = requestedSelectionId != null ? requestedSelectionId
                : selectedEntry() == null ? null : selectedEntry().getId();
        loading = true;
        updateActions();
        FarmUi.updateStatusBadge(statusBadge, "Searching the farm's lessons…", FarmUi.Tone.INFO);
        if (host != null) {
            host.setStatus("Searching the Farm Knowledge Log…");
        }

        new SwingWorker<KnowledgeSnapshot, Void>() {
            @Override
            protected KnowledgeSnapshot doInBackground() {
                return new KnowledgeSnapshot(
                        List.copyOf(knowledgeService.search(request)),
                        List.copyOf(knowledgeService.distinctTags()));
            }

            @Override
            protected void done() {
                try {
                    applySnapshot(get(), selectedId);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFailure("Searching farm knowledge", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    showFailure("Searching farm knowledge",
                            exception.getCause() == null ? exception : exception.getCause());
                } finally {
                    loading = false;
                    updateActions();
                    if (searchQueued) {
                        searchQueued = false;
                        startSearch();
                    }
                }
            }
        }.execute();
    }

    private KnowledgeService.SearchRequest currentRequest() {
        String selectedTag = tagFilter == null ? "" : (String) tagFilter.getSelectedItem();
        return new KnowledgeService.SearchRequest(
                searchField == null ? "" : searchField.getText().trim(),
                "", "", ALL_TAGS.equals(selectedTag) ? "" : selectedTag,
                null, null, dueOnly != null && dueOnly.isSelected(),
                KnowledgeService.DEFAULT_LIMIT);
    }

    private void applySnapshot(KnowledgeSnapshot snapshot, Long selectedId) {
        lastView = snapshot.entries();
        updateTagChoices(snapshot.tags());
        tableModel.setRowCount(0);
        for (KnowledgeEntry entry : lastView) {
            tableModel.addRow(row(entry));
        }
        boolean restored = restoreSelection(selectedId);
        requestedSelectionId = null;
        if (!restored && !lastView.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        } else if (lastView.isEmpty()) {
            detailArea.setText("No lessons matched this search. Try fewer words, clear the tag, or record the first lesson.");
        }
        String message = lastView.size() == 1
                ? "1 farm lesson found" : lastView.size() + " farm lessons found";
        FarmUi.updateStatusBadge(statusBadge, message,
                lastView.isEmpty() ? FarmUi.Tone.NEUTRAL : FarmUi.Tone.SUCCESS);
        if (host != null) {
            host.setStatus(message + ".");
        }
    }

    private void updateTagChoices(List<String> tags) {
        String selected = tagFilter.getSelectedItem() instanceof String value ? value : ALL_TAGS;
        updatingTags = true;
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(ALL_TAGS);
        for (String tag : tags) {
            model.addElement(tag);
        }
        tagFilter.setModel(model);
        tagFilter.setSelectedItem(tags.contains(selected) ? selected : ALL_TAGS);
        updatingTags = false;
    }

    private Object[] row(KnowledgeEntry entry) {
        String crop = entry.getCropName();
        if (!entry.getVariety().isBlank()) {
            crop = crop.isBlank() ? entry.getVariety() : crop + " · " + entry.getVariety();
        }
        return new Object[]{
                entry.getObservedOn(), entry.getSummary(), crop, entry.getBedOrField(),
                String.join(", ", entry.getTags()), entry.getConfidence().getLabel(),
                entry.getFollowUpDate() == null ? "" : entry.getFollowUpDate()
        };
    }

    private boolean restoreSelection(Long id) {
        if (id == null) {
            return false;
        }
        for (int modelRow = 0; modelRow < lastView.size(); modelRow++) {
            if (id.equals(lastView.get(modelRow).getId())) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    return true;
                }
            }
        }
        return false;
    }

    private void showSelected() {
        KnowledgeEntry entry = selectedEntry();
        if (entry == null) {
            detailArea.setText("Select a saved lesson to see the observation, action, result, and follow-up.");
        } else {
            detailArea.setText(detail(entry));
            detailArea.setCaretPosition(0);
        }
        updateActions();
    }

    private KnowledgeEntry selectedEntry() {
        if (table == null || table.getSelectedRow() < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
        return modelRow >= 0 && modelRow < lastView.size() ? lastView.get(modelRow) : null;
    }

    private String detail(KnowledgeEntry entry) {
        StringBuilder text = new StringBuilder();
        text.append(entry.getObservedOn()).append(" · ")
                .append(entry.getKind().getLabel()).append("\n")
                .append(entry.getSummary()).append("\n\n");
        appendLine(text, "Crop", joined(entry.getCropName(), entry.getVariety()));
        appendLine(text, "Place", entry.getBedOrField());
        appendLine(text, "Stage", entry.getProductionStage());
        text.append("\nWHAT WE NOTICED\n").append(entry.getObservation()).append("\n");
        if (!entry.getActionTaken().isBlank()) {
            text.append("\nACTION TAKEN\n").append(entry.getActionTaken()).append("\n");
            appendLine(text, "Who", entry.getPerformedBy());
        }
        if (!entry.getResult().isBlank()) {
            text.append("\nRESULT\n").append(entry.getResult()).append("\n");
        }
        text.append("\nCONFIDENCE\n").append(entry.getConfidence().getLabel()).append("\n");
        if (entry.getFollowUpDate() != null) {
            appendLine(text, "Follow up", entry.getFollowUpDate().toString());
        }
        if (!entry.getTags().isEmpty()) {
            appendLine(text, "Tags", String.join(", ", entry.getTags()));
        }
        String links = links(entry);
        if (!links.isBlank()) {
            text.append("\nLINKED FARM RECORDS\n").append(links).append("\n");
        }
        return text.toString();
    }

    private void recordLesson() {
        if (!GuiPermissions.requireWrite(host, panel, "record a farm lesson")) {
            return;
        }
        KnowledgeEntry retained = retrySaveId == null ? retrySaveDraft : null;
        KnowledgeEntry entry = retained == null
                ? showForm(null)
                : showForm(retained, "Retry farm lesson");
        if (entry != null) {
            startSave(null, entry);
        }
    }

    private void editSelected() {
        if (!GuiPermissions.requireWrite(host, panel, "edit a farm lesson")) {
            return;
        }
        KnowledgeEntry selected = selectedEntry();
        if (selected == null) {
            return;
        }
        KnowledgeEntry retained = selected.getId().equals(retrySaveId)
                ? retrySaveDraft : selected;
        KnowledgeEntry entry = showForm(retained,
                retained == selected ? "Edit farm lesson" : "Retry farm lesson");
        if (entry != null) {
            startSave(selected.getId(), entry);
        }
    }

    private KnowledgeEntry showForm(KnowledgeEntry existing) {
        String title = existing == null ? "Record a farm lesson" : "Edit farm lesson";
        return showForm(existing, title);
    }

    private KnowledgeEntry showForm(KnowledgeEntry existing, String title) {
        KnowledgeForm form = new KnowledgeForm(existing);
        JScrollPane scroll = new JScrollPane(form.panel());
        scroll.setPreferredSize(new Dimension(720, 630));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        while (true) {
            int choice = dialogs.showConfirm(
                    panel, scroll, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
            try {
                return form.read();
            } catch (IllegalArgumentException exception) {
                dialogs.showMessage(panel, exception.getMessage(),
                        "Check this lesson", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void startSave(Long id, KnowledgeEntry entry) {
        if (saving) {
            return;
        }
        retrySaveId = id;
        retrySaveDraft = entry;
        saving = true;
        savingDraft = true;
        updateActions();
        FarmUi.updateStatusBadge(statusBadge,
                id == null ? "Recording farm lesson…" : "Updating farm lesson…",
                FarmUi.Tone.INFO);
        new SwingWorker<KnowledgeEntry, Void>() {
            @Override
            protected KnowledgeEntry doInBackground() {
                return id == null ? knowledgeService.create(entry) : knowledgeService.update(id, entry);
            }

            @Override
            protected void done() {
                try {
                    KnowledgeEntry saved = get();
                    retrySaveId = null;
                    retrySaveDraft = null;
                    requestedSelectionId = saved.getId();
                    saving = false;
                    if (host != null) {
                        host.setStatus("Farm lesson saved: " + saved.getSummary());
                        host.refreshAll();
                    } else {
                        startSearch();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showSaveFailure(exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    showSaveFailure(
                            exception.getCause() == null ? exception : exception.getCause());
                } finally {
                    saving = false;
                    savingDraft = false;
                    updateActions();
                }
            }
        }.execute();
    }

    private void deleteSelected() {
        if (!GuiPermissions.requireWrite(host, panel, "remove a farm lesson")) {
            return;
        }
        KnowledgeEntry selected = selectedEntry();
        if (selected == null) {
            return;
        }
        int answer = dialogs.showConfirm(panel,
                FarmUi.confirmationPanel(
                        "Remove this saved lesson?",
                        "“" + selected.getSummary() + "” will be removed from the Knowledge Log. "
                                + "Harvest, inventory, customer, and order records will not change.",
                        FarmUi.Tone.WARNING),
                "Remove farm lesson", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }
        saving = true;
        updateActions();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                knowledgeService.delete(selected.getId());
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    saving = false;
                    if (host != null) {
                        host.setStatus("Farm lesson removed. Operational records were unchanged.");
                        host.refreshAll();
                    } else {
                        startSearch();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFailure("Removing farm knowledge", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    showFailure("Removing farm knowledge",
                            exception.getCause() == null ? exception : exception.getCause());
                } finally {
                    saving = false;
                    updateActions();
                }
            }
        }.execute();
    }

    private void clearSearch() {
        searchField.setText("");
        updatingTags = true;
        tagFilter.setSelectedItem(ALL_TAGS);
        updatingTags = false;
        dueOnly.setSelected(false);
        startSearch();
    }

    private void showFailure(String action, Throwable failure) {
        String message = FarmUi.actionableError(action, failure);
        FarmUi.updateStatusBadge(statusBadge, message, FarmUi.Tone.DANGER);
        if (host != null) {
            host.setStatus(message);
        }
    }

    private void showSaveFailure(Throwable failure) {
        String action = retrySaveId == null ? "Retry lesson" : "Edit selected";
        String message = FarmUi.actionableError("Saving farm knowledge", failure)
                + " The complete lesson draft was kept; choose " + action
                + " to review and retry.";
        FarmUi.updateStatusBadge(statusBadge,
                "Farm lesson not saved · draft kept", FarmUi.Tone.DANGER);
        statusBadge.setToolTipText(message);
        statusBadge.getAccessibleContext().setAccessibleDescription(message);
        if (host != null) {
            host.setStatus(message);
        }
    }

    private void updateActions() {
        boolean ready = !loading && !saving;
        if (recordButton != null) {
            String label = savingDraft ? "Saving lesson…"
                    : retrySaveDraft != null && retrySaveId == null
                    ? "Retry lesson" : "Record a lesson";
            recordButton.setText(label);
            recordButton.setToolTipText(retrySaveDraft != null && retrySaveId == null
                    ? "Review the complete lesson retained after the failed save."
                    : "Record an observation, action, result, and follow-up for the farm.");
            recordButton.getAccessibleContext().setAccessibleDescription(
                    recordButton.getToolTipText());
        }
        GuiPermissions.setWritable(canWrite && ready, recordButton);
        GuiPermissions.setWritable(canWrite && ready && selectedEntry() != null,
                editButton, deleteButton);
        if (searchButton != null) {
            searchButton.setEnabled(ready);
            clearButton.setEnabled(ready);
            refreshButton.setEnabled(ready);
        }
    }

    private static String links(KnowledgeEntry entry) {
        return Arrays.asList(
                        linked("Harvest", entry.getHarvestId()),
                        linked("Inventory item", entry.getInventoryItemId()),
                        linked("Order", entry.getOrderId()),
                        linked("Customer", entry.getCustomerId()))
                .stream().filter(value -> !value.isBlank()).collect(Collectors.joining("\n"));
    }

    private static String linked(String label, Long id) {
        return id == null ? "" : label + " #" + id;
    }

    private static void appendLine(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append(label).append(": ").append(value).append("\n");
        }
    }

    private static String joined(String first, String second) {
        if (first == null || first.isBlank()) {
            return second == null ? "" : second;
        }
        return second == null || second.isBlank() ? first : first + " · " + second;
    }

    private record KnowledgeSnapshot(List<KnowledgeEntry> entries, List<String> tags) {
    }

    private static final class KnowledgeForm {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JTextField date = new JTextField(12);
        private final JTextField summary = new JTextField(42);
        private final JComboBox<KnowledgeKind> kind = new JComboBox<>(KnowledgeKind.values());
        private final JTextField crop = new JTextField(24);
        private final JTextField variety = new JTextField(24);
        private final JTextField bed = new JTextField(20);
        private final JTextField stage = new JTextField(20);
        private final JTextArea observation = area(4);
        private final JTextArea action = area(4);
        private final JTextField performedBy = new JTextField(24);
        private final JTextArea result = area(4);
        private final JTextField followUp = new JTextField(12);
        private final JComboBox<KnowledgeConfidence> confidence =
                new JComboBox<>(KnowledgeConfidence.values());
        private final JTextField tags = new JTextField(36);
        private final JTextField harvestId = new JTextField(8);
        private final JTextField inventoryId = new JTextField(8);
        private final JTextField orderId = new JTextField(8);
        private final JTextField customerId = new JTextField(8);
        private int row;

        KnowledgeForm(KnowledgeEntry existing) {
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            renderer(kind);
            renderer(confidence);
            add("Observation date *", date, "YYYY-MM-DD; future dates are not allowed.");
            add("What happened *", summary, "A short sentence the crew can scan later.");
            add("Type", kind, "Problem, opportunity, or practice worth repeating.");
            add("Crop", crop, "For example: Rose, Dahlia, or Tulip.");
            add("Variety", variety, "Optional cultivar or variety.");
            add("Bed or field", bed, "Where it happened.");
            add("Production stage", stage, "For example: vegetative, budding, harvest, or post-harvest.");
            add("What did you notice? *", new JScrollPane(observation),
                    "Record facts: signs, timing, weather, scale, or opportunity.");
            add("What action did you take?", new JScrollPane(action),
                    "Leave blank if the farm has not acted yet.");
            add("Who took the action?", performedBy, "Name or crew role.");
            add("What was the result?", new JScrollPane(result),
                    "Record what changed, including no change.");
            add("Follow-up date", followUp, "Optional YYYY-MM-DD reminder date.");
            add("Confidence", confidence, "Be honest about how repeatable this lesson is.");
            add("Tags", tags, "Comma-separated, such as mildew, irrigation, timing, pests, market.");
            addHeading("Optional links to saved farm records");
            add("Harvest #", harvestId, "Optional saved harvest id.");
            add("Inventory item #", inventoryId, "Optional saved inventory item id.");
            add("Order #", orderId, "Optional saved order id.");
            add("Customer #", customerId, "Optional saved customer id.");
            if (existing == null) {
                date.setText(LocalDate.now().toString());
            } else {
                populate(existing);
            }
        }

        JPanel panel() {
            return panel;
        }

        KnowledgeEntry read() {
            LocalDate observed = requiredDate(date.getText(), "Observation date");
            LocalDate follow = optionalDate(followUp.getText(), "Follow-up date");
            KnowledgeEntry entry = new KnowledgeEntry(
                    observed, summary.getText(), (KnowledgeKind) kind.getSelectedItem(),
                    crop.getText(), variety.getText(), bed.getText(), stage.getText(),
                    observation.getText(), action.getText(), performedBy.getText(),
                    result.getText(), follow,
                    (KnowledgeConfidence) confidence.getSelectedItem(), parseTags(tags.getText()));
            entry.setHarvestId(optionalId(harvestId.getText(), "Harvest #"));
            entry.setInventoryItemId(optionalId(inventoryId.getText(), "Inventory item #"));
            entry.setOrderId(optionalId(orderId.getText(), "Order #"));
            entry.setCustomerId(optionalId(customerId.getText(), "Customer #"));
            return entry;
        }

        private void populate(KnowledgeEntry entry) {
            date.setText(entry.getObservedOn().toString());
            summary.setText(entry.getSummary());
            kind.setSelectedItem(entry.getKind());
            crop.setText(entry.getCropName());
            variety.setText(entry.getVariety());
            bed.setText(entry.getBedOrField());
            stage.setText(entry.getProductionStage());
            observation.setText(entry.getObservation());
            action.setText(entry.getActionTaken());
            performedBy.setText(entry.getPerformedBy());
            result.setText(entry.getResult());
            followUp.setText(entry.getFollowUpDate() == null
                    ? "" : entry.getFollowUpDate().toString());
            confidence.setSelectedItem(entry.getConfidence());
            tags.setText(String.join(", ", entry.getTags()));
            setId(harvestId, entry.getHarvestId());
            setId(inventoryId, entry.getInventoryItemId());
            setId(orderId, entry.getOrderId());
            setId(customerId, entry.getCustomerId());
        }

        private void add(String label, Component field, String help) {
            GridBagConstraints left = constraints(0, row);
            left.anchor = GridBagConstraints.NORTHWEST;
            left.insets = new Insets(6, 4, 2, 10);
            Component target = field instanceof JScrollPane scroll
                    ? scroll.getViewport().getView() : field;
            if (target instanceof JComponent component) {
                component.getAccessibleContext().setAccessibleName(
                        label.replace(" *", "").trim());
            }
            JLabel caption = new JLabel(label);
            caption.setLabelFor(target);
            panel.add(caption, left);
            GridBagConstraints right = constraints(1, row);
            right.weightx = 1;
            right.fill = GridBagConstraints.HORIZONTAL;
            right.insets = new Insets(4, 2, 1, 4);
            panel.add(field, right);
            row++;
            GridBagConstraints hint = constraints(1, row++);
            hint.weightx = 1;
            hint.fill = GridBagConstraints.HORIZONTAL;
            hint.insets = new Insets(0, 4, 4, 4);
            panel.add(FarmUi.formHint(help), hint);
        }

        private void addHeading(String text) {
            GridBagConstraints heading = constraints(0, row++);
            heading.gridwidth = 2;
            heading.fill = GridBagConstraints.HORIZONTAL;
            heading.insets = new Insets(14, 4, 6, 4);
            JLabel label = new JLabel(text);
            label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
            panel.add(label, heading);
        }

        private static GridBagConstraints constraints(int x, int y) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = x;
            constraints.gridy = y;
            return constraints;
        }

        private static JTextArea area(int rows) {
            JTextArea area = new JTextArea(rows, 42);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            return area;
        }

        private static <E> void renderer(JComboBox<E> box) {
            box.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(
                        JList<?> list, Object value, int index,
                        boolean selected, boolean focus) {
                    super.getListCellRendererComponent(list, value, index, selected, focus);
                    if (value instanceof KnowledgeKind item) {
                        setText(item.getLabel());
                    } else if (value instanceof KnowledgeConfidence item) {
                        setText(item.getLabel());
                    }
                    return this;
                }
            });
        }

        private static LocalDate requiredDate(String raw, String label) {
            LocalDate value = optionalDate(raw, label);
            if (value == null) {
                throw new IllegalArgumentException(label + " is required. Use YYYY-MM-DD.");
            }
            return value;
        }

        private static LocalDate optionalDate(String raw, String label) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(raw.trim());
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(label + " must use YYYY-MM-DD.", exception);
            }
        }

        private static Long optionalId(String raw, String label) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                long value = Long.parseLong(raw.trim());
                if (value <= 0) {
                    throw new NumberFormatException("not positive");
                }
                return value;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(label + " must be a positive number.", exception);
            }
        }

        private static Collection<String> parseTags(String raw) {
            return raw == null || raw.isBlank() ? Set.of()
                    : Arrays.stream(raw.split(","))
                    .map(String::trim).filter(value -> !value.isBlank()).toList();
        }

        private static void setId(JTextField field, Long id) {
            field.setText(id == null ? "" : id.toString());
        }
    }
}
