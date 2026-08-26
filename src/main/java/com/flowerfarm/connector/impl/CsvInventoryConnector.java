package com.flowerfarm.connector.impl;

import com.flowerfarm.connector.*;
import com.flowerfarm.model.Item;
import com.flowerfarm.util.CsvSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

/**
 * Fully-functional CSV connector.
 *
 * <p>Supports any CSV layout via configurable header→field mappings. The
 * defaults match the native {@code farm_inventory.csv} format produced by
 * {@link com.flowerfarm.service.InventoryService}, but any column names
 * can be remapped in {@code application.properties}.
 *
 * <pre>
 * # application.properties
 * connector.csv.import-file=farm_inventory.csv
 * connector.csv.export-file=exported_inventory.csv
 * connector.csv.delimiter=,
 *
 * # Optional header renames (external header → Item field)
 * connector.csv.col.name=Name
 * connector.csv.col.category=Category
 * connector.csv.col.price=Price
 * connector.csv.col.unit=Unit
 * connector.csv.col.cost=Cost
 * connector.csv.col.quantity=Quantity
 * connector.csv.col.notes=Notes
 * </pre>
 */
@Component
public class CsvInventoryConnector implements ExternalConnector<String[]> {

    private static final Logger log = LoggerFactory.getLogger(CsvInventoryConnector.class);

    private final ConnectorConfig config;
    private final FieldMapper     fieldMapper;

    // Column-header names configured externally (default = native format)
    private final String colName;
    private final String colCategory;
    private final String colPrice;
    private final String colUnit;
    private final String colCost;
    private final String colQuantity;
    private final String colNotes;

    public CsvInventoryConnector(
            @Value("${connector.csv.import-file:farm_inventory.csv}")   String importFile,
            @Value("${connector.csv.export-file:exported_inventory.csv}") String exportFile,
            @Value("${connector.csv.delimiter:,}")                       String delimiter,
            @Value("${connector.csv.col.name:Name}")                     String colName,
            @Value("${connector.csv.col.category:Category}")             String colCategory,
            @Value("${connector.csv.col.price:Price}")                   String colPrice,
            @Value("${connector.csv.col.unit:Unit}")                     String colUnit,
            @Value("${connector.csv.col.cost:Cost}")                     String colCost,
            @Value("${connector.csv.col.quantity:Quantity}")             String colQuantity,
            @Value("${connector.csv.col.notes:Notes}")                   String colNotes
    ) {
        this.colName     = colName;
        this.colCategory = colCategory;
        this.colPrice    = colPrice;
        this.colUnit     = colUnit;
        this.colCost     = colCost;
        this.colQuantity = colQuantity;
        this.colNotes    = colNotes;

        this.config = new ConnectorConfig("csv")
                .set("import-file", importFile)
                .set("export-file", exportFile)
                .set("delimiter",   delimiter);

        // Outbound: Item → external CSV field names
        this.fieldMapper = new FieldMapper()
                .registerOutbound(colName,     Item::getName)
                .registerOutbound(colCategory, Item::getCategory)
                .registerOutbound(colPrice,    item -> String.format(Locale.ROOT, "%.2f", item.getPrice()))
                .registerOutbound(colUnit,     Item::getUnit)
                .registerOutbound(colCost,     item -> String.format(Locale.ROOT, "%.2f", item.getCost()))
                .registerOutbound(colQuantity, Item::getQuantity)
                .registerOutbound(colNotes,    Item::getNotes);
        // Inbound mappings are handled by header-index lookup in mapToItem()
    }

    // ── ExternalConnector ─────────────────────────────────────────────────────

    @Override public String getName()        { return "csv"; }
    @Override public String getDescription() { return "CSV file connector — import / export inventory as comma-separated values"; }
    @Override public SyncDirection getSupportedDirection() { return SyncDirection.BIDIRECTIONAL; }

    @Override
    public boolean isAvailable() {
        return new File(config.get("import-file")).exists();
    }

    // ── Import ────────────────────────────────────────────────────────────────

    @Override
    public ConnectorResult<List<Item>> importItems() {
        String filePath = config.get("import-file");
        File   file     = new File(filePath);

        if (!file.exists()) {
            return ConnectorResult.fail(
                    "Import file not found: " + filePath,
                    "Create the file or update connector.csv.import-file.", getName());
        }

        List<Item>   items    = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        try (Reader reader = new FileReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            char delimiter = delimiter();
            List<List<String>> records = CsvSupport.readAll(reader, delimiter);
            if (records.isEmpty()) {
                return ConnectorResult.fail("CSV file is empty.", filePath, getName());
            }

            String[] headers = records.get(0).toArray(new String[0]);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);

            for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
                try {
                    String[] row = records.get(rowIndex).toArray(new String[0]);
                    items.add(mapToItem(row, headerIndex));
                } catch (Exception e) {
                    String msg = "Row " + (rowIndex + 1) + ": " + e.getMessage();
                    warnings.add(msg);
                    log.warn("[csv] Skipping malformed row — {}", msg);
                }
            }

        } catch (IOException | IllegalArgumentException e) {
            return ConnectorResult.fail("Failed to read CSV file.", e, getName());
        }

        String msg = "Imported " + items.size() + " items from '" + filePath + "'"
                + (warnings.isEmpty() ? "." : " (" + warnings.size() + " rows skipped).");
        log.info("[csv] {}", msg);
        return ConnectorResult.ok(items, msg, getName());
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Override
    public ConnectorResult<Integer> exportItems(List<Item> items) {
        if (items == null) {
            items = List.of();
        }
        String filePath = config.get("export-file");
        final char delimiter;
        try {
            delimiter = delimiter();
        } catch (IllegalArgumentException e) {
            return ConnectorResult.fail("Export failed.", e, getName());
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8))) {
            // Header row — use configured column names
            bw.write(CsvSupport.row(delimiter, fieldMapper.outboundFields()));
            bw.newLine();

            int count = 0;
            for (Item item : items) {
                Map<String, Object> fields = fieldMapper.toExternalMap(item);
                List<String> row = new ArrayList<>();
                for (Object val : fields.values()) {
                    String s = val == null ? "" : val.toString();
                    row.add(s);
                }
                bw.write(CsvSupport.row(delimiter, row));
                bw.newLine();
                count++;
            }

            String msg = "Exported " + count + " items to '" + filePath + "'.";
            log.info("[csv] {}", msg);
            return ConnectorResult.ok(count, msg, getName());

        } catch (IOException e) {
            return ConnectorResult.fail("Export failed.", e, getName());
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * CSV sync strategy: compare local items against the CSV by name.
     * Items in local but not in CSV → written to CSV (created).
     * Items in both with different quantity → updated in CSV.
     * Items in CSV but not local → skipped (not deleted by default).
     */
    @Override
    public ConnectorResult<SyncSummary> syncUpdates(List<Item> localItems) {
        if (localItems == null) {
            localItems = List.of();
        }
        // Read existing CSV state
        ConnectorResult<List<Item>> imported = importItems();
        Map<String, Item> csvByName = new LinkedHashMap<>();

        if (imported.isSuccess() && imported.getPayload() != null) {
            imported.getPayload().forEach(i -> csvByName.put(i.getName().toLowerCase(Locale.ROOT), i));
        } else if (new File(config.get("import-file")).exists()) {
            return ConnectorResult.fail(
                    "CSV sync aborted because the existing file could not be read.",
                    imported.getErrorDetail(),
                    getName());
        }

        Map<String, Item> localByName = new LinkedHashMap<>();
        localItems.forEach(i -> localByName.put(i.getName().toLowerCase(Locale.ROOT), i));

        int created = 0, updated = 0, skipped = 0, errors = 0;
        List<Item> merged = new ArrayList<>(localItems);

        for (Map.Entry<String, Item> entry : localByName.entrySet()) {
            Item local = entry.getValue();
            Item csv   = csvByName.get(entry.getKey());

            if (csv == null) {
                created++;          // new local item — will be written
            } else if (!sameItem(csv, local)) {
                updated++;          // changed item — overwrite in CSV
            } else {
                skipped++;
            }
        }

        // Remote-only rows are explicitly preserved rather than erased by the rewrite.
        for (Map.Entry<String, Item> entry : csvByName.entrySet()) {
            if (!localByName.containsKey(entry.getKey())) {
                merged.add(entry.getValue());
                skipped++;
            }
        }

        // Re-export the fully merged list
        ConnectorResult<Integer> exportResult = exportItems(merged);
        if (!exportResult.isSuccess()) {
            return ConnectorResult.fail("Sync export step failed: " + exportResult.getMessage(),
                    exportResult.getErrorDetail(), getName());
        }

        SyncSummary summary = new SyncSummary(created, updated, 0, skipped, errors);
        log.info("[csv] Sync complete — {}", summary);
        return ConnectorResult.ok(summary, "CSV sync complete. " + summary, getName());
    }

    // ── Field mapping ─────────────────────────────────────────────────────────

    /** Maps a raw CSV row (String[]) to an {@link Item} using a header index. */
    @Override
    public Item mapToItem(String[] raw) {
        // Called when header index is unknown — build a minimal positional map
        // matching the default 7-column layout: Name,Category,Price,Unit,Cost,Qty,Notes
        if (raw.length < 6) throw new IllegalArgumentException("Insufficient columns: " + raw.length);
        return new Item(
                raw[0].trim(),
                raw[1].trim(),
                parseDouble(raw[2]),
                raw[3].trim(),
                parseDouble(raw[4]),
                parseInt(raw[5]),
                raw.length > 6 ? raw[6].trim() : ""
        );
    }

    /** Converts an {@link Item} to a String[] using configured column order. */
    @Override
    public String[] mapFromItem(Item item) {
        Map<String, Object> fields = fieldMapper.toExternalMap(item);
        return fields.values().stream()
                .map(v -> v == null ? "" : v.toString())
                .toArray(String[]::new);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Item mapToItem(String[] row, Map<String, Integer> idx) {
        return new Item(
                col(row, idx, colName,     "Unknown"),
                col(row, idx, colCategory, "Other"),
                colDouble(row, idx, colPrice,    0.0),
                col(row, idx, colUnit,     "Per Unit"),
                colDouble(row, idx, colCost,     0.0),
                colInt   (row, idx, colQuantity, 0),
                col(row, idx, colNotes,    "")
        );
    }

    private Map<String, Integer> buildHeaderIndex(String[] headers) {
        Map<String, Integer> idx = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            idx.put(headers[i].trim(), i);
        }
        return idx;
    }

    private String col(String[] row, Map<String, Integer> idx, String col, String def) {
        Integer i = idx.get(col);
        if (i == null || i >= row.length) return def;
        String v = row[i].trim();
        return v.isEmpty() ? def : v;
    }

    private double colDouble(String[] row, Map<String, Integer> idx, String col, double def) {
        try { return Double.parseDouble(col(row, idx, col, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private int colInt(String[] row, Map<String, Integer> idx, String col, int def) {
        try { return Integer.parseInt(col(row, idx, col, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private char delimiter() {
        String configured = config.get("delimiter", ",");
        if (configured.length() != 1 || configured.charAt(0) == '"'
                || configured.charAt(0) == '\r' || configured.charAt(0) == '\n') {
            throw new IllegalArgumentException("CSV delimiter must be one non-quote character.");
        }
        return configured.charAt(0);
    }

    private boolean sameItem(Item left, Item right) {
        return left != null && right != null
                && left.getName().equalsIgnoreCase(right.getName())
                && Objects.equals(left.getCategory(), right.getCategory())
                && Double.compare(left.getPrice(), right.getPrice()) == 0
                && Objects.equals(left.getUnit(), right.getUnit())
                && Double.compare(left.getCost(), right.getCost()) == 0
                && left.getQuantity() == right.getQuantity()
                && Objects.equals(left.getNotes(), right.getNotes());
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
