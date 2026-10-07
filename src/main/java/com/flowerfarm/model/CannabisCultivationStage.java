package com.flowerfarm.model;

/** Operational stage of a cultivation batch; no regulatory status is implied. */
public enum CannabisCultivationStage {
    PLANNED("Planned"),
    PROPAGATING("Propagating"),
    VEGETATIVE("Vegetative"),
    FLOWERING("Flowering"),
    HARVESTED("Harvested"),
    COMPLETE("Complete"),
    CANCELLED("Cancelled");

    private final String label;

    CannabisCultivationStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
