package com.flowerfarm.telemetry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingAgeTest {
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @ParameterizedTest
    @CsvSource({"0, Under a minute old", "59, Under a minute old", "60, 1 minute old", "119, 1 minute old",
            "120, 2 minutes old", "3599, 59 minutes old", "3600, 1 hour old", "7199, 1 hour old",
            "7200, 2 hours old", "86399, 23 hours old", "86400, 1 day old", "172800, 2 days old"})
    void ageUsesElapsedMeasurementTimeAndClearBoundaries(long seconds, String expected) {
        assertThat(ReadingAge.describe(NOW.minusSeconds(seconds), NOW)).isEqualTo(expected);
    }

    @Test void anyFutureObservationCallsOutTheClockInsteadOfClaimingFreshness() {
        for (Instant future : List.of(NOW.plusNanos(1), NOW.plusSeconds(300))) {
            assertThat(ReadingAge.describe(future, NOW)).isEqualTo("Clock ahead — check time");
        }
        assertThat(ReadingAge.describe(NOW.minusSeconds(60).plusNanos(1), NOW)).isEqualTo("Under a minute old");
    }
}
