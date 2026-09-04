package com.flowerfarm.service;

import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.IntegrationHandoff;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.repository.ProductionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Production Studio service")
class ProductionStudioServiceTest {

    @Mock ProductionRepository repository;
    @Mock SeasonPlanService seasonPlans;
    @Mock InventoryService inventory;
    @Mock OrderService orders;

    private ProductionStudioService service;

    @BeforeEach
    void setUp() {
        FarmProfile profile = new FarmProfile();
        profile.setName("Blue Heron Flowers");
        service = new ProductionStudioService(
                repository, seasonPlans, inventory, orders, profile);
    }

    @Test
    @DisplayName("farm frost anchors shift complete succession schedules and yield assumptions")
    void draftsFrostAwareSuccessions() {
        GrowingTemplate template = template();
        FarmSeasonSettings settings = settings();
        when(repository.findTemplate(3L)).thenReturn(Optional.of(template));
        when(repository.findSeasonSettings(2026)).thenReturn(Optional.of(settings));

        List<ProductionStudioService.PlantingDraft> drafts =
                service.draftPlantings(3L, 2026, "Bed A", 50, 2);

        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0))
                .extracting(ProductionStudioService.PlantingDraft::sowDate,
                        ProductionStudioService.PlantingDraft::transplantDate,
                        ProductionStudioService.PlantingDraft::harvestStart,
                        ProductionStudioService.PlantingDraft::harvestEnd,
                        ProductionStudioService.PlantingDraft::plantSites,
                        ProductionStudioService.PlantingDraft::viablePlants,
                        ProductionStudioService.PlantingDraft::expectedStems)
                .containsExactly(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 5, 8),
                        LocalDate.of(2026, 6, 18), LocalDate.of(2026, 7, 8),
                        266, 226, 678);
        assertThat(drafts.get(1).sowDate()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(drafts).allSatisfy(draft -> assertThat(draft.fallFrostRisk()).isFalse());
    }

    @Test
    @DisplayName("frost anchor updates lock the existing season row before replacing values")
    void locksSeasonSettingsForUpdate() {
        FarmSeasonSettings existing = settings();
        existing.setId(5L);
        FarmSeasonSettings revised = new FarmSeasonSettings(2026,
                LocalDate.of(2026, 4, 28), LocalDate.of(2026, 10, 20),
                "Revised station", 31.50);
        when(repository.findSeasonSettingsForUpdate(2026)).thenReturn(Optional.of(existing));
        when(repository.saveSeasonSettings(existing)).thenReturn(existing);

        FarmSeasonSettings saved = service.saveSeasonSettings(revised);

        assertThat(saved.getId()).isEqualTo(5L);
        assertThat(saved.getLastSpringFrost()).isEqualTo(LocalDate.of(2026, 4, 28));
        assertThat(saved.getSource()).isEqualTo("Revised station");
        assertThat(saved.getLoadedLaborRatePerHour()).isEqualTo(31.50);
        verify(repository).findSeasonSettingsForUpdate(2026);
    }

    @Test
    @DisplayName("rolls task actuals and exact harvest allocation into auditable production costs")
    void calculatesPlantingAndSeasonProductionCosts() {
        FarmSeasonSettings settings = new FarmSeasonSettings(2026,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 15),
                "Farm station", 25);
        SeasonPlanEntry plan = new SeasonPlanEntry(2026, "Dahlia", "Cafe au Lait",
                "Bed A", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 10),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30),
                40, 100, SeasonPlanStatus.HARVESTING, "");
        plan.setId(7L);
        ProductionTask completed = task(7L, LocalDate.of(2026, 5, 1));
        completed.recordActuals(120, 30, "Compost and netting installed",
                java.time.Instant.parse("2026-05-01T18:00:00Z"));
        ProductionTask open = task(7L, LocalDate.of(2026, 6, 1));
        ProductionTask unlinked = task(null, LocalDate.of(2026, 7, 1));
        unlinked.recordActuals(60, 5, "Irrigation repair",
                java.time.Instant.parse("2026-07-01T18:00:00Z"));
        SeasonPlanService.SeasonSummary summary = new SeasonPlanService.SeasonSummary(
                1, 1, 1, 1, 40, 100,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 30));
        SeasonPlanService.PlanPerformance performance =
                new SeasonPlanService.PlanPerformance(plan, 80, 80, 0, 100,
                        SeasonPlanService.PlanPace.ON_TRACK);
        when(repository.findSeasonSettings(2026)).thenReturn(Optional.of(settings));
        when(seasonPlans.analyze(2026)).thenReturn(new SeasonPlanService.SeasonAnalysis(
                List.of(plan), summary, List.of(performance), List.of(), List.of(), List.of(),
                80, 5, 1));
        when(repository.findTasksForPlan(7L)).thenReturn(List.of(completed, open));
        when(repository.findTasksBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(completed, open, unlinked));

        ProductionStudioService.ProductionCostReport report =
                service.productionCosts(2026, null);

        assertThat(report.loadedLaborRatePerHour()).isEqualTo(25);
        assertThat(report.laborRateSource()).isEqualTo("SEASON_SETTINGS");
        assertThat(report.taskCount()).isEqualTo(3);
        assertThat(report.actualizedTaskCount()).isEqualTo(2);
        assertThat(report.actualMinutes()).isEqualTo(180);
        assertThat(report.laborCost()).isEqualTo(75);
        assertThat(report.inputCost()).isEqualTo(35);
        assertThat(report.totalProductionCost()).isEqualTo(110);
        assertThat(report.allocatedProductionCost()).isEqualTo(80);
        assertThat(report.costPerExpectedStem()).isEqualTo(0.8);
        assertThat(report.costPerHarvestedStem()).isEqualTo(1.0);
        assertThat(report.unlinkedProductionCost()).isEqualTo(30);
        assertThat(report.plantings()).singleElement().satisfies(row -> {
            assertThat(row.seasonPlanId()).isEqualTo(7L);
            assertThat(row.totalProductionCost()).isEqualTo(80);
            assertThat(row.actualizedTaskCount()).isEqualTo(1);
        });
        assertThat(report.warnings()).hasSize(3)
                .anyMatch(message -> message.contains("could not be matched"));
    }

    @Test
    @DisplayName("rejects invalid loaded labor rates before cost aggregation")
    void rejectsInvalidProductionCostRate() {
        assertThatThrownBy(() -> service.productionCosts(2026, -0.01))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Loaded labor rate");
        assertThatThrownBy(() -> new FarmSeasonSettings(2026,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 10, 15),
                "Farm station", 10_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Loaded labor rate");
        verify(seasonPlans, never()).analyze(2026);
    }

    @Test
    @DisplayName("committing a planting creates plan blocks and dependent task chains")
    void commitsPlansAndTasksTogether() {
        GrowingTemplate template = template();
        when(repository.findTemplateForUpdate(3L)).thenReturn(Optional.of(template));
        when(repository.findTemplate(3L)).thenReturn(Optional.of(template));
        when(repository.findSeasonSettings(2026)).thenReturn(Optional.of(settings()));
        AtomicLong ids = new AtomicLong(10);
        when(seasonPlans.create(any())).thenAnswer(invocation -> {
            SeasonPlanEntry plan = invocation.getArgument(0);
            plan.setId(ids.incrementAndGet());
            return plan;
        });
        when(repository.saveTasks(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ProductionStudioService.PlantingDraft> reviewed = service.draftPlantings(
                3L, 2026, "North tunnel", 30, 2);
        List<ProductionStudioService.CommittedPlanting> committed = service.commitPlantings(
                3L, 2026, "North tunnel", 30, 2,
                service.plantingFingerprint(reviewed));

        assertThat(committed).hasSize(2).allSatisfy(block -> {
            assertThat(block.plan().getId()).isPositive();
            assertThat(block.tasks()).hasSize(6)
                    .extracting(ProductionTask::getSeasonPlanId)
                    .containsOnly(block.plan().getId());
        });
        assertThat(committed.get(0).tasks())
                .extracting(ProductionTask::getTaskType)
                .containsExactly(com.flowerfarm.model.ProductionTaskType.SOIL_PREP,
                        com.flowerfarm.model.ProductionTaskType.SOW,
                        com.flowerfarm.model.ProductionTaskType.TRANSPLANT,
                        com.flowerfarm.model.ProductionTaskType.SUPPORT,
                        com.flowerfarm.model.ProductionTaskType.HARVEST_PREP,
                        com.flowerfarm.model.ProductionTaskType.FIRST_HARVEST);
    }

    @Test
    @DisplayName("recipe economics use farm supply first and round only shortages to full packs")
    void calculatesRecipeProfitAndPurchasing() {
        FloralRecipe recipe = new FloralRecipe("Golden hour", "Bouquet", 35,
                12, 25, 15, 10, "");
        recipe.setId(7L);
        recipe.addIngredient(new FloralRecipeIngredient("Sunflower", 3, 10, 12, ""));
        SeasonPlanEntry planned = new SeasonPlanEntry(2026, "Sunflower", "ProCut Orange",
                "Bed B", LocalDate.of(2026, 4, 1), null,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 21),
                20, 14, SeasonPlanStatus.PLANNED, "");
        when(repository.findRecipe(7L)).thenReturn(Optional.of(recipe));
        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Sunflower", "Flowers/Plants", 2.5,
                        "Per Stem", 1, 5, "")));
        when(seasonPlans.listForSeason(2026)).thenReturn(List.of(planned));

        ProductionStudioService.RecipeEstimate estimate = service.estimateRecipe(
                7L, 10, LocalDate.of(2026, 6, 17));

        assertThat(estimate.ingredients()).singleElement().satisfies(ingredient -> {
            assertThat(ingredient.requiredWithWaste()).isEqualTo(33);
            assertThat(ingredient.liveInventory()).isEqualTo(5);
            assertThat(ingredient.plannedThisWeek()).isEqualTo(14);
            assertThat(ingredient.farmAllocated()).isEqualTo(19);
            assertThat(ingredient.remainingShortage()).isEqualTo(14);
            assertThat(ingredient.packsToBuy()).isEqualTo(2);
            assertThat(ingredient.purchasedStems()).isEqualTo(20);
            assertThat(ingredient.packLeftovers()).isEqualTo(6);
            assertThat(ingredient.materialCost()).isEqualTo(43);
        });
        assertThat(estimate.flowerCost()).isEqualTo(43);
        assertThat(estimate.laborCost()).isEqualTo(50);
        assertThat(estimate.overheadCost()).isEqualTo(13.95);
        assertThat(estimate.totalCost()).isEqualTo(106.95);
        assertThat(estimate.revenue()).isEqualTo(350);
        assertThat(estimate.profit()).isEqualTo(243.05);
        assertThat(estimate.marginPercent()).isBetween(69.4, 69.5);
        assertThat(estimate.markupPercent()).isBetween(227.2, 227.3);
    }

    @Test
    @DisplayName("payment handoffs store an operational reference without payment credentials")
    void createsSafePaymentHandoff() {
        Customer customer = new Customer("Wildflower Cafe", "Mina", "", "", "WHOLESALE", "");
        CustomerOrder order = new CustomerOrder(customer, LocalDate.of(2026, 9, 12),
                "CONFIRMED", "", "DELIVERY");
        order.setId(44L);
        order.addLine(new OrderLine("Market bouquet", 10, "bouquet", 30));
        when(orders.findById(44L)).thenReturn(Optional.of(order));
        when(repository.saveHandoff(any())).thenAnswer(invocation -> invocation.getArgument(0));

        IntegrationHandoff handoff = service.createHandoff(
                new ProductionStudioService.HandoffRequest("PAYMENT", "Square", "READY",
                        "invoice-44", 44L, null, null, "USD", "Deposit request"));

        assertThat(handoff.getAmount()).isEqualTo(300);
        assertThat(handoff.getPayloadSummary())
                .contains("payment request", "order=44", "No card, bank, token")
                .doesNotContain("4242", "checkout credentials=");
        verify(repository).saveHandoff(handoff);
    }

    @Test
    @DisplayName("multiple successions require an explicit interval")
    void rejectsAmbiguousSuccessionTemplate() {
        GrowingTemplate template = template();
        template.setSuccessionDays(0);
        when(repository.findTemplate(3L)).thenReturn(Optional.of(template));
        when(repository.findSeasonSettings(2026)).thenReturn(Optional.of(settings()));

        assertThatThrownBy(() -> service.draftPlantings(3L, 2026, "Bed A", 50, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("succession interval");
    }

    @Test
    @DisplayName("completed plans and empty inventory do not inflate sellable availability")
    void excludesFinishedAndEmptySupply() {
        FloralRecipe recipe = new FloralRecipe("Late summer", "Bouquet", 30,
                10, 24, 10, 0, "");
        recipe.setId(8L);
        recipe.addIngredient(new FloralRecipeIngredient("Zinnia", 2, 10, 8, ""));
        SeasonPlanEntry complete = new SeasonPlanEntry(2026, "Zinnia", "Benary's Giant",
                "Bed C", LocalDate.of(2026, 5, 1), null,
                LocalDate.of(2026, 8, 24), LocalDate.of(2026, 9, 6),
                20, 70, SeasonPlanStatus.COMPLETE, "Finished early");
        when(repository.findRecipe(8L)).thenReturn(Optional.of(recipe));
        when(inventory.getAllItems()).thenReturn(List.of(
                new Item("Zinnia", "Flowers/Plants", 1.5,
                        "Per Stem", 5, 0, "")));
        when(seasonPlans.listForSeason(2026)).thenReturn(List.of(complete));

        ProductionStudioService.RecipeEstimate estimate = service.estimateRecipe(
                8L, 5, LocalDate.of(2026, 8, 30));

        assertThat(estimate.ingredients()).singleElement().satisfies(ingredient -> {
            assertThat(ingredient.plannedThisWeek()).isZero();
            assertThat(ingredient.liveInventory()).isZero();
            assertThat(ingredient.packsToBuy()).isEqualTo(1);
        });
        assertThat(service.availability(LocalDate.of(2026, 8, 30))).isEmpty();
    }

    @Test
    @DisplayName("recipe validation prevents duplicate lines from double allocating farm supply")
    void rejectsDuplicateRecipeIngredients() {
        FloralRecipe recipe = new FloralRecipe("Market bunch", "Bouquet", 25,
                8, 24, 10, 5, "");
        recipe.addIngredient(new FloralRecipeIngredient("Sunflower", 2, 10, 8, ""));
        recipe.addIngredient(new FloralRecipeIngredient(" sunflower ", 1, 10, 8, ""));

        assertThatThrownBy(() -> service.createRecipe(recipe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appears more than once", "combine it into one line");
    }

    @Test
    @DisplayName("an exact planting block is rejected before any plan is created")
    void rejectsDuplicatePlantingCommit() {
        GrowingTemplate template = template();
        when(repository.findTemplateForUpdate(3L)).thenReturn(Optional.of(template));
        when(repository.findTemplate(3L)).thenReturn(Optional.of(template));
        when(repository.findSeasonSettings(2026)).thenReturn(Optional.of(settings()));
        ProductionStudioService.PlantingDraft draft = service.draftPlantings(
                3L, 2026, "Bed A", 50, 1).get(0);
        SeasonPlanEntry existing = new SeasonPlanEntry(draft.seasonYear(), draft.cropName(),
                draft.variety(), draft.bedOrField(), draft.sowDate(), draft.transplantDate(),
                draft.harvestStart(), draft.harvestEnd(), draft.bedFeet(),
                draft.expectedStems(), SeasonPlanStatus.PLANNED, "Existing block");
        when(seasonPlans.listForSeason(2026)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.commitPlantings(
                3L, 2026, "Bed A", 50, 1, service.plantingFingerprint(List.of(draft))))
                .isInstanceOf(ProductionConflictException.class)
                .hasMessageContaining("identical planting block", "Bed A");
        verify(seasonPlans, never()).create(any());
    }

    @Test
    @DisplayName("a changed planting preview is rejected before any plan is created")
    void rejectsStalePlantingPreview() {
        GrowingTemplate template = template();
        when(repository.findTemplateForUpdate(3L)).thenReturn(Optional.of(template));
        when(repository.findTemplate(3L)).thenReturn(Optional.of(template));
        when(repository.findSeasonSettings(2026)).thenReturn(Optional.of(settings()));

        assertThatThrownBy(() -> service.commitPlantings(
                3L, 2026, "Bed A", 50, 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Preview", "before committing");
        assertThatThrownBy(() -> service.commitPlantings(
                3L, 2026, "Bed A", 50, 1, "stale-preview-token"))
                .isInstanceOf(ProductionConflictException.class)
                .hasMessageContaining("preview changed", "Preview it again");
        verify(seasonPlans, never()).listForSeason(2026);
        verify(seasonPlans, never()).create(any());
    }

    @Test
    @DisplayName("task regeneration rejects a template that does not own the plan")
    void rejectsCrossTemplateRegeneration() {
        GrowingTemplate otherTemplate = template();
        otherTemplate.setId(4L);
        SeasonPlanEntry plan = new SeasonPlanEntry(2026, "Sunflower", "ProCut Orange",
                "Bed A", LocalDate.of(2026, 3, 20), LocalDate.of(2026, 5, 8),
                LocalDate.of(2026, 6, 18), LocalDate.of(2026, 7, 8),
                50, 678, SeasonPlanStatus.PLANNED, "");
        plan.setId(21L);
        ProductionTask existing = new ProductionTask(21L, 3L, LocalDate.of(2026, 3, 20),
                ProductionTaskType.SOW, ProductionTaskStatus.TODO, "Sunflower", "Bed A",
                1, true, "Sow");
        when(repository.findTemplate(4L)).thenReturn(Optional.of(otherTemplate));
        when(seasonPlans.findForUpdate(21L)).thenReturn(plan);
        when(repository.findTasksForPlan(21L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.regenerateTasks(21L, 4L))
                .isInstanceOf(ProductionConflictException.class)
                .hasMessageContaining("does not own");
        verify(repository, never()).deleteOpenGeneratedTasks(21L);
    }

    @Test
    @DisplayName("templates with generated history cannot be deleted")
    void protectsInUseTemplate() {
        when(repository.findTemplateForUpdate(3L)).thenReturn(Optional.of(template()));
        when(repository.hasTasksForTemplate(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteTemplate(3L))
                .isInstanceOf(ProductionConflictException.class)
                .hasMessageContaining("cannot be deleted", "reproducible");
        verify(repository, never()).deleteTemplate(3L);
    }

    @Test
    @DisplayName("regeneration stops when the original template link is missing")
    void rejectsOrphanedTaskRegeneration() {
        SeasonPlanEntry plan = new SeasonPlanEntry(2026, "Sunflower", "ProCut Orange",
                "Bed A", LocalDate.of(2026, 3, 20), LocalDate.of(2026, 5, 8),
                LocalDate.of(2026, 6, 18), LocalDate.of(2026, 7, 8),
                50, 678, SeasonPlanStatus.PLANNED, "");
        plan.setId(21L);
        ProductionTask orphaned = new ProductionTask(21L, null, LocalDate.of(2026, 3, 20),
                ProductionTaskType.SOW, ProductionTaskStatus.TODO, "Sunflower", "Bed A",
                1, true, "Sow");
        when(repository.findTemplate(3L)).thenReturn(Optional.of(template()));
        when(seasonPlans.findForUpdate(21L)).thenReturn(plan);
        when(repository.findTasksForPlan(21L)).thenReturn(List.of(orphaned));

        assertThatThrownBy(() -> service.regenerateTasks(21L, 3L))
                .isInstanceOf(ProductionConflictException.class)
                .hasMessageContaining("original template link is missing");
        verify(repository, never()).deleteOpenGeneratedTasks(21L);
    }

    @Test
    @DisplayName("regeneration preserves reopened work that contains farm actuals")
    void preservesActualsDuringRegeneration() {
        GrowingTemplate template = template();
        SeasonPlanEntry plan = new SeasonPlanEntry(2026, "Sunflower", "ProCut Orange",
                "Bed A", LocalDate.of(2026, 3, 20), LocalDate.of(2026, 5, 8),
                LocalDate.of(2026, 6, 18), LocalDate.of(2026, 7, 8),
                50, 678, SeasonPlanStatus.PLANNED, "");
        plan.setId(21L);
        ProductionTask reopenedWithActuals = new ProductionTask(
                21L, 3L, LocalDate.of(2026, 3, 20), ProductionTaskType.SOW,
                ProductionTaskStatus.TODO, "Sunflower", "Bed A",
                1, true, "Sow");
        reopenedWithActuals.recordActuals(45, 12,
                "Crusting after rain", java.time.Instant.now());
        reopenedWithActuals.reopenOrSkip(ProductionTaskStatus.TODO);
        when(repository.findTemplate(3L)).thenReturn(Optional.of(template));
        when(seasonPlans.findForUpdate(21L)).thenReturn(plan);
        when(repository.findTasksForPlan(21L))
                .thenReturn(List.of(reopenedWithActuals));
        when(repository.saveTasks(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ProductionTask> regenerated = service.regenerateTasks(21L, 3L);

        assertThat(regenerated).extracting(ProductionTask::getTaskType)
                .doesNotContain(ProductionTaskType.SOW)
                .contains(ProductionTaskType.SOIL_PREP,
                        ProductionTaskType.FIRST_HARVEST);
        assertThat(reopenedWithActuals.hasActuals()).isTrue();
        verify(repository).deleteOpenGeneratedTasks(21L);
    }

    @Test
    @DisplayName("regeneration preserves open generated work that owns field evidence")
    void preservesFieldEvidenceDuringRegeneration() {
        GrowingTemplate template = template();
        SeasonPlanEntry plan = new SeasonPlanEntry(2026, "Sunflower", "ProCut Orange",
                "Bed A", LocalDate.of(2026, 3, 20), LocalDate.of(2026, 5, 8),
                LocalDate.of(2026, 6, 18), LocalDate.of(2026, 7, 8),
                50, 678, SeasonPlanStatus.PLANNED, "");
        plan.setId(21L);
        ProductionTask taskWithEvidence = new ProductionTask(
                21L, 3L, LocalDate.of(2026, 3, 20), ProductionTaskType.SOW,
                ProductionTaskStatus.TODO, "Sunflower", "Bed A",
                1, true, "Sow");
        taskWithEvidence.setId(99L);
        when(repository.findTemplate(3L)).thenReturn(Optional.of(template));
        when(seasonPlans.findForUpdate(21L)).thenReturn(plan);
        when(repository.findTasksForPlan(21L)).thenReturn(List.of(taskWithEvidence));
        when(repository.hasAttachmentsForTask(99L)).thenReturn(true);
        when(repository.saveTasks(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<ProductionTask> regenerated = service.regenerateTasks(21L, 3L);

        assertThat(regenerated).extracting(ProductionTask::getTaskType)
                .doesNotContain(ProductionTaskType.SOW)
                .contains(ProductionTaskType.SOIL_PREP,
                        ProductionTaskType.FIRST_HARVEST);
        verify(repository).hasAttachmentsForTask(99L);
        verify(repository).deleteOpenGeneratedTasks(21L);
    }

    @Test
    @DisplayName("final task states must be reopened before switching terminal outcome")
    void protectsTerminalTaskState() {
        ProductionTask task = new ProductionTask(21L, 3L, LocalDate.now(),
                ProductionTaskType.SOW, ProductionTaskStatus.DONE, "Sunflower", "Bed A",
                1, true, "Sow");
        task.setId(9L);
        when(repository.findTaskForUpdate(9L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.updateTaskStatus(9L, ProductionTaskStatus.SKIPPED))
                .isInstanceOf(ProductionConflictException.class)
                .hasMessageContaining("Reopen");
        verify(repository, never()).saveTask(any());
    }

    @Test
    @DisplayName("completes plan-linked work with structured labor inputs and field evidence")
    void recordsTaskActuals() {
        ProductionTask task = new ProductionTask(21L, 3L, LocalDate.now(),
                ProductionTaskType.SOIL_PREP, ProductionTaskStatus.TODO,
                "Sunflower", "Bed A", 1, true, "Prepare the bed");
        task.setId(9L);
        when(repository.findTaskForUpdate(9L)).thenReturn(Optional.of(task));
        when(repository.saveTask(task)).thenReturn(task);

        ProductionTask saved = service.recordTaskActuals(9L,
                new ProductionStudioService.TaskActuals(
                        75, 18.50, "Added compost; soil held moisture evenly"));

        assertThat(saved.getStatus()).isEqualTo(ProductionTaskStatus.DONE);
        assertThat(saved.getActualMinutes()).isEqualTo(75);
        assertThat(saved.getInputCost()).isEqualTo(18.50);
        assertThat(saved.getFieldObservation())
                .isEqualTo("Added compost; soil held moisture evenly");
        assertThat(saved.getCompletedAt()).isNotNull();
        verify(repository).findTaskForUpdate(9L);
        verify(repository).saveTask(task);
    }

    @Test
    @DisplayName("skipped work must be reopened before actuals can be recorded")
    void rejectsActualsForSkippedTask() {
        ProductionTask task = new ProductionTask(21L, 3L, LocalDate.now(),
                ProductionTaskType.SOW, ProductionTaskStatus.SKIPPED,
                "Sunflower", "Bed A", 1, true, "Sow");
        task.setId(9L);
        when(repository.findTaskForUpdate(9L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.recordTaskActuals(9L,
                new ProductionStudioService.TaskActuals(10, 0, "")))
                .isInstanceOf(ProductionConflictException.class)
                .hasMessageContaining("Reopen skipped work");
        verify(repository, never()).saveTask(any());
    }

    @Test
    @DisplayName("task queries reject ranges wider than five years")
    void rejectsUnboundedTaskRange() {
        assertThatThrownBy(() -> service.listTasks(
                LocalDate.of(2020, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("five years");
    }

    @Test
    @DisplayName("sent handoffs require one new provider reference and valid recipe link")
    void validatesSentHandoffReferences() {
        assertThatThrownBy(() -> service.createHandoff(
                new ProductionStudioService.HandoffRequest("MARKETPLACE", "Florist portal",
                        "PAID", "listing-paid", null, null, null, "USD", "Availability")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid", "marketplace");

        assertThatThrownBy(() -> service.createHandoff(
                new ProductionStudioService.HandoffRequest("MARKETPLACE", "Florist portal",
                        "SENT", "", null, null, null, "USD", "Availability")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("External reference is required");

        when(repository.findHandoffByReference(
                "MARKETPLACE", "Florist portal", "listing-12"))
                .thenReturn(Optional.of(new IntegrationHandoff("MARKETPLACE", "Florist portal",
                        "SENT", "listing-12", null, null, 0, "USD", "Existing")));
        assertThatThrownBy(() -> service.createHandoff(
                new ProductionStudioService.HandoffRequest("MARKETPLACE", "Florist portal",
                        "SENT", "listing-12", null, null, null, "USD", "Availability")))
                .isInstanceOf(ProductionConflictException.class)
                .hasMessageContaining("already recorded");

        when(repository.findHandoffByReference(
                "MARKETPLACE", "Florist portal", "listing-13"))
                .thenReturn(Optional.empty());
        when(repository.findRecipe(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createHandoff(
                new ProductionStudioService.HandoffRequest("MARKETPLACE", "Florist portal",
                        "SENT", "listing-13", null, 999L, null, "USD", "Availability")))
                .isInstanceOf(IndexOutOfBoundsException.class)
                .hasMessageContaining("No floral recipe with id=999");
    }

    private static GrowingTemplate template() {
        GrowingTemplate template = new GrowingTemplate("Sunflower production", "Sunflower",
                "ProCut Orange", 9, 4, -42, 7, 90, 21, 14,
                3, 85, 14, 10, true, "Farm trial");
        template.setId(3L);
        return template;
    }

    private static FarmSeasonSettings settings() {
        return new FarmSeasonSettings(2026, LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 10, 15), "Farm weather station");
    }

    private static ProductionTask task(Long planId, LocalDate dueDate) {
        return new ProductionTask(planId, null, dueDate, ProductionTaskType.CUSTOM,
                ProductionTaskStatus.TODO, "Dahlia", "Bed A", 1, false, "Farm work");
    }
}
