package com.flowerfarm.repository;

import com.flowerfarm.model.FieldAttachmentTargetType;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductionTaskJpaRepository extends JpaRepository<ProductionTask, Long> {

    List<ProductionTask> findByDueDateBetweenOrderByDueDateAscSequenceNumberAsc(
            LocalDate from, LocalDate to);

    List<ProductionTask> findByDueDateBetween(
            LocalDate from, LocalDate to, Pageable pageable);

    long countByDueDateBetween(LocalDate from, LocalDate to);

    List<ProductionTask> findByStatusAndDueDateLessThanEqual(
            ProductionTaskStatus status, LocalDate date, Pageable pageable);

    long countByStatusAndDueDateLessThanEqual(
            ProductionTaskStatus status, LocalDate date);

    List<ProductionTask> findBySeasonPlanIdOrderByDueDateAscSequenceNumberAsc(Long seasonPlanId);

    boolean existsByGrowingTemplateId(Long growingTemplateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ProductionTask t where t.id = :id")
    Optional<ProductionTask> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("delete from ProductionTask t where t.seasonPlanId = :planId "
            + "and t.generated = true and t.status = :status "
            + "and t.actualMinutes is null and t.inputCost = 0 "
            + "and (t.fieldObservation is null or t.fieldObservation = '') "
            + "and not exists (select a.id from FieldAttachment a "
            + "where a.targetType = :attachmentType and a.targetRecordId = t.id)")
    void deleteGeneratedForPlanWithStatus(@Param("planId") Long planId,
                                          @Param("status") ProductionTaskStatus status,
                                          @Param("attachmentType")
                                          FieldAttachmentTargetType attachmentType);
}
