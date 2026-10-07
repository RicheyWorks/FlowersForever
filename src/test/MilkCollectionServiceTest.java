package com.flowerfarm.service;

import com.flowerfarm.model.MilkCollection;
import com.flowerfarm.model.MilkCollectionUnit;
import com.flowerfarm.repository.MilkCollectionRepository;
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
@DisplayName("Milk collection service")
class MilkCollectionServiceTest {

    @Mock MilkCollectionRepository repository;

    private MilkCollectionService service;

    @BeforeEach
    void setUp() {
        service = new MilkCollectionService(repository);
    }

    @Test
    void createsDetachedCollectionWithoutPostingStock() {
        MilkCollection incoming = collection("Milking herd");
        when(repository.save(any(MilkCollection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MilkCollection created = service.create(incoming);

        assertThat(created).isNotSameAs(incoming);
        created.setGroupName("Other herd");
        assertThat(incoming.getGroupName()).isEqualTo("Milking herd");
        verify(repository).save(any(MilkCollection.class));
    }

    @Test
    void listsFindsAndUpdatesThroughDetachedSnapshotsAndRowLock() {
        MilkCollection stored = collection("Milking herd");
        when(repository.findAll()).thenReturn(List.of(stored));
        when(repository.findById(7L)).thenReturn(Optional.of(stored));
        assertThat(service.list().get(0)).isNotSameAs(stored);
        MilkCollection found = service.find(7L).orElseThrow();
        found.setAmount(new BigDecimal("126.00"));
        assertThat(stored.getAmount()).isEqualByComparingTo("125.50");

        MilkCollection edit = collection("North herd");
        edit.setAmount(new BigDecimal("90.25"));
        edit.setUnit(MilkCollectionUnit.GALLONS);
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);
        MilkCollection updated = service.update(7L, edit);
        assertThat(updated).isNotSameAs(stored).isNotSameAs(edit);
        assertThat(stored.getGroupName()).isEqualTo("North herd");
        assertThat(stored.getAmount()).isEqualByComparingTo("90.25");
        assertThat(stored.getUnit()).isEqualTo(MilkCollectionUnit.GALLONS);
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

    private static MilkCollection collection(String group) {
        return new MilkCollection(LocalDate.of(2026, 10, 1), group, "Morning",
                "Tank A", new BigDecimal("125.50"), MilkCollectionUnit.POUNDS, "");
    }
}
