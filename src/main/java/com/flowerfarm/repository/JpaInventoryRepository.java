package com.flowerfarm.repository;

import com.flowerfarm.model.Item;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Production inventory store backed by Spring Data JPA (H2 file by default).
 *
 * <p>Active for every profile except {@code test-inmemory}, which unit tests
 * can use if they boot a full context with an alternate bean.
 */
@Repository
@Profile("!test-inmemory")
@Transactional
public class JpaInventoryRepository implements InventoryRepository {

    private final ItemJpaRepository jpa;

    public JpaInventoryRepository(ItemJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> findAllOrdered() {
        return jpa.findAllByOrderByIdAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Item> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpa.count();
    }

    @Override
    public Item save(Item item) {
        return jpa.save(item);
    }

    @Override
    public List<Item> saveAll(List<Item> items) {
        return jpa.saveAll(items);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }

    @Override
    public void deleteAll() {
        jpa.deleteAllInBatch();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return jpa.searchByNameOrCategory(query.trim());
    }
}
