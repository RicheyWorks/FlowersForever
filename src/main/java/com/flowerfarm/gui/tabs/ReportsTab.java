package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.service.ReportService;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private final FarmDialogs dialogs;
    private final FarmFileChooser files;
    private FarmWorkspace workspace = FarmWorkspace.ROSE_AND_EVENTS;

    private JPanel panel;
    private JTextField fromField;
    private JTextField toField;
    private JTextArea statusArea;
    private JLabel reportStatus;
    private JLabel rangePreview;
    private JButton weeklyButton;
    private JButton rangeButton;
    private JButton previewButton;
    private JLabel harvestPreviewLabel;
    private JLabel orderPreviewLabel;
    private JLabel orderPreviewDetail;
    private JLabel committedPreviewLabel;
    private JLabel fulfilledPreviewLabel;
    private JTable productPerformanceTable;
    private JTable customerPerformanceTable;
    private DefaultTableModel productPerformanceModel;
    private DefaultTableModel customerPerformanceModel;
    private CardLayout productPerformanceLayout;
    private JPanel productPerformanceState;
    private CardLayout customerPerformanceLayout;
    private JPanel customerPerformanceState;
    private boolean generating;
    private boolean previewing;
    private boolean previewQueued;
    private DateRange activePreviewRange;
    private DateRange lastPreviewRange;

    public ReportsTab(ReportService reportService, TabHost host) {
        this(reportService, host, FarmDialogs.swing(), FarmFileChooser.swing());
    }

    public ReportsTab(ReportService reportService, TabHost host,
                      FarmDialogs dialogs, FarmFileChooser files) {
        this.reportService = reportService;
        this.host = host;
        this.dialogs = java.util.Objects.requireNonNull(dialogs, "dialogs");
        this.files = java.util.Objects.requireNonNull(files, "files");
    }

    /** Select report copy for the workspace before building the tab UI. */
    public void setWorkspace(FarmWorkspace workspace) {
        if (panel != null) {
            throw new IllegalStateException("Set the report workspace before building the tab.");
        }
        this.workspace = java.util.Objects.requireNonNull(workspace, "workspace");
    }

    private boolean flowerProductionWorkspace() {
        return workspace == FarmWorkspace.ROSE_AND_EVENTS
                || workspace == FarmWorkspace.WILDFLOWER_FARM;
    }

    private LocalDate reportToday() {
        return java.util.Objects.requireNonNullElseGet(reportService.today(), LocalDate::now);
    }

    @Override public String getTabTitle() { return "Reports"; }

    @Override
    public String getDescription() {
        return flowerProductionWorkspace()
                ? "Printable weekly harvest and sales reports"
                : "Printable weekly activity and sales reports";
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
        weeklyButton.setToolTipText(flowerProductionWorkspace()
                ? "Create the standard harvest and wholesale summary."
                : "Create a summary of recorded activity and sales.");
        weeklyButton.addActionListener(event -> generateWeekly());
        reportStatus = FarmUi.statusBadge("Ready to create a report", FarmUi.Tone.NEUTRAL);
        panel.add(FarmUi.pageHeader(
                "Farm reports",
                flowerProductionWorkspace()
                        ? "Save a printable harvest, production, and sales summary for your records."
                        : "Save a printable summary of recorded activity, orders, and sales.",
                FarmUi.toolbar(reportStatus, weeklyButton)), BorderLayout.NORTH);

        fromField = FarmUi.entryControl(
                new JTextField(reportToday().minusDays(6).toString(), 12),
                "YYYY-MM-DD", "First included report date, using YYYY-MM-DD.");
        FarmUi.initialFocus(fromField);
        fromField.getAccessibleContext().setAccessibleName("Report start date");
        toField = FarmUi.entryControl(
                new JTextField(reportToday().toString(), 12),
                "YYYY-MM-DD", "Last included report date, using YYYY-MM-DD.");
        toField.getAccessibleContext().setAccessibleName("Report end date");
        JLabel fromLabel = new JLabel("Start date:");
        fromLabel.setLabelFor(fromField);
        fromLabel.setDisplayedMnemonic('S');
        JLabel toLabel = new JLabel("End date:");
        toLabel.setLabelFor(toField);
        toLabel.setDisplayedMnemonic('E');
        rangeButton = FarmUi.primaryButton("Create selected report…");
        rangeButton.addActionListener(event -> generateRange());
        previewButton = FarmUi.secondaryButton("Preview range");
        previewButton.setName("reports.previewButton");
        previewButton.setToolTipText(
                "Load headline activity and revenue before creating the PDF.");
        previewButton.addActionListener(event -> previewRange());
        JButton today = FarmUi.secondaryButton("Today");
        today.addActionListener(event -> applyRange(reportToday(), reportToday()));
        JButton lastWeek = FarmUi.secondaryButton("Last 7 days");
        lastWeek.addActionListener(event -> applyRange(
                reportToday().minusDays(6), reportToday()));
        JButton thisMonth = FarmUi.secondaryButton("This month");
        thisMonth.addActionListener(event -> applyRange(
                reportToday().withDayOfMonth(1), reportToday()));
        fromField.addActionListener(event -> generateRange());
        toField.addActionListener(event -> generateRange());
        JPanel rangeFields = FarmUi.toolbar(
                fromLabel, fromField, toLabel, toField, previewButton);
        rangeFields.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel rangePresets = FarmUi.toolbar(
                new JLabel("Quick ranges:"), today, lastWeek, thisMonth, rangeButton);
        rangePresets.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel rangeToolbar = new JPanel();
        rangeToolbar.setOpaque(false);
        rangeToolbar.setLayout(new BoxLayout(rangeToolbar, BoxLayout.Y_AXIS));
        rangeToolbar.add(rangeFields);
        rangeToolbar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        rangeToolbar.add(rangePresets);
        rangeToolbar.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        rangePreview = FarmUi.formHint(" ");
        rangePreview.setName("reports.rangePreview");
        rangePreview.setAlignmentX(Component.LEFT_ALIGNMENT);
        rangePreview.getAccessibleContext().setAccessibleName("Selected report period");
        rangeToolbar.add(rangePreview);

        DocumentListener rangeListener = new DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                reportRangeChanged();
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                reportRangeChanged();
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {
                reportRangeChanged();
            }
        };
        fromField.getDocument().addDocumentListener(rangeListener);
        toField.getDocument().addDocumentListener(rangeListener);

        harvestPreviewLabel = previewValue("reports.preview.harvest", "—");
        orderPreviewLabel = previewValue("reports.preview.orders", "—");
        orderPreviewDetail = previewDetail("Status counts load with preview");
        committedPreviewLabel = previewValue("reports.preview.committedRevenue", "—");
        fulfilledPreviewLabel = previewValue("reports.preview.fulfilledRevenue", "—");
        JPanel previewMetrics = new JPanel(new GridLayout(1, 4, FarmUi.CONTROL_GAP, 0));
        previewMetrics.setOpaque(false);
        previewMetrics.setName("reports.preview.metrics");
        previewMetrics.add(FarmUi.metricCard(
                "Harvest activity", harvestPreviewLabel,
                previewDetail("Recorded harvest logs"), FarmUi.Tone.SUCCESS));
        previewMetrics.add(FarmUi.metricCard(
                "Orders", orderPreviewLabel, orderPreviewDetail, FarmUi.Tone.INFO));
        previewMetrics.add(FarmUi.metricCard(
                "Committed revenue", committedPreviewLabel,
                previewDetail("Confirmed + fulfilled"), FarmUi.Tone.WARNING));
        previewMetrics.add(FarmUi.metricCard(
                "Fulfilled revenue", fulfilledPreviewLabel,
                previewDetail("Completed orders only"), FarmUi.Tone.SUCCESS));

        statusArea = FarmUi.displayArea(5, 50);
        statusArea.setName("reports.summary");
        statusArea.getAccessibleContext().setAccessibleName("Report contents and export status");
        statusArea.setText(flowerProductionWorkspace()
                ? "Preview the selected range to verify activity and revenue before export.\n\n"
                        + "Every PDF also includes harvest detail, season-to-date crop totals, "
                        + "bed / field production, product performance, customer performance, "
                        + "and order detail."
                : "Preview recorded activity and revenue before export.\n\n"
                        + "The PDF includes order detail, product and customer performance, "
                        + "and any harvests in the shared log. Crop and field totals reflect "
                        + "those logged harvests.");
        productPerformanceModel = readOnlyModel(new String[]{
                "Product", "Quantity", "Unit", "Orders", "Avg price", "Committed", "Fulfilled"
        });
        productPerformanceTable = new JTable(productPerformanceModel);
        productPerformanceTable.setName("reports.productPerformance");
        FarmUi.configureTable(productPerformanceTable,
                "Product sales performance ranked by committed revenue");
        productPerformanceTable.setAutoCreateRowSorter(true);

        customerPerformanceModel = readOnlyModel(new String[]{
                "Customer", "Orders", "Avg order", "Committed", "Fulfilled"
        });
        customerPerformanceTable = new JTable(customerPerformanceModel);
        customerPerformanceTable.setName("reports.customerPerformance");
        FarmUi.configureTable(customerPerformanceTable,
                "Customer sales performance ranked by committed revenue");
        customerPerformanceTable.setAutoCreateRowSorter(true);

        JTabbedPane performanceTabs = new JTabbedPane();
        performanceTabs.setName("reports.performanceTabs");
        performanceTabs.getAccessibleContext().setAccessibleName(
                "Report overview, product performance, and customer performance");
        performanceTabs.addTab("Overview", new JScrollPane(statusArea));
        productPerformanceLayout = new CardLayout();
        productPerformanceState = new JPanel(productPerformanceLayout);
        productPerformanceState.add(FarmUi.tableScroll(productPerformanceTable), "table");
        productPerformanceState.add(FarmUi.emptyState(
                "No product performance to show",
                "Preview another date range, or confirm an order to start tracking product demand."),
                "empty");
        customerPerformanceLayout = new CardLayout();
        customerPerformanceState = new JPanel(customerPerformanceLayout);
        customerPerformanceState.add(FarmUi.tableScroll(customerPerformanceTable), "table");
        customerPerformanceState.add(FarmUi.emptyState(
                "No customer performance to show",
                "Preview another date range, or add and confirm the first customer order."),
                "empty");
        productPerformanceLayout.show(productPerformanceState, "empty");
        customerPerformanceLayout.show(customerPerformanceState, "empty");
        performanceTabs.addTab("Products", productPerformanceState);
        performanceTabs.addTab("Customers", customerPerformanceState);
        JPanel previewWorkspace = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        previewWorkspace.setOpaque(false);
        previewWorkspace.add(previewMetrics, BorderLayout.NORTH);
        previewWorkspace.add(performanceTabs, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        content.setOpaque(false);
        content.add(FarmUi.section(
                "Custom date range",
                "Use YYYY-MM-DD. Start and end dates are both included; press Enter to create.",
                rangeToolbar), BorderLayout.NORTH);
        content.add(FarmUi.section(
                "Range preview & report status",
                "Preview distinguishes committed revenue from completed sales; the saved location appears here after export.",
                previewWorkspace), BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        updateRangePreview();
    }

    private void previewRange() {
        if (generating) {
            return;
        }
        if (previewing) {
            queueLatestPreviewIfNeeded();
            return;
        }
        try {
            DateRange range = validatedRange(true);
            activePreviewRange = range;
            previewQueued = false;
            setPreviewing(true);
            FarmUi.updateStatusBadge(reportStatus, "Previewing selected range…", FarmUi.Tone.INFO);
            statusArea.setText("Loading harvest and order totals for "
                    + range.from() + " through " + range.to() + "…");
            new SwingWorker<ReportService.ReportPreview, Void>() {
                @Override
                protected ReportService.ReportPreview doInBackground() {
                    return reportService.preview(range.from(), range.to());
                }

                @Override
                protected void done() {
                    try {
                        ReportService.ReportPreview preview = get();
                        if (preview == null) {
                            throw new IllegalStateException("The report preview returned no data.");
                        }
                        if (!previewQueued && matchesSelectedRange(range)) {
                            applyPreview(preview);
                        } else {
                            FarmUi.updateStatusBadge(
                                    reportStatus, "Latest range queued · starting preview",
                                    FarmUi.Tone.INFO);
                            statusArea.setText(
                                    "The dates changed while the earlier preview was loading. "
                                            + "That result was discarded; the latest range will load next.");
                        }
                    } catch (Exception exception) {
                        Throwable cause = exception instanceof ExecutionException
                                && exception.getCause() != null
                                ? exception.getCause() : exception;
                        if (!previewQueued) {
                            showPreviewFailure(cause);
                        }
                    } finally {
                        boolean runLatest = previewQueued;
                        previewQueued = false;
                        activePreviewRange = null;
                        setPreviewing(false);
                        if (runLatest) {
                            SwingUtilities.invokeLater(ReportsTab.this::previewRange);
                        }
                    }
                }
            }.execute();
        } catch (RuntimeException exception) {
            FarmUi.updateStatusBadge(reportStatus, "Preview needs attention", FarmUi.Tone.DANGER);
            showError(FarmUi.actionableError("Previewing the report range", exception));
        }
    }

    private void applyPreview(ReportService.ReportPreview preview) {
        lastPreviewRange = new DateRange(preview.from(), preview.to());
        updatePreviewValue(harvestPreviewLabel,
                preview.harvestEntries() + " log" + (preview.harvestEntries() == 1 ? "" : "s"));
        updatePreviewValue(orderPreviewLabel,
                preview.orders() + " order" + (preview.orders() == 1 ? "" : "s"));
        orderPreviewDetail.setText(preview.confirmedOrders() + " confirmed · "
                + preview.fulfilledOrders() + " fulfilled");
        orderPreviewDetail.getAccessibleContext().setAccessibleName(orderPreviewDetail.getText());
        updatePreviewValue(committedPreviewLabel,
                String.format(java.util.Locale.US, "$%,.2f", preview.committedRevenue()));
        updatePreviewValue(fulfilledPreviewLabel,
                String.format(java.util.Locale.US, "$%,.2f", preview.fulfilledRevenue()));
        populatePerformanceTables(preview);
        boolean noActivity = preview.harvestEntries() == 0 && preview.orders() == 0
                && preview.products().isEmpty() && preview.customers().isEmpty();
        FarmUi.updateStatusBadge(reportStatus,
                noActivity ? "Range preview ready · no activity found" : "Range preview ready",
                noActivity ? FarmUi.Tone.NEUTRAL : FarmUi.Tone.SUCCESS);
        statusArea.setText(noActivity
                ? "No harvest or order activity was recorded from " + preview.from()
                        + " through " + preview.to() + ".\n\n"
                        + (flowerProductionWorkspace()
                        ? "Try another range, log the first harvest, or confirm a customer order. "
                        : "Try another range or add a customer order. ")
                        + "A zero-activity PDF can still be created for the farm record."
                : "Preview ready for " + preview.from() + " through " + preview.to() + ".\n\n"
                        + "Committed revenue includes confirmed and fulfilled orders. "
                        + "Fulfilled revenue counts completed orders only. Compare "
                        + preview.products().size() + " product line"
                        + (preview.products().size() == 1 ? "" : "s") + " and "
                        + preview.customers().size() + " customer"
                        + (preview.customers().size() == 1 ? "" : "s")
                        + " in the tabs, then create the PDF when the totals look right.");
        if (host != null) {
            host.setStatus("Report preview ready for " + preview.from() + " through " + preview.to() + ".");
        }
    }

    private JLabel previewValue(String name, String value) {
        JLabel label = new JLabel(value);
        label.setName(name);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        label.getAccessibleContext().setAccessibleName(value);
        return label;
    }

    private JLabel previewDetail(String value) {
        JLabel label = FarmUi.mutedLabel(value);
        label.getAccessibleContext().setAccessibleName(value);
        return label;
    }

    private void updatePreviewValue(JLabel label, String value) {
        label.setText(value);
        label.getAccessibleContext().setAccessibleName(value);
    }

    private void populatePerformanceTables(ReportService.ReportPreview preview) {
        productPerformanceModel.setRowCount(0);
        for (ReportService.ProductPerformance product : preview.products()) {
            productPerformanceModel.addRow(new Object[]{
                    product.productName(),
                    String.format(java.util.Locale.US, "%,.1f", product.quantity()),
                    product.unit(),
                    String.valueOf(product.orderCount()),
                    String.format(java.util.Locale.US, "$%,.2f", product.averageUnitPrice()),
                    String.format(java.util.Locale.US, "$%,.2f", product.committedRevenue()),
                    String.format(java.util.Locale.US, "$%,.2f", product.fulfilledRevenue())
            });
        }
        customerPerformanceModel.setRowCount(0);
        for (ReportService.CustomerPerformance customer : preview.customers()) {
            customerPerformanceModel.addRow(new Object[]{
                    customer.customerName(),
                    String.valueOf(customer.orderCount()),
                    String.format(java.util.Locale.US, "$%,.2f",
                            customer.averageOrderValue()),
                    String.format(java.util.Locale.US, "$%,.2f",
                            customer.committedRevenue()),
                    String.format(java.util.Locale.US, "$%,.2f",
                            customer.fulfilledRevenue())
            });
        }
        productPerformanceLayout.show(productPerformanceState,
                preview.products().isEmpty() ? "empty" : "table");
        customerPerformanceLayout.show(customerPerformanceState,
                preview.customers().isEmpty() ? "empty" : "table");
    }

    private DefaultTableModel readOnlyModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private boolean matchesSelectedRange(DateRange range) {
        try {
            return range.equals(validatedRange(false));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void generateWeekly() {
        LocalDate today = reportToday();
        startGeneration(
                "Creating 7-day report…",
                flowerProductionWorkspace()
                        ? "Preparing the last 7 days of harvest and sales activity…"
                        : "Preparing the last 7 days of recorded activity and sales…",
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
        var selected = files.chooseSave(panel, "Save farm report PDF", new File(defaultName));
        if (selected.isEmpty()) {
            FarmUi.updateStatusBadge(reportStatus, "Report ready — save cancelled", FarmUi.Tone.WARNING);
            statusArea.setText("The report was created, but no file was saved.\n"
                    + "Choose Create report again when you are ready to select a location.");
            if (host != null) {
                host.setStatus("Report save cancelled. No file was changed.");
            }
            return;
        }
        File file = ensurePdfExtension(selected.get());
        Path path = file.toPath();
        if (Files.exists(path)) {
            int overwrite = dialogs.showConfirm(
                    panel,
                    "A file named " + file.getName() + " already exists. Replace it?",
                    "Replace existing report?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                FarmUi.updateStatusBadge(
                        reportStatus, "Report not saved · existing file kept", FarmUi.Tone.WARNING);
                statusArea.setText("The report was created, but the existing file was kept unchanged.");
                if (host != null) {
                    host.setStatus("Report not saved. Existing file kept: " + file.getName());
                }
                return;
            }
        }
        writePdfAtomically(path, pdf);
        FarmUi.updateStatusBadge(reportStatus, "Report saved", FarmUi.Tone.SUCCESS);
        statusArea.setText("Report saved successfully to:\n" + file.getAbsolutePath());
        if (host != null) {
            host.setStatus("PDF report saved: " + file.getName());
        }
        if (panel.isShowing()) {
            dialogs.showMessage(panel,
                    "Report saved to:\n" + file.getAbsolutePath(),
                    "Report ready", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void writePdfAtomically(Path destination, byte[] pdf) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("The selected report folder is not available.");
        }
        Path temporary = Files.createTempFile(parent, ".farm-report-", ".tmp");
        try {
            Files.write(temporary, pdf);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
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
                    + (flowerProductionWorkspace()
                    ? " · harvest, bed production, orders, and revenue"
                    : " · recorded harvests, orders, and revenue");
            rangePreview.setText(text);
            rangePreview.getAccessibleContext().setAccessibleName(text);
            rangeButton.setEnabled(!generating && !previewing);
            previewButton.setEnabled(!generating && !previewing);
        } catch (RuntimeException ex) {
            String guidance = ex.getMessage() == null
                    ? "Enter a valid start and end date."
                    : ex.getMessage();
            rangePreview.setText(guidance);
            rangePreview.getAccessibleContext().setAccessibleName(guidance);
            rangeButton.setEnabled(false);
            previewButton.setEnabled(false);
        }
    }

    private void reportRangeChanged() {
        updateRangePreview();
        queueLatestPreviewIfNeeded();
    }

    private void queueLatestPreviewIfNeeded() {
        if (!previewing || activePreviewRange == null) {
            return;
        }
        previewQueued = !matchesSelectedRange(activePreviewRange);
        if (previewQueued) {
            FarmUi.updateStatusBadge(
                    reportStatus, "Range changed · latest preview queued", FarmUi.Tone.INFO);
            statusArea.setText("The active preview will finish safely, then the latest valid "
                    + "date range will load automatically.");
        }
    }

    private void showPreviewFailure(Throwable failure) {
        String detail = FarmUi.actionableError("Previewing the report range", failure);
        if (lastPreviewRange == null) {
            FarmUi.updateStatusBadge(
                    reportStatus, "Preview needs attention", FarmUi.Tone.DANGER);
            showError(detail);
            return;
        }
        FarmUi.updateStatusBadge(
                reportStatus, "Preview failed · previous range shown", FarmUi.Tone.DANGER);
        showError(detail + "\n\nThe visible metrics and tables remain from "
                + lastPreviewRange.from() + " through " + lastPreviewRange.to()
                + ". Retry Preview range before treating them as current.");
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
                        field, label + " must use YYYY-MM-DD, such as " + reportToday() + ".");
            }
            throw new IllegalArgumentException(label + " must use YYYY-MM-DD.");
        }
    }

    private void setGenerating(boolean value) {
        generating = value;
        weeklyButton.setEnabled(!value && !previewing);
        weeklyButton.setText(value ? "Creating report…" : "Create 7-day report…");
        if (value) {
            rangeButton.setEnabled(false);
            previewButton.setEnabled(false);
        } else {
            updateRangePreview();
        }
    }

    private void setPreviewing(boolean value) {
        previewing = value;
        weeklyButton.setEnabled(!value && !generating);
        previewButton.setText(value ? "Previewing…" : "Preview range");
        if (value) {
            previewButton.setEnabled(false);
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
