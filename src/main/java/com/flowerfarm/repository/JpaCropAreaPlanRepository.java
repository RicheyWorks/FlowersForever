package com.flowerfarm.repository;

import com.flowerfarm.model.CropAreaPlan;
import com.flowerfarm.model.FarmWorkspace;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for field and orchard planning. */
@Repository
public class JpaCropAreaPlanRepository implements CropAreaPlanRepository {

    private final CropAreaPlanJpaRepository jpa;

    public JpaCropAreaPlanRepository(CropAreaPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<CropAreaPlan> findByWorkspace(FarmWorkspace workspace) {
        return jpa.findAllByWorkspaceOrderByIdDesc(workspace);
    }

    @Override
    public Optional<CropAreaPlan> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<CropAreaPlan> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public CropAreaPlan save(CropAreaPlan plan) {
        return jpa.saveAndFlush(plan);
    }
}
