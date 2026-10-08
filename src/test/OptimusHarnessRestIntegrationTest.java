package com.flowerfarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowerfarm.automation.optimus.OptimusHarnessPlugin;
import com.flowerfarm.automation.optimus.OptimusRobotAdapter;
import com.flowerfarm.automation.optimus.OptimusStagedPlanStore;
import com.flowerfarm.controller.HarnessController;
import com.flowerfarm.repository.RobotOperationEventJpaRepository;
import com.flowerfarm.repository.RobotControlLeaseJpaRepository;
import com.flowerfarm.repository.RobotStagedPlanJpaRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureTestRestTemplate
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.headless=true",
                "spring.datasource.url=jdbc:h2:mem:optimus-harness;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=validate",
                "flowerfarm.inventory.seed-csv=target/no-optimus-harness-seed.csv",
                "flowerfarm.irrigation.live-weather=false",
                "flowerfarm.harness.enabled=true",
                "flowerfarm.harness.token=optimus-rest-token-123456789",
                "flowerfarm.harness.allow-drafts=true",
                "flowerfarm.harness.allow-mutations=true",
                "flowerfarm.harness.allow-destructive=true",
                "flowerfarm.harness.allow-physical-actions=true",
                "flowerfarm.harness.optimus.enabled=true",
                "flowerfarm.harness.optimus.mode=simulator"
        })
@DisplayName("Optimus harness REST integration")
class OptimusHarnessRestIntegrationTest {

    private static final String TOKEN = "optimus-rest-token-123456789";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    RobotOperationEventJpaRepository operationEvents;

    @Autowired
    RobotControlLeaseJpaRepository controlLeases;

    @Autowired
    RobotStagedPlanJpaRepository stagedPlans;

    @Autowired
    OptimusStagedPlanStore stagedPlanStore;

    @Test
    void conditionallyClearsOnlyTheExactDurableExecutionClaim() {
        var identity = new OptimusRobotAdapter.RobotIdentity(OptimusRobotAdapter.AdapterMode.SIMULATOR,
                "cleanup-store-simulator", "Cleanup simulator", "test", "safe-home");
        var draft = new OptimusRobotAdapter.TaskDraft("Carry a tote", "packing", 60, List.of());
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        var first = new OptimusRobotAdapter.TaskPlan("first-reviewed-token", draft, now);
        stagedPlanStore.replace(identity, "cleanup-lease", first);
        var claimed = stagedPlanStore.claimExecution(identity, "cleanup-lease",
                first.planToken(), "first-execution", now);
        String firstFingerprint = OptimusRobotAdapter.planFingerprint(first.planToken());
        try {
            stagedPlanStore.clearExecution(identity, "different-request", firstFingerprint);
            stagedPlanStore.clearExecution(identity, "first-execution", "different-token");
            assertThat(stagedPlanStore.current(identity)).contains(claimed);

            stagedPlanStore.clearExecution(identity, "first-execution", firstFingerprint);
            assertThat(stagedPlanStore.current(identity)).isEmpty();

            var next = new OptimusRobotAdapter.TaskPlan("next-reviewed-token", draft, now);
            var reviewed = stagedPlanStore.replace(identity, "cleanup-lease", next);
            stagedPlanStore.clearExecution(identity, "first-execution", firstFingerprint);
            assertThat(stagedPlanStore.current(identity)).contains(reviewed);
            var nextClaim = stagedPlanStore.claimExecution(identity, "cleanup-lease",
                    next.planToken(), "next-execution", now);
            stagedPlanStore.clearExecution(identity, "first-execution",
                    OptimusRobotAdapter.planFingerprint(next.planToken()));
            assertThat(stagedPlanStore.current(identity)).contains(nextClaim);
        } finally {
            stagedPlanStore.clear(identity);
        }
    }

    @Test
    @DisplayName("publishes and executes the reviewed simulator flow over JSON")
    void executesReviewedSimulatorFlowOverJson() {
        ResponseEntity<JsonNode> manifest = rest.exchange(
                url("/api/harness/manifest"), HttpMethod.GET,
                new HttpEntity<>(headers()), JsonNode.class);
        assertThat(manifest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(manifest.getBody().path("plugins").toString())
                .contains(OptimusHarnessPlugin.ID, "ROBOT", "EMERGENCY_STOP");
        assertThat(manifest.getBody().path("tools").toString())
                .contains("tesla-optimus__stage-task",
                        "tesla-optimus__acquire-control-lease",
                        "tesla-optimus__renew-control-lease",
                        "tesla-optimus__release-control-lease",
                        "tesla-optimus__execute-staged-task",
                        "tesla-optimus__get-operation",
                        "tesla-optimus__list-operations",
                        "tesla-optimus__list-operation-events",
                        "tesla-optimus__cancel-active-task",
                        "\"freshnessRequired\":true");

        ResponseEntity<JsonNode> observation = rest.exchange(
                url("/api/harness/tesla-optimus/observe"), HttpMethod.GET,
                new HttpEntity<>(headers()), JsonNode.class);
        assertThat(observation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(observation.getBody().path("metadata").path("connection-state").asText())
                .isEqualTo("SIMULATED");
        assertThat(observation.getBody().path("metadata").path("simulated").asBoolean())
                .isTrue();
        assertThat(observation.getBody().path("metadata").path("task-status").asText())
                .isEqualTo("IDLE");
        assertThat(observation.getBody().path("metadata").path("operational").asBoolean())
                .isTrue();
        assertThat(observation.getBody().path("metadata").path("staged-plan").asBoolean())
                .isFalse();
        assertThat(observation.getBody().path("metadata")
                .path("staged-plan-adapter-confirmed").asBoolean()).isFalse();
        assertThat(observation.getBody().path("metadata")
                .path("control-lease-active").asBoolean()).isFalse();
        assertThat(observation.getBody().path("metadata").has("current-task")).isFalse();
        assertThat(observation.getBody().path("metadata").has("pose")).isFalse();

        ResponseEntity<JsonNode> acquired = post(Map.of(
                "requestId", "rest-acquire-lease-1",
                "action", "acquire-control-lease",
                "arguments", Map.of(
                        "holder", "rest-simulator-agent",
                        "duration-seconds", 300)));
        assertThat(acquired.getStatusCode()).isEqualTo(HttpStatus.OK);
        String leaseToken = acquired.getBody().path("output").path("leaseToken").asText();
        assertThat(leaseToken).hasSize(43);
        assertThat(acquired.getBody().path("snapshot").path("metadata")
                .path("control-lease-active").asBoolean()).isTrue();
        assertThat(controlLeases.findAll()).singleElement().satisfies(lease -> {
            assertThat(lease.getTokenHash()).hasSize(64).doesNotContain(leaseToken);
            assertThat(lease.getHolder()).isEqualTo("rest-simulator-agent");
        });

        ResponseEntity<JsonNode> staged = post(Map.of(
                "requestId", "rest-stage-1",
                "action", "stage-task",
                "arguments", Map.of(
                        "task", "Carry one empty tote to the wash station",
                        "work-area", "Packing shed",
                        "max-duration-seconds", 120,
                        "constraints", List.of("Keep clear of people"),
                        "lease-token", leaseToken)));
        assertThat(staged.getStatusCode()).isEqualTo(HttpStatus.OK);
        String planToken = staged.getBody().path("output").path("planToken").asText();
        assertThat(planToken).startsWith("sim-plan-");
        assertThat(Instant.parse(staged.getBody().path("output")
                .path("planExpiresAt").asText())).isAfter(Instant.now());
        assertThat(staged.getBody().path("snapshot").path("metadata")
                .path("staged-plan").asBoolean()).isTrue();
        assertThat(staged.getBody().path("snapshot").path("metadata")
                .path("staged-plan-adapter-confirmed").asBoolean()).isTrue();
        assertThat(stagedPlans.findAll()).singleElement().satisfies(plan -> {
            assertThat(plan.getPlanTokenHash()).hasSize(64).doesNotContain(planToken);
            assertThat(plan.getLeaseId()).isEqualTo(
                    acquired.getBody().path("output").path("leaseId").asText());
            assertThat(plan.getExecutionRequestId()).isNull();
            assertThat(plan.getExecutionRequestedAt()).isNull();
        });

        ResponseEntity<JsonNode> running = post(Map.of(
                "requestId", "rest-execute-1",
                "action", "execute-staged-task",
                "expiresAt", Instant.now().plusSeconds(10).toString(),
                "arguments", Map.of(
                        "plan-token", planToken,
                        "lease-token", leaseToken)));
        assertThat(running.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(running.getBody().path("output").path("status").asText())
                .isEqualTo("RUNNING");
        assertThat(running.getBody().path("snapshot").path("metadata")
                .path("motion-state").asText()).isEqualTo("RUNNING");
        assertThat(running.getBody().path("snapshot").path("metadata")
                .path("staged-plan").asBoolean()).isFalse();
        assertThat(running.getBody().path("snapshot").path("metadata")
                .path("staged-plan-adapter-confirmed").asBoolean()).isFalse();
        assertThat(running.getBody().path("snapshot").path("metadata")
                .path("task-status").asText()).isEqualTo("RUNNING");
        assertThat(stagedPlans.findAll()).isEmpty();

        String commandId = running.getBody().path("output").path("commandId").asText();
        ResponseEntity<JsonNode> liveOperation = post(Map.of(
                "requestId", "rest-get-running-1",
                "action", "get-operation",
                "arguments", Map.of("command-id", commandId)));
        assertThat(liveOperation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liveOperation.getBody().path("output").path("status").asText())
                .isEqualTo("RUNNING");
        assertThat(liveOperation.getBody().path("output")
                .path("reconciliation").asText()).isEqualTo("LIVE");
        ResponseEntity<JsonNode> cancelled = post(Map.of(
                "requestId", "rest-cancel-active-1",
                "action", "cancel-active-task",
                "expiresAt", Instant.now().plusSeconds(10).toString(),
                "arguments", Map.of(
                        "command-id", commandId,
                        "lease-token", leaseToken)));
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().path("output").path("status").asText())
                .isEqualTo("CANCELLED");
        assertThat(cancelled.getBody().path("snapshot").path("metadata")
                .path("motion-state").asText()).isEqualTo("IDLE");
        assertThat(cancelled.getBody().path("snapshot").path("metadata")
                .path("task-status").asText()).isEqualTo("CANCELLED");

        ResponseEntity<JsonNode> terminalOperation = post(Map.of(
                "requestId", "rest-get-cancelled-1",
                "action", "get-operation",
                "arguments", Map.of("command-id", commandId)));
        assertThat(terminalOperation.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(terminalOperation.getBody().path("output").path("status").asText())
                .isEqualTo("CANCELLED");
        assertThat(terminalOperation.getBody().path("output").path("terminal").asBoolean())
                .isTrue();
        assertThat(terminalOperation.getBody().path("output").has("robotId")).isFalse();
        assertThat(terminalOperation.getBody().path("output").has("statusMessage"))
                .isFalse();

        ResponseEntity<JsonNode> recentOperations = post(Map.of(
                "requestId", "rest-list-operations-1",
                "action", "list-operations",
                "arguments", Map.of("limit", 10)));
        assertThat(recentOperations.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recentOperations.getBody().path("output").path("count").asInt())
                .isEqualTo(2);
        assertThat(recentOperations.getBody().path("output")
                .path("operations").toString())
                .contains(commandId, "CANCELLED", "TERMINAL_HISTORY")
                .doesNotContain("robotId", "statusMessage");
        assertThat(operationEvents
                .findFirstByPluginIdAndOperationIdOrderByIdDesc(
                        OptimusHarnessPlugin.ID, commandId))
                .hasValueSatisfying(event -> {
                    assertThat(event.getStatus()).isEqualTo("CANCELLED");
                    assertThat(event.getProgressPercent()).isZero();
                });
        assertThat(operationEvents
                .findFirstByPluginIdAndRequestIdOrderByIdDesc(
                        OptimusHarnessPlugin.ID, "rest-execute-1"))
                .hasValueSatisfying(event -> {
                    assertThat(event.getOperationId()).isEqualTo(commandId);
                    assertThat(event.getRequestFingerprint())
                            .hasSize(64)
                            .doesNotContain(planToken, leaseToken);
                });

        ResponseEntity<JsonNode> firstEventPage = post(Map.of(
                "requestId", "rest-list-events-1",
                "action", "list-operation-events",
                "arguments", Map.of("after-cursor", "0", "limit", 2)));
        assertThat(firstEventPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstEventPage.getBody().path("output").path("count").asInt())
                .isEqualTo(2);
        assertThat(firstEventPage.getBody().path("output").path("hasMore").asBoolean())
                .isTrue();
        String eventCursor = firstEventPage.getBody().path("output")
                .path("nextCursor").asText();
        assertThat(Long.parseLong(eventCursor)).isPositive();
        assertThat(firstEventPage.getBody().path("output").path("events").toString())
                .contains(commandId, "RUNNING", "cursor")
                .doesNotContain("robotId", "statusMessage");

        ResponseEntity<JsonNode> remainingEvents = post(Map.of(
                "requestId", "rest-list-events-2",
                "action", "list-operation-events",
                "arguments", Map.of("after-cursor", eventCursor, "limit", 50)));
        assertThat(remainingEvents.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(remainingEvents.getBody().path("output").path("hasMore").asBoolean())
                .isFalse();
        assertThat(remainingEvents.getBody().path("output").path("events").toString())
                .contains("CANCELLED", "cursor")
                .doesNotContain("robotId", "statusMessage");

        ResponseEntity<JsonNode> released = post(Map.of(
                "requestId", "rest-release-lease-1",
                "action", "release-control-lease",
                "arguments", Map.of("lease-token", leaseToken)));
        assertThat(released.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(released.getBody().path("output").path("released").asBoolean())
                .isTrue();
        ResponseEntity<JsonNode> leaseStatus = post(Map.of(
                "requestId", "rest-get-lease-1",
                "action", "get-control-lease",
                "arguments", Map.of()));
        assertThat(leaseStatus.getBody().path("output").path("active").asBoolean())
                .isFalse();
        assertThat(leaseStatus.getBody().toString())
                .doesNotContain(leaseToken, "rest-simulator-agent");
    }

    private ResponseEntity<JsonNode> post(Map<String, Object> body) {
        return rest.postForEntity(
                url("/api/harness/tesla-optimus/commands"),
                new HttpEntity<>(body, headers()), JsonNode.class);
    }

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HarnessController.TOKEN_HEADER, TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
