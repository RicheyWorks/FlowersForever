package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.event.MouseEvent;

/** Interactive selling-unit reference for PNW flower-farm products. */
public final class PricingInfoTab implements FlowerFarmTab {

    private static final String[][] GUIDANCE = {
            {"Cut flowers", "Per Stem", "Roses, dahlias, tulips, ranunculus",
                    "Best for stem counts and wholesale recipes"},
            {"Bulk products", "Per Weight (lb)", "Herbs, greens, volume peonies",
                    "Record the scale weight at packing"},
            {"Supplies and tools", "Per Unit", "Snips, buckets, floral supplies",
                    "Use one inventory count per sellable item"},
            {"Liquids", "Per Gallon", "Fertilizer, fuel, amendments",
                    "Use the same volume unit for counts and sales"},
            {"Equipment and labor", "Per Hour", "Tractor, delivery, design labor",
                    "Record the billable duration in notes"}
    };

    private JPanel panel;

    @Override
    public String getTabTitle() {
        return "Pricing Info";
    }

    @Override
    public String getDescription() {
        return "Choose consistent selling units for PNW flower-farm products";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUi();
        }
        return panel;
    }

    private void buildUi() {
        panel = FarmUi.page();
        JLabel recommendation = FarmUi.statusBadge(
                "Cut flowers → Per Stem", FarmUi.Tone.SUCCESS);
        panel.add(FarmUi.pageHeader(
                "Pricing and selling units",
                "Use one clear unit from inventory count through market or wholesale sale.",
                recommendation), BorderLayout.NORTH);

        JComboBox<String> productType = new JComboBox<>();
        FarmUi.initialFocus(productType);
        productType.getAccessibleContext().setAccessibleName("Product type for unit guidance");
        for (String[] row : GUIDANCE) {
            productType.addItem(row[0]);
        }

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Product type", "Recommended unit", "Examples", "Worker note"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (String[] row : GUIDANCE) {
            model.addRow(row);
        }
        JTable table = new JTable(model) {
            @Override public String getToolTipText(MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                int column = columnAtPoint(event.getPoint());
                if (row < 0 || column < 0) {
                    return null;
                }
                String value = String.valueOf(getValueAt(row, column));
                return "<html>" + value.replace("&", "&amp;")
                        .replace("<", "&lt;").replace(">", "&gt;") + "</html>";
            }
        };
        table.setName("pricing.units.table");
        FarmUi.configureTable(table, "Pricing and selling unit guidance table");
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(250);
        table.getColumnModel().getColumn(3).setPreferredWidth(280);

        productType.addActionListener(event -> {
            int index = productType.getSelectedIndex();
            if (index >= 0) {
                int viewRow = table.convertRowIndexToView(index);
                table.setRowSelectionInterval(viewRow, viewRow);
                table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                FarmUi.updateStatusBadge(recommendation,
                        GUIDANCE[index][0] + " → " + GUIDANCE[index][1],
                        FarmUi.Tone.SUCCESS);
            }
        });
        productType.setSelectedIndex(0);

        JPanel chooser = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        chooser.setOpaque(false);
        chooser.add(FarmUi.toolbar(new JLabel("What are you selling?"), productType), BorderLayout.NORTH);
        chooser.add(FarmUi.formHint("The same unit should appear in inventory, orders, and reports."),
                BorderLayout.SOUTH);
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.SECTION_GAP));
        content.setOpaque(false);
        content.add(FarmUi.section(
                "Choose a product type",
                "The recommended unit and matching table row update together.",
                chooser), BorderLayout.NORTH);
        content.add(FarmUi.section(
                "Selling-unit guide",
                "Sort the table when comparing product types or preparing price lists.",
                FarmUi.tableScroll(table)), BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
    }
}
