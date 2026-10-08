package com.flowerfarm.service;

import com.flowerfarm.model.HerdGroup;
import com.flowerfarm.repository.HerdGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates current livestock group planning snapshots. */
@Service
public class HerdGroupService {

    private final HerdGroupRepository repository;

    public HerdGroupService(HerdGroupRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<HerdGroup> list() {
        return repository.findAll().stream().map(HerdGroup::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<HerdGroup> find(long id) {
        requireId(id);
        return repository.findById(id).map(HerdGroup::snapshot);
    }

    @Transactional(readOnly = true)
    public Optional<HerdGroup> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(HerdGroup::snapshot);
    }

    @Transactional
    public HerdGroup create(HerdGroup incoming) {
        requireGroup(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New herd groups cannot have an id.");
        }
        HerdGroup draft = new HerdGroup(incoming.getGroupName(),
                incoming.getSpecies(), incoming.getHeadCount(),
                incoming.getLocation(), incoming.getPurpose(),
                incoming.getNextCheckDate(), incoming.getNotes());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public HerdGroup update(long id, HerdGroup incoming) {
        requireId(id);
        requireGroup(incoming);
        if (incoming.getId() != null && id != incoming.getId()) {
            throw new IllegalArgumentException("Herd group id does not match the edit target.");
        }
        HerdGroup current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Herd group #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Herd group id must be positive.");
        }
    }

    private static void requireGroup(HerdGroup group) {
        if (group == null) {
            throw new IllegalArgumentException("Herd group is required.");
        }
        group.validate();
    }
}
