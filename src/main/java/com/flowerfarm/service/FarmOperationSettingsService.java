package com.flowerfarm.service;

import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.repository.FarmOperationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and updates the local installation's farm specialization choices. */
@Service
public class FarmOperationSettingsService {

    private final FarmOperationSettingsRepository repository;

    public FarmOperationSettingsService(FarmOperationSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public FarmOperationSettings getSettings() {
        return repository.findById(FarmOperationSettings.SINGLETON_ID)
                .map(FarmOperationSettings::snapshot)
                .orElseGet(FarmOperationSettings::new);
    }

    @Transactional
    public FarmOperationSettings saveSettings(FarmOperationSettings incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("Farm operation settings are required.");
        }
        incoming.validate();
        FarmOperationSettings current = repository
                .findByIdForUpdate(FarmOperationSettings.SINGLETON_ID)
                .orElseGet(FarmOperationSettings::new);
        current.copyBusinessFieldsFrom(incoming);
        return repository.save(current).snapshot();
    }
}
