package com.flowerfarm.gui;

import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.model.FarmEnterprise;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.FarmState;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.model.FlowerOffering;
import com.flowerfarm.service.FarmOperationSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Farm setup desktop form")
class FarmSetupDialogTest {

    @Test
    void nonFlowerEnterpriseDisablesOfferingsAndKeepsMultiEnterpriseSpecialties() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        JTextArea readiness = named(form, "farmSetup.readiness", JTextArea.class);
        assertThat(readiness.getRows()).isGreaterThanOrEqualTo(5);

        named(form, "farmSetup.state", JComboBox.class).setSelectedItem(FarmState.OR);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.CANNABIS_FARM);
        JCheckBox flowers = named(form, "farmSetup.enterprise.CUT_FLOWERS", JCheckBox.class);
        JCheckBox orchards = named(form, "farmSetup.enterprise.ORCHARDS", JCheckBox.class);
        flowers.setSelected(false);
        orchards.setSelected(true);
        named(form, "farmSetup.specialtyCrops", JTextArea.class)
                .setText(" potatoes, Honeycrisp apples\ndairy cattle, potatoes ");

        for (FlowerOffering offering : FlowerOffering.values()) {
            JCheckBox box = named(form, "farmSetup.offering." + offering, JCheckBox.class);
            assertThat(box.isSelected()).isTrue();
            assertThat(box.isEnabled()).isFalse();
        }
        FarmOperationSettings draft = form.draft();
        assertThat(draft.getState()).isEqualTo(FarmState.OR);
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.CANNABIS_FARM);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.CANNABIS);
        assertThat(draft.getEnterprises()).containsExactly(
                FarmEnterprise.ORCHARDS, FarmEnterprise.CANNABIS);
        assertThat(draft.getSpecialtyCrops()).containsExactly(
                "dairy cattle", "Honeycrisp apples", "potatoes");
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("cultivation batch planning",
                "plant counts", "no cannabis-specific compliance",
                "Apple orchard has a block planning notebook",
                "bin, grading, and cold-storage workflows are planned",
                "Irrigation advice remains tailored");
        assertThat(readiness.getRows()).isGreaterThan(5);

        flowers.setSelected(true);
        JCheckBox stems = named(form, "farmSetup.offering.STEMS", JCheckBox.class);
        assertThat(stems.isEnabled()).isTrue();
        assertThat(stems.isSelected()).isTrue();
        named(form, "farmSetup.offering.BOUQUETS", JCheckBox.class).setSelected(false);
        named(form, "farmSetup.offering.WEDDINGS", JCheckBox.class).setSelected(false);
        assertThat(form.draft().getFlowerOfferings()).containsExactly(FlowerOffering.STEMS);
    }

    @Test
    void ownerCanSaveHumanLabeledChoicesThroughDialogBoundary() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        when(service.getSettings()).thenReturn(new FarmOperationSettings());
        when(service.saveSettings(any(FarmOperationSettings.class)))
                .thenAnswer(invocation -> ((FarmOperationSettings) invocation.getArgument(0)).snapshot());
        SwingDialogHarness dialogs = new SwingDialogHarness().chooseForm(
                "Farm setup", "Save farm setup", form -> form
                        .select("Farm workspace", "Cannabis farm")
                        .select("Farm state", "Oregon (OR)")
                        .type("Farm specialties", "potatoes, Honeycrisp apples")
                        .expectText("The workspace chooses visible tools"));

        FarmOperationSettings saved = new FarmSetupDialog(service, dialogs)
                .show(null, new FarmProfile(), FarmRole.OWNER);

        assertThat(saved.getState()).isEqualTo(FarmState.OR);
        assertThat(saved.getWorkspace()).isEqualTo(FarmWorkspace.CANNABIS_FARM);
        assertThat(saved.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.CANNABIS);
        assertThat(saved.getEnterprises()).containsExactly(FarmEnterprise.CANNABIS);
        assertThat(saved.getSpecialtyCrops()).containsExactly("Honeycrisp apples", "potatoes");
        assertThat(saved.getFlowerOfferings()).isEmpty();
        verify(service).saveSettings(any(FarmOperationSettings.class));
        dialogs.assertComplete();
    }

    @Test
    void failedSaveKeepsTheOwnerDraftAvailableForRetry() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        when(service.getSettings()).thenReturn(new FarmOperationSettings());
        when(service.saveSettings(any(FarmOperationSettings.class)))
                .thenThrow(new IllegalStateException("Store temporarily unavailable"))
                .thenAnswer(invocation -> ((FarmOperationSettings) invocation.getArgument(0)).snapshot());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .chooseForm("Farm setup", "Save farm setup", form -> form
                        .select("Farm state", "Oregon (OR)")
                        .type("Farm specialties", "potatoes"))
                .chooseForm("Farm setup", "Save farm setup", form -> form
                        .expectText("Your choices remain here")
                        .expectText("potatoes"));

        FarmOperationSettings saved = new FarmSetupDialog(service, dialogs)
                .show(null, new FarmProfile(), FarmRole.OWNER);

        assertThat(saved.getState()).isEqualTo(FarmState.OR);
        assertThat(saved.getSpecialtyCrops()).containsExactly("potatoes");
        verify(service, times(2)).saveSettings(any(FarmOperationSettings.class));
        dialogs.assertComplete();
    }

    @Test
    void flowerShopCanStartWithoutClaimingToGrowCutFlowers() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);

        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.FLOWER_SHOP);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.FLOWER_SHOP);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.FLORAL_RETAIL);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.FLORAL_RETAIL);
        assertThat(draft.getFlowerOfferings()).containsExactly(
                FlowerOffering.STEMS, FlowerOffering.BOUQUETS, FlowerOffering.WEDDINGS);
        assertThat(form.readinessText()).contains("stock, customer orders",
                "Bouquet recipes do not assemble stock automatically");
    }

    @Test
    void potatoAppleAndBerrySetupSelectTheirEnterpriseWithoutTheFlowerDefault() {
        for (var choice : java.util.Map.of(
                FarmWorkspace.POTATO_FARM, FarmEnterprise.ROW_CROPS,
                FarmWorkspace.APPLE_ORCHARD, FarmEnterprise.ORCHARDS,
                FarmWorkspace.BERRY_FARM, FarmEnterprise.BERRIES).entrySet()) {
            FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                    new FarmProfile(), new FarmOperationSettings(), true);
            named(form, "farmSetup.workspace", JComboBox.class)
                    .setSelectedItem(choice.getKey());

            FarmOperationSettings draft = form.draft().snapshot();
            assertThat(draft.getWorkspace()).isEqualTo(choice.getKey());
            assertThat(draft.getPrimaryEnterprise()).isEqualTo(choice.getValue());
            assertThat(draft.getEnterprises()).containsExactly(choice.getValue());
            assertThat(draft.getFlowerOfferings()).isEmpty();
            assertThat(form.readinessText()).contains("planning")
                    .contains("packout");
        }
    }

    @Test
    void nurserySetupSelectsLivePlantEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.state", JComboBox.class)
                .setSelectedItem(FarmState.OR);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.NURSERY_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.NURSERY_FARM);
        assertThat(draft.getState()).isEqualTo(FarmState.OR);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.NURSERY);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.NURSERY);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("live-plant growing batches")
                .contains("do not create stock", "licensing", "inspections");
    }

    @Test
    void livestockSetupSelectsHerdEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.LIVESTOCK_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.LIVESTOCK_FARM);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.LIVESTOCK);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.LIVESTOCK);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("editable herd group plans")
                .contains("individual animals", "health")
                .doesNotContain("general inventory, harvest, orders");
    }

    @Test
    void poultrySetupSelectsEggEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.POULTRY_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.POULTRY_FARM);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.POULTRY_AND_EGGS);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.POULTRY_AND_EGGS);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("egg collections", "Usable counts",
                "stock and orders remain separate").doesNotContain(
                "general inventory, harvest, orders");
    }

    @Test
    void dairySetupSelectsMilkEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.DAIRY_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.DAIRY_FARM);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.DAIRY);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.DAIRY);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("milking sessions", "measured amount",
                "do not post to stock or orders")
                .doesNotContain("general inventory, harvest, orders");
    }

    @Test
    void vineyardSetupSelectsGrapeEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.VINEYARD_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.VINEYARD_FARM);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.VINEYARDS);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.VINEYARDS);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("grape blocks", "planned harvest windows",
                "do not create harvest lots", "winery production");
    }

    @Test
    void haySetupSelectsForageEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.HAY_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.HAY_FARM);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.HAY_AND_FORAGE);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.HAY_AND_FORAGE);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("hay cuttings", "target dates",
                "do not create bale inventory", "feed rations");
    }

    @Test
    void hopsSetupSelectsHopsEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.HOPS_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.HOPS_FARM);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.HOPS);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.HOPS);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("hop yards", "stringing date",
                "do not record actual yield");
    }

    @Test
    void grainsSetupSelectsGrainsEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.GRAIN_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.GRAIN_FARM);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.GRAINS);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.GRAINS);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("grain fields", "previous crop",
                "seeding date", "harvest window", "actual yield", "moisture");
    }

    @Test
    void seedSetupSelectsSeedEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.SEED_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.SEED_FARM);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.SEED_CROPS);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.SEED_CROPS);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("seed fields", "flowering window",
                "manual isolation notes", "do not calculate isolation",
                "certify seed");
    }

    @Test
    void nutOrchardSetupSelectsTreeNutEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.state", JComboBox.class)
                .setSelectedItem(FarmState.OR);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.NUT_ORCHARD);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.NUT_ORCHARD);
        assertThat(draft.getState()).isEqualTo(FarmState.OR);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.TREE_NUTS);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.TREE_NUTS);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("nut orchard blocks", "pollinizer",
                "floor preparation", "harvest window", "compatibility is not checked");
    }

    @Test
    void vegetableSetupSelectsVegetablesEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.state", JComboBox.class)
                .setSelectedItem(FarmState.OR);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.VEGETABLE_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.VEGETABLE_FARM);
        assertThat(draft.getState()).isEqualTo(FarmState.OR);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.VEGETABLES);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.VEGETABLES);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("vegetable plantings", "succession",
                "grower-entered", "do not predict harvests", "stock", "orders");
    }

    @Test
    void aquacultureSetupSelectsAquacultureEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.state", JComboBox.class)
                .setSelectedItem(FarmState.OR);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.AQUACULTURE_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.AQUACULTURE_FARM);
        assertThat(draft.getState()).isEqualTo(FarmState.OR);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.AQUACULTURE);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.AQUACULTURE);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("aquatic cohorts", "growing site",
                "harvest-area status", "traceability", "stock", "orders");
    }

    @Test
    void christmasTreeSetupSelectsItsOwnEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.state", JComboBox.class)
                .setSelectedItem(FarmState.OR);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.CHRISTMAS_TREE_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.CHRISTMAS_TREE_FARM);
        assertThat(draft.getState()).isEqualTo(FarmState.OR);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.CHRISTMAS_TREES);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.CHRISTMAS_TREES);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("Christmas tree blocks", "shearing",
                "target sale season", "licensing", "export clearance");
    }

    @Test
    void apiarySetupSelectsItsOwnEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.state", JComboBox.class)
                .setSelectedItem(FarmState.WA);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.APIARY_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.APIARY_FARM);
        assertThat(draft.getState()).isEqualTo(FarmState.WA);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.APIARY);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.APIARY);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("Hive checks", "next check date",
                "registration", "honey stock");
    }

    @Test
    void mushroomSetupSelectsItsOwnEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.state", JComboBox.class)
                .setSelectedItem(FarmState.OR);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.MUSHROOM_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.MUSHROOM_FARM);
        assertThat(draft.getState()).isEqualTo(FarmState.OR);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.MUSHROOMS);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.MUSHROOMS);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("Mushroom batches", "substrate",
                "fruiting window", "stock and sales");
    }

    @Test
    void cranberrySetupSelectsItsOwnEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.state", JComboBox.class)
                .setSelectedItem(FarmState.OR);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.CRANBERRY_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.CRANBERRY_FARM);
        assertThat(draft.getState()).isEqualTo(FarmState.OR);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.CRANBERRIES);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.CRANBERRIES);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("Cranberry beds", "wet or dry",
                "harvest window", "stock and sales");
    }

    @Test
    void pumpkinSetupSelectsItsOwnEnterpriseWithoutTheFlowerDefault() {
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), new FarmOperationSettings(), true);
        named(form, "farmSetup.state", JComboBox.class)
                .setSelectedItem(FarmState.WA);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.PUMPKIN_PATCH);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.PUMPKIN_PATCH);
        assertThat(draft.getState()).isEqualTo(FarmState.WA);
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.PUMPKINS);
        assertThat(draft.getEnterprises()).containsExactly(FarmEnterprise.PUMPKINS);
        assertThat(draft.getFlowerOfferings()).isEmpty();
        assertThat(form.readinessText()).contains("Pumpkin patches", "pick-your-own",
                "visitor-area", "bookings");
    }

    @Test
    void nurserySetupKeepsAnIntentionallyConfiguredRoseLine() {
        FarmOperationSettings roses = new FarmOperationSettings();
        roses.setState(FarmState.OR);
        roses.setSpecialtyCrops(java.util.Set.of("Nootka roses"));
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), roses, true);
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.NURSERY_FARM);

        FarmOperationSettings draft = form.draft().snapshot();
        assertThat(draft.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.NURSERY);
        assertThat(draft.getEnterprises()).containsExactly(
                FarmEnterprise.CUT_FLOWERS, FarmEnterprise.NURSERY);
        assertThat(draft.getFlowerOfferings()).isEqualTo(roses.getFlowerOfferings());
        assertThat(form.readinessText()).contains("general inventory, orders, and notes")
                .doesNotContain("general inventory, harvest, orders, and notes");
    }

    @Test
    void setupEditsKeepCombinedFarmTypesAndAllowChangingPrimary() {
        FarmOperationSettings combined = new FarmOperationSettings();
        combined.setWorkspace(FarmWorkspace.POTATO_FARM);
        combined.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD)));
        combined.setEnterprises(Set.of(FarmEnterprise.ROW_CROPS,
                FarmEnterprise.CANNABIS, FarmEnterprise.ORCHARDS));
        combined.setPrimaryEnterprise(FarmEnterprise.ROW_CROPS);
        combined.setFlowerOfferings(Set.of());
        FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                new FarmProfile(), combined, true, true);

        JTextArea summary = named(form, "farmSetup.selectedTypes", JTextArea.class);
        assertThat(summary.getText()).contains("Potato farm", "Cannabis farm",
                "Apple orchard", "Primary: Potato farm");
        named(form, "farmSetup.state", JComboBox.class).setSelectedItem(FarmState.OR);
        named(form, "farmSetup.specialtyCrops", JTextArea.class)
                .setText("Russet potatoes, Honeycrisp apples");
        named(form, "farmSetup.workspace", JComboBox.class)
                .setSelectedItem(FarmWorkspace.APPLE_ORCHARD);

        FarmOperationSettings draft = form.draft();
        assertThat(draft.getState()).isEqualTo(FarmState.OR);
        assertThat(draft.getWorkspace()).isEqualTo(FarmWorkspace.APPLE_ORCHARD);
        assertThat(draft.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.APPLE_ORCHARD, FarmWorkspace.POTATO_FARM,
                FarmWorkspace.CANNABIS_FARM);
        assertThat(draft.getSpecialtyCrops()).containsExactly(
                "Honeycrisp apples", "Russet potatoes");
        assertThat(summary.getText()).contains("Primary: Apple orchard",
                "Potato farm", "Cannabis farm");

        named(form, "farmSetup.enterprise.CANNABIS", JCheckBox.class)
                .setSelected(false);
        assertThatThrownBy(form::draft).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CANNABIS");
    }

    @Test
    void editFarmMixSavesOtherChangesBeforeReturningToPicker() throws Exception {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        when(service.getSettings()).thenReturn(new FarmOperationSettings());
        when(service.saveSettings(any(FarmOperationSettings.class)))
                .thenAnswer(invocation -> ((FarmOperationSettings) invocation.getArgument(0))
                        .snapshot());
        AtomicBoolean pickerOpened = new AtomicBoolean();
        SwingDialogHarness dialogs = new SwingDialogHarness().chooseForm(
                "Farm setup", "Save & edit farm mix", form -> form
                        .expectText("Selected farm types")
                        .select("Farm state", "Oregon (OR)"));

        FarmOperationSettings saved = new FarmSetupDialog(service, dialogs)
                .show(null, new FarmProfile(), FarmRole.OWNER,
                        () -> pickerOpened.set(true));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(saved.getState()).isEqualTo(FarmState.OR);
        assertThat(saved.getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.ROSE_AND_EVENTS);
        assertThat(pickerOpened).isTrue();
        verify(service).saveSettings(any(FarmOperationSettings.class));
        dialogs.assertComplete();
    }

    @Test
    void handAndViewerCanInspectButCannotSaveFarmSetup() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        when(service.getSettings()).thenReturn(new FarmOperationSettings());
        for (FarmRole role : new FarmRole[]{FarmRole.HAND, FarmRole.VIEWER}) {
            FarmSetupDialog.Form form = new FarmSetupDialog.Form(
                    new FarmProfile(), new FarmOperationSettings(), false);
            assertThat(named(form, "farmSetup.state", JComboBox.class).isEnabled()).isFalse();
            assertThat(named(form, "farmSetup.primaryEnterprise", JComboBox.class)
                    .isEnabled()).isFalse();
            assertThat(named(form, "farmSetup.enterprise.CUT_FLOWERS", JCheckBox.class)
                    .isEnabled()).isFalse();
            assertThat(named(form, "farmSetup.specialtyCrops", JTextArea.class)
                    .isEditable()).isFalse();
            SwingDialogHarness dialogs = new SwingDialogHarness().chooseForm(
                    "Farm setup", "Close", script -> script
                            .expectText("What works today")
                            .expectText("does not change weather guidance"));
            assertThat(new FarmSetupDialog(service, dialogs)
                    .show(null, new FarmProfile(), role)).isNull();
            dialogs.assertComplete();
        }
        verify(service, never()).saveSettings(any(FarmOperationSettings.class));
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = findNamed(child, name, type);
                if (match != null) {
                    return match;
                }
            }
        }
        throw new AssertionError("Missing control " + name);
    }

    private static <T extends Component> T findNamed(Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = findNamed(child, name, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
