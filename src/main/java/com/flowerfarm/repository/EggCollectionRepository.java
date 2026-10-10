package com.flowerfarm.repository;

import com.flowerfarm.model.EggCollection;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for egg collection records. */
public interface EggCollectionRepository {

    List<EggCollection> findAll();

    Optional<EggCollection> findById(Long id);

    Optional<EggCollection> findByIdForUpdate(Long id);

    EggCollection save(EggCollection collection);
}
