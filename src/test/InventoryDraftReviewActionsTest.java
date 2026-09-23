package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.gui.presenter.InventoryItemPresenter;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Retained inventory drafts can be reviewed and resolved independently")
class InventoryDraftReviewActionsTest {
    private static final long ROSE_ID = 11L;
    private static final long DAHLIA_ID = 22L;
    private static final InventoryItemPresenter.ItemDraft ROSE_EDIT =
            new InventoryItemPresenter.ItemDraft("Nootka Rose Premium", "Flowers/Plants",
                    "Nootka", "7.2500", "Per Gallon", "2.1500", "0037", "  Cooler A draft  ");

    @Test
    void missingProductsCompleteEditCanBeReviewedCopiedAndExplicitlyDiscarded() throws Exception {
        Fixture fixture = new Fixture();
        fixture.failEdit();
        fixture.replace(List.of(item(DAHLIA_ID, "Dahlia Mix", 12)));
        clearInvocations(fixture.service);
        fixture.reviewChoice = 1;
        fixture.confirmChoice = JOptionPane.YES_OPTION;
        fixture.reviewProbe = panel -> {
            assertThat(choices(panel).getItemCount()).isEqualTo(1);
            String retained = details(panel).getText();
            assertThat(retained).contains("Nootka Rose", "11", "Nootka Rose Premium",
                    "Flowers/Plants", "Nootka", "7.2500", "Per Gallon", "2.1500", "0037",
                    "  Cooler A draft  ");
            assertThat(retained.toLowerCase()).contains("missing");
            assertThat(details(panel).isEditable()).isFalse();
            details(panel).selectAll();
            assertThat(details(panel).getSelectedText()).isEqualTo(retained);
        };

        fixture.review();

        fixture.assertNoDrafts();
        fixture.assertNoWrites();
    }

    @ParameterizedTest(name = "discard response {0} preserves the exact count")
    @ValueSource(ints = {JOptionPane.NO_OPTION, JOptionPane.CLOSED_OPTION})
    void decliningOrClosingDiscardConfirmationKeepsDraft(int response) throws Exception {
        Fixture fixture = new Fixture();
        fixture.failCount(ROSE_ID, 37);
        clearInvocations(fixture.service);
        fixture.reviewChoice = 1;
        fixture.confirmChoice = response;

        fixture.review();

        fixture.reviewChoice = 2;
        fixture.reviewProbe = panel -> {
            assertThat(choices(panel).getItemCount()).isEqualTo(1);
            assertThat(details(panel).getText()).contains("Nootka Rose", "37");
        };
        fixture.review();
        fixture.assertDraftsRemain();
        fixture.assertNoWrites();
    }

    @Test
    void discardRemovesOnlyChosenOperationEvenWhenTableSelectionChanges() throws Exception {
        Fixture fixture = new Fixture();
        fixture.failCount(ROSE_ID, 37);
        fixture.failEdit();
        fixture.failCount(DAHLIA_ID, 47);
        clearInvocations(fixture.service);
        fixture.reviewChoice = 1;
        fixture.confirmChoice = JOptionPane.YES_OPTION;
        fixture.reviewProbe = panel -> {
            assertThat(choices(panel).getItemCount()).isEqualTo(3);
            choose(panel, "Stock count", "Nootka Rose");
        };
        fixture.duringConfirm = () -> select(fixture.root, "Dahlia Mix");

        fixture.review();

        fixture.reviewChoice = 2;
        fixture.reviewProbe = panel -> {
            assertThat(choices(panel).getItemCount()).isEqualTo(2);
            choose(panel, "Item edit", "Nootka Rose");
            assertThat(details(panel).getText()).contains("7.2500", "0037", "  Cooler A draft  ");
            choose(panel, "Stock count", "Dahlia Mix");
            assertThat(details(panel).getText()).contains("47");
        };
        fixture.review();
        fixture.assertDraftsRemain();
        fixture.assertNoWrites();
    }

    @Test
    void viewerCanReviewAndDiscardLocalDraftWithoutInventoryWriteAccess() throws Exception {
        Fixture fixture = new Fixture();
        fixture.failCount(ROSE_ID, 37);
        SwingUtilities.invokeAndWait(() -> fixture.tab.applyRolePermissions(false));
        clearInvocations(fixture.service);
        fixture.reviewChoice = 1;
        fixture.confirmChoice = JOptionPane.YES_OPTION;
        fixture.reviewProbe = panel -> assertThat(details(panel).getText()).contains("37");

        fixture.review();

        fixture.assertNoDrafts();
        fixture.assertNoWrites();
    }

    @Test
    void openProductClearsFilterAndFindsRenamedOriginalByIdWithoutSaving() throws Exception {
        Fixture fixture = new Fixture();
        fixture.failCount(ROSE_ID, 37);
        fixture.replace(List.of(item(DAHLIA_ID, "Dahlia Mix", 12),
                item(ROSE_ID, "Renamed Rose", 91)));
        fixture.user.type("Inventory search", "Dahlia");
        clearInvocations(fixture.service);
        fixture.reviewChoice = 0;
        fixture.reviewProbe = panel -> assertThat(details(panel).getText())
                .contains("Nootka Rose", "Renamed Rose", "37", "91");

        fixture.review();

        assertThat(fixture.user.fieldValue("Inventory search")).isEmpty();
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(fixture.root, JTable.class);
            assertThat(table.getRowCount()).isEqualTo(2);
            assertThat(table.getSelectedRow()).isNotNegative();
            assertThat(table.getValueAt(table.getSelectedRow(), 0)).isEqualTo("Renamed Rose");
        });
        fixture.assertDraftsRemain();
        fixture.assertNoWrites();
    }

    @Test
    void missingOriginalCannotOpenSameNameReplacement() throws Exception {
        Fixture fixture = new Fixture();
        fixture.failCount(ROSE_ID, 37);
        fixture.replace(List.of(item(33L, "Nootka Rose", 99), item(DAHLIA_ID, "Dahlia Mix", 12)));
        fixture.select(DAHLIA_ID);
        clearInvocations(fixture.service);
        fixture.reviewChoice = 0;
        fixture.reviewProbe = panel -> assertThat(details(panel).getText().toLowerCase())
                .contains("missing");

        fixture.review();

        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(fixture.root, JTable.class);
            if (table.getSelectedRow() >= 0) {
                assertThat(table.getValueAt(table.getSelectedRow(), 0)).isNotEqualTo("Nootka Rose");
            }
        });
        fixture.assertDraftsRemain();
        fixture.assertNoWrites();
    }

    @Test
    void changingDraftDuringDiscardConfirmationPreservesNewlyEnteredValues() throws Exception {
        Fixture fixture = new Fixture();
        fixture.failCount(ROSE_ID, 37);
        clearInvocations(fixture.service);
        fixture.reviewChoice = 1;
        fixture.confirmChoice = JOptionPane.YES_OPTION;
        fixture.countEditor = panel -> find(panel, JSpinner.class).setValue(55);
        fixture.duringConfirm = () -> button(fixture.root, "Record stock count…").doClick();

        fixture.review();

        fixture.reviewChoice = 2;
        fixture.reviewProbe = panel -> {
            assertThat(choices(panel).getItemCount()).isEqualTo(1);
            assertThat(details(panel).getText()).contains("55");
        };
        fixture.review();
        fixture.assertDraftsRemain();
        fixture.assertNoWrites();
    }

    @Test
    void anotherProductsFailedAndSuccessfulEditsKeepOriginalDraftsNameConflict() throws Exception {
        Fixture fixture = new Fixture();
        fixture.failConflictingEdit();
        fixture.select(DAHLIA_ID);
        InventoryItemPresenter.ItemDraft dahliaEdit = new InventoryItemPresenter.ItemDraft(
                "Dahlia Mix", "Flowers/Plants", "", "8.5000", "Per Stem", "3.7500", "0047",
                "  Cooler B correction  ");
        SwingUtilities.invokeAndWait(() -> fixture.tab.startSelectedItemEdit(dahliaEdit));
        fixture.awaitIdle();
        fixture.select(ROSE_ID);
        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(fixture.root, "Review existing SKU").isVisible()).isTrue();
            assertThat(button(fixture.root, "Review existing SKU").isEnabled()).isTrue();
        });
        fixture.reviewProbe = panel -> {
            assertThat(choices(panel).getItemCount()).isEqualTo(2);
            choose(panel, "Item edit", "Nootka Rose");
            assertThat(details(panel).getText()).contains("Name already used by: Dahlia Mix",
                    "7.2500", "0037", "  Cooler A draft  ");
            choose(panel, "Item edit", "Dahlia Mix");
            assertThat(details(panel).getText()).contains("8.5000", "0047")
                    .doesNotContain("Name already used by:");
        };
        fixture.review();

        doAnswer(invocation -> {
            Item entered = invocation.getArgument(1);
            Item saved = new Item(DAHLIA_ID, entered.getName(), entered.getCategory(), entered.getPrice(),
                    entered.getUnit(), entered.getCost(), entered.getQuantity(), entered.getNotes());
            fixture.items.updateAndGet(existing -> existing.stream()
                    .map(item -> item.getId().equals(DAHLIA_ID) ? saved : item).toList());
            return saved;
        }).when(fixture.service).updateById(eq(DAHLIA_ID), any(Item.class));
        fixture.select(DAHLIA_ID);
        SwingUtilities.invokeAndWait(() -> fixture.tab.startSelectedItemEdit(null));
        fixture.awaitIdle();
        fixture.select(ROSE_ID);
        fixture.reviewProbe = panel -> {
            assertThat(choices(panel).getItemCount()).isEqualTo(1);
            assertThat(details(panel).getText()).contains("Nootka Rose", "Name already used by: Dahlia Mix",
                    "7.2500", "0037", "  Cooler A draft  ");
        };
        fixture.review();
        clearInvocations(fixture.service);

        fixture.user.click("Review existing SKU");

        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(fixture.root, JTable.class);
            assertThat(table.getRowCount()).isEqualTo(1);
            assertThat(table.getValueAt(table.getSelectedRow(), 0)).isEqualTo("Dahlia Mix");
        });
        fixture.assertDraftsRemain();
        fixture.assertNoWrites();
    }

    @Test
    void cancellingCorrectedRenameKeepsEnteredFieldsAndClearsObsoleteConflict() throws Exception {
        Fixture fixture = new Fixture();
        fixture.failConflictingEdit();
        clearInvocations(fixture.service);
        fixture.editEditor = panel -> {
            SwingUserHarness editor = SwingUserHarness.on(panel);
            assertThat(editor.fieldValue("Item name")).isEqualTo("  dahlia mix  ");
            assertThat(editor.fieldValue("Selling price")).isEqualTo("7.2500");
            editor.type("Item name", "Nootka Rose Reserved")
                    .type("Selling price", "9.8750")
                    .type("Inventory quantity", "0053")
                    .type("Item notes", "  Keep corrected cooler notes  ");
        };

        fixture.user.click("Edit selected");

        SwingUtilities.invokeAndWait(() -> {
            assertThat(button(fixture.root, "Review existing SKU").isVisible()).isFalse();
            assertThat(button(fixture.root, "Review existing SKU").isEnabled()).isFalse();
        });
        fixture.reviewProbe = panel -> {
            assertThat(choices(panel).getItemCount()).isEqualTo(1);
            assertThat(details(panel).getText()).contains("Nootka Rose Reserved", "9.8750", "0053",
                    "  Keep corrected cooler notes  ").doesNotContain("Name already used by:");
        };
        fixture.review();
        fixture.editEditor = panel -> {
            SwingUserHarness editor = SwingUserHarness.on(panel);
            assertThat(editor.fieldValue("Item name")).isEqualTo("Nootka Rose Reserved");
            assertThat(editor.fieldValue("Selling price")).isEqualTo("9.8750");
            assertThat(editor.fieldValue("Inventory quantity")).isEqualTo("0053");
            assertThat(editor.fieldValue("Item notes")).isEqualTo("  Keep corrected cooler notes  ");
        };
        fixture.user.click("Edit selected");
        fixture.assertDraftsRemain();
        fixture.assertNoWrites();
    }

    @ParameterizedTest(name = "delete saved={0} clears only acknowledged product drafts")
    @ValueSource(booleans = {false, true})
    void deleteExplainsDraftLossAndOnlyClearsAfterAcknowledgement(boolean saved) throws Exception {
        Fixture fixture = new Fixture();
        fixture.failCount(ROSE_ID, 37);
        fixture.failEdit();
        fixture.failCount(DAHLIA_ID, 47);
        fixture.select(ROSE_ID);
        fixture.confirmChoice = JOptionPane.YES_OPTION;
        fixture.confirmProbe = message -> {
            JComponent content = (JComponent) message;
            String explanation = content.getAccessibleContext().getAccessibleName()
                    + " " + find(content, JTextArea.class).getText();
            assertThat(explanation).contains("Nootka Rose", "2");
            assertThat(explanation.toLowerCase()).contains("draft", "discard");
        };
        doAnswer(invocation -> {
            if (!saved) {
                throw new IllegalStateException("inventory database unavailable");
            }
            long id = invocation.getArgument(0);
            fixture.items.updateAndGet(existing -> existing.stream()
                    .filter(item -> !item.getId().equals(id)).toList());
            return null;
        }).when(fixture.service).deleteById(ROSE_ID);

        fixture.user.click("Delete selected");
        fixture.awaitIdle();

        fixture.reviewProbe = panel -> {
            assertThat(choices(panel).getItemCount()).isEqualTo(saved ? 1 : 3);
            choose(panel, "Stock count", "Dahlia Mix");
            assertThat(details(panel).getText()).contains("47");
            if (!saved) {
                choose(panel, "Stock count", "Nootka Rose");
                assertThat(details(panel).getText()).contains("37");
                choose(panel, "Item edit", "Nootka Rose");
                assertThat(details(panel).getText()).contains("7.2500", "0037");
            }
        };
        fixture.review();
        fixture.assertDraftsRemain();
        verify(fixture.service).deleteById(ROSE_ID);
        verify(fixture.service, never()).deleteById(DAHLIA_ID);
    }

    private static final class Fixture {
        private final InventoryService service = mock(InventoryService.class);
        private final FarmDialogs dialogs = mock(FarmDialogs.class);
        private final AtomicReference<List<Item>> items = new AtomicReference<>(List.of(
                item(ROSE_ID, "Nootka Rose", 10), item(DAHLIA_ID, "Dahlia Mix", 12)));
        private final InventoryTab tab;
        private final JComponent root;
        private final SwingUserHarness user;
        private int reviewChoice = 2;
        private int confirmChoice = JOptionPane.CLOSED_OPTION;
        private Consumer<JComponent> reviewProbe = panel -> { };
        private Consumer<JComponent> editEditor = panel -> { };
        private Consumer<Component> countEditor = panel -> { };
        private Consumer<Object> confirmProbe = message -> { };
        private Runnable duringConfirm = () -> { };

        private Fixture() throws Exception {
            when(service.getAllItems()).thenAnswer(invocation -> items.get());
            when(service.updateById(anyLong(), any(Item.class)))
                    .thenThrow(new IllegalStateException("inventory database unavailable"));
            when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        String title = invocation.getArgument(2);
                        Component message = invocation.getArgument(1);
                        Object[] options = invocation.getArgument(6);
                        if ("Retained inventory drafts".equals(title)) {
                            assertThat(options).containsExactly("Open product", "Discard draft", "Close");
                            assertThat((Object) invocation.getArgument(7)).isEqualTo("Close");
                            reviewProbe.accept((JComponent) message);
                            return reviewChoice;
                        }
                        assertThat(title).startsWith("Count “");
                        countEditor.accept(message);
                        return options.length - 1;
                    });
            when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> {
                        String title = invocation.getArgument(2);
                        if (title.startsWith("Edit “")) {
                            assertThat((Integer) invocation.getArgument(3))
                                    .isEqualTo(JOptionPane.OK_CANCEL_OPTION);
                            editEditor.accept(invocation.getArgument(1));
                            return JOptionPane.CANCEL_OPTION;
                        }
                        assertThat(title).isIn("Discard retained inventory draft?", "Delete inventory item?");
                        assertThat((Integer) invocation.getArgument(3)).isEqualTo(JOptionPane.YES_NO_OPTION);
                        confirmProbe.accept(invocation.getArgument(1));
                        duringConfirm.run();
                        return confirmChoice;
                    });
            tab = new InventoryTab(service, new QuietHost(), dialogs);
            JComponent[] built = new JComponent[1];
            SwingUtilities.invokeAndWait(() -> {
                built[0] = tab.getUIComponent();
                tab.initialize();
            });
            root = built[0];
            user = SwingUserHarness.on(root);
            awaitIdle();
            assertNoDrafts();
        }

        private void failCount(long id, int quantity) throws Exception {
            select(id);
            SwingUtilities.invokeAndWait(() -> tab.startSelectedStockCountSave(quantity, 0, false));
            awaitIdle();
            assertDraftsRemain();
        }

        private void failEdit() throws Exception {
            select(ROSE_ID);
            SwingUtilities.invokeAndWait(() -> tab.startSelectedItemEdit(ROSE_EDIT));
            awaitIdle();
            assertDraftsRemain();
        }

        private void failConflictingEdit() throws Exception {
            doThrow(new InventoryService.DuplicateItemNameException(DAHLIA_ID, "Dahlia Mix"))
                    .when(service).updateById(eq(ROSE_ID), any(Item.class));
            InventoryItemPresenter.ItemDraft conflict = new InventoryItemPresenter.ItemDraft(
                    "  dahlia mix  ", ROSE_EDIT.category(), ROSE_EDIT.roseVariety(), ROSE_EDIT.price(),
                    ROSE_EDIT.unit(), ROSE_EDIT.cost(), ROSE_EDIT.quantity(), ROSE_EDIT.notes());
            select(ROSE_ID);
            SwingUtilities.invokeAndWait(() -> tab.startSelectedItemEdit(conflict));
            awaitIdle();
            assertDraftsRemain();
        }

        private void select(long id) throws Exception {
            String name = items.get().stream().filter(item -> item.getId().equals(id))
                    .findFirst().orElseThrow().getName();
            SwingUtilities.invokeAndWait(() -> InventoryDraftReviewActionsTest.select(root, name));
        }

        private void replace(List<Item> replacement) throws Exception {
            items.set(replacement);
            SwingUtilities.invokeAndWait(tab::refreshData);
            awaitIdle();
        }

        private void awaitIdle() {
            user.awaitButtonEnabled("Refresh", true);
        }

        private void review() {
            user.click("Review retained drafts…");
        }

        private void assertNoDrafts() throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                assertThat(tab.applicationExitWarning()).isNull();
                assertThat(visibleWithin(root, button(root, "Review retained drafts…"))).isFalse();
            });
        }

        private void assertDraftsRemain() throws Exception {
            SwingUtilities.invokeAndWait(() -> {
                assertThat(tab.hasPendingSave()).isFalse();
                assertThat(tab.applicationExitWarning()).contains("retained for review");
                JButton review = button(root, "Review retained drafts…");
                assertThat(review.getName()).isEqualTo("inventory.reviewDrafts");
                assertThat(visibleWithin(root, review)).isTrue();
                assertThat(review.isEnabled()).isTrue();
            });
        }

        private void assertNoWrites() {
            verify(service, never()).updateById(anyLong(), any(Item.class));
            verify(service, never()).editItem(anyInt(), any(Item.class));
            verify(service, never()).deleteById(anyLong());
            verify(service, never()).deleteItem(anyInt());
        }
    }

    private static void choose(JComponent panel, String operation, String product) {
        JComboBox<?> combo = choices(panel);
        for (int index = 0; index < combo.getItemCount(); index++) {
            String label = combo.getItemAt(index).toString();
            if (label.contains(operation) && label.contains(product)) {
                combo.setSelectedIndex(index);
                return;
            }
        }
        throw new AssertionError("Missing retained draft: " + operation + " / " + product);
    }

    private static JComboBox<?> choices(JComponent panel) {
        JComboBox<?> combo = find(panel, JComboBox.class);
        assertThat(combo.getAccessibleContext().getAccessibleName()).isEqualTo("Retained inventory draft");
        return combo;
    }

    private static JTextArea details(JComponent panel) {
        JTextArea area = find(panel, JTextArea.class);
        assertThat(area.getAccessibleContext().getAccessibleName())
                .isEqualTo("Retained inventory draft details");
        return area;
    }

    private static void select(Component root, String name) {
        JTable table = find(root, JTable.class);
        for (int row = 0; row < table.getRowCount(); row++) {
            if (name.equals(table.getValueAt(row, 0))) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
        throw new AssertionError("Missing inventory product " + name);
    }

    private static JButton button(Component root, String text) {
        if (root instanceof JButton button && text.equals(button.getText())) {
            return button;
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

    private static boolean visibleWithin(Component root, Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
            if (current == root) {
                return true;
            }
        }
        return false;
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

    private static final class QuietHost implements TabHost {
        @Override public void refreshAll() { }
        @Override public void selectTab(String title) { }
        @Override public void setStatus(String message) { }
        @Override public void runTrendAnalysis() { }
    }
}
