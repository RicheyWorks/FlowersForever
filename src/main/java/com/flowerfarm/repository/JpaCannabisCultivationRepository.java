package com.flowerfarm.repository;

import com.flowerfarm.model.CannabisCultivationBatch;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for cultivation planning. */
@Repository
public class JpaCannabisCultivationRepository implements CannabisCultivationRepository {

    private final CannabisCultivationJpaRepository jpa;

    public JpaCannabisCultivationRepository(CannabisCultivationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<CannabisCultivationBatch> findAll() {
        return jpa.findAllByOrderByStartDateDescIdDesc();
    }

    @Override
    public Optional<CannabisCultivationBatch> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<CannabisCultivationBatch> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public CannabisCultivationBatch save(CannabisCultivationBatch batch) {
        return jpa.saveAndFlush(batch);
    }
}
