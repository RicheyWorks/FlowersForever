package com.flowerfarm.repository;

import com.flowerfarm.model.WorkerActionReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence for retry-safe worker field-operation results. */
public interface WorkerActionReceiptJpaRepository
        extends JpaRepository<WorkerActionReceipt, Long> {

    Optional<WorkerActionReceipt> findByOperationKeyHash(String operationKeyHash);
}
