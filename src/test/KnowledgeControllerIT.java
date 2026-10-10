package com.flowerfarm.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowerfarm.controller.KnowledgeController;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.service.KnowledgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = KnowledgeController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Farm Knowledge Log API")
class KnowledgeControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
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

    @Test
    @DisplayName("create and update accept record links while managed identity and timestamps stay server-owned")
    void refusesManagedFieldsInCreateAndUpdate() throws Exception {
        KnowledgeEntry saved = linkedEntry();
        ObjectNode request = mapper.valueToTree(saved);
        request.put("id", 999).put("createdAt", "1999-01-01T00:00:00Z")
                .put("updatedAt", "1999-01-02T00:00:00Z");
        when(knowledgeService.create(any())).thenReturn(saved);
        when(knowledgeService.update(eq(7L), any())).thenReturn(saved);

        mockMvc.perform(post("/api/knowledge").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.createdAt").value("2026-08-20T12:00:00Z"));
        mockMvc.perform(put("/api/knowledge/7").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        var created = org.mockito.ArgumentCaptor.forClass(KnowledgeEntry.class);
        var updated = org.mockito.ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(knowledgeService).create(created.capture());
        verify(knowledgeService).update(eq(7L), updated.capture());
        for (KnowledgeEntry incoming : List.of(created.getValue(), updated.getValue())) {
            assertThat(incoming.getId()).isNull();
            assertThat(incoming.getCreatedAt()).isNull();
            assertThat(incoming.getUpdatedAt()).isNull();
            assertThat(incoming.getHarvestId()).isEqualTo(31L);
            assertThat(incoming.getInventoryItemId()).isEqualTo(32L);
            assertThat(incoming.getOrderId()).isEqualTo(33L);
            assertThat(incoming.getCustomerId()).isEqualTo(34L);
            assertThat(incoming.getFieldScienceExportId()).isEqualTo(35L);
            assertThat(incoming.getObservation()).isEqualTo(saved.getObservation());
            assertThat(incoming.getTags()).isEqualTo(saved.getTags());
        }
    }

    @Test
    @DisplayName("explicit responses preserve the existing complete Knowledge Log JSON shape")
    void preservesCompleteResponseShape() throws Exception {
        KnowledgeEntry saved = linkedEntry();
        when(knowledgeService.findById(7L)).thenReturn(Optional.of(saved));
        when(knowledgeService.search(any())).thenReturn(List.of(saved));
        String single = mockMvc.perform(get("/api/knowledge/7"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String list = mockMvc.perform(get("/api/knowledge"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(single)).isEqualTo(mapper.valueToTree(saved));
        assertThat(mapper.readTree(list)).isEqualTo(mapper.valueToTree(List.of(saved)));
    }

    private static KnowledgeEntry linkedEntry() {
        KnowledgeEntry entry = entry(7L);
        entry.setHarvestId(31L);
        entry.setInventoryItemId(32L);
        entry.setOrderId(33L);
        entry.setCustomerId(34L);
        entry.setFieldScienceExportId(35L);
        entry.initializeTimestamps(Instant.parse("2026-08-20T12:00:00Z"));
        entry.touch(Instant.parse("2026-08-21T12:00:00Z"));
        return entry;
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
