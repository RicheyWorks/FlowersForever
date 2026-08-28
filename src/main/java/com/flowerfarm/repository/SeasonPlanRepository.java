package com.flowerfarm.repository;

import com.flowerfarm.model.SeasonPlanEntry;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for seasonal crop and bed plans. */
public interface SeasonPlanRepository {

    List<SeasonPlanEntry> findBySeasonYear(int seasonYear);

    List<Integer> findDistinctSeasonYears();

    Optional<SeasonPlanEntry> findByIdForUpdate(Long id);

    SeasonPlanEntry save(SeasonPlanEntry entry);

    void deleteById(Long id);
}
