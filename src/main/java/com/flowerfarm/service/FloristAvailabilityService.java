package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.service.ProductionStudioService.AvailabilityLine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Builds a Florlist-friendly live + planned availability sheet for buyers. */
@Service
public class FloristAvailabilityService {

    private final InventoryService inventoryService;
    private final ProductionStudioService productionStudioService;
    private final FarmProfile farmProfile;

    public FloristAvailabilityService(InventoryService inventoryService,
                                      ProductionStudioService productionStudioService,
                                      FarmProfile farmProfile) {
        this.inventoryService = inventoryService;
        this.productionStudioService = productionStudioService;
        this.farmProfile = farmProfile;
    }

    @Transactional(readOnly = true)
    public FloristAvailabilityExport.Sheet buildForCurrentWeek() {
        return build(farmProfile.today());
    }

    @Transactional(readOnly = true)
    public FloristAvailabilityExport.Sheet build(LocalDate asOf) {
        LocalDate day = asOf == null ? farmProfile.today() : asOf;
        LocalDate weekStart = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        InventoryService.PriceListReport live = inventoryService.buildPriceListReport(true);
        List<AvailabilityLine> planned = productionStudioService.availability(weekStart);
        List<FloristAvailabilityExport.Line> lines = FloristAvailabilityExport.merge(
                live.lines(), planned, weekStart, weekEnd);
        return new FloristAvailabilityExport.Sheet(
                farmProfile.getName(),
                contactLine(),
                day,
                weekStart,
                weekEnd,
                lines);
    }

    private String contactLine() {
        List<String> parts = new ArrayList<>();
        if (farmProfile.getPhone() != null && !farmProfile.getPhone().isBlank()) {
            parts.add(farmProfile.getPhone().trim());
        }
        if (farmProfile.getEmail() != null && !farmProfile.getEmail().isBlank()) {
            parts.add(farmProfile.getEmail().trim());
        }
        String location = farmProfile.locationLabel();
        if (location != null && !location.isBlank()) {
            parts.add(location.trim());
        }
        return String.join(" · ", parts);
    }
}
