package com.flowerfarm.integration;

import com.flowerfarm.controller.MarketDayController;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MarketDayPackingService.MarketDayPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MarketDayController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/market-day")
class MarketDayControllerIT {

    @Autowired MockMvc mockMvc;

    @MockBean MarketDayPackingService packingService;

    private MarketDayPlan samplePlan() {
        return new MarketDayPlan(
                LocalDate.of(2026, 7, 12),
                LocalDate.of(2026, 7, 12),
                LocalDate.of(2026, 7, 12),
                "CONFIRMED",
                1,
                120.0,
                List.of(new MarketDayPackingService.CustomerPack(
                        5L, "Kitsap Blooms", "FLORIST", "CONFIRMED",
                        LocalDate.of(2026, 7, 12), "AM",
                        List.of(new MarketDayPackingService.PackLine(
                                "Nootka Rose", "stems", 40, 3.0, 120.0)),
                        120.0
                )),
                List.of(new MarketDayPackingService.ProductNeed(
                        "Nootka Rose", "stems", 40, 50, false, 0)),
                0,
                "MARKET DAY PACKING LIST"
        );
    }

    @Test
    @DisplayName("JSON plan")
    void jsonPlan() throws Exception {
        when(packingService.buildPlan(any(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(samplePlan());

        mockMvc.perform(get("/api/market-day").param("date", "2026-07-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.pipelineValue").value(120.0))
                .andExpect(jsonPath("$.customers[0].customerName").value("Kitsap Blooms"))
                .andExpect(jsonPath("$.pickList[0].productName").value("Nootka Rose"));
    }

    @Test
    @DisplayName("plain text")
    void plainText() throws Exception {
        when(packingService.buildPlan(any(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(samplePlan());

        mockMvc.perform(get("/api/market-day/text"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("MARKET DAY")));
    }

    @Test
    @DisplayName("CSV export")
    void csvExport() throws Exception {
        when(packingService.buildPlan(any(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(samplePlan());
        when(packingService.exportCsv(any())).thenReturn("section,orderId\nPICK,,,,\n");

        mockMvc.perform(get("/api/market-day/export.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("market-day-")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("section")));
    }

    @Test
    @DisplayName("packing PDF")
    void packingPdf() throws Exception {
        when(packingService.buildPlan(any(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(samplePlan());
        when(packingService.generatePackingPdf(any()))
                .thenReturn("%PDF-1.4 mock".getBytes());

        mockMvc.perform(get("/api/market-day/packing.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("market-day-packing-")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/pdf")));
    }

    @Test
    @DisplayName("POST fulfill batch")
    void fulfillBatch() throws Exception {
        when(packingService.buildPlan(any(), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(samplePlan());
        when(packingService.fulfillConfirmedOrders(any())).thenReturn(
                new MarketDayPackingService.FulfillBatchResult(
                        1, 1, 0, 0, List.of("#5 Kitsap Blooms → FULFILLED")));

        mockMvc.perform(post("/api/market-day/fulfill").param("date", "2026-07-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfilled").value(1))
                .andExpect(jsonPath("$.attempted").value(1))
                .andExpect(jsonPath("$.messages[0]").value(
                        org.hamcrest.Matchers.containsString("FULFILLED")));
    }
}
