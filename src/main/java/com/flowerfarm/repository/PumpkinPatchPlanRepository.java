package com.flowerfarm.repository;

import com.flowerfarm.model.PumpkinPatchPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local pumpkin patch plans. */
public interface PumpkinPatchPlanRepository {

    List<PumpkinPatchPlan> findAll();

    Optional<PumpkinPatchPlan> findById(Long id);

    Optional<PumpkinPatchPlan> findByIdForUpdate(Long id);

    PumpkinPatchPlan save(PumpkinPatchPlan plan);
}
