package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cannabis cultivation batch")
class CannabisCultivationBatchTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate HARVEST = LocalDate.of(2026, 12, 1);

    @Test
    void normalizesOptionalDetailsAndKeepsSnapshotsDetached() {
        CannabisCultivationBatch batch = new CannabisCultivationBatch(
                " Autumn A ", null, " Greenhouse 2 ", 0,
                START, HARVEST, null, null);

        assertThat(batch.getBatchName()).isEqualTo("Autumn A");
        assertThat(batch.getArea()).isEqualTo("Greenhouse 2");
        assertThat(batch.getCultivar()).isEmpty();
        assertThat(batch.getNotes()).isEmpty();
        assertThat(batch.getStage()).isEqualTo(CannabisCultivationStage.PLANNED);

        CannabisCultivationBatch copy = batch.snapshot();
        copy.setPlantCount(40);
        copy.setStage(CannabisCultivationStage.VEGETATIVE);
        assertThat(copy).isNotSameAs(batch);
        assertThat(batch.getPlantCount()).isZero();
        assertThat(batch.getStage()).isEqualTo(CannabisCultivationStage.PLANNED);
    }

    @Test
    void requiresUsableBatchDetailsAndChronologicalDates() {
        assertThatThrownBy(() -> new CannabisCultivationBatch(
                " ", "", "Room 1", 10, START, HARVEST,
                CannabisCultivationStage.PLANNED, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch name");
        assertThatThrownBy(() -> new CannabisCultivationBatch(
                "Autumn A", "", " ", 10, START, HARVEST,
                CannabisCultivationStage.PLANNED, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cultivation area");
        assertThatThrownBy(() -> new CannabisCultivationBatch(
                "Autumn A", "", "Room 1", 10, HARVEST, START,
                CannabisCultivationStage.PLANNED, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest date");
    }

    @Test
    void rejectsInvalidCountAndOversizedNotes() {
        assertThatThrownBy(() -> new CannabisCultivationBatch(
                "Autumn A", "", "Room 1", -1, START, HARVEST,
                CannabisCultivationStage.PLANNED, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plant count");
        assertThatThrownBy(() -> new CannabisCultivationBatch(
                "Autumn A", "", "Room 1", 1_000_001, START, HARVEST,
                CannabisCultivationStage.PLANNED, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plant count");
        assertThatThrownBy(() -> new CannabisCultivationBatch(
                "Autumn A", "", "Room 1", 10, START, HARVEST,
                CannabisCultivationStage.PLANNED, "x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Notes");
    }

    @Test
    void invalidSourceCannotPartiallyOverwriteAnExistingBatch() {
        CannabisCultivationBatch saved = new CannabisCultivationBatch(
                "Autumn A", "Cultivar A", "Room 1", 20,
                START, HARVEST, CannabisCultivationStage.VEGETATIVE, "Healthy");
        CannabisCultivationBatch incoming = new CannabisCultivationBatch(
                "Autumn B", "Cultivar B", "Room 2", 30,
                START, HARVEST, CannabisCultivationStage.FLOWERING, "Review");
        incoming.setPlannedHarvestDate(START.minusDays(1));

        assertThatThrownBy(() -> saved.copyBusinessFieldsFrom(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest date");
        assertThat(saved.getBatchName()).isEqualTo("Autumn A");
        assertThat(saved.getPlantCount()).isEqualTo(20);
        assertThat(saved.getStage()).isEqualTo(CannabisCultivationStage.VEGETATIVE);
    }
}
