package com.flowerfarm.service;

import com.flowerfarm.util.CsvSupport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Pure Florlist-friendly availability sheet: live stock for buyers now, plus
 * planned stems for the current week. CSV first row is a simple header florists
 * and marketplace importers can map.
 */
public final class FloristAvailabilityExport {

    public static final String TITLE = "Share availability with florists";

    private FloristAvailabilityExport() {
    }

    public record Line(
            String product,
            String unit,
            int liveQuantity,
            double unitPrice,
            double plannedStems,
            LocalDate weekStart,
            LocalDate weekEnd,
            String notes) {
        public Line {
            product = require(product, "product");
            unit = unit == null || unit.isBlank() ? "stems" : unit.trim();
            notes = notes == null ? "" : notes.trim();
        }

        private static String require(String value, String label) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(label + " is required");
            }
            return value.trim();
        }
    }

    public record Sheet(
            String farmName,
            String farmContact,
            LocalDate asOf,
            LocalDate weekStart,
            LocalDate weekEnd,
            List<Line> lines) {
        public Sheet {
            farmName = farmName == null || farmName.isBlank() ? "Flower farm" : farmName.trim();
            farmContact = farmContact == null ? "" : farmContact.trim();
            asOf = Objects.requireNonNull(asOf, "asOf");
            weekStart = Objects.requireNonNull(weekStart, "weekStart");
            weekEnd = Objects.requireNonNull(weekEnd, "weekEnd");
            lines = List.copyOf(Objects.requireNonNullElse(lines, List.of()));
        }

        public boolean isEmpty() {
            return lines.isEmpty();
        }
    }

    /** CSV columns chosen for Florlist-style / spreadsheet import. */
    public static String csv(Sheet sheet) {
        Objects.requireNonNull(sheet, "sheet");
        StringBuilder out = new StringBuilder();
        out.append(CsvSupport.row(',',
                "Product", "Quantity", "Unit", "Price",
                "Week start", "Week end", "Planned stems", "Notes"));
        out.append('\n');
        for (Line line : sheet.lines()) {
            out.append(CsvSupport.row(',',
                    line.product(),
                    Integer.toString(line.liveQuantity()),
                    line.unit(),
                    money(line.unitPrice()),
                    line.weekStart().toString(),
                    line.weekEnd().toString(),
                    trimNumber(line.plannedStems()),
                    line.notes()));
            out.append('\n');
        }
        return out.toString();
    }

    /** Paste into email, SMS, or Florlist notes — not a hosted web link. */
    public static String shareText(Sheet sheet) {
        Objects.requireNonNull(sheet, "sheet");
        StringBuilder out = new StringBuilder();
        out.append(sheet.farmName()).append(" — flower availability\n");
        out.append("As of ").append(sheet.asOf())
                .append(" · week ").append(sheet.weekStart())
                .append(" to ").append(sheet.weekEnd()).append('\n');
        if (!sheet.farmContact().isBlank()) {
            out.append("Contact: ").append(sheet.farmContact()).append('\n');
        }
        out.append('\n');
        if (sheet.isEmpty()) {
            out.append("No in-stock products to share right now.\n");
            return out.toString();
        }
        out.append(String.format(Locale.ROOT, "%-24s %8s %-10s %10s %10s%n",
                "Product", "Live", "Unit", "Price", "Planned"));
        for (Line line : sheet.lines()) {
            out.append(String.format(Locale.ROOT, "%-24s %8d %-10s %10s %10s%n",
                    truncate(line.product(), 24),
                    line.liveQuantity(),
                    truncate(line.unit(), 10),
                    "$" + money(line.unitPrice()),
                    trimNumber(line.plannedStems())));
        }
        out.append("\nQuantity is live cooler stock. Planned stems are this week’s estimate — ")
                .append("confirm before promising. Import the CSV into Florlist or paste this list ")
                .append("to florists.\n");
        return out.toString();
    }

    public static String emailSubject(Sheet sheet) {
        return sheet.farmName() + " availability " + sheet.asOf();
    }

    public static String suggestedCsvFileName(Sheet sheet) {
        return "florist-availability-" + sheet.asOf() + ".csv";
    }

    public static String statusCopied() {
        return "Availability copied — paste into email, Florlist, or a text thread.";
    }

    public static String statusSaved(String fileName) {
        return "Florist availability CSV saved → " + fileName;
    }

    public static String statusEmpty() {
        return "No in-stock products to share. Record stock or uncheck “in stock only” after adding inventory.";
    }

    public static List<Line> merge(
            List<InventoryService.PriceListLine> live,
            List<ProductionStudioService.AvailabilityLine> plannedWeek,
            LocalDate weekStart,
            LocalDate weekEnd) {
        List<InventoryService.PriceListLine> stock = live == null ? List.of() : live;
        List<ProductionStudioService.AvailabilityLine> planned =
                plannedWeek == null ? List.of() : plannedWeek;
        java.util.Map<String, ProductionStudioService.AvailabilityLine> byCrop =
                new java.util.LinkedHashMap<>();
        for (ProductionStudioService.AvailabilityLine row : planned) {
            if (row != null && row.cropName() != null) {
                byCrop.put(row.cropName().trim().toLowerCase(Locale.ROOT), row);
            }
        }
        List<Line> lines = new ArrayList<>();
        for (InventoryService.PriceListLine item : stock) {
            if (item == null || item.quantity() <= 0) {
                continue;
            }
            ProductionStudioService.AvailabilityLine match =
                    byCrop.get(item.name().trim().toLowerCase(Locale.ROOT));
            double plannedQty = match == null ? 0 : match.plannedStems();
            String notes = match == null || match.harvestWindows() == null
                    || match.harvestWindows().isBlank()
                    ? "Live stock"
                    : "Harvest window: " + match.harvestWindows();
            lines.add(new Line(
                    item.name(),
                    item.unit(),
                    item.quantity(),
                    item.unitPrice(),
                    plannedQty,
                    weekStart,
                    weekEnd,
                    notes));
        }
        return List.copyOf(lines);
    }

    private static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String trimNumber(double value) {
        if (Math.rint(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String truncate(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
