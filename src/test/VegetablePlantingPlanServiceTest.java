package com.flowerfarm.service;

import com.flowerfarm.model.VegetablePlantingPlan;
import com.flowerfarm.model.VegetablePlantingStage;
import com.flowerfarm.repository.VegetablePlantingPlanRepository;
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
@DisplayName("Vegetable planting plan service")
class VegetablePlantingPlanServiceTest {

    @Mock VegetablePlantingPlanRepository repository;

    private VegetablePlantingPlanService service;

    @BeforeEach
    void setUp() {
        service = new VegetablePlantingPlanService(repository);
    }

    @Test
    void createsDetachedPlan() {
        VegetablePlantingPlan incoming = plan("Bed 3");
        when(repository.save(any(VegetablePlantingPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VegetablePlantingPlan created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setFieldOrBed("Bed 4");
        assertThat(incoming.getFieldOrBed()).isEqualTo("Bed 3");
        verify(repository).save(any(VegetablePlantingPlan.class));
    }

    @Test
    void listsAndFindsDetachedSnapshots() {
        VegetablePlantingPlan stored = plan("Bed 3");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        VegetablePlantingPlan listed = service.list().get(0);
        VegetablePlantingPlan found = service.findById(7L).orElseThrow();
        listed.setFieldOrBed("Bed 4");
        found.setVegetableCrop("Carrot");

        assertThat(stored.getFieldOrBed()).isEqualTo("Bed 3");
        assertThat(stored.getVegetableCrop()).isEqualTo("Lettuce");
    }

    @Test
    void updatesFieldsAfterLocking() {
        VegetablePlantingPlan stored = plan("Bed 3");
        VegetablePlantingPlan incoming = plan("Bed 4");
        incoming.setVegetableCrop("Carrot");
        incoming.setStage(VegetablePlantingStage.TRANSPLANTED);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        VegetablePlantingPlan updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getFieldOrBed()).isEqualTo("Bed 4");
        assertThat(stored.getVegetableCrop()).isEqualTo("Carrot");
        assertThat(stored.getStage()).isEqualTo(VegetablePlantingStage.TRANSPLANTED);
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedPlan() {
        VegetablePlantingPlan stored = plan("Bed 3");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        VegetablePlantingPlan moved = service.updateStage(7L,
                VegetablePlantingStage.HARVEST_WINDOW);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(VegetablePlantingStage.HARVEST_WINDOW);
        assertThat(stored.getFieldOrBed()).isEqualTo("Bed 3");
        assertThat(stored.getVegetableCrop()).isEqualTo("Lettuce");
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        VegetablePlantingPlan invalid = plan("Bed 3");
        invalid.setPlannedHarvestEnd(invalid.getPlannedHarvestStart().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        VegetablePlantingPlan incoming = plan("Bed 4");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not found");
    }

    private static VegetablePlantingPlan plan(String fieldOrBed) {
        return new VegetablePlantingPlan(fieldOrBed, "Lettuce", "Buttercrunch", "Beans",
                new BigDecimal("0.50"), 1, LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 27), "CSA", VegetablePlantingStage.GROWING, "");
    }
}
