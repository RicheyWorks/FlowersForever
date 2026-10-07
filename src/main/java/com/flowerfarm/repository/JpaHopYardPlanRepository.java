package com.flowerfarm.repository;

import com.flowerfarm.model.HopYardPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local hop yard plans. */
@Repository
public class JpaHopYardPlanRepository implements HopYardPlanRepository {

    private final HopYardPlanJpaRepository jpa;

    public JpaHopYardPlanRepository(HopYardPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<HopYardPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<HopYardPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<HopYardPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public HopYardPlan save(HopYardPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
