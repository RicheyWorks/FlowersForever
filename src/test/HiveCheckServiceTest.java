package com.flowerfarm.service;

import com.flowerfarm.model.HiveCheck;
import com.flowerfarm.model.HiveCheckStatus;
import com.flowerfarm.repository.HiveCheckRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Apiary hive check service")
class HiveCheckServiceTest {

    @Mock HiveCheckRepository repository;

    private HiveCheckService service;

    @BeforeEach
    void setUp() {
        service = new HiveCheckService(repository);
    }

    @Test
    void createsAndReadsDetachedChecks() {
        HiveCheck incoming = check("Hive 4");
        when(repository.save(any(HiveCheck.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        HiveCheck created = service.create(incoming);
        assertThat(created).isNotSameAs(incoming);
        created.setHiveLabel("Hive 9");
        assertThat(incoming.getHiveLabel()).isEqualTo("Hive 4");

        when(repository.findAll()).thenReturn(List.of(incoming));
        when(repository.findById(7L)).thenReturn(Optional.of(incoming));
        HiveCheck listed = service.list().get(0);
        HiveCheck found = service.findById(7L).orElseThrow();
        listed.setObservations("Changed");
        found.setStatus(HiveCheckStatus.INACTIVE);
        assertThat(incoming.getObservations()).isEqualTo("Adult bees at entrance");
        assertThat(incoming.getStatus()).isEqualTo(HiveCheckStatus.ACTIVE);
    }

    @Test
    void updatesLockedCheckAndChangesOnlyStatus() {
        HiveCheck stored = check("Hive 4");
        HiveCheck incoming = check("Hive 9");
        incoming.setObservations("New observation");
        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        HiveCheck updated = service.update(7L, incoming);
        assertThat(updated).isNotSameAs(stored).isNotSameAs(incoming);
        assertThat(stored.getHiveLabel()).isEqualTo("Hive 9");
        assertThat(stored.getObservations()).isEqualTo("New observation");

        HiveCheck moved = service.updateStatus(7L, HiveCheckStatus.OVERWINTERING);
        assertThat(moved).isNotSameAs(stored);
        assertThat(stored.getStatus()).isEqualTo(HiveCheckStatus.OVERWINTERING);
        assertThat(stored.getHiveLabel()).isEqualTo("Hive 9");
        assertThat(stored.getObservations()).isEqualTo("New observation");
        verify(repository, times(2)).save(stored);
    }

    @Test
    void rejectsInvalidEditsAndMissingTargetsBeforeSaving() {
        HiveCheck invalid = check("Hive 4");
        invalid.setNextCheckDate(invalid.getCheckDate().minusDays(1));
        assertThatThrownBy(() -> service.update(7L, invalid))
                .hasMessageContaining("Next check date");
        assertThatThrownBy(() -> service.updateStatus(7L, null))
                .hasMessageContaining("status");
        verifyNoInteractions(repository);

        when(repository.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(7L, check("Hive 9")))
                .hasMessageContaining("was not found");
    }

    private static HiveCheck check(String hiveLabel) {
        LocalDate date = LocalDate.of(2027, 4, 12);
        return new HiveCheck("East apiary", hiveLabel, date, "Spring check",
                HiveCheckStatus.ACTIVE, date.plusDays(10),
                "Adult bees at entrance", "Observe again");
    }
}
