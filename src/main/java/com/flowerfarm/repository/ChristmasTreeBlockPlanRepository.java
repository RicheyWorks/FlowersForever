package com.flowerfarm.repository;

import com.flowerfarm.model.ChristmasTreeBlockPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local Christmas tree block plans. */
public interface ChristmasTreeBlockPlanRepository {

    List<ChristmasTreeBlockPlan> findAll();

    Optional<ChristmasTreeBlockPlan> findById(Long id);

    Optional<ChristmasTreeBlockPlan> findByIdForUpdate(Long id);

    ChristmasTreeBlockPlan save(ChristmasTreeBlockPlan plan);
}
