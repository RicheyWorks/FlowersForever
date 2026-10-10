package com.flowerfarm.gui.view;

import com.flowerfarm.util.InventoryQuantity;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.Item;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JFormattedTextField;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.NumberFormatter;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.ParsePosition;

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

        countInput = FarmUi.initialFocus(new JSpinner(com.flowerfarm.util.InventoryUnits.measured(item.getUnit())
                ? new SpinnerNumberModel(item.getQuantity(), 0d, (double) Integer.MAX_VALUE, 0.001d)
                : new SpinnerNumberModel(InventoryQuantity.whole(item.getQuantity()),
                        0, Integer.MAX_VALUE, 1)));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(countInput, "0.######");
        editor.getFormat().setParseBigDecimal(true);
        NumberFormatter formatter = new NumberFormatter(editor.getFormat()) {
            @Override
            public Object stringToValue(String text) throws ParseException {
                String input = text.trim();
                ParsePosition position = new ParsePosition(0);
                Number parsed = editor.getFormat().parse(input, position);
                if (!(parsed instanceof BigDecimal amount) || position.getIndex() != input.length()) {
                    throw new ParseException("Enter a complete numeric quantity.", position.getIndex());
                }
                try {
                    BigDecimal exactAmount = amount.setScale(InventoryQuantity.SCALE, RoundingMode.UNNECESSARY);
                    double quantity = InventoryQuantity.requireForUnit(exactAmount.doubleValue(), item.getUnit());
                    if (com.flowerfarm.util.InventoryUnits.measured(item.getUnit())) {
                        return Double.valueOf(quantity);
                    }
                    return Integer.valueOf(exactAmount.intValueExact());
                } catch (IllegalArgumentException | ArithmeticException invalid) {
                    ParseException rejected = new ParseException(invalid.getMessage(), 0);
                    rejected.initCause(invalid);
                    throw rejected;
                }
            }
        };
        editor.getTextField().setFormatterFactory(new DefaultFormatterFactory(formatter));
        editor.getTextField().setFocusLostBehavior(JFormattedTextField.PERSIST);
        countInput.setEditor(editor);
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

    public double countedQuantity() {
        return ((Number) countInput.getValue()).doubleValue();
    }

    /** Validate the actual typed text before saving, including an unfocused draft. */
    public double commitCountedQuantity() {
        try {
            countInput.commitEdit();
            return countedQuantity();
        } catch (ParseException invalid) {
            throw new IllegalArgumentException(invalid.getMessage(), invalid);
        }
    }

    public double variance() {
        return java.math.BigDecimal.valueOf(countedQuantity())
                .subtract(java.math.BigDecimal.valueOf(item.getQuantity())).doubleValue();
    }

    public void setCountedQuantity(double quantity) {
        if (com.flowerfarm.util.InventoryUnits.measured(item.getUnit())) countInput.setValue(quantity);
        else countInput.setValue(InventoryQuantity.whole(quantity));
    }

    public JSpinner countInput() {
        return countInput;
    }

    public JLabel varianceLabel() {
        return varianceLabel;
    }

    private void updateVariance() {
        double difference = variance();
        String message;
        FarmUi.Tone tone;
        if (difference == 0) {
            message = "Count matches system quantity";
            tone = FarmUi.Tone.NEUTRAL;
        } else if (difference > 0) {
            message = "Inventory will increase by " + InventoryQuantity.format(difference);
            tone = FarmUi.Tone.WARNING;
        } else {
            message = "Inventory will decrease by " + InventoryQuantity.format(Math.abs(difference));
            tone = FarmUi.Tone.WARNING;
        }
        FarmUi.updateStatusBadge(varianceLabel, message, tone);
        varianceLabel.getAccessibleContext().setAccessibleDescription(
                message + "; new quantity " + InventoryQuantity.format(countedQuantity()) + " " + item.getUnit());
    }
}
