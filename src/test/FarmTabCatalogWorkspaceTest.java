package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.FlowerFarmTab;
import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.gui.tabs.WorkspaceHomeTab;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.BerryBlockPlanService;
import com.flowerfarm.service.NurseryBatchService;
import com.flowerfarm.service.HerdGroupService;
import com.flowerfarm.service.EggCollectionService;
import com.flowerfarm.service.MilkCollectionService;
import com.flowerfarm.service.VineyardBlockPlanService;
import com.flowerfarm.service.HayCuttingService;
import com.flowerfarm.service.HopYardPlanService;
import com.flowerfarm.service.GrainFieldPlanService;
import com.flowerfarm.service.SeedFieldPlanService;
import com.flowerfarm.service.NutBlockPlanService;
import com.flowerfarm.service.VegetablePlantingPlanService;
import com.flowerfarm.service.AquaticCohortPlanService;
import com.flowerfarm.service.ChristmasTreeBlockPlanService;
import com.flowerfarm.service.HiveCheckService;
import com.flowerfarm.service.MushroomBatchPlanService;
import com.flowerfarm.service.CranberryBedPlanService;
import com.flowerfarm.service.PumpkinPatchPlanService;
import com.flowerfarm.service.FieldScienceExportService;
import com.flowerfarm.service.CannabisCultivationService;
import com.flowerfarm.service.CropAreaPlanService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SeasonPlanService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;

import java.util.List;
import java.util.LinkedHashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Farm workspace primary navigation")
class FarmTabCatalogWorkspaceTest {

    @Test
    void installedReadingServiceAddsExactlyOneEquipmentScreenToEveryFarmAndMixedFarm() {
        FarmTabCatalog configured = catalog();
        configured.setFarmReadingService(mock(com.flowerfarm.telemetry.FarmReadingService.class));
        for (FarmWorkspace workspace : FarmWorkspace.values()) {
            assertThat(titles(configured.create(mock(TabHost.class), workspace)))
                    .filteredOn("Equipment readings"::equals).hasSize(1);
        }
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.CUSTOM_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.CUSTOM_FARM, FarmWorkspace.NURSERY_FARM, FarmWorkspace.CANNABIS_FARM)));
        assertThat(titles(configured.create(mock(TabHost.class), FarmWorkspace.CUSTOM_FARM, settings)))
                .filteredOn("Equipment readings"::equals).hasSize(1);
    }

    private static final List<String> LEGACY_ROSE_TABS = List.of(
            "Dashboard", "Market Day", "Season Planner", "Harvest Log", "Inventory",
            "Irrigation & Care", "Knowledge Log", "Customers & Orders", "Reports",
            "Trend Analysis", "Activity History", "Reference & Planning");

    @Test
    void roseWorkspaceAndDefaultRetainTheLegacyTwelveTabOrder() {
        FarmTabCatalog catalog = catalog();
        TabHost host = mock(TabHost.class);

        assertThat(titles(catalog.create(host))).containsExactlyElementsOf(LEGACY_ROSE_TABS);
        assertThat(titles(catalog.create(host, FarmWorkspace.ROSE_AND_EVENTS)))
                .containsExactlyElementsOf(LEGACY_ROSE_TABS);
    }

    @Test
    void wildflowerWorkspaceShowsGrowingToolsWithoutRoseOrKitsapTabs() {
        assertThat(titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.WILDFLOWER_FARM))).containsExactly(
                "Workspace", "Market Day", "Season Planner", "Harvest Log", "Inventory",
                "Knowledge Log", "Customers & Orders", "Reports", "Trend Analysis",
                "Activity History", "Reference & Planning");
    }

    @Test
    void flowerShopWorkspaceShowsRetailAndDesignToolsWithoutFieldTabs() {
        assertThat(titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.FLOWER_SHOP))).containsExactly(
                "Workspace", "Inventory", "Customers & Orders", "Event Services",
                "Production Studio", "Knowledge Log", "Reports", "Activity History");
    }

    @Test
    void cannabisAddsCultivationPlanningWhileCustomUsesGenericRecords() {
        List<String> generic = List.of("Workspace", "Harvest Log", "Inventory",
                "Knowledge Log", "Customers & Orders", "Reports", "Activity History");

        assertThat(titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.CANNABIS_FARM))).containsExactly(
                "Workspace", "Cultivation", "Harvest Log", "Inventory",
                "Knowledge Log", "Customers & Orders", "Reports", "Activity History");
        assertThat(titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.CUSTOM_FARM))).containsExactlyElementsOf(generic);
    }

    @Test
    void customFarmCanShareAMixWithoutHidingNamedPlans() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.CUSTOM_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.CUSTOM_FARM, FarmWorkspace.POTATO_FARM,
                FarmWorkspace.APPLE_ORCHARD)));
        settings.setSpecialtyCrops(java.util.Set.of("Lavender", "Pears"));

        FarmTabCatalog.Catalog mixed = catalog().create(mock(TabHost.class),
                FarmWorkspace.CUSTOM_FARM, settings);

        assertThat(titles(mixed)).containsExactly("Workspace", "Field plans",
                "Orchard blocks", "Harvest Log", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(titles(mixed)).containsOnlyOnce("Harvest Log", "Inventory",
                "Customers & Orders", "Knowledge Log");
        assertThat(mixed.tabs().get(0)).isInstanceOf(WorkspaceHomeTab.class);
    }

    @Test
    void fieldScienceAppearsOnlyForMatchingFarmsAndOnceInAMix() {
        assertThat(allTitles(catalog().create(mock(TabHost.class),
                FarmWorkspace.CANNABIS_FARM)))
                .containsSubsequence("Cultivation", "Harvest Log", "Field science", "Inventory");
        assertThat(allTitles(catalog().create(mock(TabHost.class),
                FarmWorkspace.FLOWER_SHOP))).doesNotContain("Field science");
        assertThat(allTitles(catalog().create(mock(TabHost.class),
                FarmWorkspace.CUSTOM_FARM))).doesNotContain("Field science");

        FarmOperationSettings mixed = new FarmOperationSettings();
        mixed.setWorkspace(FarmWorkspace.CANNABIS_FARM);
        mixed.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.CANNABIS_FARM, FarmWorkspace.MUSHROOM_FARM)));
        assertThat(allTitles(catalog().create(mock(TabHost.class),
                FarmWorkspace.CANNABIS_FARM, mixed)))
                .containsOnlyOnce("Field science");
    }

    @Test
    void installedFieldScienceServiceEnablesExportCaptureInTheFarmCatalog() {
        FarmTabCatalog configured = catalog();
        configured.setFieldScienceExportService(mock(FieldScienceExportService.class));

        var science = configured.create(mock(TabHost.class),
                FarmWorkspace.CANNABIS_FARM).fieldScience();

        assertThat(science).isNotNull();
        assertThat(findButton(science.getUIComponent(),
                "fieldScience.save.soil-recipes").isEnabled()).isTrue();
    }

    @Test
    void installedArchiveRemainsReachableWhenTheCurrentFarmHasNoMatchedTools() {
        FarmTabCatalog configured = catalog();
        configured.setFieldScienceExportService(mock(FieldScienceExportService.class));

        for (FarmWorkspace workspace : List.of(FarmWorkspace.FLOWER_SHOP,
                FarmWorkspace.CUSTOM_FARM, FarmWorkspace.APIARY_FARM)) {
            FarmTabCatalog.Catalog result = configured.create(mock(TabHost.class),
                    workspace);
            assertThat(allTitles(result)).containsOnlyOnce("Field science");
            assertThat(result.fieldScience().getUIComponent()).isNotNull();
        }
    }

    @Test
    void potatoAndAppleWorkspacesUseTheirOwnPlanningNotebookAndSharedRecords() {
        assertThat(titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.POTATO_FARM))).containsExactly(
                "Workspace", "Field plans", "Harvest Log", "Inventory",
                "Knowledge Log", "Customers & Orders", "Reports", "Activity History");
        assertThat(titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.APPLE_ORCHARD))).containsExactly(
                "Workspace", "Orchard blocks", "Harvest Log", "Inventory",
                "Knowledge Log", "Customers & Orders", "Reports", "Activity History");
    }

    @Test
    void berryWorkspaceUsesItsOwnPickingWindowNotebookAndSharedRecords() {
        assertThat(titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.BERRY_FARM))).containsExactly(
                "Workspace", "Berry blocks", "Harvest Log", "Inventory",
                "Knowledge Log", "Customers & Orders", "Reports", "Activity History");
    }

    @Test
    void nurseryWorkspaceUsesGrowingBatchesAndSharedStockAndOrders() {
        assertThat(titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.NURSERY_FARM))).containsExactly(
                "Workspace", "Growing batches", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
    }

    @Test
    void livestockWorkspaceUsesHerdGroupsWithoutCropHarvestTools() {
        FarmTabCatalog.Catalog livestock = catalog().create(mock(TabHost.class),
                FarmWorkspace.LIVESTOCK_FARM);
        assertThat(titles(livestock)).containsExactly(
                "Workspace", "Herd groups", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(livestock.herdGroups()).isNotNull();
    }

    @Test
    void poultryWorkspaceUsesEggCollectionsWithoutCropHarvestTools() {
        FarmTabCatalog.Catalog poultry = catalog().create(mock(TabHost.class),
                FarmWorkspace.POULTRY_FARM);
        assertThat(titles(poultry)).containsExactly(
                "Workspace", "Egg collections", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(poultry.eggCollections()).isNotNull();
    }

    @Test
    void dairyWorkspaceUsesMilkCollectionsWithoutCropHarvestTools() {
        FarmTabCatalog.Catalog dairy = catalog().create(mock(TabHost.class),
                FarmWorkspace.DAIRY_FARM);
        assertThat(titles(dairy)).containsExactly(
                "Workspace", "Milk collections", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(dairy.milkCollections()).isNotNull();
    }

    @Test
    void vineyardWorkspaceUsesGrapeBlockPlansWithoutPostingHarvestStock() {
        FarmTabCatalog.Catalog vineyard = catalog().create(mock(TabHost.class),
                FarmWorkspace.VINEYARD_FARM);
        assertThat(titles(vineyard)).containsExactly(
                "Workspace", "Vineyard blocks", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(vineyard.vineyardBlocks()).isNotNull();
    }

    @Test
    void hayWorkspaceUsesRepeatCuttingPlansWithoutPostingBaleStock() {
        FarmTabCatalog.Catalog hay = catalog().create(mock(TabHost.class),
                FarmWorkspace.HAY_FARM);
        assertThat(titles(hay)).containsExactly(
                "Workspace", "Hay cuttings", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(hay.hayCuttings()).isNotNull();
    }

    @Test
    void hopsWorkspaceUsesYardPlansWithoutPostingHarvestStock() {
        FarmTabCatalog.Catalog hops = catalog().create(mock(TabHost.class),
                FarmWorkspace.HOPS_FARM);
        assertThat(titles(hops)).containsExactly(
                "Workspace", "Hop yards", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(hops.hopYards()).isNotNull();
    }

    @Test
    void grainsWorkspaceUsesRotationPlansWithoutPostingHarvestStock() {
        FarmTabCatalog.Catalog grains = catalog().create(mock(TabHost.class),
                FarmWorkspace.GRAIN_FARM);
        assertThat(titles(grains)).containsExactly(
                "Workspace", "Grain fields", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(grains.grainFields()).isNotNull();
    }

    @Test
    void seedWorkspaceUsesFieldPlansWithoutClaimingHarvestOrSeedStock() {
        FarmTabCatalog.Catalog seeds = catalog().create(mock(TabHost.class),
                FarmWorkspace.SEED_FARM);
        assertThat(titles(seeds)).containsExactly(
                "Workspace", "Seed fields", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(seeds.seedFields()).isNotNull();
    }

    @Test
    void treeNutWorkspaceUsesBlockPlansWithoutClaimingHarvestOrStock() {
        FarmTabCatalog.Catalog nuts = catalog().create(mock(TabHost.class),
                FarmWorkspace.NUT_ORCHARD);
        assertThat(titles(nuts)).containsExactly(
                "Workspace", "Nut blocks", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(nuts.nutBlocks()).isNotNull();
    }

    @Test
    void vegetableWorkspaceUsesSuccessionPlansWithoutClaimingHarvestOrStock() {
        FarmTabCatalog.Catalog vegetables = catalog().create(mock(TabHost.class),
                FarmWorkspace.VEGETABLE_FARM);
        assertThat(titles(vegetables)).containsExactly(
                "Workspace", "Vegetable plantings", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(vegetables.vegetablePlantings()).isNotNull();
    }

    @Test
    void aquacultureWorkspaceUsesCohortPlansWithoutClaimingHarvestClearance() {
        FarmTabCatalog.Catalog aquaculture = catalog().create(mock(TabHost.class),
                FarmWorkspace.AQUACULTURE_FARM);
        assertThat(titles(aquaculture)).containsExactly(
                "Workspace", "Aquatic cohorts", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(aquaculture.aquaticCohorts()).isNotNull();
        assertThat(titles(aquaculture)).doesNotContain("Harvest Log");
    }

    @Test
    void aquaculturePotatoAndVegetableMixKeepsPlansAndSharedToolsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.AQUACULTURE_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.AQUACULTURE_FARM, FarmWorkspace.VEGETABLE_FARM,
                FarmWorkspace.POTATO_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.AQUACULTURE_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Field plans",
                "Vegetable plantings", "Aquatic cohorts", "Harvest Log", "Inventory",
                "Knowledge Log", "Customers & Orders", "Reports", "Activity History");
        assertThat(mixed).containsOnlyOnce("Inventory", "Customers & Orders");
    }

    @Test
    void aquacultureAndVegetableMixDoesNotImplyActualHarvestLog() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.AQUACULTURE_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.AQUACULTURE_FARM, FarmWorkspace.VEGETABLE_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.AQUACULTURE_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Vegetable plantings",
                "Aquatic cohorts", "Inventory", "Knowledge Log", "Customers & Orders",
                "Reports", "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void christmasTreeWorkspaceUsesSeasonalBlockPlansWithoutClaimingCutsOrStock() {
        FarmTabCatalog.Catalog trees = catalog().create(mock(TabHost.class),
                FarmWorkspace.CHRISTMAS_TREE_FARM);
        assertThat(titles(trees)).containsExactly(
                "Workspace", "Tree blocks", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(trees.treeBlocks()).isNotNull();
        assertThat(titles(trees)).doesNotContain("Harvest Log");
    }

    @Test
    void christmasTreeAndNurseryMixKeepsDistinctPlansWithoutActualHarvestLog() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.CHRISTMAS_TREE_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.CHRISTMAS_TREE_FARM, FarmWorkspace.NURSERY_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.CHRISTMAS_TREE_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Growing batches", "Tree blocks",
                "Inventory", "Knowledge Log", "Customers & Orders", "Reports",
                "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void christmasTreeAndPotatoMixKeepsSharedRecordsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.CHRISTMAS_TREE_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.CHRISTMAS_TREE_FARM, FarmWorkspace.POTATO_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.CHRISTMAS_TREE_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Field plans", "Tree blocks",
                "Harvest Log", "Inventory", "Knowledge Log", "Customers & Orders",
                "Reports", "Activity History");
        assertThat(mixed).containsOnlyOnce("Inventory", "Customers & Orders");
    }

    @Test
    void apiaryWorkspaceUsesDatedHiveChecksWithoutClaimingHoneyHarvest() {
        FarmTabCatalog.Catalog apiary = catalog().create(mock(TabHost.class),
                FarmWorkspace.APIARY_FARM);
        assertThat(titles(apiary)).containsExactly(
                "Workspace", "Hive checks", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(apiary.hiveChecks()).isNotNull();
        assertThat(titles(apiary)).doesNotContain("Harvest Log");
    }

    @Test
    void apiaryAndNurseryMixKeepsSeparateToolsAndNoGenericHarvestLog() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.APIARY_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.APIARY_FARM, FarmWorkspace.NURSERY_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.APIARY_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Growing batches", "Hive checks",
                "Inventory", "Knowledge Log", "Customers & Orders", "Reports",
                "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void apiaryAndAppleMixKeepsSharedRecordsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.APIARY_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.APIARY_FARM, FarmWorkspace.APPLE_ORCHARD)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.APIARY_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Orchard blocks", "Hive checks",
                "Harvest Log", "Inventory", "Knowledge Log", "Customers & Orders",
                "Reports", "Activity History");
        assertThat(mixed).containsOnlyOnce("Inventory", "Customers & Orders");
    }

    @Test
    void mushroomWorkspaceUsesItsBatchPlannerWithoutClaimingHarvest() {
        FarmTabCatalog.Catalog mushrooms = catalog().create(mock(TabHost.class),
                FarmWorkspace.MUSHROOM_FARM);
        assertThat(titles(mushrooms)).containsExactly("Workspace", "Mushroom batches",
                "Inventory", "Knowledge Log", "Customers & Orders", "Reports",
                "Activity History");
        assertThat(mushrooms.mushroomBatches()).isNotNull();
        assertThat(titles(mushrooms)).doesNotContain("Harvest Log");
    }

    @Test
    void mushroomAndPotatoMixKeepsBatchAndFieldPlansWithSharedToolsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.MUSHROOM_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.MUSHROOM_FARM, FarmWorkspace.POTATO_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.MUSHROOM_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Field plans", "Mushroom batches",
                "Harvest Log", "Inventory", "Knowledge Log", "Customers & Orders",
                "Reports", "Activity History");
        assertThat(mixed).containsOnlyOnce("Inventory", "Customers & Orders");
    }

    @Test
    void cranberryWorkspaceUsesBedPlansWithoutClaimingHarvest() {
        FarmTabCatalog.Catalog cranberries = catalog().create(mock(TabHost.class),
                FarmWorkspace.CRANBERRY_FARM);
        assertThat(titles(cranberries)).containsExactly("Workspace", "Cranberry beds",
                "Inventory", "Knowledge Log", "Customers & Orders", "Reports",
                "Activity History");
        assertThat(cranberries.cranberryBeds()).isNotNull();
        assertThat(titles(cranberries)).doesNotContain("Harvest Log");
    }

    @Test
    void cranberryAndMushroomMixKeepsBothPlansAndSharedToolsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.CRANBERRY_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.CRANBERRY_FARM, FarmWorkspace.MUSHROOM_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.CRANBERRY_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Mushroom batches",
                "Cranberry beds", "Inventory", "Knowledge Log", "Customers & Orders",
                "Reports", "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
        assertThat(mixed).containsOnlyOnce("Inventory", "Customers & Orders");
    }

    @Test
    void pumpkinPatchUsesSeasonalPlansWithoutClaimingVisitorTransactions() {
        FarmTabCatalog.Catalog pumpkins = catalog().create(mock(TabHost.class),
                FarmWorkspace.PUMPKIN_PATCH);
        assertThat(titles(pumpkins)).containsExactly("Workspace", "Pumpkin patches",
                "Inventory", "Knowledge Log", "Customers & Orders", "Reports",
                "Activity History");
        assertThat(pumpkins.pumpkinPatches()).isNotNull();
        assertThat(titles(pumpkins)).doesNotContain("Harvest Log");
    }

    @Test
    void pumpkinAndCranberryMixKeepsBothPlansAndSharedToolsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.PUMPKIN_PATCH);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.PUMPKIN_PATCH, FarmWorkspace.CRANBERRY_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.PUMPKIN_PATCH, settings));
        assertThat(mixed).containsExactly("Workspace", "Cranberry beds",
                "Pumpkin patches", "Inventory", "Knowledge Log", "Customers & Orders",
                "Reports", "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
        assertThat(mixed).containsOnlyOnce("Inventory", "Customers & Orders");
    }

    @Test
    void vegetableNutAndPotatoMixKeepsPlansAndSharedRecordsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.VEGETABLE_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.VEGETABLE_FARM, FarmWorkspace.NUT_ORCHARD,
                FarmWorkspace.POTATO_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.VEGETABLE_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Field plans", "Nut blocks",
                "Vegetable plantings", "Harvest Log", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(mixed).containsOnlyOnce("Inventory", "Customers & Orders");
    }

    @Test
    void vegetableAndNutMixDoesNotImplyActualHarvestLog() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.VEGETABLE_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.VEGETABLE_FARM, FarmWorkspace.NUT_ORCHARD)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.VEGETABLE_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Nut blocks",
                "Vegetable plantings", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void nutSeedAndAppleMixKeepsThreePlansAndSharedRecordsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.NUT_ORCHARD);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.NUT_ORCHARD, FarmWorkspace.SEED_FARM,
                FarmWorkspace.APPLE_ORCHARD)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.NUT_ORCHARD, settings));
        assertThat(mixed).containsExactly("Workspace", "Orchard blocks",
                "Seed fields", "Nut blocks", "Harvest Log", "Inventory",
                "Knowledge Log", "Customers & Orders", "Reports",
                "Activity History");
        assertThat(mixed).containsOnlyOnce("Inventory", "Customers & Orders");
    }

    @Test
    void nutAndSeedMixDoesNotImplyActualHarvestLog() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.NUT_ORCHARD);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.NUT_ORCHARD, FarmWorkspace.SEED_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.NUT_ORCHARD, settings));
        assertThat(mixed).containsExactly("Workspace", "Seed fields", "Nut blocks",
                "Inventory", "Knowledge Log", "Customers & Orders", "Reports",
                "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void seedGrainAndHayMixKeepsEachPlanAndSharedRecordsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.SEED_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.SEED_FARM, FarmWorkspace.GRAIN_FARM,
                FarmWorkspace.HAY_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.SEED_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Hay cuttings",
                "Grain fields", "Seed fields", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void grainHayAndHopsMixKeepsThreePlansAndSharedRecordsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.GRAIN_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.GRAIN_FARM, FarmWorkspace.HAY_FARM,
                FarmWorkspace.HOPS_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.GRAIN_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Hay cuttings", "Hop yards",
                "Grain fields", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void hopsAndHayMixKeepsBothPlansAndSharedRecordsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.HOPS_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.HOPS_FARM, FarmWorkspace.HAY_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.HOPS_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Hay cuttings", "Hop yards",
                "Inventory", "Knowledge Log", "Customers & Orders", "Reports",
                "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void hayDairyLivestockMixKeepsThreeToolsAndSharedRecordsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.HAY_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.HAY_FARM, FarmWorkspace.DAIRY_FARM,
                FarmWorkspace.LIVESTOCK_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.HAY_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Herd groups",
                "Milk collections", "Hay cuttings", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void vineyardAndDairyMixKeepsSpecialtyPlansAndSharedToolsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.VINEYARD_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.VINEYARD_FARM, FarmWorkspace.DAIRY_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.VINEYARD_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Milk collections",
                "Vineyard blocks", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void dairyLivestockPoultryMixKeepsThreeDistinctRecordsAndSharedToolsOnce() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.DAIRY_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.DAIRY_FARM, FarmWorkspace.LIVESTOCK_FARM,
                FarmWorkspace.POULTRY_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.DAIRY_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Herd groups",
                "Egg collections", "Milk collections", "Inventory", "Knowledge Log",
                "Customers & Orders", "Reports", "Activity History");
        assertThat(mixed).doesNotContain("Harvest Log");
    }

    @Test
    void poultryAndPotatoMixSharesRecordsAndKeepsEggLogSeparateFromHarvest() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.POULTRY_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.POULTRY_FARM, FarmWorkspace.POTATO_FARM)));
        List<String> mixed = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.POULTRY_FARM, settings));
        assertThat(mixed).containsExactly("Workspace", "Field plans", "Egg collections",
                "Harvest Log", "Inventory", "Knowledge Log", "Customers & Orders",
                "Reports", "Activity History");
    }

    @Test
    void potatoCannabisAppleMixShowsThreeSpecialtyToolsAndOneSetOfSharedRecords() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setWorkspace(FarmWorkspace.POTATO_FARM);
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD)));

        FarmTabCatalog.Catalog combined = catalog().create(mock(TabHost.class),
                FarmWorkspace.POTATO_FARM, settings);

        assertThat(titles(combined)).containsExactly("Workspace", "Cultivation",
                "Field plans", "Orchard blocks", "Harvest Log", "Inventory",
                "Knowledge Log", "Customers & Orders", "Reports", "Activity History");
        assertThat(combined.cropAreas()).extracting(FlowerFarmTab::getTabTitle)
                .containsExactly("Field plans", "Orchard blocks");
        assertThat(combined.cropAreas().get(0))
                .isNotSameAs(combined.cropAreas().get(1));
    }

    @Test
    void allNamedFarmTypesCanShareOneWorkspaceWithoutDuplicateTabs() {
        FarmOperationSettings settings = new FarmOperationSettings();
        settings.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.ROSE_AND_EVENTS, FarmWorkspace.WILDFLOWER_FARM,
                FarmWorkspace.FLOWER_SHOP, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD,
                FarmWorkspace.BERRY_FARM, FarmWorkspace.NURSERY_FARM,
                FarmWorkspace.LIVESTOCK_FARM, FarmWorkspace.POULTRY_FARM,
                FarmWorkspace.DAIRY_FARM, FarmWorkspace.VINEYARD_FARM,
                FarmWorkspace.HAY_FARM, FarmWorkspace.HOPS_FARM,
                FarmWorkspace.GRAIN_FARM, FarmWorkspace.SEED_FARM,
                FarmWorkspace.NUT_ORCHARD, FarmWorkspace.VEGETABLE_FARM,
                FarmWorkspace.AQUACULTURE_FARM, FarmWorkspace.CHRISTMAS_TREE_FARM,
                FarmWorkspace.APIARY_FARM, FarmWorkspace.MUSHROOM_FARM,
                FarmWorkspace.CRANBERRY_FARM, FarmWorkspace.PUMPKIN_PATCH)));

        List<String> titles = titles(catalog().create(mock(TabHost.class),
                FarmWorkspace.ROSE_AND_EVENTS, settings));

        assertThat(titles).contains("Cultivation", "Field plans", "Orchard blocks",
                "Berry blocks", "Growing batches", "Herd groups", "Egg collections",
                "Milk collections", "Vineyard blocks", "Hay cuttings", "Hop yards",
                "Grain fields", "Seed fields", "Nut blocks", "Vegetable plantings",
                "Aquatic cohorts", "Tree blocks", "Hive checks", "Mushroom batches",
                "Cranberry beds", "Pumpkin patches",
                "Reference & Planning");
        assertThat(titles).doesNotHaveDuplicates();
        assertThat(titles).containsOnlyOnce("Harvest Log", "Inventory",
                "Customers & Orders", "Reports", "Activity History");
    }

    @Test
    void allFarmTypesPopulateAndRemainReachableAfterLetterShortcutsRunOut() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FarmOperationSettings settings = new FarmOperationSettings();
            settings.setEnabledWorkspaces(new LinkedHashSet<>(
                    java.util.Arrays.asList(FarmWorkspace.values())));
            FarmTabNavigator[] navigator = new FarmTabNavigator[1];
            TabHost host = new TabHost() {
                @Override public void refreshAll() { }
                @Override public void selectTab(String title) { navigator[0].select(title); }
                @Override public void setStatus(String message) { }
                @Override public void runTrendAnalysis() { }
            };
            FarmTabCatalog.Catalog combined = catalog().create(host,
                    FarmWorkspace.ROSE_AND_EVENTS, settings);
            JTabbedPane pane = new JTabbedPane(JTabbedPane.LEFT,
                    JTabbedPane.SCROLL_TAB_LAYOUT);
            navigator[0] = new FarmTabNavigator(pane, combined.tabs(),
                    FarmWorkspace.ROSE_AND_EVENTS, true);

            navigator[0].populate();

            assertThat(pane.getTabCount()).isEqualTo(combined.tabs().size()).isGreaterThan(26);
            assertThat(navigationGroups(pane)).contains("FARM TOOLS & STOCK")
                    .doesNotContain("GROW & STOCK");
            java.util.Set<Integer> used = new java.util.HashSet<>();
            for (int index = 0; index < pane.getTabCount(); index++) {
                int mnemonic = pane.getMnemonicAt(index);
                if (index < 26) {
                    assertThat(mnemonic).isBetween((int) 'A', (int) 'Z');
                    assertThat(used.add(mnemonic)).as("Unique letter shortcut at " + index).isTrue();
                } else {
                    assertThat(mnemonic).as("No duplicate letter shortcut at " + index).isZero();
                    assertThat(pane.getTabComponentAt(index).getAccessibleContext()
                            .getAccessibleName()).doesNotContain("shortcut", "\u0000");
                }
                assertThat(navigator[0].select(pane.getTitleAt(index))).isTrue();
                assertThat(pane.getSelectedIndex()).isEqualTo(index);
            }

            pane.setSelectedIndex(25);
            javax.swing.Action next = pane.getActionMap().get("navigateNext");
            assertThat(next).as("Standard keyboard traversal remains available").isNotNull();
            next.actionPerformed(new java.awt.event.ActionEvent(pane,
                    java.awt.event.ActionEvent.ACTION_PERFORMED, "navigateNext"));
            assertThat(pane.getSelectedIndex()).isEqualTo(26);

            JComponent home = combined.tabs().get(0).getUIComponent();
            JButton activity = findButton(home, "workspace.open.activity-history");
            assertThat(activity).isNotNull();
            activity.doClick();
            assertThat(pane.getTitleAt(pane.getSelectedIndex())).isEqualTo("Activity History");
            assertThat(pane.getMnemonicAt(pane.getSelectedIndex())).isZero();
        });
    }

    @Test
    void productionStudioMatchesTheGrowingOrRetailMix() {
        FarmOperationSettings retail = new FarmOperationSettings();
        retail.setWorkspace(FarmWorkspace.POTATO_FARM);
        retail.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.FLOWER_SHOP)));
        FarmTabCatalog.Catalog retailCatalog = catalog().create(mock(TabHost.class),
                FarmWorkspace.POTATO_FARM, retail);
        assertThat(retailCatalog.productionStudio().getDescription())
                .contains("Floral design recipes");

        FarmOperationSettings grower = new FarmOperationSettings();
        grower.setWorkspace(FarmWorkspace.FLOWER_SHOP);
        grower.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.FLOWER_SHOP, FarmWorkspace.ROSE_AND_EVENTS)));
        FarmTabCatalog.Catalog growerCatalog = catalog().create(mock(TabHost.class),
                FarmWorkspace.FLOWER_SHOP, grower);
        assertThat(growerCatalog.productionStudio().getDescription())
                .contains("Farm templates", "design recipes");
        assertThat(titles(growerCatalog)).contains("Dashboard", "Event Services",
                "Production Studio", "Reference & Planning");
    }

    private static List<String> titles(FarmTabCatalog.Catalog catalog) {
        return allTitles(catalog).stream()
                .filter(title -> !"Field science".equals(title)).toList();
    }

    private static List<String> allTitles(FarmTabCatalog.Catalog catalog) {
        return catalog.tabs().stream().map(FlowerFarmTab::getTabTitle).toList();
    }

    private static JButton findButton(Component root, String name) {
        if (root instanceof JButton button && name.equals(button.getName())) {
            return button;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton found = findButton(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<String> navigationGroups(Component root) {
        List<String> groups = new java.util.ArrayList<>();
        if (root instanceof javax.swing.JLabel label
                && "farm.navigationGroup".equals(label.getName())) {
            groups.add(label.getText());
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                groups.addAll(navigationGroups(child));
            }
        }
        return groups;
    }

    private static FarmTabCatalog catalog() {
        return new FarmTabCatalog(
                mock(InventoryService.class), mock(TrendService.class),
                mock(HarvestService.class), mock(SyncHistoryService.class),
                mock(CustomerService.class), mock(OrderService.class),
                mock(ReportService.class), mock(IrrigationAdvisorService.class),
                mock(MarketDayPackingService.class), mock(MorningBriefingService.class),
                mock(DayCloseoutService.class), mock(CustomerStatementService.class),
                mock(KnowledgeService.class), mock(SeasonPlanService.class),
                mock(ProductionStudioService.class), mock(CannabisCultivationService.class),
                mock(CropAreaPlanService.class), mock(BerryBlockPlanService.class),
                mock(NurseryBatchService.class), mock(HerdGroupService.class),
                mock(EggCollectionService.class), mock(MilkCollectionService.class),
                mock(VineyardBlockPlanService.class), mock(HayCuttingService.class),
                mock(HopYardPlanService.class), mock(GrainFieldPlanService.class),
                mock(SeedFieldPlanService.class), mock(NutBlockPlanService.class),
                mock(VegetablePlantingPlanService.class),
                mock(AquaticCohortPlanService.class),
                mock(ChristmasTreeBlockPlanService.class),
                mock(HiveCheckService.class), mock(MushroomBatchPlanService.class),
                mock(CranberryBedPlanService.class), mock(PumpkinPatchPlanService.class));
    }
}
