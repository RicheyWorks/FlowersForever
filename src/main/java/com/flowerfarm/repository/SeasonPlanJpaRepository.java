package com.flowerfarm.repository;

import com.flowerfarm.model.SeasonPlanEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeasonPlanJpaRepository extends JpaRepository<SeasonPlanEntry, Long> {

    List<SeasonPlanEntry> findBySeasonYearOrderBySowDateAscBedOrFieldAscCropNameAsc(
            int seasonYear);

    @Query("select distinct p.seasonYear from SeasonPlanEntry p order by p.seasonYear desc")
    List<Integer> findDistinctSeasonYears();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SeasonPlanEntry p where p.id = :id")
    Optional<SeasonPlanEntry> findByIdForUpdate(@Param("id") Long id);
}
