package com.flowerfarm.scouting;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Scouting service: findings, samples, and submission sheets")
class ScoutingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @TempDir
    Path data;

    private ScoutingService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneId.of("America/Los_Angeles"));
        FarmProfile profile = new FarmProfile();
        profile.setName("Rose & Thorn <Farm>");
        profile.setLocation("Gig Harbor, WA");
        profile.setPhone("253-555-0100");
        profile.setEmail("grower@example.com");
        SeasonPlanEntry dahlias = entry(41L, 2026, "Dahlia", "Café au Lait", "Bed 4");
        SeasonPlanEntry oldSnaps = entry(7L, 2025, "Snapdragon", "Potomac", "Block B");
        service = new ScoutingService(new ScoutingStore(data, clock),
                season -> season == 2026 ? List.of(dahlias) : season == 2025 ? List.of(oldSnaps) : List.of(),
                profile, clock);
    }

    private static SeasonPlanEntry entry(Long id, int year, String crop, String variety, String bed) {
        SeasonPlanEntry entry = new SeasonPlanEntry(year, crop, variety, bed, LocalDate.of(year, 3, 1),
                LocalDate.of(year, 4, 15), LocalDate.of(year, 7, 1), LocalDate.of(year, 9, 30), 10.0, 10,
                SeasonPlanStatus.values()[0], "");
        entry.setId(id);
        return entry;
    }

    private Scouting.Finding newFinding(String organism) {
        return new Scouting.Finding(null, TODAY, 41L, "Bed 4", "Dahlia", "Café au Lait",
                Scouting.Category.FUNGAL, organism, "Gray fuzzy mold on spent blooms", Scouting.Severity.HIGH,
                30.0, "", "Removed infected blooms");
    }

    private Scouting.LabSample newSample(String findingId, String lab) {
        return new Scouting.LabSample(null, findingId, "", Scouting.SampleType.PLANT_DISEASE, lab, "", TODAY,
                null, "", Scouting.SampleStatus.COLLECTED, new BigDecimal("40"), "", "", null);
    }

    @Test
    void offersThisAndLastSeasonsPlantings() {
        assertThat(service.plantingChoices()).extracting(ScoutingService.PlantingChoice::id).containsExactly(41L, 7L);
        assertThat(service.plantingChoices().get(0).toString()).isEqualTo("2026 · Bed 4 · Dahlia (Café au Lait)");
    }

    @Test
    void newFindingsGetDailyIdsAndAreSaved() throws Exception {
        Scouting.Finding first = service.saveFinding(newFinding("Botrytis?"));
        Scouting.Finding second = service.saveFinding(newFinding("Powdery mildew?"));
        assertThat(first.id()).isEqualTo("F-20261007-001");
        assertThat(second.id()).isEqualTo("F-20261007-002");
        assertThat(service.board().findings()).extracting(Scouting.Finding::id)
                .containsExactly("F-20261007-002", "F-20261007-001");
        assertThat(service.finding(first.id())).contains(first);
    }

    @Test
    void editingKeepsTheIdAndReplacesTheFinding() throws Exception {
        Scouting.Finding saved = service.saveFinding(newFinding("Botrytis?"));
        Scouting.Finding edited = new Scouting.Finding(saved.id(), saved.date(), saved.plantingId(), saved.bed(),
                saved.crop(), saved.variety(), saved.category(), "Botrytis (lab confirmed)", saved.symptoms(),
                Scouting.Severity.SEVERE, 45.0, "", "Pulled plants");
        service.saveFinding(edited);
        assertThat(service.board().findings()).containsExactly(edited);
    }

    @Test
    void invalidEntriesAreRejectedWithPlainMessages() {
        Scouting.Finding bad = new Scouting.Finding(null, TODAY.plusDays(2), null, "", "", "", null, "", "", null,
                null, "", "");
        assertThatThrownBy(() -> service.saveFinding(bad)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be in the future").hasMessageContaining("Say where you found it");
        assertThatThrownBy(() -> service.saveSample(newSample("F-missing", "wsu-puyallup")))
                .hasMessageContaining("linked scouting finding no longer exists");
    }

    @Test
    void samplesTakeTheDirectoryLabNameAndCountAsOverdue() throws Exception {
        Scouting.Finding finding = service.saveFinding(newFinding("Botrytis?"));
        Scouting.LabSample sample = service.saveSample(newSample(finding.id(), "wsu-puyallup"));
        assertThat(sample.id()).isEqualTo("S-20261007-001");
        assertThat(sample.labName()).isEqualTo(LabDirectory.byId("wsu-puyallup").orElseThrow().name());

        Scouting.LabSample old = new Scouting.LabSample(null, "", "North field", Scouting.SampleType.SOIL,
                "soiltest", "", TODAY.minusDays(40), TODAY.minusDays(30), "9400", Scouting.SampleStatus.SHIPPED,
                null, "", "", null);
        service.saveSample(old);
        service.saveOverdueDays(21);
        ScoutingService.Board board = service.board();
        assertThat(board.overdueDays()).isEqualTo(21);
        assertThat(board.overdueCount()).isEqualTo(1);
        assertThat(board.samples().get(0).soilBlock()).isEqualTo("North field");
        assertThat(board.overdue(board.samples().get(0))).isTrue();
        assertThat(board.headline()).isEqualTo("1 finding, 2 lab samples. 1 lab sample has no results after 21 "
                + "days. Call or email the lab with the sample ID.");
    }

    @Test
    void findingsWithSamplesCannotBeDeleted() throws Exception {
        Scouting.Finding linked = service.saveFinding(newFinding("Botrytis?"));
        Scouting.Finding loose = service.saveFinding(newFinding("Aphids?"));
        service.saveSample(newSample(linked.id(), "pierce-mg"));
        assertThatThrownBy(() -> service.deleteFinding(linked.id())).hasMessageContaining("lab samples linked");
        service.deleteFinding(loose.id());
        assertThat(service.board().findings()).containsExactly(linked);
    }

    @Test
    void submissionSheetIsPrefilledAndEscaped() throws Exception {
        Scouting.Finding finding = service.saveFinding(new Scouting.Finding(null, TODAY, 41L, "Bed 4", "Dahlia",
                "Café au Lait", Scouting.Category.VIRAL, "<script>alert(1)</script>", "Ring spots & yellow lines",
                Scouting.Severity.MODERATE, 12.5, "", ""));
        Scouting.LabSample sample = service.saveSample(newSample(finding.id(), "wsu-puyallup"));
        String html = service.submissionSheet(sample.id());
        assertThat(html).startsWith("<!DOCTYPE html>")
                .contains("Sample ID: S-20261007-001")
                .contains("Rose &amp; Thorn &lt;Farm&gt;")
                .contains("Gig Harbor, WA").contains("253-555-0100").contains("grower@example.com")
                .contains("Café au Lait").contains("Ring spots &amp; yellow lines").contains("12.5%")
                .contains("2606 W. Pioneer")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<script>");
        assertThatThrownBy(() -> service.submissionSheet("S-nope")).hasMessageContaining("no longer exists");
    }

    @Test
    void sheetEscapingAndNumbers() {
        assertThat(SampleSubmissionSheet.esc("a\"b'c")).isEqualTo("a&quot;b&#39;c");
        assertThat(SampleSubmissionSheet.esc(null)).isEmpty();
        assertThat(SampleSubmissionSheet.trimNumber(15.0)).isEqualTo("15");
        assertThat(SampleSubmissionSheet.trimNumber(2.5)).isEqualTo("2.5");
    }
}
