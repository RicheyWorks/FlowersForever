package com.flowerfarm.telemetry;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualFarmReadingTest {
    @Test void humanNamesRemainExactAndAdapterNamesAreNeverReinterpreted() {
        for (String name : new String[] {"North greenhouse probe", "Équipement 温室", "x".repeat(84)}) {
            var row = ManualFarmReading.draft("key", name, "North bed", "soil.moisture", "%", "0", "GOOD", Instant.EPOCH);
            String id = (String) row.get("deviceId");
            assertThat(id).hasSizeLessThanOrEqualTo(120).matches("[A-Za-z0-9][A-Za-z0-9._:/-]*");
            var saved = new FarmReading(1, ManualFarmReading.SOURCE, "key", id, "soil.moisture", "%",
                    BigDecimal.ZERO, "GOOD", Instant.EPOCH, Instant.EPOCH, "North bed", "");
            assertThat(ManualFarmReading.equipmentName(saved)).isEqualTo(name);
            var adapter = new FarmReading(1, "bridge", "key", id, "soil.moisture", "%",
                    BigDecimal.ZERO, "GOOD", Instant.EPOCH, Instant.EPOCH, "North bed", "");
            assertThat(ManualFarmReading.equipmentName(adapter)).isEqualTo(id);
        }
    }

    @Test void rejectsOversizedControlOrMalformedNamesAndNonnumericValues() {
        for (String name : new String[] {"", "x".repeat(85), "é".repeat(43), "probe\n1", "\ud800"}) {
            assertThatThrownBy(() -> ManualFarmReading.draft("key", name, "", "water.ph", "pH", "7", "GOOD", Instant.EPOCH))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> ManualFarmReading.draft("key", "probe", "", "water.ph", "pH", "NaN", "GOOD", Instant.EPOCH))
                .hasMessageContaining("Enter a number");
        assertThat(ManualFarmReading.draft("key", "probe", "", "water.ph", "pH", "", "MISSING", Instant.EPOCH))
                .doesNotContainKey("value");
    }

    @Test void malformedOrNoncanonicalEncodedAdapterNamesStayLiteral() {
        for (String id : new String[] {"name.!", "name.", "name.Cg", "name./w", "name.cHJvYmU="}) {
            var saved = new FarmReading(1, ManualFarmReading.SOURCE, "key", id, "water.ph", "pH",
                    BigDecimal.ONE, "GOOD", Instant.EPOCH, Instant.EPOCH, "", "");
            assertThat(ManualFarmReading.equipmentName(saved)).isEqualTo(id);
        }
    }
}
