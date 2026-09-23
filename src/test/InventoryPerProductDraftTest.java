package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.gui.presenter.InventoryItemPresenter;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Inventory retry drafts belong to each product and operation")
class InventoryPerProductDraftTest {
    private static final long ROSE_ID = 11L;
    private static final long DAHLIA_ID = 22L;
    private static final InventoryItemPresenter.ItemDraft ROSE_EDIT =
            new InventoryItemPresenter.ItemDraft("Nootka Rose Premium", "Flowers/Plants",
                    "Nootka", "7.2500", "Per Gallon", "2.1500", "0037", "  Cooler A draft  ");
    private static final InventoryItemPresenter.ItemDraft DAHLIA_EDIT =
            new InventoryItemPresenter.ItemDraft("Dahlia Market Mix", "Other",
                    "", "8.5000", "Per Unit", "3.7500", "0047", "  Cooler B draft  ");

    @ParameterizedTest(name = "{0}: failing a second product preserves both exact drafts")
    @EnumSource(Operation.class)
    void failedWritesKeepIndependentDraftsForBothProducts(Operation operation) throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(operation, ROSE_ID);
        fixture.fail(operation, DAHLIA_ID);

        fixture.expectDraft(operation, ROSE_ID);
        fixture.expectDraft(operation, DAHLIA_ID);
        // Closing either review without saving must not discard an earlier failure.
        fixture.expectDraft(operation, ROSE_ID);
        fixture.assertUnfinished();
        assertThat(fixture.writes).hasValue(2);
    }

    @Test
    void successfulCountClearsOnlyThatProductsCountAndWarnsUntilTheOtherIsSaved()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(Operation.COUNT, ROSE_ID);
        fixture.fail(Operation.COUNT, DAHLIA_ID);

        fixture.succeed(Operation.COUNT, ROSE_ID);
        fixture.assertUnfinished();
        fixture.expectDraft(Operation.COUNT, DAHLIA_ID);
        // A later authoritative quantity must appear once Rose's draft was resolved.
        fixture.replace(List.of(item(ROSE_ID, "Nootka Rose", 99), fixture.current(DAHLIA_ID)));
        fixture.expectCount("Nootka Rose", 99);
        fixture.succeed(Operation.COUNT, DAHLIA_ID);

        fixture.assertFinished();
        assertThat(fixture.writes).hasValue(4);
    }

    @Test
    void successfulEditClearsOnlyThatProductsEditAndWarnsUntilTheOtherIsSaved()
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(Operation.EDIT, ROSE_ID);
        fixture.fail(Operation.EDIT, DAHLIA_ID);

        fixture.succeed(Operation.EDIT, ROSE_ID);
        fixture.assertUnfinished();
        fixture.expectDraft(Operation.EDIT, DAHLIA_ID);
        // The retry entry point has no edit left to submit for the resolved product.
        fixture.select(ROSE_ID);
        SwingUtilities.invokeAndWait(() -> fixture.tab.startSelectedItemEdit(null));
        fixture.awaitIdle();
        assertThat(fixture.writes).hasValue(3);
        fixture.succeed(Operation.EDIT, DAHLIA_ID);

        fixture.assertFinished();
        assertThat(fixture.writes).hasValue(4);
    }

    @Test
    void savingAnEditLeavesTheSameProductsFailedPhysicalCountAvailable() throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(Operation.COUNT, ROSE_ID);
        fixture.fail(Operation.EDIT, ROSE_ID);

        fixture.succeed(Operation.EDIT, ROSE_ID);

        fixture.assertUnfinished();
        fixture.expectDraft(Operation.COUNT, ROSE_ID);
        fixture.succeed(Operation.COUNT, ROSE_ID);
        fixture.assertFinished();
        assertThat(fixture.writes).hasValue(4);
    }

    @Test
    void savingACountLeavesTheSameProductsCompleteFailedEditAvailable() throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(Operation.EDIT, ROSE_ID);
        fixture.fail(Operation.COUNT, ROSE_ID);

        fixture.succeed(Operation.COUNT, ROSE_ID);

        fixture.assertUnfinished();
        fixture.expectDraft(Operation.EDIT, ROSE_ID);
        fixture.succeed(Operation.EDIT, ROSE_ID);
        fixture.assertFinished();
        assertThat(fixture.writes).hasValue(4);
    }

    @ParameterizedTest(name = "{0}: IDs restore both drafts after rename and row reorder")
    @EnumSource(Operation.class)
    void renamingAndReorderingDoesNotMoveDraftsBetweenProducts(Operation operation)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(operation, ROSE_ID);
        fixture.fail(operation, DAHLIA_ID);

        fixture.replace(List.of(item(DAHLIA_ID, "Zinnia New Name", 88),
                item(ROSE_ID, "Aster New Name", 99)));
        SwingUtilities.invokeAndWait(() -> find(fixture.root, JTable.class)
                .getRowSorter().toggleSortOrder(0));

        fixture.expectDraft(operation, ROSE_ID);
        fixture.expectDraft(operation, DAHLIA_ID);
        fixture.assertUnfinished();
        assertThat(fixture.writes).hasValue(2);
    }

    @ParameterizedTest(name = "{0}: filtering and refresh keep hidden and visible drafts")
    @EnumSource(Operation.class)
    void filteringAndRefreshingDoesNotDiscardEitherProductsDraft(Operation operation)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(operation, ROSE_ID);
        fixture.fail(operation, DAHLIA_ID);

        fixture.user.type("Inventory search", "Dahlia");
        fixture.refresh();
        SwingUtilities.invokeAndWait(() ->
                assertThat(find(fixture.root, JTable.class).getRowCount()).isEqualTo(1));
        fixture.expectDraft(operation, DAHLIA_ID);
        fixture.assertUnfinished();
        fixture.user.type("Inventory search", "no matching product");
        fixture.refresh();
        fixture.assertUnfinished();
        fixture.user.type("Inventory search", "");

        fixture.expectDraft(operation, ROSE_ID);
        fixture.expectDraft(operation, DAHLIA_ID);
        assertThat(fixture.writes).hasValue(2);
    }

    @ParameterizedTest(name = "{0}: a missing product retains its draft and exit warning")
    @EnumSource(Operation.class)
    void absentProductKeepsDraftAfterTheRemainingProductsDraftIsSaved(Operation operation)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(operation, ROSE_ID);
        fixture.fail(operation, DAHLIA_ID);
        fixture.replace(List.of(fixture.current(DAHLIA_ID)));

        fixture.succeed(operation, DAHLIA_ID);
        fixture.assertUnfinished();
        fixture.replace(List.of(fixture.current(DAHLIA_ID), item(ROSE_ID, "Returned Rose", 99)));

        fixture.expectDraft(operation, ROSE_ID);
        fixture.succeed(operation, ROSE_ID);
        fixture.assertFinished();
        assertThat(fixture.writes).hasValue(4);
    }

    @ParameterizedTest(name = "{0}: a replacement with the same name cannot inherit another ID's draft")
    @EnumSource(Operation.class)
    void sameNameReplacementDoesNotInheritTheMissingProductsDraft(Operation operation)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(operation, ROSE_ID);
        fixture.fail(operation, DAHLIA_ID);
        fixture.replace(List.of(item(33L, "Nootka Rose", 99), fixture.current(DAHLIA_ID)));

        if (operation == Operation.COUNT) {
            fixture.expectCount("Nootka Rose", 99);
        } else {
            fixture.expectEdit("Nootka Rose", new InventoryItemPresenter.ItemDraft(
                    "Nootka Rose", "Flowers/Plants", "", "4.50", "Per Stem", "1.20", "99", "Field A"));
        }
        fixture.succeed(operation, DAHLIA_ID);
        fixture.assertUnfinished();
        fixture.replace(List.of(fixture.current(33L), fixture.current(DAHLIA_ID),
                item(ROSE_ID, "Original Rose Returned", 10)));

        fixture.expectDraft(operation, ROSE_ID);
        fixture.succeed(operation, ROSE_ID);
        fixture.assertFinished();
        assertThat(fixture.writes).hasValue(4);
    }

    @ParameterizedTest(name = "{0}: rejecting two changed modal targets retains both drafts")
    @EnumSource(Operation.class)
    void rejectedModalDecisionsRetainBothProductsEnteredValues(Operation operation)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.rejectAfterSelectionChanges(operation, ROSE_ID, DAHLIA_ID);
        fixture.rejectAfterSelectionChanges(operation, DAHLIA_ID, ROSE_ID);

        fixture.expectDraft(operation, ROSE_ID);
        fixture.expectDraft(operation, DAHLIA_ID);
        fixture.assertUnfinished();
        verify(fixture.service, never()).updateById(anyLong(), any(Item.class));
        assertThat(fixture.writes).hasValue(0);
    }

    @ParameterizedTest(name = "{0}: an older modal cannot save over corrections kept by a nested Cancel")
    @EnumSource(Operation.class)
    void staleModalSaveCannotOverwriteCorrectionsFromANestedRetryDialog(Operation operation)
            throws Exception {
        Fixture fixture = new Fixture();
        fixture.fail(operation, ROSE_ID);
        fixture.failingIds.remove(ROSE_ID);
        InventoryItemPresenter.ItemDraft correctedEdit = new InventoryItemPresenter.ItemDraft(
                "Rose Revised", "Flowers/Plants", "Alba", "9.8500", "Per Unit", "3.4500",
                "0083", "  Newer nested correction  ");
        int correctedCount = 83;
        String action = operation == Operation.COUNT ? "Record stock count…" : "Edit selected";
        String name = fixture.current(ROSE_ID).getName();
        if (operation == Operation.COUNT) {
            fixture.dialogs.scripts.choose("Count “" + name + "”", "Save count")
                    .enterSpinnerAndChoose("Count “" + name + "”",
                            "Counted quantity for " + name, correctedCount, "Cancel");
        } else {
            fixture.dialogs.scripts.confirm("Edit “" + name + "”", true)
                    .confirmForm("Edit “" + name + "”", false,
                            form -> fillEdit(form, correctedEdit));
        }
        AtomicInteger inspected = new AtomicInteger();
        fixture.dialogs.inspect = content -> {
            boolean outer = inspected.incrementAndGet() == 1;
            if (operation == Operation.COUNT) {
                assertThat(find(content, JSpinner.class).getValue())
                        .isEqualTo(outer ? count(ROSE_ID) : correctedCount);
            } else {
                assertEdit(content, outer ? ROSE_EDIT : correctedEdit);
            }
        };
        AtomicInteger answered = new AtomicInteger();
        fixture.dialogs.afterAnswer = () -> {
            if (answered.incrementAndGet() == 1) {
                // The outer form still contains its old values while the nested review is open.
                fixture.user.click(action);
            }
        };

        fixture.select(ROSE_ID);
        fixture.user.click(action);
        fixture.dialogs.reset();
        fixture.awaitIdle();

        assertThat(inspected).hasValue(2);
        assertThat(answered).hasValue(2);
        verify(fixture.service, times(1)).updateById(anyLong(), any(Item.class));
        assertThat(fixture.writes).hasValue(1);
        if (operation == Operation.COUNT) {
            fixture.expectCount(name, correctedCount);
        } else {
            fixture.expectEdit(name, correctedEdit);
        }
        fixture.assertUnfinished();
    }

    private enum Operation {
        COUNT, EDIT
    }

    private static final class Fixture {
        private final InventoryService service = mock(InventoryService.class);
        private final AtomicReference<List<Item>> items = new AtomicReference<>(List.of(
                item(ROSE_ID, "Nootka Rose", 10), item(DAHLIA_ID, "Dahlia Mix", 12)));
        private final Set<Long> failingIds = ConcurrentHashMap.newKeySet();
        private final AtomicInteger writes = new AtomicInteger();
        private final DialogProbe dialogs = new DialogProbe();
        private final TrackingHost host = new TrackingHost();
        private final InventoryTab tab;
        private final JComponent root;
        private final SwingUserHarness user;

        private Fixture() throws Exception {
            failingIds.addAll(List.of(ROSE_ID, DAHLIA_ID));
            when(service.getAllItems()).thenAnswer(invocation -> items.get());
            when(service.updateById(anyLong(), any(Item.class))).thenAnswer(invocation -> {
                long id = invocation.getArgument(0);
                Item submitted = invocation.getArgument(1);
                writes.incrementAndGet();
                if (failingIds.contains(id)) {
                    throw new IllegalStateException("inventory database unavailable");
                }
                Item saved = new Item(id, submitted.getName(), submitted.getCategory(),
                        submitted.getPrice(), submitted.getUnit(), submitted.getCost(),
                        submitted.getQuantity(), submitted.getNotes());
                items.updateAndGet(existing -> existing.stream()
                        .map(item -> item.getId().equals(id) ? saved : item).toList());
                return saved;
            });
            tab = new InventoryTab(service, host, dialogs);
            JComponent[] built = new JComponent[1];
            SwingUtilities.invokeAndWait(() -> {
                built[0] = tab.getUIComponent();
                tab.initialize();
            });
            root = built[0];
            user = SwingUserHarness.on(root);
            awaitIdle();
        }

        private Item current(long id) {
            return items.get().stream().filter(item -> item.getId().equals(id))
                    .findFirst().orElseThrow();
        }

        private void select(long id) throws Exception {
            SwingUtilities.invokeAndWait(() -> selectProduct(root, current(id).getName()));
        }

        private void fail(Operation operation, long id) throws Exception {
            select(id);
            SwingUtilities.invokeAndWait(() -> start(operation, id, false));
            awaitIdle();
            assertThat(host.status).contains("not saved");
            assertUnfinished();
        }

        private void succeed(Operation operation, long id) throws Exception {
            failingIds.remove(id);
            select(id);
            int before = writes.get();
            SwingUtilities.invokeAndWait(() -> start(operation, id, true));
            awaitIdle();
            assertThat(writes).hasValue(before + 1);
        }

        private void start(Operation operation, long id, boolean retry) {
            if (operation == Operation.COUNT) {
                tab.startSelectedStockCountSave(count(id), count(id) - current(id).getQuantity(), false);
            } else {
                tab.startSelectedItemEdit(retry ? null : edit(id));
            }
        }

        private void expectDraft(Operation operation, long id) throws Exception {
            String name = current(id).getName();
            if (operation == Operation.COUNT) {
                expectCount(name, count(id));
                return;
            }
            expectEdit(name, edit(id));
        }

        private void expectEdit(String name, InventoryItemPresenter.ItemDraft expected)
                throws Exception {
            dialogs.scripts.confirm("Edit “" + name + "”", false);
            dialogs.inspect = content -> assertEdit(content, expected);
            SwingUtilities.invokeAndWait(() -> selectProduct(root, name));
            user.click("Edit selected");
            dialogs.reset();
        }

        private void expectCount(String name, int quantity) throws Exception {
            dialogs.scripts.choose("Count “" + name + "”", "Cancel");
            dialogs.inspect = content ->
                    assertThat(find(content, JSpinner.class).getValue()).isEqualTo(quantity);
            SwingUtilities.invokeAndWait(() -> selectProduct(root, name));
            user.click("Record stock count…");
            dialogs.reset();
        }

        private void rejectAfterSelectionChanges(Operation operation, long id, long otherId)
                throws Exception {
            String name = current(id).getName();
            if (operation == Operation.COUNT) {
                dialogs.scripts.enterSpinnerAndChoose("Count “" + name + "”",
                        "Counted quantity for " + name, count(id), "Save count");
            } else {
                dialogs.scripts.confirmForm("Edit “" + name + "”", true,
                        form -> fillEdit(form, edit(id)));
            }
            dialogs.afterAnswer = () -> selectProduct(root, current(otherId).getName());
            select(id);
            user.click(operation == Operation.COUNT ? "Record stock count…" : "Edit selected");
            dialogs.reset();
            assertThat(host.status).contains("No change was submitted");
            assertUnfinished();
        }

        private void replace(List<Item> replacement) throws Exception {
            items.set(replacement);
            refresh();
        }

        private void refresh() throws Exception {
            SwingUtilities.invokeAndWait(tab::refreshData);
            awaitIdle();
        }

        private void awaitIdle() {
            user.awaitButtonEnabled("Refresh", true);
        }

        private void assertUnfinished() throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                assertThat(tab.hasPendingSave()).isFalse();
                assertThat(tab.applicationExitWarning()).contains("retained for review", "lost");
            });
        }

        private void assertFinished() throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                assertThat(tab.hasPendingSave()).isFalse();
                assertThat(tab.applicationExitWarning()).isNull();
            });
        }
    }

    private static int count(long id) {
        return id == ROSE_ID ? 37 : 47;
    }

    private static InventoryItemPresenter.ItemDraft edit(long id) {
        return id == ROSE_ID ? ROSE_EDIT : DAHLIA_EDIT;
    }

    private static void fillEdit(SwingDialogHarness.DialogForm form,
                                 InventoryItemPresenter.ItemDraft draft) {
        form.type("Item name", draft.name())
                .select("Item category", draft.category())
                .select("Rose variety", draft.roseVariety().isBlank()
                        ? "Not a rose / unspecified" : draft.roseVariety())
                .type("Selling price", draft.price())
                .select("Selling unit", draft.unit())
                .type("Cost per unit", draft.cost())
                .type("Inventory quantity", draft.quantity())
                .type("Item notes", draft.notes());
    }

    private static void assertEdit(Component content, InventoryItemPresenter.ItemDraft expected) {
        SwingUserHarness form = SwingUserHarness.on((JComponent) content);
        assertThat(form.fieldValue("Item name")).isEqualTo(expected.name());
        assertThat(form.selectedOption("Item category")).isEqualTo(expected.category());
        assertThat(form.selectedOption("Rose variety")).isEqualTo(expected.roseVariety().isBlank()
                ? "Not a rose / unspecified" : expected.roseVariety());
        assertThat(form.fieldValue("Selling price")).isEqualTo(expected.price());
        assertThat(form.selectedOption("Selling unit")).isEqualTo(expected.unit());
        assertThat(form.fieldValue("Cost per unit")).isEqualTo(expected.cost());
        assertThat(form.fieldValue("Inventory quantity")).isEqualTo(expected.quantity());
        assertThat(form.fieldValue("Item notes")).isEqualTo(expected.notes());
    }

    private static void selectProduct(Component root, String name) {
        JTable table = find(root, JTable.class);
        for (int row = 0; row < table.getRowCount(); row++) {
            if (name.equals(table.getValueAt(row, 0))) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
        throw new AssertionError("Missing inventory product " + name);
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T result = find(child, type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static Item item(long id, String name, int quantity) {
        return new Item(id, name, "Flowers/Plants", 4.5, "Per Stem", 1.2, quantity, "Field A");
    }

    private static final class DialogProbe implements FarmDialogs {
        private final SwingDialogHarness scripts = new SwingDialogHarness();
        private Consumer<Component> inspect = content -> { };
        private Runnable afterAnswer = () -> { };

        private void reset() {
            scripts.assertComplete();
            inspect = content -> { };
            afterAnswer = () -> { };
        }

        @Override
        public int showOption(Component parent, Object message, String title, int optionType,
                              int messageType, Icon icon, Object[] options, Object initialValue) {
            int result = scripts.showOption(parent, message, title, optionType,
                    messageType, icon, options, initialValue);
            inspect.accept((Component) message);
            afterAnswer.run();
            return result;
        }

        @Override
        public int showConfirm(Component parent, Object message, String title,
                               int optionType, int messageType) {
            int result = scripts.showConfirm(parent, message, title, optionType, messageType);
            inspect.accept((Component) message);
            afterAnswer.run();
            return result;
        }

        @Override
        public void showMessage(Component parent, Object message, String title, int messageType) {
            scripts.showMessage(parent, message, title, messageType);
        }
    }

    private static final class TrackingHost implements TabHost {
        private volatile String status;

        @Override public void refreshAll() { }
        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) { status = message; }
        @Override public void runTrendAnalysis() { }
    }
}
