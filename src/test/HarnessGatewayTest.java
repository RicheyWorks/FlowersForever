package com.flowerfarm.automation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Provider-neutral automation harness gateway")
class HarnessGatewayTest {

    private static final String TOKEN = "local-ai-harness-token-123456";

    @Test
    @DisplayName("requires an explicit strong local token even for discovery")
    void requiresToken() {
        HarnessProperties properties = properties();
        HarnessGateway gateway = gateway(new FakePlugin(), properties);

        assertThatThrownBy(() -> gateway.discover("wrong"))
                .isInstanceOf(HarnessAuthenticationException.class);
        assertThat(gateway.discover(TOKEN))
                .extracting(HarnessPluginDescriptor::id)
                .containsExactly("fake");

        HarnessManifest manifest = gateway.manifest(TOKEN);
        assertThat(manifest.protocolVersion()).isEqualTo("1.0");
        assertThat(manifest.replayCacheLimit()).isEqualTo(256);
        assertThat(manifest.replayCacheByteLimit()).isEqualTo(8L * 1024 * 1024);
        assertThat(manifest.strictArguments()).isTrue();
        assertThat(manifest.physicalCommandMaxTtlSeconds()).isEqualTo(30);
        assertThat(manifest.policy())
                .containsEntry(HarnessRisk.READ, true)
                .containsEntry(HarnessRisk.SENSITIVE_READ, false)
                .containsEntry(HarnessRisk.NAVIGATE, true)
                .containsEntry(HarnessRisk.DRAFT, false)
                .containsEntry(HarnessRisk.DESTRUCTIVE, false)
                .containsEntry(HarnessRisk.PHYSICAL, false);
        assertThat(manifest.plugins().get(0).kind())
                .isEqualTo(HarnessPluginKind.APPLICATION);
        assertThat(manifest.plugins().get(0).capabilities())
                .containsExactlyInAnyOrder(HarnessPluginCapability.OBSERVATION,
                        HarnessPluginCapability.COMMANDS);
        assertThat(manifest.tools())
                .extracting(HarnessToolDefinition::name)
                .contains("fake__navigate", "fake__with-argument");
        HarnessToolDefinition typed = manifest.tools().stream()
                .filter(tool -> tool.action().equals("integer-argument"))
                .findFirst().orElseThrow();
        assertThat(typed.inputSchema())
                .containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        assertThat(typed.allowed()).isTrue();
        assertThat(typed.annotations().idempotentHint()).isTrue();
        assertThat(typed.annotations().physicalWorldHint()).isFalse();
        HarnessToolDefinition rows = manifest.tools().stream()
                .filter(tool -> tool.action().equals("integer-array"))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> schemaProperties = (Map<String, Object>)
                rows.inputSchema().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> rowsSchema = (Map<String, Object>)
                schemaProperties.get("rows");
        assertThat(rowsSchema).containsEntry("items", Map.of("type", "integer"));
    }

    @Test
    @DisplayName("discovers robot sensing and requires policy plus a short-lived command")
    void protectsPhysicalWorldActions() {
        RobotPlugin robot = new RobotPlugin();
        HarnessProperties properties = properties();
        HarnessGateway gateway = gateway(robot, properties);

        HarnessManifest manifest = gateway.manifest(TOKEN);
        HarnessPluginDescriptor descriptor = manifest.plugins().get(0);
        assertThat(descriptor.kind()).isEqualTo(HarnessPluginKind.ROBOT);
        assertThat(descriptor.capabilities()).contains(
                HarnessPluginCapability.PHYSICAL_SENSING,
                HarnessPluginCapability.PHYSICAL_ACTUATION,
                HarnessPluginCapability.EMERGENCY_STOP);
        assertThat(descriptor.observations())
                .extracting(HarnessObservationSpec::name)
                .containsExactly("battery-percent", "pose", "camera-frame");
        assertThat(gateway.observe(TOKEN, "robot").metadata())
                .containsKeys("battery-percent", "pose")
                .doesNotContainKey("camera-frame");
        properties.setAllowSensitiveRead(true);
        assertThat(gateway.observe(TOKEN, "robot").metadata())
                .containsEntry("camera-frame", "redacted-test-frame");
        assertThatThrownBy(() -> gateway(new InvalidSignalPlugin(), properties())
                .observe(TOKEN, "bad-sensor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be a JSON number");
        assertThatThrownBy(() -> gateway(new UndeclaredSignalPlugin(), properties())
                .observe(TOKEN, "extra-sensor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undeclared physical signal 'private-frame'");

        HarnessToolDefinition move = manifest.tools().get(0);
        assertThat(move.allowed()).isFalse();
        assertThat(move.annotations().physicalWorldHint()).isTrue();
        assertThat(move.annotations().freshnessRequired()).isTrue();
        assertThat(move.annotations().openWorldHint()).isTrue();
        assertThat(move.annotations().destructiveHint()).isTrue();

        Instant deadline = Instant.now().plusSeconds(10);
        HarnessCommand blocked = new HarnessCommand("robot-blocked", "move-to",
                Map.of("x", 1.5, "y", 2.0), deadline);
        assertThatThrownBy(() -> gateway.execute(TOKEN, "robot", blocked))
                .isInstanceOf(HarnessPolicyException.class)
                .hasMessageContaining("PHYSICAL");

        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        properties.setAllowPhysicalActions(true);
        properties.validateConfiguration();

        HarnessCommand noDeadline = new HarnessCommand("robot-no-deadline", "move-to",
                Map.of("x", 1.5, "y", 2.0));
        assertThatThrownBy(() -> gateway.execute(TOKEN, "robot", noDeadline))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires expiresAt");
        HarnessCommand expired = new HarnessCommand("robot-expired", "move-to",
                Map.of("x", 1.5, "y", 2.0), Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> gateway.execute(TOKEN, "robot", expired))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        HarnessCommand tooFar = new HarnessCommand("robot-too-far", "move-to",
                Map.of("x", 1.5, "y", 2.0), Instant.now().plusSeconds(31));
        assertThatThrownBy(() -> gateway.execute(TOKEN, "robot", tooFar))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("within 30 seconds");

        HarnessCommand moveCommand = new HarnessCommand("robot-move", "move-to",
                Map.of("x", 1.5, "y", 2.0), Instant.now().plusSeconds(10));
        assertThat(gateway.execute(TOKEN, "robot", moveCommand).replayed()).isFalse();
        assertThat(gateway.execute(TOKEN, "robot", moveCommand).replayed()).isTrue();
        assertThat(robot.executions).hasValue(1);
    }

    @Test
    @DisplayName("enforces plugin-declared risk instead of caller-provided claims")
    void enforcesPluginRisk() {
        HarnessProperties properties = properties();
        HarnessGateway gateway = gateway(new FakePlugin(), properties);

        HarnessCommand draft = command("draft-1", "draft");
        HarnessCommand sensitive = command("sensitive-1", "sensitive");
        HarnessCommand destructive = command("destroy-1", "destroy");
        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", draft))
                .isInstanceOf(HarnessPolicyException.class)
                .hasMessageContaining("DRAFT");
        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", destructive))
                .isInstanceOf(HarnessPolicyException.class)
                .hasMessageContaining("DESTRUCTIVE");
        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", sensitive))
                .isInstanceOf(HarnessPolicyException.class)
                .hasMessageContaining("SENSITIVE_READ");

        properties.setAllowSensitiveRead(true);
        assertThat(gateway.execute(TOKEN, "fake", sensitive).success()).isTrue();
        properties.setAllowDrafts(true);
        assertThat(gateway.execute(TOKEN, "fake", draft).success()).isTrue();
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        assertThat(gateway.execute(TOKEN, "fake", destructive).success()).isTrue();
    }

    @Test
    @DisplayName("replays a completed request id without executing it twice")
    void preventsDuplicateExecutionOnRetry() {
        FakePlugin plugin = new FakePlugin();
        HarnessGateway gateway = gateway(plugin, properties());
        HarnessCommand command = command("same-request", "navigate");

        HarnessResponse first = gateway.execute(TOKEN, "fake", command);
        HarnessResponse replay = gateway.execute(TOKEN, "fake", command);

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.message()).isEqualTo(first.message());
        assertThat(plugin.executions).hasValue(1);

        HarnessCommand collision = command("same-request", "draft");
        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", collision))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already used for a different command");
        assertThat(plugin.executions).hasValue(1);
    }

    @Test
    void doesNotRepeatACompletedEffectWhenPostCommandObservationFails() {
        FakePlugin delegate = new FakePlugin();
        AtomicBoolean observationAvailable = new AtomicBoolean(false);
        HarnessPlugin plugin = new HarnessPlugin() {
            @Override public HarnessPluginDescriptor descriptor() {
                return delegate.descriptor();
            }

            @Override public HarnessSnapshot observe() {
                if (!observationAvailable.get()) {
                    throw new IllegalStateException("Temporary observation failure");
                }
                return delegate.observe();
            }

            @Override public HarnessExecution execute(HarnessCommand command) {
                return delegate.execute(command);
            }
        };
        HarnessGateway gateway = gateway(plugin, properties());
        HarnessCommand command = command("observation-retry", "navigate");

        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", command))
                .hasMessageContaining("Temporary observation failure");
        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", command))
                .hasMessageContaining("Temporary observation failure");
        observationAvailable.set(true);

        assertThat(gateway.execute(TOKEN, "fake", command).replayed()).isTrue();
        assertThat(delegate.executions).hasValue(1);
    }

    @Test
    void revokingSensitiveReadAlsoProtectsCachedResults() {
        HarnessProperties properties = properties();
        properties.setAllowSensitiveRead(true);
        HarnessGateway gateway = gateway(new FakePlugin(), properties);
        HarnessCommand sensitive = command("sensitive-cached", "sensitive");
        gateway.execute(TOKEN, "fake", sensitive);
        properties.setAllowSensitiveRead(false);

        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", sensitive))
                .isInstanceOf(HarnessPolicyException.class);
    }

    @Test
    void retainsNewestOversizedReceiptButAccountsForItsCommandPayload() {
        FakePlugin plugin = new FakePlugin();
        HarnessGateway gateway = gateway(plugin, properties());
        HarnessCommand large = new HarnessCommand("large-input", "with-argument",
                Map.of("selector", "x".repeat((int) HarnessGateway.REPLAY_CACHE_BYTE_LIMIT + 1)));
        gateway.execute(TOKEN, "fake", large);
        assertThat(gateway.execute(TOKEN, "fake", large).replayed()).isTrue();

        gateway.execute(TOKEN, "fake", command("newer-small", "navigate"));
        assertThat(gateway.execute(TOKEN, "fake", large).replayed()).isFalse();
        assertThat(plugin.executions).hasValue(3);
    }

    @Test
    void redactsCachedPhysicalObservationWhileKeepingReceiptReplayAvailable() {
        HarnessProperties properties = properties();
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        properties.setAllowPhysicalActions(true);
        properties.setAllowSensitiveRead(true);
        RobotPlugin robot = new RobotPlugin();
        HarnessGateway gateway = gateway(robot, properties);
        HarnessCommand command = new HarnessCommand("redacted-replay", "move-to",
                Map.of("x", 1.5, "y", 2.0), Instant.now().plusSeconds(10));
        assertThat(gateway.execute(TOKEN, "robot", command).snapshot().metadata())
                .containsKey("camera-frame");

        properties.setAllowSensitiveRead(false);
        properties.setAllowPhysicalActions(false);
        HarnessResponse replay = gateway.execute(TOKEN, "robot", command);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.snapshot().metadata()).doesNotContainKey("camera-frame");
        assertThat(robot.executions).hasValue(1);
    }

    @Test
    @DisplayName("fails closed on weak tokens and unsafe destructive configuration")
    void validatesFailClosedConfiguration() {
        HarnessProperties weak = new HarnessProperties();
        weak.setEnabled(true);
        weak.setToken("short");
        assertThatThrownBy(weak::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("24 characters");

        HarnessProperties unsafe = properties();
        unsafe.setAllowDestructive(true);
        assertThatThrownBy(unsafe::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allow-mutations");

        HarnessProperties unsafePhysical = properties();
        unsafePhysical.setAllowPhysicalActions(true);
        assertThatThrownBy(unsafePhysical::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allow-destructive");
    }

    @Test
    @DisplayName("rejects duplicate plugin ids and missing required arguments")
    void validatesPluginsAndCommands() {
        FakePlugin plugin = new FakePlugin();
        assertThatThrownBy(() -> new HarnessRegistry(List.of(plugin, plugin)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
        assertThatThrownBy(() -> new HarnessPluginDescriptor(
                "unsafe_plugin", "Unsafe", "Invalid id", "1.0", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
        assertThatThrownBy(() -> new HarnessActionSpec(
                "this-action-name-is-over-thirty-chars", "Invalid action",
                HarnessRisk.READ, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1-30");
        HarnessActionSpec physical = new HarnessActionSpec(
                "move", "Move hardware", HarnessRisk.PHYSICAL, List.of());
        assertThatThrownBy(() -> new HarnessPluginDescriptor(
                "bad-robot", "Bad robot", "Missing physical capability", "1.0",
                HarnessPluginKind.ROBOT, Set.of(HarnessPluginCapability.COMMANDS),
                List.of(physical)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PHYSICAL_ACTUATION");

        HarnessGateway gateway = gateway(plugin, properties());
        HarnessCommand missing = new HarnessCommand(
                "missing-argument", "with-argument", Map.of());
        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires argument 'selector'");

        HarnessCommand wrongType = new HarnessCommand(
                "wrong-type", "integer-argument", Map.of("row", "1"));
        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", wrongType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a JSON integer");

        HarnessCommand unknown = new HarnessCommand(
                "unknown-argument", "navigate", Map.of("typo", "value"));
        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not accept argument 'typo'");

        HarnessCommand wrongArrayItem = new HarnessCommand(
                "wrong-array-item", "integer-array", Map.of("rows", List.of(1, "2")));
        assertThatThrownBy(() -> gateway.execute(TOKEN, "fake", wrongArrayItem))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("items must be JSON integer values");
        assertThatThrownBy(() -> new HarnessArgumentSpec(
                "bad", HarnessValueType.STRING, "Bad item type", true,
                HarnessValueType.INTEGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only for array");

        assertThatThrownBy(() -> new HarnessResponse("2.0", "version", "fake",
                true, false, "", null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported harness protocol version");
        assertThatThrownBy(() -> new HarnessManifest(
                "2.0", 1, true, Map.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported harness protocol version");
    }

    private static HarnessCommand command(String requestId, String action) {
        return new HarnessCommand(requestId, action, Map.of());
    }

    private static HarnessGateway gateway(
            HarnessPlugin plugin, HarnessProperties properties) {
        return new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties);
    }

    private static HarnessProperties properties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.validateConfiguration();
        return properties;
    }

    private static final class FakePlugin implements HarnessPlugin {
        private final AtomicInteger executions = new AtomicInteger();
        private final HarnessPluginDescriptor descriptor = new HarnessPluginDescriptor(
                "fake", "Fake", "Test plugin", "1.0",
                List.of(
                        new HarnessActionSpec("navigate", "Navigate", HarnessRisk.NAVIGATE,
                                Set.of()),
                        new HarnessActionSpec("draft", "Draft", HarnessRisk.DRAFT, Set.of()),
                        new HarnessActionSpec("sensitive", "Sensitive read",
                                HarnessRisk.SENSITIVE_READ, Set.of()),
                        new HarnessActionSpec("destroy", "Destroy", HarnessRisk.DESTRUCTIVE,
                                Set.of()),
                        new HarnessActionSpec("with-argument", "Argument",
                                HarnessRisk.NAVIGATE, Set.of("selector")),
                        new HarnessActionSpec("integer-argument", "Integer argument",
                                HarnessRisk.NAVIGATE, List.of(new HarnessArgumentSpec(
                                "row", HarnessValueType.INTEGER,
                                "Zero-based row.", true))),
                        new HarnessActionSpec("integer-array", "Integer array",
                                HarnessRisk.NAVIGATE, List.of(new HarnessArgumentSpec(
                                "rows", HarnessValueType.ARRAY,
                                "Zero-based rows.", true, HarnessValueType.INTEGER)))));

        @Override public HarnessPluginDescriptor descriptor() {
            return descriptor;
        }

        @Override public HarnessSnapshot observe() {
            return new HarnessSnapshot("fake", Instant.now(), List.of("test"), List.of(),
                    Map.of("ready", true));
        }

        @Override public HarnessExecution execute(HarnessCommand command) {
            executions.incrementAndGet();
            return HarnessExecution.success("executed " + command.action());
        }
    }

    private static final class RobotPlugin implements HarnessPlugin {
        private final AtomicInteger executions = new AtomicInteger();
        private final HarnessPluginDescriptor descriptor = new HarnessPluginDescriptor(
                "robot", "Future field robot", "Test robot contract", "0.1",
                HarnessPluginKind.ROBOT,
                Set.of(HarnessPluginCapability.OBSERVATION,
                        HarnessPluginCapability.COMMANDS,
                        HarnessPluginCapability.PHYSICAL_SENSING,
                        HarnessPluginCapability.PHYSICAL_ACTUATION,
                        HarnessPluginCapability.EMERGENCY_STOP),
                List.of(
                        new HarnessObservationSpec("battery-percent",
                                HarnessValueType.NUMBER, "Remaining battery charge.",
                                "%", false),
                        new HarnessObservationSpec("pose", HarnessValueType.OBJECT,
                                "Current robot pose in its declared frame.", null, false),
                        new HarnessObservationSpec("camera-frame",
                                HarnessValueType.STRING, "Latest encoded camera frame.",
                                null, true)),
                List.of(new HarnessActionSpec("move-to", "Move to a reviewed pose.",
                        HarnessRisk.PHYSICAL, List.of(
                        new HarnessArgumentSpec("x", HarnessValueType.NUMBER,
                                "Target X coordinate.", true),
                        new HarnessArgumentSpec("y", HarnessValueType.NUMBER,
                                "Target Y coordinate.", true)))));

        @Override public HarnessPluginDescriptor descriptor() {
            return descriptor;
        }

        @Override public HarnessSnapshot observe() {
            return new HarnessSnapshot("robot", Instant.now(), List.of(), List.of(),
                    Map.of("battery-percent", 82.0,
                            "pose", Map.of("x", 0.0, "y", 0.0),
                            "camera-frame", "redacted-test-frame"));
        }

        @Override public HarnessExecution execute(HarnessCommand command) {
            executions.incrementAndGet();
            return HarnessExecution.success("accepted physical command");
        }
    }

    private static final class InvalidSignalPlugin implements HarnessPlugin {
        @Override public HarnessPluginDescriptor descriptor() {
            return new HarnessPluginDescriptor("bad-sensor", "Bad sensor",
                    "Publishes an invalid signal for contract testing.", "0.1",
                    HarnessPluginKind.DEVICE,
                    Set.of(HarnessPluginCapability.OBSERVATION,
                            HarnessPluginCapability.PHYSICAL_SENSING),
                    List.of(new HarnessObservationSpec("temperature",
                            HarnessValueType.NUMBER, "Temperature.", "C", false)),
                    List.of());
        }

        @Override public HarnessSnapshot observe() {
            return new HarnessSnapshot("bad-sensor", Instant.now(), List.of(), List.of(),
                    Map.of("temperature", "warm"));
        }

        @Override public HarnessExecution execute(HarnessCommand command) {
            throw new IllegalArgumentException("No commands supported");
        }
    }

    private static final class UndeclaredSignalPlugin implements HarnessPlugin {
        @Override public HarnessPluginDescriptor descriptor() {
            return new HarnessPluginDescriptor("extra-sensor", "Extra sensor",
                    "Publishes an undeclared signal for contract testing.", "0.1",
                    HarnessPluginKind.DEVICE,
                    Set.of(HarnessPluginCapability.OBSERVATION,
                            HarnessPluginCapability.PHYSICAL_SENSING),
                    List.of(new HarnessObservationSpec("temperature",
                            HarnessValueType.NUMBER, "Temperature.", "C", false)),
                    List.of());
        }

        @Override public HarnessSnapshot observe() {
            return new HarnessSnapshot("extra-sensor", Instant.now(), List.of(), List.of(),
                    Map.of("temperature", 20.0, "private-frame", "must-not-pass"));
        }

        @Override public HarnessExecution execute(HarnessCommand command) {
            throw new IllegalArgumentException("No commands supported");
        }
    }
}
