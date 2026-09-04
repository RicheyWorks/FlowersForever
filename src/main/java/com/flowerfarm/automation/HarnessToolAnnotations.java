package com.flowerfarm.automation;

/**
 * Provider-neutral execution hints. The first four mirror common AI/MCP tool
 * annotations; the final two make physical-device safety explicit.
 */
public record HarnessToolAnnotations(boolean readOnlyHint,
                                     boolean destructiveHint,
                                     boolean idempotentHint,
                                     boolean openWorldHint,
                                     boolean physicalWorldHint,
                                     boolean freshnessRequired) {

    public static HarnessToolAnnotations from(HarnessActionSpec action) {
        boolean physical = action.effect() == HarnessActionEffect.PHYSICAL_WORLD;
        boolean openWorld = physical
                || action.effect() == HarnessActionEffect.EXTERNAL_SYSTEM;
        return new HarnessToolAnnotations(
                action.effect() == HarnessActionEffect.NONE,
                action.risk() == HarnessRisk.DESTRUCTIVE || physical,
                action.idempotent(), openWorld, physical,
                action.requiresFreshness());
    }
}
