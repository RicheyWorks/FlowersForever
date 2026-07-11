package com.flowerfarm.repository;

import com.flowerfarm.model.HarvestEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HarvestJpaRepository extends JpaRepository<HarvestEntry, Long> {

    List<HarvestEntry> findAllByOrderByHarvestDateDescIdDesc();

    List<HarvestEntry> findByHarvestDateBetweenOrderByHarvestDateDescIdDesc(
            LocalDate from, LocalDate to);

    List<HarvestEntry> findByCropNameContainingIgnoreCaseOrderByHarvestDateDesc(
            String cropName);
}
