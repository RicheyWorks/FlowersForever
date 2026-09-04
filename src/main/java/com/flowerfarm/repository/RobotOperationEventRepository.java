package com.flowerfarm.repository;

import com.flowerfarm.model.RobotOperationEvent;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for append-only robot operation events. */
public interface RobotOperationEventRepository {

    RobotOperationEvent append(RobotOperationEvent event);

    Optional<RobotOperationEvent> findLatest(String pluginId, String operationId);

    Optional<RobotOperationEvent> findByRequest(String pluginId, String requestId);

    List<RobotOperationEvent> findRecentOperations(String pluginId, int limit);

    List<RobotOperationEvent> findAfter(
            String pluginId, long afterEventId, int limit);
}
