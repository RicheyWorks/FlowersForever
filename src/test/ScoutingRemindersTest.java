package com.flowerfarm.scouting;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.service.GrowerReminder;
import com.flowerfarm.service.GrowerReminders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Scouting items in Today's reminders")
class ScoutingRemindersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    @TempDir
    Path data;

    private static Scouting.Finding finding(String id, LocalDate date, Scouting.Severity severity) {
        return new Scouting.Finding(id, date, null, "Bed 4", "Dahlia", "", Scouting.Category.FUNGAL, "Botrytis",
                "Gray mold", severity, 30.0, "", "");
    }

    private static Scouting.LabSample shipped(String id, String findingId, LocalDate shipped) {
        return new Scouting.LabSample(id, findingId, findingId.isEmpty() ? "North field" : "",
                Scouting.SampleType.PLANT_DISEASE, "wsu-puyallup", "WSU Puyallup Plant & Insect Diagnostic Lab",
                shipped.minusDays(1), shipped, "1Z", Scouting.SampleStatus.SHIPPED, null, "", "", null);
    }

    private static ScoutingService.Board board(List<Scouting.Finding> findings, List<Scouting.LabSample> samples) {
        long overdue = samples.stream().filter(s -> Scouting.overdue(s, TODAY, 14)).count();
        return new ScoutingService.Board(findings, samples, 14, overdue, TODAY);
    }

    @Test
    void overdueSamplesBecomeRemindersThatOpenScouting() {
        List<GrowerReminder> items = ScoutingReminders.from(board(List.of(),
                List.of(shipped("S-1", "", TODAY.minusDays(20)), shipped("S-2", "", TODAY.minusDays(3)))));
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.kind()).isEqualTo(GrowerReminder.Kind.SCOUTING);
            assertThat(item.title()).isEqualTo("Lab results overdue · S-1");
            assertThat(item.detail()).contains("WSU Puyallup").contains("shipped 2026-09-17").contains("20 days")
                    .contains("sample ID S-1");
            assertThat(item.targetTab()).isEqualTo(Scouting.TITLE);
        });
    }

    @Test
    void onlyRecentHighOrSevereFindingsAreShown() {
        List<Scouting.Finding> findings = List.of(
                finding("F-new", TODAY, Scouting.Severity.SEVERE),
                finding("F-week", TODAY.minusDays(7), Scouting.Severity.HIGH),
                finding("F-old", TODAY.minusDays(8), Scouting.Severity.SEVERE),
                finding("F-mild", TODAY, Scouting.Severity.MODERATE),
                finding("F-future", TODAY.plusDays(1), Scouting.Severity.SEVERE));
        List<GrowerReminder> items = ScoutingReminders.from(board(findings,
                List.of(shipped("S-9", "F-week", TODAY.minusDays(1)))));
        assertThat(items).extracting(GrowerReminder::title)
                .containsExactly("Scouting · Severe Botrytis · Bed 4", "Scouting · High Botrytis · Bed 4");
        assertThat(items.get(0).detail()).contains("30% of plants affected").contains("No lab sample yet");
        assertThat(items.get(1).detail()).contains("Lab sample S-9: shipped");
    }

    @Test
    void eachKindIsCappedSoScoutingCannotCrowdOutFieldWork() {
        List<Scouting.Finding> findings = new ArrayList<>();
        List<Scouting.LabSample> samples = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            findings.add(finding("F-" + i, TODAY, Scouting.Severity.HIGH));
            samples.add(shipped("S-" + i, "", TODAY.minusDays(30 + i)));
        }
        List<GrowerReminder> items = ScoutingReminders.from(board(findings, samples));
        assertThat(items).hasSize(2 * ScoutingReminders.MAX_EACH);
    }

    @Test
    void reminderListPlacesScoutingAfterCoolerAndBeforeKnowledge() {
        GrowerReminders.Digest digest = GrowerReminders.assemble(TODAY, List.of(), List.of(), null, true,
                List.of(), 10, List.of(), List.of(ScoutingReminders.unreadable()));
        assertThat(digest.items()).extracting(GrowerReminder::kind)
                .containsExactly(GrowerReminder.Kind.HARVEST, GrowerReminder.Kind.SCOUTING);
        assertThat(GrowerReminders.assemble(TODAY, List.of(), List.of(), null, false, List.of(), 10, List.of(),
                null).isEmpty()).isTrue();
    }

    @Test
    void serviceRemindersNeverThrowEvenWhenTheLogIsDamaged() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T20:00:00Z"), ZoneId.of("America/Los_Angeles"));
        ScoutingStore store = new ScoutingStore(data, clock);
        ScoutingService service = new ScoutingService(store, season -> List.of(), new FarmProfile(), clock);
        assertThat(service.reminders(TODAY)).isEmpty();

        service.saveFinding(finding(null, TODAY, Scouting.Severity.SEVERE));
        assertThat(service.reminders(TODAY)).singleElement()
                .extracting(GrowerReminder::title).asString().startsWith("Scouting · Severe Botrytis");

        Files.writeString(store.file(), "{ not json");
        assertThat(service.reminders(TODAY)).containsExactly(ScoutingReminders.unreadable());
        assertThat(Files.readString(store.file())).isEqualTo("{ not json");
    }

    @Test
    void recentAndSeriousHandlesMissingValues() {
        assertThat(ScoutingReminders.recentAndSerious(null, TODAY)).isFalse();
        assertThat(ScoutingReminders.recentAndSerious(finding("F", null, Scouting.Severity.SEVERE), TODAY)).isFalse();
        assertThat(ScoutingReminders.recentAndSerious(finding("F", TODAY, Scouting.Severity.SEVERE), null)).isFalse();
    }
}
