package com.flowerfarm.repository;

import com.flowerfarm.model.HiveCheck;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local apiary hive checks. */
@Repository
public class JpaHiveCheckRepository implements HiveCheckRepository {

    private final HiveCheckJpaRepository jpa;

    public JpaHiveCheckRepository(HiveCheckJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<HiveCheck> findAll() {
        return jpa.findAllByOrderByCheckDateDescIdDesc();
    }

    @Override
    public Optional<HiveCheck> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<HiveCheck> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public HiveCheck save(HiveCheck check) {
        return jpa.saveAndFlush(check);
    }
}
