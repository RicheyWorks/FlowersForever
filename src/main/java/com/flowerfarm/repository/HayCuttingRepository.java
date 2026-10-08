package com.flowerfarm.repository;

import com.flowerfarm.model.HayCutting;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for local hay cutting plans. */
public interface HayCuttingRepository {

    List<HayCutting> findAll();

    Optional<HayCutting> findById(Long id);

    Optional<HayCutting> findByIdForUpdate(Long id);

    HayCutting save(HayCutting cutting);
}
