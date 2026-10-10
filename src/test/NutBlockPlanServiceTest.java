package com.flowerfarm.service;

import com.flowerfarm.model.NutBlockPlan;
import com.flowerfarm.model.NutBlockStage;
import com.flowerfarm.repository.NutBlockPlanRepository;
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
@DisplayName("Tree nut block plan service")
class NutBlockPlanServiceTest {

    @Mock NutBlockPlanRepository repository;

    private NutBlockPlanService service;

    @BeforeEach
    void setUp() {
        service = new NutBlockPlanService(repository);
    }

    @Test
    void createsDetachedPlan() {
        NutBlockPlan incoming = plan("North block");
        when(repository.save(any(NutBlockPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NutBlockPlan created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setBlockName("East block");
        assertThat(incoming.getBlockName()).isEqualTo("North block");
        verify(repository).save(any(NutBlockPlan.class));
    }

    @Test
    void listsAndFindsDetachedSnapshots() {
        NutBlockPlan stored = plan("North block");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        NutBlockPlan listed = service.list().get(0);
        NutBlockPlan found = service.findById(7L).orElseThrow();
        listed.setBlockName("East block");
        found.setNutCrop("Walnut");

        assertThat(stored.getBlockName()).isEqualTo("North block");
        assertThat(stored.getNutCrop()).isEqualTo("Hazelnut");
    }

    @Test
    void updatesFieldsAfterLocking() {
        NutBlockPlan stored = plan("North block");
        NutBlockPlan incoming = plan("East block");
        incoming.setNutCrop("Walnut");
        incoming.setStage(NutBlockStage.FLOOR_PREP);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        NutBlockPlan updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getBlockName()).isEqualTo("East block");
        assertThat(stored.getNutCrop()).isEqualTo("Walnut");
        assertThat(stored.getStage()).isEqualTo(NutBlockStage.FLOOR_PREP);
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedPlan() {
        NutBlockPlan stored = plan("North block");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        NutBlockPlan moved = service.updateStage(7L, NutBlockStage.HARVEST_WINDOW);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(NutBlockStage.HARVEST_WINDOW);
        assertThat(stored.getBlockName()).isEqualTo("North block");
        assertThat(stored.getNutCrop()).isEqualTo("Hazelnut");
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        NutBlockPlan invalid = plan("North block");
        invalid.setPlannedHarvestEnd(invalid.getPlannedHarvestStart().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        NutBlockPlan incoming = plan("New block");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not found");
    }

    private static NutBlockPlan plan(String block) {
        return new NutBlockPlan(block, "Hazelnut", "Jefferson", "Eta",
                new BigDecimal("12.50"), 500, LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 30), "Processor", NutBlockStage.NUT_SET, "");
    }
}
