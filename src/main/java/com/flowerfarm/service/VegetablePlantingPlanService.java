package com.flowerfarm.service;

import com.flowerfarm.model.VegetablePlantingPlan;
import com.flowerfarm.model.VegetablePlantingStage;
import com.flowerfarm.repository.VegetablePlantingPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local vegetable succession plans and their progress stage. */
@Service
public class VegetablePlantingPlanService {

    private final VegetablePlantingPlanRepository repository;

    public VegetablePlantingPlanService(VegetablePlantingPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<VegetablePlantingPlan> list() {
        return repository.findAll().stream().map(VegetablePlantingPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<VegetablePlantingPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(VegetablePlantingPlan::snapshot);
    }

    @Transactional
    public VegetablePlantingPlan create(VegetablePlantingPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New vegetable planting plans cannot have an id.");
        }
        VegetablePlantingPlan draft = new VegetablePlantingPlan(incoming.getFieldOrBed(),
                incoming.getVegetableCrop(), incoming.getVariety(), incoming.getPreviousCrop(),
                incoming.getAcres(), incoming.getSuccessionNumber(), incoming.getPlannedSowingDate(),
                incoming.getPlannedTransplantDate(), incoming.getPlannedHarvestStart(),
                incoming.getPlannedHarvestEnd(), incoming.getIntendedChannel(),
                incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public VegetablePlantingPlan update(Long id, VegetablePlantingPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException(
                    "Vegetable planting plan id does not match the edit target.");
        }
        VegetablePlantingPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vegetable planting plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of the locked saved plan, preserving other planning edits. */
    @Transactional
    public VegetablePlantingPlan updateStage(long id, VegetablePlantingStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Vegetable planting stage is required.");
        }
        VegetablePlantingPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vegetable planting plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Vegetable planting plan id must be positive.");
        }
    }

    private static void requirePlan(VegetablePlantingPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Vegetable planting plan is required.");
        }
        plan.validate();
    }
}
