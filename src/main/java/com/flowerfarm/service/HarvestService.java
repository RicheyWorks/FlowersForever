package com.flowerfarm.service;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.repository.HarvestJpaRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
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

    private final HarvestJpaRepository repository;
    private final InventoryService inventoryService;
    private final SyncHistoryService syncHistoryService;

    public HarvestService(HarvestJpaRepository repository,
                          InventoryService inventoryService,
                          SyncHistoryService syncHistoryService) {
        this.repository = repository;
        this.inventoryService = inventoryService;
        this.syncHistoryService = syncHistoryService;
    }

    @Transactional(readOnly = true)
    public List<HarvestEntry> getAll() {
        return new ArrayList<>(repository.findAllByOrderByHarvestDateDescIdDesc());
    }

    @Transactional(readOnly = true)
    public Optional<HarvestEntry> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<HarvestEntry> findBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Both from and to dates are required.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to date must be on or after from date.");
        }
        return new ArrayList<>(repository.findByHarvestDateBetweenOrderByHarvestDateDescIdDesc(from, to));
    }

    @Transactional(readOnly = true)
    public List<HarvestEntry> searchByCrop(String crop) {
        if (crop == null || crop.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(
                repository.findByCropNameContainingIgnoreCaseOrderByHarvestDateDesc(crop.trim()));
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
        List<HarvestEntry> saved = new ArrayList<>();
        for (HarvestEntry e : entries) {
            if (e == null) {
                continue;
            }
            if (e.getCropName() == null || e.getCropName().isBlank()) {
                continue;
            }
            if (e.getQuantity() <= 0) {
                continue;
            }
            saved.add(add(e));
        }
        if (saved.isEmpty()) {
            throw new IllegalArgumentException("No valid harvest rows in batch (need crop + qty > 0).");
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
        entry.setId(null);
        HarvestEntry saved = repository.save(entry);

        int amount = (int) Math.round(saved.getQuantity());
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
        HarvestEntry existing = repository.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No harvest entry with id=" + id));

        String oldCrop = existing.getCropName();
        int oldQty = (int) Math.round(existing.getQuantity());
        String newCrop = incoming.getCropName() == null ? "" : incoming.getCropName().trim();
        int newQty = (int) Math.round(incoming.getQuantity());
        String newUnit = incoming.getUnit();

        if (newCrop.isBlank()) {
            throw new IllegalArgumentException("Crop name is required.");
        }
        if (incoming.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
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
                + " → " + saved.getCropName() + "×" + (int) Math.round(saved.getQuantity());
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
        HarvestEntry existing = repository.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No harvest entry with id=" + id));

        int amount = (int) Math.round(existing.getQuantity());
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
        for (HarvestEntry e : repository.findAllByOrderByHarvestDateDescIdDesc()) {
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
        StringBuilder sb = new StringBuilder();
        sb.append("bed,totalQuantity,entryCount,firstDate,lastDate,crop,cropQuantity\n");
        for (BedProduction b : report.beds()) {
            if (b.byCrop().isEmpty()) {
                sb.append(csv(b.bed())).append(',')
                        .append(b.totalQuantity()).append(',')
                        .append(b.entryCount()).append(',')
                        .append(b.firstDate()).append(',')
                        .append(b.lastDate()).append(",,\n");
                continue;
            }
            for (Map.Entry<String, Double> crop : b.byCrop().entrySet()) {
                sb.append(csv(b.bed())).append(',')
                        .append(b.totalQuantity()).append(',')
                        .append(b.entryCount()).append(',')
                        .append(b.firstDate()).append(',')
                        .append(b.lastDate()).append(',')
                        .append(csv(crop.getKey())).append(',')
                        .append(crop.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Printable bed / field production PDF (ranked beds + crop mix).
     */
    public byte[] generateBedProductionPdf(BedProductionReport report) {
        if (report == null) {
            throw new IllegalArgumentException("report is required.");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.LETTER, 40, 40, 48, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Color brandGreen = new Color(34, 100, 54);
            Color brandSoft = new Color(232, 245, 233);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandGreen);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, brandGreen);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font banner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    "FlowersForever  ·  Port Orchard / Kitsap County  ·  Bed Production",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Bed / Field Production Report", titleFont));
            doc.add(new Paragraph(
                    "Period: " + report.from() + "  →  " + report.to()
                            + "     ·     Generated: " + LocalDate.now(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Beds", String.valueOf(report.bedCount()), brandSoft));
            summary.addCell(summaryCell("Harvest logs", String.valueOf(report.entryCount()), brandSoft));
            summary.addCell(summaryCell("Total qty",
                    String.format(Locale.US, "%,.1f", report.grandTotal()), brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Beds ranked by quantity", h2));
            doc.add(Chunk.NEWLINE);
            if (report.beds().isEmpty()) {
                doc.add(new Paragraph(
                        "No harvest rows in range — log cuts with a Bed / field value.", body));
            } else {
                PdfPTable bedTable = new PdfPTable(new float[]{0.7f, 2.5f, 1.3f, 1.0f, 2.5f, 2.2f});
                bedTable.setWidthPercentage(100);
                headerCell(bedTable, "#");
                headerCell(bedTable, "Bed / Field");
                headerCell(bedTable, "Qty");
                headerCell(bedTable, "Logs");
                headerCell(bedTable, "Top crop");
                headerCell(bedTable, "Dates");
                int rank = 1;
                for (BedProduction b : report.beds()) {
                    String topCrop = "—";
                    if (!b.byCrop().isEmpty()) {
                        Map.Entry<String, Double> first = b.byCrop().entrySet().iterator().next();
                        topCrop = first.getKey() + " ("
                                + String.format(Locale.US, "%.0f", first.getValue()) + ")";
                    }
                    String dates = (b.firstDate().isEmpty() ? "?" : b.firstDate())
                            + " → "
                            + (b.lastDate().isEmpty() ? "?" : b.lastDate());
                    bedTable.addCell(cell(String.valueOf(rank++), body));
                    bedTable.addCell(cell(b.bed(), body));
                    bedTable.addCell(cell(String.format(Locale.US, "%.1f", b.totalQuantity()), body));
                    bedTable.addCell(cell(String.valueOf(b.entryCount()), body));
                    bedTable.addCell(cell(topCrop, body));
                    bedTable.addCell(cell(dates, body));
                }
                doc.add(bedTable);
            }

            if (!report.beds().isEmpty()) {
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph("2. Crop mix by bed", h2));
                doc.add(Chunk.NEWLINE);
                for (BedProduction b : report.beds()) {
                    doc.add(new Paragraph(
                            b.bed() + "  ·  qty " + String.format(Locale.US, "%,.1f", b.totalQuantity()),
                            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, brandGreen)));
                    if (b.byCrop().isEmpty()) {
                        doc.add(new Paragraph("  (no crop detail)", small));
                    } else {
                        PdfPTable crops = new PdfPTable(new float[]{3, 1.5f});
                        crops.setWidthPercentage(60);
                        headerCell(crops, "Crop");
                        headerCell(crops, "Qty");
                        for (Map.Entry<String, Double> c : b.byCrop().entrySet()) {
                            crops.addCell(cell(c.getKey(), body));
                            crops.addCell(cell(String.format(Locale.US, "%.1f", c.getValue()), body));
                        }
                        doc.add(crops);
                    }
                    doc.add(Chunk.NEWLINE);
                }
            }

            doc.add(new Paragraph(
                    "Tip: consistent bed names (Bed A, Tunnel 1) make this report sharper.",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated bed production PDF {} → {} ({} beds, qty {})",
                    report.from(), report.to(), report.bedCount(), report.grandTotal());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build bed production PDF: " + e.getMessage(), e);
        }
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
                        .thenComparing(e -> e.getId() == null ? 0L : e.getId()))
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
        if (report == null) {
            throw new IllegalArgumentException("report is required.");
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.LETTER, 40, 40, 48, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Color brandGreen = new Color(34, 100, 54);
            Color brandSoft = new Color(232, 245, 233);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandGreen);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, brandGreen);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font banner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    "FlowersForever  ·  Port Orchard / Kitsap  ·  Harvest Log",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Harvest Log", titleFont));
            doc.add(new Paragraph(
                    "Period: " + report.from() + "  →  " + report.to()
                            + "     ·     Generated: " + LocalDate.now(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Entries", String.valueOf(report.entryCount()), brandSoft));
            summary.addCell(summaryCell("Total qty",
                    String.format(Locale.US, "%,.1f", report.totalQuantity()), brandSoft));
            summary.addCell(summaryCell("Crops",
                    String.valueOf(report.byCrop().size()), brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. By crop", h2));
            doc.add(Chunk.NEWLINE);
            if (report.byCrop().isEmpty()) {
                doc.add(new Paragraph("No harvests in this period.", body));
            } else {
                PdfPTable crops = new PdfPTable(new float[]{3, 1.5f});
                crops.setWidthPercentage(55);
                headerCell(crops, "Crop");
                headerCell(crops, "Qty");
                for (Map.Entry<String, Double> c : report.byCrop().entrySet()) {
                    crops.addCell(cell(c.getKey(), body));
                    crops.addCell(cell(String.format(Locale.US, "%,.1f", c.getValue()), body));
                }
                doc.add(crops);
            }
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("2. Detail log (chronological)", h2));
            doc.add(Chunk.NEWLINE);
            if (report.entries().isEmpty()) {
                doc.add(new Paragraph("No rows — log cuts after morning harvest.", body));
            } else {
                PdfPTable t = new PdfPTable(new float[]{1.3f, 2.2f, 1.0f, 1.0f, 1.4f, 2.0f});
                t.setWidthPercentage(100);
                headerCell(t, "Date");
                headerCell(t, "Crop");
                headerCell(t, "Qty");
                headerCell(t, "Unit");
                headerCell(t, "Bed");
                headerCell(t, "Notes");
                for (HarvestEntry e : report.entries()) {
                    t.addCell(cell(e.getHarvestDate() == null ? "" : e.getHarvestDate().toString(), body));
                    t.addCell(cell(e.getCropName(), body));
                    t.addCell(cell(String.format(Locale.US, "%,.1f", e.getQuantity()), body));
                    t.addCell(cell(e.getUnit() == null ? "" : e.getUnit(), body));
                    t.addCell(cell(e.getBedOrField() == null || e.getBedOrField().isBlank()
                            ? "—" : e.getBedOrField(), body));
                    t.addCell(cell(e.getNotes() == null ? "" : e.getNotes(), body));
                }
                doc.add(t);
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "Tip: bed production PDF ranks fields; this sheet is the raw cut list for the cooler.",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated harvest log PDF {} → {} ({} entries, qty {})",
                    report.from(), report.to(), report.entryCount(), report.totalQuantity());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build harvest log PDF: " + e.getMessage(), e);
        }
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

    private static void headerCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        cell.setBackgroundColor(new Color(34, 100, 54));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4);
        cell.setBorderColor(new Color(200, 210, 200));
        return cell;
    }

    private static PdfPCell summaryCell(String label, String value, Color bg) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        p.add(new Chunk(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(bg);
        cell.setPadding(8);
        cell.setBorderColor(new Color(180, 200, 180));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
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

    private static String csv(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }
        if (entries == null) {
            entries = List.of();
        }
        try (java.io.BufferedWriter bw = new java.io.BufferedWriter(new java.io.FileWriter(filename.trim(), java.nio.charset.StandardCharsets.UTF_8))) {
            bw.write("Id,Date,Crop,Quantity,Unit,BedOrField,Notes");
            bw.newLine();
            for (HarvestEntry e : entries) {
                bw.write(String.format("%s,%s,%s,%.2f,%s,%s,\"%s\"",
                        e.getId() == null ? "" : e.getId(),
                        e.getHarvestDate(),
                        csvEscape(e.getCropName()),
                        e.getQuantity(),
                        csvEscape(e.getUnit()),
                        csvEscape(e.getBedOrField()),
                        e.getNotes() == null ? "" : e.getNotes().replace("\"", "\"\"")));
                bw.newLine();
            }
            log.info("Harvest log exported to '{}' ({} row(s)).", filename, entries.size());
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Harvest export failed: " + ex.getMessage(), ex);
        }
    }

    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
