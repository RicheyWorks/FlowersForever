package com.flowerfarm.repository;

import com.flowerfarm.model.ChristmasTreeBlockPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChristmasTreeBlockPlanJpaRepository
        extends JpaRepository<ChristmasTreeBlockPlan, Long> {

    List<ChristmasTreeBlockPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from ChristmasTreeBlockPlan plan where plan.id = :id")
    Optional<ChristmasTreeBlockPlan> findByIdForUpdate(@Param("id") Long id);
}
