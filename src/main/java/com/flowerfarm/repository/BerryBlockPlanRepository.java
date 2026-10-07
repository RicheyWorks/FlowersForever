package com.flowerfarm.repository;

import com.flowerfarm.model.BerryBlockPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local berry block planning. */
public interface BerryBlockPlanRepository {

    List<BerryBlockPlan> findAll();

    Optional<BerryBlockPlan> findById(Long id);

    Optional<BerryBlockPlan> findByIdForUpdate(Long id);

    BerryBlockPlan save(BerryBlockPlan plan);
}
