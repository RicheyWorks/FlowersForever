package com.flowerfarm.service;

import com.flowerfarm.model.NurseryBatch;
import com.flowerfarm.model.NurseryBatchStage;
import com.flowerfarm.model.NurseryPlantForm;
import com.flowerfarm.model.NurserySalesChannel;
import com.flowerfarm.repository.NurseryBatchRepository;
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
@DisplayName("Nursery batch service")
class NurseryBatchServiceTest {

    @Mock NurseryBatchRepository repository;

    private NurseryBatchService service;

    @BeforeEach
    void setUp() {
        service = new NurseryBatchService(repository);
    }

    @Test
    void createsDetachedBatch() {
        NurseryBatch incoming = batch("Spring starts");
        when(repository.save(any(NurseryBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NurseryBatch created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setBatchName("Autumn starts");
        assertThat(incoming.getBatchName()).isEqualTo("Spring starts");
        verify(repository).save(any(NurseryBatch.class));
    }

    @Test
    void listsFindsAndUpdatesThroughDetachedSnapshots() {
        NurseryBatch stored = batch("Spring starts");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));
        assertThat(service.list().get(0)).isNotSameAs(stored);
        NurseryBatch found = service.find(7L).orElseThrow();
        found.setPlantName("Roses");
        assertThat(stored.getPlantName()).isEqualTo("Dahlias");

        NurseryBatch edit = batch("Summer starts");
        edit.setStage(NurseryBatchStage.READY_FOR_SALE);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);
        NurseryBatch updated = service.update(7L, edit);
        assertThat(updated).isNotSameAs(stored).isNotSameAs(edit);
        assertThat(stored.getBatchName()).isEqualTo("Summer starts");
        assertThat(stored.getStage()).isEqualTo(NurseryBatchStage.READY_FOR_SALE);
    }

    @Test
    void rejectsInvalidEditsBeforeLockingAndDoesNotSaveMissingTarget() {
        NurseryBatch invalid = batch("Spring starts");
        invalid.setPlannedReadyDate(LocalDate.of(2026, 2, 28));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned ready date");
        verifyNoInteractions(repository);

        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, batch("Other")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedBatch() {
        NurseryBatch stored = batch("Spring starts");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        NurseryBatch moved = service.updateStage(7L, NurseryBatchStage.READY_FOR_SALE);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(NurseryBatchStage.READY_FOR_SALE);
        assertThat(stored.getBatchName()).isEqualTo("Spring starts");
        assertThat(stored.getPlantName()).isEqualTo("Dahlias");
        assertThat(stored.getCultivar()).isEqualTo("Cafe au Lait");
        assertThat(stored.getGrowingArea()).isEqualTo("Greenhouse 2");
        assertThat(stored.getStartedDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(stored.getPlannedReadyDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(stored.getStartingCount()).isEqualTo(120);
        assertThat(stored.getChannel()).isEqualTo(NurserySalesChannel.RETAIL);
        verify(repository).findByIdForUpdate(7L);
        verify(repository).save(stored);
    }

    @Test
    void stageMoveRejectsInvalidAndMissingTargets() {
        assertThatThrownBy(() -> service.updateStage(0L, NurseryBatchStage.GROWING))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("positive");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stage");
        verifyNoInteractions(repository);

        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateStage(7L, NurseryBatchStage.GROWING))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidIdAndNullBatch() {
        assertThatThrownBy(() -> service.find(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        verifyNoInteractions(repository);
    }

    private static NurseryBatch batch(String name) {
        return new NurseryBatch(name, "Dahlias", "Cafe au Lait",
                NurseryPlantForm.STARTS_OR_PLUGS, "Greenhouse 2",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 1), 120,
                NurserySalesChannel.RETAIL, NurseryBatchStage.GROWING, "");
    }
}
