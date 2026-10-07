package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.HarvestGrade;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure cooler shelf-life math: review intervals by crop/conditioning, aging status,
 * and how a count / quality / loss review changes a harvest lot. A due review is a
 * farm reminder — not a guarantee of vase life.
 */
public final class CoolerShelfLife {

    public static final String TITLE = "Cooler lot review";
    public static final int DEFAULT_INTERVAL_DAYS = 5;
    public static final int MIN_INTERVAL_DAYS = 1;
    public static final int MAX_INTERVAL_DAYS = 60;
    public static final int MAX_LIST = 80;

    public enum Conditioning {
        FRESH_CUT(4),
        CONDITIONED(7),
        WOODY(10);

        private final int days;

        Conditioning(int days) {
            this.days = days;
        }

        public int days() {
            return days;
        }

        public static Conditioning fromName(String value) {
            if (value == null || value.isBlank()) {
                return FRESH_CUT;
            }
            String key = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            for (Conditioning item : values()) {
                if (item.name().equals(key)) {
                    return item;
                }
            }
            throw new IllegalArgumentException(
                    "Conditioning must be FRESH_CUT, CONDITIONED, or WOODY.");
        }
    }

    public enum Status {
        OK,
        DUE,
        OVERDUE
    }

    public record Settings(
            int defaultIntervalDays,
            Map<String, Integer> cropIntervals,
            Map<String, Conditioning> cropConditioning,
            Map<String, LocalDate> lastReviewedByLot) {

        public Settings {
            defaultIntervalDays = clampInterval(defaultIntervalDays);
            cropIntervals = normalizeIntervals(cropIntervals);
            cropConditioning = normalizeConditioning(cropConditioning);
            lastReviewedByLot = normalizeReviews(lastReviewedByLot);
        }

        public static Settings defaults() {
            return new Settings(DEFAULT_INTERVAL_DAYS, Map.of(), Map.of(), Map.of());
        }

        public Settings withDefaultInterval(int days) {
            return new Settings(days, cropIntervals, cropConditioning, lastReviewedByLot);
        }

        public Settings withCropInterval(String crop, int days) {
            Map<String, Integer> next = new LinkedHashMap<>(cropIntervals);
            String key = normalizeCrop(crop);
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Crop name is required.");
            }
            next.put(key, clampInterval(days));
            return new Settings(defaultIntervalDays, next, cropConditioning, lastReviewedByLot);
        }

        public Settings withCropConditioning(String crop, Conditioning conditioning) {
            Map<String, Conditioning> next = new LinkedHashMap<>(cropConditioning);
            String key = normalizeCrop(crop);
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Crop name is required.");
            }
            next.put(key, Objects.requireNonNull(conditioning, "conditioning"));
            return new Settings(defaultIntervalDays, cropIntervals, next, lastReviewedByLot);
        }

        public Settings withReviewed(String lotCode, LocalDate on) {
            Map<String, LocalDate> next = new LinkedHashMap<>(lastReviewedByLot);
            String key = normalizeLot(lotCode);
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Lot code is required.");
            }
            next.put(key, Objects.requireNonNull(on, "on"));
            return new Settings(defaultIntervalDays, cropIntervals, cropConditioning, next);
        }
    }

    public record AgedLot(
            Long harvestId,
            String lotCode,
            String cropName,
            LocalDate harvestDate,
            String storageLocation,
            HarvestGrade grade,
            double marketableQuantity,
            double lossQuantity,
            String unit,
            int ageDays,
            int reviewIntervalDays,
            LocalDate reviewDueDate,
            LocalDate lastReviewed,
            Status status,
            Conditioning conditioning) {

        public AgedLot {
            harvestId = Objects.requireNonNull(harvestId, "harvestId");
            lotCode = requireText(lotCode, "lotCode");
            cropName = requireText(cropName, "cropName");
            harvestDate = Objects.requireNonNull(harvestDate, "harvestDate");
            storageLocation = storageLocation == null || storageLocation.isBlank()
                    ? "Unassigned cooler"
                    : storageLocation.trim();
            grade = grade == null ? HarvestGrade.UNGRADED : grade;
            unit = unit == null || unit.isBlank() ? "stems" : unit.trim();
            status = Objects.requireNonNull(status, "status");
            conditioning = conditioning == null ? Conditioning.FRESH_CUT : conditioning;
            reviewDueDate = Objects.requireNonNull(reviewDueDate, "reviewDueDate");
        }

        public String packFirstHint() {
            return switch (status) {
                case OVERDUE -> "Pack first — review overdue";
                case DUE -> "Review today before packing";
                case OK -> "Still inside review window";
            };
        }
    }

    public record Board(LocalDate asOf, List<AgedLot> lots, String headline, String guidance) {
        public Board {
            asOf = Objects.requireNonNull(asOf, "asOf");
            lots = List.copyOf(Objects.requireNonNullElse(lots, List.of()));
            headline = headline == null || headline.isBlank()
                    ? (lots.isEmpty() ? "No cooler lots waiting" : lots.size() + " cooler lot"
                    + (lots.size() == 1 ? "" : "s"))
                    : headline.trim();
            guidance = guidance == null ? "" : guidance.trim();
        }

        public boolean isEmpty() {
            return lots.isEmpty();
        }

        public long overdueCount() {
            return lots.stream().filter(lot -> lot.status() == Status.OVERDUE).count();
        }

        public long dueCount() {
            return lots.stream().filter(lot -> lot.status() == Status.DUE).count();
        }
    }

    /**
     * Count, optional grade, and additional culls recorded during a cooler check.
     * Additional loss must leave at least one inventory unit of marketable stems
     * (Harvest Log cannot hold a zero-quantity lot).
     */
    public record ReviewAction(
            Double countedMarketable,
            HarvestGrade grade,
            double additionalLoss,
            String note) {

        public ReviewAction {
            if (countedMarketable != null && (!Double.isFinite(countedMarketable)
                    || countedMarketable <= 0 || countedMarketable > Integer.MAX_VALUE)) {
                throw new IllegalArgumentException(
                        "Counted marketable quantity must be greater than zero.");
            }
            if (!Double.isFinite(additionalLoss) || additionalLoss < 0
                    || additionalLoss > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Additional loss must be a finite number from 0 upward.");
            }
            note = note == null ? "" : note.trim();
            if (note.length() > 500) {
                throw new IllegalArgumentException("Review note cannot exceed 500 characters.");
            }
        }

        public boolean changesLot() {
            return countedMarketable != null || grade != null || additionalLoss > 0 || !note.isBlank();
        }
    }

    public record AppliedReview(
            double marketableQuantity,
            double lossQuantity,
            HarvestGrade grade,
            String notes,
            String summary) {
    }

    private CoolerShelfLife() {
    }

    public static String pageGuidance() {
        return "Oldest cooler lots first. Review intervals are farm reminders by crop "
                + "and conditioning — not a promise of vase life. Record a count, quality "
                + "grade, or cull; print the existing QR lot label when you need it.";
    }

    public static String menuDescription() {
        return "Review aging harvest lots by cooler location, crop interval, and QR label.";
    }

    public static int clampInterval(int days) {
        if (days < MIN_INTERVAL_DAYS || days > MAX_INTERVAL_DAYS) {
            throw new IllegalArgumentException(
                    "Review interval must be between " + MIN_INTERVAL_DAYS
                            + " and " + MAX_INTERVAL_DAYS + " days.");
        }
        return days;
    }

    public static int intervalDays(Settings settings, String cropName) {
        Settings safe = settings == null ? Settings.defaults() : settings;
        String crop = normalizeCrop(cropName);
        Integer override = safe.cropIntervals().get(crop);
        if (override != null) {
            return override;
        }
        if (safe.cropConditioning().containsKey(crop)) {
            return safe.cropConditioning().get(crop).days();
        }
        return safe.defaultIntervalDays();
    }

    public static Board board(LocalDate asOf, List<HarvestEntry> harvests, Settings settings) {
        LocalDate day = Objects.requireNonNull(asOf, "asOf");
        Settings safe = settings == null ? Settings.defaults() : settings;
        List<AgedLot> lots = new ArrayList<>();
        if (harvests != null) {
            for (HarvestEntry entry : harvests) {
                age(entry, day, safe).ifPresent(lots::add);
            }
        }
        lots.sort(Comparator
                .comparing((AgedLot lot) -> lot.status() == Status.OVERDUE ? 0
                        : lot.status() == Status.DUE ? 1 : 2)
                .thenComparing(AgedLot::harvestDate)
                .thenComparing(AgedLot::lotCode));
        if (lots.size() > MAX_LIST) {
            lots = new ArrayList<>(lots.subList(0, MAX_LIST));
        }
        long overdue = lots.stream().filter(lot -> lot.status() == Status.OVERDUE).count();
        long due = lots.stream().filter(lot -> lot.status() == Status.DUE).count();
        String headline;
        if (lots.isEmpty()) {
            headline = "No marketable cooler lots right now";
        } else if (overdue > 0) {
            headline = overdue + " overdue cooler lot" + (overdue == 1 ? "" : "s")
                    + " — pack these first";
        } else if (due > 0) {
            headline = due + " cooler lot" + (due == 1 ? "" : "s") + " due for review today";
        } else {
            headline = lots.size() + " cooler lot" + (lots.size() == 1 ? "" : "s")
                    + " inside their review window";
        }
        String guidance = lots.isEmpty()
                ? "Log a harvest with a storage location to see it here."
                : pageGuidance();
        return new Board(day, lots, headline, guidance);
    }

    public static Optional<AgedLot> age(HarvestEntry entry, LocalDate asOf, Settings settings) {
        if (entry == null || entry.getId() == null || entry.getHarvestDate() == null) {
            return Optional.empty();
        }
        if (!Double.isFinite(entry.getQuantity()) || entry.getQuantity() <= 0) {
            return Optional.empty();
        }
        if (entry.getLotCode() == null || entry.getLotCode().isBlank()) {
            return Optional.empty();
        }
        LocalDate day = Objects.requireNonNull(asOf, "asOf");
        Settings safe = settings == null ? Settings.defaults() : settings;
        int interval = intervalDays(safe, entry.getCropName());
        LocalDate last = safe.lastReviewedByLot().get(normalizeLot(entry.getLotCode()));
        LocalDate baseline = last != null && !last.isBefore(entry.getHarvestDate())
                ? last
                : entry.getHarvestDate();
        LocalDate due = baseline.plusDays(interval);
        int ageDays = (int) Math.max(0, ChronoUnit.DAYS.between(entry.getHarvestDate(), day));
        Status status;
        if (day.isAfter(due)) {
            status = Status.OVERDUE;
        } else if (day.equals(due)) {
            status = Status.DUE;
        } else {
            status = Status.OK;
        }
        Conditioning conditioning = safe.cropConditioning()
                .getOrDefault(normalizeCrop(entry.getCropName()), Conditioning.FRESH_CUT);
        return Optional.of(new AgedLot(
                entry.getId(),
                entry.getLotCode(),
                entry.getCropName(),
                entry.getHarvestDate(),
                entry.getStorageLocation(),
                entry.getGrade(),
                entry.getQuantity(),
                entry.getLossQuantity(),
                entry.getUnit(),
                ageDays,
                interval,
                due,
                last,
                status,
                conditioning));
    }

    public static AppliedReview applyReview(HarvestEntry existing, ReviewAction action) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(action, "action");
        double marketable = existing.getQuantity();
        double loss = existing.getLossQuantity();
        HarvestGrade grade = existing.getGrade();
        if (action.countedMarketable() != null) {
            marketable = action.countedMarketable();
        }
        if (action.additionalLoss() > 0) {
            if (action.additionalLoss() >= marketable) {
                throw new IllegalArgumentException(
                        "Additional loss must leave marketable stems on the lot. "
                                + "Write off a finished lot from Harvest Log after packing it out.");
            }
            marketable -= action.additionalLoss();
            loss += action.additionalLoss();
        }
        if (marketable < 0.5) {
            throw new IllegalArgumentException(
                    "Marketable quantity must stay at least 0.5 so inventory can hold one unit.");
        }
        if (action.grade() != null) {
            grade = action.grade();
        }
        String notes = existing.getNotes() == null ? "" : existing.getNotes();
        if (!action.note().isBlank()) {
            String addition = "Cooler review: " + action.note();
            notes = notes.isBlank() ? addition : notes + "\n" + addition;
            if (notes.length() > 2000) {
                notes = notes.substring(0, 2000);
            }
        }
        String summary = summary(action, marketable, loss, grade);
        return new AppliedReview(marketable, loss, grade, notes, summary);
    }

    public static String statusSavedInterval(String crop, int days) {
        return "Review every " + days + " day" + (days == 1 ? "" : "s")
                + " for " + crop.trim() + ".";
    }

    public static String statusReviewRecorded(String lotCode) {
        return "Cooler review saved for lot " + lotCode + ".";
    }

    public static String statusEmpty() {
        return "No marketable harvest lots in the cooler right now.";
    }

    public static String statusLabelSaved(String lotCode) {
        return "QR lot label saved for " + lotCode + ".";
    }

    private static String summary(ReviewAction action, double marketable, double loss,
                                  HarvestGrade grade) {
        StringBuilder out = new StringBuilder("Cooler review");
        if (action.countedMarketable() != null) {
            out.append("; count ").append(trimNumber(marketable));
        }
        if (action.additionalLoss() > 0) {
            out.append("; +").append(trimNumber(action.additionalLoss())).append(" loss");
        }
        if (action.grade() != null) {
            out.append("; grade ").append(grade.name());
        }
        out.append(" (marketable ").append(trimNumber(marketable))
                .append(", loss ").append(trimNumber(loss)).append(')');
        return out.toString();
    }

    private static String trimNumber(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return Double.toString(value);
    }

    private static Map<String, Integer> normalizeIntervals(Map<String, Integer> source) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (source == null) {
            return Map.copyOf(out);
        }
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String crop = normalizeCrop(entry.getKey());
            if (!crop.isEmpty() && entry.getValue() != null) {
                out.put(crop, clampInterval(entry.getValue()));
            }
        }
        return Map.copyOf(out);
    }

    private static Map<String, Conditioning> normalizeConditioning(Map<String, Conditioning> source) {
        Map<String, Conditioning> out = new LinkedHashMap<>();
        if (source == null) {
            return Map.copyOf(out);
        }
        for (Map.Entry<String, Conditioning> entry : source.entrySet()) {
            String crop = normalizeCrop(entry.getKey());
            if (!crop.isEmpty() && entry.getValue() != null) {
                out.put(crop, entry.getValue());
            }
        }
        return Map.copyOf(out);
    }

    private static Map<String, LocalDate> normalizeReviews(Map<String, LocalDate> source) {
        Map<String, LocalDate> out = new LinkedHashMap<>();
        if (source == null) {
            return Map.copyOf(out);
        }
        for (Map.Entry<String, LocalDate> entry : source.entrySet()) {
            String lot = normalizeLot(entry.getKey());
            if (!lot.isEmpty() && entry.getValue() != null) {
                out.put(lot, entry.getValue());
            }
        }
        return Map.copyOf(out);
    }

    static String normalizeCrop(String crop) {
        return crop == null ? "" : crop.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeLot(String lot) {
        return lot == null ? "" : lot.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
