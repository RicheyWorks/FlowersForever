package com.flowerfarm.repository;

import com.flowerfarm.model.NurseryBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NurseryBatchJpaRepository extends JpaRepository<NurseryBatch, Long> {

    List<NurseryBatch> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from NurseryBatch batch where batch.id = :id")
    Optional<NurseryBatch> findByIdForUpdate(@Param("id") Long id);
}
