package com.flowerfarm.service;

import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.repository.SeasonPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Transactional seasonal planning workflows and dashboard-ready summaries. */
@Service
public class SeasonPlanService {

    private final SeasonPlanRepository repository;
    private final SyncHistoryService syncHistoryService;
    private final Clock clock;

    @Autowired
    public SeasonPlanService(SeasonPlanRepository repository,
                             SyncHistoryService syncHistoryService) {
        this(repository, syncHistoryService, Clock.systemDefaultZone());
    }

    SeasonPlanService(SeasonPlanRepository repository,
                      SyncHistoryService syncHistoryService, Clock clock) {
        this.repository = repository;
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
}
