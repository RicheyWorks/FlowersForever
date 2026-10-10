package com.flowerfarm.gui;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessControl;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.gui.automation.SwingHarnessBridge;
import com.flowerfarm.gui.automation.SwingHarnessPlugin;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.gui.tabs.SeasonPlannerTab;
import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.SeasonPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Advertised desktop selectors survive real planting workflow label changes")
class SwingSelectorWorkflowTest {
    private static final String TOKEN = "selector-workflow-token-123456789";
    private static final int YEAR = LocalDate.now().getYear();

    @Test
    void advertisedAddSelectorSurvivesSavingRetryAndFreshSave() throws Exception {
        Fixture fixture = new Fixture();
        String add = fixture.byLabel("Add planting").selector();
        String refresh = fixture.byLabel("Refresh").selector();
        String review = fixture.byLabel("Review drafts (0)").selector();
        assertThat(add).isEqualTo("seasonPlanner.add");
        assertThat(refresh).isEqualTo("seasonPlanner.refresh");
        assertThat(review).isEqualTo("seasonPlanner.reviewDrafts");
        fixture.assertReadableStatus();

        fixture.failWrites = true;
        WriteGate failedWrite = fixture.holdNextWrite();
        try {
            fixture.form("Add planting block", true, panel -> fill(panel, "Retained sunflower"));
            fixture.click(add);
            failedWrite.awaitStarted();
            fixture.assertControl(add, "Saving planting…", false);
            fixture.assertControl(review, "Review drafts (1)", false);
            assertThat(fixture.readStatus()).contains("Adding planting block");
            assertThat(fixture.snapshot().toString()).doesNotContain("private field notes");
        } finally {
            failedWrite.release();
            fixture.awaitIdle();
        }
        fixture.assertControl(add, "Retry planting", true);
        fixture.assertControl(review, "Review drafts (1)", true);
        assertThat(fixture.readStatus()).contains("not saved");

        fixture.failWrites = false;
        WriteGate successfulWrite = fixture.holdNextWrite();
        try {
            fixture.form("Retry planting block", true, panel -> assertDraft(panel, "Retained sunflower"));
            fixture.click(add);
            successfulWrite.awaitStarted();
            fixture.assertControl(add, "Saving planting…", false);
        } finally {
            successfulWrite.release();
            fixture.awaitIdle();
        }
        fixture.assertControl(add, "Add planting", true);
        fixture.assertControl(review, "Review drafts (0)", false);
        assertThat(fixture.readStatus()).contains("saved");
        fixture.click(refresh);
        fixture.awaitIdle();
        fixture.assertReadableStatus();
        fixture.assertControl(add, "Add planting", true);
        assertThat(fixture.attempts).hasValue(2);
        assertThat(fixture.commits).hasValue(1);
        fixture.assertNoDraft();
    }

    @Test
    void advertisedReviewSelectorStillSelectsEachDraftAsItsCountChanges() throws Exception {
        Fixture fixture = new Fixture();
        String review = fixture.byLabel("Review drafts (0)").selector();
        String edit = fixture.byLabel("Edit selected").selector();
        assertThat(review).isEqualTo("seasonPlanner.reviewDrafts");
        assertThat(edit).isEqualTo("seasonPlanner.edit");
        assertThat(fixture.control("seasonPlanner.table").commandable()).isTrue();
        fixture.failWrites = true;
        for (int row = 0; row < 2; row++) {
            String crop = row == 0 ? "Rose retained" : "Dahlia retained";
            fixture.execute("select-row", Map.of("table", "seasonPlanner.table", "row", row));
            fixture.form("Edit planting block", true, panel -> fill(panel, crop));
            fixture.click(edit);
            fixture.awaitIdle();
            fixture.assertControl(review, "Review drafts (" + (row + 1) + ")", true);
        }
        fixture.failWrites = false;
        fixture.review(review, "Rose retained");
        fixture.assertControl(review, "Review drafts (1)", true);
        fixture.review(review, "Dahlia retained");
        fixture.assertControl(review, "Review drafts (0)", false);
        fixture.assertNoDraft();
        assertThat(fixture.attempts).hasValue(4);
        assertThat(fixture.commits).hasValue(2);
        assertThat(fixture.entries.get()).extracting(SeasonPlanEntry::getCropName)
                .containsExactlyInAnyOrder("Rose retained", "Dahlia retained");
    }

    @ParameterizedTest(name = "saved selector still respects {0} after its label changes")
    @EnumSource(Guard.class)
    void stableSelectorDoesNotBypassDeadlineContextOrViewerRestrictions(Guard guard) throws Exception {
        Fixture fixture = new Fixture();
        HarnessSnapshot original = fixture.snapshot();
        String add = fixture.byLabel("Add planting").selector();
        String oldContext = String.valueOf(original.metadata().get("contextToken"));
        assertThat(add).isEqualTo("seasonPlanner.add");
        fixture.failWrites = true;
        fixture.form("Add planting block", true, panel -> fill(panel, "Guarded planting"));
        fixture.click(add);
        fixture.awaitIdle();
        fixture.assertControl(add, "Retry planting", true);

        switch (guard) {
            case EXPIRED_DEADLINE -> assertThatThrownBy(() -> fixture.gateway.execute(TOKEN,
                    SwingHarnessPlugin.ID, new HarnessCommand("expired-retry", "activate-button",
                    Map.of("selector", add), Instant.EPOCH)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expired");
            case STALE_CONTEXT -> {
                HarnessResponse refused = fixture.execute("activate-button",
                        Map.of("selector", add, "expected-context", oldContext));
                assertThat(refused.output()).containsEntry("status", "FAILED")
                        .containsEntry("failureType", "DesktopContextChangedException");
            }
            case VIEWER_ACCESS -> {
                SwingUtilities.invokeAndWait(() -> fixture.tab.applyRolePermissions(false));
                fixture.assertControl(add, "Retry planting", false);
                HarnessResponse refused = fixture.execute("activate-button", Map.of("selector", add));
                assertThat(refused.output()).containsEntry("status", "FAILED")
                        .containsEntry("failureType", "IllegalArgumentException");
                SwingUtilities.invokeAndWait(() -> fixture.tab.applyRolePermissions(true));
            }
        }
        fixture.assertAnswersComplete();
        assertThat(fixture.attempts).hasValue(1);
        assertThat(fixture.commits).hasValue(0);
        fixture.form("Retry planting block", false, panel -> assertDraft(panel, "Guarded planting"));
        HarnessResponse accepted = fixture.execute("activate-button", Map.of("selector", add,
                "expected-context", fixture.snapshot().metadata().get("contextToken")));
        assertThat(accepted.output()).containsEntry("status", "COMPLETED");
        fixture.assertAnswersComplete();
        assertThat(fixture.attempts).hasValue(1);
    }

    private enum Guard { EXPIRED_DEADLINE, STALE_CONTEXT, VIEWER_ACCESS }

    private static final class Fixture {
        private final SeasonPlanService service = mock(SeasonPlanService.class);
        private final FarmDialogs dialogs = mock(FarmDialogs.class);
        private final Deque<FormAnswer> forms = new ArrayDeque<>();
        private final Deque<Consumer<JComponent>> choices = new ArrayDeque<>();
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicReference<WriteGate> nextWrite = new AtomicReference<>();
        private final AtomicReference<List<SeasonPlanEntry>> entries = new AtomicReference<>(List.of(
                entry(11L, "Rose", "Bed A"), entry(22L, "Dahlia", "Bed B")));
        private final SeasonPlannerTab tab;
        private final SwingUserHarness user;
        private final HarnessGateway gateway;
        private volatile boolean failWrites;

        private Fixture() throws Exception {
            when(service.availableSeasons()).thenReturn(List.of(YEAR));
            when(service.analyze(anyInt())).thenAnswer(invocation -> analysis(entries.get()));
            when(service.create(any(SeasonPlanEntry.class))).thenAnswer(invocation -> save(null, invocation.getArgument(0)));
            when(service.update(anyLong(), any(SeasonPlanEntry.class)))
                    .thenAnswer(invocation -> save(invocation.getArgument(0), invocation.getArgument(1)));
            when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt())).thenAnswer(invocation -> {
                FormAnswer answer = forms.pollFirst();
                assertThat(answer).as("Expected form %s", (String) invocation.getArgument(2)).isNotNull();
                assertThat((String) invocation.getArgument(2)).isEqualTo(answer.title());
                answer.inspect().accept(invocation.getArgument(1));
                return answer.save() ? JOptionPane.OK_OPTION : JOptionPane.CANCEL_OPTION;
            });
            when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        assertThat((String) invocation.getArgument(2)).isEqualTo("Retained planting drafts");
                        Consumer<JComponent> inspect = choices.pollFirst();
                        assertThat(inspect).isNotNull();
                        inspect.accept(invocation.getArgument(1));
                        return 1;
                    });
            TabHost host = mock(TabHost.class);
            when(host.canMutateData()).thenReturn(true);
            tab = new SeasonPlannerTab(service, host, dialogs);
            AtomicReference<JComponent> content = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                content.set(tab.getUIComponent());
                tab.initialize();
            });
            user = SwingUserHarness.on(content.get());
            awaitIdle();
            SwingHarnessBridge bridge = new SwingHarnessBridge();
            bridge.attach(content.get(), ignored -> true);
            HarnessProperties properties = new HarnessProperties();
            properties.setEnabled(true);
            properties.setToken(TOKEN);
            properties.setAllowDrafts(true);
            properties.setAllowMutations(true);
            properties.setAllowDestructive(true);
            properties.setAllowSensitiveRead(true);
            gateway = new HarnessGateway(new HarnessRegistry(List.of(new SwingHarnessPlugin(bridge))), properties);
            assertThat(execute("select-tab", Map.of("selector", "seasonPlanner.workspace", "tab", "Plan records"))
                    .output()).containsEntry("status", "COMPLETED");
        }

        private SeasonPlanEntry save(Long id, SeasonPlanEntry draft) throws InterruptedException {
            attempts.incrementAndGet();
            WriteGate gate = nextWrite.getAndSet(null);
            if (gate != null) {
                gate.started.countDown();
                assertThat(gate.released.await(10, TimeUnit.SECONDS)).as("write gate released").isTrue();
            }
            if (failWrites) {
                throw new IllegalStateException("planting database busy");
            }
            draft.setId(id == null ? 100L : id);
            entries.updateAndGet(current -> {
                List<SeasonPlanEntry> updated = new ArrayList<>(current);
                updated.removeIf(entry -> entry.getId().equals(draft.getId()));
                updated.add(draft);
                return List.copyOf(updated);
            });
            commits.incrementAndGet();
            return draft;
        }

        private WriteGate holdNextWrite() {
            WriteGate gate = new WriteGate();
            nextWrite.set(gate);
            return gate;
        }

        private void form(String title, boolean save, Consumer<JComponent> inspect) {
            forms.addLast(new FormAnswer(title, save, inspect));
        }

        private void review(String selector, String crop) {
            choices.addLast(panel -> {
                JComboBox<?> chooser = SwingUserHarness.on(panel).named("seasonPlanner.draftChoice", JComboBox.class);
                for (int index = 0; index < chooser.getItemCount(); index++) {
                    if (chooser.getItemAt(index).toString().contains(crop)) {
                        chooser.setSelectedIndex(index);
                        return;
                    }
                }
                throw new AssertionError("No retained draft for " + crop);
            });
            form("Retry planting block", true, panel -> assertDraft(panel, crop));
            click(selector);
            awaitIdle();
            assertAnswersComplete();
        }

        private HarnessSnapshot snapshot() {
            return gateway.observe(TOKEN, SwingHarnessPlugin.ID);
        }

        private HarnessControl byLabel(String label) {
            List<HarnessControl> matching = snapshot().controls().stream()
                    .filter(control -> control.label().equals(label)).toList();
            assertThat(matching).as("advertised label %s", label).hasSize(1);
            return matching.get(0);
        }

        private HarnessControl control(String selector) {
            List<HarnessControl> matching = snapshot().controls().stream()
                    .filter(control -> control.selector().equals(selector)).toList();
            assertThat(matching).as("advertised selector %s", selector).hasSize(1);
            return matching.get(0);
        }

        private void assertControl(String selector, String label, boolean enabled) {
            HarnessControl control = control(selector);
            assertThat(control.label()).isEqualTo(label);
            assertThat(control.enabled()).isEqualTo(enabled);
            assertThat(control.commandable()).isTrue();
        }

        private HarnessResponse execute(String action, Map<String, Object> arguments) {
            HarnessResponse result = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                    new HarnessCommand("selector-workflow-" + requests.incrementAndGet(), action, arguments));
            if ("PENDING".equals(result.output().get("status"))) {
                try {
                    SwingUtilities.invokeAndWait(() -> { });
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
                result = gateway.execute(TOKEN, SwingHarnessPlugin.ID, new HarnessCommand(
                        "selector-status-" + requests.incrementAndGet(), "action-status",
                        Map.of("request-id", result.requestId())));
            }
            return result;
        }

        private void click(String selector) {
            assertThat(execute("activate-button", Map.of("selector", selector)).output())
                    .containsEntry("status", "COMPLETED");
        }

        private String readStatus() {
            return String.valueOf(execute("read-control", Map.of("selector", "seasonPlanner.status"))
                    .output().get("value"));
        }

        @SuppressWarnings("unchecked")
        private void assertReadableStatus() {
            List<HarnessControl> readable = (List<HarnessControl>) snapshot().metadata().get("readableControls");
            assertThat(readable).filteredOn(control -> control.selector().equals("seasonPlanner.status"))
                    .hasSize(1).allMatch(HarnessControl::commandable);
            assertThat(readStatus()).isNotBlank().isNotEqualTo("null");
        }

        private void awaitIdle() {
            user.awaitButtonEnabled("Refresh", true);
        }

        private void assertAnswersComplete() {
            assertThat(forms).isEmpty();
            assertThat(choices).isEmpty();
        }

        private void assertNoDraft() throws Exception {
            assertAnswersComplete();
            SwingUtilities.invokeAndWait(() -> assertThat(tab.applicationExitWarning()).isNull());
        }
    }

    private static void fill(JComponent panel, String crop) {
        SwingUserHarness.on(panel).type("Crop", crop).type("Variety", "Trial variety")
                .type("Bed or field", crop + " bed").type("Sow date", YEAR + "-03-01")
                .type("Transplant date", YEAR + "-04-12").type("Harvest start", YEAR + "-06-20")
                .type("Harvest end", YEAR + "-09-10").type("Bed length in feet", "080.00")
                .type("Expected stems", "0037").type("Planning notes", "private field notes")
                .select("Planting status", "Planted");
    }

    private static void assertDraft(JComponent panel, String crop) {
        SwingUserHarness form = SwingUserHarness.on(panel);
        assertThat(form.fieldValue("Crop")).isEqualTo(crop);
        assertThat(form.fieldValue("Expected stems")).isEqualTo("0037");
        assertThat(form.fieldValue("Planning notes")).isEqualTo("private field notes");
    }

    private static SeasonPlanEntry entry(long id, String crop, String bed) {
        SeasonPlanEntry entry = new SeasonPlanEntry(YEAR, crop, "Trial variety", bed,
                LocalDate.of(YEAR, 3, 1), LocalDate.of(YEAR, 4, 12),
                LocalDate.of(YEAR, 6, 20), LocalDate.of(YEAR, 9, 10),
                80, 640, SeasonPlanStatus.PLANTED, "Saved notes");
        entry.setId(id);
        return entry;
    }

    private static SeasonPlanService.SeasonAnalysis analysis(List<SeasonPlanEntry> entries) {
        SeasonPlanService.SeasonSummary summary = new SeasonPlanService.SeasonSummary(
                entries.size(), entries.size(), entries.size(), entries.size(), entries.size() * 80,
                entries.stream().mapToLong(SeasonPlanEntry::getExpectedStems).sum(),
                LocalDate.of(YEAR, 6, 20), LocalDate.of(YEAR, 9, 10));
        return new SeasonPlanService.SeasonAnalysis(entries, summary, List.of(), List.of(),
                List.of(), List.of(), 0, 0, 0);
    }

    private record FormAnswer(String title, boolean save, Consumer<JComponent> inspect) { }

    private static final class WriteGate {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        private void awaitStarted() throws InterruptedException {
            assertThat(started.await(5, TimeUnit.SECONDS)).as("save began").isTrue();
        }

        private void release() {
            released.countDown();
        }
    }
}
