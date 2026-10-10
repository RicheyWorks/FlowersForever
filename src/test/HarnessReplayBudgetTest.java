package com.flowerfarm.automation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.flowerfarm.model.Item;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarnessReplayBudgetTest {
    private static final String TOKEN = "local-replay-budget-test-token";
    private static final String ID = "payload-fixture";
    private static final int OVERSIZED_LENGTH = (int) HarnessGateway.REPLAY_CACHE_BYTE_LIMIT + 1;

    @Test
    void evictsRealInventoryPagesByTheirSerializedPayloadBeforeTheCommandLimit() {
        PayloadPlugin plugin = new PayloadPlugin(command -> {
            List<Item> records = new ArrayList<>();
            for (int index = 0; index < 200; index++) {
                records.add(new Item("Dahlia " + index, "Flowers", 1, "stem", 0, 1,
                        "n".repeat(2_000)));
            }
            return HarnessExecution.success("Read inventory page", Map.of("records", records));
        });
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties());

        for (int index = 0; index < 25; index++) {
            run(gateway, "page-" + index);
        }

        assertThat(run(gateway, "page-24").replayed()).isTrue();
        assertThat(plugin.executions).hasValue(25);
        assertThat(run(gateway, "page-0").replayed()).isFalse();
        assertThat(plugin.executions).hasValue(26);
    }

    @Test
    void usesTheApplicationsCustomSerializerWithoutChangingOutputObjects() {
        AtomicInteger serializations = new AtomicInteger();
        ObjectMapper mapper = mapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(CompactPayload.class, new StdSerializer<>(CompactPayload.class) {
            @Override public void serialize(CompactPayload value, JsonGenerator generator,
                                            SerializerProvider serializers) throws IOException {
                serializations.incrementAndGet();
                generator.writeString("compact public representation");
            }
        });
        mapper.registerModule(module);
        CompactPayload original = new CompactPayload("x".repeat(OVERSIZED_LENGTH));
        PayloadPlugin plugin = new PayloadPlugin(command -> HarnessExecution.success("Read record",
                command.requestId().equals("custom") ? Map.of("record", original) : Map.of()));
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties(), mapper);

        HarnessResponse first = run(gateway, "custom");
        run(gateway, "newer");

        assertThat(first.output().get("record")).isSameAs(original);
        assertThat(run(gateway, "custom").replayed()).isTrue();
        assertThat(plugin.executions).hasValue(2);
        assertThat(serializations.get()).isGreaterThan(0);
    }

    @Test
    void stopsSerializingOnceThePayloadExceedsTheBudget() {
        AtomicInteger serializedChunks = new AtomicInteger();
        ObjectMapper mapper = mapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(ExpandedPayload.class, new StdSerializer<>(ExpandedPayload.class) {
            @Override public void serialize(ExpandedPayload value, JsonGenerator generator,
                                            SerializerProvider serializers) throws IOException {
                generator.writeStartArray();
                for (int index = 0; index < 10_000; index++) {
                    serializedChunks.incrementAndGet();
                    generator.writeString("x".repeat(256));
                }
                generator.writeEndArray();
            }
        });
        mapper.registerModule(module);

        long weight = new HarnessPayloadSizer(mapper, 1_024).size(new ExpandedPayload());

        assertThat(weight).isEqualTo(1_025);
        assertThat(serializedChunks.get()).isBetween(1, 9_999);
    }

    @Test
    void countsUtf8BytesRatherThanCharacters() throws Exception {
        ObjectMapper mapper = mapper();
        Map<String, Object> value = Map.of("label", "農場 🌻");
        long actualBytes = mapper.writeValueAsBytes(new Object[]{value}).length;

        assertThat(new HarnessPayloadSizer(mapper, 1_024).size(value)).isEqualTo(actualBytes);
        assertThat(actualBytes).isGreaterThan(mapper.writeValueAsString(new Object[]{value}).length());
    }

    @Test
    void retainsTheNewestUnreadableReceiptAndDoesNotRepeatItsMutation() {
        ObjectMapper mapper = mapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(UnreadablePayload.class, new StdSerializer<>(UnreadablePayload.class) {
            @Override public void serialize(UnreadablePayload value, JsonGenerator generator,
                                            SerializerProvider serializers) throws IOException {
                throw new IOException("Serializer unavailable after mutation");
            }
        });
        mapper.registerModule(module);
        PayloadPlugin plugin = new PayloadPlugin(command -> HarnessExecution.success("Mutation saved",
                Map.of("receipt", new UnreadablePayload())));
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties(), mapper);

        HarnessResponse saved = run(gateway, "mutation");
        assertThat(saved.success()).isTrue();
        assertThatThrownBy(() -> mapper.writeValueAsString(saved))
                .hasMessageContaining("Serializer unavailable");

        HarnessResponse replay = run(gateway, "mutation");
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output().get("receipt")).isSameAs(saved.output().get("receipt"));
        assertThat(plugin.executions).hasValue(1);
    }

    @Test
    void treatsSerializationFailureAsOversizedForSubsequentEviction() {
        ObjectMapper mapper = mapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(UnreadablePayload.class, new StdSerializer<>(UnreadablePayload.class) {
            @Override public void serialize(UnreadablePayload value, JsonGenerator generator,
                                            SerializerProvider serializers) throws IOException {
                throw new IOException("Unreadable receipt");
            }
        });
        mapper.registerModule(module);
        PayloadPlugin plugin = new PayloadPlugin(command -> HarnessExecution.success("Saved",
                command.requestId().equals("unreadable")
                        ? Map.of("record", new UnreadablePayload()) : Map.of()));
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties(), mapper);

        run(gateway, "unreadable");
        assertThat(run(gateway, "unreadable").replayed()).isTrue();
        run(gateway, "newer-small");

        assertThat(run(gateway, "unreadable").replayed()).isFalse();
        assertThat(plugin.executions).hasValue(3);
    }

    @Test
    void keepsTheEffectReceiptWhenAccountingAndObservationBothFail() {
        AtomicBoolean observationAvailable = new AtomicBoolean(false);
        PayloadPlugin plugin = new PayloadPlugin(command -> HarnessExecution.success("Saved",
                Map.of("record", new Object())), command -> {
            if (!observationAvailable.get()) {
                throw new IllegalStateException("Observation unavailable");
            }
            return emptySnapshot();
        });
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties());

        assertThatThrownBy(() -> run(gateway, "mutation"))
                .hasMessageContaining("Observation unavailable");
        assertThatThrownBy(() -> run(gateway, "mutation"))
                .hasMessageContaining("Observation unavailable");
        observationAvailable.set(true);

        assertThat(run(gateway, "mutation").replayed()).isTrue();
        assertThat(plugin.executions).hasValue(1);
    }

    @Test
    void retainsAnOlderIncompleteReceiptWhenRetryAccountingFails() {
        AtomicBoolean serializerFails = new AtomicBoolean(false);
        AtomicBoolean observationAvailable = new AtomicBoolean(false);
        AtomicInteger mutations = new AtomicInteger();
        ObjectMapper mapper = mapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(UnreadablePayload.class, new StdSerializer<>(UnreadablePayload.class) {
            @Override public void serialize(UnreadablePayload value, JsonGenerator generator,
                                            SerializerProvider serializers) throws IOException {
                if (serializerFails.get()) {
                    throw new IOException("Receipt serializer became unavailable");
                }
                generator.writeString("Small saved receipt");
            }
        });
        mapper.registerModule(module);
        PayloadPlugin plugin = new PayloadPlugin(command -> {
            if (command.requestId().equals("mutation")) {
                mutations.incrementAndGet();
                return HarnessExecution.success("Mutation saved", Map.of("receipt", new UnreadablePayload()));
            }
            return HarnessExecution.success("Newer small result");
        }, ignored -> {
            if (!observationAvailable.get()) {
                throw new IllegalStateException("Observation unavailable");
            }
            return emptySnapshot();
        });
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties(), mapper);

        assertThatThrownBy(() -> run(gateway, "mutation"))
                .hasMessageContaining("Observation unavailable");
        observationAvailable.set(true);
        run(gateway, "newer-small");
        observationAvailable.set(false);
        serializerFails.set(true);
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> run(gateway, "mutation"))
                    .hasMessageContaining("Observation unavailable");
        }
        observationAvailable.set(true);
        serializerFails.set(false);

        assertThat(run(gateway, "mutation").replayed()).isTrue();
        assertThat(mutations).hasValue(1);
        assertThat(plugin.executions).hasValue(2);
    }

    @Test
    void retainsAnOlderReceiptCompletedWithAnOversizedSnapshot() {
        AtomicBoolean observationAvailable = new AtomicBoolean(false);
        AtomicBoolean largeSnapshot = new AtomicBoolean(false);
        AtomicInteger mutations = new AtomicInteger();
        HarnessSnapshot oversized = new HarnessSnapshot(ID, Instant.now(), List.of(), List.of(),
                Map.of("readout", "s".repeat(OVERSIZED_LENGTH)));
        PayloadPlugin plugin = new PayloadPlugin(command -> {
            if (command.requestId().equals("mutation")) {
                mutations.incrementAndGet();
            }
            return HarnessExecution.success("Saved");
        }, ignored -> {
            if (!observationAvailable.get()) {
                throw new IllegalStateException("Observation unavailable");
            }
            return largeSnapshot.get() ? oversized : emptySnapshot();
        });
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties());

        assertThatThrownBy(() -> run(gateway, "mutation"))
                .hasMessageContaining("Observation unavailable");
        observationAvailable.set(true);
        run(gateway, "newer-small");
        largeSnapshot.set(true);

        HarnessResponse recovered = run(gateway, "mutation");
        assertThat(recovered.replayed()).isTrue();
        assertThat(recovered.snapshot().metadata().get("readout")).isEqualTo(oversized.metadata().get("readout"));
        assertThat(run(gateway, "mutation").replayed()).isTrue();
        assertThat(mutations).hasValue(1);
        assertThat(plugin.executions).hasValue(2);
    }

    @Test
    void includesExecutionMessagesInTheReplayBudget() {
        String largeMessage = "m".repeat(OVERSIZED_LENGTH);
        PayloadPlugin plugin = new PayloadPlugin(command -> HarnessExecution.success(
                command.requestId().equals("large") ? largeMessage : "Small result"));
        assertLargeReceiptIsEvictedByNextCommand(plugin);
    }

    @Test
    void ordinaryResponseReplayDoesNotRefreshItsEvictionPosition() {
        PayloadPlugin plugin = new PayloadPlugin(command -> HarnessExecution.success("Saved"));
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties());
        for (int index = 0; index < HarnessGateway.REPLAY_CACHE_LIMIT; index++) {
            run(gateway, "receipt-" + index);
        }

        assertThat(run(gateway, "receipt-0").replayed()).isTrue();
        run(gateway, "newest");

        assertThat(run(gateway, "receipt-1").replayed()).isTrue();
        assertThat(run(gateway, "receipt-0").replayed()).isFalse();
        assertThat(plugin.executions).hasValue(HarnessGateway.REPLAY_CACHE_LIMIT + 2);
    }

    @Test
    void includesSnapshotRoutesAndControlSelectorsInTheReplayBudget() {
        String largeLabel = "s".repeat(OVERSIZED_LENGTH);
        for (boolean routePayload : List.of(true, false)) {
            PayloadPlugin plugin = new PayloadPlugin(command -> HarnessExecution.success("Saved"), command -> {
                if (!command.requestId().equals("large")) {
                    return emptySnapshot();
                }
                return new HarnessSnapshot(ID, Instant.now(),
                        routePayload ? List.of(largeLabel) : List.of(),
                        routePayload ? List.of()
                                : List.of(new HarnessControl("button", largeLabel, true, true, true)),
                        Map.of());
            });
            assertLargeReceiptIsEvictedByNextCommand(plugin);
        }
    }

    @Test
    void fallbackConstructorsSupportTimeValuesAndRetainOrdinaryReceipts() {
        for (boolean explicitClock : List.of(false, true)) {
            PayloadPlugin plugin = new PayloadPlugin(command -> HarnessExecution.success("Read time",
                    Map.of("at", Instant.parse("2026-09-08T12:00:00Z"))));
            HarnessRegistry registry = new HarnessRegistry(List.of(plugin));
            HarnessGateway gateway = explicitClock
                    ? new HarnessGateway(registry, properties(),
                            Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC))
                    : new HarnessGateway(registry, properties());

            run(gateway, "first");
            run(gateway, "second");

            assertThat(run(gateway, "first").replayed()).isTrue();
            assertThat(plugin.executions).hasValue(2);
        }
    }

    private static void assertLargeReceiptIsEvictedByNextCommand(PayloadPlugin plugin) {
        HarnessGateway gateway = new HarnessGateway(new HarnessRegistry(List.of(plugin)), properties());
        run(gateway, "large");
        assertThat(run(gateway, "large").replayed()).isTrue();
        run(gateway, "small");
        assertThat(run(gateway, "large").replayed()).isFalse();
        assertThat(plugin.executions).hasValue(3);
    }

    private static HarnessResponse run(HarnessGateway gateway, String requestId) {
        return gateway.execute(TOKEN, ID, new HarnessCommand(requestId, "run", Map.of()));
    }

    private static HarnessProperties properties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowMutations(true);
        return properties;
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private static HarnessSnapshot emptySnapshot() {
        return new HarnessSnapshot(ID, Instant.now(), List.of(), List.of(), Map.of());
    }

    private record CompactPayload(String content) { }
    private record ExpandedPayload() { }
    private record UnreadablePayload() { }

    private static final class PayloadPlugin implements HarnessPlugin {
        private final AtomicInteger executions = new AtomicInteger();
        private final Function<HarnessCommand, HarnessExecution> result;
        private final Function<HarnessCommand, HarnessSnapshot> snapshot;
        private HarnessCommand lastCommand;

        private PayloadPlugin(Function<HarnessCommand, HarnessExecution> result) {
            this(result, ignored -> emptySnapshot());
        }

        private PayloadPlugin(Function<HarnessCommand, HarnessExecution> result,
                              Function<HarnessCommand, HarnessSnapshot> snapshot) {
            this.result = result;
            this.snapshot = snapshot;
        }

        @Override public HarnessPluginDescriptor descriptor() {
            return new HarnessPluginDescriptor(ID, "Payload fixture", "Replay budget fixture", "1",
                    List.of(new HarnessActionSpec("run", "Return a payload", HarnessRisk.MUTATE, List.of())));
        }

        @Override public HarnessSnapshot observe() {
            return snapshot.apply(lastCommand);
        }

        @Override public HarnessExecution execute(HarnessCommand command) {
            lastCommand = command;
            executions.incrementAndGet();
            return result.apply(command);
        }
    }
}
