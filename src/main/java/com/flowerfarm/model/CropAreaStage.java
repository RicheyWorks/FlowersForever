package com.flowerfarm.model;

/** Simple planning progress for a potato field or apple orchard block. */
public enum CropAreaStage {
    PLANNED("Planned"),
    ESTABLISHING("Establishing"),
    GROWING("Growing"),
    HARVEST_READY("Harvest ready"),
    HARVESTED("Harvested"),
    COMPLETE("Complete"),
    CANCELLED("Cancelled");

    private final String label;

    CropAreaStage(String label) {
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
