package com.flowerfarm.automation;

import java.util.Map;

/** Stable command envelope returned by every transport. */
public record HarnessResponse(String protocolVersion, String requestId, String pluginId,
                              boolean success, boolean replayed, String message,
                              HarnessSnapshot snapshot, Map<String, Object> output) {

    public static final String PROTOCOL_VERSION = "1.0";

    public HarnessResponse {
        if (protocolVersion == null || protocolVersion.isBlank()) {
            protocolVersion = PROTOCOL_VERSION;
        } else if (!PROTOCOL_VERSION.equals(protocolVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported harness protocol version '" + protocolVersion + "'");
        }
        message = message == null ? "" : message;
        output = output == null ? Map.of() : Map.copyOf(output);
    }

    public HarnessResponse asReplay() {
        return new HarnessResponse(protocolVersion, requestId, pluginId, success, true,
                message, snapshot, output);
    }
}
