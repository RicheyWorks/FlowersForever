package com.flowerfarm.service;

import com.flowerfarm.util.InventoryQuantity;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.util.CsvSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes harvest-history rows using the application's stable CSV contract. */
@Component
public class HarvestCsvExporter {

    private static final Logger log = LoggerFactory.getLogger(HarvestCsvExporter.class);

    public void export(String filename, List<HarvestEntry> entries) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Export filename must not be null or empty.");
        }
        List<HarvestEntry> rows = entries == null ? List.of() : entries;
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(filename.trim(), StandardCharsets.UTF_8))) {
            writeCsv(writer, rows);
            log.info("Harvest log exported to '{}' ({} row(s)).", LogValue.escape(filename), rows.size());
        } catch (IOException ex) {
            throw new IllegalStateException("Harvest export failed: " + ex.getMessage(), ex);
        }
    }

    /** Serialize the current harvest contract without choosing or closing the destination. */
    public void writeCsv(Writer writer, List<HarvestEntry> entries) throws IOException {
        List<HarvestEntry> rows = entries == null ? List.of() : entries;
        writer.write("Id,Date,Crop,MarketableQuantity,Unit,BedOrField,Notes,LotCode,Grade,LossQuantity,GrossQuantity,StorageLocation");
        writer.write(System.lineSeparator());
        for (HarvestEntry entry : rows) {
            writer.write(String.format(java.util.Locale.ROOT,
                    "%s,%s,%s,%s,%s,%s,\"%s\",%s,%s,%s,%s,%s",
                    entry.getId() == null ? "" : entry.getId(),
                    entry.getHarvestDate(),
                    CsvSupport.escape(entry.getCropName(), ','),
                    InventoryQuantity.csv(entry.getQuantity()),
                    CsvSupport.escape(entry.getUnit(), ','),
                    CsvSupport.escape(entry.getBedOrField(), ','),
                    entry.getNotes() == null
                            ? "" : entry.getNotes().replace("\"", "\"\""),
                    CsvSupport.escape(entry.getLotCode(), ','),
                    entry.getGrade(),
                    InventoryQuantity.csv(entry.getLossQuantity()),
                    InventoryQuantity.csv(entry.getGrossQuantity()),
                    CsvSupport.escape(entry.getStorageLocation(), ',')));
            writer.write(System.lineSeparator());
        }
    }
}
