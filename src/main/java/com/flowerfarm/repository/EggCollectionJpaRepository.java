package com.flowerfarm.repository;

import com.flowerfarm.model.EggCollection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EggCollectionJpaRepository extends JpaRepository<EggCollection, Long> {

    List<EggCollection> findAllByOrderByCollectionDateDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from EggCollection entry where entry.id = :id")
    Optional<EggCollection> findByIdForUpdate(@Param("id") Long id);
}
