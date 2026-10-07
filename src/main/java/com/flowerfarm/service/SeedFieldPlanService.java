package com.flowerfarm.service;

import com.flowerfarm.model.SeedFieldPlan;
import com.flowerfarm.model.SeedFieldStage;
import com.flowerfarm.repository.SeedFieldPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local seed field plans and their progress stage. */
@Service
public class SeedFieldPlanService {

    private final SeedFieldPlanRepository repository;

    public SeedFieldPlanService(SeedFieldPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<SeedFieldPlan> list() {
        return repository.findAll().stream().map(SeedFieldPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<SeedFieldPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(SeedFieldPlan::snapshot);
    }

    @Transactional
    public SeedFieldPlan create(SeedFieldPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New seed field plans cannot have an id.");
        }
        SeedFieldPlan draft = new SeedFieldPlan(incoming.getFieldName(), incoming.getSeedCrop(),
                incoming.getVariety(), incoming.getAcres(), incoming.getPreviousCrop(),
                incoming.getPlannedSowingDate(), incoming.getPlannedFloweringStart(),
                incoming.getPlannedFloweringEnd(), incoming.getPlannedHarvestStart(),
                incoming.getPlannedHarvestEnd(), incoming.getIntendedUse(),
                incoming.getIsolationNotes(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public SeedFieldPlan update(Long id, SeedFieldPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Seed field plan id does not match the edit target.");
        }
        SeedFieldPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Seed field plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of the locked saved plan, preserving other planning edits. */
    @Transactional
    public SeedFieldPlan updateStage(long id, SeedFieldStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Seed field stage is required.");
        }
        SeedFieldPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Seed field plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Seed field plan id must be positive.");
        }
    }

    private static void requirePlan(SeedFieldPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Seed field plan is required.");
        }
        plan.validate();
    }
}
