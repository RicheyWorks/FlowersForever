package com.flowerfarm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.controller.InventoryController;
import com.flowerfarm.model.Item;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;   // explicit: shadow hamcrest's any(Class) in stubbing
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for InventoryController — starts only the web layer,
 * all service dependencies are mocked via @MockBean.
 */
@WebMvcTest(InventoryController.class)
@DisplayName("InventoryController (MockMvc)")
class InventoryControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean InventoryService inventoryService;

    private static final Item SAMPLE_ROSE =
            new Item("Nootka Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, "PNW native");

    // ── GET /api/inventory ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/inventory")
    class GetAll {

        @Test
        @DisplayName("returns 200 with JSON array of items")
        void returnsAllItems() throws Exception {
            when(inventoryService.getAllItems()).thenReturn(List.of(SAMPLE_ROSE));

            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name", is("Nootka Rose")))
                    .andExpect(jsonPath("$[0].category", is("Flowers/Plants")))
                    .andExpect(jsonPath("$[0].price", is(3.50)))
                    .andExpect(jsonPath("$[0].quantity", is(50)));
        }

        @Test
        @DisplayName("returns 200 with empty array when inventory is empty")
        void returnsEmptyArray() throws Exception {
            when(inventoryService.getAllItems()).thenReturn(List.of());

            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ── GET /api/inventory/search ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/inventory/search")
    class Search {

        @Test
        @DisplayName("returns matching items for a valid query")
        void returnsMatches() throws Exception {
            when(inventoryService.searchItems("rose")).thenReturn(List.of(SAMPLE_ROSE));

            mockMvc.perform(get("/api/inventory/search").param("q", "rose"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name", is("Nootka Rose")));
        }

        @Test
        @DisplayName("returns empty array when no items match")
        void returnsEmptyForNoMatch() throws Exception {
            when(inventoryService.searchItems("zyxwv")).thenReturn(List.of());

            mockMvc.perform(get("/api/inventory/search").param("q", "zyxwv"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    // ── POST /api/inventory ───────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/inventory")
    class AddItem {

        @Test
        @DisplayName("returns 201 Created with the added item")
        void returns201OnSuccess() throws Exception {
            doNothing().when(inventoryService).addItem(any(Item.class));

            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SAMPLE_ROSE)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is("Nootka Rose")));

            verify(inventoryService).addItem(any(Item.class));
        }

        @Test
        @DisplayName("returns 400 Bad Request when service throws IllegalArgumentException")
        void returns400OnValidationFailure() throws Exception {
            doThrow(new IllegalArgumentException("Name cannot be empty"))
                    .when(inventoryService).addItem(any(Item.class));

            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SAMPLE_ROSE)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("Name cannot be empty")));
        }
    }

    // ── PUT /api/inventory/{index} ────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/inventory/{index}")
    class EditItem {

        @Test
        @DisplayName("returns 200 OK with updated item on success")
        void returns200OnSuccess() throws Exception {
            doNothing().when(inventoryService).editItem(eq(0), any(Item.class));

            mockMvc.perform(put("/api/inventory/0")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SAMPLE_ROSE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Nootka Rose")));
        }

        @Test
        @DisplayName("returns 404 Not Found when index is out of bounds")
        void returns404OnBadIndex() throws Exception {
            doThrow(new IndexOutOfBoundsException("Invalid index: 999 (size=4)"))
                    .when(inventoryService).editItem(eq(999), any(Item.class));

            mockMvc.perform(put("/api/inventory/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SAMPLE_ROSE)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", containsString("Invalid index")));
        }

        @Test
        @DisplayName("returns 400 Bad Request when service throws IllegalArgumentException")
        void returns400OnValidationFailure() throws Exception {
            doThrow(new IllegalArgumentException("Price cannot be negative"))
                    .when(inventoryService).editItem(anyInt(), any(Item.class));

            mockMvc.perform(put("/api/inventory/0")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SAMPLE_ROSE)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error", containsString("Price cannot be negative")));
        }
    }

    // ── DELETE /api/inventory/{index} ─────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/inventory/{index}")
    class DeleteItem {

        @Test
        @DisplayName("returns 204 No Content on successful deletion")
        void returns204OnSuccess() throws Exception {
            doNothing().when(inventoryService).deleteItem(0);

            mockMvc.perform(delete("/api/inventory/0"))
                    .andExpect(status().isNoContent());

            verify(inventoryService).deleteItem(0);
        }

        @Test
        @DisplayName("returns 404 Not Found when index is out of bounds")
        void returns404OnBadIndex() throws Exception {
            doThrow(new IndexOutOfBoundsException("Invalid index: 99"))
                    .when(inventoryService).deleteItem(99);

            mockMvc.perform(delete("/api/inventory/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", containsString("Invalid index")));
        }
    }

    // ── POST /api/inventory/export ────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/inventory/export")
    class ExportCsv {

        @Test
        @DisplayName("returns 200 with confirmation message")
        void returns200WithConfirmation() throws Exception {
            doNothing().when(inventoryService).exportToCsv("exported_inventory.csv");

            mockMvc.perform(post("/api/inventory/export"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message", containsString("exported_inventory.csv")));

            verify(inventoryService).exportToCsv("exported_inventory.csv");
        }
    }

    // ── POST /api/inventory/sample-rose ──────────────────────────────────────

    @Nested
    @DisplayName("POST /api/inventory/sample-rose")
    class AddSampleRose {

        @Test
        @DisplayName("returns 201 Created with a Nootka Rose payload")
        void returns201WithNootkaRose() throws Exception {
            doNothing().when(inventoryService).addItem(any(Item.class));

            mockMvc.perform(post("/api/inventory/sample-rose"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is("Nootka Rose")))
                    .andExpect(jsonPath("$.category", is("Flowers/Plants")));
        }
    }
}
