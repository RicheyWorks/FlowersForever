package com.flowerfarm.service;

/** A physical count was based on an inventory quantity that has since changed. */
public class InventoryCountConflictException extends RuntimeException {

    private final double currentQuantity;

    public InventoryCountConflictException(String itemName, double currentQuantity) {
        super("Inventory for '" + itemName + "' is now " + currentQuantity
                + ". Review the current quantity and submit the physical count again.");
        this.currentQuantity = currentQuantity;
    }

    public double currentQuantity() {
        return currentQuantity;
    }
}
