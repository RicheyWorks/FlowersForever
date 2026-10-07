package com.flowerfarm.service;

import com.flowerfarm.model.CropAreaPlan;
import com.flowerfarm.model.CropAreaStage;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.repository.CropAreaPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local potato field and apple block plans. */
@Service
public class CropAreaPlanService {

    private final CropAreaPlanRepository repository;

    public CropAreaPlanService(CropAreaPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CropAreaPlan> listByWorkspace(FarmWorkspace workspace) {
        CropAreaPlan.requireSupportedWorkspace(workspace);
        return repository.findByWorkspace(workspace).stream()
                .map(CropAreaPlan::snapshot)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CropAreaPlan> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(CropAreaPlan::snapshot);
    }

    @Transactional
    public CropAreaPlan create(CropAreaPlan incoming) {
        requirePlan(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New crop area plans cannot have an id.");
        }
        CropAreaPlan draft = new CropAreaPlan(incoming.getWorkspace(), incoming.getAreaName(),
                incoming.getVariety(), incoming.getAcres(), incoming.getPlantingDate(),
                incoming.getTargetHarvestDate(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public CropAreaPlan update(Long id, CropAreaPlan incoming) {
        requireId(id);
        requirePlan(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Crop area plan id does not match the edit target.");
        }
        CropAreaPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Crop area plan #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Changes only the stage of the locked saved plan; other edits are preserved. */
    @Transactional
    public CropAreaPlan updateStage(Long id, FarmWorkspace workspace, CropAreaStage stage) {
        requireId(id);
        CropAreaPlan.requireSupportedWorkspace(workspace);
        if (stage == null) {
            throw new IllegalArgumentException("Planning stage is required.");
        }
        CropAreaPlan current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Crop area plan #" + id + " was not found."));
        if (current.getWorkspace() != workspace) {
            throw new IllegalArgumentException("Crop area plan belongs to another farm workspace.");
        }
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Crop area plan id must be positive.");
        }
    }

    private static void requirePlan(CropAreaPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Crop area plan is required.");
        }
        plan.validate();
    }
}
