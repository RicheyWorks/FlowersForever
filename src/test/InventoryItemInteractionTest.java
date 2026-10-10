package com.flowerfarm.gui;

import com.flowerfarm.gui.presenter.InventoryItemPresenter;
import com.flowerfarm.gui.view.InventoryItemFormView;
import com.flowerfarm.gui.view.StockCountView;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@DisplayName("Shared inventory item data entry")
class InventoryItemInteractionTest {

    @Test
    void correctingAnEntryRestoresItsFarmerGuidance() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            InventoryItemFormView form = new InventoryItemFormView();
            var quantity = form.quantityField();
            form.markError("quantity", "Enter a whole number.");
            quantity.setText("8");
            assertThat(quantity.getClientProperty("JComponent.outline")).isNull();
            assertThat(quantity.getAccessibleContext().getAccessibleDescription())
                    .contains("Whole units on hand", "Start at zero");
            assertThat(quantity.getToolTipText()).contains("Whole units on hand");
            form.resetForSimilarItem();
            assertThat(form.draft().quantity()).isEqualTo("0");
            assertThat(quantity.getToolTipText()).contains("Whole units on hand");
        });
    }

    @Test
    @DisplayName("presenter parses one shared draft and delegates add and edit mutations")
    void presenterParsesAndDelegatesMutations() {
        InventoryService service = mock(InventoryService.class);
        InventoryItemPresenter presenter = new InventoryItemPresenter(service);
        InventoryItemPresenter.ItemDraft draft = new InventoryItemPresenter.ItemDraft(
                " Nootka Rose ", "Flowers/Plants", "Nootka", "4.50",
                "Per Stem", "1.25", "20", " cooler ");

        Item created = presenter.add(draft);
        Item edited = presenter.update(3, draft);
        Item counted = presenter.setQuantity(3, edited, 17);

        assertThat(created.getName()).isEqualTo("Nootka Rose");
        assertThat(created.getNotes()).isEqualTo("Type: Nootka; cooler");
        assertThat(created.getPrice()).isEqualTo(4.50);
        assertThat(created.getQuantity()).isEqualTo(20);
        assertThat(edited.getUnit()).isEqualTo("Per Stem");
        assertThat(counted.getQuantity()).isEqualTo(17);
        verify(service).addItem(created);
        verify(service).editItem(3, edited);
        verify(service).editItem(3, counted);
    }

    @Test
    @DisplayName("presenter identifies the exact invalid field without mutating inventory")
    void presenterReturnsFieldSpecificValidation() {
        InventoryService service = mock(InventoryService.class);
        InventoryItemPresenter presenter = new InventoryItemPresenter(service);
        InventoryItemPresenter.ItemDraft draft = new InventoryItemPresenter.ItemDraft(
                "Rose", "Flowers/Plants", "Nootka", "not money",
                "Per Stem", "1.00", "4", "");

        assertThatThrownBy(() -> presenter.add(draft))
                .isInstanceOf(InventoryItemPresenter.FieldValidationException.class)
                .satisfies(exception -> assertThat(
                        ((InventoryItemPresenter.FieldValidationException) exception).field())
                        .isEqualTo("price"))
                .hasMessageContaining("Selling price");
        assertThatThrownBy(() -> presenter.add(new InventoryItemPresenter.ItemDraft(
                "Rose", "Flowers/Plants", "", "1000000001",
                "Per Stem", "1.00", "4", "")))
                .isInstanceOfSatisfying(InventoryItemPresenter.FieldValidationException.class,
                        exception -> assertThat(exception.field()).isEqualTo("price"))
                .hasMessageContaining("1000000000");
        assertThatThrownBy(() -> presenter.add(new InventoryItemPresenter.ItemDraft(
                "Rose", "Flowers/Plants", "", "4.00",
                "Per Stem", "1.00", "4", "x".repeat(2001))))
                .isInstanceOfSatisfying(InventoryItemPresenter.FieldValidationException.class,
                        exception -> assertThat(exception.field()).isEqualTo("notes"))
                .hasMessageContaining("2000");
        verify(service, org.mockito.Mockito.never()).addItem(any());
    }

    @Test
    @DisplayName("persisted inventory mutations target ids instead of mutable row positions")
    void presenterTargetsPersistedItemIds() {
        InventoryService service = mock(InventoryService.class);
        when(service.updateById(anyLong(), any(Item.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        InventoryItemPresenter presenter = new InventoryItemPresenter(service);
        Item current = new Item(43L, "Dahlia Mix", "Flowers/Plants", 3.5,
                "Per Stem", 1.0, 12, "Field B");
        InventoryItemPresenter.ItemDraft draft = new InventoryItemPresenter.ItemDraft(
                "Nootka Rose", "Flowers/Plants", "Nootka", "4.50",
                "Per Stem", "1.25", "20", "cooler");

        presenter.updateById(42L, draft);
        Item counted = presenter.setQuantityById(43L, current, 9);
        presenter.deleteById(44L);

        assertThat(counted.getQuantity()).isEqualTo(9);
        verify(service).updateById(org.mockito.ArgumentMatchers.eq(42L), any(Item.class));
        verify(service).updateById(org.mockito.ArgumentMatchers.eq(43L), any(Item.class));
        verify(service).deleteById(44L);
    }

    @Test
    @DisplayName("form restores an item, preserves editable values, and exposes accessible fields")
    void formRestoresValuesAndAccessibility() throws Exception {
        InventoryItemFormView[] form = new InventoryItemFormView[1];
        SwingUtilities.invokeAndWait(() -> {
            form[0] = new InventoryItemFormView();
            form[0].setItem(new Item("Damask Rose", "Flowers/Plants", 5.25,
                    "Per Stem", 1.50, 12, "Type: Damask; Friday market"));
            form[0].markError("quantity", "Enter a whole number.");
        });

        InventoryItemPresenter.ItemDraft draft = form[0].draft();
        assertThat(form[0].getName()).isEqualTo("inventory.itemForm");
        assertThat(form[0].getAccessibleContext().getAccessibleName())
                .isEqualTo("Inventory item details form");
        assertThat(draft.name()).isEqualTo("Damask Rose");
        assertThat(draft.roseVariety()).isEqualTo("Damask");
        assertThat(draft.notes()).isEqualTo("Friday market");
        assertThat(draft.quantity()).isEqualTo("12");

        JComponent quantity = components(form[0]).stream()
                .filter(component -> "Inventory quantity".equals(
                        component.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
        assertThat(quantity.getClientProperty("JComponent.outline")).isEqualTo("error");
        assertThat(quantity.getAccessibleContext().getAccessibleDescription())
                .isEqualTo("Enter a whole number.");
    }

    @Test
    @DisplayName("common worker preset reduces repeated item setup without erasing prices")
    void formAppliesWorkerPreset() throws Exception {
        InventoryItemFormView[] form = new InventoryItemFormView[1];
        SwingUtilities.invokeAndWait(() -> {
            form[0] = new InventoryItemFormView();
            form[0].applyPreset("Market bouquet");
        });

        InventoryItemPresenter.ItemDraft draft = form[0].draft();
        assertThat(draft.name()).isEqualTo("Market Bouquet");
        assertThat(draft.category()).isEqualTo("Flowers/Plants");
        assertThat(draft.unit()).isEqualTo("Per Unit");
        assertThat(draft.price()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("flower starter library fills safe product facts without inventing prices")
    void formAppliesFlowerStarter() throws Exception {
        InventoryItemFormView[] form = new InventoryItemFormView[1];
        SwingUtilities.invokeAndWait(() -> {
            form[0] = new InventoryItemFormView();
            form[0].applyPreset("Nootka rose stem");
        });

        InventoryItemPresenter.ItemDraft draft = form[0].draft();
        assertThat(draft.name()).isEqualTo("Nootka Rose");
        assertThat(draft.category()).isEqualTo("Flowers/Plants");
        assertThat(draft.roseVariety()).isEqualTo("Nootka");
        assertThat(draft.unit()).isEqualTo("Per Stem");
        assertThat(draft.price()).isEqualTo("0.00");
        assertThat(draft.cost()).isEqualTo("0.00");
    }

    @Test
    @DisplayName("similar-item reset keeps shared setup and clears product-specific facts")
    void formPreparesSafeSimilarItem() throws Exception {
        InventoryItemFormView[] form = new InventoryItemFormView[1];
        SwingUtilities.invokeAndWait(() -> {
            form[0] = new InventoryItemFormView();
            form[0].setItem(new Item("Friday Dahlia", "Flowers/Plants", 4.75,
                    "Per Stem", 1.40, 28, "Type: Damask; cooler bin 3"));
            form[0].resetForSimilarItem();
        });

        InventoryItemPresenter.ItemDraft next = form[0].draft();
        assertThat(next.category()).isEqualTo("Flowers/Plants");
        assertThat(next.price()).isEqualTo("4.75");
        assertThat(next.unit()).isEqualTo("Per Stem");
        assertThat(next.cost()).isEqualTo("1.40");
        assertThat(next.name()).isEmpty();
        assertThat(next.roseVariety()).isEmpty();
        assertThat(next.quantity()).isEqualTo("0");
        assertThat(next.notes()).isEmpty();
    }

    @Test
    @DisplayName("physical count view previews the exact stock adjustment")
    void stockCountPreviewsVariance() throws Exception {
        StockCountView[] view = new StockCountView[1];
        SwingUtilities.invokeAndWait(() -> {
            view[0] = new StockCountView(new Item(
                    "Dahlia Mix", "Flowers/Plants", 3.5,
                    "stems", 1.0, 12, "Field B"));
            view[0].setCountedQuantity(9);
        });

        assertThat(view[0].countedQuantity()).isEqualTo(9);
        assertThat(view[0].variance()).isEqualTo(-3);
        assertThat(view[0].varianceLabel().getText()).isEqualTo(
                "Inventory will decrease by 3");
        assertThat(view[0].countInput().getAccessibleContext().getAccessibleName())
                .isEqualTo("Counted quantity for Dahlia Mix");
        assertThat(view[0].varianceLabel().getAccessibleContext()
                .getAccessibleDescription()).contains("new quantity 9 stems");
    }

    private static List<JComponent> components(Component root) {
        List<JComponent> matches = new ArrayList<>();
        visit(root, component -> {
            if (component instanceof JComponent swingComponent) {
                matches.add(swingComponent);
            }
        });
        return matches;
    }

    private static void visit(Component component,
                              java.util.function.Consumer<Component> consumer) {
        consumer.accept(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                visit(child, consumer);
            }
        }
    }
}
