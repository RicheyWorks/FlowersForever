package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Nursery growing batch")
class NurseryBatchTest {

    @Test
    void normalizesOptionalFieldsAndKeepsSnapshotsDetached() {
        NurseryBatch batch = new NurseryBatch(" Spring starts ", " Dahlias ",
                null, null, null, null, null, null, null, null, null);

        assertThat(batch.getBatchName()).isEqualTo("Spring starts");
        assertThat(batch.getPlantName()).isEqualTo("Dahlias");
        assertThat(batch.getCultivar()).isEmpty();
        assertThat(batch.getGrowingArea()).isEmpty();
        assertThat(batch.getNotes()).isEmpty();
        assertThat(batch.getForm()).isEqualTo(NurseryPlantForm.UNSPECIFIED);
        assertThat(batch.getChannel()).isEqualTo(NurserySalesChannel.UNDECIDED);
        assertThat(batch.getStage()).isEqualTo(NurseryBatchStage.PLANNED);

        NurseryBatch snapshot = batch.snapshot();
        snapshot.setBatchName("Autumn starts");
        assertThat(batch.getBatchName()).isEqualTo("Spring starts");
    }

    @Test
    void requiresNamesAndLimitsStartingPlantCount() {
        assertThatThrownBy(() -> new NurseryBatch(" ", "Dahlias", "", null,
                "", null, null, null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch name");
        assertThatThrownBy(() -> new NurseryBatch("Spring", " ", "", null,
                "", null, null, null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plant name");
        NurseryBatch batch = batch();
        assertThatThrownBy(() -> batch.setStartingCount(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Starting plant count");
        assertThatThrownBy(() -> batch.setStartingCount(1_000_001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Starting plant count");
    }

    @Test
    void rejectsReadyDateBeforeStartingDateAndInvalidIncomingCopy() {
        NurseryBatch batch = batch();
        batch.setPlannedReadyDate(LocalDate.of(2026, 2, 28));
        assertThatThrownBy(batch::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned ready date");

        NurseryBatch stored = batch();
        NurseryBatch incoming = batch();
        incoming.setPlantName("Roses");
        stored.copyBusinessFieldsFrom(incoming);
        assertThat(stored.getPlantName()).isEqualTo("Roses");
        incoming.setPlannedReadyDate(LocalDate.of(2026, 2, 28));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned ready date");
        assertThat(stored.getStage()).isEqualTo(NurseryBatchStage.GROWING);
    }

    private static NurseryBatch batch() {
        return new NurseryBatch("Spring starts", "Dahlias", "Cafe au Lait",
                NurseryPlantForm.STARTS_OR_PLUGS, "Greenhouse 2",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 1), 120,
                NurserySalesChannel.RETAIL, NurseryBatchStage.GROWING, "");
    }
}
