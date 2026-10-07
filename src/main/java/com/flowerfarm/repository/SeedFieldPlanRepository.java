package com.flowerfarm.repository;

import com.flowerfarm.model.SeedFieldPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local seed field plans. */
public interface SeedFieldPlanRepository {

    List<SeedFieldPlan> findAll();

    Optional<SeedFieldPlan> findById(Long id);

    Optional<SeedFieldPlan> findByIdForUpdate(Long id);

    SeedFieldPlan save(SeedFieldPlan plan);
}
