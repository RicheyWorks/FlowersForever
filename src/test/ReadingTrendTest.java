package com.flowerfarm.telemetry;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadingTrendTest {
    private static FarmReading row(long id, String time, String value, String quality) {
        return new FarmReading(id, "source", "key-" + id, "probe", "soil.moisture", "%",
                value == null ? null : new BigDecimal(value), quality, Instant.parse(time),
                Instant.parse("2026-10-10T12:00:00Z"), "North", "");
    }
    private static FarmReading row(long id, String value, String quality) {
        return row(id, "2026-10-10T11:00:00Z", value, quality);
    }

    @Test void preservesTinyChangesAtLargeBaselineAndNanosecondIntervals() {
        var trend = new ReadingTrend(List.of(
                row(1, "2026-10-10T11:00:00.000000001Z", "999999999999999999.000000000001", "GOOD"),
                row(2, "2026-10-10T11:00:00.000000002Z", "999999999999999999.000000000002", "GOOD"),
                row(3, "2026-10-10T11:00:00.000000005Z", "999999999999999999.000000000005", "GOOD")));
        assertThat(trend.points()).extracting(ReadingTrend.Point::timeFraction).containsExactly(0.0, 0.25, 1.0);
        assertThat(trend.points()).extracting(ReadingTrend.Point::valueFraction).containsExactly(0.0, 0.25, 1.0);
        assertThat(trend.maximum()).isEqualByComparingTo("999999999999999999.000000000005");
    }

    @Test void onlyAdjacentGoodPointsJoinAndFaultNumbersDoNotDistortRange() {
        var trend = new ReadingTrend(List.of(row(1, "-4", "GOOD"), row(2, "-2", "GOOD"),
                row(3, "0", "SUSPECT"), row(4, "2", "GOOD"), row(5, null, "MISSING"),
                row(6, "4", "GOOD"), row(7, "999", "ERROR"), row(8, "6", "GOOD")));
        assertThat(trend.points()).extracting(ReadingTrend.Point::connectsPrevious)
                .containsExactly(false, true, false, false, false, false, false, false);
        assertThat(trend.points().get(4).valueFraction()).isNull();
        assertThat(trend.points().get(6).valueFraction()).isNull();
        assertThat(trend.minimum()).isEqualByComparingTo("-4");
        assertThat(trend.maximum()).isEqualByComparingTo("6");
    }

    @Test void singleAndConstantReadingsUseFiniteCenteredGeometry() {
        var single = new ReadingTrend(List.of(row(1, "0", "GOOD")));
        assertThat(single.points().get(0).timeFraction()).isEqualTo(0.5);
        assertThat(single.points().get(0).valueFraction()).isEqualTo(0.5);
        var constant = new ReadingTrend(List.of(row(1, "0", "GOOD"), row(2, "0", "GOOD")));
        assertThat(constant.points()).extracting(ReadingTrend.Point::valueFraction).containsExactly(0.5, 0.5);
        assertThat(constant.points().get(1).connectsPrevious()).isTrue();
    }

    @Test void allMissingOrFaultedReadingsRemainGapsWithoutInventedRange() {
        var trend = new ReadingTrend(List.of(row(1, null, "MISSING"), row(2, "123", "ERROR"), row(3, null, "ERROR")));
        assertThat(trend.minimum()).isNull(); assertThat(trend.maximum()).isNull();
        assertThat(trend.points()).allSatisfy(point -> {
            assertThat(point.valueFraction()).isNull(); assertThat(point.connectsPrevious()).isFalse();
        });
    }

    @Test void spansOverDurationNanosecondLimitAndIrregularIntervalsStayFinite() {
        var trend = new ReadingTrend(List.of(row(1, "1970-01-01T00:00:00Z", "-999999999999999999", "GOOD"),
                row(2, "2300-01-01T00:00:00Z", "999999999999999999", "GOOD")));
        assertThat(trend.points()).extracting(ReadingTrend.Point::timeFraction).containsExactly(0.0, 1.0);
        assertThat(trend.points()).extracting(ReadingTrend.Point::valueFraction).containsExactly(0.0, 1.0);
    }

    @Test void rejectsMixedChannelsBackwardsTimeAndDuplicateTieOrder() {
        var first = row(1, "1", "GOOD");
        for (var other : List.of(
                new FarmReading(2, "other", "k", "probe", "soil.moisture", "%", BigDecimal.ONE, "GOOD", first.observedAt(), first.receivedAt(), "North", ""),
                new FarmReading(2, "source", "k", "other", "soil.moisture", "%", BigDecimal.ONE, "GOOD", first.observedAt(), first.receivedAt(), "North", ""),
                new FarmReading(2, "source", "k", "probe", "soil.temperature", "%", BigDecimal.ONE, "GOOD", first.observedAt(), first.receivedAt(), "North", ""),
                new FarmReading(2, "source", "k", "probe", "soil.moisture", "C", BigDecimal.ONE, "GOOD", first.observedAt(), first.receivedAt(), "North", ""),
                new FarmReading(2, "source", "k", "probe", "soil.moisture", "%", BigDecimal.ONE, "GOOD", first.observedAt(), first.receivedAt(), "South", ""),
                row(2, "2026-10-09T00:00:00Z", "1", "GOOD"), first)) {
            assertThatThrownBy(() -> new ReadingTrend(List.of(first, other))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void boundedImmutableSnapshotDoesNotChangeWithCallerList() {
        var input = new java.util.ArrayList<>(List.of(row(1, "1", "GOOD")));
        var trend = new ReadingTrend(input); input.clear();
        assertThat(trend.readings()).hasSize(1);
        assertThatThrownBy(() -> trend.points().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ReadingTrend(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReadingTrend(IntStream.rangeClosed(1, 201)
                .mapToObj(i -> row(i, "1", "GOOD")).toList())).isInstanceOf(IllegalArgumentException.class);
    }
}
