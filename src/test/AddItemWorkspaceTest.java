package com.flowerfarm.gui.tabs;

import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("Add Item workspace presentation")
class AddItemWorkspaceTest {

    @Test
    void defaultAndFlowerShopKeepFloralStarters() throws Exception {
        JComponent defaultRoot = onEdt(() -> tab(null).getUIComponent());
        JComponent shopRoot = onEdt(() -> tab(FarmWorkspace.FLOWER_SHOP).getUIComponent());

        for (JComponent root : List.of(defaultRoot, shopRoot)) {
            JComboBox<?> starters = combo(root, "Flower product starter library");
            assertThat(options(starters)).contains("Nootka rose stem", "Wedding flower bucket");
            assertThat(combo(root, "Item category").getSelectedItem())
                    .isEqualTo("Flowers/Plants");
            assertThat(combo(root, "Selling unit").getSelectedItem()).isEqualTo(root == shopRoot ? "bouquets" : "Per Stem");
        }
    }

    @Test
    void farmTypesUseTheirOwnNamedProductsAndUnitsAfterOpeningAndReset() throws Exception {
        for (FarmWorkspace workspace : List.of(
                FarmWorkspace.CANNABIS_FARM, FarmWorkspace.POTATO_FARM,
                FarmWorkspace.APPLE_ORCHARD, FarmWorkspace.BERRY_FARM,
                FarmWorkspace.LIVESTOCK_FARM, FarmWorkspace.POULTRY_FARM,
                FarmWorkspace.DAIRY_FARM,
                FarmWorkspace.VINEYARD_FARM,
                FarmWorkspace.HAY_FARM,
                FarmWorkspace.HOPS_FARM,
                FarmWorkspace.GRAIN_FARM,
                FarmWorkspace.SEED_FARM,
                FarmWorkspace.NUT_ORCHARD,
                FarmWorkspace.VEGETABLE_FARM,
                FarmWorkspace.AQUACULTURE_FARM,
                FarmWorkspace.CHRISTMAS_TREE_FARM,
                FarmWorkspace.APIARY_FARM,
                FarmWorkspace.MUSHROOM_FARM,
                FarmWorkspace.CRANBERRY_FARM,
                FarmWorkspace.PUMPKIN_PATCH,
                FarmWorkspace.CUSTOM_FARM)) {
            AddItemTab tab = tab(workspace);
            JComponent root = onEdt(tab::getUIComponent);
            JComboBox<?> starters = combo(root, "Farm item starter library");
            JComboBox<?> category = combo(root, "Item category");
            JComboBox<?> unit = combo(root, "Selling unit");

            com.flowerfarm.model.FarmProductProfile profile = com.flowerfarm.model.FarmProductProfile.forWorkspace(workspace);
            assertThat(options(starters)).containsAll(profile.examples()).contains("Farm supply", "Tool or equipment");
            assertThat(category.getSelectedItem()).isEqualTo(profile.category());
            assertThat(unit.getSelectedItem()).isEqualTo(profile.defaultUnit());
            assertThat(label(root, "Rose variety · optional").getParent().isVisible()).isFalse();
            assertThat(textField(root, "Item name").getClientProperty(
                    "JTextField.placeholderText")).isEqualTo(profile.nameHint());

            onEdt(() -> {
                category.setSelectedItem("Flowers/Plants");
                button(root, "Reset form").doClick();
            });
            assertThat(category.getSelectedItem()).isEqualTo(profile.category());
            assertThat(unit.getSelectedItem()).isEqualTo(profile.defaultUnit());
        }
    }

    @Test
    void nurseryHasLivePlantStartersWithWholePlantSalesUnits() throws Exception {
        JComponent root = onEdt(() -> tab(FarmWorkspace.NURSERY_FARM).getUIComponent());
        JComboBox<?> starters = combo(root, "Nursery product starter library");
        assertThat(options(starters)).containsExactly(
                "Potted plant", "Seedling tray", "Bare-root plant",
                "Farm supply", "Tool or equipment");
        assertThat(combo(root, "Item category").getSelectedItem()).isEqualTo("Live plants");
        assertThat(combo(root, "Selling unit").getSelectedItem()).isEqualTo("plants");
        assertThat(label(root, "Rose variety · optional").getParent().isVisible()).isFalse();

        onEdt(() -> {
            starters.setSelectedItem("Potted plant");
            button(root, "Use starter").doClick();
        });
        assertThat(combo(root, "Item category").getSelectedItem())
                .isEqualTo("Live plants");
        assertThat(combo(root, "Selling unit").getSelectedItem()).isEqualTo("pots");
        assertThat(textField(root, "Item name").getText()).isEqualTo("Potted Plant");
    }

    @Test
    void wildflowerKeepsFlowerUnitsWithoutRosePresets() throws Exception {
        JComponent root = onEdt(() -> tab(FarmWorkspace.WILDFLOWER_FARM).getUIComponent());
        assertThat(options(combo(root, "Flower product starter library"))).containsExactly(
                "Cut flower / stem", "Market bouquet", "Farm supply", "Tool or equipment");
        assertThat(label(root, "Rose variety · optional").getParent().isVisible()).isFalse();
        assertThat(combo(root, "Item category").getSelectedItem()).isEqualTo("Flowers/Plants");
    }

    @Test
    void combinedFarmIncludesStartersAndFieldsFromEverySelectedType() throws Exception {
        AddItemTab tab = tab(FarmWorkspace.POTATO_FARM);
        tab.setEnabledWorkspaces(new java.util.LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.ROSE_AND_EVENTS,
                FarmWorkspace.NURSERY_FARM)));
        JComponent root = onEdt(tab::getUIComponent);

        assertThat(options(combo(root, "Farm item starter library")))
                .contains("Farm supply", "Cut flower / stem", "Wedding flower bucket",
                        "Potted plant", "Seedling tray");
        assertThat(label(root, "Rose variety · optional").getParent().isVisible())
                .isTrue();
        assertThat(combo(root, "Item category").getSelectedItem())
                .isEqualTo("Potatoes");
    }

    @Test
    void workspaceMustBeSelectedBeforeBuildingForm() throws Exception {
        AddItemTab tab = tab(null);
        onEdt(tab::getUIComponent);
        assertThatThrownBy(() -> tab.setWorkspace(FarmWorkspace.CUSTOM_FARM))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unfinishedProductDetailsBlockWorkspaceReplacementUntilReset() throws Exception {
        AddItemTab tab = tab(FarmWorkspace.CUSTOM_FARM);
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.applicationExitWarning()).isNull();

        onEdt(() -> textField(root, "Inventory quantity").setText("24"));
        assertThat(tab.applicationExitWarning()).contains("unsaved inventory item draft");

        onEdt(() -> button(root, "Reset form").doClick());
        assertThat(tab.applicationExitWarning()).isNull();

        onEdt(() -> textField(root, "Item notes").setText("Cooler 2"));
        assertThat(tab.applicationExitWarning()).contains("unsaved inventory item draft");
    }

    private static AddItemTab tab(FarmWorkspace workspace) {
        AddItemTab tab = new AddItemTab(mock(InventoryService.class), mock(TabHost.class));
        if (workspace != null) {
            tab.setWorkspace(workspace);
        }
        return tab;
    }

    private static List<String> options(JComboBox<?> combo) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < combo.getItemCount(); index++) {
            values.add(String.valueOf(combo.getItemAt(index)));
        }
        return values;
    }

    private static JComboBox<?> combo(Component root, String name) {
        return descendants(root, JComboBox.class).stream()
                .filter(value -> name.equals(value.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JTextField textField(Component root, String name) {
        return descendants(root, JTextField.class).stream()
                .filter(value -> name.equals(value.getAccessibleContext().getAccessibleName()))
                .findFirst().orElseThrow();
    }

    private static JLabel label(Component root, String text) {
        return descendants(root, JLabel.class).stream()
                .filter(value -> text.equals(value.getText()))
                .findFirst().orElseThrow();
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(value -> text.equals(value.getText()))
                .findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(descendants(child, type));
            }
        }
        return found;
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    private static void onEdt(Runnable action) throws Exception {
        onEdt(() -> {
            action.run();
            return null;
        });
    }
}
