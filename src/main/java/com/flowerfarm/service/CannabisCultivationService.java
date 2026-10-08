package com.flowerfarm.service;

import com.flowerfarm.model.CannabisCultivationBatch;
import com.flowerfarm.model.CannabisCultivationStage;
import com.flowerfarm.repository.CannabisCultivationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local cultivation batch plans. */
@Service
public class CannabisCultivationService {

    private final CannabisCultivationRepository repository;

    public CannabisCultivationService(CannabisCultivationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CannabisCultivationBatch> listAll() {
        return repository.findAll().stream()
                .map(CannabisCultivationBatch::snapshot)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CannabisCultivationBatch> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(CannabisCultivationBatch::snapshot);
    }

    @Transactional
    public CannabisCultivationBatch create(CannabisCultivationBatch incoming) {
        requireBatch(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New cultivation batches cannot have an id.");
        }
        CannabisCultivationBatch draft = new CannabisCultivationBatch(
                incoming.getBatchName(), incoming.getCultivar(), incoming.getArea(),
                incoming.getPlantCount(), incoming.getStartDate(),
                incoming.getPlannedHarvestDate(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public CannabisCultivationBatch update(Long id, CannabisCultivationBatch incoming) {
        requireId(id);
        requireBatch(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Cultivation batch id does not match the edit target.");
        }
        CannabisCultivationBatch current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cultivation batch #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Changes only the planning stage of the locked saved batch. */
    @Transactional
    public CannabisCultivationBatch updateStage(Long id, CannabisCultivationStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Cultivation batch stage is required.");
        }
        CannabisCultivationBatch current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cultivation batch #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Cultivation batch id must be positive.");
        }
    }

    private static void requireBatch(CannabisCultivationBatch batch) {
        if (batch == null) {
            throw new IllegalArgumentException("Cultivation batch is required.");
        }
        batch.validate();
    }
}
