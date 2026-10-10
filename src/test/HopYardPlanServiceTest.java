package com.flowerfarm.service;

import com.flowerfarm.model.HopYardPlan;
import com.flowerfarm.model.HopYardStage;
import com.flowerfarm.repository.HopYardPlanRepository;
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
@DisplayName("Hop yard plan service")
class HopYardPlanServiceTest {

    @Mock HopYardPlanRepository repository;

    private HopYardPlanService service;

    @BeforeEach
    void setUp() {
        service = new HopYardPlanService(repository);
    }

    @Test
    void createsDetachedPlan() {
        HopYardPlan incoming = plan("North yard");
        when(repository.save(any(HopYardPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HopYardPlan created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setYardName("South yard");
        assertThat(incoming.getYardName()).isEqualTo("North yard");
        verify(repository).save(any(HopYardPlan.class));
    }

    @Test
    void listsAndFindsDetachedSnapshots() {
        HopYardPlan stored = plan("West yard");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        HopYardPlan listed = service.list().get(0);
        HopYardPlan found = service.findById(7L).orElseThrow();
        listed.setYardName("East yard");
        found.setHopVariety("Citra");

        assertThat(stored.getYardName()).isEqualTo("West yard");
        assertThat(stored.getHopVariety()).isEqualTo("Cascade");
    }

    @Test
    void updatesFieldsAfterLocking() {
        HopYardPlan stored = plan("North yard");
        HopYardPlan incoming = plan("South yard");
        incoming.setHopVariety("Citra");
        incoming.setStage(HopYardStage.TRAINING);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        HopYardPlan updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getYardName()).isEqualTo("South yard");
        assertThat(stored.getHopVariety()).isEqualTo("Citra");
        assertThat(stored.getStage()).isEqualTo(HopYardStage.TRAINING);
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedPlan() {
        HopYardPlan stored = plan("North yard");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        HopYardPlan moved = service.updateStage(7L, HopYardStage.CONE_DEVELOPMENT);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(HopYardStage.CONE_DEVELOPMENT);
        assertThat(stored.getYardName()).isEqualTo("North yard");
        assertThat(stored.getHopVariety()).isEqualTo("Cascade");
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        HopYardPlan invalid = plan("North yard");
        invalid.setPlannedHarvestEnd(invalid.getPlannedHarvestStart().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        HopYardPlan incoming = plan("New yard");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> service.updateStage(7L, HopYardStage.PLANNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidIdAndNullPlan() {
        assertThatThrownBy(() -> service.findById(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.updateStage(0L, HopYardStage.PLANNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        verifyNoInteractions(repository);
    }

    private static HopYardPlan plan(String yardName) {
        return new HopYardPlan(yardName, "Cascade", new BigDecimal("12.50"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 30), HopYardStage.STRINGING, "");
    }
}
