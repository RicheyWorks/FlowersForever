package com.flowerfarm.service;

import com.flowerfarm.model.HiveCheck;
import com.flowerfarm.model.HiveCheckStatus;
import com.flowerfarm.repository.HiveCheckRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Creates and updates local apiary hive checks and their recorded status. */
@Service
public class HiveCheckService {

    private final HiveCheckRepository repository;

    public HiveCheckService(HiveCheckRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<HiveCheck> list() {
        return repository.findAll().stream().map(HiveCheck::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public Optional<HiveCheck> findById(Long id) {
        requireId(id);
        return repository.findById(id).map(HiveCheck::snapshot);
    }

    @Transactional
    public HiveCheck create(HiveCheck incoming) {
        requireCheck(incoming);
        if (incoming.getId() != null) {
            throw new IllegalArgumentException("New hive checks cannot have an id.");
        }
        HiveCheck draft = new HiveCheck(incoming.getApiaryName(), incoming.getHiveLabel(),
                incoming.getCheckDate(), incoming.getPurpose(), incoming.getStatus(),
                incoming.getNextCheckDate(), incoming.getObservations(),
                incoming.getActions());
        return repository.save(draft).snapshot();
    }

    @Transactional
    public HiveCheck update(Long id, HiveCheck incoming) {
        requireId(id);
        requireCheck(incoming);
        if (incoming.getId() != null && !id.equals(incoming.getId())) {
            throw new IllegalArgumentException("Hive check id does not match the edit target.");
        }
        HiveCheck current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hive check #" + id + " was not found."));
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }

    /** Change only the status of a locked saved check, preserving observations. */
    @Transactional
    public HiveCheck updateStatus(long id, HiveCheckStatus status) {
        requireId(id);
        if (status == null) {
            throw new IllegalArgumentException("Hive check status is required.");
        }
        HiveCheck current = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hive check #" + id + " was not found."));
        current.setStatus(status);
        return repository.save(current).snapshot();
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Hive check id must be positive.");
        }
    }

    private static void requireCheck(HiveCheck check) {
        if (check == null) {
            throw new IllegalArgumentException("Hive check is required.");
        }
        check.validate();
    }
}
