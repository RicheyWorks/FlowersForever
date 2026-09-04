package com.flowerfarm.automation;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Transport-neutral automation gateway with policy enforcement and replay safety.
 * REST is only one adapter; MCP, stdio, or an embedded test agent can call this API.
 */
@Component
public class HarnessGateway {

    public static final int REPLAY_CACHE_LIMIT = 256;
    public static final long REPLAY_CACHE_BYTE_LIMIT =
            HarnessManifest.DEFAULT_REPLAY_CACHE_BYTE_LIMIT;
    public static final long PHYSICAL_COMMAND_MAX_TTL_SECONDS = 30;

    private final HarnessRegistry registry;
    private final HarnessProperties properties;
    private final Clock clock;
    private final Map<String, CompletedCommand> completed = new LinkedHashMap<>();
    private long completedOutputBytes;

    @Autowired
    public HarnessGateway(HarnessRegistry registry, HarnessProperties properties) {
        this(registry, properties, Clock.systemUTC());
    }

    public HarnessGateway(HarnessRegistry registry, HarnessProperties properties, Clock clock) {
        this.registry = registry;
        this.properties = properties;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<HarnessPluginDescriptor> discover(String token) {
        properties.authenticate(token);
        properties.authorize(HarnessRisk.READ);
        return registry.descriptors();
    }

    public HarnessManifest manifest(String token) {
        properties.authenticate(token);
        properties.authorize(HarnessRisk.READ);
        List<HarnessPluginDescriptor> plugins = registry.descriptors();
        Map<HarnessRisk, Boolean> policy = new EnumMap<>(HarnessRisk.class);
        for (HarnessRisk risk : HarnessRisk.values()) {
            policy.put(risk, properties.allows(risk));
        }
        List<HarnessToolDefinition> tools = new ArrayList<>();
        for (HarnessPluginDescriptor plugin : plugins) {
            for (HarnessActionSpec action : plugin.actions()) {
                tools.add(HarnessToolDefinition.from(
                        plugin, action, properties.allows(action.risk())));
            }
        }
        return new HarnessManifest(HarnessResponse.PROTOCOL_VERSION,
                REPLAY_CACHE_LIMIT, REPLAY_CACHE_BYTE_LIMIT, true,
                PHYSICAL_COMMAND_MAX_TTL_SECONDS, policy, plugins, tools);
    }

    public HarnessSnapshot observe(String token, String pluginId) {
        properties.authenticate(token);
        properties.authorize(HarnessRisk.READ);
        return observePlugin(plugin(pluginId));
    }

    public synchronized HarnessResponse execute(
            String token, String pluginId, HarnessCommand command) {
        properties.authenticate(token);
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        HarnessPlugin plugin = plugin(pluginId);
        HarnessActionSpec action = plugin.descriptor().action(command.action());
        // Replaying a physical receipt is safe after actions are disabled, but
        // a cached sensitive read must never bypass a revoked read permission.
        if (action.risk() == HarnessRisk.SENSITIVE_READ) {
            properties.authorize(action.risk());
        }
        String cacheKey = plugin.descriptor().id() + "\u0000" + command.requestId();
        CompletedCommand cached = completed.get(cacheKey);
        if (cached != null) {
            if (!cached.command().equals(command)) {
                throw new IllegalArgumentException("requestId '" + command.requestId()
                        + "' was already used for a different command");
            }
            if (cached.response() == null) {
                return complete(cacheKey, plugin, command, cached.execution(), true);
            }
            HarnessResponse response = cached.response();
            return new HarnessResponse(response.protocolVersion(), response.requestId(),
                    response.pluginId(), response.success(), true, response.message(),
                    filterSnapshot(plugin.descriptor(), response.snapshot()), response.output());
        }

        validateArguments(command, action);
        HarnessExecution durableReplay = plugin.replay(command).orElse(null);
        if (durableReplay != null) {
            return complete(cacheKey, plugin, command, durableReplay, true);
        }
        validateFreshness(command, action);
        properties.authorize(action.risk());
        HarnessExecution execution = plugin.execute(command);
        return complete(cacheKey, plugin, command, execution, false);
    }

    private HarnessResponse complete(
            String cacheKey, HarnessPlugin plugin, HarnessCommand command,
            HarnessExecution execution, boolean replayed) {
        Objects.requireNonNull(execution, "Harness plugin returned no execution result");
        // Record the completed effect before observation: an unavailable sensor or
        // UI must not cause an already performed mutation to run again on retry.
        long executionBytes = estimateBytes(execution.output()) + estimateBytes(command.arguments());
        cache(cacheKey, new CompletedCommand(command, execution, null, executionBytes));
        HarnessResponse response = new HarnessResponse(
                HarnessResponse.PROTOCOL_VERSION, command.requestId(),
                plugin.descriptor().id(), execution.success(), replayed,
                execution.message(), observePlugin(plugin), execution.output());
        long outputBytes = executionBytes
                + estimateBytes(response.snapshot().metadata());
        cache(cacheKey, new CompletedCommand(command, execution, response, outputBytes));
        return response;
    }

    private void cache(String key, CompletedCommand entry) {
        CompletedCommand previous = completed.put(key, entry);
        completedOutputBytes += entry.outputBytes()
                - (previous == null ? 0 : previous.outputBytes());
        trimReplayCache();
    }

    private HarnessPlugin plugin(String pluginId) {
        return registry.find(pluginId).orElseThrow(() ->
                new IllegalArgumentException("Unknown harness plugin '" + pluginId + "'"));
    }

    private HarnessSnapshot observePlugin(HarnessPlugin plugin) {
        HarnessPluginDescriptor descriptor = plugin.descriptor();
        HarnessSnapshot snapshot = Objects.requireNonNull(plugin.observe(),
                "Harness plugin '" + descriptor.id() + "' returned no snapshot");
        return filterSnapshot(descriptor, snapshot);
    }

    private HarnessSnapshot filterSnapshot(
            HarnessPluginDescriptor descriptor, HarnessSnapshot snapshot) {
        if (!descriptor.id().equals(snapshot.pluginId())) {
            throw new IllegalStateException("Harness plugin '" + descriptor.id()
                    + "' returned snapshot for '" + snapshot.pluginId() + "'");
        }
        Map<String, Object> metadata = new LinkedHashMap<>(snapshot.metadata());
        if (descriptor.kind() == HarnessPluginKind.DEVICE
                || descriptor.kind() == HarnessPluginKind.ROBOT) {
            Set<String> declaredSignals = descriptor.observations().stream()
                    .map(HarnessObservationSpec::name)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            for (String signal : metadata.keySet()) {
                if (!declaredSignals.contains(signal)) {
                    throw new IllegalStateException("Harness plugin '" + descriptor.id()
                            + "' returned undeclared physical signal '" + signal + "'");
                }
            }
        }
        for (HarnessObservationSpec observation : descriptor.observations()) {
            if (observation.sensitive() && !properties.allows(HarnessRisk.SENSITIVE_READ)) {
                metadata.remove(observation.name());
                continue;
            }
            Object value = metadata.get(observation.name());
            if (value != null && !observation.type().accepts(value)) {
                throw new IllegalStateException("Harness plugin '" + descriptor.id()
                        + "' observation '" + observation.name() + "' must be a JSON "
                        + observation.type().wireName());
            }
        }
        return new HarnessSnapshot(snapshot.pluginId(), snapshot.capturedAt(),
                snapshot.route(), snapshot.controls(), metadata);
    }

    private static void validateArguments(
            HarnessCommand command, HarnessActionSpec action) {
        Set<String> declared = new HashSet<>();
        for (HarnessArgumentSpec argument : action.arguments()) {
            declared.add(argument.name());
            Object value = command.arguments().get(argument.name());
            if (argument.required() && value == null) {
                throw new IllegalArgumentException("Action '" + action.name()
                        + "' requires argument '" + argument.name() + "'");
            }
            if (command.arguments().containsKey(argument.name())) {
                argument.validate(value, action.name());
            }
        }
        for (String supplied : command.arguments().keySet()) {
            if (!declared.contains(supplied)) {
                throw new IllegalArgumentException("Action '" + action.name()
                        + "' does not accept argument '" + supplied + "'");
            }
        }
    }

    private void validateFreshness(
            HarnessCommand command, HarnessActionSpec action) {
        if (!action.requiresFreshness()) {
            return;
        }
        Instant expiresAt = command.expiresAt();
        if (expiresAt == null) {
            throw new IllegalArgumentException("Action '" + action.name()
                    + "' requires expiresAt for stale-command protection");
        }
        Instant now = clock.instant();
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("Action '" + action.name()
                    + "' command has expired");
        }
        if (expiresAt.isAfter(now.plusSeconds(PHYSICAL_COMMAND_MAX_TTL_SECONDS))) {
            throw new IllegalArgumentException("Action '" + action.name()
                    + "' expiresAt must be within "
                    + PHYSICAL_COMMAND_MAX_TTL_SECONDS + " seconds");
        }
    }

    private void trimReplayCache() {
        while (completed.size() > REPLAY_CACHE_LIMIT
                || completedOutputBytes > REPLAY_CACHE_BYTE_LIMIT
                && completed.size() > 1) {
            String first = completed.keySet().iterator().next();
            CompletedCommand removed = completed.remove(first);
            completedOutputBytes -= removed.outputBytes();
        }
    }

    private static long estimateBytes(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8).length;
        }
        if (value instanceof Map<?, ?> map) {
            long bytes = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                bytes += estimateBytes(entry.getKey()) + estimateBytes(entry.getValue());
            }
            return bytes;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().mapToLong(HarnessGateway::estimateBytes).sum();
        }
        if (value.getClass().isArray()) {
            long bytes = 0;
            for (int index = 0; index < Array.getLength(value); index++) {
                bytes += estimateBytes(Array.get(value, index));
            }
            return bytes;
        }
        return 16;
    }

    private record CompletedCommand(HarnessCommand command, HarnessExecution execution,
                                    HarnessResponse response, long outputBytes) {
    }
}
