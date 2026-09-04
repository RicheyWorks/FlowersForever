package com.flowerfarm.gui.view;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.presenter.InventoryItemPresenter;
import com.flowerfarm.model.Item;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reusable inventory-item form for both creation and editing. */
public final class InventoryItemFormView extends JPanel {

    private static final String NO_ROSE_VARIETY = "Not a rose / unspecified";

    private static final String[] CATEGORIES = {
            "Flowers/Plants", "Fertilizers", "Pest Control",
            "Tools/Equipment", "Rentals", "Gas/Fuel", "Other"
    };
    private static final String[] UNITS = {
            "Per Stem", "Per Weight (lb)", "Per Unit", "Per Gallon", "Per Hour"
    };
    private static final String[] ROSE_TYPES = {
            NO_ROSE_VARIETY,
            "Alba", "Damask", "Gallicas", "Centifolia", "Moss", "Bourbon",
            "Hybrid Perpetual", "Portland", "Nootka", "Rosa rugosa",
            "New Dawn", "Queen Elizabeth"
    };

    private final JTextField nameField = new JTextField(22);
    private final JComboBox<String> categoryBox = new JComboBox<>(CATEGORIES);
    private final JComboBox<String> roseBox = new JComboBox<>(ROSE_TYPES);
    private final JTextField priceField = new JTextField("0.00", 10);
    private final JComboBox<String> unitBox = new JComboBox<>(UNITS);
    private final JTextField costField = new JTextField("0.00", 10);
    private final JTextField quantityField = new JTextField("0", 10);
    private final JTextField notesField = new JTextField(28);
    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private Runnable validationCorrectionListener = () -> { };
    private Runnable draftChangeListener = () -> { };

    public InventoryItemFormView() {
        super(new GridBagLayout());
        setOpaque(false);
        setName("inventory.itemForm");
        getAccessibleContext().setAccessibleName("Inventory item details form");

        categoryBox.setEditable(true);
        unitBox.setEditable(true);
        roseBox.setEnabled(true);
        categoryBox.addActionListener(event -> updateRoseAvailability());

        register("name", nameField, "Item name");
        register("category", categoryBox, "Item category");
        register("roseVariety", roseBox, "Rose variety");
        register("price", priceField, "Selling price");
        register("unit", unitBox, "Selling unit");
        register("cost", costField, "Cost per unit");
        register("quantity", quantityField, "Inventory quantity");
        register("notes", notesField, "Item notes");
        FarmUi.initialFocus(nameField);
        FarmUi.entryControl(nameField, "e.g. Café au Lait dahlia", "Use the product name your crew and customers recognize.");
        FarmUi.entryControl(categoryBox, "", "Choose a category or type your own.");
        FarmUi.entryControl(roseBox, "", "Optional; choose a variety only for rose products.");
        FarmUi.entryControl(unitBox, "", "Choose a selling unit or type your own, such as Per Bunch.");
        FarmUi.entryControl(quantityField, "0", "Whole units on hand now. Start at zero if the crop is not ready.");
        FarmUi.entryControl(priceField, "0.00", "Your selling price for one unit. Zero is allowed while planning.");
        FarmUi.entryControl(costField, "0.00", "Your cost for one unit; used in the margin preview.");
        FarmUi.entryControl(notesField, "e.g. Cooler 2 · Friday market", "Optional growing, storage, or packing notes.");

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(0, 5, 9, 5);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        addField(constraints, 0, 0, 1, "Item name *", nameField, 'N');
        addField(constraints, 0, 1, 1, "Category *", categoryBox, 'C');
        addField(constraints, 1, 0, 1, "Quantity on hand *", quantityField, 'Q');
        addField(constraints, 1, 1, 1, "Sold by *", unitBox, 'U');
        addField(constraints, 2, 0, 1, "Selling price ($) *", priceField, 'P');
        addField(constraints, 2, 1, 1, "Cost per unit ($) *", costField, 'O');
        addField(constraints, 3, 0, 1, "Notes · optional", notesField, 'T');
        addField(constraints, 3, 1, 1, "Rose variety · optional", roseBox, 'R');
        JPanel verticalFiller = new JPanel();
        verticalFiller.setOpaque(false);
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        add(verticalFiller, constraints);
        updateRoseAvailability();
    }

    public InventoryItemPresenter.ItemDraft draft() {
        return new InventoryItemPresenter.ItemDraft(
                nameField.getText(), selectedText(categoryBox),
                selectedRoseVariety(),
                priceField.getText(), selectedText(unitBox), costField.getText(),
                quantityField.getText(), notesField.getText());
    }

    public void setItem(Item item) {
        clearValidation();
        nameField.setText(item.getName());
        categoryBox.setSelectedItem(item.getCategory());
        priceField.setText(String.format(java.util.Locale.US, "%.2f", item.getPrice()));
        unitBox.setSelectedItem(item.getUnit());
        costField.setText(String.format(java.util.Locale.US, "%.2f", item.getCost()));
        quantityField.setText(String.valueOf(item.getQuantity()));
        String notes = item.getNotes() == null ? "" : item.getNotes();
        if (notes.startsWith("Type: ")) {
            int separator = notes.indexOf(';');
            String variety = separator < 0 ? notes.substring(6) : notes.substring(6, separator);
            roseBox.setSelectedItem(variety.trim());
            notes = separator < 0 ? "" : notes.substring(separator + 1).trim();
        } else {
            roseBox.setSelectedItem(NO_ROSE_VARIETY);
        }
        notesField.setText(notes);
        updateRoseAvailability();
    }

    /** Restores a captured edit after a storage failure without changing any field. */
    public void setDraft(InventoryItemPresenter.ItemDraft draft) {
        if (draft == null) {
            return;
        }
        clearValidation();
        nameField.setText(draft.name());
        categoryBox.setSelectedItem(draft.category());
        roseBox.setSelectedItem(draft.roseVariety() == null || draft.roseVariety().isBlank()
                ? NO_ROSE_VARIETY : draft.roseVariety());
        priceField.setText(draft.price());
        unitBox.setSelectedItem(draft.unit());
        costField.setText(draft.cost());
        quantityField.setText(draft.quantity());
        notesField.setText(draft.notes());
        updateRoseAvailability();
    }

    public void reset() {
        clearValidation();
        nameField.setText("");
        categoryBox.setSelectedIndex(0);
        roseBox.setSelectedIndex(0);
        priceField.setText("0.00");
        unitBox.setSelectedIndex(0);
        costField.setText("0.00");
        quantityField.setText("0");
        notesField.setText("");
        nameField.requestFocusInWindow();
    }

    /**
     * Prepares another similar catalog item while retaining only shared setup.
     * Identity, variety, quantity, and notes are cleared to avoid copying
     * product-specific facts into the next record.
     */
    public void resetForSimilarItem() {
        String category = selectedText(categoryBox);
        String price = priceField.getText();
        String unit = selectedText(unitBox);
        String cost = costField.getText();
        clearValidation();
        nameField.setText("");
        categoryBox.setSelectedItem(category);
        roseBox.setSelectedIndex(0);
        priceField.setText(price);
        unitBox.setSelectedItem(unit);
        costField.setText(cost);
        quantityField.setText("0");
        notesField.setText("");
        updateRoseAvailability();
        nameField.requestFocusInWindow();
    }

    public void markError(String field, String message) {
        JComponent component = fields.get(field);
        if (component instanceof JTextField textField) {
            FarmUi.markFieldError(textField, message);
        } else if (component != null) {
            component.putClientProperty("JComponent.outline", "error");
            component.setToolTipText(message);
            component.getAccessibleContext().setAccessibleDescription(message);
            component.requestFocusInWindow();
        }
    }

    public void clearValidation() {
        for (JComponent component : fields.values()) {
            FarmUi.clearControlError(component);
        }
    }

    /** Notify the owning workflow when a worker changes a previously invalid value. */
    public void setValidationCorrectionListener(Runnable listener) {
        validationCorrectionListener = listener == null ? () -> { } : listener;
    }

    /** Notify the owning workflow whenever a value affecting the item draft changes. */
    public void setDraftChangeListener(Runnable listener) {
        draftChangeListener = listener == null ? () -> { } : listener;
    }

    public void setWritable(boolean writable) {
        for (JComponent component : fields.values()) {
            component.setEnabled(writable);
        }
        if (writable) {
            updateRoseAvailability();
        }
    }

    public JTextField quantityField() {
        return quantityField;
    }

    /**
     * Fill a routed product name only when the current item draft has no name.
     * Existing worker input is never replaced by cross-workflow navigation.
     */
    public boolean prepareNameIfBlank(String name) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isEmpty()) {
            nameField.requestFocusInWindow();
            return false;
        }
        if (nameField.getText().isBlank()) {
            clearValidation();
            nameField.setText(safeName);
            nameField.requestFocusInWindow();
            return true;
        }
        nameField.requestFocusInWindow();
        return nameField.getText().trim().equalsIgnoreCase(safeName);
    }

    /** Applies safe defaults for a repeated farm item type without erasing entered prices. */
    public void applyPreset(String preset) {
        clearValidation();
        switch (preset == null ? "" : preset) {
            case "Cut flower / stem" -> {
                categoryBox.setSelectedItem("Flowers/Plants");
                unitBox.setSelectedItem("Per Stem");
            }
            case "Dahlia stem" -> applyFlowerStarter("Dahlia", "Per Stem");
            case "Nootka rose stem" -> {
                applyFlowerStarter("Nootka Rose", "Per Stem");
                roseBox.setSelectedItem("Nootka");
            }
            case "Peony stem" -> applyFlowerStarter("Peony", "Per Stem");
            case "Sunflower stem" -> applyFlowerStarter("Sunflower", "Per Stem");
            case "Market bouquet" -> {
                applyFlowerStarter("Market Bouquet", "Per Unit");
            }
            case "Wedding flower bucket" ->
                    applyFlowerStarter("Wedding Flower Bucket", "Per Unit");
            case "Flower subscription" ->
                    applyFlowerStarter("Flower Subscription Bouquet", "Per Unit");
            case "Farm supply" -> {
                categoryBox.setSelectedItem("Other");
                unitBox.setSelectedItem("Per Unit");
            }
            case "Tool or equipment" -> {
                categoryBox.setSelectedItem("Tools/Equipment");
                unitBox.setSelectedItem("Per Unit");
            }
            default -> {
                // Unknown presets intentionally leave worker-entered data unchanged.
            }
        }
        updateRoseAvailability();
        nameField.requestFocusInWindow();
    }

    private void applyFlowerStarter(String name, String unit) {
        setIfBlank(nameField, name);
        categoryBox.setSelectedItem("Flowers/Plants");
        unitBox.setSelectedItem(unit);
    }

    private static void setIfBlank(JTextField field, String value) {
        if (field.getText().isBlank()) {
            field.setText(value);
        }
    }

    private void register(String key, JComponent component, String accessibleName) {
        fields.put(key, component);
        component.getAccessibleContext().setAccessibleName(accessibleName);
        if (component instanceof JTextField textField) {
            watchValidationCorrection(textField, component);
        } else if (component instanceof JComboBox<?> comboBox) {
            comboBox.addActionListener(event -> {
                clearCorrectedValidation(component);
                draftChangeListener.run();
            });
            if (comboBox.isEditable()
                    && comboBox.getEditor().getEditorComponent() instanceof JTextField editor) {
                watchValidationCorrection(editor, component);
            }
        }
    }

    private void watchValidationCorrection(JTextField editor, JComponent validationTarget) {
        editor.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                clearCorrectedValidation(validationTarget);
                draftChangeListener.run();
            }

            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        });
    }

    private void clearCorrectedValidation(JComponent component) {
        if (!"error".equals(component.getClientProperty("JComponent.outline"))) {
            return;
        }
        FarmUi.clearControlError(component);
        validationCorrectionListener.run();
    }

    private void addField(GridBagConstraints constraints, int row, int column, int span, String text,
                          JComponent field, char mnemonic) {
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.gridwidth = span;
        constraints.weightx = 1;
        add(FarmUi.entryField(text, field, field, mnemonic), constraints);
    }

    private void updateRoseAvailability() {
        roseBox.setEnabled("Flowers/Plants".equals(selectedText(categoryBox))
                && categoryBox.isEnabled());
    }

    private String selectedRoseVariety() {
        if (!roseBox.isEnabled()) {
            return "";
        }
        String selected = selectedText(roseBox);
        return NO_ROSE_VARIETY.equals(selected) ? "" : selected;
    }

    private static String selectedText(JComboBox<String> box) {
        Object editorValue = box.isEditable() ? box.getEditor().getItem() : box.getSelectedItem();
        return editorValue == null ? "" : String.valueOf(editorValue).trim();
    }
}
