package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MarketDayPackingService.FulfillBatchResult;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;

/**
 * Market morning packing list — pick sheet + per-customer slips from CRM pipeline.
 */
public class MarketDayTab implements FlowerFarmTab {

    private final MarketDayPackingService packingService;
    private final TabHost host;

    private JPanel panel;
    private JTextField dateField;
    private JSpinner windowSpinner;
    private JCheckBox draftBox;
    private JCheckBox fulfilledBox;
    private JTextArea planArea;
    private JLabel summaryLabel;
    private JButton fulfillBtn;

    public MarketDayTab(MarketDayPackingService packingService, TabHost host) {
        this.packingService = packingService;
        this.host = host;
    }

    @Override
    public String getTabTitle() {
        return "Market Day";
    }

    @Override
    public String getDescription() {
        return "Packing list for market / wholesale load-out";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    @Override
    public void initialize() {
        refreshPlan();
    }

    @Override
    public void refreshData() {
        refreshPlan();
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        GuiPermissions.setWritable(canWrite, fulfillBtn);
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel header = new JLabel("Market Day Packing — Port Orchard / Kitsap load-out");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        dateField = new JTextField(LocalDate.now().toString(), 12);
        dateField.setToolTipText("Market / delivery date (YYYY-MM-DD)");
        windowSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 30, 1));
        windowSpinner.setToolTipText("1 = that day only; 7 = trailing week ending on market date");
        draftBox = new JCheckBox("Include DRAFT");
        fulfilledBox = new JCheckBox("Include FULFILLED");
        JButton build = new JButton("Build packing list");
        build.setFont(build.getFont().deriveFont(Font.BOLD));
        build.addActionListener(e -> refreshPlan());
        JButton today = new JButton("Today");
        today.addActionListener(e -> {
            dateField.setText(LocalDate.now().toString());
            refreshPlan();
        });
        JButton exportTxt = new JButton("Export TXT…");
        exportTxt.addActionListener(e -> exportText());
        JButton exportCsv = new JButton("Export CSV…");
        exportCsv.addActionListener(e -> exportCsv());
        JButton exportPdf = new JButton("Export PDF…");
        exportPdf.setToolTipText("Printable packing sheet (pick list + customer slips).");
        exportPdf.addActionListener(e -> exportPdf());
        fulfillBtn = new JButton("Fulfill all CONFIRMED…");
        fulfillBtn.setToolTipText(
                "After market: mark every CONFIRMED order FULFILLED and deduct inventory.");
        fulfillBtn.addActionListener(e -> fulfillAllConfirmed());
        JButton openCrm = new JButton("Open CRM");
        openCrm.addActionListener(e -> {
            if (host != null) {
                host.selectTab("CRM");
                host.setStatus("Confirm orders for market day, then rebuild packing list.");
            }
        });

        controls.add(new JLabel("Date:"));
        controls.add(dateField);
        controls.add(new JLabel("Window days:"));
        controls.add(windowSpinner);
        controls.add(draftBox);
        controls.add(fulfilledBox);
        controls.add(build);
        controls.add(today);
        controls.add(exportTxt);
        controls.add(exportCsv);
        controls.add(exportPdf);
        controls.add(fulfillBtn);
        controls.add(openCrm);

        summaryLabel = new JLabel(" ");
        summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.BOLD));

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(header, BorderLayout.NORTH);
        north.add(controls, BorderLayout.CENTER);
        north.add(summaryLabel, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);

        planArea = new JTextArea(22, 72);
        planArea.setEditable(false);
        planArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        planArea.setLineWrap(false);
        panel.add(new JScrollPane(planArea), BorderLayout.CENTER);
    }

    private MarketDayPlan currentPlan() {
        LocalDate date = LocalDate.parse(dateField.getText().trim());
        int window = ((Number) windowSpinner.getValue()).intValue();
        return packingService.buildPlan(date, window, draftBox.isSelected(), fulfilledBox.isSelected());
    }

    private void refreshPlan() {
        try {
            if (host != null) {
                host.setStatus("⏳ Building market-day packing list…");
            }
            MarketDayPlan plan = currentPlan();
            planArea.setText(plan.plainText());
            planArea.setCaretPosition(0);
            String shortMsg = plan.shortfallSkuCount() > 0
                    ? " · ⚠ " + plan.shortfallSkuCount() + " shortfall SKU(s)"
                    : " · stock OK";
            summaryLabel.setText(String.format(
                    "Orders: %d  ·  Pipeline $%,.2f  ·  Scope %s%s",
                    plan.orderCount(), plan.pipelineValue(), plan.scope(), shortMsg));
            if (host != null) {
                host.setStatus("Market day " + plan.marketDate() + ": "
                        + plan.orderCount() + " order(s), $"
                        + String.format("%,.2f", plan.pipelineValue()) + " pipeline.");
            }
        } catch (Exception ex) {
            planArea.setText("Could not build plan: " + ex.getMessage());
            if (host != null) {
                host.setStatus("Market day plan failed: " + ex.getMessage());
            }
        }
    }

    private void exportText() {
        try {
            MarketDayPlan plan = currentPlan();
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("market-day-" + plan.marketDate() + ".txt"));
            if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Files.writeString(chooser.getSelectedFile().toPath(), plan.plainText(), StandardCharsets.UTF_8);
            if (host != null) {
                host.setStatus("Wrote " + chooser.getSelectedFile().getName());
            }
            JOptionPane.showMessageDialog(panel, "Saved packing list.", "Export",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(), "Export failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportCsv() {
        try {
            MarketDayPlan plan = currentPlan();
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("market-day-" + plan.marketDate() + ".csv"));
            if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Files.writeString(chooser.getSelectedFile().toPath(),
                    packingService.exportCsv(plan), StandardCharsets.UTF_8);
            if (host != null) {
                host.setStatus("Wrote " + chooser.getSelectedFile().getName());
            }
            JOptionPane.showMessageDialog(panel, "Saved CSV packing export.", "Export",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(), "Export failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportPdf() {
        try {
            if (host != null) {
                host.setStatus("⏳ Building market-day packing PDF…");
            }
            MarketDayPlan plan = currentPlan();
            byte[] pdf = packingService.generatePackingPdf(plan);
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new File("market-day-packing-" + plan.marketDate() + ".pdf"));
            if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Files.write(chooser.getSelectedFile().toPath(), pdf);
            if (host != null) {
                host.setStatus("Wrote packing PDF → " + chooser.getSelectedFile().getName()
                        + " (" + pdf.length + " bytes)");
            }
            JOptionPane.showMessageDialog(panel,
                    "Saved packing PDF:\n" + chooser.getSelectedFile().getAbsolutePath(),
                    "Export", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(), "PDF export failed",
                    JOptionPane.ERROR_MESSAGE);
            if (host != null) {
                host.setStatus("Packing PDF failed: " + ex.getMessage());
            }
        }
    }

    private void fulfillAllConfirmed() {
        if (!GuiPermissions.requireWrite(host, panel, "fulfill market-day orders")) {
            return;
        }
        try {
            // Always CONFIRMED-only for safety
            LocalDate date = LocalDate.parse(dateField.getText().trim());
            int window = ((Number) windowSpinner.getValue()).intValue();
            MarketDayPlan plan = packingService.buildPlan(date, window, false, false);
            long confirmed = plan.customers().stream()
                    .filter(c -> "CONFIRMED".equalsIgnoreCase(c.status()))
                    .count();
            if (confirmed == 0) {
                JOptionPane.showMessageDialog(panel,
                        "No CONFIRMED orders in this window to fulfill.",
                        "Fulfill all", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String warn = plan.shortfallSkuCount() > 0
                    ? "\n\n⚠ " + plan.shortfallSkuCount()
                    + " SKU shortfall(s) — inventory may not fully match."
                    : "";
            int ok = JOptionPane.showConfirmDialog(panel,
                    "Fulfill " + confirmed + " CONFIRMED order(s) for "
                            + plan.from() + " → " + plan.to() + "?\n"
                            + "Pipeline ≈ $" + String.format("%,.2f", plan.pipelineValue()) + "\n"
                            + "This deducts matching inventory and cannot be undone from here."
                            + warn,
                    "Fulfill all CONFIRMED",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) {
                return;
            }
            if (host != null) {
                host.setStatus("⏳ Fulfilling " + confirmed + " market-day order(s)…");
            }
            FulfillBatchResult result = packingService.fulfillConfirmedOrders(plan);
            StringBuilder msg = new StringBuilder();
            msg.append("Fulfilled: ").append(result.fulfilled())
                    .append("  ·  Skipped: ").append(result.skipped())
                    .append("  ·  Failed: ").append(result.failed()).append("\n\n");
            for (String line : result.messages()) {
                msg.append("• ").append(line).append('\n');
            }
            JTextArea area = new JTextArea(msg.toString(), 14, 52);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JOptionPane.showMessageDialog(panel, new JScrollPane(area),
                    "Batch fulfill complete", JOptionPane.INFORMATION_MESSAGE);
            refreshPlan();
            if (host != null) {
                host.refreshAll();
                host.setStatus("Market-day fulfill: " + result.fulfilled()
                        + " done, " + result.failed() + " failed.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(),
                    "Batch fulfill failed", JOptionPane.ERROR_MESSAGE);
            if (host != null) {
                host.setStatus("Batch fulfill failed: " + ex.getMessage());
            }
        }
    }
}
