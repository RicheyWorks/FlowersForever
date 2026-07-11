package com.flowerfarm.repository;

import com.flowerfarm.model.SyncHistoryEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncHistoryJpaRepository extends JpaRepository<SyncHistoryEntry, Long> {

    List<SyncHistoryEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);

    List<SyncHistoryEntry> findByConnectorNameIgnoreCaseOrderByOccurredAtDesc(
            String connectorName, Pageable pageable);
}
