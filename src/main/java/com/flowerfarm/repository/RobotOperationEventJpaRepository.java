package com.flowerfarm.repository;

import com.flowerfarm.model.RobotOperationEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RobotOperationEventJpaRepository
        extends JpaRepository<RobotOperationEvent, Long> {

    Optional<RobotOperationEvent>
            findFirstByPluginIdAndOperationIdOrderByIdDesc(
                    String pluginId, String operationId);

    Optional<RobotOperationEvent>
            findFirstByPluginIdAndRequestIdOrderByIdDesc(
                    String pluginId, String requestId);

    @Query("""
            SELECT event FROM RobotOperationEvent event
            WHERE event.pluginId = :pluginId
              AND event.id IN (
                  SELECT MAX(candidate.id) FROM RobotOperationEvent candidate
                  WHERE candidate.pluginId = :pluginId
                  GROUP BY candidate.operationId)
            ORDER BY event.id DESC
            """)
    List<RobotOperationEvent> findRecentOperations(
            @Param("pluginId") String pluginId, Pageable pageable);

    List<RobotOperationEvent> findByPluginIdAndIdGreaterThanOrderByIdAsc(
            String pluginId, Long afterEventId, Pageable pageable);
}
