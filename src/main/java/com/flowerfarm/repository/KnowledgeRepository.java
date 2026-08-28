package com.flowerfarm.repository;

import com.flowerfarm.model.KnowledgeEntry;

import java.util.List;
import java.util.Optional;

/** Persistence boundary required by the Farm Knowledge Log. */
public interface KnowledgeRepository {

    List<KnowledgeEntry> findAllNewestFirst();

    Optional<KnowledgeEntry> findById(Long id);

    Optional<KnowledgeEntry> findByIdForUpdate(Long id);

    KnowledgeEntry save(KnowledgeEntry entry);

    void deleteById(Long id);
}
