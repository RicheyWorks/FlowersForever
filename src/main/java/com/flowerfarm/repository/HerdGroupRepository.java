package com.flowerfarm.repository;

import com.flowerfarm.model.HerdGroup;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local livestock group plans. */
public interface HerdGroupRepository {

    List<HerdGroup> findAll();

    Optional<HerdGroup> findById(Long id);

    Optional<HerdGroup> findByIdForUpdate(Long id);

    HerdGroup save(HerdGroup group);
}
