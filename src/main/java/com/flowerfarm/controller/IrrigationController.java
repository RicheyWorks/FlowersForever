package com.flowerfarm.controller;

import com.flowerfarm.service.IrrigationAdvisorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kitsap irrigation advisory — climatology + optional live Open-Meteo.
 *
 * <ul>
 *   <li>{@code GET /api/irrigation/advice} — prefer live weather when available</li>
 *   <li>{@code GET /api/irrigation/advice?live=false} — climatology only</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/irrigation")
public class IrrigationController {

    private final IrrigationAdvisorService irrigationAdvisorService;

    public IrrigationController(IrrigationAdvisorService irrigationAdvisorService) {
        this.irrigationAdvisorService = irrigationAdvisorService;
    }

    @GetMapping("/advice")
    public Map<String, Object> advice(
            @RequestParam(value = "live", defaultValue = "true") boolean live) {
        return irrigationAdvisorService.advise(live).toMap();
    }
}
