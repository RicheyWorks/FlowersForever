package com.flowerfarm.controller;

import com.flowerfarm.model.EventServiceProfile;
import com.flowerfarm.reference.EventServiceCatalog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only REST access to sourced wedding and memorial service starters. */
@RestController
@RequestMapping("/api/event-services")
public final class EventServiceController {

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String occasion,
            @RequestParam(required = false) String category) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String selectedOccasion = occasion == null ? "" : occasion.trim();
        String selectedCategory = category == null ? "" : category.trim();
        List<EventServiceProfile> matches = EventServiceCatalog.all().stream()
                .filter(service -> query.isBlank()
                        || service.searchableText().contains(query))
                .filter(service -> selectedOccasion.isBlank()
                        || service.occasion().equalsIgnoreCase(selectedOccasion))
                .filter(service -> selectedCategory.isBlank()
                        || service.category().equalsIgnoreCase(selectedCategory))
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", matches.size());
        response.put("totalAvailable", EventServiceCatalog.all().size());
        response.put("filters", Map.of(
                "q", query,
                "occasion", selectedOccasion,
                "category", selectedCategory));
        response.put("occasions", EventServiceCatalog.occasions());
        response.put("categories", EventServiceCatalog.categories());
        response.put("services", matches);
        response.put("disclaimer", "Planning starters only. Confirm provider licensing, food "
                + "safety, contracts, insurance, venue rules, accessibility, privacy, image "
                + "rights, cultural preferences, and local requirements.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sources")
    public ResponseEntity<?> sources() {
        return ResponseEntity.ok(Map.of(
                "count", EventServiceCatalog.sources().size(),
                "sources", EventServiceCatalog.sources()));
    }
}
