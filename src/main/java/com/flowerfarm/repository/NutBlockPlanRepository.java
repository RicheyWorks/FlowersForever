package com.flowerfarm.repository;

import com.flowerfarm.model.NutBlockPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local tree nut orchard block plans. */
public interface NutBlockPlanRepository {

    List<NutBlockPlan> findAll();

    Optional<NutBlockPlan> findById(Long id);

    Optional<NutBlockPlan> findByIdForUpdate(Long id);

    NutBlockPlan save(NutBlockPlan plan);
}
