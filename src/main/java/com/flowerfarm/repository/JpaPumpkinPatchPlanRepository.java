package com.flowerfarm.repository;

import com.flowerfarm.model.PumpkinPatchPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local pumpkin patch plans. */
@Repository
public class JpaPumpkinPatchPlanRepository implements PumpkinPatchPlanRepository {

    private final PumpkinPatchPlanJpaRepository jpa;

    public JpaPumpkinPatchPlanRepository(PumpkinPatchPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<PumpkinPatchPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<PumpkinPatchPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<PumpkinPatchPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public PumpkinPatchPlan save(PumpkinPatchPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
