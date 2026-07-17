package com.flowerfarm.service;

import com.flowerfarm.model.Item;
import com.flowerfarm.repository.InventoryRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.awt.Color;
import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Flower-farm inventory operations backed by {@link InventoryRepository}.
 *
 * <p>Default production store is JPA + file H2. On first launch (empty DB) the
 * service seeds from {@code flowerfarm.inventory.seed-csv} when present, otherwise
 * from a small PNW sample set. CSV export remains available for backups and
 * spreadsheet workflows; the CSV connector is unchanged.
 *
 * <p>Index-based edit/delete are preserved for the Swing GUI; id-based methods
 * are preferred for REST and new code.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** Standard CSV header shared by export and legacy seed files. */
    public static final String CSV_HEADER = "Name,Category,Price,Unit,Cost,Quantity,Notes";

    private final InventoryRepository repository;
    private final String seedCsvPath;

    public InventoryService(
            InventoryRepository repository,
            @Value("${flowerfarm.inventory.seed-csv:farm_inventory.csv}") String seedCsvPath) {
        this.repository = repository;
        this.seedCsvPath = seedCsvPath == null || seedCsvPath.isBlank()
                ? "farm_inventory.csv"
                : seedCsvPath.trim();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @PostConstruct
    @Transactional
    public void init() {
        long existing = repository.count();
        if (existing > 0) {
            log.info("InventoryService initialised — {} item(s) already in database.", existing);
            return;
        }

        List<Item> seed = loadSeedFromCsv();
        if (seed.isEmpty()) {
            seed = buildSampleInventory();
            log.info("No seed CSV at '{}' — loading {} sample PNW inventory item(s).",
                    seedCsvPath, seed.size());
        } else {
            log.info("Seeding database from '{}' ({} item(s)).", seedCsvPath, seed.size());
        }
        repository.saveAll(seed);
        log.info("InventoryService initialised — {} item(s) persisted.", repository.count());
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public synchronized List<Item> getAllItems() {
        return new ArrayList<>(repository.findAllOrdered());
    }

    @Transactional(readOnly = true)
    public synchronized Optional<Item> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public synchronized List<Item> searchItems(String query) {
        return new ArrayList<>(repository.search(query));
    }

    @Transactional
    public synchronized Item addItem(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        // Force insert
        item.setId(null);
        Item saved = repository.save(item);
        log.debug("Added item id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Replaces the item at list position {@code index} (ordered by id).
     * Prefer {@link #updateById(Long, Item)} when the id is known.
     */
    @Transactional
    public synchronized Item editItem(int index, Item newItem) {
        if (newItem == null) {
            throw new IllegalArgumentException("Replacement item must not be null.");
        }
        Item existing = itemAtIndex(index);
        existing.copyBusinessFieldsFrom(newItem);
        Item saved = repository.save(existing);
        log.debug("Edited item at index {} id={}", index, saved.getId());
        return saved;
    }

    @Transactional
    public synchronized Item updateById(Long id, Item newItem) {
        if (newItem == null) {
            throw new IllegalArgumentException("Replacement item must not be null.");
        }
        Item existing = repository.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException("No inventory item with id=" + id));
        existing.copyBusinessFieldsFrom(newItem);
        Item saved = repository.save(existing);
        log.debug("Updated item id={}", id);
        return saved;
    }

    @Transactional
    public synchronized void deleteItem(int index) {
        Item existing = itemAtIndex(index);
        repository.deleteById(existing.getId());
        log.debug("Deleted item at index {} id={}", index, existing.getId());
    }

    @Transactional
    public synchronized void deleteById(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new IndexOutOfBoundsException("No inventory item with id=" + id);
        }
        repository.deleteById(id);
        log.debug("Deleted item id={}", id);
    }

    /**
     * Case-insensitive exact name match (first hit). Used by CRM fulfillment.
     */
    @Transactional(readOnly = true)
    public synchronized Optional<Item> findByNameIgnoreCase(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String key = name.trim().toLowerCase();
        return repository.findAllOrdered().stream()
                .filter(i -> i.getName() != null && i.getName().toLowerCase().equals(key))
                .findFirst();
    }

    /**
     * Decrements quantity for the named SKU (floored at zero). Returns empty if
     * no matching inventory row exists.
     */
    @Transactional
    public synchronized Optional<Item> decrementQuantityByName(String name, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Decrement amount cannot be negative.");
        }
        Optional<Item> found = findByNameIgnoreCase(name);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Item item = found.get();
        int next = Math.max(0, item.getQuantity() - amount);
        item.setQuantity(next);
        Item saved = repository.save(item);
        log.info("Inventory '{}' qty → {} (decremented by {})", saved.getName(), next, amount);
        return Optional.of(saved);
    }

    /**
     * Increases quantity for the named SKU. If no SKU exists, creates one under
     * category {@code Flowers/Plants} so harvest logging always grows stock.
     */
    @Transactional
    public synchronized Item incrementQuantityByName(String name, int amount, String unit, String notesHint) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name is required.");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Increment amount cannot be negative.");
        }
        Optional<Item> found = findByNameIgnoreCase(name);
        if (found.isPresent()) {
            Item item = found.get();
            item.setQuantity(item.getQuantity() + amount);
            Item saved = repository.save(item);
            log.info("Inventory '{}' qty → {} (incremented by {})", saved.getName(), saved.getQuantity(), amount);
            return saved;
        }
        String u = (unit == null || unit.isBlank()) ? "stems" : unit.trim();
        String notes = notesHint == null || notesHint.isBlank()
                ? "Auto-created from harvest log"
                : notesHint.trim();
        Item created = new Item(name.trim(), "Flowers/Plants", 0.0, u, 0.0, amount, notes);
        Item saved = repository.save(created);
        log.info("Inventory created '{}' with qty {} from harvest", saved.getName(), amount);
        return saved;
    }

    /**
     * Snapshot of inventory KPIs for the dashboard / API.
     */
    @Transactional(readOnly = true)
    public InventoryKpiSnapshot inventoryKpis(int lowStockThreshold) {
        int threshold = Math.max(0, lowStockThreshold);
        List<Item> items = getAllItems();
        int skuCount = items.size();
        double sellValue = 0;
        double costBasis = 0;
        int lowStock = 0;
        int totalUnits = 0;
        for (Item i : items) {
            sellValue += i.getPrice() * i.getQuantity();
            costBasis += i.getCost() * i.getQuantity();
            totalUnits += i.getQuantity();
            if (i.getQuantity() <= threshold) {
                lowStock++;
            }
        }
        return new InventoryKpiSnapshot(skuCount, sellValue, costBasis, lowStock, totalUnits, threshold);
    }

    public record InventoryKpiSnapshot(
            int skuCount,
            double sellValue,
            double costBasis,
            int lowStockCount,
            int totalUnits,
            int lowStockThreshold
    ) {}

    /**
     * One SKU on the barn reorder sheet: current qty vs threshold and a
     * simple restock suggestion (bring back to at least 2× threshold).
     */
    public record LowStockLine(
            Long id,
            String name,
            String category,
            int quantity,
            String unit,
            double unitPrice,
            double unitCost,
            int threshold,
            int suggestedOrderQty,
            double suggestedOrderCost
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("category", category);
            m.put("quantity", quantity);
            m.put("unit", unit);
            m.put("unitPrice", unitPrice);
            m.put("unitCost", unitCost);
            m.put("threshold", threshold);
            m.put("suggestedOrderQty", suggestedOrderQty);
            m.put("suggestedOrderCost", suggestedOrderCost);
            return m;
        }
    }

    public record LowStockReport(
            LocalDate date,
            int threshold,
            int skuCount,
            int lowStockCount,
            double suggestedOrderCostTotal,
            List<LowStockLine> lines,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date.toString());
            m.put("threshold", threshold);
            m.put("skuCount", skuCount);
            m.put("lowStockCount", lowStockCount);
            m.put("suggestedOrderCostTotal", suggestedOrderCostTotal);
            m.put("lines", lines.stream().map(LowStockLine::toMap).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    /** Items with quantity ≤ threshold, lowest stock first. */
    @Transactional(readOnly = true)
    public List<Item> findLowStock(int threshold) {
        int t = Math.max(0, threshold);
        return getAllItems().stream()
                .filter(i -> i.getQuantity() <= t)
                .sorted(Comparator.comparingInt(Item::getQuantity)
                        .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Barn reorder sheet: SKUs at/under threshold with suggested restock qty
     * (target = max(threshold × 2, threshold + 1) − on-hand).
     */
    @Transactional(readOnly = true)
    public LowStockReport buildLowStockReport(int threshold) {
        int t = Math.max(0, threshold);
        List<Item> all = getAllItems();
        List<LowStockLine> lines = new ArrayList<>();
        double costTotal = 0;
        for (Item i : findLowStock(t)) {
            int target = Math.max(t * 2, t + 1);
            int suggest = Math.max(0, target - i.getQuantity());
            double lineCost = suggest * i.getCost();
            costTotal += lineCost;
            lines.add(new LowStockLine(
                    i.getId(),
                    i.getName(),
                    i.getCategory() == null ? "" : i.getCategory(),
                    i.getQuantity(),
                    i.getUnit() == null ? "" : i.getUnit(),
                    i.getPrice(),
                    i.getCost(),
                    t,
                    suggest,
                    lineCost
            ));
        }
        String text = formatLowStockText(t, all.size(), lines, costTotal);
        return new LowStockReport(
                LocalDate.now(),
                t,
                all.size(),
                lines.size(),
                costTotal,
                List.copyOf(lines),
                text
        );
    }

    public byte[] generateLowStockPdf(LowStockReport report) {
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
            Color warnSoft = new Color(255, 243, 224);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandGreen);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, brandGreen);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font banner = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase(
                    "FlowersForever  ·  Port Orchard / Kitsap  ·  Low-Stock Reorder",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Low-Stock Reorder List", titleFont));
            doc.add(new Paragraph(
                    "Date: " + report.date()
                            + "     ·     Threshold: ≤ " + report.threshold()
                            + "     ·     SKUs: " + report.skuCount(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("Low SKUs",
                    String.valueOf(report.lowStockCount()),
                    report.lowStockCount() > 0 ? warnSoft : brandSoft));
            summary.addCell(summaryCell("Suggest cost $",
                    String.format(Locale.US, "%,.0f", report.suggestedOrderCostTotal()), brandSoft));
            summary.addCell(summaryCell("Threshold",
                    "≤ " + report.threshold(), brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Reorder sheet (lowest first)", h2));
            doc.add(Chunk.NEWLINE);
            if (report.lines().isEmpty()) {
                doc.add(new Paragraph(
                        "All SKUs above threshold — no restock needed today.", body));
            } else {
                PdfPTable t = new PdfPTable(new float[]{2.8f, 1.5f, 0.9f, 1.0f, 1.1f, 1.2f});
                t.setWidthPercentage(100);
                headerCell(t, "SKU");
                headerCell(t, "Category");
                headerCell(t, "On hand");
                headerCell(t, "Unit");
                headerCell(t, "Order qty");
                headerCell(t, "Est. cost");
                for (LowStockLine line : report.lines()) {
                    Color rowBg = line.quantity() == 0 ? new Color(255, 235, 230) : Color.WHITE;
                    t.addCell(cell(line.name(), body, rowBg));
                    t.addCell(cell(line.category(), body, rowBg));
                    t.addCell(cell(String.valueOf(line.quantity()), body, rowBg));
                    t.addCell(cell(line.unit(), body, rowBg));
                    t.addCell(cell(String.valueOf(line.suggestedOrderQty()), body, rowBg));
                    t.addCell(cell(String.format(Locale.US, "%,.2f", line.suggestedOrderCost()), body, rowBg));
                }
                doc.add(t);
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph(String.format(Locale.US,
                        "Suggested restock total (at cost): $%,.2f",
                        report.suggestedOrderCostTotal()), body));
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "Tip: target qty = max(2× threshold, threshold+1). "
                            + "Zero-qty rows are highlighted for harvest priority.",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated low-stock reorder PDF (threshold={}, lines={})",
                    report.threshold(), report.lowStockCount());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build low-stock PDF: " + e.getMessage(), e);
        }
    }

    /**
     * One line on the market / wholesale price list.
     */
    public record PriceListLine(
            Long id,
            String name,
            String category,
            double unitPrice,
            String unit,
            int quantity,
            double lineSellValue,
            double unitCost,
            double lineCostBasis
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("category", category);
            m.put("unitPrice", unitPrice);
            m.put("unit", unit);
            m.put("quantity", quantity);
            m.put("lineSellValue", lineSellValue);
            m.put("unitCost", unitCost);
            m.put("lineCostBasis", lineCostBasis);
            return m;
        }
    }

    public record PriceListReport(
            LocalDate date,
            int skuCount,
            int totalUnits,
            double sellValue,
            double costBasis,
            boolean inStockOnly,
            List<PriceListLine> lines,
            String plainText
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date.toString());
            m.put("skuCount", skuCount);
            m.put("totalUnits", totalUnits);
            m.put("sellValue", sellValue);
            m.put("costBasis", costBasis);
            m.put("inStockOnly", inStockOnly);
            m.put("lines", lines.stream().map(PriceListLine::toMap).toList());
            m.put("plainText", plainText);
            return m;
        }
    }

    /**
     * Market / wholesale price list from inventory.
     * When {@code inStockOnly}, zero-qty SKUs are omitted (booth sheet).
     */
    @Transactional(readOnly = true)
    public PriceListReport buildPriceListReport(boolean inStockOnly) {
        List<Item> items = getAllItems().stream()
                .filter(i -> !inStockOnly || i.getQuantity() > 0)
                .sorted(Comparator
                        .comparing((Item i) -> i.getCategory() == null ? "" : i.getCategory(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Item::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<PriceListLine> lines = new ArrayList<>();
        double sell = 0;
        double cost = 0;
        int units = 0;
        for (Item i : items) {
            double lineSell = i.getPrice() * i.getQuantity();
            double lineCost = i.getCost() * i.getQuantity();
            sell += lineSell;
            cost += lineCost;
            units += i.getQuantity();
            lines.add(new PriceListLine(
                    i.getId(),
                    i.getName(),
                    i.getCategory() == null ? "" : i.getCategory(),
                    i.getPrice(),
                    i.getUnit() == null ? "" : i.getUnit(),
                    i.getQuantity(),
                    lineSell,
                    i.getCost(),
                    lineCost
            ));
        }
        String text = formatPriceListText(inStockOnly, lines, sell, cost, units);
        return new PriceListReport(
                LocalDate.now(),
                lines.size(),
                units,
                sell,
                cost,
                inStockOnly,
                List.copyOf(lines),
                text
        );
    }

    public byte[] generatePriceListPdf(PriceListReport report) {
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
                    "FlowersForever  ·  Port Orchard / Kitsap  ·  Price List",
                    banner));
            bannerCell.setBackgroundColor(brandGreen);
            bannerCell.setPadding(10);
            bannerCell.setBorder(Rectangle.NO_BORDER);
            bannerTable.addCell(bannerCell);
            doc.add(bannerTable);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("Wholesale / Market Price List", titleFont));
            doc.add(new Paragraph(
                    "Date: " + report.date()
                            + "     ·     "
                            + (report.inStockOnly() ? "In-stock only" : "All SKUs")
                            + "     ·     SKUs: " + report.skuCount(),
                    small));
            doc.add(Chunk.NEWLINE);

            PdfPTable summary = new PdfPTable(new float[]{1, 1, 1});
            summary.setWidthPercentage(100);
            summary.addCell(summaryCell("SKUs", String.valueOf(report.skuCount()), brandSoft));
            summary.addCell(summaryCell("Sell value $",
                    String.format(Locale.US, "%,.0f", report.sellValue()), brandSoft));
            summary.addCell(summaryCell("Units on hand",
                    String.valueOf(report.totalUnits()), brandSoft));
            doc.add(summary);
            doc.add(Chunk.NEWLINE);

            doc.add(new Paragraph("1. Catalog (by category)", h2));
            doc.add(Chunk.NEWLINE);
            if (report.lines().isEmpty()) {
                doc.add(new Paragraph(
                        report.inStockOnly()
                                ? "No in-stock SKUs — harvest or clear in-stock filter."
                                : "Inventory is empty.",
                        body));
            } else {
                PdfPTable t = new PdfPTable(new float[]{2.6f, 1.6f, 1.1f, 1.0f, 0.9f, 1.2f});
                t.setWidthPercentage(100);
                headerCell(t, "SKU");
                headerCell(t, "Category");
                headerCell(t, "Unit $");
                headerCell(t, "Unit");
                headerCell(t, "Qty");
                headerCell(t, "Ext. $");
                for (PriceListLine line : report.lines()) {
                    Color bg = line.quantity() <= 0 ? new Color(245, 245, 245) : Color.WHITE;
                    t.addCell(cell(line.name(), body, bg));
                    t.addCell(cell(line.category(), body, bg));
                    t.addCell(cell(String.format(Locale.US, "%,.2f", line.unitPrice()), body, bg));
                    t.addCell(cell(line.unit(), body, bg));
                    t.addCell(cell(String.valueOf(line.quantity()), body, bg));
                    t.addCell(cell(String.format(Locale.US, "%,.2f", line.lineSellValue()), body, bg));
                }
                doc.add(t);
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph(String.format(Locale.US,
                        "Catalog sell value: $%,.2f   ·   Cost basis: $%,.2f",
                        report.sellValue(), report.costBasis()), body));
            }
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph(
                    "Tip: booth sheet = in-stock only; full list includes zeros for planning. "
                            + "Low-stock reorder is a separate PDF.",
                    small));
            doc.add(new Paragraph(
                    "FlowersForever · practical tools for PNW flower growers", small));

            doc.close();
            log.info("Generated price list PDF (skus={}, inStockOnly={})",
                    report.skuCount(), report.inStockOnly());
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to build price list PDF: " + e.getMessage(), e);
        }
    }

    private static String formatPriceListText(boolean inStockOnly, List<PriceListLine> lines,
                                              double sell, double cost, int units) {
        StringBuilder sb = new StringBuilder();
        sb.append("PRICE LIST — Port Orchard / Kitsap County\n");
        sb.append("═══════════════════════════════════════\n");
        sb.append("Date: ").append(LocalDate.now())
                .append(inStockOnly ? "  ·  In-stock only" : "  ·  All SKUs")
                .append('\n');
        sb.append(String.format(Locale.US,
                "SKUs: %d  ·  Units: %d  ·  Sell $%,.2f  ·  Cost $%,.2f%n",
                lines.size(), units, sell, cost));
        sb.append('\n');
        if (lines.isEmpty()) {
            sb.append("  (empty)\n");
        } else {
            sb.append(String.format(Locale.US, "%-22s %-14s %8s %6s %8s%n",
                    "SKU", "Category", "Unit $", "Qty", "Ext. $"));
            sb.append("-".repeat(64)).append('\n');
            for (PriceListLine l : lines) {
                sb.append(String.format(Locale.US, "%-22s %-14s %8.2f %6d %8.2f%n",
                        truncate(l.name(), 22),
                        truncate(l.category(), 14),
                        l.unitPrice(),
                        l.quantity(),
                        l.lineSellValue()));
            }
            sb.append("-".repeat(64)).append('\n');
            sb.append(String.format(Locale.US, "TOTAL SELL  $%,.2f%n", sell));
        }
        sb.append("\nTip: print for the market table; pair with packing PDF on van days.\n");
        return sb.toString();
    }

    private static String formatLowStockText(int threshold, int skuCount,
                                             List<LowStockLine> lines, double costTotal) {
        StringBuilder sb = new StringBuilder();
        sb.append("LOW-STOCK REORDER — Port Orchard / Kitsap County\n");
        sb.append("═══════════════════════════════════════════════\n");
        sb.append("Date: ").append(LocalDate.now())
                .append("  ·  Threshold: ≤ ").append(threshold)
                .append("  ·  SKUs total: ").append(skuCount).append('\n');
        sb.append(String.format(Locale.US,
                "Low SKUs: %d  ·  Suggested restock cost: $%,.2f%n",
                lines.size(), costTotal));
        sb.append('\n');
        if (lines.isEmpty()) {
            sb.append("  All clear — nothing under threshold.\n");
        } else {
            sb.append(String.format(Locale.US, "%-24s %6s %8s %10s %10s%n",
                    "SKU", "OnHand", "Order", "Unit", "Est.Cost"));
            sb.append("-".repeat(64)).append('\n');
            for (LowStockLine l : lines) {
                sb.append(String.format(Locale.US, "%-24s %6d %8d %10s %10.2f%n",
                        truncate(l.name(), 24),
                        l.quantity(),
                        l.suggestedOrderQty(),
                        truncate(l.unit(), 10),
                        l.suggestedOrderCost()));
            }
            sb.append("-".repeat(64)).append('\n');
            sb.append(String.format(Locale.US, "TOTAL SUGGESTED COST  $%,.2f%n", costTotal));
        }
        sb.append("\nTip: print PDF for the cooler door; harvest zeros first.\n");
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void headerCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        cell.setBackgroundColor(new Color(34, 100, 54));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static PdfPCell cell(String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4);
        cell.setBackgroundColor(bg);
        cell.setBorderColor(new Color(200, 210, 200));
        return cell;
    }

    private static PdfPCell summaryCell(String label, String value, Color bg) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "\n", FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY)));
        p.add(new Chunk(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13)));
        PdfPCell cell = new PdfPCell(p);
        cell.setBackgroundColor(bg);
        cell.setPadding(8);
        cell.setBorderColor(new Color(180, 200, 180));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    @Transactional(readOnly = true)
    public synchronized void exportToCsv(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }

        List<Item> inventory = repository.findAllOrdered();
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename.trim(), java.nio.charset.StandardCharsets.UTF_8))) {
            bw.write(CSV_HEADER);
            bw.newLine();
            for (Item item : inventory) {
                bw.write(item.toCsv());
                bw.newLine();
            }
            log.info("Inventory exported to '{}' ({} item(s)).", filename, inventory.size());
        } catch (IOException e) {
            log.error("Export to '{}' failed: {}", filename, e.getMessage());
            throw new IllegalStateException("Export to '" + filename + "' failed: " + e.getMessage(), e);
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Item itemAtIndex(int index) {
        List<Item> all = repository.findAllOrdered();
        if (index < 0 || index >= all.size()) {
            throw new IndexOutOfBoundsException(
                    "Invalid inventory index: " + index + " (size=" + all.size() + ")");
        }
        return all.get(index);
    }

    private List<Item> loadSeedFromCsv() {
        File file = new File(seedCsvPath);
        if (!file.exists()) {
            return List.of();
        }

        List<Item> items = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean firstDataLine = true;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = parseCsvLine(line);
                if (firstDataLine && isHeaderRow(parts)) {
                    firstDataLine = false;
                    continue;
                }
                firstDataLine = false;
                if (parts.length == 7) {
                    try {
                        items.add(new Item(
                                parts[0], parts[1],
                                Double.parseDouble(parts[2]),
                                parts[3],
                                Double.parseDouble(parts[4]),
                                Integer.parseInt(parts[5]),
                                parts[6]
                        ));
                    } catch (Exception e) {
                        log.warn("Skipping malformed seed CSV line: '{}' — {}", line, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Could not read seed CSV '{}': {}", seedCsvPath, e.getMessage());
            return List.of();
        }
        return items;
    }

    private static boolean isHeaderRow(String[] parts) {
        return parts.length > 0 && "name".equalsIgnoreCase(parts[0].trim());
    }

    private String[] parseCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString().trim());
        return parts.toArray(new String[0]);
    }

    private List<Item> buildSampleInventory() {
        List<Item> items = new ArrayList<>();
        items.add(new Item("Organic Fertilizer", "Fertilizers", 15.00, "Per Unit", 10.00, 50, "For PNW soil amendment"));
        items.add(new Item("Neem Oil Spray", "Pest Control", 20.00, "Per Unit", 12.00, 20, "Natural aphid control"));
        items.add(new Item("Nootka Rose", "Flowers/Plants", 2.20, "Per Stem", 1.10, 50, "Native PNW rose"));
        items.add(new Item("Rosa rugosa Hansa", "Flowers/Plants", 2.60, "Per Stem", 1.30, 60, "Disease-resistant shrub"));
        return items;
    }
}
