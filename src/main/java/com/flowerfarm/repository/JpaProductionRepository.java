package com.flowerfarm.repository;

import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FieldAttachmentTargetType;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.IntegrationHandoff;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Spring Data adapter for the production-studio persistence boundary. */
@Repository
public class JpaProductionRepository implements ProductionRepository {

    private final FarmSeasonSettingsJpaRepository seasons;
    private final GrowingTemplateJpaRepository templates;
    private final ProductionTaskJpaRepository tasks;
    private final FloralRecipeJpaRepository recipes;
    private final IntegrationHandoffJpaRepository handoffs;
    private final FieldAttachmentJpaRepository attachments;

    public JpaProductionRepository(FarmSeasonSettingsJpaRepository seasons,
                                   GrowingTemplateJpaRepository templates,
                                   ProductionTaskJpaRepository tasks,
                                   FloralRecipeJpaRepository recipes,
                                   IntegrationHandoffJpaRepository handoffs,
                                   FieldAttachmentJpaRepository attachments) {
        this.seasons = seasons;
        this.templates = templates;
        this.tasks = tasks;
        this.recipes = recipes;
        this.handoffs = handoffs;
        this.attachments = attachments;
    }

    @Override
    public Optional<FarmSeasonSettings> findSeasonSettings(int seasonYear) {
        return seasons.findBySeasonYear(seasonYear);
    }

    @Override
    public Optional<FarmSeasonSettings> findSeasonSettingsForUpdate(int seasonYear) {
        return seasons.findBySeasonYearForUpdate(seasonYear);
    }

    @Override
    public FarmSeasonSettings saveSeasonSettings(FarmSeasonSettings settings) {
        return seasons.save(settings);
    }

    @Override
    public List<GrowingTemplate> findAllTemplates() {
        return templates.findAllByOrderByActiveDescCropNameAscNameAsc();
    }

    @Override
    public Optional<GrowingTemplate> findTemplate(Long id) {
        return templates.findById(id);
    }

    @Override
    public Optional<GrowingTemplate> findTemplateForUpdate(Long id) {
        return templates.findByIdForUpdate(id);
    }

    @Override
    public GrowingTemplate saveTemplate(GrowingTemplate template) {
        return templates.save(template);
    }

    @Override
    public void deleteTemplate(Long id) {
        templates.deleteById(id);
    }

    @Override
    public List<ProductionTask> findTasksBetween(LocalDate from, LocalDate to) {
        return tasks.findByDueDateBetweenOrderByDueDateAscSequenceNumberAsc(from, to);
    }

    @Override
    public List<ProductionTask> findOpenTasksDueThrough(LocalDate date, int limit) {
        return tasks.findByStatusAndDueDateLessThanEqual(
                ProductionTaskStatus.TODO, date, PageRequest.of(0, limit,
                        Sort.by("dueDate").ascending()
                                .and(Sort.by("sequenceNumber").ascending())
                                .and(Sort.by("id").ascending())));
    }

    @Override
    public long countOpenTasksDueThrough(LocalDate date) {
        return tasks.countByStatusAndDueDateLessThanEqual(
                ProductionTaskStatus.TODO, date);
    }

    @Override
    public List<ProductionTask> findTasksForPlan(Long seasonPlanId) {
        return tasks.findBySeasonPlanIdOrderByDueDateAscSequenceNumberAsc(seasonPlanId);
    }

    @Override
    public Optional<ProductionTask> findTask(Long id) {
        return tasks.findById(id);
    }

    @Override
    public boolean hasTasksForTemplate(Long growingTemplateId) {
        return tasks.existsByGrowingTemplateId(growingTemplateId);
    }

    @Override
    public Optional<ProductionTask> findTaskForUpdate(Long id) {
        return tasks.findByIdForUpdate(id);
    }

    @Override
    public ProductionTask saveTask(ProductionTask task) {
        return tasks.save(task);
    }

    @Override
    public List<ProductionTask> saveTasks(List<ProductionTask> productionTasks) {
        return tasks.saveAll(productionTasks);
    }

    @Override
    public boolean hasAttachmentsForTask(Long taskId) {
        return attachments.existsByTargetTypeAndTargetRecordId(
                FieldAttachmentTargetType.PRODUCTION_TASK, taskId);
    }

    @Override
    public void deleteOpenGeneratedTasks(Long seasonPlanId) {
        tasks.deleteGeneratedForPlanWithStatus(
                seasonPlanId, ProductionTaskStatus.TODO,
                FieldAttachmentTargetType.PRODUCTION_TASK);
    }

    @Override
    public List<FloralRecipe> findAllRecipes() {
        return recipes.findAllByOrderByDesignTypeAscNameAsc();
    }

    @Override
    public Optional<FloralRecipe> findRecipe(Long id) {
        return recipes.findById(id);
    }

    @Override
    public Optional<FloralRecipe> findRecipeForUpdate(Long id) {
        return recipes.findByIdForUpdate(id);
    }

    @Override
    public FloralRecipe saveRecipe(FloralRecipe recipe) {
        return recipes.save(recipe);
    }

    @Override
    public void deleteRecipe(Long id) {
        recipes.deleteById(id);
    }

    @Override
    public List<IntegrationHandoff> findAllHandoffs() {
        return handoffs.findAllByOrderByCreatedAtDescIdDesc();
    }

    @Override
    public Optional<IntegrationHandoff> findHandoffByReference(
            String handoffType, String provider, String externalReference) {
        return handoffs.findFirstByHandoffTypeAndProviderIgnoreCaseAndExternalReferenceIgnoreCase(
                handoffType, provider, externalReference);
    }

    @Override
    public IntegrationHandoff saveHandoff(IntegrationHandoff handoff) {
        return handoffs.save(handoff);
    }
}
