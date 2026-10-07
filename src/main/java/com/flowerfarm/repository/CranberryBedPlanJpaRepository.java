package com.flowerfarm.repository;

import com.flowerfarm.model.CranberryBedPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CranberryBedPlanJpaRepository
        extends JpaRepository<CranberryBedPlan, Long> {

    List<CranberryBedPlan> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from CranberryBedPlan plan where plan.id = :id")
    Optional<CranberryBedPlan> findByIdForUpdate(@Param("id") Long id);
}
