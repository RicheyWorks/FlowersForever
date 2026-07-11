package com.flowerfarm.integration;

import com.flowerfarm.controller.IrrigationController;
import com.flowerfarm.service.IrrigationAdvisorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IrrigationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/irrigation/advice")
class IrrigationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IrrigationAdvisorService irrigationAdvisorService;

    @Test
    @DisplayName("returns advisory payload")
    void adviceOk() throws Exception {
        when(irrigationAdvisorService.advise(anyBoolean())).thenReturn(
                new IrrigationAdvisorService.IrrigationAdvice(
                        "Port Orchard / Kitsap County, WA",
                        "CLIMATOLOGY",
                        "2026-07-11",
                        IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                        IrrigationAdvisorService.Priority.HIGH,
                        "Peak dry summer",
                        List.of("Deep soak"),
                        List.of("Bed A"),
                        null, null, null, null, null,
                        "July–August dry"
                ));

        mockMvc.perform(get("/api/irrigation/advice").param("live", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Port Orchard / Kitsap County, WA"))
                .andExpect(jsonPath("$.mode").value("CLIMATOLOGY"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.season").value("PEAK_DRY_SUMMER"))
                .andExpect(jsonPath("$.actions[0]").value("Deep soak"))
                .andExpect(jsonPath("$.activeBeds[0]").value("Bed A"));
    }
}
