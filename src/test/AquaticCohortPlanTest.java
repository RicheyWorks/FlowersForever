package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Aquaculture cohort plan")
class AquaticCohortPlanTest {

    private static final LocalDate HARVEST = LocalDate.of(2027, 7, 20);

    @Test
    void trimsFieldsKeepsOptionalDetailsOpenAndSnapshotsIndependently() {
        AquaticCohortPlan plan = new AquaticCohortPlan(" Bay 3 ", " Pacific oyster ",
                " South lease ", null, null, HARVEST, null, " ", null, null);

        assertThat(plan.getCohortName()).isEqualTo("Bay 3");
        assertThat(plan.getSpecies()).isEqualTo("Pacific oyster");
        assertThat(plan.getGrowingSite()).isEqualTo("South lease");
        assertThat(plan.getCultureMethod()).isEmpty();
        assertThat(plan.getIntendedDestination()).isEmpty();
        assertThat(plan.getStage()).isEqualTo(AquaticCohortStage.PLANNED);

        AquaticCohortPlan snapshot = plan.snapshot();
        snapshot.setCohortName("Bay 4");
        assertThat(plan.getCohortName()).isEqualTo("Bay 3");
    }

    @Test
    void requiresCohortSpeciesSiteAndHarvestStart() {
        assertThatThrownBy(() -> new AquaticCohortPlan(" ", "Oyster", "Bay", "",
                null, HARVEST, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cohort name");
        assertThatThrownBy(() -> new AquaticCohortPlan("Bay 3", " ", "Bay", "",
                null, HARVEST, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Species");
        assertThatThrownBy(() -> new AquaticCohortPlan("Bay 3", "Oyster", " ", "",
                null, HARVEST, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Growing site");
        assertThatThrownBy(() -> new AquaticCohortPlan("Bay 3", "Oyster", "Bay", "",
                null, null, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required aquaculture cohort details");
    }

    @Test
    void rejectsInvalidDatesAndOverlongNotes() {
        AquaticCohortPlan plan = plan();
        plan.setPlannedStockingDate(HARVEST.plusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned stocking date");
        plan.setPlannedStockingDate(HARVEST.minusMonths(4));
        plan.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned harvest end");
        assertThatThrownBy(() -> plan.setNotes("x".repeat(2001)))
                .hasMessageContaining("Notes cannot exceed 2000 characters");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        AquaticCohortPlan stored = plan();
        AquaticCohortPlan incoming = new AquaticCohortPlan("Bay 4", "Kelp", "West line",
                "Longline", HARVEST.minusMonths(3), HARVEST.plusDays(10),
                HARVEST.plusDays(20), "Processor", AquaticCohortStage.GROWING_OUT,
                "Second cohort");
        stored.copyBusinessFieldsFrom(incoming);

        assertThat(stored.getCohortName()).isEqualTo("Bay 4");
        assertThat(stored.getCultureMethod()).isEqualTo("Longline");
        assertThat(stored.getStage()).isEqualTo(AquaticCohortStage.GROWING_OUT);

        incoming.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .hasMessageContaining("Planned harvest end");
        assertThat(stored.getCohortName()).isEqualTo("Bay 4");
    }

    private static AquaticCohortPlan plan() {
        return new AquaticCohortPlan("Bay 3", "Pacific oyster", "South lease",
                "Bottom culture", HARVEST.minusMonths(6), HARVEST,
                HARVEST.plusDays(14), "Wholesale", AquaticCohortStage.GROWING_OUT, "");
    }
}
