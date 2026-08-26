package com.flowerfarm.repository;

import com.flowerfarm.model.SyncHistoryEntry;

import java.util.List;

/** Persistence boundary required by sync-history application services. */
public interface SyncHistoryRepository {

    SyncHistoryEntry save(SyncHistoryEntry entry);

    List<SyncHistoryEntry> findRecent(int limit);

    List<SyncHistoryEntry> findRecentForConnector(String connectorName, int limit);

    void deleteAll();
}
