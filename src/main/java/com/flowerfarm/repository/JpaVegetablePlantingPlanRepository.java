package com.flowerfarm.repository;

import com.flowerfarm.model.VegetablePlantingPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for vegetable field and bed planting plans. */
@Repository
public class JpaVegetablePlantingPlanRepository implements VegetablePlantingPlanRepository {

    private final VegetablePlantingPlanJpaRepository jpa;

    public JpaVegetablePlantingPlanRepository(VegetablePlantingPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<VegetablePlantingPlan> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<VegetablePlantingPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<VegetablePlantingPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public VegetablePlantingPlan save(VegetablePlantingPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
