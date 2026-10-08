package com.flowerfarm.scouting;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.service.FieldAttachmentService;
import com.flowerfarm.service.SeasonPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.nio.file.Files;
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

    /** Where photo bytes live: the farm's attachment store, so photos are deduplicated and backed up. */
    public interface PhotoContent {
        FieldAttachmentService.StoredContent store(String filename, byte[] bytes);

        byte[] read(String sha256, long byteSize);

        static PhotoContent of(FieldAttachmentService attachments) {
            Objects.requireNonNull(attachments, "attachments");
            return new PhotoContent() {
                @Override
                public FieldAttachmentService.StoredContent store(String filename, byte[] bytes) {
                    return attachments.storeContent(filename, null, bytes);
                }

                @Override
                public byte[] read(String sha256, long byteSize) {
                    return attachments.readContent(sha256, byteSize);
                }
            };
        }
    }

    private final ScoutingLogStore store;
    private final IntFunction<List<SeasonPlanEntry>> plantings;
    private final FarmProfile farmProfile;
    private final Clock clock;
    private PhotoContent photoContent;

    /** The log lives in the farm database; the old JSON file is imported once and kept as a backup. */
    @Autowired
    public ScoutingService(SeasonPlanService seasonPlans, FarmProfile farmProfile, JdbcTemplate jdbc,
                           PlatformTransactionManager transactionManager) {
        this(new DatabaseScoutingStore(jdbc, transactionManager,
                        new ScoutingStore(Path.of(System.getProperty("flowerfarm.data-dir", "data")),
                                Clock.system(farmProfile.zoneId())),
                        Clock.system(farmProfile.zoneId())),
                seasonPlans::listForSeason, farmProfile, Clock.system(farmProfile.zoneId()));
    }

    ScoutingService(ScoutingLogStore store, IntFunction<List<SeasonPlanEntry>> plantings, FarmProfile farmProfile,
                    Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.plantings = Objects.requireNonNull(plantings, "plantings");
        this.farmProfile = Objects.requireNonNull(farmProfile, "farmProfile");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Photos go through the existing attachments feature; without it, attaching explains why it can't. */
    @Autowired(required = false)
    public void setAttachments(FieldAttachmentService attachments) {
        this.photoContent = attachments == null ? null : PhotoContent.of(attachments);
    }

    void setPhotoContent(PhotoContent content) {
        this.photoContent = content;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Board board() throws IOException {
        return board(today());
    }

    /** The board as of {@code day} (used by Today's reminders). */
    public Board board(LocalDate day) throws IOException {
        ScoutingStore.Log log = store.load();
        LocalDate today = day == null ? today() : day;
        List<Scouting.Finding> findings = new ArrayList<>(log.findings());
        findings.sort(Scouting.NEWEST_FINDING_FIRST);
        List<Scouting.LabSample> samples = new ArrayList<>(log.samples());
        samples.sort(Scouting.sampleOrder(today, log.overdueDays()));
        long overdue = samples.stream().filter(sample -> Scouting.overdue(sample, today, log.overdueDays())).count();
        return new Board(List.copyOf(findings), List.copyOf(samples), log.overdueDays(), overdue, today);
    }

    /**
     * Today's-reminder items: overdue lab results and serious findings from the last week. Never throws;
     * an unreadable log becomes a single "needs attention" reminder.
     */
    public List<com.flowerfarm.service.GrowerReminder> reminders(LocalDate day) {
        try {
            return ScoutingReminders.from(board(day));
        } catch (IOException | RuntimeException unreadable) {
            return List.of(ScoutingReminders.unreadable());
        }
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
        Optional<Scouting.Finding> stored = finding.id() == null ? Optional.empty() : log.finding(finding.id());
        Scouting.Finding saved = finding.id() == null || finding.id().isBlank()
                ? finding.withId(Scouting.nextId("F", finding.date(),
                        log.findings().stream().map(Scouting.Finding::id).toList()))
                // Photos change only through attachPhoto/removePhoto, so an edit never drops them.
                : stored.map(previous -> finding.withPhotos(previous.photos())).orElse(finding);
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

    /**
     * Copies a photo into the attachment store and adds it to the finding. Attaching the same picture twice
     * keeps one entry. The original file is left where it is.
     */
    public Scouting.Photo attachPhoto(String findingId, Path file, String caption) throws IOException {
        Objects.requireNonNull(file, "file");
        PhotoContent content = requirePhotoContent();
        ScoutingStore.Log log = store.load();
        Scouting.Finding finding = log.finding(findingId)
                .orElseThrow(() -> new IllegalArgumentException("That finding no longer exists."));
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Choose a photo file (JPEG, PNG, WebP, or HEIC).");
        }
        if (Files.size(file) > FieldAttachmentService.MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("That photo is larger than 15 MB. Use a smaller copy.");
        }
        Path name = file.getFileName();
        FieldAttachmentService.StoredContent stored = content.store(name == null ? "" : name.toString(),
                Files.readAllBytes(file));
        Optional<Scouting.Photo> already = finding.photos().stream()
                .filter(photo -> photo.sha256().equals(stored.sha256())).findFirst();
        if (already.isPresent()) {
            return already.get();
        }
        Scouting.Photo photo = new Scouting.Photo(stored.sha256(), stored.mediaType(), stored.byteSize(),
                stored.originalFilename(), caption, today());
        List<Scouting.Photo> photos = new ArrayList<>(finding.photos());
        photos.add(photo);
        Scouting.Finding updated = finding.withPhotos(photos);
        List<String> problems = Scouting.problems(updated, today());
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }
        store.save(new ScoutingStore.Log(log.version(), log.overdueDays(),
                ScoutingStore.replace(log.findings(), existing -> existing.id().equals(finding.id()), updated),
                log.samples()));
        return photo;
    }

    /** Verified photo bytes; fails clearly if the stored file is missing or changed. */
    public byte[] photoBytes(Scouting.Photo photo) {
        Objects.requireNonNull(photo, "photo");
        return requirePhotoContent().read(photo.sha256(), photo.byteSize());
    }

    /**
     * Removes a photo from a finding. The stored copy stays in the attachment store (other records may share
     * it, and backups keep it), so this is reversible by attaching the same file again.
     */
    public void removePhoto(String findingId, String sha256) throws IOException {
        ScoutingStore.Log log = store.load();
        Scouting.Finding finding = log.finding(findingId)
                .orElseThrow(() -> new IllegalArgumentException("That finding no longer exists."));
        Scouting.Finding updated = finding.withPhotos(finding.photos().stream()
                .filter(photo -> !photo.sha256().equals(sha256)).toList());
        store.save(new ScoutingStore.Log(log.version(), log.overdueDays(),
                ScoutingStore.replace(log.findings(), existing -> existing.id().equals(finding.id()), updated),
                log.samples()));
    }

    private PhotoContent requirePhotoContent() {
        if (photoContent == null) {
            throw new IllegalStateException("Photo storage is not available in this mode of the app.");
        }
        return photoContent;
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
