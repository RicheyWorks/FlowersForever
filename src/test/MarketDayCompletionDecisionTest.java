package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MarketDayPackingService.CustomerPack;
import com.flowerfarm.service.MarketDayPackingService.FulfillBatchResult;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import com.flowerfarm.service.MarketDayPackingService.PackLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Market Day completion requires current packing evidence and a current decision")
class MarketDayCompletionDecisionTest {
    @ParameterizedTest(name = "{0} invalidates an open completion decision")
    @EnumSource(ModalChange.class)
    void changedModalContextDoesNotSubmitCompletion(ModalChange change) throws Exception {
        Fixture fixture = new Fixture();
        fixture.beforeDecision = () -> {
            switch (change) {
                case ROLE -> fixture.tab.applyRolePermissions(false);
                case HOST_ACCESS -> fixture.host.canWrite = false;
                case UNCHECKED -> fixture.packed().setSelected(false);
                case RECHECKED -> {
                    fixture.packed().setSelected(false);
                    fixture.packed().setSelected(true);
                }
                case SCOPE -> find(fixture.root, JTextField.class)
                        .setText(LocalDate.now().plusDays(1).toString());
                case REFRESHED -> fixture.refreshInsideModal();
                case CHANGED_PLAN -> {
                    fixture.read.set(() -> plan("New pickup instructions"));
                    fixture.refreshInsideModal();
                }
                case FAILED_REFRESH -> {
                    fixture.read.set(() -> { throw new IllegalStateException("Order read unavailable"); });
                    fixture.refreshInsideModal();
                }
            }
        };

        fixture.user.click("Complete confirmed orders…");

        verify(fixture.service, never()).fulfillConfirmedOrders(any());
        assertThat(fixture.host.status).contains("changed", "No completion was submitted");
        assertThat(fixture.tab.hasPendingSave()).isFalse();
        if (change != ModalChange.UNCHECKED && change != ModalChange.CHANGED_PLAN) {
            assertThat(fixture.packed().isSelected()).isTrue();
        }
    }

    @ParameterizedTest(name = "response {0} keeps checked loads without submitting")
    @ValueSource(ints = {0, JOptionPane.CLOSED_OPTION})
    void cancelAndClosePreservePackingEvidence(int choice) throws Exception {
        Fixture fixture = new Fixture();
        fixture.decision = choice;

        fixture.user.click("Complete confirmed orders…");

        assertThat(fixture.packed().isSelected()).isTrue();
        assertThat(fixture.user.isButtonEnabled("Complete confirmed orders…")).isTrue();
        verify(fixture.service, never()).fulfillConfirmedOrders(any());
    }

    @Test
    void newerNestedCompletionConsumesTheDecisionEvenWhenItFailsBeforeOuterApproval() throws Exception {
        Fixture fixture = new Fixture();
        AtomicBoolean outer = new AtomicBoolean(true);
        fixture.beforeDecision = () -> {
            if (outer.getAndSet(false)) {
                fixture.completeButton().doClick();
                pumpUntil(() -> !fixture.tab.hasPendingSave());
            }
        };

        fixture.user.click("Complete confirmed orders…");

        verify(fixture.service).fulfillConfirmedOrders(any());
        assertThat(fixture.packed().isSelected()).isTrue();
        assertThat(fixture.tab.hasPendingSave()).isFalse();
        assertThat(fixture.host.status).contains("No completion was submitted from this decision");
    }

    @Test
    void refreshStartedInsideConfirmationBlocksApprovalUntilTheNewListFinishes() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fixture.read.set(() -> {
            entered.countDown();
            await(release);
            return fixture.initial;
        });
        fixture.beforeDecision = fixture.tab::refreshData;
        try {
            fixture.user.click("Complete confirmed orders…");

            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            verify(fixture.service, never()).fulfillConfirmedOrders(any());
            assertThat(fixture.packed().isSelected()).isTrue();
            assertThat(fixture.completeButton().isEnabled()).isFalse();
        } finally {
            release.countDown();
            fixture.awaitRefresh();
        }
        assertThat(fixture.completeButton().isEnabled()).isTrue();
    }

    @ParameterizedTest(name = "direct completion respects {0}")
    @EnumSource(DirectBlock.class)
    void directCompletionCannotBypassCurrentAccessChecksOrReadState(DirectBlock block) throws Exception {
        Fixture fixture = new Fixture();
        AtomicReference<MarketDayPlan> submitted = new AtomicReference<>(fixture.initial);
        SwingUtilities.invokeAndWait(() -> {
            switch (block) {
                case ROLE -> fixture.tab.applyRolePermissions(false);
                case HOST_ACCESS -> fixture.host.canWrite = false;
                case UNPACKED -> fixture.packed().setSelected(false);
                case SCOPE -> find(fixture.root, JTextField.class)
                        .setText(LocalDate.now().plusDays(1).toString());
                case DIFFERENT_PLAN -> submitted.set(plan("Different reviewed instructions"));
                case FAILED_REFRESH -> {
                    fixture.read.set(() -> { throw new IllegalStateException("Order read unavailable"); });
                    fixture.refreshInsideModal();
                }
            }
            fixture.tab.startFulfillment(submitted.get());
        });

        verify(fixture.service, never()).fulfillConfirmedOrders(any());
        assertThat(fixture.tab.hasPendingSave()).isFalse();
    }

    @Test
    void staleServiceRejectionStaysLockedThroughFailedRefreshUntilSuccessfulRebuild() throws Exception {
        Fixture fixture = new Fixture();
        fixture.complete.set(plan -> {
            throw new IllegalArgumentException("Packing details changed. Rebuild the packing list and check again.");
        });
        fixture.user.click("Complete confirmed orders…");
        fixture.user.awaitText("Packing review changed · rebuild required");
        assertThat(fixture.packed().isSelected()).isTrue();
        assertThat(fixture.packed().isEnabled()).isFalse();
        assertThat(fixture.completeButton().isEnabled()).isFalse();
        SwingUtilities.invokeAndWait(() -> {
            fixture.tab.applyRolePermissions(true);
            fixture.tab.startFulfillment(fixture.initial);
        });
        verify(fixture.service).fulfillConfirmedOrders(any());
        assertThat(fixture.packed().isEnabled()).isFalse();
        fixture.read.set(() -> { throw new IllegalStateException("Order read unavailable"); });
        SwingUtilities.invokeAndWait(fixture.tab::refreshData);
        fixture.awaitRefresh();
        assertThat(fixture.completeButton().isEnabled()).isFalse();
        assertThat(fixture.packed().isSelected()).isTrue();

        fixture.read.set(() -> fixture.initial);
        SwingUtilities.invokeAndWait(fixture.tab::refreshData);
        fixture.awaitRefresh();

        assertThat(fixture.completeButton().isEnabled()).isTrue();
        assertThat(fixture.packed().isEnabled()).isTrue();
        fixture.complete.set(plan -> { throw new IllegalStateException("Order database is busy"); });
        fixture.user.click("Complete confirmed orders…");
        fixture.user.awaitText("Completion failed · Order database is busy · try again");
        verify(fixture.service, times(2)).fulfillConfirmedOrders(any());
    }

    @ParameterizedTest(name = "completion saved={0} resolves before one queued plan refresh")
    @ValueSource(booleans = {false, true})
    void globalRefreshWaitsForCompletionResultAndCoalescesRequests(boolean saved) throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        fixture.complete.set(plan -> {
            entered.countDown();
            await(release);
            if (!saved) {
                throw new IllegalStateException("Order database is busy");
            }
            return new FulfillBatchResult(1, 1, 0, 0, List.of("#1 Harbor Floral → FULFILLED"),
                    Set.of(1L), Set.of());
        });
        fixture.read.set(() -> saved ? emptyPlan() : fixture.initial);
        JCheckBox original = fixture.packed();
        try {
            fixture.user.click("Complete confirmed orders…");
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            SwingUtilities.invokeAndWait(() -> {
                fixture.tab.refreshData();
                fixture.tab.refreshData();
                assertThat(fixture.packed()).isSameAs(original);
                assertThat(original.isSelected()).isTrue();
            });
            verify(fixture.service).buildPlan(any(), anyInt(), anyBoolean(), anyBoolean());
        } finally {
            release.countDown();
            fixture.awaitRefresh();
        }

        verify(fixture.service, times(2)).buildPlan(any(), anyInt(), anyBoolean(), anyBoolean());
        verify(fixture.service).fulfillConfirmedOrders(any());
        if (saved) {
            assertThat(fixture.tab.applicationExitWarning()).isNull();
            assertThat(fixture.user.text("marketDay.fulfillmentResultDetails"))
                    .contains("1 completed", "#1 Harbor Floral");
        } else {
            assertThat(fixture.packed().isSelected()).isTrue();
            assertThat(fixture.completeButton().isEnabled()).isTrue();
            assertThat(fixture.user.text("marketDay.fulfillmentResultDetails"))
                    .contains("Order database is busy", "packed checks were kept");
        }
    }

    @Test
    void submissionUsesTheImmutableDisplayedLoadEvenIfTheServiceReturnedMutableLists() throws Exception {
        MarketDayPlan source = plan("Original pickup instructions");
        List<CustomerPack> mutableCustomers = new ArrayList<>(source.customers());
        MarketDayPlan mutablePlan = new MarketDayPlan(source.marketDate(), source.from(), source.to(),
                source.scope(), source.orderCount(), source.pipelineValue(), mutableCustomers,
                source.pickList(), source.shortfallSkuCount(), source.plainText());
        Fixture fixture = new Fixture(mutablePlan);
        fixture.beforeDecision = mutableCustomers::clear;

        fixture.user.click("Complete confirmed orders…");
        fixture.user.awaitText("Completion failed · Order database is busy · try again");

        ArgumentCaptor<MarketDayPlan> captured = ArgumentCaptor.forClass(MarketDayPlan.class);
        verify(fixture.service).fulfillConfirmedOrders(captured.capture());
        assertThat(captured.getValue().customers()).hasSize(1);
        assertThat(captured.getValue().customers().get(0).notes()).isEqualTo("Original pickup instructions");
        assertThatThrownBy(() -> captured.getValue().customers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private enum ModalChange {
        ROLE, HOST_ACCESS, UNCHECKED, RECHECKED, SCOPE, REFRESHED, CHANGED_PLAN, FAILED_REFRESH
    }

    private enum DirectBlock {
        ROLE, HOST_ACCESS, UNPACKED, SCOPE, DIFFERENT_PLAN, FAILED_REFRESH
    }

    private static final class Fixture {
        private final MarketDayPackingService service = mock(MarketDayPackingService.class);
        private final FarmDialogs dialogs = mock(FarmDialogs.class);
        private final TestHost host = new TestHost();
        private final MarketDayPlan initial;
        private final AtomicReference<Supplier<MarketDayPlan>> read;
        private final AtomicReference<Function<MarketDayPlan, FulfillBatchResult>> complete =
                new AtomicReference<>(plan -> { throw new IllegalStateException("Order database is busy"); });
        private final MarketDayTab tab;
        private final JComponent root;
        private final SwingUserHarness user;
        private Runnable beforeDecision = () -> { };
        private int decision = 1;

        private Fixture() throws Exception {
            this(plan("Original pickup instructions"));
        }

        private Fixture(MarketDayPlan plan) throws Exception {
            initial = plan;
            read = new AtomicReference<>(() -> initial);
            when(service.buildPlan(any(), anyInt(), anyBoolean(), anyBoolean()))
                    .thenAnswer(invocation -> read.get().get());
            when(service.fulfillConfirmedOrders(any()))
                    .thenAnswer(invocation -> complete.get().apply(invocation.getArgument(0)));
            when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        assertThat((String) invocation.getArgument(2)).isEqualTo("Complete confirmed orders");
                        assertThat((Object) invocation.getArgument(7)).isEqualTo("Cancel");
                        beforeDecision.run();
                        return decision;
                    });
            tab = new MarketDayTab(service, host, dialogs);
            JComponent[] built = new JComponent[1];
            SwingUtilities.invokeAndWait(() -> {
                built[0] = tab.getUIComponent();
                tab.initialize();
            });
            root = built[0];
            user = SwingUserHarness.on(root);
            awaitRefresh();
            SwingUtilities.invokeAndWait(() -> packed().doClick());
            assertThat(completeButton().isEnabled()).isTrue();
        }

        private JCheckBox packed() {
            return user.named("marketDay.packOrder.1", JCheckBox.class);
        }

        private JButton completeButton() {
            return user.named("marketDay.fulfillConfirmed", JButton.class);
        }

        private void awaitRefresh() {
            user.awaitButtonEnabled("Build packing list", true);
        }

        private void refreshInsideModal() {
            tab.refreshData();
            pumpUntil(() -> findButton(root, "Build packing list").isEnabled());
        }
    }

    private static MarketDayPlan plan(String notes) {
        LocalDate day = LocalDate.now();
        CustomerPack customer = new CustomerPack(1L, "Harbor Floral", "FLORIST", "", "", "",
                "CONFIRMED", day, notes, List.of(new PackLine("Tulips", "stems", 10, 3, 30)),
                30, "PICKUP");
        return new MarketDayPlan(day, day, day, "CONFIRMED", 1, 30, List.of(customer),
                List.of(), 0, "Packing list for Harbor Floral");
    }

    private static MarketDayPlan emptyPlan() {
        LocalDate day = LocalDate.now();
        return new MarketDayPlan(day, day, day, "CONFIRMED", 0, 0, List.of(), List.of(), 0, "No orders");
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void pumpUntil(BooleanSupplier finished) {
        assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        Timer timer = new Timer(10, event -> {
            if (finished.getAsBoolean() || System.nanoTime() > deadline) {
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

    private static JButton findButton(Component root, String text) {
        if (root instanceof JButton button && text.equals(button.getText())) {
            return button;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton match = findButton(child, text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = find(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static final class TestHost implements TabHost {
        private boolean canWrite = true;
        private volatile String status;
        @Override public boolean canMutateData() { return canWrite; }
        @Override public void refreshAll() { }
        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) { status = message; }
        @Override public void runTrendAnalysis() { }
    }
}
