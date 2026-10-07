package com.flowerfarm.repository;

import com.flowerfarm.model.MilkCollection;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for milk collection records. */
@Repository
public class JpaMilkCollectionRepository implements MilkCollectionRepository {

    private final MilkCollectionJpaRepository jpa;

    public JpaMilkCollectionRepository(MilkCollectionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<MilkCollection> findAll() {
        return jpa.findAllByOrderByCollectionDateDescIdDesc();
    }

    @Override
    public Optional<MilkCollection> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<MilkCollection> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public MilkCollection save(MilkCollection collection) {
        return jpa.saveAndFlush(collection);
    }
}
