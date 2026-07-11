package com.flowerfarm.gui.tabs;

import com.flowerfarm.service.ReportService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;

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

    public ReportsTab(ReportService reportService, TabHost host) {
        this.reportService = reportService;
        this.host = host;
    }

    @Override public String getTabTitle() { return "Reports"; }

    @Override
    public String getDescription() {
        return "PDF weekly harvest + sales reports";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUI();
        }
        return panel;
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel header = new JLabel("Farm Reports — Harvest & Wholesale Sales PDF");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JButton weekly = new JButton("Generate trailing 7-day PDF…");
        weekly.setAlignmentX(Component.LEFT_ALIGNMENT);
        weekly.addActionListener(e -> generateWeekly());
        center.add(weekly);
        center.add(Box.createVerticalStrut(12));

        JPanel range = new JPanel(new FlowLayout(FlowLayout.LEFT));
        range.setAlignmentX(Component.LEFT_ALIGNMENT);
        fromField = new JTextField(LocalDate.now().minusDays(6).toString(), 12);
        toField = new JTextField(LocalDate.now().toString(), 12);
        range.add(new JLabel("From:"));
        range.add(fromField);
        range.add(new JLabel("To:"));
        range.add(toField);
        JButton custom = new JButton("Generate range PDF…");
        custom.addActionListener(e -> generateRange());
        range.add(custom);
        center.add(range);

        center.add(Box.createVerticalStrut(16));
        statusArea = new JTextArea(8, 50);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setText(
                "Reports combine:\n"
                        + "  • Harvest log entries in the period\n"
                        + "  • Season-to-date harvest totals\n"
                        + "  • Customer orders + line revenue (CONFIRMED / FULFILLED)\n\n"
                        + "Also available via REST:\n"
                        + "  GET /api/reports/weekly.pdf\n"
                        + "  GET /api/reports/range.pdf?from=YYYY-MM-DD&to=YYYY-MM-DD"
        );
        JScrollPane scroll = new JScrollPane(statusArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(scroll);

        panel.add(center, BorderLayout.CENTER);
    }

    private void generateWeekly() {
        try {
            if (host != null) {
                host.setStatus("⏳ Generating weekly PDF report…");
            }
            byte[] pdf = reportService.generateWeeklyReportPdf();
            savePdf(pdf, "flowerfarm-weekly-" + LocalDate.now() + ".pdf");
        } catch (Exception ex) {
            showError(ex.getMessage());
            if (host != null) {
                host.setStatus("PDF generation failed: " + ex.getMessage());
            }
        }
    }

    private void generateRange() {
        try {
            if (host != null) {
                host.setStatus("⏳ Generating range PDF report…");
            }
            LocalDate from = LocalDate.parse(fromField.getText().trim());
            LocalDate to = LocalDate.parse(toField.getText().trim());
            byte[] pdf = reportService.generateReportPdf(from, to);
            savePdf(pdf, "flowerfarm-" + from + "_to_" + to + ".pdf");
        } catch (Exception ex) {
            showError(ex.getMessage());
            if (host != null) {
                host.setStatus("PDF generation failed: " + ex.getMessage());
            }
        }
    }

    private void savePdf(byte[] pdf, String defaultName) throws Exception {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(defaultName));
        chooser.setDialogTitle("Save farm report PDF");
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        Files.write(file.toPath(), pdf);
        statusArea.setText("Saved report (" + pdf.length + " bytes) to:\n" + file.getAbsolutePath());
        if (host != null) {
            host.setStatus("PDF report saved: " + file.getName());
        }
        JOptionPane.showMessageDialog(panel,
                "Report saved to:\n" + file.getAbsolutePath(),
                "Report ready", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(panel, msg, "Reports", JOptionPane.ERROR_MESSAGE);
    }
}
