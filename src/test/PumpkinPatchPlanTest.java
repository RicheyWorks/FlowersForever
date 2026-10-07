package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pumpkin patch plan")
class PumpkinPatchPlanTest {

    @Test
    void trimsPatchNameKeepsOptionalFieldsOpenAndSnapshotsIndependently() {
        PumpkinPatchPlan plan = new PumpkinPatchPlan(" North patch ", null,
                null, null, null, null, null, null, null, null);

        assertThat(plan.getPatchName()).isEqualTo("North patch");
        assertThat(plan.getVariety()).isEmpty();
        assertThat(plan.getVisitorAreaNotes()).isEmpty();
        assertThat(plan.getSalesMode()).isEqualTo(PumpkinSalesMode.UNDECIDED);
        assertThat(plan.getStage()).isEqualTo(PumpkinPatchStage.PLANNED);

        PumpkinPatchPlan copy = plan.snapshot();
        copy.setPatchName("South patch");
        assertThat(plan.getPatchName()).isEqualTo("North patch");
    }

    @Test
    void requiresPatchNameAndGuardsAcreageWithoutRounding() {
        assertThatThrownBy(() -> minimal(" "))
                .hasMessageContaining("Patch name");
        PumpkinPatchPlan plan = minimal("North patch");
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
    void guardsPlantingAndPickingDatesAndNoteLengths() {
        PumpkinPatchPlan plan = minimal("North patch");
        plan.setPlannedPlantingDate(LocalDate.of(2027, 5, 15));
        plan.setPlannedPickingStart(LocalDate.of(2027, 5, 14));
        assertThatThrownBy(plan::validate)
                .hasMessageContaining("Planned picking start");

        plan.setPlannedPickingStart(null);
        plan.setPlannedPickingEnd(LocalDate.of(2027, 10, 20));
        assertThatThrownBy(plan::validate)
                .hasMessageContaining("requires a planned picking start");

        plan.setPlannedPickingStart(LocalDate.of(2027, 10, 21));
        assertThatThrownBy(plan::validate)
                .hasMessageContaining("Planned picking end");
        plan.setPlannedPickingEnd(LocalDate.of(2027, 10, 22));
        plan.validate();
        assertThatThrownBy(() -> plan.setVisitorAreaNotes("x".repeat(2001)))
                .hasMessageContaining("Visitor area notes cannot exceed 2000 characters");
        assertThatThrownBy(() -> plan.setNotes("x".repeat(2001)))
                .hasMessageContaining("Notes cannot exceed 2000 characters");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        PumpkinPatchPlan stored = minimal("North patch");
        PumpkinPatchPlan incoming = new PumpkinPatchPlan("South patch", "Jack O Lantern",
                new BigDecimal("2.50"), LocalDate.of(2027, 5, 15),
                LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 20),
                PumpkinSalesMode.PICK_YOUR_OWN, "East entrance",
                PumpkinPatchStage.GROWING, "Fall plan");
        stored.copyBusinessFieldsFrom(incoming);
        assertThat(stored.getPatchName()).isEqualTo("South patch");
        assertThat(stored.getSalesMode()).isEqualTo(PumpkinSalesMode.PICK_YOUR_OWN);
        assertThat(stored.getVisitorAreaNotes()).isEqualTo("East entrance");

        incoming.setPlannedPickingEnd(LocalDate.of(2027, 9, 30));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .hasMessageContaining("Planned picking end");
        assertThat(stored.getPatchName()).isEqualTo("South patch");
    }

    private static PumpkinPatchPlan minimal(String patchName) {
        return new PumpkinPatchPlan(patchName, null, null, null,
                null, null, null, null, null, null);
    }
}
