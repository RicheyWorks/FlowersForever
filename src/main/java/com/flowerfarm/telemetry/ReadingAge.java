package com.flowerfarm.telemetry;

import java.time.Duration;
import java.time.Instant;

/** Age describes the reported measurement time, never equipment health or connection state. */
public final class ReadingAge {
    private ReadingAge() { }

    public static String describe(Instant measuredAt, Instant shownAt) {
        Duration age = Duration.between(measuredAt, shownAt);
        if (age.isNegative()) return "Clock ahead — check time";
        if (age.toMinutes() < 1) return "Under a minute old";
        if (age.toHours() < 1) return amount(age.toMinutes(), "minute");
        if (age.toDays() < 1) return amount(age.toHours(), "hour");
        return amount(age.toDays(), "day");
    }

    private static String amount(long value, String unit) {
        return value + " " + unit + (value == 1 ? "" : "s") + " old";
    }
}
