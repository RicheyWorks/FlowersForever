package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.PnwPlantProfile;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.reference.PnwPlantCatalog;
import com.flowerfarm.service.SeasonPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Season Planner decisions require current access, scope, records, and save state")
class SeasonPlannerDecisionSafetyTest {
    private static final int YEAR = LocalDate.now().getYear();
    private static final long FIRST_ID = 11L;
    private static final long SECOND_ID = 22L;
    private static final PnwPlantProfile DAHLIA = PnwPlantCatalog.all().stream()
            .filter(profile -> "Dahlia".equals(profile.commonName())).findFirst().orElseThrow();

    @ParameterizedTest(name = "{0}: {1} invalidates modal approval")
    @MethodSource("modalChanges")
    void changedAccessScopeOrRefreshRejectsTheCapturedDecision(Action action, ModalChange change)
            throws Exception {
        Fixture fixture = new Fixture();
        Gate read = change == ModalChange.REFRESH_STARTED ? new Gate() : null;
        fixture.duringDecision = () -> {
            switch (change) {
                case ROLE -> fixture.tab.applyRolePermissions(false);
                case HOST_ACCESS -> fixture.host.canWrite = false;
                case REFRESH_STARTED -> {
                    fixture.nextRead.set(read);
                    fixture.tab.refreshData();
                }
                case REFRESH_COMPLETED -> fixture.refreshInsideModal();
                case REFRESH_FAILED -> {
                    fixture.failRead.set(true);
                    fixture.refreshInsideModal();
                }
                case SEASON -> {
                    fixture.season().setSelectedItem(YEAR + 1);
                    pumpUntil(() -> fixture.refreshButton().isEnabled());
                }
            }
        };
        try {
            fixture.runAction(action, false);

            assertThat(fixture.decisions).hasValue(1);
            SwingUtilities.invokeAndWait(() -> assertThat(fixture.tab.hasPendingSave()).isFalse());
            fixture.assertNoWrites();
            if (action == Action.GUIDE) {
                assertThat(fixture.guideAccepted.get()).isFalse();
            }
            if (read != null) {
                read.assertEntered();
            }
        } finally {
            if (read != null) {
                read.release();
                fixture.awaitIdle();
            }
        }
    }

    @ParameterizedTest(name = "{0}: changing {1} rejects selected-record approval")
    @MethodSource("selectedChanges")
    void changedSelectedPlantingCannotUseAnOlderApproval(Action action, SelectedChange change)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.duringDecision = () -> {
            if (change == SelectedChange.SELECTION) {
                fixture.table().setRowSelectionInterval(1, 1);
            } else {
                fixture.selectedModel().setNotes("Changed saved planning notes during review");
            }
        };

        fixture.runAction(action, false);

        assertThat(fixture.decisions).hasValue(1);
        SwingUtilities.invokeAndWait(() -> assertThat(fixture.tab.hasPendingSave()).isFalse());
        fixture.assertNoWrites();
    }

    @ParameterizedTest(name = "{0}: a finished nested write cannot authorize another submission")
    @EnumSource(Action.class)
    void nestedSaveInvalidatesOuterDecisionEvenAfterFailureRestoresControls(Action action) throws Exception {
        Fixture fixture = new Fixture();
        AtomicBoolean outer = new AtomicBoolean(true);
        fixture.duringDecision = () -> {
            if (outer.getAndSet(false)) {
                fixture.invokeAction(Action.ADD, true);
                pumpUntil(() -> fixture.refreshButton().isEnabled());
            }
        };

        fixture.runAction(action, false);

        assertThat(fixture.decisions).hasValue(2);
        SwingUtilities.invokeAndWait(() -> assertThat(fixture.tab.hasPendingSave()).isFalse());
        verify(fixture.service).create(any(SeasonPlanEntry.class));
        verify(fixture.service, never()).update(anyLong(), any(SeasonPlanEntry.class));
        verify(fixture.service, never()).delete(anyLong());
        if (action == Action.GUIDE) {
            assertThat(fixture.guideAccepted.get()).isFalse();
        }
    }

    @ParameterizedTest(name = "direct {0} while {1} opens no modal and submits no write")
    @MethodSource("directStates")
    void directEntryPointsRespectUnavailableActions(Action action, DirectState state) throws Exception {
        Fixture fixture = new Fixture();
        Gate read = state == DirectState.LOADING ? new Gate() : null;
        Gate write = state == DirectState.SAVING ? new Gate() : null;
        switch (state) {
            case ROLE -> SwingUtilities.invokeAndWait(() -> fixture.tab.applyRolePermissions(false));
            case HOST_ACCESS -> fixture.host.canWrite = false;
            case FAILED_REFRESH -> {
                fixture.failRead.set(true);
                SwingUtilities.invokeAndWait(fixture.tab::refreshData);
                fixture.awaitIdle();
            }
            case LOADING -> {
                fixture.nextRead.set(read);
                SwingUtilities.invokeAndWait(fixture.tab::refreshData);
                read.assertEntered();
            }
            case SAVING -> {
                fixture.saveResponse.set((id, entry) -> {
                    write.awaitRelease();
                    throw new IllegalStateException("Season plan database busy");
                });
                fixture.runAction(Action.ADD, false);
                write.assertEntered();
            }
        }
        clearInvocations(fixture.service, fixture.dialogs);
        fixture.decisions.set(0);
        try {
            fixture.runAction(action, true);

            assertThat(fixture.decisions).hasValue(0);
            SwingUtilities.invokeAndWait(() -> assertThat(fixture.tab.hasPendingSave())
                    .as("only the already-submitted blocked save can remain pending")
                    .isEqualTo(state == DirectState.SAVING));
            fixture.assertNoWrites();
            verify(fixture.dialogs, never()).showConfirm(any(), any(), anyString(), anyInt(), anyInt());
            if (action == Action.GUIDE) {
                assertThat(fixture.guideAccepted.get()).isFalse();
            }
        } finally {
            if (read != null) {
                read.release();
                fixture.awaitIdle();
            }
            if (write != null) {
                write.release();
                fixture.awaitIdle();
            }
        }
    }

    @ParameterizedTest(name = "{0}: failed reads stay locked until a successful explicit refresh")
    @EnumSource(Action.class)
    void successfulRefreshRestoresWriteActionsWithoutReusingARejectedDecision(Action action) throws Exception {
        Fixture fixture = new Fixture();
        fixture.failRead.set(true);
        SwingUtilities.invokeAndWait(fixture.tab::refreshData);
        fixture.awaitIdle();
        SwingUtilities.invokeAndWait(() -> {
            fixture.tab.applyRolePermissions(true);
            fixture.table().setRowSelectionInterval(1, 1);
            fixture.table().setRowSelectionInterval(0, 0);
            fixture.assertWriteButtonsEnabled(false);
        });
        fixture.runAction(action, true);
        fixture.assertNoWrites();
        assertThat(fixture.decisions).hasValue(0);

        fixture.failRead.set(false);
        SwingUtilities.invokeAndWait(fixture.tab::refreshData);
        fixture.awaitIdle();
        fixture.runAction(action, false);
        fixture.awaitIdle();

        assertThat(fixture.decisions).hasValue(1);
        fixture.assertOnlyWrite(action);
        if (action == Action.GUIDE) {
            assertThat(fixture.guideAccepted.get()).isTrue();
        }
    }

    @ParameterizedTest(name = "save succeeds={0}: refresh requests wait for the write result")
    @ValueSource(booleans = {false, true})
    void refreshDuringSaveIsQueuedAndCoalesced(boolean succeeds) throws Exception {
        Fixture fixture = new Fixture();
        Gate write = new Gate();
        fixture.saveResponse.set((id, entry) -> {
            write.awaitRelease();
            if (!succeeds) {
                throw new IllegalStateException("Season plan database busy");
            }
            SeasonPlanEntry saved = copy(entry);
            saved.setId(99L);
            return saved;
        });
        try {
            fixture.runAction(Action.ADD, false);
            write.assertEntered();
            SwingUtilities.invokeAndWait(() -> {
                fixture.tab.refreshData();
                fixture.tab.refreshData();
                assertThat(fixture.table().getRowCount()).isEqualTo(2);
                fixture.assertWriteButtonsEnabled(false);
            });

            verify(fixture.service).analyze(YEAR);
        } finally {
            write.release();
            fixture.awaitIdle();
        }

        verify(fixture.service).create(any(SeasonPlanEntry.class));
        verify(fixture.service, times(2)).analyze(YEAR);
        assertThat(fixture.table().getRowCount()).isEqualTo(2);
        SwingUtilities.invokeAndWait(() -> fixture.assertWriteButtonsEnabled(true));
    }

    private static Stream<Arguments> modalChanges() {
        return Stream.of(Action.values()).flatMap(action -> Stream.of(ModalChange.values())
                .map(change -> Arguments.of(action, change)));
    }

    private static Stream<Arguments> selectedChanges() {
        return Stream.of(Action.EDIT, Action.SUCCESSION, Action.DELETE)
                .flatMap(action -> Stream.of(SelectedChange.values()).map(change -> Arguments.of(action, change)));
    }

    private static Stream<Arguments> directStates() {
        return Stream.of(Action.values()).flatMap(action -> Stream.of(DirectState.values())
                .map(state -> Arguments.of(action, state)));
    }

    private enum Action {
        ADD("seasonPlanner.add", "addPlanting"),
        EDIT("seasonPlanner.edit", "editSelected"),
        SUCCESSION("seasonPlanner.succession", "planSuccession"),
        DELETE("seasonPlanner.delete", "deleteSelected"),
        GUIDE(null, null);

        private final String buttonName;
        private final String methodName;

        Action(String buttonName, String methodName) {
            this.buttonName = buttonName;
            this.methodName = methodName;
        }
    }

    private enum ModalChange {
        ROLE, HOST_ACCESS, REFRESH_STARTED, REFRESH_COMPLETED, REFRESH_FAILED, SEASON
    }

    private enum SelectedChange {
        SELECTION, SAVED_NOTES
    }

    private enum DirectState {
        ROLE, HOST_ACCESS, LOADING, SAVING, FAILED_REFRESH
    }

    private static final class Fixture {
        private final SeasonPlanService service = mock(SeasonPlanService.class);
        private final FarmDialogs dialogs = mock(FarmDialogs.class);
        private final AtomicBoolean failRead = new AtomicBoolean();
        private final AtomicReference<Gate> nextRead = new AtomicReference<>();
        private final AtomicInteger decisions = new AtomicInteger();
        private final AtomicReference<Boolean> guideAccepted = new AtomicReference<>();
        private final AtomicReference<BiFunction<Long, SeasonPlanEntry, SeasonPlanEntry>> saveResponse =
                new AtomicReference<>((id, entry) -> { throw new IllegalStateException("Season plan database busy"); });
        private final TestHost host = new TestHost();
        private final SeasonPlannerTab tab;
        private final JComponent root;
        private final SwingUserHarness user;
        private Runnable duringDecision = () -> { };

        private Fixture() throws Exception {
            when(service.analyze(anyInt())).thenAnswer(invocation -> {
                Gate gate = nextRead.getAndSet(null);
                if (gate != null) {
                    gate.awaitRelease();
                }
                if (failRead.get()) {
                    throw new IllegalStateException("Season plan read unavailable");
                }
                int year = invocation.getArgument(0);
                return analysis(year);
            });
            when(service.availableSeasons()).thenReturn(List.of(YEAR, YEAR + 1));
            when(service.draftSuccession(any(SeasonPlanEntry.class), eq(14)))
                    .thenAnswer(invocation -> copy(invocation.getArgument(0)));
            when(service.create(any(SeasonPlanEntry.class)))
                    .thenAnswer(invocation -> saveResponse.get().apply(null, invocation.getArgument(0)));
            when(service.update(anyLong(), any(SeasonPlanEntry.class)))
                    .thenAnswer(invocation -> saveResponse.get()
                            .apply(invocation.getArgument(0), invocation.getArgument(1)));
            doAnswer(invocation -> { throw new IllegalStateException("Season plan database busy"); })
                    .when(service).delete(anyLong());
            when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> {
                        assertThat((Integer) invocation.getArgument(3)).isEqualTo(JOptionPane.OK_CANCEL_OPTION);
                        if (!"Remove planting block".equals(invocation.getArgument(2))) {
                            fillForm((JComponent) invocation.getArgument(1));
                        }
                        decisions.incrementAndGet();
                        duringDecision.run();
                        return JOptionPane.OK_OPTION;
                    });
            tab = new SeasonPlannerTab(service, host, dialogs);
            JComponent[] built = new JComponent[1];
            SwingUtilities.invokeAndWait(() -> {
                built[0] = tab.getUIComponent();
                tab.initialize();
            });
            root = built[0];
            user = SwingUserHarness.on(root);
            awaitIdle();
            SwingUtilities.invokeAndWait(() -> {
                user.named("seasonPlanner.workspace", JTabbedPane.class).setSelectedIndex(1);
                table().setRowSelectionInterval(0, 0);
                assertWriteButtonsEnabled(true);
            });
        }

        private void runAction(Action action, boolean direct) throws Exception {
            SwingUtilities.invokeAndWait(() -> invokeAction(action, direct));
        }

        private void invokeAction(Action action, boolean direct) {
            if (action == Action.GUIDE) {
                guideAccepted.set(tab.preparePlantingFromReference(DAHLIA));
            } else if (direct) {
                ReflectionTestUtils.invokeMethod(tab, action.methodName);
            } else {
                JButton button = user.named(action.buttonName, JButton.class);
                assertThat(button.isEnabled()).as("action is enabled before the modal opens").isTrue();
                button.doClick();
            }
        }

        private JTable table() {
            return user.named("seasonPlanner.table", JTable.class);
        }

        private JComboBox<?> season() {
            return user.named("seasonPlanner.season", JComboBox.class);
        }

        private JButton refreshButton() {
            return user.named("seasonPlanner.refresh", JButton.class);
        }

        @SuppressWarnings("unchecked")
        private SeasonPlanEntry selectedModel() {
            List<SeasonPlanEntry> entries = (List<SeasonPlanEntry>) ReflectionTestUtils.getField(tab, "lastView");
            return entries.get(table().convertRowIndexToModel(table().getSelectedRow()));
        }

        private void refreshInsideModal() {
            tab.refreshData();
            pumpUntil(() -> refreshButton().isEnabled());
        }

        private void awaitIdle() {
            user.awaitButtonEnabled("Refresh", true);
        }

        private void assertWriteButtonsEnabled(boolean enabled) {
            for (Action action : List.of(Action.ADD, Action.EDIT, Action.SUCCESSION, Action.DELETE)) {
                assertThat(user.named(action.buttonName, JButton.class).isEnabled())
                        .as(action + " enablement").isEqualTo(enabled);
            }
        }

        private void assertNoWrites() {
            verify(service, never()).create(any(SeasonPlanEntry.class));
            verify(service, never()).update(anyLong(), any(SeasonPlanEntry.class));
            verify(service, never()).delete(anyLong());
        }

        private void assertOnlyWrite(Action action) {
            verify(service, times(action == Action.ADD || action == Action.SUCCESSION || action == Action.GUIDE ? 1 : 0))
                    .create(any(SeasonPlanEntry.class));
            verify(service, times(action == Action.EDIT ? 1 : 0))
                    .update(eq(FIRST_ID), any(SeasonPlanEntry.class));
            verify(service, times(action == Action.DELETE ? 1 : 0)).delete(FIRST_ID);
            verify(service, never()).update(eq(SECOND_ID), any(SeasonPlanEntry.class));
            verify(service, never()).delete(SECOND_ID);
        }
    }

    private static void fillForm(JComponent panel) {
        SwingUserHarness.on(panel).type("Crop", "Dahlia")
                .type("Variety", "Cafe au Lait")
                .type("Bed or field", "Bed A revised")
                .type("Sow date", YEAR + "-03-01")
                .type("Transplant date", YEAR + "-04-12")
                .type("Harvest start", YEAR + "-06-20")
                .type("Harvest end", YEAR + "-09-10")
                .type("Bed length in feet", "85.5")
                .type("Expected stems", "650")
                .type("Planning notes", "Retain reviewed planning details");
    }

    private static SeasonPlanService.SeasonAnalysis analysis(int year) {
        List<SeasonPlanEntry> entries = List.of(entry(FIRST_ID, year), entry(SECOND_ID, year));
        return new SeasonPlanService.SeasonAnalysis(entries,
                new SeasonPlanService.SeasonSummary(2, 2, 2, 2, 160, 1_280,
                        LocalDate.of(year, 6, 20), LocalDate.of(year, 9, 10)),
                List.of(), List.of(), List.of(), List.of(), 0, 0, 0);
    }

    private static SeasonPlanEntry entry(long id, int year) {
        SeasonPlanEntry entry = new SeasonPlanEntry(year, id == FIRST_ID ? "Rose" : "Tulip", "Market mix",
                id == FIRST_ID ? "Bed A" : "Bed B", LocalDate.of(year, 3, 1), LocalDate.of(year, 4, 12),
                LocalDate.of(year, 6, 20), LocalDate.of(year, 9, 10), 80, 640,
                SeasonPlanStatus.PLANNED, "Original planting notes");
        entry.setId(id);
        return entry;
    }

    private static SeasonPlanEntry copy(SeasonPlanEntry entry) {
        return new SeasonPlanEntry(entry.getSeasonYear(), entry.getCropName(), entry.getVariety(),
                entry.getBedOrField(), entry.getSowDate(), entry.getTransplantDate(), entry.getHarvestStart(),
                entry.getHarvestEnd(), entry.getBedFeet(), entry.getExpectedStems(), entry.getStatus(), entry.getNotes());
    }

    private static void pumpUntil(BooleanSupplier finished) {
        assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        Timer timer = new Timer(10, event -> {
            if (finished.getAsBoolean() || System.nanoTime() >= deadline) {
                loop.exit();
            }
        });
        timer.start();
        try {
            assertThat(loop.enter()).isTrue();
            assertThat(finished.getAsBoolean()).isTrue();
        } finally {
            timer.stop();
        }
    }

    private static final class Gate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        private void awaitRelease() {
            entered.countDown();
            try {
                assertThat(released.await(5, TimeUnit.SECONDS)).as("test releases the blocked worker").isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }

        private void assertEntered() throws InterruptedException {
            assertThat(entered.await(4, TimeUnit.SECONDS)).isTrue();
        }

        private void release() {
            released.countDown();
        }
    }

    private static final class TestHost implements TabHost {
        private boolean canWrite = true;
        @Override public boolean canMutateData() { return canWrite; }
        @Override public void refreshAll() { }
        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }
}
