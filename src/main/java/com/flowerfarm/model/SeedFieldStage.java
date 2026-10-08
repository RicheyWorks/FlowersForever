package com.flowerfarm.model;

/** Planning progress for a seed field, without harvest or certification records. */
public enum SeedFieldStage {
    PLANNED,
    SOWN,
    ESTABLISHED,
    FLOWERING,
    HARVEST_WINDOW
}
