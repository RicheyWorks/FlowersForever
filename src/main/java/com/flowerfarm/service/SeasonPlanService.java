package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.repository.HarvestRepository;
import com.flowerfarm.repository.SeasonPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Transactional seasonal planning workflows and dashboard-ready summaries. */
@Service
public class SeasonPlanService {

    private static final int MIN_HARVEST_GAP_DAYS = 7;

    private final SeasonPlanRepository repository;
    private final HarvestRepository harvestRepository;
    private final SyncHistoryService syncHistoryService;
    private final Clock clock;

    @Autowired
    public SeasonPlanService(SeasonPlanRepository repository,
                             HarvestRepository harvestRepository,
                             SyncHistoryService syncHistoryService) {
        this(repository, harvestRepository, syncHistoryService, Clock.systemDefaultZone());
    }

    SeasonPlanService(SeasonPlanRepository repository,
                      HarvestRepository harvestRepository,
                      SyncHistoryService syncHistoryService, Clock clock) {
        this.repository = repository;
        this.harvestRepository = harvestRepository;
        this.syncHistoryService = syncHistoryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SeasonPlanEntry> listForSeason(int seasonYear) {
        requireYear(seasonYear);
        return List.copyOf(repository.findBySeasonYear(seasonYear));
    }

    @Transactional(readOnly = true)
    public List<Integer> availableSeasons() {
        LinkedHashSet<Integer> years = new LinkedHashSet<>();
        years.add(LocalDate.now(clock).getYear());
        years.addAll(repository.findDistinctSeasonYears());
        return List.copyOf(years);
    }

    @Transactional(readOnly = true)
    public SeasonSummary summarize(int seasonYear) {
        List<SeasonPlanEntry> entries = listForSeason(seasonYear);
        return summarizeEntries(entries);
    }

    /**
     * Connects recorded stem harvests to one best matching crop/bed block and
     * derives warnings without double-counting a harvest across overlapping plans.
     */
    @Transactional(readOnly = true)
    public SeasonAnalysis analyze(int seasonYear) {
        requireYear(seasonYear);
        List<SeasonPlanEntry> entries = List.copyOf(repository.findBySeasonYear(seasonYear));
        SeasonSummary summary = summarizeEntries(entries);
        if (entries.isEmpty()) {
            return new SeasonAnalysis(entries, summary, List.of(), List.of(), List.of(),
                    0, 0, 0);
        }
        LocalDate seasonStart = earlier(LocalDate.of(seasonYear, 1, 1),
                summary.firstHarvest());
        LocalDate seasonEnd = later(LocalDate.of(seasonYear, 12, 31),
                summary.lastHarvest());
        List<HarvestEntry> harvests = harvestRepository.findBetween(seasonStart, seasonEnd);
        Allocation allocation = allocateHarvests(entries, harvests);
        List<PlanPerformance> performance = entries.stream()
                .map(entry -> performance(entry,
                        allocation.actualByPlan().getOrDefault(entry, 0d)))
                .toList();
        double totalActual = performance.stream()
                .mapToDouble(PlanPerformance::actualStems).sum();
        return new SeasonAnalysis(entries, summary, performance,
                findBedConflicts(entries), findHarvestGaps(entries), totalActual,
                allocation.unmatchedStems(), allocation.unmatchedHarvestCount());
    }

    private SeasonSummary summarizeEntries(List<SeasonPlanEntry> entries) {
        Set<String> beds = new LinkedHashSet<>();
        Set<String> crops = new LinkedHashSet<>();
        double bedFeet = 0;
        long expectedStems = 0;
        LocalDate firstHarvest = null;
        LocalDate lastHarvest = null;
        int activeBlocks = 0;
        for (SeasonPlanEntry entry : entries) {
            beds.add(entry.getBedOrField().toLowerCase(java.util.Locale.ROOT));
            crops.add(entry.getCropName().toLowerCase(java.util.Locale.ROOT));
            bedFeet += entry.getBedFeet();
            expectedStems += entry.getExpectedStems();
            firstHarvest = earlier(firstHarvest, entry.getHarvestStart());
            lastHarvest = later(lastHarvest, entry.getHarvestEnd());
            if (entry.getStatus() != SeasonPlanStatus.COMPLETE) {
                activeBlocks++;
            }
        }
        return new SeasonSummary(entries.size(), activeBlocks, beds.size(), crops.size(),
                bedFeet, expectedStems, firstHarvest, lastHarvest);
    }

    private Allocation allocateHarvests(List<SeasonPlanEntry> entries,
                                        List<HarvestEntry> harvests) {
        Map<SeasonPlanEntry, Double> actualByPlan = new IdentityHashMap<>();
        double unmatchedStems = 0;
        int unmatchedHarvestCount = 0;
        for (HarvestEntry harvest : harvests == null ? List.<HarvestEntry>of() : harvests) {
            if (!isStemUnit(harvest.getUnit())) {
                continue;
            }
            List<SeasonPlanEntry> candidates = entries.stream()
                    .filter(entry -> matchesCropAndBed(entry, harvest))
                    .filter(entry -> within(harvest.getHarvestDate(),
                            entry.getHarvestStart(), entry.getHarvestEnd()))
                    .sorted(Comparator
                            .comparingLong((SeasonPlanEntry entry) -> Math.abs(
                                    ChronoUnit.DAYS.between(
                                            entry.getHarvestStart(), harvest.getHarvestDate())))
                            .thenComparing(SeasonPlanService::stableId))
                    .toList();
            if (candidates.isEmpty()) {
                unmatchedStems += harvest.getQuantity();
                unmatchedHarvestCount++;
            } else {
                SeasonPlanEntry best = candidates.get(0);
                actualByPlan.merge(best, harvest.getQuantity(), Double::sum);
            }
        }
        return new Allocation(actualByPlan, unmatchedStems, unmatchedHarvestCount);
    }

    private PlanPerformance performance(SeasonPlanEntry entry, double actualStems) {
        LocalDate today = LocalDate.now(clock);
        double expectedToDate;
        PlanPace pace;
        if (today.isBefore(entry.getHarvestStart())) {
            expectedToDate = 0;
            pace = PlanPace.NOT_STARTED;
        } else {
            LocalDate through = today.isAfter(entry.getHarvestEnd())
                    ? entry.getHarvestEnd() : today;
            long windowDays = ChronoUnit.DAYS.between(
                    entry.getHarvestStart(), entry.getHarvestEnd()) + 1;
            long elapsedDays = ChronoUnit.DAYS.between(entry.getHarvestStart(), through) + 1;
            expectedToDate = entry.getExpectedStems() * elapsedDays / (double) windowDays;
            double paceRatio = expectedToDate <= 0 ? 1 : actualStems / expectedToDate;
            if (paceRatio >= 1.10) {
                pace = PlanPace.AHEAD;
            } else if (paceRatio < 0.80) {
                pace = PlanPace.BEHIND;
            } else {
                pace = PlanPace.ON_TRACK;
            }
        }
        double completion = entry.getExpectedStems() == 0 ? 0
                : actualStems * 100d / entry.getExpectedStems();
        return new PlanPerformance(entry, actualStems, expectedToDate,
                actualStems - expectedToDate, completion, pace);
    }

    private static List<BedConflict> findBedConflicts(List<SeasonPlanEntry> entries) {
        List<BedConflict> conflicts = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < entries.size(); firstIndex++) {
            SeasonPlanEntry first = entries.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < entries.size(); secondIndex++) {
                SeasonPlanEntry second = entries.get(secondIndex);
                if (!normalized(first.getBedOrField()).equals(normalized(second.getBedOrField()))) {
                    continue;
                }
                LocalDate overlapStart = later(occupancyStart(first), occupancyStart(second));
                LocalDate overlapEnd = earlier(first.getHarvestEnd(), second.getHarvestEnd());
                if (!overlapEnd.isBefore(overlapStart)) {
                    conflicts.add(new BedConflict(first, second, overlapStart, overlapEnd));
                }
            }
        }
        return List.copyOf(conflicts);
    }

    private static List<HarvestGap> findHarvestGaps(List<SeasonPlanEntry> entries) {
        List<SeasonPlanEntry> byHarvest = entries.stream()
                .sorted(Comparator.comparing(SeasonPlanEntry::getHarvestStart)
                        .thenComparing(SeasonPlanEntry::getHarvestEnd))
                .toList();
        if (byHarvest.size() < 2) {
            return List.of();
        }
        List<HarvestGap> gaps = new ArrayList<>();
        LocalDate coveredThrough = byHarvest.get(0).getHarvestEnd();
        for (int index = 1; index < byHarvest.size(); index++) {
            SeasonPlanEntry next = byHarvest.get(index);
            long gapDays = ChronoUnit.DAYS.between(
                    coveredThrough.plusDays(1), next.getHarvestStart());
            if (gapDays >= MIN_HARVEST_GAP_DAYS) {
                gaps.add(new HarvestGap(coveredThrough.plusDays(1),
                        next.getHarvestStart().minusDays(1), (int) gapDays));
            }
            if (next.getHarvestEnd().isAfter(coveredThrough)) {
                coveredThrough = next.getHarvestEnd();
            }
        }
        return List.copyOf(gaps);
    }

    private static boolean matchesCropAndBed(SeasonPlanEntry plan, HarvestEntry harvest) {
        return normalized(plan.getCropName()).equals(normalized(harvest.getCropName()))
                && normalized(plan.getBedOrField()).equals(normalized(harvest.getBedOrField()));
    }

    private static boolean isStemUnit(String unit) {
        String normalized = normalized(unit);
        return "stem".equals(normalized) || "stems".equals(normalized);
    }

    private static boolean within(LocalDate date, LocalDate from, LocalDate to) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private static LocalDate occupancyStart(SeasonPlanEntry entry) {
        return entry.getTransplantDate() == null
                ? entry.getSowDate() : entry.getTransplantDate();
    }

    private static long stableId(SeasonPlanEntry entry) {
        return entry.getId() == null ? Long.MAX_VALUE : entry.getId();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    @Transactional
    public SeasonPlanEntry create(SeasonPlanEntry entry) {
        requireEntry(entry);
        entry.setId(null);
        entry.validateSchedule();
        entry.initializeTimestamps(Instant.now(clock));
        SeasonPlanEntry saved = repository.save(entry);
        audit("SEASON_PLAN_CREATE", saved, "Planting block planned");
        return saved;
    }

    @Transactional
    public SeasonPlanEntry update(Long id, SeasonPlanEntry incoming) {
        requireEntry(incoming);
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Season plan id must be positive.");
        }
        incoming.validateSchedule();
        SeasonPlanEntry existing = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No season plan entry with id=" + id));
        existing.copyBusinessFieldsFrom(incoming);
        existing.touch(Instant.now(clock));
        SeasonPlanEntry saved = repository.save(existing);
        audit("SEASON_PLAN_UPDATE", saved, "Planting block updated");
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Season plan id must be positive.");
        }
        SeasonPlanEntry existing = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No season plan entry with id=" + id));
        repository.deleteById(id);
        audit("SEASON_PLAN_DELETE", existing, "Planting block removed");
    }

    private void audit(String operation, SeasonPlanEntry entry, String message) {
        syncHistoryService.record(
                "season-planner", operation, true,
                message + ": " + entry.getCropName() + " in " + entry.getBedOrField(),
                "seasonPlanId=" + entry.getId() + ", season=" + entry.getSeasonYear()
                        + ", expectedStems=" + entry.getExpectedStems(), 1);
    }

    private static LocalDate earlier(LocalDate current, LocalDate candidate) {
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private static LocalDate later(LocalDate current, LocalDate candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static void requireEntry(SeasonPlanEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Season plan entry is required.");
        }
    }

    private static void requireYear(int seasonYear) {
        if (seasonYear < 2000 || seasonYear > 2200) {
            throw new IllegalArgumentException("Season year must be between 2000 and 2200.");
        }
    }

    public record SeasonSummary(int plantingBlocks, int activeBlocks, int bedCount,
                                int cropCount, double bedFeet, long expectedStems,
                                LocalDate firstHarvest, LocalDate lastHarvest) {
    }

    public record SeasonAnalysis(List<SeasonPlanEntry> entries, SeasonSummary summary,
                                 List<PlanPerformance> performance,
                                 List<BedConflict> bedConflicts,
                                 List<HarvestGap> harvestGaps,
                                 double actualStems, double unmatchedStems,
                                 int unmatchedHarvestCount) {
        public SeasonAnalysis {
            entries = List.copyOf(entries);
            performance = List.copyOf(performance);
            bedConflicts = List.copyOf(bedConflicts);
            harvestGaps = List.copyOf(harvestGaps);
        }
    }

    public record PlanPerformance(SeasonPlanEntry entry, double actualStems,
                                  double expectedToDate, double varianceToDate,
                                  double completionPercent, PlanPace pace) {
    }

    public record BedConflict(SeasonPlanEntry first, SeasonPlanEntry second,
                              LocalDate overlapStart, LocalDate overlapEnd) {
    }

    public record HarvestGap(LocalDate from, LocalDate to, int days) {
    }

    public enum PlanPace {
        NOT_STARTED("Not started"),
        AHEAD("Ahead"),
        ON_TRACK("On track"),
        BEHIND("Behind");

        private final String label;

        PlanPace(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private record Allocation(Map<SeasonPlanEntry, Double> actualByPlan,
                              double unmatchedStems, int unmatchedHarvestCount) {
    }
}
