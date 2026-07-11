package com.flowerfarm.integration;

import com.flowerfarm.controller.HarvestController;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.HarvestService.HarvestLogReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HarvestController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/harvest/log")
class HarvestLogControllerIT {

    @Autowired MockMvc mockMvc;

    @MockBean HarvestService harvestService;

    private HarvestLogReport sample() {
        Map<String, Double> byCrop = new LinkedHashMap<>();
        byCrop.put("Nootka Rose", 40.0);
        HarvestEntry e = new HarvestEntry(LocalDate.of(2026, 7, 11), "Nootka Rose", 40, "stems", "Bed A", "");
        e.setId(1L);
        return new HarvestLogReport(
                "2026-07-05", "2026-07-11", 1, 40.0, byCrop, List.of(e), "HARVEST LOG"
        );
    }

    @Test
    @DisplayName("JSON harvest log")
    void json() throws Exception {
        when(harvestService.buildHarvestLogReportLast7Days()).thenReturn(sample());
        mockMvc.perform(get("/api/harvest/log").param("week", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryCount").value(1))
                .andExpect(jsonPath("$.totalQuantity").value(40.0));
    }

    @Test
    @DisplayName("plain text")
    void text() throws Exception {
        when(harvestService.buildHarvestLogReportLast7Days()).thenReturn(sample());
        mockMvc.perform(get("/api/harvest/log/text").param("week", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("HARVEST LOG")));
    }

    @Test
    @DisplayName("PDF")
    void pdf() throws Exception {
        when(harvestService.buildHarvestLogReportLast7Days()).thenReturn(sample());
        when(harvestService.generateHarvestLogPdf(any())).thenReturn("%PDF-1.4".getBytes());
        mockMvc.perform(get("/api/harvest/log/report.pdf").param("week", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("harvest-log-")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/pdf")));
    }

    @Test
    @DisplayName("custom range via service")
    void range() throws Exception {
        when(harvestService.buildHarvestLogReport(any(), any())).thenReturn(sample());
        mockMvc.perform(get("/api/harvest/log")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryCount").value(1));
    }
}
