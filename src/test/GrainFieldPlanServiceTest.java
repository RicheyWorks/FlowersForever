package com.flowerfarm.service;

import com.flowerfarm.model.GrainFieldPlan;
import com.flowerfarm.model.GrainFieldStage;
import com.flowerfarm.repository.GrainFieldPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Grain field plan service")
class GrainFieldPlanServiceTest {

    @Mock GrainFieldPlanRepository repository;

    private GrainFieldPlanService service;

    @BeforeEach
    void setUp() {
        service = new GrainFieldPlanService(repository);
    }

    @Test
    void createsDetachedPlan() {
        GrainFieldPlan incoming = plan("North field");
        when(repository.save(any(GrainFieldPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GrainFieldPlan created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setFieldName("South field");
        assertThat(incoming.getFieldName()).isEqualTo("North field");
        verify(repository).save(any(GrainFieldPlan.class));
    }

    @Test
    void listsAndFindsDetachedSnapshots() {
        GrainFieldPlan stored = plan("West field");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        GrainFieldPlan listed = service.list().get(0);
        GrainFieldPlan found = service.findById(7L).orElseThrow();
        listed.setFieldName("East field");
        found.setGrainCrop("Barley");

        assertThat(stored.getFieldName()).isEqualTo("West field");
        assertThat(stored.getGrainCrop()).isEqualTo("Wheat");
    }

    @Test
    void updatesFieldsAfterLocking() {
        GrainFieldPlan stored = plan("North field");
        GrainFieldPlan incoming = plan("South field");
        incoming.setGrainCrop("Barley");
        incoming.setStage(GrainFieldStage.GROWING);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        GrainFieldPlan updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getFieldName()).isEqualTo("South field");
        assertThat(stored.getGrainCrop()).isEqualTo("Barley");
        assertThat(stored.getStage()).isEqualTo(GrainFieldStage.GROWING);
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedPlan() {
        GrainFieldPlan stored = plan("North field");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        GrainFieldPlan moved = service.updateStage(7L, GrainFieldStage.HARVEST_WINDOW);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(GrainFieldStage.HARVEST_WINDOW);
        assertThat(stored.getFieldName()).isEqualTo("North field");
        assertThat(stored.getGrainCrop()).isEqualTo("Wheat");
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        GrainFieldPlan invalid = plan("North field");
        invalid.setPlannedHarvestEnd(invalid.getPlannedHarvestStart().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        GrainFieldPlan incoming = plan("New field");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not found");
    }

    private static GrainFieldPlan plan(String field) {
        return new GrainFieldPlan(field, "Wheat", "Soft white", new BigDecimal("12.50"),
                "Canola", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 30), GrainFieldStage.SEEDED, "");
    }
}
