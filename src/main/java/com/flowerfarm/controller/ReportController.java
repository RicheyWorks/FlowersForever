package com.flowerfarm.controller;

import com.flowerfarm.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * PDF report endpoints for harvest + sales summaries.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Trailing 7-day harvest + sales PDF. */
    @GetMapping(value = "/weekly.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> weekly() {
        try {
            byte[] pdf = reportService.generateWeeklyReportPdf();
            return pdfResponse(pdf, "flowerfarm-weekly-" + LocalDate.now() + ".pdf");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /** Custom range harvest + sales PDF. */
    @GetMapping(value = "/range.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            byte[] pdf = reportService.generateReportPdf(from, to);
            String name = "flowerfarm-" + from.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + to.format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf";
            return pdfResponse(pdf, name);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private static ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
