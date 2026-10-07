package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Cranberry bed plan")
class CranberryBedPlanTest {

    @Test
    void trimsBedNameAndKeepsOptionalFieldsOpen() {
        CranberryBedPlan plan = new CranberryBedPlan(" North bed ", null, null,
                null, null, null, null, null, null);

        assertThat(plan.getBedName()).isEqualTo("North bed");
        assertThat(plan.getVariety()).isEmpty();
        assertThat(plan.getIntendedChannel()).isEmpty();
        assertThat(plan.getHarvestMethod()).isEqualTo(CranberryHarvestMethod.UNDECIDED);
        assertThat(plan.getStage()).isEqualTo(CranberryBedStage.PLANNED);

        CranberryBedPlan copy = plan.snapshot();
        copy.setBedName("South bed");
        assertThat(plan.getBedName()).isEqualTo("North bed");
    }

    @Test
    void requiresNamedBedAndGuardsAcreageWithoutRounding() {
        assertThatThrownBy(() -> minimal(" "))
                .hasMessageContaining("Bed name");
        CranberryBedPlan plan = minimal("North bed");
        assertThatThrownBy(() -> plan.setAcres(BigDecimal.ZERO))
                .hasMessageContaining("Acres must be positive");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1.234")))
                .hasMessageContaining("two decimal places");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("10000000000")))
                .hasMessageContaining("cannot exceed");
        plan.setAcres(new BigDecimal("1.20"));
        assertThat(plan.getAcres()).isEqualByComparingTo("1.20");
    }

    @Test
    void guardsPlannedHarvestWindowAndTextLength() {
        CranberryBedPlan plan = minimal("North bed");
        plan.setPlannedHarvestEnd(LocalDate.of(2027, 10, 20));
        assertThatThrownBy(plan::validate)
                .hasMessageContaining("requires a planned harvest start");
        plan.setPlannedHarvestStart(LocalDate.of(2027, 10, 21));
        assertThatThrownBy(plan::validate)
                .hasMessageContaining("Planned harvest end");
        plan.setPlannedHarvestEnd(LocalDate.of(2027, 10, 22));
        plan.validate();
        assertThatThrownBy(() -> plan.setVariety("x".repeat(121)))
                .hasMessageContaining("Variety cannot exceed 120 characters");
        assertThatThrownBy(() -> plan.setNotes("x".repeat(2001)))
                .hasMessageContaining("Notes cannot exceed 2000 characters");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        CranberryBedPlan stored = minimal("North bed");
        CranberryBedPlan incoming = new CranberryBedPlan("South bed", "Stevens",
                new BigDecimal("2.50"), CranberryHarvestMethod.WET,
                LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 20),
                "Processor", CranberryBedStage.HARVEST_PREP, "Fall plan");
        stored.copyBusinessFieldsFrom(incoming);
        assertThat(stored.getBedName()).isEqualTo("South bed");
        assertThat(stored.getHarvestMethod()).isEqualTo(CranberryHarvestMethod.WET);
        assertThat(stored.getStage()).isEqualTo(CranberryBedStage.HARVEST_PREP);

        incoming.setPlannedHarvestEnd(LocalDate.of(2027, 9, 30));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .hasMessageContaining("Planned harvest end");
        assertThat(stored.getBedName()).isEqualTo("South bed");
    }

    private static CranberryBedPlan minimal(String bedName) {
        return new CranberryBedPlan(bedName, null, null, null,
                null, null, null, null, null);
    }
}
