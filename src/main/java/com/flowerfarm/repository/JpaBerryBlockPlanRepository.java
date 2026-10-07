package com.flowerfarm.repository;

import com.flowerfarm.model.BerryBlockPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local berry block planning. */
@Repository
public class JpaBerryBlockPlanRepository implements BerryBlockPlanRepository {

    private final BerryBlockPlanJpaRepository jpa;

    public JpaBerryBlockPlanRepository(BerryBlockPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<BerryBlockPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<BerryBlockPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<BerryBlockPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public BerryBlockPlan save(BerryBlockPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
