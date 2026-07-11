package com.flowerfarm.integration;

import com.flowerfarm.controller.MorningBriefingController;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.MorningBriefingService.MorningBriefing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MorningBriefingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/briefing")
class MorningBriefingControllerIT {

    @Autowired MockMvc mockMvc;

    @MockBean MorningBriefingService morningBriefingService;

    private MorningBriefing sample() {
        LocalDate d = LocalDate.of(2026, 7, 12);
        MarketDayPackingService.MarketDayPlan pack = new MarketDayPackingService.MarketDayPlan(
                d, d, d, "CONFIRMED", 0, 0, List.of(), List.of(), 0, "pack");
        HarvestService.BedProductionReport beds = new HarvestService.BedProductionReport(
                d.toString(), d.toString(), 0, 0, 0, List.of(), "beds");
        IrrigationAdvisorService.IrrigationAdvice water =
                new IrrigationAdvisorService.IrrigationAdvice(
                        "Kitsap", "CLIMATOLOGY", d.toString(),
                        IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                        IrrigationAdvisorService.Priority.LOW, "OK",
                        List.of(), List.of(), null, null, null, null, null, "note");
        return new MorningBriefing(
                d, "08:00", "Kitsap", 10, 50, 20, pack, beds, water,
                List.of(), 10, List.of("Do a thing"), "MORNING BRIEFING"
        );
    }

    @Test
    @DisplayName("JSON briefing")
    void json() throws Exception {
        when(morningBriefingService.build(anyBoolean())).thenReturn(sample());
        mockMvc.perform(get("/api/briefing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Kitsap"))
                .andExpect(jsonPath("$.actionItems[0]").value("Do a thing"));
    }

    @Test
    @DisplayName("plain text")
    void text() throws Exception {
        when(morningBriefingService.build(anyBoolean())).thenReturn(sample());
        mockMvc.perform(get("/api/briefing/text"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MORNING BRIEFING")));
    }

    @Test
    @DisplayName("PDF")
    void pdf() throws Exception {
        when(morningBriefingService.build(anyBoolean())).thenReturn(sample());
        when(morningBriefingService.generatePdf(org.mockito.ArgumentMatchers.any()))
                .thenReturn("%PDF-1.4".getBytes());
        mockMvc.perform(get("/api/briefing/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("morning-briefing-")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/pdf")));
    }
}
