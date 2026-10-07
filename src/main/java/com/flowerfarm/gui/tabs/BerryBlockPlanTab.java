package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.BerryBlockPlan;
import com.flowerfarm.model.BerryBlockStage;
import com.flowerfarm.model.BerrySalesChannel;
import com.flowerfarm.service.BerryBlockPlanService;

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
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
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

/** Local berry-block and picking-window planning, separate from production records. */
public final class BerryBlockPlanTab implements FlowerFarmTab {

    private final BerryBlockPlanService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private JPanel panel;
    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField blockField;
    private JTextField cropField;
    private JTextField cultivarField;
    private JTextField acresField;
    private JTextField plantingDateField;
    private JTextField firstPickDateField;
    private JTextField lastPickDateField;
    private JComboBox<BerrySalesChannel> channelField;
    private JComboBox<BerryBlockStage> stageField;
    private JTextArea notesField;
    private JButton newButton;
    private JButton saveButton;
    private JButton discardButton;
    private JButton refreshButton;
    private JLabel feedback;
    private JLabel accessBadge;
    private List<BerryBlockPlan> lastView = List.of();
    private Long editingId;
    private FormState cleanForm;
    private boolean canWrite = true;
    private boolean saving;
    private boolean loading;
    private boolean selectionAdjusting;
    private boolean refreshQueued;

    public BerryBlockPlanTab(BerryBlockPlanService service, TabHost host) {
        this(service, host, FarmDialogs.swing());
    }

    public BerryBlockPlanTab(BerryBlockPlanService service, TabHost host,
                             FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    @Override
    public String getTabTitle() {
        return "Berry blocks";
    }

    @Override
    public String getDescription() {
        return "Local berry block planning and general record links";
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
        new SwingWorker<List<BerryBlockPlan>, Void>() {
            @Override
            protected List<BerryBlockPlan> doInBackground() {
                return service.list();
            }

            @Override
            protected void done() {
                try {
                    List<BerryBlockPlan> plans = get();
                    showPlans(plans == null ? List.of() : plans);
                    showFeedback(hasUnsavedDraft()
                                    ? "Berry block plans refreshed; unsaved draft kept."
                                    : "Berry block plans refreshed.",
                            FarmUi.Tone.SUCCESS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFeedback("Could not load plans. Choose Refresh to retry.",
                            FarmUi.Tone.DANGER);
                } catch (Exception exception) {
                    showFeedback("Could not load plans. Choose Refresh to retry.",
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
            return "A berry block plan save is in progress.";
        }
        return hasUnsavedDraft()
                ? "There is an unsaved berry block draft."
                : null;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("berryBlock.page");
        panel.getAccessibleContext().setAccessibleName("Berry block plans workspace");
        accessBadge = FarmUi.statusBadge("Planning notebook", FarmUi.Tone.INFO);
        panel.add(FarmUi.pageHeader("Berry block plans",
                        "Plan berry crops, pick windows, and intended sales channels.",
                        accessBadge),
                BorderLayout.NORTH);

        JButton harvestLink = FarmUi.secondaryButton("Open Harvest Log");
        harvestLink.setName("berryBlock.openHarvest");
        harvestLink.addActionListener(event -> openGeneral("Harvest Log"));
        JButton inventoryLink = FarmUi.secondaryButton("Open Inventory");
        inventoryLink.setName("berryBlock.openInventory");
        inventoryLink.addActionListener(event -> openGeneral("Inventory"));
        JButton ordersLink = FarmUi.secondaryButton("Open Customers & Orders");
        ordersLink.setName("berryBlock.openOrders");
        ordersLink.addActionListener(event -> openGeneral("Customers & Orders"));
        JPanel scope = FarmUi.section("Planning only",
                "Block plans do not create linked harvest or stock records, packout lots, "
                        + "or U-pick bookings. Use the general tools separately.",
                FarmUi.toolbar(harvestLink, inventoryLink, ordersLink));

        WorkspaceBody body = new WorkspaceBody();
        body.add(scope, BorderLayout.NORTH);
        body.add(buildPlanList(), BorderLayout.CENTER);
        body.add(buildForm(), BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("berryBlock.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        clearForm();
        updateActions();
    }

    private JComponent buildPlanList() {
        tableModel = new DefaultTableModel(new String[]{"Block", "Crop", "Cultivar",
                "Acres", "First pick", "Last pick", "Channel", "Stage"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setName("berryBlock.planTable");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(table, "Berry block plans table");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !selectionAdjusting) {
                selectPlan();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setColumnHeaderView(table.getTableHeader());
        tableScroll.setPreferredSize(new Dimension(720, 130));
        newButton = FarmUi.primaryButton("New berry block");
        newButton.setName("berryBlock.new");
        newButton.addActionListener(event -> newPlan());
        refreshButton = FarmUi.secondaryButton("Refresh plans");
        refreshButton.setName("berryBlock.refresh");
        refreshButton.addActionListener(event -> refreshData());
        JPanel list = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        list.setOpaque(false);
        list.add(FarmUi.toolbar(newButton, refreshButton), BorderLayout.NORTH);
        list.add(tableScroll, BorderLayout.CENTER);
        return FarmUi.section("Saved berry blocks",
                "Select a row to review or change its local planning details.", list);
    }

    private JComponent buildForm() {
        blockField = field("Block name", "e.g. North blueberry block", 24);
        cropField = field("Berry crop", "e.g. blueberries", 24);
        cultivarField = field("Cultivar", "Optional, e.g. Duke", 24);
        acresField = field("Acres", "Optional, e.g. 2.50", 12);
        plantingDateField = field("Planting date", "Optional YYYY-MM-DD", 12);
        firstPickDateField = field("First planned pick", "Optional YYYY-MM-DD", 12);
        lastPickDateField = field("Last planned pick", "Optional YYYY-MM-DD", 12);
        channelField = new JComboBox<>(BerrySalesChannel.values());
        channelField.setName("berryBlock.channel");
        channelField.getAccessibleContext().setAccessibleName("Intended sales channel");
        channelField.setRenderer(enumRenderer());
        stageField = new JComboBox<>(BerryBlockStage.values());
        stageField.setName("berryBlock.stage");
        stageField.getAccessibleContext().setAccessibleName("Planning stage");
        stageField.setRenderer(enumRenderer());
        notesField = new JTextArea(3, 40);
        notesField.setName("berryBlock.notes");
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        notesField.getAccessibleContext().setAccessibleName("Block notes");
        FarmUi.initialFocus(blockField);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        addField(fields, 0, 0, "Block name *", blockField, blockField);
        addField(fields, 0, 1, "Berry crop *", cropField, cropField);
        addField(fields, 1, 0, "Cultivar", cultivarField, cultivarField);
        addField(fields, 1, 1, "Acres", acresField, acresField);
        addField(fields, 2, 0, "Planting date", plantingDateField, plantingDateField);
        addField(fields, 2, 1, "Planning stage", stageField, stageField);
        addField(fields, 3, 0, "First planned pick", firstPickDateField,
                firstPickDateField);
        addField(fields, 3, 1, "Last planned pick", lastPickDateField,
                lastPickDateField);
        addField(fields, 4, 0, "Intended sales channel", channelField, channelField);
        JScrollPane notesScroll = new JScrollPane(notesField);
        notesScroll.setPreferredSize(new Dimension(300, 82));
        addField(fields, 4, 1, "Block notes", notesScroll, notesField);

        saveButton = FarmUi.primaryButton("Save plan");
        saveButton.setName("berryBlock.save");
        saveButton.addActionListener(event -> savePlan());
        discardButton = FarmUi.secondaryButton("Discard changes");
        discardButton.setName("berryBlock.discard");
        discardButton.addActionListener(event -> discardChanges());
        feedback = FarmUi.wrappingStatusBadge("Ready to plan a berry block",
                FarmUi.Tone.NEUTRAL);
        feedback.setName("berryBlock.feedback");
        feedback.getAccessibleContext().setAccessibleName("Berry block plan status");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setOpaque(false);
        form.add(fields, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(FarmUi.toolbar(saveButton, discardButton), BorderLayout.NORTH);
        footer.add(feedback, BorderLayout.SOUTH);
        form.add(footer, BorderLayout.SOUTH);
        return FarmUi.section("Plan details",
                "Local planning fields. Save a new plan or edit the selected one.", form);
    }

    private static JTextField field(String name, String placeholder, int columns) {
        JTextField field = new JTextField(columns);
        field.setName("berryBlock." + name.toLowerCase(java.util.Locale.ROOT)
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
        host.setStatus("Opened general " + title + "; your berry block draft remains here.");
    }

    private void showPlans(List<BerryBlockPlan> plans) {
        lastView = List.copyOf(plans);
        selectionAdjusting = true;
        try {
            tableModel.setRowCount(0);
            for (BerryBlockPlan plan : lastView) {
                tableModel.addRow(new Object[]{plan.getBlockName(), plan.getCrop(),
                        plan.getCultivar(),
                        plan.getAcres() == null ? ""
                                : plan.getAcres().stripTrailingZeros().toPlainString(),
                        plan.getFirstPickDate() == null ? "" : plan.getFirstPickDate(),
                        plan.getLastPickDate() == null ? "" : plan.getLastPickDate(),
                        enumLabel(plan.getChannel()), enumLabel(plan.getStage())});
            }
            restoreSelection();
        } finally {
            selectionAdjusting = false;
        }
        if (editingId != null && !hasUnsavedDraft()) {
            lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                    .findFirst().ifPresent(this::loadPlan);
        }
    }

    private void selectPlan() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= lastView.size()) {
            return;
        }
        BerryBlockPlan selected = lastView.get(table.convertRowIndexToModel(row));
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
        loadPlan(selected);
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

    private void newPlan() {
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
        blockField.requestFocusInWindow();
    }

    private void clearForm() {
        editingId = null;
        blockField.setText("");
        cropField.setText("");
        cultivarField.setText("");
        acresField.setText("");
        plantingDateField.setText("");
        firstPickDateField.setText("");
        lastPickDateField.setText("");
        channelField.setSelectedItem(BerrySalesChannel.UNDECIDED);
        stageField.setSelectedItem(BerryBlockStage.PLANNED);
        notesField.setText("");
        cleanForm = formState();
        showFeedback("Ready to plan a berry block", FarmUi.Tone.NEUTRAL);
    }

    private void loadPlan(BerryBlockPlan plan) {
        editingId = plan.getId();
        blockField.setText(plan.getBlockName());
        cropField.setText(plan.getCrop());
        cultivarField.setText(plan.getCultivar());
        acresField.setText(plan.getAcres() == null ? "" : plan.getAcres().toPlainString());
        plantingDateField.setText(plan.getPlantingDate() == null
                ? "" : plan.getPlantingDate().toString());
        firstPickDateField.setText(plan.getFirstPickDate() == null
                ? "" : plan.getFirstPickDate().toString());
        lastPickDateField.setText(plan.getLastPickDate() == null
                ? "" : plan.getLastPickDate().toString());
        channelField.setSelectedItem(plan.getChannel());
        stageField.setSelectedItem(plan.getStage());
        notesField.setText(plan.getNotes());
        cleanForm = formState();
        showFeedback("Reviewing saved berry block “" + plan.getBlockName() + "”",
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
                "This berry block has unsaved planning changes. Discard them?",
                "Unsaved berry block draft", JOptionPane.DEFAULT_OPTION,
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
        lastView.stream().filter(plan -> editingId.equals(plan.getId()))
                .findFirst().ifPresentOrElse(this::loadPlan, this::clearForm);
    }

    private void savePlan() {
        if (!canWrite || saving || loading) {
            return;
        }
        BerryBlockPlan draft;
        try {
            draft = readDraft();
        } catch (IllegalArgumentException exception) {
            showFeedback("Not saved · " + exception.getMessage(), FarmUi.Tone.WARNING);
            return;
        }
        Long targetId = editingId;
        saving = true;
        updateActions();
        showFeedback("Saving berry block…", FarmUi.Tone.INFO);
        new SwingWorker<BerryBlockPlan, Void>() {
            @Override
            protected BerryBlockPlan doInBackground() {
                return targetId == null ? service.create(draft) : service.update(targetId, draft);
            }

            @Override
            protected void done() {
                boolean wrote = false;
                try {
                    BerryBlockPlan saved = get();
                    editingId = saved.getId();
                    cleanForm = formState();
                    showFeedback("Plan saved · " + saved.getBlockName(), FarmUi.Tone.SUCCESS);
                    host.setStatus("Berry block “" + saved.getBlockName() + "” saved.");
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

    private BerryBlockPlan readDraft() {
        String block = blockField.getText().trim();
        if (block.isEmpty()) {
            blockField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a block name.");
        }
        String crop = cropField.getText().trim();
        if (crop.isEmpty()) {
            cropField.requestFocusInWindow();
            throw new IllegalArgumentException("Enter a berry crop.");
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
            if (acres.signum() <= 0 || acres.stripTrailingZeros().scale() > 2) {
                acresField.requestFocusInWindow();
                throw new IllegalArgumentException("Enter positive acres with up to two decimals.");
            }
        }
        LocalDate planting = date(plantingDateField, "planting date");
        LocalDate firstPick = date(firstPickDateField, "first planned pick");
        LocalDate lastPick = date(lastPickDateField, "last planned pick");
        if (planting != null && firstPick != null && firstPick.isBefore(planting)) {
            firstPickDateField.requestFocusInWindow();
            throw new IllegalArgumentException("First planned pick cannot precede planting.");
        }
        if (planting != null && lastPick != null && lastPick.isBefore(planting)) {
            lastPickDateField.requestFocusInWindow();
            throw new IllegalArgumentException("Last planned pick cannot precede planting.");
        }
        if (firstPick != null && lastPick != null && lastPick.isBefore(firstPick)) {
            lastPickDateField.requestFocusInWindow();
            throw new IllegalArgumentException("Last planned pick cannot precede first pick.");
        }
        return new BerryBlockPlan(block, crop, cultivarField.getText().trim(), acres,
                planting, firstPick, lastPick,
                (BerrySalesChannel) channelField.getSelectedItem(),
                (BerryBlockStage) stageField.getSelectedItem(), notesField.getText().trim());
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
        host.setStatus("Berry block plan was not saved; its draft remains on screen.");
    }

    private void showFeedback(String text, FarmUi.Tone tone) {
        if (feedback != null) {
            FarmUi.updateStatusBadge(feedback, text, tone);
        }
    }

    private FormState formState() {
        return new FormState(blockField.getText(), cropField.getText(),
                cultivarField.getText(), acresField.getText(), plantingDateField.getText(),
                firstPickDateField.getText(), lastPickDateField.getText(),
                (BerrySalesChannel) channelField.getSelectedItem(),
                (BerryBlockStage) stageField.getSelectedItem(), notesField.getText());
    }

    private boolean hasUnsavedDraft() {
        return cleanForm != null && !cleanForm.equals(formState());
    }

    private void updateActions() {
        if (panel == null || blockField == null) {
            return;
        }
        boolean editable = canWrite && !saving;
        for (JComponent control : List.<JComponent>of(blockField, cropField, cultivarField,
                acresField, plantingDateField, firstPickDateField, lastPickDateField,
                channelField, stageField, notesField)) {
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
        if (value == BerrySalesChannel.U_PICK) {
            return "U-pick";
        }
        String words = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private record FormState(String block, String crop, String cultivar, String acres,
                             String plantingDate, String firstPickDate, String lastPickDate,
                             BerrySalesChannel channel, BerryBlockStage stage, String notes) {
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
