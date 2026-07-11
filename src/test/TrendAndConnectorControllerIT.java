package com.flowerfarm.integration;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.SyncSummary;
import com.flowerfarm.controller.ConnectorController;
import com.flowerfarm.controller.TrendController;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// ═══════════════════════════════════════════════════════════════════════════
// TrendController
// ═══════════════════════════════════════════════════════════════════════════

@WebMvcTest(controllers = {TrendController.class, ConnectorController.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TrendController + ConnectorController (MockMvc)")
class TrendAndConnectorControllerIT {

    @Autowired MockMvc mockMvc;

    @MockBean TrendService trendService;
    @MockBean ConnectorRegistry connectorRegistry;
    @MockBean SyncHistoryService syncHistoryService;

    // ── GET /api/trends ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/trends")
    class GetTrends {

        @Test
        @DisplayName("returns 200 with predictedQuantity and summary on success")
        void returns200OnSuccess() throws Exception {
            TrendService.TrendResult result =
                    new TrendService.TrendResult(125.5, "Trend summary text here", null);
            when(trendService.analyzeQuantityTrend()).thenReturn(result);

            mockMvc.perform(get("/api/trends"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.predictedQuantity", is(125.5)))
                    .andExpect(jsonPath("$.summary", is("Trend summary text here")));
        }

        @Test
        @DisplayName("returns 500 Internal Server Error when analysis fails")
        void returns500OnFailure() throws Exception {
            TrendService.TrendResult result =
                    new TrendService.TrendResult(0, null, "Weka regression failed");
            when(trendService.analyzeQuantityTrend()).thenReturn(result);

            mockMvc.perform(get("/api/trends"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error", is("Weka regression failed")));
        }
    }

    // ── GET /api/roses ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/roses")
    class GetRoses {

        @Test
        @DisplayName("returns 200 with region, oneTimeBloomers, repeatBloomers, regionalVarieties")
        void returns200WithAllGroups() throws Exception {
            mockMvc.perform(get("/api/roses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.region", containsString("Cascades")))
                    .andExpect(jsonPath("$.oneTimeBloomers", hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$.repeatBloomers", hasSize(greaterThan(0))))
                    .andExpect(jsonPath("$.regionalVarieties", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("one-time bloomers include Alba and Damask entries")
        void oneTimeBloomersContainExpectedTypes() throws Exception {
            mockMvc.perform(get("/api/roses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.oneTimeBloomers[*].type",
                            hasItems("Alba", "Damask")));
        }

        @Test
        @DisplayName("regional varieties include Nootka Rose")
        void regionalVarietiesContainNootkaRose() throws Exception {
            mockMvc.perform(get("/api/roses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.regionalVarieties[*].name",
                            hasItem(containsString("Nootka Rose"))));
        }

        @Test
        @DisplayName("response includes a tip field")
        void responseIncludesTip() throws Exception {
            mockMvc.perform(get("/api/roses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tip").isString());
        }
    }

    // ── GET /api/connectors ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/connectors")
    class ListConnectors {

        @Test
        @DisplayName("returns 200 with the list from registry")
        void returns200WithList() throws Exception {
            List<Map<String, Object>> info = List.of(
                    Map.of("name", "csv", "direction", "IMPORT_ONLY",
                           "canImport", true, "canExport", false, "available", true)
            );
            when(connectorRegistry.listConnectorInfo()).thenReturn(info);

            mockMvc.perform(get("/api/connectors"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].name", is("csv")));
        }
    }

    // ── GET /api/connectors/{name}/status ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/connectors/{name}/status")
    class ConnectorStatus {

        @Test
        @DisplayName("returns 404 when connector is not found")
        void returns404WhenNotFound() throws Exception {
            when(connectorRegistry.find("phantom")).thenReturn(java.util.Optional.empty());

            mockMvc.perform(get("/api/connectors/phantom/status"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error", containsString("not found")));
        }
    }

    // ── POST /api/connectors/{name}/import ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/connectors/{name}/import")
    class RunImport {

        @Test
        @DisplayName("returns 200 with imported count on success")
        void returns200OnSuccess() throws Exception {
            List<Item> items = List.of(
                    new Item("Rose", "Flowers/Plants", 3.50, "Per Stem", 2.00, 50, "")
            );
            when(connectorRegistry.runImport("csv"))
                    .thenReturn(ConnectorResult.ok(items, "Imported 1", "csv"));

            mockMvc.perform(post("/api/connectors/csv/import"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.connector", is("csv")))
                    .andExpect(jsonPath("$.imported", is(1)))
                    .andExpect(jsonPath("$.items", hasSize(1)));
        }

        @Test
        @DisplayName("returns 502 Bad Gateway on connector failure")
        void returns502OnConnectorFailure() throws Exception {
            when(connectorRegistry.runImport("csv"))
                    .thenReturn(ConnectorResult.fail("File not found", "FileNotFoundException: missing.csv", "csv"));

            mockMvc.perform(post("/api/connectors/csv/import"))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error", containsString("File not found")));
        }
    }

    // ── POST /api/connectors/{name}/export ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/connectors/{name}/export")
    class RunExport {

        @Test
        @DisplayName("returns 200 with export count on success")
        void returns200OnSuccess() throws Exception {
            when(connectorRegistry.runExport("csv"))
                    .thenReturn(ConnectorResult.ok(4, "Exported 4", "csv"));

            mockMvc.perform(post("/api/connectors/csv/export"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exported", is(4)));
        }

        @Test
        @DisplayName("returns 502 Bad Gateway on connector failure")
        void returns502OnFailure() throws Exception {
            when(connectorRegistry.runExport("csv"))
                    .thenReturn(ConnectorResult.fail("Write error", "IOException", "csv"));

            mockMvc.perform(post("/api/connectors/csv/export"))
                    .andExpect(status().isBadGateway());
        }
    }

    // ── POST /api/connectors/{name}/sync ─────────────────────────────────────

    @Nested
    @DisplayName("POST /api/connectors/{name}/sync")
    class RunSync {

        @Test
        @DisplayName("returns 200 with full SyncSummary breakdown on success")
        void returns200WithSummary() throws Exception {
            SyncSummary summary = new SyncSummary(2, 1, 0, 3, 0);
            when(connectorRegistry.runSync("airtable"))
                    .thenReturn(ConnectorResult.ok(summary, "Sync complete", "airtable"));

            mockMvc.perform(post("/api/connectors/airtable/sync"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.created", is(2)))
                    .andExpect(jsonPath("$.summary.updated", is(1)))
                    .andExpect(jsonPath("$.summary.deleted", is(0)))
                    .andExpect(jsonPath("$.summary.skipped", is(3)))
                    .andExpect(jsonPath("$.summary.errors",  is(0)))
                    .andExpect(jsonPath("$.summary.total",   is(6)));
        }

        @Test
        @DisplayName("returns 404 when connector name is unrecognised")
        void returns404WhenConnectorNotFound() throws Exception {
            when(connectorRegistry.runSync("ghost"))
                    .thenReturn(ConnectorResult.fail("Connector not found: ghost", "", "ghost"));

            mockMvc.perform(post("/api/connectors/ghost/sync"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 502 on connector-side sync failure")
        void returns502OnSyncFailure() throws Exception {
            when(connectorRegistry.runSync("airtable"))
                    .thenReturn(ConnectorResult.fail("API rate limit exceeded", "429", "airtable"));

            mockMvc.perform(post("/api/connectors/airtable/sync"))
                    .andExpect(status().isBadGateway());
        }
    }
}
