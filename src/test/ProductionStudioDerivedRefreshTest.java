package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.IntegrationHandoff;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.ProductionStudioService;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionStudioDerivedRefreshTest {
    private static final int YEAR = Year.now().getValue();

    @Test
    void completingWorkRefreshesCostReadoutsWithoutReplacingUnrelatedDrafts() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        ProductionTask task = task();
        when(service.listTasks(any(), any())).thenReturn(List.of(task));
        when(service.productionCosts(YEAR, null))
                .thenAnswer(ignored -> costs(true, task.hasActuals()));
        when(service.recordTaskActuals(eq(19L), any())).thenAnswer(invocation -> {
            ProductionStudioService.TaskActuals actuals = invocation.getArgument(1);
            task.recordActuals(actuals.actualMinutes(), actuals.inputCost(),
                    actuals.fieldObservation(), Instant.now());
            return task;
        });
        SwingUserHarness user = build(service, new SwingDialogHarness().safelyCancelUnscripted());
        user.type("Source / station", "Unsaved farm observation")
                .type("Labor cost per hour ($)", "37.50")
                .selectTab("Design recipes").type("Recipe name", "Unfinished bouquet")
                .selectTab("Plantings & work").selectTab("Field work")
                .selectRow("production.taskTable", 0)
                .type("Crew minutes", "60")
                .type("Input cost ($)", "25")
                .type("Field result or observation", "Bed amended")
                .click("Save actuals + done").awaitText("field result saved")
                .selectTab("Economics & handoffs").selectTab("Production costs");

        assertThat(user.tableValue("production.costTable", 0, 7)).isEqualTo("$50.00");
        assertThat(user.text("production.costSummary")).contains("Actuals 1/1 tasks", "total $50.00");
        user.selectTab("Frost & crop templates");
        assertThat(user.fieldValue("Source / station")).isEqualTo("Unsaved farm observation");
        assertThat(user.fieldValue("Labor cost per hour ($)")).isEqualTo("37.50");
        user.selectTab("Design recipes");
        assertThat(user.fieldValue("Recipe name")).isEqualTo("Unfinished bouquet");
        verify(service, times(1)).recordTaskActuals(eq(19L), any());
    }

    @Test
    void committingPlantingsRefreshesAvailabilityAndCostsInsideTheSameWorkspace() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        AtomicBoolean committed = stubPlanting(service);
        when(service.productionCosts(YEAR, null)).thenAnswer(ignored -> costs(committed.get(), false));
        SwingDialogHarness dialogs = new SwingDialogHarness().confirm("Commit production plan", true);
        SwingUserHarness user = build(service, dialogs);
        user.type("Source / station", "Keep this frost draft")
                .selectTab("Plantings & work").click("Commit plan & tasks")
                .selectTab("Economics & handoffs").selectTab("Planned + live availability");

        assertThat(user.tableValue("production.availabilityTable", 0, 2)).isEqualTo(100.0);
        assertThat(user.tableValue("production.availabilityTable", 0, 4)).isEqualTo(110.0);
        user.selectTab("Production costs");
        assertThat(user.tableRows("production.costTable")).isEqualTo(1);
        assertThat(user.text("production.costSummary")).contains("100 expected stems");
        user.selectTab("Frost & crop templates");
        assertThat(user.fieldValue("Source / station")).isEqualTo("Keep this frost draft");
        verify(service, times(1)).commitPlantings(anyLong(), anyInt(), anyString(), anyDouble(), anyInt(), anyString());
        dialogs.assertComplete();
    }

    @Test
    void failedDependentRefreshPreservesTheCommittedResultAndOffersReadOnlyRecovery() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        AtomicBoolean committed = stubPlanting(service);
        AtomicBoolean costsAvailable = new AtomicBoolean(false);
        when(service.productionCosts(YEAR, null)).thenAnswer(ignored -> {
            if (committed.get() && !costsAvailable.get()) {
                throw new IllegalStateException("Cost report unavailable");
            }
            return costs(committed.get(), false);
        });
        SwingDialogHarness dialogs = new SwingDialogHarness().confirm("Commit production plan", true);
        SwingUserHarness user = build(service, dialogs);
        user.selectTab("Plantings & work").click("Commit plan & tasks");

        assertThat(user.text("production.status")).contains("Committed 1 planting block(s)",
                "previous data", "Refresh costs", "do not repeat the saved action");
        user.selectTab("Economics & handoffs").selectTab("Planned + live availability");
        assertThat(user.tableValue("production.availabilityTable", 0, 2)).isEqualTo(100.0);
        user.selectTab("Production costs");
        assertThat(user.tableRows("production.costTable")).isZero();
        costsAvailable.set(true);
        user.click("Refresh costs");
        assertThat(user.tableRows("production.costTable")).isEqualTo(1);
        verify(service, times(1)).commitPlantings(anyLong(), anyInt(), anyString(), anyDouble(), anyInt(), anyString());
        dialogs.assertComplete();
    }

    @Test
    void invalidHiddenCostFilterCannotTurnASavedTaskIntoAFailedSave() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        ProductionTask task = task();
        when(service.listTasks(any(), any())).thenReturn(List.of(task));
        when(service.productionCosts(YEAR, null)).thenReturn(costs(true, false));
        when(service.recordTaskActuals(eq(19L), any())).thenReturn(task);
        SwingDialogHarness dialogs = new SwingDialogHarness().safelyCancelUnscripted();
        SwingUserHarness user = build(service, dialogs);
        user.selectTab("Economics & handoffs").selectTab("Production costs")
                .type("Season", "unfinished year")
                .selectTab("Plantings & work").selectTab("Field work").selectRow("production.taskTable", 0)
                .click("Save actuals + done").awaitText("field result saved");

        assertThat(user.text("production.status")).contains("field result saved", "Refresh costs",
                "do not repeat the saved action");
        user.selectTab("Economics & handoffs").selectTab("Production costs");
        assertThat(user.fieldValue("Season")).isEqualTo("unfinished year");
        assertThat(user.tableValue("production.costTable", 0, 7)).isEqualTo("$0.00");
        verify(service, times(1)).recordTaskActuals(eq(19L), any());
        dialogs.assertComplete();
    }

    @Test
    void savedFrostSettingsRemainSuccessfulWhenCostRefreshFailsAndTaskDraftSurvives() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        when(service.listTasks(any(), any())).thenReturn(List.of(task()));
        when(service.productionCosts(YEAR, null)).thenReturn(costs(true, false))
                .thenThrow(new IllegalStateException("Report unavailable"))
                .thenReturn(costs(true, true));
        SwingDialogHarness dialogs = new SwingDialogHarness();
        SwingUserHarness user = build(service, dialogs);
        user.selectTab("Plantings & work").selectTab("Field work").selectRow("production.taskTable", 0)
                .type("Crew minutes", "35")
                .type("Input cost ($)", "4.75")
                .type("Field result or observation", "Unfinished task observation")
                .selectTab("Frost & crop templates")
                .type("Source / station", "Reviewed farm frost dates")
                .type("Labor cost per hour ($)", "37.50")
                .click("Save frost anchors");

        assertThat(user.text("production.status")).contains("Saved frost anchors", "Refresh costs",
                "do not repeat the saved action");
        assertThat(user.fieldValue("Labor cost per hour ($)")).isEqualTo("37.50");
        user.selectTab("Plantings & work");
        assertThat(user.fieldValue("Crew minutes")).isEqualTo("35");
        assertThat(user.fieldValue("Input cost ($)")).isEqualTo("4.75");
        assertThat(user.fieldValue("Field result or observation")).isEqualTo("Unfinished task observation");
        user.selectTab("Economics & handoffs").selectTab("Production costs");
        assertThat(user.tableValue("production.costTable", 0, 7)).isEqualTo("$0.00");
        user.click("Refresh costs");
        assertThat(user.tableValue("production.costTable", 0, 7)).isEqualTo("$50.00");
        var settings = org.mockito.ArgumentCaptor.forClass(FarmSeasonSettings.class);
        verify(service, times(1)).saveSeasonSettings(settings.capture());
        assertThat(settings.getValue().getLoadedLaborRatePerHour()).isEqualTo(37.50);
        verify(service, times(1)).listTasks(any(), any());
        dialogs.assertComplete();
    }

    @Test
    void deletingRecipeRefreshesClearedHandoffLinksWithoutReplacingHandoffDraft() throws Exception {
        ProductionStudioService service = mock(ProductionStudioService.class);
        FloralRecipe recipe = new FloralRecipe("Market bouquet", "Bouquet", 35, 12, 25, 15, 10, "");
        recipe.setId(11L);
        IntegrationHandoff handoff = new IntegrationHandoff("MARKETPLACE", "Market partner", "READY",
                "listing-101", null, 11L, 35, "USD", "Bouquet listing");
        AtomicBoolean deleted = new AtomicBoolean();
        when(service.listRecipes()).thenAnswer(ignored -> deleted.get() ? List.of() : List.of(recipe));
        when(service.listHandoffs()).thenReturn(List.of(handoff));
        doAnswer(ignored -> {
            deleted.set(true);
            handoff.setRelatedRecipeId(null);
            return null;
        }).when(service).deleteRecipe(11L);
        SwingDialogHarness dialogs = new SwingDialogHarness().confirm("Delete recipe", true);
        SwingUserHarness user = build(service, dialogs);
        user.selectTab("Economics & handoffs").selectTab("External handoffs")
                .type("Provider", "Unsent provider")
                .type("External reference", "draft-reference");
        assertThat(user.tableValue("production.handoffTable", 0, 5)).isEqualTo(11L);
        user.selectTab("Design recipes").selectRow("production.recipeTable", 0).click("Delete selected")
                .selectTab("Economics & handoffs").selectTab("External handoffs");

        assertThat(user.tableRows("production.handoffTable")).isEqualTo(1);
        assertThat(user.tableValue("production.handoffTable", 0, 5)).isNull();
        assertThat(user.tableValue("production.handoffTable", 0, 7)).isEqualTo("listing-101");
        assertThat(user.fieldValue("Provider")).isEqualTo("Unsent provider");
        assertThat(user.fieldValue("External reference")).isEqualTo("draft-reference");
        verify(service, times(1)).deleteRecipe(11L);
        dialogs.assertComplete();
    }

    private static AtomicBoolean stubPlanting(ProductionStudioService service) {
        AtomicBoolean committed = new AtomicBoolean();
        GrowingTemplate template = new GrowingTemplate("Zinnia production", "Zinnia", "", 9, 4,
                -42, null, 90, 21, 14, 3, 85, 14, 10, true, "");
        template.setId(3L);
        when(service.listTemplates()).thenReturn(List.of(template));
        ProductionStudioService.PlantingDraft draft = new ProductionStudioService.PlantingDraft(
                YEAR, 3L, template.getName(), "Zinnia", "", "Bed A", 50, 1,
                LocalDate.now(), null, LocalDate.now(), LocalDate.now().plusDays(20),
                100, 100, 100, false, "Inside window");
        when(service.draftPlantings(anyLong(), anyInt(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(draft));
        when(service.plantingFingerprint(List.of(draft))).thenReturn("reviewed-preview");
        SeasonPlanEntry plan = new SeasonPlanEntry(YEAR, "Zinnia", "", "Bed A",
                draft.sowDate(), null, draft.harvestStart(), draft.harvestEnd(),
                50, 100, SeasonPlanStatus.PLANNED, "");
        plan.setId(4L);
        when(service.commitPlantings(anyLong(), anyInt(), anyString(), anyDouble(), anyInt(), anyString()))
                .thenAnswer(ignored -> {
                    committed.set(true);
                    return List.of(new ProductionStudioService.CommittedPlanting(plan, List.of(task())));
                });
        when(service.listTasks(any(), any())).thenAnswer(ignored -> committed.get() ? List.of(task()) : List.of());
        when(service.availability(any())).thenAnswer(invocation -> {
            LocalDate week = invocation.getArgument(0);
            return List.of(new ProductionStudioService.AvailabilityLine("Zinnia", week, week.plusDays(6),
                    committed.get() ? 100 : 0, 10, committed.get() ? 110 : 10, "Current season"));
        });
        return committed;
    }

    private static ProductionTask task() {
        ProductionTask task = new ProductionTask(4L, 3L, LocalDate.now(),
                ProductionTaskType.SOIL_PREP, ProductionTaskStatus.TODO, "Zinnia", "Bed A",
                1, true, "Prepare bed");
        task.setId(19L);
        return task;
    }

    private static ProductionStudioService.ProductionCostReport costs(boolean planted, boolean actualized) {
        int minutes = actualized ? 60 : 0;
        double inputs = actualized ? 25 : 0;
        double total = actualized ? 50 : 0;
        int count = planted ? 1 : 0;
        int actuals = actualized ? 1 : 0;
        List<ProductionStudioService.PlantingCostLine> lines = planted ? List.of(
                new ProductionStudioService.PlantingCostLine(4L, "Zinnia", "", "Bed A",
                        SeasonPlanStatus.PLANNED, 100, 0, 1, actuals, minutes,
                        inputs, inputs, total, total / 100, null, actualized ? 100 : 0)) : List.of();
        return new ProductionStudioService.ProductionCostReport(YEAR, 25, "SEASON_SETTINGS", lines,
                planted ? 100 : 0, 0, count, actuals, minutes, inputs, inputs, total, total,
                planted ? total / 100 : null, null, actualized ? 100 : 0,
                0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }

    private static SwingUserHarness build(ProductionStudioService service, SwingDialogHarness dialogs)
            throws Exception {
        AtomicReference<JComponent> root = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ProductionStudioTab tab = new ProductionStudioTab(service, dialogs);
            root.set(tab.getUIComponent());
            tab.initialize();
        });
        return SwingUserHarness.on(root.get()).awaitText("Production Studio refreshed.");
    }
}
