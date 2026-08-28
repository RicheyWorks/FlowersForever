package com.flowerfarm.repository;

import com.flowerfarm.model.SeasonPlanEntry;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for the seasonal planning persistence port. */
@Repository
public class JpaSeasonPlanRepository implements SeasonPlanRepository {

    private final SeasonPlanJpaRepository jpa;

    public JpaSeasonPlanRepository(SeasonPlanJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<SeasonPlanEntry> findBySeasonYear(int seasonYear) {
        return jpa.findBySeasonYearOrderBySowDateAscBedOrFieldAscCropNameAsc(seasonYear);
    }

    @Override
    public List<Integer> findDistinctSeasonYears() {
        return jpa.findDistinctSeasonYears();
    }

    @Override
    public Optional<SeasonPlanEntry> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public SeasonPlanEntry save(SeasonPlanEntry entry) {
        return jpa.save(entry);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
