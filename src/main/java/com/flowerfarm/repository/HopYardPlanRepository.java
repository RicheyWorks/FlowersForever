package com.flowerfarm.repository;

import com.flowerfarm.model.HopYardPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local hop yard plans. */
public interface HopYardPlanRepository {

    List<HopYardPlan> findAll();

    Optional<HopYardPlan> findById(Long id);

    Optional<HopYardPlan> findByIdForUpdate(Long id);

    HopYardPlan save(HopYardPlan plan);
}
