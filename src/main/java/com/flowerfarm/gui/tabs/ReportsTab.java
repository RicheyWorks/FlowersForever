package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.service.ReportService;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Generate PDF farm reports (harvest + sales) for a week or custom range.
 */
public class ReportsTab implements FlowerFarmTab {

    private final ReportService reportService;
    private final TabHost host;

    private JPanel panel;
    private JTextField fromField;
    private JTextField toField;
    private JTextArea statusArea;
    private JLabel reportStatus;
    private JLabel rangePreview;
    private JButton weeklyButton;
    private JButton rangeButton;
    private boolean generating;

    public ReportsTab(ReportService reportService, TabHost host) {
        this.reportService = reportService;
        this.host = host;
    }

    @Override public String getTabTitle() { return "Reports"; }

    @Override
    public String getDescription() {
        return "Printable weekly harvest and sales reports";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    private void buildUI() {
        panel = FarmUi.page();

        weeklyButton = FarmUi.primaryButton("Create 7-day report…");
        weeklyButton.setToolTipText("Create the standard harvest and wholesale summary.");
        weeklyButton.addActionListener(event -> generateWeekly());
        reportStatus = FarmUi.statusBadge("Ready to create a report", FarmUi.Tone.NEUTRAL);
        panel.add(FarmUi.pageHeader(
                "Farm reports",
                "Save a printable harvest, production, and sales summary for your records.",
                FarmUi.toolbar(reportStatus, weeklyButton)), BorderLayout.NORTH);

        fromField = new JTextField(LocalDate.now().minusDays(6).toString(), 12);
        FarmUi.initialFocus(fromField);
        fromField.getAccessibleContext().setAccessibleName("Report start date");
        toField = new JTextField(LocalDate.now().toString(), 12);
        toField.getAccessibleContext().setAccessibleName("Report end date");
        JLabel fromLabel = new JLabel("Start date:");
        fromLabel.setLabelFor(fromField);
        fromLabel.setDisplayedMnemonic('S');
        JLabel toLabel = new JLabel("End date:");
        toLabel.setLabelFor(toField);
        toLabel.setDisplayedMnemonic('E');
        rangeButton = FarmUi.primaryButton("Create selected report…");
        rangeButton.addActionListener(event -> generateRange());
        JButton today = FarmUi.secondaryButton("Today");
        today.addActionListener(event -> applyRange(LocalDate.now(), LocalDate.now()));
        JButton lastWeek = FarmUi.secondaryButton("Last 7 days");
        lastWeek.addActionListener(event -> applyRange(
                LocalDate.now().minusDays(6), LocalDate.now()));
        JButton thisMonth = FarmUi.secondaryButton("This month");
        thisMonth.addActionListener(event -> applyRange(
                LocalDate.now().withDayOfMonth(1), LocalDate.now()));
        fromField.addActionListener(event -> generateRange());
        toField.addActionListener(event -> generateRange());
        JPanel rangeFields = FarmUi.toolbar(
                fromLabel, fromField, toLabel, toField, rangeButton);
        JPanel rangePresets = FarmUi.toolbar(
                new JLabel("Quick ranges:"), today, lastWeek, thisMonth);
        JPanel rangeToolbar = new JPanel();
        rangeToolbar.setOpaque(false);
        rangeToolbar.setLayout(new BoxLayout(rangeToolbar, BoxLayout.Y_AXIS));
        rangeToolbar.add(rangeFields);
        rangeToolbar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        rangeToolbar.add(rangePresets);
        rangeToolbar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        rangePreview = FarmUi.formHint(" ");
        rangePreview.setName("reports.rangePreview");
        rangePreview.getAccessibleContext().setAccessibleName("Selected report period");
        rangeToolbar.add(rangePreview);

        DocumentListener rangeListener = new DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateRangePreview();
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateRangePreview();
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateRangePreview();
            }
        };
        fromField.getDocument().addDocumentListener(rangeListener);
        toField.getDocument().addDocumentListener(rangeListener);

        statusArea = FarmUi.displayArea(9, 50);
        statusArea.setName("reports.summary");
        statusArea.getAccessibleContext().setAccessibleName("Report contents and export status");
        statusArea.setText(
                "Reports combine:\n"
                        + "  • Harvest log entries in the period\n"
                        + "  • Season-to-date harvest totals\n"
                        + "  • Bed / field production rollup for the period\n"
                        + "  • Confirmed and completed customer-order revenue\n\n"
                        + "Choose a date range above or create the standard 7-day report."
        );

        JPanel content = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        content.setOpaque(false);
        content.add(FarmUi.section(
                "Custom date range",
                "Use YYYY-MM-DD. Start and end dates are both included; press Enter to create.",
                rangeToolbar), BorderLayout.NORTH);
        content.add(FarmUi.section(
                "Included in every report",
                "The saved location and result appear here after generation.",
                new JScrollPane(statusArea)), BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        updateRangePreview();
    }

    private void generateWeekly() {
        LocalDate today = LocalDate.now();
        startGeneration(
                "Creating 7-day report…",
                "Preparing the last 7 days of harvest and sales activity…",
                "Creating the 7-day farm report…",
                "Creating the 7-day report",
                () -> reportService.generateWeeklyReportPdf(),
                "flowerfarm-weekly-" + today + ".pdf");
    }

    private void generateRange() {
        try {
            DateRange range = validatedRange(true);
            startGeneration(
                    "Creating selected report…",
                    "Preparing activity from " + range.from() + " through " + range.to() + "…",
                    "Creating the farm report for " + range.from() + " through " + range.to() + "…",
                    "Creating the date-range report",
                    () -> reportService.generateReportPdf(range.from(), range.to()),
                    "flowerfarm-" + range.from() + "_to_" + range.to() + ".pdf");
        } catch (Exception ex) {
            FarmUi.updateStatusBadge(reportStatus, "Report needs attention", FarmUi.Tone.DANGER);
            showError(FarmUi.actionableError("Creating the date-range report", ex));
            if (host != null) {
                host.setStatus("The report was not created. Check the highlighted dates.");
            }
        }
    }

    private void startGeneration(String badgeText, String detailText, String hostText,
                                 String errorAction, Supplier<byte[]> generator,
                                 String defaultName) {
        if (generating) {
            return;
        }
        setGenerating(true);
        FarmUi.updateStatusBadge(reportStatus, badgeText, FarmUi.Tone.INFO);
        statusArea.setText(detailText + "\n\nYou can continue using other farm tabs while this runs.");
        if (host != null) {
            host.setStatus(hostText);
        }
        new SwingWorker<byte[], Void>() {
            @Override
            protected byte[] doInBackground() {
                return generator.get();
            }

            @Override
            protected void done() {
                try {
                    byte[] pdf = get();
                    if (pdf == null || pdf.length == 0) {
                        throw new IllegalStateException("The report generator returned an empty PDF.");
                    }
                    savePdf(pdf, defaultName);
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                            ? ex.getCause() : ex;
                    FarmUi.updateStatusBadge(
                            reportStatus, "Report needs attention", FarmUi.Tone.DANGER);
                    showError(FarmUi.actionableError(errorAction, cause));
                    if (host != null) {
                        host.setStatus("The report was not created. Review the details and try again.");
                    }
                } finally {
                    setGenerating(false);
                }
            }
        }.execute();
    }

    private void savePdf(byte[] pdf, String defaultName) throws Exception {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        chooser.setDialogTitle("Save farm report PDF");
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            FarmUi.updateStatusBadge(reportStatus, "Report ready — save cancelled", FarmUi.Tone.WARNING);
            statusArea.setText("The report was created, but no file was saved.\n"
                    + "Choose Create report again when you are ready to select a location.");
            return;
        }
        File file = ensurePdfExtension(chooser.getSelectedFile());
        Path path = file.toPath();
        if (Files.exists(path)) {
            int overwrite = JOptionPane.showConfirmDialog(
                    panel,
                    "A file named " + file.getName() + " already exists. Replace it?",
                    "Replace existing report?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                FarmUi.updateStatusBadge(
                        reportStatus, "Report ready — replace cancelled", FarmUi.Tone.WARNING);
                statusArea.setText("The report was created, but the existing file was not replaced.");
                return;
            }
        }
        Files.write(path, pdf, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        FarmUi.updateStatusBadge(reportStatus, "Report saved", FarmUi.Tone.SUCCESS);
        statusArea.setText("Report saved successfully to:\n" + file.getAbsolutePath());
        if (host != null) {
            host.setStatus("PDF report saved: " + file.getName());
        }
        JOptionPane.showMessageDialog(panel,
                "Report saved to:\n" + file.getAbsolutePath(),
                "Report ready", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String msg) {
        statusArea.setText(msg);
        statusArea.setCaretPosition(0);
    }

    private void clearDateErrors() {
        FarmUi.clearFieldError(fromField);
        FarmUi.clearFieldError(toField);
    }

    private void applyRange(LocalDate from, LocalDate to) {
        fromField.setText(from.toString());
        toField.setText(to.toString());
        clearDateErrors();
        FarmUi.updateStatusBadge(reportStatus, "Range ready", FarmUi.Tone.INFO);
        updateRangePreview();
    }

    private void updateRangePreview() {
        try {
            DateRange range = validatedRange(false);
            long days = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
            String text = days + " day" + (days == 1 ? "" : "s") + " selected · "
                    + range.from() + " through " + range.to()
                    + " · harvest, bed production, orders, and revenue";
            rangePreview.setText(text);
            rangePreview.getAccessibleContext().setAccessibleName(text);
            rangeButton.setEnabled(!generating);
        } catch (RuntimeException ex) {
            String guidance = ex.getMessage() == null
                    ? "Enter a valid start and end date."
                    : ex.getMessage();
            rangePreview.setText(guidance);
            rangePreview.getAccessibleContext().setAccessibleName(guidance);
            rangeButton.setEnabled(false);
        }
    }

    private DateRange validatedRange(boolean markErrors) {
        if (markErrors) {
            clearDateErrors();
        }
        LocalDate from = parseDate(fromField, "Start date", markErrors);
        LocalDate to = parseDate(toField, "End date", markErrors);
        if (to.isBefore(from)) {
            if (markErrors) {
                FarmUi.markFieldError(toField, "End date must be on or after the start date.");
            }
            throw new IllegalArgumentException("End date must be on or after the start date.");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > 3_661) {
            if (markErrors) {
                FarmUi.markFieldError(toField, "Report range cannot exceed 10 years.");
            }
            throw new IllegalArgumentException("Report range cannot exceed 10 years.");
        }
        return new DateRange(from, to);
    }

    private LocalDate parseDate(JTextField field, String label, boolean markErrors) {
        try {
            return LocalDate.parse(field.getText().trim());
        } catch (RuntimeException exception) {
            if (markErrors) {
                FarmUi.markFieldError(
                        field, label + " must use YYYY-MM-DD, such as " + LocalDate.now() + ".");
            }
            throw new IllegalArgumentException(label + " must use YYYY-MM-DD.");
        }
    }

    private void setGenerating(boolean value) {
        generating = value;
        weeklyButton.setEnabled(!value);
        weeklyButton.setText(value ? "Creating report…" : "Create 7-day report…");
        if (value) {
            rangeButton.setEnabled(false);
        } else {
            updateRangePreview();
        }
    }

    private File ensurePdfExtension(File selected) {
        String name = selected.getName();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            return selected;
        }
        File parent = selected.getParentFile();
        return new File(parent, name + ".pdf");
    }

    private record DateRange(LocalDate from, LocalDate to) { }
}
