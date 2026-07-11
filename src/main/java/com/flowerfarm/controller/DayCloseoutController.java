package com.flowerfarm.controller;

import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.DayCloseoutService.DayCloseout;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * End-of-day Kitsap farm closeout (bookend to morning briefing).
 *
 * <ul>
 *   <li>{@code GET /api/closeout} — JSON</li>
 *   <li>{@code GET /api/closeout/text} — plain text</li>
 *   <li>{@code GET /api/closeout/report.pdf} — printable PDF</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/closeout")
public class DayCloseoutController {

    private final DayCloseoutService dayCloseoutService;

    public DayCloseoutController(DayCloseoutService dayCloseoutService) {
        this.dayCloseoutService = dayCloseoutService;
    }

    @GetMapping
    public Map<String, Object> json() {
        return dayCloseoutService.build().toMap();
    }

    @GetMapping(value = "/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public String text() {
        return dayCloseoutService.build().plainText();
    }

    @GetMapping(value = "/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf() {
        DayCloseout closeout = dayCloseoutService.build();
        byte[] pdf = dayCloseoutService.generatePdf(closeout);
        String filename = "day-closeout-" + closeout.date() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
