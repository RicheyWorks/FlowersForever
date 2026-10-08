package com.flowerfarm.service;

import com.flowerfarm.model.CranberryBedPlan;
import com.flowerfarm.model.CranberryBedStage;
import com.flowerfarm.repository.CranberryBedPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local cranberry bed plans and their planning stage. */
@Service
public class CranberryBedPlanService {

    private final CranberryBedPlanRepository repository;

    public CranberryBedPlanService(CranberryBedPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CranberryBedPlan> list() {
        return repository.findAll().stream().map(CranberryBedPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<CranberryBedPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(CranberryBedPlan::snapshot);
    }

    @Transactional
    public CranberryBedPlan create(CranberryBedPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New cranberry bed plans cannot have an id.");
        }
        CranberryBedPlan draft = new CranberryBedPlan(incoming.getBedName(),
                incoming.getVariety(), incoming.getAcres(), incoming.getHarvestMethod(),
                incoming.getPlannedHarvestStart(), incoming.getPlannedHarvestEnd(),
                incoming.getIntendedChannel(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public CranberryBedPlan update(Long id, CranberryBedPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException(
                    "Cranberry bed plan id does not match the edit target.");
        }
        CranberryBedPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cranberry bed plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of a locked saved plan, preserving other planning edits. */
    @Transactional
    public CranberryBedPlan updateStage(long id, CranberryBedStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Cranberry bed stage is required.");
        }
        CranberryBedPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cranberry bed plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Cranberry bed plan id must be positive.");
        }
    }

    private static void requirePlan(CranberryBedPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Cranberry bed plan is required.");
        }
        plan.validate();
    }
}
