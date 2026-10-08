package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Vegetable planting plan")
class VegetablePlantingPlanTest {

    private static final LocalDate HARVEST = LocalDate.of(2026, 9, 20);

    @Test
    void trimsFieldsKeepsOptionalDetailsOpenAndSnapshotsIndependently() {
        VegetablePlantingPlan plan = new VegetablePlantingPlan(" Bed 3 ", " Lettuce ",
                null, " ", new BigDecimal("0.500"), 1, null, null, HARVEST,
                null, null, null, null);

        assertThat(plan.getFieldOrBed()).isEqualTo("Bed 3");
        assertThat(plan.getVegetableCrop()).isEqualTo("Lettuce");
        assertThat(plan.getVariety()).isEmpty();
        assertThat(plan.getPreviousCrop()).isEmpty();
        assertThat(plan.getAcres()).isEqualByComparingTo("0.50");
        assertThat(plan.getSuccessionNumber()).isEqualTo(1);
        assertThat(plan.getStage()).isEqualTo(VegetablePlantingStage.PLANNED);

        VegetablePlantingPlan snapshot = plan.snapshot();
        snapshot.setFieldOrBed("Bed 4");
        assertThat(plan.getFieldOrBed()).isEqualTo("Bed 3");
    }

    @Test
    void requiresFieldCropPositiveSuccessionAndHarvestStart() {
        assertThatThrownBy(() -> new VegetablePlantingPlan(" ", "Lettuce", "", "",
                null, 1, null, null, HARVEST, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field or bed");
        assertThatThrownBy(() -> new VegetablePlantingPlan("Bed 3", " ", "", "",
                null, 1, null, null, HARVEST, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vegetable crop");
        assertThatThrownBy(() -> new VegetablePlantingPlan("Bed 3", "Lettuce", "", "",
                null, 0, null, null, HARVEST, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Succession number");
        assertThatThrownBy(() -> new VegetablePlantingPlan("Bed 3", "Lettuce", "", "",
                null, 1, null, null, null, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required vegetable planting details");
    }

    @Test
    void rejectsInvalidAcresAndDateOrder() {
        VegetablePlantingPlan plan = plan();
        assertThatThrownBy(() -> plan.setAcres(BigDecimal.ZERO))
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1000000.01")))
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1.001")))
                .hasMessageContaining("two decimal places");

        plan.setPlannedSowingDate(HARVEST.plusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned sowing date");
        plan.setPlannedSowingDate(HARVEST.minusDays(10));
        plan.setPlannedTransplantDate(HARVEST.minusDays(11));
        assertThatThrownBy(plan::validate).hasMessageContaining("planned transplant date");
        plan.setPlannedTransplantDate(HARVEST.plusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned transplant date");
        plan.setPlannedTransplantDate(HARVEST.minusDays(2));
        plan.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned harvest end");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        VegetablePlantingPlan stored = plan();
        VegetablePlantingPlan incoming = new VegetablePlantingPlan("Bed 4", "Carrot",
                "Nantes", "Peas", new BigDecimal("1.75"), 2,
                HARVEST.minusMonths(3), null, HARVEST.plusDays(10),
                HARVEST.plusDays(20), "Farm stand", VegetablePlantingStage.GROWING,
                "Second planting");
        stored.copyBusinessFieldsFrom(incoming);

        assertThat(stored.getFieldOrBed()).isEqualTo("Bed 4");
        assertThat(stored.getPreviousCrop()).isEqualTo("Peas");
        assertThat(stored.getSuccessionNumber()).isEqualTo(2);
        assertThat(stored.getStage()).isEqualTo(VegetablePlantingStage.GROWING);

        incoming.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .hasMessageContaining("Planned harvest end");
        assertThat(stored.getFieldOrBed()).isEqualTo("Bed 4");
    }

    private static VegetablePlantingPlan plan() {
        return new VegetablePlantingPlan("Bed 3", "Lettuce", "Buttercrunch", "Beans",
                new BigDecimal("0.50"), 1, HARVEST.minusMonths(2),
                HARVEST.minusMonths(1), HARVEST, HARVEST.plusDays(7),
                "CSA", VegetablePlantingStage.GROWING, "");
    }
}
