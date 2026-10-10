package com.flowerfarm.service;

/** A physical count was based on an inventory quantity that has since changed. */
public class InventoryCountConflictException extends RuntimeException {

    private final int currentQuantity;

    public InventoryCountConflictException(String itemName, int currentQuantity) {
        super("Inventory for '" + itemName + "' is now " + currentQuantity
                + ". Review the current quantity and submit the physical count again.");
        this.currentQuantity = currentQuantity;
    }

    public int currentQuantity() {
        return currentQuantity;
    }
}
