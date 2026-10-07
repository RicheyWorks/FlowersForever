package com.flowerfarm.repository;

import com.flowerfarm.model.SeedFieldPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeedFieldPlanJpaRepository extends JpaRepository<SeedFieldPlan, Long> {

    List<SeedFieldPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from SeedFieldPlan plan where plan.id = :id")
    Optional<SeedFieldPlan> findByIdForUpdate(@Param("id") Long id);
}
