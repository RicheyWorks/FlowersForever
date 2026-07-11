package com.flowerfarm.controller;

import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.MorningBriefingService.MorningBriefing;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Start-of-day Kitsap farm briefing.
 *
 * <ul>
 *   <li>{@code GET /api/briefing} — JSON</li>
 *   <li>{@code GET /api/briefing/text} — plain text</li>
 *   <li>{@code GET /api/briefing/report.pdf} — printable PDF</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/briefing")
public class MorningBriefingController {

    private final MorningBriefingService morningBriefingService;

    public MorningBriefingController(MorningBriefingService morningBriefingService) {
        this.morningBriefingService = morningBriefingService;
    }

    @GetMapping
    public Map<String, Object> json(
            @RequestParam(value = "live", defaultValue = "false") boolean live) {
        return morningBriefingService.build(live).toMap();
    }

    @GetMapping(value = "/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public String text(
            @RequestParam(value = "live", defaultValue = "false") boolean live) {
        return morningBriefingService.build(live).plainText();
    }

    @GetMapping(value = "/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(
            @RequestParam(value = "live", defaultValue = "false") boolean live) {
        MorningBriefing briefing = morningBriefingService.build(live);
        byte[] pdf = morningBriefingService.generatePdf(briefing);
        String filename = "morning-briefing-" + briefing.date() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
