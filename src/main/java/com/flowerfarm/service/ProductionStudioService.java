package com.flowerfarm.service;

import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.IntegrationHandoff;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.repository.ProductionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Connects farm crop assumptions to frost-aware plans, dependent work, design recipes,
 * profit math, and deliberately narrow external-system handoffs.
 */
@Service
public class ProductionStudioService {

    private static final int MAX_SUCCESSIONS = 52;
    private static final int MAX_DESIGNS = 1_000_000;
    private static final long MAX_TASK_RANGE_DAYS = 1_830;
    private static final Set<String> HANDOFF_TYPES =
            Set.of("CONTRACT", "PAYMENT", "MARKETPLACE");
    private static final Map<String, Set<String>> HANDOFF_STATUSES = Map.of(
            "CONTRACT", Set.of("DRAFT", "READY", "SENT", "ACCEPTED", "FAILED", "CANCELLED"),
            "PAYMENT", Set.of("DRAFT", "READY", "SENT", "PAID", "FAILED", "CANCELLED"),
            "MARKETPLACE", Set.of("DRAFT", "READY", "SENT", "FAILED", "CANCELLED"));
    private static final Set<String> REFERENCE_REQUIRED_STATUSES =
            Set.of("SENT", "ACCEPTED", "PAID");

    private final ProductionRepository repository;
    private final SeasonPlanService seasonPlans;
    private final InventoryService inventory;
    private final OrderService orders;
    private final FarmProfile farmProfile;

    public ProductionStudioService(ProductionRepository repository,
                                   SeasonPlanService seasonPlans,
                                   InventoryService inventory,
                                   OrderService orders,
                                   FarmProfile farmProfile) {
        this.repository = repository;
        this.seasonPlans = seasonPlans;
        this.inventory = inventory;
        this.orders = orders;
        this.farmProfile = farmProfile;
    }

    @Transactional(readOnly = true)
    public Optional<FarmSeasonSettings> findSeasonSettings(int seasonYear) {
        requireYear(seasonYear);
        return repository.findSeasonSettings(seasonYear);
    }

    @Transactional
    public FarmSeasonSettings saveSeasonSettings(FarmSeasonSettings incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("Farm season settings are required.");
        }
        incoming.validateWindow();
        FarmSeasonSettings existing = repository
                .findSeasonSettingsForUpdate(incoming.getSeasonYear())
                .orElse(null);
        if (existing == null) {
            incoming.setId(null);
            return repository.saveSeasonSettings(incoming);
        }
        existing.copyBusinessFieldsFrom(incoming);
        return repository.saveSeasonSettings(existing);
    }

    @Transactional(readOnly = true)
    public List<GrowingTemplate> listTemplates() {
        return List.copyOf(repository.findAllTemplates());
    }

    @Transactional(readOnly = true)
    public Optional<GrowingTemplate> findTemplate(Long id) {
        requireId(id, "Growing template id");
        return repository.findTemplate(id);
    }

    @Transactional
    public GrowingTemplate createTemplate(GrowingTemplate template) {
        requireTemplate(template);
        template.setId(null);
        return repository.saveTemplate(template);
    }

    @Transactional
    public GrowingTemplate updateTemplate(Long id, GrowingTemplate incoming) {
        requireId(id, "Growing template id");
        requireTemplate(incoming);
        GrowingTemplate existing = repository.findTemplateForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No growing template with id=" + id));
        existing.copyBusinessFieldsFrom(incoming);
        return repository.saveTemplate(existing);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        requireId(id, "Growing template id");
        repository.findTemplateForUpdate(id).orElseThrow(() ->
                new IndexOutOfBoundsException("No growing template with id=" + id));
        if (repository.hasTasksForTemplate(id)) {
            throw new ProductionConflictException(
                    "Templates with generated work cannot be deleted; keep the template "
                            + "so completed plans remain reproducible.");
        }
        repository.deleteTemplate(id);
    }

    /** Produces reviewable plan and task drafts without changing the database. */
    @Transactional(readOnly = true)
    public List<PlantingDraft> draftPlantings(Long templateId, int seasonYear,
                                              String bedOrField, double bedFeet,
                                              int successions) {
        requireYear(seasonYear);
        GrowingTemplate template = template(templateId);
        FarmSeasonSettings settings = repository.findSeasonSettings(seasonYear)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Save frost dates for " + seasonYear + " before building a schedule."));
        requireBed(bedOrField, bedFeet);
        requireSuccessions(template, successions);
        List<PlantingDraft> drafts = new ArrayList<>();
        for (int index = 0; index < successions; index++) {
            int successionShift = template.getSuccessionDays() * index;
            drafts.add(draft(template, settings, bedOrField.trim(), bedFeet,
                    index + 1, successionShift));
        }
        return List.copyOf(drafts);
    }

    /** Commits only when the fingerprint still matches the reviewed draft. */
    @Transactional
    public List<CommittedPlanting> commitPlantings(Long templateId, int seasonYear,
                                                   String bedOrField, double bedFeet,
                                                   int successions,
                                                   String expectedFingerprint) {
        GrowingTemplate template = templateForUpdate(templateId);
        List<PlantingDraft> drafts = draftPlantings(
                templateId, seasonYear, bedOrField, bedFeet, successions);
        String currentFingerprint = plantingFingerprint(drafts);
        String reviewedFingerprint = normalizeFingerprint(expectedFingerprint);
        if (reviewedFingerprint == null) {
            throw new IllegalArgumentException(
                    "Preview the planting schedule before committing it.");
        }
        if (!currentFingerprint.equals(reviewedFingerprint)) {
            throw new ProductionConflictException(
                    "The planting preview changed before commit. Preview it again and review "
                            + "the updated dates, yield, and frost warnings.");
        }
        rejectDuplicatePlantingBlocks(drafts, seasonPlans.listForSeason(seasonYear));
        List<CommittedPlanting> committed = new ArrayList<>();
        for (PlantingDraft draft : drafts) {
            SeasonPlanEntry savedPlan = seasonPlans.create(toPlanEntry(draft));
            List<ProductionTask> savedTasks = repository.saveTasks(
                    tasksFor(savedPlan, template));
            committed.add(new CommittedPlanting(savedPlan, savedTasks));
        }
        return List.copyOf(committed);
    }

    /** Stable token for detecting drift between one reviewed preview and commit. */
    public String plantingFingerprint(List<PlantingDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            throw new IllegalArgumentException("At least one planting draft is required.");
        }
        String canonical = drafts.stream()
                .map(ProductionStudioService::canonicalDraft)
                .collect(Collectors.joining("\n"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    /** Replaces only open generated work, preserving actuals, evidence, and custom tasks. */
    @Transactional
    public List<ProductionTask> regenerateTasks(Long seasonPlanId, Long templateId) {
        requireId(seasonPlanId, "Season plan id");
        GrowingTemplate template = template(templateId);
        SeasonPlanEntry plan = seasonPlans.findForUpdate(seasonPlanId);
        List<ProductionTask> existingTasks = repository.findTasksForPlan(seasonPlanId);
        boolean missingTemplateLink = existingTasks.stream()
                .anyMatch(task -> task.isGenerated() && task.getGrowingTemplateId() == null);
        if (missingTemplateLink) {
            throw new ProductionConflictException(
                    "This plan's original template link is missing; its work cannot be "
                            + "safely regenerated.");
        }
        Set<Long> linkedTemplates = existingTasks.stream()
                .filter(ProductionTask::isGenerated)
                .map(ProductionTask::getGrowingTemplateId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (!linkedTemplates.isEmpty() && !linkedTemplates.equals(Set.of(templateId))) {
            throw new ProductionConflictException(
                    "The selected template does not own this plan's generated work.");
        }
        Set<ProductionTaskType> protectedTypes = existingTasks.stream()
                .filter(task -> task.isGenerated()
                        && (task.getStatus() != ProductionTaskStatus.TODO
                        || task.hasActuals()
                        || (task.getId() != null
                        && repository.hasAttachmentsForTask(task.getId()))))
                .map(ProductionTask::getTaskType)
                .collect(Collectors.toSet());
        repository.deleteOpenGeneratedTasks(seasonPlanId);
        List<ProductionTask> regenerated = tasksFor(plan, template).stream()
                .filter(task -> !protectedTypes.contains(task.getTaskType()))
                .toList();
        return List.copyOf(repository.saveTasks(regenerated));
    }

    @Transactional(readOnly = true)
    public List<ProductionTask> listTasks(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Task range must have a valid from and to date.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_TASK_RANGE_DAYS) {
            throw new IllegalArgumentException("Task range cannot exceed five years.");
        }
        return List.copyOf(repository.findTasksBetween(from, to));
    }

    @Transactional(readOnly = true)
    public List<ProductionTask> listOpenTasksDueThrough(LocalDate date, int limit) {
        if (date == null) {
            throw new IllegalArgumentException("Worker task date is required.");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Worker task limit must be between 1 and 100.");
        }
        return List.copyOf(repository.findOpenTasksDueThrough(date, limit));
    }

    @Transactional(readOnly = true)
    public long countOpenTasksDueThrough(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Worker task date is required.");
        }
        return repository.countOpenTasksDueThrough(date);
    }

    @Transactional(readOnly = true)
    public Optional<ProductionTask> findTask(Long id) {
        requireId(id, "Production task id");
        return repository.findTask(id);
    }

    @Transactional
    public ProductionTask updateTaskStatus(Long id, ProductionTaskStatus status) {
        requireId(id, "Production task id");
        if (status == null) {
            throw new IllegalArgumentException("Production task status is required.");
        }
        ProductionTask task = repository.findTaskForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No production task with id=" + id));
        if (task.getStatus() != ProductionTaskStatus.TODO
                && status != ProductionTaskStatus.TODO
                && task.getStatus() != status) {
            throw new ProductionConflictException(
                    "Reopen completed or skipped work before changing its final status.");
        }
        if (status == ProductionTaskStatus.DONE) {
            task.markDone(java.time.Instant.now());
        } else {
            task.reopenOrSkip(status);
        }
        return repository.saveTask(task);
    }

    /** Completes one task while retaining the farm's structured actual work. */
    @Transactional
    public ProductionTask recordTaskActuals(Long id, TaskActuals actuals) {
        requireId(id, "Production task id");
        if (actuals == null) {
            throw new IllegalArgumentException("Production task actuals are required.");
        }
        ProductionTask task = repository.findTaskForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No production task with id=" + id));
        if (task.getStatus() == ProductionTaskStatus.SKIPPED) {
            throw new ProductionConflictException(
                    "Reopen skipped work before recording actual labor or inputs.");
        }
        task.recordActuals(actuals.actualMinutes(), actuals.inputCost(),
                actuals.fieldObservation(), java.time.Instant.now());
        return repository.saveTask(task);
    }

    /**
     * Joins planting-linked task actuals with the season planner's one-to-one harvest
     * allocation. Revenue is intentionally excluded because order lines do not identify a
     * planting block; the result is a production-cost report, not inferred profitability.
     */
    @Transactional(readOnly = true)
    public ProductionCostReport productionCosts(int seasonYear, Double laborRateOverride) {
        requireYear(seasonYear);
        if (laborRateOverride != null) {
            requireLaborRate(laborRateOverride);
        }
        Optional<FarmSeasonSettings> settings = repository.findSeasonSettings(seasonYear);
        double laborRate = laborRateOverride == null
                ? settings.map(FarmSeasonSettings::getLoadedLaborRatePerHour).orElse(0d)
                : laborRateOverride;
        String laborRateSource = laborRateOverride != null
                ? "REQUEST_OVERRIDE" : settings.isPresent() ? "SEASON_SETTINGS" : "UNCONFIGURED";

        SeasonPlanService.SeasonAnalysis analysis = seasonPlans.analyze(seasonYear);
        List<PlantingCostLine> plantings = new ArrayList<>();
        TaskCostTotals allocated = new TaskCostTotals();
        for (SeasonPlanService.PlanPerformance performance : analysis.performance()) {
            SeasonPlanEntry plan = performance.entry();
            List<ProductionTask> tasks = plan.getId() == null
                    ? List.of() : repository.findTasksForPlan(plan.getId());
            TaskCostTotals taskCosts = TaskCostTotals.from(tasks);
            allocated.add(taskCosts);
            double laborCost = taskCosts.laborCost(laborRate);
            double totalCost = laborCost + taskCosts.inputCost;
            plantings.add(new PlantingCostLine(
                    plan.getId(), plan.getCropName(), plan.getVariety(), plan.getBedOrField(),
                    plan.getStatus(), plan.getExpectedStems(), round(performance.actualStems()),
                    taskCosts.taskCount, taskCosts.actualizedTaskCount, taskCosts.actualMinutes,
                    round(laborCost), round(taskCosts.inputCost), round(totalCost),
                    unitCost(totalCost, plan.getExpectedStems()),
                    unitCost(totalCost, performance.actualStems()),
                    coverage(taskCosts.actualizedTaskCount, taskCosts.taskCount)));
        }

        TaskCostTotals unlinked = TaskCostTotals.from(repository.findTasksBetween(
                        LocalDate.of(seasonYear, 1, 1), LocalDate.of(seasonYear, 12, 31)).stream()
                .filter(task -> task.getSeasonPlanId() == null)
                .toList());
        TaskCostTotals all = new TaskCostTotals();
        all.add(allocated);
        all.add(unlinked);
        double allocatedLaborCost = allocated.laborCost(laborRate);
        double unlinkedLaborCost = unlinked.laborCost(laborRate);
        double totalLaborCost = all.laborCost(laborRate);
        double allocatedCost = allocatedLaborCost + allocated.inputCost;
        double totalCost = totalLaborCost + all.inputCost;

        List<String> warnings = new ArrayList<>();
        if (laborRate == 0) {
            warnings.add("Loaded labor rate is 0; labor minutes are shown but add no labor cost.");
        }
        if (all.actualizedTaskCount < all.taskCount) {
            warnings.add((all.taskCount - all.actualizedTaskCount)
                    + " task(s) have no structured actuals yet.");
        }
        if (unlinked.taskCount > 0) {
            warnings.add(unlinked.taskCount
                    + " calendar-year task(s) are not linked to a planting block.");
        }
        if (analysis.unmatchedHarvestCount() > 0) {
            warnings.add(analysis.unmatchedHarvestCount() + " harvest(s), totaling "
                    + round(analysis.unmatchedStems())
                    + " stems, could not be matched to one planting block.");
        }
        return new ProductionCostReport(
                seasonYear, round(laborRate), laborRateSource, List.copyOf(plantings),
                analysis.summary().expectedStems(), round(analysis.actualStems()),
                all.taskCount, all.actualizedTaskCount, all.actualMinutes,
                round(totalLaborCost), round(all.inputCost), round(totalCost),
                round(allocatedCost), unitCost(allocatedCost, analysis.summary().expectedStems()),
                unitCost(allocatedCost, analysis.actualStems()),
                coverage(all.actualizedTaskCount, all.taskCount),
                unlinked.taskCount, unlinked.actualizedTaskCount, unlinked.actualMinutes,
                round(unlinkedLaborCost), round(unlinked.inputCost),
                round(unlinkedLaborCost + unlinked.inputCost),
                round(analysis.unmatchedStems()), analysis.unmatchedHarvestCount(),
                List.copyOf(warnings));
    }

    @Transactional(readOnly = true)
    public List<FloralRecipe> listRecipes() {
        return List.copyOf(repository.findAllRecipes());
    }

    @Transactional(readOnly = true)
    public Optional<FloralRecipe> findRecipe(Long id) {
        requireId(id, "Recipe id");
        return repository.findRecipe(id);
    }

    @Transactional(readOnly = true)
    public FloralRecipe getRecipe(Long id) {
        return findRecipe(id).orElseThrow(() ->
                new IndexOutOfBoundsException("No floral recipe with id=" + id));
    }

    @Transactional
    public FloralRecipe createRecipe(FloralRecipe recipe) {
        requireRecipe(recipe);
        recipe.setId(null);
        return repository.saveRecipe(recipe);
    }

    @Transactional
    public FloralRecipe updateRecipe(Long id, FloralRecipe incoming) {
        requireId(id, "Recipe id");
        requireRecipe(incoming);
        FloralRecipe existing = repository.findRecipeForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No floral recipe with id=" + id));
        existing.copyBusinessFieldsFrom(incoming);
        return repository.saveRecipe(existing);
    }

    @Transactional
    public void deleteRecipe(Long id) {
        requireId(id, "Recipe id");
        repository.findRecipeForUpdate(id).orElseThrow(() ->
                new IndexOutOfBoundsException("No floral recipe with id=" + id));
        repository.deleteRecipe(id);
    }

    /** Calculates pack rounding, waste, live/planned supply, labor, markup, and margin. */
    @Transactional(readOnly = true)
    public RecipeEstimate estimateRecipe(Long recipeId, int designCount, LocalDate eventDate) {
        if (designCount < 1 || designCount > MAX_DESIGNS) {
            throw new IllegalArgumentException(
                    "Design count must be between 1 and " + MAX_DESIGNS + ".");
        }
        if (eventDate == null) {
            throw new IllegalArgumentException("Event date is required.");
        }
        requireYear(eventDate.getYear());
        FloralRecipe recipe = getRecipe(recipeId);
        Map<String, Item> inventoryByName = inventoryByName();
        List<SeasonPlanEntry> plans = seasonPlans.listForSeason(eventDate.getYear());
        List<IngredientEstimate> ingredients = new ArrayList<>();
        double flowerCost = 0;
        for (FloralRecipeIngredient ingredient : recipe.getIngredients()) {
            IngredientEstimate estimate = ingredientEstimate(
                    recipe, ingredient, designCount, eventDate, inventoryByName, plans);
            ingredients.add(estimate);
            flowerCost += estimate.materialCost();
        }
        double laborCost = recipe.getLaborMinutes() / 60d
                * recipe.getLaborRatePerHour() * designCount;
        double overheadCost = (flowerCost + laborCost) * recipe.getOverheadPercent() / 100d;
        double totalCost = flowerCost + laborCost + overheadCost;
        double revenue = recipe.getSalePrice() * designCount;
        double profit = revenue - totalCost;
        double marginPercent = revenue == 0 ? 0 : profit / revenue * 100d;
        double markupPercent = totalCost == 0 ? 0 : profit / totalCost * 100d;
        return new RecipeEstimate(recipe.getId(), recipe.getName(), designCount, eventDate,
                ingredients, flowerCost, laborCost, overheadCost, totalCost,
                revenue, profit, marginPercent, markupPercent);
    }

    /** Planned weekly crop supply plus live inventory for sales and marketplace decisions. */
    @Transactional(readOnly = true)
    public List<AvailabilityLine> availability(LocalDate weekOf) {
        if (weekOf == null) {
            throw new IllegalArgumentException("Availability week is required.");
        }
        requireYear(weekOf.getYear());
        LocalDate weekStart = weekOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        Map<String, MutableAvailability> rows = new LinkedHashMap<>();
        for (SeasonPlanEntry plan : seasonPlans.listForSeason(weekOf.getYear())) {
            if (plan.getStatus() == SeasonPlanStatus.COMPLETE) {
                continue;
            }
            double planned = plannedDuring(plan, weekStart, weekEnd);
            if (planned <= 0) {
                continue;
            }
            String key = normalize(plan.getCropName());
            MutableAvailability row = rows.computeIfAbsent(key,
                    ignored -> new MutableAvailability(plan.getCropName()));
            row.planned += planned;
            row.windows.add(plan.getHarvestStart() + "–" + plan.getHarvestEnd());
        }
        for (Item item : inventory.getAllItems()) {
            if (item.getQuantity() <= 0) {
                continue;
            }
            String key = normalize(item.getName());
            MutableAvailability row = rows.computeIfAbsent(key,
                    ignored -> new MutableAvailability(item.getName()));
            row.live += item.getQuantity();
        }
        return rows.values().stream()
                .map(row -> new AvailabilityLine(row.cropName, weekStart, weekEnd,
                        round(row.planned), row.live, round(row.planned + row.live),
                        String.join(", ", row.windows)))
                .sorted(Comparator.comparing(AvailabilityLine::cropName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Records a narrow outbound reference. The summary intentionally excludes
     * card data, signatures, tokens, and full legal-contract content.
     */
    @Transactional
    public IntegrationHandoff createHandoff(HandoffRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Integration handoff is required.");
        }
        String type = normalizedType(request.handoffType());
        String status = normalizedType(request.status());
        if (!HANDOFF_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported handoff type '" + type + "'.");
        }
        if (!HANDOFF_STATUSES.get(type).contains(status)) {
            throw new IllegalArgumentException("Status '" + status
                    + "' is not valid for a " + type.toLowerCase(Locale.ROOT) + " handoff.");
        }
        String provider = clean(request.provider());
        if (provider.isBlank() || provider.length() > 80) {
            throw new IllegalArgumentException("Provider is required and limited to 80 characters.");
        }
        String reference = clean(request.externalReference());
        if (reference.length() > 255) {
            throw new IllegalArgumentException(
                    "External reference cannot exceed 255 characters.");
        }
        if (REFERENCE_REQUIRED_STATUSES.contains(status) && reference.isBlank()) {
            throw new IllegalArgumentException(
                    "External reference is required after a handoff is sent or completed.");
        }
        if (!reference.isBlank() && repository.findHandoffByReference(
                type, provider, reference).isPresent()) {
            throw new ProductionConflictException(
                    "That provider reference is already recorded for this handoff type.");
        }
        CustomerOrder order = request.relatedOrderId() == null ? null
                : orders.findById(request.relatedOrderId()).orElseThrow(() ->
                new IndexOutOfBoundsException("No order with id=" + request.relatedOrderId()));
        if (request.relatedRecipeId() != null) {
            getRecipe(request.relatedRecipeId());
        }
        double amount = request.amount() == null
                ? order == null ? 0 : order.lineTotal() : request.amount();
        if ("PAYMENT".equals(type) && amount <= 0) {
            throw new IllegalArgumentException("Payment handoff amount must be greater than zero.");
        }
        String summary = switch (type) {
            case "CONTRACT" -> contractBrief(order, request.summary());
            case "PAYMENT" -> paymentBrief(order, amount, request.summary());
            case "MARKETPLACE" -> marketplaceBrief(request.summary());
            default -> throw new IllegalArgumentException("Unsupported handoff type '" + type + "'.");
        };
        IntegrationHandoff handoff = new IntegrationHandoff(type, provider,
                status, reference, request.relatedOrderId(),
                request.relatedRecipeId(), amount,
                request.currency() == null ? "USD" : request.currency(), summary);
        return repository.saveHandoff(handoff);
    }

    @Transactional(readOnly = true)
    public List<IntegrationHandoff> listHandoffs() {
        return List.copyOf(repository.findAllHandoffs());
    }

    private PlantingDraft draft(GrowingTemplate template, FarmSeasonSettings settings,
                                String bed, double bedFeet, int successionNumber,
                                int successionShift) {
        LocalDate frost = settings.getLastSpringFrost();
        LocalDate sow = frost.plusDays(template.getSowDaysFromFrost() + successionShift);
        LocalDate transplant = template.getTransplantDaysFromFrost() == null ? null
                : frost.plusDays(template.getTransplantDaysFromFrost() + successionShift);
        LocalDate established = transplant == null ? sow : transplant;
        LocalDate harvestStart = sow.plusDays(template.getDaysToHarvest());
        if (harvestStart.isBefore(established)) {
            harvestStart = established;
        }
        LocalDate harvestEnd = harvestStart.plusDays(template.getHarvestWindowDays() - 1L);
        int plantSites = (int) Math.floor(
                bedFeet * 12d / template.getSpacingInches() * template.getRowsPerBed());
        int viablePlants = (int) Math.floor(plantSites * template.getGerminationPercent() / 100d);
        int expectedStems = (int) Math.min(100_000_000,
                Math.round(viablePlants * template.getStemsPerPlant()));
        boolean frostRisk = !harvestEnd.isBefore(settings.getFirstFallFrost());
        return new PlantingDraft(settings.getSeasonYear(), template.getId(),
                template.getName(), template.getCropName(), template.getVariety(), bed,
                bedFeet, successionNumber, sow, transplant, harvestStart, harvestEnd,
                plantSites, viablePlants, expectedStems, frostRisk,
                frostRisk ? "Harvest window reaches the farm's first-fall-frost anchor."
                        : "Harvest window falls inside the farm's frost anchors.");
    }

    private static SeasonPlanEntry toPlanEntry(PlantingDraft draft) {
        return new SeasonPlanEntry(draft.seasonYear(), draft.cropName(), draft.variety(),
                draft.bedOrField(), draft.sowDate(), draft.transplantDate(),
                draft.harvestStart(), draft.harvestEnd(), draft.bedFeet(),
                draft.expectedStems(), SeasonPlanStatus.PLANNED,
                "Generated from farm template '" + draft.templateName() + "', succession "
                        + draft.successionNumber() + ". " + draft.frostMessage());
    }

    private static List<ProductionTask> tasksFor(SeasonPlanEntry plan,
                                                  GrowingTemplate template) {
        LocalDate planting = plan.getTransplantDate() == null
                ? plan.getSowDate() : plan.getTransplantDate();
        List<ProductionTask> tasks = new ArrayList<>();
        addTask(tasks, plan, template, plan.getSowDate().minusDays(template.getSoilPrepLeadDays()),
                ProductionTaskType.SOIL_PREP, "Prepare bed and verify amendments.");
        addTask(tasks, plan, template, plan.getSowDate(), ProductionTaskType.SOW,
                "Sow using the template's spacing and germination assumptions.");
        if (plan.getTransplantDate() != null) {
            addTask(tasks, plan, template, plan.getTransplantDate(),
                    ProductionTaskType.TRANSPLANT, "Transplant and water in.");
        }
        addTask(tasks, plan, template,
                planting.plusDays(template.getSupportDaysAfterPlanting()),
                ProductionTaskType.SUPPORT, "Install or check crop support.");
        addTask(tasks, plan, template, plan.getHarvestStart().minusDays(2),
                ProductionTaskType.HARVEST_PREP, "Prepare harvest supplies and sales allocation.");
        addTask(tasks, plan, template, plan.getHarvestStart(),
                ProductionTaskType.FIRST_HARVEST, "Begin the planned harvest window.");
        return tasks;
    }

    private static void addTask(List<ProductionTask> tasks, SeasonPlanEntry plan,
                                GrowingTemplate template, LocalDate dueDate,
                                ProductionTaskType type, String notes) {
        tasks.add(new ProductionTask(plan.getId(), template.getId(), dueDate, type,
                ProductionTaskStatus.TODO, plan.getCropName(), plan.getBedOrField(),
                tasks.size() + 1, true, notes));
    }

    private static void rejectDuplicatePlantingBlocks(
            List<PlantingDraft> drafts, List<SeasonPlanEntry> existingPlans) {
        for (PlantingDraft draft : drafts) {
            boolean duplicate = existingPlans.stream().anyMatch(plan ->
                    samePlantingBlock(draft, plan));
            if (duplicate) {
                throw new ProductionConflictException(
                        "An identical planting block already exists for "
                                + draft.cropName() + " in " + draft.bedOrField()
                                + " on " + draft.sowDate() + ".");
            }
        }
    }

    private static boolean samePlantingBlock(PlantingDraft draft, SeasonPlanEntry plan) {
        return draft.seasonYear() == plan.getSeasonYear()
                && normalize(draft.cropName()).equals(normalize(plan.getCropName()))
                && normalize(draft.variety()).equals(normalize(plan.getVariety()))
                && normalize(draft.bedOrField()).equals(normalize(plan.getBedOrField()))
                && draft.sowDate().equals(plan.getSowDate())
                && java.util.Objects.equals(draft.transplantDate(), plan.getTransplantDate())
                && draft.harvestStart().equals(plan.getHarvestStart())
                && draft.harvestEnd().equals(plan.getHarvestEnd())
                && Double.compare(draft.bedFeet(), plan.getBedFeet()) == 0
                && draft.expectedStems() == plan.getExpectedStems();
    }

    private static String canonicalDraft(PlantingDraft draft) {
        return String.join("|",
                Integer.toString(draft.seasonYear()),
                String.valueOf(draft.templateId()),
                encoded(draft.templateName()),
                encoded(draft.cropName()),
                encoded(draft.variety()),
                encoded(draft.bedOrField()),
                Double.toHexString(draft.bedFeet()),
                Integer.toString(draft.successionNumber()),
                String.valueOf(draft.sowDate()),
                String.valueOf(draft.transplantDate()),
                String.valueOf(draft.harvestStart()),
                String.valueOf(draft.harvestEnd()),
                Integer.toString(draft.plantSites()),
                Integer.toString(draft.viablePlants()),
                Integer.toString(draft.expectedStems()),
                Boolean.toString(draft.fallFrostRisk()),
                encoded(draft.frostMessage()));
    }

    private static String encoded(String value) {
        String safe = value == null ? "" : value;
        return safe.length() + ":" + safe;
    }

    private static String normalizeFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        String normalized = fingerprint.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"")
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private IngredientEstimate ingredientEstimate(
            FloralRecipe recipe, FloralRecipeIngredient ingredient, int count,
            LocalDate eventDate, Map<String, Item> inventoryByName,
            List<SeasonPlanEntry> plans) {
        double baseStems = ingredient.getStemsPerDesign() * count;
        int required = (int) Math.ceil(baseStems * (1 + recipe.getWastePercent() / 100d));
        Item liveItem = inventoryByName.get(normalize(ingredient.getItemName()));
        int live = liveItem == null ? 0 : liveItem.getQuantity();
        int planned = (int) Math.floor(plans.stream()
                .filter(plan -> plan.getStatus() != SeasonPlanStatus.COMPLETE)
                .filter(plan -> cropMatches(plan, ingredient.getItemName()))
                .mapToDouble(plan -> plannedDuring(plan,
                        eventDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                        eventDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))))
                .sum());
        int farmAllocated = Math.min(required, live + planned);
        int shortage = Math.max(0, required - farmAllocated);
        int packs = (int) Math.ceil(shortage / (double) ingredient.getPackSize());
        int purchased = packs * ingredient.getPackSize();
        double unitCost = liveItem == null ? 0 : liveItem.getCost();
        double farmCost = farmAllocated * unitCost;
        double wholesaleCost = ingredient.getPackCost() > 0
                ? packs * ingredient.getPackCost() : purchased * unitCost;
        return new IngredientEstimate(ingredient.getItemName(), baseStems, required,
                ingredient.getPackSize(), packs, purchased, purchased - shortage,
                live, planned, farmAllocated, shortage, farmCost, wholesaleCost,
                farmCost + wholesaleCost);
    }

    private static boolean cropMatches(SeasonPlanEntry plan, String ingredient) {
        String crop = normalize(plan.getCropName());
        String requested = normalize(ingredient);
        return crop.equals(requested) || requested.startsWith(crop + " ")
                || crop.startsWith(requested + " ");
    }

    private static double plannedDuring(SeasonPlanEntry plan,
                                        LocalDate from, LocalDate to) {
        LocalDate overlapStart = plan.getHarvestStart().isAfter(from)
                ? plan.getHarvestStart() : from;
        LocalDate overlapEnd = plan.getHarvestEnd().isBefore(to)
                ? plan.getHarvestEnd() : to;
        if (overlapEnd.isBefore(overlapStart)) {
            return 0;
        }
        long totalDays = ChronoUnit.DAYS.between(
                plan.getHarvestStart(), plan.getHarvestEnd()) + 1;
        long overlapDays = ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
        return plan.getExpectedStems() * overlapDays / (double) totalDays;
    }

    private Map<String, Item> inventoryByName() {
        Map<String, Item> result = new LinkedHashMap<>();
        for (Item item : inventory.getAllItems()) {
            result.putIfAbsent(normalize(item.getName()), item);
        }
        return result;
    }

    private GrowingTemplate template(Long id) {
        requireId(id, "Growing template id");
        return repository.findTemplate(id).orElseThrow(() ->
                new IndexOutOfBoundsException("No growing template with id=" + id));
    }

    private GrowingTemplate templateForUpdate(Long id) {
        requireId(id, "Growing template id");
        return repository.findTemplateForUpdate(id).orElseThrow(() ->
                new IndexOutOfBoundsException("No growing template with id=" + id));
    }

    private String contractBrief(CustomerOrder order, String extra) {
        if (order == null) {
            throw new IllegalArgumentException("Contract handoffs require a related order.");
        }
        return truncate(farmProfile.getName() + " operational scope brief; customer="
                + order.getCustomer().getName() + "; service date=" + order.getOrderDate()
                + "; order total=" + money(order.lineTotal()) + "; line count="
                + order.getLines().size() + "; handoff=" + order.getHandoffType()
                + ". Review terms, cancellation, substitutions, delivery, and signature "
                + "inside the selected contract provider. " + clean(extra));
    }

    private String paymentBrief(CustomerOrder order, double amount, String extra) {
        if (order == null) {
            throw new IllegalArgumentException("Payment handoffs require a related order.");
        }
        return truncate(farmProfile.getName() + " payment request; customer="
                + order.getCustomer().getName() + "; order=" + order.getId()
                + "; amount=" + money(amount) + "; service date=" + order.getOrderDate()
                + ". No card, bank, token, or checkout credentials are stored here. "
                + clean(extra));
    }

    private String marketplaceBrief(String extra) {
        return truncate(farmProfile.getName()
                + " marketplace availability handoff. Publish only reviewed quantities, "
                + "prices, units, and pickup/delivery terms in the selected marketplace. "
                + clean(extra));
    }

    private static String normalizedType(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private static String money(double amount) {
        return String.format(Locale.ROOT, "USD %.2f", amount);
    }

    private static String truncate(String value) {
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private static void requireTemplate(GrowingTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Growing template is required.");
        }
    }

    private static void requireRecipe(FloralRecipe recipe) {
        if (recipe == null) {
            throw new IllegalArgumentException("Floral recipe is required.");
        }
        if (recipe.getIngredients().isEmpty()) {
            throw new IllegalArgumentException("A floral recipe needs at least one ingredient.");
        }
        Set<String> ingredientNames = new HashSet<>();
        for (FloralRecipeIngredient ingredient : recipe.getIngredients()) {
            String name = normalize(ingredient.getItemName());
            if (!ingredientNames.add(name)) {
                throw new IllegalArgumentException("Recipe ingredient '" + ingredient.getItemName()
                        + "' appears more than once; combine it into one line.");
            }
        }
    }

    private static void requireBed(String bed, double bedFeet) {
        if (bed == null || bed.isBlank() || bed.trim().length() > 128) {
            throw new IllegalArgumentException("Bed or field is required and limited to 128 characters.");
        }
        if (!Double.isFinite(bedFeet) || bedFeet <= 0 || bedFeet > 100_000) {
            throw new IllegalArgumentException("Bed length must be between 0 and 100,000 feet.");
        }
    }

    private static void requireSuccessions(GrowingTemplate template, int successions) {
        if (successions < 1 || successions > MAX_SUCCESSIONS) {
            throw new IllegalArgumentException("Successions must be between 1 and "
                    + MAX_SUCCESSIONS + ".");
        }
        if (successions > 1 && template.getSuccessionDays() == 0) {
            throw new IllegalArgumentException(
                    "This template needs a succession interval before creating multiple blocks.");
        }
    }

    private static void requireYear(int year) {
        if (year < 2000 || year > 2200) {
            throw new IllegalArgumentException("Season year must be between 2000 and 2200.");
        }
    }

    private static void requireLaborRate(double rate) {
        if (!Double.isFinite(rate) || rate < 0 || rate > 10_000) {
            throw new IllegalArgumentException(
                    "Loaded labor rate must be between 0 and 10,000 per hour.");
        }
    }

    private static double coverage(int actualized, int total) {
        return total == 0 ? 0 : round(actualized * 100d / total);
    }

    private static Double unitCost(double cost, double stems) {
        return stems <= 0 ? null : Math.round(cost / stems * 10_000d) / 10_000d;
    }

    private static void requireId(Long id, String field) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(field + " must be positive.");
        }
    }

    public record PlantingDraft(
            int seasonYear, Long templateId, String templateName, String cropName,
            String variety, String bedOrField, double bedFeet, int successionNumber,
            LocalDate sowDate, LocalDate transplantDate, LocalDate harvestStart,
            LocalDate harvestEnd, int plantSites, int viablePlants, int expectedStems,
            boolean fallFrostRisk, String frostMessage) {
    }

    public record CommittedPlanting(SeasonPlanEntry plan, List<ProductionTask> tasks) {
        public CommittedPlanting {
            tasks = List.copyOf(tasks);
        }
    }

    public record TaskActuals(
            int actualMinutes, double inputCost, String fieldObservation) {
        public TaskActuals {
            if (actualMinutes < 0 || actualMinutes > 10_080) {
                throw new IllegalArgumentException(
                        "Actual labor minutes must be between 0 and 10,080.");
            }
            if (!Double.isFinite(inputCost)
                    || inputCost < 0 || inputCost > 1_000_000) {
                throw new IllegalArgumentException(
                        "Input cost must be between 0 and 1,000,000.");
            }
            fieldObservation = fieldObservation == null
                    ? "" : fieldObservation.trim();
            if (fieldObservation.length() > 1000) {
                throw new IllegalArgumentException(
                        "Field observation cannot exceed 1000 characters.");
            }
        }
    }

    public record PlantingCostLine(
            Long seasonPlanId, String cropName, String variety, String bedOrField,
            SeasonPlanStatus status, int expectedStems, double harvestedStems,
            int taskCount, int actualizedTaskCount, int actualMinutes,
            double laborCost, double inputCost, double totalProductionCost,
            Double costPerExpectedStem, Double costPerHarvestedStem,
            double actualCoveragePercent) {
    }

    public record ProductionCostReport(
            int seasonYear, double loadedLaborRatePerHour, String laborRateSource,
            List<PlantingCostLine> plantings, long expectedStems, double harvestedStems,
            int taskCount, int actualizedTaskCount, int actualMinutes,
            double laborCost, double inputCost, double totalProductionCost,
            double allocatedProductionCost, Double costPerExpectedStem,
            Double costPerHarvestedStem, double actualCoveragePercent,
            int unlinkedTaskCount, int unlinkedActualizedTaskCount,
            int unlinkedActualMinutes, double unlinkedLaborCost,
            double unlinkedInputCost, double unlinkedProductionCost,
            double unmatchedHarvestStems, int unmatchedHarvestCount,
            List<String> warnings) {
        public ProductionCostReport {
            plantings = List.copyOf(plantings);
            warnings = List.copyOf(warnings);
        }
    }

    public record IngredientEstimate(
            String itemName, double baseStems, int requiredWithWaste, int packSize,
            int packsToBuy, int purchasedStems, int packLeftovers, int liveInventory,
            int plannedThisWeek, int farmAllocated, int remainingShortage,
            double farmCost, double wholesaleCost, double materialCost) {
    }

    public record RecipeEstimate(
            Long recipeId, String recipeName, int designCount, LocalDate eventDate,
            List<IngredientEstimate> ingredients, double flowerCost, double laborCost,
            double overheadCost, double totalCost, double revenue, double profit,
            double marginPercent, double markupPercent) {
        public RecipeEstimate {
            ingredients = List.copyOf(ingredients);
        }
    }

    public record AvailabilityLine(
            String cropName, LocalDate weekStart, LocalDate weekEnd,
            double plannedStems, int liveInventory, double totalAvailable,
            String harvestWindows) {
    }

    public record HandoffRequest(
            String handoffType, String provider, String status,
            String externalReference, Long relatedOrderId, Long relatedRecipeId,
            Double amount, String currency, String summary) {
    }

    private static final class MutableAvailability {
        private final String cropName;
        private final List<String> windows = new ArrayList<>();
        private double planned;
        private int live;

        private MutableAvailability(String cropName) {
            this.cropName = cropName;
        }
    }

    private static final class TaskCostTotals {
        private int taskCount;
        private int actualizedTaskCount;
        private int actualMinutes;
        private double inputCost;

        private static TaskCostTotals from(List<ProductionTask> tasks) {
            TaskCostTotals totals = new TaskCostTotals();
            totals.taskCount = tasks.size();
            for (ProductionTask task : tasks) {
                if (!task.hasActuals()) {
                    continue;
                }
                totals.actualizedTaskCount++;
                totals.actualMinutes += task.getActualMinutes() == null
                        ? 0 : task.getActualMinutes();
                totals.inputCost += task.getInputCost();
            }
            return totals;
        }

        private void add(TaskCostTotals other) {
            taskCount += other.taskCount;
            actualizedTaskCount += other.actualizedTaskCount;
            actualMinutes += other.actualMinutes;
            inputCost += other.inputCost;
        }

        private double laborCost(double rate) {
            return actualMinutes / 60d * rate;
        }
    }
}
