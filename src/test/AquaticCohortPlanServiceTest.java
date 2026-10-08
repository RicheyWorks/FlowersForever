package com.flowerfarm.service;

import com.flowerfarm.model.AquaticCohortPlan;
import com.flowerfarm.model.AquaticCohortStage;
import com.flowerfarm.repository.AquaticCohortPlanRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Aquaculture cohort plan service")
class AquaticCohortPlanServiceTest {

    @Mock AquaticCohortPlanRepository repository;

    private AquaticCohortPlanService service;

    @BeforeEach
    void setUp() {
        service = new AquaticCohortPlanService(repository);
    }

    @Test
    void createsDetachedPlan() {
        AquaticCohortPlan incoming = plan("Bay 3");
        when(repository.save(any(AquaticCohortPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AquaticCohortPlan created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setCohortName("Bay 4");
        assertThat(incoming.getCohortName()).isEqualTo("Bay 3");
        verify(repository).save(any(AquaticCohortPlan.class));
    }

    @Test
    void listsAndFindsDetachedSnapshots() {
        AquaticCohortPlan stored = plan("Bay 3");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        AquaticCohortPlan listed = service.list().get(0);
        AquaticCohortPlan found = service.findById(7L).orElseThrow();
        listed.setCohortName("Bay 4");
        found.setSpecies("Kelp");

        assertThat(stored.getCohortName()).isEqualTo("Bay 3");
        assertThat(stored.getSpecies()).isEqualTo("Pacific oyster");
    }

    @Test
    void updatesFieldsAfterLocking() {
        AquaticCohortPlan stored = plan("Bay 3");
        AquaticCohortPlan incoming = plan("Bay 4");
        incoming.setSpecies("Kelp");
        incoming.setStage(AquaticCohortStage.STOCKING_WINDOW);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        AquaticCohortPlan updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getCohortName()).isEqualTo("Bay 4");
        assertThat(stored.getSpecies()).isEqualTo("Kelp");
        assertThat(stored.getStage()).isEqualTo(AquaticCohortStage.STOCKING_WINDOW);
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedPlan() {
        AquaticCohortPlan stored = plan("Bay 3");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        AquaticCohortPlan moved = service.updateStage(7L,
                AquaticCohortStage.HARVEST_WINDOW);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(AquaticCohortStage.HARVEST_WINDOW);
        assertThat(stored.getCohortName()).isEqualTo("Bay 3");
        assertThat(stored.getSpecies()).isEqualTo("Pacific oyster");
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        AquaticCohortPlan invalid = plan("Bay 3");
        invalid.setPlannedHarvestEnd(invalid.getPlannedHarvestStart().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        AquaticCohortPlan incoming = plan("Bay 4");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not found");
    }

    private static AquaticCohortPlan plan(String cohortName) {
        return new AquaticCohortPlan(cohortName, "Pacific oyster", "South lease",
                "Bottom culture", LocalDate.of(2026, 10, 20),
                LocalDate.of(2027, 7, 20), LocalDate.of(2027, 8, 3),
                "Wholesale", AquaticCohortStage.GROWING_OUT, "");
    }
}
