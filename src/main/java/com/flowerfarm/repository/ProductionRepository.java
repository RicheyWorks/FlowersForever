package com.flowerfarm.repository;

import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.IntegrationHandoff;
import com.flowerfarm.model.ProductionTask;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for end-to-end crop and design production. */
public interface ProductionRepository {

    Optional<FarmSeasonSettings> findSeasonSettings(int seasonYear);

    Optional<FarmSeasonSettings> findSeasonSettingsForUpdate(int seasonYear);

    FarmSeasonSettings saveSeasonSettings(FarmSeasonSettings settings);

    List<GrowingTemplate> findAllTemplates();

    Optional<GrowingTemplate> findTemplate(Long id);

    Optional<GrowingTemplate> findTemplateForUpdate(Long id);

    GrowingTemplate saveTemplate(GrowingTemplate template);

    void deleteTemplate(Long id);

    List<ProductionTask> findTasksBetween(LocalDate from, LocalDate to);

    List<ProductionTask> findOpenTasksDueThrough(LocalDate date, int limit);

    long countOpenTasksDueThrough(LocalDate date);

    List<ProductionTask> findTasksForPlan(Long seasonPlanId);

    Optional<ProductionTask> findTask(Long id);

    boolean hasTasksForTemplate(Long growingTemplateId);

    Optional<ProductionTask> findTaskForUpdate(Long id);

    ProductionTask saveTask(ProductionTask task);

    List<ProductionTask> saveTasks(List<ProductionTask> tasks);

    boolean hasAttachmentsForTask(Long taskId);

    void deleteOpenGeneratedTasks(Long seasonPlanId);

    List<FloralRecipe> findAllRecipes();

    Optional<FloralRecipe> findRecipe(Long id);

    Optional<FloralRecipe> findRecipeForUpdate(Long id);

    FloralRecipe saveRecipe(FloralRecipe recipe);

    void deleteRecipe(Long id);

    List<IntegrationHandoff> findAllHandoffs();

    Optional<IntegrationHandoff> findHandoffByReference(
            String handoffType, String provider, String externalReference);

    IntegrationHandoff saveHandoff(IntegrationHandoff handoff);
}
