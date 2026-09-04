package com.flowerfarm.repository;

import com.flowerfarm.model.RobotStagedPlan;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RobotStagedPlanJpaRepository
        extends JpaRepository<RobotStagedPlan, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT plan FROM RobotStagedPlan plan WHERE plan.robotKey = :robotKey")
    Optional<RobotStagedPlan> findForUpdate(@Param("robotKey") String robotKey);
}
