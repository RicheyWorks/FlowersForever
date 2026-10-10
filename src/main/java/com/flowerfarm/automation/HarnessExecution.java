package com.flowerfarm.automation;

import java.util.Map;

/** Plugin-owned outcome before the gateway adds a fresh observation. */
public record HarnessExecution(boolean success, String message, Map<String, Object> output) {

    public HarnessExecution {
        message = message == null ? "" : message;
        output = output == null ? Map.of() : Map.copyOf(output);
    }

    public static HarnessExecution success(String message) {
        return new HarnessExecution(true, message, Map.of());
    }

    public static HarnessExecution success(String message, Map<String, Object> output) {
        return new HarnessExecution(true, message, output);
    }
}
