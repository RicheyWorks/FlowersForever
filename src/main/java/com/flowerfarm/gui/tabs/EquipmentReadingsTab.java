package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.telemetry.FarmReading;
import com.flowerfarm.telemetry.FarmReadingService;
import com.flowerfarm.telemetry.ManualFarmReading;

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
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.text.ParseException;
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
    private final JPanel root = FarmUi.page();
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
    private final JLabel feedback = FarmUi.wrappingStatusBadge(
            "Enter a reading from your equipment, or review readings brought in by a farm connection.", FarmUi.Tone.INFO);
    private final DefaultTableModel rows = new DefaultTableModel(new String[] {
            "Equipment", "Growing area", "Measurement", "Value", "Unit", "Condition", "Measured at", "Source"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private volatile boolean canWrite;
    private boolean saving;
    private boolean loading;
    private boolean latestRequested;
    private long nextCursor;
    private boolean hasMore;
    private String readingKey = UUID.randomUUID().toString();

    public EquipmentReadingsTab(FarmReadingService service, TabHost host, FarmDialogs dialogs) {
        this.service = Objects.requireNonNull(service, "service");
        this.host = Objects.requireNonNull(host, "host");
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
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
        entry.add(FarmUi.toolbar(save, next), BorderLayout.SOUTH);
        top.add(FarmUi.section("Record a measurement",
                "Use the equipment's unit. Leave the value blank for No reading.", entry), BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);
        JTable table = new JTable(rows);
        table.setName("equipmentReadings.history");
        FarmUi.configureTable(table, "Saved equipment readings");
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {180, 150, 155, 130, 80, 140, 210, 175};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        DefaultTableCellRenderer plain = new DefaultTableCellRenderer();
        plain.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, plain);
        JScrollPane history = FarmUi.tableScroll(table);
        history.setColumnHeaderView(table.getTableHeader());
        root.add(FarmUi.section("Saved equipment readings", "Each value keeps its reported unit and condition.",
                history), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.setOpaque(false);
        JPanel controls = FarmUi.toolbar(latest, older,
                new JLabel("200 readings per page; newest received first. Times include the local offset."));
        bottom.add(controls, BorderLayout.NORTH);
        feedback.setName("equipmentReadings.feedback");
        bottom.add(feedback, BorderLayout.CENTER);
        bottom.add(FarmUi.mutedLabel("Equipment files use the import helper. Automatic pairing and controls are future features."), BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);
        save.addActionListener(event -> saveReading());
        next.addActionListener(event -> newReading());
        latest.addActionListener(event -> load(0));
        older.addActionListener(event -> load(nextCursor));
        updateControls();
    }

    private static JTextField field(String name) {
        JTextField field = new JTextField();
        field.setName("equipmentReadings." + name);
        return field;
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
    @Override public JComponent getUIComponent() { return root; }
    @Override public String getDescription() { return "Save and review equipment measurements across your farm."; }
    @Override public void initialize() { refreshData(); }
    @Override public void refreshData() { load(0); }
    @Override public void applyRolePermissions(boolean allowed) {
        canWrite = allowed;
        updateControls();
    }

    private void updateControls() {
        boolean edit = canWrite && !saving;
        for (JComponent control : List.of(equipment, area, value, unit, measurement, condition, observed, save, next)) {
            control.setEnabled(edit);
        }
        latest.setEnabled(!loading);
        older.setEnabled(!loading && hasMore);
    }

    private void message(String text) {
        feedback.setText(text);
        host.setStatus(text);
    }

    private void newReading() {
        if (saving || !canWrite || !host.canMutateData()) return;
        if (!value.getText().isBlank() && dialogs.showConfirm(root,
                "Leave this draft and start a separate reading? If a save failed, check Latest readings first.",
                "Start a new reading", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                != JOptionPane.YES_OPTION) return;
        if (!canWrite || !host.canMutateData()) return;
        readingKey = UUID.randomUUID().toString();
        value.setText("");
        observed.setValue(new Date());
        condition.setSelectedIndex(0);
        message("Ready for a new reading. Equipment, area, measurement, and unit are kept.");
    }

    private void saveReading() {
        if (saving || !canWrite || !host.canMutateData()) {
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
                    message(result.replayed() > 0 ? "This reading was already saved. No duplicate was added."
                            : "Equipment reading saved.");
                    load(0);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    message("Save interrupted. Your draft is kept; refresh history before retrying.");
                } catch (ExecutionException failed) {
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

    private void load(long beforeId) {
        if (loading) {
            if (beforeId == 0) latestRequested = true;
            return;
        }
        loading = true;
        updateControls();
        new SwingWorker<FarmReadingService.ReadingPage, Void>() {
            @Override protected FarmReadingService.ReadingPage doInBackground() {
                return service.recent(beforeId, FarmReadingService.MAX_PAGE);
            }
            @Override protected void done() {
                loading = false;
                try {
                    FarmReadingService.ReadingPage page = get();
                    rows.setRowCount(0);
                    for (FarmReading reading : page.readings()) {
                        int metric = METRICS.indexOf(reading.metric());
                        int quality = CONDITIONS.indexOf(reading.quality());
                        rows.addRow(new Object[] {ManualFarmReading.equipmentName(reading), reading.location(),
                                metric < 0 ? reading.metric() : LABELS.get(metric),
                                reading.value() == null ? "No value" : reading.value().stripTrailingZeros().toPlainString(),
                                reading.unit(), quality < 0 ? reading.quality() : condition.getItemAt(quality),
                                TIME.format(reading.observedAt()),
                                ManualFarmReading.SOURCE.equals(reading.sourceId()) ? "Entered on this farm" : reading.sourceId()});
                    }
                    nextCursor = page.nextAfterId();
                    hasMore = page.hasMore();
                    if (page.readings().isEmpty()) message("No saved readings on this page. Enter your first equipment reading above.");
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
