package com.flowerfarm.automation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Structured observation returned to any AI or automation transport. */
public record HarnessSnapshot(String pluginId, Instant capturedAt, List<String> route,
                              List<HarnessControl> controls, Map<String, Object> metadata) {

    public HarnessSnapshot {
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
        route = route == null ? List.of() : List.copyOf(route);
        controls = controls == null ? List.of() : List.copyOf(controls);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
