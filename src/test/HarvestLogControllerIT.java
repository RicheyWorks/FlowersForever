package com.flowerfarm.integration;

import com.flowerfarm.controller.HarvestController;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.HarvestGrade;
import com.flowerfarm.service.ExportPathResolver;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.HarvestService.HarvestLogReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HarvestController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/harvest/log")
class HarvestLogControllerIT {

    @Autowired MockMvc mockMvc;

    @MockitoBean HarvestService harvestService;
    @MockitoBean ExportPathResolver exportPathResolver;

    private HarvestLogReport sample() {
        Map<String, Double> byCrop = new LinkedHashMap<>();
        byCrop.put("Nootka Rose", 40.0);
        HarvestEntry e = new HarvestEntry(
                LocalDate.of(2026, 7, 11), "Nootka Rose", 40, "stems",
                "Bed A", "", "LOT-ROSE-0711", HarvestGrade.SELECT,
                3, "Cooler A / Shelf 2");
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
                .andExpect(jsonPath("$.totalQuantity").value(40.0))
                .andExpect(jsonPath("$.totalLossQuantity").value(3.0))
                .andExpect(jsonPath("$.totalGrossQuantity").value(43.0));
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

    @Test
    @DisplayName("exact lot lookup exposes structured traceability")
    void lotLookup() throws Exception {
        HarvestEntry entry = sample().entries().get(0);
        when(harvestService.findByLotCode("lot-rose-0711"))
                .thenReturn(Optional.of(entry));

        mockMvc.perform(get("/api/harvest/lots/lot-rose-0711"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotCode").value("LOT-ROSE-0711"))
                .andExpect(jsonPath("$.grade").value("SELECT"))
                .andExpect(jsonPath("$.quantity").value(40.0))
                .andExpect(jsonPath("$.lossQuantity").value(3.0))
                .andExpect(jsonPath("$.grossQuantity").value(43.0))
                .andExpect(jsonPath("$.storageLocation")
                        .value("Cooler A / Shelf 2"));
    }

    @Test
    @DisplayName("query lot lookup supports slash-delimited farm codes")
    void slashLotLookup() throws Exception {
        HarvestEntry entry = sample().entries().get(0);
        entry.setLotCode("2026/ROSE/0711");
        when(harvestService.findByLotCode("2026/rose/0711"))
                .thenReturn(Optional.of(entry));

        mockMvc.perform(get("/api/harvest/lot")
                        .param("code", "2026/rose/0711"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lotCode").value("2026/ROSE/0711"));
    }

    @Test
    @DisplayName("printable lot label uses a stable lot filename")
    void lotLabelPdf() throws Exception {
        HarvestEntry entry = sample().entries().get(0);
        entry.setLotCode("2026/ROSE/0711");
        when(harvestService.findById(1L)).thenReturn(Optional.of(entry));
        when(harvestService.generateLotLabelPdf(1L))
                .thenReturn("%PDF-1.4".getBytes());

        mockMvc.perform(get("/api/harvest/1/label.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(
                                "harvest-lot-2026-ROSE-0711.pdf")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/pdf")))
                .andExpect(content().bytes("%PDF-1.4".getBytes()));
    }
}
