package com.flowerfarm.controller;

import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.HarvestService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST API for the harvest log.
 *
 * <pre>
 * GET    /api/harvest              list all (newest first)
 * GET    /api/harvest/{id}         get one
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

    public HarvestController(HarvestService harvestService) {
        this.harvestService = harvestService;
    }

    @GetMapping
    public List<HarvestEntry> list() {
        return harvestService.getAll();
    }

    @GetMapping("/range")
    public ResponseEntity<?> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            return ResponseEntity.ok(harvestService.findBetween(from, to));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
        try {
            if (from != null && to != null && to.isBefore(from)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "to date must be on or after from date."));
            }
            return ResponseEntity.ok(harvestService.filter(crop, bed, notes, from, to));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
        try {
            HarvestService.BedProductionReport report = week
                    ? harvestService.productionByBedLast7Days()
                    : harvestService.productionByBed(from, to);
            return ResponseEntity.ok(report.toMap());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(value = "/beds/text", produces = "text/plain")
    public ResponseEntity<?> bedProductionText(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "week", defaultValue = "false") boolean week) {
        try {
            HarvestService.BedProductionReport report = week
                    ? harvestService.productionByBedLast7Days()
                    : harvestService.productionByBed(from, to);
            return ResponseEntity.ok(report.plainText());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Printable bed / field production PDF. */
    @GetMapping(value = "/beds/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> bedProductionPdf(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "week", defaultValue = "false") boolean week) {
        try {
            HarvestService.BedProductionReport report = week
                    ? harvestService.productionByBedLast7Days()
                    : harvestService.productionByBed(from, to);
            byte[] pdf = harvestService.generateBedProductionPdf(report);
            String filename = "bed-production-" + report.to() + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return harvestService.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No harvest entry with id=" + id)));
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody HarvestEntry entry) {
        try {
            HarvestEntry saved = harvestService.add(entry);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Batch-log multiple harvest rows. Each valid line increments inventory;
     * one {@code HARVEST_BATCH} audit row is recorded for the group.
     */
    @PostMapping("/batch")
    public ResponseEntity<?> addBatch(@RequestBody List<HarvestEntry> entries) {
        try {
            List<HarvestEntry> saved = harvestService.addBatch(entries);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody HarvestEntry entry) {
        try {
            return ResponseEntity.ok(harvestService.update(id, entry));
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            harvestService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IndexOutOfBoundsException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
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
        try {
            boolean anyFilter = (crop != null && !crop.isBlank())
                    || (bed != null && !bed.isBlank())
                    || (notes != null && !notes.isBlank())
                    || from != null || to != null;
            if (anyFilter) {
                List<HarvestEntry> filtered = harvestService.filter(crop, bed, notes, from, to);
                harvestService.exportToCsv(filename, filtered);
                return ResponseEntity.ok(Map.of(
                        "message", "Exported " + filtered.size() + " filtered harvest row(s) to " + filename,
                        "rows", String.valueOf(filtered.size())));
            }
            harvestService.exportToCsv(filename);
            return ResponseEntity.ok(Map.of("message", "Exported harvest log to " + filename));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
