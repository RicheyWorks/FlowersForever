package com.flowerfarm.service;

import com.flowerfarm.model.CranberryBedPlan;
import com.flowerfarm.model.CranberryBedStage;
import com.flowerfarm.model.CranberryHarvestMethod;
import com.flowerfarm.repository.CranberryBedPlanRepository;
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
@DisplayName("Cranberry bed plan service")
class CranberryBedPlanServiceTest {

    @Mock CranberryBedPlanRepository repository;

    private CranberryBedPlanService service;

    @BeforeEach
    void setUp() {
        service = new CranberryBedPlanService(repository);
    }

    @Test
    void createsAndReadsDetachedPlans() {
        CranberryBedPlan incoming = plan("North bed");
        when(repository.save(any(CranberryBedPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        CranberryBedPlan created = service.create(incoming);
        assertThat(created).isNotSameAs(incoming);
        created.setBedName("South bed");
        assertThat(incoming.getBedName()).isEqualTo("North bed");

        when(repository.findAll()).thenReturn(List.of(incoming));
        when(repository.findById(7L)).thenReturn(Optional.of(incoming));
        CranberryBedPlan listed = service.list().get(0);
        CranberryBedPlan found = service.findById(7L).orElseThrow();
        listed.setBedName("West bed");
        found.setVariety("Ben Lear");
        assertThat(incoming.getBedName()).isEqualTo("North bed");
        assertThat(incoming.getVariety()).isEqualTo("Stevens");
    }

    @Test
    void updatesFieldsAfterLockingAndMovesOnlyStage() {
        CranberryBedPlan stored = plan("North bed");
        CranberryBedPlan incoming = plan("South bed");
        incoming.setHarvestMethod(CranberryHarvestMethod.DRY);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        CranberryBedPlan updated = service.update(7L, incoming);
        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getBedName()).isEqualTo("South bed");
        assertThat(stored.getHarvestMethod()).isEqualTo(CranberryHarvestMethod.DRY);

        CranberryBedPlan moved = service.updateStage(7L,
                CranberryBedStage.HARVEST_WINDOW);
        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStage()).isEqualTo(CranberryBedStage.HARVEST_WINDOW);
        assertThat(stored.getBedName()).isEqualTo("South bed");
        assertThat(stored.getHarvestMethod()).isEqualTo(CranberryHarvestMethod.DRY);
        verify(repository, times(2)).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        CranberryBedPlan invalid = plan("North bed");
        invalid.setPlannedHarvestStart(null);
        invalid.setPlannedHarvestEnd(LocalDate.of(2027, 10, 20));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .hasMessageContaining("requires a planned harvest start");
        assertThatThrownBy(() -> service.updateStage(7L, null))
                .hasMessageContaining("stage");
        verifyNoInteractions(repository);

        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, plan("South bed")))
                .hasMessageContaining("was not found");
    }

    private static CranberryBedPlan plan(String bedName) {
        return new CranberryBedPlan(bedName, "Stevens", new BigDecimal("2.50"),
                CranberryHarvestMethod.WET, LocalDate.of(2027, 10, 1),
                LocalDate.of(2027, 10, 20), "Processor",
                CranberryBedStage.GROWING, "Fall plan");
    }
}
