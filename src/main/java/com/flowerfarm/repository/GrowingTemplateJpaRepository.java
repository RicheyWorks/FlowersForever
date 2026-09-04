package com.flowerfarm.repository;

import com.flowerfarm.model.GrowingTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GrowingTemplateJpaRepository extends JpaRepository<GrowingTemplate, Long> {

    List<GrowingTemplate> findAllByOrderByActiveDescCropNameAscNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from GrowingTemplate t where t.id = :id")
    Optional<GrowingTemplate> findByIdForUpdate(@Param("id") Long id);
}
