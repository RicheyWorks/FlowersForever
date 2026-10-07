package com.flowerfarm.repository;

import com.flowerfarm.model.FarmOperationSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FarmOperationSettingsJpaRepository
        extends JpaRepository<FarmOperationSettings, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settings from FarmOperationSettings settings where settings.id = :id")
    Optional<FarmOperationSettings> findByIdForUpdate(@Param("id") Long id);
}
