package com.flowerfarm.service;

import com.flowerfarm.model.BerryBlockPlan;
import com.flowerfarm.repository.BerryBlockPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local berry block plans. */
@Service
public class BerryBlockPlanService {

    private final BerryBlockPlanRepository repository;

    public BerryBlockPlanService(BerryBlockPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<BerryBlockPlan> list() {
        return repository.findAll().stream().map(BerryBlockPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<BerryBlockPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(BerryBlockPlan::snapshot);
    }

    @Transactional
    public BerryBlockPlan create(BerryBlockPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New berry block plans cannot have an id.");
        }
        BerryBlockPlan draft = new BerryBlockPlan(incoming.getBlockName(), incoming.getCrop(),
                incoming.getCultivar(), incoming.getAcres(), incoming.getPlantingDate(),
                incoming.getFirstPickDate(), incoming.getLastPickDate(), incoming.getChannel(),
                incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public BerryBlockPlan update(Long id, BerryBlockPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Berry block plan id does not match the edit target.");
        }
        BerryBlockPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Berry block plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Berry block plan id must be positive.");
        }
    }

    private static void requirePlan(BerryBlockPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Berry block plan is required.");
        }
        plan.validate();
    }
}
