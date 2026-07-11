package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Modern Inventory management tab with integrated live search, color-coded
 * stock levels, sortable columns, edit/delete actions, and CSV export of the
 * currently visible (filtered &amp; sorted) rows.
 *
 * <p>Mutations are delegated to the {@link InventoryService}; after a change the
 * tab asks the {@link TabHost} to refresh every tab so the Dashboard KPIs stay
 * consistent with the table.
 */
public class InventoryTab implements FlowerFarmTab {

    private static final String[] COLUMNS =
            {"Name", "Category", "Price", "Unit", "Cost", "Qty", "Notes"};
    private static final int QTY_COLUMN = 5;
    private static final int NOTES_COLUMN = 6;

    private final InventoryService inventoryService;
    private final TabHost host;

    private JPanel panel;
    private DefaultTableModel tableModel;
    private JTable inventoryTable;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField searchField;
    private JLabel statusLabel;

    public InventoryTab(InventoryService inventoryService, TabHost host) {
        this.inventoryService = inventoryService;
        this.host = host;
    }

    @Override
    public String getTabTitle() {
        return "Inventory";
    }

    @Override
    public String getDescription() {
        return "Search, sort, edit, and export inventory";
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
        tableModel.setRowCount(0);
        for (Item item : inventoryService.getAllItems()) {
            tableModel.addRow(new Object[]{
                    item.getName(),
                    item.getCategory(),
                    String.format("%.2f", item.getPrice()),
                    item.getUnit(),
                    String.format("%.2f", item.getCost()),
                    item.getQuantity(),
                    item.getNotes()
            });
        }
        updateStatus();
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── Top toolbar: live search ─────────────────────────────────────────
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        searchField = new JTextField(25);
        searchField.setToolTipText("Filter inventory by name, category, or notes…");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
        });

        JButton clearSearchBtn = new JButton("Clear");
        clearSearchBtn.addActionListener(e -> {
            searchField.setText("");
            filterTable();
        });

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshData());

        topPanel.add(new JLabel("Search / Filter:"));
        topPanel.add(searchField);
        topPanel.add(clearSearchBtn);
        topPanel.add(refreshBtn);

        // ── Table ────────────────────────────────────────────────────────────
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        inventoryTable = new JTable(tableModel);
        inventoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        inventoryTable.setRowHeight(22);
        inventoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        sorter = new TableRowSorter<>(tableModel);
        inventoryTable.setRowSorter(sorter);

        // Color coding for low stock on the Qty column.
        inventoryTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    Color bg = table.getBackground();
                    if (column == QTY_COLUMN && value != null) {
                        try {
                            int qty = Integer.parseInt(value.toString().trim());
                            if (qty <= 5) {
                                bg = new Color(255, 200, 200);      // light red
                            } else if (qty <= 15) {
                                bg = new Color(255, 255, 200);      // light yellow
                            }
                        } catch (NumberFormatException ignored) {
                            // leave default background
                        }
                    }
                    c.setBackground(bg);
                }
                return c;
            }
        });

        JScrollPane tableScroll = new JScrollPane(inventoryTable);
        tableScroll.setPreferredSize(new Dimension(900, 450));

        // ── Bottom action bar ────────────────────────────────────────────────
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton editBtn = new JButton("Edit Selected");
        JButton deleteBtn = new JButton("Delete Selected");
        JButton exportBtn = new JButton("Export Visible to CSV");

        editBtn.addActionListener(this::editSelected);
        deleteBtn.addActionListener(this::deleteSelected);
        exportBtn.addActionListener(e -> exportVisibleToCsv());

        actionPanel.add(editBtn);
        actionPanel.add(deleteBtn);
        actionPanel.add(exportBtn);

        statusLabel = new JLabel("Loading inventory…");

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(actionPanel, BorderLayout.WEST);
        bottomPanel.add(statusLabel, BorderLayout.EAST);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void filterTable() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
        }
        updateStatus();
    }

    private void updateStatus() {
        if (statusLabel != null && inventoryTable != null) {
            int visible = inventoryTable.getRowCount();
            int total = tableModel.getRowCount();
            statusLabel.setText(visible == total
                    ? total + " items"
                    : visible + " of " + total + " items shown");
        }
    }

    private void editSelected(java.awt.event.ActionEvent e) {
        if (!GuiPermissions.requireWrite(host, panel, "edit inventory")) {
            return;
        }
        int viewRow = inventoryTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(panel, "Please select a row to edit.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = inventoryTable.convertRowIndexToModel(viewRow);

        try {
            String name     = ask("Edit Name:",     tableModel.getValueAt(modelRow, 0));
            String category = ask("Edit Category:", tableModel.getValueAt(modelRow, 1));
            double price    = Double.parseDouble(ask("Edit Price:", tableModel.getValueAt(modelRow, 2)));
            String unit     = ask("Edit Unit:",     tableModel.getValueAt(modelRow, 3));
            double cost     = Double.parseDouble(ask("Edit Cost:",  tableModel.getValueAt(modelRow, 4)));
            int qty         = Integer.parseInt(ask("Edit Quantity:", tableModel.getValueAt(modelRow, 5)));
            String notes    = ask("Edit Notes:",    tableModel.getValueAt(modelRow, 6));

            inventoryService.editItem(modelRow, new Item(name, category, price, unit, cost, qty, notes));
            afterMutation("Edited: " + name);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(panel, "Price, Cost and Quantity must be valid numbers.",
                    "Edit Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(panel, "Invalid input: " + ex.getMessage(),
                    "Edit Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected(java.awt.event.ActionEvent e) {
        if (!GuiPermissions.requireWrite(host, panel, "delete inventory items")) {
            return;
        }
        int viewRow = inventoryTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(panel, "Please select a row to delete.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int modelRow = inventoryTable.convertRowIndexToModel(viewRow);
        String name = String.valueOf(tableModel.getValueAt(modelRow, 0));

        int confirm = JOptionPane.showConfirmDialog(panel,
                "Delete '" + name + "'?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            inventoryService.deleteItem(modelRow);
            afterMutation("Deleted: " + name);
        }
    }

    /** Refresh this tab (and siblings via the host) and report a status message. */
    private void afterMutation(String message) {
        if (host != null) {
            host.refreshAll();
            host.setStatus(message);
        } else {
            refreshData();
        }
    }

    private String ask(String prompt, Object current) {
        return JOptionPane.showInputDialog(panel, prompt, current);
    }

    /**
     * Exports the currently visible rows (respecting the active filter and sort
     * order) to a CSV file chosen by the user.
     */
    private void exportVisibleToCsv() {
        int rows = inventoryTable.getRowCount();
        if (rows == 0) {
            JOptionPane.showMessageDialog(panel, "There are no visible rows to export.",
                    "Nothing to Export", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Visible Rows to CSV");
        chooser.setSelectedFile(new File("inventory_visible.csv"));
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            bw.write(String.join(",", COLUMNS));
            bw.newLine();
            for (int viewRow = 0; viewRow < rows; viewRow++) {
                StringBuilder line = new StringBuilder();
                for (int col = 0; col < COLUMNS.length; col++) {
                    Object value = inventoryTable.getValueAt(viewRow, col);
                    String cell = value == null ? "" : value.toString();
                    if (col == NOTES_COLUMN) {
                        cell = "\"" + cell.replace("\"", "\"\"") + "\"";
                    }
                    if (col > 0) {
                        line.append(',');
                    }
                    line.append(cell);
                }
                bw.write(line.toString());
                bw.newLine();
            }
            String msg = rows + " row(s) exported to " + file.getName();
            if (host != null) {
                host.setStatus(msg);
            }
            JOptionPane.showMessageDialog(panel, rows + " row(s) exported to\n" + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(panel, "Export failed: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
