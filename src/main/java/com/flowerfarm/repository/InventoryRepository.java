package com.flowerfarm.repository;

import com.flowerfarm.model.Item;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for flower-farm inventory.
 *
 * <p>Production uses a JPA/H2 implementation; unit tests inject an in-memory
 * implementation so they stay hermetic and fast.
 */
public interface InventoryRepository {

    List<Item> findAllOrdered();

    Optional<Item> findById(Long id);

    long count();

    /** Persists a new or existing item; returns the managed instance (with id). */
    Item save(Item item);

    List<Item> saveAll(List<Item> items);

    void deleteById(Long id);

    void deleteAll();

    /**
     * Case-insensitive substring match on name or category.
     * Empty / null query → empty list.
     */
    List<Item> search(String query);
}
