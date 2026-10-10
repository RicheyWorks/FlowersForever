package com.flowerfarm.gui.presenter;

import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;

import java.util.List;

/**
 * UI-independent inventory-item parsing and mutation workflows shared by add
 * and edit screens.
 */
public final class InventoryItemPresenter {

    private final InventoryService inventoryService;

    public InventoryItemPresenter(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public List<Item> items() {
        return inventoryService.getAllItems();
    }

    public Item add(ItemDraft draft) {
        Item item = toItem(draft);
        inventoryService.addItem(item);
        return item;
    }

    public Item update(int index, ItemDraft draft) {
        Item item = toItem(draft);
        inventoryService.editItem(index, item);
        return item;
    }

    /** Updates the exact persisted item rather than relying on a mutable list position. */
    public Item updateById(Long id, ItemDraft draft) {
        if (id == null) {
            throw new FieldValidationException("name", "This inventory item has no saved id.");
        }
        return inventoryService.updateById(id, toItem(draft));
    }

    /** Updates a physical count without making the worker re-enter product details. */
    public Item setQuantity(int index, Item current, double quantity) {
        if (current == null) {
            throw new FieldValidationException("quantity", "Select an inventory item first.");
        }
        if (quantity < 0) {
            throw new FieldValidationException("quantity",
                    "Counted quantity must be a whole number of zero or more.");
        }
        Item updated = new Item(current.getId(), current.getName(), current.getCategory(),
                current.getPrice(), current.getUnit(), current.getCost(), quantity,
                current.getNotes());
        inventoryService.editItem(index, updated);
        return updated;
    }

    /** Records a physical count against the exact persisted item id. */
    public Item setQuantityById(Long id, Item current, double quantity) {
        if (id == null) {
            throw new FieldValidationException("quantity", "This inventory item has no saved id.");
        }
        Item updated = quantityUpdate(current, quantity);
        return inventoryService.updateById(id, updated);
    }

    private static Item quantityUpdate(Item current, double quantity) {
        if (current == null) {
            throw new FieldValidationException("quantity", "Select an inventory item first.");
        }
        if (quantity < 0) {
            throw new FieldValidationException("quantity",
                    "Counted quantity must be a whole number of zero or more.");
        }
        return new Item(current.getId(), current.getName(), current.getCategory(),
                current.getPrice(), current.getUnit(), current.getCost(), quantity,
                current.getNotes());
    }

    public void delete(int index) {
        inventoryService.deleteItem(index);
    }

    public void deleteById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("This inventory item has no saved id.");
        }
        inventoryService.deleteById(id);
    }

    public Item toItem(ItemDraft draft) {
        if (draft == null) {
            throw new FieldValidationException("name", "Enter the item details before saving.");
        }
        String name = required(draft.name(), "name", "Enter the item name.");
        String category = required(draft.category(), "category", "Choose or enter a category.");
        String unit = required(draft.unit(), "unit", "Choose or enter how this item is sold.");
        double price = nonNegativeDecimal(draft.price(), "price", "Selling price");
        double cost = nonNegativeDecimal(draft.cost(), "cost", "Cost per unit");
        double quantity = nonNegativeDecimal(draft.quantity(), "quantity", "Quantity");
        String notes = draft.notes() == null ? "" : draft.notes().trim();
        if ("Flowers/Plants".equals(category)
                && draft.roseVariety() != null && !draft.roseVariety().isBlank()) {
            notes = "Type: " + draft.roseVariety().trim()
                    + (notes.isBlank() ? "" : "; " + notes);
        }
        try {
            return new Item(name, category, price, unit, cost, quantity, notes);
        } catch (IllegalArgumentException exception) {
            throw new FieldValidationException(modelField(exception), exception.getMessage());
        }
    }

    private static String required(String raw, String field, String message) {
        if (raw == null || raw.isBlank()) {
            throw new FieldValidationException(field, message);
        }
        return raw.trim();
    }

    private static double nonNegativeDecimal(String raw, String field, String label) {
        try {
            double value = Double.parseDouble(raw == null ? "" : raw.trim());
            if (!Double.isFinite(value) || value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new FieldValidationException(field,
                    label + " must be zero or greater, such as 12.50.");
        }
    }

    private static String modelField(IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.startsWith("Item name")) {
            return "name";
        }
        if (message.startsWith("Category")) {
            return "category";
        }
        if (message.startsWith("Price")) {
            return "price";
        }
        if (message.startsWith("Unit")) {
            return "unit";
        }
        if (message.startsWith("Cost")) {
            return "cost";
        }
        if (message.startsWith("Quantity")) {
            return "quantity";
        }
        if (message.startsWith("Notes")) {
            return "notes";
        }
        return "name";
    }

    public record ItemDraft(String name, String category, String roseVariety,
                            String price, String unit, String cost,
                            String quantity, String notes) {
    }

    /** Validation failure tied to one stable form field key. */
    public static final class FieldValidationException extends IllegalArgumentException {
        private final String field;

        public FieldValidationException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }
}
