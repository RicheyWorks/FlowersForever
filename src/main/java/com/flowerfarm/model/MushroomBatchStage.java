package com.flowerfarm.model;

/** Planning progress for a mushroom batch, separate from harvest and stock. */
public enum MushroomBatchStage {
    PLANNED,
    INOCULATION_WINDOW,
    INCUBATING,
    FRUITING_WINDOW
}
