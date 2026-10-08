package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Vineyard block plan")
class VineyardBlockPlanTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 15);

    @Test
    void normalizesOptionalDetailsAndKeepsSnapshotsDetached() {
        VineyardBlockPlan plan = new VineyardBlockPlan(" North slope ", null,
                " Pinot noir ", new BigDecimal("2.500"), null, null,
                null, null, null);

        assertThat(plan.getBlockName()).isEqualTo("North slope");
        assertThat(plan.getLocation()).isEmpty();
        assertThat(plan.getGrapeVariety()).isEqualTo("Pinot noir");
        assertThat(plan.getAcres()).isEqualByComparingTo("2.50");
        assertThat(plan.getDestination()).isEqualTo(VineyardDestination.UNDECIDED);
        assertThat(plan.getStage()).isEqualTo(VineyardBlockStage.PLANNED);
        assertThat(plan.getNotes()).isEmpty();

        VineyardBlockPlan snapshot = plan.snapshot();
        snapshot.setBlockName("South slope");
        assertThat(snapshot).isNotSameAs(plan);
        assertThat(plan.getBlockName()).isEqualTo("North slope");
    }

    @Test
    void requiresBlockAndGrapeAndRejectsInvalidAcres() {
        assertThatThrownBy(() -> new VineyardBlockPlan(" ", "", "Pinot noir", null,
                null, null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Block name");
        assertThatThrownBy(() -> new VineyardBlockPlan("North", "", " ", null,
                null, null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Grape variety");
        VineyardBlockPlan plan = plan();
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
    void validatesHarvestWindowWhenBothDatesPresent() {
        VineyardBlockPlan plan = plan();
        plan.setPlannedHarvestEnd(START.minusDays(1));
        assertThatThrownBy(plan::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");

        plan.setPlannedHarvestStart(null);
        plan.validate();
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        VineyardBlockPlan stored = plan();
        VineyardBlockPlan incoming = new VineyardBlockPlan("West bench", "Block C",
                "Chardonnay", new BigDecimal("1.75"), START, END,
                VineyardDestination.ESTATE_WINERY, VineyardBlockStage.HARVEST_WINDOW,
                "Check sugars weekly");
        stored.copyBusinessFieldsFrom(incoming);

        assertThat(stored.getBlockName()).isEqualTo("West bench");
        assertThat(stored.getLocation()).isEqualTo("Block C");
        assertThat(stored.getGrapeVariety()).isEqualTo("Chardonnay");
        assertThat(stored.getDestination()).isEqualTo(VineyardDestination.ESTATE_WINERY);
        assertThat(stored.getStage()).isEqualTo(VineyardBlockStage.HARVEST_WINDOW);

        incoming.setPlannedHarvestEnd(START.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThat(stored.getBlockName()).isEqualTo("West bench");
    }

    private static VineyardBlockPlan plan() {
        return new VineyardBlockPlan("North slope", "West bench", "Pinot noir",
                new BigDecimal("2.50"), START, END, VineyardDestination.WINERY_BUYER,
                VineyardBlockStage.GROWING, "");
    }
}
