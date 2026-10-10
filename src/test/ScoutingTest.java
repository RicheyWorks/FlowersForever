package com.flowerfarm.scouting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Scouting log rules")
class ScoutingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    static Scouting.Finding finding(String id, LocalDate date) {
        return new Scouting.Finding(id, date, null, "Bed 4", "Dahlia", "Café au Lait",
                Scouting.Category.INSECT_MITE, "Thrips", "Silvery streaks on petals", Scouting.Severity.MODERATE,
                15.0, "", "Removed spent blooms");
    }

    static Scouting.LabSample sample(String id, Scouting.SampleStatus status, LocalDate collected, LocalDate shipped) {
        return new Scouting.LabSample(id, "F-20261001-001", "", Scouting.SampleType.INSECT, "wsu-puyallup",
                "WSU Puyallup Plant & Insect Diagnostic Clinic", collected, shipped, "1Z999",
                status, new BigDecimal("25"), status == Scouting.SampleStatus.RESULTS_IN ? "Western flower thrips" : "",
                "", null);
    }

    @Test
    void cleanFindingHasNoProblems() {
        assertThat(Scouting.problems(finding(null, TODAY), TODAY)).isEmpty();
    }

    @Test
    void textIsTrimmedAndDefaultsFilled() {
        Scouting.Finding raw = new Scouting.Finding(null, TODAY, null, "  Bed 2 ", null, null, null, " aphids ",
                null, null, null, null, null);
        assertThat(raw.bed()).isEqualTo("Bed 2");
        assertThat(raw.crop()).isEmpty();
        assertThat(raw.organism()).isEqualTo("aphids");
        assertThat(raw.category()).isEqualTo(Scouting.Category.UNKNOWN);
        assertThat(raw.severity()).isEqualTo(Scouting.Severity.LOW);
    }

    @Test
    void findingProblemsArePlainLanguage() {
        Scouting.Finding empty = new Scouting.Finding(null, null, null, "", "", "", null, "", "", null, 140.0, "",
                "");
        assertThat(Scouting.problems(empty, TODAY)).containsExactly(
                "Enter the date you scouted.",
                "Say where you found it: a bed or block, a crop, or a planting.",
                "Describe the symptoms or the suspected pest or disease.",
                "Plants affected must be between 0 and 100 percent.");
        assertThat(Scouting.problems(finding(null, TODAY.plusDays(1)), TODAY))
                .containsExactly("The scouting date cannot be in the future.");
        Scouting.Finding nan = new Scouting.Finding(null, TODAY, 7L, "", "", "", null, "spots", "", null,
                Double.NaN, "", "");
        assertThat(Scouting.problems(nan, TODAY)).containsExactly("Plants affected must be between 0 and 100 percent.");
        Scouting.Finding longText = new Scouting.Finding(null, TODAY, null, "Bed 1", "", "", null, "",
                "x".repeat(Scouting.MAX_TEXT + 1), null, null, "", "");
        assertThat(Scouting.problems(longText, TODAY)).singleElement().asString().startsWith("Symptoms is too long");
    }

    @Test
    void sampleProblemsCoverLinksDatesAndResults() {
        Scouting.LabSample orphan = new Scouting.LabSample(null, "", "", Scouting.SampleType.SOIL, "", "", null,
                null, "", Scouting.SampleStatus.RESULTS_IN, new BigDecimal("-1"), "", "", null);
        assertThat(Scouting.problems(orphan, TODAY)).containsExactly(
                "Link the sample to a scouting finding, or name the soil block or water source.",
                "Enter the date the sample was collected.",
                "Enter the ship (or drop-off) date for a sample that has gone to the lab.",
                "Add a short result summary or attach the lab report.",
                "Cost cannot be negative.",
                "Choose the lab or clinic the sample goes to.");
        Scouting.LabSample backwards = sample(null, Scouting.SampleStatus.SHIPPED, TODAY, TODAY.minusDays(2));
        assertThat(Scouting.problems(backwards, TODAY)).containsExactly(
                "The ship date cannot be before the collection date.");
        assertThat(Scouting.problems(sample(null, Scouting.SampleStatus.COLLECTED, TODAY, null), TODAY)).isEmpty();
    }

    @Test
    void overdueCountsFromShipDateAndStopsWhenResultsArrive() {
        Scouting.LabSample shipped = sample("S-1", Scouting.SampleStatus.SHIPPED, TODAY.minusDays(30),
                TODAY.minusDays(14));
        assertThat(Scouting.overdue(shipped, TODAY, 14)).isFalse();
        assertThat(Scouting.overdue(shipped, TODAY.plusDays(1), 14)).isTrue();
        assertThat(Scouting.daysWaiting(shipped, TODAY)).isEqualTo(14);
        Scouting.LabSample notShipped = sample("S-2", Scouting.SampleStatus.COLLECTED, TODAY.minusDays(20), null);
        assertThat(Scouting.overdue(notShipped, TODAY, 14)).isTrue();
        Scouting.LabSample done = sample("S-3", Scouting.SampleStatus.RESULTS_IN, TODAY.minusDays(60),
                TODAY.minusDays(59));
        assertThat(Scouting.overdue(done, TODAY, 14)).isFalse();
        assertThat(Scouting.overdue(shipped, TODAY, 0)).as("days below 1 are treated as 1").isTrue();
    }

    @Test
    void clampKeepsReminderInRange() {
        assertThat(Scouting.clampDays(0)).isEqualTo(1);
        assertThat(Scouting.clampDays(500)).isEqualTo(120);
        assertThat(Scouting.clampDays(21)).isEqualTo(21);
    }

    @Test
    void idsCountUpPerDayAndSkipHandEditedIds() {
        assertThat(Scouting.nextId("F", TODAY, List.of())).isEqualTo("F-20261007-001");
        assertThat(Scouting.nextId("F", TODAY, List.of("F-20261007-001", "F-20261007-009", "F-20261006-050",
                "F-20261007-abc"))).isEqualTo("F-20261007-010");
    }

    @Test
    void ordersPutNewestFindingsAndOverdueSamplesFirst() {
        List<Scouting.Finding> findings = new ArrayList<>(List.of(finding("F-a", TODAY.minusDays(3)),
                finding("F-b", TODAY), finding("F-c", TODAY.minusDays(1))));
        findings.sort(Scouting.NEWEST_FINDING_FIRST);
        assertThat(findings).extracting(Scouting.Finding::id).containsExactly("F-b", "F-c", "F-a");

        List<Scouting.LabSample> samples = new ArrayList<>(List.of(
                sample("done", Scouting.SampleStatus.RESULTS_IN, TODAY.minusDays(2), TODAY.minusDays(1)),
                sample("waiting", Scouting.SampleStatus.SHIPPED, TODAY.minusDays(3), TODAY.minusDays(3)),
                sample("late", Scouting.SampleStatus.SHIPPED, TODAY.minusDays(40), TODAY.minusDays(30))));
        samples.sort(Scouting.sampleOrder(TODAY, 14));
        assertThat(samples).extracting(Scouting.LabSample::id).containsExactly("late", "waiting", "done");
    }

    @Test
    void headlineAndLabelsReadNaturally() {
        assertThat(Scouting.overdueHeadline(0, 14)).isEqualTo("No lab results are overdue.");
        assertThat(Scouting.overdueHeadline(1, 14)).startsWith("1 lab sample has no results after 14 days.");
        assertThat(Scouting.overdueHeadline(2, 10)).startsWith("2 lab samples have no results after 10 days.");
        assertThat(finding("F-1", TODAY).label()).isEqualTo("2026-10-07 · Bed 4 · Dahlia · Thrips");
        assertThat(Scouting.Category.FUNGAL.toString()).isEqualTo(Scouting.Category.FUNGAL.label());
    }
}
