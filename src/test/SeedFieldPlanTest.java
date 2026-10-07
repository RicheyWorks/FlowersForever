package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Seed field plan")
class SeedFieldPlanTest {

    private static final LocalDate HARVEST = LocalDate.of(2026, 8, 20);

    @Test
    void keepsOptionalSowingAndFloweringOpenForEstablishedStands() {
        SeedFieldPlan plan = new SeedFieldPlan(" West stand ", " Ryegrass ", " ",
                new BigDecimal("12.500"), null, null, null, null,
                HARVEST, null, null, null, null, null);

        assertThat(plan.getFieldName()).isEqualTo("West stand");
        assertThat(plan.getSeedCrop()).isEqualTo("Ryegrass");
        assertThat(plan.getVariety()).isEmpty();
        assertThat(plan.getAcres()).isEqualByComparingTo("12.50");
        assertThat(plan.getPreviousCrop()).isEmpty();
        assertThat(plan.getPlannedSowingDate()).isNull();
        assertThat(plan.getPlannedFloweringStart()).isNull();
        assertThat(plan.getStage()).isEqualTo(SeedFieldStage.PLANNED);
        assertThat(plan.getIsolationNotes()).isEmpty();

        SeedFieldPlan snapshot = plan.snapshot();
        snapshot.setFieldName("East stand");
        assertThat(plan.getFieldName()).isEqualTo("West stand");
    }

    @Test
    void requiresFieldCropAndHarvestStart() {
        assertThatThrownBy(() -> new SeedFieldPlan(" ", "Ryegrass", "", null,
                "", null, null, null, HARVEST, null, "", "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name");
        assertThatThrownBy(() -> new SeedFieldPlan("West stand", " ", "", null,
                "", null, null, null, HARVEST, null, "", "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Seed crop");
        assertThatThrownBy(() -> new SeedFieldPlan("West stand", "Ryegrass", "", null,
                "", null, null, null, null, null, "", "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required seed field details");
    }

    @Test
    void rejectsInvalidAcresAndOutOfOrderDates() {
        SeedFieldPlan plan = plan();
        assertThatThrownBy(() -> plan.setAcres(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1000000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
        plan.setPlannedSowingDate(HARVEST.plusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned sowing date");
        plan.setPlannedSowingDate(HARVEST.minusMonths(4));
        plan.setPlannedFloweringEnd(plan.getPlannedFloweringStart().minusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned flowering end");
        plan.setPlannedFloweringEnd(HARVEST.minusDays(10));
        plan.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned harvest end");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        SeedFieldPlan stored = plan();
        SeedFieldPlan incoming = new SeedFieldPlan("South plot", "Clover", "Red",
                new BigDecimal("5.75"), "Peas", LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10),
                HARVEST.plusDays(10), HARVEST.plusDays(20), "Cover crop seed",
                "North boundary", SeedFieldStage.FLOWERING, "Scout field");
        stored.copyBusinessFieldsFrom(incoming);

        assertThat(stored.getSeedCrop()).isEqualTo("Clover");
        assertThat(stored.getIntendedUse()).isEqualTo("Cover crop seed");
        assertThat(stored.getIsolationNotes()).isEqualTo("North boundary");
        assertThat(stored.getStage()).isEqualTo(SeedFieldStage.FLOWERING);

        incoming.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThat(stored.getSeedCrop()).isEqualTo("Clover");
    }

    private static SeedFieldPlan plan() {
        return new SeedFieldPlan("West stand", "Ryegrass", "Perennial",
                new BigDecimal("12.50"), "Wheat", LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10),
                HARVEST, HARVEST.plusDays(10), "Turf", "North boundary",
                SeedFieldStage.SOWN, "");
    }
}
