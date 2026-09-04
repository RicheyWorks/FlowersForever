package com.flowerfarm.repository;

import com.flowerfarm.model.RobotControlLease;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RobotControlLeaseJpaRepository
        extends JpaRepository<RobotControlLease, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lease FROM RobotControlLease lease WHERE lease.robotKey = :robotKey")
    Optional<RobotControlLease> findForUpdate(@Param("robotKey") String robotKey);
}
