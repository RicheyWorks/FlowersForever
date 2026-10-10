package com.flowerfarm.repository;

import com.flowerfarm.model.HiveCheck;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HiveCheckJpaRepository extends JpaRepository<HiveCheck, Long> {

    List<HiveCheck> findAllByOrderByCheckDateDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select hiveCheck from HiveCheck hiveCheck where hiveCheck.id = :id")
    Optional<HiveCheck> findByIdForUpdate(@Param("id") Long id);
}
