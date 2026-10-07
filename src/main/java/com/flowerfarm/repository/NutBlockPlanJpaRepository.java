package com.flowerfarm.repository;

import com.flowerfarm.model.NutBlockPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NutBlockPlanJpaRepository extends JpaRepository<NutBlockPlan, Long> {

    List<NutBlockPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from NutBlockPlan plan where plan.id = :id")
    Optional<NutBlockPlan> findByIdForUpdate(@Param("id") Long id);
}
