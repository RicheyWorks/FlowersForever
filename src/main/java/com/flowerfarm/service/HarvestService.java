package com.flowerfarm.service;

import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.repository.HarvestRepository;
import com.flowerfarm.util.InventoryUnits;
import com.flowerfarm.util.InventoryQuantity;
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
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

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
    private final FarmProfile farmProfile;

    public HarvestService(HarvestRepository repository,
                          InventoryService inventoryService,
                          SyncHistoryService syncHistoryService,
                          HarvestReportGenerator reportGenerator,
                          HarvestCsvExporter csvExporter,
                          FarmProfile farmProfile) {
        this.repository = repository;
        this.inventoryService = inventoryService;
        this.syncHistoryService = syncHistoryService;
        this.reportGenerator = reportGenerator;
        this.csvExporter = csvExporter;
        this.farmProfile = farmProfile;
    }

    @Transactional(readOnly = true)
    public List<HarvestEntry> getAll() {
        return new ArrayList<>(repository.findAllNewestFirst());
    }

    @Transactional(readOnly = true)
    public Optional<HarvestEntry> findById(Long id) {
        return repository.findById(id);
    }

    /** Exact, case-insensitive lot lookup used by labels, scanners, and AI clients. */
    @Transactional(readOnly = true)
    public Optional<HarvestEntry> findByLotCode(String lotCode) {
        String normalized = normalizeLotCode(lotCode);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Lot code is required.");
        }
        return repository.findByLotCodeIgnoreCase(normalized);
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
        validateBatch(entries);
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
     * Imports historical harvest evidence without changing today's inventory balance.
     * This keeps migration semantics separate from a newly logged operational harvest.
     */
    @Transactional
    public List<HarvestEntry> importHistoricalBatch(List<HarvestEntry> entries) {
        validateBatch(entries);
        validateLotCodes(entries, null);
        List<HarvestEntry> saved = new ArrayList<>();
        for (HarvestEntry entry : entries) {
            entry.setId(null);
            assignLotCode(entry);
            entry.setInventoryContribution(0);
            saved.add(repository.save(entry));
        }
        syncHistoryService.record(
                "harvest",
                "HARVEST_HISTORY_IMPORT",
                true,
                "Historical harvest imported: " + saved.size() + " row(s)",
                "Inventory was not adjusted",
                saved.size());
        return List.copyOf(saved);
    }

    /** Validates a proposed harvest batch without saving records or adjusting inventory. */
    public void validateBatch(List<HarvestEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Batch must contain at least one harvest row.");
        }
        if (entries.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Batch cannot contain more than " + MAX_BATCH_SIZE + " harvest rows.");
        }
        for (HarvestEntry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("Batch harvest rows must not be null.");
            }
            validateHarvestDate(entry.getHarvestDate());
            inventoryQuantityFor(entry.getQuantity(), entry.getUnit());
            InventoryQuantity.requireForUnit(entry.getLossQuantity(), entry.getUnit());
            validateLossQuantity(entry.getLossQuantity());
        }
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
        validateLossQuantity(entry.getLossQuantity());
        inventoryQuantityFor(entry.getQuantity(), entry.getUnit());
        InventoryQuantity.requireForUnit(entry.getLossQuantity(), entry.getUnit());
        entry.setId(null);
        validateLotCodes(List.of(entry), null);
        assignLotCode(entry);
        entry.setInventoryContribution(entry.getQuantity());
        HarvestEntry saved = repository.save(entry);

        double amount = inventoryQuantityFor(saved.getQuantity(), saved.getUnit());
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
                "harvestId=" + saved.getId() + ", lot=" + saved.getLotCode()
                        + ", grade=" + saved.getGrade() + ", loss=" + saved.getLossQuantity()
                        + ", storage=" + saved.getStorageLocation()
                        + ", inventoryId=" + stock.getId() + ", bed=" + saved.getBedOrField(),
                1
        );
        log.info("[harvest] {}", msg);
        return saved;
    }

    /**
     * Updates a harvest entry and corrects inventory for crop/qty changes.
     * Same crop → adjust by delta; crop rename → reverse old, apply new.
     * A same-crop change to a different unit requires an explicit conversion.
     * Records {@code HARVEST_EDIT} in the audit trail.
     */
    @Transactional
    public HarvestEntry update(Long id, HarvestEntry incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("Harvest entry must not be null.");
        }
        validateHarvestDate(incoming.getHarvestDate());
        validateLossQuantity(incoming.getLossQuantity());
        InventoryQuantity.requireForUnit(incoming.getLossQuantity(), incoming.getUnit());
        HarvestEntry existing = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No harvest entry with id=" + id));

        String oldCrop = existing.getCropName();
        double oldQty = existing.getInventoryContribution();
        String newCrop = incoming.getCropName() == null ? "" : incoming.getCropName().trim();
        double newQty = inventoryQuantityFor(incoming.getQuantity(), incoming.getUnit());
        String newUnit = incoming.getUnit();

        if (newCrop.isBlank()) {
            throw new IllegalArgumentException("Crop name is required.");
        }
        if (oldCrop.equalsIgnoreCase(newCrop)
                && !InventoryUnits.equivalent(existing.getUnit(), newUnit)) {
            throw new IllegalArgumentException(
                    "Cannot change the unit for an existing harvest of '" + oldCrop
                            + "' from " + existing.getUnit() + " to " + newUnit
                            + ". An explicit inventory conversion is required.");
        }
        if (incoming.getLotCode() == null || incoming.getLotCode().isBlank()) {
            assignLotCode(existing);
            incoming.setLotCode(existing.getLotCode());
        }
        validateLotCodes(List.of(incoming), id);
        // Apply inventory correction before persisting harvest fields
        if (oldQty > 0) {
            applyInventoryEdit(oldCrop, oldQty, newCrop, newQty, newUnit, existing.getHarvestDate());
        }

        existing.setHarvestDate(incoming.getHarvestDate());
        existing.setCropName(incoming.getCropName());
        existing.setQuantity(incoming.getQuantity());
        existing.setInventoryContribution(oldQty > 0 ? newQty : 0);
        existing.setUnit(incoming.getUnit());
        existing.setBedOrField(incoming.getBedOrField());
        existing.setNotes(incoming.getNotes());
        existing.setLotCode(incoming.getLotCode());
        existing.setGrade(incoming.getGrade());
        existing.setLossQuantity(incoming.getLossQuantity());
        existing.setStorageLocation(incoming.getStorageLocation());
        HarvestEntry saved = repository.save(existing);

        String msg = "Harvest edited id=" + id + ": " + oldCrop + "×" + oldQty
                + " → " + saved.getCropName() + "×" + newQty;
        syncHistoryService.record(
                "harvest",
                "HARVEST_EDIT",
                true,
                msg,
                "lot=" + saved.getLotCode() + ", grade=" + saved.getGrade()
                        + ", loss=" + saved.getLossQuantity()
                        + ", storage=" + saved.getStorageLocation()
                        + ", notes=" + saved.getNotes() + ", bed=" + saved.getBedOrField(),
                1
        );
        log.info("[harvest] {}", msg);
        return saved;
    }

    /**
     * Legacy whole-count validation. Measured harvests must supply their unit.
     */
    public static int inventoryQuantityFor(double quantity) {
        return InventoryQuantity.whole(inventoryQuantityFor(quantity, "each"));
    }

    public static double inventoryQuantityFor(double quantity, String unit) {
        if (!Double.isFinite(quantity) || quantity <= 0 || quantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Quantity must be a finite number greater than zero and no more than "
                            + Integer.MAX_VALUE + ".");
        }
        return InventoryQuantity.requireForUnit(quantity, unit);
    }

    private static void validateLossQuantity(double lossQuantity) {
        if (!Double.isFinite(lossQuantity) || lossQuantity < 0
                || lossQuantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Loss quantity must be a finite number from 0 through "
                            + Integer.MAX_VALUE + ".");
        }
    }

    private void validateLotCodes(List<HarvestEntry> entries, Long allowedId) {
        Set<String> batchCodes = new HashSet<>();
        for (HarvestEntry entry : entries) {
            String code = normalizeLotCode(entry.getLotCode());
            if (code.isEmpty()) {
                continue;
            }
            if (!batchCodes.add(code)) {
                throw new IllegalArgumentException("Duplicate harvest lot code in batch: " + code + ".");
            }
            repository.findByLotCodeIgnoreCase(code).ifPresent(existing -> {
                if (allowedId == null || !allowedId.equals(existing.getId())) {
                    throw new IllegalArgumentException(
                            "Harvest lot code already exists: " + code + ".");
                }
            });
        }
    }

    private static void assignLotCode(HarvestEntry entry) {
        if (entry.getLotCode() != null && !entry.getLotCode().isBlank()) {
            return;
        }
        String date = entry.getHarvestDate().toString().replace("-", "");
        String nonce = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        entry.setLotCode("FFH-" + date + "-" + nonce);
    }

    private static String normalizeLotCode(String lotCode) {
        return lotCode == null ? "" : lotCode.trim().toUpperCase(Locale.ROOT);
    }

    private void validateHarvestDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Harvest date is required.");
        }
        LocalDate today = farmProfile.today();
        if (date.isAfter(today)) {
            throw new IllegalArgumentException(
                    "Harvest date cannot be after today (" + today + ").");
        }
    }

    private void applyInventoryEdit(String oldCrop, double oldQty, String newCrop, double newQty,
                                    String unit, LocalDate harvestDate) {
        boolean sameCrop = oldCrop != null && oldCrop.equalsIgnoreCase(newCrop);
        if (sameCrop) {
            double delta = InventoryQuantity.decimal(newQty).subtract(InventoryQuantity.decimal(oldQty)).doubleValue();
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

        double amount = existing.getInventoryContribution();
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
                1
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

    @Transactional(readOnly = true)
    public Map<String, Double> totalsByProductUnit() {
        return totalsByProductUnit(repository.findAllNewestFirst());
    }

    public Map<String, Double> totalsByProductUnit(List<HarvestEntry> entries) {
        return groupByProductUnit(entries);
    }

    public static Map<String, Double> groupByProductUnit(List<HarvestEntry> entries) {
        Map<String, java.math.BigDecimal> exact = new LinkedHashMap<>();
        if (entries != null) {
            for (HarvestEntry entry : entries) {
                String label = entry.getCropName() + " (" + InventoryUnits.canonical(entry.getUnit()) + ")";
                exact.merge(label, java.math.BigDecimal.valueOf(entry.getQuantity()), java.math.BigDecimal::add);
            }
        }
        Map<String, Double> totals = new LinkedHashMap<>();
        exact.forEach((name, value) -> totals.put(name, value.doubleValue()));
        return totals;
    }

    /** Sum of harvest quantities for the trailing 7 days (inclusive of today). */
    @Transactional(readOnly = true)
    public double totalQuantityLast7Days() {
        LocalDate to = farmProfile.today();
        LocalDate from = to.minusDays(6);
        return findBetween(from, to).stream().mapToDouble(HarvestEntry::getQuantity).sum();
    }

    /** Prior 7-day window (days -13 … -7) for week-over-week comparison. */
    @Transactional(readOnly = true)
    public double totalQuantityPrior7Days() {
        LocalDate to = farmProfile.today().minusDays(7);
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
        LocalDate today = farmProfile.today();
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
            String lastDate,
            String quantitySummary,
            Map<String, Double> byProductUnit
    ) {
        public BedProduction(String bed, double totalQuantity, int entryCount,
                Map<String, Double> byCrop, String firstDate, String lastDate) {
            this(bed, totalQuantity, entryCount, byCrop, firstDate, lastDate,
                    InventoryQuantity.format(totalQuantity), byCrop);
        }
    }

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
                row.put("byProductUnit", b.byProductUnit());
                row.put("quantitySummary", b.quantitySummary());
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
                            (m, e) -> m.put(e.getKey(), e.getValue()),
                            LinkedHashMap::putAll);
            grand += total;
            beds.add(new BedProduction(
                    en.getKey(),
                    total,
                    rows.size(),
                    sortedCrops,
                    first != null ? first.toString() : "",
                    last != null ? last.toString() : "",
                    InventoryQuantity.summary(rows, HarvestEntry::getQuantity, HarvestEntry::getUnit),
                    totalsByProductUnit(rows)
            ));
        }
        beds.sort(Comparator.comparing(BedProduction::totalQuantity).reversed()
                .thenComparing(BedProduction::bed, String.CASE_INSENSITIVE_ORDER));

        String fromStr = from != null ? from.toString() : "all";
        String toStr = to != null ? to.toString() : "all";
        String text = formatBedProductionText(fromStr, toStr, beds,
                InventoryQuantity.summary(entries, HarvestEntry::getQuantity, HarvestEntry::getUnit), entries.size());
        return new BedProductionReport(
                fromStr, toStr, beds.size(), entries.size(), grand,
                List.copyOf(beds), text);
    }

    /** Trailing 7 days bed production (inclusive of today). */
    @Transactional(readOnly = true)
    public BedProductionReport productionByBedLast7Days() {
        LocalDate to = farmProfile.today();
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
            double totalLoss = entries.stream()
                    .mapToDouble(HarvestEntry::getLossQuantity).sum();
            m.put("from", from);
            m.put("to", to);
            m.put("entryCount", entryCount);
            m.put("totalQuantity", totalQuantity);
            m.put("totalLossQuantity", totalLoss);
            m.put("totalGrossQuantity", totalQuantity + totalLoss);
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
                row.put("lotCode", e.getLotCode());
                row.put("grade", e.getGrade());
                row.put("lossQuantity", e.getLossQuantity());
                row.put("grossQuantity", e.getGrossQuantity());
                row.put("storageLocation", e.getStorageLocation());
                return row;
            }).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    @Transactional(readOnly = true)
    public HarvestLogReport buildHarvestLogReport(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : farmProfile.today();
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
            orderedCrops.put(c.getKey(), c.getValue());
        }
        String text = formatHarvestLogText(start.toString(), end.toString(), entries, total, orderedCrops);
        return new HarvestLogReport(
                start.toString(),
                end.toString(),
                entries.size(),
                total,
                orderedCrops,
                List.copyOf(entries),
                text
        );
    }

    /** Trailing 7 days harvest log (clipboard default). */
    @Transactional(readOnly = true)
    public HarvestLogReport buildHarvestLogReportLast7Days() {
        LocalDate to = farmProfile.today();
        return buildHarvestLogReport(to.minusDays(6), to);
    }

    public byte[] generateHarvestLogPdf(HarvestLogReport report) {
        return reportGenerator.generateHarvestLogPdf(report);
    }

    @Transactional(readOnly = true)
    public byte[] generateLotLabelPdf(Long harvestId) {
        HarvestEntry entry = repository.findById(harvestId)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No harvest entry with id=" + harvestId));
        return reportGenerator.generateHarvestLotLabelPdf(entry);
    }

    private String formatHarvestLogText(String from, String to,
                                        List<HarvestEntry> entries,
                                        double total,
                                        Map<String, Double> byCrop) {
        StringBuilder sb = new StringBuilder();
        sb.append(farmProfile.documentBanner("Harvest Log").toUpperCase(Locale.ROOT))
                .append('\n');
        sb.append("═════════════════════════════════════════\n");
        sb.append("Period: ").append(from).append(" → ").append(to).append('\n');
        sb.append(String.format(Locale.US,
                "Entries: %d  ·  Marketable: %s  ·  Loss: %s  ·  Crops: %d%n",
                entries.size(), InventoryQuantity.summary(entries, HarvestEntry::getQuantity, HarvestEntry::getUnit),
                InventoryQuantity.summary(entries, HarvestEntry::getLossQuantity, HarvestEntry::getUnit), byCrop.size()));
        sb.append('\n');
        sb.append("BY CROP\n");
        sb.append("───────\n");
        if (byCrop.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (Map.Entry<String, Double> c : totalsByProductUnit(entries).entrySet()) {
                sb.append(String.format(Locale.US, "  %-40s %12s%n",
                        c.getKey(), InventoryQuantity.format(c.getValue())));
            }
        }
        sb.append('\n');
        sb.append("DETAIL\n");
        sb.append("──────\n");
        if (entries.isEmpty()) {
            sb.append("  (no harvests in period)\n");
        } else {
            sb.append(String.format(Locale.US, "%-12s %-16s %8s %-8s %-10s %-12s %-10s%n",
                    "Date", "Crop", "Qty", "Unit", "Grade", "Lot", "Loss"));
            sb.append("-".repeat(86)).append('\n');
            for (HarvestEntry e : entries) {
                sb.append(String.format(Locale.US,
                        "%-12s %-16s %12s %-8s %-10s %-12s %12s%n",
                        e.getHarvestDate() == null ? "" : e.getHarvestDate().toString(),
                        truncate(e.getCropName(), 16),
                        InventoryQuantity.format(e.getQuantity()),
                        truncate(e.getUnit() == null ? "" : e.getUnit(), 8),
                        e.getGrade(),
                        truncate(e.getLotCode(), 12),
                        InventoryQuantity.format(e.getLossQuantity())));
                sb.append("    Source: ").append(
                        e.getBedOrField() == null || e.getBedOrField().isBlank()
                                ? "—" : e.getBedOrField())
                        .append(" · Storage: ").append(
                                e.getStorageLocation() == null || e.getStorageLocation().isBlank()
                                        ? "—" : e.getStorageLocation())
                        .append('\n');
            }
            sb.append("-".repeat(86)).append('\n');
            sb.append(String.format(Locale.US,
                    "TOTAL MARKETABLE  %s · TOTAL LOSS %s%n",
                    InventoryQuantity.summary(entries, HarvestEntry::getQuantity, HarvestEntry::getUnit),
                    InventoryQuantity.summary(entries, HarvestEntry::getLossQuantity, HarvestEntry::getUnit)));
        }
        sb.append("\nTip: export bed production PDF for ranked fields; this is the cut list.\n");
        return sb.toString();
    }

    private String formatBedProductionText(String from, String to,
                                           List<BedProduction> beds,
                                           String quantitySummary, int entryCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(farmProfile.documentBanner("Bed / Field Production")
                .toUpperCase(Locale.ROOT)).append('\n');
        sb.append("════════════════════════════════════════════════════\n");
        sb.append("Period     : ").append(from).append(" → ").append(to).append('\n');
        sb.append("Beds       : ").append(beds.size())
                .append("   Entries: ").append(entryCount)
                .append("   Total qty: ").append(quantitySummary)
                .append('\n');
        sb.append('\n');
        if (beds.isEmpty()) {
            sb.append("(no harvest rows in range — log cuts with a Bed / field value)\n");
            return sb.toString();
        }
        int rank = 1;
        for (BedProduction b : beds) {
            sb.append(String.format(Locale.US, "%2d. %-18s  qty %s  (%d log%s)  %s → %s%n",
                    rank++, truncate(b.bed(), 18), b.quantitySummary(), b.entryCount(),
                    b.entryCount() == 1 ? "" : "s",
                    b.firstDate().isEmpty() ? "?" : b.firstDate(),
                    b.lastDate().isEmpty() ? "?" : b.lastDate()));
            for (Map.Entry<String, Double> c : b.byProductUnit().entrySet()) {
                sb.append(String.format(Locale.US, "      • %-40s %12s%n",
                        c.getKey(), InventoryQuantity.format(c.getValue())));
            }
            sb.append('\n');
        }
        sb.append(String.format(Locale.US, "GRAND TOTAL %s%n", quantitySummary));
        sb.append("Tip: consistent bed names (Bed A, Tunnel 1) make this report sharper.\n");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
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

    /** Serialize rows to a caller-owned writer using the same desktop CSV contract. */
    public void writeCsv(java.io.Writer writer, List<HarvestEntry> entries) throws java.io.IOException {
        csvExporter.writeCsv(writer, entries);
    }
}
