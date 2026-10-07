package com.flowerfarm.service;

import com.flowerfarm.model.AquaticCohortPlan;
import com.flowerfarm.model.AquaticCohortStage;
import com.flowerfarm.repository.AquaticCohortPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local aquatic cohort plans and their progress stage. */
@Service
public class AquaticCohortPlanService {

    private final AquaticCohortPlanRepository repository;

    public AquaticCohortPlanService(AquaticCohortPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AquaticCohortPlan> list() {
        return repository.findAll().stream().map(AquaticCohortPlan::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AquaticCohortPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(AquaticCohortPlan::snapshot);
    }

    @Transactional
    public AquaticCohortPlan create(AquaticCohortPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New aquaculture cohort plans cannot have an id.");
        }
        AquaticCohortPlan draft = new AquaticCohortPlan(incoming.getCohortName(),
                incoming.getSpecies(), incoming.getGrowingSite(), incoming.getCultureMethod(),
                incoming.getPlannedStockingDate(), incoming.getPlannedHarvestStart(),
                incoming.getPlannedHarvestEnd(), incoming.getIntendedDestination(),
                incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public AquaticCohortPlan update(Long id, AquaticCohortPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException(
                    "Aquaculture cohort plan id does not match the edit target.");
        }
        AquaticCohortPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aquaculture cohort plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of the locked saved plan, preserving other planning edits. */
    @Transactional
    public AquaticCohortPlan updateStage(long id, AquaticCohortStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Aquaculture cohort stage is required.");
        }
        AquaticCohortPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aquaculture cohort plan #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Aquaculture cohort plan id must be positive.");
        }
    }

    private static void requirePlan(AquaticCohortPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Aquaculture cohort plan is required.");
        }
        plan.validate();
    }
}
