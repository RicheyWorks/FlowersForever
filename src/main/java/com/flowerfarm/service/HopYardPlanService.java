package com.flowerfarm.service;

import com.flowerfarm.model.HopYardPlan;
import com.flowerfarm.model.HopYardStage;
import com.flowerfarm.repository.HopYardPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local hop yard plans and their progress stage. */
@Service
public class HopYardPlanService {

    private final HopYardPlanRepository repository;

    public HopYardPlanService(HopYardPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<HopYardPlan> list() {
        return repository.findAll().stream().map(HopYardPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<HopYardPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(HopYardPlan::snapshot);
    }

    @Transactional
    public HopYardPlan create(HopYardPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New hop yard plans cannot have an id.");
        }
        HopYardPlan draft = new HopYardPlan(incoming.getYardName(), incoming.getHopVariety(),
                incoming.getAcres(), incoming.getPlannedStringingDate(),
                incoming.getPlannedHarvestStart(), incoming.getPlannedHarvestEnd(),
                incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public HopYardPlan update(Long id, HopYardPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Hop yard plan id does not match the edit target.");
        }
        HopYardPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hop yard plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of the locked saved plan, preserving other planning edits. */
    @Transactional
    public HopYardPlan updateStage(long id, HopYardStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Hop yard stage is required.");
        }
        HopYardPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hop yard plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Hop yard plan id must be positive.");
        }
    }

    private static void requirePlan(HopYardPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Hop yard plan is required.");
        }
        plan.validate();
    }
}
