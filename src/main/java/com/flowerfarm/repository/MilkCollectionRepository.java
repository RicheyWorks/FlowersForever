package com.flowerfarm.repository;

import com.flowerfarm.model.MilkCollection;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for milk collection records. */
public interface MilkCollectionRepository {

    List<MilkCollection> findAll();

    Optional<MilkCollection> findById(Long id);

    Optional<MilkCollection> findByIdForUpdate(Long id);

    MilkCollection save(MilkCollection collection);
}
