package com.flowerfarm.service;

import com.flowerfarm.model.PumpkinPatchPlan;
import com.flowerfarm.model.PumpkinPatchStage;
import com.flowerfarm.model.PumpkinSalesMode;
import com.flowerfarm.repository.PumpkinPatchPlanRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pumpkin patch plan service")
class PumpkinPatchPlanServiceTest {

    @Mock PumpkinPatchPlanRepository repository;

    private PumpkinPatchPlanService service;

    @BeforeEach
    void setUp() {
        service = new PumpkinPatchPlanService(repository);
    }

    @Test
    void createsAndReadsDetachedPlans() {
        PumpkinPatchPlan incoming = plan("North patch");
        when(repository.save(any(PumpkinPatchPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PumpkinPatchPlan created = service.create(incoming);
        assertThat(created).isNotSameAs(incoming);
        created.setPatchName("South patch");
        assertThat(incoming.getPatchName()).isEqualTo("North patch");

        when(repository.findAll()).thenReturn(List.of(incoming));
        when(repository.findById(7L)).thenReturn(Optional.of(incoming));
        PumpkinPatchPlan listed = service.list().get(0);
        PumpkinPatchPlan found = service.findById(7L).orElseThrow();
        listed.setPatchName("West patch");
        found.setVariety("Sugar pie");
        assertThat(incoming.getPatchName()).isEqualTo("North patch");
        assertThat(incoming.getVariety()).isEqualTo("Jack O Lantern");
    }

    @Test
    void updatesFieldsAfterLockingAndMovesOnlyStage() {
        PumpkinPatchPlan stored = plan("North patch");
        PumpkinPatchPlan incoming = plan("South patch");
        incoming.setSalesMode(PumpkinSalesMode.FARM_STAND);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        PumpkinPatchPlan updated = service.update(7L, incoming);
        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getPatchName()).isEqualTo("South patch");
        assertThat(stored.getSalesMode()).isEqualTo(PumpkinSalesMode.FARM_STAND);

        PumpkinPatchPlan moved = service.updateStage(7L,
                PumpkinPatchStage.PICKING_WINDOW);
        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(PumpkinPatchStage.PICKING_WINDOW);
        assertThat(stored.getPatchName()).isEqualTo("South patch");
        assertThat(stored.getSalesMode()).isEqualTo(PumpkinSalesMode.FARM_STAND);
        verify(repository, times(2)).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        PumpkinPatchPlan invalid = plan("North patch");
        invalid.setPlannedPickingStart(null);
        invalid.setPlannedPickingEnd(LocalDate.of(2027, 10, 20));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .hasMessageContaining("requires a planned picking start");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, plan("South patch")))
                .hasMessageContaining("was not found");
    }

    private static PumpkinPatchPlan plan(String patchName) {
        return new PumpkinPatchPlan(patchName, "Jack O Lantern", new BigDecimal("2.50"),
                LocalDate.of(2027, 5, 15), LocalDate.of(2027, 10, 1),
                LocalDate.of(2027, 10, 20), PumpkinSalesMode.PICK_YOUR_OWN,
                "East entrance", PumpkinPatchStage.GROWING, "Fall plan");
    }
}
