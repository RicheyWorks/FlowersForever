package com.flowerfarm.service;

import com.flowerfarm.model.HayCutting;
import com.flowerfarm.model.HayCuttingStage;
import com.flowerfarm.repository.HayCuttingRepository;
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
@DisplayName("Hay cutting service")
class HayCuttingServiceTest {

    @Mock HayCuttingRepository repository;

    private HayCuttingService service;

    @BeforeEach
    void setUp() {
        service = new HayCuttingService(repository);
    }

    @Test
    void createsDetachedPlan() {
        HayCutting incoming = plan("North field");
        when(repository.save(any(HayCutting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HayCutting created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setFieldName("South field");
        assertThat(incoming.getFieldName()).isEqualTo("North field");
        verify(repository).save(any(HayCutting.class));
    }

    @Test
    void listsAndFindsDetachedSnapshots() {
        HayCutting stored = plan("West pasture");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        HayCutting listed = service.list().get(0);
        HayCutting found = service.findById(7L).orElseThrow();
        listed.setFieldName("East pasture");
        found.setForageCrop("Timothy");

        assertThat(stored.getFieldName()).isEqualTo("West pasture");
        assertThat(stored.getForageCrop()).isEqualTo("Alfalfa");
    }

    @Test
    void updatesFieldsAfterLocking() {
        HayCutting stored = plan("North field");
        HayCutting incoming = plan("South field");
        incoming.setForageCrop("Timothy");
        incoming.setCuttingNumber(3);
        incoming.setStage(HayCuttingStage.CURING);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        HayCutting updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getFieldName()).isEqualTo("South field");
        assertThat(stored.getForageCrop()).isEqualTo("Timothy");
        assertThat(stored.getCuttingNumber()).isEqualTo(3);
        assertThat(stored.getStage()).isEqualTo(HayCuttingStage.CURING);
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedPlan() {
        HayCutting stored = plan("North field");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        HayCutting moved = service.updateStage(7L, HayCuttingStage.BALED);

        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(HayCuttingStage.BALED);
        assertThat(stored.getFieldName()).isEqualTo("North field");
        assertThat(stored.getForageCrop()).isEqualTo("Alfalfa");
        assertThat(stored.getCuttingNumber()).isEqualTo(2);
        verify(repository).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        HayCutting invalid = plan("North field");
        invalid.setPlannedBaleDate(invalid.getPlannedCutDate().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned bale date");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        HayCutting incoming = plan("New field");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> service.updateStage(7L, HayCuttingStage.PLANNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidIdAndNullPlan() {
        assertThatThrownBy(() -> service.findById(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.updateStage(0L, HayCuttingStage.CUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        verifyNoInteractions(repository);
    }

    private static HayCutting plan(String fieldName) {
        return new HayCutting(fieldName, "Alfalfa", 2,
                new BigDecimal("12.50"), LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 5), HayCuttingStage.CUT, "");
    }
}
