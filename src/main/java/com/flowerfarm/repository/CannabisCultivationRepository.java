package com.flowerfarm.repository;

import com.flowerfarm.model.CannabisCultivationBatch;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local cultivation planning batches. */
public interface CannabisCultivationRepository {

    List<CannabisCultivationBatch> findAll();

    Optional<CannabisCultivationBatch> findById(Long id);

    Optional<CannabisCultivationBatch> findByIdForUpdate(Long id);

    CannabisCultivationBatch save(CannabisCultivationBatch batch);
}
