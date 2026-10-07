package com.flowerfarm.repository;

import com.flowerfarm.model.FarmOperationSettings;

import java.util.Optional;

/** Persistence boundary for the installation's farm specialization settings. */
public interface FarmOperationSettingsRepository {

    Optional<FarmOperationSettings> findById(Long id);

    Optional<FarmOperationSettings> findByIdForUpdate(Long id);

    FarmOperationSettings save(FarmOperationSettings settings);
}
