package com.flowerfarm.integration;

import com.flowerfarm.controller.ConnectorController;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.SyncHistoryService.AuditReport;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ConnectorController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GET /api/connectors/history/report")
class AuditHistoryControllerIT {

    @Autowired MockMvc mockMvc;

    @MockBean ConnectorRegistry connectorRegistry;
    @MockBean SyncHistoryService syncHistoryService;

    private AuditReport sample() {
        return new AuditReport(
                LocalDate.of(2026, 7, 12),
                "Filter · FAIL only",
                2, 1, 1,
                List.of(),
                "OPS AUDIT HISTORY"
        );
    }

    @Test
    @DisplayName("JSON report")
    void json() throws Exception {
        when(syncHistoryService.buildAuditReport(any(), any(), any(), any(), anyInt()))
                .thenReturn(sample());
        mockMvc.perform(get("/api/connectors/history/report").param("success", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fail").value(1))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    @DisplayName("plain text")
    void text() throws Exception {
        when(syncHistoryService.buildAuditReport(any(), any(), any(), any(), anyInt()))
                .thenReturn(sample());
        mockMvc.perform(get("/api/connectors/history/report.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OPS AUDIT HISTORY")));
    }

    @Test
    @DisplayName("PDF")
    void pdf() throws Exception {
        when(syncHistoryService.buildAuditReport(any(), any(), any(), any(), anyInt()))
                .thenReturn(sample());
        when(syncHistoryService.generateAuditPdf(any())).thenReturn("%PDF-1.4".getBytes());
        mockMvc.perform(get("/api/connectors/history/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("audit-history-")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/pdf")));
    }
}
