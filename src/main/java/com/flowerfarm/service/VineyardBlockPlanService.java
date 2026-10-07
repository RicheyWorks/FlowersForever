package com.flowerfarm.service;

import com.flowerfarm.model.VineyardBlockPlan;
import com.flowerfarm.model.VineyardBlockStage;
import com.flowerfarm.repository.VineyardBlockPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local vineyard block plans. */
@Service
public class VineyardBlockPlanService {

    private final VineyardBlockPlanRepository repository;

    public VineyardBlockPlanService(VineyardBlockPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<VineyardBlockPlan> list() {
        return repository.findAll().stream().map(VineyardBlockPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<VineyardBlockPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(VineyardBlockPlan::snapshot);
    }

    @Transactional
    public VineyardBlockPlan create(VineyardBlockPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New vineyard block plans cannot have an id.");
        }
        VineyardBlockPlan draft = new VineyardBlockPlan(incoming.getBlockName(),
                incoming.getLocation(), incoming.getGrapeVariety(), incoming.getAcres(),
                incoming.getPlannedHarvestStart(), incoming.getPlannedHarvestEnd(),
                incoming.getDestination(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public VineyardBlockPlan update(Long id, VineyardBlockPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Vineyard block plan id does not match the edit target.");
        }
        VineyardBlockPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vineyard block plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of the locked saved block, preserving other planning edits. */
    @Transactional
    public VineyardBlockPlan updateStage(long id, VineyardBlockStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Vineyard block stage is required.");
        }
        VineyardBlockPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vineyard block plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Vineyard block plan id must be positive.");
        }
    }

    private static void requirePlan(VineyardBlockPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Vineyard block plan is required.");
        }
        plan.validate();
    }
}
