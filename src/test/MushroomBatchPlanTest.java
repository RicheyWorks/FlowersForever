package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Mushroom batch plan")
class MushroomBatchPlanTest {

    private static final LocalDate INOCULATION = LocalDate.of(2027, 2, 10);

    @Test
    void trimsRequiredFieldsKeepsOptionalPlanningFieldsOpenAndSnapshotsIndependently() {
        MushroomBatchPlan plan = new MushroomBatchPlan(" Log set A ", " Shiitake ",
                null, null, null, null, null, null, null, null, null);

        assertThat(plan.getBatchName()).isEqualTo("Log set A");
        assertThat(plan.getMushroomSpecies()).isEqualTo("Shiitake");
        assertThat(plan.getGrowingMethod()).isEmpty();
        assertThat(plan.getSubstrate()).isEmpty();
        assertThat(plan.getGrowingSite()).isEmpty();
        assertThat(plan.getStage()).isEqualTo(MushroomBatchStage.PLANNED);
        assertThat(plan.getPlannedInoculationDate()).isNull();

        MushroomBatchPlan copy = plan.snapshot();
        copy.setBatchName("Log set B");
        assertThat(plan.getBatchName()).isEqualTo("Log set A");
    }

    @Test
    void requiresBatchAndSpecies() {
        assertThatThrownBy(() -> minimal(" ", "Shiitake"))
                .hasMessageContaining("Batch name");
        assertThatThrownBy(() -> minimal("Log set A", " "))
                .hasMessageContaining("Mushroom species");
    }

    @Test
    void guardsFruitingDateOrderAndTextLengths() {
        MushroomBatchPlan plan = minimal("Log set A", "Shiitake");
        plan.setPlannedInoculationDate(INOCULATION);
        plan.setPlannedFruitingStart(INOCULATION.minusDays(1));
        assertThatThrownBy(plan::validate)
                .hasMessageContaining("Planned fruiting start");

        plan.setPlannedFruitingStart(null);
        plan.setPlannedFruitingEnd(INOCULATION.plusMonths(6));
        assertThatThrownBy(plan::validate)
                .hasMessageContaining("requires a planned fruiting start");

        plan.setPlannedFruitingStart(INOCULATION.plusMonths(8));
        assertThatThrownBy(plan::validate)
                .hasMessageContaining("Planned fruiting end");

        plan.setPlannedFruitingEnd(INOCULATION.plusMonths(9));
        plan.validate();
        assertThatThrownBy(() -> plan.setGrowingMethod("x".repeat(121)))
                .hasMessageContaining("Growing method cannot exceed 120 characters");
        assertThatThrownBy(() -> plan.setNotes("x".repeat(2001)))
                .hasMessageContaining("Notes cannot exceed 2000 characters");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        MushroomBatchPlan stored = minimal("Log set A", "Shiitake");
        MushroomBatchPlan incoming = new MushroomBatchPlan("Bag room 2", "Oyster",
                "Bags", "Straw", "North room", INOCULATION,
                INOCULATION.plusDays(30), INOCULATION.plusDays(45),
                "Farm stand", MushroomBatchStage.INCUBATING, "First cycle");
        stored.copyBusinessFieldsFrom(incoming);
        assertThat(stored.getBatchName()).isEqualTo("Bag room 2");
        assertThat(stored.getSubstrate()).isEqualTo("Straw");
        assertThat(stored.getStage()).isEqualTo(MushroomBatchStage.INCUBATING);

        incoming.setPlannedFruitingEnd(INOCULATION.plusDays(20));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .hasMessageContaining("Planned fruiting end");
        assertThat(stored.getBatchName()).isEqualTo("Bag room 2");
    }

    private static MushroomBatchPlan minimal(String name, String species) {
        return new MushroomBatchPlan(name, species, null, null, null,
                null, null, null, null, null, null);
    }
}
