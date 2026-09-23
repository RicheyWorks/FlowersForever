package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.presenter.InventoryItemPresenter;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Inventory mutation commit and refresh recovery")
class InventoryMutationRecoveryTest {
    private static final long PRODUCT_ID = 11L;
    private static final String READ_FAILURE = "inventory readback unavailable";
    private static final InventoryItemPresenter.ItemDraft EDIT_DRAFT =
            new InventoryItemPresenter.ItemDraft("Nootka Rose Premium", "Flowers/Plants",
                    "Nootka", "5.00", "Per Stem", "1.20", "7", "Cooler row 2");

    @ParameterizedTest(name = "{0}: a committed write waits for a successful refresh")
    @EnumSource(Mutation.class)
    void committedMutationDoesNotBecomeRetryableWhenReadbackFails(Mutation mutation)
            throws Exception {
        Fixture fixture = new Fixture(mutation);
        fixture.failReadsAfterCommit.set(true);
        fixture.initialize();

        onEdt(() -> mutation.start(fixture.tab));
        fixture.awaitIdle();
        onEdt(() -> {
            assertCommittedRecovery(fixture, mutation);
            assertMutationButtonsDisabled(fixture.root);
            assertNoRetryDraft(fixture.tab);
        });

        // A stale row remains unsafe when filtering or selecting another product.
        onEdt(() -> {
            String warning = status(fixture.root).getText();
            String recovery = status(fixture.root).getAccessibleContext()
                    .getAccessibleDescription();
            search(fixture.root).setText("no matching inventory product");
            assertThat(status(fixture.root).getText()).isEqualTo(warning);
            search(fixture.root).setText("");
            JToggleButton lowStock = find(fixture.root, JToggleButton.class).stream()
                    .filter(button -> "inventory.lowStockOnly".equals(button.getName()))
                    .findFirst().orElseThrow();
            lowStock.doClick();
            lowStock.doClick();
            if (table(fixture.root).getRowCount() > 0) {
                table(fixture.root).setRowSelectionInterval(0, 0);
            }
            fixture.tab.applyRolePermissions(true);
            assertThat(status(fixture.root).getText()).isEqualTo(warning);
            assertThat(status(fixture.root).getAccessibleContext()
                    .getAccessibleDescription()).isEqualTo(recovery);
            assertMutationButtonsDisabled(fixture.root);
            invokeAllMutations(fixture.tab);
        });

        onEdt(fixture.tab::startInventoryRefresh);
        fixture.awaitIdle();
        onEdt(() -> {
            assertCommittedRecovery(fixture, mutation);
            assertMutationButtonsDisabled(fixture.root);
            assertNoRetryDraft(fixture.tab);
        });
        assertThat(fixture.writes).hasValue(1);

        fixture.failReadsAfterCommit.set(false);
        onEdt(fixture.tab::startInventoryRefresh);
        fixture.awaitIdle();
        onEdt(() -> {
            fixture.selectFirstRow();
            assertThat(button(fixture.root, "Record stock count…").isEnabled()).isTrue();
            assertThat(button(fixture.root, "Edit selected").isEnabled()).isTrue();
            assertThat(button(fixture.root, "Delete selected").isEnabled()).isTrue();
            assertThat(status(fixture.root).getAccessibleContext().getAccessibleDescription())
                    .doesNotContain(READ_FAILURE);
            assertRefreshedRows(fixture, mutation);
            // Null means "retry retained edit"; a committed edit must leave none.
            fixture.tab.startSelectedItemEdit(null);
            assertNoRetryDraft(fixture.tab);
        });
        assertThat(fixture.writes).hasValue(1);
    }

    @ParameterizedTest(name = "{0}: another tab's refresh failure cannot undo the write")
    @EnumSource(Mutation.class)
    void hostRefreshExceptionPreservesCommittedResult(Mutation mutation) throws Exception {
        Fixture fixture = new Fixture(mutation);
        fixture.host.failRefresh = true;
        fixture.initialize();

        onEdt(() -> mutation.start(fixture.tab));
        fixture.awaitIdle();

        onEdt(() -> {
            assertCommittedStatus(status(fixture.root).getText(), mutation);
            assertThat(fixture.host.status.toLowerCase(Locale.ROOT))
                    .doesNotContain("not saved", "not updated", "not deleted");
            assertNoRetryDraft(fixture.tab);
        });
        assertThat(fixture.writes).hasValue(1);
        assertThat(fixture.host.refreshes).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}: a queued refresh cannot race the pending write")
    @EnumSource(Mutation.class)
    void refreshRequestedDuringMutationWaitsForWriteResolution(Mutation mutation)
            throws Exception {
        Fixture fixture = new Fixture(mutation);
        fixture.blockWrite = true;
        fixture.initialize();
        onEdt(() -> mutation.start(fixture.tab));
        assertThat(fixture.writeEntered.await(3, TimeUnit.SECONDS)).isTrue();
        try {
            onEdt(fixture.tab::startInventoryRefresh);
            assertThat(fixture.readDuringWrite.await(200, TimeUnit.MILLISECONDS))
                    .as("refresh must wait until the database write resolves")
                    .isFalse();
            assertThat(fixture.reads).hasValue(1);
        } finally {
            fixture.releaseWrite.countDown();
        }
        fixture.awaitIdle();

        onEdt(() -> assertRefreshedRows(fixture, mutation));
        assertThat(fixture.writes).hasValue(1);
        assertThat(fixture.reads.get()).isGreaterThanOrEqualTo(2);
        assertThat(fixture.readDuringWrite.getCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("direct mutation helpers cannot bypass disabled actions during a refresh")
    void refreshBlocksAllMutationEntryPoints() throws Exception {
        Fixture fixture = new Fixture(Mutation.COUNT);
        fixture.initialize();
        fixture.blockRefresh.set(true);
        onEdt(fixture.tab::startInventoryRefresh);
        assertThat(fixture.refreshEntered.await(3, TimeUnit.SECONDS)).isTrue();
        try {
            onEdt(() -> {
                fixture.tab.applyRolePermissions(true);
                assertMutationButtonsDisabled(fixture.root);
                invokeAllMutations(fixture.tab);
            });
        } finally {
            fixture.releaseRefresh.countDown();
        }
        fixture.awaitIdle();
        assertThat(fixture.writes).hasValue(0);
    }

    @ParameterizedTest(name = "{0}: pending mutation blocks every other helper")
    @EnumSource(Mutation.class)
    void pendingMutationBlocksOtherMutationEntryPoints(Mutation mutation) throws Exception {
        Fixture fixture = new Fixture(mutation);
        fixture.blockWrite = true;
        fixture.initialize();
        onEdt(() -> mutation.start(fixture.tab));
        assertThat(fixture.writeEntered.await(3, TimeUnit.SECONDS)).isTrue();
        try {
            onEdt(() -> {
                fixture.tab.applyRolePermissions(true);
                invokeAllMutations(fixture.tab);
            });
        } finally {
            fixture.releaseWrite.countDown();
        }
        fixture.awaitIdle();
        assertThat(fixture.writes).hasValue(1);
        assertThat(fixture.deletes).hasValue(mutation == Mutation.DELETE ? 1 : 0);
        assertThat(fixture.updates).hasValue(mutation == Mutation.DELETE ? 0 : 1);
    }

    @Test
    @DisplayName("viewer permissions apply to direct mutation entry points")
    void viewerCannotBypassDisabledMutationActions() throws Exception {
        Fixture fixture = new Fixture(Mutation.COUNT);
        fixture.initialize();
        onEdt(() -> {
            fixture.tab.applyRolePermissions(false);
            assertMutationButtonsDisabled(fixture.root);
            invokeAllMutations(fixture.tab);
        });
        fixture.awaitIdle();
        assertThat(fixture.writes).hasValue(0);
    }

    private static void assertCommittedRecovery(Fixture fixture, Mutation mutation) {
        JLabel label = status(fixture.root);
        assertCommittedStatus(label.getText(), mutation);
        assertThat(label.getText().toLowerCase(Locale.ROOT)).contains("refresh");
        assertThat(label.getAccessibleContext().getAccessibleDescription()
                .toLowerCase(Locale.ROOT)).contains(READ_FAILURE, "refresh")
                .doesNotContain("retained for retry", "retry count", "retry edit");
        assertThat(fixture.host.status.toLowerCase(Locale.ROOT))
                .doesNotContain("not saved", "not updated", "not deleted");
        assertThat(button(fixture.root, "Refresh").isEnabled()).isTrue();
    }

    private static void assertCommittedStatus(String text, Mutation mutation) {
        String normalized = text.toLowerCase(Locale.ROOT);
        assertThat(normalized).doesNotContain("not saved", "not updated", "not deleted");
        if (mutation == Mutation.DELETE) {
            assertThat(normalized).contains("deleted");
        } else {
            assertThat(normalized).containsAnyOf("saved", "updated");
        }
    }

    private static void assertRefreshedRows(Fixture fixture, Mutation mutation) {
        JTable table = table(fixture.root);
        List<String> names = new ArrayList<>();
        for (int row = 0; row < table.getRowCount(); row++) {
            names.add(String.valueOf(table.getValueAt(row, 0)));
        }
        if (mutation == Mutation.DELETE) {
            assertThat(names).containsExactly("Dahlia Mix");
        } else {
            String savedName = mutation == Mutation.EDIT ? "Nootka Rose Premium" : "Nootka Rose";
            assertThat(names).containsExactlyInAnyOrder(savedName, "Dahlia Mix");
            int savedRow = names.indexOf(savedName);
            assertThat(table.getValueAt(savedRow, 5)).isEqualTo(7);
        }
    }

    private static void assertNoRetryDraft(InventoryTab tab) {
        for (String fieldName : List.of("retryCountQuantity", "retryCountItem",
                "retryEditDraft", "retryEditItem")) {
            try {
                Field field = InventoryTab.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                assertThat(field.get(tab)).as(fieldName).isNull();
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Cannot inspect mutation retry state", exception);
            }
        }
    }

    private static void assertMutationButtonsDisabled(Component root) {
        assertThat(button(root, "Record stock count…").isEnabled()).isFalse();
        assertThat(button(root, "Edit selected").isEnabled()).isFalse();
        assertThat(button(root, "Delete selected").isEnabled()).isFalse();
    }

    private static void invokeAllMutations(InventoryTab tab) {
        tab.startSelectedStockCountSave(7, 5, false);
        tab.startSelectedItemEdit(EDIT_DRAFT);
        tab.startSelectedItemDelete();
    }

    private enum Mutation {
        COUNT, EDIT, DELETE;

        void start(InventoryTab tab) {
            switch (this) {
                case COUNT -> tab.startSelectedStockCountSave(7, 5, true);
                case EDIT -> tab.startSelectedItemEdit(EDIT_DRAFT);
                case DELETE -> tab.startSelectedItemDelete();
            }
        }
    }

    private static final class Fixture {
        private final InventoryService service = mock(InventoryService.class);
        private final TrackingHost host = new TrackingHost();
        private final InventoryTab tab = new InventoryTab(service, host);
        private final AtomicBoolean committed = new AtomicBoolean();
        private final AtomicBoolean failReadsAfterCommit = new AtomicBoolean();
        private final AtomicBoolean blockRefresh = new AtomicBoolean();
        private final AtomicBoolean writePending = new AtomicBoolean();
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicInteger updates = new AtomicInteger();
        private final AtomicInteger deletes = new AtomicInteger();
        private final CountDownLatch writeEntered = new CountDownLatch(1);
        private final CountDownLatch releaseWrite = new CountDownLatch(1);
        private final CountDownLatch readDuringWrite = new CountDownLatch(1);
        private final CountDownLatch refreshEntered = new CountDownLatch(1);
        private final CountDownLatch releaseRefresh = new CountDownLatch(1);
        private boolean blockWrite;
        private JComponent root;

        private Fixture(Mutation mutation) {
            Item original = new Item(PRODUCT_ID, "Nootka Rose", "Flowers/Plants",
                    4.5, "Per Stem", 1.2, 2, "Field A");
            Item next = new Item(22L, "Dahlia Mix", "Flowers/Plants",
                    3.5, "Per Stem", 1.0, 10, "Field B");
            Item saved = new Item(PRODUCT_ID,
                    mutation == Mutation.EDIT ? "Nootka Rose Premium" : "Nootka Rose",
                    "Flowers/Plants", 5.0, "Per Stem", 1.2, 7, "Cooler row 2");
            when(service.getAllItems()).thenAnswer(invocation -> {
                reads.incrementAndGet();
                if (writePending.get()) {
                    readDuringWrite.countDown();
                }
                if (blockRefresh.get()) {
                    refreshEntered.countDown();
                    assertThat(releaseRefresh.await(5, TimeUnit.SECONDS)).isTrue();
                }
                if (committed.get() && failReadsAfterCommit.get()) {
                    throw new IllegalStateException(READ_FAILURE);
                }
                return committed.get()
                        ? mutation == Mutation.DELETE ? List.of(next) : List.of(saved, next)
                        : List.of(original, next);
            });
            when(service.updateById(anyLong(), any(Item.class))).thenAnswer(invocation -> {
                updates.incrementAndGet();
                commitWrite();
                return saved;
            });
            doAnswer(invocation -> {
                deletes.incrementAndGet();
                commitWrite();
                return null;
            }).when(service).deleteById(anyLong());
        }

        private void commitWrite() throws InterruptedException {
            writes.incrementAndGet();
            writePending.set(true);
            writeEntered.countDown();
            if (blockWrite) {
                assertThat(releaseWrite.await(5, TimeUnit.SECONDS)).isTrue();
            }
            committed.set(true);
            writePending.set(false);
        }

        private void initialize() throws Exception {
            onEdt(() -> {
                root = tab.getUIComponent();
                tab.initialize();
            });
            awaitIdle();
            onEdt(() -> {
                JTable table = table(root);
                for (int row = 0; row < table.getRowCount(); row++) {
                    if ("Nootka Rose".equals(table.getValueAt(row, 0))) {
                        table.setRowSelectionInterval(row, row);
                        return;
                    }
                }
                throw new AssertionError("Original product is missing");
            });
        }

        private void selectFirstRow() {
            table(root).setRowSelectionInterval(0, 0);
        }

        private void awaitIdle() throws Exception {
            await(() -> button(root, "Refresh").isEnabled()
                    && find(root, JButton.class).stream().noneMatch(button ->
                            List.of("Saving count…", "Saving edit…", "Deleting…")
                                    .contains(button.getText()))
                    && !status(root).getText().startsWith("Refreshing inventory")
                    && !status(root).getText().startsWith("Refresh queued"));
        }
    }

    private static void await(BooleanSupplier condition) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            boolean[] done = new boolean[1];
            onEdt(() -> done[0] = condition.getAsBoolean());
            if (done[0]) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for inventory work to finish");
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static JTable table(Component root) {
        return find(root, JTable.class).get(0);
    }

    private static JTextField search(Component root) {
        return find(root, JTextField.class).stream().filter(field ->
                "Inventory search".equals(field.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JLabel status(Component root) {
        return find(root, JLabel.class).stream().filter(label ->
                "Inventory results and selected product".equals(
                        label.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JButton button(Component root, String text) {
        return find(root, JButton.class).stream()
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> find(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(root)) {
            matches.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                matches.addAll(find(child, type));
            }
        }
        return matches;
    }

    private static final class TrackingHost implements TabHost {
        private String status = "";
        private int refreshes;
        private boolean failRefresh;

        @Override public void refreshAll() {
            refreshes++;
            if (failRefresh) {
                throw new IllegalStateException("Dashboard refresh unavailable");
            }
        }

        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) { status = message; }
        @Override public void runTrendAnalysis() { }
    }
}
