package com.flowerfarm.repository;

import com.flowerfarm.model.GrainFieldPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local grain field plans. */
@Repository
public class JpaGrainFieldPlanRepository implements GrainFieldPlanRepository {

    private final GrainFieldPlanJpaRepository jpa;

    public JpaGrainFieldPlanRepository(GrainFieldPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<GrainFieldPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<GrainFieldPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<GrainFieldPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public GrainFieldPlan save(GrainFieldPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
