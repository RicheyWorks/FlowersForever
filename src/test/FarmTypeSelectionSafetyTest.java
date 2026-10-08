package com.flowerfarm.gui;

import com.flowerfarm.auth.DesktopSession;
import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.auth.FarmUser;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.gui.automation.SwingHarnessBridge;
import com.flowerfarm.gui.tabs.AddItemTab;
import com.flowerfarm.gui.tabs.EggCollectionTab;
import com.flowerfarm.gui.tabs.MilkCollectionTab;
import com.flowerfarm.gui.tabs.VineyardBlockPlanTab;
import com.flowerfarm.gui.tabs.HayCuttingTab;
import com.flowerfarm.gui.tabs.HopYardPlanTab;
import com.flowerfarm.gui.tabs.GrainFieldPlanTab;
import com.flowerfarm.gui.tabs.SeedFieldPlanTab;
import com.flowerfarm.gui.tabs.NutBlockPlanTab;
import com.flowerfarm.gui.tabs.VegetablePlantingTab;
import com.flowerfarm.gui.tabs.AquaticCohortTab;
import com.flowerfarm.gui.tabs.ChristmasTreeBlockTab;
import com.flowerfarm.gui.tabs.HiveCheckTab;
import com.flowerfarm.gui.tabs.MushroomBatchTab;
import com.flowerfarm.gui.tabs.CranberryBedTab;
import com.flowerfarm.gui.tabs.PumpkinPatchTab;
import com.flowerfarm.model.FarmEnterprise;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.FarmOperationSettingsService;
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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.swing.JOptionPane;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FarmTypeSelectionSafetyTest {

    @Test
    void ownerCannotReplaceAWorkspaceWithAnUnfinishedItemDraft() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        AddItemTab addItem = mock(AddItemTab.class);
        when(addItem.applicationExitWarning())
                .thenReturn("Add Item has an unsaved inventory item draft.");
        ReflectionTestUtils.setField(gui, "addItemTab", addItem);

        gui.selectFarmType(FarmWorkspace.CANNABIS_FARM);

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void ownerCannotEditFarmCombinationWithAnUnfinishedItemDraft() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        AddItemTab addItem = mock(AddItemTab.class);
        when(addItem.applicationExitWarning())
                .thenReturn("Add Item has an unsaved inventory item draft.");
        ReflectionTestUtils.setField(gui, "addItemTab", addItem);

        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedEggCollectionBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        EggCollectionTab eggs = mock(EggCollectionTab.class);
        when(eggs.applicationExitWarning())
                .thenReturn("There is an unsaved egg collection draft.");
        ReflectionTestUtils.setField(gui, "eggCollectionTab", eggs);

        gui.selectFarmType(FarmWorkspace.POTATO_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedMilkCollectionBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        MilkCollectionTab milk = mock(MilkCollectionTab.class);
        when(milk.applicationExitWarning())
                .thenReturn("There is an unsaved milk collection draft.");
        ReflectionTestUtils.setField(gui, "milkCollectionTab", milk);

        gui.selectFarmType(FarmWorkspace.POTATO_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedVineyardBlockBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        VineyardBlockPlanTab vineyard = mock(VineyardBlockPlanTab.class);
        when(vineyard.applicationExitWarning())
                .thenReturn("There is an unsaved vineyard block draft.");
        ReflectionTestUtils.setField(gui, "vineyardBlockPlanTab", vineyard);

        gui.selectFarmType(FarmWorkspace.VINEYARD_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedHayCuttingBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        HayCuttingTab hay = mock(HayCuttingTab.class);
        when(hay.applicationExitWarning())
                .thenReturn("There is an unsaved hay cutting draft.");
        ReflectionTestUtils.setField(gui, "hayCuttingTab", hay);

        gui.selectFarmType(FarmWorkspace.HAY_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedHopYardBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        HopYardPlanTab hops = mock(HopYardPlanTab.class);
        when(hops.applicationExitWarning())
                .thenReturn("There is an unsaved hop yard draft.");
        ReflectionTestUtils.setField(gui, "hopYardPlanTab", hops);

        gui.selectFarmType(FarmWorkspace.HOPS_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedGrainFieldBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        GrainFieldPlanTab grains = mock(GrainFieldPlanTab.class);
        when(grains.applicationExitWarning())
                .thenReturn("There is an unsaved grain field draft.");
        ReflectionTestUtils.setField(gui, "grainFieldPlanTab", grains);

        gui.selectFarmType(FarmWorkspace.GRAIN_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedSeedFieldBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        SeedFieldPlanTab seed = mock(SeedFieldPlanTab.class);
        when(seed.applicationExitWarning())
                .thenReturn("There is an unsaved seed field draft.");
        ReflectionTestUtils.setField(gui, "seedFieldPlanTab", seed);

        gui.selectFarmType(FarmWorkspace.SEED_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedNutBlockBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        NutBlockPlanTab nuts = mock(NutBlockPlanTab.class);
        when(nuts.applicationExitWarning())
                .thenReturn("There is an unsaved nut block draft.");
        ReflectionTestUtils.setField(gui, "nutBlockPlanTab", nuts);

        gui.selectFarmType(FarmWorkspace.NUT_ORCHARD);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedVegetablePlantingBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        VegetablePlantingTab vegetables = mock(VegetablePlantingTab.class);
        when(vegetables.applicationExitWarning())
                .thenReturn("There is an unsaved vegetable planting draft.");
        ReflectionTestUtils.setField(gui, "vegetablePlantingTab", vegetables);

        gui.selectFarmType(FarmWorkspace.VEGETABLE_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedAquaticCohortBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        AquaticCohortTab aquatic = mock(AquaticCohortTab.class);
        when(aquatic.applicationExitWarning())
                .thenReturn("There is an unsaved aquatic cohort draft.");
        ReflectionTestUtils.setField(gui, "aquaticCohortTab", aquatic);

        gui.selectFarmType(FarmWorkspace.AQUACULTURE_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedChristmasTreeBlockBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        ChristmasTreeBlockTab trees = mock(ChristmasTreeBlockTab.class);
        when(trees.applicationExitWarning())
                .thenReturn("There is an unsaved Christmas tree block draft.");
        ReflectionTestUtils.setField(gui, "christmasTreeBlockTab", trees);

        gui.selectFarmType(FarmWorkspace.CHRISTMAS_TREE_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedHiveCheckBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        HiveCheckTab hiveChecks = mock(HiveCheckTab.class);
        when(hiveChecks.applicationExitWarning())
                .thenReturn("There is an unsaved hive check draft.");
        ReflectionTestUtils.setField(gui, "hiveCheckTab", hiveChecks);

        gui.selectFarmType(FarmWorkspace.APIARY_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedMushroomBatchBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        MushroomBatchTab mushrooms = mock(MushroomBatchTab.class);
        when(mushrooms.applicationExitWarning())
                .thenReturn("There is an unsaved mushroom batch draft.");
        ReflectionTestUtils.setField(gui, "mushroomBatchTab", mushrooms);

        gui.selectFarmType(FarmWorkspace.MUSHROOM_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedCranberryBedBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        CranberryBedTab cranberries = mock(CranberryBedTab.class);
        when(cranberries.applicationExitWarning())
                .thenReturn("There is an unsaved cranberry bed draft.");
        ReflectionTestUtils.setField(gui, "cranberryBedTab", cranberries);

        gui.selectFarmType(FarmWorkspace.CRANBERRY_FARM);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void unfinishedPumpkinPatchBlocksTypeAndMixChanges() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        PumpkinPatchTab pumpkins = mock(PumpkinPatchTab.class);
        when(pumpkins.applicationExitWarning())
                .thenReturn("There is an unsaved pumpkin patch draft.");
        ReflectionTestUtils.setField(gui, "pumpkinPatchTab", pumpkins);

        gui.selectFarmType(FarmWorkspace.PUMPKIN_PATCH);
        gui.showFarmCombination();

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm type were kept"),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Your current draft and farm mix were kept"),
                eq("Farm mix unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void failedCombinationSaveKeepsTheEditedMixAvailable() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmOperationSettings current = new FarmOperationSettings();
        current.setWorkspace(FarmWorkspace.POTATO_FARM);
        current.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM)));
        current.setEnterprises(java.util.Set.of(FarmEnterprise.ROW_CROPS,
                FarmEnterprise.CANNABIS));
        current.setPrimaryEnterprise(FarmEnterprise.ROW_CROPS);
        current.setFlowerOfferings(java.util.Set.of());
        when(service.getSettings()).thenReturn(current);
        when(service.saveSettings(any()))
                .thenThrow(new IllegalStateException("Store temporarily unavailable"));
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .chooseForm("Combine farm types", "Save combination", form -> form
                        .select("Main farm type", "Cannabis farm"))
                .chooseForm("Farm mix not saved", "Edit mix", form -> form
                        .expectText("Your selections are kept here"))
                .chooseForm("Combine farm types", "Cancel", form -> form
                        .expectText("Main farm type: Cannabis farm"));
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        ReflectionTestUtils.setField(gui, "currentWorkspace", FarmWorkspace.POTATO_FARM);
        ReflectionTestUtils.setField(gui, "currentOperationSettings", current);

        gui.showFarmCombination();

        verify(service).saveSettings(any());
        assertThat(ReflectionTestUtils.getField(gui, "currentWorkspace"))
                .isEqualTo(FarmWorkspace.POTATO_FARM);
        dialogs.assertComplete();
    }

    @Test
    void viewerCanEnterCurrentTypeButCannotChangeTheInstallation() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.VIEWER, service, dialogs);

        gui.selectFarmType(FarmWorkspace.ROSE_AND_EVENTS);
        gui.selectFarmType(FarmWorkspace.FLOWER_SHOP);

        verify(service, never()).saveSettings(any());
        verify(dialogs).showMessage(any(), eq("Only an owner can change the farm type."),
                eq("Farm type unchanged"), eq(JOptionPane.INFORMATION_MESSAGE));
    }

    @Test
    void openingAnIncludedTypeNeverReplacesTheSavedCombination() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.VIEWER, service, dialogs);
        FarmOperationSettings mix = new FarmOperationSettings();
        mix.setWorkspace(FarmWorkspace.POTATO_FARM);
        mix.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD)));
        ReflectionTestUtils.setField(gui, "currentWorkspace", FarmWorkspace.POTATO_FARM);
        ReflectionTestUtils.setField(gui, "currentOperationSettings", mix);

        gui.selectFarmType(FarmWorkspace.APPLE_ORCHARD);

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        assertThat(ReflectionTestUtils.getField(gui, "currentWorkspace"))
                .isEqualTo(FarmWorkspace.POTATO_FARM);
        assertThat(mix.getEnabledWorkspaces()).contains(FarmWorkspace.POTATO_FARM,
                FarmWorkspace.CANNABIS_FARM, FarmWorkspace.APPLE_ORCHARD);
    }

    @Test
    void cancellingAddFromAnOutsideCardKeepsTheCurrentMix() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmOperationSettings mix = combinedPotatoAndCannabis();
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .chooseForm("Combine farm types", "Cancel", form -> form
                        .expectText("Cannabis farm + Potato farm + Apple orchard"));
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        ReflectionTestUtils.setField(gui, "currentWorkspace", FarmWorkspace.POTATO_FARM);
        ReflectionTestUtils.setField(gui, "currentOperationSettings", mix);

        gui.selectFarmType(FarmWorkspace.APPLE_ORCHARD);

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        assertThat(ReflectionTestUtils.getField(gui, "currentWorkspace"))
                .isEqualTo(FarmWorkspace.POTATO_FARM);
        assertThat(mix.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM);
        dialogs.assertComplete();
    }

    @Test
    void addFromAnOutsideCardProposesAllExistingTypesAndKeepsThemOnSaveFailure() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmOperationSettings mix = combinedPotatoAndCannabis();
        when(service.getSettings()).thenReturn(mix);
        when(service.saveSettings(any())).thenAnswer(invocation -> {
            FarmOperationSettings proposed = invocation.getArgument(0);
            assertThat(proposed.getWorkspace()).isEqualTo(FarmWorkspace.POTATO_FARM);
            assertThat(proposed.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                    FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                    FarmWorkspace.APPLE_ORCHARD);
            throw new IllegalStateException("Store unavailable");
        });
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .chooseForm("Combine farm types", "Save combination", form -> form
                        .expectText("Cannabis farm + Potato farm + Apple orchard"))
                .choose("Farm mix not saved", "Cancel");
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        ReflectionTestUtils.setField(gui, "currentWorkspace", FarmWorkspace.POTATO_FARM);
        ReflectionTestUtils.setField(gui, "currentOperationSettings", mix);

        gui.selectFarmType(FarmWorkspace.APPLE_ORCHARD);

        verify(service).saveSettings(any());
        assertThat(ReflectionTestUtils.getField(gui, "currentWorkspace"))
                .isEqualTo(FarmWorkspace.POTATO_FARM);
        assertThat(mix.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM);
        dialogs.assertComplete();
    }

    @Test
    void customFarmCardProposesAddingGeneralRecordsToTheCurrentMix() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmOperationSettings mix = combinedPotatoAndCannabis();
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .chooseForm("Combine farm types", "Cancel", form -> form
                        .expectText("Cannabis farm + Potato farm + Custom farm"));
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);
        ReflectionTestUtils.setField(gui, "currentWorkspace", FarmWorkspace.POTATO_FARM);
        ReflectionTestUtils.setField(gui, "currentOperationSettings", mix);

        gui.selectFarmType(FarmWorkspace.CUSTOM_FARM);

        verify(service, never()).getSettings();
        verify(service, never()).saveSettings(any());
        assertThat(mix.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM);
        dialogs.assertComplete();
    }

    private static FarmOperationSettings combinedPotatoAndCannabis() {
        FarmOperationSettings mix = new FarmOperationSettings();
        mix.setWorkspace(FarmWorkspace.POTATO_FARM);
        mix.setEnabledWorkspaces(new LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM)));
        mix.setEnterprises(java.util.Set.of(FarmEnterprise.ROW_CROPS,
                FarmEnterprise.CANNABIS));
        mix.setPrimaryEnterprise(FarmEnterprise.ROW_CROPS);
        mix.setFlowerOfferings(java.util.Set.of());
        return mix;
    }

    @Test
    void failedSaveLeavesWorkspaceUnchangedAfterReplacingTheFlowerStarter() {
        FarmOperationSettingsService service = mock(FarmOperationSettingsService.class);
        FarmOperationSettings saved = new FarmOperationSettings();
        when(service.getSettings()).thenReturn(saved);
        when(service.saveSettings(any())).thenAnswer(invocation -> {
            FarmOperationSettings selected = invocation.getArgument(0);
            assertThat(selected.getWorkspace()).isEqualTo(FarmWorkspace.FLOWER_SHOP);
            assertThat(selected.getEnterprises()).containsExactly(
                    FarmEnterprise.FLORAL_RETAIL);
            throw new IllegalStateException("Store unavailable");
        });
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(FarmRole.OWNER, service, dialogs);

        gui.selectFarmType(FarmWorkspace.FLOWER_SHOP);

        assertThat(ReflectionTestUtils.getField(gui, "currentWorkspace"))
                .isEqualTo(FarmWorkspace.ROSE_AND_EVENTS);
        verify(dialogs).showMessage(any(), org.mockito.ArgumentMatchers.contains(
                "Farm type was not changed"), eq("Farm type unavailable"),
                eq(JOptionPane.ERROR_MESSAGE));
    }

    private static FlowerFarmGUI gui(FarmRole role,
                                     FarmOperationSettingsService service,
                                     FarmDialogs dialogs) {
        DesktopSession session = new DesktopSession();
        session.setAuthEnabled(true);
        session.set(new FarmUser("tester", "", role));
        return new FlowerFarmGUI(
                mock(InventoryService.class), mock(TrendService.class),
                mock(ConnectorRegistry.class), mock(HarvestService.class),
                mock(SyncHistoryService.class), mock(CustomerService.class),
                mock(OrderService.class), mock(ReportService.class),
                mock(IrrigationAdvisorService.class), mock(MarketDayPackingService.class),
                mock(MorningBriefingService.class), mock(DayCloseoutService.class),
                mock(CustomerStatementService.class), mock(KnowledgeService.class),
                mock(SeasonPlanService.class), mock(ProductionStudioService.class),
                mock(GuiLoginGate.class), session, new FarmProfile(), service,
                new SwingHarnessBridge(), dialogs, mock(FarmFileChooser.class));
    }
}
