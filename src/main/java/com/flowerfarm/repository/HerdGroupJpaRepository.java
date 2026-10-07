package com.flowerfarm.repository;

import com.flowerfarm.model.HerdGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HerdGroupJpaRepository extends JpaRepository<HerdGroup, Long> {

    List<HerdGroup> findAllByOrderByIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select herd from HerdGroup herd where herd.id = :id")
    Optional<HerdGroup> findByIdForUpdate(@Param("id") Long id);
}
