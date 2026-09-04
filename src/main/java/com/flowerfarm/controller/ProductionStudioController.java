package com.flowerfarm.controller;

import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.service.ProductionStudioService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** REST boundary for crop production, recipes, economics, and outbound handoffs. */
@RestController
@RequestMapping("/api/production-studio")
public class ProductionStudioController {

    private final ProductionStudioService service;

    public ProductionStudioController(ProductionStudioService service) {
        this.service = service;
    }

    @GetMapping("/seasons/{year}")
    public ResponseEntity<?> season(@PathVariable int year) {
        return service.findSeasonSettings(year)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/seasons/{year}")
    public ResponseEntity<?> saveSeason(@PathVariable int year,
                                        @RequestBody SeasonSettingsRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Season settings are required.");
        }
        double laborRate = body.loadedLaborRatePerHour() == null
                ? service.findSeasonSettings(year)
                .map(FarmSeasonSettings::getLoadedLaborRatePerHour).orElse(0d)
                : body.loadedLaborRatePerHour();
        return ResponseEntity.ok(service.saveSeasonSettings(new FarmSeasonSettings(
                year, body.lastSpringFrost(), body.firstFallFrost(), body.source(), laborRate)));
    }

    @GetMapping("/seasons/{year}/production-costs")
    public ResponseEntity<?> productionCosts(
            @PathVariable int year,
            @RequestParam(required = false) Double laborRatePerHour) {
        return ResponseEntity.ok(service.productionCosts(year, laborRatePerHour));
    }

    @GetMapping("/templates")
    public ResponseEntity<?> templates() {
        return ResponseEntity.ok(service.listTemplates());
    }

    @PostMapping("/templates")
    public ResponseEntity<?> createTemplate(@RequestBody GrowingTemplate template) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTemplate(template));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<?> updateTemplate(@PathVariable Long id,
                                            @RequestBody GrowingTemplate template) {
        return ResponseEntity.ok(service.updateTemplate(id, template));
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable Long id) {
        service.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plantings/draft")
    public ResponseEntity<?> draftPlantings(@RequestBody PlantingRequest body) {
        requirePlantingRequest(body);
        var drafts = service.draftPlantings(body.templateId(), body.seasonYear(),
                body.bedOrField(), body.bedFeet(), body.successions());
        return ResponseEntity.ok()
                .eTag(service.plantingFingerprint(drafts))
                .body(drafts);
    }

    @PostMapping("/plantings/commit")
    public ResponseEntity<?> commitPlantings(
            @RequestBody PlantingRequest body,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        requirePlantingRequest(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.commitPlantings(
                body.templateId(), body.seasonYear(), body.bedOrField(),
                body.bedFeet(), body.successions(),
                preferredFingerprint(body.previewFingerprint(), ifMatch)));
    }

    @PostMapping("/plantings/{planId}/tasks/regenerate")
    public ResponseEntity<?> regenerateTasks(@PathVariable Long planId,
                                             @RequestParam Long templateId) {
        return ResponseEntity.ok(service.regenerateTasks(planId, templateId));
    }

    @GetMapping("/tasks")
    public ResponseEntity<?> tasks(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.listTasks(from, to));
    }

    @PatchMapping("/tasks/{id}/status")
    public ResponseEntity<?> taskStatus(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        String rawStatus = body == null ? null : body.get("status");
        ProductionTaskStatus status;
        try {
            status = rawStatus == null ? null
                    : ProductionTaskStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Status must be TODO, DONE, or SKIPPED.");
        }
        return ResponseEntity.ok(service.updateTaskStatus(id, status));
    }

    @PutMapping("/tasks/{id}/actuals")
    public ResponseEntity<?> taskActuals(
            @PathVariable Long id,
            @RequestBody ProductionStudioService.TaskActuals actuals) {
        return ResponseEntity.ok(service.recordTaskActuals(id, actuals));
    }

    @GetMapping("/recipes")
    public ResponseEntity<?> recipes() {
        return ResponseEntity.ok(service.listRecipes());
    }

    @GetMapping("/recipes/{id}")
    public ResponseEntity<?> recipe(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRecipe(id));
    }

    @PostMapping("/recipes")
    public ResponseEntity<?> createRecipe(@RequestBody FloralRecipe recipe) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRecipe(recipe));
    }

    @PutMapping("/recipes/{id}")
    public ResponseEntity<?> updateRecipe(@PathVariable Long id,
                                          @RequestBody FloralRecipe recipe) {
        return ResponseEntity.ok(service.updateRecipe(id, recipe));
    }

    @DeleteMapping("/recipes/{id}")
    public ResponseEntity<?> deleteRecipe(@PathVariable Long id) {
        service.deleteRecipe(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/recipes/{id}/estimate")
    public ResponseEntity<?> estimate(
            @PathVariable Long id,
            @RequestParam int count,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDate) {
        return ResponseEntity.ok(service.estimateRecipe(id, count, eventDate));
    }

    @GetMapping("/availability")
    public ResponseEntity<?> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        return ResponseEntity.ok(service.availability(weekOf));
    }

    @GetMapping("/handoffs")
    public ResponseEntity<?> handoffs() {
        return ResponseEntity.ok(service.listHandoffs());
    }

    @PostMapping("/handoffs")
    public ResponseEntity<?> createHandoff(
            @RequestBody ProductionStudioService.HandoffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createHandoff(request));
    }

    public record SeasonSettingsRequest(
            LocalDate lastSpringFrost, LocalDate firstFallFrost, String source,
            Double loadedLaborRatePerHour) {
    }

    public record PlantingRequest(
            Long templateId, int seasonYear, String bedOrField,
            double bedFeet, int successions, String previewFingerprint) {
    }

    private static void requirePlantingRequest(PlantingRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Planting request is required.");
        }
    }

    private static String preferredFingerprint(String bodyFingerprint, String ifMatch) {
        String value = bodyFingerprint == null || bodyFingerprint.isBlank()
                ? ifMatch : bodyFingerprint;
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"")
                && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }
}
