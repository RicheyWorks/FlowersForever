package com.flowerfarm.controller;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.ExportPathResolver;
import com.flowerfarm.service.HarvestService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST API for the harvest log.
 *
 * <pre>
 * GET    /api/harvest              list all (newest first)
 * GET    /api/harvest/{id}         get one
 * GET    /api/harvest/lots/{lotCode}  exact traceability lookup
 * GET    /api/harvest/lot?code=     exact lookup for codes containing '/'
 * GET    /api/harvest/{id}/label.pdf  printable QR lot label
 * GET    /api/harvest/range?from=&amp;to=  filter by date
 * GET    /api/harvest/search?crop=     search crop name
 * GET    /api/harvest/totals           quantity totals by crop
 * POST   /api/harvest              add entry
 * POST   /api/harvest/batch        batch-add entries (one transaction + HARVEST_BATCH audit)
 * PUT    /api/harvest/{id}         update entry (corrects inventory)
 * DELETE /api/harvest/{id}         delete entry (reverses inventory)
 * GET    /api/harvest/week         trailing 7-day total + daily series
 * POST   /api/harvest/export       write harvest_log.csv
 * </pre>
 */
@RestController
@RequestMapping("/api/harvest")
public class HarvestController {

    private final HarvestService harvestService;
    private final ExportPathResolver exportPathResolver;

    public HarvestController(HarvestService harvestService,
                             ExportPathResolver exportPathResolver) {
        this.harvestService = harvestService;
        this.exportPathResolver = exportPathResolver;
    }

    @GetMapping
    public List<HarvestEntry> list() {
        return harvestService.getAll();
    }

    @GetMapping("/range")
    public ResponseEntity<?> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(harvestService.findBetween(from, to));
    }

    @GetMapping("/search")
    public List<HarvestEntry> search(@RequestParam("crop") String crop) {
        return harvestService.searchByCrop(crop);
    }

    /**
     * Flexible filter: optional crop + bed substrings and inclusive date bounds.
     * Omitting a param leaves that constraint unbounded / empty.
     */
    @GetMapping("/filter")
    public ResponseEntity<?> filter(
            @RequestParam(value = "crop", required = false) String crop,
            @RequestParam(value = "bed", required = false) String bed,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to date must be on or after from date.");
        }
        return ResponseEntity.ok(harvestService.filter(crop, bed, notes, from, to));
    }

    @GetMapping("/totals")
    public Map<String, Double> totals() {
        return harvestService.totalsByCrop();
    }

    /** Trailing 7-day harvest total plus daily sparkline series (oldest → today). */
    @GetMapping("/week")
    public Map<String, Object> weekSummary() {
        return Map.of(
                "totalQuantity", harvestService.totalQuantityLast7Days(),
                "dailyQuantities", harvestService.dailyQuantitiesLast7Days()
        );
    }

    /**
     * Bed / field production rollup (quantity + crop mix).
     * Optional {@code from}/{@code to}; omit both for all-time; {@code week=true} = last 7 days.
     */
    @GetMapping("/beds")
    public ResponseEntity<?> bedProduction(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "week", defaultValue = "false") boolean week) {
        HarvestService.BedProductionReport report = week
                ? harvestService.productionByBedLast7Days()
                : harvestService.productionByBed(from, to);
        return ResponseEntity.ok(report.toMap());
    }

    @GetMapping(value = "/beds/text", produces = "text/plain")
    public ResponseEntity<?> bedProductionText(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "week", defaultValue = "false") boolean week) {
        HarvestService.BedProductionReport report = week
                ? harvestService.productionByBedLast7Days()
                : harvestService.productionByBed(from, to);
        return ResponseEntity.ok(report.plainText());
    }

    /** Printable bed / field production PDF. */
    @GetMapping(value = "/beds/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> bedProductionPdf(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "week", defaultValue = "false") boolean week) {
        HarvestService.BedProductionReport report = week
                ? harvestService.productionByBedLast7Days()
                : harvestService.productionByBed(from, to);
        byte[] pdf = harvestService.generateBedProductionPdf(report);
        String filename = "bed-production-" + report.to() + ".pdf";
        return DownloadResponse.pdf(pdf, filename);
    }

    /**
     * Chronological harvest log report.
     * Default: trailing 7 days when dates omitted; {@code week=true} forces last 7 days.
     */
    @GetMapping("/log")
    public ResponseEntity<?> harvestLog(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "week", defaultValue = "false") boolean week) {
        HarvestService.HarvestLogReport report = week
                ? harvestService.buildHarvestLogReportLast7Days()
                : harvestService.buildHarvestLogReport(from, to);
        return ResponseEntity.ok(report.toMap());
    }

    @GetMapping(value = "/log/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> harvestLogText(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "week", defaultValue = "false") boolean week) {
        HarvestService.HarvestLogReport report = week
                ? harvestService.buildHarvestLogReportLast7Days()
                : harvestService.buildHarvestLogReport(from, to);
        return ResponseEntity.ok(report.plainText());
    }

    @GetMapping(value = "/log/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> harvestLogPdf(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "week", defaultValue = "false") boolean week) {
        HarvestService.HarvestLogReport report = week
                ? harvestService.buildHarvestLogReportLast7Days()
                : harvestService.buildHarvestLogReport(from, to);
        byte[] pdf = harvestService.generateHarvestLogPdf(report);
        String filename = "harvest-log-" + report.to() + ".pdf";
        return DownloadResponse.pdf(pdf, filename);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        HarvestEntry entry = harvestService.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No harvest entry with id=" + id));
        return ResponseEntity.ok(entry);
    }

    @GetMapping("/lots/{lotCode}")
    public ResponseEntity<?> getByLotCode(@PathVariable String lotCode) {
        HarvestEntry entry = harvestService.findByLotCode(lotCode)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No harvest lot with code=" + lotCode));
        return ResponseEntity.ok(entry);
    }

    @GetMapping("/lot")
    public ResponseEntity<?> getByLotCodeQuery(@RequestParam("code") String lotCode) {
        return getByLotCode(lotCode);
    }

    @GetMapping(value = "/{id}/label.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> lotLabel(@PathVariable Long id) {
        HarvestEntry entry = harvestService.findById(id)
                .orElseThrow(() -> new IndexOutOfBoundsException(
                        "No harvest entry with id=" + id));
        return DownloadResponse.pdf(
                harvestService.generateLotLabelPdf(id),
                "harvest-lot-" + labelFilenameCode(entry.getLotCode()) + ".pdf");
    }

    private static String labelFilenameCode(String lotCode) {
        return lotCode.replace('/', '-');
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody HarvestEntry entry) {
        HarvestEntry saved = harvestService.add(entry);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * Batch-log multiple harvest rows. Each valid line increments inventory;
     * one {@code HARVEST_BATCH} audit row is recorded for the group.
     */
    @PostMapping("/batch")
    public ResponseEntity<?> addBatch(@RequestBody List<HarvestEntry> entries) {
        List<HarvestEntry> saved = harvestService.addBatch(entries);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody HarvestEntry entry) {
        return ResponseEntity.ok(harvestService.update(id, entry));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        harvestService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Server-side CSV export. Optional crop/bed/notes/from/to filter the rows exported
     * (same semantics as {@code GET /filter}).
     */
    @PostMapping("/export")
    public ResponseEntity<Map<String, String>> exportCsv(
            @RequestParam(value = "filename", defaultValue = "harvest_log.csv") String filename,
            @RequestParam(value = "crop", required = false) String crop,
            @RequestParam(value = "bed", required = false) String bed,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Path exportPath = exportPathResolver.resolveCsv(filename, "harvest_log.csv");
        boolean anyFilter = (crop != null && !crop.isBlank())
                || (bed != null && !bed.isBlank())
                || (notes != null && !notes.isBlank())
                || from != null || to != null;
        if (anyFilter) {
            List<HarvestEntry> filtered = harvestService.filter(crop, bed, notes, from, to);
            harvestService.exportToCsv(exportPath.toString(), filtered);
            return ResponseEntity.ok(Map.of(
                    "message", "Exported " + filtered.size() + " filtered harvest row(s) to " + filename,
                    "rows", String.valueOf(filtered.size())));
        }
        harvestService.exportToCsv(exportPath.toString());
        return ResponseEntity.ok(Map.of("message", "Exported harvest log to " + filename));
    }
}
