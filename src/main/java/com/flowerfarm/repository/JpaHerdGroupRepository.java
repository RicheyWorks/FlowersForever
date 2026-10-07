package com.flowerfarm.repository;

import com.flowerfarm.model.HerdGroup;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local livestock group plans. */
@Repository
public class JpaHerdGroupRepository implements HerdGroupRepository {

    private final HerdGroupJpaRepository jpa;

    public JpaHerdGroupRepository(HerdGroupJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<HerdGroup> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<HerdGroup> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<HerdGroup> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public HerdGroup save(HerdGroup group) {
        return jpa.saveAndFlush(group);
    }
}
