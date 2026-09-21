package com.flowerfarm.automation;

import com.flowerfarm.controller.HarnessController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HarnessCommandDeadlineTest {

    private static final String TOKEN = "command-deadline-test-token-1234";
    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private final Clock clock = mock(Clock.class);
    private final RecordingPlugin plugin = new RecordingPlugin();
    private final HarnessProperties properties = properties();
    private final HarnessGateway gateway = new HarnessGateway(
            new HarnessRegistry(List.of(plugin)), properties, clock);

    @ParameterizedTest
    @CsvSource({"read,-1", "read,0", "write,-1", "write,0"})
    void refusesExpiredOptionalDeadlinesBeforeExecuting(String action, long nanoseconds) {
        when(clock.instant()).thenReturn(NOW);
        HarnessCommand expired = command("expired", action, NOW.plusNanos(nanoseconds));

        assertThatThrownBy(() -> gateway.execute(TOKEN, "deadline", expired))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command has expired");
        assertThat(plugin.commands).isEmpty();

        // A deadline refusal has no effect and does not reserve the request ID.
        HarnessResponse accepted = gateway.execute(TOKEN, "deadline",
                command("expired", action, NOW.plusSeconds(60)));
        assertThat(accepted.success()).isTrue();
        assertThat(accepted.replayed()).isFalse();
        assertThat(plugin.commands).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "write"})
    void acceptsNonphysicalCommandsWithoutDeadlines(String action) {
        HarnessCommand withoutDeadline = command("optional", action, null);

        assertThat(gateway.execute(TOKEN, "deadline", withoutDeadline).success()).isTrue();
        assertThat(plugin.commands).containsExactly(withoutDeadline);
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "write"})
    void optionalDeadlinesAreNotLimitedToPhysicalThirtySecondWindow(String action) {
        when(clock.instant()).thenReturn(NOW);
        HarnessCommand future = command("future", action, NOW.plusSeconds(3600));

        assertThat(gateway.execute(TOKEN, "deadline", future).success()).isTrue();
        assertThat(plugin.commands).containsExactly(future);
    }

    @Test
    void completedWriteReplaysAfterExpiryAndMutationPolicyRevocation() {
        when(clock.instant()).thenReturn(NOW);
        HarnessCommand write = command("completed", "write", NOW.plusSeconds(1));
        HarnessResponse original = gateway.execute(TOKEN, "deadline", write);
        when(clock.instant()).thenReturn(NOW.plusSeconds(2));
        properties.setAllowMutations(false);

        HarnessResponse replay = gateway.execute(TOKEN, "deadline", write);

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.output()).isEqualTo(original.output());
        assertThat(gateway.replay(TOKEN, "deadline", write)).isPresent();
        assertThat(plugin.commands).containsExactly(write);
        assertThatThrownBy(() -> gateway.execute(TOKEN, "deadline",
                command("new-expired", "write", write.expiresAt())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> gateway.execute(TOKEN, "deadline",
                command("new-fresh", "write", NOW.plusSeconds(60))))
                .isInstanceOf(HarnessPolicyException.class);
        assertThat(plugin.commands).containsExactly(write);
    }

    @Test
    void physicalActionsStillRequireDeadlineAndRejectMoreThanThirtySeconds() {
        when(clock.instant()).thenReturn(NOW);

        assertThatThrownBy(() -> gateway.execute(TOKEN, "deadline",
                command("missing", "move", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires expiresAt");
        assertThatThrownBy(() -> gateway.execute(TOKEN, "deadline",
                command("too-far", "move", NOW.plusSeconds(30).plusNanos(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("within 30 seconds");
        assertThat(plugin.commands).isEmpty();

        HarnessCommand allowed = command("limit", "move", NOW.plusSeconds(30));
        assertThat(gateway.execute(TOKEN, "deadline", allowed).success()).isTrue();
        assertThat(plugin.commands).containsExactly(allowed);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void physicalDeadlineMustStillBeStrictlyAfterNow(long nanoseconds) {
        when(clock.instant()).thenReturn(NOW);

        assertThatThrownBy(() -> gateway.execute(TOKEN, "deadline",
                command("expired-physical", "move", NOW.plusNanos(nanoseconds))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
        assertThat(plugin.commands).isEmpty();
    }

    @Test
    void restRefusesExpiredWriteAndAcceptsNextFreshRequest() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        MockMvc mvc = standaloneSetup(new HarnessController(gateway)).build();

        mvc.perform(post("/api/harness/deadline/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"rest","action":"write","arguments":{},
                                 "expiresAt":"2026-09-21T12:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Action 'write' command has expired"));
        assertThat(plugin.commands).isEmpty();

        mvc.perform(post("/api/harness/deadline/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"rest","action":"write","arguments":{},
                                 "expiresAt":"2026-09-21T13:00:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.replayed").value(false));
        assertThat(plugin.commands).containsExactly(
                command("rest", "write", NOW.plusSeconds(3600)));
    }

    private static HarnessCommand command(String requestId, String action, Instant deadline) {
        return new HarnessCommand(requestId, action, Map.of(), deadline);
    }

    private static HarnessProperties properties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        properties.setAllowPhysicalActions(true);
        properties.validateConfiguration();
        return properties;
    }

    private static final class RecordingPlugin implements HarnessPlugin {
        private final List<HarnessCommand> commands = new ArrayList<>();

        @Override
        public HarnessPluginDescriptor descriptor() {
            return new HarnessPluginDescriptor("deadline", "Deadline fixture",
                    "Records effects to prove deadline refusal happens before execution.", "1.0",
                    HarnessPluginKind.ROBOT,
                    Set.of(HarnessPluginCapability.OBSERVATION, HarnessPluginCapability.COMMANDS,
                            HarnessPluginCapability.PHYSICAL_ACTUATION),
                    List.of(new HarnessActionSpec("read", "Read current value", HarnessRisk.READ, List.of()),
                            new HarnessActionSpec("write", "Write a record", HarnessRisk.MUTATE, List.of()),
                            new HarnessActionSpec("move", "Move device", HarnessRisk.PHYSICAL, List.of())));
        }

        @Override
        public HarnessSnapshot observe() {
            return new HarnessSnapshot("deadline", NOW, List.of(), List.of(), Map.of());
        }

        @Override
        public HarnessExecution execute(HarnessCommand command) {
            commands.add(command);
            return HarnessExecution.success("Executed", Map.of("execution", commands.size()));
        }
    }
}
