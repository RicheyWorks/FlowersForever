package com.flowerfarm.model;

import com.flowerfarm.gui.view.InventoryItemFormView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import javax.swing.SwingUtilities;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class FarmProductProfileTest {
    @ParameterizedTest @EnumSource(FarmWorkspace.class)
    void eachFarmHasNamedProductsAndAppropriateDefaultUnits(FarmWorkspace workspace) throws Exception {
        FarmProductProfile profile = FarmProductProfile.forWorkspace(workspace);
        assertThat(profile.examples()).hasSize(3);
        assertThat(profile.units()).isNotEmpty();
        SwingUtilities.invokeAndWait(() -> {
            InventoryItemFormView form = new InventoryItemFormView();
            form.setProductProfile(profile);
            assertThat(form.draft().unit()).isEqualTo(profile.defaultUnit());
            form.applyPreset(profile.examples().get(0));
            assertThat(form.draft().name()).isEqualTo(profile.examples().get(0));
            assertThat(form.draft().category()).isEqualTo(profile.category());
        });
        if (workspace != FarmWorkspace.ROSE_AND_EVENTS && workspace != FarmWorkspace.WILDFLOWER_FARM
                && workspace != FarmWorkspace.FLOWER_SHOP) {
            assertThat(profile.units()).noneMatch(unit -> unit.toLowerCase().contains("stem"));
            assertThat(profile.examples()).noneMatch(name -> name.contains("Rose"));
        }
    }

    @Test void combinedFarmsKeepEachProductStarterUnit() throws Exception {
        FarmProductProfile profile = FarmProductProfile.combined(FarmWorkspace.CANNABIS_FARM,
                Set.of(FarmWorkspace.CANNABIS_FARM, FarmWorkspace.DAIRY_FARM));
        assertThat(profile.defaultUnit()).isEqualTo("grams");
        assertThat(profile.units()).contains("grams", "gallons").doesNotContain("stems");
        SwingUtilities.invokeAndWait(() -> {
            InventoryItemFormView form = new InventoryItemFormView();
            form.setProductProfile(profile);
            form.applyPreset("Whole Cow Milk");
            assertThat(form.draft().unit()).isEqualTo("gallons");
            assertThat(form.draft().category()).isEqualTo("Dairy");
            form.setItem(new Item("My Own Cultivar", "My category", 4, "oz", 1, 2.125, "Lot A"));
            assertThat(form.draft().name()).isEqualTo("My Own Cultivar");
            assertThat(form.draft().quantity()).isEqualTo("2.125");
            assertThat(form.draft().unit()).isEqualTo("oz");
        });
    }
}
