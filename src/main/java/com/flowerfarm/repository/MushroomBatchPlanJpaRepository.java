package com.flowerfarm.repository;

import com.flowerfarm.model.MushroomBatchPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MushroomBatchPlanJpaRepository
        extends JpaRepository<MushroomBatchPlan, Long> {

    List<MushroomBatchPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from MushroomBatchPlan plan where plan.id = :id")
    Optional<MushroomBatchPlan> findByIdForUpdate(@Param("id") Long id);
}
