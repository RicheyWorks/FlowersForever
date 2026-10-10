package com.flowerfarm.automation;

import java.util.Optional;

/**
 * Extension point implemented by a UI, workflow, device, or third-party adapter.
 * Implementations may be registered as ordinary Spring beans.
 */
public interface HarnessPlugin {

    HarnessPluginDescriptor descriptor();

    HarnessSnapshot observe();

    /** Recover an already completed exact request from plugin-owned durable state. */
    default Optional<HarnessExecution> replay(HarnessCommand command) {
        return Optional.empty();
    }

    HarnessExecution execute(HarnessCommand command);
}
