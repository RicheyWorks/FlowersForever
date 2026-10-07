package com.flowerfarm.service;

import com.flowerfarm.model.SeedFieldPlan;
import com.flowerfarm.model.SeedFieldStage;
import com.flowerfarm.repository.SeedFieldPlanRepository;
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
@DisplayName("Seed field plan service")
class SeedFieldPlanServiceTest {

    @Mock SeedFieldPlanRepository repository;

    private SeedFieldPlanService service;

    @BeforeEach
    void setUp() {
        service = new SeedFieldPlanService(repository);
    }

    @Test
    void createsDetachedPlan() {
        SeedFieldPlan incoming = plan("West stand");
        when(repository.save(any(SeedFieldPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SeedFieldPlan created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setFieldName("East stand");
        assertThat(incoming.getFieldName()).isEqualTo("West stand");
        verify(repository).save(any(SeedFieldPlan.class));
    }

    @Test
    void listsAndFindsDetachedSnapshots() {
        SeedFieldPlan stored = plan("West stand");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        SeedFieldPlan listed = service.list().get(0);
        SeedFieldPlan found = service.findById(7L).orElseThrow();
        listed.setFieldName("East stand");
        found.setSeedCrop("Clover");

        assertThat(stored.getFieldName()).isEqualTo("West stand");
        assertThat(stored.getSeedCrop()).isEqualTo("Ryegrass");
    }

    @Test
    void updatesFieldsAfterLocking() {
        SeedFieldPlan stored = plan("West stand");
        SeedFieldPlan incoming = plan("East stand");
        incoming.setSeedCrop("Clover");
        incoming.setStage(SeedFieldStage.FLOWERING);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        SeedFieldPlan updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getFieldName()).isEqualTo("East stand");
        assertThat(stored.getSeedCrop()).isEqualTo("Clover");
        assertThat(stored.getStage()).isEqualTo(SeedFieldStage.FLOWERING);
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedPlan() {
        SeedFieldPlan stored = plan("West stand");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        SeedFieldPlan moved = service.updateStage(7L, SeedFieldStage.HARVEST_WINDOW);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(SeedFieldStage.HARVEST_WINDOW);
        assertThat(stored.getFieldName()).isEqualTo("West stand");
        assertThat(stored.getSeedCrop()).isEqualTo("Ryegrass");
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        SeedFieldPlan invalid = plan("West stand");
        invalid.setPlannedHarvestEnd(invalid.getPlannedHarvestStart().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        SeedFieldPlan incoming = plan("New field");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not found");
    }

    private static SeedFieldPlan plan(String field) {
        return new SeedFieldPlan(field, "Ryegrass", "Perennial", new BigDecimal("12.50"),
                "Wheat", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 30), "Turf", "North boundary",
                SeedFieldStage.SOWN, "");
    }
}
