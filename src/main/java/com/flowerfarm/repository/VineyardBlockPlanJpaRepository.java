package com.flowerfarm.repository;

import com.flowerfarm.model.VineyardBlockPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VineyardBlockPlanJpaRepository extends JpaRepository<VineyardBlockPlan, Long> {

    List<VineyardBlockPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from VineyardBlockPlan plan where plan.id = :id")
    Optional<VineyardBlockPlan> findByIdForUpdate(@Param("id") Long id);
}
