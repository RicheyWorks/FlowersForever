package com.flowerfarm.repository;

import com.flowerfarm.model.HayCutting;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Spring Data adapter for local hay cutting plans. */
@Repository
public class JpaHayCuttingRepository implements HayCuttingRepository {

    private final HayCuttingJpaRepository jpa;

    public JpaHayCuttingRepository(HayCuttingJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<HayCutting> findAll() {
        return jpa.findAllByOrderByIdDesc();
    }

    @Override
    public Optional<HayCutting> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<HayCutting> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public HayCutting save(HayCutting cutting) {
        return jpa.saveAndFlush(cutting);
    }
}
