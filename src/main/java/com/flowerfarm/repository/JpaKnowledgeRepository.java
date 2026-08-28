package com.flowerfarm.repository;

import com.flowerfarm.model.KnowledgeEntry;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for the Farm Knowledge Log persistence port. */
@Repository
public class JpaKnowledgeRepository implements KnowledgeRepository {

    private final KnowledgeJpaRepository jpa;

    public JpaKnowledgeRepository(KnowledgeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<KnowledgeEntry> findAllNewestFirst() {
        return jpa.findAllByOrderByObservedOnDescIdDesc();
    }

    @Override
    public Optional<KnowledgeEntry> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<KnowledgeEntry> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public KnowledgeEntry save(KnowledgeEntry entry) {
        return jpa.save(entry);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
