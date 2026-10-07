package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Berry block plan")
class BerryBlockPlanTest {

    private static final LocalDate PLANTING = LocalDate.of(2026, 3, 1);
    private static final LocalDate FIRST_PICK = LocalDate.of(2026, 6, 1);
    private static final LocalDate LAST_PICK = LocalDate.of(2026, 8, 1);

    @Test
    void normalizesOptionalDetailsAndKeepsSnapshotsDetached() {
        BerryBlockPlan plan = new BerryBlockPlan(" North rows ", " Blueberries ",
                null, new BigDecimal("2.500"), null, null, null, null, null, null);

        assertThat(plan.getBlockName()).isEqualTo("North rows");
        assertThat(plan.getCrop()).isEqualTo("Blueberries");
        assertThat(plan.getCultivar()).isEmpty();
        assertThat(plan.getAcres()).isEqualByComparingTo("2.50");
        assertThat(plan.getChannel()).isEqualTo(BerrySalesChannel.UNDECIDED);
        assertThat(plan.getStage()).isEqualTo(BerryBlockStage.PLANNED);
        assertThat(plan.getNotes()).isEmpty();

        BerryBlockPlan snapshot = plan.snapshot();
        snapshot.setBlockName("South rows");
        assertThat(snapshot).isNotSameAs(plan);
        assertThat(plan.getBlockName()).isEqualTo("North rows");
    }

    @Test
    void requiresBlockAndCropAndRejectsInvalidAcres() {
        assertThatThrownBy(() -> new BerryBlockPlan(" ", "Blueberries", "", null,
                null, null, null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Block name");
        assertThatThrownBy(() -> new BerryBlockPlan("North", " ", "", null,
                null, null, null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Berry crop");
        BerryBlockPlan plan = plan();
        assertThatThrownBy(() -> plan.setAcres(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1000000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
    }

    @Test
    void validatesEveryDatePairWhenPresent() {
        BerryBlockPlan plan = plan();
        plan.setFirstPickDate(PLANTING.minusDays(1));
        assertThatThrownBy(plan::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("First pick date");

        plan.setFirstPickDate(FIRST_PICK);
        plan.setLastPickDate(FIRST_PICK.minusDays(1));
        assertThatThrownBy(plan::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Last pick date");

        plan.setFirstPickDate(null);
        plan.setLastPickDate(PLANTING.minusDays(1));
        assertThatThrownBy(plan::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Last pick date");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        BerryBlockPlan stored = plan();
        BerryBlockPlan incoming = new BerryBlockPlan("West rows", "Raspberries",
                "Heritage", new BigDecimal("1.75"), null, null, null,
                BerrySalesChannel.U_PICK, BerryBlockStage.PICKING, "Weekend picking");
        stored.copyBusinessFieldsFrom(incoming);

        assertThat(stored.getBlockName()).isEqualTo("West rows");
        assertThat(stored.getCrop()).isEqualTo("Raspberries");
        assertThat(stored.getChannel()).isEqualTo(BerrySalesChannel.U_PICK);
        assertThat(stored.getStage()).isEqualTo(BerryBlockStage.PICKING);
        incoming.setPlantingDate(PLANTING);
        incoming.setLastPickDate(PLANTING.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Last pick date");
        assertThat(stored.getBlockName()).isEqualTo("West rows");
    }

    private static BerryBlockPlan plan() {
        return new BerryBlockPlan("North rows", "Blueberries", "Duke",
                new BigDecimal("2.50"), PLANTING, FIRST_PICK, LAST_PICK,
                BerrySalesChannel.FRESH, BerryBlockStage.GROWING, "");
    }
}
