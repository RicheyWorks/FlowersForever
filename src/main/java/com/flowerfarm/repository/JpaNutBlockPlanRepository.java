package com.flowerfarm.repository;

import com.flowerfarm.model.NutBlockPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local tree nut orchard block plans. */
@Repository
public class JpaNutBlockPlanRepository implements NutBlockPlanRepository {

    private final NutBlockPlanJpaRepository jpa;

    public JpaNutBlockPlanRepository(NutBlockPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<NutBlockPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<NutBlockPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<NutBlockPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public NutBlockPlan save(NutBlockPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
