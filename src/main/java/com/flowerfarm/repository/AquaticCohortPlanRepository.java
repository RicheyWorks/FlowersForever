package com.flowerfarm.repository;

import com.flowerfarm.model.AquaticCohortPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local aquaculture cohort plans. */
public interface AquaticCohortPlanRepository {

    List<AquaticCohortPlan> findAll();

    Optional<AquaticCohortPlan> findById(Long id);

    Optional<AquaticCohortPlan> findByIdForUpdate(Long id);

    AquaticCohortPlan save(AquaticCohortPlan plan);
}
