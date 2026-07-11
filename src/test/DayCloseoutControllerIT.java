package com.flowerfarm.integration;

import com.flowerfarm.controller.DayCloseoutController;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.DayCloseoutService.DayCloseout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DayCloseoutController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/closeout")
class DayCloseoutControllerIT {

    @Autowired MockMvc mockMvc;

    @MockBean DayCloseoutService dayCloseoutService;

    private DayCloseout sample() {
        LocalDate d = LocalDate.of(2026, 7, 12);
        return new DayCloseout(
                d, "18:30", "Kitsap",
                2, 120.0,
                List.of(new DayCloseoutService.FulfilledLine(1L, "Kitsap Blooms", 80.0, 2)),
                0, 1, 40.0,
                50.0, 2, 200.0,
                300.0, 40.0, 500.0,
                10, List.of(),
                List.of("Restock Dahlias"),
                "DAY CLOSEOUT"
        );
    }

    @Test
    @DisplayName("JSON closeout")
    void json() throws Exception {
        when(dayCloseoutService.build()).thenReturn(sample());
        mockMvc.perform(get("/api/closeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Kitsap"))
                .andExpect(jsonPath("$.fulfilledCount").value(2))
                .andExpect(jsonPath("$.nextSteps[0]").value("Restock Dahlias"));
    }

    @Test
    @DisplayName("plain text")
    void text() throws Exception {
        when(dayCloseoutService.build()).thenReturn(sample());
        mockMvc.perform(get("/api/closeout/text"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("DAY CLOSEOUT")));
    }

    @Test
    @DisplayName("PDF")
    void pdf() throws Exception {
        when(dayCloseoutService.build()).thenReturn(sample());
        when(dayCloseoutService.generatePdf(org.mockito.ArgumentMatchers.any()))
                .thenReturn("%PDF-1.4".getBytes());
        mockMvc.perform(get("/api/closeout/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("day-closeout-")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/pdf")));
    }
}
