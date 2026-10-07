package com.flowerfarm.service;

import com.flowerfarm.model.MilkCollection;
import com.flowerfarm.repository.MilkCollectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and edits milking session notes without changing stock or orders. */
@Service
public class MilkCollectionService {

    private final MilkCollectionRepository repository;

    public MilkCollectionService(MilkCollectionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<MilkCollection> list() {
        return repository.findAll().stream().map(MilkCollection::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<MilkCollection> find(long id) {
        requireId(id);
        return repository.findById(id).map(MilkCollection::snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<MilkCollection> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(MilkCollection::snapshot);
    }

    @Transactional
    public MilkCollection create(MilkCollection incoming) {
        requireCollection(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New milk collections cannot have an id.");
        }
        MilkCollection draft = new MilkCollection(incoming.getCollectionDate(),
                incoming.getGroupName(), incoming.getSessionName(),
                incoming.getTankLocation(), incoming.getAmount(), incoming.getUnit(),
                incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public MilkCollection update(long id, MilkCollection incoming) {
        requireId(id);
        requireCollection(incoming);
        if (incoming.getId() != null && id != incoming.getId()) {
            throw new IllegalArgumentException("Milk collection id does not match the edit target.");
        }
        MilkCollection current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Milk collection #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Milk collection id must be positive.");
        }
    }

    private static void requireCollection(MilkCollection collection) {
        if (collection == null) {
            throw new IllegalArgumentException("Milk collection is required.");
        }
        collection.validate();
    }
}
