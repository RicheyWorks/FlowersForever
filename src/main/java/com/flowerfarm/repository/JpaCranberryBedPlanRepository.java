package com.flowerfarm.repository;

import com.flowerfarm.model.CranberryBedPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local cranberry bed plans. */
@Repository
public class JpaCranberryBedPlanRepository implements CranberryBedPlanRepository {

    private final CranberryBedPlanJpaRepository jpa;

    public JpaCranberryBedPlanRepository(CranberryBedPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<CranberryBedPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<CranberryBedPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<CranberryBedPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public CranberryBedPlan save(CranberryBedPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
