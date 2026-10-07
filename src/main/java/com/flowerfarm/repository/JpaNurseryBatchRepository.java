package com.flowerfarm.repository;

import com.flowerfarm.model.NurseryBatch;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local nursery growing plans. */
@Repository
public class JpaNurseryBatchRepository implements NurseryBatchRepository {

    private final NurseryBatchJpaRepository jpa;

    public JpaNurseryBatchRepository(NurseryBatchJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<NurseryBatch> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<NurseryBatch> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<NurseryBatch> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public NurseryBatch save(NurseryBatch batch) {
        return jpa.saveAndFlush(batch);
    }
}
