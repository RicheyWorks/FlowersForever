package com.flowerfarm.integration;

import com.flowerfarm.controller.KnowledgeController;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.service.KnowledgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KnowledgeController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Farm Knowledge Log API")
class KnowledgeControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean KnowledgeService knowledgeService;

    @Test
    @DisplayName("plain-language search returns farm lessons and forwards filters")
    void searches() throws Exception {
        when(knowledgeService.search(any())).thenReturn(List.of(entry(7L)));

        mockMvc.perform(get("/api/knowledge")
                        .param("q", "What worked for mildew on this rose last August?")
                        .param("tag", "powdery mildew")
                        .param("followUpDue", "true")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].summary").value("Mildew appeared after warm nights"))
                .andExpect(jsonPath("$[0].tags[0]").value("powdery-mildew"));

        var request = org.mockito.ArgumentCaptor.forClass(
                KnowledgeService.SearchRequest.class);
        verify(knowledgeService).search(request.capture());
        assertThat(request.getValue().query()).contains("last August");
        assertThat(request.getValue().tag()).isEqualTo("powdery mildew");
        assertThat(request.getValue().followUpDue()).isTrue();
        assertThat(request.getValue().limit()).isEqualTo(25);
    }

    @Test
    @DisplayName("entry and tag lookups have simple stable shapes")
    void reads() throws Exception {
        when(knowledgeService.findById(7L)).thenReturn(Optional.of(entry(7L)));
        when(knowledgeService.distinctTags())
                .thenReturn(List.of("irrigation", "powdery-mildew"));

        mockMvc.perform(get("/api/knowledge/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value("PROMISING"));
        mockMvc.perform(get("/api/knowledge/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1]").value("powdery-mildew"));
    }

    @Test
    @DisplayName("create accepts farm language and returns the saved entry")
    void creates() throws Exception {
        when(knowledgeService.create(any())).thenReturn(entry(7L));

        mockMvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "observedOn": "2026-08-20",
                                  "summary": "Mildew appeared after warm nights",
                                  "kind": "PROBLEM",
                                  "cropName": "Nootka Rose",
                                  "bedOrField": "Bed A",
                                  "observation": "White patches appeared on lower leaves.",
                                  "actionTaken": "Removed leaves and watered earlier.",
                                  "result": "No new patches after seven days.",
                                  "confidence": "PROMISING",
                                  "tags": ["powdery mildew"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @DisplayName("field science export id round trips through the knowledge API")
    void acceptsOptionalFieldScienceExportLink() throws Exception {
        when(knowledgeService.create(any())).thenAnswer(invocation -> {
            KnowledgeEntry incoming = invocation.getArgument(0);
            incoming.setId(7L);
            return incoming;
        });

        mockMvc.perform(post("/api/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "observedOn": "2026-08-20",
                                  "summary": "Finding from Farm Scout",
                                  "kind": "PRACTICE",
                                  "observation": "Scouted the west block.",
                                  "confidence": "NEEDS_REVIEW",
                                  "fieldScienceExportId": 42
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fieldScienceExportId").value(42));
    }

    @Test
    @DisplayName("delete returns no content after removing the lesson")
    void deletes() throws Exception {
        mockMvc.perform(delete("/api/knowledge/7"))
                .andExpect(status().isNoContent());
        verify(knowledgeService).delete(7L);
    }

    private static KnowledgeEntry entry(Long id) {
        KnowledgeEntry entry = new KnowledgeEntry(
                LocalDate.of(2026, 8, 20),
                "Mildew appeared after warm nights",
                KnowledgeKind.PROBLEM,
                "Nootka Rose",
                "",
                "Bed A",
                "Vegetative",
                "White patches appeared on lower leaves.",
                "Removed affected leaves and watered earlier.",
                "Richmond",
                "No new patches after seven days.",
                LocalDate.of(2026, 8, 28),
                KnowledgeConfidence.PROMISING,
                List.of("powdery-mildew"));
        entry.setId(id);
        return entry;
    }
}
