package com.flowerfarm.integration;

import com.flowerfarm.controller.EventServiceController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventServiceController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("EventServiceController (MockMvc)")
class EventServiceControllerIT {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("lists broad wedding and memorial service coverage")
    void listsCatalog() throws Exception {
        mockMvc.perform(get("/api/event-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(24)))
                .andExpect(jsonPath("$.totalAvailable", greaterThanOrEqualTo(24)))
                .andExpect(jsonPath("$.categories", hasItem("Catering")))
                .andExpect(jsonPath("$.categories", hasItem("Photography & media")))
                .andExpect(jsonPath("$.services[*].serviceName",
                        hasItem("Memorial photography")))
                .andExpect(jsonPath("$.disclaimer", containsString("image rights")));
    }

    @Test
    @DisplayName("composes text, occasion, and category filters")
    void filtersCatalog() throws Exception {
        mockMvc.perform(get("/api/event-services")
                        .param("q", "family")
                        .param("occasion", "Memorial / funeral")
                        .param("category", "Photography & media"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.filters.occasion", is("Memorial / funeral")))
                .andExpect(jsonPath("$.services[*].occasion",
                        everyItem(is("Memorial / funeral"))))
                .andExpect(jsonPath("$.services[*].category",
                        everyItem(is("Photography & media"))));
    }

    @Test
    @DisplayName("publishes the official event workflow source library")
    void listsSources() throws Exception {
        mockMvc.perform(get("/api/event-services/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.sources[*].organization", hasItem("Tripleseat")))
                .andExpect(jsonPath("$.sources[*].organization",
                        hasItem("Tribute Technology")));
    }
}
