package com.flowerfarm.automation;

import java.util.List;
import java.util.Map;

/** One authenticated document from which an adapter can build AI tools. */
public record HarnessManifest(String protocolVersion, int replayCacheLimit,
                              long replayCacheByteLimit, boolean strictArguments,
                              long physicalCommandMaxTtlSeconds,
                              Map<HarnessRisk, Boolean> policy,
                              List<HarnessPluginDescriptor> plugins,
                              List<HarnessToolDefinition> tools) {

    public static final long DEFAULT_REPLAY_CACHE_BYTE_LIMIT = 8L * 1024 * 1024;

    /** Backward-compatible constructor for adapters compiled before physical actions. */
    public HarnessManifest(String protocolVersion, int replayCacheLimit,
                           long replayCacheByteLimit, boolean strictArguments,
                           Map<HarnessRisk, Boolean> policy,
                           List<HarnessPluginDescriptor> plugins,
                           List<HarnessToolDefinition> tools) {
        this(protocolVersion, replayCacheLimit, replayCacheByteLimit, strictArguments,
                HarnessGateway.PHYSICAL_COMMAND_MAX_TTL_SECONDS,
                policy, plugins, tools);
    }

    /** Backward-compatible constructor for embedded adapters compiled against 1.0. */
    public HarnessManifest(String protocolVersion, int replayCacheLimit,
                           boolean strictArguments, Map<HarnessRisk, Boolean> policy,
                           List<HarnessPluginDescriptor> plugins,
                           List<HarnessToolDefinition> tools) {
        this(protocolVersion, replayCacheLimit, DEFAULT_REPLAY_CACHE_BYTE_LIMIT,
                strictArguments, HarnessGateway.PHYSICAL_COMMAND_MAX_TTL_SECONDS,
                policy, plugins, tools);
    }

    public HarnessManifest {
        protocolVersion = protocolVersion == null || protocolVersion.isBlank()
                ? HarnessResponse.PROTOCOL_VERSION : protocolVersion.trim();
        if (!HarnessResponse.PROTOCOL_VERSION.equals(protocolVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported harness protocol version '" + protocolVersion + "'");
        }
        if (replayCacheLimit < 1) {
            throw new IllegalArgumentException("replayCacheLimit must be positive");
        }
        if (replayCacheByteLimit < 1) {
            throw new IllegalArgumentException("replayCacheByteLimit must be positive");
        }
        if (physicalCommandMaxTtlSeconds < 1) {
            throw new IllegalArgumentException(
                    "physicalCommandMaxTtlSeconds must be positive");
        }
        policy = policy == null ? Map.of() : Map.copyOf(policy);
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
