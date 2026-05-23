package com.flowerfarm.controller;

import com.flowerfarm.service.TrendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for ML trend analysis and static rose variety info.
 *
 * <pre>
 * GET /api/trends        → run Weka linear regression, return result
 * GET /api/roses         → structured list of PNW rose variety suggestions
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class TrendController {

    private final TrendService trendService;

    public TrendController(TrendService trendService) {
        this.trendService = trendService;
    }

    /**
     * Runs the linear-regression trend analysis and returns either a result
     * object or an error message wrapped in JSON.
     */
    @GetMapping("/trends")
    public ResponseEntity<?> getTrends() {
        TrendService.TrendResult result = trendService.analyzeQuantityTrend();
        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "predictedQuantity", result.predictedQuantity(),
                    "summary", result.summary()
            ));
        }
        return ResponseEntity.internalServerError()
                .body(Map.of("error", result.error()));
    }

    /**
     * Returns structured rose variety data for the PNW west of the Cascades.
     * Organised into three groups: one-time bloomers, repeat bloomers, and
     * regionally native / disease-resistant varieties.
     */
    @GetMapping("/roses")
    public ResponseEntity<?> getRoseVarieties() {
        return ResponseEntity.ok(Map.of(
                "region", "Oregon and Western Washington — west of the Cascades",
                "oneTimeBloomers", List.of(
                        Map.of("type", "Alba",      "example", "Alba Maxima",       "notes", "White, fragrant, hardy in shade"),
                        Map.of("type", "Damask",    "example", "Ispahan",           "notes", "Pink, strong scent, drought-tolerant"),
                        Map.of("type", "Gallicas",  "example", "Charles de Mills",  "notes", "Crimson, compact shrub"),
                        Map.of("type", "Centifolia","example", "Fantin Latour",     "notes", "Pink, very full blooms"),
                        Map.of("type", "Moss",      "example", "William Lobb",      "notes", "Purple, mossy buds, vigorous")
                ),
                "repeatBloomers", List.of(
                        Map.of("type", "Bourbon",           "example", "Zephirine Drouhin",        "notes", "Pink climber, thornless, shade-tolerant"),
                        Map.of("type", "Hybrid Perpetual",  "example", "Reine des Violettes",      "notes", "Purple, recurrent blooms, fragrant"),
                        Map.of("type", "Portland",          "example", "Comte de Chambord",        "notes", "Pink, compact, spicy scent")
                ),
                "regionalVarieties", List.of(
                        Map.of("name", "Nootka Rose (Rosa nutkana)",     "notes", "Native PNW, pink single blooms, wildlife-friendly, tolerates wet soils"),
                        Map.of("name", "Baldhip Rose (Rosa gymnocarpa)", "notes", "Native, pink, small hips, shade-loving"),
                        Map.of("name", "Cluster Rose (Rosa pisocarpa)",  "notes", "Native, pink clusters, erosion control"),
                        Map.of("name", "Rosa rugosa Hansa",              "notes", "Purple shrub, rugged, salt-tolerant, disease-resistant"),
                        Map.of("name", "New Dawn Climber",               "notes", "Light pink, vigorous, blackspot-resistant"),
                        Map.of("name", "Queen Elizabeth Hybrid Tea",     "notes", "Pink, tall, hearty"),
                        Map.of("name", "Strawberry Hill (David Austin)", "notes", "Pink climber, fragrant English rose"),
                        Map.of("name", "Munstead Wood (David Austin)",   "notes", "Rich dark red, OGR-type bloom, fragrant"),
                        Map.of("name", "Harison's Yellow Shrub",         "notes", "Yellow, drought-tolerant")
                ),
                "tip", "Choose disease-resistant varieties like rugosas for humid PNW conditions. "
                     + "Consult Portland Nursery or Swansons (Seattle) for local availability."
        ));
    }
}
