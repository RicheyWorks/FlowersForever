package com.flowerfarm.repository;

import com.flowerfarm.model.CannabisCultivationBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CannabisCultivationJpaRepository
        extends JpaRepository<CannabisCultivationBatch, Long> {

    List<CannabisCultivationBatch> findAllByOrderByStartDateDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from CannabisCultivationBatch batch where batch.id = :id")
    Optional<CannabisCultivationBatch> findByIdForUpdate(@Param("id") Long id);
}
