package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.telemetry.EquipmentSpreadsheet;
import com.flowerfarm.telemetry.FarmReading;
import com.flowerfarm.telemetry.FarmReadingService;
import com.flowerfarm.telemetry.ManualFarmReading;
import com.flowerfarm.telemetry.ReadingAge;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.SpinnerDateModel;
import javax.swing.Scrollable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.text.ParseException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/** Human-readable manual observations and the shared adapter history, without hardware control. */
public final class EquipmentReadingsTab implements FlowerFarmTab {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
            .withZone(ZoneId.systemDefault());
    private static final List<String> METRICS = List.of("air.temperature", "soil.temperature",
            "soil.moisture", "air.humidity", "water.flow", "water.level", "water.ph", "light.level");
    private static final List<String> LABELS = List.of("Air temperature", "Soil temperature",
            "Soil moisture", "Air humidity", "Water flow", "Water level", "Water pH", "Light level");
    private static final List<String> CONDITIONS = List.of("GOOD", "SUSPECT", "MISSING", "ERROR");
    private final FarmReadingService service;
    private final TabHost host;
    private final FarmDialogs dialogs;
    private final FarmFileChooser files;
    private final Clock clock;
    private final JPanel root = FarmUi.page();
    private final JScrollPane page;
    private final JTextField equipment = field("equipment");
    private final JTextField area = field("area");
    private final JTextField value = field("value");
    private final JTextField unit = field("unit");
    private final JComboBox<String> measurement = new JComboBox<>(LABELS.toArray(String[]::new));
    private final JComboBox<String> condition = new JComboBox<>(new String[] {
            "Measured", "Needs checking", "No reading", "Equipment error"});
    private final JSpinner observed = new JSpinner(new SpinnerDateModel());
    private final JButton save = button("Review and save reading", "save");
    private final JButton next = button("Start a new reading", "new");
    private final JButton latest = button("Latest readings", "latest");
    private final JButton older = button("Older readings", "older");
    private final JButton bring = button("Bring in equipment spreadsheet…", "import");
    private final JButton leave = button("Leave spreadsheet preview", "leaveImport");
    private final JButton layout = button("Spreadsheet layout", "layout");
    private final JComboBox<String> view = new JComboBox<>(new String[] {
            "All saved readings", "Latest reported measurements"});
    private final JLabel pageInfo = FarmUi.mutedLabel("All saved readings · 200 per page · Newest received first · Age at last refresh");
    private final JLabel feedback = FarmUi.wrappingStatusBadge(
            "Enter a reading from your equipment, or review readings brought in by a farm connection.", FarmUi.Tone.INFO);
    private final DefaultTableModel rows = new DefaultTableModel(new String[] {
            "Equipment", "Growing area", "Measurement", "Value", "Unit", "Condition", "Measured at", "Source", "Age of reading"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private volatile boolean canWrite;
    private volatile boolean saving;
    private volatile boolean loading;
    private volatile boolean latestRequested;
    private boolean unconfirmedSave;
    private volatile boolean importing;
    private boolean unconfirmedImport;
    private List<Map<String, Object>> importDraft;
    private List<Object> cleanDraft;
    private long nextCursor;
    private boolean hasMore;
    private int displayedView;
    private String readingKey = UUID.randomUUID().toString();

    public EquipmentReadingsTab(FarmReadingService service, TabHost host, FarmDialogs dialogs) {
        this(service, host, dialogs, FarmFileChooser.swing());
    }

    public EquipmentReadingsTab(FarmReadingService service, TabHost host, FarmDialogs dialogs, FarmFileChooser files) {
        this(service, host, dialogs, files, Clock.systemUTC());
    }

    EquipmentReadingsTab(FarmReadingService service, TabHost host, FarmDialogs dialogs, FarmFileChooser files, Clock clock) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.files = Objects.requireNonNull(files, "files");
        this.clock = Objects.requireNonNull(clock, "clock");
        canWrite = host.canMutateData();
        root.setName("equipmentReadings.root");
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setOpaque(false);
        top.add(FarmUi.pageHeader("Equipment readings",
                "Keep measurements from your soil, water, air, and growing spaces.", null), BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addField(form, labeled("Equipment name", equipment));
        addField(form, labeled("Growing area or building", area));
        measurement.setName("equipmentReadings.measurement");
        addField(form, labeled("Measurement", measurement));
        condition.setName("equipmentReadings.condition");
        addField(form, labeled("Condition", condition));
        addField(form, labeled("Value (number only)", value));
        addField(form, labeled("Unit (e.g. °C, %, L/min)", unit));
        observed.setName("equipmentReadings.observed");
        JSpinner.DateEditor editor = new JSpinner.DateEditor(observed, "yyyy-MM-dd HH:mm:ss XXX");
        editor.getFormat().setLenient(false);
        observed.setEditor(editor);
        addField(form, labeled("Measured at (local time and offset)", observed));
        JPanel entry = new JPanel(new BorderLayout(0, 8));
        entry.setOpaque(false);
        entry.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new java.awt.GridLayout(2, 1, 0, 4));
        actions.setOpaque(false);
        actions.add(FarmUi.toolbar(save, next));
        actions.add(FarmUi.toolbar(bring, leave, layout));
        entry.add(actions, BorderLayout.SOUTH);
        top.add(FarmUi.section("Record a measurement",
                "Use the equipment's unit. Leave the value blank for No reading.", entry), BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);
        JTable table = new JTable(rows);
        table.setName("equipmentReadings.history");
        FarmUi.configureTable(table, "Saved equipment readings");
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {180, 150, 155, 130, 80, 140, 210, 175, 190};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        DefaultTableCellRenderer plain = new DefaultTableCellRenderer();
        plain.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, plain);
        JScrollPane history = FarmUi.tableScroll(table);
        history.setColumnHeaderView(table.getTableHeader());
        history.setPreferredSize(new Dimension(800, table.getTableHeader().getPreferredSize().height
                + table.getRowHeight() * 4 + history.getHorizontalScrollBar().getPreferredSize().height + 2));
        root.add(FarmUi.section("Saved equipment readings", "Each value keeps its reported unit and condition. Age does not prove equipment is working.",
                history), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.setOpaque(false);
        view.setName("equipmentReadings.view");
        view.getAccessibleContext().setAccessibleName("Choose saved history or latest reported measurements");
        JPanel controls = new JPanel(new BorderLayout(0, 4));
        controls.setOpaque(false);
        controls.add(FarmUi.toolbar(new JLabel("Show"), view, latest, older), BorderLayout.NORTH);
        pageInfo.setName("equipmentReadings.pageInfo");
        controls.add(pageInfo, BorderLayout.SOUTH);
        bottom.add(controls, BorderLayout.NORTH);
        feedback.setName("equipmentReadings.feedback");
        bottom.add(feedback, BorderLayout.CENTER);
        bottom.add(FarmUi.mutedLabel("Bring in up to 500 readings from a spreadsheet (CSV). Automatic pairing and controls are future features."), BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);
        JPanel body = new ReadingPage(root);
        page = new JScrollPane(body, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        page.setName("equipmentReadings.pageScroll");
        page.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        page.getVerticalScrollBar().setUnitIncrement(24);
        FarmUi.revealFocusedControls(body);
        save.addActionListener(event -> saveReading());
        next.addActionListener(event -> newReading());
        latest.addActionListener(event -> load(0));
        older.addActionListener(event -> load(nextCursor));
        view.addActionListener(event -> load(0));
        bring.addActionListener(event -> bringSpreadsheet());
        leave.addActionListener(event -> leaveSpreadsheet());
        layout.addActionListener(event -> showSpreadsheetLayout());
        cleanDraft = draftState();
        updateControls();
    }

    private static JTextField field(String name) {
        JTextField field = new JTextField();
        field.setName("equipmentReadings." + name);
        return field;
    }

    /** Leave room for saved readings while keeping the whole form reachable on smaller windows. */
    private static final class ReadingPage extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        private ReadingPage(JComponent content) {
            super(new BorderLayout());
            setOpaque(false);
            add(content, BorderLayout.CENTER);
        }

        @Override public Dimension getPreferredSize() {
            Dimension preferred = super.getPreferredSize();
            return new Dimension(preferred.width, Math.max(780, preferred.height));
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    private static void addField(JPanel form, JComponent field) {
        int index = form.getComponentCount();
        GridBagConstraints position = new GridBagConstraints();
        position.gridx = index % 4;
        position.gridy = index / 4;
        position.gridwidth = index == 6 ? 2 : 1;
        position.weightx = position.gridwidth;
        position.fill = GridBagConstraints.BOTH;
        position.insets = new Insets(4, 0, 4, position.gridx + position.gridwidth == 4 ? 0 : 10);
        form.add(field, position);
    }

    private static JButton button(String label, String name) {
        JButton button = "save".equals(name) ? FarmUi.primaryButton(label) : FarmUi.secondaryButton(label);
        button.setName("equipmentReadings." + name);
        return button;
    }

    private static JPanel labeled(String text, JComponent control) {
        JPanel panel = new JPanel(new BorderLayout(2, 2));
        panel.setOpaque(false);
        JLabel label = new JLabel(text);
        label.setLabelFor(control);
        control.getAccessibleContext().setAccessibleName(text);
        panel.add(label, BorderLayout.NORTH);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    @Override public String getTabTitle() { return "Equipment readings"; }
    @Override public JComponent getUIComponent() { return page; }
    @Override public String getDescription() { return "Save and review equipment measurements across your farm."; }
    @Override public void initialize() { refreshData(); }
    @Override public void refreshData() { load(0); }
    @Override public void applyRolePermissions(boolean allowed) {
        canWrite = allowed;
        updateControls();
    }

    private void updateControls() {
        boolean edit = canWrite && !saving && !importing && importDraft == null;
        for (JComponent control : List.of(equipment, area, value, unit, measurement, condition, observed, save, next)) {
            control.setEnabled(edit);
        }
        latest.setEnabled(!loading);
        older.setEnabled(!loading && hasMore && displayedView == view.getSelectedIndex());
        view.setEnabled(!loading);
        bring.setEnabled(canWrite && !saving && !importing && !unconfirmedSave);
        bring.setText(importDraft == null ? "Bring in equipment spreadsheet…" : "Review and retry spreadsheet…");
        leave.setEnabled(importDraft != null && !importing);
    }

    private void message(String text) {
        feedback.setText(text);
        host.setStatus(text);
    }

    private void newReading() {
        if (saving || importing || importDraft != null || !canWrite || !host.canMutateData()) return;
        if (hasUnfinishedDraft() && dialogs.showConfirm(root,
                "Leave this draft and start a separate reading? If a save failed, check Latest readings first.",
                "Start a new reading", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                != JOptionPane.YES_OPTION) return;
        if (!canWrite || !host.canMutateData()) return;
        readingKey = UUID.randomUUID().toString();
        value.setText("");
        observed.setValue(new Date());
        condition.setSelectedIndex(0);
        unconfirmedSave = false;
        cleanDraft = draftState();
        message("Ready for a new reading. Equipment, area, measurement, and unit are kept.");
    }

    public boolean hasPendingSave() { return saving || importing; }

    public String applicationExitWarning() {
        if (saving) return "Equipment readings has a save still running. Wait for its result before leaving.";
        if (importing) return "Equipment readings has a spreadsheet being read or saved. Wait for its result before leaving.";
        if (unconfirmedImport) return "Equipment readings has a spreadsheet save that could not be confirmed. Check Latest readings, then review and retry the kept preview.";
        if (importDraft != null) return "Equipment readings has a spreadsheet preview. Save it or explicitly leave the preview before changing farm types.";
        if (unconfirmedSave) return "Equipment readings has a save that could not be confirmed. Check Latest readings before retrying or leaving.";
        return hasUnfinishedDraft() ? "Equipment readings has an unfinished reading. Save it or explicitly start a new reading before changing farm types." : null;
    }

    private boolean hasUnfinishedDraft() {
        return unconfirmedSave || !draftState().equals(cleanDraft);
    }

    private List<Object> draftState() {
        return List.of(equipment.getText(), area.getText(), value.getText(), unit.getText(),
                measurement.getSelectedIndex(), condition.getSelectedIndex(),
                ((Date) observed.getValue()).getTime(),
                ((JSpinner.DateEditor) observed.getEditor()).getTextField().getText());
    }

    private void saveReading() {
        if (saving || importing || importDraft != null || !canWrite || !host.canMutateData()) {
            message("This session can review readings but cannot save them.");
            return;
        }
        final Map<String, Object> draft;
        try {
            observed.commitEdit();
            draft = ManualFarmReading.draft(readingKey, equipment.getText(), area.getText(),
                    METRICS.get(measurement.getSelectedIndex()), unit.getText(), value.getText(),
                    CONDITIONS.get(condition.getSelectedIndex()), ((Date) observed.getValue()).toInstant());
            service.validate(ManualFarmReading.SOURCE, draft);
        } catch (ParseException invalid) {
            message("Check the measured time, including its date and time-zone offset. Your draft is kept.");
            return;
        } catch (IllegalArgumentException invalid) {
            message(readableError(invalid.getMessage()) + " Your draft is kept.");
            return;
        }
        String summary = equipment.getText().trim() + " · " + area.getText().trim() + "\n"
                + measurement.getSelectedItem() + ": "
                + (draft.containsKey("value") ? draft.get("value") + " " + draft.get("unit") : "No value")
                + "\n" + condition.getSelectedItem() + " · " + TIME.format(((Date) observed.getValue()).toInstant())
                + "\n\nSave this observation to your farm records?";
        JTextArea review = new JTextArea(summary, 7, 48);
        review.setEditable(false);
        review.setLineWrap(true);
        review.setWrapStyleWord(true);
        review.getAccessibleContext().setAccessibleName("Equipment reading to review before saving");
        if (dialogs.showConfirm(root, review, "Review equipment reading",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) return;
        if (!canWrite || !host.canMutateData()) {
            message("Your access changed. The reading has not been saved; your draft is kept.");
            return;
        }
        saving = true;
        updateControls();
        message("Saving equipment reading…");
        new SwingWorker<FarmReadingService.IntakeResult, Void>() {
            @Override protected FarmReadingService.IntakeResult doInBackground() {
                if (!canWrite || !host.canMutateData()) throw new IllegalStateException("Access changed before saving.");
                return service.ingest(ManualFarmReading.SOURCE, List.of(draft));
            }
            @Override protected void done() {
                saving = false;
                try {
                    FarmReadingService.IntakeResult result = get();
                    readingKey = UUID.randomUUID().toString();
                    value.setText("");
                    observed.setValue(new Date());
                    unconfirmedSave = false;
                    cleanDraft = draftState();
                    message(result.replayed() > 0 ? "This reading was already saved. No duplicate was added."
                            : "Equipment reading saved.");
                    load(0);
                } catch (InterruptedException interrupted) {
                    unconfirmedSave = true;
                    Thread.currentThread().interrupt();
                    message("Save interrupted. Your draft is kept; refresh history before retrying.");
                } catch (ExecutionException failed) {
                    unconfirmedSave = true;
                    message("Could not confirm the save. Your draft is kept; refresh history before retrying.");
                }
                updateControls();
            }
        }.execute();
    }

    private static String readableError(String error) {
        return error.replace("GOOD and SUSPECT readings require a numeric value.",
                        "Enter a value for Measured or Needs checking.")
                .replace("MISSING readings must omit value; missing is not zero.",
                        "Leave the value blank for No reading; missing is not zero.")
                .replace("Use a finite reading with at most 18 integer and 12 fractional digits.",
                        "Use a number with up to 18 digits before and 12 digits after the decimal point.")
                .replace("Unit is required when declared and must fit its text limit without control characters.",
                        "Enter the equipment's unit on one line, up to 40 characters.")
                .replace("Location is required when declared and must fit its text limit without control characters.",
                        "Use a growing area name on one line, up to 180 characters.");
    }

    private void showSpreadsheetLayout() {
        JTextArea guide = new JTextArea("Save your equipment sheet as CSV UTF-8, up to 1 MB and 500 readings.\n"
                + "Use these headings (any order):\n" + String.join(", ", EquipmentSpreadsheet.HEADINGS)
                + "\n\nMeasurements: " + String.join(", ", LABELS)
                + "\nConditions: Measured, Needs checking, No reading, Equipment error.\n"
                + "Leave Value blank for No reading; keep zero when equipment measured zero.\n"
                + "Keep the unit in its own column. Use a decimal point for numbers.\n"
                + "Measured at example: 2026-10-01T08:30:00-07:00 (include the time-zone offset).\n"
                + "Use a different, consistent equipment name for each device on this farm.\n"
                + "Optional Reading reference: an equipment event number, reused only for the same event.\n"
                + "Without a reference, equipment + measurement + measured time identifies a reading.\n"
                + "Reopening the same sheet adds no duplicates. Changing an existing reading conflicts;\n"
                + "do not change names or times just to retry. All rows save together after your review.", 17, 62);
        guide.setEditable(false);
        guide.setLineWrap(true);
        guide.setWrapStyleWord(true);
        dialogs.showMessage(root, new JScrollPane(guide), "Equipment spreadsheet layout", JOptionPane.INFORMATION_MESSAGE);
    }

    private void bringSpreadsheet() {
        if (saving || importing || unconfirmedSave || !canWrite || !host.canMutateData()) return;
        if (importDraft != null) { reviewSpreadsheet(); return; }
        var selected = files.chooseEquipmentSpreadsheet(root, "Bring in equipment spreadsheet (CSV)");
        if (selected.isEmpty()) return;
        if (!canWrite || !host.canMutateData()) { message("Your access changed. No spreadsheet was brought in."); return; }
        importing = true;
        updateControls();
        message("Checking your spreadsheet before review. No readings have been saved yet…");
        new SwingWorker<List<Map<String, Object>>, Void>() {
            @Override protected List<Map<String, Object>> doInBackground() throws java.io.IOException {
                return EquipmentSpreadsheet.read(selected.get().toPath(), service);
            }
            @Override protected void done() {
                importing = false;
                try {
                    importDraft = get();
                    reviewSpreadsheet();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    message("Spreadsheet check interrupted. No readings were saved; your manual draft is kept.");
                } catch (ExecutionException failed) {
                    String reason = failed.getCause() instanceof IllegalArgumentException
                            ? readableError(failed.getCause().getMessage()) : "Could not read this spreadsheet. Choose a readable local CSV file.";
                    message(reason + " No readings were saved; your manual draft is kept.");
                }
                updateControls();
            }
        }.execute();
    }

    private void leaveSpreadsheet() {
        if (importing || importDraft == null) return;
        JTextArea warning = new JTextArea(unconfirmedImport
                ? "The last save could not be confirmed. Leaving this preview does not undo saved readings. Check Latest readings first. Leave the preview?"
                : "Leave this spreadsheet preview without saving it? Your manual reading stays intact.", 4, 48);
        warning.setEditable(false);
        warning.setLineWrap(true);
        warning.setWrapStyleWord(true);
        if (dialogs.showConfirm(root, warning, "Leave spreadsheet preview", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) return;
        importDraft = null;
        unconfirmedImport = false;
        updateControls();
        message("Spreadsheet preview left. Your manual reading is kept.");
    }

    private void reviewSpreadsheet() {
        updateControls();
        if (importDraft == null || !canWrite || !host.canMutateData()) {
            message("Your access changed. The spreadsheet preview is kept; nothing new was saved.");
            return;
        }
        DefaultTableModel previewRows = new DefaultTableModel(EquipmentSpreadsheet.HEADINGS.toArray(), 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (Map<String, Object> row : importDraft) {
            var reading = new FarmReading(0, EquipmentSpreadsheet.SOURCE, (String) row.get("readingKey"),
                    (String) row.get("deviceId"), (String) row.get("metric"), (String) row.get("unit"),
                    (java.math.BigDecimal) row.get("value"), (String) row.get("quality"),
                    java.time.Instant.parse((String) row.get("observedAt")), java.time.Instant.EPOCH,
                    (String) row.get("location"), null);
            int metric = METRICS.indexOf(reading.metric());
            int quality = CONDITIONS.indexOf(reading.quality());
            previewRows.addRow(new Object[] {ManualFarmReading.equipmentName(reading), reading.location(),
                    LABELS.get(metric), reading.value() == null ? "No value" : reading.value().toPlainString(),
                    reading.unit(), condition.getItemAt(quality), TIME.format(reading.observedAt())});
        }
        JTable preview = new JTable(previewRows);
        preview.setName("equipmentReadings.importPreview");
        FarmUi.configureTable(preview, "Equipment spreadsheet readings to review before saving");
        preview.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        DefaultTableCellRenderer plain = new DefaultTableCellRenderer();
        plain.putClientProperty("html.disable", Boolean.TRUE);
        preview.setDefaultRenderer(Object.class, plain);
        int[] widths = {180, 150, 150, 130, 75, 140, 210};
        for (int i = 0; i < widths.length; i++) preview.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane scroll = FarmUi.tableScroll(preview);
        scroll.setColumnHeaderView(preview.getTableHeader());
        scroll.setPreferredSize(new Dimension(800, 280));
        JPanel review = new JPanel(new BorderLayout(0, 8));
        review.add(new JLabel(importDraft.size() + " readings checked. Scroll across to review units and times. Save all together?"), BorderLayout.NORTH);
        review.add(scroll, BorderLayout.CENTER);
        review.add(new JLabel("Repeated events add no duplicates. A conflict saves none of this batch. Your manual draft stays intact."), BorderLayout.SOUTH);
        if (dialogs.showConfirm(root, review, "Review equipment spreadsheet", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
            message("Spreadsheet preview kept. Review it again or use Leave spreadsheet preview.");
            updateControls();
            return;
        }
        if (!canWrite || !host.canMutateData()) { message("Your access changed. The spreadsheet preview is kept."); updateControls(); return; }
        final List<Map<String, Object>> batch = importDraft;
        importing = true;
        updateControls();
        message("Saving the reviewed spreadsheet readings together…");
        new SwingWorker<FarmReadingService.IntakeResult, Void>() {
            @Override protected FarmReadingService.IntakeResult doInBackground() {
                if (!canWrite || !host.canMutateData()) throw new IllegalStateException("Access changed before saving.");
                return service.ingest(EquipmentSpreadsheet.SOURCE, batch);
            }
            @Override protected void done() {
                importing = false;
                try {
                    var result = get();
                    importDraft = null;
                    unconfirmedImport = false;
                    message("Spreadsheet saved: " + result.inserted() + " new readings; " + result.replayed()
                            + " already saved. Your manual draft is kept.");
                    load(0);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    unconfirmedImport = true;
                    message("Spreadsheet save interrupted. Check Latest readings, then review and retry the kept preview.");
                } catch (ExecutionException failed) {
                    unconfirmedImport = true;
                    message("Could not confirm the spreadsheet save. A conflicting event saves none of this batch. Check Latest readings, then review and retry the kept preview.");
                }
                updateControls();
            }
        }.execute();
    }

    private void load(long beforeId) {
        if (loading) {
            if (beforeId == 0) latestRequested = true;
            return;
        }
        loading = true;
        final int requestedView = view.getSelectedIndex();
        updateControls();
        new SwingWorker<FarmReadingService.ReadingPage, Void>() {
            @Override protected FarmReadingService.ReadingPage doInBackground() {
                return requestedView == 1 ? service.latestReported(beforeId, FarmReadingService.MAX_PAGE)
                        : service.recent(beforeId, FarmReadingService.MAX_PAGE);
            }
            @Override protected void done() {
                loading = false;
                try {
                    FarmReadingService.ReadingPage page = get();
                    if (requestedView == view.getSelectedIndex()) {
                        java.time.Instant shownAt = clock.instant();
                        rows.setRowCount(0);
                        for (FarmReading reading : page.readings()) {
                            int metric = METRICS.indexOf(reading.metric());
                            int quality = CONDITIONS.indexOf(reading.quality());
                            rows.addRow(new Object[] {ManualFarmReading.equipmentName(reading), reading.location(),
                                    metric < 0 ? reading.metric() : LABELS.get(metric),
                                    reading.value() == null ? "No value" : reading.value().stripTrailingZeros().toPlainString(),
                                    reading.unit(), quality < 0 ? reading.quality() : condition.getItemAt(quality),
                                    TIME.format(reading.observedAt()),
                                    ManualFarmReading.SOURCE.equals(reading.sourceId()) ? "Entered on this farm"
                                            : EquipmentSpreadsheet.SOURCE.equals(reading.sourceId()) ? "Equipment spreadsheet" : reading.sourceId(),
                                    ReadingAge.describe(reading.observedAt(), shownAt)});
                        }
                        displayedView = requestedView;
                        pageInfo.setText(requestedView == 1
                                ? "Latest reported measurements · Sources, units and areas stay separate · Age at " + TIME.format(shownAt)
                                : "All saved readings · Newest received first · Age at " + TIME.format(shownAt));
                        nextCursor = page.nextAfterId();
                        hasMore = page.hasMore();
                        if (page.readings().isEmpty()) message("No saved readings on this page. Enter your first equipment reading above.");
                        else if (feedback.getText().startsWith("Could not refresh history.")
                                || feedback.getText().startsWith("History refresh interrupted.")) message("Readings refreshed.");
                    } else {
                        latestRequested = true;
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    message("History refresh interrupted. Previous readings remain visible.");
                } catch (ExecutionException failed) {
                    message("Could not refresh history. Previous readings remain visible; try Latest readings again.");
                }
                updateControls();
                if (latestRequested) {
                    latestRequested = false;
                    load(0);
                }
            }
        }.execute();
    }
}
