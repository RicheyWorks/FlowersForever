package com.flowerfarm.model;

/** Units recorded as entered for a milk collection; no unit conversion is implied. */
public enum MilkCollectionUnit {
    POUNDS("Pounds", "lb"),
    GALLONS("Gallons", "gal"),
    LITERS("Liters", "L");

    private final String label;
    private final String symbol;

    MilkCollectionUnit(String label, String symbol) {
        this.label = label;
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    @Override
    public String toString() {
        return label + " (" + symbol + ")";
    }
}
