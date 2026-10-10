package com.flowerfarm.repository;

import com.flowerfarm.model.FarmDataImportReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FarmDataImportReceiptJpaRepository
        extends JpaRepository<FarmDataImportReceipt, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT receipt FROM FarmDataImportReceipt receipt
            WHERE receipt.sourceSystem = :sourceSystem
              AND receipt.recordType = :recordType
              AND receipt.sourceKey = :sourceKey
            """)
    Optional<FarmDataImportReceipt> findForUpdate(
            @Param("sourceSystem") String sourceSystem,
            @Param("recordType") String recordType,
            @Param("sourceKey") String sourceKey);

    @Query("""
            SELECT receipt FROM FarmDataImportReceipt receipt
            WHERE (:sourceSystem IS NULL OR receipt.sourceSystem = :sourceSystem)
              AND (:recordType IS NULL OR receipt.recordType = :recordType)
            ORDER BY receipt.id ASC
            """)
    Page<FarmDataImportReceipt> findFiltered(
            @Param("sourceSystem") String sourceSystem,
            @Param("recordType") String recordType,
            Pageable pageable);
}
