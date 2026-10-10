package com.flowerfarm.gui.view;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.Item;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;

/** Focused physical-count input with a live adjustment preview. */
public final class StockCountView extends JPanel {

    private final Item item;
    private final JSpinner countInput;
    private final JLabel varianceLabel;

    public StockCountView(Item item) {
        super(new BorderLayout(0, FarmUi.CONTROL_GAP));
        if (item == null) {
            throw new IllegalArgumentException("Select an inventory item first.");
        }
        this.item = item;
        setName("inventory.stockCountForm");
        setOpaque(false);
        getAccessibleContext().setAccessibleName("Physical count for " + item.getName());

        countInput = FarmUi.initialFocus(new JSpinner(new SpinnerNumberModel(
                item.getQuantity(), 0, 1_000_000, 1)));
        countInput.setName("inventory.physicalCount");
        countInput.getAccessibleContext().setAccessibleName(
                "Counted quantity for " + item.getName());
        countInput.getAccessibleContext().setAccessibleDescription(
                "Current system quantity is " + item.getQuantity() + " " + item.getUnit());
        JLabel countLabel = new JLabel("Counted quantity:");
        countLabel.setLabelFor(countInput);
        countLabel.setDisplayedMnemonic('C');

        varianceLabel = FarmUi.statusBadge("Count matches system quantity", FarmUi.Tone.NEUTRAL);
        varianceLabel.setName("inventory.countVariance");
        varianceLabel.getAccessibleContext().setAccessibleName("Inventory count adjustment");
        countInput.addChangeListener(event -> updateVariance());

        add(FarmUi.toolbar(countLabel, countInput, new JLabel(item.getUnit())),
                BorderLayout.NORTH);
        add(varianceLabel, BorderLayout.SOUTH);
        updateVariance();
    }

    public int countedQuantity() {
        return ((Number) countInput.getValue()).intValue();
    }

    public int variance() {
        return countedQuantity() - item.getQuantity();
    }

    public void setCountedQuantity(int quantity) {
        countInput.setValue(quantity);
    }

    public JSpinner countInput() {
        return countInput;
    }

    public JLabel varianceLabel() {
        return varianceLabel;
    }

    private void updateVariance() {
        int difference = variance();
        String message;
        FarmUi.Tone tone;
        if (difference == 0) {
            message = "Count matches system quantity";
            tone = FarmUi.Tone.NEUTRAL;
        } else if (difference > 0) {
            message = "Inventory will increase by " + difference;
            tone = FarmUi.Tone.WARNING;
        } else {
            message = "Inventory will decrease by " + Math.abs(difference);
            tone = FarmUi.Tone.WARNING;
        }
        FarmUi.updateStatusBadge(varianceLabel, message, tone);
        varianceLabel.getAccessibleContext().setAccessibleDescription(
                message + "; new quantity " + countedQuantity() + " " + item.getUnit());
    }
}
