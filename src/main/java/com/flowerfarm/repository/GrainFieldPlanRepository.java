package com.flowerfarm.repository;

import com.flowerfarm.model.GrainFieldPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local grain field plans. */
public interface GrainFieldPlanRepository {

    List<GrainFieldPlan> findAll();

    Optional<GrainFieldPlan> findById(Long id);

    Optional<GrainFieldPlan> findByIdForUpdate(Long id);

    GrainFieldPlan save(GrainFieldPlan plan);
}
