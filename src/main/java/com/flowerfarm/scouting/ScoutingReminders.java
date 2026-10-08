package com.flowerfarm.scouting;

import com.flowerfarm.service.GrowerReminder;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Today's-reminder items from the scouting log: lab results that are overdue, and serious pest or
 * disease findings from the last few days. Opening one takes the grower to Scouting &amp; lab samples.
 */
public final class ScoutingReminders {

    /** Findings this many days old or newer count as recent. */
    public static final int RECENT_DAYS = 7;
    /** At most this many reminders of each kind, so scouting never crowds out field work. */
    public static final int MAX_EACH = 3;

    private ScoutingReminders() {
    }

    public static List<GrowerReminder> from(ScoutingService.Board board) {
        Objects.requireNonNull(board, "board");
        List<GrowerReminder> items = new ArrayList<>();
        addOverdue(items, board);
        addSerious(items, board);
        return List.copyOf(items);
    }

    /** One reminder telling the grower the scouting log needs attention. */
    public static GrowerReminder unreadable() {
        return new GrowerReminder(GrowerReminder.Kind.SCOUTING, "Scouting log needs attention",
                "The scouting log could not be opened. Open Scouting & lab samples for details; nothing was "
                        + "changed or deleted.", Scouting.TITLE);
    }

    /** True for HIGH or SEVERE findings dated within the last {@link #RECENT_DAYS} days (not in the future). */
    public static boolean recentAndSerious(Scouting.Finding finding, LocalDate today) {
        if (finding == null || finding.date() == null || today == null) {
            return false;
        }
        boolean serious = finding.severity() == Scouting.Severity.HIGH
                || finding.severity() == Scouting.Severity.SEVERE;
        long age = ChronoUnit.DAYS.between(finding.date(), today);
        return serious && age >= 0 && age <= RECENT_DAYS;
    }

    private static void addOverdue(List<GrowerReminder> items, ScoutingService.Board board) {
        int shown = 0;
        for (Scouting.LabSample sample : board.samples()) {
            if (!board.overdue(sample)) {
                continue;
            }
            long days = Scouting.daysWaiting(sample, board.today());
            String lab = sample.labName().isEmpty() ? "the lab" : sample.labName();
            String sent = sample.shipped() != null ? "shipped " + sample.shipped() : "collected " + sample.collected();
            items.add(new GrowerReminder(GrowerReminder.Kind.SCOUTING,
                    "Lab results overdue · " + sample.id(),
                    lab + " · " + sent + " (" + days + " days, no results yet). Call or email the lab with "
                            + "sample ID " + sample.id() + ".",
                    Scouting.TITLE));
            if (++shown >= MAX_EACH) {
                return;
            }
        }
    }

    private static void addSerious(List<GrowerReminder> items, ScoutingService.Board board) {
        int shown = 0;
        for (Scouting.Finding finding : board.findings()) {
            if (!recentAndSerious(finding, board.today())) {
                continue;
            }
            String what = finding.organism().isEmpty() ? finding.category().label() : finding.organism();
            String where = finding.bed().isEmpty() ? finding.crop() : finding.bed();
            StringBuilder detail = new StringBuilder("Found ").append(finding.date());
            if (!finding.crop().isEmpty()) {
                detail.append(" on ").append(finding.crop());
            }
            if (finding.incidencePercent() != null) {
                detail.append(" · ").append(SampleSubmissionSheet.trimNumber(finding.incidencePercent()))
                        .append("% of plants affected");
            }
            Optional<Scouting.LabSample> sample = board.samples().stream()
                    .filter(candidate -> candidate.findingId().equals(finding.id())).findFirst();
            detail.append(sample.map(s -> ". Lab sample " + s.id() + ": " + s.status().label().toLowerCase(
                            java.util.Locale.ROOT) + ".")
                    .orElse(". No lab sample yet; see How to send a sample if you need a diagnosis."));
            items.add(new GrowerReminder(GrowerReminder.Kind.SCOUTING,
                    "Scouting · " + shortLabel(finding.severity()) + " " + what
                            + (where.isEmpty() ? "" : " · " + where),
                    detail.toString(), Scouting.TITLE));
            if (++shown >= MAX_EACH) {
                return;
            }
        }
    }

    /** "High", "Severe" etc. without the explanation after the colon, so titles stay short. */
    static String shortLabel(Scouting.Severity severity) {
        String label = severity.label();
        int colon = label.indexOf(':');
        return colon < 0 ? label : label.substring(0, colon);
    }
}
