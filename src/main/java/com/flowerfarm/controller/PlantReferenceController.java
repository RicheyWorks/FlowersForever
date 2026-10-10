package com.flowerfarm.controller;

import com.flowerfarm.model.PnwPlantProfile;
import com.flowerfarm.reference.PnwPlantCatalog;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only REST access to the bundled, sourced Pacific Northwest plant guide. */
@RestController
@RequestMapping("/api/plants")
public final class PlantReferenceController {

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean nativeOnly) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String selectedType = type == null ? "" : type.trim();
        List<PnwPlantProfile> matches = PnwPlantCatalog.all().stream()
                .filter(plant -> query.isBlank() || plant.searchableText().contains(query))
                .filter(plant -> selectedType.isBlank()
                        || plant.type().equalsIgnoreCase(selectedType))
                .filter(plant -> !Boolean.TRUE.equals(nativeOnly)
                        || plant.nativeToPacificNorthwest())
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("region", "Washington and Oregon, with emphasis west of the Cascades");
        response.put("count", matches.size());
        response.put("totalAvailable", PnwPlantCatalog.all().size());
        response.put("filters", Map.of(
                "q", query,
                "type", selectedType,
                "nativeOnly", Boolean.TRUE.equals(nativeOnly)));
        response.put("plants", matches);
        response.put("disclaimer", "Regional reference only; confirm cultivar, microclimate, "
                + "soil, toxicity, and current pest guidance before field decisions.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sources")
    public ResponseEntity<?> sources() {
        return ResponseEntity.ok(Map.of(
                "count", PnwPlantCatalog.sources().size(),
                "sources", PnwPlantCatalog.sources()));
    }
}
