package com.flowerfarm.integration;

import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.controller.ConnectorController;
import com.flowerfarm.controller.HarvestController;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.SyncHistoryEntry;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.ServerCsvExportService;
import com.flowerfarm.service.SyncHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP routing and real filesystem containment; CSV business serializers are separate service tests. */
class FarmRestCsvExportIT {
    @TempDir Path temporaryDirectory;
    private Path exportRoot;
    private MockMvc mvc;
    private HarvestService harvest;
    private SyncHistoryService history;

    @BeforeEach
    void setup() throws Exception {
        exportRoot = temporaryDirectory.resolve("exports");
        ServerCsvExportService writer = new ServerCsvExportService(exportRoot.toString());
        harvest = mock(HarvestService.class);
        history = mock(SyncHistoryService.class);
        when(harvest.getAll()).thenReturn(List.of());
        when(harvest.filter(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(history.filter(any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        doAnswer(call -> { ((BufferedWriter) call.getArgument(0)).write("harvest,rosé\n"); return null; })
                .when(harvest).writeCsv(any(), any());
        doAnswer(call -> { ((BufferedWriter) call.getArgument(0)).write("history,import\n"); return null; })
                .when(history).writeCsv(any(), any());
        mvc = MockMvcBuilders.standaloneSetup(new HarvestController(harvest, writer),
                new ConnectorController(mock(ConnectorRegistry.class), history, writer)).build();
    }

    @Test
    void defaultNamesWriteOnlyInsideExportRootWithExistingResponseKeys() throws Exception {
        mvc.perform(post("/api/harvest/export"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Exported harvest log to harvest_log.csv"));
        mvc.perform(post("/api/connectors/history/export"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows").value("0"));
        assertEquals("harvest,rosé\n", Files.readString(exportRoot.resolve("harvest_log.csv"), StandardCharsets.UTF_8));
        assertEquals("history,import\n", Files.readString(exportRoot.resolve("sync_history.csv"), StandardCharsets.UTF_8));
        assertFalse(Files.exists(temporaryDirectory.resolve("harvest_log.csv")));
        verify(harvest, never()).exportToCsv(anyString());
        verify(harvest, never()).exportToCsv(anyString(), any());
        verify(history, never()).exportToCsv(anyString(), any());
    }

    @Test
    void requestControlledPathsCannotOverwriteAnOutsideFileOrInvokeCsvSerialization() throws Exception {
        Path outside = temporaryDirectory.resolve("outside.csv");
        Files.writeString(outside, "keep me", StandardCharsets.UTF_8);
        for (String filename : List.of("../outside.csv", "..\\outside.csv", outside.toAbsolutePath().toString(),
                "nested/escape.csv", "nested\\escape.csv", "wrong.txt")) {
            for (String endpoint : List.of("/api/harvest/export", "/api/connectors/history/export")) {
                mvc.perform(post(endpoint).param("filename", filename)).andExpect(status().isBadRequest());
            }
        }
        assertEquals("keep me", Files.readString(outside, StandardCharsets.UTF_8));
        verify(harvest, never()).writeCsv(any(), any());
        verify(history, never()).writeCsv(any(), any());
    }

    @Test
    void filteredExportsPassOriginalRowsToWriterAndKeepCounts() throws Exception {
        HarvestEntry row = new HarvestEntry(LocalDate.of(2026, 7, 10), "Rose", 2, "stems", "A", "");
        List<HarvestEntry> rows = List.of(row);
        when(harvest.filter(eq("Rose"), any(), any(), any(), any())).thenReturn(rows);
        SyncHistoryEntry audit = new SyncHistoryEntry("csv", "IMPORT", true, "Imported", "", 2);
        List<SyncHistoryEntry> audits = List.of(audit);
        when(history.filter(eq("csv"), any(), any(), any(), anyInt())).thenReturn(audits);
        mvc.perform(post("/api/harvest/export").param("crop", "Rose").param("filename", "filtered.csv"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows").value("1"));
        mvc.perform(post("/api/connectors/history/export").param("connector", "csv").param("filename", "audit.csv"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rows").value("1"));
        verify(harvest).writeCsv(any(), same(rows));
        verify(history).writeCsv(any(), same(audits));
    }

    @Test
    void actualWriterFailureReturnsServerErrorAndKeepsExistingDestination() throws Exception {
        Files.createDirectories(exportRoot);
        Path destination = exportRoot.resolve("harvest_log.csv");
        Files.writeString(destination, "previous complete export", StandardCharsets.UTF_8);
        doThrow(new IOException("fixture writer failure")).when(harvest).writeCsv(any(), any());
        mvc.perform(post("/api/harvest/export")).andExpect(status().isInternalServerError());
        assertEquals("previous complete export", Files.readString(destination, StandardCharsets.UTF_8));
    }
}
