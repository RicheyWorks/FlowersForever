package com.flowerfarm.service;

import com.flowerfarm.model.BerryBlockPlan;
import com.flowerfarm.model.BerryBlockStage;
import com.flowerfarm.model.BerrySalesChannel;
import com.flowerfarm.repository.BerryBlockPlanRepository;
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
@DisplayName("Berry block plan service")
class BerryBlockPlanServiceTest {

    @Mock BerryBlockPlanRepository repository;

    private BerryBlockPlanService service;

    @BeforeEach
    void setUp() {
        service = new BerryBlockPlanService(repository);
    }

    @Test
    void createsDetachedPlan() {
        BerryBlockPlan incoming = plan("North rows");
        when(repository.save(any(BerryBlockPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BerryBlockPlan created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setBlockName("South rows");
        assertThat(incoming.getBlockName()).isEqualTo("North rows");
        verify(repository).save(any(BerryBlockPlan.class));
    }

    @Test
    void listsAndFindsDetachedSnapshots() {
        BerryBlockPlan stored = plan("West rows");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        BerryBlockPlan listed = service.list().get(0);
        BerryBlockPlan found = service.findById(7L).orElseThrow();
        listed.setBlockName("East rows");
        found.setCultivar("Legacy");

        assertThat(stored.getBlockName()).isEqualTo("West rows");
        assertThat(stored.getCultivar()).isEqualTo("Duke");
    }

    @Test
    void updatesFieldsAfterLocking() {
        BerryBlockPlan stored = plan("North rows");
        BerryBlockPlan incoming = plan("South rows");
        incoming.setChannel(BerrySalesChannel.PROCESSING);
        incoming.setStage(BerryBlockStage.PICKING);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        BerryBlockPlan updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getBlockName()).isEqualTo("South rows");
        assertThat(stored.getChannel()).isEqualTo(BerrySalesChannel.PROCESSING);
        assertThat(stored.getStage()).isEqualTo(BerryBlockStage.PICKING);
    }

    @Test
    void rejectsInvalidEditBeforeLockingAndMissingTargetBeforeSaving() {
        BerryBlockPlan invalid = plan("North rows");
        invalid.setLastPickDate(invalid.getFirstPickDate().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Last pick date");
        verifyNoInteractions(repository);

        BerryBlockPlan incoming = plan("New rows");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidIdAndNullPlan() {
        assertThatThrownBy(() -> service.findById(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        verifyNoInteractions(repository);
    }

    private static BerryBlockPlan plan(String blockName) {
        return new BerryBlockPlan(blockName, "Blueberries", "Duke",
                new BigDecimal("2.50"), LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 1),
                BerrySalesChannel.FRESH, BerryBlockStage.GROWING, "");
    }
}
