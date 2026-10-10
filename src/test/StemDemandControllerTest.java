package com.flowerfarm.controller;

import com.flowerfarm.service.StemDemand;
import com.flowerfarm.service.StemDemandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Stem demand REST boundary")
class StemDemandControllerTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 12);

    private final StemDemandService service = mock(StemDemandService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StemDemandController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        StemDemand.Board board = StemDemand.plan(FROM, FROM.plusDays(13), false,
                List.of(new StemDemand.Need(FROM.plusDays(5), "Nootka Rose", 40, true, "Order #1 Bloom Studio", 1L,
                        "Order #1 Bloom Studio")),
                List.of(new StemDemand.Stock("Nootka Rose", 25)), List.of(), List.of(), List.of());
        when(service.build(any(), anyInt(), anyBoolean())).thenReturn(board);
    }

    @Test
    @DisplayName("GET returns lines with cooler, cut, and short amounts for the requested window")
    void board() throws Exception {
        mockMvc.perform(get("/api/production-studio/stem-demand")
                        .param("from", "2026-10-12").param("days", "14").param("includeDrafts", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].crop").value("Nootka Rose"))
                .andExpect(jsonPath("$.lines[0].needed").value(40))
                .andExpect(jsonPath("$.lines[0].fromCooler").value(25))
                .andExpect(jsonPath("$.lines[0].shortBy").value(15))
                .andExpect(jsonPath("$.lines[0].action").value("BUY"));
        verify(service).build(FROM, 14, true);
    }

    @Test
    @DisplayName("GET defaults to today and two weeks without drafts")
    void defaults() throws Exception {
        mockMvc.perform(get("/api/production-studio/stem-demand")).andExpect(status().isOk());
        verify(service).build(isNull(), eq(StemDemandService.DEFAULT_DAYS), eq(false));
    }

    @Test
    @DisplayName("CSV download names the dates and uses the spreadsheet columns")
    void csv() throws Exception {
        mockMvc.perform(get("/api/production-studio/stem-demand/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"stem-demand-2026-10-12-to-2026-10-25.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("Date,Crop,Stems needed")));
    }

    @Test
    @DisplayName("GET events returns each event with its crops, and the board JSON carries the same events")
    void events() throws Exception {
        mockMvc.perform(get("/api/production-studio/stem-demand/events")
                        .param("from", "2026-10-12").param("days", "21").param("includeDrafts", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].event").value("Order #1 Bloom Studio"))
                .andExpect(jsonPath("$[0].confirmed").value(true))
                .andExpect(jsonPath("$[0].lines[0].crop").value("Nootka Rose"))
                .andExpect(jsonPath("$[0].lines[0].fromCooler").value(25))
                .andExpect(jsonPath("$[0].lines[0].shortBy").value(15));
        verify(service).build(FROM, 21, true);
        mockMvc.perform(get("/api/production-studio/stem-demand"))
                .andExpect(jsonPath("$.events[0].lines[0].needed").value(40));
    }

    @Test
    @DisplayName("events CSV download names the dates and uses the by-event columns")
    void eventsCsv() throws Exception {
        mockMvc.perform(get("/api/production-studio/stem-demand/events/csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"buying-list-by-event-2026-10-12-to-2026-10-25.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("Date,Event,Status,Crop")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Order #1 Bloom Studio,Confirmed,Nootka Rose,40,25,0,15")));
    }

    @Test
    @DisplayName("PUT links a line to a recipe; bad windows and unknown records map to 400 and 404")
    void linkAndErrors() throws Exception {
        when(service.linkRecipe(20L, 7L, 5L))
                .thenReturn(new StemDemandService.RecipeLink(20L, 7L, "Centerpiece", 5L, "Centerpiece"));
        when(service.linkRecipe(20L, 8L, 5L)).thenThrow(new IndexOutOfBoundsException("No product line"));
        when(service.build(any(), eq(0), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Choose between 1 and 120 days."));

        mockMvc.perform(put("/api/production-studio/stem-demand/orders/20/lines/7/recipe")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipeId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipeName").value("Centerpiece"));
        mockMvc.perform(put("/api/production-studio/stem-demand/orders/20/lines/8/recipe")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipeId\":5}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/production-studio/stem-demand").param("days", "0"))
                .andExpect(status().isBadRequest());
    }
}
