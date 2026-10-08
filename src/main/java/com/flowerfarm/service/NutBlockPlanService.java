package com.flowerfarm.service;

import com.flowerfarm.model.NutBlockPlan;
import com.flowerfarm.model.NutBlockStage;
import com.flowerfarm.repository.NutBlockPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local tree nut orchard block plans and their progress stage. */
@Service
public class NutBlockPlanService {

    private final NutBlockPlanRepository repository;

    public NutBlockPlanService(NutBlockPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NutBlockPlan> list() {
        return repository.findAll().stream().map(NutBlockPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<NutBlockPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(NutBlockPlan::snapshot);
    }

    @Transactional
    public NutBlockPlan create(NutBlockPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New tree nut block plans cannot have an id.");
        }
        NutBlockPlan draft = new NutBlockPlan(incoming.getBlockName(), incoming.getNutCrop(),
                incoming.getVariety(), incoming.getPollinizerVariety(), incoming.getAcres(),
                incoming.getTreeCount(), incoming.getPlannedBloomStart(),
                incoming.getPlannedFloorPrepDate(), incoming.getPlannedHarvestStart(),
                incoming.getPlannedHarvestEnd(), incoming.getIntendedDestination(),
                incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public NutBlockPlan update(Long id, NutBlockPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Tree nut block plan id does not match the edit target.");
        }
        NutBlockPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tree nut block plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of the locked saved plan, preserving other planning edits. */
    @Transactional
    public NutBlockPlan updateStage(long id, NutBlockStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Tree nut block stage is required.");
        }
        NutBlockPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tree nut block plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Tree nut block plan id must be positive.");
        }
    }

    private static void requirePlan(NutBlockPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Tree nut block plan is required.");
        }
        plan.validate();
    }
}
