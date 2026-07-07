package com.flowerfarm.gui.tabs;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import javax.swing.*;
import java.awt.*;

/**
 * Clean, self-contained "Add Item" form tab.
 *
 * <p>Category selection conditionally enables the Rose Type combo; on a
 * successful add the tab clears the form and asks the {@link TabHost} to
 * refresh the other tabs so the Dashboard and Inventory views stay in sync.
 */
public class AddItemTab implements FlowerFarmTab {

    private final InventoryService inventoryService;
    private final TabHost host;

    private JPanel panel;

    public AddItemTab(InventoryService inventoryService, TabHost host) {
        this.inventoryService = inventoryService;
        this.host = host;
    }

    @Override
    public String getTabTitle() {
        return "Add Item";
    }

    @Override
    public String getDescription() {
        return "Add a new item to the inventory";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildForm();
        }
        return panel;
    }

    private void buildForm() {
        panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        JTextField nameField = new JTextField();
        JTextField priceField = new JTextField();
        JTextField costField = new JTextField();
        JTextField qtyField = new JTextField();
        JTextField notesField = new JTextField();

        String[] categories = {
                "Flowers/Plants", "Fertilizers", "Pest Control",
                "Tools/Equipment", "Rentals", "Gas/Fuel", "Other"
        };
        JComboBox<String> categoryCombo = new JComboBox<>(categories);

        String[] units = {"Per Stem", "Per Weight (lb)", "Per Unit", "Per Gallon", "Per Hour"};
        JComboBox<String> unitCombo = new JComboBox<>(units);

        String[] roseTypes = {
                "Alba", "Damask", "Gallicas", "Centifolia", "Moss",
                "Bourbon", "Hybrid Perpetual", "Portland",
                "Nootka", "Rosa rugosa", "New Dawn", "Queen Elizabeth"
        };
        JComboBox<String> roseCombo = new JComboBox<>(roseTypes);
        roseCombo.setEnabled(false);

        categoryCombo.addActionListener(e ->
                roseCombo.setEnabled("Flowers/Plants".equals(categoryCombo.getSelectedItem())));

        panel.add(new JLabel("Item Name *"));               panel.add(nameField);
        panel.add(new JLabel("Category"));                  panel.add(categoryCombo);
        panel.add(new JLabel("Rose Type (if applicable)")); panel.add(roseCombo);
        panel.add(new JLabel("Price ($) *"));               panel.add(priceField);
        panel.add(new JLabel("Unit"));                      panel.add(unitCombo);
        panel.add(new JLabel("Cost per Unit ($) *"));       panel.add(costField);
        panel.add(new JLabel("Initial Quantity *"));        panel.add(qtyField);
        panel.add(new JLabel("Notes / PNW Details"));       panel.add(notesField);

        JButton addBtn = new JButton("Add to Inventory");
        addBtn.setFont(addBtn.getFont().deriveFont(Font.BOLD, 14f));

        addBtn.addActionListener(e -> {
            try {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    throw new IllegalArgumentException("Name is required");
                }

                String category = (String) categoryCombo.getSelectedItem();
                double price = Double.parseDouble(priceField.getText().trim());
                String unit = (String) unitCombo.getSelectedItem();
                double cost = Double.parseDouble(costField.getText().trim());
                int qty = Integer.parseInt(qtyField.getText().trim());
                String notes = notesField.getText().trim();

                if ("Flowers/Plants".equals(category) && roseCombo.isEnabled()
                        && roseCombo.getSelectedItem() != null) {
                    notes = "Type: " + roseCombo.getSelectedItem() + "; " + notes;
                }

                inventoryService.addItem(new Item(name, category, price, unit, cost, qty, notes));

                if (host != null) {
                    host.refreshAll();
                    host.setStatus("Added: " + name);
                }
                JOptionPane.showMessageDialog(panel, "Item added successfully: " + name,
                        "Success", JOptionPane.INFORMATION_MESSAGE);

                nameField.setText("");
                priceField.setText("");
                costField.setText("");
                qtyField.setText("");
                notesField.setText("");

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Price, Cost and Quantity must be valid numbers.",
                        "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(panel, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.add(new JLabel());
        panel.add(addBtn);
    }
}
