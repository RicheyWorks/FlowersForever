package com.flowerfarm.service;

import com.flowerfarm.model.VineyardBlockPlan;
import com.flowerfarm.model.VineyardBlockStage;
import com.flowerfarm.model.VineyardDestination;
import com.flowerfarm.repository.VineyardBlockPlanRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Vineyard block plan service")
class VineyardBlockPlanServiceTest {

    @Mock VineyardBlockPlanRepository repository;

    private VineyardBlockPlanService service;

    @BeforeEach
    void setUp() {
        service = new VineyardBlockPlanService(repository);
    }

    @Test
    void createsDetachedPlan() {
        VineyardBlockPlan incoming = plan("North slope");
        when(repository.save(any(VineyardBlockPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VineyardBlockPlan created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setBlockName("South slope");
        assertThat(incoming.getBlockName()).isEqualTo("North slope");
        verify(repository).save(any(VineyardBlockPlan.class));
    }

    @Test
    void listsAndFindsDetachedSnapshots() {
        VineyardBlockPlan stored = plan("West bench");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        VineyardBlockPlan listed = service.list().get(0);
        VineyardBlockPlan found = service.findById(7L).orElseThrow();
        listed.setBlockName("East bench");
        found.setGrapeVariety("Chardonnay");

        assertThat(stored.getBlockName()).isEqualTo("West bench");
        assertThat(stored.getGrapeVariety()).isEqualTo("Pinot noir");
    }

    @Test
    void updatesFieldsAfterLocking() {
        VineyardBlockPlan stored = plan("North slope");
        VineyardBlockPlan incoming = plan("South slope");
        incoming.setLocation("Terrace C");
        incoming.setDestination(VineyardDestination.ESTATE_WINERY);
        incoming.setStage(VineyardBlockStage.HARVEST_WINDOW);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        VineyardBlockPlan updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getBlockName()).isEqualTo("South slope");
        assertThat(stored.getLocation()).isEqualTo("Terrace C");
        assertThat(stored.getDestination()).isEqualTo(VineyardDestination.ESTATE_WINERY);
        assertThat(stored.getStage()).isEqualTo(VineyardBlockStage.HARVEST_WINDOW);
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedPlan() {
        VineyardBlockPlan stored = plan("North slope");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        VineyardBlockPlan moved = service.updateStage(7L, VineyardBlockStage.POST_HARVEST);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(VineyardBlockStage.POST_HARVEST);
        assertThat(stored.getBlockName()).isEqualTo("North slope");
        assertThat(stored.getGrapeVariety()).isEqualTo("Pinot noir");
        assertThat(stored.getDestination()).isEqualTo(VineyardDestination.WINERY_BUYER);
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        VineyardBlockPlan invalid = plan("North slope");
        invalid.setPlannedHarvestEnd(invalid.getPlannedHarvestStart().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        VineyardBlockPlan incoming = plan("New block");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> service.updateStage(7L, VineyardBlockStage.PLANNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidIdAndNullPlan() {
        assertThatThrownBy(() -> service.findById(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.updateStage(0L, VineyardBlockStage.GROWING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        verifyNoInteractions(repository);
    }

    private static VineyardBlockPlan plan(String blockName) {
        return new VineyardBlockPlan(blockName, "West bench", "Pinot noir",
                new BigDecimal("2.50"), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 10, 15), VineyardDestination.WINERY_BUYER,
                VineyardBlockStage.GROWING, "");
    }
}
