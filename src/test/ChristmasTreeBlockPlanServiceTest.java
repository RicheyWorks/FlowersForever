package com.flowerfarm.service;

import com.flowerfarm.model.ChristmasTreeBlockPlan;
import com.flowerfarm.model.ChristmasTreeBlockStage;
import com.flowerfarm.repository.ChristmasTreeBlockPlanRepository;
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
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("Christmas tree block plan service")
class ChristmasTreeBlockPlanServiceTest {

    @Mock ChristmasTreeBlockPlanRepository repository;

    private ChristmasTreeBlockPlanService service;

    @BeforeEach
    void setUp() {
        service = new ChristmasTreeBlockPlanService(repository);
    }

    @Test
    void createsAndReadsDetachedPlans() {
        ChristmasTreeBlockPlan incoming = plan("North slope");
        when(repository.save(any(ChristmasTreeBlockPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ChristmasTreeBlockPlan created = service.create(incoming);
        assertThat(created).isNotSameAs(incoming);
        created.setBlockName("East block");
        assertThat(incoming.getBlockName()).isEqualTo("North slope");

        when(repository.findAll()).thenReturn(List.of(incoming));
        when(repository.findById(7L)).thenReturn(Optional.of(incoming));
        ChristmasTreeBlockPlan listed = service.list().get(0);
        ChristmasTreeBlockPlan found = service.findById(7L).orElseThrow();
        listed.setBlockName("South slope");
        found.setTreeSpecies("Douglas fir");
        assertThat(incoming.getBlockName()).isEqualTo("North slope");
        assertThat(incoming.getTreeSpecies()).isEqualTo("Noble fir");
    }

    @Test
    void updatesFieldsAfterLockingAndMovesOnlyStage() {
        ChristmasTreeBlockPlan stored = plan("North slope");
        ChristmasTreeBlockPlan incoming = plan("East block");
        incoming.setTreeSpecies("Douglas fir");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        ChristmasTreeBlockPlan updated = service.update(7L, incoming);
        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getBlockName()).isEqualTo("East block");
        assertThat(stored.getTreeSpecies()).isEqualTo("Douglas fir");

        ChristmasTreeBlockPlan moved = service.updateStage(7L,
                ChristmasTreeBlockStage.HARVEST_WINDOW);
        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(ChristmasTreeBlockStage.HARVEST_WINDOW);
        assertThat(stored.getBlockName()).isEqualTo("East block");
        verify(repository, times(2)).save(stored);
    }

    @Test
    void rejectsInvalidPlansAndMissingTargetsBeforeSaving() {
        ChristmasTreeBlockPlan invalid = plan("North slope");
        invalid.setPlannedCutStart(null);
        invalid.setPlannedCutEnd(LocalDate.of(2033, 12, 1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .hasMessageContaining("requires a planned cut start");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, plan("East block")))
                .hasMessageContaining("was not found");
    }

    private static ChristmasTreeBlockPlan plan(String blockName) {
        return new ChristmasTreeBlockPlan(blockName, "Noble fir", 2025,
                new BigDecimal("2.50"), 3000, "6–7 ft",
                LocalDate.of(2032, 8, 1), 2033,
                LocalDate.of(2033, 11, 20), LocalDate.of(2033, 12, 20),
                "Choose and cut", ChristmasTreeBlockStage.GROWING, "");
    }
}
