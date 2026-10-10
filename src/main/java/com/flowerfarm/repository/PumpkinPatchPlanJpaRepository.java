package com.flowerfarm.repository;

import com.flowerfarm.model.PumpkinPatchPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PumpkinPatchPlanJpaRepository
        extends JpaRepository<PumpkinPatchPlan, Long> {

    List<PumpkinPatchPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from PumpkinPatchPlan plan where plan.id = :id")
    Optional<PumpkinPatchPlan> findByIdForUpdate(@Param("id") Long id);
}
