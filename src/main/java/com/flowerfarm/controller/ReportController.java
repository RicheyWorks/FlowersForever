package com.flowerfarm.controller;

import com.flowerfarm.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
        byte[] pdf = reportService.generateWeeklyReportPdf();
        return DownloadResponse.pdf(pdf, "flowerfarm-weekly-" + LocalDate.now() + ".pdf");
    }

    /** Custom range harvest + sales PDF. */
    @GetMapping(value = "/range.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> range(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] pdf = reportService.generateReportPdf(from, to);
        String name = "flowerfarm-" + from.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + to.format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf";
        return DownloadResponse.pdf(pdf, name);
    }
}
