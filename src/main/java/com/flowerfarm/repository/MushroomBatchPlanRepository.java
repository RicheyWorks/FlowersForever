package com.flowerfarm.repository;

import com.flowerfarm.model.MushroomBatchPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local mushroom batch plans. */
public interface MushroomBatchPlanRepository {

    List<MushroomBatchPlan> findAll();

    Optional<MushroomBatchPlan> findById(Long id);

    Optional<MushroomBatchPlan> findByIdForUpdate(Long id);

    MushroomBatchPlan save(MushroomBatchPlan plan);
}
