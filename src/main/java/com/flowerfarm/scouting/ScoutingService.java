package com.flowerfarm.scouting;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.service.SeasonPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;

/** Scouting findings and lab samples, linked to the season plan where possible. */
@Service
public class ScoutingService {

    /** A season plan block offered as a link target. */
    public record PlantingChoice(Long id, String crop, String variety, String bed, int seasonYear) {
        @Override
        public String toString() {
            return seasonYear + " · " + bed + " · " + crop + (variety == null || variety.isBlank() ? ""
                    : " (" + variety + ")");
        }
    }

    /** What the panel shows. */
    public record Board(List<Scouting.Finding> findings, List<Scouting.LabSample> samples, int overdueDays,
                        long overdueCount, LocalDate today) {

        public boolean overdue(Scouting.LabSample sample) {
            return Scouting.overdue(sample, today, overdueDays);
        }

        public String headline() {
            return findings.size() + (findings.size() == 1 ? " finding, " : " findings, ") + samples.size()
                    + (samples.size() == 1 ? " lab sample. " : " lab samples. ")
                    + Scouting.overdueHeadline(overdueCount, overdueDays);
        }
    }

    private final ScoutingStore store;
    private final IntFunction<List<SeasonPlanEntry>> plantings;
    private final FarmProfile farmProfile;
    private final Clock clock;

    @Autowired
    public ScoutingService(SeasonPlanService seasonPlans, FarmProfile farmProfile) {
        this(new ScoutingStore(Path.of(System.getProperty("flowerfarm.data-dir", "data")),
                        Clock.system(farmProfile.zoneId())),
                seasonPlans::listForSeason, farmProfile, Clock.system(farmProfile.zoneId()));
    }

    ScoutingService(ScoutingStore store, IntFunction<List<SeasonPlanEntry>> plantings, FarmProfile farmProfile,
                    Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.plantings = Objects.requireNonNull(plantings, "plantings");
        this.farmProfile = Objects.requireNonNull(farmProfile, "farmProfile");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Board board() throws IOException {
        ScoutingStore.Log log = store.load();
        LocalDate today = today();
        List<Scouting.Finding> findings = new ArrayList<>(log.findings());
        findings.sort(Scouting.NEWEST_FINDING_FIRST);
        List<Scouting.LabSample> samples = new ArrayList<>(log.samples());
        samples.sort(Scouting.sampleOrder(today, log.overdueDays()));
        long overdue = samples.stream().filter(sample -> Scouting.overdue(sample, today, log.overdueDays())).count();
        return new Board(List.copyOf(findings), List.copyOf(samples), log.overdueDays(), overdue, today);
    }

    /** Recovery message from the last load (e.g. damaged file replaced by the last good copy), or empty. */
    public String takeNotice() {
        return store.takeNotice();
    }

    /** This season's and last season's planting blocks, for linking a finding. */
    public List<PlantingChoice> plantingChoices() {
        int year = today().getYear();
        List<PlantingChoice> choices = new ArrayList<>();
        for (int season : new int[] {year, year - 1}) {
            for (SeasonPlanEntry entry : plantings.apply(season)) {
                choices.add(new PlantingChoice(entry.getId(), entry.getCropName(), entry.getVariety(),
                        entry.getBedOrField(), entry.getSeasonYear()));
            }
        }
        return choices;
    }

    /** Adds a new finding (blank id) or replaces the one with the same id. Returns what was saved. */
    public Scouting.Finding saveFinding(Scouting.Finding finding) throws IOException {
        Objects.requireNonNull(finding, "finding");
        List<String> problems = Scouting.problems(finding, today());
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        ScoutingStore.Log log = store.load();
        Scouting.Finding saved = finding.id() == null || finding.id().isBlank()
                ? finding.withId(Scouting.nextId("F", finding.date(),
                        log.findings().stream().map(Scouting.Finding::id).toList()))
                : finding;
        List<Scouting.Finding> findings = ScoutingStore.replace(log.findings(),
                existing -> existing.id().equals(saved.id()), saved);
        store.save(new ScoutingStore.Log(log.version(), log.overdueDays(), findings, log.samples()));
        return saved;
    }

    public Scouting.LabSample saveSample(Scouting.LabSample sample) throws IOException {
        Objects.requireNonNull(sample, "sample");
        List<String> problems = new ArrayList<>(Scouting.problems(sample, today()));
        ScoutingStore.Log log = store.load();
        if (!sample.findingId().isEmpty() && log.finding(sample.findingId()).isEmpty()) {
            problems.add("The linked scouting finding no longer exists.");
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        LabDirectory.Lab lab = LabDirectory.byId(sample.labId()).orElse(null);
        Scouting.LabSample named = lab == null || !sample.labName().isEmpty() ? sample
                : new Scouting.LabSample(sample.id(), sample.findingId(), sample.soilBlock(), sample.type(),
                        sample.labId(), lab.name(), sample.collected(), sample.shipped(), sample.tracking(),
                        sample.status(), sample.cost(), sample.resultSummary(), sample.reportPath(),
                        sample.resultsDate());
        Scouting.LabSample saved = named.id() == null || named.id().isBlank()
                ? named.withId(Scouting.nextId("S", named.collected(),
                        log.samples().stream().map(Scouting.LabSample::id).toList()))
                : named;
        List<Scouting.LabSample> samples = ScoutingStore.replace(log.samples(),
                existing -> existing.id().equals(saved.id()), saved);
        store.save(new ScoutingStore.Log(log.version(), log.overdueDays(), log.findings(), samples));
        return saved;
    }

    /** Deletes a finding that no sample points to. */
    public void deleteFinding(String id) throws IOException {
        ScoutingStore.Log log = store.load();
        if (log.samples().stream().anyMatch(sample -> sample.findingId().equals(id))) {
            throw new IllegalArgumentException("This finding has lab samples linked to it. Delete or relink the "
                    + "samples first.");
        }
        List<Scouting.Finding> findings = log.findings().stream().filter(finding -> !finding.id().equals(id))
                .toList();
        store.save(new ScoutingStore.Log(log.version(), log.overdueDays(), findings, log.samples()));
    }

    public void saveOverdueDays(int days) throws IOException {
        ScoutingStore.Log log = store.load();
        store.save(new ScoutingStore.Log(log.version(), Scouting.clampDays(days), log.findings(), log.samples()));
    }

    public Optional<Scouting.Finding> finding(String id) throws IOException {
        return store.load().finding(id);
    }

    /** Printable HTML to send in the box with the sample. */
    public String submissionSheet(String sampleId) throws IOException {
        ScoutingStore.Log log = store.load();
        Scouting.LabSample sample = log.sample(sampleId)
                .orElseThrow(() -> new IllegalArgumentException("That lab sample no longer exists."));
        Scouting.Finding finding = sample.findingId().isEmpty() ? null : log.finding(sample.findingId()).orElse(null);
        SampleSubmissionSheet.Grower grower = new SampleSubmissionSheet.Grower(farmProfile.getName(),
                farmProfile.getLocation(), farmProfile.getPhone(), farmProfile.getEmail());
        return SampleSubmissionSheet.html(sample, finding, grower, today());
    }
}
