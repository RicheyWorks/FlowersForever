package com.flowerfarm.gui.presenter;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.HarvestService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI-independent validation, projection, and workflow delegation for the harvest log.
 */
public final class HarvestLogPresenter {

    private final HarvestService harvestService;

    public HarvestLogPresenter(HarvestService harvestService) {
        this.harvestService = harvestService;
    }

    public HarvestView filter(FilterDraft filter) {
        LocalDate from = parseFilterDate(filter.from(), "filterFrom", "From");
        LocalDate to = parseFilterDate(filter.to(), "filterTo", "To");
        if (from != null && to != null && to.isBefore(from)) {
            throw new InputValidationException("filterTo",
                    "The To date must be on or after the From date.");
        }
        List<HarvestEntry> entries = harvestService.filter(
                trim(filter.crop()), trim(filter.bed()), trim(filter.notes()), from, to);
        double quantity = entries.stream().mapToDouble(HarvestEntry::getQuantity).sum();
        return new HarvestView(entries, entries.stream().map(HarvestRow::from).toList(), quantity);
    }

    public HarvestEntry fromForm(EntryDraft draft) {
        LocalDate date;
        try {
            date = LocalDate.parse(trim(draft.date()));
        } catch (RuntimeException exception) {
            throw new InputValidationException("date", "Use YYYY-MM-DD, for example "
                    + LocalDate.now() + ".");
        }
        validateHarvestDate(date);
        String crop = trim(draft.crop());
        if (crop.isEmpty()) {
            throw new InputValidationException("crop", "Enter the crop or variety harvested.");
        }
        double quantity = parsePositiveQuantity(draft.quantity());
        String unit = trim(draft.unit());
        if (unit.isEmpty()) {
            throw new InputValidationException("unit", "Enter how this harvest is measured.");
        }
        try {
            return new HarvestEntry(date, crop, quantity, unit,
                    trim(draft.bed()), trim(draft.notes()));
        } catch (IllegalArgumentException exception) {
            throw new InputValidationException("form", exception.getMessage());
        }
    }

    public List<HarvestEntry> parseBatch(BatchDraft draft) {
        LocalDate date = LocalDate.parse(draft.date().trim());
        validateHarvestDate(date);
        String defaultUnit = trim(draft.defaultUnit());
        if (defaultUnit.isEmpty()) {
            defaultUnit = "stems";
        }
        String defaultBed = trim(draft.defaultBed());
        String notes = trim(draft.notes());
        List<HarvestEntry> batch = new ArrayList<>();
        String[] lines = draft.lines() == null ? new String[0] : draft.lines().split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line == null || line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }
            int lineNumber = index + 1;
            String[] parts = line.split("[,;\\t]", -1);
            if (parts.length < 2) {
                throw new IllegalArgumentException(
                        "Line " + lineNumber + ": use Crop,Quantity[,Unit][,Bed].");
            }
            if (parts.length > 4) {
                throw new IllegalArgumentException(
                        "Line " + lineNumber + ": found more than four columns.");
            }
            String crop = parts[0].trim();
            if (crop.isEmpty()) {
                throw new IllegalArgumentException(
                        "Line " + lineNumber + ": enter a crop or variety.");
            }
            String unit = parts.length >= 3 && !parts[2].trim().isEmpty()
                    ? parts[2].trim() : defaultUnit;
            String bed = parts.length >= 4 && !parts[3].trim().isEmpty()
                    ? parts[3].trim() : defaultBed;
            double quantity;
            try {
                quantity = Double.parseDouble(parts[1].trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Line " + lineNumber + ": quantity must be a number.", exception);
            }
            if (!Double.isFinite(quantity) || quantity <= 0) {
                throw new IllegalArgumentException(
                        "Line " + lineNumber + ": quantity must be greater than zero.");
            }
            try {
                HarvestService.inventoryQuantityFor(quantity);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Line " + lineNumber + ": " + lowerFirst(exception.getMessage()),
                        exception);
            }
            batch.add(new HarvestEntry(date, crop, quantity, unit, bed, notes));
            if (batch.size() > HarvestService.MAX_BATCH_SIZE) {
                throw new IllegalArgumentException(
                        "Batch cannot contain more than " + HarvestService.MAX_BATCH_SIZE
                                + " harvest rows.");
            }
        }
        if (batch.isEmpty()) {
            throw new IllegalArgumentException("Enter at least one harvest line.");
        }
        return List.copyOf(batch);
    }

    /** Builds submission feedback without allowing malformed rows to reach the service layer. */
    public BatchPreview previewBatch(BatchDraft draft) {
        try {
            List<HarvestEntry> entries = parseBatch(draft);
            Map<String, Double> totalsByUnit = new LinkedHashMap<>();
            Map<String, Integer> inventoryByUnit = new LinkedHashMap<>();
            boolean rounded = false;
            for (HarvestEntry entry : entries) {
                totalsByUnit.merge(entry.getUnit(), entry.getQuantity(), Double::sum);
                int inventoryQuantity = HarvestService.inventoryQuantityFor(entry.getQuantity());
                inventoryByUnit.merge(entry.getUnit(), inventoryQuantity, Integer::sum);
                rounded |= inventoryQuantity != entry.getQuantity();
            }
            String totals = totalsByUnit.entrySet().stream()
                    .map(entry -> formatQuantity(entry.getValue()) + " " + entry.getKey())
                    .reduce((left, right) -> left + " · " + right)
                    .orElse("");
            String inventoryImpact = inventoryByUnit.entrySet().stream()
                    .map(entry -> "+" + entry.getValue() + " " + entry.getKey())
                    .reduce((left, right) -> left + " · " + right)
                    .orElse("");
            return new BatchPreview(true, entries.size(),
                    entries.size() + (entries.size() == 1 ? " row" : " rows")
                            + " ready" + (totals.isEmpty() ? "" : " · " + totals)
                            + (rounded ? " · inventory " + inventoryImpact
                                    + " (rounded by row)" : ""));
        } catch (RuntimeException exception) {
            String message = exception instanceof java.time.format.DateTimeParseException
                    ? "Batch date must be YYYY-MM-DD."
                    : exception.getMessage();
            return new BatchPreview(false, 0,
                    message == null || message.isBlank() ? "Check the batch lines." : message);
        }
    }

    private static String formatQuantity(double quantity) {
        return quantity == Math.rint(quantity)
                ? Long.toString((long) quantity)
                : String.format(java.util.Locale.ROOT, "%.2f", quantity)
                        .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public HarvestEntry add(HarvestEntry entry) {
        return harvestService.add(entry);
    }

    public List<String> missingInventoryProducts(List<HarvestEntry> entries) {
        return harvestService.missingInventoryProducts(entries);
    }

    public List<HarvestEntry> addBatch(List<HarvestEntry> entries) {
        return harvestService.addBatch(entries);
    }

    public HarvestEntry update(long id, HarvestEntry entry) {
        return harvestService.update(id, entry);
    }

    public void delete(long id) {
        harvestService.delete(id);
    }

    public Map<String, Double> totalsByCrop() {
        return harvestService.totalsByCrop();
    }

    public Map<String, Double> totalsFor(List<HarvestEntry> entries) {
        return harvestService.totalsByCrop(entries);
    }

    public void exportAll(String path) {
        harvestService.exportToCsv(path);
    }

    public void exportView(String path, List<HarvestEntry> entries) {
        harvestService.exportToCsv(path, entries);
    }

    public HarvestService.BedProductionReport bedProduction(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return harvestService.productionByBed(from, to);
    }

    public HarvestService.BedProductionReport bedProductionLast7Days() {
        return harvestService.productionByBedLast7Days();
    }

    public String bedProductionCsv(HarvestService.BedProductionReport report) {
        return harvestService.exportBedProductionCsv(report);
    }

    public byte[] bedProductionPdf(HarvestService.BedProductionReport report) {
        return harvestService.generateBedProductionPdf(report);
    }

    public HarvestService.HarvestLogReport harvestLog(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return harvestService.buildHarvestLogReport(from, to);
    }

    public HarvestService.HarvestLogReport harvestLogLast7Days() {
        return harvestService.buildHarvestLogReportLast7Days();
    }

    public byte[] harvestLogPdf(HarvestService.HarvestLogReport report) {
        return harvestService.generateHarvestLogPdf(report);
    }

    public static LocalDate parseOptionalDate(String raw) {
        return raw == null || raw.isBlank() ? null : LocalDate.parse(raw.trim());
    }

    private static LocalDate parseFilterDate(String raw, String field, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException exception) {
            throw new InputValidationException(field,
                    label + " date must use YYYY-MM-DD, or be left blank.");
        }
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("Filter 'To' date must be on or after 'From'.");
        }
    }

    private static void validateHarvestDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isAfter(today)) {
            throw new InputValidationException("date",
                    "Harvest date cannot be after today (" + today + ").");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lowerFirst(String value) {
        if (value == null || value.isBlank()) {
            return "check the quantity.";
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private static double parsePositiveQuantity(String raw) {
        double quantity;
        try {
            quantity = Double.parseDouble(trim(raw));
        } catch (NumberFormatException exception) {
            throw new InputValidationException("quantity",
                    "Enter a numeric quantity greater than zero.");
        }
        if (!Double.isFinite(quantity) || quantity <= 0 || quantity > Integer.MAX_VALUE) {
            throw new InputValidationException("quantity",
                    "Enter a finite quantity greater than zero and no more than "
                            + Integer.MAX_VALUE + ".");
        }
        try {
            HarvestService.inventoryQuantityFor(quantity);
        } catch (IllegalArgumentException exception) {
            throw new InputValidationException("quantity", exception.getMessage());
        }
        return quantity;
    }

    public record FilterDraft(String crop, String bed, String notes, String from, String to) {
    }

    public record EntryDraft(String date, String crop, String quantity,
                             String unit, String bed, String notes) {
    }

    public record BatchDraft(String date, String defaultUnit, String defaultBed,
                             String notes, String lines) {
    }

    public record BatchPreview(boolean valid, int rowCount, String message) {
    }

    public record HarvestRow(Long id, LocalDate date, String crop, double quantity,
                             String unit, String bed, String notes) {
        private static HarvestRow from(HarvestEntry entry) {
            return new HarvestRow(entry.getId(), entry.getHarvestDate(), entry.getCropName(),
                    entry.getQuantity(), entry.getUnit(), entry.getBedOrField(), entry.getNotes());
        }

        public Object[] cells() {
            return new Object[]{id, date, crop, quantity, unit, bed, notes};
        }
    }

    public record HarvestView(List<HarvestEntry> sourceEntries, List<HarvestRow> rows,
                              double quantity) {
        public HarvestView {
            sourceEntries = List.copyOf(sourceEntries);
            rows = List.copyOf(rows);
        }
    }

    public static final class InputValidationException extends IllegalArgumentException {
        private final String field;

        public InputValidationException(String field, String message) {
            super(message);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }
}
