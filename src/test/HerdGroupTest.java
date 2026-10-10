package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Livestock herd group")
class HerdGroupTest {

    @Test
    void normalizesTextAllowsPlannedZeroAndKeepsSnapshotsDetached() {
        HerdGroup group = new HerdGroup(" Spring calves ", " Cattle ", 0,
                null, null, null, null);

        assertThat(group.getGroupName()).isEqualTo("Spring calves");
        assertThat(group.getSpecies()).isEqualTo("Cattle");
        assertThat(group.getHeadCount()).isZero();
        assertThat(group.getLocation()).isEmpty();
        assertThat(group.getPurpose()).isEmpty();
        assertThat(group.getNotes()).isEmpty();
        assertThat(group.getNextCheckDate()).isNull();

        HerdGroup snapshot = group.snapshot();
        snapshot.setGroupName("Autumn calves");
        assertThat(group.getGroupName()).isEqualTo("Spring calves");
    }

    @Test
    void requiresNameSpeciesAndValidHeadCount() {
        assertThatThrownBy(() -> new HerdGroup(" ", "Cattle", 8,
                "", "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Group name");
        assertThatThrownBy(() -> new HerdGroup("Spring calves", " ", 8,
                "", "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Species");
        assertThatThrownBy(() -> new HerdGroup("Spring calves", "Cattle", null,
                "", "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Head count");
        HerdGroup group = group();
        assertThatThrownBy(() -> group.setHeadCount(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Head count");
        assertThatThrownBy(() -> group.setHeadCount(1_000_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Head count");
        group.setHeadCount(1_000_000);
        assertThat(group.getHeadCount()).isEqualTo(1_000_000);
    }

    @Test
    void updatesEditablePlanFieldsWithoutChangingSource() {
        HerdGroup stored = group();
        HerdGroup edit = new HerdGroup("Ewes", "Sheep", 45,
                "North pasture", "Breeding", LocalDate.of(2026, 10, 7),
                "Check fence");

        stored.copyBusinessFieldsFrom(edit);
        edit.setLocation("South pasture");

        assertThat(stored.getGroupName()).isEqualTo("Ewes");
        assertThat(stored.getSpecies()).isEqualTo("Sheep");
        assertThat(stored.getHeadCount()).isEqualTo(45);
        assertThat(stored.getLocation()).isEqualTo("North pasture");
        assertThat(stored.getPurpose()).isEqualTo("Breeding");
        assertThat(stored.getNextCheckDate()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(stored.getNotes()).isEqualTo("Check fence");
    }

    private static HerdGroup group() {
        return new HerdGroup("Spring calves", "Cattle", 18,
                "West pasture", "Growing", LocalDate.of(2026, 10, 3), "");
    }
}
