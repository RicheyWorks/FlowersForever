package com.flowerfarm.integration;

import com.flowerfarm.controller.ProductionStudioController;
import com.flowerfarm.model.FarmSeasonSettings;
import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.model.FloralRecipeIngredient;
import com.flowerfarm.model.GrowingTemplate;
import com.flowerfarm.model.IntegrationHandoff;
import com.flowerfarm.model.ProductionTask;
import com.flowerfarm.model.ProductionTaskStatus;
import com.flowerfarm.model.ProductionTaskType;
import com.flowerfarm.service.ProductionConflictException;
import com.flowerfarm.service.ProductionStudioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductionStudioController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Production Studio API")
class ProductionStudioControllerIT {

    @Autowired MockMvc mockMvc;
    @MockitoBean ProductionStudioService service;

    @Test
    @DisplayName("saves annual farm frost anchors")
    void savesFrostAnchors() throws Exception {
        when(service.saveSeasonSettings(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/production-studio/seasons/2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSpringFrost":"2026-05-01",
                                 "firstFallFrost":"2026-10-15",
                                 "source":"Farm weather station",
                                 "loadedLaborRatePerHour":28.5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonYear").value(2026))
                .andExpect(jsonPath("$.lastSpringFrost").value("2026-05-01"))
                .andExpect(jsonPath("$.source").value("Farm weather station"))
                .andExpect(jsonPath("$.loadedLaborRatePerHour").value(28.5));
    }

    @Test
    @DisplayName("older season-setting clients preserve an already saved labor rate")
    void preservesLaborRateWhenOmitted() throws Exception {
        when(service.findSeasonSettings(2026)).thenReturn(java.util.Optional.of(
                new FarmSeasonSettings(2026, LocalDate.of(2026, 5, 1),
                        LocalDate.of(2026, 10, 15), "Existing", 31)));
        when(service.saveSeasonSettings(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/production-studio/seasons/2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastSpringFrost":"2026-05-02",
                                 "firstFallFrost":"2026-10-16",
                                 "source":"Updated station"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loadedLaborRatePerHour").value(31));
    }

    @Test
    @DisplayName("publishes planting-linked production costs with an optional AI rate override")
    void returnsProductionCosts() throws Exception {
        when(service.productionCosts(2026, 32d)).thenReturn(
                new ProductionStudioService.ProductionCostReport(
                        2026, 32, "REQUEST_OVERRIDE", List.of(), 100, 80,
                        2, 1, 120, 64, 16, 80, 80, 0.8, 1.0, 50,
                        0, 0, 0, 0, 0, 0, 0, 0, List.of()));

        mockMvc.perform(get("/api/production-studio/seasons/2026/production-costs")
                        .param("laborRatePerHour", "32"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasonYear").value(2026))
                .andExpect(jsonPath("$.laborRateSource").value("REQUEST_OVERRIDE"))
                .andExpect(jsonPath("$.totalProductionCost").value(80))
                .andExpect(jsonPath("$.costPerHarvestedStem").value(1.0));
    }

    @Test
    @DisplayName("accepts complete farm crop templates")
    void createsGrowingTemplate() throws Exception {
        when(service.createTemplate(any())).thenAnswer(invocation -> {
            GrowingTemplate template = invocation.getArgument(0);
            template.setId(3L);
            return template;
        });

        mockMvc.perform(post("/api/production-studio/templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sunflower production","cropName":"Sunflower",
                                 "variety":"ProCut Orange","spacingInches":9,"rowsPerBed":4,
                                 "sowDaysFromFrost":-42,"transplantDaysFromFrost":7,
                                 "daysToHarvest":90,"harvestWindowDays":21,
                                 "successionDays":14,"stemsPerPlant":3,
                                 "germinationPercent":85,"soilPrepLeadDays":14,
                                 "supportDaysAfterPlanting":10,"active":true,"notes":"Farm trial"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.cropName").value("Sunflower"))
                .andExpect(jsonPath("$.sowDaysFromFrost").value(-42));
    }

    @Test
    @DisplayName("publishes frost-aware planting drafts")
    void draftsPlantings() throws Exception {
        ProductionStudioService.PlantingDraft draft =
                new ProductionStudioService.PlantingDraft(2026, 3L, "Sunflower production",
                        "Sunflower", "ProCut Orange", "Bed A", 50, 1,
                        LocalDate.of(2026, 3, 20), LocalDate.of(2026, 5, 8),
                        LocalDate.of(2026, 6, 18), LocalDate.of(2026, 7, 8),
                        266, 226, 678, false, "Inside frost anchors");
        when(service.draftPlantings(3L, 2026, "Bed A", 50, 2))
                .thenReturn(List.of(draft));
        when(service.plantingFingerprint(List.of(draft))).thenReturn("preview-token");

        mockMvc.perform(post("/api/production-studio/plantings/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateId":3,"seasonYear":2026,"bedOrField":"Bed A",
                                 "bedFeet":50,"successions":2}
                """))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"preview-token\""))
                .andExpect(jsonPath("$[0].expectedStems").value(678))
                .andExpect(jsonPath("$[0].harvestStart").value("2026-06-18"));
    }

    @Test
    @DisplayName("returns an actionable conflict instead of duplicating a planting commit")
    void rejectsDuplicatePlantingCommit() throws Exception {
        when(service.commitPlantings(3L, 2026, "Bed A", 50, 1, "preview-token"))
                .thenThrow(new ProductionConflictException(
                        "An identical planting block already exists for Sunflower in Bed A."));

        mockMvc.perform(post("/api/production-studio/plantings/commit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateId":3,"seasonYear":2026,"bedOrField":"Bed A",
                                 "bedFeet":50,"successions":1,
                                 "previewFingerprint":"preview-token"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_CONFLICT"))
                .andExpect(jsonPath("$.error").value(
                        "An identical planting block already exists for Sunflower in Bed A."));
    }

    @Test
    @DisplayName("accepts the planting preview ETag through standard If-Match")
    void commitsReviewedPlantingPreview() throws Exception {
        when(service.commitPlantings(3L, 2026, "Bed A", 50, 1, "preview-token"))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/production-studio/plantings/commit")
                        .header(HttpHeaders.IF_MATCH, "\"preview-token\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateId":3,"seasonYear":2026,"bedOrField":"Bed A",
                                 "bedFeet":50,"successions":1}
                                """))
                .andExpect(status().isCreated());
        verify(service).commitPlantings(3L, 2026, "Bed A", 50, 1, "preview-token");
    }

    @Test
    @DisplayName("completes production work with structured actuals")
    void recordsProductionTaskActuals() throws Exception {
        ProductionTask task = new ProductionTask(31L, 3L,
                LocalDate.of(2026, 9, 2), ProductionTaskType.SOIL_PREP,
                ProductionTaskStatus.TODO, "Zinnia", "Bed C", 1,
                true, "Prepare bed");
        task.setId(19L);
        task.recordActuals(80, 24.75,
                "Compost incorporated; drip line replaced",
                Instant.parse("2026-09-02T18:30:00Z"));
        when(service.recordTaskActuals(eq(19L), any())).thenReturn(task);

        mockMvc.perform(put("/api/production-studio/tasks/19/actuals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualMinutes":80,"inputCost":24.75,
                                 "fieldObservation":"Compost incorporated; drip line replaced"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.actualMinutes").value(80))
                .andExpect(jsonPath("$.inputCost").value(24.75))
                .andExpect(jsonPath("$.fieldObservation")
                        .value("Compost incorporated; drip line replaced"))
                .andExpect(jsonPath("$.completedAt")
                        .value("2026-09-02T18:30:00Z"));

        var captor = org.mockito.ArgumentCaptor.forClass(
                ProductionStudioService.TaskActuals.class);
        verify(service).recordTaskActuals(eq(19L), captor.capture());
        assertThat(captor.getValue().actualMinutes()).isEqualTo(80);
        assertThat(captor.getValue().inputCost()).isEqualTo(24.75);
    }

    @Test
    @DisplayName("accepts multi-ingredient recipes and exposes profit estimates")
    void createsAndEstimatesRecipe() throws Exception {
        when(service.createRecipe(any())).thenAnswer(invocation -> {
            FloralRecipe recipe = invocation.getArgument(0);
            recipe.setId(8L);
            return recipe;
        });
        when(service.estimateRecipe(eq(8L), eq(20), eq(LocalDate.of(2026, 9, 12))))
                .thenReturn(new ProductionStudioService.RecipeEstimate(
                        8L, "Autumn event", 20, LocalDate.of(2026, 9, 12), List.of(),
                        220, 150, 55.5, 425.5, 900, 474.5, 52.72, 111.52));

        mockMvc.perform(post("/api/production-studio/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Autumn event","designType":"Centerpiece",
                                 "salePrice":45,"laborMinutes":18,"laborRatePerHour":25,
                                 "overheadPercent":15,"wastePercent":10,"notes":"",
                                 "ingredients":[
                                   {"itemName":"Dahlia","stemsPerDesign":5,
                                    "packSize":10,"packCost":14,"notes":""},
                                   {"itemName":"Zinnia","stemsPerDesign":4,
                                    "packSize":10,"packCost":9,"notes":""}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.ingredients.length()").value(2));

        mockMvc.perform(get("/api/production-studio/recipes/8/estimate")
                        .param("count", "20").param("eventDate", "2026-09-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profit").value(474.5))
                .andExpect(jsonPath("$.marginPercent").value(52.72));
    }

    @Test
    @DisplayName("records provider-neutral handoffs")
    void createsHandoff() throws Exception {
        when(service.createHandoff(any())).thenAnswer(invocation -> {
            ProductionStudioService.HandoffRequest request = invocation.getArgument(0);
            IntegrationHandoff handoff = new IntegrationHandoff(request.handoffType(),
                    request.provider(), request.status(), request.externalReference(),
                    request.relatedOrderId(), request.relatedRecipeId(), request.amount(),
                    request.currency(), "No card, bank, token, or signature data stored.");
            handoff.setId(12L);
            return handoff;
        });

        mockMvc.perform(post("/api/production-studio/handoffs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"handoffType":"PAYMENT","provider":"Square","status":"READY",
                                 "externalReference":"invoice-44","relatedOrderId":44,
                                 "relatedRecipeId":8,"amount":300,"currency":"USD",
                                 "summary":"Deposit"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.handoffType").value("PAYMENT"))
                .andExpect(jsonPath("$.externalReference").value("invoice-44"));

        var captor = org.mockito.ArgumentCaptor.forClass(
                ProductionStudioService.HandoffRequest.class);
        verify(service).createHandoff(captor.capture());
        assertThat(captor.getValue().relatedOrderId()).isEqualTo(44L);
    }
}
