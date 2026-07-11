package com.flowerfarm.repository;

import com.flowerfarm.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Item} entities.
 */
public interface ItemJpaRepository extends JpaRepository<Item, Long> {

    List<Item> findAllByOrderByIdAsc();

    @Query("""
            SELECT i FROM Item i
            WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(i.category) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY i.id ASC
            """)
    List<Item> searchByNameOrCategory(@Param("q") String query);
}
