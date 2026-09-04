package com.flowerfarm.repository;

import com.flowerfarm.model.HarvestEntry;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Spring Data adapter for the harvest persistence port. */
@Repository
public class JpaHarvestRepository implements HarvestRepository {

    private final HarvestJpaRepository jpa;

    public JpaHarvestRepository(HarvestJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<HarvestEntry> findAllNewestFirst() {
        return jpa.findAllByOrderByHarvestDateDescIdDesc();
    }

    @Override
    public List<HarvestEntry> findBetween(LocalDate from, LocalDate to) {
        return jpa.findByHarvestDateBetweenOrderByHarvestDateDescIdDesc(from, to);
    }

    @Override
    public List<HarvestEntry> searchByCrop(String cropName) {
        return jpa.findByCropNameContainingIgnoreCaseOrderByHarvestDateDesc(cropName);
    }

    @Override
    public Optional<HarvestEntry> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<HarvestEntry> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public Optional<HarvestEntry> findByLotCodeIgnoreCase(String lotCode) {
        return jpa.findByLotCodeIgnoreCase(lotCode);
    }

    @Override
    public HarvestEntry save(HarvestEntry entry) {
        return jpa.save(entry);
    }

    @Override
    public void deleteById(Long id) {
        jpa.deleteById(id);
    }
}
