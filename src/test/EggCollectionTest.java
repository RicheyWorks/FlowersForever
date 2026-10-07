package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Egg collection")
class EggCollectionTest {

    @Test
    void normalizesOptionalTextAndDerivesUsableCountWithoutChangingTheRecord() {
        EggCollection collection = new EggCollection(LocalDate.of(2026, 10, 1),
                " Layer flock ", null, 84, 3, null);

        assertThat(collection.getFlockName()).isEqualTo("Layer flock");
        assertThat(collection.getLocation()).isEmpty();
        assertThat(collection.getNotes()).isEmpty();
        assertThat(collection.getUsableCount()).isEqualTo(81);
        assertThat(collection.getCollectedCount()).isEqualTo(84);
        assertThat(collection.getUnusableCount()).isEqualTo(3);

        EggCollection snapshot = collection.snapshot();
        snapshot.setFlockName("Other flock");
        assertThat(collection.getFlockName()).isEqualTo("Layer flock");
    }

    @Test
    void requiresDateFlockAndCountsWithinLimits() {
        assertThatThrownBy(() -> new EggCollection(null, "Layers", "", 8, 0, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Collection date");
        assertThatThrownBy(() -> new EggCollection(date(), " ", "", 8, 0, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Flock name");
        assertThatThrownBy(() -> new EggCollection(date(), "Layers", "", null, 0, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Collected count");
        assertThatThrownBy(() -> new EggCollection(date(), "Layers", "", 8, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unusable count");
        assertThatThrownBy(() -> new EggCollection(date(), "Layers", "", -1, 0, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Collected count");
        assertThatThrownBy(() -> new EggCollection(date(), "Layers", "", 8, -1, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unusable count");
        assertThatThrownBy(() -> new EggCollection(date(), "Layers", "", 1_000_001, 0, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Collected count");
        assertThatThrownBy(() -> new EggCollection(date(), "Layers", "", 8, 9, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unusable count cannot exceed");
        assertThat(new EggCollection(date(), "Layers", "", 0, 0, "")
                .getUsableCount()).isZero();
        assertThat(new EggCollection(date(), "Layers", "", 1_000_000,
                1_000_000, "").getUsableCount()).isZero();
    }

    @Test
    void editCopiesBusinessFieldsWithoutTyingRecordsTogether() {
        EggCollection stored = collection();
        EggCollection edit = new EggCollection(LocalDate.of(2026, 10, 2),
                "North layers", "Coop B", 72, 2, "After rain");

        stored.copyBusinessFieldsFrom(edit);
        edit.setLocation("Coop C");

        assertThat(stored.getCollectionDate()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(stored.getFlockName()).isEqualTo("North layers");
        assertThat(stored.getLocation()).isEqualTo("Coop B");
        assertThat(stored.getCollectedCount()).isEqualTo(72);
        assertThat(stored.getUnusableCount()).isEqualTo(2);
        assertThat(stored.getUsableCount()).isEqualTo(70);
        assertThat(stored.getNotes()).isEqualTo("After rain");
    }

    private static LocalDate date() {
        return LocalDate.of(2026, 10, 1);
    }

    private static EggCollection collection() {
        return new EggCollection(date(), "Layers", "Coop A", 84, 3, "");
    }
}
