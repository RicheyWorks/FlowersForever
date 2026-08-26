package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.repository.HarvestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Harvest logging for PNW flower production — stems, bunches, beds, market prep.
 * Logging a harvest <em>increases</em> matching inventory (mirror of order fulfill).
 */
@Service
public class HarvestService {

    private static final Logger log = LoggerFactory.getLogger(HarvestService.class);
    public static final int MAX_BATCH_SIZE = 500;
    private static final long MAX_QUERY_DAYS = 3_660;

    private final HarvestRepository repository;
    private final InventoryService inventoryService;
    private final SyncHistoryService syncHistoryService;
    private final HarvestReportGenerator reportGenerator;
    private final HarvestCsvExporter csvExporter;

    public HarvestService(HarvestRepository repository,
                          InventoryService inventoryService,
                          SyncHistoryService syncHistoryService,
                          HarvestReportGenerator reportGenerator,
                          HarvestCsvExporter csvExporter) {
        this.repository = repository;
        this.inventoryService = inventoryService;
        this.syncHistoryService = syncHistoryService;
        this.reportGenerator = reportGenerator;
        this.csvExporter = csvExporter;
    }

    @Transactional(readOnly = true)
    public List<HarvestEntry> getAll() {
        return new ArrayList<>(repository.findAllNewestFirst());
    }

    @Transactional(readOnly = true)
    public Optional<HarvestEntry> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * Identify harvest crop names that will cause automatic zero-price Inventory
     * SKU creation. Intended for background UI previews immediately before save.
     */
    @Transactional(readOnly = true)
    public List<String> missingInventoryProducts(List<HarvestEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        Map<String, String> uniqueNames = new LinkedHashMap<>();
        for (HarvestEntry entry : entries) {
            if (entry != null && entry.getCropName() != null
                    && !entry.getCropName().isBlank()) {
                String name = entry.getCropName().trim();
                uniqueNames.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return uniqueNames.values().stream()
                .filter(name -> inventoryService.findByNameIgnoreCase(name).isEmpty())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HarvestEntry> findBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both from and to dates are required.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to date must be on or after from date.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_QUERY_DAYS) {
            throw new IllegalArgumentException("Date range cannot exceed 10 years.");
        }
        return new ArrayList<>(repository.findBetween(from, to));
    }

    @Transactional(readOnly = true)
    public List<HarvestEntry> searchByCrop(String crop) {
        if (crop == null || crop.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(
                repository.searchByCrop(crop.trim()));
    }

    /**
     * Batch-log multiple harvest rows in one transaction (shared date/bed/notes optional).
     * Each line still increments inventory and is individually audited.
     */
    @Transactional
    public List<HarvestEntry> addBatch(List<HarvestEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Batch must contain at least one harvest row.");
        }
        if (entries.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Batch cannot contain more than " + MAX_BATCH_SIZE + " harvest rows.");
        }
        for (HarvestEntry e : entries) {
            if (e == null) {
                throw new IllegalArgumentException("Batch harvest rows must not be null.");
            }
            validateHarvestDate(e.getHarvestDate());
            inventoryQuantityFor(e.getQuantity());
        }
        List<HarvestEntry> saved = new ArrayList<>();
        for (HarvestEntry e : entries) {
            saved.add(add(e));
        }
        syncHistoryService.record(
                "harvest",
                "HARVEST_BATCH",
                true,
                "Batch harvest logged: " + saved.size() + " row(s)",
                saved.stream().map(h -> h.getCropName() + "×" + h.getQuantity())
                        .reduce((a, b) -> a + "; " + b).orElse(""),
                saved.size()
        );
        log.info("[harvest] Batch logged {} row(s).", saved.size());
        return saved;
    }

    /**
     * Saves the harvest entry and increments inventory for the crop name.
     * Creates a new inventory SKU when none exists. Records {@code HARVEST_LOG} audit.
     */
    @Transactional
    public HarvestEntry add(HarvestEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Harvest entry must not be null.");
        }
        validateHarvestDate(entry.getHarvestDate());
        entry.setId(null);
        HarvestEntry saved = repository.save(entry);

        int amount = inventoryQuantityFor(saved.getQuantity());
        Item stock = inventoryService.incrementQuantityByName(
                saved.getCropName(),
                amount,
                saved.getUnit(),
                "From harvest " + saved.getHarvestDate()
                        + (saved.getBedOrField().isBlank() ? "" : " @ " + saved.getBedOrField())
        );

        String msg = "Harvest logged: " + saved.getCropName() + " × " + amount + " " + saved.getUnit()
                + " → inventory now " + stock.getQuantity();
        syncHistoryService.record(
                "harvest",
                "HARVEST_LOG",
                true,
                msg,
                "harvestId=" + saved.getId() + ", inventoryId=" + stock.getId()
                        + ", bed=" + saved.getBedOrField(),
                amount
        );
        log.info("[harvest] {}", msg);
        return saved;
    }

    /**
     * Updates a harvest entry and corrects inventory for crop/qty changes.
     * Same crop → adjust by delta; crop rename → reverse old, apply new.
     * Records {@code HARVEST_EDIT} in the audit trail.
     */
    @Transactional
    public HarvestEntry update(Long id, HarvestEntry incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("Harvest entry must not be null.");
        }
        validateHarvestDate(incoming.getHarvestDate());
        HarvestEntry existing = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No harvest entry with id=" + id));

        String oldCrop = existing.getCropName();
        int oldQty = inventoryQuantityFor(existing.getQuantity());
        String newCrop = incoming.getCropName() == null ? "" : incoming.getCropName().trim();
        int newQty = inventoryQuantityFor(incoming.getQuantity());
        String newUnit = incoming.getUnit();

        if (newCrop.isBlank()) {
            throw new IllegalArgumentException("Crop name is required.");
        }
        // Apply inventory correction before persisting harvest fields
        applyInventoryEdit(oldCrop, oldQty, newCrop, newQty, newUnit, existing.getHarvestDate());

        existing.setHarvestDate(incoming.getHarvestDate());
        existing.setCropName(incoming.getCropName());
        existing.setQuantity(incoming.getQuantity());
        existing.setUnit(incoming.getUnit());
        existing.setBedOrField(incoming.getBedOrField());
        existing.setNotes(incoming.getNotes());
        HarvestEntry saved = repository.save(existing);

        String msg = "Harvest edited id=" + id + ": " + oldCrop + "×" + oldQty
                + " → " + saved.getCropName() + "×" + newQty;
        syncHistoryService.record(
                "harvest",
                "HARVEST_EDIT",
                true,
                msg,
                "notes=" + saved.getNotes() + ", bed=" + saved.getBedOrField(),
                Math.abs(newQty - oldQty)
        );
        log.info("[harvest] {}", msg);
        return saved;
    }

    /**
     * Converts a recorded harvest quantity to the whole units held by inventory.
     * Keeping this rule public lets every input surface preview the same adjustment
     * that the transaction will apply.
     */
    public static int inventoryQuantityFor(double quantity) {
        if (!Double.isFinite(quantity) || quantity <= 0 || quantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Quantity must be a finite number greater than zero and no more than "
                            + Integer.MAX_VALUE + ".");
        }
        int rounded = (int) Math.round(quantity);
        if (rounded <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must round to at least one inventory unit; enter 0.5 or more.");
        }
        return rounded;
    }

    private static void validateHarvestDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Harvest date is required.");
        }
        LocalDate today = LocalDate.now();
        if (date.isAfter(today)) {
            throw new IllegalArgumentException(
                    "Harvest date cannot be after today (" + today + ").");
        }
    }

    private void applyInventoryEdit(String oldCrop, int oldQty, String newCrop, int newQty,
                                    String unit, LocalDate harvestDate) {
        boolean sameCrop = oldCrop != null && oldCrop.equalsIgnoreCase(newCrop);
        if (sameCrop) {
            int delta = newQty - oldQty;
            if (delta > 0) {
                inventoryService.incrementQuantityByName(newCrop, delta, unit,
                        "Harvest edit +" + delta + " on " + harvestDate);
            } else if (delta < 0) {
                inventoryService.decrementQuantityByName(oldCrop, -delta);
            }
            return;
        }
        // Crop changed: reverse old contribution, apply new
        if (oldQty > 0) {
            inventoryService.decrementQuantityByName(oldCrop, oldQty);
        }
        if (newQty > 0) {
            inventoryService.incrementQuantityByName(newCrop, newQty, unit,
                    "Harvest edit reassigned from " + oldCrop);
        }
    }

    /**
     * Deletes a harvest entry and <em>reverses</em> the inventory increase
     * (floored at zero). Records {@code HARVEST_UNDO} in the audit trail.
     */
    @Transactional
    public void delete(Long id) {
        HarvestEntry existing = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No harvest entry with id=" + id));

        int amount = inventoryQuantityFor(existing.getQuantity());
        var stock = inventoryService.decrementQuantityByName(existing.getCropName(), amount);

        repository.deleteById(id);

        String msg = "Harvest deleted (undo): " + existing.getCropName() + " × " + amount
                + (stock.isPresent()
                ? " → inventory now " + stock.get().getQuantity()
                : " → no inventory SKU matched for reverse");
        syncHistoryService.record(
                "harvest",
                "HARVEST_UNDO",
                stock.isPresent(),
                msg,
                "harvestId=" + id + ", crop=" + existing.getCropName()
                        + ", bed=" + existing.getBedOrField(),
                amount
        );
        log.info("[harvest] {}", msg);
    }

    /**
     * Totals quantity by crop for a quick season snapshot (all records).
     */
    @Transactional(readOnly = true)
    public Map<String, Double> totalsByCrop() {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (HarvestEntry e : repository.findAllNewestFirst()) {
            totals.merge(e.getCropName(), e.getQuantity(), Double::sum);
        }
        return totals;
    }

    /** Sum of harvest quantities for the trailing 7 days (inclusive of today). */
    @Transactional(readOnly = true)
    public double totalQuantityLast7Days() {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(6);
        return findBetween(from, to).stream().mapToDouble(HarvestEntry::getQuantity).sum();
    }

    /** Prior 7-day window (days -13 … -7) for week-over-week comparison. */
    @Transactional(readOnly = true)
    public double totalQuantityPrior7Days() {
        LocalDate to = LocalDate.now().minusDays(7);
        LocalDate from = to.minusDays(6);
        return findBetween(from, to).stream().mapToDouble(HarvestEntry::getQuantity).sum();
    }

    /**
     * Percent change vs prior week. {@code null} when prior week was zero
     * (undefined %); use UI to show "new" / "n/a".
     */
    @Transactional(readOnly = true)
    public Double weekOverWeekPercentChange() {
        double current = totalQuantityLast7Days();
        double prior = totalQuantityPrior7Days();
        if (prior <= 0) {
            return null;
        }
        return ((current - prior) / prior) * 100.0;
    }

    /**
     * Daily harvest totals for the last 7 days (index 0 = 6 days ago, 6 = today).
     * Used by dashboard sparkline.
     */
    @Transactional(readOnly = true)
    public double[] dailyQuantitiesLast7Days() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        Map<LocalDate, Double> byDay = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            byDay.put(from.plusDays(i), 0.0);
        }
        for (HarvestEntry e : findBetween(from, today)) {
            byDay.merge(e.getHarvestDate(), e.getQuantity(), Double::sum);
        }
        double[] out = new double[7];
        int i = 0;
        for (double v : byDay.values()) {
            out[i++] = v;
        }
        return out;
    }

    /**
     * Filter helper for UI: optional crop substring + optional inclusive date range.
     * Null/blank crop → all crops; null from/to → unbounded on that side.
     */
    @Transactional(readOnly = true)
    public List<HarvestEntry> filter(String cropQuery, LocalDate from, LocalDate to) {
        return filter(cropQuery, null, null, from, to);
    }

    /**
     * Filter with optional bed/field substring (case-insensitive contains).
     */
    @Transactional(readOnly = true)
    public List<HarvestEntry> filter(String cropQuery, String bedQuery, LocalDate from, LocalDate to) {
        return filter(cropQuery, bedQuery, null, from, to);
    }

    /**
     * Full filter: crop, bed, notes substrings (any blank = ignore) + optional date bounds.
     */
    @Transactional(readOnly = true)
    public List<HarvestEntry> filter(String cropQuery, String bedQuery, String notesQuery,
                                     LocalDate from, LocalDate to) {
        List<HarvestEntry> base = getAll();
        String q = cropQuery == null ? "" : cropQuery.trim().toLowerCase();
        String bed = bedQuery == null ? "" : bedQuery.trim().toLowerCase();
        String notes = notesQuery == null ? "" : notesQuery.trim().toLowerCase();
        return base.stream()
                .filter(e -> q.isEmpty()
                        || (e.getCropName() != null && e.getCropName().toLowerCase().contains(q)))
                .filter(e -> bed.isEmpty()
                        || (e.getBedOrField() != null && e.getBedOrField().toLowerCase().contains(bed)))
                .filter(e -> notes.isEmpty()
                        || (e.getNotes() != null && e.getNotes().toLowerCase().contains(notes)))
                .filter(e -> from == null || !e.getHarvestDate().isBefore(from))
                .filter(e -> to == null || !e.getHarvestDate().isAfter(to))
                .toList();
    }

    /** Totals by crop for a filtered subset (UI season panel when filter active). */
    public Map<String, Double> totalsByCrop(List<HarvestEntry> entries) {
        Map<String, Double> totals = new LinkedHashMap<>();
        if (entries == null) {
            return totals;
        }
        for (HarvestEntry e : entries) {
            totals.merge(e.getCropName(), e.getQuantity(), Double::sum);
        }
        return totals;
    }

    /** One bed/field production row with crop breakdown. */
    public record BedProduction(
            String bed,
            double totalQuantity,
            int entryCount,
            Map<String, Double> byCrop,
            String firstDate,
            String lastDate
    ) {}

    /** Farm-wide bed production report for a date window. */
    public record BedProductionReport(
            String from,
            String to,
            int bedCount,
            int entryCount,
            double grandTotal,
            List<BedProduction> beds,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", from);
            m.put("to", to);
            m.put("bedCount", bedCount);
            m.put("entryCount", entryCount);
            m.put("grandTotal", grandTotal);
            m.put("beds", beds.stream().map(b -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("bed", b.bed());
                row.put("totalQuantity", b.totalQuantity());
                row.put("entryCount", b.entryCount());
                row.put("byCrop", b.byCrop());
                row.put("firstDate", b.firstDate());
                row.put("lastDate", b.lastDate());
                return row;
            }).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    /**
     * Aggregate harvest quantity by bed/field (blank bed → {@code (unassigned)}).
     *
     * @param from inclusive start (null = unbounded)
     * @param to   inclusive end (null = unbounded)
     */
    @Transactional(readOnly = true)
    public BedProductionReport productionByBed(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from.");
        }
        List<HarvestEntry> entries = filter(null, null, null, from, to);
        Map<String, List<HarvestEntry>> byBed = new LinkedHashMap<>();
        for (HarvestEntry e : entries) {
            String bed = (e.getBedOrField() == null || e.getBedOrField().isBlank())
                    ? "(unassigned)"
                    : e.getBedOrField().trim();
            byBed.computeIfAbsent(bed, k -> new ArrayList<>()).add(e);
        }

        List<BedProduction> beds = new ArrayList<>();
        double grand = 0;
        for (Map.Entry<String, List<HarvestEntry>> en : byBed.entrySet()) {
            List<HarvestEntry> rows = en.getValue();
            Map<String, Double> crops = new LinkedHashMap<>();
            double total = 0;
            LocalDate first = null;
            LocalDate last = null;
            for (HarvestEntry r : rows) {
                total += r.getQuantity();
                crops.merge(r.getCropName() == null ? "(unknown)" : r.getCropName(),
                        r.getQuantity(), Double::sum);
                if (r.getHarvestDate() != null) {
                    if (first == null || r.getHarvestDate().isBefore(first)) {
                        first = r.getHarvestDate();
                    }
                    if (last == null || r.getHarvestDate().isAfter(last)) {
                        last = r.getHarvestDate();
                    }
                }
            }
            // sort crops by qty desc for readability
            Map<String, Double> sortedCrops = crops.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .collect(LinkedHashMap::new,
                            (m, e) -> m.put(e.getKey(), round1(e.getValue())),
                            LinkedHashMap::putAll);
            grand += total;
            beds.add(new BedProduction(
                    en.getKey(),
                    round1(total),
                    rows.size(),
                    sortedCrops,
                    first != null ? first.toString() : "",
                    last != null ? last.toString() : ""
            ));
        }
        beds.sort(Comparator.comparing(BedProduction::totalQuantity).reversed()
                .thenComparing(BedProduction::bed, String.CASE_INSENSITIVE_ORDER));

        String fromStr = from != null ? from.toString() : "all";
        String toStr = to != null ? to.toString() : "all";
        String text = formatBedProductionText(fromStr, toStr, beds, grand, entries.size());
        return new BedProductionReport(
                fromStr, toStr, beds.size(), entries.size(), round1(grand),
                List.copyOf(beds), text);
    }

    /** Trailing 7 days bed production (inclusive of today). */
    @Transactional(readOnly = true)
    public BedProductionReport productionByBedLast7Days() {
        LocalDate to = LocalDate.now();
        return productionByBed(to.minusDays(6), to);
    }

    public String exportBedProductionCsv(BedProductionReport report) {
        return reportGenerator.exportBedProductionCsv(report);
    }

    /**
     * Printable bed / field production PDF (ranked beds + crop mix).
     */
    public byte[] generateBedProductionPdf(BedProductionReport report) {
        return reportGenerator.generateBedProductionPdf(report);
    }

    /**
     * Chronological harvest log for a date range (barn clipboard sheet).
     * Null {@code from}/{@code to} = trailing 7 days through today.
     */
    public record HarvestLogReport(
            String from,
            String to,
            int entryCount,
            double totalQuantity,
            Map<String, Double> byCrop,
            List<HarvestEntry> entries,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", from);
            m.put("to", to);
            m.put("entryCount", entryCount);
            m.put("totalQuantity", totalQuantity);
            m.put("byCrop", byCrop);
            m.put("entries", entries.stream().map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", e.getId());
                row.put("harvestDate", e.getHarvestDate() == null ? "" : e.getHarvestDate().toString());
                row.put("cropName", e.getCropName());
                row.put("quantity", e.getQuantity());
                row.put("unit", e.getUnit());
                row.put("bedOrField", e.getBedOrField() == null ? "" : e.getBedOrField());
                row.put("notes", e.getNotes() == null ? "" : e.getNotes());
                return row;
            }).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    @Transactional(readOnly = true)
    public HarvestLogReport buildHarvestLogReport(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(6);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to must be on or after from.");
        }
        List<HarvestEntry> entries = findBetween(start, end).stream()
                .sorted(Comparator.comparing(HarvestEntry::getHarvestDate)
                        .thenComparingLong(e -> e.getId() == null ? 0L : e.getId()))
                .toList();
        Map<String, Double> byCrop = new LinkedHashMap<>();
        double total = 0;
        for (HarvestEntry e : entries) {
            total += e.getQuantity();
            String crop = e.getCropName() == null || e.getCropName().isBlank()
                    ? "(unnamed)" : e.getCropName().trim();
            byCrop.merge(crop, e.getQuantity(), Double::sum);
        }
        // Sort crops by qty desc for summary
        List<Map.Entry<String, Double>> cropSorted = new ArrayList<>(byCrop.entrySet());
        cropSorted.sort(Map.Entry.<String, Double>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)));
        Map<String, Double> orderedCrops = new LinkedHashMap<>();
        for (Map.Entry<String, Double> c : cropSorted) {
            orderedCrops.put(c.getKey(), round1(c.getValue()));
        }
        String text = formatHarvestLogText(start.toString(), end.toString(), entries, total, orderedCrops);
        return new HarvestLogReport(
                start.toString(),
                end.toString(),
                entries.size(),
                round1(total),
                orderedCrops,
                List.copyOf(entries),
                text
        );
    }

    /** Trailing 7 days harvest log (clipboard default). */
    @Transactional(readOnly = true)
    public HarvestLogReport buildHarvestLogReportLast7Days() {
        LocalDate to = LocalDate.now();
        return buildHarvestLogReport(to.minusDays(6), to);
    }

    public byte[] generateHarvestLogPdf(HarvestLogReport report) {
        return reportGenerator.generateHarvestLogPdf(report);
    }

    private static String formatHarvestLogText(String from, String to,
                                               List<HarvestEntry> entries,
                                               double total,
                                               Map<String, Double> byCrop) {
        StringBuilder sb = new StringBuilder();
        sb.append("HARVEST LOG — Port Orchard / Kitsap County\n");
        sb.append("═════════════════════════════════════════\n");
        sb.append("Period: ").append(from).append(" → ").append(to).append('\n');
        sb.append(String.format(Locale.US, "Entries: %d  ·  Total qty: %,.1f  ·  Crops: %d%n",
                entries.size(), total, byCrop.size()));
        sb.append('\n');
        sb.append("BY CROP\n");
        sb.append("───────\n");
        if (byCrop.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Map.Entry<String, Double> c : byCrop.entrySet()) {
                sb.append(String.format(Locale.US, "  %-24s %8.1f%n",
                        truncate(c.getKey(), 24), c.getValue()));
            }
        }
        sb.append('\n');
        sb.append("DETAIL\n");
        sb.append("──────\n");
        if (entries.isEmpty()) {
            sb.append("  (no harvests in period)\n");
        } else {
            sb.append(String.format(Locale.US, "%-12s %-18s %8s %-8s %-12s%n",
                    "Date", "Crop", "Qty", "Unit", "Bed"));
            sb.append("-".repeat(62)).append('\n');
            for (HarvestEntry e : entries) {
                sb.append(String.format(Locale.US, "%-12s %-18s %8.1f %-8s %-12s%n",
                        e.getHarvestDate() == null ? "" : e.getHarvestDate().toString(),
                        truncate(e.getCropName(), 18),
                        e.getQuantity(),
                        truncate(e.getUnit() == null ? "" : e.getUnit(), 8),
                        truncate(e.getBedOrField() == null || e.getBedOrField().isBlank()
                                ? "—" : e.getBedOrField(), 12)));
            }
            sb.append("-".repeat(62)).append('\n');
            sb.append(String.format(Locale.US, "TOTAL QTY  %,.1f%n", total));
        }
        sb.append("\nTip: export bed production PDF for ranked fields; this is the cut list.\n");
        return sb.toString();
    }

    private static String formatBedProductionText(String from, String to,
                                                  List<BedProduction> beds,
                                                  double grand, int entryCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("BED / FIELD PRODUCTION — Port Orchard / Kitsap County\n");
        sb.append("════════════════════════════════════════════════════\n");
        sb.append("Period     : ").append(from).append(" → ").append(to).append('\n');
        sb.append("Beds       : ").append(beds.size())
                .append("   Entries: ").append(entryCount)
                .append("   Total qty: ").append(String.format(Locale.US, "%,.1f", grand))
                .append('\n');
        sb.append('\n');
        if (beds.isEmpty()) {
            sb.append("(no harvest rows in range — log cuts with a Bed / field value)\n");
            return sb.toString();
        }
        int rank = 1;
        for (BedProduction b : beds) {
            sb.append(String.format(Locale.US, "%2d. %-18s  qty %8.1f  (%d log%s)  %s → %s%n",
                    rank++, truncate(b.bed(), 18), b.totalQuantity(), b.entryCount(),
                    b.entryCount() == 1 ? "" : "s",
                    b.firstDate().isEmpty() ? "?" : b.firstDate(),
                    b.lastDate().isEmpty() ? "?" : b.lastDate()));
            for (Map.Entry<String, Double> c : b.byCrop().entrySet()) {
                sb.append(String.format(Locale.US, "      • %-20s %8.1f%n",
                        truncate(c.getKey(), 20), c.getValue()));
            }
            sb.append('\n');
        }
        sb.append(String.format(Locale.US, "GRAND TOTAL %,.1f%n", grand));
        sb.append("Tip: consistent bed names (Bed A, Tunnel 1) make this report sharper.\n");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /**
     * Export harvest history to CSV (header + one row per entry, newest first).
     */
    @Transactional(readOnly = true)
    public void exportToCsv(String filename) {
        exportToCsv(filename, getAll());
    }

    /** Export a filtered subset (e.g. current UI filter). */
    @Transactional(readOnly = true)
    public void exportToCsv(String filename, List<HarvestEntry> entries) {
        csvExporter.export(filename, entries);
    }
}
