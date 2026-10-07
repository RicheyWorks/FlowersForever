package com.flowerfarm.service;

import com.flowerfarm.model.HayCutting;
import com.flowerfarm.model.HayCuttingStage;
import com.flowerfarm.repository.HayCuttingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local hay cutting plans and their progress stage. */
@Service
public class HayCuttingService {

    private final HayCuttingRepository repository;

    public HayCuttingService(HayCuttingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<HayCutting> list() {
        return repository.findAll().stream().map(HayCutting::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<HayCutting> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(HayCutting::snapshot);
    }

    @Transactional
    public HayCutting create(HayCutting incoming) {
        requireCutting(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New hay cuttings cannot have an id.");
        }
        HayCutting draft = new HayCutting(incoming.getFieldName(), incoming.getForageCrop(),
                incoming.getCuttingNumber(), incoming.getAcres(), incoming.getPlannedCutDate(),
                incoming.getPlannedBaleDate(), incoming.getStage(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public HayCutting update(Long id, HayCutting incoming) {
        requireId(id);
        requireCutting(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Hay cutting id does not match the edit target.");
        }
        HayCutting current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hay cutting #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the stage of the locked saved cutting, preserving other planning edits. */
    @Transactional
    public HayCutting updateStage(long id, HayCuttingStage stage) {
        requireId(id);
        if (stage == null) {
            throw new IllegalArgumentException("Hay cutting stage is required.");
        }
        HayCutting current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hay cutting #" + id + " was not found."));
        current.setStage(stage);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Hay cutting id must be positive.");
        }
    }

    private static void requireCutting(HayCutting cutting) {
        if (cutting == null) {
            throw new IllegalArgumentException("Hay cutting is required.");
        }
        cutting.validate();
    }
}
