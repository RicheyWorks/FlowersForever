package com.flowerfarm.service;

import com.flowerfarm.model.PumpkinPatchPlan;
import com.flowerfarm.model.PumpkinPatchStage;
import com.flowerfarm.repository.PumpkinPatchPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local pumpkin patch plans and their planning stage. */
@Service
public class PumpkinPatchPlanService {

    private final PumpkinPatchPlanRepository repository;

    public PumpkinPatchPlanService(PumpkinPatchPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PumpkinPatchPlan> list() {
        return repository.findAll().stream().map(PumpkinPatchPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<PumpkinPatchPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(PumpkinPatchPlan::snapshot);
    }

    @Transactional
    public PumpkinPatchPlan create(PumpkinPatchPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New pumpkin patch plans cannot have an id.");
        }
        PumpkinPatchPlan draft = new PumpkinPatchPlan(incoming.getPatchName(),
                incoming.getVariety(), incoming.getAcres(),
                incoming.getPlannedPlantingDate(), incoming.getPlannedPickingStart(),
                incoming.getPlannedPickingEnd(), incoming.getSalesMode(),
                incoming.getVisitorAreaNotes(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public PumpkinPatchPlan update(Long id, PumpkinPatchPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException(
                    "Pumpkin patch plan id does not match the edit target.");
        }
        PumpkinPatchPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pumpkin patch plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of a locked saved plan, preserving other planning edits. */
    @Transactional
    public PumpkinPatchPlan updateStage(long id, PumpkinPatchStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Pumpkin patch stage is required.");
        }
        PumpkinPatchPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pumpkin patch plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Pumpkin patch plan id must be positive.");
        }
    }

    private static void requirePlan(PumpkinPatchPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Pumpkin patch plan is required.");
        }
        plan.validate();
    }
}
