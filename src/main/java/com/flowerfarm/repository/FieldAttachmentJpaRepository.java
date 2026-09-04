package com.flowerfarm.repository;

import com.flowerfarm.model.FieldAttachment;
import com.flowerfarm.model.FieldAttachmentTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Database metadata for immutable field photos. */
public interface FieldAttachmentJpaRepository extends JpaRepository<FieldAttachment, Long> {

    List<FieldAttachment> findByTargetTypeAndTargetRecordIdOrderByCapturedAtDescIdDesc(
            FieldAttachmentTargetType targetType, Long targetRecordId);

    boolean existsByTargetTypeAndTargetRecordId(
            FieldAttachmentTargetType targetType, Long targetRecordId);
}
