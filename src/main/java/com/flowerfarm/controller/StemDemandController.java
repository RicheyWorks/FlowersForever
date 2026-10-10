package com.flowerfarm.controller;

import com.flowerfarm.service.StemDemand;
import com.flowerfarm.service.StemDemandService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** REST boundary for the stem demand board and buying list by event (read) and order-line recipe links (write). */
@RestController
@RequestMapping("/api/production-studio/stem-demand")
public class StemDemandController {

    private final StemDemandService service;

    public StemDemandController(StemDemandService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<StemDemand.Board> board(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "" + StemDemandService.DEFAULT_DAYS) int days,
            @RequestParam(defaultValue = "false") boolean includeDrafts) {
        return ResponseEntity.ok(service.build(from, days, includeDrafts));
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "" + StemDemandService.DEFAULT_DAYS) int days,
            @RequestParam(defaultValue = "false") boolean includeDrafts) {
        StemDemand.Board board = service.build(from, days, includeDrafts);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + StemDemand.suggestedCsvFileName(board) + "\"")
                .body(StemDemand.csv(board).getBytes(StandardCharsets.UTF_8));
    }

    /** The same window split by event: each order with its date and what it needs, crop by crop. */
    @GetMapping("/events")
    public ResponseEntity<List<StemDemand.Event>> events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "" + StemDemandService.DEFAULT_DAYS) int days,
            @RequestParam(defaultValue = "false") boolean includeDrafts) {
        return ResponseEntity.ok(service.build(from, days, includeDrafts).events());
    }

    @GetMapping("/events/csv")
    public ResponseEntity<byte[]> eventsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "" + StemDemandService.DEFAULT_DAYS) int days,
            @RequestParam(defaultValue = "false") boolean includeDrafts) {
        StemDemand.Board board = service.build(from, days, includeDrafts);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + StemDemand.suggestedEventsCsvFileName(board) + "\"")
                .body(StemDemand.eventsCsv(board).getBytes(StandardCharsets.UTF_8));
    }

    /** Body {@code {"recipeId": 5}} counts the line as that recipe; {@code {"recipeId": null}} stops. */
    @PutMapping("/orders/{orderId}/lines/{lineId}/recipe")
    public ResponseEntity<StemDemandService.RecipeLink> linkRecipe(@PathVariable Long orderId,
                                                                   @PathVariable Long lineId,
                                                                   @RequestBody RecipeLinkRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("A recipeId (or null to stop counting) is required.");
        }
        return ResponseEntity.ok(service.linkRecipe(orderId, lineId, body.recipeId()));
    }

    public record RecipeLinkRequest(Long recipeId) {
    }
}
