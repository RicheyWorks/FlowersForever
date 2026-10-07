package com.flowerfarm.repository;

import com.flowerfarm.model.CranberryBedPlan;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local cranberry bed plans. */
public interface CranberryBedPlanRepository {

    List<CranberryBedPlan> findAll();

    Optional<CranberryBedPlan> findById(Long id);

    Optional<CranberryBedPlan> findByIdForUpdate(Long id);

    CranberryBedPlan save(CranberryBedPlan plan);
}
