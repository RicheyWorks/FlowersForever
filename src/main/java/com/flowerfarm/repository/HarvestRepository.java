package com.flowerfarm.repository;

import com.flowerfarm.model.HarvestEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence boundary required by harvest application services. */
public interface HarvestRepository {

    List<HarvestEntry> findAllNewestFirst();

    List<HarvestEntry> findBetween(LocalDate from, LocalDate to);

    List<HarvestEntry> searchByCrop(String cropName);

    Optional<HarvestEntry> findById(Long id);

    Optional<HarvestEntry> findByIdForUpdate(Long id);

    Optional<HarvestEntry> findByLotCodeIgnoreCase(String lotCode);

    HarvestEntry save(HarvestEntry entry);

    void deleteById(Long id);
}
