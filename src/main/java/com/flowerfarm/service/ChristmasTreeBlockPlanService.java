package com.flowerfarm.service;

import com.flowerfarm.model.ChristmasTreeBlockPlan;
import com.flowerfarm.model.ChristmasTreeBlockStage;
import com.flowerfarm.repository.ChristmasTreeBlockPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local Christmas tree block plans and their planning stage. */
@Service
public class ChristmasTreeBlockPlanService {

    private final ChristmasTreeBlockPlanRepository repository;

    public ChristmasTreeBlockPlanService(ChristmasTreeBlockPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ChristmasTreeBlockPlan> list() {
        return repository.findAll().stream().map(ChristmasTreeBlockPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<ChristmasTreeBlockPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(ChristmasTreeBlockPlan::snapshot);
    }

    @Transactional
    public ChristmasTreeBlockPlan create(ChristmasTreeBlockPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New Christmas tree block plans cannot have an id.");
        }
        ChristmasTreeBlockPlan draft = new ChristmasTreeBlockPlan(
                incoming.getBlockName(), incoming.getTreeSpecies(), incoming.getPlantingYear(),
                incoming.getAcres(), incoming.getPlantedTreeCount(), incoming.getTargetHeight(),
                incoming.getNextShearingDate(), incoming.getTargetHarvestYear(),
                incoming.getPlannedCutStart(), incoming.getPlannedCutEnd(),
                incoming.getIntendedChannel(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public ChristmasTreeBlockPlan update(Long id, ChristmasTreeBlockPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException(
                    "Christmas tree block plan id does not match the edit target.");
        }
        ChristmasTreeBlockPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Christmas tree block plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of the locked saved plan, preserving other planning edits. */
    @Transactional
    public ChristmasTreeBlockPlan updateStage(long id, ChristmasTreeBlockStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Christmas tree block stage is required.");
        }
        ChristmasTreeBlockPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Christmas tree block plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Christmas tree block plan id must be positive.");
        }
    }

    private static void requirePlan(ChristmasTreeBlockPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Christmas tree block plan is required.");
        }
        plan.validate();
    }
}
