package com.flowerfarm.repository;

import com.flowerfarm.model.EggCollection;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for egg collection records. */
@Repository
public class JpaEggCollectionRepository implements EggCollectionRepository {

    private final EggCollectionJpaRepository jpa;

    public JpaEggCollectionRepository(EggCollectionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<EggCollection> findAll() {
        return jpa.findAllByOrderByCollectionDateDescIdDesc();
    }

    @Override
    public Optional<EggCollection> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<EggCollection> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public EggCollection save(EggCollection collection) {
        return jpa.saveAndFlush(collection);
    }
}
