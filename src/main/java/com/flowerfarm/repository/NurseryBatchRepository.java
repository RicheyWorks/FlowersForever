package com.flowerfarm.repository;

import com.flowerfarm.model.NurseryBatch;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local nursery growing plans. */
public interface NurseryBatchRepository {

    List<NurseryBatch> findAll();

    Optional<NurseryBatch> findById(Long id);

    Optional<NurseryBatch> findByIdForUpdate(Long id);

    NurseryBatch save(NurseryBatch batch);
}
