package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.repository.HarvestRepository;
import com.flowerfarm.repository.SeasonPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Season Planner service")
class SeasonPlanServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-28T17:00:00Z"), ZoneOffset.UTC);

    @Mock SeasonPlanRepository repository;
    @Mock HarvestRepository harvestRepository;
    @Mock SyncHistoryService syncHistoryService;

    private SeasonPlanService service;

    @BeforeEach
    void setUp() {
        service = new SeasonPlanService(
                repository, harvestRepository, syncHistoryService, CLOCK);
    }

    @Test
    @DisplayName("summarizes beds, crops, capacity, stems, and the harvest window")
    void summarizesSeason() {
        SeasonPlanEntry rose = entry("Nootka Rose", "Bed A", 80, 640,
                LocalDate.of(2026, 6, 20), LocalDate.of(2026, 9, 10));
        SeasonPlanEntry dahlia = entry("Dahlia", "Bed B", 50, 400,
                LocalDate.of(2026, 7, 5), LocalDate.of(2026, 10, 2));
        SeasonPlanEntry secondRose = entry("Nootka Rose", "Bed A", 20, 120,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 10));
        secondRose.setStatus(SeasonPlanStatus.COMPLETE);
        when(repository.findBySeasonYear(2026))
                .thenReturn(List.of(rose, dahlia, secondRose));

        SeasonPlanService.SeasonSummary summary = service.summarize(2026);

        assertThat(summary.plantingBlocks()).isEqualTo(3);
        assertThat(summary.activeBlocks()).isEqualTo(2);
        assertThat(summary.bedCount()).isEqualTo(2);
        assertThat(summary.cropCount()).isEqualTo(2);
        assertThat(summary.bedFeet()).isEqualTo(150);
        assertThat(summary.expectedStems()).isEqualTo(1_160);
        assertThat(summary.firstHarvest()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(summary.lastHarvest()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    @DisplayName("creates a planting block with timestamps and an activity record")
    void createsPlan() {
        SeasonPlanEntry entry = entry("Nootka Rose", "Bed A", 80, 640,
                LocalDate.of(2026, 6, 20), LocalDate.of(2026, 9, 10));
        when(repository.save(any())).thenAnswer(invocation -> {
            SeasonPlanEntry saved = invocation.getArgument(0);
            saved.setId(17L);
            return saved;
        });

        SeasonPlanEntry saved = service.create(entry);

        assertThat(saved.getId()).isEqualTo(17L);
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.now(CLOCK));
        verify(syncHistoryService).record(
                eq("season-planner"), eq("SEASON_PLAN_CREATE"), eq(true),
                contains("Nootka Rose in Bed A"), contains("seasonPlanId=17"), eq(1));
    }

    @Test
    @DisplayName("always includes the current season in the year selector")
    void includesCurrentSeason() {
        when(repository.findDistinctSeasonYears()).thenReturn(List.of(2025, 2024));

        assertThat(service.availableSeasons()).containsExactly(2026, 2025, 2024);
    }

    @Test
    @DisplayName("allocates each stem harvest to one best crop and bed block")
    void analyzesActualProductionWithoutDoubleCounting() {
        SeasonPlanEntry early = entry("Nootka Rose", "Bed A", 80, 640,
                LocalDate.of(2026, 6, 20), LocalDate.of(2026, 8, 20));
        early.setId(1L);
        SeasonPlanEntry succession = entry("Nootka Rose", "Bed A", 20, 120,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 10));
        succession.setId(2L);
        when(repository.findBySeasonYear(2026)).thenReturn(List.of(early, succession));
        when(harvestRepository.findBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of(
                        harvest(LocalDate.of(2026, 7, 1), "Nootka Rose", 100,
                                "stems", "Bed A"),
                        harvest(LocalDate.of(2026, 8, 15), "Nootka Rose", 60,
                                "stem", "Bed A"),
                        harvest(LocalDate.of(2026, 8, 16), "Nootka Rose", 3,
                                "bunches", "Bed A"),
                        harvest(LocalDate.of(2026, 8, 17), "Other crop", 20,
                                "stems", "Bed A")));

        SeasonPlanService.SeasonAnalysis analysis = service.analyze(2026);

        assertThat(analysis.actualStems()).isEqualTo(160);
        assertThat(analysis.unmatchedStems()).isEqualTo(20);
        assertThat(analysis.unmatchedHarvestCount()).isEqualTo(1);
        assertThat(analysis.performance()).extracting(
                SeasonPlanService.PlanPerformance::actualStems)
                .containsExactly(100d, 60d);
        assertThat(analysis.performance()).extracting(
                SeasonPlanService.PlanPerformance::pace)
                .containsExactly(SeasonPlanService.PlanPace.BEHIND,
                        SeasonPlanService.PlanPace.AHEAD);
        assertThat(analysis.weeklyProduction())
                .extracting(SeasonPlanService.WeeklyProduction::actualStems)
                .filteredOn(value -> value > 0)
                .containsExactly(100d, 60d);
        assertThat(analysis.weeklyProduction().stream()
                .mapToDouble(SeasonPlanService.WeeklyProduction::plannedStems).sum())
                .isCloseTo(760d, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(analysis.weeklyProduction().stream()
                .mapToDouble(SeasonPlanService.WeeklyProduction::actualStems).sum())
                .isEqualTo(160d);
        assertThat(analysis.bedConflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.first()).isSameAs(early);
            assertThat(conflict.second()).isSameAs(succession);
            assertThat(conflict.overlapStart()).isEqualTo(LocalDate.of(2026, 4, 12));
        });
    }

    @Test
    @DisplayName("finds a week-or-long gap between expected harvest windows")
    void findsHarvestGaps() {
        SeasonPlanEntry first = entry("Rose", "A", 20, 100,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        SeasonPlanEntry next = entry("Dahlia", "B", 20, 100,
                LocalDate.of(2026, 7, 10), LocalDate.of(2026, 8, 20));
        when(repository.findBySeasonYear(2026)).thenReturn(List.of(first, next));
        when(harvestRepository.findBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .thenReturn(List.of());

        SeasonPlanService.SeasonAnalysis analysis = service.analyze(2026);

        assertThat(analysis.harvestGaps()).singleElement().satisfies(gap -> {
            assertThat(gap.from()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(gap.to()).isEqualTo(LocalDate.of(2026, 7, 9));
            assertThat(gap.days()).isEqualTo(9);
        });
    }

    private static SeasonPlanEntry entry(String crop, String bed, double bedFeet,
                                         int stems, LocalDate harvestStart,
                                         LocalDate harvestEnd) {
        return new SeasonPlanEntry(
                2026, crop, "", bed, LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 12), harvestStart, harvestEnd,
                bedFeet, stems, SeasonPlanStatus.PLANTED, "");
    }

    private static HarvestEntry harvest(LocalDate date, String crop, double quantity,
                                         String unit, String bed) {
        return new HarvestEntry(date, crop, quantity, unit, bed, "");
    }
}
