package com.flowerfarm.repository;

import com.flowerfarm.model.CropAreaPlan;
import com.flowerfarm.model.FarmWorkspace;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for field and orchard planning. */
public interface CropAreaPlanRepository {

    List<CropAreaPlan> findByWorkspace(FarmWorkspace workspace);

    Optional<CropAreaPlan> findById(Long id);

    Optional<CropAreaPlan> findByIdForUpdate(Long id);

    CropAreaPlan save(CropAreaPlan plan);
}
