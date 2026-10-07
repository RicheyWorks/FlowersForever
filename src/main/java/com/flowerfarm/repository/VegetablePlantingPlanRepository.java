package com.flowerfarm.repository;

import com.flowerfarm.model.VegetablePlantingPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for vegetable field and bed planting plans. */
public interface VegetablePlantingPlanRepository {

    List<VegetablePlantingPlan> findAll();

    Optional<VegetablePlantingPlan> findById(Long id);

    Optional<VegetablePlantingPlan> findByIdForUpdate(Long id);

    VegetablePlantingPlan save(VegetablePlantingPlan plan);
}
