package com.flowerfarm.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Apiary hive check")
class HiveCheckTest {

    private static final LocalDate CHECK_DATE = LocalDate.of(2027, 4, 12);

    @Test
    void trimsNamesKeepsOptionalObservationsOpenAndSnapshotsIndependently() {
        HiveCheck check = new HiveCheck(" East apiary ", " Hive 4 ", CHECK_DATE,
                null, null, null, null, null);

        assertThat(check.getApiaryName()).isEqualTo("East apiary");
        assertThat(check.getHiveLabel()).isEqualTo("Hive 4");
        assertThat(check.getPurpose()).isEmpty();
        assertThat(check.getObservations()).isEmpty();
        assertThat(check.getActions()).isEmpty();
        assertThat(check.getStatus()).isEqualTo(HiveCheckStatus.NOT_ASSESSED);

        HiveCheck snapshot = check.snapshot();
        snapshot.setHiveLabel("Hive 5");
        assertThat(check.getHiveLabel()).isEqualTo("Hive 4");
    }

    @Test
    void requiresApiaryHiveAndCheckDate() {
        assertThatThrownBy(() -> new HiveCheck(" ", "Hive 4", CHECK_DATE,
                null, null, null, null, null))
                .hasMessageContaining("Apiary name");
        assertThatThrownBy(() -> new HiveCheck("East apiary", " ", CHECK_DATE,
                null, null, null, null, null))
                .hasMessageContaining("Hive label");
        assertThatThrownBy(() -> new HiveCheck("East apiary", "Hive 4", null,
                null, null, null, null, null))
                .hasMessageContaining("required hive check details");
    }

    @Test
    void guardsNextCheckDateAndTextLengths() {
        HiveCheck check = check();
        check.setNextCheckDate(CHECK_DATE.minusDays(1));
        assertThatThrownBy(check::validate)
                .hasMessageContaining("Next check date");
        check.setNextCheckDate(CHECK_DATE);
        check.validate();
        assertThatThrownBy(() -> check.setPurpose("x".repeat(121)))
                .hasMessageContaining("Purpose cannot exceed 120 characters");
        assertThatThrownBy(() -> check.setObservations("x".repeat(2001)))
                .hasMessageContaining("Observations cannot exceed 2000 characters");
        assertThatThrownBy(() -> check.setActions("x".repeat(2001)))
                .hasMessageContaining("Actions cannot exceed 2000 characters");
    }

    @Test
    void copiesOnlyValidBusinessFields() {
        HiveCheck stored = check();
        HiveCheck incoming = new HiveCheck("North yard", "Hive 9",
                CHECK_DATE.plusDays(7), "Spring check", HiveCheckStatus.ACTIVE,
                CHECK_DATE.plusDays(14), "Brood seen", "Schedule next check");
        stored.copyBusinessFieldsFrom(incoming);
        assertThat(stored.getHiveLabel()).isEqualTo("Hive 9");
        assertThat(stored.getStatus()).isEqualTo(HiveCheckStatus.ACTIVE);
        assertThat(stored.getNextCheckDate()).isEqualTo(CHECK_DATE.plusDays(14));

        incoming.setNextCheckDate(CHECK_DATE.minusDays(1));
        assertThatThrownBy(() -> stored.copyBusinessFieldsFrom(incoming))
                .hasMessageContaining("Next check date");
        assertThat(stored.getHiveLabel()).isEqualTo("Hive 9");
    }

    private static HiveCheck check() {
        return new HiveCheck("East apiary", "Hive 4", CHECK_DATE,
                "Spring check", HiveCheckStatus.NEEDS_FOLLOW_UP,
                CHECK_DATE.plusDays(10), "Adult bees at entrance", "Observe again");
    }
}
