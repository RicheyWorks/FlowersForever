package com.flowerfarm.model;

/** Planning progress for a tree nut orchard block, without harvest or processing records. */
public enum NutBlockStage {
    PLANNED,
    POLLINATION,
    NUT_SET,
    FLOOR_PREP,
    HARVEST_WINDOW
}
