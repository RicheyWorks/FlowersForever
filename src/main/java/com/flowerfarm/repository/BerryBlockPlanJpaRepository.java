package com.flowerfarm.repository;

import com.flowerfarm.model.BerryBlockPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BerryBlockPlanJpaRepository extends JpaRepository<BerryBlockPlan, Long> {

    List<BerryBlockPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from BerryBlockPlan plan where plan.id = :id")
    Optional<BerryBlockPlan> findByIdForUpdate(@Param("id") Long id);
}
