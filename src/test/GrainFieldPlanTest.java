package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Grain field plan")
class GrainFieldPlanTest {

    private static final LocalDate HARVEST = LocalDate.of(2026, 8, 20);

    @Test
    void normalizesOptionalDetailsAndKeepsSnapshotsDetached() {
        GrainFieldPlan plan = new GrainFieldPlan(" North field ", " Wheat ", " ",
                new BigDecimal("12.500"), null, null, HARVEST, null, null, null);

        assertThat(plan.getFieldName()).isEqualTo("North field");
        assertThat(plan.getGrainCrop()).isEqualTo("Wheat");
        assertThat(plan.getVariety()).isEmpty();
        assertThat(plan.getAcres()).isEqualByComparingTo("12.50");
        assertThat(plan.getPreviousCrop()).isEmpty();
        assertThat(plan.getPlannedSeedingDate()).isNull();
        assertThat(plan.getPlannedHarvestEnd()).isNull();
        assertThat(plan.getStage()).isEqualTo(GrainFieldStage.PLANNED);
        assertThat(plan.getNotes()).isEmpty();

        GrainFieldPlan snapshot = plan.snapshot();
        snapshot.setFieldName("South field");
        assertThat(snapshot).isNotSameAs(plan);
        assertThat(plan.getFieldName()).isEqualTo("North field");
    }

    @Test
    void requiresFieldCropAndHarvestStart() {
        assertThatThrownBy(() -> new GrainFieldPlan(" ", "Wheat", "", null,
                "", null, HARVEST, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name");
        assertThatThrownBy(() -> new GrainFieldPlan("North field", " ", "", null,
                "", null, HARVEST, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Grain crop");
        assertThatThrownBy(() -> new GrainFieldPlan("North field", "Wheat", "", null,
                "", null, null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required grain field details");
    }

    @Test
    void rejectsInvalidAcresAndOutOfOrderDates() {
        GrainFieldPlan plan = plan();
        assertThatThrownBy(() -> plan.setAcres(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1000000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
        plan.setPlannedSeedingDate(HARVEST.plusDays(1));
        assertThatThrownBy(plan::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned seeding date");
        plan.setPlannedSeedingDate(HARVEST.minusDays(1));
        plan.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(plan::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        GrainFieldPlan stored = plan();
        GrainFieldPlan incoming = new GrainFieldPlan("South field", "Barley", "Harrington",
                new BigDecimal("5.75"), "Peas", LocalDate.of(2026, 3, 1),
                HARVEST.plusDays(10), HARVEST.plusDays(20),
                GrainFieldStage.GROWING, "Scout field");
        stored.copyBusinessFieldsFrom(incoming);

        assertThat(stored.getFieldName()).isEqualTo("South field");
        assertThat(stored.getGrainCrop()).isEqualTo("Barley");
        assertThat(stored.getVariety()).isEqualTo("Harrington");
        assertThat(stored.getPreviousCrop()).isEqualTo("Peas");
        assertThat(stored.getStage()).isEqualTo(GrainFieldStage.GROWING);

        incoming.setPlannedHarvestEnd(HARVEST.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned harvest end");
        assertThat(stored.getFieldName()).isEqualTo("South field");
    }

    private static GrainFieldPlan plan() {
        return new GrainFieldPlan("North field", "Wheat", "Soft white",
                new BigDecimal("12.50"), "Canola", LocalDate.of(2026, 3, 1),
                HARVEST, HARVEST.plusDays(10), GrainFieldStage.SEEDED, "");
    }
}
