package com.flowerfarm.repository;

import com.flowerfarm.model.SyncHistoryEntry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Spring Data adapter for the sync-history persistence port. */
@Repository
public class JpaSyncHistoryRepository implements SyncHistoryRepository {

    private final SyncHistoryJpaRepository jpa;

    public JpaSyncHistoryRepository(SyncHistoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public SyncHistoryEntry save(SyncHistoryEntry entry) {
        return jpa.save(entry);
    }

    @Override
    public List<SyncHistoryEntry> findRecent(int limit) {
        return jpa.findAllByOrderByOccurredAtDesc(PageRequest.of(0, limit));
    }

    @Override
    public List<SyncHistoryEntry> findRecentForConnector(String connectorName, int limit) {
        return jpa.findByConnectorNameIgnoreCaseOrderByOccurredAtDesc(
                connectorName, PageRequest.of(0, limit));
    }

    @Override
    public void deleteAll() {
        jpa.deleteAllInBatch();
    }
}
