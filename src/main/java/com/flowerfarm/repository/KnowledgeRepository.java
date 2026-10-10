package com.flowerfarm.repository;

import com.flowerfarm.model.KnowledgeEntry;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

/** Persistence boundary required by the Farm Knowledge Log. */
public interface KnowledgeRepository {

    List<KnowledgeEntry> findAllNewestFirst();

    default List<KnowledgeEntry> findRecentThrough(LocalDate through, int limit) {
        return findAllNewestFirst().stream()
                .filter(entry -> !entry.getObservedOn().isAfter(through))
                .limit(limit).toList();
    }

    Optional<KnowledgeEntry> findById(Long id);

    Optional<KnowledgeEntry> findByIdForUpdate(Long id);

    KnowledgeEntry save(KnowledgeEntry entry);

    void deleteById(Long id);
}
