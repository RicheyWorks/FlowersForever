package com.flowerfarm.model;

/** Practical lifecycle states for one seasonal planting block. */
public enum SeasonPlanStatus {
    PLANNED("Planned"),
    SOWN("Sown"),
    PLANTED("Planted"),
    HARVESTING("Harvesting"),
    COMPLETE("Complete");

    private final String label;

    SeasonPlanStatus(String label) {
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
