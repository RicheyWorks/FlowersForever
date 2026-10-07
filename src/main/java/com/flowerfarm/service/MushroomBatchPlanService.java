package com.flowerfarm.service;

import com.flowerfarm.model.MushroomBatchPlan;
import com.flowerfarm.model.MushroomBatchStage;
import com.flowerfarm.repository.MushroomBatchPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local mushroom batch plans and their planning stage. */
@Service
public class MushroomBatchPlanService {

    private final MushroomBatchPlanRepository repository;

    public MushroomBatchPlanService(MushroomBatchPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MushroomBatchPlan> list() {
        return repository.findAll().stream().map(MushroomBatchPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<MushroomBatchPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(MushroomBatchPlan::snapshot);
    }

    @Transactional
    public MushroomBatchPlan create(MushroomBatchPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New mushroom batch plans cannot have an id.");
        }
        MushroomBatchPlan draft = new MushroomBatchPlan(incoming.getBatchName(),
                incoming.getMushroomSpecies(), incoming.getGrowingMethod(),
                incoming.getSubstrate(), incoming.getGrowingSite(),
                incoming.getPlannedInoculationDate(), incoming.getPlannedFruitingStart(),
                incoming.getPlannedFruitingEnd(), incoming.getIntendedChannel(),
                incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public MushroomBatchPlan update(Long id, MushroomBatchPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException(
                    "Mushroom batch plan id does not match the edit target.");
        }
        MushroomBatchPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Mushroom batch plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of a locked saved plan, preserving other planning edits. */
    @Transactional
    public MushroomBatchPlan updateStage(long id, MushroomBatchStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Mushroom batch stage is required.");
        }
        MushroomBatchPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Mushroom batch plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Mushroom batch plan id must be positive.");
        }
    }

    private static void requirePlan(MushroomBatchPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Mushroom batch plan is required.");
        }
        plan.validate();
    }
}
