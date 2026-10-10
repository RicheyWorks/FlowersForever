package com.flowerfarm.service;

import com.flowerfarm.model.EggCollection;
import com.flowerfarm.repository.EggCollectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and edits dated egg collections without changing stock or orders. */
@Service
public class EggCollectionService {

    private final EggCollectionRepository repository;

    public EggCollectionService(EggCollectionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<EggCollection> list() {
        return repository.findAll().stream().map(EggCollection::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<EggCollection> find(long id) {
        requireId(id);
        return repository.findById(id).map(EggCollection::snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<EggCollection> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(EggCollection::snapshot);
    }

    @Transactional
    public EggCollection create(EggCollection incoming) {
        requireCollection(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New egg collections cannot have an id.");
        }
        EggCollection draft = new EggCollection(incoming.getCollectionDate(),
                incoming.getFlockName(), incoming.getLocation(),
                incoming.getCollectedCount(), incoming.getUnusableCount(),
                incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public EggCollection update(long id, EggCollection incoming) {
        requireId(id);
        requireCollection(incoming);
        if (incoming.getId() != null && id != incoming.getId()) {
            throw new IllegalArgumentException("Egg collection id does not match the edit target.");
        }
        EggCollection current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Egg collection #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Egg collection id must be positive.");
        }
    }

    private static void requireCollection(EggCollection collection) {
        if (collection == null) {
            throw new IllegalArgumentException("Egg collection is required.");
        }
        collection.validate();
    }
}
