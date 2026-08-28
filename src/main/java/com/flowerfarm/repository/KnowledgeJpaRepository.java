package com.flowerfarm.repository;

import com.flowerfarm.model.KnowledgeEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeJpaRepository extends JpaRepository<KnowledgeEntry, Long> {

    List<KnowledgeEntry> findAllByOrderByObservedOnDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from KnowledgeEntry k where k.id = :id")
    Optional<KnowledgeEntry> findByIdForUpdate(@Param("id") Long id);
}
