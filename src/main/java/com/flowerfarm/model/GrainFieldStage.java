package com.flowerfarm.model;

/** Planning progress for a grain field, without harvested crop or sales records. */
public enum GrainFieldStage {
    PLANNED,
    SEEDED,
    ESTABLISHED,
    GROWING,
    HARVEST_WINDOW
}
