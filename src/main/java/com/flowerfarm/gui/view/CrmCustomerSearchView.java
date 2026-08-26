package com.flowerfarm.gui.view;

import com.flowerfarm.gui.FarmUi;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Search controls for the CRM customer table.
 */
public final class CrmCustomerSearchView extends JPanel {

    private final JTextField searchField;

    public CrmCustomerSearchView(Runnable searchAction) {
        searchField = FarmUi.searchField(
                "Customer search", "Search name, contact, phone, email, type, or notes", 22);
        searchField.setToolTipText("Search name, contact, email, phone, type, notes");
        searchField.addActionListener(event -> searchAction.run());
        JButton search = FarmUi.primaryButton("Search");
        search.addActionListener(event -> searchAction.run());
        JButton clear = FarmUi.secondaryButton("Show all");
        clear.addActionListener(event -> {
            searchField.setText("");
            searchAction.run();
        });
        JLabel label = new JLabel("Search:");
        label.setLabelFor(searchField);
        label.setDisplayedMnemonic('S');
        setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, FarmUi.CONTROL_GAP, 0));
        setOpaque(false);
        add(label);
        add(searchField);
        add(search);
        add(clear);
    }

    public JTextField searchField() {
        return searchField;
    }
}
