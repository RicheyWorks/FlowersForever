package com.flowerfarm.repository;

import com.flowerfarm.model.MushroomBatchPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local mushroom batch plans. */
@Repository
public class JpaMushroomBatchPlanRepository implements MushroomBatchPlanRepository {

    private final MushroomBatchPlanJpaRepository jpa;

    public JpaMushroomBatchPlanRepository(MushroomBatchPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<MushroomBatchPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<MushroomBatchPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<MushroomBatchPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public MushroomBatchPlan save(MushroomBatchPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
