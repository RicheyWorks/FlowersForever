package com.flowerfarm.repository;

import com.flowerfarm.model.AquaticCohortPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local aquaculture cohort plans. */
@Repository
public class JpaAquaticCohortPlanRepository implements AquaticCohortPlanRepository {

    private final AquaticCohortPlanJpaRepository jpa;

    public JpaAquaticCohortPlanRepository(AquaticCohortPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<AquaticCohortPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<AquaticCohortPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<AquaticCohortPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public AquaticCohortPlan save(AquaticCohortPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
