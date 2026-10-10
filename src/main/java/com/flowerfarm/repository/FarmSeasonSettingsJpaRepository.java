package com.flowerfarm.repository;

import com.flowerfarm.model.FarmSeasonSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FarmSeasonSettingsJpaRepository
        extends JpaRepository<FarmSeasonSettings, Long> {

    Optional<FarmSeasonSettings> findBySeasonYear(int seasonYear);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FarmSeasonSettings s where s.seasonYear = :seasonYear")
    Optional<FarmSeasonSettings> findBySeasonYearForUpdate(
            @Param("seasonYear") int seasonYear);
}
