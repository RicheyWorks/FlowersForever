package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@DisplayName("Season planting block")
class SeasonPlanEntryTest {

    @Test
    @DisplayName("preserves normalized crop, bed, capacity, and timing")
    void preservesPlanningFacts() {
        SeasonPlanEntry entry = entry();

        assertThat(entry.getCropName()).isEqualTo("Nootka Rose");
        assertThat(entry.getBedOrField()).isEqualTo("Bed A");
        assertThat(entry.getBedFeet()).isEqualTo(80);
        assertThat(entry.getExpectedStems()).isEqualTo(640);
        assertThat(entry.getStatus()).isEqualTo(SeasonPlanStatus.PLANTED);
    }

    @Test
    @DisplayName("rejects schedule dates that cannot form a season timeline")
    void rejectsInvalidSchedule() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SeasonPlanEntry(
                2026, "Rose", "", "A",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1),
                50, 300, SeasonPlanStatus.PLANNED, ""))
                .withMessageContaining("Transplant date");

        assertThatIllegalArgumentException().isThrownBy(() -> new SeasonPlanEntry(
                2026, "Rose", "", "A",
                LocalDate.of(2026, 4, 1), null,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 30),
                50, 300, SeasonPlanStatus.PLANNED, ""))
                .withMessageContaining("Harvest end");
    }

    @Test
    @DisplayName("requires usable bed and yield capacity values")
    void rejectsInvalidCapacity() {
        SeasonPlanEntry entry = entry();

        assertThatIllegalArgumentException().isThrownBy(() -> entry.setBedFeet(0))
                .withMessageContaining("Bed length");
        assertThatIllegalArgumentException().isThrownBy(() -> entry.setExpectedStems(-1))
                .withMessageContaining("Expected stems");
    }

    private static SeasonPlanEntry entry() {
        return new SeasonPlanEntry(
                2026, " Nootka Rose ", "", " Bed A ",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 12),
                LocalDate.of(2026, 6, 20), LocalDate.of(2026, 9, 10),
                80, 640, SeasonPlanStatus.PLANTED, "Keep the first flush for market.");
    }
}
