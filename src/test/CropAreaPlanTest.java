package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Crop area plan")
class CropAreaPlanTest {

    private static final LocalDate PLANTING = LocalDate.of(2026, 4, 1);
    private static final LocalDate HARVEST = LocalDate.of(2026, 10, 1);

    @Test
    void normalizesOptionalDetailsAndKeepsSnapshotsDetached() {
        CropAreaPlan plan = new CropAreaPlan(FarmWorkspace.POTATO_FARM,
                " North pivot ", null, new BigDecimal("17.500"),
                null, null, null, null);

        assertThat(plan.getAreaName()).isEqualTo("North pivot");
        assertThat(plan.getVariety()).isEmpty();
        assertThat(plan.getAcres()).isEqualByComparingTo("17.50");
        assertThat(plan.getPlantingDate()).isNull();
        assertThat(plan.getStage()).isEqualTo(CropAreaStage.PLANNED);
        assertThat(plan.getNotes()).isEmpty();

        CropAreaPlan copy = plan.snapshot();
        copy.setAreaName("South pivot");
        assertThat(copy).isNotSameAs(plan);
        assertThat(plan.getAreaName()).isEqualTo("North pivot");
    }

    @Test
    void requiresSupportedWorkspaceAndAreaName() {
        assertThatThrownBy(() -> plan(FarmWorkspace.CUSTOM_FARM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("potato farm or apple orchard");
        assertThatThrownBy(() -> new CropAreaPlan(FarmWorkspace.APPLE_ORCHARD,
                " ", "Honeycrisp", null, PLANTING, HARVEST,
                CropAreaStage.GROWING, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Area name");
    }

    @Test
    void rejectsInvalidAcresAndChronology() {
        CropAreaPlan plan = plan(FarmWorkspace.POTATO_FARM);
        assertThatThrownBy(() -> plan.setAcres(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1000000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> plan.setAcres(new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
        plan.setTargetHarvestDate(PLANTING.minusDays(1));
        assertThatThrownBy(plan::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target harvest date");
    }

    @Test
    void crossWorkspaceEditFailsBeforeMutatingStoredPlan() {
        CropAreaPlan potato = plan(FarmWorkspace.POTATO_FARM);
        CropAreaPlan apple = plan(FarmWorkspace.APPLE_ORCHARD);
        apple.setAreaName("West orchard");

        assertThatThrownBy(() -> potato.copyBusinessFieldsFrom(apple))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot move between farm workspaces");
        assertThat(potato.getAreaName()).isEqualTo("North pivot");
        assertThat(potato.getWorkspace()).isEqualTo(FarmWorkspace.POTATO_FARM);
    }

    private static CropAreaPlan plan(FarmWorkspace workspace) {
        return new CropAreaPlan(workspace, "North pivot", "Russet",
                new BigDecimal("18.25"), PLANTING, HARVEST,
                CropAreaStage.GROWING, "");
    }
}
