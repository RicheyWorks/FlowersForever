package com.flowerfarm.repository;

import com.flowerfarm.model.VineyardBlockPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local vineyard block planning. */
@Repository
public class JpaVineyardBlockPlanRepository implements VineyardBlockPlanRepository {

    private final VineyardBlockPlanJpaRepository jpa;

    public JpaVineyardBlockPlanRepository(VineyardBlockPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<VineyardBlockPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<VineyardBlockPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<VineyardBlockPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public VineyardBlockPlan save(VineyardBlockPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
