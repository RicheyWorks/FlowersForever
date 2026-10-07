package com.flowerfarm.repository;

import com.flowerfarm.model.HayCutting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HayCuttingJpaRepository extends JpaRepository<HayCutting, Long> {

    List<HayCutting> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cutting from HayCutting cutting where cutting.id = :id")
    Optional<HayCutting> findByIdForUpdate(@Param("id") Long id);
}
