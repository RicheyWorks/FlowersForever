package com.flowerfarm.service;

import com.flowerfarm.model.MushroomBatchPlan;
import com.flowerfarm.model.MushroomBatchStage;
import com.flowerfarm.repository.MushroomBatchPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Mushroom batch plan service")
class MushroomBatchPlanServiceTest {

    @Mock MushroomBatchPlanRepository repository;

    private MushroomBatchPlanService service;

    @BeforeEach
    void setUp() {
        service = new MushroomBatchPlanService(repository);
    }

    @Test
    void createsAndReadsDetachedPlans() {
        MushroomBatchPlan incoming = plan("Log set A");
        when(repository.save(any(MushroomBatchPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        MushroomBatchPlan created = service.create(incoming);
        assertThat(created).isNotSameAs(incoming);
        created.setBatchName("Log set B");
        assertThat(incoming.getBatchName()).isEqualTo("Log set A");

        when(repository.findAll()).thenReturn(List.of(incoming));
        when(repository.findById(7L)).thenReturn(Optional.of(incoming));
        MushroomBatchPlan listed = service.list().get(0);
        MushroomBatchPlan found = service.findById(7L).orElseThrow();
        listed.setBatchName("Log set C");
        found.setMushroomSpecies("Oyster");
        assertThat(incoming.getBatchName()).isEqualTo("Log set A");
        assertThat(incoming.getMushroomSpecies()).isEqualTo("Shiitake");
    }

    @Test
    void updatesFieldsAfterLockingAndMovesOnlyStage() {
        MushroomBatchPlan stored = plan("Log set A");
        MushroomBatchPlan incoming = plan("Log set B");
        incoming.setSubstrate("Oak logs");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        MushroomBatchPlan updated = service.update(7L, incoming);
        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getBatchName()).isEqualTo("Log set B");
        assertThat(stored.getSubstrate()).isEqualTo("Oak logs");

        MushroomBatchPlan moved = service.updateStage(7L,
                MushroomBatchStage.FRUITING_WINDOW);
        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(MushroomBatchStage.FRUITING_WINDOW);
        assertThat(stored.getBatchName()).isEqualTo("Log set B");
        assertThat(stored.getSubstrate()).isEqualTo("Oak logs");
        verify(repository, times(2)).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        MushroomBatchPlan invalid = plan("Log set A");
        invalid.setPlannedFruitingStart(null);
        invalid.setPlannedFruitingEnd(LocalDate.of(2027, 11, 1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .hasMessageContaining("requires a planned fruiting start");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, plan("Log set B")))
                .hasMessageContaining("was not found");
    }

    private static MushroomBatchPlan plan(String batchName) {
        LocalDate inoculation = LocalDate.of(2027, 2, 10);
        return new MushroomBatchPlan(batchName, "Shiitake", "Logs", "Hardwood",
                "North grove", inoculation, inoculation.plusMonths(6),
                inoculation.plusMonths(7), "Farm stand", MushroomBatchStage.INCUBATING,
                "Inspect moisture");
    }
}
