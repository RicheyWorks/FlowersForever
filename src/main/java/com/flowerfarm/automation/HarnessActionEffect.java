package com.flowerfarm.automation;

/** Primary state boundary affected by a discoverable harness action. */
public enum HarnessActionEffect {
    NONE,
    APPLICATION_STATE,
    DRAFT_STATE,
    PERSISTENT_DATA,
    EXTERNAL_SYSTEM,
    PHYSICAL_WORLD;

    public static HarnessActionEffect defaultFor(HarnessRisk risk) {
        return switch (risk) {
            case READ, SENSITIVE_READ -> NONE;
            case NAVIGATE -> APPLICATION_STATE;
            case DRAFT -> DRAFT_STATE;
            case MUTATE, DESTRUCTIVE -> PERSISTENT_DATA;
            case PHYSICAL -> PHYSICAL_WORLD;
        };
    }
}
