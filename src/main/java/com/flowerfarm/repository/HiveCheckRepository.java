package com.flowerfarm.repository;

import com.flowerfarm.model.HiveCheck;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local apiary hive checks. */
public interface HiveCheckRepository {

    List<HiveCheck> findAll();

    Optional<HiveCheck> findById(Long id);

    Optional<HiveCheck> findByIdForUpdate(Long id);

    HiveCheck save(HiveCheck check);
}
