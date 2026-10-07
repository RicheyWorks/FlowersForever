package com.flowerfarm.service;

import com.flowerfarm.model.CannabisCultivationBatch;
import com.flowerfarm.model.CannabisCultivationStage;
import com.flowerfarm.repository.CannabisCultivationRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cannabis cultivation service")
class CannabisCultivationServiceTest {

    @Mock CannabisCultivationRepository repository;

    private CannabisCultivationService service;

    @BeforeEach
    void setUp() {
        service = new CannabisCultivationService(repository);
    }

    @Test
    void createsAValidatedDetachedBatchWithoutAliasingTheRequest() {
        CannabisCultivationBatch incoming = batch("Autumn A", 24);
        when(repository.save(any(CannabisCultivationBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CannabisCultivationBatch created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        assertThat(created.getBatchName()).isEqualTo("Autumn A");
        created.setPlantCount(40);
        assertThat(incoming.getPlantCount()).isEqualTo(24);
        verify(repository).save(any(CannabisCultivationBatch.class));
    }

    @Test
    void returnsDetachedSnapshotsForReads() {
        CannabisCultivationBatch stored = batch("Autumn A", 24);
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        CannabisCultivationBatch listed = service.listAll().get(0);
        CannabisCultivationBatch found = service.findById(7L).orElseThrow();
        listed.setBatchName("Edited list item");
        found.setPlantCount(50);

        assertThat(listed).isNotSameAs(stored);
        assertThat(found).isNotSameAs(stored);
        assertThat(stored.getBatchName()).isEqualTo("Autumn A");
        assertThat(stored.getPlantCount()).isEqualTo(24);
    }

    @Test
    void locksTheExistingRowAndCopiesOnlyValidatedBusinessFields() {
        CannabisCultivationBatch stored = batch("Autumn A", 24);
        CannabisCultivationBatch incoming = batch("Autumn B", 30);
        incoming.setStage(CannabisCultivationStage.FLOWERING);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        CannabisCultivationBatch updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getBatchName()).isEqualTo("Autumn B");
        assertThat(stored.getPlantCount()).isEqualTo(30);
        assertThat(stored.getStage()).isEqualTo(CannabisCultivationStage.FLOWERING);
        assertThat(incoming.getBatchName()).isEqualTo("Autumn B");
        verify(repository).findByIdForUpdate(7L);
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidEditBeforeLockingOrMutatingAnExistingRow() {
        CannabisCultivationBatch incoming = batch("Autumn B", 30);
        incoming.setPlannedHarvestDate(incoming.getStartDate().minusDays(1));

        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest date");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsAnEditForAMissingBatch() {
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(7L, batch("Autumn B", 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not found");
        verify(repository, never()).save(any());
    }

    @Test
    void stageMoveLocksSavedBatchAndPreservesOtherPlanningFields() {
        CannabisCultivationBatch stored = batch("Autumn A", 24);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        CannabisCultivationBatch moved = service.updateStage(7L,
                CannabisCultivationStage.FLOWERING);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(CannabisCultivationStage.FLOWERING);
        assertThat(stored.getBatchName()).isEqualTo("Autumn A");
        assertThat(stored.getArea()).isEqualTo("Room 1");
        assertThat(stored.getPlantCount()).isEqualTo(24);
        assertThat(stored.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(stored.getPlannedHarvestDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        verify(repository).findByIdForUpdate(7L);
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidStageMoveBeforeTouchingStore() {
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage is required");
        assertThatThrownBy(() -> service.updateStage(0L,
                CannabisCultivationStage.FLOWERING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id must be positive");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsStageMoveForMissingBatchWithoutSaving() {
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStage(7L,
                CannabisCultivationStage.FLOWERING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not found");
        verify(repository, never()).save(any());
    }

    private static CannabisCultivationBatch batch(String name, int plants) {
        return new CannabisCultivationBatch(name, "Cultivar A", "Room 1", plants,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1),
                CannabisCultivationStage.VEGETATIVE, "");
    }
}
