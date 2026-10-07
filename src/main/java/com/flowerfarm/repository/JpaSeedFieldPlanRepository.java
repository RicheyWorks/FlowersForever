package com.flowerfarm.repository;

import com.flowerfarm.model.SeedFieldPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local seed field plans. */
@Repository
public class JpaSeedFieldPlanRepository implements SeedFieldPlanRepository {

    private final SeedFieldPlanJpaRepository jpa;

    public JpaSeedFieldPlanRepository(SeedFieldPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<SeedFieldPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<SeedFieldPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<SeedFieldPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public SeedFieldPlan save(SeedFieldPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
