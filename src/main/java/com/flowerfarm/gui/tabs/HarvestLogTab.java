package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.HarvestService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Harvest log tab — add / edit / filter history, inventory-aware updates,
 * season totals, and CSV export for Kitsap market prep.
 */
public class HarvestLogTab implements FlowerFarmTab {

    private final HarvestService harvestService;
    private final TabHost host;

    private JPanel panel;
    private DefaultTableModel tableModel;
    private JTable table;

    private JTextField dateField;
    private JTextField cropField;
    private JComboBox<String> cropSuggest;
    private JTextField qtyField;
    private JTextField unitField;
    private JTextField bedField;
    private JTextField notesField;

    private JTextField filterCropField;
    private JTextField filterBedField;
    private JTextField filterNotesField;
    private JTextField filterFromField;
    private JTextField filterToField;
    private JLabel filterStatusLabel;
    private JCheckBox filterTotalsOnly;

    private JTextArea totalsArea;
    private JTextArea batchArea;
    private JComboBox<String> unitSuggest;
    private JButton batchBtn;
    private JButton fillSampleBtn;
    private JButton addBtn;
    private JButton loadBtn;
    private JButton saveBtn;
    private JButton clearFormBtn;
    private JButton deleteBtn;
    private JButton dupBtn;
    private Long editingId; // null = add mode
    private List<HarvestEntry> lastView = List.of();

    public HarvestLogTab(HarvestService harvestService, TabHost host) {
        this.harvestService = harvestService;
        this.host = host;
    }

    @Override public String getTabTitle() { return "Harvest Log"; }

    @Override
    public String getDescription() {
        return "Log, edit, filter, and export harvests — inventory stays in sync";
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
        applyFilter();
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        // Filter / export / refresh stay enabled for VIEWER
        GuiPermissions.setWritable(canWrite,
                dateField, cropField, cropSuggest, qtyField, unitField, unitSuggest,
                bedField, notesField, batchArea,
                batchBtn, fillSampleBtn, addBtn, loadBtn, saveBtn, clearFormBtn, deleteBtn, dupBtn);
    }

    private void buildUI() {
        panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel north = new JPanel(new BorderLayout(8, 4));
        JLabel header = new JLabel("Harvest Log — Port Orchard / Kitsap County");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        north.add(header, BorderLayout.NORTH);
        north.add(buildFilterBar(), BorderLayout.CENTER);
        panel.add(north, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
                new String[]{"Id", "Date", "Crop", "Qty", "Unit", "Bed / Field", "Notes"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(6).setPreferredWidth(180);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                // optional: auto-load on select — leave explicit Load button for control
            }
        });
        // Double-click loads into form for edit
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    loadSelectedIntoForm();
                }
            }
        });
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 8));
        JPanel forms = new JPanel(new GridLayout(1, 2, 8, 0));
        forms.add(buildForm());
        forms.add(buildBatchPanel());
        south.add(forms, BorderLayout.CENTER);
        totalsArea = new JTextArea(5, 22);
        totalsArea.setEditable(false);
        totalsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        totalsArea.setBorder(BorderFactory.createTitledBorder("Season totals by crop"));
        south.add(new JScrollPane(totalsArea), BorderLayout.EAST);
        panel.add(south, BorderLayout.SOUTH);
    }

    private JPanel buildBatchPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.setBorder(BorderFactory.createTitledBorder(
                "Batch log — Crop,Qty[,Unit][,Bed]  (# comments ok; defaults from left form)"));
        batchArea = new JTextArea(6, 28);
        batchArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        batchArea.setText("# morning cut — unit/bed default from left form\n"
                + "Nootka Rose,40\nDamask Rose,25,bunches\nDahlia mix,15,stems,Bed C\n");
        p.add(new JScrollPane(batchArea), BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        batchBtn = new JButton("Log batch harvest");
        batchBtn.setToolTipText("Creates one harvest entry per non-empty line and updates inventory.");
        batchBtn.addActionListener(e -> logBatch());
        fillSampleBtn = new JButton("Sample lines");
        fillSampleBtn.addActionListener(e -> batchArea.setText(
                "# Crop,Qty[,Unit][,Bed]\nNootka Rose,40\nDamask Rose,25,bunches\nDahlia mix,15,stems,Bed C\n"));
        south.add(batchBtn);
        south.add(fillSampleBtn);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void logBatch() {
        if (!GuiPermissions.requireWrite(host, panel, "batch-log harvests")) {
            return;
        }
        try {
            LocalDate date = LocalDate.parse(dateField.getText().trim());
            String defaultUnit = unitField.getText().trim();
            if (defaultUnit.isEmpty()) {
                defaultUnit = "stems";
            }
            String defaultBed = bedField.getText().trim();
            String notes = notesField.getText().trim();
            List<HarvestEntry> batch = new ArrayList<>();
            String[] lines = batchArea.getText().split("\\R");
            for (String line : lines) {
                if (line == null || line.isBlank() || line.trim().startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("[,;\\t]");
                if (parts.length < 2) {
                    showError("Bad batch line (need Crop,Qty[,Unit][,Bed]): " + line);
                    return;
                }
                String crop = parts[0].trim();
                double qty = Double.parseDouble(parts[1].trim());
                String unit = parts.length >= 3 && !parts[2].trim().isEmpty()
                        ? parts[2].trim() : defaultUnit;
                String bed = parts.length >= 4 && !parts[3].trim().isEmpty()
                        ? parts[3].trim() : defaultBed;
                batch.add(new HarvestEntry(date, crop, qty, unit, bed, notes));
            }
            if (host != null) {
                host.setStatus("⏳ Logging batch harvest (" + batch.size() + " rows)…");
            }
            List<HarvestEntry> saved = harvestService.addBatch(batch);
            applyFilter();
            if (host != null) {
                host.refreshAll();
                host.setStatus("Batch harvest: " + saved.size()
                        + " row(s) logged — inventory updated; HARVEST_BATCH recorded.");
            }
        } catch (DateTimeParseException ex) {
            showError("Date must be YYYY-MM-DD on the left form.");
        } catch (NumberFormatException ex) {
            showError("Each batch line needs a numeric qty: Crop,Qty[,Unit][,Bed]");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.setBorder(BorderFactory.createTitledBorder("History filter / search"));
        filterCropField = new JTextField(10);
        filterBedField = new JTextField(8);
        filterNotesField = new JTextField(8);
        filterFromField = new JTextField(LocalDate.now().minusDays(30).toString(), 10);
        filterToField = new JTextField(LocalDate.now().toString(), 10);
        filterStatusLabel = new JLabel(" ");
        filterTotalsOnly = new JCheckBox("Totals = filtered view", true);
        filterTotalsOnly.setToolTipText("When checked, season totals panel uses the filtered rows only.");
        filterTotalsOnly.addActionListener(e -> rebuildTotals());

        java.awt.event.ActionListener applyListener = e -> applyFilter();
        filterCropField.addActionListener(applyListener);
        filterBedField.addActionListener(applyListener);
        filterNotesField.addActionListener(applyListener);
        filterFromField.addActionListener(applyListener);
        filterToField.addActionListener(applyListener);

        bar.add(new JLabel("Crop:"));
        bar.add(filterCropField);
        bar.add(new JLabel("Bed:"));
        bar.add(filterBedField);
        bar.add(new JLabel("Notes:"));
        bar.add(filterNotesField);
        bar.add(new JLabel("From:"));
        bar.add(filterFromField);
        bar.add(new JLabel("To:"));
        bar.add(filterToField);

        JButton apply = new JButton("Apply filter");
        apply.addActionListener(applyListener);
        JButton clear = new JButton("Show all");
        clear.addActionListener(e -> {
            filterCropField.setText("");
            filterBedField.setText("");
            filterNotesField.setText("");
            filterFromField.setText("");
            filterToField.setText("");
            applyFilter();
        });
        JButton thisWeek = new JButton("This week");
        thisWeek.setToolTipText("Filter to trailing 7 days (incl. today).");
        thisWeek.addActionListener(e -> {
            filterFromField.setText(LocalDate.now().minusDays(6).toString());
            filterToField.setText(LocalDate.now().toString());
            applyFilter();
        });
        JButton exportBtn = new JButton("Export all CSV…");
        exportBtn.addActionListener(e -> exportCsv(false));
        JButton exportViewBtn = new JButton("Export filtered CSV…");
        exportViewBtn.addActionListener(e -> exportCsv(true));
        bar.add(apply);
        bar.add(clear);
        bar.add(thisWeek);
        bar.add(exportBtn);
        bar.add(exportViewBtn);
        bar.add(filterTotalsOnly);
        bar.add(filterStatusLabel);
        return bar;
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Add / edit harvest (double-click row to load)"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        dateField = new JTextField(LocalDate.now().toString(), 10);
        cropField = new JTextField(14);
        cropSuggest = new JComboBox<>();
        cropSuggest.setEditable(false);
        cropSuggest.setPrototypeDisplayValue("Recent crop…");
        cropSuggest.addActionListener(e -> {
            Object sel = cropSuggest.getSelectedItem();
            if (sel != null && !"Recent crop…".equals(sel.toString()) && !sel.toString().isBlank()) {
                cropField.setText(sel.toString());
            }
        });
        qtyField = new JTextField("0", 6);
        unitField = new JTextField("stems", 8);
        unitSuggest = new JComboBox<>(new String[]{
                "stems", "bunches", "buckets", "lbs", "oz", "each"
        });
        unitSuggest.setSelectedItem("stems");
        unitSuggest.addActionListener(e -> {
            Object u = unitSuggest.getSelectedItem();
            if (u != null) {
                unitField.setText(u.toString());
            }
        });
        bedField = new JTextField(10);
        notesField = new JTextField(18);

        int row = 0;
        addField(form, c, row++, "Date (YYYY-MM-DD)", dateField);
        addField(form, c, row++, "Crop / variety", cropField);
        addField(form, c, row++, "Recent crops", cropSuggest);
        addField(form, c, row++, "Quantity", qtyField);
        addField(form, c, row++, "Unit", unitField);
        addField(form, c, row++, "Unit presets", unitSuggest);
        addField(form, c, row++, "Bed / field", bedField);
        addField(form, c, row++, "Notes", notesField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addBtn = new JButton("Add harvest");
        addBtn.addActionListener(e -> addHarvest());
        loadBtn = new JButton("Load selected");
        loadBtn.addActionListener(e -> loadSelectedIntoForm());
        saveBtn = new JButton("Save edit");
        saveBtn.setToolTipText("Updates inventory for qty/crop changes (HARVEST_EDIT).");
        saveBtn.addActionListener(e -> saveEdit());
        clearFormBtn = new JButton("Clear form");
        clearFormBtn.addActionListener(e -> clearForm());
        deleteBtn = new JButton("Delete selected");
        deleteBtn.addActionListener(e -> deleteSelected());
        dupBtn = new JButton("Duplicate as today");
        dupBtn.setToolTipText("Copy selected crop/qty/unit/bed into the form with today's date, then Add harvest.");
        dupBtn.addActionListener(e -> duplicateSelectedAsToday());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> applyFilter());
        buttons.add(addBtn);
        buttons.add(loadBtn);
        buttons.add(saveBtn);
        buttons.add(clearFormBtn);
        buttons.add(deleteBtn);
        buttons.add(dupBtn);
        buttons.add(refreshBtn);

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        form.add(buttons, c);
        return form;
    }

    private void addField(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.gridwidth = 1;
        form.add(new JLabel(label + ":"), c);
        c.gridx = 1;
        c.weightx = 1;
        form.add(field, c);
    }

    private void applyFilter() {
        if (tableModel == null) {
            return;
        }
        try {
            String crop = filterCropField == null ? "" : filterCropField.getText().trim();
            LocalDate from = parseOptionalDate(filterFromField == null ? "" : filterFromField.getText());
            LocalDate to = parseOptionalDate(filterToField == null ? "" : filterToField.getText());
            if (from != null && to != null && to.isBefore(from)) {
                showError("Filter 'To' date must be on or after 'From'.");
                return;
            }

            String bed = filterBedField == null ? "" : filterBedField.getText().trim();
            String notes = filterNotesField == null ? "" : filterNotesField.getText().trim();
            List<HarvestEntry> entries = harvestService.filter(crop, bed, notes, from, to);
            lastView = entries;
            tableModel.setRowCount(0);
            double viewQty = 0;
            for (HarvestEntry e : entries) {
                viewQty += e.getQuantity();
                tableModel.addRow(new Object[]{
                        e.getId(),
                        e.getHarvestDate(),
                        e.getCropName(),
                        e.getQuantity(),
                        e.getUnit(),
                        e.getBedOrField(),
                        e.getNotes()
                });
            }
            rebuildTotals();
            refreshCropSuggestions();
            if (filterStatusLabel != null) {
                filterStatusLabel.setText(String.format(
                        "Showing %d row(s) · qty sum %.0f", entries.size(), viewQty));
            }
        } catch (DateTimeParseException ex) {
            showError("Filter dates must be YYYY-MM-DD (or blank).");
        }
    }

    private void refreshCropSuggestions() {
        if (cropSuggest == null) {
            return;
        }
        Object previous = cropSuggest.getSelectedItem();
        cropSuggest.removeAllItems();
        cropSuggest.addItem("Recent crop…");
        for (String crop : harvestService.totalsByCrop().keySet()) {
            cropSuggest.addItem(crop);
        }
        if (previous != null) {
            cropSuggest.setSelectedItem(previous);
        }
    }

    private static LocalDate parseOptionalDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDate.parse(raw.trim());
    }

    private void addHarvest() {
        if (!GuiPermissions.requireWrite(host, panel, "log harvests")) {
            return;
        }
        try {
            HarvestEntry entry = readFormEntry();
            if (host != null) {
                host.setStatus("⏳ Logging harvest and updating inventory…");
            }
            harvestService.add(entry);
            clearForm();
            applyFilter();
            if (host != null) {
                host.refreshAll();
                host.setStatus("Harvest logged: " + entry.getCropName() + " × "
                        + entry.getQuantity() + " " + entry.getUnit()
                        + " — inventory increased; HARVEST_LOG recorded.");
            }
        } catch (DateTimeParseException ex) {
            showError("Date must be YYYY-MM-DD (e.g. " + LocalDate.now() + ").");
        } catch (NumberFormatException ex) {
            showError("Quantity must be a number.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void loadSelectedIntoForm() {
        int row = table.getSelectedRow();
        if (row < 0) {
            showError("Select a harvest row to load (or double-click it).");
            return;
        }
        editingId = (Long) tableModel.getValueAt(row, 0);
        dateField.setText(String.valueOf(tableModel.getValueAt(row, 1)));
        cropField.setText(String.valueOf(tableModel.getValueAt(row, 2)));
        qtyField.setText(String.valueOf(tableModel.getValueAt(row, 3)));
        unitField.setText(String.valueOf(tableModel.getValueAt(row, 4)));
        bedField.setText(nullToEmpty(tableModel.getValueAt(row, 5)));
        notesField.setText(nullToEmpty(tableModel.getValueAt(row, 6)));
        if (host != null) {
            host.setStatus("Editing harvest id=" + editingId + " — change fields and click Save edit.");
        }
    }

    /** Prefill form from selected row with today's date (add mode, not edit). */
    private void duplicateSelectedAsToday() {
        int row = table.getSelectedRow();
        if (row < 0) {
            showError("Select a harvest row to duplicate.");
            return;
        }
        editingId = null;
        dateField.setText(LocalDate.now().toString());
        cropField.setText(String.valueOf(tableModel.getValueAt(row, 2)));
        qtyField.setText(String.valueOf(tableModel.getValueAt(row, 3)));
        unitField.setText(String.valueOf(tableModel.getValueAt(row, 4)));
        bedField.setText(nullToEmpty(tableModel.getValueAt(row, 5)));
        notesField.setText(nullToEmpty(tableModel.getValueAt(row, 6)));
        if (host != null) {
            host.setStatus("Duplicated into form as today — adjust qty if needed, then Add harvest.");
        }
    }

    private void saveEdit() {
        if (!GuiPermissions.requireWrite(host, panel, "edit harvests")) {
            return;
        }
        if (editingId == null) {
            showError("Load a harvest row first (Load selected / double-click), then Save edit.");
            return;
        }
        try {
            HarvestEntry entry = readFormEntry();
            if (host != null) {
                host.setStatus("⏳ Saving harvest edit and correcting inventory…");
            }
            harvestService.update(editingId, entry);
            Long id = editingId;
            clearForm();
            applyFilter();
            if (host != null) {
                host.refreshAll();
                host.setStatus("Harvest id=" + id + " updated — inventory corrected; HARVEST_EDIT recorded.");
            }
        } catch (DateTimeParseException ex) {
            showError("Date must be YYYY-MM-DD.");
        } catch (NumberFormatException ex) {
            showError("Quantity must be a number.");
        } catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
            showError(ex.getMessage());
        }
    }

    private HarvestEntry readFormEntry() {
        LocalDate date = LocalDate.parse(dateField.getText().trim());
        String crop = cropField.getText().trim();
        double qty = Double.parseDouble(qtyField.getText().trim());
        String unit = unitField.getText().trim();
        String bed = bedField.getText().trim();
        String notes = notesField.getText().trim();
        return new HarvestEntry(date, crop, qty, unit, bed, notes);
    }

    private void clearForm() {
        editingId = null;
        dateField.setText(LocalDate.now().toString());
        cropField.setText("");
        qtyField.setText("0");
        unitField.setText("stems");
        bedField.setText("");
        notesField.setText("");
    }

    private void deleteSelected() {
        if (!GuiPermissions.requireWrite(host, panel, "delete harvests")) {
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0) {
            showError("Select a harvest row to delete.");
            return;
        }
        Long id = (Long) tableModel.getValueAt(row, 0);
        String crop = String.valueOf(tableModel.getValueAt(row, 2));
        int confirm = JOptionPane.showConfirmDialog(panel,
                "Delete harvest of '" + crop + "' (id=" + id + ")?\nInventory will be reversed.",
                "Confirm delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            if (host != null) {
                host.setStatus("⏳ Undoing harvest and reversing inventory…");
            }
            harvestService.delete(id);
            if (editingId != null && editingId.equals(id)) {
                clearForm();
            }
            applyFilter();
            if (host != null) {
                host.refreshAll();
                host.setStatus("Harvest deleted (id=" + id + ") — inventory reversed; HARVEST_UNDO recorded.");
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void exportCsv(boolean filteredOnly) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(filteredOnly ? "harvest_filtered.csv" : "harvest_log.csv"));
        chooser.setDialogTitle(filteredOnly
                ? "Export filtered harvest view to CSV"
                : "Export full harvest history to CSV");
        if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            if (host != null) {
                host.setStatus("⏳ Exporting harvest log…");
            }
            if (filteredOnly) {
                harvestService.exportToCsv(file.getAbsolutePath(), lastView);
            } else {
                harvestService.exportToCsv(file.getAbsolutePath());
            }
            if (host != null) {
                host.setStatus("Harvest log exported → " + file.getName()
                        + (filteredOnly ? " (filtered view)" : " (all rows)"));
            }
            JOptionPane.showMessageDialog(panel,
                    "Exported to:\n" + file.getAbsolutePath(),
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError(ex.getMessage());
            if (host != null) {
                host.setStatus("Harvest export failed: " + ex.getMessage());
            }
        }
    }

    private void rebuildTotals() {
        boolean filtered = filterTotalsOnly != null && filterTotalsOnly.isSelected();
        Map<String, Double> totals = filtered
                ? harvestService.totalsByCrop(lastView)
                : harvestService.totalsByCrop();
        if (totals.isEmpty()) {
            totalsArea.setText(filtered
                    ? "(no rows in current filter)\nClear filter or log a harvest."
                    : "(no harvests logged yet)\nTip: log Nootka rose stems after morning cut.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(filtered ? "# filtered view\n" : "# all-time season\n");
        double sum = 0;
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            sum += e.getValue();
            sb.append(String.format("%-20s %8.1f%n", e.getKey(), e.getValue()));
        }
        sb.append(String.format("%n%-20s %8.1f%n", "TOTAL", sum));
        totalsArea.setText(sb.toString());
    }

    private static String nullToEmpty(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(panel, msg, "Harvest Log", JOptionPane.ERROR_MESSAGE);
    }
}
