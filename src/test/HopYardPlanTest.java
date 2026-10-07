package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Hop yard plan")
class HopYardPlanTest {

    private static final LocalDate HARVEST = LocalDate.of(2026, 8, 20);

    @Test
    void normalizesOptionalDetailsAndKeepsSnapshotsDetached() {
        HopYardPlan plan = new HopYardPlan(" North yard ", " Cascade ",
                new BigDecimal("12.500"), null, HARVEST, null, null, null);

        assertThat(plan.getYardName()).isEqualTo("North yard");
        assertThat(plan.getHopVariety()).isEqualTo("Cascade");
        assertThat(plan.getAcres()).isEqualByComparingTo("12.50");
        assertThat(plan.getPlannedStringingDate()).isNull();
        assertThat(plan.getPlannedHarvestEnd()).isNull();
        assertThat(plan.getStage()).isEqualTo(HopYardStage.PLANNED);
        assertThat(plan.getNotes()).isEmpty();

        HopYardPlan snapshot = plan.snapshot();
        snapshot.setYardName("South yard");
        assertThat(snapshot).isNotSameAs(plan);
        assertThat(plan.getYardName()).isEqualTo("North yard");
    }

    @Test
    void requiresYardVarietyAndHarvestStart() {
        assertThatThrownBy(() -> new HopYardPlan(" ", "Cascade", null,
                null, HARVEST, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Yard name");
        assertThatThrownBy(() -> new HopYardPlan("North yard", " ", null,
                null, HARVEST, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hop variety");
        assertThatThrownBy(() -> new HopYardPlan("North yard", "Cascade", null,
                null, null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required hop yard details");
    }

    @Test
    void rejectsInvalidAcresAndOutOfOrderDates() {
        HopYardPlan plan = plan();
        assertThatThrownBy(() -> plan.setAcres(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1000000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
        plan.setPlannedStringingDate(HARVEST.plusDays(1));
        assertThatThrownBy(plan::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned stringing date");
        plan.setPlannedStringingDate(HARVEST.minusDays(1));
        plan.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(plan::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        HopYardPlan stored = plan();
        HopYardPlan incoming = new HopYardPlan("South yard", "Citra",
                new BigDecimal("5.75"), LocalDate.of(2026, 3, 1),
                HARVEST.plusDays(10), HARVEST.plusDays(20),
                HopYardStage.TRAINING, "Inspect trellis");
        stored.copyBusinessFieldsFrom(incoming);

        assertThat(stored.getYardName()).isEqualTo("South yard");
        assertThat(stored.getHopVariety()).isEqualTo("Citra");
        assertThat(stored.getStage()).isEqualTo(HopYardStage.TRAINING);
        assertThat(stored.getPlannedStringingDate()).isEqualTo(LocalDate.of(2026, 3, 1));

        incoming.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThat(stored.getYardName()).isEqualTo("South yard");
    }

    private static HopYardPlan plan() {
        return new HopYardPlan("North yard", "Cascade", new BigDecimal("12.50"),
                LocalDate.of(2026, 3, 1), HARVEST, HARVEST.plusDays(10),
                HopYardStage.STRINGING, "");
    }
}
