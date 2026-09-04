package com.flowerfarm.repository;

import com.flowerfarm.model.RobotOperationEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for the append-only robot operation event port. */
@Repository
public class JpaRobotOperationEventRepository
        implements RobotOperationEventRepository {

    private final RobotOperationEventJpaRepository jpa;

    public JpaRobotOperationEventRepository(RobotOperationEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RobotOperationEvent append(RobotOperationEvent event) {
        if (event.getId() != null) {
            throw new IllegalArgumentException(
                    "Robot operation events are append-only.");
        }
        return jpa.save(event);
    }

    @Override
    public Optional<RobotOperationEvent> findLatest(
            String pluginId, String operationId) {
        return jpa.findFirstByPluginIdAndOperationIdOrderByIdDesc(
                pluginId, operationId);
    }

    @Override
    public Optional<RobotOperationEvent> findByRequest(
            String pluginId, String requestId) {
        return jpa.findFirstByPluginIdAndRequestIdOrderByIdDesc(
                pluginId, requestId);
    }

    @Override
    public List<RobotOperationEvent> findRecentOperations(
            String pluginId, int limit) {
        return jpa.findRecentOperations(pluginId, PageRequest.of(0, limit));
    }

    @Override
    public List<RobotOperationEvent> findAfter(
            String pluginId, long afterEventId, int limit) {
        return jpa.findByPluginIdAndIdGreaterThanOrderByIdAsc(
                pluginId, afterEventId, PageRequest.of(0, limit));
    }
}
