package com.flowerfarm.scouting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Scouting findings and lab samples: plain records, validation, ids, and the
 * overdue rule. No storage or UI here so every rule is easy to test.
 */
public final class Scouting {

    public static final String TITLE = "Scouting & lab samples";
    public static final int DEFAULT_OVERDUE_DAYS = 14;
    public static final int MIN_OVERDUE_DAYS = 1;
    public static final int MAX_OVERDUE_DAYS = 120;
    static final int MAX_TEXT = 2000;
    static final int MAX_SHORT = 200;
    private static final DateTimeFormatter ID_DAY = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);

    private Scouting() {
    }

    /** What kind of problem a finding is (best guess until a lab confirms it). */
    public enum Category {
        INSECT_MITE("Insect / mite"),
        FUNGAL("Fungal or water mold"),
        BACTERIAL("Bacterial"),
        VIRAL("Viral"),
        ABIOTIC("Abiotic (weather, water, nutrients, spray injury)"),
        UNKNOWN("Unknown");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** How bad it looks where it was found. */
    public enum Severity {
        LOW("Low: a few plants or leaves"),
        MODERATE("Moderate: spreading in the bed"),
        HIGH("High: many plants, quality loss"),
        SEVERE("Severe: crop at risk");

        private final String label;

        Severity(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SampleType {
        PLANT_DISEASE("Plant tissue (disease)"),
        INSECT("Insect / mite"),
        SOIL("Soil"),
        WATER("Water");

        private final String label;

        SampleType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum SampleStatus {
        COLLECTED("Collected"),
        SHIPPED("Shipped"),
        RECEIVED("Received by lab"),
        RESULTS_IN("Results in");

        private final String label;

        SampleStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** One scouting observation in a bed or block. */
    public record Finding(String id, LocalDate date, Long plantingId, String bed, String crop, String variety,
                          Category category, String organism, String symptoms, Severity severity,
                          Double incidencePercent, String photoPath, String action) {

        public Finding {
            bed = clean(bed);
            crop = clean(crop);
            variety = clean(variety);
            organism = clean(organism);
            symptoms = clean(symptoms);
            photoPath = clean(photoPath);
            action = clean(action);
            category = category == null ? Category.UNKNOWN : category;
            severity = severity == null ? Severity.LOW : severity;
        }

        public Finding withId(String newId) {
            return new Finding(newId, date, plantingId, bed, crop, variety, category, organism, symptoms, severity,
                    incidencePercent, photoPath, action);
        }

        /** Short label for lists and sample links, e.g. "Oct 7 · Bed 4 · Dahlia · Thrips?". */
        public String label() {
            StringBuilder text = new StringBuilder(date == null ? "?" : date.toString());
            if (!bed.isEmpty()) {
                text.append(" · ").append(bed);
            }
            if (!crop.isEmpty()) {
                text.append(" · ").append(crop);
            }
            text.append(" · ").append(organism.isEmpty() ? category.label() : organism);
            return text.toString();
        }
    }

    /** One sample sent (or about to be sent) to a lab. */
    public record LabSample(String id, String findingId, String soilBlock, SampleType type, String labId,
                            String labName, LocalDate collected, LocalDate shipped, String tracking,
                            SampleStatus status, BigDecimal cost, String resultSummary, String reportPath,
                            LocalDate resultsDate) {

        public LabSample {
            findingId = clean(findingId);
            soilBlock = clean(soilBlock);
            labId = clean(labId);
            labName = clean(labName);
            tracking = clean(tracking);
            resultSummary = clean(resultSummary);
            reportPath = clean(reportPath);
            type = type == null ? SampleType.PLANT_DISEASE : type;
            status = status == null ? SampleStatus.COLLECTED : status;
        }

        public LabSample withId(String newId) {
            return new LabSample(newId, findingId, soilBlock, type, labId, labName, collected, shipped, tracking,
                    status, cost, resultSummary, reportPath, resultsDate);
        }

        /** The day the overdue clock starts: shipped, or collected if not shipped yet. */
        public Optional<LocalDate> clockStart() {
            return Optional.ofNullable(shipped != null ? shipped : collected);
        }
    }

    /** Plain-language problems with a finding; empty when it can be saved. */
    public static List<String> problems(Finding finding, LocalDate today) {
        List<String> problems = new ArrayList<>();
        if (finding.date() == null) {
            problems.add("Enter the date you scouted.");
        } else if (today != null && finding.date().isAfter(today)) {
            problems.add("The scouting date cannot be in the future.");
        }
        if (finding.bed().isEmpty() && finding.crop().isEmpty() && finding.plantingId() == null) {
            problems.add("Say where you found it: a bed or block, a crop, or a planting.");
        }
        if (finding.symptoms().isEmpty() && finding.organism().isEmpty()) {
            problems.add("Describe the symptoms or the suspected pest or disease.");
        }
        if (finding.incidencePercent() != null
                && (finding.incidencePercent().isNaN() || finding.incidencePercent() < 0
                || finding.incidencePercent() > 100)) {
            problems.add("Plants affected must be between 0 and 100 percent.");
        }
        tooLong(problems, "Symptoms", finding.symptoms(), MAX_TEXT);
        tooLong(problems, "Action taken", finding.action(), MAX_TEXT);
        tooLong(problems, "Suspected organism", finding.organism(), MAX_SHORT);
        tooLong(problems, "Bed or block", finding.bed(), MAX_SHORT);
        tooLong(problems, "Crop", finding.crop(), MAX_SHORT);
        tooLong(problems, "Variety", finding.variety(), MAX_SHORT);
        tooLong(problems, "Photo file", finding.photoPath(), 1000);
        return problems;
    }

    /** Plain-language problems with a lab sample; empty when it can be saved. */
    public static List<String> problems(LabSample sample, LocalDate today) {
        List<String> problems = new ArrayList<>();
        if (sample.findingId().isEmpty() && sample.soilBlock().isEmpty()) {
            problems.add("Link the sample to a scouting finding, or name the soil block or water source.");
        }
        if (sample.collected() == null) {
            problems.add("Enter the date the sample was collected.");
        } else if (today != null && sample.collected().isAfter(today)) {
            problems.add("The collection date cannot be in the future.");
        }
        if (sample.shipped() != null && sample.collected() != null && sample.shipped().isBefore(sample.collected())) {
            problems.add("The ship date cannot be before the collection date.");
        }
        if (sample.status() != SampleStatus.COLLECTED && sample.shipped() == null) {
            problems.add("Enter the ship (or drop-off) date for a sample that has gone to the lab.");
        }
        if (sample.status() == SampleStatus.RESULTS_IN && sample.resultSummary().isEmpty()
                && sample.reportPath().isEmpty()) {
            problems.add("Add a short result summary or attach the lab report.");
        }
        if (sample.resultsDate() != null && sample.collected() != null
                && sample.resultsDate().isBefore(sample.collected())) {
            problems.add("The results date cannot be before the collection date.");
        }
        if (sample.cost() != null && sample.cost().signum() < 0) {
            problems.add("Cost cannot be negative.");
        }
        if (sample.labName().isEmpty() && sample.labId().isEmpty()) {
            problems.add("Choose the lab or clinic the sample goes to.");
        }
        tooLong(problems, "Result summary", sample.resultSummary(), MAX_TEXT);
        tooLong(problems, "Tracking number", sample.tracking(), MAX_SHORT);
        tooLong(problems, "Soil block", sample.soilBlock(), MAX_SHORT);
        tooLong(problems, "Lab", sample.labName(), MAX_SHORT);
        tooLong(problems, "Report file", sample.reportPath(), 1000);
        return problems;
    }

    /** True when results are still missing more than {@code days} days after the sample left. */
    public static boolean overdue(LabSample sample, LocalDate today, int days) {
        if (sample.status() == SampleStatus.RESULTS_IN || today == null) {
            return false;
        }
        return sample.clockStart().map(start -> ChronoUnit.DAYS.between(start, today) > clampDays(days))
                .orElse(false);
    }

    public static long daysWaiting(LabSample sample, LocalDate today) {
        return sample.clockStart().map(start -> Math.max(0, ChronoUnit.DAYS.between(start, today))).orElse(0L);
    }

    public static int clampDays(int days) {
        return Math.max(MIN_OVERDUE_DAYS, Math.min(MAX_OVERDUE_DAYS, days));
    }

    /** Next id like {@code F-20261007-002}: prefix, day, then a counter unique among {@code existing}. */
    public static String nextId(String prefix, LocalDate day, List<String> existing) {
        String stem = prefix + "-" + day.format(ID_DAY) + "-";
        int max = 0;
        for (String id : existing) {
            if (id != null && id.startsWith(stem)) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(stem.length())));
                } catch (NumberFormatException ignored) {
                    // a hand-edited id; skip it
                }
            }
        }
        return stem + String.format(Locale.ROOT, "%03d", max + 1);
    }

    /** Findings newest first, then by id. */
    public static final Comparator<Finding> NEWEST_FINDING_FIRST = Comparator
            .comparing(Finding::date, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Finding::id, Comparator.nullsLast(Comparator.reverseOrder()));

    /** Overdue first, then waiting longest, then finished samples newest first. */
    public static Comparator<LabSample> sampleOrder(LocalDate today, int days) {
        return Comparator.<LabSample, Boolean>comparing(sample -> !overdue(sample, today, days))
                .thenComparing(sample -> sample.status() == SampleStatus.RESULTS_IN)
                .thenComparing(sample -> sample.collected(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LabSample::id, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    public static String overdueHeadline(long overdueCount, int days) {
        if (overdueCount == 0) {
            return "No lab results are overdue.";
        }
        return overdueCount + (overdueCount == 1 ? " lab sample has" : " lab samples have")
                + " no results after " + days + " days. Call or email the lab with the sample ID.";
    }

    static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static void tooLong(List<String> problems, String field, String value, int max) {
        if (Objects.requireNonNullElse(value, "").length() > max) {
            problems.add(field + " is too long (keep it under " + max + " characters).");
        }
    }
}
