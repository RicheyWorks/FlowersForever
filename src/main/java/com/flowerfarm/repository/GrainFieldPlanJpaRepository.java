package com.flowerfarm.repository;

import com.flowerfarm.model.GrainFieldPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GrainFieldPlanJpaRepository extends JpaRepository<GrainFieldPlan, Long> {

    List<GrainFieldPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from GrainFieldPlan plan where plan.id = :id")
    Optional<GrainFieldPlan> findByIdForUpdate(@Param("id") Long id);
}
