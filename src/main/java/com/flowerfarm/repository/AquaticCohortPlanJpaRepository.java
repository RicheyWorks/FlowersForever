package com.flowerfarm.repository;

import com.flowerfarm.model.AquaticCohortPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AquaticCohortPlanJpaRepository
        extends JpaRepository<AquaticCohortPlan, Long> {

    List<AquaticCohortPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from AquaticCohortPlan plan where plan.id = :id")
    Optional<AquaticCohortPlan> findByIdForUpdate(@Param("id") Long id);
}
