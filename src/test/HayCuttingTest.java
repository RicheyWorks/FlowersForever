package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Hay cutting plan")
class HayCuttingTest {

    private static final LocalDate CUT = LocalDate.of(2026, 6, 1);
    private static final LocalDate BALE = LocalDate.of(2026, 6, 5);

    @Test
    void normalizesOptionalDetailsAndKeepsSnapshotsDetached() {
        HayCutting cutting = new HayCutting(" North field ", " Alfalfa ", 2,
                new BigDecimal("12.500"), CUT, null, null, null);

        assertThat(cutting.getFieldName()).isEqualTo("North field");
        assertThat(cutting.getForageCrop()).isEqualTo("Alfalfa");
        assertThat(cutting.getCuttingNumber()).isEqualTo(2);
        assertThat(cutting.getAcres()).isEqualByComparingTo("12.50");
        assertThat(cutting.getPlannedBaleDate()).isNull();
        assertThat(cutting.getStage()).isEqualTo(HayCuttingStage.PLANNED);
        assertThat(cutting.getNotes()).isEmpty();

        HayCutting snapshot = cutting.snapshot();
        snapshot.setFieldName("South field");
        assertThat(snapshot).isNotSameAs(cutting);
        assertThat(cutting.getFieldName()).isEqualTo("North field");
    }

    @Test
    void requiresFieldCropCuttingNumberAndCutDate() {
        assertThatThrownBy(() -> new HayCutting(" ", "Alfalfa", 1, null,
                CUT, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name");
        assertThatThrownBy(() -> new HayCutting("North field", " ", 1, null,
                CUT, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Forage crop");
        assertThatThrownBy(() -> new HayCutting("North field", "Alfalfa", 0, null,
                CUT, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cutting number");
        assertThatThrownBy(() -> new HayCutting("North field", "Alfalfa", 100, null,
                CUT, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cutting number");
        assertThatThrownBy(() -> new HayCutting("North field", "Alfalfa", 1, null,
                null, null, null, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required hay cutting details");
    }

    @Test
    void rejectsInvalidAcresAndBaleDateBeforeCutDate() {
        HayCutting cutting = plan();
        assertThatThrownBy(() -> cutting.setAcres(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> cutting.setAcres(new BigDecimal("1000000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acres");
        assertThatThrownBy(() -> cutting.setAcres(new BigDecimal("1.001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
        cutting.setPlannedBaleDate(CUT.minusDays(1));
        assertThatThrownBy(cutting::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned bale date");
    }

    @Test
    void copiesOnlyValidatedBusinessFields() {
        HayCutting stored = plan();
        HayCutting incoming = new HayCutting("South field", "Timothy", 3,
                new BigDecimal("5.75"), CUT.plusDays(10), BALE.plusDays(10),
                HayCuttingStage.CURING, "Rake before baling");
        stored.copyBusinessFieldsFrom(incoming);

        assertThat(stored.getFieldName()).isEqualTo("South field");
        assertThat(stored.getForageCrop()).isEqualTo("Timothy");
        assertThat(stored.getCuttingNumber()).isEqualTo(3);
        assertThat(stored.getStage()).isEqualTo(HayCuttingStage.CURING);

        incoming.setPlannedBaleDate(CUT.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Planned bale date");
        assertThat(stored.getFieldName()).isEqualTo("South field");
    }

    private static HayCutting plan() {
        return new HayCutting("North field", "Alfalfa", 2, new BigDecimal("12.50"),
                CUT, BALE, HayCuttingStage.CUT, "");
    }
}
