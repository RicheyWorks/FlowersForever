package com.flowerfarm.repository;

import com.flowerfarm.model.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory {@link InventoryRepository} for unit tests and demos.
 * Not a Spring bean by default — construct explicitly in tests.
 */
public class InMemoryInventoryRepository implements InventoryRepository {

    private final List<Item> items = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public synchronized List<Item> findAllOrdered() {
        return items.stream()
                .sorted((a, b) -> Long.compare(
                        a.getId() == null ? 0L : a.getId(),
                        b.getId() == null ? 0L : b.getId()))
                .map(this::copy)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public synchronized Optional<Item> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return items.stream()
                .filter(i -> id.equals(i.getId()))
                .findFirst()
                .map(this::copy);
    }

    @Override
    public synchronized long count() {
        return items.size();
    }

    @Override
    public synchronized Item save(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        if (item.getId() == null) {
            item.setId(seq.getAndIncrement());
            items.add(copy(item));
            return copy(item);
        }
        for (int i = 0; i < items.size(); i++) {
            if (item.getId().equals(items.get(i).getId())) {
                items.set(i, copy(item));
                return copy(item);
            }
        }
        items.add(copy(item));
        return copy(item);
    }

    @Override
    public synchronized List<Item> saveAll(List<Item> toSave) {
        List<Item> saved = new ArrayList<>();
        for (Item item : toSave) {
            saved.add(save(item));
        }
        return saved;
    }

    @Override
    public synchronized void deleteById(Long id) {
        items.removeIf(i -> id != null && id.equals(i.getId()));
    }

    @Override
    public synchronized void deleteAll() {
        items.clear();
        seq.set(1);
    }

    @Override
    public synchronized List<Item> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String q = query.trim().toLowerCase();
        return findAllOrdered().stream()
                .filter(i -> i.getName().toLowerCase().contains(q)
                        || i.getCategory().toLowerCase().contains(q))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Item copy(Item src) {
        Item copy = new Item(
                src.getName(),
                src.getCategory(),
                src.getPrice(),
                src.getUnit(),
                src.getCost(),
                src.getQuantity(),
                src.getNotes()
        );
        copy.setId(src.getId());
        return copy;
    }
}
