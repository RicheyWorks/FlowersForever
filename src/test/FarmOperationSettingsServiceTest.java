package com.flowerfarm.service;

import com.flowerfarm.model.FarmEnterprise;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.model.FarmState;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.model.FlowerOffering;
import com.flowerfarm.repository.FarmOperationSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FarmOperationSettingsServiceTest {

    @Mock FarmOperationSettingsRepository repository;

    private FarmOperationSettingsService service;

    @BeforeEach
    void setUp() {
        service = new FarmOperationSettingsService(repository);
    }

    @Test
    void readsDetachedDefaultWhenNoSettingsRowExists() {
        FarmOperationSettings first = service.getSettings();
        FarmOperationSettings second = service.getSettings();

        assertThat(first).isNotSameAs(second);
        assertThat(first.getState()).isEqualTo(FarmState.WA);
        assertThat(first.getWorkspace()).isEqualTo(FarmWorkspace.ROSE_AND_EVENTS);
        assertThat(first.getEnterprises()).containsExactly(FarmEnterprise.CUT_FLOWERS);
    }

    @Test
    void savesMixedFarmThroughLockedSingletonWithoutAliasingInput() {
        FarmOperationSettings existing = new FarmOperationSettings();
        FarmOperationSettings incoming = new FarmOperationSettings();
        incoming.setState(FarmState.OR);
        incoming.setWorkspace(FarmWorkspace.CUSTOM_FARM);
        incoming.setPrimaryEnterprise(FarmEnterprise.ORCHARDS);
        incoming.setEnterprises(Set.of(FarmEnterprise.ORCHARDS, FarmEnterprise.CUT_FLOWERS));
        incoming.setEnabledWorkspaces(Set.of(FarmWorkspace.APPLE_ORCHARD));
        incoming.setSpecialtyCrops(Set.of("Apples", "Roses"));
        incoming.setFlowerOfferings(Set.of(FlowerOffering.STEMS, FlowerOffering.WEDDINGS));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        FarmOperationSettings saved = service.saveSettings(incoming);

        assertThat(saved).isNotSameAs(existing).isNotSameAs(incoming);
        assertThat(saved.getState()).isEqualTo(FarmState.OR);
        assertThat(saved.getWorkspace()).isEqualTo(FarmWorkspace.CUSTOM_FARM);
        assertThat(saved.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.CUSTOM_FARM, FarmWorkspace.APPLE_ORCHARD);
        assertThat(existing.getWorkspace()).isEqualTo(FarmWorkspace.CUSTOM_FARM);
        assertThat(existing.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.CUSTOM_FARM, FarmWorkspace.APPLE_ORCHARD);
        assertThat(saved.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.ORCHARDS);
        assertThat(saved.getSpecialtyCrops()).containsExactly("Apples", "Roses");
        assertThat(saved.getFlowerOfferings()).containsExactly(
                FlowerOffering.STEMS, FlowerOffering.WEDDINGS);
        verify(repository).findByIdForUpdate(1L);
        verify(repository).save(existing);
    }

    @Test
    void rejectsInvalidSelectionsBeforeAnyWrite() {
        FarmOperationSettings incoming = new FarmOperationSettings();
        incoming.setPrimaryEnterprise(FarmEnterprise.ROW_CROPS);

        assertThatThrownBy(() -> service.saveSettings(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primary enterprise");
        verify(repository, never()).save(any());
    }
}
