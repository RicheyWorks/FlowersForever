package com.flowerfarm.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final HarnessPayloadSizer payloadSizer;
    private final Map<String, CompletedCommand> completed = new LinkedHashMap<>();
    private long completedOutputBytes;

    @Autowired
    public HarnessGateway(HarnessRegistry registry, HarnessProperties properties, ObjectMapper mapper) {
        this(registry, properties, Clock.systemUTC(), mapper);
    }

    public HarnessGateway(HarnessRegistry registry, HarnessProperties properties) {
        this(registry, properties, Clock.systemUTC(), new ObjectMapper().findAndRegisterModules());
    }

    public HarnessGateway(HarnessRegistry registry, HarnessProperties properties, Clock clock) {
        this(registry, properties, clock, new ObjectMapper().findAndRegisterModules());
    }

    private HarnessGateway(HarnessRegistry registry, HarnessProperties properties, Clock clock,
                           ObjectMapper mapper) {
        this.registry = registry;
        this.properties = properties;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.payloadSizer = new HarnessPayloadSizer(mapper, REPLAY_CACHE_BYTE_LIMIT);
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
        Optional<HarnessResponse> replay = replayExisting(plugin, action, command);
        if (replay.isPresent()) {
            return replay.get();
        }
        validateFreshness(command, action);
        properties.authorize(action.risk());
        HarnessExecution execution = plugin.execute(command);
        return complete(cacheKey(plugin, command), plugin, command, execution, false);
    }

    /** Recovers an exact existing receipt without executing a fresh plugin action. */
    public synchronized Optional<HarnessResponse> replay(
            String token, String pluginId, HarnessCommand command) {
        properties.authenticate(token);
        if (command == null) {
            throw new IllegalArgumentException("command is required");
        }
        HarnessPlugin plugin = plugin(pluginId);
        HarnessActionSpec action = plugin.descriptor().action(command.action());
        return replayExisting(plugin, action, command);
    }

    private Optional<HarnessResponse> replayExisting(
            HarnessPlugin plugin, HarnessActionSpec action, HarnessCommand command) {
        // Replaying a physical receipt is safe after actions are disabled, but
        // a cached sensitive read must never bypass a revoked read permission.
        if (action.risk() == HarnessRisk.SENSITIVE_READ) {
            properties.authorize(action.risk());
        }
        String cacheKey = cacheKey(plugin, command);
        CompletedCommand cached = completed.get(cacheKey);
        if (cached != null) {
            if (!cached.command().equals(command)) {
                throw new IllegalArgumentException("requestId '" + command.requestId()
                        + "' was already used for a different command");
            }
            if (cached.response() == null) {
                return Optional.of(complete(cacheKey, plugin, command, cached.execution(), true));
            }
            HarnessResponse response = cached.response();
            return Optional.of(new HarnessResponse(response.protocolVersion(), response.requestId(),
                    response.pluginId(), response.success(), true, response.message(),
                    filterSnapshot(plugin.descriptor(), response.snapshot()), response.output()));
        }

        validateArguments(command, action);
        HarnessExecution durableReplay = plugin.replay(command).orElse(null);
        if (durableReplay != null) {
            return Optional.of(complete(cacheKey, plugin, command, durableReplay, true));
        }
        return Optional.empty();
    }

    private static String cacheKey(HarnessPlugin plugin, HarnessCommand command) {
        return plugin.descriptor().id() + "\u0000" + command.requestId();
    }

    private HarnessResponse complete(
            String cacheKey, HarnessPlugin plugin, HarnessCommand command,
            HarnessExecution execution, boolean replayed) {
        Objects.requireNonNull(execution, "Harness plugin returned no execution result");
        // Record the completed effect before accounting or observation: serializer,
        // sensor, or UI failures must not repeat an already performed mutation.
        cache(cacheKey, new CompletedCommand(command, execution, null, 0));
        long executionBytes = payloadSizer.size(command, execution);
        cache(cacheKey, new CompletedCommand(command, execution, null, executionBytes));
        HarnessResponse response = new HarnessResponse(
                HarnessResponse.PROTOCOL_VERSION, command.requestId(),
                plugin.descriptor().id(), execution.success(), replayed,
                execution.message(), observePlugin(plugin), execution.output());
        long outputBytes = payloadSizer.combined(executionBytes, payloadSizer.size(response.snapshot()));
        cache(cacheKey, new CompletedCommand(command, execution, response, outputBytes));
        return response;
    }

    private void cache(String key, CompletedCommand entry) {
        // A receipt being completed must survive its own accounting/observation
        // retry even when it was inserted before other completed commands.
        CompletedCommand previous = completed.remove(key);
        completed.put(key, entry);
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

    private record CompletedCommand(HarnessCommand command, HarnessExecution execution,
                                    HarnessResponse response, long outputBytes) {
    }
}
