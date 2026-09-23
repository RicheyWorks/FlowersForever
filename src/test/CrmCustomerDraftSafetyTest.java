package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.gui.presenter.CrmPresenter;
import com.flowerfarm.model.Customer;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Customer drafts survive navigation, failed saves, and obsolete discard decisions")
class CrmCustomerDraftSafetyTest {
    private static final long FIRST_ID = 11L;
    private static final long SECOND_ID = 22L;
    private static final CrmPresenter.CustomerDraft RAW = new CrmPresenter.CustomerDraft(
            "  Harbor Floral revised  ", "  Maya Rivera  ", "maya@example.test",
            "  360-555-0191 ext 02  ", "Florist", "  Keep these exact delivery details  ");

    @Test
    void emptyAndUnchangedLoadedFormsNeitherWarnNorAskToDiscard() throws Exception {
        Fixture fixture = new Fixture();
        fixture.assertClean();
        fixture.user.click("Clear form");
        fixture.load(FIRST_ID);
        fixture.assertClean();
        fixture.load(SECOND_ID);
        fixture.assertClean();
        fixture.user.click("Clear form");
        fixture.assertEmpty();
        fixture.assertClean();
        assertThat(fixture.replacementPrompts).hasValue(0);
        fixture.assertNoWrites();
    }

    @ParameterizedTest(name = "customer-only change in {0}")
    @ValueSource(strings = {"name", "contact", "email", "phone", "type", "notes"})
    void everyRawFieldParticipatesInCustomerOnlyExitWarningAndReturningToBaselineClearsIt(String field)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.load(FIRST_ID);
        CrmPresenter.CustomerDraft baseline = fixture.draft();
        SwingUtilities.invokeAndWait(() -> {
            switch (field) {
                case "name" -> fixture.field("Customer or business name").setText(" Harbor Floral ");
                case "contact" -> fixture.field("Customer contact person").setText(" Sam Rivera ");
                case "email" -> fixture.field("Customer email").setText(" unfinished@ ");
                case "phone" -> fixture.field("Customer phone").setText(" 360-555-0184 ");
                case "type" -> fixture.type().setSelectedItem("Other");
                default -> fixture.field("Customer notes").setText(" Thursday pickup ");
            }
        });
        fixture.assertCustomerWarning();
        fixture.setDraft(baseline);
        fixture.assertClean();
        assertThat(fixture.replacementPrompts).hasValue(0);
        fixture.assertNoWrites();
    }

    @ParameterizedTest(name = "{0} draft keeps values after {1} response {2}")
    @CsvSource({
            "new, load, 0", "new, load, -1", "edit, load, 0", "edit, load, -1",
            "new, doubleclick, 0", "new, doubleclick, -1", "edit, doubleclick, 0", "edit, doubleclick, -1",
            "new, clear, 0", "new, clear, -1", "edit, clear, 0", "edit, clear, -1"
    })
    void keepingOrClosingReplacementPreservesRawFieldsAndOriginalEditTarget(
            String mode, String action, int response) throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareRaw("edit".equals(mode), FIRST_ID);
        fixture.replacementResponse = response;
        fixture.selectOnEdt(SECOND_ID);

        fixture.replace(action);

        fixture.assertDraft(RAW);
        fixture.assertCustomerWarning();
        assertThat(fixture.replacementPrompts).hasValue(1);
        fixture.assertNoWrites();
        fixture.installSuccessfulSave(false);
        fixture.user.click("edit".equals(mode) ? "Save edit" : "Add customer");
        fixture.awaitSaved();
        if ("edit".equals(mode)) {
            verify(fixture.customers).update(eq(FIRST_ID), any(Customer.class));
            verify(fixture.customers, never()).update(eq(SECOND_ID), any(Customer.class));
        } else {
            verify(fixture.customers).add(any(Customer.class));
            verify(fixture.customers, never()).update(anyLong(), any(Customer.class));
        }
    }

    @ParameterizedTest(name = "discard {0} draft to {1}")
    @CsvSource({"new, load", "edit, load", "new, clear", "edit, clear"})
    void approvedReplacementSetsOnlyTheNewCleanCustomerBaseline(String mode, String action)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareRaw("edit".equals(mode), FIRST_ID);
        fixture.replacementResponse = 1;
        fixture.selectOnEdt(SECOND_ID);

        fixture.replace(action);

        assertThat(fixture.replacementPrompts).hasValue(1);
        if ("load".equals(action)) {
            fixture.assertDraft(savedDraft(SECOND_ID));
            assertThat(fixture.user.isButtonEnabled("Save edit")).isTrue();
        } else {
            fixture.assertEmpty();
            assertThat(fixture.user.isButtonEnabled("Add customer")).isTrue();
        }
        fixture.assertClean();
        fixture.assertNoWrites();
    }

    @Test
    void unchangedLoadedCustomerThatFailsToSaveStillNeedsExplicitDiscard() throws Exception {
        Fixture fixture = new Fixture();
        fixture.load(FIRST_ID);
        fixture.assertClean();
        doThrow(new IllegalStateException("customer database busy"))
                .when(fixture.customers).update(eq(FIRST_ID), any(Customer.class));

        fixture.user.click("Save edit").awaitText("Customer not saved · retry available");

        fixture.assertDraft(savedDraft(FIRST_ID));
        fixture.assertCustomerWarning();
        fixture.user.click("Clear form");
        fixture.assertDraft(savedDraft(FIRST_ID));
        fixture.assertCustomerWarning();
        assertThat(fixture.replacementPrompts).hasValue(1);
        fixture.replacementResponse = 1;
        fixture.user.click("Clear form");
        fixture.assertEmpty();
        fixture.assertClean();
        verify(fixture.customers, times(1)).update(eq(FIRST_ID), any(Customer.class));
    }

    @ParameterizedTest(name = "acknowledged {0} with readback failure={1}")
    @CsvSource({"create, false", "edit, false", "create, true", "edit, true"})
    void acknowledgedCustomerSaveClearsDraftEvenWhenReadbackFails(String action, boolean failReadback)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareRaw("edit".equals(action), FIRST_ID);
        fixture.installSuccessfulSave(failReadback);

        fixture.user.click("edit".equals(action) ? "Save edit" : "Add customer");
        if (failReadback) {
            fixture.user.awaitText("Customer saved once · reload required");
        } else {
            fixture.awaitSaved();
        }

        fixture.assertEmpty();
        fixture.assertClean();
        assertThat(fixture.replacementPrompts).hasValue(0);
        assertThat(fixture.tab.hasPendingSave()).isFalse();
    }

    @ParameterizedTest(name = "delete {0}")
    @ValueSource(strings = {"loaded-success", "other-success", "loaded-failure"})
    void customerDeletionClearsDraftOnlyWhenItsLoadedAccountWasAcknowledgedDeleted(String outcome)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareRaw(true, FIRST_ID);
        long deletedId = "other-success".equals(outcome) ? SECOND_ID : FIRST_ID;
        fixture.selectOnEdt(deletedId);
        doAnswer(invocation -> {
            if ("loaded-failure".equals(outcome)) {
                throw new IllegalStateException("account has linked orders");
            }
            fixture.rows.updateAndGet(rows -> rows.stream()
                    .filter(customer -> !Long.valueOf(deletedId).equals(customer.getId())).toList());
            return null;
        }).when(fixture.customers).delete(deletedId);

        fixture.user.click("Delete selected");
        fixture.user.awaitText("loaded-failure".equals(outcome)
                ? "Customer not deleted · retry available" : "Customer deleted ·");

        if ("loaded-success".equals(outcome)) {
            fixture.assertEmpty();
            // Deleting the default order customer may still leave independent order-entry work.
            SwingUtilities.invokeAndWait(() -> {
                String warning = fixture.tab.applicationExitWarning();
                assertThat(warning == null ? "" : warning)
                        .doesNotContain("unsaved customer details", "new-customer draft");
            });
        } else {
            fixture.assertDraft(RAW);
            fixture.assertCustomerWarning();
        }
        assertThat(fixture.replacementPrompts).hasValue(0);
        verify(fixture.customers, times(1)).delete(deletedId);
    }

    @ParameterizedTest(name = "dirty customer survives {0}")
    @ValueSource(strings = {"refresh", "search", "handoff"})
    void refreshSearchAndCustomerHandoffPreserveDraftAndOriginalEditTarget(String navigation)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareRaw(true, SECOND_ID);
        if ("search".equals(navigation)) {
            fixture.user.type("Customer search", "Harbor").click("Search");
        } else if ("handoff".equals(navigation)) {
            fixture.user.selectTab("Orders").click("Open draft customer");
        } else {
            fixture.user.click("Refresh CRM");
        }
        fixture.awaitRefresh();

        fixture.assertDraft(RAW);
        fixture.assertCustomerWarning();
        assertThat(fixture.replacementPrompts).hasValue(0);
        fixture.installSuccessfulSave(false);
        fixture.user.click("Save edit");
        fixture.awaitSaved();
        verify(fixture.customers).update(eq(SECOND_ID), any(Customer.class));
        verify(fixture.customers, never()).update(eq(FIRST_ID), any(Customer.class));
    }

    @ParameterizedTest(name = "obsolete {0} discard after {1}")
    @CsvSource({
            "load, form", "clear, form", "load, form-reverted", "clear, form-reverted",
            "load, viewer", "clear, viewer", "load, refresh-failure", "clear, refresh-failure",
            "load, selection", "load, target-changed", "load, target-missing"
    })
    void affirmativeDiscardRechecksCapturedFormTargetAndAccess(String action, String change)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareRaw(true, FIRST_ID);
        fixture.selectOnEdt(SECOND_ID);
        fixture.replacementResponse = 1;
        fixture.duringReplacement = ignored -> {
            switch (change) {
                case "form" -> fixture.field("Customer notes").setText("Newer unsaved note");
                case "form-reverted" -> {
                    fixture.field("Customer notes").setText("Newer note temporarily entered");
                    fixture.field("Customer notes").setText(RAW.notes());
                }
                case "viewer" -> fixture.tab.applyRolePermissions(false);
                case "selection" -> fixture.select(FIRST_ID);
                case "refresh-failure" -> {
                    fixture.failRead.set(true);
                    fixture.refreshInsideModal();
                }
                default -> {
                    fixture.rows.set("target-missing".equals(change)
                            ? List.of(customer(FIRST_ID))
                            : List.of(customer(FIRST_ID), new Customer(SECOND_ID, "Kitsap Blooms",
                            "Updated contact", "sam@example.test", "360-555-0184", "WHOLESALE", "Thursday pickup")));
                    fixture.refreshInsideModal();
                }
            }
        };

        fixture.replace(action);

        fixture.assertDraft("form".equals(change) ? withNotes("Newer unsaved note") : RAW);
        fixture.assertCustomerWarning();
        assertThat(fixture.replacementPrompts).hasValue(1);
        fixture.assertNoWrites();
    }

    @ParameterizedTest(name = "obsolete {0} discard during {1}")
    @CsvSource({"load, refresh", "clear, refresh", "load, save", "clear, save"})
    void affirmativeDiscardCannotReplaceFieldsWhileNewWorkIsPending(String action, String pending)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareRaw(true, FIRST_ID);
        fixture.selectOnEdt(SECOND_ID);
        fixture.replacementResponse = 1;
        Gate gate = new Gate();
        if ("save".equals(pending)) {
            doAnswer(invocation -> {
                gate.awaitRelease();
                throw new IllegalStateException("customer save unavailable");
            }).when(fixture.customers).update(eq(FIRST_ID), any(Customer.class));
        } else {
            fixture.nextRead.set(gate);
        }
        fixture.duringReplacement = ignored -> {
            if ("save".equals(pending)) {
                button(fixture.root, "Save edit").doClick();
            } else {
                fixture.tab.refreshData();
            }
        };
        try {
            fixture.replace(action);
            gate.assertEntered();
            fixture.assertDraft(RAW);
            fixture.assertCustomerWarning();
        } finally {
            gate.release();
        }
        if ("save".equals(pending)) {
            fixture.user.awaitText("Customer not saved · retry available");
            verify(fixture.customers, times(1)).update(eq(FIRST_ID), any(Customer.class));
        } else {
            fixture.awaitRefresh();
            fixture.assertNoWrites();
        }
        fixture.assertDraft(RAW);
    }

    @ParameterizedTest(name = "older discard after newer {0}")
    @ValueSource(strings = {"load", "clear", "failed-save"})
    void anOlderDiscardCannotEraseNewerWorkEvenWhenRawFieldsReturnToTheirOriginalValues(String newer)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareRaw(true, FIRST_ID);
        fixture.selectOnEdt(FIRST_ID);
        fixture.replacementResponse = 1;
        doThrow(new IllegalStateException("newer save failed"))
                .when(fixture.customers).update(eq(FIRST_ID), any(Customer.class));
        fixture.duringReplacement = call -> {
            if (call != 1) {
                return;
            }
            if ("failed-save".equals(newer)) {
                button(fixture.root, "Save edit").doClick();
                fixture.awaitInsideModal(() -> !fixture.tab.hasPendingSave());
            } else if ("load".equals(newer)) {
                button(fixture.root, "Load selected").doClick();
            } else {
                button(fixture.root, "Clear form").doClick();
                button(fixture.root, "Load selected").doClick();
            }
            fixture.setDraftOnEdt(RAW);
        };

        fixture.user.click("Clear form");

        fixture.assertDraft(RAW);
        fixture.assertCustomerWarning();
        assertThat(fixture.user.isButtonEnabled("Save edit")).isTrue();
        verify(fixture.customers, times("failed-save".equals(newer) ? 1 : 0))
                .update(eq(FIRST_ID), any(Customer.class));
        verify(fixture.customers, never()).delete(anyLong());
    }

    @Test
    void unchangedSuccessfulRefreshDoesNotInvalidateAnOtherwiseCurrentLoadApproval() throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareRaw(true, FIRST_ID);
        fixture.selectOnEdt(SECOND_ID);
        fixture.replacementResponse = 1;
        fixture.duringReplacement = ignored -> {
            fixture.rows.set(List.of(customer(SECOND_ID), customer(FIRST_ID)));
            fixture.refreshInsideModal();
        };

        fixture.user.click("Load selected");

        fixture.assertDraft(savedDraft(SECOND_ID));
        fixture.assertClean();
        fixture.assertNoWrites();
        assertThat(fixture.replacementPrompts).hasValue(1);
    }

    private static final class Fixture {
        private final CustomerService customers = mock(CustomerService.class);
        private final OrderService orders = mock(OrderService.class);
        private final FarmDialogs dialogs = mock(FarmDialogs.class);
        private final AtomicReference<List<Customer>> rows = new AtomicReference<>(
                List.of(customer(FIRST_ID), customer(SECOND_ID)));
        private final AtomicReference<Gate> nextRead = new AtomicReference<>();
        private final AtomicBoolean failRead = new AtomicBoolean();
        private final AtomicInteger replacementPrompts = new AtomicInteger();
        private final CrmTab tab;
        private final JComponent root;
        private final SwingUserHarness user;
        private int replacementResponse;
        private IntConsumer duringReplacement = ignored -> { };

        private Fixture() throws Exception {
            when(customers.search(anyString())).thenAnswer(invocation -> {
                Gate gate = nextRead.getAndSet(null);
                if (gate != null) {
                    gate.awaitRelease();
                }
                if (failRead.get()) {
                    throw new IllegalStateException("customer read unavailable");
                }
                return rows.get();
            });
            when(customers.getAll()).thenAnswer(invocation -> rows.get());
            when(orders.filter(anyString(), anyString(), nullable(LocalDate.class), nullable(LocalDate.class)))
                    .thenReturn(List.of());
            when(orders.confirmedCompletionQueue()).thenReturn(List.of());
            when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        assertThat((String) invocation.getArgument(2)).isEqualTo("Discard customer changes?");
                        Object[] options = invocation.getArgument(6);
                        assertThat(options).hasSize(2);
                        assertThat(options[0]).isEqualTo("Keep editing");
                        assertThat(options[1]).isIn("Discard and load", "Discard and clear");
                        assertThat((Object) invocation.getArgument(7)).isEqualTo("Keep editing");
                        duringReplacement.accept(replacementPrompts.incrementAndGet());
                        return replacementResponse;
                    });
            when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> {
                        assertThat((String) invocation.getArgument(2)).isEqualTo("Delete customer");
                        return JOptionPane.YES_OPTION;
                    });
            tab = new CrmTab(customers, orders, new Host(), dialogs);
            JComponent[] built = new JComponent[1];
            SwingUtilities.invokeAndWait(() -> {
                built[0] = tab.getUIComponent();
                tab.initialize();
            });
            root = built[0];
            user = SwingUserHarness.on(root);
            awaitRefresh();
        }

        private void prepareRaw(boolean editing, long id) throws Exception {
            if (editing) {
                load(id);
            }
            setDraft(RAW);
        }

        private void load(long id) throws Exception {
            selectOnEdt(id);
            user.click("Load selected");
        }

        private void replace(String action) throws Exception {
            if ("doubleclick".equals(action)) {
                SwingUtilities.invokeAndWait(() -> {
                    JTable table = table();
                    MouseEvent click = new MouseEvent(table, MouseEvent.MOUSE_CLICKED,
                            System.currentTimeMillis(), 0, 10, 10, 2, false, MouseEvent.BUTTON1);
                    for (java.awt.event.MouseListener listener : table.getMouseListeners()) {
                        listener.mouseClicked(click);
                    }
                });
            } else {
                user.click("clear".equals(action) ? "Clear form" : "Load selected");
            }
        }

        private void installSuccessfulSave(boolean failReadback) {
            doAnswer(invocation -> {
                Customer saved = invocation.getArgument(0);
                saved.setId(33L);
                rows.updateAndGet(existing -> java.util.stream.Stream.concat(existing.stream(),
                        java.util.stream.Stream.of(saved)).toList());
                failRead.set(failReadback);
                return saved;
            }).when(customers).add(any(Customer.class));
            doAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                Customer saved = invocation.getArgument(1);
                saved.setId(id);
                rows.updateAndGet(existing -> existing.stream()
                        .map(current -> id.equals(current.getId()) ? saved : current).toList());
                failRead.set(failReadback);
                return saved;
            }).when(customers).update(anyLong(), any(Customer.class));
        }

        private void awaitSaved() {
            user.awaitText("Customer saved ·");
            awaitRefresh();
        }

        private void awaitRefresh() {
            user.awaitButtonEnabled("Refresh CRM", true);
        }

        private void refreshInsideModal() {
            tab.refreshData();
            awaitInsideModal(() -> button(root, "Refresh CRM").isEnabled());
        }

        private void awaitInsideModal(BooleanSupplier finished) {
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
            } finally {
                timer.stop();
            }
            assertThat(finished.getAsBoolean()).isTrue();
        }

        private void selectOnEdt(long id) throws Exception {
            SwingUtilities.invokeAndWait(() -> select(id));
        }

        private void select(long id) {
            JTable table = table();
            for (int row = 0; row < table.getRowCount(); row++) {
                if (Long.valueOf(id).equals(table.getValueAt(row, 0))) {
                    table.setRowSelectionInterval(row, row);
                    return;
                }
            }
            throw new AssertionError("Missing customer #" + id);
        }

        private JTable table() {
            return accessible(root, "Customer accounts table", JTable.class);
        }

        private JTextField field(String name) {
            return accessible(root, name, JTextField.class);
        }

        private JComboBox<?> type() {
            return accessible(root, "Customer type", JComboBox.class);
        }

        private CrmPresenter.CustomerDraft draft() throws Exception {
            AtomicReference<CrmPresenter.CustomerDraft> result = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> result.set(new CrmPresenter.CustomerDraft(
                    field("Customer or business name").getText(), field("Customer contact person").getText(),
                    field("Customer email").getText(), field("Customer phone").getText(),
                    String.valueOf(type().getSelectedItem()), field("Customer notes").getText())));
            return result.get();
        }

        private void setDraft(CrmPresenter.CustomerDraft value) throws Exception {
            SwingUtilities.invokeAndWait(() -> setDraftOnEdt(value));
        }

        private void setDraftOnEdt(CrmPresenter.CustomerDraft value) {
            field("Customer or business name").setText(value.name());
            field("Customer contact person").setText(value.contact());
            field("Customer email").setText(value.email());
            field("Customer phone").setText(value.phone());
            type().setSelectedItem(value.type());
            field("Customer notes").setText(value.notes());
        }

        private void assertDraft(CrmPresenter.CustomerDraft expected) throws Exception {
            assertThat(draft()).isEqualTo(expected);
        }

        private void assertEmpty() throws Exception {
            assertDraft(new CrmPresenter.CustomerDraft("", "", "", "", "Wholesale", ""));
        }

        private void assertClean() throws Exception {
            SwingUtilities.invokeAndWait(() -> assertThat(tab.applicationExitWarning()).isNull());
        }

        private void assertCustomerWarning() throws Exception {
            SwingUtilities.invokeAndWait(() -> assertThat(tab.applicationExitWarning())
                    .isNotNull().containsIgnoringCase("customer"));
        }

        private void assertNoWrites() {
            verify(customers, never()).add(any(Customer.class));
            verify(customers, never()).update(anyLong(), any(Customer.class));
            verify(customers, never()).delete(anyLong());
        }
    }

    private static Customer customer(long id) {
        return new Customer(id, id == FIRST_ID ? "Harbor Floral" : "Kitsap Blooms",
                "Sam Rivera", "sam@example.test", "360-555-0184", "WHOLESALE", "Thursday pickup");
    }

    private static CrmPresenter.CustomerDraft savedDraft(long id) {
        Customer customer = customer(id);
        return new CrmPresenter.CustomerDraft(customer.getName(), customer.getContactName(),
                customer.getEmail(), customer.getPhone(), "Wholesale", customer.getNotes());
    }

    private static CrmPresenter.CustomerDraft withNotes(String notes) {
        return new CrmPresenter.CustomerDraft(RAW.name(), RAW.contact(), RAW.email(), RAW.phone(), RAW.type(), notes);
    }

    private static <T extends Component> T accessible(Component root, String name, Class<T> type) {
        if (type.isInstance(root) && root.getAccessibleContext() != null
                && name.equals(root.getAccessibleContext().getAccessibleName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T result = accessible(child, name, type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JButton button(Component root, String text) {
        if (root instanceof JButton candidate && text.equals(candidate.getText())) {
            return candidate;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton result = button(child, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static final class Gate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        private void awaitRelease() throws InterruptedException {
            entered.countDown();
            assertThat(released.await(5, TimeUnit.SECONDS)).isTrue();
        }

        private void assertEntered() throws InterruptedException {
            assertThat(entered.await(4, TimeUnit.SECONDS)).isTrue();
        }

        private void release() {
            released.countDown();
        }
    }

    private static final class Host implements TabHost {
        @Override public void refreshAll() { }
        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }
}
