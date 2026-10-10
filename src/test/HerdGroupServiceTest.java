package com.flowerfarm.service;

import com.flowerfarm.model.HerdGroup;
import com.flowerfarm.repository.HerdGroupRepository;
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
@DisplayName("Herd group service")
class HerdGroupServiceTest {

    @Mock HerdGroupRepository repository;

    private HerdGroupService service;

    @BeforeEach
    void setUp() {
        service = new HerdGroupService(repository);
    }

    @Test
    void createsDetachedPlan() {
        HerdGroup incoming = group("Spring calves");
        when(repository.save(any(HerdGroup.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HerdGroup created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setGroupName("Summer calves");
        assertThat(incoming.getGroupName()).isEqualTo("Spring calves");
        verify(repository).save(any(HerdGroup.class));
    }

    @Test
    void listsFindsAndUpdatesThroughDetachedSnapshotsAndRowLock() {
        HerdGroup stored = group("Spring calves");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));
        assertThat(service.list().get(0)).isNotSameAs(stored);
        HerdGroup found = service.find(7L).orElseThrow();
        found.setHeadCount(30);
        assertThat(stored.getHeadCount()).isEqualTo(18);

        HerdGroup edit = group("Summer calves");
        edit.setHeadCount(22);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);
        HerdGroup updated = service.update(7L, edit);
        assertThat(updated).isNotSameAs(stored).isNotSameAs(edit);
        assertThat(stored.getGroupName()).isEqualTo("Summer calves");
        assertThat(stored.getHeadCount()).isEqualTo(22);
        verify(repository).findByIdForUpdate(7L);
    }

    @Test
    void rejectsInvalidInputBeforeLockingAndMissingTargetWithoutSaving() {
        assertThatThrownBy(() -> service.update(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        verifyNoInteractions(repository);

        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, group("Missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidIdAndNullGroup() {
        assertThatThrownBy(() -> service.find(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        verifyNoInteractions(repository);
    }

    private static HerdGroup group(String name) {
        return new HerdGroup(name, "Cattle", 18,
                "West pasture", "Growing", LocalDate.of(2026, 10, 3), "");
    }
}
