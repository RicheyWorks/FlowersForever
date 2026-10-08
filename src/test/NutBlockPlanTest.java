package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Tree nut block plan")
class NutBlockPlanTest {

    private static final LocalDate HARVEST = LocalDate.of(2026, 9, 20);

    @Test
    void keepsOptionalDetailsOpenAndReturnsIndependentSnapshot() {
        NutBlockPlan plan = new NutBlockPlan(" North block ", " Hazelnut ", null,
                " ", new BigDecimal("12.500"), 500, null, null,
                HARVEST, null, null, null, null);

        assertThat(plan.getBlockName()).isEqualTo("North block");
        assertThat(plan.getNutCrop()).isEqualTo("Hazelnut");
        assertThat(plan.getVariety()).isEmpty();
        assertThat(plan.getPollinizerVariety()).isEmpty();
        assertThat(plan.getAcres()).isEqualByComparingTo("12.50");
        assertThat(plan.getTreeCount()).isEqualTo(500);
        assertThat(plan.getStage()).isEqualTo(NutBlockStage.PLANNED);

        NutBlockPlan snapshot = plan.snapshot();
        snapshot.setBlockName("East block");
        assertThat(plan.getBlockName()).isEqualTo("North block");
    }

    @Test
    void requiresBlockCropAndHarvestStart() {
        assertThatThrownBy(() -> new NutBlockPlan(" ", "Hazelnut", "", "",
                null, null, null, null, HARVEST, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Block name");
        assertThatThrownBy(() -> new NutBlockPlan("North block", " ", "", "",
                null, null, null, null, HARVEST, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nut crop");
        assertThatThrownBy(() -> new NutBlockPlan("North block", "Hazelnut", "", "",
                null, null, null, null, null, null, "", null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required tree nut block details");
    }

    @Test
    void rejectsInvalidAcresTreeCountAndDateOrder() {
        NutBlockPlan plan = plan();
        assertThatThrownBy(() -> plan.setAcres(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1000000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
        assertThatThrownBy(() -> plan.setTreeCount(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tree count");

        plan.setPlannedBloomStart(HARVEST.plusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned bloom start");
        plan.setPlannedBloomStart(HARVEST.minusMonths(7));
        plan.setPlannedFloorPrepDate(HARVEST.plusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned floor prep date");
        plan.setPlannedFloorPrepDate(HARVEST.minusDays(10));
        plan.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(plan::validate).hasMessageContaining("Planned harvest end");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        NutBlockPlan stored = plan();
        NutBlockPlan incoming = new NutBlockPlan("South block", "Walnut", "Chandler",
                "Franquette", new BigDecimal("5.75"), 120,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 1),
                HARVEST.plusDays(10), HARVEST.plusDays(20), "Farm stand",
                NutBlockStage.FLOOR_PREP, "Sweep before pickup");
        stored.copyBusinessFieldsFrom(incoming);

        assertThat(stored.getNutCrop()).isEqualTo("Walnut");
        assertThat(stored.getPollinizerVariety()).isEqualTo("Franquette");
        assertThat(stored.getTreeCount()).isEqualTo(120);
        assertThat(stored.getStage()).isEqualTo(NutBlockStage.FLOOR_PREP);

        incoming.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThat(stored.getNutCrop()).isEqualTo("Walnut");
    }

    private static NutBlockPlan plan() {
        return new NutBlockPlan("North block", "Hazelnut", "Jefferson", "Eta",
                new BigDecimal("12.50"), 500, LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 8, 1), HARVEST, HARVEST.plusDays(10),
                "Processor", NutBlockStage.NUT_SET, "");
    }
}
