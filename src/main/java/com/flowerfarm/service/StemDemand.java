package com.flowerfarm.service;

import com.flowerfarm.util.CsvSupport;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure stem demand board: dated stem needs from orders (stems sold directly, or a
 * saved recipe times the design count) totalled by crop and date, then covered
 * from live cooler stock first and the season plan's projected harvest second.
 *
 * <p>Confirmed orders are covered before draft orders, so a draft never takes
 * stems a confirmed order needs. Projected harvest is a forecast and is shown
 * separately from stock; nothing here reserves or changes any record.
 */
public final class StemDemand {

    public static final String TITLE = "Stem demand board";
    public static final String FORECAST_NOTE =
            "\"To cut\" is the season plan's projected harvest, a forecast: confirm before promising.";

    private StemDemand() {
    }

    /** What the grower does about one crop on one date. */
    public enum Action {
        FROM_COOLER("From cooler"),
        CUT("Cut"),
        BUY("Short: buy or substitute");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Stems one order needs of one crop on its date. */
    public record Need(LocalDate date, String crop, int stems, boolean confirmed, String source) {
        public Need {
            date = Objects.requireNonNull(date, "date");
            crop = requireText(crop, "crop");
            if (stems < 0) {
                throw new IllegalArgumentException("stems cannot be negative");
            }
            source = source == null ? "" : source.trim();
        }
    }

    /** An order line the board could not turn into stems, with the reason. */
    public record NotCounted(LocalDate date, Long orderId, Long lineId, String source, String product,
                             double quantity, String unit, String reason) {
        public NotCounted {
            date = Objects.requireNonNull(date, "date");
            source = source == null ? "" : source.trim();
            product = product == null ? "" : product.trim();
            unit = unit == null ? "" : unit.trim();
            reason = reason == null ? "" : reason.trim();
        }
    }

    /** Live cooler stock counted in stems. */
    public record Stock(String name, int stems) {
        public Stock {
            name = requireText(name, "name");
            stems = Math.max(0, stems);
        }
    }

    /** One planting in the season plan that is not complete yet. */
    public record Planting(String crop, LocalDate harvestStart, LocalDate harvestEnd, int expectedStems) {
        public Planting {
            crop = requireText(crop, "crop");
            harvestStart = Objects.requireNonNull(harvestStart, "harvestStart");
            harvestEnd = Objects.requireNonNull(harvestEnd, "harvestEnd");
            expectedStems = Math.max(0, expectedStems);
        }
    }

    /** One crop on one date: total stems, how they are covered, and what is short. */
    public record Line(LocalDate date, String crop, int needed, int draftStems, int fromCooler,
                       int toCut, int shortBy, Action action, String neededFor) {
        public boolean isShort() {
            return shortBy > 0;
        }
    }

    /** One crop across the window. */
    public record CropTotal(String crop, int needed, int coolerStock, int projectedHarvest,
                            int fromCooler, int toCut, int shortBy) {
    }

    public record Board(LocalDate from, LocalDate to, boolean includesDrafts, List<Line> lines,
                        List<CropTotal> crops, List<NotCounted> notCounted, List<String> notes) {
        public Board {
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
            lines = List.copyOf(lines);
            crops = List.copyOf(crops);
            notCounted = List.copyOf(notCounted);
            notes = List.copyOf(notes);
        }

        public boolean isEmpty() {
            return lines.isEmpty() && notCounted.isEmpty();
        }

        public int totalNeeded() {
            return lines.stream().mapToInt(Line::needed).sum();
        }

        public int totalShort() {
            return lines.stream().mapToInt(Line::shortBy).sum();
        }

        public List<Line> shortLines() {
            return lines.stream().filter(Line::isShort).toList();
        }
    }

    /**
     * Builds the board. Needs outside {@code from..to} are ignored; plantings only
     * contribute harvest projected between {@code from} and each need's date.
     */
    public static Board plan(LocalDate from, LocalDate to, boolean includesDrafts, List<Need> needs,
                             List<Stock> stock, List<Planting> plantings, List<NotCounted> notCounted,
                             List<String> extraNotes) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The end date must be on or after the start date.");
        }
        Map<String, CropDemand> byCrop = new TreeMap<>();
        for (Need need : safe(needs)) {
            if (need.date().isBefore(from) || need.date().isAfter(to) || need.stems() == 0) {
                continue;
            }
            byCrop.computeIfAbsent(normalize(need.crop()), key -> new CropDemand(need.crop())).add(need);
        }
        List<Line> lines = new ArrayList<>();
        List<CropTotal> crops = new ArrayList<>();
        for (Map.Entry<String, CropDemand> entry : byCrop.entrySet()) {
            CropDemand crop = entry.getValue();
            int cooler = safe(stock).stream()
                    .filter(item -> normalize(item.name()).equals(entry.getKey()))
                    .mapToInt(Stock::stems).sum();
            List<Planting> matching = safe(plantings).stream()
                    .filter(planting -> cropMatches(planting.crop(), crop.display))
                    .toList();
            crop.allocate(from, cooler, matching);
            int needed = 0;
            int fromCooler = 0;
            int toCut = 0;
            int shortBy = 0;
            for (DateBucket bucket : crop.dates.values()) {
                Line line = bucket.toLine(crop.display);
                lines.add(line);
                needed += line.needed();
                fromCooler += line.fromCooler();
                toCut += line.toCut();
                shortBy += line.shortBy();
            }
            crops.add(new CropTotal(crop.display, needed, cooler,
                    (int) Math.floor(projected(matching, from, to)), fromCooler, toCut, shortBy));
        }
        lines.sort(Comparator.comparing(Line::date)
                .thenComparing(Line::crop, String.CASE_INSENSITIVE_ORDER));
        crops.sort(Comparator.comparing(CropTotal::crop, String.CASE_INSENSITIVE_ORDER));
        List<NotCounted> skipped = new ArrayList<>(safe(notCounted));
        skipped.removeIf(line -> line.date().isBefore(from) || line.date().isAfter(to));
        skipped.sort(Comparator.comparing(NotCounted::date).thenComparing(NotCounted::source));
        List<String> notes = new ArrayList<>(safe(extraNotes));
        if (lines.stream().anyMatch(line -> line.toCut() > 0)) {
            notes.add(FORECAST_NOTE);
        }
        if (!includesDrafts) {
            notes.add("Draft orders are not included. Tick \"Include draft orders\" to see them.");
        }
        return new Board(from, to, includesDrafts, lines, crops, skipped, notes);
    }

    /** Order lines count directly only when they are sold in stems. */
    public static boolean isStemUnit(String unit) {
        return unit != null && normalize(unit).contains("stem");
    }

    /** Cooler stock counts as stems when recorded per stem or per single item. */
    public static boolean isStockCountedInStems(String unit) {
        if (unit == null || unit.isBlank()) {
            return true;
        }
        String normalized = normalize(unit);
        return normalized.contains("stem") || Set.of("each", "ea", "per unit", "unit", "units",
                "piece", "pieces", "pc", "pcs", "per piece", "per each").contains(normalized);
    }

    /** Stems a recipe needs for a number of designs, with the recipe's waste allowance. */
    public static int recipeStems(double stemsPerDesign, int designs, double wastePercent) {
        if (designs <= 0 || stemsPerDesign <= 0) {
            return 0;
        }
        double withWaste = stemsPerDesign * designs * (1 + Math.max(0, wastePercent) / 100d);
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(withWaste - 1e-9));
    }

    public static String csv(Board board) {
        Objects.requireNonNull(board, "board");
        StringBuilder out = new StringBuilder();
        out.append(CsvSupport.row(',', "Date", "Crop", "Stems needed", "Draft stems", "From cooler",
                "To cut (forecast)", "Short", "Action", "Needed for"));
        out.append('\n');
        for (Line line : board.lines()) {
            out.append(CsvSupport.row(',', line.date().toString(), cell(line.crop()),
                    Integer.toString(line.needed()), Integer.toString(line.draftStems()),
                    Integer.toString(line.fromCooler()), Integer.toString(line.toCut()),
                    Integer.toString(line.shortBy()), line.action().label(), cell(line.neededFor())));
            out.append('\n');
        }
        for (NotCounted line : board.notCounted()) {
            out.append(CsvSupport.row(',', line.date().toString(), cell(line.product()), "", "", "", "",
                    "", "Not counted", cell(line.source() + " · " + quantity(line.quantity()) + " "
                            + line.unit() + " · " + line.reason())));
            out.append('\n');
        }
        return out.toString();
    }

    /** Plain text for the clipboard: buy list first, then cut list, then the full board. */
    public static String shareText(Board board, String farmName) {
        Objects.requireNonNull(board, "board");
        StringBuilder out = new StringBuilder();
        String farm = farmName == null || farmName.isBlank() ? "Flower farm" : farmName.trim();
        out.append(farm).append(" — stem demand ").append(board.from()).append(" to ")
                .append(board.to()).append('\n');
        if (board.isEmpty()) {
            out.append("\nNo stems are needed by orders in these dates.\n");
            return out.toString();
        }
        out.append(board.totalNeeded()).append(" stems needed");
        if (board.totalShort() > 0) {
            out.append(", ").append(board.totalShort()).append(" short");
        }
        out.append(".\n");
        List<Line> shortLines = board.shortLines();
        if (!shortLines.isEmpty()) {
            out.append("\nBuy or substitute:\n");
            for (Line line : shortLines) {
                out.append(String.format(Locale.ROOT, "  %s  %-24s %6d stems%n", line.date(),
                        truncate(line.crop(), 24), line.shortBy()));
            }
        }
        List<Line> cutLines = board.lines().stream().filter(line -> line.toCut() > 0).toList();
        if (!cutLines.isEmpty()) {
            out.append("\nCut from the field (forecast):\n");
            for (Line line : cutLines) {
                out.append(String.format(Locale.ROOT, "  %s  %-24s %6d stems%n", line.date(),
                        truncate(line.crop(), 24), line.toCut()));
            }
        }
        out.append(String.format(Locale.ROOT, "%n%-10s  %-24s %7s %7s %7s %7s  %s%n",
                "Date", "Crop", "Needed", "Cooler", "Cut", "Short", "Needed for"));
        for (Line line : board.lines()) {
            out.append(String.format(Locale.ROOT, "%-10s  %-24s %7d %7d %7d %7d  %s%n",
                    line.date(), truncate(line.crop(), 24), line.needed(), line.fromCooler(),
                    line.toCut(), line.shortBy(), truncate(line.neededFor(), 60)));
        }
        if (!board.notCounted().isEmpty()) {
            out.append("\nNot counted (no stem count yet):\n");
            for (NotCounted line : board.notCounted()) {
                out.append("  ").append(line.date()).append("  ").append(line.product()).append(" × ")
                        .append(quantity(line.quantity())).append(' ').append(line.unit())
                        .append(" — ").append(line.source()).append(". ").append(line.reason())
                        .append('\n');
            }
        }
        for (String note : board.notes()) {
            out.append('\n').append(note);
        }
        out.append('\n');
        return out.toString();
    }

    public static String suggestedCsvFileName(Board board) {
        return "stem-demand-" + board.from() + "-to-" + board.to() + ".csv";
    }

    public static String statusCopied() {
        return "Stem demand list copied — paste it into an email, a supplier order, or a text.";
    }

    public static String statusSaved(String fileName) {
        return "Stem demand CSV saved → " + fileName;
    }

    public static String summary(Board board) {
        if (board.isEmpty()) {
            return "No stems are needed by orders from " + board.from() + " to " + board.to() + ".";
        }
        String text = board.totalNeeded() + " stems needed across " + board.crops().size()
                + " crop(s) from " + board.from() + " to " + board.to() + ".";
        int shortCrops = (int) board.crops().stream().filter(crop -> crop.shortBy() > 0).count();
        if (shortCrops > 0) {
            text += " " + board.totalShort() + " stems short on " + shortCrops + " crop(s).";
        } else {
            text += " Everything is covered by the cooler or the planned harvest.";
        }
        if (!board.notCounted().isEmpty()) {
            text += " " + board.notCounted().size() + " order line(s) not counted.";
        }
        return text;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Same crop-to-planting rule as the recipe estimate ("Dahlia" plans cover "Dahlia Cafe au Lait"). */
    static boolean cropMatches(String plantingCrop, String demandCrop) {
        String crop = normalize(plantingCrop);
        String requested = normalize(demandCrop);
        return crop.equals(requested) || requested.startsWith(crop + " ") || crop.startsWith(requested + " ");
    }

    /** Stems a set of plantings is projected to yield from {@code from} through {@code to}. */
    static double projected(List<Planting> plantings, LocalDate from, LocalDate to) {
        double total = 0;
        for (Planting planting : plantings) {
            LocalDate start = planting.harvestStart().isAfter(from) ? planting.harvestStart() : from;
            LocalDate end = planting.harvestEnd().isBefore(to) ? planting.harvestEnd() : to;
            if (end.isBefore(start)) {
                continue;
            }
            long windowDays = ChronoUnit.DAYS.between(planting.harvestStart(), planting.harvestEnd()) + 1;
            long overlapDays = ChronoUnit.DAYS.between(start, end) + 1;
            total += planting.expectedStems() * overlapDays / (double) Math.max(1, windowDays);
        }
        return total;
    }

    private static final class CropDemand {
        private final String display;
        private final TreeMap<LocalDate, DateBucket> dates = new TreeMap<>();

        CropDemand(String display) {
            this.display = display.trim();
        }

        void add(Need need) {
            dates.computeIfAbsent(need.date(), DateBucket::new).add(need);
        }

        /** Confirmed stems first (by date), then drafts, cooler before forecast harvest. */
        void allocate(LocalDate from, int coolerStock, List<Planting> plantings) {
            int[] cooler = {coolerStock};
            List<LocalDate> order = new ArrayList<>(dates.keySet());
            for (boolean confirmedPass : new boolean[] {true, false}) {
                for (LocalDate date : order) {
                    DateBucket bucket = dates.get(date);
                    int need = confirmedPass ? bucket.confirmed : bucket.draft;
                    if (need == 0) {
                        continue;
                    }
                    int fromCooler = Math.min(need, cooler[0]);
                    cooler[0] -= fromCooler;
                    int harvest = Math.min(need - fromCooler, harvestAvailable(from, date, plantings));
                    bucket.fromCooler += fromCooler;
                    bucket.toCut += harvest;
                    bucket.shortBy += need - fromCooler - harvest;
                }
            }
        }

        /**
         * Harvest that can still go to a need on {@code date}: for every later date,
         * what has been projected by then minus what is already promised by then.
         */
        private int harvestAvailable(LocalDate from, LocalDate date, List<Planting> plantings) {
            if (plantings.isEmpty()) {
                return 0;
            }
            int available = Integer.MAX_VALUE;
            for (LocalDate checkpoint : dates.tailMap(date, true).keySet()) {
                int promised = dates.headMap(checkpoint, true).values().stream()
                        .mapToInt(bucket -> bucket.toCut).sum();
                int projected = (int) Math.floor(projected(plantings, from, checkpoint) + 1e-9);
                available = Math.min(available, projected - promised);
            }
            return Math.max(0, available);
        }
    }

    private static final class DateBucket {
        private final LocalDate date;
        private int confirmed;
        private int draft;
        private int fromCooler;
        private int toCut;
        private int shortBy;
        private final Set<String> sources = new LinkedHashSet<>();

        DateBucket(LocalDate date) {
            this.date = date;
        }

        void add(Need need) {
            if (need.confirmed()) {
                confirmed += need.stems();
            } else {
                draft += need.stems();
            }
            if (!need.source().isBlank()) {
                sources.add(need.source());
            }
        }

        Line toLine(String crop) {
            Action action = shortBy > 0 ? Action.BUY : toCut > 0 ? Action.CUT : Action.FROM_COOLER;
            return new Line(date, crop, confirmed + draft, draft, fromCooler, toCut, shortBy, action,
                    String.join("; ", sources));
        }
    }

    /** Keeps spreadsheet apps from treating farm text as a formula. */
    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            return "'" + text;
        }
        return text;
    }

    private static String quantity(double value) {
        return Math.rint(value) == value ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
