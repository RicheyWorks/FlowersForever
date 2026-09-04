package com.flowerfarm.repository;

import com.flowerfarm.model.FloralRecipe;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FloralRecipeJpaRepository extends JpaRepository<FloralRecipe, Long> {

    List<FloralRecipe> findAllByOrderByDesignTypeAscNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from FloralRecipe r where r.id = :id")
    Optional<FloralRecipe> findByIdForUpdate(@Param("id") Long id);
}
