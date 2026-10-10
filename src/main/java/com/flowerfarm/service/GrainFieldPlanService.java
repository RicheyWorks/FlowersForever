package com.flowerfarm.service;

import com.flowerfarm.model.GrainFieldPlan;
import com.flowerfarm.model.GrainFieldStage;
import com.flowerfarm.repository.GrainFieldPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local grain field plans and their progress stage. */
@Service
public class GrainFieldPlanService {

    private final GrainFieldPlanRepository repository;

    public GrainFieldPlanService(GrainFieldPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<GrainFieldPlan> list() {
        return repository.findAll().stream().map(GrainFieldPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<GrainFieldPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(GrainFieldPlan::snapshot);
    }

    @Transactional
    public GrainFieldPlan create(GrainFieldPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New grain field plans cannot have an id.");
        }
        GrainFieldPlan draft = new GrainFieldPlan(incoming.getFieldName(), incoming.getGrainCrop(),
                incoming.getVariety(), incoming.getAcres(), incoming.getPreviousCrop(),
                incoming.getPlannedSeedingDate(), incoming.getPlannedHarvestStart(),
                incoming.getPlannedHarvestEnd(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public GrainFieldPlan update(Long id, GrainFieldPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Grain field plan id does not match the edit target.");
        }
        GrainFieldPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Grain field plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of the locked saved plan, preserving other planning edits. */
    @Transactional
    public GrainFieldPlan updateStage(long id, GrainFieldStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Grain field stage is required.");
        }
        GrainFieldPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Grain field plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Grain field plan id must be positive.");
        }
    }

    private static void requirePlan(GrainFieldPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Grain field plan is required.");
        }
        plan.validate();
    }
}
