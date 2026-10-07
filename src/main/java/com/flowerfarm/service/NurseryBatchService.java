package com.flowerfarm.service;

import com.flowerfarm.model.NurseryBatch;
import com.flowerfarm.model.NurseryBatchStage;
import com.flowerfarm.repository.NurseryBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local plans for live-plant growing batches. */
@Service
public class NurseryBatchService {

    private final NurseryBatchRepository repository;

    public NurseryBatchService(NurseryBatchRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NurseryBatch> list() {
        return repository.findAll().stream().map(NurseryBatch::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<NurseryBatch> find(long id) {
        requireId(id);
        return repository.findById(id).map(NurseryBatch::snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<NurseryBatch> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(NurseryBatch::snapshot);
    }

    @Transactional
    public NurseryBatch create(NurseryBatch incoming) {
        requireBatch(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New nursery batches cannot have an id.");
        }
        NurseryBatch draft = new NurseryBatch(incoming.getBatchName(),
                incoming.getPlantName(), incoming.getCultivar(), incoming.getForm(),
                incoming.getGrowingArea(), incoming.getStartedDate(),
                incoming.getPlannedReadyDate(), incoming.getStartingCount(),
                incoming.getChannel(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public NurseryBatch update(long id, NurseryBatch incoming) {
        requireId(id);
        requireBatch(incoming);
        if (incoming.getId() != null && id != incoming.getId()) {
            throw new IllegalArgumentException("Nursery batch id does not match the edit target.");
        }
        NurseryBatch current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nursery batch #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Changes only the stage of the locked saved batch; other planning edits are preserved. */
    @Transactional
    public NurseryBatch updateStage(long id, NurseryBatchStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Nursery batch stage is required.");
        }
        NurseryBatch current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nursery batch #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Nursery batch id must be positive.");
        }
    }

    private static void requireBatch(NurseryBatch batch) {
        if (batch == null) {
            throw new IllegalArgumentException("Nursery batch is required.");
        }
        batch.validate();
    }
}
