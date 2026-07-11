package com.flowerfarm.gui.tabs;

import com.flowerfarm.auth.FarmSession;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.service.SyncHistoryService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Audit log — connector / CRM / harvest ops with filter, failures-only, CSV export.
 */
public class SyncHistoryTab implements FlowerFarmTab {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final int VIEW_LIMIT = 300;

    private final SyncHistoryService syncHistoryService;
    private final TabHost host;

    private JPanel panel;
    private DefaultTableModel tableModel;
    private JTable table;
    private JComboBox<String> connectorFilter;
    private JComboBox<String> operationFilter;
    private JComboBox<String> resultFilter;
    private JTextField messageSearch;
    private JLabel statsLabel;
    private JButton clearHistBtn;
    private List<SyncHistoryEntry> lastView = List.of();

    public SyncHistoryTab(SyncHistoryService syncHistoryService, TabHost host) {
        this.syncHistoryService = syncHistoryService;
        this.host = host;
    }

    @Override public String getTabTitle() { return "Sync History"; }

    @Override
    public String getDescription() {
        return "Audit log of connectors, harvest, and CRM operations";
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        // Clear history is OWNER-only when auth is on; HAND can write elsewhere but not clear
        boolean canClear = host != null ? host.canClearHistory() : FarmSession.canClearHistory();
        GuiPermissions.setWritable(canClear, GuiPermissions.OWNER_CLEAR_TIP, clearHistBtn);
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
        refreshData();
    }

    @Override
    public void refreshData() {
        if (tableModel == null) {
            return;
        }
        refreshFilterOptions();
        applyFilter();
    }

    private void applyFilter() {
        if (tableModel == null) {
            return;
        }
        String connector = comboValue(connectorFilter);
        String operation = comboValue(operationFilter);
        Boolean successOnly = parseResultFilter();
        String query = messageSearch == null ? "" : messageSearch.getText().trim();

        List<SyncHistoryEntry> entries = syncHistoryService.filter(
                connector, operation, successOnly, query, VIEW_LIMIT);
        lastView = entries;

        tableModel.setRowCount(0);
        for (SyncHistoryEntry e : entries) {
            tableModel.addRow(new Object[]{
                    e.getId(),
                    e.getOccurredAt() == null ? "" : FMT.format(e.getOccurredAt()),
                    e.getConnectorName(),
                    e.getOperation(),
                    e.isSuccess() ? "OK" : "FAIL",
                    e.getRecordCount() == null ? "" : e.getRecordCount(),
                    e.getMessage(),
                    e.getDetail() == null ? "" : e.getDetail()
            });
        }

        SyncHistoryService.HistoryStats stats = syncHistoryService.stats(entries);
        if (statsLabel != null) {
            statsLabel.setText(String.format(
                    "Showing %d · OK %d · FAIL %d", stats.total(), stats.ok(), stats.fail()));
        }
    }

    private void refreshFilterOptions() {
        if (connectorFilter == null) {
            return;
        }
        Object prevConn = connectorFilter.getSelectedItem();
        Object prevOp = operationFilter.getSelectedItem();
        connectorFilter.removeAllItems();
        connectorFilter.addItem("ALL");
        for (String c : syncHistoryService.distinctConnectors(VIEW_LIMIT)) {
            connectorFilter.addItem(c);
        }
        if (prevConn != null) {
            connectorFilter.setSelectedItem(prevConn);
        }
        operationFilter.removeAllItems();
        operationFilter.addItem("ALL");
        for (String op : syncHistoryService.distinctOperations(VIEW_LIMIT)) {
            operationFilter.addItem(op);
        }
        if (prevOp != null) {
            operationFilter.setSelectedItem(prevOp);
        }
    }

    private static String comboValue(JComboBox<String> box) {
        if (box == null || box.getSelectedItem() == null) {
            return "";
        }
        return String.valueOf(box.getSelectedItem());
    }

    private Boolean parseResultFilter() {
        if (resultFilter == null) {
            return null;
        }
        String v = String.valueOf(resultFilter.getSelectedItem());
        if ("OK only".equals(v)) {
            return true;
        }
        if ("FAIL only".equals(v)) {
            return false;
        }
        return null;
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel north = new JPanel(new BorderLayout(8, 4));
        JLabel header = new JLabel("Audit / Sync History — connectors · harvest · CRM");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        north.add(header, BorderLayout.NORTH);
        north.add(buildFilterBar(), BorderLayout.CENTER);
        panel.add(north, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
                new String[]{"Id", "When", "Connector", "Op", "Status", "Count", "Message", "Detail"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setMaxWidth(60);
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusRenderer());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JLabel tip = new JLabel("Tip: connector bar, harvest log, and CRM fulfill/confirm write here automatically.");
        tip.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        south.add(tip, BorderLayout.WEST);
        statsLabel = new JLabel(" ");
        south.add(statsLabel, BorderLayout.EAST);
        panel.add(south, BorderLayout.SOUTH);
    }

    private JPanel buildFilterBar() {
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        filterBar.setBorder(BorderFactory.createTitledBorder("Filter / search"));

        connectorFilter = new JComboBox<>(new String[]{"ALL"});
        operationFilter = new JComboBox<>(new String[]{"ALL"});
        resultFilter = new JComboBox<>(new String[]{"Any result", "OK only", "FAIL only"});
        messageSearch = new JTextField(14);
        messageSearch.setToolTipText("Search message or detail text");
        messageSearch.addActionListener(e -> applyFilter());

        java.awt.event.ActionListener apply = e -> applyFilter();
        connectorFilter.addActionListener(apply);
        operationFilter.addActionListener(apply);
        resultFilter.addActionListener(apply);

        filterBar.add(new JLabel("Source:"));
        filterBar.add(connectorFilter);
        filterBar.add(new JLabel("Op:"));
        filterBar.add(operationFilter);
        filterBar.add(new JLabel("Result:"));
        filterBar.add(resultFilter);
        filterBar.add(new JLabel("Search:"));
        filterBar.add(messageSearch);

        JButton applyBtn = new JButton("Apply");
        applyBtn.addActionListener(apply);
        JButton failures = new JButton("Failures only");
        failures.addActionListener(e -> {
            resultFilter.setSelectedItem("FAIL only");
            applyFilter();
        });
        JButton clear = new JButton("Clear filters");
        clear.addActionListener(e -> {
            connectorFilter.setSelectedItem("ALL");
            operationFilter.setSelectedItem("ALL");
            resultFilter.setSelectedItem("Any result");
            messageSearch.setText("");
            applyFilter();
        });
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refreshData());
        JButton exportBtn = new JButton("Export CSV…");
        exportBtn.addActionListener(e -> exportCsv());
        JButton exportPdfBtn = new JButton("Export PDF…");
        exportPdfBtn.setToolTipText("Printable audit sheet for the current filter (VIEWER OK).");
        exportPdfBtn.addActionListener(e -> exportPdf());
        clearHistBtn = new JButton("Clear history…");
        clearHistBtn.addActionListener(e -> clearHistory());

        filterBar.add(applyBtn);
        filterBar.add(failures);
        filterBar.add(clear);
        filterBar.add(refresh);
        filterBar.add(exportBtn);
        filterBar.add(exportPdfBtn);
        filterBar.add(clearHistBtn);
        return filterBar;
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("sync_history.csv"));
        chooser.setDialogTitle("Export audit history (current filter)");
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            syncHistoryService.exportToCsv(file.getAbsolutePath(), lastView);
            if (host != null) {
                host.setStatus("Audit history exported → " + file.getName()
                        + " (" + lastView.size() + " row(s))");
            }
            JOptionPane.showMessageDialog(panel,
                    "Exported " + lastView.size() + " row(s) to:\n" + file.getAbsolutePath(),
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(), "Export failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void exportPdf() {
        try {
            String scope = "GUI filter";
            Object conn = connectorFilter != null ? connectorFilter.getSelectedItem() : null;
            Object op = operationFilter != null ? operationFilter.getSelectedItem() : null;
            Object res = resultFilter != null ? resultFilter.getSelectedItem() : null;
            if (conn != null && !"ALL".equals(String.valueOf(conn))) {
                scope += " · " + conn;
            }
            if (op != null && !"ALL".equals(String.valueOf(op))) {
                scope += " · " + op;
            }
            if (res != null && !"Any result".equals(String.valueOf(res))) {
                scope += " · " + res;
            }
            var report = syncHistoryService.buildAuditReport(lastView, scope);
            JTextArea area = new JTextArea(report.plainText(), 20, 70);
            area.setEditable(false);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            area.setCaretPosition(0);
            JScrollPane scroll = new JScrollPane(area);
            scroll.setPreferredSize(new Dimension(700, 400));

            Object[] options = {"Save PDF…", "Close"};
            int choice = JOptionPane.showOptionDialog(panel, scroll,
                    "Audit history — " + report.total() + " row(s)",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[1]);
            if (choice == 0) {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(new File(
                        "audit-history-" + report.generatedOn() + ".pdf"));
                if (chooser.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
                    byte[] pdf = syncHistoryService.generateAuditPdf(report);
                    java.nio.file.Files.write(chooser.getSelectedFile().toPath(), pdf);
                    if (host != null) {
                        host.setStatus("Audit PDF → " + chooser.getSelectedFile().getName()
                                + " (" + report.total() + " rows, " + report.fail() + " fail)");
                    }
                }
            } else if (host != null) {
                host.setStatus("Audit: " + report.ok() + " OK / " + report.fail() + " FAIL");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, ex.getMessage(), "Audit PDF failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearHistory() {
        if (!com.flowerfarm.gui.GuiPermissions.requireOwnerClear(host, panel)) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(panel,
                "Delete all sync history records?",
                "Clear history", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        syncHistoryService.clearAll();
        refreshData();
        if (host != null) {
            host.setStatus("Sync history cleared.");
        }
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                boolean ok = "OK".equals(String.valueOf(value));
                c.setForeground(ok ? new Color(0, 120, 60) : new Color(160, 30, 30));
            }
            return c;
        }
    }
}
