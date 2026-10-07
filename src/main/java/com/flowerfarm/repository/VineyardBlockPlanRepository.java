package com.flowerfarm.repository;

import com.flowerfarm.model.VineyardBlockPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local vineyard block planning. */
public interface VineyardBlockPlanRepository {

    List<VineyardBlockPlan> findAll();

    Optional<VineyardBlockPlan> findById(Long id);

    Optional<VineyardBlockPlan> findByIdForUpdate(Long id);

    VineyardBlockPlan save(VineyardBlockPlan plan);
}
