package com.flowerfarm.repository;

import com.flowerfarm.model.HarvestEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HarvestJpaRepository extends JpaRepository<HarvestEntry, Long> {

    List<HarvestEntry> findAllByOrderByHarvestDateDescIdDesc();

    List<HarvestEntry> findByHarvestDateBetweenOrderByHarvestDateDescIdDesc(
            LocalDate from, LocalDate to);

    List<HarvestEntry> findByCropNameContainingIgnoreCaseOrderByHarvestDateDesc(
            String cropName);

    /** Serializes inventory-correcting edits and deletes for one harvest row. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HarvestEntry h where h.id = :id")
    Optional<HarvestEntry> findByIdForUpdate(@Param("id") Long id);
}
