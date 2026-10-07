package com.flowerfarm.service;

import com.flowerfarm.model.CropAreaPlan;
import com.flowerfarm.model.CropAreaStage;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.repository.CropAreaPlanRepository;
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
@DisplayName("Crop area plan service")
class CropAreaPlanServiceTest {

    @Mock CropAreaPlanRepository repository;

    private CropAreaPlanService service;

    @BeforeEach
    void setUp() {
        service = new CropAreaPlanService(repository);
    }

    @Test
    void createsDetachedPlanForSupportedWorkspace() {
        CropAreaPlan incoming = plan(FarmWorkspace.POTATO_FARM, "North pivot");
        when(repository.save(any(CropAreaPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CropAreaPlan created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setAreaName("South pivot");
        assertThat(incoming.getAreaName()).isEqualTo("North pivot");
        verify(repository).save(any(CropAreaPlan.class));
    }

    @Test
    void listsOnlyRequestedWorkspaceAndReturnsSnapshots() {
        CropAreaPlan stored = plan(FarmWorkspace.APPLE_ORCHARD, "West block");
        when(repository.findByWorkspace(FarmWorkspace.APPLE_ORCHARD))
                .thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));

        CropAreaPlan listed = service.listByWorkspace(FarmWorkspace.APPLE_ORCHARD).get(0);
        CropAreaPlan found = service.findById(7L).orElseThrow();
        listed.setAreaName("East block");
        found.setVariety("Gala");

        assertThat(stored.getAreaName()).isEqualTo("West block");
        assertThat(stored.getVariety()).isEqualTo("Russet");
        verify(repository).findByWorkspace(FarmWorkspace.APPLE_ORCHARD);
    }

    @Test
    void rejectsUnsupportedListWithoutReadingRepository() {
        assertThatThrownBy(() -> service.listByWorkspace(FarmWorkspace.CUSTOM_FARM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("potato farm or apple orchard");
        verifyNoInteractions(repository);
    }

    @Test
    void updatesValidatedFieldsButKeepsWorkspace() {
        CropAreaPlan stored = plan(FarmWorkspace.POTATO_FARM, "North pivot");
        CropAreaPlan incoming = plan(FarmWorkspace.POTATO_FARM, "South pivot");
        incoming.setStage(CropAreaStage.HARVEST_READY);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        CropAreaPlan updated = service.update(7L, incoming);

        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getAreaName()).isEqualTo("South pivot");
        assertThat(stored.getStage()).isEqualTo(CropAreaStage.HARVEST_READY);
        assertThat(stored.getWorkspace()).isEqualTo(FarmWorkspace.POTATO_FARM);
    }

    @Test
    void rejectsCrossWorkspaceEditBeforeSaving() {
        CropAreaPlan stored = plan(FarmWorkspace.POTATO_FARM, "North pivot");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.update(7L,
                plan(FarmWorkspace.APPLE_ORCHARD, "West block")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot move between farm workspaces");
        assertThat(stored.getAreaName()).isEqualTo("North pivot");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidEditBeforeLocking() {
        CropAreaPlan incoming = plan(FarmWorkspace.POTATO_FARM, "North pivot");
        incoming.setTargetHarvestDate(incoming.getPlantingDate().minusDays(1));

        assertThatThrownBy(() -> service.update(7L, incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target harvest date");
        verifyNoInteractions(repository);
    }

    @Test
    void stageMoveChangesOnlyStageOnLockedPlan() {
        CropAreaPlan stored = plan(FarmWorkspace.APPLE_ORCHARD, "West block");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        CropAreaPlan moved = service.updateStage(7L, FarmWorkspace.APPLE_ORCHARD,
                CropAreaStage.HARVEST_READY);

        assertThat(moved.getStage()).isEqualTo(CropAreaStage.HARVEST_READY);
        assertThat(stored.getAreaName()).isEqualTo("West block");
        assertThat(stored.getAcres()).isEqualByComparingTo("18.25");
        assertThat(stored.getVariety()).isEqualTo("Russet");
        verify(repository).save(stored);
    }

    @Test
    void stageMoveRejectsCrossWorkspaceIdentity() {
        CropAreaPlan stored = plan(FarmWorkspace.POTATO_FARM, "North pivot");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.updateStage(7L, FarmWorkspace.APPLE_ORCHARD,
                CropAreaStage.COMPLETE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another farm workspace");
        assertThat(stored.getStage()).isEqualTo(CropAreaStage.GROWING);
        verify(repository, never()).save(any());
    }

    private static CropAreaPlan plan(FarmWorkspace workspace, String name) {
        return new CropAreaPlan(workspace, name, "Russet", new BigDecimal("18.25"),
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 10, 1),
                CropAreaStage.GROWING, "");
    }
}
