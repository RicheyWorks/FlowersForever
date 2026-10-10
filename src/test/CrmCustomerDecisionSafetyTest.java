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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Customer actions recheck current CRM state at their entry points")
class CrmCustomerDecisionSafetyTest {
    private static final long FIRST_ID = 11L;
    private static final long SECOND_ID = 22L;
    private static final CrmPresenter.CustomerDraft RAW_DRAFT = new CrmPresenter.CustomerDraft(
            "  Harbor Floral revised  ", "  Maya Rivera  ", "maya@example.test",
            "  360-555-0191 ext 02  ", "Florist", "  Keep these exact delivery details  ");

    @ParameterizedTest(name = "{0} Enter during {1}")
    @CsvSource({
            "create, failed", "edit, failed",
            "create, inflight", "edit, inflight",
            "create, queued", "edit, queued",
            "create, viewer", "edit, viewer"
    })
    void notesEnterCannotBypassDisabledSaveActions(String action, String state) throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareDraft("edit".equals(action));
        Gate read = fixture.enterBlockedState(state);
        try {
            assertThat(fixture.user.isButtonEnabled("edit".equals(action) ? "Save edit" : "Add customer"))
                    .isFalse();
            SwingUtilities.invokeAndWait(() -> {
                fixture.notes().postActionEvent();
                assertThat(fixture.tab.hasPendingSave())
                        .as("Enter must obey the same save guard as the disabled button").isFalse();
            });
            fixture.assertRawDraft();
            fixture.assertNoCustomerWrites();
        } finally {
            fixture.releaseRead(read);
        }
    }

    @ParameterizedTest(name = "double-click while {0}")
    @ValueSource(strings = {"inflight", "queued", "viewer"})
    void tableDoubleClickCannotLoadOverDraftWhileLoadActionIsUnavailable(String state) throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareDraft(false);
        Gate read = fixture.enterBlockedState(state);
        try {
            assertThat(fixture.user.isButtonEnabled("Load selected")).isFalse();
            SwingUtilities.invokeAndWait(fixture::doubleClickSelectedCustomer);
            fixture.assertRawDraft();
            fixture.assertNoCustomerWrites();
        } finally {
            fixture.releaseRead(read);
        }
    }

    @ParameterizedTest(name = "pending {0} retains exact fields")
    @ValueSource(strings = {"create", "edit"})
    void pendingSaveRejectsEnterAndDoubleClickAndFailedWriteRetainsRawForm(String action)
            throws Exception {
        Fixture fixture = new Fixture();
        boolean editing = "edit".equals(action);
        fixture.prepareDraft(editing);
        Gate write = new Gate();
        if (editing) {
            when(fixture.customers.update(eq(FIRST_ID), any(Customer.class))).thenAnswer(invocation -> {
                write.awaitRelease();
                throw new IllegalStateException("customer database busy");
            });
        } else {
            when(fixture.customers.add(any(Customer.class))).thenAnswer(invocation -> {
                write.awaitRelease();
                throw new IllegalStateException("customer database busy");
            });
        }
        try {
            fixture.user.click(editing ? "Save edit" : "Add customer");
            write.assertEntered();
            SwingUtilities.invokeAndWait(() -> {
                assertThat(fixture.customerTable().isEnabled()).isFalse();
                fixture.notes().postActionEvent();
                fixture.doubleClickSelectedCustomer();
            });
            fixture.assertRawDraft();
        } finally {
            write.release();
        }
        fixture.user.awaitText("Customer not saved · retry available");
        fixture.assertRawDraft();
        assertThat(fixture.tab.hasPendingSave()).isFalse();
        if (editing) {
            verify(fixture.customers, times(1)).update(eq(FIRST_ID), any(Customer.class));
            verify(fixture.customers, never()).add(any(Customer.class));
        } else {
            verify(fixture.customers, times(1)).add(any(Customer.class));
            verify(fixture.customers, never()).update(anyLong(), any(Customer.class));
        }
    }

    @ParameterizedTest(name = "pending deletion with {0} customer draft")
    @ValueSource(strings = {"create", "edit"})
    void pendingDeleteRejectsEnterAndDoubleClickWithoutLosingCustomerForm(String action)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareDraft("edit".equals(action));
        Gate write = new Gate();
        doAnswer(invocation -> {
            write.awaitRelease();
            throw new IllegalStateException("customer has linked orders");
        }).when(fixture.customers).delete(SECOND_ID);
        try {
            fixture.user.click("Delete selected");
            write.assertEntered();
            SwingUtilities.invokeAndWait(() -> {
                assertThat(fixture.customerTable().isEnabled()).isFalse();
                fixture.notes().postActionEvent();
                fixture.doubleClickSelectedCustomer();
            });
            fixture.assertRawDraft();
        } finally {
            write.release();
        }
        fixture.user.awaitText("Customer not deleted · retry available");
        fixture.assertRawDraft();
        verify(fixture.customers, times(1)).delete(SECOND_ID);
        verify(fixture.customers, never()).add(any(Customer.class));
        verify(fixture.customers, never()).update(anyLong(), any(Customer.class));
    }

    @ParameterizedTest(name = "delete approval after {0}")
    @ValueSource(strings = {
            "viewer", "host-viewer", "selection", "changed-form", "changed-edit-target",
            "changed-name", "changed-contact", "changed-phone",
            "changed-email", "changed-type", "changed-notes", "missing",
            "failed", "inflight", "queued"
    })
    void deleteApprovalRechecksReviewedCustomerAccessAndFreshness(String change) throws Exception {
        Fixture fixture = new Fixture();
        SwingUtilities.invokeAndWait(() -> fixture.select(FIRST_ID));
        AtomicReference<Gate> read = new AtomicReference<>();
        fixture.duringConfirmation = () -> {
            switch (change) {
                case "viewer" -> fixture.tab.applyRolePermissions(false);
                case "host-viewer" -> fixture.host.writable = false;
                case "selection" -> fixture.select(SECOND_ID);
                case "changed-form" -> fixture.notes().setText("Keep newly entered customer details");
                case "changed-edit-target" -> {
                    fixture.select(SECOND_ID);
                    button(fixture.root, "Load selected").doClick();
                    fixture.select(FIRST_ID);
                }
                case "failed" -> {
                    fixture.failRead.set(true);
                    fixture.refreshInsideModal();
                }
                case "inflight", "queued" -> {
                    Gate gate = new Gate();
                    read.set(gate);
                    fixture.nextRead.set(gate);
                    fixture.tab.refreshData();
                    if ("queued".equals(change)) {
                        fixture.tab.refreshData();
                    }
                }
                default -> {
                    fixture.rows.set("missing".equals(change)
                            ? List.of(customer(SECOND_ID))
                            : List.of(changedCustomer(change), customer(SECOND_ID)));
                    fixture.refreshInsideModal();
                }
            }
        };
        try {
            fixture.user.click("Delete selected");
            assertThat(fixture.confirmations).hasValue(1);
            assertThat(fixture.tab.hasPendingSave()).isFalse();
            fixture.assertNoCustomerWrites();
        } finally {
            fixture.releaseRead(read.get());
        }
    }

    @ParameterizedTest(name = "unchanged customer after refreshed snapshot: {0}")
    @ValueSource(booleans = {false, true})
    void sameReviewedCustomerCanBeDeletedAfterAnUnchangedSuccessfulRefresh(boolean refresh)
            throws Exception {
        Fixture fixture = new Fixture();
        SwingUtilities.invokeAndWait(() -> fixture.select(FIRST_ID));
        if (refresh) {
            fixture.duringConfirmation = () -> {
                // New source instances and a different row order must not invalidate unchanged facts.
                fixture.rows.set(List.of(customer(SECOND_ID), customer(FIRST_ID)));
                fixture.refreshInsideModal();
            };
        }
        doAnswer(invocation -> {
            fixture.rows.updateAndGet(rows -> rows.stream()
                    .filter(row -> !Long.valueOf(FIRST_ID).equals(row.getId())).toList());
            return null;
        }).when(fixture.customers).delete(FIRST_ID);

        fixture.user.click("Delete selected");
        fixture.user.awaitText("Customer deleted · “Harbor Floral”");

        assertThat(fixture.confirmations).hasValue(1);
        verify(fixture.customers, times(1)).delete(FIRST_ID);
        verify(fixture.customers, never()).delete(SECOND_ID);
        assertThat(fixture.tab.hasPendingSave()).isFalse();
    }

    @ParameterizedTest(name = "customer deletion declined with response {0}")
    @ValueSource(ints = {JOptionPane.NO_OPTION, JOptionPane.CLOSED_OPTION})
    void declinedCustomerDeleteDoesNotWrite(int response) throws Exception {
        Fixture fixture = new Fixture();
        fixture.confirmResponse = response;
        SwingUtilities.invokeAndWait(() -> fixture.select(FIRST_ID));

        fixture.user.click("Delete selected");

        assertThat(fixture.confirmations).hasValue(1);
        fixture.assertNoCustomerWrites();
    }

    @ParameterizedTest(name = "older delete after failed nested {0}")
    @ValueSource(strings = {"create", "edit", "delete"})
    void olderDeleteApprovalCannotOutliveANewerFailedCustomerWrite(String action) throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareDraft("edit".equals(action));
        when(fixture.customers.add(any(Customer.class)))
                .thenThrow(new IllegalStateException("newer customer save failed"));
        when(fixture.customers.update(eq(FIRST_ID), any(Customer.class)))
                .thenThrow(new IllegalStateException("newer customer edit failed"));
        doAnswer(invocation -> {
            throw new IllegalStateException("newer customer delete failed");
        }).when(fixture.customers).delete(SECOND_ID);
        fixture.duringConfirmation = () -> {
            if ("delete".equals(action)) {
                fixture.tab.startCustomerDelete(SECOND_ID, "Kitsap Blooms");
            } else {
                button(fixture.root, "edit".equals(action) ? "Save edit" : "Add customer").doClick();
            }
            assertThat(fixture.tab.hasPendingSave()).isTrue();
            fixture.awaitInsideModal(() -> !fixture.tab.hasPendingSave());
        };

        fixture.user.click("Delete selected");

        fixture.assertRawDraft();
        assertThat(fixture.confirmations).hasValue(1);
        assertThat(fixture.tab.hasPendingSave()).isFalse();
        verify(fixture.customers, times("create".equals(action) ? 1 : 0)).add(any(Customer.class));
        verify(fixture.customers, times("edit".equals(action) ? 1 : 0))
                .update(eq(FIRST_ID), any(Customer.class));
        verify(fixture.customers, times("delete".equals(action) ? 1 : 0)).delete(SECOND_ID);
        verify(fixture.customers, never()).delete(FIRST_ID);
    }

    @Test
    void currentHostRoleIsRecheckedEvenBeforeTheUiReceivesPermissionRefresh() throws Exception {
        Fixture fixture = new Fixture();
        fixture.prepareDraft(true);
        SwingUtilities.invokeAndWait(() -> {
            fixture.host.writable = false;
            fixture.notes().postActionEvent();
            fixture.doubleClickSelectedCustomer();
            assertThat(fixture.tab.hasPendingSave()).isFalse();
        });
        fixture.assertRawDraft();
        fixture.assertNoCustomerWrites();
    }

    private static final class Fixture {
        private final CustomerService customers = mock(CustomerService.class);
        private final OrderService orders = mock(OrderService.class);
        private final FarmDialogs dialogs = mock(FarmDialogs.class);
        private final AtomicReference<List<Customer>> rows = new AtomicReference<>(
                List.of(customer(FIRST_ID), customer(SECOND_ID)));
        private final AtomicBoolean failRead = new AtomicBoolean();
        private final AtomicReference<Gate> nextRead = new AtomicReference<>();
        private final AtomicInteger confirmations = new AtomicInteger();
        private final Host host = new Host();
        private final CrmTab tab;
        private final JComponent root;
        private final SwingUserHarness user;
        private Runnable duringConfirmation = () -> { };
        private int confirmResponse = JOptionPane.YES_OPTION;

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
            when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> {
                        assertThat((String) invocation.getArgument(2)).isEqualTo("Delete customer");
                        assertThat((Integer) invocation.getArgument(3)).isEqualTo(JOptionPane.YES_NO_OPTION);
                        confirmations.incrementAndGet();
                        duringConfirmation.run();
                        return confirmResponse;
                    });
            tab = new CrmTab(customers, orders, host, dialogs);
            JComponent[] built = new JComponent[1];
            SwingUtilities.invokeAndWait(() -> {
                built[0] = tab.getUIComponent();
                tab.initialize();
            });
            root = built[0];
            user = SwingUserHarness.on(root);
            awaitRefresh();
        }

        private void prepareDraft(boolean editing) throws Exception {
            SwingUtilities.invokeAndWait(() -> select(FIRST_ID));
            if (editing) {
                user.click("Load selected");
            }
            SwingUtilities.invokeAndWait(() -> {
                field("Customer or business name").setText(RAW_DRAFT.name());
                field("Customer contact person").setText(RAW_DRAFT.contact());
                field("Customer email").setText(RAW_DRAFT.email());
                field("Customer phone").setText(RAW_DRAFT.phone());
                accessible(root, "Customer type", JComboBox.class).setSelectedItem(RAW_DRAFT.type());
                notes().setText(RAW_DRAFT.notes());
                select(SECOND_ID);
            });
        }

        private Gate enterBlockedState(String state) throws Exception {
            if ("viewer".equals(state)) {
                SwingUtilities.invokeAndWait(() -> tab.applyRolePermissions(false));
                return null;
            }
            if ("failed".equals(state)) {
                failRead.set(true);
                SwingUtilities.invokeAndWait(tab::refreshData);
                awaitRefresh();
                user.awaitText("CRM refresh failed");
                return null;
            }
            Gate gate = new Gate();
            nextRead.set(gate);
            SwingUtilities.invokeAndWait(() -> {
                tab.refreshData();
                if ("queued".equals(state)) {
                    tab.refreshData();
                }
            });
            gate.assertEntered();
            return gate;
        }

        private void releaseRead(Gate read) {
            if (read != null) {
                read.release();
                awaitRefresh();
            }
        }

        private void awaitRefresh() {
            user.awaitButtonEnabled("Refresh CRM", true);
        }

        /** A modal Swing dialog dispatches asynchronous refresh completion before returning Yes. */
        private void refreshInsideModal() {
            assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
            tab.refreshData();
            awaitInsideModal(() -> button(root, "Refresh CRM").isEnabled());
        }

        private void awaitInsideModal(java.util.function.BooleanSupplier finished) {
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

        private void select(long id) {
            JTable table = customerTable();
            for (int row = 0; row < table.getRowCount(); row++) {
                if (Long.valueOf(id).equals(table.getValueAt(row, 0))) {
                    table.setRowSelectionInterval(row, row);
                    return;
                }
            }
            throw new AssertionError("Missing customer #" + id);
        }

        private void doubleClickSelectedCustomer() {
            JTable table = customerTable();
            assertThat(table.getSelectedRow()).isNotNegative();
            MouseEvent click = new MouseEvent(table, MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0, 10, 10, 2, false, MouseEvent.BUTTON1);
            for (java.awt.event.MouseListener listener : table.getMouseListeners()) {
                listener.mouseClicked(click);
            }
        }

        private JTable customerTable() {
            return accessible(root, "Customer accounts table", JTable.class);
        }

        private JTextField notes() {
            return field("Customer notes");
        }

        private JTextField field(String name) {
            return accessible(root, name, JTextField.class);
        }

        private void assertRawDraft() throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                assertThat(field("Customer or business name").getText()).isEqualTo(RAW_DRAFT.name());
                assertThat(field("Customer contact person").getText()).isEqualTo(RAW_DRAFT.contact());
                assertThat(field("Customer email").getText()).isEqualTo(RAW_DRAFT.email());
                assertThat(field("Customer phone").getText()).isEqualTo(RAW_DRAFT.phone());
                assertThat(accessible(root, "Customer type", JComboBox.class).getSelectedItem())
                        .isEqualTo(RAW_DRAFT.type());
                assertThat(notes().getText()).isEqualTo(RAW_DRAFT.notes());
            });
        }

        private void assertNoCustomerWrites() {
            verify(customers, never()).add(any(Customer.class));
            verify(customers, never()).update(anyLong(), any(Customer.class));
            verify(customers, never()).delete(anyLong());
        }
    }

    private static Customer customer(long id) {
        return new Customer(id, id == FIRST_ID ? "Harbor Floral" : "Kitsap Blooms",
                "Sam Rivera", "sam@example.test", "360-555-0184", "WHOLESALE", "Thursday pickup");
    }

    private static Customer changedCustomer(String change) {
        return new Customer(FIRST_ID,
                "changed-name".equals(change) ? "Harbor Floral renamed" : "Harbor Floral",
                "changed-contact".equals(change) ? "Current contact" : "Sam Rivera",
                "changed-email".equals(change) ? "current@example.test" : "sam@example.test",
                "changed-phone".equals(change) ? "360-555-0123" : "360-555-0184",
                "changed-type".equals(change) ? "FLORIST" : "WHOLESALE",
                "changed-notes".equals(change) ? "Current delivery directions" : "Thursday pickup");
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
            assertThat(released.await(5, TimeUnit.SECONDS)).as("test releases the blocked worker").isTrue();
        }

        private void assertEntered() throws InterruptedException {
            assertThat(entered.await(4, TimeUnit.SECONDS)).as("worker reached blocked operation").isTrue();
        }

        private void release() {
            released.countDown();
        }
    }

    private static final class Host implements TabHost {
        private boolean writable = true;
        @Override public boolean canMutateData() { return writable; }
        @Override public void refreshAll() { }
        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }
}
