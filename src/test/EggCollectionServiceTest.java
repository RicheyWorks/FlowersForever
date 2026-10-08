package com.flowerfarm.service;

import com.flowerfarm.model.EggCollection;
import com.flowerfarm.repository.EggCollectionRepository;
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
@DisplayName("Egg collection service")
class EggCollectionServiceTest {

    @Mock EggCollectionRepository repository;

    private EggCollectionService service;

    @BeforeEach
    void setUp() {
        service = new EggCollectionService(repository);
    }

    @Test
    void createsDetachedCollectionWithoutPostingStock() {
        EggCollection incoming = collection("Layers");
        when(repository.save(any(EggCollection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EggCollection created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setFlockName("Other layers");
        assertThat(incoming.getFlockName()).isEqualTo("Layers");
        verify(repository).save(any(EggCollection.class));
    }

    @Test
    void listsFindsAndUpdatesThroughDetachedSnapshotsAndRowLock() {
        EggCollection stored = collection("Layers");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));
        assertThat(service.list().get(0)).isNotSameAs(stored);
        EggCollection found = service.find(7L).orElseThrow();
        found.setCollectedCount(120);
        assertThat(stored.getCollectedCount()).isEqualTo(84);

        EggCollection edit = collection("North layers");
        edit.setCollectedCount(90);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);
        EggCollection updated = service.update(7L, edit);
        assertThat(updated).isNotSameAs(stored).isNotSameAs(edit);
        assertThat(stored.getFlockName()).isEqualTo("North layers");
        assertThat(stored.getCollectedCount()).isEqualTo(90);
        verify(repository).findByIdForUpdate(7L);
    }

    @Test
    void rejectsBadInputBeforeLockingAndMissingTargetWithoutSaving() {
        assertThatThrownBy(() -> service.update(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> service.find(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        verifyNoInteractions(repository);

        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, collection("Missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
        verify(repository, never()).save(any());
    }

    private static EggCollection collection(String flock) {
        return new EggCollection(LocalDate.of(2026, 10, 1), flock, "Coop A",
                84, 3, "");
    }
}
