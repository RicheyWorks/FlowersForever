package com.flowerfarm.repository;

import com.flowerfarm.model.FarmOperationSettings;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Spring Data adapter for the farm operation settings persistence boundary. */
@Repository
public class JpaFarmOperationSettingsRepository implements FarmOperationSettingsRepository {

    private final FarmOperationSettingsJpaRepository jpa;

    public JpaFarmOperationSettingsRepository(FarmOperationSettingsJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<FarmOperationSettings> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<FarmOperationSettings> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public FarmOperationSettings save(FarmOperationSettings settings) {
        return jpa.saveAndFlush(settings);
    }
}
