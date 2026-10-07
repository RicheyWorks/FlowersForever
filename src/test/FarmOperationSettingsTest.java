package com.flowerfarm.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarmOperationSettingsTest {

    @Test
    void newInstallKeepsFlowerSetupAndStableSelections() {
        FarmOperationSettings settings = new FarmOperationSettings();

        assertThat(settings.getId()).isEqualTo(1L);
        assertThat(settings.getState()).isEqualTo(FarmState.WA);
        assertThat(settings.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.CUT_FLOWERS);
        assertThat(settings.getWorkspace()).isEqualTo(FarmWorkspace.ROSE_AND_EVENTS);
        assertThat(settings.getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.ROSE_AND_EVENTS);
        assertThat(settings.getEnterprises()).containsExactly(FarmEnterprise.CUT_FLOWERS);
        assertThat(settings.getSpecialtyCrops()).isEmpty();
        assertThat(settings.getFlowerOfferings()).containsExactly(
                FlowerOffering.STEMS, FlowerOffering.BOUQUETS, FlowerOffering.WEDDINGS);
        settings.validate();
    }

    @Test
    void mixedFarmPreservesPrimaryAndNormalizesSpecialtyNames() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setState(FarmState.OR);
        settings.setWorkspace(FarmWorkspace.CUSTOM_FARM);
        settings.setEnterprises(Set.of(FarmEnterprise.ORCHARDS,
                FarmEnterprise.CUT_FLOWERS, FarmEnterprise.ROW_CROPS));
        settings.setPrimaryEnterprise(FarmEnterprise.ORCHARDS);
        settings.setSpecialtyCrops(new LinkedHashSet<>(Set.of(
                "  apples  ", "Potatoes", "APPLES", "Roses")));

        settings.validate();
        assertThat(settings.getEnterprises()).containsExactly(
                FarmEnterprise.CUT_FLOWERS, FarmEnterprise.ROW_CROPS,
                FarmEnterprise.ORCHARDS);
        assertThat(settings.getSpecialtyCrops()).containsExactly(
                "APPLES", "Potatoes", "Roses");
        assertThat(settings.getWorkspace()).isEqualTo(FarmWorkspace.CUSTOM_FARM);
        assertThat(settings.snapshot()).isNotSameAs(settings)
                .usingRecursiveComparison().isEqualTo(settings);
    }

    @Test
    void workspaceCanDifferFromPrimaryEnterprise() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setEnterprises(Set.of(FarmEnterprise.CUT_FLOWERS, FarmEnterprise.ORCHARDS));
        settings.setPrimaryEnterprise(FarmEnterprise.ORCHARDS);
        settings.setWorkspace(FarmWorkspace.WILDFLOWER_FARM);
        settings.validate();

        assertThat(settings.getWorkspace()).isEqualTo(FarmWorkspace.WILDFLOWER_FARM);
        assertThat(settings.getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.WILDFLOWER_FARM);
        assertThat(settings.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.ORCHARDS);
        assertThatThrownBy(() -> settings.setWorkspace(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("workspace");
    }

    @Test
    void combinesFarmTypesInSelectionOrderAndKeepsPrimaryFirst() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setEnterprises(Set.of(FarmEnterprise.CUT_FLOWERS,
                FarmEnterprise.CANNABIS, FarmEnterprise.ROW_CROPS,
                FarmEnterprise.ORCHARDS));
        settings.setWorkspace(FarmWorkspace.POTATO_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(Arrays.asList(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD)));

        settings.validate();
        assertThat(settings.getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.POTATO_FARM,
                        FarmWorkspace.CANNABIS_FARM, FarmWorkspace.APPLE_ORCHARD);
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactlyElementsOf(settings.getEnabledWorkspaces());

        settings.setWorkspace(FarmWorkspace.APPLE_ORCHARD);
        assertThat(settings.getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.APPLE_ORCHARD,
                        FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM);
        settings.setEnabledWorkspaces(Set.of(FarmWorkspace.APPLE_ORCHARD));
        assertThat(settings.getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.APPLE_ORCHARD);
        assertThatThrownBy(() -> settings.getEnabledWorkspaces().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void secondaryFarmTypeRequiresItsEnterprise() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setEnabledWorkspaces(Set.of(FarmWorkspace.CANNABIS_FARM));

        assertThat(settings.getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.ROSE_AND_EVENTS,
                        FarmWorkspace.CANNABIS_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CANNABIS");
        assertThatThrownBy(() -> settings.setEnabledWorkspaces(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one farm type");
        LinkedHashSet<FarmWorkspace> withNull = new LinkedHashSet<>();
        withNull.add(null);
        assertThatThrownBy(() -> settings.setEnabledWorkspaces(withNull))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void flowerShopCanOfferBouquetsWithoutGrowingCutFlowers() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setEnterprises(Set.of(FarmEnterprise.FLORAL_RETAIL));
        settings.setPrimaryEnterprise(FarmEnterprise.FLORAL_RETAIL);
        settings.setWorkspace(FarmWorkspace.FLOWER_SHOP);

        settings.validate();
        assertThat(settings.getEnterprises()).containsExactly(FarmEnterprise.FLORAL_RETAIL);
        assertThat(settings.getFlowerOfferings()).contains(FlowerOffering.BOUQUETS);
    }

    @Test
    void specializedWorkspacesRequireTheirEnterprise() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.FLOWER_SHOP);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLORAL_RETAIL");

        settings.setWorkspace(FarmWorkspace.CANNABIS_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CANNABIS");

        settings.setWorkspace(FarmWorkspace.CUSTOM_FARM);
        settings.validate();
    }

    @Test
    void cannabisWorkspaceAcceptsCultivationEnterpriseWithoutFlowerOfferings() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setEnterprises(Set.of(FarmEnterprise.CANNABIS));
        settings.setPrimaryEnterprise(FarmEnterprise.CANNABIS);
        settings.setFlowerOfferings(Set.of());
        settings.setWorkspace(FarmWorkspace.CANNABIS_FARM);

        settings.validate();
        assertThat(settings.snapshot().getWorkspace())
                .isEqualTo(FarmWorkspace.CANNABIS_FARM);
    }

    @Test
    void hopsWorkspaceRequiresHopsEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.HOPS_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HOPS");

        settings.setEnterprises(Set.of(FarmEnterprise.HOPS, FarmEnterprise.HAY_AND_FORAGE));
        settings.setPrimaryEnterprise(FarmEnterprise.HOPS);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.HOPS_FARM, FarmWorkspace.HAY_FARM)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.HOPS_FARM, FarmWorkspace.HAY_FARM);
    }

    @Test
    void grainsWorkspaceRequiresGrainsEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.GRAIN_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GRAINS");

        settings.setEnterprises(Set.of(FarmEnterprise.GRAINS, FarmEnterprise.HAY_AND_FORAGE));
        settings.setPrimaryEnterprise(FarmEnterprise.GRAINS);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.GRAIN_FARM, FarmWorkspace.HAY_FARM)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.GRAIN_FARM, FarmWorkspace.HAY_FARM);
    }

    @Test
    void seedWorkspaceRequiresSeedEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.SEED_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEED_CROPS");

        settings.setEnterprises(Set.of(FarmEnterprise.SEED_CROPS, FarmEnterprise.GRAINS));
        settings.setPrimaryEnterprise(FarmEnterprise.SEED_CROPS);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.SEED_FARM, FarmWorkspace.GRAIN_FARM)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.SEED_FARM, FarmWorkspace.GRAIN_FARM);
    }

    @Test
    void nutOrchardRequiresTreeNutsEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.NUT_ORCHARD);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TREE_NUTS");

        settings.setEnterprises(Set.of(FarmEnterprise.TREE_NUTS, FarmEnterprise.ORCHARDS));
        settings.setPrimaryEnterprise(FarmEnterprise.TREE_NUTS);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.NUT_ORCHARD, FarmWorkspace.APPLE_ORCHARD)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.NUT_ORCHARD, FarmWorkspace.APPLE_ORCHARD);
    }

    @Test
    void vegetableFarmRequiresVegetablesEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.VEGETABLE_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VEGETABLES");

        settings.setEnterprises(Set.of(FarmEnterprise.VEGETABLES,
                FarmEnterprise.ROW_CROPS));
        settings.setPrimaryEnterprise(FarmEnterprise.VEGETABLES);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.VEGETABLE_FARM, FarmWorkspace.POTATO_FARM)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.VEGETABLE_FARM,
                        FarmWorkspace.POTATO_FARM);
    }

    @Test
    void aquacultureFarmRequiresAquacultureEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.AQUACULTURE_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQUACULTURE");

        settings.setEnterprises(Set.of(FarmEnterprise.AQUACULTURE,
                FarmEnterprise.VEGETABLES));
        settings.setPrimaryEnterprise(FarmEnterprise.AQUACULTURE);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.AQUACULTURE_FARM, FarmWorkspace.VEGETABLE_FARM)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.AQUACULTURE_FARM,
                        FarmWorkspace.VEGETABLE_FARM);
    }

    @Test
    void christmasTreeFarmRequiresItsEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.CHRISTMAS_TREE_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHRISTMAS_TREES");

        settings.setEnterprises(Set.of(FarmEnterprise.CHRISTMAS_TREES,
                FarmEnterprise.NURSERY));
        settings.setPrimaryEnterprise(FarmEnterprise.CHRISTMAS_TREES);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.CHRISTMAS_TREE_FARM, FarmWorkspace.NURSERY_FARM)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.CHRISTMAS_TREE_FARM,
                        FarmWorkspace.NURSERY_FARM);
    }

    @Test
    void apiaryFarmRequiresItsEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.APIARY_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APIARY");

        settings.setEnterprises(Set.of(FarmEnterprise.APIARY,
                FarmEnterprise.ORCHARDS));
        settings.setPrimaryEnterprise(FarmEnterprise.APIARY);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.APIARY_FARM, FarmWorkspace.APPLE_ORCHARD)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.APIARY_FARM,
                        FarmWorkspace.APPLE_ORCHARD);
    }

    @Test
    void mushroomFarmRequiresItsEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.MUSHROOM_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MUSHROOMS");

        settings.setEnterprises(Set.of(FarmEnterprise.MUSHROOMS,
                FarmEnterprise.VEGETABLES));
        settings.setPrimaryEnterprise(FarmEnterprise.MUSHROOMS);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.MUSHROOM_FARM, FarmWorkspace.VEGETABLE_FARM)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.MUSHROOM_FARM,
                        FarmWorkspace.VEGETABLE_FARM);
    }

    @Test
    void cranberryFarmRequiresItsEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.CRANBERRY_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CRANBERRIES");

        settings.setEnterprises(Set.of(FarmEnterprise.CRANBERRIES,
                FarmEnterprise.BERRIES));
        settings.setPrimaryEnterprise(FarmEnterprise.CRANBERRIES);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.CRANBERRY_FARM, FarmWorkspace.BERRY_FARM)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.CRANBERRY_FARM,
                        FarmWorkspace.BERRY_FARM);
    }

    @Test
    void pumpkinPatchRequiresItsEnterpriseAndCombinesWithExistingFarms() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.PUMPKIN_PATCH);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PUMPKINS");

        settings.setEnterprises(Set.of(FarmEnterprise.PUMPKINS,
                FarmEnterprise.VEGETABLES));
        settings.setPrimaryEnterprise(FarmEnterprise.PUMPKINS);
        settings.setFlowerOfferings(Set.of());
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.PUMPKIN_PATCH, FarmWorkspace.VEGETABLE_FARM)));
        settings.validate();
        assertThat(settings.snapshot().getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.PUMPKIN_PATCH,
                        FarmWorkspace.VEGETABLE_FARM);
    }

    @Test
    void rejectsInconsistentPrimaryAndFlowerOfferingsWithoutFlowers() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setEnterprises(Set.of(FarmEnterprise.ROW_CROPS));
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primary enterprise");

        settings.setPrimaryEnterprise(FarmEnterprise.ROW_CROPS);
        settings.setWorkspace(FarmWorkspace.CUSTOM_FARM);
        assertThatThrownBy(settings::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Flower offerings require Cut flowers");

        settings.setFlowerOfferings(Set.of());
        settings.validate();
    }

    @Test
    void rejectsInvalidSpecialtyNamesAndDefensiveSelections() {
        FarmOperationSettings settings = new FarmOperationSettings();
        assertThatThrownBy(() -> settings.setSpecialtyCrops(Set.of(" ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");
        assertThatThrownBy(() -> settings.setSpecialtyCrops(Set.of("a".repeat(121))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("120 characters");
        assertThatThrownBy(() -> settings.setEnterprises(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> settings.getEnterprises().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
