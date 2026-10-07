package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Christmas tree block plan")
class ChristmasTreeBlockPlanTest {

    @Test
    void trimsRequiredFieldsAndKeepsOptionalPlanningDetailsOpen() {
        ChristmasTreeBlockPlan plan = new ChristmasTreeBlockPlan(" North slope ",
                " Noble fir ", null, null, null, null, null, 2033,
                null, null, null, null, null);

        assertThat(plan.getBlockName()).isEqualTo("North slope");
        assertThat(plan.getTreeSpecies()).isEqualTo("Noble fir");
        assertThat(plan.getTargetHeight()).isEmpty();
        assertThat(plan.getIntendedChannel()).isEmpty();
        assertThat(plan.getStage()).isEqualTo(ChristmasTreeBlockStage.PLANNED);
        assertThat(plan.getTargetHarvestYear()).isEqualTo(2033);

        ChristmasTreeBlockPlan copy = plan.snapshot();
        copy.setBlockName("South slope");
        assertThat(plan.getBlockName()).isEqualTo("North slope");
    }

    @Test
    void requiresBlockSpeciesAndTargetYear() {
        assertThatThrownBy(() -> minimal(" ", "Noble fir", 2033))
                .hasMessageContaining("Block name");
        assertThatThrownBy(() -> minimal("North slope", " ", 2033))
                .hasMessageContaining("Tree species");
        assertThatThrownBy(() -> minimal("North slope", "Noble fir", null))
                .hasMessageContaining("required Christmas tree block details");
    }

    @Test
    void guardsYearAcreCountAndCutWindowWithoutRounding() {
        ChristmasTreeBlockPlan plan = minimal("North slope", "Noble fir", 2033);
        plan.setPlantingYear(2034);
        assertThatThrownBy(plan::validate).hasMessageContaining("Planting year");
        plan.setPlantingYear(2025);
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1.234")))
                .hasMessageContaining("two decimal places");
        assertThatThrownBy(() -> plan.setAcres(BigDecimal.ZERO))
                .hasMessageContaining("Acres must be positive");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("10000000000")))
                .hasMessageContaining("cannot exceed");
        plan.setAcres(new BigDecimal("1.20"));
        assertThat(plan.getAcres()).isEqualByComparingTo("1.20");
        plan.setPlantedTreeCount(0);
        assertThatThrownBy(plan::validate).hasMessageContaining("Planted tree count");
        plan.setPlantedTreeCount(400);
        plan.setPlannedCutEnd(LocalDate.of(2033, 12, 10));
        assertThatThrownBy(plan::validate).hasMessageContaining("requires a planned cut start");
        plan.setPlannedCutStart(LocalDate.of(2033, 12, 15));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned cut end");
        plan.setPlannedCutEnd(LocalDate.of(2033, 12, 20));
        plan.setNextShearingDate(LocalDate.of(2033, 12, 16));
        assertThatThrownBy(plan::validate).hasMessageContaining("Next shearing date");
        plan.setNextShearingDate(LocalDate.of(2033, 10, 1));
        plan.validate();
    }

    @Test
    void rejectsCutDateOutsideTargetYearAndOverlongText() {
        ChristmasTreeBlockPlan plan = minimal("North slope", "Noble fir", 2033);
        plan.setPlannedCutStart(LocalDate.of(2032, 12, 1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned cut start");
        plan.setPlannedCutStart(LocalDate.of(2033, 12, 1));
        plan.setPlannedCutEnd(LocalDate.of(2034, 1, 1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned cut end");
        assertThatThrownBy(() -> plan.setNotes("x".repeat(2001)))
                .hasMessageContaining("Notes cannot exceed 2000 characters");
    }

    @Test
    void rejectsShearingAfterTargetYearWithoutAPlannedCutDate() {
        ChristmasTreeBlockPlan plan = minimal("North slope", "Noble fir", 2033);
        plan.setNextShearingDate(LocalDate.of(2034, 1, 1));
        assertThatThrownBy(plan::validate)
                .hasMessageContaining("after the target harvest year");
    }

    @Test
    void copiesOnlyValidBusinessFields() {
        ChristmasTreeBlockPlan stored = minimal("North slope", "Noble fir", 2033);
        ChristmasTreeBlockPlan incoming = minimal("East block", "Douglas fir", 2035);
        incoming.setStage(ChristmasTreeBlockStage.GROWING);
        stored.copyBusinessFieldsFrom(incoming);
        assertThat(stored.getBlockName()).isEqualTo("East block");
        assertThat(stored.getStage()).isEqualTo(ChristmasTreeBlockStage.GROWING);

        incoming.setPlannedCutStart(LocalDate.of(2034, 12, 1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .hasMessageContaining("Planned cut start");
        assertThat(stored.getBlockName()).isEqualTo("East block");
    }

    private static ChristmasTreeBlockPlan minimal(String blockName, String species,
                                                   Integer harvestYear) {
        return new ChristmasTreeBlockPlan(blockName, species, null, null, null, null,
                null, harvestYear, null, null, null, null, null);
    }
}
