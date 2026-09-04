package com.flowerfarm.integration;

import com.flowerfarm.controller.PlantReferenceController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlantReferenceController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PlantReferenceController (MockMvc)")
class PlantReferenceControllerIT {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("lists the broad sourced regional catalog")
    void listsCatalog() throws Exception {
        mockMvc.perform(get("/api/plants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(90)))
                .andExpect(jsonPath("$.totalAvailable", greaterThanOrEqualTo(90)))
                .andExpect(jsonPath("$.region", containsString("Washington")))
                .andExpect(jsonPath("$.plants[*].commonName", hasItem("Dahlia")))
                .andExpect(jsonPath("$.plants[0].source.url", containsString("https://")));
    }

    @Test
    @DisplayName("supports text, type, and native-only filters")
    void filtersCatalog() throws Exception {
        mockMvc.perform(get("/api/plants").param("q", "wet")
                        .param("type", "Native shrub").param("nativeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.filters.nativeOnly", is(true)))
                .andExpect(jsonPath("$.plants[*].nativeToPacificNorthwest",
                        everyItem(is(true))))
                .andExpect(jsonPath("$.plants[*].type",
                        everyItem(is("Native shrub"))));
    }

    @Test
    @DisplayName("publishes the public research library")
    void listsSources() throws Exception {
        mockMvc.perform(get("/api/plants/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(15)))
                .andExpect(jsonPath("$.sources[*].organization",
                        hasItem(containsString("Oregon State University"))))
                .andExpect(jsonPath("$.sources[*].organization",
                        hasItem(containsString("University of Washington"))));
    }
}
