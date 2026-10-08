package com.flowerfarm.repository;

import com.flowerfarm.model.ChristmasTreeBlockPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local Christmas tree block plans. */
@Repository
public class JpaChristmasTreeBlockPlanRepository implements ChristmasTreeBlockPlanRepository {

    private final ChristmasTreeBlockPlanJpaRepository jpa;

    public JpaChristmasTreeBlockPlanRepository(ChristmasTreeBlockPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<ChristmasTreeBlockPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<ChristmasTreeBlockPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<ChristmasTreeBlockPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public ChristmasTreeBlockPlan save(ChristmasTreeBlockPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
